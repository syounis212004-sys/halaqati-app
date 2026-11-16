package com.mohammedsamir.halaqati.pdf;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.print.HalaqatiWebViewPdfWriter;
import android.util.Base64;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Halaqati v2.40.8 PDF bridge.
 *
 * Three deliberately separate paths:
 *  1) saveHtmlPdf(): preferred direct-save path. A dedicated WebView lays out the exact report
 *     with Chromium/CSS/Cairo, then its PrintDocumentAdapter writes the PDF to an app-owned
 *     temporary file. The completed file is copied atomically to Downloads/Halaqati. PrintManager
 *     and the OEM Print Spooler are never involved, so Redmi/MIUI 0-byte Save-as-PDF bugs are
 *     bypassed while preserving Chromium's real vector text, Arabic shaping and pagination.
 *  2) saveBase64Pdf(): compatibility path for already-generated PDFs.
 *  3) exportPdf(): physical/system printing only, still isolated in its own retained WebView.
 */
@CapacitorPlugin(name = "HalaqatiPdf")
public class HalaqatiPdfPlugin extends Plugin {
    private static final long PAGE_READY_TIMEOUT_MS = 30_000L;
    private static final long PRINT_JOB_POLL_MS = 350L;
    private static final long PRINT_JOB_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final long DIRECT_SAVE_TIMEOUT_MS = 120_000L;
    private static final int MAX_PDF_BYTES = 32 * 1024 * 1024;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebView retainedOwnedPrintView;
    private Runnable pageReadyTimeout;
    private Runnable directSaveTimeout;
    private boolean activeDirectSave;

    private PrintJob activePrintJob;
    private Runnable activePrintPoll;
    private PluginCall activePrintCall;
    private long activePrintStartedAt;


    /**
     * v2.40.8 preferred direct vector save. Chromium itself generates the PDF stream through
     * WebView's PrintDocumentAdapter, but no PrintManager/Print Spooler UI is used.
     */
    @PluginMethod
    public void saveHtmlPdf(PluginCall call) {
        final String requestedName = call.getString("filename", "halaqati-report.pdf");
        final String orientation = call.getString("orientation", "landscape");
        final String subdirectory = safeSubdirectory(call.getString("subdirectory", ""));
        final String html = call.getString("html", "");
        final Activity activity = getActivity();

        if (activity == null) {
            call.reject("Android activity is unavailable");
            return;
        }
        if (activeDirectSave || hasActivePrintJob()) {
            call.reject("هناك عملية PDF أو طباعة ما زالت جارية. انتظر اكتمالها أولاً.");
            return;
        }
        if (html == null || html.trim().isEmpty()) {
            call.reject("محتوى التقرير للحفظ فارغ");
            return;
        }
        if (html.length() > 12_000_000) {
            call.reject("محتوى التقرير كبير جدًا للحفظ المباشر");
            return;
        }

        final String filename = safePdfName(requestedName);
        activeDirectSave = true;
        activity.runOnUiThread(() -> prepareOwnedSaveWebView(activity, call, html, filename, orientation, subdirectory));
    }

