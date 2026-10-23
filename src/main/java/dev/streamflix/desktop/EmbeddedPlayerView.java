package dev.streamflix.desktop;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Streamflix-owned video player component that embeds directly inside MainFrame
 * or standalone frames. mpv is the underlying engine, while all controls and
 * chrome belong to Streamflix.
 */
final class EmbeddedPlayerView extends JPanel {
    private static final String CARD_LOADING = "loading";
    private static final String CARD_VIDEO = "video";
    private static volatile EmbeddedPlayerView activeView;

    private final Canvas videoSurface = new Canvas();
    private final JPanel mediaStage = new JPanel(new CardLayout());
    private final JLayeredPane layeredPane = new JLayeredPane();
    private final JPanel chromeTop = new JPanel(new BorderLayout(18, 0));
    private final JPanel chromeBottom = new JPanel();
    private final JLabel loadingTitle = Theme.heading("STREAMFLIX", 24f);
    private final JLabel loadingDetail = Theme.muted("Buscando un servidor compatible…");
    private final JProgressBar loadingProgress = new JProgressBar();

    private final JLabel mediaTitle = Theme.heading("Reproduciendo", 16f);
    private final JLabel serverLabel = Theme.muted("Preparando…");
    private final JLabel timeLabel = Theme.muted("00:00 / 00:00");
    private final JLabel status = Theme.muted("Preparando reproducción…");

    private final JButton playPause = Theme.primaryButton("Pausar", StreamflixIcons.Glyph.PAUSE);
    private final JButton back10 = Theme.button("−10 s");
    private final JButton forward10 = Theme.button("+10 s");
    private final JButton mute = Theme.iconButton(StreamflixIcons.Glyph.VOLUME, "Silenciar");
    private final JButton popOutButton = Theme.iconButton(StreamflixIcons.Glyph.PIN, "Abrir en ventana separada (Pop-out)");
    private final JButton fullscreenButton = Theme.iconButton(StreamflixIcons.Glyph.FULLSCREEN, "Pantalla completa (F)");
    private final JButton closeButton = Theme.iconButton(StreamflixIcons.Glyph.BACK, "Volver a Streamflix (Esc)");

    private final JSlider timeline = new JSlider(0, 1000, 0);
    private final JSlider volume = new JSlider(0, 100, 80);
    private final JButton audioButton = Theme.button("Audio");
    private final JButton subtitleButton = Theme.button("Subtítulos");
    private final JButton qualityButton = Theme.button("Calidad · Auto");
    private final JPopupMenu audioMenu = new JPopupMenu();
    private final JPopupMenu subtitleMenu = new JPopupMenu();
    private final JPopupMenu qualityMenu = new JPopupMenu();

    private final Timer refreshTimer;
    private final Timer chromeHideTimer;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean();
    private volatile MpvIpcClient ipc;
    private volatile boolean closing;
    private volatile double durationSeconds;
    private boolean updatingTimeline;
    private boolean updatingVolume;
    private volatile Models.Video currentVideo;
    private volatile String currentServerName;
    private volatile long currentRequestId;
    private volatile double lastKnownTime;
    private int consecutiveRefreshFailures;
    private int stalledTicks;
    private int recoveryAttempts;
    private boolean recovering;
    private EmbeddedPlayerWindow.ProgressListener progressListener;
    private Runnable playbackIssueListener = () -> {};
    private BooleanSupplier playbackIssueHandler = () -> false;
    private long lastProgressPublishNanos;
    private final Window appOwner;
    private boolean fullScreen;
    private Rectangle windowedBounds;
    private String currentHlsBitrateOverride;
    private final Runnable onClose;
    private Runnable onPopOut;

    EmbeddedPlayerView(Window owner, String title, Runnable onClose) {
        super(new BorderLayout());
        this.appOwner = owner;
        this.onClose = onClose != null ? onClose : () -> {};
        setBackground(Color.BLACK);

        mediaTitle.setText(title);
        qualityButton.setText("Calidad · " + PlaybackSettings.qualityLabel(PlaybackSettings.qualityProfile()));
        serverLabel.setVisible(false);
        videoSurface.setBackground(Color.BLACK);
        videoSurface.setFocusable(true);

        add(buildUi(), BorderLayout.CENTER);
        installActions();

        refreshTimer = new Timer(1000, e -> refreshStateAsync());
        refreshTimer.setCoalesce(true);
        chromeHideTimer = new Timer(2800, e -> {
            if (fullScreen) {
                chromeTop.setVisible(false);
                chromeBottom.setVisible(false);
            }
        });
        chromeHideTimer.setRepeats(false);

        activeView = this;
    }

    static EmbeddedPlayerView getActiveView() {
        return activeView;
    }

    void setOnPopOut(Runnable onPopOut) {
        this.onPopOut = onPopOut;
        popOutButton.setVisible(onPopOut != null);
    }

    Models.Video currentVideo() {
        return currentVideo;
    }

    String currentServerName() {
        return currentServerName;
    }

    long currentRequestId() {
        return currentRequestId;
    }

    double lastKnownTime() {
        return lastKnownTime;
    }

