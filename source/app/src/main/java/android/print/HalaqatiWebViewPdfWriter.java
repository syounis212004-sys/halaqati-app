package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges WebView's PrintDocumentAdapter directly to a caller-owned file descriptor.
 *
 * RC14 adds an explicit cancellation handle. A few OEM WebView builds can enter onLayout/onWrite
 * and never call the result callback. The caller can now cancel both signals, close the descriptor
 * and finish the adapter before falling back to the v2.44-compatible raster path.
 */
public final class HalaqatiWebViewPdfWriter {
    private HalaqatiWebViewPdfWriter() {}

    public interface Callback {
        void onSuccess(PrintDocumentInfo info, PageRange[] pages);
        void onFailure(String message);
        void onCancelled();
    }

    public static final class Handle {
        private final Runnable cancelAction;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private Handle(Runnable cancelAction) {
            this.cancelAction = cancelAction;
        }

        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                try { cancelAction.run(); } catch (Throwable ignored) {}
            }
        }
    }

    public static Handle write(PrintDocumentAdapter adapter,
                               PrintAttributes attributes,
                               ParcelFileDescriptor destination,
                               Callback callback) {
        if (adapter == null || attributes == null || destination == null || callback == null) {
            if (callback != null) callback.onFailure("Invalid PDF writer arguments");
            closeQuietly(destination);
            return new Handle(() -> {});
        }

        final CancellationSignal layoutSignal = new CancellationSignal();
        final CancellationSignal writeSignal = new CancellationSignal();
        final AtomicBoolean terminal = new AtomicBoolean(false);

        final Runnable finishAdapter = () -> {
            try { adapter.onFinish(); } catch (Throwable ignored) {}
        };
        final Runnable cancelAction = () -> {
            if (!terminal.compareAndSet(false, true)) return;
            try { layoutSignal.cancel(); } catch (Throwable ignored) {}
            try { writeSignal.cancel(); } catch (Throwable ignored) {}
            closeQuietly(destination);
            finishAdapter.run();
        };
        final Handle handle = new Handle(cancelAction);

        try {
            adapter.onStart();
            final Bundle metadata = new Bundle();
            metadata.putBoolean(PrintDocumentAdapter.EXTRA_PRINT_PREVIEW, false);
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
        return handle;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Throwable ignored) {}
    }
}
