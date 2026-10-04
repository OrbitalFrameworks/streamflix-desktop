package dev.streamflix.desktop;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/** One bounded retry for transport/startup failures that are safe to repeat. */
final class PlaybackRecovery {
    static final int MAX_ATTEMPTS = 2;
    static final long MAX_RETRY_WINDOW_MILLIS = TimeUnit.SECONDS.toMillis(60);

    private PlaybackRecovery() {}

    @FunctionalInterface
    interface Attempt<T> {
        T run() throws Exception;
    }

    static <T> T run(Attempt<T> attempt) throws Exception {
        long startedAt = System.nanoTime();
        for (int number = 1; ; number++) {
            try {
                return attempt.run();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (CancellationException ex) {
                throw ex;
            } catch (Exception ex) {
                if (number >= MAX_ATTEMPTS
                        || !isTransient(ex)
                        || elapsedMillis(startedAt) >= MAX_RETRY_WINDOW_MILLIS) {
                    throw ex;
                }
            }
        }
    }

    static boolean isTransient(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException || current instanceof CancellationException) return false;
            if (current instanceof UnsupportedOperationException || current instanceof IllegalArgumentException) return false;
            if (current instanceof IOException) return !isPermanentMessage(current.getMessage());
            if (current instanceof IllegalStateException) return !isPermanentMessage(current.getMessage());
        }
        return false;
    }

    private static boolean isPermanentMessage(String message) {
        String value = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return value.contains("inválid") || value.contains("invalid")
                || value.contains("no disponible") || value.contains("not available")
                || value.contains("no portado") || value.contains("unsupported")
                || value.contains("api key") || value.contains("access denied")
                || value.contains("rejected") || value.contains("fue cerrado");
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
