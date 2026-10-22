package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** In-app title detail route for the streaming navigation flow. */
final class TitleDetailView extends JPanel {
    private final Window owner;
    private final Provider provider;
    private final Models.ShowItem item;
    private final Runnable onBack;
    private final Runnable onHistoryChanged;
    private final Consumer<Models.ShowItem> onOpenRelated;
    private final boolean autoPlayRequested;
    private final ExtractorRegistry extractors = new ExtractorRegistry();

    private final JPanel episodeArea = new JPanel(new BorderLayout());
    private final JPanel recommendationArea = new JPanel(new BorderLayout());
    private final JLabel status = Theme.muted(" ");
    private volatile List<Models.Episode> episodes = List.of();

    TitleDetailView(Window owner, Provider provider, Models.ShowItem item, Runnable onBack) {
        this(owner, provider, item, onBack, false, () -> {});
    }

    TitleDetailView(Window owner, Provider provider, Models.ShowItem item, Runnable onBack, boolean autoPlay) {
        this(owner, provider, item, onBack, autoPlay, () -> {});
    }

    TitleDetailView(Window owner, Provider provider, Models.ShowItem item, Runnable onBack,
                    boolean autoPlay, Runnable onHistoryChanged) {
        this(owner, provider, item, onBack, autoPlay, onHistoryChanged, ignored -> {});
    }

    TitleDetailView(Window owner, Provider provider, Models.ShowItem item, Runnable onBack,
                    boolean autoPlay, Runnable onHistoryChanged,
                    Consumer<Models.ShowItem> onOpenRelated) {
        this.owner = owner;
        this.provider = provider;
        this.item = item;
        this.onBack = onBack;
        this.onHistoryChanged = onHistoryChanged == null ? () -> {} : onHistoryChanged;
        this.onOpenRelated = onOpenRelated == null ? ignored -> {} : onOpenRelated;
        this.autoPlayRequested = autoPlay;

        setLayout(new BorderLayout());
        setBackground(Theme.BG);
        add(buildBody(), BorderLayout.CENTER);

        if (item.type() == Models.ShowType.TV_SHOW) {
            loadEpisodes();
        } else if (autoPlay) {
            SwingUtilities.invokeLater(() -> {
                UserData.HistoryEntry historyEntry = UserData.getHistoryEntry(provider.id(), item);
                double resumeAt = historyEntry != null
                        && historyEntry.progressSeconds() > 15
                        && historyEntry.durationSeconds() > 0
                        && historyEntry.progressSeconds() < historyEntry.durationSeconds() * 0.95
                        ? historyEntry.progressSeconds()
                        : 0.0;
                chooseServerAndPlay(item.providerId(), item.title(), true, null, resumeAt, null);
            });
        }
        if (provider instanceof TmdbProvider) loadRecommendations();
    }

