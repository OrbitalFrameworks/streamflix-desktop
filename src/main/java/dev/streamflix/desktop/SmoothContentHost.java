package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * A container using CardLayout with brief, interruptible, EDT-safe crossfades
 * between cards, respecting accessibility and reduced-motion preferences.
 */
final class SmoothContentHost extends JPanel {
    static final int TRANSITION_DURATION_MS = 140; // Snappy Apple-style duration
    static final int FRAME_INTERVAL_MS = 16;       // ~60 fps

    private final CardLayout cardLayout = new CardLayout();
    private final Timer transitionTimer;
    private BufferedImage transitionSnapshot;
    private float transitionAlpha = 0f;
    private Runnable pendingOnComplete;
    private String currentCardName = "";

    SmoothContentHost() {
        super();
        setLayout(cardLayout);
        setOpaque(true);
        setBackground(Theme.BG);

        float alphaStep = (float) FRAME_INTERVAL_MS / (float) TRANSITION_DURATION_MS;

        transitionTimer = new Timer(FRAME_INTERVAL_MS, e -> {
            transitionAlpha -= alphaStep;
            if (transitionAlpha <= 0.001f) {
                stopTransition();
            } else {
                repaint();
            }
        });
        transitionTimer.setCoalesce(true);

        // Immediate dismissal upon any user click to guarantee zero interaction latency
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (transitionTimer.isRunning()) {
                    stopTransition();
                }
            }
        });
    }

    void addCard(Component comp, String name) {
        add(comp, name);
    }

    String currentCard() {
        return currentCardName;
    }

    void showCard(String name) {
        showCard(name, null);
    }

    void showCard(String name, Runnable onComplete) {
        if (name == null) return;
        if (name.equals(currentCardName)) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // A superseded route must not restore focus for the old destination.
        // Explicit user dismissal still completes its callback through stopTransition().
        cancelTransition();

        boolean reducedMotion = isReducedMotion();
        boolean canAnimate = !reducedMotion
                && !GraphicsEnvironment.isHeadless()
                && isShowing()
                && getWidth() > 0
                && getHeight() > 0;

        if (!canAnimate) {
            currentCardName = name;
            cardLayout.show(this, name);
            revalidate();
            repaint();
            if (onComplete != null) onComplete.run();
            return;
        }

        // Capture snapshot of current view for outgoing fade
        try {
            BufferedImage snapshot = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = snapshot.createGraphics();
            paint(g2);
            g2.dispose();
            this.transitionSnapshot = snapshot;
            this.transitionAlpha = 1.0f;
            this.pendingOnComplete = onComplete;
        } catch (Throwable t) {
            this.transitionSnapshot = null;
            this.transitionAlpha = 0f;
            this.pendingOnComplete = null;
        }

        currentCardName = name;
        cardLayout.show(this, name);
        revalidate();
        repaint();

        if (transitionSnapshot != null) {
            transitionTimer.start();
        } else if (onComplete != null) {
            onComplete.run();
        }
    }

    void stopTransition() {
        if (transitionTimer.isRunning()) {
            transitionTimer.stop();
        }
        transitionSnapshot = null;
        transitionAlpha = 0f;
        Runnable cb = pendingOnComplete;
        pendingOnComplete = null;
        repaint();
        if (cb != null) {
            cb.run();
        }
    }

    private void cancelTransition() {
        if (transitionTimer.isRunning()) {
            transitionTimer.stop();
        }
        transitionSnapshot = null;
        transitionAlpha = 0f;
        pendingOnComplete = null;
        repaint();
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (transitionAlpha > 0.001f && transitionSnapshot != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, Math.min(1.0f, Math.max(0.0f, transitionAlpha))));
                g2.drawImage(transitionSnapshot, 0, 0, null);
            } finally {
                g2.dispose();
            }
        }
    }

    static boolean isReducedMotion() {
        String prop = System.getProperty("streamflix.reducedMotion");
        if (prop != null) {
            return "true".equalsIgnoreCase(prop) || "1".equals(prop);
        }
        try {
            Boolean winAnim = (Boolean) Toolkit.getDefaultToolkit().getDesktopProperty("win.animations.enabled");
            if (Boolean.FALSE.equals(winAnim)) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
