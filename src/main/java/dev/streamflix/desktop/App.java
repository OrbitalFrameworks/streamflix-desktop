package dev.streamflix.desktop;

import javax.swing.*;
import java.util.Arrays;

public final class App {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                AppLog.error("uncaught", "Error no controlado en " + thread.getName(), error));
        AppLog.info("app", "Iniciando Streamflix " + AppVersion.current());
        Runtime.getRuntime().addShutdownHook(new Thread(MpvPlayer::shutdown, "mpv-shutdown"));
        if (Arrays.asList(args).contains("--live-self-test")) {
            System.exit(SelfTest.runLive());
        }
        if (Arrays.asList(args).contains("--self-test")) {
            System.exit(SelfTest.run());
        }

        System.setProperty("sun.awt.noerasebackground", "true");
        Theme.install();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(ProviderRegistry.all());
            if (PlaybackSettings.startMaximized()) {
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
            frame.setVisible(true);
            UpdateService.checkAndPrompt(frame, false);
        });
    }
}
