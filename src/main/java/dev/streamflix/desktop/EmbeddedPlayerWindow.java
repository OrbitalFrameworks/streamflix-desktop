package dev.streamflix.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Separate pop-out window for video playback when explicitly requested by the user.
 * Wraps {@link EmbeddedPlayerView} while maintaining standalone window controls
 * and backward compatibility with tests and diagnostics.
 */
final class EmbeddedPlayerWindow extends JFrame {
    private static EmbeddedPlayerWindow activeWindow;

    private final EmbeddedPlayerView playerView;
    private final Window appOwner;
    private boolean minimizedByApplication;

    private Point moveStartScreen;
    private Rectangle moveStartBounds;
    private Point resizeStartScreen;
    private Rectangle resizeStartBounds;
    private int resizeEdges;

    private static final int RESIZE_NORTH = 1;
    private static final int RESIZE_SOUTH = 2;
    private static final int RESIZE_WEST = 4;
    private static final int RESIZE_EAST = 8;
    private static final int RESIZE_MARGIN = 8;

    EmbeddedPlayerWindow(Window owner, String title) {
        super("Streamflix · " + title);
        setIconImages(BrandMark.windowIcons());
        this.appOwner = owner;
        setUndecorated(true);
        setResizable(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        playerView = new EmbeddedPlayerView(this, title, this::dispose);
        setContentPane(playerView);
        configureInitialWindow(owner);
        installWindowInteractions();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                stopPlayback();
            }
        });
    }

    EmbeddedPlayerView playerView() {
        return playerView;
    }

    static EmbeddedPlayerWindow open(Window owner, String title) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("El reproductor debe abrirse desde la UI.");
        }
        if (activeWindow != null && activeWindow.isDisplayable()) activeWindow.dispose();

        EmbeddedPlayerWindow window = new EmbeddedPlayerWindow(owner, title);
        activeWindow = window;
        window.setVisible(true);
        window.toFront();
        window.playerView.requestFocusInWindow();
        return window;
    }

    void setProgressListener(ProgressListener listener) {
        playerView.setProgressListener(listener);
    }

    void setPlaybackIssueListener(Runnable listener) {
        playerView.setPlaybackIssueListener(listener);
    }

    void setPlaybackIssueHandler(BooleanSupplier handler) {
        playerView.setPlaybackIssueHandler(handler);
    }

    double lastKnownTime() {
        return playerView.lastKnownTime();
    }

    void setPreparing(String message) {
        playerView.setPreparing(message);
    }

    void start(Models.Video video, String serverName, long requestId) throws Exception {
        playerView.start(video, serverName, requestId);
    }

    void start(Models.Video video, String serverName, long requestId, double resumeAtSeconds) throws Exception {
        playerView.start(video, serverName, requestId, resumeAtSeconds);
    }

    void addExternalSubtitles(List<Models.Subtitle> subtitles) {
        playerView.addExternalSubtitles(subtitles);
    }

    void showFailure(String message) {
        playerView.showFailure(message);
    }

    void stopPlayback() {
        playerView.stopPlayback();
        if (activeWindow == this) activeWindow = null;
    }

    static boolean simulateSignalFailureForDiagnostics() {
        EmbeddedPlayerWindow window = activeWindow;
        if (window != null && window.isDisplayable()) {
            SwingUtilities.invokeLater(() -> window.playerView.requestRecovery("Caída de señal simulada desde Diagnóstico."));
            return true;
        }
        EmbeddedPlayerView view = EmbeddedPlayerView.getActiveView();
        if (view != null && view.isDisplayable()) {
            SwingUtilities.invokeLater(() -> view.requestRecovery("Caída de señal simulada desde Diagnóstico."));
            return true;
        }
        return false;
    }

    static void onApplicationStateChanged(int state) {
        Runnable action = () -> {
            EmbeddedPlayerWindow window = activeWindow;
            if (window == null || !window.isDisplayable()) return;

            boolean iconified = (state & Frame.ICONIFIED) != 0;
            if (iconified) {
                if (!window.isAlwaysOnTop()) window.minimizeWithApplication();
            } else {
                window.restoreWithApplication();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private void minimizeWithApplication() {
        if (minimizedByApplication || !isDisplayable()) return;
        minimizedByApplication = true;
        if (playerView.isFullScreen()) playerView.exitFullscreen();
        setState(Frame.ICONIFIED);
    }

    private void restoreWithApplication() {
        if (!minimizedByApplication || !isDisplayable()) return;
        minimizedByApplication = false;
        setState(Frame.NORMAL);
        setVisible(true);
        toFront();
        playerView.requestFocusInWindow();
    }

    private void configureInitialWindow(Window owner) {
        GraphicsConfiguration gc = owner != null && owner.getGraphicsConfiguration() != null
                ? owner.getGraphicsConfiguration()
                : getGraphicsConfiguration();
        Rectangle work = usableWorkArea(gc);
        Dimension minimum = normalMinimumSize(work);
        setMinimumSize(minimum);

        int width = Math.min(work.width,
                Math.max(minimum.width, Math.min(1320, (int) Math.round(work.width * 0.90))));
        int height = Math.min(work.height,
                Math.max(minimum.height, Math.min(820, (int) Math.round(work.height * 0.88))));
        setBounds(centeredBounds(work, width, height));
    }

    static Rectangle usableWorkArea(GraphicsConfiguration gc) {
        GraphicsConfiguration actual = gc != null
                ? gc
                : GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle bounds = new Rectangle(actual.getBounds());
        try {
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(actual);
            bounds.x += insets.left;
            bounds.y += insets.top;
            bounds.width = Math.max(1, bounds.width - insets.left - insets.right);
            bounds.height = Math.max(1, bounds.height - insets.top - insets.bottom);
        } catch (RuntimeException ignored) {}
        return bounds;
    }

    private static Dimension normalMinimumSize(Rectangle work) {
        return new Dimension(
                Math.max(1, Math.min(760, work.width)),
                Math.max(1, Math.min(460, work.height)));
    }

    static Rectangle fitBoundsToWorkArea(Rectangle desired, Rectangle work, Dimension minimum) {
        int minWidth = Math.min(Math.max(1, minimum.width), work.width);
        int minHeight = Math.min(Math.max(1, minimum.height), work.height);
        int width = Math.max(minWidth, Math.min(desired.width, work.width));
        int height = Math.max(minHeight, Math.min(desired.height, work.height));
        int x = Math.max(work.x, Math.min(desired.x, work.x + work.width - width));
        int y = Math.max(work.y, Math.min(desired.y, work.y + work.height - height));
        return new Rectangle(x, y, width, height);
    }

    private static Rectangle centeredBounds(Rectangle work, int width, int height) {
        int w = Math.min(Math.max(1, width), work.width);
        int h = Math.min(Math.max(1, height), work.height);
        return new Rectangle(
                work.x + Math.max(0, (work.width - w) / 2),
                work.y + Math.max(0, (work.height - h) / 2),
                w, h);
    }

    private void installWindowInteractions() {
        MouseAdapter resizeAdapter = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                if (playerView.isFullScreen()) return;
                int edges = resizeEdgesAt(e.getLocationOnScreen());
                e.getComponent().setCursor(Cursor.getPredefinedCursor(cursorForEdges(edges)));
            }

            @Override public void mousePressed(MouseEvent e) {
                if (playerView.isFullScreen() || !SwingUtilities.isLeftMouseButton(e)) return;
                int edges = resizeEdgesAt(e.getLocationOnScreen());
                if (edges == 0) return;
                resizeEdges = edges;
                resizeStartScreen = e.getLocationOnScreen();
                resizeStartBounds = getBounds();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (resizeEdges == 0 || resizeStartScreen == null || resizeStartBounds == null) return;
                resizeFrom(e.getLocationOnScreen());
            }

            @Override public void mouseReleased(MouseEvent e) {
                resizeEdges = 0;
                resizeStartScreen = null;
                resizeStartBounds = null;
            }
        };
        installMouseAdapterRecursively(getContentPane(), resizeAdapter);

        MouseAdapter moveAdapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (playerView.isFullScreen() || !SwingUtilities.isLeftMouseButton(e)) return;
                if (resizeEdgesAt(e.getLocationOnScreen()) != 0) return;
                moveStartScreen = e.getLocationOnScreen();
                moveStartBounds = getBounds();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (moveStartScreen == null || moveStartBounds == null
                        || resizeEdges != 0 || playerView.isFullScreen()) return;
                Point now = e.getLocationOnScreen();
                Rectangle work = usableWorkArea(getGraphicsConfiguration());
                Rectangle desired = new Rectangle(
                        moveStartBounds.x + now.x - moveStartScreen.x,
                        moveStartBounds.y + now.y - moveStartScreen.y,
                        moveStartBounds.width,
                        moveStartBounds.height);
                setBounds(fitBoundsToWorkArea(desired, work, getMinimumSize()));
            }

            @Override public void mouseReleased(MouseEvent e) {
                moveStartScreen = null;
                moveStartBounds = null;
            }
        };
        addMouseListener(moveAdapter);
        addMouseMotionListener(moveAdapter);
    }

    private static void installMouseAdapterRecursively(Component component, MouseAdapter adapter) {
        component.addMouseListener(adapter);
        component.addMouseMotionListener(adapter);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installMouseAdapterRecursively(child, adapter);
            }
        }
    }

    private int resizeEdgesAt(Point screen) {
        Rectangle bounds = getBounds();
        int edges = 0;
        if (Math.abs(screen.y - bounds.y) <= RESIZE_MARGIN) edges |= RESIZE_NORTH;
        if (Math.abs(screen.y - (bounds.y + bounds.height)) <= RESIZE_MARGIN) edges |= RESIZE_SOUTH;
        if (Math.abs(screen.x - bounds.x) <= RESIZE_MARGIN) edges |= RESIZE_WEST;
        if (Math.abs(screen.x - (bounds.x + bounds.width)) <= RESIZE_MARGIN) edges |= RESIZE_EAST;
        return edges;
    }

    private static int cursorForEdges(int edges) {
        if ((edges & RESIZE_NORTH) != 0 && (edges & RESIZE_WEST) != 0) return Cursor.NW_RESIZE_CURSOR;
        if ((edges & RESIZE_NORTH) != 0 && (edges & RESIZE_EAST) != 0) return Cursor.NE_RESIZE_CURSOR;
        if ((edges & RESIZE_SOUTH) != 0 && (edges & RESIZE_WEST) != 0) return Cursor.SW_RESIZE_CURSOR;
        if ((edges & RESIZE_SOUTH) != 0 && (edges & RESIZE_EAST) != 0) return Cursor.SE_RESIZE_CURSOR;
        if ((edges & RESIZE_NORTH) != 0) return Cursor.N_RESIZE_CURSOR;
        if ((edges & RESIZE_SOUTH) != 0) return Cursor.S_RESIZE_CURSOR;
        if ((edges & RESIZE_WEST) != 0) return Cursor.W_RESIZE_CURSOR;
        if ((edges & RESIZE_EAST) != 0) return Cursor.E_RESIZE_CURSOR;
        return Cursor.DEFAULT_CURSOR;
    }

    private void resizeFrom(Point screen) {
        Rectangle original = resizeStartBounds;
        Rectangle work = usableWorkArea(getGraphicsConfiguration());
        Dimension minimum = getMinimumSize();
        int dx = screen.x - resizeStartScreen.x;
        int dy = screen.y - resizeStartScreen.y;

        int newX = original.x;
        int newY = original.y;
        int newW = original.width;
        int newH = original.height;

        if ((resizeEdges & RESIZE_WEST) != 0) {
            int desiredW = original.width - dx;
            newW = Math.max(minimum.width, desiredW);
            newX = original.x + (original.width - newW);
        } else if ((resizeEdges & RESIZE_EAST) != 0) {
            newW = Math.max(minimum.width, original.width + dx);
        }

        if ((resizeEdges & RESIZE_NORTH) != 0) {
            int desiredH = original.height - dy;
            newH = Math.max(minimum.height, desiredH);
            newY = original.y + (original.height - newH);
        } else if ((resizeEdges & RESIZE_SOUTH) != 0) {
            newH = Math.max(minimum.height, original.height + dy);
        }

        setBounds(fitBoundsToWorkArea(new Rectangle(newX, newY, newW, newH), work, minimum));
    }

    static boolean shouldSeekTimeline(boolean programmaticUpdate, boolean adjusting, double durationSeconds) {
        return !programmaticUpdate && !adjusting && durationSeconds > 0;
    }

    static void layoutPlayerLayers(
            JLayeredPane root, JComponent media, JComponent top, JComponent bottom) {
        int width = Math.max(0, root.getWidth());
        int height = Math.max(0, root.getHeight());

        media.setBounds(0, 0, width, height);

        int topHeight = Math.max(0, top.getPreferredSize().height);
        int bottomHeight = Math.max(0, bottom.getPreferredSize().height);
        top.setBounds(0, 0, width, Math.min(height, topHeight));
        bottom.setBounds(0, Math.max(0, height - bottomHeight),
                width, Math.min(height, bottomHeight));
    }

    @FunctionalInterface
    interface ProgressListener {
        void onProgress(double progressSeconds, double durationSeconds, boolean finalUpdate);
    }
}