    boolean isFullScreen() {
        return fullScreen;
    }

    void setProgressListener(EmbeddedPlayerWindow.ProgressListener listener) {
        this.progressListener = listener;
    }

    void setPlaybackIssueListener(Runnable listener) {
        this.playbackIssueListener = listener == null ? () -> {} : listener;
    }

    void setPlaybackIssueHandler(BooleanSupplier handler) {
        this.playbackIssueHandler = handler == null ? () -> false : handler;
    }

    private JComponent buildUi() {
        layeredPane.setOpaque(true);
        layeredPane.setBackground(Color.BLACK);

        chromeTop.setBackground(new Color(8, 10, 15, 230));
        chromeTop.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        mediaTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverLabel.setFont(Theme.FONT.deriveFont(11.5f));
        titleBlock.add(mediaTitle);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(serverLabel);
        chromeTop.add(titleBlock, BorderLayout.WEST);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topActions.setOpaque(false);

        popOutButton.setPreferredSize(new Dimension(42, 36));
        popOutButton.setVisible(onPopOut != null);
        popOutButton.addActionListener(e -> {
            if (onPopOut != null) onPopOut.run();
        });
        topActions.add(popOutButton);

        fullscreenButton.setPreferredSize(new Dimension(42, 36));
        fullscreenButton.addActionListener(e -> toggleFullscreen());
        topActions.add(fullscreenButton);

        closeButton.setPreferredSize(new Dimension(42, 36));
        closeButton.addActionListener(e -> closeRequested());
        topActions.add(closeButton);

        chromeTop.add(topActions, BorderLayout.EAST);
        layeredPane.add(chromeTop, Integer.valueOf(100));

        mediaStage.setBackground(Color.BLACK);
        mediaStage.add(buildLoadingStage(), CARD_LOADING);

        JPanel videoWrap = new JPanel(new BorderLayout());
        videoWrap.setBackground(Color.BLACK);
        videoWrap.add(videoSurface, BorderLayout.CENTER);
        mediaStage.add(videoWrap, CARD_VIDEO);
        layeredPane.add(mediaStage, Integer.valueOf(0));

        chromeBottom.setBackground(new Color(8, 10, 15, 230));
        chromeBottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(10, 20, 14, 20)));
        chromeBottom.setLayout(new BoxLayout(chromeBottom, BoxLayout.Y_AXIS));

        JPanel progressRow = new JPanel(new BorderLayout(10, 0));
        progressRow.setOpaque(false);
        timeline.setOpaque(false);
        timeline.putClientProperty("JSlider.trackWidth", 4);
        timeline.putClientProperty("JSlider.thumbSize", new Dimension(13, 13));
        progressRow.add(timeline, BorderLayout.CENTER);
        timeLabel.setPreferredSize(new Dimension(112, 28));
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        progressRow.add(timeLabel, BorderLayout.EAST);
        chromeBottom.add(progressRow);
        chromeBottom.add(Box.createVerticalStrut(8));

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        left.setOpaque(false);
        left.add(playPause);
        left.add(back10);
        left.add(forward10);
        left.add(Box.createHorizontalStrut(8));

        mute.setPreferredSize(new Dimension(42, 36));
        left.add(mute);

        volume.setToolTipText("Volumen");
        volume.setPreferredSize(new Dimension(105, 30));
        volume.setOpaque(false);
        left.add(volume);
        actions.add(left, BorderLayout.WEST);

        JPanel tracks = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tracks.setOpaque(false);

        audioButton.setToolTipText("Seleccionar pista de audio");
        subtitleButton.setToolTipText("Seleccionar subtítulos");
        qualityButton.setToolTipText("Calidad y uso de datos");
        audioButton.setEnabled(false);
        subtitleButton.setEnabled(false);

        tracks.add(qualityButton);
        tracks.add(audioButton);
        tracks.add(subtitleButton);
        actions.add(tracks, BorderLayout.EAST);

        chromeBottom.add(actions);
        layeredPane.add(chromeBottom, Integer.valueOf(100));

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                EmbeddedPlayerWindow.layoutPlayerLayers(layeredPane, mediaStage, chromeTop, chromeBottom);
                updateResponsiveChrome(layeredPane.getWidth());
            }
        });
        SwingUtilities.invokeLater(() -> {
            EmbeddedPlayerWindow.layoutPlayerLayers(layeredPane, mediaStage, chromeTop, chromeBottom);
            updateResponsiveChrome(layeredPane.getWidth());
        });

        return layeredPane;
    }

    private JComponent buildLoadingStage() {
        JPanel stage = new JPanel(new GridBagLayout());
        stage.setBackground(Color.BLACK);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        loadingTitle.setForeground(Theme.TEXT);
        loadingTitle.setIcon(BrandMark.markIcon(30));
        loadingTitle.setIconTextGap(9);
        loadingTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingDetail.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingDetail.setFont(Theme.FONT.deriveFont(13f));

        loadingProgress.setIndeterminate(true);
        loadingProgress.setPreferredSize(new Dimension(260, 5));
        loadingProgress.setMaximumSize(new Dimension(260, 5));
        loadingProgress.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(loadingTitle);
        box.add(Box.createVerticalStrut(10));
        box.add(loadingDetail);
        box.add(Box.createVerticalStrut(18));
        box.add(loadingProgress);

        stage.add(box);
        return stage;
    }

    private void installActions() {
        playPause.addActionListener(e -> togglePause());
        back10.addActionListener(e -> seek(-10));
        forward10.addActionListener(e -> seek(10));
        mute.addActionListener(e -> toggleMute());

        timeline.addChangeListener(this::timelineChanged);
        volume.addChangeListener(e -> {
            if (!updatingVolume && !volume.getValueIsAdjusting()) {
                runCommand(() -> requireIpc().setProperty("volume", volume.getValue()));
            }
        });

        audioButton.addActionListener(e -> showTrackMenu(audioButton, audioMenu));
        subtitleButton.addActionListener(e -> showTrackMenu(subtitleButton, subtitleMenu));
        qualityButton.addActionListener(e -> showQualityMenu());

        // Keyboard navigation
        registerKeyboardAction(
                e -> closeRequested(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        registerKeyboardAction(
                e -> togglePause(),
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        registerKeyboardAction(
                e -> seek(-10),
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        registerKeyboardAction(
                e -> seek(10),
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        registerKeyboardAction(
                e -> toggleFullscreen(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        // Mouse motion revealing overlays
        MouseAdapter mouseMotion = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                revealChrome();
                if (e.getClickCount() == 2) toggleFullscreen();
            }
            @Override public void mouseMoved(MouseEvent e) { revealChrome(); }
            @Override public void mouseDragged(MouseEvent e) { revealChrome(); }
        };
        videoSurface.addMouseListener(mouseMotion);
        videoSurface.addMouseMotionListener(mouseMotion);
        layeredPane.addMouseListener(mouseMotion);
        layeredPane.addMouseMotionListener(mouseMotion);
    }

    private void closeRequested() {
        if (fullScreen) {
            exitFullscreen();
        } else {
            stopPlayback();
            onClose.run();
        }
    }

    private void timelineChanged(ChangeEvent e) {
        if (!EmbeddedPlayerWindow.shouldSeekTimeline(updatingTimeline, timeline.getValueIsAdjusting(), durationSeconds)) return;
        double target = durationSeconds * timeline.getValue() / 1000.0;
        runCommand(() -> requireIpc().setProperty("time-pos", target));
    }

    private void togglePause() {
        runCommand(() -> {
            Object raw = requireIpc().getProperty("pause");
            boolean paused = raw instanceof Boolean b && b;
            requireIpc().setProperty("pause", !paused);
            SwingUtilities.invokeLater(() -> setPlayPauseState(!paused));
        });
    }

    private void seek(int seconds) {
        runCommand(() -> requireIpc().command(List.of("seek", seconds, "relative+exact")));
    }

    private void toggleMute() {
        runCommand(() -> {
            Object raw = requireIpc().getProperty("mute");
            boolean muted = raw instanceof Boolean b && b;
            boolean next = !muted;
            requireIpc().setProperty("mute", next);
            SwingUtilities.invokeLater(() -> updateMuteButton(next));
        });
    }

    private void updateMuteButton(boolean muted) {
        Theme.setButtonIcon(mute, muted ? StreamflixIcons.Glyph.VOLUME_OFF : StreamflixIcons.Glyph.VOLUME);
        mute.setToolTipText(muted ? "Activar sonido" : "Silenciar");
        mute.getAccessibleContext().setAccessibleName(muted ? "Activar sonido" : "Silenciar");
    }

    private void setPlayPauseState(boolean paused) {
        playPause.setText(paused ? "Reanudar" : "Pausar");
        Theme.setButtonIcon(playPause, paused ? StreamflixIcons.Glyph.PLAY : StreamflixIcons.Glyph.PAUSE);
    }

    void toggleFullscreen() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window == null) window = appOwner;
        if (window instanceof JFrame frame) {
            GraphicsDevice device = frame.getGraphicsConfiguration().getDevice();
            if (!fullScreen) {
                windowedBounds = frame.getBounds();
                fullScreen = true;
                chromeHideTimer.stop();
                try {
                    device.setFullScreenWindow(frame);
                } catch (Exception ex) {
                    frame.setExtendedState(Frame.MAXIMIZED_BOTH);
                }
                revealChrome();
            } else {
                exitFullscreen();
            }
            fullscreenButton.setToolTipText(fullScreen
                    ? "Salir de pantalla completa (F)"
                    : "Pantalla completa (F)");
            videoSurface.requestFocusInWindow();
        }
    }

    void exitFullscreen() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window == null) window = appOwner;
        if (window instanceof JFrame frame) {
            GraphicsDevice device = frame.getGraphicsConfiguration().getDevice();
            chromeHideTimer.stop();
            try {
                if (device.getFullScreenWindow() == frame) {
                    device.setFullScreenWindow(null);
                }
            } catch (Exception ignored) {}
            fullScreen = false;
            chromeTop.setVisible(true);
            chromeBottom.setVisible(true);
            if (windowedBounds != null && windowedBounds.width > 0 && windowedBounds.height > 0) {
                frame.setBounds(windowedBounds);
            }
            fullscreenButton.setToolTipText("Pantalla completa (F)");
        }
    }

    void revealChrome() {
        chromeTop.setVisible(true);
        chromeBottom.setVisible(true);
        if (fullScreen) chromeHideTimer.restart();
        else chromeHideTimer.stop();
    }

    private void updateResponsiveChrome(int width) {
        boolean compact = width < 980;
        back10.setVisible(width >= 680);
        forward10.setVisible(width >= 680);
        volume.setVisible(width >= 820);
        qualityButton.setVisible(width >= 920);
        audioButton.setVisible(width >= 760);
        subtitleButton.setVisible(width >= 760);
        timeLabel.setVisible(width >= 500);

        fullscreenButton.setVisible(true);
        mute.setVisible(true);
        playPause.setVisible(true);

        chromeTop.revalidate();
        chromeBottom.revalidate();
    }

    private void showStage(String card) {
        ((CardLayout) mediaStage.getLayout()).show(mediaStage, card);
        mediaStage.revalidate();
        mediaStage.repaint();
    }

    void setPreparing(String message) {
        SwingUtilities.invokeLater(() -> {
            loadingDetail.setText(message);
            status.setText(message);
            loadingProgress.setIndeterminate(true);
            showStage(CARD_LOADING);
        });
    }

    void start(Models.Video video, String serverName, long requestId) throws Exception {
        start(video, serverName, requestId, 0.0);
    }

    void start(Models.Video video, String serverName, long requestId, double resumeAtSeconds) throws Exception {
        if (closing || !isDisplayable()) throw new IllegalStateException("El reproductor fue cerrado.");

        currentVideo = video;
        currentServerName = serverName;
        currentRequestId = requestId;
        lastKnownTime = 0;
        consecutiveRefreshFailures = 0;
        stalledTicks = 0;
        recoveryAttempts = 0;
        recovering = false;
        currentHlsBitrateOverride = null;
        qualityButton.setText("Calidad · " + PlaybackSettings.qualityLabel(PlaybackSettings.qualityProfile()));
        lastProgressPublishNanos = 0L;

        MpvIpcClient previous = ipc;
        ipc = null;
        if (previous != null) previous.close();

        long hwnd = windowHandle();
        String pipePath = "\\\\.\\pipe\\streamflix-mpv-" + UUID.randomUUID();
        setPreparing("Conectando con " + serverName + "…");

        MpvPlayer.playEmbedded(
                video, mediaTitle.getText(), requestId, hwnd, pipePath, currentHlsBitrateOverride);
        MpvIpcClient client = MpvIpcClient.connect(pipePath, 5000);

        try {
            if (closing || !isDisplayable()) {
                throw new IllegalStateException("El reproductor fue cerrado.");
            }

            waitUntilMediaReady(client, 30000);

            if (resumeAtSeconds > 1.0) {
                client.command(List.of("seek", resumeAtSeconds, "absolute+exact"));
                lastKnownTime = resumeAtSeconds;
            }

            try {
                Object rawVolume = client.getProperty("volume");
                Object rawMute = client.getProperty("mute");
                boolean muted = rawMute instanceof Boolean b && b;
                if (rawVolume instanceof Number n) {
                    SwingUtilities.invokeLater(() -> {
                        updatingVolume = true;
                        try {
                            volume.setValue((int) Math.round(n.doubleValue()));
                            updateMuteButton(muted);
                        } finally {
                            updatingVolume = false;
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> updateMuteButton(muted));
                }
            } catch (Exception ignored) {}

            this.ipc = client;
            refreshTracks();

            SwingUtilities.invokeLater(() -> {
                serverLabel.setText("Servidor · " + serverName);
                status.setForeground(Theme.MUTED);
                status.setText("Reproduciendo");
                loadingProgress.setIndeterminate(false);
                setPlayPauseState(false);
                showStage(CARD_VIDEO);
                refreshTimer.start();
                revealChrome();
            });
        } catch (Exception ex) {
            client.close();
            if (this.ipc == client) this.ipc = null;
            try { MpvPlayer.stopCurrent(); } catch (RuntimeException ignored) {}
            throw ex;
        }
    }

    void addExternalSubtitles(List<Models.Subtitle> subtitles) {
        if (subtitles == null || subtitles.isEmpty() || closing) return;

        Models.Video base = currentVideo;
        if (base == null) return;

        Models.Video extras = new Models.Video(base.source(), base.headers(), subtitles);
        List<Models.Subtitle> additions = SubtitleAggregator.additionalSubtitles(base, List.of(extras));
        if (additions.isEmpty()) return;

        Models.Video merged = SubtitleAggregator.merge(base, List.of(extras));
        currentVideo = merged;

        MpvIpcClient client = ipc;
        if (client == null) return;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                for (Models.Subtitle subtitle : additions) {
                    if (closing || ipc != client) break;
                    String label = subtitle.label() == null ? "" : subtitle.label();
                    client.command(List.of("sub-add", subtitle.file(), "auto", label));
                }
                return null;
            }

            @Override protected void done() {
                if (!closing && ipc == client) refreshTracks();
            }
        }.execute();
    }

    void showFailure(String message) {
        SwingUtilities.invokeLater(() -> {
            refreshTimer.stop();
            loadingProgress.setIndeterminate(false);
            loadingTitle.setText("No se pudo reproducir");
            loadingDetail.setText(message);
            status.setForeground(Theme.DANGER);
            status.setText(message);
            showStage(CARD_LOADING);
        });
    }

    private static void waitUntilMediaReady(MpvIpcClient client, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                Object duration = client.getProperty("duration");
                if (duration instanceof Number n && n.doubleValue() > 0) return;
            } catch (Exception ex) {
                last = ex;
            }
            Thread.sleep(250);
        }
        if (last != null) throw last;
        throw new IllegalStateException("El video no terminó de cargar.");
    }

    private void refreshStateAsync() {
        if (ipc == null || closing || recovering || !refreshInFlight.compareAndSet(false, true)) return;

        new SwingWorker<PlayerState, Void>() {
            @Override protected PlayerState doInBackground() throws Exception {
                MpvIpcClient client = requireIpc();
                Object timeRaw = client.getProperty("time-pos");
                Object durationRaw = client.getProperty("duration");
                Object pauseRaw = client.getProperty("pause");
                Object cacheRaw = client.getProperty("paused-for-cache");
                Object eofRaw = client.getProperty("eof-reached");

                double time = timeRaw instanceof Number n ? n.doubleValue() : 0.0;
                double duration = durationRaw instanceof Number n ? n.doubleValue() : 0.0;
                boolean paused = pauseRaw instanceof Boolean b && b;
                boolean pausedForCache = cacheRaw instanceof Boolean b && b;
                boolean eofReached = eofRaw instanceof Boolean b && b;
                return new PlayerState(time, duration, paused, pausedForCache, eofReached);
            }

            @Override protected void done() {
                refreshInFlight.set(false);
                if (closing || recovering) return;

                try {
                    PlayerState state = get();
                    consecutiveRefreshFailures = 0;
                    durationSeconds = state.duration();

                    if (!timeline.getValueIsAdjusting() && state.duration() > 0) {
                        updatingTimeline = true;
                        try {
                            timeline.setValue((int) Math.max(0, Math.min(1000,
                                    Math.round(state.time() / state.duration() * 1000))));
                        } finally {
                            updatingTimeline = false;
                        }
                    }

                    timeLabel.setText(formatTime(state.time()) + " / " + formatTime(state.duration()));
                    setPlayPauseState(state.paused());

                    if (state.eofReached()) {
                        stalledTicks = 0;
                        lastKnownTime = state.time();
                        status.setText("Finalizado");
                        return;
                    }

                    boolean moved = Math.abs(state.time() - lastKnownTime) >= 0.20;
                    if (moved) {
                        lastKnownTime = state.time();
                        stalledTicks = 0;
                        if (!state.paused()) status.setText("Reproduciendo");
                    } else if (state.paused()) {
                        stalledTicks = 0;
                    } else {
                        stalledTicks++;
                        if (state.pausedForCache()) status.setText("Cargando…");
                    }

                    publishProgress(state.time(), state.duration());

                    boolean automaticQuality = "auto".equals(PlaybackSettings.qualityProfile());
                    int recoveryThreshold = state.pausedForCache() && automaticQuality ? 6 : 20;
                    if (!state.paused() && stalledTicks >= recoveryThreshold) {
                        requestRecovery(state.pausedForCache()
                                ? "El servidor dejó de entregar datos."
                                : "La reproducción dejó de avanzar.");
                    }
                } catch (Exception ex) {
                    consecutiveRefreshFailures++;
                    boolean processGone = !MpvPlayer.isCurrentAlive();
                    if (processGone || consecutiveRefreshFailures >= 3) {
                        requestRecovery(processGone
                                ? "El motor de reproducción se cerró inesperadamente."
                                : "Se perdió la comunicación con el reproductor.");
                    }
                }
            }
        }.execute();
    }

    private void publishProgress(double progress, double duration) {
        EmbeddedPlayerWindow.ProgressListener listener = progressListener;
        if (listener == null || duration <= 0 || progress < 0) return;

        long now = System.nanoTime();
        if (lastProgressPublishNanos != 0L
                && now - lastProgressPublishNanos < java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) {
            return;
        }
        lastProgressPublishNanos = now;
        try {
            listener.onProgress(progress, duration, false);
        } catch (RuntimeException ignored) {}
    }

    void requestRecovery(String reason) {
        if (closing || recovering || currentVideo == null) return;

        try { playbackIssueListener.run(); } catch (RuntimeException ignored) {}

        try {
            if (playbackIssueHandler.getAsBoolean()) {
                refreshTimer.stop();
                setPreparing("Cambiando a otra señal…");
                return;
            }
        } catch (RuntimeException ex) {
            AppLog.warn("player", "Falló el handler de recuperación de señal", ex);
        }

        boolean automaticQuality = "auto".equals(PlaybackSettings.qualityProfile());
        int maxRecoveries = automaticQuality ? 2 : 1;
        if (recoveryAttempts >= maxRecoveries) {
            refreshTimer.stop();
            showFailure("La reproducción se detuvo y no pudo recuperarse automáticamente.");
            return;
        }

        if (automaticQuality) {
            currentHlsBitrateOverride = recoveryAttempts == 0 ? "2500000" : "1500000";
            qualityButton.setText("Calidad · Auto ↓");
        }

        recoveryAttempts++;
        recovering = true;
        refreshTimer.stop();

        double resumeAt = Math.max(0.0, lastKnownTime - 0.5);
        Models.Video video = currentVideo;
        String serverName = currentServerName == null ? "servidor" : currentServerName;
        long requestId = currentRequestId;

        setPreparing(automaticQuality
                ? "Ajustando calidad a tu conexión…"
                : "Recuperando reproducción…");

        MpvIpcClient previous = ipc;
        ipc = null;
        if (previous != null) previous.close();

        new SwingWorker<MpvIpcClient, Void>() {
            @Override protected MpvIpcClient doInBackground() throws Exception {
                long hwnd = windowHandle();
                String pipePath = "\\\\.\\pipe\\streamflix-mpv-" + UUID.randomUUID();

                MpvPlayer.playEmbedded(
                        video, mediaTitle.getText(), requestId, hwnd, pipePath, currentHlsBitrateOverride);
                MpvIpcClient client = MpvIpcClient.connect(pipePath, 5000);
                try {
                    waitUntilMediaReady(client, 30000);
                    if (resumeAt > 1.0) {
                        client.command(List.of("seek", resumeAt, "absolute+exact"));
                    }
                    return client;
                } catch (Exception ex) {
                    client.close();
                    try { MpvPlayer.stopCurrent(); } catch (RuntimeException ignored) {}
                    throw ex;
                }
            }

            @Override protected void done() {
                if (closing) {
                    recovering = false;
                    return;
                }

                try {
                    ipc = get();
                    consecutiveRefreshFailures = 0;
                    stalledTicks = 0;
                    lastKnownTime = resumeAt;
                    recovering = false;

                    serverLabel.setText("Servidor · " + serverName);
                    status.setForeground(Theme.MUTED);
                    status.setText("Reproduciendo");
                    loadingProgress.setIndeterminate(false);
                    setPlayPauseState(false);
                    showStage(CARD_VIDEO);
                    refreshTracks();
                    refreshTimer.start();
                } catch (Exception ex) {
                    recovering = false;
                    refreshTimer.stop();
                    showFailure("No se pudo recuperar la reproducción. " + reason);
                }
            }
        }.execute();
    }

    private void refreshTracks() {
        new SwingWorker<Tracks, Void>() {
            @Override protected Tracks doInBackground() throws Exception {
                List<?> list = List.of();
                for (int attempt = 0; attempt < 15; attempt++) {
                    Object raw = requireIpc().getProperty("track-list");
                    if (raw instanceof List<?> candidate && !candidate.isEmpty()) {
                        list = candidate;
                        break;
                    }
                    Thread.sleep(100);
                }
                if (list.isEmpty()) return new Tracks(List.of(), List.of());

                ArrayList<TrackOption> audio = new ArrayList<>();
                ArrayList<TrackOption> subtitles = new ArrayList<>();
                subtitles.add(new TrackOption(0, "Desactivados", "", "", false));

                for (Object item : list) {
                    if (!(item instanceof Map<?, ?>)) continue;
                    Map<String, Object> track = Json.object(item);
                    Integer id = Json.integer(track.get("id"));
                    if (id == null) continue;

                    String type = Json.string(track.get("type"));
                    String lang = Json.string(track.get("lang"));
                    String title = Json.string(track.get("title"));
                    boolean selected = Boolean.TRUE.equals(track.get("selected"));
                    String label = trackLabel(lang, title, id);

                    if ("audio".equals(type)) {
                        audio.add(new TrackOption(id, label, lang, title, selected));
                    } else if ("sub".equals(type)) {
                        subtitles.add(new TrackOption(id, label, lang, title, selected));
                    }
                }

                List<TrackOption> preferredAudio = applyPreferredTrack(
                        audio, false, PlaybackSettings.audioLanguage());
                List<TrackOption> preferredSubtitles = applyPreferredTrack(
                        subtitles, true, PlaybackSettings.subtitleLanguage());
                return new Tracks(preferredAudio, preferredSubtitles);
            }

            @Override protected void done() {
                try {
                    Tracks tracks = get();
                    populateTrackMenu(audioButton, audioMenu, tracks.audio(), false, "aid");
                    populateTrackMenu(subtitleButton, subtitleMenu, tracks.subtitles(), true, "sid");
                } catch (Exception ex) {
                    if (!closing) AppLog.warn("tracks", "No se pudieron preparar las pistas.", ex);
                }
            }
        }.execute();
    }

    private List<TrackOption> applyPreferredTrack(
            List<TrackOption> tracks, boolean subtitles, String preferred) throws Exception {
        if (tracks == null || tracks.isEmpty()) return List.of();
        if (!subtitles && "auto".equals(preferred)) return List.copyOf(tracks);

        int selectedId = -1;
        if (subtitles && "off".equals(preferred)) {
            selectedId = 0;
        } else {
            TrackOption preferredTrack = tracks.stream()
                    .filter(track -> track.id() != 0)
                    .filter(track -> MediaLanguage.matches(track.language(), track.title(), preferred))
                    .findFirst()
                    .orElse(null);
            if (preferredTrack != null) {
                selectedId = preferredTrack.id();
                AppLog.info("tracks",
                        (subtitles ? "Subtítulos" : "Audio")
                                + " preferidos " + preferred + " -> " + preferredTrack.label());
            } else if (subtitles) {
                selectedId = 0;
                AppLog.info("tracks",
                        "Sin subtítulos " + preferred + " identificables; se desactivan.");
            } else {
                return List.copyOf(tracks);
            }
        }

        String property = subtitles ? "sid" : "aid";
        requireIpc().setProperty(property, subtitles && selectedId == 0 ? "no" : selectedId);
        final int appliedId = selectedId;
        return tracks.stream()
                .map(track -> new TrackOption(
                        track.id(), track.label(), track.language(), track.title(),
                        track.id() == appliedId))
                .toList();
    }

    private void populateTrackMenu(JButton button, JPopupMenu menu, List<TrackOption> tracks,
                                   boolean allowOff, String property) {
        menu.removeAll();
        ButtonGroup group = new ButtonGroup();
        TrackOption selected = null;

        for (TrackOption track : tracks) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(track.label());
            item.setFont(Theme.FONT.deriveFont(12.5f));
            item.setForeground(Theme.TEXT);
            item.setBackground(Theme.PANEL);
            item.setSelected(track.selected());
            item.addActionListener(e -> {
                button.setText(track.id() == 0
                        ? (allowOff ? "Subtítulos" : "Audio")
                        : (allowOff ? "Subtítulos · " : "Audio · ") + shortTrackLabel(track.label()));
                runCommand(() -> requireIpc().setProperty(property,
                        allowOff && track.id() == 0 ? "no" : track.id()));
            });
            group.add(item);
            menu.add(item);
            if (track.selected()) selected = track;
        }

        if (allowOff && selected == null && !tracks.isEmpty()) {
            ((JRadioButtonMenuItem) menu.getComponent(0)).setSelected(true);
        }

        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        menu.setBackground(Theme.PANEL);

        int meaningful = tracks.size() - (allowOff ? 1 : 0);
        button.setEnabled(meaningful > 0);
        if (selected != null && selected.id() != 0) {
            button.setText((allowOff ? "Subtítulos · " : "Audio · ") + shortTrackLabel(selected.label()));
        } else {
            button.setText(allowOff ? "Subtítulos" : "Audio");
        }
    }

    private void showQualityMenu() {
        qualityMenu.removeAll();
        qualityMenu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        qualityMenu.setBackground(Theme.PANEL);

        String current = PlaybackSettings.qualityProfile();
        ButtonGroup group = new ButtonGroup();
        addQualityOption(group, "Automática", "auto", current);
        addQualityOption(group, "Ahorro de datos · ~1.5 Mbps", "saver", current);
        addQualityOption(group, "Equilibrada · ~3 Mbps", "balanced", current);
        addQualityOption(group, "Alta · ~6 Mbps", "high", current);
        addQualityOption(group, "Máxima disponible", "max", current);

        Dimension preferred = qualityMenu.getPreferredSize();
        int x = Math.max(0, qualityButton.getWidth() - preferred.width);
        int y = -preferred.height - 6;
        qualityMenu.show(qualityButton, x, y);
    }

    private void addQualityOption(ButtonGroup group, String label, String profile, String current) {
        JRadioButtonMenuItem option = new JRadioButtonMenuItem(label);
        option.setFont(Theme.FONT.deriveFont(12.5f));
        option.setForeground(Theme.TEXT);
        option.setBackground(Theme.PANEL);
        option.setSelected(profile.equals(current));
        option.addActionListener(e -> applyQualityProfile(profile));
        group.add(option);
        qualityMenu.add(option);
    }

    private void applyQualityProfile(String profile) {
        PlaybackSettings.saveQualityProfile(profile);
        currentHlsBitrateOverride = null;
        recoveryAttempts = 0;
        qualityButton.setText("Calidad · " + PlaybackSettings.qualityLabel(profile));

        if (ipc != null && currentVideo != null && !recovering) {
            restartForQuality(Math.max(0.0, lastKnownTime - 0.25), profile);
        }
    }

    private void restartForQuality(double resumeAt, String profile) {
        if (closing || recovering || currentVideo == null) return;

        recovering = true;
        refreshTimer.stop();

        Models.Video video = currentVideo;
        String serverName = currentServerName == null ? "servidor" : currentServerName;
        long requestId = currentRequestId;

        setPreparing("Aplicando calidad " + PlaybackSettings.qualityLabel(profile) + "…");

        MpvIpcClient previous = ipc;
        ipc = null;
        if (previous != null) previous.close();

        new SwingWorker<MpvIpcClient, Void>() {
            @Override protected MpvIpcClient doInBackground() throws Exception {
                long hwnd = windowHandle();
                String pipePath = "\\\\.\\pipe\\streamflix-mpv-" + UUID.randomUUID();

                MpvPlayer.playEmbedded(video, mediaTitle.getText(), requestId, hwnd, pipePath);
                MpvIpcClient client = MpvIpcClient.connect(pipePath, 5000);
                try {
                    waitUntilMediaReady(client, 30000);
                    if (resumeAt > 1.0) {
                        client.command(List.of("seek", resumeAt, "absolute+exact"));
                    }
                    return client;
                } catch (Exception ex) {
                    client.close();
                    try { MpvPlayer.stopCurrent(); } catch (RuntimeException ignored) {}
                    throw ex;
                }
            }

            @Override protected void done() {
                if (closing) {
                    recovering = false;
                    return;
                }

                try {
                    ipc = get();
                    consecutiveRefreshFailures = 0;
                    stalledTicks = 0;
                    lastKnownTime = resumeAt;
                    recovering = false;
                    status.setForeground(Theme.MUTED);
                    status.setText("Reproduciendo");
                    loadingProgress.setIndeterminate(false);
                    setPlayPauseState(false);
                    showStage(CARD_VIDEO);
                    refreshTracks();
                    refreshTimer.start();
                    revealChrome();
                } catch (Exception ex) {
                    recovering = false;
                    refreshTimer.stop();
                    showFailure("No se pudo aplicar la nueva calidad.");
                }
            }
        }.execute();
    }

    private static String shortTrackLabel(String label) {
        if (label == null || label.isBlank()) return "";
        String value = label.strip();
        return value.length() <= 14 ? value : value.substring(0, 13).strip() + "…";
    }

    private static void showTrackMenu(JButton button, JPopupMenu menu) {
        if (!button.isEnabled() || menu.getComponentCount() == 0) return;
        Dimension preferred = menu.getPreferredSize();
        int x = Math.max(0, button.getWidth() - preferred.width);
        int y = -preferred.height - 6;
        menu.show(button, x, y);
    }

    private long windowHandle() throws Exception {
        AtomicLong value = new AtomicLong();

        Runnable read = () -> {
            if (!videoSurface.isDisplayable()) return;
            Pointer pointer = Native.getComponentPointer(videoSurface);
            if (pointer != null) value.set(Pointer.nativeValue(pointer));
        };

        if (SwingUtilities.isEventDispatchThread()) read.run();
        else SwingUtilities.invokeAndWait(read);

        if (value.get() == 0L) throw new IllegalStateException("No se pudo preparar la superficie de video.");
        return value.get();
    }

    private void runCommand(ThrowingRunnable command) {
        if (ipc == null || closing) return;
        new Thread(() -> {
            try {
                command.run();
            } catch (Exception ex) {
                if (!closing) SwingUtilities.invokeLater(() -> status.setText("Control no disponible"));
            }
        }, "streamflix-player-control").start();
    }

    private MpvIpcClient requireIpc() {
        MpvIpcClient client = ipc;
        if (client == null) throw new IllegalStateException("Reproductor aún no conectado.");
        return client;
    }

    void stopPlayback() {
        if (closing) return;
        closing = true;
        refreshTimer.stop();
        chromeHideTimer.stop();

        if (fullScreen) {
            exitFullscreen();
        }

        MpvIpcClient client = ipc;
        ipc = null;
        if (client != null) client.close();

        if (!MpvPlayer.isShutdown()) {
            try { MpvPlayer.beginRequest(); } catch (RuntimeException ignored) {}
            try { MpvPlayer.stopCurrent(); } catch (RuntimeException ignored) {}
        }
        if (durationSeconds > 0 && lastKnownTime >= 0) {
            EmbeddedPlayerWindow.ProgressListener listener = progressListener;
            if (listener != null) {
                try { listener.onProgress(lastKnownTime, durationSeconds, true); }
                catch (RuntimeException ignored) {}
            }
        }

        if (activeView == this) activeView = null;
    }

    private static String trackLabel(String lang, String title, int id) {
        String language = lang == null || lang.isBlank() ? "" : lang.toUpperCase();
        String name = title == null || title.isBlank() ? "" : title.strip();

        if (!language.isBlank() && !name.isBlank()) return language + " · " + name;
        if (!language.isBlank()) return language;
        if (!name.isBlank()) return name;
        return "Pista " + id;
    }

    private static String formatTime(double rawSeconds) {
        if (!Double.isFinite(rawSeconds) || rawSeconds < 0) rawSeconds = 0;

        long total = Math.round(rawSeconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;

        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record TrackOption(
            int id, String label, String language, String title, boolean selected) {
        @Override public String toString() { return label; }
    }

    private record PlayerState(double time, double duration, boolean paused,
                               boolean pausedForCache, boolean eofReached) {}
    private record Tracks(List<TrackOption> audio, List<TrackOption> subtitles) {}
}