    private JComponent buildBody() {
        JPanel content = new JPanel();
        content.setBackground(Theme.BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JComponent hero = buildHero();
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(hero);

        if (item.type() == Models.ShowType.TV_SHOW) {
            JPanel episodesWrap = new JPanel(new BorderLayout());
            episodesWrap.setOpaque(false);
            episodesWrap.setBorder(new EmptyBorder(18, 40, 46, 40));
            episodeArea.setOpaque(false);

            JLabel loading = Theme.muted("Cargando episodios…");
            loading.setBorder(new EmptyBorder(24, 0, 24, 0));
            episodeArea.add(loading, BorderLayout.CENTER);

            episodesWrap.add(episodeArea, BorderLayout.CENTER);
            episodesWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(episodesWrap);
        }

        if (provider instanceof TmdbProvider) {
            JPanel recommendationsWrap = new JPanel(new BorderLayout());
            recommendationsWrap.setOpaque(false);
            recommendationsWrap.setBorder(new EmptyBorder(18, 40, 8, 40));
            recommendationArea.setOpaque(false);
            JLabel loading = Theme.muted("Cargando títulos relacionados…");
            loading.setBorder(new EmptyBorder(12, 0, 12, 0));
            recommendationArea.add(loading, BorderLayout.CENTER);
            recommendationsWrap.add(recommendationArea, BorderLayout.CENTER);
            recommendationsWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(recommendationsWrap);
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(30);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    private void loadRecommendations() {
        new SwingWorker<List<Models.ShowItem>, Void>() {
            @Override protected List<Models.ShowItem> doInBackground() throws Exception {
                return ((TmdbProvider) provider).recommendations(item);
            }

            @Override protected void done() {
                try {
                    renderRecommendations(get());
                } catch (Exception ex) {
                    recommendationArea.removeAll();
                    recommendationArea.add(Theme.muted(
                            "No se pudieron cargar títulos relacionados. Inténtalo de nuevo más tarde."),
                            BorderLayout.CENTER);
                    recommendationArea.revalidate();
                    recommendationArea.repaint();
                }
            }
        }.execute();
    }

    private void renderRecommendations(List<Models.ShowItem> recommendations) {
        recommendationArea.removeAll();
        if (recommendations == null || recommendations.isEmpty()) {
            recommendationArea.add(Theme.muted("No hay títulos relacionados disponibles."), BorderLayout.CENTER);
        } else {
            JPanel rail = new JPanel();
            rail.setOpaque(false);
            rail.setLayout(new BoxLayout(rail, BoxLayout.X_AXIS));
            List<Models.ShowItem> visibleRecommendations = recommendations.stream().limit(12).toList();
            for (int i = 0; i < visibleRecommendations.size(); i++) {
                Models.ShowItem recommendation = visibleRecommendations.get(i);
                rail.add(new LandscapeCard(recommendation, onOpenRelated));
                if (i < visibleRecommendations.size() - 1) rail.add(Box.createHorizontalStrut(14));
            }
            JPanel section = new JPanel();
            section.setOpaque(false);
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            JLabel heading = Theme.heading("También te puede gustar", 23f);
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel sectionHeading = new JPanel(new BorderLayout());
            sectionHeading.setOpaque(false);
            sectionHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
            sectionHeading.add(heading, BorderLayout.WEST);

            JPanel railActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            railActions.setOpaque(false);
            JButton previous = Theme.iconButton(StreamflixIcons.Glyph.CHEVRON_LEFT, "Anterior");
            JButton next = Theme.iconButton(StreamflixIcons.Glyph.CHEVRON_RIGHT, "Siguiente");
            previous.setToolTipText("Anterior");
            next.setToolTipText("Siguiente");
            previous.getAccessibleContext().setAccessibleName("Mostrar recomendaciones anteriores");
            next.getAccessibleContext().setAccessibleName("Mostrar más recomendaciones");
            previous.setPreferredSize(new Dimension(38, 34));
            next.setPreferredSize(new Dimension(38, 34));
            railActions.add(previous);
            railActions.add(next);
            sectionHeading.add(railActions, BorderLayout.EAST);

            JScrollPane scroll = new JScrollPane(rail);
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setWheelScrollingEnabled(false);
            scroll.getHorizontalScrollBar().setUnitIncrement(34);
            JScrollBar horizontal = scroll.getHorizontalScrollBar();
            Runnable updateArrows = () -> {
                int max = Math.max(horizontal.getMinimum(),
                        horizontal.getMaximum() - horizontal.getVisibleAmount());
                previous.setEnabled(horizontal.getValue() > horizontal.getMinimum());
                next.setEnabled(horizontal.getValue() < max);
            };
            horizontal.addAdjustmentListener(e -> updateArrows.run());
            previous.addActionListener(e -> scrollBarBy(horizontal, -1.0,
                    Math.max(240, scroll.getViewport().getWidth() - 100)));
            next.addActionListener(e -> scrollBarBy(horizontal, 1.0,
                    Math.max(240, scroll.getViewport().getWidth() - 100)));
            MouseWheelListener railWheel = e -> {
                boolean canScrollHorizontally = horizontal.getMaximum() - horizontal.getMinimum()
                        > horizontal.getVisibleAmount();
                if (canScrollHorizontally) {
                    scrollBarBy(horizontal, e.getPreciseWheelRotation(), 118);
                }
                e.consume();
            };
            installMouseWheelListenerRecursively(scroll.getViewport(), railWheel);
            scroll.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { updateArrows.run(); }
            });
            SwingUtilities.invokeLater(updateArrows);

            section.add(sectionHeading);
            section.add(Box.createVerticalStrut(12));
            scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            scroll.setPreferredSize(new Dimension(800, 198));
            section.add(scroll);
            recommendationArea.add(section, BorderLayout.CENTER);
        }
        recommendationArea.revalidate();
        recommendationArea.repaint();
    }

    private JComponent buildHero() {
        JLayeredPane hero = new JLayeredPane();
        hero.setPreferredSize(new Dimension(1200, 520));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 520));
        hero.setOpaque(true);
        hero.setBackground(Color.BLACK);

        String image = item.banner() != null && !item.banner().isBlank()
                ? item.banner() : item.poster();
        ArtworkPanel background = new ArtworkPanel(image);
        background.setFallbackText("");
        hero.add(background, Integer.valueOf(0));

