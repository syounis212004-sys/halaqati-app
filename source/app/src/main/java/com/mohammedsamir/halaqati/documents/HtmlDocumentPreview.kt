package com.mohammedsamir.halaqati.documents

import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.coroutines.resume

/**
 * RC15.2 — one live document surface.
 *
 * The preview WebView is attached, visible, sized and already rendered. Printing that same
 * WebView is the most reliable path on Android: a hidden/second WebView is what stalled
 * (onPageFinished / evaluateJavascript never arriving → "Timed out waiting for 30000 ms").
 * The registry below lets [VectorDocumentRenderer] export from the preview itself.
 */
internal class PreviewRenderState {
    var composeHtml: String = ""
    var composeHash: Int = 0
    var zoom: Float = 1f
    var loadedHash: Int = 0
    var ready: Boolean = false
    var exporting: Boolean = false
    var onReady: (() -> Unit)? = null
}

object LiveDocumentPreview {
    private var ref: java.lang.ref.WeakReference<WebView>? = null

    internal fun holderOf(view: WebView): PreviewRenderState? = view.tag as? PreviewRenderState

    internal fun bind(view: WebView) { ref = java.lang.ref.WeakReference(view) }

    internal fun unbind(view: WebView) { if (ref?.get() === view) ref = null }

    /** Main thread only. Returns the attached, laid-out preview WebView or null. */
    fun current(): WebView? = ref?.get()?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }

    /**
     * Main thread only. Ensures the live preview shows [html] (loading it into the SAME WebView
     * when the issued document differs from the draft, e.g. an official certificate number) and
     * waits for it to render. Returns null when no usable preview exists; callers then fall back.
     */
    suspend fun acquireFor(html: String, timeoutMs: Long = 8_000L): WebView? {
        val view = current() ?: return null
        val holder = holderOf(view) ?: return null
        val hash = html.hashCode()
        holder.exporting = true
        if (holder.loadedHash == hash && holder.ready) return view
        val ok = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                holder.ready = false
                holder.loadedHash = hash
                holder.onReady = { if (cont.isActive) cont.resume(Unit) }
                cont.invokeOnCancellation { holder.onReady = null }
                view.loadDataWithBaseURL("file:///android_asset/documents/", previewHtml(html), "text/html", "UTF-8", null)
            }
        }
        return if (ok != null) view else null
    }

    /** Main thread only. Ends export mode and restores whatever the screen currently shows. */
    fun release(view: WebView?) {
        val v = view ?: return
        val holder = holderOf(v) ?: return
        holder.exporting = false
        holder.onReady = null
        if (holder.composeHash != 0 && holder.loadedHash != holder.composeHash) {
            holder.ready = false
            holder.loadedHash = holder.composeHash
            v.loadDataWithBaseURL("file:///android_asset/documents/", previewHtml(holder.composeHtml), "text/html", "UTF-8", null)
        } else {
            v.post { applyPreviewFit(v, holder.zoom) }
        }
    }

    /** Main thread only. Lays the certificate out at exactly the view width for raster capture. */
    internal fun applyExportFit(view: WebView) {
        view.evaluateJavascript("window.__HALAQATI_EXPORT_FIT__ && window.__HALAQATI_EXPORT_FIT__();", null)
    }

    /** RC24: fail-closed structured certificate validation on the exact live preview DOM. */
    suspend fun evaluateCertificateGate(view: WebView): CertificateRenderGateResult =
        com.mohammedsamir.halaqati.documents.awaitCertificateGate(view)
}


/**
 * RC13 preview renderer.
 *
 * v2.44 did not depend on WebView's mobile zoom heuristics for certificate/report previews.
 * It kept the A4 document at its real desktop width and scaled that document explicitly into
 * the preview viewport. The fit rules are screen-only, so printing this same WebView produces
 * the original A4 HTML/CSS layout.
 */
@Composable
fun HtmlDocumentPreview(
    html: String,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
) {
    val safeZoom = zoom.coerceIn(.7f, 1.2f)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                tag = PreviewRenderState()
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowContentAccess = false
                settings.allowFileAccess = true
                settings.blockNetworkLoads = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.defaultTextEncodingName = "UTF-8"
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                isHorizontalScrollBarEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val holder = view.tag as? PreviewRenderState ?: return
                        view.post {
                            applyPreviewFit(view, holder.zoom)
                            // Fonts + two frames, then the page is really painted.
                            view.evaluateJavascript(
                                "(function(){try{return (!document.fonts||document.fonts.status==='loaded')&&Array.prototype.every.call(document.images||[],function(i){return i.complete})}catch(e){return true}})()",
                            ) {
                                view.postDelayed({
                                    holder.ready = true
                                    holder.onReady?.invoke()
                                    holder.onReady = null
                                }, 48)
                            }
                        }
                    }

                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                        (view.tag as? PreviewRenderState)?.ready = false
                        LiveDocumentPreview.unbind(view)
                        return true
                    }
                }
                addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) { LiveDocumentPreview.bind(v as WebView) }
                    override fun onViewDetachedFromWindow(v: android.view.View) { LiveDocumentPreview.unbind(v as WebView) }
                })
                LiveDocumentPreview.bind(this)
            }
        },
        update = { view ->
            val holder = view.tag as? PreviewRenderState ?: PreviewRenderState().also { view.tag = it }
            val hash = html.hashCode()
            holder.composeHtml = html
            holder.composeHash = hash
            val zoomChanged = kotlin.math.abs(holder.zoom - safeZoom) > .001f
            holder.zoom = safeZoom
            // While exporting, the export owns the WebView; release() restores the screen content.
            if (holder.exporting) {
                // no-op: the export owns the view until release()
            } else if (holder.loadedHash != hash) {
                holder.loadedHash = hash
                holder.ready = false
                view.loadDataWithBaseURL(
                    "file:///android_asset/documents/",
                    previewHtml(html),
                    "text/html",
                    "UTF-8",
                    null,
                )
            } else if (zoomChanged) {
                view.post { applyPreviewFit(view, holder.zoom) }
            }
        },
        onRelease = { view -> LiveDocumentPreview.unbind(view) },
    )
}

