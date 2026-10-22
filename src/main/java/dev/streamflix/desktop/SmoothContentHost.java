package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * A container using CardLayout with brief, interruptible, EDT-safe crossfades
 * between cards, respecting accessibility and reduced-motion preferences.
 * Real-time frame pacing and interactive fluid rendering depend on host display
 * compositor performance and remain tracked as native validation debt in headless CI.
 */
final class SmoothContentHost extends JPanel {
    static final int TRANSITION_DURATION_MS = 140; // Snappy crossfade duration
    static final int FRAME_INTERVAL_MS = 16;       // 16 ms timer intervals on EDT; display frame rate is hardware-dependent

    private final CardLayout cardLayout = new CardLayout();
    private final Timer transitionTimer;
    private BufferedImage transitionSnapshot;
    private float transitionAlpha = 0f;
    private long transitionStartTime = 0L;
    private Runnable pendingOnComplete;
    private String currentCardName = "";

    SmoothContentHost() {
        super();
        setLayout(cardLayout);
        setOpaque(true);
        setBackground(Theme.BG);

        transitionTimer = new Timer(FRAME_INTERVAL_MS, e -> {
            long elapsed = System.currentTimeMillis() - transitionStartTime;
            float progress = (float) elapsed / (float) TRANSITION_DURATION_MS;
            if (progress >= 1.0f) {
                stopTransition();
            } else {
                transitionAlpha = Math.max(0.0f, 1.0f - progress);
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

    boolean isTransitioning() {
        return transitionTimer.isRunning();
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
            this.transitionStartTime = System.currentTimeMillis();
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
        transitionStartTime = 0L;
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
        transitionStartTime = 0L;
        pendingOnComplete = null;
        repaint();
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        BufferedImage snapshot = transitionSnapshot;
        float alpha = transitionAlpha;
        if (alpha > 0.001f && snapshot != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, Math.min(1.0f, Math.max(0.0f, alpha))));
                g2.drawImage(snapshot, 0, 0, null);
            } finally {
                g2.dispose();
            }
        }
    }

    static boolean isReducedMotion() {
        String prop = System.getProperty("streamflix.reducedMotion");
        if (prop == null) {
            prop = System.getenv("STREAMFLIX_REDUCED_MOTION");
        }
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
