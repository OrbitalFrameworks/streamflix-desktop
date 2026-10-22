package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class Diagnostics {
    private Diagnostics() {}

    static void chooseAndExport(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar diagnóstico de Streamflix");
        chooser.setSelectedFile(new java.io.File(
                "streamflix-diagnostics-" + AppVersion.current() + ".zip"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        Path target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase().endsWith(".zip")) {
            target = target.resolveSibling(target.getFileName() + ".zip");
        }
        Path finalTarget = target;

        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws Exception {
                export(finalTarget);
                return finalTarget;
            }
            @Override protected void done() {
                try {
                    Path exported = get();
                    JOptionPane.showMessageDialog(parent,
                            "Diagnóstico exportado en:\n" + exported,
                            "Diagnóstico", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    AppLog.error("diagnostics", "No se pudo exportar el diagnóstico.", ex);
                    JOptionPane.showMessageDialog(parent,
                            "No se pudo exportar el diagnóstico.",
                            "Diagnóstico", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    static void export(Path target) throws Exception {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(
                parent == null ? Path.of(".") : parent, "streamflix-diagnostics-", ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                writeText(zip, "diagnostics.txt", summary());
                addLogIfPresent(zip, AppLog.logFile(), "streamflix.log");
                addLogIfPresent(zip, AppLog.logFile().resolveSibling("streamflix.log.1"),
                        "streamflix.log.1");
            }
            Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
    private static String summary() {
        StringBuilder out = new StringBuilder();
        out.append("Streamflix Desktop diagnostics\n");
        out.append("generated=").append(Instant.now()).append('\n');
        out.append("version=").append(AppVersion.current()).append('\n');
        out.append("os=").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(' ')
                .append(System.getProperty("os.arch")).append('\n');
        out.append("java=").append(System.getProperty("java.version")).append(' ')
                .append(System.getProperty("java.vendor")).append('\n');
        out.append("packaged=").append(System.getProperty("jpackage.app-path") != null).append('\n');
        out.append("portableUpdater=").append(PortableUpdater.canSelfUpdate()).append('\n');
        out.append("mpv=").append(MpvPlayer.isAvailable()).append('\n');

        try {
            out.append("tmdbConfigured=").append(TmdbSettings.hasApiKey()).append('\n');
        } catch (Exception ex) {
            out.append("tmdbConfigured=ERROR\n");
        }
        out.append("tmdbEnvironmentOverride=")
                .append(TmdbSettings.environmentOverrideActive()).append('\n');
        out.append("catalogLanguage=").append(TmdbSettings.catalogLanguage()).append('\n');
        out.append("startMaximized=").append(PlaybackSettings.startMaximized()).append('\n');
        out.append("audioLanguage=").append(PlaybackSettings.audioLanguage()).append('\n');
        out.append("subtitleLanguage=").append(PlaybackSettings.subtitleLanguage()).append('\n');
        out.append("qualityProfile=").append(PlaybackSettings.qualityProfile()).append('\n');
        List<Provider> providers = ProviderRegistry.all();
        out.append("providers=").append(providers.size()).append('\n');
        for (Provider provider : providers) {
            out.append("provider=").append(provider.id())
                    .append('|').append(provider.name())
                    .append("|movies=").append(provider.supportsMovies())
                    .append("|tv=").append(provider.supportsTvShows())
                    .append('\n');
        }

        out.append(ProviderHealthStats.summary());
        out.append("favorites=").append(UserData.getFavorites().size()).append('\n');
        out.append("historyEntries=").append(UserData.getHistory().size()).append('\n');
        out.append("privacy=No TMDb credential, media titles, URLs or user-data contents are exported.\n");
        return out.toString();
    }

    private static void writeText(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void addLogIfPresent(ZipOutputStream zip, Path path, String name)
            throws IOException {
        if (!Files.isRegularFile(path)) return;
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(path, zip);
        zip.closeEntry();
    }
}
