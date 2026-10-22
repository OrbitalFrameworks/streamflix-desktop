package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class MpvBootstrap {
    static final String BUILD_ID = "20261021-e471496c56";
    // Keep already-provisioned verified runtimes usable after app updates;
    // upstream daily release assets may be removed, but installed binaries
    // remain valid offline. Only fresh installations fetch the new pinned one.
    static final String PREVIOUS_VERIFIED_BUILD_ID = "20260830-e8673660ab";
    static final URI DOWNLOAD_URI = URI.create(
            "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/20261021/"
                    + "mpv-x86_64-20261021-git-e471496c56.7z");
    static final String ARCHIVE_SHA256 =
            "4A355F3E89C83FABC730D33FB4E759DFEC747806ED2287B61A90D1681B83E8D3";
    private static final String RUNTIME_DIR_PROPERTY = "streamflix.mpv.dir";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private MpvBootstrap() {}

    static Path runtimeDir() {
        String override = System.getProperty(RUNTIME_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                : Path.of(localAppData);
        return base.resolve("Streamflix").resolve("runtime").resolve("mpv")
                .toAbsolutePath().normalize();
    }

    static Path executablePath() {
        return runtimeDir().resolve(isWindows() ? "mpv.exe" : "mpv");
    }

    static boolean managedRuntimeReady() {
        Path executable = executablePath();
        Path version = runtimeDir().resolve("VERSION");
        if (!Files.isRegularFile(executable) || !Files.isRegularFile(version)) return false;
        try {
            String installedBuild = Files.readString(version, StandardCharsets.UTF_8).strip();
            return BUILD_ID.equals(installedBuild) || PREVIOUS_VERIFIED_BUILD_ID.equals(installedBuild);
        } catch (IOException ignored) {
            return false;
        }
    }

    static void ensureReady(Component parent, Consumer<Boolean> completion) {
        Consumer<Boolean> safeCompletion = completion == null ? ignored -> {} : completion;
        if (MpvPlayer.isAvailable()) {
            safeCompletion.accept(true);
            return;
        }
        if (!isWindows()) {
            showFailure(parent, "mpv no está instalado y el aprovisionamiento automático es solo para Windows.");
            safeCompletion.accept(false);
            return;
        }

        ProgressDialog dialog = new ProgressDialog(parent);
        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override protected Path doInBackground() throws Exception {
                return downloadAndInstall();
            }

            @Override protected void done() {
                finishProvisioning(this, dialog::dispose, parent, safeCompletion);
            }
        };
        worker.execute();
        dialog.setVisible(true);
    }

    static void finishProvisioning(Future<Path> task, Runnable closeDialog,
                                    Component parent, Consumer<Boolean> completion) {
        Path executable;
        try {
            executable = task.get();
        } catch (Exception ex) {
            AppLog.error("mpv-bootstrap", "No se pudo preparar mpv.", ex);
            closeDialog.run();
            showFailure(parent, rootMessage(ex));
            completion.accept(false);
            return;
        }

        AppLog.info("mpv-bootstrap", "mpv preparado en " + executable);
        closeDialog.run();
        completion.accept(true);
    }

    static Path downloadAndInstall() throws Exception {
        Path parent = runtimeDir().getParent();
        if (parent == null) throw new IOException("Directorio de runtime inválido.");
        Files.createDirectories(parent);

        Path archive = Files.createTempFile(parent, "mpv-", ".7z.part");
        try {
            HttpRequest request = HttpRequest.newBuilder(DOWNLOAD_URI)
                    .timeout(Duration.ofMinutes(10))
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "StreamflixDesktop/" + AppVersion.current())
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GitHub respondió HTTP " + response.statusCode());
            }
            try (InputStream input = response.body()) {
                Files.copy(input, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            return installVerifiedArchive(archive, runtimeDir());
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    static Path installVerifiedArchive(Path archive, Path target) throws Exception {
        String actual = sha256(archive);
        if (!ARCHIVE_SHA256.equalsIgnoreCase(actual)) {
            throw new SecurityException("SHA-256 de mpv no coincide con el valor fijado.");
        }

        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("Directorio de runtime inválido.");
        Files.createDirectories(parent);

        Path stage = Files.createTempDirectory(parent, ".mpv-stage-");
        Path backup = target.resolveSibling(target.getFileName() + ".previous");
        try {
            extract(archive, stage);
            Path stagedExe = stage.resolve(isWindows() ? "mpv.exe" : "mpv");
            if (!Files.isRegularFile(stagedExe)) {
                throw new IOException("El archive de mpv no produjo el ejecutable esperado.");
            }
            Files.writeString(stage.resolve("VERSION"), BUILD_ID, StandardCharsets.UTF_8);

            deleteRecursively(backup);
            if (Files.exists(target)) {
                Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception installError) {
                deleteRecursively(target);
                if (Files.exists(backup)) {
                    Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
                }
                throw installError;
            }

            deleteRecursively(backup);
            return target.resolve(isWindows() ? "mpv.exe" : "mpv");
        } finally {
            deleteRecursively(stage);
        }
    }

    private static void extract(Path archive, Path destination) throws Exception {
        Exception tarFailure = null;
        try {
            runExtractor(new ProcessBuilder(
                    "tar.exe", "-xf", archive.toString(), "-C", destination.toString()));
            return;
        } catch (Exception ex) {
            tarFailure = ex;
        }

        Path sevenZip = Path.of("C:\\Program Files\\7-Zip\\7z.exe");
        if (Files.isRegularFile(sevenZip)) {
            runExtractor(new ProcessBuilder(
                    sevenZip.toString(), "x", archive.toString(),
                    "-o" + destination, "-y"));
            return;
        }

        throw new IOException(
                "No hay un extractor compatible (tar.exe o 7-Zip).", tarFailure);
    }

    private static void runExtractor(ProcessBuilder builder) throws Exception {
        Process process = builder.redirectErrorStream(true).start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("La extracción de mpv excedió el tiempo límite.");
        }
        if (process.exitValue() != 0) {
            String detail = output.strip();
            if (detail.length() > 500) detail = detail.substring(0, 500);
            throw new IOException("No se pudo extraer mpv. " + detail);
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).toUpperCase();
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                try { Files.deleteIfExists(path); }
                catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void showFailure(Component parent, String detail) {
        JOptionPane.showMessageDialog(parent,
                "Streamflix no pudo preparar el motor de reproducción mpv.\n"
                        + "Puedes seguir explorando el catálogo y volver a intentarlo al reiniciar.\n\n"
                        + detail,
                "Motor de reproducción", JOptionPane.WARNING_MESSAGE);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }

    private static final class ProgressDialog extends JDialog {
        ProgressDialog(Component parent) {
            super(SwingUtilities.getWindowAncestor(parent),
                    "Preparando reproducción", ModalityType.APPLICATION_MODAL);
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JPanel body = new JPanel();
            body.setBorder(new EmptyBorder(20, 22, 20, 22));
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

            JLabel title = Theme.heading("Preparando motor de reproducción", 18f);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(title);
            body.add(Box.createVerticalStrut(8));

            JLabel detail = Theme.muted(
                    "Este intento de reproducción necesita mpv; descargándolo desde el release upstream verificado.");
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(detail);
            body.add(Box.createVerticalStrut(16));

            JProgressBar progress = new JProgressBar();
            progress.setIndeterminate(true);
            progress.setAlignmentX(Component.LEFT_ALIGNMENT);
            progress.setPreferredSize(new Dimension(500, 20));
            progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            body.add(progress);

            setContentPane(body);
            pack();
            setMinimumSize(new Dimension(560, getHeight()));
            setLocationRelativeTo(parent);
        }
    }
}
