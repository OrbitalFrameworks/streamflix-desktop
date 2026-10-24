package dev.streamflix.desktop;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class HttpRegressionTest {
    public static void main(String[] args) {
        testThreadInterruptionPriorToRequest();
        testThreadInterruptionGetBytes();
        testThreadInterruptionGetBytesLegacy();
        testAsyncCancellation();
        testConfigurableTimeouts();
        testUrlEncoding();
        System.out.println("HttpRegressionTest OK");
    }

    private static void testThreadInterruptionPriorToRequest() {
        Http http = new Http();
        AtomicBoolean threwExpected = new AtomicBoolean(false);
        AtomicBoolean wasInterrupted = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                http.get("https://127.0.0.1:9999/unreachable");
            } catch (InterruptedException ex) {
                threwExpected.set(true);
                wasInterrupted.set(Thread.currentThread().isInterrupted());
            } catch (Exception ex) {
                throw new AssertionError("Expected InterruptedException, got: " + ex);
            }
        });

        worker.start();
        join(worker);

        require(threwExpected.get(), "http.get must throw InterruptedException on interrupted thread");
        require(wasInterrupted.get(), "thread interrupt flag must be preserved");
    }

    private static void testThreadInterruptionGetBytes() {
        Http http = new Http();
        AtomicBoolean threwExpected = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                http.getBytes("https://127.0.0.1:9999/unreachable");
            } catch (InterruptedException ex) {
                threwExpected.set(true);
            } catch (Exception ex) {
                throw new AssertionError("Expected InterruptedException, got: " + ex);
            }
        });

        worker.start();
        join(worker);

        require(threwExpected.get(), "http.getBytes must throw InterruptedException on interrupted thread");
    }

    private static void testThreadInterruptionGetBytesLegacy() {
        Http http = new Http();
        AtomicBoolean threwExpected = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                http.getBytesLegacy("https://127.0.0.1:9999/unreachable", Map.of());
            } catch (IOException ex) {
                if (ex.getCause() instanceof InterruptedException) {
                    threwExpected.set(true);
                }
            } catch (Exception ex) {
                throw new AssertionError("Expected IOException with InterruptedException cause, got: " + ex);
            }
        });

        worker.start();
        join(worker);

        require(threwExpected.get(), "http.getBytesLegacy must throw IOException wrapping InterruptedException");
    }

    private static void testAsyncCancellation() {
        Http http = new Http();
        CompletableFuture<String> future = http.getAsync("https://127.0.0.1:9999/unreachable");
        require(future != null, "getAsync returns non-null future");
        boolean cancelled = future.cancel(true);
        require(cancelled, "future.cancel(true) succeeds");
        require(future.isCancelled(), "future is marked cancelled");
        require(future.isDone(), "cancelled future is done");
    }

    private static void testConfigurableTimeouts() {
        Http http = new Http(Duration.ofSeconds(3), Duration.ofSeconds(7));
        require(http.requestTimeout().toSeconds() == 7, "requestTimeout matches configured value");
    }

    private static void testUrlEncoding() {
        String encoded = Http.encode("Dune: Part Two & 100%");
        require(encoded.contains("Dune") && encoded.contains("%26") && encoded.contains("%25"),
                "encode properly encodes reserved characters");
    }

    private static void join(Thread t) {
        try {
            t.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Join interrupted", e);
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }
}
