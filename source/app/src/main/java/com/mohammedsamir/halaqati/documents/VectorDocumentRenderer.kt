package com.mohammedsamir.halaqati.documents

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.HalaqatiWebViewPdfWriter
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.provider.MediaStore
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


enum class DocumentOrientation { LANDSCAPE, PORTRAIT }

enum class DocumentExportPhase(val arabicLabel: String) {
    PREPARING("تجهيز المستند…"),
    CHROMIUM("إنشاء PDF المتجهي…"),
    COMPATIBILITY("محرك Chromium لم يستجب؛ جاري استخدام وضع التوافق…"),
    SAVING("حفظ ملف PDF والتحقق منه…"),
}

data class VectorPdfResult(
    val pageCount: Int,
    val bytes: Long,
    val engine: String = "chromium-webview-vector",
    val uri: String? = null,
    val directory: String? = null,
)

private data class FallbackSegment(val top: Int, val bottom: Int) {
    val height: Int get() = max(1, bottom - top)
}

private data class FallbackPlan(
    val sourceWidth: Int,
    val pageCssHeight: Int,
    val segments: List<FallbackSegment>,
)

/**
 * Native document renderer used by certificates and reports.
 *
 * RC14 restores the complete v2.44 strategy instead of relying on one WebView print path:
 *  1. Try Chromium/WebView PrintDocumentAdapter first for searchable/vector text.
 *  2. If an OEM WebView stalls or fails, cancel it quickly and automatically switch to a
 *     compatibility renderer that draws the exact same HTML WebView to A4 PdfDocument pages.
 *  3. Only after a real PDF passes signature/size validation is it published to Downloads.
 *
 * This keeps the application itself Kotlin/Compose while matching the reliability behavior that
 * made v2.44 usable on devices where direct Chromium PDF generation was unreliable.
 */
