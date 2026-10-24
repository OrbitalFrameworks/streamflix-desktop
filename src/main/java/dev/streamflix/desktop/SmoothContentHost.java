package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
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
    private final javax.swing.Timer transitionTimer;
    private BufferedImage transitionSnapshot;
    private float transitionAlpha = 0f;
    private long transitionStartTime = 0L;
    private volatile Runnable pendingOnComplete;
    private volatile String currentCardName = "";
    private volatile long transitionGeneration = 0L;
    private boolean forceAnimationForTesting = false;

    private final MouseAdapter descendantMouseListener = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            if (isTransitioning()) {
                stopTransition();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (isTransitioning()) {
                stopTransition();
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (isTransitioning()) {
                stopTransition();
            }
        }
    };

    private final KeyAdapter descendantKeyListener = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (isTransitioning()) {
                stopTransition();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (isTransitioning()) {
                stopTransition();
            }
        }

        @Override
        public void keyTyped(KeyEvent e) {
            if (isTransitioning()) {
                stopTransition();
            }
        }
    };

    private final ContainerListener descendantContainerListener = new ContainerAdapter() {
        @Override
        public void componentAdded(ContainerEvent e) {
            hookDescendants(e.getChild());
        }

        @Override
        public void componentRemoved(ContainerEvent e) {
            unhookDescendants(e.getChild());
        }
    };

    private final AWTEventListener awtInteractionListener = event -> {
        if (!isTransitioning()) return;
        if (event instanceof MouseEvent me) {
            int id = me.getID();
            if (id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED) {
                Object src = me.getSource();
                if (src instanceof Component c && (c == SmoothContentHost.this || SwingUtilities.isDescendingFrom(c, SmoothContentHost.this))) {
                    stopTransition();
                }
            }
        } else if (event instanceof KeyEvent ke) {
            int id = ke.getID();
            if (id == KeyEvent.KEY_PRESSED || id == KeyEvent.KEY_RELEASED || id == KeyEvent.KEY_TYPED) {
                Object src = ke.getSource();
                if (src instanceof Component c && (c == SmoothContentHost.this || SwingUtilities.isDescendingFrom(c, SmoothContentHost.this))) {
                    stopTransition();
                }
            }
        }
    };

    private boolean awtListenerAttached = false;

    SmoothContentHost() {
        super();
        setLayout(cardLayout);
        setOpaque(true);
        setBackground(Theme.BG);

        transitionTimer = new javax.swing.Timer(FRAME_INTERVAL_MS, e -> {
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

    void setForceAnimationForTesting(boolean force) {
        this.forceAnimationForTesting = force;
    }

    boolean isForceAnimationForTesting() {
        return forceAnimationForTesting;
    }

    private synchronized void attachAwtListener() {
        if (!awtListenerAttached) {
            try {
                Toolkit.getDefaultToolkit().addAWTEventListener(
                        awtInteractionListener,
                        AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
                awtListenerAttached = true;
            } catch (Throwable ignored) {}
        }
    }

    private synchronized void detachAwtListener() {
        if (awtListenerAttached) {
            try {
                Toolkit.getDefaultToolkit().removeAWTEventListener(awtInteractionListener);
            } catch (Throwable ignored) {}
            awtListenerAttached = false;
        }
    }

    private void hookDescendants(Component comp) {
        if (comp == null || comp == this) return;
        comp.removeMouseListener(descendantMouseListener);
        comp.addMouseListener(descendantMouseListener);
        comp.removeKeyListener(descendantKeyListener);
        comp.addKeyListener(descendantKeyListener);
        if (comp instanceof Container container) {
            container.removeContainerListener(descendantContainerListener);
            container.addContainerListener(descendantContainerListener);
            for (Component child : container.getComponents()) {
                hookDescendants(child);
            }
        }
    }

    private void unhookDescendants(Component comp) {
        if (comp == null || comp == this) return;
        comp.removeMouseListener(descendantMouseListener);
        comp.removeKeyListener(descendantKeyListener);
        if (comp instanceof Container container) {
            container.removeContainerListener(descendantContainerListener);
            for (Component child : container.getComponents()) {
                unhookDescendants(child);
            }
        }
    }

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        super.addImpl(comp, constraints, index);
        hookDescendants(comp);
    }

    @Override
    public void remove(Component comp) {
        unhookDescendants(comp);
        super.remove(comp);
    }

    @Override
    public void remove(int index) {
        if (index >= 0 && index < getComponentCount()) {
            unhookDescendants(getComponent(index));
        }
        super.remove(index);
    }

    @Override
    public void removeAll() {
        for (Component c : getComponents()) {
            unhookDescendants(c);
        }
        super.removeAll();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        detachAwtListener();
    }

    void flushPendingCallbacks() {
        if (SwingUtilities.isEventDispatchThread()) {
            transitionGeneration++;
            Runnable cb = pendingOnComplete;
            pendingOnComplete = null;
            if (cb != null) {
                cb.run();
            }
        } else {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    transitionGeneration++;
                    Runnable cb = pendingOnComplete;
                    pendingOnComplete = null;
                    if (cb != null) {
                        cb.run();
                    }
                });
            } catch (Exception ignored) {}
        }
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
        final long currentGen = ++transitionGeneration;

        boolean reducedMotion = isReducedMotion();
        boolean isPlayerTransition = "PLAYER".equals(name) || "PLAYER".equals(currentCardName);
        boolean canAnimate = !reducedMotion
                && !isPlayerTransition
                && (forceAnimationForTesting || (!GraphicsEnvironment.isHeadless()
                        && isShowing()
                        && getWidth() > 0
                        && getHeight() > 0));

        if (!canAnimate) {
            currentCardName = name;
            cardLayout.show(this, name);
            revalidate();
            repaint();
            if (onComplete != null) {
                this.pendingOnComplete = onComplete;
                final Runnable cb = onComplete;
                final String targetCard = name;
                SwingUtilities.invokeLater(() -> {
                    if (this.transitionGeneration == currentGen
                            && this.pendingOnComplete == cb
                            && targetCard.equals(this.currentCardName)) {
                        this.pendingOnComplete = null;
                        cb.run();
                    }
                });
            }
            return;
        }

        // Capture snapshot of current view for outgoing fade
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        try {
            BufferedImage snapshot = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
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
            attachAwtListener();
            transitionTimer.start();
        } else if (onComplete != null) {
            this.pendingOnComplete = onComplete;
            final Runnable cb = onComplete;
            final String targetCard = name;
            SwingUtilities.invokeLater(() -> {
                if (this.transitionGeneration == currentGen
                        && this.pendingOnComplete == cb
                        && targetCard.equals(this.currentCardName)) {
                    this.pendingOnComplete = null;
                    cb.run();
                }
            });
        }
    }

    void stopTransition() {
        if (transitionTimer.isRunning()) {
            transitionTimer.stop();
        }
        detachAwtListener();
        transitionSnapshot = null;
        transitionAlpha = 0f;
        transitionStartTime = 0L;
        transitionGeneration++;
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
        detachAwtListener();
        transitionSnapshot = null;
        transitionAlpha = 0f;
        transitionStartTime = 0L;
        pendingOnComplete = null;
        transitionGeneration++;
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
