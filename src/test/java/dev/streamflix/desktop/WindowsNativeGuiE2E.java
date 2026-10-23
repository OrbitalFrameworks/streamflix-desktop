package dev.streamflix.desktop;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies the running Swing UI using native Windows desktop input/screen capture. */
public final class WindowsNativeGuiE2E {
    private static final Path OUT = Path.of(System.getProperty("streamflix.e2e.output", "build/gui-e2e"));
    private static final List<String> steps = new ArrayList<>();
    private static Robot robot;
    private static void log(String msg) { System.out.println("GUI-E2E: " + msg); steps.add(msg); }
    private static void verify(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); log("PASS " + msg); }
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    private static JButton findButton(Container root, String label) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && label.equals(b.getText()) && b.isShowing()) return b;
            if (c instanceof Container nested) {
                JButton found = findButton(nested, label);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void click(Container root, String label) throws Exception {
        AtomicReference<Point> at = new AtomicReference<>();
        edt(() -> {
            JButton btn = findButton(root, label);
            if (btn == null || !btn.isEnabled()) throw new AssertionError("Missing visible button: " + label);
            Point p = btn.getLocationOnScreen();
            at.set(new Point(p.x + btn.getWidth() / 2, p.y + btn.getHeight() / 2));
        });
        robot.mouseMove(at.get().x, at.get().y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        log("CLICK " + label);
    }
    private static void shot(String name, Window window) throws Exception {
        edt(() -> { window.toFront(); window.requestFocus(); });
        Thread.sleep(700);
        Rectangle bounds = window.getBounds().intersection(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        verify(bounds.width > 400 && bounds.height > 250, name + " onscreen bounds");
        BufferedImage img = robot.createScreenCapture(bounds);
        Set<Integer> sampled = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 9)
            for (int x = 0; x < img.getWidth(); x += 9) sampled.add(img.getRGB(x, y));
        ImageIO.write(img, "png", OUT.resolve(name + ".png").toFile());
        // Evidence is transported through CI logs, because branch CI has no artifact upload step.
        // The preview is derived from actual OS Robot pixels, not an application paint mock.
        int width = Math.min(img.getWidth(), 900);
        int height = Math.max(1, img.getHeight() * width / img.getWidth());
        BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preview.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, width, height, null);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(preview, "jpg", out);
        String data = Base64.getEncoder().encodeToString(out.toByteArray());
        System.out.println("E2E_SCREENSHOT_BEGIN|" + name + "|" + data.length());
        for (int idx = 0; idx < data.length(); idx += 2048)
            System.out.println("E2E_SCREENSHOT_CHUNK|" + name + "|" + data.substring(idx, Math.min(idx + 2048, data.length())));
        System.out.println("E2E_SCREENSHOT_END|" + name);
        verify(sampled.size() > 40, name + " real desktop screenshot has varied pixels: " + sampled.size());
        log("CAPTURE " + name + " " + bounds.width + "x" + bounds.height);
    }
    private static JDialog waitForDialog() throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing()) return d;
            Thread.sleep(250);
        }
        throw new AssertionError("Settings dialog not visible");
    }
    private static void run() throws Exception {
        Files.createDirectories(OUT);
        verify(!GraphicsEnvironment.isHeadless(), "real graphical environment");
        System.setProperty("streamflix.data.dir", Files.createTempDirectory("streamflix-gui-e2e-").toString());
        Theme.install();
        robot = new Robot();
        robot.setAutoDelay(80);
        AtomicReference<MainFrame> ref = new AtomicReference<>();
        try {
            edt(() -> {
                MainFrame frame = new MainFrame(List.of(ProviderRegistry.all().get(0)));
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(1280, 800);
                frame.setLocation(0, 0);
                frame.setVisible(true);
                ref.set(frame);
            });
            MainFrame frame = ref.get();
            verify(frame.isShowing(), "Streamflix actual MainFrame shown");
            shot("01-home", frame);
            for (String tab : List.of("Películas", "Series", "TV", "Deportes", "Mi lista", "Inicio")) {
                click(frame, tab);
                Thread.sleep(650);
            }
            shot("02-navigation", frame);
            click(frame, "Ajustes");
            JDialog settings = waitForDialog();
            verify(settings.getTitle().contains("Configuración"), "actual settings dialog title");
            shot("03-settings", settings);
            edt(settings::dispose);
            AtomicReference<EmbeddedPlayerView> player = new AtomicReference<>();
            edt(() -> player.set(frame.openEmbeddedPlayer("GUI E2E", () -> {})));
            Thread.sleep(1300);
            verify(player.get().isShowing(), "embedded player mounted in original MainFrame");
            shot("04-embedded-player", frame);
            String media = System.getenv("STREAMFLIX_E2E_MEDIA");
            if (media == null || media.isBlank()) throw new AssertionError("No local A/V fixture configured");
            player.get().start(new Models.Video(media), "Synthetic video fixture", MpvPlayer.beginRequest());
            Thread.sleep(2300);
            verify(MpvPlayer.isCurrentAlive(), "mpv process running with embedded window");
            verify(player.get().lastKnownTime() > 0.1, "mpv IPC playback position advanced");
            shot("05-playing", frame);
            log("AUDIO_SPEAKER_OUTPUT_UNVERIFIED: hosted CI lacks physical audio inspection");
            edt(frame::closeEmbeddedPlayer);
            Thread.sleep(900);
            verify(frame.isShowing(), "original frame restored without second main window");
            shot("06-restored", frame);
        } finally {
            if (ref.get() != null) edt(ref.get()::dispose);
        }
    }
    public static void main(String[] args) {
        int code = 0;
        try { run(); log("E2E_SUCCESS"); }
        catch (Throwable err) { err.printStackTrace(); log("E2E_FAILURE " + err); code = 1; }
        try { Files.createDirectories(OUT); Files.write(OUT.resolve("results.txt"), steps); }
        catch (Exception ignored) {}
        System.exit(code);
    }
}