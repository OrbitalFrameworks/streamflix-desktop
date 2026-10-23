package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmbeddedPlayerIntegrationTest {
    public static void main(String[] args) throws Exception {
        testEmbeddedPlayerViewStructure();
        testPlayerOverlayMaintainsCanvasBounds();
        testMainFrameEmbeddedPlayerFlow();
        testPopOutWindowDelegation();
        System.out.println("EmbeddedPlayerIntegrationTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }

    private static void testEmbeddedPlayerViewStructure() {
        AtomicBoolean closed = new AtomicBoolean(false);
        EmbeddedPlayerView view = new EmbeddedPlayerView(null, "Test Title", () -> closed.set(true));

        check(view.getComponentCount() > 0, "EmbeddedPlayerView must have child components");
        check(view.getBackground().equals(Color.BLACK), "Player background must be black");
        check(view.lastKnownTime() == 0.0, "Initial time must be 0");
    }

    private static void testPlayerOverlayMaintainsCanvasBounds() {
        JLayeredPane root = new JLayeredPane();
        root.setSize(1280, 720);

        JPanel media = new JPanel();
        JPanel top = new JPanel();
        JPanel bottom = new JPanel();
        top.setPreferredSize(new Dimension(1280, 60));
        bottom.setPreferredSize(new Dimension(1280, 80));

        EmbeddedPlayerWindow.layoutPlayerLayers(root, media, top, bottom);
        Rectangle initialMediaBounds = media.getBounds();
        check(initialMediaBounds.equals(new Rectangle(0, 0, 1280, 720)),
                "Media stage must cover 100% of the player viewport");

        // Hide overlays simulating fullscreen inactivity
        top.setVisible(false);
        bottom.setVisible(false);
        EmbeddedPlayerWindow.layoutPlayerLayers(root, media, top, bottom);

        check(media.getBounds().equals(initialMediaBounds),
                "Hiding overlay chrome must never change video canvas size or cause resize-flash");
    }

    private static void testMainFrameEmbeddedPlayerFlow() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        SwingUtilities.invokeAndWait(() -> {
            Provider dummy = ProviderRegistry.all().get(0);
            MainFrame frame = new MainFrame(List.of(dummy));
            try {
                frame.addNotify();
                AtomicBoolean historyChanged = new AtomicBoolean(false);
                EmbeddedPlayerView player = frame.openEmbeddedPlayer("In-Frame Playback Test", () -> historyChanged.set(true));
                check(player != null, "Embedded player view must be returned");
                check(player.isDisplayable(), "Embedded player view must be mounted in frame hierarchy");

                // Verify closing player restores shell
                frame.closeEmbeddedPlayer();
                check(frame.isDisplayable(), "MainFrame must remain displayable after closing player");
            } finally {
                frame.dispose();
            }
        });
    }

    private static void testPopOutWindowDelegation() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        SwingUtilities.invokeAndWait(() -> {
            EmbeddedPlayerWindow window = EmbeddedPlayerWindow.open(null, "Pop-Out Test");
            try {
                check(window != null, "Pop-out window must be created");
                check(window.playerView() != null, "Pop-out window must delegate to an EmbeddedPlayerView");
                check(window.isDisplayable(), "Pop-out window must be displayable");
            } finally {
                window.dispose();
            }
        });
    }
}
