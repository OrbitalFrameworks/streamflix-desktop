package dev.streamflix.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MpvBootstrapTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("streamflix-mpv-bootstrap-test-");
        System.setProperty("streamflix.mpv.dir", root.toString());
        try {
            check(MpvBootstrap.runtimeDir().equals(root.toAbsolutePath().normalize()), "runtime override");
            check(!MpvBootstrap.managedRuntimeReady(), "empty runtime not ready");

            Files.write(MpvBootstrap.executablePath(), new byte[] {1});
            Files.writeString(root.resolve("VERSION"), MpvBootstrap.BUILD_ID);
            check(MpvBootstrap.managedRuntimeReady(), "matching runtime ready");
            Files.writeString(root.resolve("VERSION"), MpvBootstrap.PREVIOUS_VERIFIED_BUILD_ID);
            check(MpvBootstrap.managedRuntimeReady(), "previously verified runtime retained for offline updates");
            Files.writeString(root.resolve("VERSION"), MpvBootstrap.BUILD_ID);
            AtomicReference<Boolean> ready = new AtomicReference<>();
            MpvBootstrap.ensureReady(null, ready::set);
            check(Boolean.TRUE.equals(ready.get()), "ready callback allows playback");

            RuntimeException callbackFailure = new RuntimeException("callback failure");
            AtomicInteger successCallbacks = new AtomicInteger();
            AtomicInteger failureCallbacks = new AtomicInteger();
            try {
                MpvBootstrap.finishProvisioning(
                        CompletableFuture.completedFuture(MpvBootstrap.executablePath()),
                        () -> {}, null, result -> {
                            if (result) successCallbacks.incrementAndGet();
                            else failureCallbacks.incrementAndGet();
                            throw callbackFailure;
                        });
                throw new AssertionError("callback failure was swallowed");
            } catch (RuntimeException expected) {
                check(expected == callbackFailure, "success callback exception propagated");
            }
            check(successCallbacks.get() == 1, "success callback invoked once");
            check(failureCallbacks.get() == 0, "success callback exception did not report failure");

            Files.writeString(root.resolve("VERSION"), "old-build");
            check(!MpvBootstrap.managedRuntimeReady(), "old runtime rejected");

            Path badArchive = root.resolve("bad.7z");
            Files.writeString(badArchive, "not mpv");
            try {
                MpvBootstrap.installVerifiedArchive(badArchive, root.resolve("install"));
                throw new AssertionError("bad archive accepted");
            } catch (SecurityException expected) {
                // expected
            }

            check("github.com".equalsIgnoreCase(MpvBootstrap.DOWNLOAD_URI.getHost()), "upstream host");
            check(MpvBootstrap.ARCHIVE_SHA256.matches("[0-9A-F]{64}"), "sha format");
            System.out.println("MpvBootstrapTest OK");
        } finally {
            System.clearProperty("streamflix.mpv.dir");
            try (var walk = Files.walk(root)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