    private void prepareOwnedSaveWebView(Activity activity, PluginCall call, String html, String filename, String orientation, String subdirectory) {
        releaseOwnedPrintView();
        final WebView webView = createOwnedPdfWebView(activity);
        retainedOwnedPrintView = webView;
        final boolean[] launched = new boolean[]{false};

        pageReadyTimeout = () -> {
            if (launched[0]) return;
            launched[0] = true;
            activeDirectSave = false;
            releaseOwnedPrintView();
            call.reject("انتهت مهلة تجهيز التقرير للحفظ. حاول مرة أخرى.");
        };
        mainHandler.postDelayed(pageReadyTimeout, PAGE_READY_TIMEOUT_MS);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (launched[0]) return;
                waitForOwnedSaveDocumentReady(call, webView, filename, orientation, subdirectory, launched, 0);
            }
        });
        webView.loadDataWithBaseURL("https://halaqati.local/", html, "text/html", "UTF-8", null);
    }

    private void waitForOwnedSaveDocumentReady(PluginCall call, WebView webView, String filename,
                                               String orientation, String subdirectory, boolean[] launched, int attempt) {
        if (launched[0] || retainedOwnedPrintView != webView) return;
        final String readyJs =
                "(function(){try{" +
                "var fontsReady=(!document.fonts||document.fonts.status==='loaded');" +
                "var imagesReady=Array.prototype.every.call(document.images,function(i){return i.complete;});" +
                "return (!!document.body&&document.readyState==='complete'&&fontsReady&&imagesReady)?'READY':'WAIT';" +
                "}catch(e){return 'WAIT';}})();";
        try {
            webView.evaluateJavascript(readyJs, value -> {
                if (launched[0] || retainedOwnedPrintView != webView) return;
                final String state = value == null ? "" : value.replace("\"", "");
                if ("READY".equals(state) || attempt >= 120) {
                    launched[0] = true;
                    clearPageReadyTimeout();
                    writeOwnedWebViewPdf(call, webView, filename, orientation, subdirectory);
                    return;
                }
                mainHandler.postDelayed(() -> waitForOwnedSaveDocumentReady(call, webView, filename, orientation, subdirectory, launched, attempt + 1), 100L);
            });
        } catch (Throwable error) {
            mainHandler.postDelayed(() -> {
                if (launched[0] || retainedOwnedPrintView != webView) return;
                launched[0] = true;
                clearPageReadyTimeout();
                writeOwnedWebViewPdf(call, webView, filename, orientation, subdirectory);
            }, 500L);
        }
    }

    private void writeOwnedWebViewPdf(PluginCall call, WebView webView, String filename, String orientation, String subdirectory) {
        final Context context = getContext();
        if (context == null) {
            activeDirectSave = false;
            releaseOwnedPrintView();
            call.reject("Android context is unavailable");
            return;
        }

        PrintAttributes.MediaSize mediaSize = PrintAttributes.MediaSize.ISO_A4;
        mediaSize = "portrait".equalsIgnoreCase(orientation) ? mediaSize.asPortrait() : mediaSize.asLandscape();
        final PrintAttributes attributes = new PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setResolution(new PrintAttributes.Resolution("halaqati-vector", "Halaqati Vector PDF", 600, 600))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build();

        final File tempDir = new File(context.getCacheDir(), "halaqati-pdf");
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            activeDirectSave = false;
            releaseOwnedPrintView();
            call.reject("تعذر تجهيز مساحة PDF المؤقتة");
            return;
        }
        final File temp = new File(tempDir, "vector-" + System.nanoTime() + ".pdf");
        final boolean[] finished = new boolean[]{false};

        try {
            final ParcelFileDescriptor destination = ParcelFileDescriptor.open(temp,
                    ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
            final PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(filename);

            directSaveTimeout = () -> {
                if (finished[0]) return;
                finished[0] = true;
                activeDirectSave = false;
                // Force-close the caller-owned descriptor on timeout so a wedged OEM WebView
                // cannot keep writing to an unlinked temp inode or hold the report WebView alive.
                try { destination.close(); } catch (Throwable ignored) {}
                try { adapter.onFinish(); } catch (Throwable ignored) {}
                try { temp.delete(); } catch (Throwable ignored) {}
                releaseOwnedPrintView();
                call.reject("انتهت مهلة إنشاء PDF المتجهي. لم يتم ترك ملف ناقص.");
            };
            mainHandler.postDelayed(directSaveTimeout, DIRECT_SAVE_TIMEOUT_MS);

            HalaqatiWebViewPdfWriter.write(adapter, attributes, destination, new HalaqatiWebViewPdfWriter.Callback() {
                @Override
                public void onSuccess(android.print.PrintDocumentInfo info, android.print.PageRange[] pages) {
                    if (finished[0]) return;
                    finished[0] = true;
                    clearDirectSaveTimeout();
                    new Thread(() -> {
                        try {
                            final int pageCount = info == null ? -1 : info.getPageCount();
                            final JSObject out = persistCompletedPdfFile(context, temp, filename, pageCount, subdirectory);
                            mainHandler.post(() -> {
                                activeDirectSave = false;
                                releaseOwnedPrintView();
                                call.resolve(out);
                            });
                        } catch (Throwable error) {
                            try { temp.delete(); } catch (Throwable ignored) {}
                            mainHandler.post(() -> {
                                activeDirectSave = false;
                                releaseOwnedPrintView();
                                call.reject("تعذر حفظ PDF المتجهي: " + safeMessage(error));
                            });
                        }
                    }, "halaqati-chromium-pdf-persist").start();
                }

                @Override
                public void onFailure(String message) {
                    if (finished[0]) return;
                    finished[0] = true;
                    clearDirectSaveTimeout();
                    activeDirectSave = false;
                    try { temp.delete(); } catch (Throwable ignored) {}
                    releaseOwnedPrintView();
                    call.reject("تعذر إنشاء PDF المتجهي: " + message);
                }

                @Override
                public void onCancelled() {
                    if (finished[0]) return;
                    finished[0] = true;
                    clearDirectSaveTimeout();
                    activeDirectSave = false;
                    try { temp.delete(); } catch (Throwable ignored) {}
                    releaseOwnedPrintView();
                    call.reject("تم إلغاء إنشاء PDF");
                }
            });
        } catch (Throwable error) {
            clearDirectSaveTimeout();
            activeDirectSave = false;
            try { temp.delete(); } catch (Throwable ignored) {}
            releaseOwnedPrintView();
            call.reject("تعذر بدء إنشاء PDF المتجهي: " + safeMessage(error));
        }
    }

    private JSObject persistCompletedPdfFile(Context context, File source, String filename, int pageCount, String subdirectory) throws Exception {
        if (!source.isFile() || source.length() < 512L || source.length() > MAX_PDF_BYTES) {
            throw new IllegalStateException("ملف Chromium الناتج غير صالح أو فارغ");
        }
        try (FileInputStream check = new FileInputStream(source)) {
            final byte[] sig = new byte[4];
            if (check.read(sig) != 4 || sig[0] != '%' || sig[1] != 'P' || sig[2] != 'D' || sig[3] != 'F') {
                throw new IllegalStateException("Chromium لم ينتج ملف PDF صالحًا");
            }
        }

        Uri uri = null;
        try {
            final JSObject out = new JSObject();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final ContentResolver resolver = context.getContentResolver();
                final ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                final String relativeDir = Environment.DIRECTORY_DOWNLOADS + "/Halaqati" + (subdirectory.isEmpty() ? "" : "/" + subdirectory);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("تعذر إنشاء ملف داخل التنزيلات");
                try (InputStream input = new FileInputStream(source); OutputStream output = resolver.openOutputStream(uri, "w")) {
                    if (output == null) throw new IllegalStateException("تعذر فتح ملف PDF للكتابة");
                    copyStream(input, output);
                    output.flush();
                }
                final long written = verifyStoredPdf(resolver, uri);
                if (written <= 0) {
                    resolver.delete(uri, null, null);
                    uri = null;
                    throw new IllegalStateException("فشل التحقق من الملف بعد الكتابة؛ تم منع إنشاء ملف 0 بايت");
                }
                final ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, done, null, null);
                out.put("saved", true);
                out.put("filename", filename);
                out.put("bytes", written);
                out.put("uri", uri.toString());
                out.put("directory", "Downloads/Halaqati" + (subdirectory.isEmpty() ? "" : "/" + subdirectory));
                out.put("pages", pageCount);
                out.put("engine", "chromium-webview-direct-vector-no-print-spooler");
            } else {
                final File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Halaqati");
                final File dir = subdirectory.isEmpty() ? baseDir : new File(baseDir, subdirectory);
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد التنزيلات");
                final File target = uniqueLegacyFile(dir, filename);
                try (InputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(target, false)) {
                    copyStream(input, output);
                    output.flush();
                    output.getFD().sync();
                }
                if (!target.isFile() || target.length() < 512L) {
                    target.delete();
                    throw new IllegalStateException("فشل التحقق من الملف بعد الكتابة");
                }
                out.put("saved", true);
                out.put("filename", target.getName());
                out.put("bytes", target.length());
                out.put("uri", Uri.fromFile(target).toString());
                out.put("directory", target.getParent());
                out.put("pages", pageCount);
                out.put("engine", "chromium-webview-direct-vector-file-no-print-spooler");
            }
            return out;
        } catch (Exception error) {
            if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { context.getContentResolver().delete(uri, null, null); } catch (Throwable ignored) {}
            }
            throw error;
        } finally {
            try { source.delete(); } catch (Throwable ignored) {}
        }
    }

    private void copyStream(InputStream input, OutputStream output) throws Exception {
        final byte[] buffer = new byte[64 * 1024];
        int read;
        long total = 0L;
        while ((read = input.read(buffer)) != -1) {
            if (read <= 0) continue;
            total += read;
            if (total > MAX_PDF_BYTES) throw new IllegalStateException("ملف PDF أكبر من الحد الآمن");
            output.write(buffer, 0, read);
        }
    }

    private WebView createOwnedPdfWebView(Activity activity) {
        final WebView webView = new WebView(activity);
        final WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setLoadsImagesAutomatically(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        webView.setBackgroundColor(0xFFFFFFFF);
        return webView;
    }

    /**
     * Direct save path. The JS vector renderer creates the PDF bytes; Android only persists
     * them to public Downloads and verifies that a non-empty file was actually written.
     */
    @PluginMethod
    public void saveBase64Pdf(PluginCall call) {
        final String raw = call.getString("base64", "");
        final String filename = safePdfName(call.getString("filename", "halaqati-report.pdf"));
        final String subdirectory = safeSubdirectory(call.getString("subdirectory", ""));
        final Context context = getContext();
        if (context == null) {
            call.reject("Android context is unavailable");
            return;
        }
        if (raw == null || raw.trim().isEmpty()) {
            call.reject("بيانات PDF فارغة؛ لم يتم إنشاء أي ملف");
            return;
        }

        final String clean = raw.startsWith("data:") && raw.contains(",")
                ? raw.substring(raw.indexOf(',') + 1)
                : raw;

        new Thread(() -> {
            Uri uri = null;
            try {
                final byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                if (bytes.length < 512) {
                    throw new IllegalStateException("ملف PDF الناتج صغير جدًا أو غير صالح");
                }
                if (bytes.length > MAX_PDF_BYTES) {
                    throw new IllegalStateException("ملف PDF أكبر من الحد الآمن للحفظ المباشر");
                }
                if (!(bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F')) {
                    throw new IllegalStateException("بيانات الملف ليست PDF صالحًا");
                }

                final JSObject out = new JSObject();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    final ContentResolver resolver = context.getContentResolver();
                    final ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    final String relativeDir = Environment.DIRECTORY_DOWNLOADS + "/Halaqati" + (subdirectory.isEmpty() ? "" : "/" + subdirectory);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir);
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);

                    uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("تعذر إنشاء ملف داخل التنزيلات");

                    try (OutputStream stream = resolver.openOutputStream(uri, "w")) {
                        if (stream == null) throw new IllegalStateException("تعذر فتح ملف PDF للكتابة");
                        stream.write(bytes);
                        stream.flush();
                    }

                    final long written = verifyStoredPdf(resolver, uri);
                    if (written <= 0) {
                        resolver.delete(uri, null, null);
                        throw new IllegalStateException("فشل التحقق من الملف بعد الكتابة؛ تم منع إنشاء ملف 0 بايت");
                    }

                    final ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    resolver.update(uri, done, null, null);

                    out.put("saved", true);
                    out.put("filename", filename);
                    out.put("bytes", written);
                    out.put("uri", uri.toString());
                    out.put("directory", "Downloads/Halaqati" + (subdirectory.isEmpty() ? "" : "/" + subdirectory));
                    out.put("engine", "direct-mediastore-no-print-spooler");
                } else {
                    final File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Halaqati");
                    final File dir = subdirectory.isEmpty() ? baseDir : new File(baseDir, subdirectory);
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد التنزيلات");
                    final File file = uniqueLegacyFile(dir, filename);
                    try (FileOutputStream stream = new FileOutputStream(file, false)) {
                        stream.write(bytes);
                        stream.flush();
                        stream.getFD().sync();
                    }
                    if (!file.isFile() || file.length() <= 0) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        throw new IllegalStateException("فشل التحقق من الملف بعد الكتابة؛ تم منع إنشاء ملف 0 بايت");
                    }
                    out.put("saved", true);
                    out.put("filename", file.getName());
                    out.put("bytes", file.length());
                    out.put("uri", Uri.fromFile(file).toString());
                    out.put("directory", file.getParent());
                    out.put("engine", "direct-file-no-print-spooler");
                }
                call.resolve(out);
            } catch (Throwable error) {
                if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try { context.getContentResolver().delete(uri, null, null); } catch (Throwable ignored) {}
                }
                call.reject("تعذر حفظ PDF مباشرة: " + safeMessage(error));
            }
        }, "halaqati-pdf-save").start();
    }

    /** System/physical printing only. Always prints a dedicated HTML WebView. */
    @PluginMethod
    public void exportPdf(PluginCall call) {
        final String requestedName = call.getString("filename", "halaqati-report.pdf");
        final String orientation = call.getString("orientation", "landscape");
        final String html = call.getString("html", "");
        final Activity activity = getActivity();

        if (activity == null) {
            call.reject("Android activity is unavailable");
            return;
        }
        if (activeDirectSave || hasActivePrintJob()) {
            call.reject("هناك عملية PDF أو طباعة ما زالت جارية. أنهها أولاً ثم أعد المحاولة.");
            return;
        }
        if (html == null || html.trim().isEmpty()) {
            call.reject("محتوى التقرير للطباعة فارغ");
            return;
        }
        if (html.length() > 12_000_000) {
            call.reject("محتوى التقرير كبير جدًا للطباعة");
            return;
        }

        final String filename = safePdfName(requestedName);
        activity.runOnUiThread(() -> prepareOwnedPrintWebView(activity, call, html, filename, orientation));
    }

    private void prepareOwnedPrintWebView(Activity activity, PluginCall call, String html, String filename, String orientation) {
        releaseOwnedPrintView();

        // Dedicated WebView: never mutate/reload the Capacitor app WebView for printing.
        final WebView webView = createOwnedPdfWebView(activity);
        retainedOwnedPrintView = webView;

        final boolean[] launched = new boolean[]{false};
        pageReadyTimeout = () -> {
            if (launched[0]) return;
            releaseOwnedPrintView();
            call.reject("انتهت مهلة تجهيز التقرير للطباعة. حاول مرة أخرى.");
        };
        mainHandler.postDelayed(pageReadyTimeout, PAGE_READY_TIMEOUT_MS);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (launched[0]) return;
                waitForOwnedDocumentReady(activity, call, webView, filename, orientation, launched, 0);
            }
        });

        webView.loadDataWithBaseURL(
                "https://halaqati.local/",
                html,
                "text/html",
                "UTF-8",
                null
        );
    }

    private void waitForOwnedDocumentReady(Activity activity, PluginCall call, WebView webView,
                                           String filename, String orientation, boolean[] launched,
                                           int attempt) {
        if (launched[0] || retainedOwnedPrintView != webView) return;

        final String readyJs =
                "(function(){try{" +
                "var fontsReady=(!document.fonts||document.fonts.status==='loaded');" +
                "var imagesReady=Array.prototype.every.call(document.images,function(i){return i.complete;});" +
                "return (!!document.body&&document.readyState==='complete'&&fontsReady&&imagesReady)?'READY':'WAIT';" +
                "}catch(e){return 'WAIT';}})();";

        try {
            webView.evaluateJavascript(readyJs, value -> {
                if (launched[0] || retainedOwnedPrintView != webView) return;
                final String state = value == null ? "" : value.replace("\"", "");
                if ("READY".equals(state) || attempt >= 120) {
                    launched[0] = true;
                    clearPageReadyTimeout();
                    openSystemPrintDialog(activity, call, webView, filename, orientation);
                    return;
                }
                mainHandler.postDelayed(
                        () -> waitForOwnedDocumentReady(activity, call, webView, filename, orientation,
                                launched, attempt + 1),
                        100L
                );
            });
        } catch (Throwable error) {
            mainHandler.postDelayed(() -> {
                if (launched[0] || retainedOwnedPrintView != webView) return;
                launched[0] = true;
                clearPageReadyTimeout();
                openSystemPrintDialog(activity, call, webView, filename, orientation);
            }, 500L);
        }
    }

    private void openSystemPrintDialog(Activity activity, PluginCall call, WebView webView,
                                       String filename, String orientation) {
        final PrintManager printManager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) {
            releaseOwnedPrintView();
            call.reject("خدمة الطباعة غير متاحة على هذا الجهاز");
            return;
        }

        PrintAttributes.MediaSize mediaSize = PrintAttributes.MediaSize.ISO_A4;
        mediaSize = "portrait".equalsIgnoreCase(orientation)
                ? mediaSize.asPortrait()
                : mediaSize.asLandscape();

        final PrintAttributes attributes = new PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build();

        try {
            final PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(filename);
            final PrintJob job = printManager.print(filename, adapter, attributes);
            if (job == null) {
                releaseOwnedPrintView();
                call.reject("تعذر بدء مهمة الطباعة على هذا الجهاز");
                return;
            }

            activePrintJob = job;
            activePrintCall = call;
            activePrintStartedAt = System.currentTimeMillis();
            pollActivePrintJob(filename);
        } catch (Throwable error) {
            releaseOwnedPrintView();
            clearActivePrintState();
            call.reject("تعذر فتح شاشة الطباعة: " + safeMessage(error));
        }
    }

    private void pollActivePrintJob(String filename) {
        if (activePrintPoll != null) mainHandler.removeCallbacks(activePrintPoll);
        activePrintPoll = () -> {
            final PrintJob job = activePrintJob;
            final PluginCall call = activePrintCall;
            if (job == null || call == null) return;

            try {
                if (job.isCompleted()) {
                    final JSObject out = new JSObject();
                    out.put("opened", true);
                    out.put("completed", true);
                    out.put("cancelled", false);
                    out.put("printDialog", true);
                    out.put("filename", filename);
                    out.put("engine", "chromium-dedicated-webview-public-print-api");
                    finishActivePrint();
                    call.resolve(out);
                    return;
                }
                if (job.isCancelled()) {
                    final JSObject out = new JSObject();
                    out.put("opened", true);
                    out.put("completed", false);
                    out.put("cancelled", true);
                    out.put("printDialog", true);
                    out.put("filename", filename);
                    finishActivePrint();
                    call.resolve(out);
                    return;
                }
                if (job.isFailed()) {
                    finishActivePrint();
                    call.reject("فشلت مهمة الطباعة في خدمة الطباعة على الجهاز");
                    return;
                }
            } catch (Throwable error) {
                finishActivePrint();
                call.reject("تعذر متابعة حالة الطباعة: " + safeMessage(error));
                return;
            }

            if (System.currentTimeMillis() - activePrintStartedAt >= PRINT_JOB_TIMEOUT_MS) {
                final JSObject out = new JSObject();
                out.put("opened", true);
                out.put("completed", false);
                out.put("cancelled", false);
                out.put("timedOut", true);
                out.put("printDialog", true);
                out.put("filename", filename);
                finishActivePrint();
                call.resolve(out);
                return;
            }

            mainHandler.postDelayed(activePrintPoll, PRINT_JOB_POLL_MS);
        };
        mainHandler.postDelayed(activePrintPoll, PRINT_JOB_POLL_MS);
    }

    private boolean hasActivePrintJob() {
        final PrintJob job = activePrintJob;
        if (job == null) return false;
        try {
            if (job.isCompleted() || job.isCancelled() || job.isFailed()) {
                finishActivePrint();
                return false;
            }
        } catch (Throwable ignored) {
            // Keep the operation protected when OEM state querying is transiently unavailable.
        }
        return activePrintJob != null;
    }

    private void finishActivePrint() {
        if (activePrintPoll != null) {
            mainHandler.removeCallbacks(activePrintPoll);
            activePrintPoll = null;
        }
        clearActivePrintState();
        releaseOwnedPrintView();
    }

    private void clearActivePrintState() {
        activePrintJob = null;
        activePrintCall = null;
        activePrintStartedAt = 0L;
    }

    private void clearDirectSaveTimeout() {
        if (directSaveTimeout != null) {
            mainHandler.removeCallbacks(directSaveTimeout);
            directSaveTimeout = null;
        }
    }

    private void clearPageReadyTimeout() {
        if (pageReadyTimeout != null) {
            mainHandler.removeCallbacks(pageReadyTimeout);
            pageReadyTimeout = null;
        }
    }

    private void releaseOwnedPrintView() {
        clearPageReadyTimeout();
        final WebView webView = retainedOwnedPrintView;
        retainedOwnedPrintView = null;
        if (webView == null) return;
        try { webView.stopLoading(); } catch (Throwable ignored) {}
        try { webView.loadUrl("about:blank"); } catch (Throwable ignored) {}
        try { webView.destroy(); } catch (Throwable ignored) {}
    }

    private long verifyStoredPdf(ContentResolver resolver, Uri uri) {
        // Read back through the same ContentResolver instead of trusting only the MediaStore
        // SIZE column. Some OEMs update that metadata lazily; a byte-for-byte readback gives
        // us a real guarantee that the user will not be left with a 0-byte placeholder.
        long queried = querySize(resolver, uri);
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) return 0L;
            final byte[] buffer = new byte[16 * 1024];
            long total = 0L;
            int read;
            boolean signatureChecked = false;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) continue;
                if (!signatureChecked) {
                    if (read < 4 || buffer[0] != '%' || buffer[1] != 'P' || buffer[2] != 'D' || buffer[3] != 'F') {
                        return 0L;
                    }
                    signatureChecked = true;
                }
                total += read;
                if (total > MAX_PDF_BYTES) return 0L;
            }
            if (!signatureChecked || total < 512L) return 0L;
            return Math.max(total, queried);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private long querySize(ContentResolver resolver, Uri uri) {
        try (android.database.Cursor cursor = resolver.query(uri,
                new String[]{MediaStore.MediaColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(0);
        } catch (Throwable ignored) {}
        return 0L;
    }

    private File uniqueLegacyFile(File dir, String filename) {
        File file = new File(dir, filename);
        if (!file.exists()) return file;
        final String base = filename.toLowerCase().endsWith(".pdf")
                ? filename.substring(0, filename.length() - 4)
                : filename;
        for (int i = 2; i < 1000; i++) {
            file = new File(dir, base + " (" + i + ").pdf");
            if (!file.exists()) return file;
        }
        return new File(dir, base + "-" + System.currentTimeMillis() + ".pdf");
    }

    private String safeMessage(Throwable error) {
        if (error == null) return "unknown";
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String safeSubdirectory(String value) {
        String dir = value == null ? "" : value.trim();
        dir = dir.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        dir = dir.replaceAll("^\\.+|\\.+$", "");
        if (dir.length() > 48) dir = dir.substring(0, 48);
        return dir;
    }

    private String safePdfName(String value) {
        String name = value == null ? "halaqati-report.pdf" : value.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";
        if (name.length() > 120) name = name.substring(0, 116) + ".pdf";
        return name.isEmpty() ? "halaqati-report.pdf" : name;
    }

    @Override
    protected void handleOnDestroy() {
        if (activePrintPoll != null) {
            mainHandler.removeCallbacks(activePrintPoll);
            activePrintPoll = null;
        }
        clearPageReadyTimeout();
        clearDirectSaveTimeout();
        activeDirectSave = false;
        // If Android is destroying the Activity during print UI, avoid explicitly tearing
        // down the source before the spooler has had a chance to finish its current write.
        if (activePrintJob == null) releaseOwnedPrintView();
        clearActivePrintState();
        super.handleOnDestroy();
    }
}