private fun applyPreviewFit(view: WebView, zoom: Float) {
    val z = zoom.coerceIn(.7f, 1.2f)
    view.evaluateJavascript(
        "window.__HALAQATI_APPLY_PREVIEW_FIT__ && window.__HALAQATI_APPLY_PREVIEW_FIT__(${z});",
        null,
    )
}

/** Display-only wrapper; the PDF renderer receives the unmodified [html]. */
private fun previewHtml(html: String): String {
    val viewport = "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">"
    val viewportRegex = Regex("<meta\\s+name=[\\\"']viewport[\\\"'][^>]*>", RegexOption.IGNORE_CASE)
    val base = if (viewportRegex.containsMatchIn(html)) html.replace(viewportRegex, viewport) else html.replace("<head>", "<head>$viewport")
    val bridge = """
      <style id="halaqati-preview-fit-style">
        @media screen {
        html,body{margin:0!important;padding:0!important;min-width:0!important;max-width:none!important;background:transparent!important;overflow-x:auto!important;direction:ltr!important;}
        body{display:block!important;justify-content:initial!important;align-items:initial!important;min-height:0!important;}
        .halaqati-certificate{margin:0!important;max-width:none!important;transform-origin:top left!important;direction:rtl!important;}
        .pro-report{margin:0!important;padding:0!important;max-width:none!important;transform-origin:top left!important;direction:rtl!important;}
        .pro-report-page{max-width:none!important;}
        }
        @media print {
          html,body{width:auto!important;height:auto!important;min-height:0!important;overflow:visible!important;}
          .halaqati-certificate,.pro-report{transform:none!important;left:0!important;top:0!important;}
        }
      </style>
      <script>
      (function(){
        function num(v,d){v=Number(v);return Number.isFinite(v)?v:d}
        function applyPreviewFit(zoom){
          try{
            zoom=Math.max(.7,Math.min(1.2,num(zoom,1)));
            window.__HALAQATI_PREVIEW_ZOOM__=zoom;
            var cert=document.querySelector('.halaqati-certificate');
            var report=document.querySelector('.pro-report');
            var target=cert||report;
            if(!target)return false;
            target.style.transform='none';
            target.style.left='0px';
            target.style.top='0px';
            target.style.position='relative';
            var page=cert||document.querySelector('.pro-report-page');
            var baseW=cert?1122:Math.max(1,(page&&page.offsetWidth)||1120);
            var rawH=Math.max(target.scrollHeight,target.offsetHeight,1);
            var viewportW=Math.max(1,document.documentElement.clientWidth||window.innerWidth||360);
            var pad=8;
            var available=Math.max(1,viewportW-pad*2);
            var fit=Math.min(1,available/baseW);
            var scale=fit*zoom;
            var visualW=baseW*scale;
            var left=visualW<available?Math.max(0,(available-visualW)/2):0;
            target.style.width=baseW+'px';
            target.style.transformOrigin='top left';
            target.style.transform='scale('+scale+')';
            target.style.left=(pad+left)+'px';
            var h=Math.max(1,Math.ceil(rawH*scale)+16);
            document.body.style.height=h+'px';
            document.documentElement.style.minHeight=h+'px';
            document.body.style.width=Math.max(viewportW,Math.ceil(visualW+pad*2))+'px';
            document.documentElement.dataset.previewFit='1';
            document.documentElement.dataset.previewScale=String(scale);
            window.scrollTo(0,0);
            return true;
          }catch(_e){return false}
        }
        window.__HALAQATI_APPLY_PREVIEW_FIT__=applyPreviewFit;
        window.__HALAQATI_EXPORT_FIT__=function(){
          try{
            var cert=document.querySelector('.halaqati-certificate');if(!cert)return false;
            var vw=Math.max(1,document.documentElement.clientWidth||window.innerWidth||360);
            var s=vw/1122;
            cert.style.width='1122px';cert.style.transformOrigin='top left';
            cert.style.transform='scale('+s+')';cert.style.left='0px';cert.style.top='0px';cert.style.position='relative';
            document.body.style.width=vw+'px';document.body.style.height=Math.ceil(794*s)+'px';
            window.scrollTo(0,0);return true;
          }catch(_e){return false}
        };
        window.addEventListener('resize',function(){applyPreviewFit(window.__HALAQATI_PREVIEW_ZOOM__||1)});
        var run=function(){requestAnimationFrame(function(){requestAnimationFrame(function(){applyPreviewFit(window.__HALAQATI_PREVIEW_ZOOM__||1)})})};
        if(document.fonts&&document.fonts.ready)document.fonts.ready.then(run).catch(run);else run();
      })();
      </script>
    """.trimIndent()
    return if (base.contains("</body>", ignoreCase = true)) base.replace(Regex("</body>", RegexOption.IGNORE_CASE), "$bridge</body>") else "$base$bridge"
}