object VectorDocumentRenderer {
    private const val READY_TIMEOUT_MS = 30_000L // v2.44 PAGE_READY_TIMEOUT_MS
    private const val READY_MAX_POLLS = 120 // v2.44: after ~12s of polling, print anyway instead of failing
    private const val READY_POLL_MS = 100L
    private const val DIRECT_WRITE_TIMEOUT_MS = 120_000L // v2.44 DIRECT_SAVE_TIMEOUT_MS
    private const val COMPATIBILITY_TIMEOUT_MS = 55_000L
    private const val MIN_VALID_PDF_BYTES = 1024L
    private const val MAX_PDF_BYTES = 32L * 1024L * 1024L
    private const val FALLBACK_SCALE_NORMAL = 1.5f
    private const val FALLBACK_SCALE_LONG = 1.25f
    private const val FALLBACK_SCALE_LOW = 1.0f
    // v2.44 never skipped the vector engine; one slow run must not force raster output afterwards.
    private const val DIRECT_FAILURE_COOLDOWN_MS = 0L
    @Volatile private var directBrokenUntilEpochMs: Long = 0L
    private val PDF_MAGIC = byteArrayOf('%'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), 'F'.code.toByte(), '-'.code.toByte())
    private val exportMutex = Mutex()

    /** Compatibility path for existing SAF callers. New certificate/report actions use Downloads directly. */
    suspend fun saveHtmlPdf(
        context: Context,
        uri: Uri,
        html: String,
        documentName: String,
        orientation: DocumentOrientation,
        onProgress: ((DocumentExportPhase) -> Unit)? = null,
    ): VectorPdfResult = exportMutex.withLock {
        require(html.isNotBlank()) { "محتوى المستند فارغ" }
        val temp = newTempPdf(context)
        try {
            notifyProgress(onProgress, DocumentExportPhase.PREPARING)
            val rendered = renderToSeekableFile(context, temp, html, documentName, orientation, onProgress)
            notifyProgress(onProgress, DocumentExportPhase.SAVING)
            val copied = copyValidatedPdfToUri(context, temp, uri)
            rendered.copy(bytes = copied, uri = uri.toString())
        } finally {
            withContext(Dispatchers.IO) { runCatching { temp.delete() } }
        }
    }

    /**
     * Render fully to a private temp file first. No 0-byte Downloads entry is exposed while a
     * WebView/renderer is still working or while a fallback is being selected.
     */
    suspend fun saveHtmlPdfToDownloads(
        context: Context,
        html: String,
        fileName: String,
        subdirectory: String,
        documentName: String,
        orientation: DocumentOrientation,
        onProgress: ((DocumentExportPhase) -> Unit)? = null,
    ): VectorPdfResult = exportMutex.withLock {
        require(html.isNotBlank()) { "محتوى المستند فارغ" }
        val temp = newTempPdf(context)
        try {
            notifyProgress(onProgress, DocumentExportPhase.PREPARING)
            val rendered = renderToSeekableFile(context, temp, html, documentName, orientation, onProgress)
            notifyProgress(onProgress, DocumentExportPhase.SAVING)
            val stored = withContext(Dispatchers.IO) {
                persistCompletedPdfToDownloads(context, temp, safePdfName(fileName), safeSubdirectory(subdirectory))
            }
            rendered.copy(
                bytes = stored.bytes,
                engine = "${rendered.engine}+${stored.engine}",
                uri = stored.uri,
                directory = stored.directory,
            )
        } finally {
            withContext(Dispatchers.IO) { runCatching { temp.delete() } }
        }
    }

    suspend fun saveHtmlPdfToFile(
        context: Context,
        file: File,
        html: String,
        documentName: String,
        orientation: DocumentOrientation,
        onProgress: ((DocumentExportPhase) -> Unit)? = null,
    ): VectorPdfResult = exportMutex.withLock {
        require(html.isNotBlank()) { "محتوى المستند فارغ" }
        notifyProgress(onProgress, DocumentExportPhase.PREPARING)
        renderToSeekableFile(context, file, html, documentName, orientation, onProgress)
    }

    private suspend fun notifyProgress(
        callback: ((DocumentExportPhase) -> Unit)?,
        phase: DocumentExportPhase,
    ) {
        if (callback == null) return
        withContext(Dispatchers.Main.immediate) { callback(phase) }
    }

    private suspend fun renderToSeekableFile(
        context: Context,
        file: File,
        html: String,
        documentName: String,
        orientation: DocumentOrientation,
        onProgress: ((DocumentExportPhase) -> Unit)?,
    ): VectorPdfResult {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            if (file.exists() && !file.delete()) error("تعذر استبدال ملف PDF المؤقت")
        }

        // RC15.2 tier 0: export from the SAME preview WebView the user is looking at.
        // It is attached, visible and already painted, so there is nothing to wait for.
        val live = try {
            renderFromLivePreview(file, html, documentName, orientation, onProgress)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.w("HalaqatiPdf", "live preview export failed; using isolated engines", e)
            null
        }
        if (live != null) return live
        withContext(Dispatchers.IO) { runCatching { file.delete() } }

        var directError: Throwable? = null
        if (!shouldSkipDirectChromium()) {
            directError = runCatching {
                notifyProgress(onProgress, DocumentExportPhase.CHROMIUM)
                renderChromiumToFile(context, file, html, documentName, orientation)
            }.exceptionOrNull()
            if (directError == null) {
                clearDirectChromiumFailure()
                val bytes = withContext(Dispatchers.IO) {
                    validatePdfFile(file)
                    file.length()
                }
                return VectorPdfResult(
                    pageCount = pdfPageCountHint(file).coerceAtLeast(1),
                    bytes = bytes,
                    engine = "chromium-webview-vector",
                )
            }
            markDirectChromiumFailure()
        } else {
            directError = IllegalStateException("تم تجاوز Chromium مؤقتًا بعد تعطل سابق على هذا الجهاز")
        }

        // The legacy v2.44 implementation did not leave the user stuck when an OEM Chromium
        // adapter failed. It switched to a compatibility path. Do the same here and ensure the
        // failed direct descriptor cannot keep the public operation alive. The small circuit
        // breaker above also prevents a bulk certificate job from waiting 12 seconds per student.
        withContext(Dispatchers.IO) { runCatching { file.delete() } }
        notifyProgress(onProgress, DocumentExportPhase.COMPATIBILITY)
        return try {
            withTimeout(COMPATIBILITY_TIMEOUT_MS) {
                renderCompatibilityToFile(context, file, html, orientation)
            }
        } catch (fallback: Throwable) {
            val primary = directError?.message?.take(160).orEmpty()
            val secondary = fallback.message?.take(160).orEmpty()
            throw IllegalStateException(
                "تعذر إنشاء PDF. المسار المتجهي: ${primary.ifBlank { "لم يستجب" }} · وضع التوافق: ${secondary.ifBlank { "فشل" }}",
                fallback,
            )
        }
    }

    private const val LIVE_VECTOR_TIMEOUT_MS = 25_000L

    /**
     * Vector PDF from the live preview; if that adapter misbehaves on an OEM WebView, the
     * certificate is rasterised from the same painted preview (independent of Chromium printing).
     * Returns null when no usable preview is on screen, so isolated engines take over.
     */
    private suspend fun renderFromLivePreview(
        file: File,
        html: String,
        documentName: String,
        orientation: DocumentOrientation,
        onProgress: ((DocumentExportPhase) -> Unit)?,
    ): VectorPdfResult? {
        val view = withContext(Dispatchers.Main.immediate) { LiveDocumentPreview.acquireFor(html) }
        if (view == null) {
            withContext(Dispatchers.Main.immediate) { LiveDocumentPreview.release(LiveDocumentPreview.current()) }
            return null
        }
        try {
            requireCertificateGateIfNeeded(view, html)
            notifyProgress(onProgress, DocumentExportPhase.CHROMIUM)
            val vectorError = try {
                val descriptor = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE,
                    )
                }
                try {
                    withContext(Dispatchers.Main.immediate) {
                        val adapter = view.createPrintDocumentAdapter(documentName)
                        withTimeout(LIVE_VECTOR_TIMEOUT_MS) { writeAdapter(adapter, descriptor, orientation) }
                    }
                } catch (t: Throwable) {
                    runCatching { descriptor.close() }
                    throw t
                }
                withContext(Dispatchers.IO) { validatePdfFile(file) }
                null
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                e
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                e
            }
            if (vectorError == null) {
                val bytes = withContext(Dispatchers.IO) { file.length() }
                return VectorPdfResult(pageCount = pdfPageCountHint(file).coerceAtLeast(1), bytes = bytes, engine = "live-preview-vector")
            }
            android.util.Log.w("HalaqatiPdf", "live vector failed", vectorError)
            if (!html.contains("halaqati-certificate")) return null
            notifyProgress(onProgress, DocumentExportPhase.COMPATIBILITY)
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
            renderLiveCertificateRaster(view, file)
            val bytes = withContext(Dispatchers.IO) { validatePdfFile(file); file.length() }
            return VectorPdfResult(pageCount = 1, bytes = bytes, engine = "live-preview-raster-fallback")
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) { LiveDocumentPreview.release(view) }
        }
    }

    private suspend fun requireCertificateGateIfNeeded(view: WebView, html: String) {
        if (!html.contains("halaqati-certificate")) return
        // The page gate re-fits the layout itself (idempotent), so only still-loading fonts can delay it.
        delay(64)
        val gate = awaitCertificateGate(view)
        if (!gate.valid) throw IllegalStateException(gate.arabicError())
    }

    private suspend fun renderLiveCertificateRaster(view: WebView, file: File) {
        val bitmap = withContext(Dispatchers.Main.immediate) {
            LiveDocumentPreview.applyExportFit(view)
            delay(140) // two+ frames so the export fit is painted
            val w = max(1, view.width)
            val h = max(1, min(view.height, ceil(w * 794f / 1122f).toInt()))
            val scale = min(2.5f, 2600f / w).coerceAtLeast(1f)
            val bmp = Bitmap.createBitmap(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale, scale)
            view.draw(canvas)
            bmp
        }
        try {
            withContext(Dispatchers.IO) {
                val pdf = PdfDocument()
                try {
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(
                        bitmap,
                        Rect(0, 0, bitmap.width, bitmap.height),
                        Rect(0, 0, 842, 595),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                    )
                    pdf.finishPage(page)
                    file.parentFile?.mkdirs()
                    FileOutputStream(file, false).use { out -> pdf.writeTo(out); out.flush(); out.fd.sync() }
                } finally {
                    runCatching { pdf.close() }
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun renderChromiumToFile(
        context: Context,
        file: File,
        html: String,
        documentName: String,
        orientation: DocumentOrientation,
    ): VectorPdfResult {
        val descriptor = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE,
            )
        }
        val activity = context.findActivity() ?: error("يلزم فتح المستند من واجهة التطبيق")
        return try {
            withContext(Dispatchers.Main.immediate) {
                val w = if (orientation == DocumentOrientation.PORTRAIT) 794 else 1122
                val webView = createPdfWebView(activity, w, pageCssHeight(w, orientation))
                try {
                    loadAndAwaitReady(webView, html)
                    requireCertificateGateIfNeeded(webView, html)
                    val adapter = webView.createPrintDocumentAdapter(documentName)
                    withTimeout(DIRECT_WRITE_TIMEOUT_MS) {
                        writeAdapter(adapter, descriptor, orientation)
                    }
                } finally {
                    destroyWebView(webView)
                }
            }
        } catch (t: Throwable) {
            runCatching { descriptor.close() }
            throw t
        }.also {
            withContext(Dispatchers.IO) { validatePdfFile(file) }
        }
    }

    private suspend fun renderCompatibilityToFile(
        context: Context,
        file: File,
        html: String,
        orientation: DocumentOrientation,
    ): VectorPdfResult {
        val activity = context.findActivity() ?: error("يلزم فتح المستند من واجهة التطبيق")
        val sourceWidthHint = if (html.contains("halaqati-certificate")) 1122 else if (orientation == DocumentOrientation.PORTRAIT) 792 else 1120
        val pageCssHeightHint = pageCssHeight(sourceWidthHint, orientation)

        val webView = withContext(Dispatchers.Main.immediate) {
            createPdfWebView(activity, sourceWidthHint, pageCssHeightHint).apply {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                measure(
                    View.MeasureSpec.makeMeasureSpec(sourceWidthHint, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(pageCssHeightHint, View.MeasureSpec.EXACTLY),
                )
                layout(0, 0, sourceWidthHint, pageCssHeightHint)
            }
        }

        try {
            withContext(Dispatchers.Main.immediate) {
                loadAndAwaitReady(webView, compatibilityHtml(html, orientation))
                requireCertificateGateIfNeeded(webView, html)
            }
            val plan = withContext(Dispatchers.Main.immediate) { fallbackPlan(webView, orientation) }
            require(plan.segments.isNotEmpty()) { "لم يجد وضع التوافق صفحات قابلة للرسم" }
            require(plan.segments.size <= 80) { "التقرير طويل جدًا لوضع التوافق" }

            val firstScale = when {
                plan.segments.size >= 12 -> FALLBACK_SCALE_LOW
                plan.segments.size >= 6 -> FALLBACK_SCALE_LONG
                else -> FALLBACK_SCALE_NORMAL
            }
            val pageCount = try {
                renderFallbackPdfOnce(webView, file, plan, orientation, firstScale)
            } catch (oom: OutOfMemoryError) {
                withContext(Dispatchers.IO) { runCatching { file.delete() } }
                System.gc()
                renderFallbackPdfOnce(webView, file, plan, orientation, FALLBACK_SCALE_LOW)
            }
            val bytes = withContext(Dispatchers.IO) {
                validatePdfFile(file)
                file.length()
            }
            return VectorPdfResult(
                pageCount = pageCount,
                bytes = bytes,
                engine = "android-webview-raster-fallback-v244",
            )
        } finally {
            withContext(Dispatchers.Main.immediate) { destroyWebView(webView) }
        }
    }

    private suspend fun renderFallbackPdfOnce(
        webView: WebView,
        file: File,
        plan: FallbackPlan,
        orientation: DocumentOrientation,
        scale: Float,
    ): Int {
        val pageWidthPt = if (orientation == DocumentOrientation.LANDSCAPE) 842 else 595
        val pageHeightPt = if (orientation == DocumentOrientation.LANDSCAPE) 595 else 842
        val pdf = PdfDocument()
        try {
            plan.segments.forEachIndexed { index, segment ->
                val bitmap = captureWebViewSegment(
                    webView = webView,
                    sourceWidth = plan.sourceWidth,
                    pageCssHeight = plan.pageCssHeight,
                    segment = segment,
                    scale = scale,
                )
                try {
                    withContext(Dispatchers.IO) {
                        val info = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, index + 1).create()
                        val page = pdf.startPage(info)
                        page.canvas.drawColor(Color.WHITE)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        val src = Rect(0, 0, bitmap.width, bitmap.height)
                        val dstHeight = min(
                            pageHeightPt,
                            max(1, ((segment.height.toFloat() / plan.pageCssHeight.toFloat()) * pageHeightPt).toInt()),
                        )
                        val dst = Rect(0, 0, pageWidthPt, dstHeight)
                        page.canvas.drawBitmap(bitmap, src, dst, paint)
                        pdf.finishPage(page)
                    }
                } finally {
                    bitmap.recycle()
                }
                // Let Compose/WebView breathe between pages on lower-memory phones.
                delay(16)
            }
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                FileOutputStream(file, false).use { output ->
                    pdf.writeTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            return plan.segments.size
        } finally {
            runCatching { pdf.close() }
        }
    }

    private suspend fun captureWebViewSegment(
        webView: WebView,
        sourceWidth: Int,
        pageCssHeight: Int,
        segment: FallbackSegment,
        scale: Float,
    ): Bitmap = withContext(Dispatchers.Main.immediate) {
        val segmentHeight = segment.height.coerceAtMost(pageCssHeight)
        // RC15.1: the WebView is attached now; keep its LayoutParams in sync so the parent's
        // next layout pass does not resize it back between measure() and draw().
        runCatching {
            webView.layoutParams?.let { lp -> lp.width = sourceWidth; lp.height = segmentHeight; webView.layoutParams = lp }
        }
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(sourceWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(segmentHeight, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, sourceWidth, segmentHeight)
        webView.scrollTo(0, segment.top)
        // Two frames mirrors the old v2.44 waitForPdfPaint() behavior.
        delay(32)
        val bitmapWidth = max(1, ceil(sourceWidth * scale).toInt())
        val bitmapHeight = max(1, ceil(pageCssHeight * scale).toInt())
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale, scale)
        webView.draw(canvas)
        canvas.restore()
        bitmap
    }

    private suspend fun fallbackPlan(webView: WebView, orientation: DocumentOrientation): FallbackPlan {
        val ratio = if (orientation == DocumentOrientation.LANDSCAPE) 595.0 / 842.0 else 842.0 / 595.0
        val script = """
            (function(){
              try{
                var cert=document.querySelector('.halaqati-certificate');
                var reportPages=[].slice.call(document.querySelectorAll('.pro-report-page'));
                var width=cert?Math.round(cert.getBoundingClientRect().width):Math.round((reportPages[0]&&reportPages[0].getBoundingClientRect().width)||${if (orientation == DocumentOrientation.PORTRAIT) 792 else 1120});
                width=Math.max(320,width);
                var pageH=Math.max(300,Math.round(width*${ratio}));
                var segments=[];
                function absRect(el){var r=el.getBoundingClientRect();return {top:Math.round(r.top+window.scrollY),bottom:Math.round(r.bottom+window.scrollY)}}
                function safeCuts(root,start,end){
                  var nodes=[].slice.call(root.querySelectorAll('.pro-head,.pro-meta,.pro-kpis,.student-report-card,.pro-section-title,.pro-table tr,.pro-footer'));
                  return nodes.map(function(n){return absRect(n).bottom}).filter(function(y){return y>start&&y<end}).sort(function(a,b){return a-b});
                }
                function addPaged(root){
                  var r=absRect(root), top=r.top, end=r.bottom, cuts=safeCuts(root,top,end), guard=0;
                  while(top<end-2&&guard++<80){
                    var maxBottom=Math.min(end,top+pageH);
                    var bottom=maxBottom;
                    if(maxBottom<end){
                      var minAdvance=top+Math.max(90,Math.floor((maxBottom-top)*.45));
                      var candidates=cuts.filter(function(y){return y>minAdvance&&y<=maxBottom});
                      if(candidates.length)bottom=candidates[candidates.length-1];
                    }
                    if(bottom<=top+10)bottom=Math.min(end,top+pageH);
                    segments.push({top:Math.round(top),bottom:Math.round(bottom)});
                    top=bottom;
                  }
                }
                if(cert){var c=absRect(cert);pageH=Math.max(pageH,c.bottom-c.top);segments.push({top:c.top,bottom:c.bottom});}
                else if(reportPages.length){reportPages.forEach(addPaged);}
                else {var h=Math.max(document.documentElement.scrollHeight,document.body?document.body.scrollHeight:0);segments.push({top:0,bottom:h});}
                return {width:width,pageH:pageH,segments:segments};
              }catch(e){return {error:String(e),width:0,pageH:0,segments:[]};}
            })();
        """.trimIndent()
        val raw = kotlinx.coroutines.withTimeoutOrNull(8_000L) { evaluateJavascriptJson(webView, script) }
            ?: error("لم يستجب WebView لتقسيم صفحات وضع التوافق")
        raw.optString("error").takeIf { it.isNotBlank() }?.let { error("فشل تقسيم صفحات وضع التوافق: $it") }
        val width = raw.optInt("width", 0).coerceAtLeast(320)
        val pageH = raw.optInt("pageH", pageCssHeight(width, orientation)).coerceAtLeast(300)
        val array = raw.optJSONArray("segments") ?: error("تعذر قراءة صفحات وضع التوافق")
        val segments = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val top = item.optInt("top", 0).coerceAtLeast(0)
                val bottom = item.optInt("bottom", top + 1).coerceAtLeast(top + 1)
                add(FallbackSegment(top, bottom))
            }
        }
        return FallbackPlan(width, pageH, segments)
    }

    private fun pageCssHeight(sourceWidth: Int, orientation: DocumentOrientation): Int = when (orientation) {
        DocumentOrientation.LANDSCAPE -> max(1, (sourceWidth * 595f / 842f).toInt())
        DocumentOrientation.PORTRAIT -> max(1, (sourceWidth * 842f / 595f).toInt())
    }

    /** Screen-mode document tuned to mirror the print box used by v2.44 compatibility export. */
    private fun compatibilityHtml(html: String, orientation: DocumentOrientation): String {
        val reportWidth = if (orientation == DocumentOrientation.PORTRAIT) 792 else 1120
        val css = """
          <style id="halaqati-compat-render">
            html,body{margin:0!important;padding:0!important;background:#fff!important;overflow:visible!important;min-width:0!important;max-width:none!important;}
            .halaqati-certificate{margin:0!important;box-shadow:none!important;transform:none!important;}
            .pro-report{margin:0!important;padding:0!important;background:#fff!important;}
            .pro-report-page{width:${reportWidth}px!important;max-width:none!important;margin:0!important;box-shadow:none!important;border:0!important;border-radius:0!important;overflow:visible!important;padding:37px!important;transform:none!important;}
          </style>
        """.trimIndent()
        return if (html.contains("</head>", ignoreCase = true)) {
            html.replace(Regex("</head>", RegexOption.IGNORE_CASE), "$css</head>")
        } else "$css$html"
    }

    private suspend fun evaluateJavascriptJson(webView: WebView, script: String): JSONObject = suspendCancellableCoroutine { cont ->
        webView.evaluateJavascript(script) { raw ->
            if (!cont.isActive) return@evaluateJavascript
            try {
                val value = raw?.trim().orEmpty()
                if (value.isBlank() || value == "null") error("لم يرجع WebView بيانات")
                cont.resume(JSONObject(value))
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }
    }

    private suspend fun copyValidatedPdfToUri(context: Context, source: File, uri: Uri): Long = withContext(Dispatchers.IO) {
        validatePdfFile(source)
        val resolver = context.contentResolver
        val output = resolver.openOutputStream(uri, "w") ?: error("تعذر فتح الملف المختار للكتابة")
        output.use { out ->
            FileInputStream(source).use { input -> copyPdfStream(input, out) }
            out.flush()
            if (out is FileOutputStream) runCatching { out.fd.sync() }
        }
        verifyStoredPdf(resolver = resolver, uri = uri)
    }

    private data class StoredPdf(val bytes: Long, val uri: String, val directory: String, val engine: String)

    private fun persistCompletedPdfToDownloads(context: Context, source: File, fileName: String, subdirectory: String): StoredPdf {
        validatePdfFile(source)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relative = buildString {
                append(Environment.DIRECTORY_DOWNLOADS).append("/Halaqati")
                if (subdirectory.isNotBlank()) append('/').append(subdirectory)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            var uri: Uri? = null
            try {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("تعذر إنشاء ملف PDF داخل التنزيلات")
                resolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(source).use { input -> copyPdfStream(input, output) }
                    output.flush()
                    if (output is FileOutputStream) runCatching { output.fd.sync() }
                } ?: error("تعذر فتح ملف PDF داخل التنزيلات")

                val bytes = verifyStoredPdf(resolver, uri)
                val published = resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
                require(published > 0) { "تم إنشاء PDF لكن تعذر نشره في التنزيلات" }
                return StoredPdf(
                    bytes = bytes,
                    uri = uri.toString(),
                    directory = relative,
                    engine = "verified-mediastore",
                )
            } catch (t: Throwable) {
                uri?.let { runCatching { resolver.delete(it, null, null) } }
                throw t
            }
        }

        val publicAllowed = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val base = if (publicAllowed) {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        }
        val dir = File(base, "Halaqati${if (subdirectory.isBlank()) "" else "/$subdirectory"}").apply {
            if (!exists() && !mkdirs()) error("تعذر إنشاء مجلد PDF")
        }
        val target = uniqueFile(dir, fileName)
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(target, false).use { output ->
                    copyPdfStream(input, output)
                    output.flush()
                    output.fd.sync()
                }
            }
            validatePdfFile(target)
            return StoredPdf(
                bytes = target.length(),
                uri = Uri.fromFile(target).toString(),
                directory = dir.absolutePath,
                engine = "verified-file",
            )
        } catch (t: Throwable) {
            runCatching { target.delete() }
            throw t
        }
    }

    private fun verifyStoredPdf(resolver: android.content.ContentResolver, uri: Uri): Long {
        var queried = 0L
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) queried = cursor.getLong(0)
            }
        }
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            var signatureChecked = false
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                if (!signatureChecked) {
                    require(read >= PDF_MAGIC.size && buffer.copyOf(PDF_MAGIC.size).contentEquals(PDF_MAGIC)) {
                        "الملف المحفوظ ليس PDF صالحًا"
                    }
                    signatureChecked = true
                }
                total += read
                require(total <= MAX_PDF_BYTES) { "ملف PDF أكبر من الحد الآمن" }
            }
            require(signatureChecked && total >= MIN_VALID_PDF_BYTES) { "فشل التحقق بعد الحفظ؛ تم منع ملف 0 بايت" }
            return maxOf(total, queried)
        }
        error("تعذر قراءة ملف PDF بعد الحفظ")
    }

    private fun copyPdfStream(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            require(total <= MAX_PDF_BYTES) { "ملف PDF أكبر من الحد الآمن" }
            output.write(buffer, 0, read)
        }
        require(total >= MIN_VALID_PDF_BYTES) { "ملف PDF الناتج فارغ أو غير مكتمل" }
        return total
    }

    private fun validatePdfFile(file: File) {
        require(file.exists() && file.length() in MIN_VALID_PDF_BYTES..MAX_PDF_BYTES) {
            "فشل محرك PDF: الملف الناتج فارغ أو غير مكتمل"
        }
        val header = ByteArray(PDF_MAGIC.size)
        val read = FileInputStream(file).use { it.read(header) }
        require(read == PDF_MAGIC.size && header.contentEquals(PDF_MAGIC)) {
            "فشل محرك PDF: الناتج ليس ملف PDF صالحًا"
        }
    }

    /** Cheap page-count hint used only after the PDF is already validated. */
    private fun pdfPageCountHint(file: File): Int = runCatching {
        val text = FileInputStream(file).use { input ->
            val bytes = ByteArray(min(file.length(), 2_000_000L).toInt())
            val n = input.read(bytes)
            if (n <= 0) "" else String(bytes, 0, n, Charsets.ISO_8859_1)
        }
        Regex("/Type\\s*/Page(?!s)").findAll(text).count()
    }.getOrDefault(-1)

    private fun shouldSkipDirectChromium(): Boolean = System.currentTimeMillis() < directBrokenUntilEpochMs

    private fun markDirectChromiumFailure() {
        directBrokenUntilEpochMs = System.currentTimeMillis() + DIRECT_FAILURE_COOLDOWN_MS
    }

    private fun clearDirectChromiumFailure() {
        directBrokenUntilEpochMs = 0L
    }

    private fun newTempPdf(context: Context): File = File(context.cacheDir, "halaqati_pdf/${UUID.randomUUID()}.pdf").apply {
        parentFile?.mkdirs()
        if (exists()) delete()
    }

    private fun safePdfName(raw: String): String {
        val base = raw.trim().ifBlank { "halaqati.pdf" }
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .take(180)
        return if (base.endsWith(".pdf", true)) base else "$base.pdf"
    }

    private fun safeSubdirectory(raw: String): String = raw.trim()
        .replace(Regex("[^\\p{L}\\p{N} _.-]"), "_")
        .trim(' ', '.', '_')
        .take(48)

    private fun uniqueFile(dir: File, fileName: String): File {
        val direct = File(dir, fileName)
        if (!direct.exists()) return direct
        val stem = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "pdf")
        var i = 2
        while (i < 1000) {
            val candidate = File(dir, "$stem ($i).$ext")
            if (!candidate.exists()) return candidate
            i++
        }
        return File(dir, "$stem-${System.currentTimeMillis()}.$ext")
    }

    /**
     * RC15.1 root-cause fix for "Timed out waiting for 30000 ms" on BOTH engines:
     * a WebView that is never attached to a window (and has 0x0 size) is treated by Chromium as
     * hidden; on many OEM builds its renderer is throttled, onPageFinished/rAF/document.fonts never
     * settle and evaluateJavascript callbacks stall. v2.44 always rendered inside an
     * attached WebView. We now attach the export WebView off-screen with a real A4 size, and we
     * resume the process-wide WebView timers that another screen may have paused.
     */
    private const val HOST_TAG = "halaqati-pdf-host"
    private const val PAGE_FINISHED_SOFT_MS = 12_000L
    private const val JS_EVAL_TIMEOUT_MS = 1_500L
    private const val READY_POLL_BUDGET_MS = 10_000L

    private fun attachOffscreen(activity: Activity, webView: WebView, width: Int, height: Int) {
        val root = activity.window?.decorView as? android.view.ViewGroup ?: return
        runCatching {
            val host = android.widget.FrameLayout(activity).apply {
                tag = HOST_TAG
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                // Off-screen but VISIBLE: Chromium keeps rendering, the user sees nothing.
                translationX = -(width + 4000).toFloat()
            }
            host.addView(webView, android.widget.FrameLayout.LayoutParams(width, height))
            root.addView(host, android.view.ViewGroup.LayoutParams(width, height))
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, width, height)
        }
    }

    private fun createPdfWebView(activity: Activity, width: Int = 1122, height: Int = 794): WebView = WebView(activity).apply {
        runCatching { resumeTimers() }
        runCatching { onResume() }
        attachOffscreen(activity, this, width, height)
        setBackgroundColor(Color.WHITE)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowContentAccess = false
        settings.allowFileAccess = true
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.loadWithOverviewMode = false
        settings.useWideViewPort = true
        settings.defaultTextEncodingName = "UTF-8"
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }

    private fun destroyWebView(webView: WebView) {
        runCatching {
            val host = webView.parent as? android.view.ViewGroup
            host?.removeView(webView)
            if (host?.tag == HOST_TAG) (host.parent as? android.view.ViewGroup)?.removeView(host)
        }
        runCatching { webView.stopLoading() }
        runCatching { webView.loadUrl("about:blank") }
        runCatching { webView.removeAllViews() }
        runCatching { webView.destroy() }
    }

    private suspend fun loadAndAwaitReady(webView: WebView, html: String) {
        withTimeout(READY_TIMEOUT_MS) {
            val finished = kotlinx.coroutines.withTimeoutOrNull(PAGE_FINISHED_SOFT_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val terminal = AtomicBoolean(false)
                    webView.webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            if (newProgress >= 100 && terminal.compareAndSet(false, true) && cont.isActive) cont.resume(Unit)
                        }
                    }
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (terminal.compareAndSet(false, true) && cont.isActive) cont.resume(Unit)
                        }
                        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                            if (terminal.compareAndSet(false, true) && cont.isActive) {
                                cont.resumeWithException(IllegalStateException("توقف محرك العرض (Renderer) أثناء تجهيز المستند"))
                            }
                            return true // keep the app alive; the fallback engine takes over
                        }
                    }
                    cont.invokeOnCancellation {
                        terminal.set(true)
                        runCatching { webView.stopLoading() }
                    }
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/documents/",
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
            if (finished == null) android.util.Log.w("HalaqatiPdf", "onPageFinished not received; continuing with readiness probe")
            // Bounded readiness probe: each JS call has its own timeout so a stalled renderer
            // can never consume the whole 30s budget. After the budget, print as-is (v2.44 behavior).
            val deadline = System.currentTimeMillis() + READY_POLL_BUDGET_MS
            var attempt = 0
            while (attempt < READY_MAX_POLLS && System.currentTimeMillis() < deadline) {
                when (kotlinx.coroutines.withTimeoutOrNull(JS_EVAL_TIMEOUT_MS) { evaluateReady(webView) } ?: 0) {
                    1 -> break
                    -1 -> {
                        android.util.Log.w("HalaqatiPdf", "Layout preflight warning; printing anyway (v2.44 behavior)")
                        break
                    }
                    else -> delay(READY_POLL_MS)
                }
                attempt++
            }
            // Two frames so the final layout is committed before printing/capturing.
            delay(48)
        }
    }

    /** 1=ready, 0=waiting, -1=layout rejected by the document preflight. */
    private suspend fun evaluateReady(webView: WebView): Int = suspendCancellableCoroutine { cont ->
        val js = """
            (function(){
              try {
                var fonts = (!document.fonts || document.fonts.status === 'loaded');
                var images = Array.prototype.every.call(document.images || [], function(i){return i.complete;});
                var app = (window.__HALAQATI_READY__ === true || window.__HALAQATI_READY__ === 'READY');
                if (!(document.readyState === 'complete' && fonts && images && app)) return 'WAIT';
                if (window.__HALAQATI_LAYOUT_OK__ === false || document.documentElement.dataset.layoutOk === '0') return 'LAYOUT_ERROR';
                return 'READY';
              } catch(e) { return 'WAIT'; }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { raw ->
            if (cont.isActive) {
                when (raw?.replace("\"", "")) {
                    "READY" -> cont.resume(1)
                    "LAYOUT_ERROR" -> cont.resume(-1)
                    else -> cont.resume(0)
                }
            }
        }
    }

    private suspend fun writeAdapter(
        adapter: PrintDocumentAdapter,
        descriptor: ParcelFileDescriptor,
        orientation: DocumentOrientation,
    ): VectorPdfResult = suspendCancellableCoroutine { cont ->
        val media = when (orientation) {
            DocumentOrientation.PORTRAIT -> PrintAttributes.MediaSize.ISO_A4.asPortrait()
            DocumentOrientation.LANDSCAPE -> PrintAttributes.MediaSize.ISO_A4.asLandscape()
        }
        val attrs = PrintAttributes.Builder()
            .setMediaSize(media)
            .setResolution(PrintAttributes.Resolution("halaqati-vector", "Halaqati Vector", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        val done = AtomicBoolean(false)
        val handleRef = AtomicReference<HalaqatiWebViewPdfWriter.Handle?>(null)
        cont.invokeOnCancellation {
            if (done.compareAndSet(false, true)) {
                handleRef.get()?.cancel()
                runCatching { descriptor.close() }
                runCatching { adapter.onFinish() }
            }
        }
        val handle = HalaqatiWebViewPdfWriter.write(adapter, attrs, descriptor, object : HalaqatiWebViewPdfWriter.Callback {
            override fun onSuccess(info: PrintDocumentInfo?, pages: Array<PageRange>?) {
                if (!done.compareAndSet(false, true) || !cont.isActive) return
                val pageCount = info?.pageCount ?: -1
                cont.resume(VectorPdfResult(pageCount = pageCount, bytes = -1L))
            }

            override fun onFailure(message: String?) {
                if (!done.compareAndSet(false, true) || !cont.isActive) return
                cont.resumeWithException(IllegalStateException(message ?: "تعذر إنشاء PDF المتجهي"))
            }

            override fun onCancelled() {
                if (!done.compareAndSet(false, true) || !cont.isActive) return
                cont.resumeWithException(IllegalStateException("تم إلغاء إنشاء PDF"))
            }
        })
        handleRef.set(handle)
        if (!cont.isActive) handle.cancel()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
