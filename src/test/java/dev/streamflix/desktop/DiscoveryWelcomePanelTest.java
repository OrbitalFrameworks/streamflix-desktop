package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Headless regression for the first-run catalogue fallback and its real CTAs. */
public final class DiscoveryWelcomePanelTest {
    public static void main(String[] args) {
        AtomicInteger settings = new AtomicInteger();
        AtomicInteger movies = new AtomicInteger();
        AtomicInteger tv = new AtomicInteger();
        DiscoveryWelcomePanel panel = new DiscoveryWelcomePanel(settings::incrementAndGet,
                movies::incrementAndGet, tv::incrementAndGet);
        for (int width : new int[] {1480, 1280, 1024}) {
            panel.setSize(width, 385);
            layoutTree(panel);
            BufferedImage image = new BufferedImage(width, 385, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try { panel.paint(g); } finally { g.dispose(); }
            check(panel.getComponent(0).getWidth() > 0, "Welcome stage has width at " + width);
            check(panel.getComponent(0).getHeight() > 0, "Welcome stage has height at " + width);
        }
        List<JButton> buttons = new ArrayList<>();
        findButtons(panel, buttons);
        check(buttons.size() == 3, "Three visible onboarding actions are required");
        for (JButton button : buttons) {
            check(button.getActionListeners().length > 0, "CTA has an executable action: " + button.getText());
            button.doClick();
        }
        check(settings.get() == 1 && movies.get() == 1 && tv.get() == 1,
                "Welcome actions must be independently wired, exactly once");
        System.out.println("DiscoveryWelcomePanelTest OK");
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static void findButtons(Container root, List<JButton> result) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button) result.add(button);
            if (child instanceof Container nested) findButtons(nested, result);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
