package dev.streamflix.desktop;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class MpvPlayer {
    private static final MpvPlayer PLAYER = new MpvPlayer(MpvPlayer::requireMpv,
            ProcessBuilder::start, 1500, 1000);

    @FunctionalInterface
    interface ProcessLauncher {
        Process start(ProcessBuilder builder) throws IOException;
    }

    private final Supplier<String> executable;
    private final ProcessLauncher launcher;
    private final long startupMillis;
    private final long stopMillis;
    private Process active;
    private long requestSequence;
    private long latestRequest;
    private volatile boolean closed;

    // Isolated instances let fixtures supply fake processes without finding or running mpv.
    MpvPlayer(Supplier<String> executable, ProcessLauncher launcher, long startupMillis, long stopMillis) {
        if (startupMillis <= 0 || stopMillis <= 0) throw new IllegalArgumentException("Invalid timeout");
        this.executable = executable;
        this.launcher = launcher;
        this.startupMillis = startupMillis;
        this.stopMillis = stopMillis;
    }

    static boolean isAvailable() { return locate() != null; }

    static long beginRequest() { return PLAYER.newRequest(); }

    static void play(Models.Video video, String title) throws Exception {
        long requestId = PLAYER.newRequest();
        PLAYER.start(video, title, requestId);
    }

    static void play(Models.Video video, String title, long requestId) throws Exception {
        PLAYER.start(video, title, requestId);
    }

    static void playEmbedded(Models.Video video, String title, long requestId,
                             long windowId, String ipcPath) throws Exception {
        PLAYER.startEmbedded(video, title, requestId, windowId, ipcPath, null);
    }

    static void playEmbedded(Models.Video video, String title, long requestId,
                             long windowId, String ipcPath, String hlsBitrateOverride) throws Exception {
        PLAYER.startEmbedded(video, title, requestId, windowId, ipcPath, hlsBitrateOverride);
    }

    static void stopCurrent() { PLAYER.stop(); }

    static boolean isCurrentAlive() { return PLAYER.currentAlive(); }

    static void shutdown() { PLAYER.close(); }
    static boolean isShutdown() { return PLAYER.closed; }

    synchronized long newRequest() {
        checkOpen();
        latestRequest = ++requestSequence;
        return latestRequest;
    }

    synchronized void start(Models.Video video, String title) throws Exception {
        long requestId = newRequest();
        start(video, title, requestId);
    }

    synchronized void start(Models.Video video, String title, long requestId) throws Exception {
        startCommand(video, requestId, playbackCommand(executable.get(), video, title), startupMillis);
    }

    synchronized void startEmbedded(Models.Video video, String title, long requestId,
                                    long windowId, String ipcPath, String hlsBitrateOverride) throws Exception {
        if (windowId == 0L) throw new IllegalArgumentException("Ventana de video no disponible.");
        if (ipcPath == null || ipcPath.isBlank()) throw new IllegalArgumentException("Canal IPC no disponible.");
        // IPC connection is the real startup proof for the embedded player. A long
        // process-survival guard here only adds latency before we can even connect.
        long embeddedGuardMillis = Math.min(startupMillis, 300L);
        startCommand(video, requestId,
                embeddedPlaybackCommand(executable.get(), video, title, windowId, ipcPath, hlsBitrateOverride),
                embeddedGuardMillis);
    }

    private void startCommand(Models.Video video, long requestId, List<String> command,
                              long guardMillis) throws Exception {
        checkRequest(requestId);
        if (video == null || video.source() == null || video.source().isBlank()) {
            throw new IllegalArgumentException("El servidor no devolvi\u00f3 un video reproducible.");
        }
        stop();
        checkRequest(requestId);
        active = launch(command);
        try {
            // Extraction alone is not success. A surviving process counts as started;
            // an early clean exit (e.g. the user closed mpv) must not trigger fallback.
            if (active.waitFor(guardMillis, TimeUnit.MILLISECONDS) || !active.isAlive()) {
                int exit = active.exitValue();
                active = null;
                if (exit != 0) throw new IOException("mpv no pudo iniciar la reproducci\u00f3n.");
            }
            checkRequest(requestId);
        } catch (InterruptedException ex) {
            try { stop(); }
            finally { Thread.currentThread().interrupt(); }
            throw ex;
        } catch (CancellationException ex) {
            stop();
            throw ex;
        }
    }

    // Keep ownership until termination is confirmed; never launch over a stuck child.
    synchronized boolean currentAlive() {
        return active != null && active.isAlive();
    }

    synchronized void stop() {
        if (active == null) return;
        boolean interrupted = Thread.interrupted();
        try {
            if (active.isAlive()) {
                active.destroy();
                try { active.waitFor(stopMillis, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { interrupted = true; }
                if (active.isAlive()) {
                    active.destroyForcibly();
                    try { active.waitFor(stopMillis, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException ex) { interrupted = true; }
                }
            }
            if (active.isAlive()) throw new IllegalStateException("No se pudo cerrar la reproducci\u00f3n anterior.");
            active = null;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    void close() {
        // Set before taking the monitor so queued/extracting workers cannot start later.
        closed = true;
        stop();
    }

    private void checkOpen() {
        if (closed || Thread.currentThread().isInterrupted()) throw new CancellationException("Reproducci\u00f3n cancelada.");
    }

    private void checkRequest(long requestId) {
        checkOpen();
        if (requestId != latestRequest) throw new CancellationException("Solicitud de reproducci\u00f3n reemplazada.");
    }

    private Process launch(List<String> command) throws IOException {
        // mpv can fill an unread stdout pipe during playback and then hang.
        return launcher.start(new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD));
    }

    static List<String> playbackCommand(String mpv, Models.Video video, String title) {
        ArrayList<String> cmd = basePlaybackCommand(mpv, video, title);
        cmd.add("--force-window=yes");
        cmd.add("--keep-open=no");
        cmd.add("--save-position-on-quit");
        cmd.add(video.source());
        return List.copyOf(cmd);
    }

    static List<String> embeddedPlaybackCommand(String mpv, Models.Video video, String title,
                                                long windowId, String ipcPath) {
        return embeddedPlaybackCommand(mpv, video, title, windowId, ipcPath, null);
    }

    static List<String> embeddedPlaybackCommand(String mpv, Models.Video video, String title,
                                                long windowId, String ipcPath,
                                                String hlsBitrateOverride) {
        ArrayList<String> cmd = basePlaybackCommand(mpv, video, title, hlsBitrateOverride);
        cmd.add("--wid=" + Long.toUnsignedString(windowId));
        cmd.add("--force-window=yes");
        cmd.add("--keep-open=yes");
        cmd.add("--osc=no");
        cmd.add("--input-default-bindings=no");
        cmd.add("--input-vo-keyboard=no");
        cmd.add("--input-ipc-server=" + ipcPath);
        cmd.add("--no-border");
        cmd.add(video.source());
        return List.copyOf(cmd);
    }

    private static ArrayList<String> basePlaybackCommand(String mpv, Models.Video video, String title) {
        return basePlaybackCommand(mpv, video, title, null);
    }

    private static ArrayList<String> basePlaybackCommand(String mpv, Models.Video video, String title,
                                                          String hlsBitrateOverride) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(mpv);
        cmd.add("--hwdec=auto-safe");
        cmd.add("--network-timeout=20");
        cmd.add("--cache=yes");
        cmd.add("--cache-pause=yes");
        cmd.add("--cache-pause-wait=2");
        cmd.add("--cache-secs=45");
        cmd.add("--demuxer-max-bytes=96MiB");
        cmd.add("--stream-buffer-size=1MiB");
        String hlsBitrate = hlsBitrateOverride == null || hlsBitrateOverride.isBlank()
                ? PlaybackSettings.hlsBitrateArgument()
                : hlsBitrateOverride;
        cmd.add("--hls-bitrate=" + hlsBitrate);
        cmd.add("--force-media-title=" + safe(title));
        cmd.add("--audio-client-name=Streamflix");
        String audioPreference = PlaybackSettings.audioLanguage();
        if (!"auto".equals(audioPreference)) {
            cmd.add("--alang=" + PlaybackSettings.audioPreferenceArgument());
        }

        addHeaders(cmd, video);
        List<Models.Subtitle> subtitles = orderedSubtitles(video.subtitles());
        String subtitlePreference = PlaybackSettings.subtitlePreferenceArgument();
        if (!subtitles.isEmpty() && !subtitlePreference.isBlank()) {
            cmd.add("--slang=" + subtitlePreference);
            cmd.add("--sid=auto");
        } else if ("off".equals(PlaybackSettings.subtitleLanguage())) {
            cmd.add("--sid=no");
        }
        for (Models.Subtitle subtitle : subtitles) {
            cmd.add("--sub-file=" + subtitle.file());
        }
        return cmd;
    }

    static List<Models.Subtitle> orderedSubtitles(List<Models.Subtitle> subtitles) {
        if (subtitles == null) return List.of();
        String preferred = PlaybackSettings.subtitleLanguage();
        return subtitles.stream()
                .filter(s -> s != null && s.file() != null && !s.file().isBlank())
                .sorted(Comparator.comparingInt((Models.Subtitle s) ->
                                MediaLanguage.rank(null, s.label(), preferred))
                        .thenComparingInt(s -> s.isDefault() ? 0 : 1)
                        .thenComparing(s -> s.label() == null ? "" : s.label(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Models.Subtitle::file))
                .toList();
    }

    static int smoke(Models.Video video) throws Exception {
        return PLAYER.runSmoke(video);
    }

    synchronized int runSmoke(Models.Video video) throws Exception {
        checkOpen();
        String mpv = executable.get();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(mpv);
        cmd.add("--no-config");
        cmd.add("--vo=null");
        cmd.add("--ao=null");
        cmd.add("--frames=1");
        cmd.add("--network-timeout=10");
        cmd.add("--really-quiet");
        addHeaders(cmd, video);
        cmd.add(video.source());

        stop();
        checkOpen();
        active = launch(cmd);
        try {
            if (!active.waitFor(30, TimeUnit.SECONDS)) return 124;
            return active.exitValue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } finally {
            stop();
        }
    }

    private static void addHeaders(List<String> cmd, Models.Video video) {
        if (video.headers() == null || video.headers().isEmpty()) return;
        ArrayList<String> extra = new ArrayList<>();
        for (var entry : video.headers().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.equalsIgnoreCase("User-Agent")) {
                cmd.add("--user-agent=" + value);
            } else if (key.equalsIgnoreCase("Referer") || key.equalsIgnoreCase("Referrer")) {
                if (value.matches("https?://[^/]+")) value += "/";
                cmd.add("--referrer=" + value);
            } else {
                extra.add(key + ": " + value.replace(",", "\\,"));
            }
        }
        if (!extra.isEmpty()) cmd.add("--http-header-fields=" + String.join(",", extra));
    }

    private static String requireMpv() {
        String mpv = locate();
        if (mpv == null) throw new IllegalStateException("mpv no fue encontrado.");
        return mpv;
    }

    private static String locate() {
        String env = System.getenv("STREAMFLIX_MPV");
        if (isExecutable(env)) return env;

        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path appDir = Path.of(appPath).toAbsolutePath().getParent();
            if (appDir != null) {
                Path bundled = appDir.resolve("tools").resolve("mpv").resolve(isWindows() ? "mpv.exe" : "mpv");
                if (Files.isRegularFile(bundled)) return bundled.toString();
            }
        }
        Path managed = MpvBootstrap.executablePath();
        if (MpvBootstrap.managedRuntimeReady() && Files.isRegularFile(managed)) {
            return managed.toString();
        }

        List<String> candidates = List.of(
                "tools" + File.separator + "mpv" + File.separator + (isWindows() ? "mpv.exe" : "mpv"),
                "C:\\Program Files\\mpv\\mpv.exe",
                "C:\\Program Files (x86)\\mpv\\mpv.exe"
        );
        for (String candidate : candidates) {
            if (isExecutable(candidate)) return candidate;
        }

        String path = System.getenv("PATH");
        if (path != null) {
            String filename = isWindows() ? "mpv.exe" : "mpv";
            for (String dir : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                Path candidate = Path.of(dir, filename);
                if (Files.isRegularFile(candidate)) return candidate.toString();
            }
        }
        return null;
    }

    private static boolean isExecutable(String path) {
        if (path == null || path.isBlank()) return false;
        try { return Files.isRegularFile(Path.of(path)); }
        catch (Exception ignored) { return false; }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String safe(String value) {
        return value == null ? "Streamflix" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