        JPanel shade = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int w = getWidth();
                    int h = getHeight();
                    g2.setPaint(new GradientPaint(
                            0, 0, new Color(0, 0, 0, 220),
                            Math.max(1, (int) (w * 0.72)), 0, new Color(0, 0, 0, 24)));
                    g2.fillRect(0, 0, w, h);
                    g2.setPaint(new GradientPaint(
                            0, Math.max(1, (int) (h * 0.48)), new Color(0, 0, 0, 0),
                            0, h, Theme.BG));
                    g2.fillRect(0, 0, w, h);
                } finally {
                    g2.dispose();
                }
            }
        };
        shade.setOpaque(false);
        hero.add(shade, Integer.valueOf(1));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        JLabel eyebrow = Theme.eyebrow(item.type() == Models.ShowType.MOVIE ? "PELÍCULA" : "SERIE");
        eyebrow.setForeground(new Color(224, 226, 230));
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = Theme.heading(
                "<html><body style='width:720px'>" + escapeHtml(item.title()) + "</body></html>", 38f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (item.released() != null && !item.released().isBlank()) {
            String released = item.released().length() >= 4 ? item.released().substring(0, 4) : item.released();
            JLabel value = Theme.muted(released);
            value.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
            meta.add(value);
        }
        if (item.rating() != null && item.rating() > 0) {
            JLabel rating = new JLabel(String.format("%.1f / 10", item.rating()));
            rating.setForeground(new Color(232, 211, 96));
            rating.setFont(Theme.FONT_BOLD.deriveFont(12.5f));
            meta.add(rating);
        }
        if (item.runtimeMinutes() != null && item.runtimeMinutes() > 0) {
            JLabel runtime = Theme.muted(item.runtimeMinutes() + " min");
            runtime.setFont(Theme.FONT.deriveFont(12.5f));
            meta.add(runtime);
        }

        String overviewText = item.overview() == null || item.overview().isBlank()
                ? "Sin descripción disponible."
                : item.overview();
        JLabel overview = Theme.muted(
                "<html><body style='width:710px'>" + escapeHtml(shorten(overviewText, 380)) + "</body></html>");
        overview.setForeground(new Color(220, 223, 228));
        overview.setFont(Theme.FONT.deriveFont(14.5f));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        UserData.HistoryEntry historyEntry = UserData.getHistoryEntry(provider.id(), item);
        boolean resumable = isResumablePosition(historyEntry);

        if (item.type() == Models.ShowType.MOVIE) {
            double resumeAt = resumable ? historyEntry.progressSeconds() : 0.0;
            JButton play = Theme.primaryButton(resumable
                    ? "Continuar · " + formatTime(resumeAt)
                    : "Reproducir", StreamflixIcons.Glyph.PLAY);
            play.addActionListener(e -> chooseServerAndPlay(
                    item.providerId(), item.title(), true, null, resumeAt, null));
            actions.add(play);
        } else if (historyEntry != null
                && historyEntry.mediaId() != null
                && historyEntry.seasonNumber() != null
                && historyEntry.episodeNumber() != null) {
            Models.Episode resumeEpisode = new Models.Episode(
                    historyEntry.mediaId(),
                    historyEntry.seasonNumber(),
                    historyEntry.episodeNumber(),
                    historyEntry.mediaTitle(),
                    null,
                    null
            );
            String label = "Continuar T" + historyEntry.seasonNumber()
                    + ":E" + historyEntry.episodeNumber();
            JButton continueButton = Theme.primaryButton(label, StreamflixIcons.Glyph.PLAY);
            continueButton.addActionListener(e -> chooseServerAndPlay(
                    historyEntry.mediaId(),
                    historyEntry.mediaTitle() == null ? item.title() : historyEntry.mediaTitle(),
                    true,
                    resumeEpisode,
                    resumable ? historyEntry.progressSeconds() : 0.0,
                    null
            ));
            actions.add(continueButton);
        }

        boolean initiallyFavorite = UserData.isFavorite(provider.id(), item.id());
        JButton favorite = Theme.button(initiallyFavorite ? "En mi lista" : "Mi lista",
                initiallyFavorite ? StreamflixIcons.Glyph.CHECK : StreamflixIcons.Glyph.PLUS);
        favorite.addActionListener(e -> {
            UserData.toggleFavorite(provider.id(), item);
            boolean selected = UserData.isFavorite(provider.id(), item.id());
            favorite.setText(selected ? "En mi lista" : "Mi lista");
            Theme.setButtonIcon(favorite,
                    selected ? StreamflixIcons.Glyph.CHECK : StreamflixIcons.Glyph.PLUS);
        });
        actions.add(favorite);

        if (item.type() == Models.ShowType.MOVIE) {
            JButton servers = Theme.button("Opciones de reproducción", StreamflixIcons.Glyph.MORE);
            servers.addActionListener(e -> chooseServerAndPlay(
                    item.providerId(), item.title(), false, null, 0.0, servers));
            actions.add(servers);
        }

        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setFont(Theme.FONT.deriveFont(11.5f));

        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(9));
        copy.add(title);
        copy.add(Box.createVerticalStrut(10));
        copy.add(meta);
        copy.add(Box.createVerticalStrut(15));
        copy.add(overview);
        copy.add(Box.createVerticalStrut(20));
        copy.add(actions);
        copy.add(Box.createVerticalStrut(10));
        copy.add(status);

        hero.add(copy, Integer.valueOf(2));

        JButton back = Theme.button("Volver", StreamflixIcons.Glyph.BACK);
        back.addActionListener(e -> onBack.run());
        hero.add(back, Integer.valueOf(3));

        hero.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int w = hero.getWidth();
                int h = hero.getHeight();
                background.setBounds(0, 0, w, h);
                shade.setBounds(0, 0, w, h);
                int inset = Math.min(40, Math.max(18, w / 18));
                copy.setBounds(inset, Math.max(54, h - 360),
                        Math.min(780, Math.max(0, w - inset * 2)), Math.min(330, Math.max(0, h - 90)));
                back.setBounds(30, 24, 105, 38);
            }
        });

        SwingUtilities.invokeLater(() -> {
            int w = hero.getWidth();
            int h = hero.getHeight();
            background.setBounds(0, 0, w, h);
            shade.setBounds(0, 0, w, h);
            int inset = Math.min(40, Math.max(18, w / 18));
            copy.setBounds(inset, Math.max(54, h - 360),
                    Math.min(780, Math.max(0, w - inset * 2)), Math.min(330, Math.max(0, h - 90)));
            back.setBounds(30, 24, 105, 38);
        });

        return hero;
    }

    private void loadEpisodes() {
        new SwingWorker<List<Models.Episode>, Void>() {
            @Override protected List<Models.Episode> doInBackground() throws Exception {
                return provider.episodes(item);
            }

            @Override protected void done() {
                try {
                    episodes = get();
                    renderEpisodes();
                    if (autoPlayRequested) autoPlaySeries();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    episodeArea.removeAll();
                    episodeArea.add(errorLabel(cause), BorderLayout.CENTER);
                    episodeArea.revalidate();
                    episodeArea.repaint();
                }
            }
        }.execute();
    }

    private void renderEpisodes() {
        episodeArea.removeAll();

        Map<Integer, List<Models.Episode>> seasons = episodes.stream()
                .collect(Collectors.groupingBy(
                        Models.Episode::seasonNumber,
                        TreeMap::new,
                        Collectors.toList()));

        if (seasons.isEmpty()) {
            episodeArea.add(Theme.muted("No hay episodios disponibles."), BorderLayout.CENTER);
            return;
        }

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(0, 0, 15, 0));

        JLabel episodesTitle = Theme.heading("Episodios", 23f);
        episodesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(episodesTitle);
        header.add(Box.createVerticalStrut(12));

        JPanel seasonTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        seasonTabs.setOpaque(false);

        JScrollPane seasonScroll = new JScrollPane(seasonTabs);
        seasonScroll.setBorder(null);
        seasonScroll.setOpaque(false);
        seasonScroll.getViewport().setOpaque(false);
        seasonScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        seasonScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        seasonScroll.getHorizontalScrollBar().setUnitIncrement(24);
        seasonScroll.setPreferredSize(new Dimension(800, 48));
        seasonScroll.setWheelScrollingEnabled(false);
        seasonScroll.addMouseWheelListener(e -> {
            if (!e.isShiftDown()) return;
            JScrollBar bar = seasonScroll.getHorizontalScrollBar();
            int delta = (int) Math.round(e.getPreciseWheelRotation() * 72);
            int max = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
            bar.setValue(Math.max(bar.getMinimum(), Math.min(max, bar.getValue() + delta)));
            e.consume();
        });
        seasonScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(seasonScroll);
        episodeArea.add(header, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        episodeArea.add(rows, BorderLayout.CENTER);

        Map<Integer, JButton> seasonButtons = new LinkedHashMap<>();

        java.util.function.IntConsumer selectSeason = selectedSeason -> {
            rows.removeAll();

            for (Models.Episode episode : seasons.getOrDefault(selectedSeason, List.of())) {
                UserData.HistoryEntry episodeHistory =
                        UserData.getEpisodeHistoryEntry(provider.id(), item, episode);
                boolean currentEpisode = episodeHistory != null
                        && episodeHistory.durationSeconds() > 0;
                double episodeProgress = currentEpisode
                        ? Math.max(0.0, Math.min(1.0,
                                episodeHistory.progressSeconds() / episodeHistory.durationSeconds()))
                        : 0.0;
                double resumeAt = isResumablePosition(episodeHistory)
                        ? episodeHistory.progressSeconds()
                        : 0.0;

                EpisodeRow row = new EpisodeRow(
                        episode,
                        ep -> chooseServerAndPlay(
                                ep.id(),
                                item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(),
                                true,
                                ep,
                                resumeAt,
                                null),
                        (ep, invoker) -> chooseServerAndPlay(
                                ep.id(),
                                item.title() + " · T" + ep.seasonNumber() + "E" + ep.episodeNumber(),
                                false,
                                ep,
                                resumeAt,
                                invoker),
                        episodeProgress
                );
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                rows.add(row);
                rows.add(Box.createVerticalStrut(9));
            }

            for (var entry : seasonButtons.entrySet()) {
                Theme.setNavSelected(entry.getValue(), entry.getKey() == selectedSeason);
            }

            rows.revalidate();
            rows.repaint();
        };

        for (int season : seasons.keySet()) {
            String label = season == 0 ? "Especiales" : "Temporada " + season;
            JButton button = Theme.button(label);
            seasonButtons.put(season, button);
            button.addActionListener(e -> selectSeason.accept(season));
            seasonTabs.add(button);
        }

        UserData.HistoryEntry continueEntry = UserData.getHistoryEntry(provider.id(), item);
        int initialSeason = continueEntry != null
                && continueEntry.seasonNumber() != null
                && seasons.containsKey(continueEntry.seasonNumber())
                ? continueEntry.seasonNumber()
                : seasons.containsKey(1) ? 1 : seasons.keySet().iterator().next();
        selectSeason.accept(initialSeason);

        episodeArea.revalidate();
        episodeArea.repaint();
    }

    private void chooseServerAndPlay(String providerItemId, String mediaTitle, boolean autoPlay,
                                      Models.Episode episode, double resumeAtSeconds, Component manualInvoker) {
        MpvBootstrap.ensureReady(owner, ready -> {
            if (!ready) {
                status.setText("No se pudo preparar el reproductor");
                return;
            }
            chooseReadyServerAndPlay(providerItemId, mediaTitle, autoPlay,
                    episode, resumeAtSeconds, manualInvoker);
        });
    }

    private void chooseReadyServerAndPlay(String providerItemId, String mediaTitle, boolean autoPlay,
                                           Models.Episode episode, double resumeAtSeconds, Component manualInvoker) {
        long playbackRequest = MpvPlayer.beginRequest();
        status.setText(autoPlay ? "Preparando reproducción…" : "Buscando fuentes…");

        EmbeddedPlayerWindow preparingWindow = autoPlay
                ? configuredPlayer(mediaTitle, episode)
                : null;
        if (preparingWindow != null) preparingWindow.setPreparing("Buscando la mejor fuente…");

        if (autoPlay && VodSearchCoordinator.isVodProvider(provider)) {
            List<ProviderFallbackCoordinator.Candidate> candidates =
                    ProviderFallbackCoordinator.resolveCandidates(
                            provider, providerItemId, item, episode, ProviderRegistry.all());
            resolveVodAndPlay(candidates, 0, mediaTitle, playbackRequest, preparingWindow, episode, resumeAtSeconds);
            return;
        }

        new SwingWorker<List<Models.Server>, Void>() {
            @Override protected List<Models.Server> doInBackground() throws Exception {
                return PlaybackRecovery.run(() -> provider.servers(providerItemId));
            }

            @Override protected void done() {
                try {
                    List<Models.Server> servers = get();
                    if (servers.isEmpty()) {
                        if (preparingWindow != null) preparingWindow.showFailure("No hay fuentes disponibles.");
                        status.setText("No hay fuentes disponibles");
                        return;
                    }

                    if (autoPlay) {
                        resolveAnyAndPlay(
                                servers, providerItemId, mediaTitle, playbackRequest,
                                preparingWindow, episode, resumeAtSeconds);
                        return;
                    }

                    Component invoker = manualInvoker != null ? manualInvoker : TitleDetailView.this;
                    ServerPickerMenu.show(invoker, servers, choice -> {
                        EmbeddedPlayerWindow player = configuredPlayer(mediaTitle, episode);
                        if (choice.automatic()) {
                            if (VodSearchCoordinator.isVodProvider(provider)) {
                                List<ProviderFallbackCoordinator.Candidate> candidates =
                                        ProviderFallbackCoordinator.resolveCandidates(
                                                provider, providerItemId, item, episode, ProviderRegistry.all());
                                resolveVodAndPlay(candidates, 0, mediaTitle, playbackRequest, player, episode, resumeAtSeconds);
                            } else {
                                resolveAnyAndPlay(
                                        servers, providerItemId, mediaTitle, playbackRequest,
                                        player, episode, resumeAtSeconds);
                            }
                        } else {
                            resolveAndPlay(
                                    choice.server(), providerItemId, mediaTitle, playbackRequest,
                                    player, episode, resumeAtSeconds);
                        }
                    });
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (preparingWindow != null) {
                        preparingWindow.showFailure("No se pudo iniciar la reproducción. Prueba otra fuente.");
                    }
                    status.setText("No se pudo reproducir");
                }
            }
        }.execute();
    }

    private EmbeddedPlayerWindow configuredPlayer(String mediaTitle, Models.Episode episode) {
        EmbeddedPlayerWindow player = EmbeddedPlayerWindow.open(owner, mediaTitle);
        if (provider instanceof SportsResolvedProvider) return player;
        if (episode == null) {
            player.setProgressListener((progress, duration, finalUpdate) -> {
                if (finalUpdate) UserData.recordHistoryFinal(provider.id(), item, progress, duration);
                else UserData.recordHistoryAsync(provider.id(), item, progress, duration);
                onHistoryChanged.run();
            });
        } else {
            player.setProgressListener((progress, duration, finalUpdate) -> {
                if (finalUpdate) {
                    UserData.recordEpisodeHistoryFinal(provider.id(), item, episode, progress, duration);
                } else {
                    UserData.recordEpisodeHistoryAsync(provider.id(), item, episode, progress, duration);
                }
                onHistoryChanged.run();
            });
        }
        return player;
    }

    private void resolveAnyAndPlay(List<Models.Server> servers, String providerItemId,
                                   String mediaTitle,
                                   long playbackRequest, EmbeddedPlayerWindow player,
                                   Models.Episode episode, double resumeAtSeconds) {
        status.setText("Buscando la mejor fuente…");

        if (provider instanceof SportsResolvedProvider) {
            resolveSportsAndPlay(servers, 0, providerItemId, mediaTitle, playbackRequest,
                    player, episode, resumeAtSeconds);
            return;
        }

        new SwingWorker<Models.Server, Void>() {
            @Override protected Models.Server doInBackground() throws Exception {
                Exception last = null;
                List<Models.Server> playbackOrder = PlaybackServerStats.rank(servers);
                for (Models.Server server : playbackOrder) {
                    if (!player.isDisplayable()) throw new CancellationException("Reproductor cerrado.");
                    player.setPreparing("Probando " + server.name() + "…");
                    long startedAt = System.nanoTime();
                    long[] attemptStartedAt = { startedAt };
                    try {
                        Models.Video video = PlaybackRecovery.run(() -> {
                            attemptStartedAt[0] = System.nanoTime();
                            Models.Video resolved = requirePlayable(extractors.resolve(server));
                            player.start(resolved, server.name(), playbackRequest, resumeAtSeconds);
                            return resolved;
                        });
                        PlaybackServerStats.recordSuccess(server, elapsedMillis(attemptStartedAt[0]));
                        player.setPlaybackIssueListener(() ->
                                PlaybackServerStats.recordFailure(server, 1));
                        enrichSubtitlesAsync(providerItemId, server, video, player, episode);
                        return server;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    } catch (CancellationException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        PlaybackServerStats.recordFailure(server, elapsedMillis(startedAt));
                        last = ex;
                    }
                }
                throw new IllegalStateException("Ninguna fuente pudo iniciar la reproducción.", last);
            }

            @Override protected void done() {
                try {
                    Models.Server server = get();
                    recordOpened(episode);
                    status.setText("Reproduciendo");
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
                    if (player.isDisplayable()) {
                        player.showFailure("No se encontró una fuente compatible. Prueba otra opción.");
                    }
                    status.setText("No se pudo reproducir");
                }
            }
        }.execute();
    }

    private void resolveVodAndPlay(List<ProviderFallbackCoordinator.Candidate> candidates, int candidateIndex,
                                   String mediaTitle,
                                   long playbackRequest, EmbeddedPlayerWindow player,
                                   Models.Episode episode, double resumeAtSeconds) {
        if (candidateIndex >= candidates.size()) {
            if (player.isDisplayable()) {
                player.showFailure("No se encontró una fuente compatible en ningún proveedor.");
            }
            status.setText("No se pudo reproducir en ningún proveedor");
            return;
        }

        ProviderFallbackCoordinator.Candidate candidate = candidates.get(candidateIndex);
        Provider candProvider = candidate.provider();
        status.setText("Probando " + candProvider.name() + "…");
        if (player.isDisplayable()) player.setPreparing("Probando " + candProvider.name() + "…");

        new SwingWorker<Models.Server, Void>() {
            private String resolvedItemId;

            @Override protected Models.Server doInBackground() throws Exception {
                long startedAt = System.nanoTime();
                try {
                    ProviderFallbackCoordinator.Candidate resolved =
                            ProviderFallbackCoordinator.resolveCandidateTarget(candidate, item, episode);
                    if (resolved == null || resolved.providerItemId() == null || resolved.providerItemId().isBlank()) {
                        throw new IllegalStateException("Candidato no disponible en " + candProvider.name());
                    }
                    resolvedItemId = resolved.providerItemId();

                    List<Models.Server> servers = PlaybackRecovery.run(() -> candProvider.servers(resolvedItemId));
                    if (servers == null || servers.isEmpty()) {
                        throw new IllegalStateException("Sin servidores en " + candProvider.name());
                    }

                    List<Models.Server> playbackOrder = PlaybackServerStats.rank(servers);
                    Exception lastServerEx = null;
                    for (Models.Server server : playbackOrder) {
                        if (!player.isDisplayable()) throw new CancellationException("Reproductor cerrado.");
                        player.setPreparing("Probando " + server.name() + " (" + candProvider.name() + ")…");
                        long serverStartedAt = System.nanoTime();
                        long[] attemptStartedAt = { serverStartedAt };
                        try {
                            Models.Video video = PlaybackRecovery.run(() -> {
                                attemptStartedAt[0] = System.nanoTime();
                                Models.Video resolvedVideo = requirePlayable(extractors.resolve(server));
                                player.start(resolvedVideo, server.name(), playbackRequest, resumeAtSeconds);
                                return resolvedVideo;
                            });

                            long elapsed = elapsedMillis(attemptStartedAt[0]);
                            PlaybackServerStats.recordSuccess(server, elapsed);
                            ProviderHealthStats.recordSuccess(candProvider.id(), elapsed);

                            player.setPlaybackIssueListener(() -> {
                                PlaybackServerStats.recordFailure(server, 1);
                                ProviderHealthStats.recordFailure(candProvider.id(), 1);
                            });

                            final int nextIndex = candidateIndex + 1;
                            player.setPlaybackIssueHandler(() -> {
                                if (nextIndex >= candidates.size()) return false;
                                double currentResume = Math.max(resumeAtSeconds, player.lastKnownTime());
                                SwingUtilities.invokeLater(() -> resolveVodAndPlay(
                                        candidates, nextIndex, mediaTitle, playbackRequest,
                                        player, episode, currentResume));
                                return true;
                            });

                            enrichSubtitlesAsync(resolvedItemId, server, video, player, episode);
                            return server;
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw ex;
                        } catch (CancellationException ex) {
                            throw ex;
                        } catch (Exception ex) {
                            PlaybackServerStats.recordFailure(server, elapsedMillis(serverStartedAt));
                            lastServerEx = ex;
                        }
                    }
                    throw new IllegalStateException("Ninguna fuente de " + candProvider.name() + " pudo reproducir.", lastServerEx);
                } catch (Exception ex) {
                    ProviderHealthStats.recordFailure(candProvider.id(), elapsedMillis(startedAt));
                    throw ex;
                }
            }

            @Override protected void done() {
                try {
                    Models.Server server = get();
                    recordOpened(episode);
                    status.setText("Reproduciendo (" + candProvider.name() + ")");
                } catch (Exception ex) {
                    if (!player.isDisplayable()) return;
                    AppLog.warn("vod-fallback", "Fallo en " + candProvider.name() + ", probando siguiente…");
                    resolveVodAndPlay(candidates, candidateIndex + 1, mediaTitle, playbackRequest,
                            player, episode, resumeAtSeconds);
                }
            }
        }.execute();
    }

    private void resolveSportsAndPlay(List<Models.Server> servers, int startIndex,
                                      String providerItemId, String mediaTitle,
                                      long playbackRequest, EmbeddedPlayerWindow player,
                                      Models.Episode episode, double resumeAtSeconds) {
        if (startIndex >= servers.size()) {
            if (player.isDisplayable()) player.showFailure("No quedan señales alternativas disponibles.");
            status.setText("No quedan señales disponibles");
            return;
        }

        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() throws Exception {
                Exception last = null;
                for (int i = startIndex; i < servers.size(); i++) {
                    Models.Server server = servers.get(i);
                    if (!player.isDisplayable()) throw new CancellationException("Reproductor cerrado.");
                    player.setPreparing("Probando " + server.name() + "…");
                    long startedAt = System.nanoTime();
                    try {
                        Models.Video video = requirePlayable(extractors.resolve(server));
                        player.start(video, server.name(), playbackRequest, resumeAtSeconds);
                        PlaybackServerStats.recordSuccess(server, elapsedMillis(startedAt));
                        final int nextIndex = i + 1;
                        player.setPlaybackIssueListener(() -> PlaybackServerStats.recordFailure(server, 1));
                        player.setPlaybackIssueHandler(() -> {
                            if (nextIndex >= servers.size()) return false;
                            SwingUtilities.invokeLater(() -> resolveSportsAndPlay(
                                    servers, nextIndex, providerItemId, mediaTitle,
                                    playbackRequest, player, episode, 0.0));
                            return true;
                        });
                        return i;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    } catch (CancellationException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        PlaybackServerStats.recordFailure(server, elapsedMillis(startedAt));
                        last = ex;
                    }
                }
                throw new IllegalStateException("Ninguna señal deportiva pudo iniciar la reproducción.", last);
            }

            @Override protected void done() {
                try {
                    get();
                    status.setText("Reproduciendo");
                } catch (Exception ex) {
                    if (player.isDisplayable()) {
                        player.showFailure("No se encontró una señal deportiva compatible.");
                    }
                    status.setText("No se pudo reproducir");
                }
            }
        }.execute();
    }

    private void resolveAndPlay(Models.Server server, String providerItemId,
                                String mediaTitle,
                                long playbackRequest, EmbeddedPlayerWindow player,
                                Models.Episode episode, double resumeAtSeconds) {
        status.setText("Preparando reproducción…");
        player.setPreparing("Preparando " + server.name() + "…");

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                long startedAt = System.nanoTime();
                long[] attemptStartedAt = { startedAt };
                try {
                    Models.Video video = PlaybackRecovery.run(() -> {
                        attemptStartedAt[0] = System.nanoTime();
                        Models.Video resolved = requirePlayable(extractors.resolve(server));
                        player.start(resolved, server.name(), playbackRequest, resumeAtSeconds);
                        return resolved;
                    });
                    PlaybackServerStats.recordSuccess(server, elapsedMillis(attemptStartedAt[0]));
                    player.setPlaybackIssueListener(() ->
                            PlaybackServerStats.recordFailure(server, 1));
                    enrichSubtitlesAsync(providerItemId, server, video, player, episode);
                    return null;
                } catch (Exception ex) {
                    PlaybackServerStats.recordFailure(server, elapsedMillis(startedAt));
                    throw ex;
                }
            }

            @Override protected void done() {
                try {
                    get();
                    recordOpened(episode);
                    status.setText("Reproduciendo");
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
                    if (player.isDisplayable()) {
                        player.showFailure("No se pudo iniciar la reproducción. Prueba otra fuente.");
                    }
                    status.setText("No se pudo reproducir");
                }
            }
        }.execute();
    }

    private void enrichSubtitlesAsync(String providerItemId, Models.Server selected,
                                      Models.Video primary, EmbeddedPlayerWindow player,
                                      Models.Episode episode) {
        if (!(provider instanceof TmdbProvider) || primary == null || !player.isDisplayable()) return;

        new SwingWorker<List<Models.Subtitle>, Void>() {
            @Override protected List<Models.Subtitle> doInBackground() throws Exception {
                List<Models.Server> compatible = SubtitleAggregator.compatibleServers(
                        provider, item, episode, providerItemId, List.of(selected));
                ArrayList<Models.Video> subtitleVideos = new ArrayList<>();
                for (Models.Server candidate : compatible) {
                    if (candidate == null || candidate.equals(selected)) continue;
                    try {
                        Models.Video video = PlaybackRecovery.run(
                                () -> requirePlayable(extractors.resolve(candidate)));
                        if (video.subtitles() != null && !video.subtitles().isEmpty()) {
                            subtitleVideos.add(video);
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    } catch (CancellationException ex) {
                        throw ex;
                    } catch (Exception ignored) {
                        // Subtitle enrichment is best effort and never blocks the playing source.
                    }
                }
                return SubtitleAggregator.additionalSubtitles(primary, subtitleVideos);
            }

            @Override protected void done() {
                try {
                    List<Models.Subtitle> additions = get();
                    if (player.isDisplayable() && !additions.isEmpty()) {
                        player.addExternalSubtitles(additions);
                    }
                } catch (Exception ignored) {
                    // The primary source is already playing; enrichment failure is non-fatal.
                }
            }
        }.execute();
    }

    private static void installMouseWheelListenerRecursively(
            Component component, MouseWheelListener listener) {
        component.addMouseWheelListener(listener);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installMouseWheelListenerRecursively(child, listener);
            }
        }
    }

    private static void scrollBarBy(JScrollBar bar, double preciseRotation, int pixelsPerNotch) {
        if (bar == null || !Double.isFinite(preciseRotation) || preciseRotation == 0.0) return;

        int min = bar.getMinimum();
        int max = Math.max(min, bar.getMaximum() - bar.getVisibleAmount());
        Timer activeTimer = (Timer) bar.getClientProperty("streamflix.smoothScrollTimer");
        Object targetProperty = bar.getClientProperty("streamflix.smoothScrollTarget");
        double base = activeTimer != null && activeTimer.isRunning() && targetProperty instanceof Double target
                ? target
                : bar.getValue();
        double target = Math.max(min, Math.min(max, base + preciseRotation * pixelsPerNotch));
        bar.putClientProperty("streamflix.smoothScrollTarget", target);

        if (activeTimer != null && activeTimer.isRunning()) return;

        Timer timer = new Timer(16, null);
        timer.setCoalesce(true);
        timer.addActionListener(e -> {
            Object value = bar.getClientProperty("streamflix.smoothScrollTarget");
            double desired = value instanceof Double d ? d : bar.getValue();
            int current = bar.getValue();
            double distance = desired - current;

            if (Math.abs(distance) <= 1.0) {
                bar.setValue((int) Math.round(desired));
                ((Timer) e.getSource()).stop();
                return;
            }

            int step = (int) Math.round(distance * 0.24);
            if (step == 0) step = distance > 0 ? 1 : -1;
            bar.setValue(Math.max(min, Math.min(max, current + step)));
        });
        bar.putClientProperty("streamflix.smoothScrollTimer", timer);
        timer.start();
    }

    private static Models.Video requirePlayable(Models.Video video) {
        if (video == null || video.source() == null || video.source().isBlank()) {
            throw new IllegalStateException("La fuente no devolvió un video reproducible.");
        }
        return video;
    }

    private void autoPlaySeries() {
        if (episodes.isEmpty()) return;

        UserData.HistoryEntry historyEntry = UserData.getHistoryEntry(provider.id(), item);
        Models.Episode target = null;
        double resumeAt = 0.0;

        if (historyEntry != null && historyEntry.mediaId() != null) {
            target = episodes.stream()
                    .filter(ep -> historyEntry.mediaId().equals(ep.id()))
                    .findFirst()
                    .orElse(null);
            if (target != null && isResumablePosition(historyEntry)) {
                resumeAt = historyEntry.progressSeconds();
            }
        }

        if (target == null) {
            target = episodes.stream()
                    .filter(ep -> ep.seasonNumber() == 1)
                    .findFirst()
                    .orElse(episodes.get(0));
        }

        Models.Episode episode = target;
        double position = resumeAt;
        SwingUtilities.invokeLater(() -> chooseServerAndPlay(
                episode.id(),
                item.title() + " · T" + episode.seasonNumber() + "E" + episode.episodeNumber(),
                true,
                episode,
                position,
                null
        ));
    }

    private void recordOpened(Models.Episode episode) {
        if (provider instanceof SportsResolvedProvider) return;
        if (episode == null) UserData.recordHistory(provider.id(), item);
        else UserData.recordEpisodeHistory(provider.id(), item, episode, 0.0, 0.0);
        onHistoryChanged.run();
    }

    private JLabel errorLabel(Throwable ex) {
        JLabel label = new JLabel("No se pudieron cargar los episodios. Vuelve a intentarlo más tarde.");
        label.setForeground(Theme.DANGER);
        return label;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(1L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAtNanos));
    }

    private static boolean isResumablePosition(UserData.HistoryEntry entry) {
        return entry != null
                && entry.durationSeconds() > 0
                && entry.progressSeconds() > 1.0
                && entry.progressSeconds() < entry.durationSeconds() * 0.95;
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

    private static String shorten(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        int cut = value.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return value.substring(0, cut).strip() + "…";
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
