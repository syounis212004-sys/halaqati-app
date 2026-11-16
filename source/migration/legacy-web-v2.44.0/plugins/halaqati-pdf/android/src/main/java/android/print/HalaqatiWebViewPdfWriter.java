package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges WebView's PrintDocumentAdapter directly to a caller-owned file descriptor.
 *
 * The Android SDK does not expose these callback constructors to ordinary application
 * packages. This tiny same-package compatibility shim is isolated here so the rest of the
 * Capacitor plugin remains on normal APIs. If an OEM rejects this bridge at runtime, the JS
 * layer falls back to the row-safe 300-DPI exporter instead of leaving a broken/0-byte file.
 * PDF layout/output itself is still produced by WebView's PrintDocumentAdapter / Chromium.
 */
public final class HalaqatiWebViewPdfWriter {
    private HalaqatiWebViewPdfWriter() {}

    public interface Callback {
        void onSuccess(PrintDocumentInfo info, PageRange[] pages);
        void onFailure(String message);
        void onCancelled();
    }

    public static void write(PrintDocumentAdapter adapter,
                             PrintAttributes attributes,
                             ParcelFileDescriptor destination,
                             Callback callback) {
        if (adapter == null || attributes == null || destination == null || callback == null) {
            if (callback != null) callback.onFailure("Invalid PDF writer arguments");
            closeQuietly(destination);
            return;
        }

        final CancellationSignal layoutSignal = new CancellationSignal();
        final CancellationSignal writeSignal = new CancellationSignal();
        final AtomicBoolean terminal = new AtomicBoolean(false);

        final Runnable finishAdapter = () -> {
            try { adapter.onFinish(); } catch (Throwable ignored) {}
        };

        try {
            adapter.onStart();
            final Bundle metadata = new Bundle();
            adapter.onLayout(null, attributes, layoutSignal,
                    new PrintDocumentAdapter.LayoutResultCallback() {
                        @Override
                        public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                            if (terminal.get()) return;
                            try {
                                adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, destination, writeSignal,
                                        new PrintDocumentAdapter.WriteResultCallback() {
                                            @Override
                                            public void onWriteFinished(PageRange[] pages) {
                                                if (!terminal.compareAndSet(false, true)) return;
                                                closeQuietly(destination);
                                                finishAdapter.run();
                                                callback.onSuccess(info, pages);
                                            }

                                            @Override
                                            public void onWriteFailed(CharSequence error) {
                                                if (!terminal.compareAndSet(false, true)) return;
                                                closeQuietly(destination);
                                                finishAdapter.run();
                                                callback.onFailure(error == null ? "WebView PDF write failed" : error.toString());
                                            }

                                            @Override
                                            public void onWriteCancelled() {
                                                if (!terminal.compareAndSet(false, true)) return;
                                                closeQuietly(destination);
                                                finishAdapter.run();
                                                callback.onCancelled();
                                            }
                                        });
                            } catch (Throwable error) {
                                if (!terminal.compareAndSet(false, true)) return;
                                closeQuietly(destination);
                                finishAdapter.run();
                                callback.onFailure(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                            }
                        }

                        @Override
                        public void onLayoutFailed(CharSequence error) {
                            if (!terminal.compareAndSet(false, true)) return;
                            closeQuietly(destination);
                            finishAdapter.run();
                            callback.onFailure(error == null ? "WebView PDF layout failed" : error.toString());
                        }

                        @Override
                        public void onLayoutCancelled() {
                            if (!terminal.compareAndSet(false, true)) return;
                            closeQuietly(destination);
                            finishAdapter.run();
                            callback.onCancelled();
                        }
                    }, metadata);
        } catch (Throwable error) {
            if (terminal.compareAndSet(false, true)) {
                closeQuietly(destination);
                finishAdapter.run();
                callback.onFailure(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            }
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Throwable ignored) {}
    }
}
