package dev.streamflix.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class MainFrame extends JFrame {
    enum Mode { HOME, MOVIES, SERIES, LIVE, SPORTS, SEARCH, FAVORITES }

    private final List<Provider> providers;
    private Provider provider;
    private final SmoothContentHost contentStack = new SmoothContentHost();
    private final JPanel detailHost = new JPanel(new BorderLayout());
    private final JPanel playerHost = new JPanel(new BorderLayout());
    private EmbeddedPlayerView activePlayerView;
    private JComponent topbar;
    private boolean detailOpen;
    private boolean homeNeedsRefresh;
    private final CatalogRamCache catalogRamCache = new CatalogRamCache();
    private record DetailRoute(Provider provider, Models.ShowItem item, boolean autoPlay) {}
    private final Deque<DetailRoute> detailHistoryStack = new ArrayDeque<>();
    private Provider currentDetailProvider;
    private Models.ShowItem currentDetailItem;
    private Component lastFocusedComponent;
    private String lastFocusedItemId;
    private final JPanel grid = new JPanel();
    private final JPanel homeRoot = new JPanel();
    private final JPanel pagingBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final JScrollPane catalogScroll = new JScrollPane();
    private final JScrollPane homeScroll = new JScrollPane();
    private final SportsDataProvider sportsDataProvider;
    private final SportsPlaybackService sportsPlaybackService;
    private final SportsHubPanel sportsHub;

    private final JLabel sectionTitle = Theme.heading("Inicio", 30f);
    private final JLabel sectionSubtitle = Theme.muted("Tu contenido, sin ruido");
    private final JLabel status = Theme.muted(" ");
    private final JPanel sectionHeaderPanel = new JPanel(new BorderLayout());
    private final JPanel catalogFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JTextField search = new JTextField(27);
    private final javax.swing.Timer searchDebounce;

    private final JButton homeButton = Theme.topNavButton("Inicio");
    private final JButton moviesButton = Theme.topNavButton("Películas");
    private final JButton seriesButton = Theme.topNavButton("Series");
    private final JButton liveButton = Theme.topNavButton("TV");
    private final JButton sportsButton = Theme.topNavButton("Deportes");
    private final JButton favoritesButton = Theme.topNavButton("Mi lista");

    private final JButton resetButton = Theme.button("Volver al inicio");
    private final JButton nextButton = Theme.button("Cargar más");
    private final JLabel pageLabel = Theme.muted("0 cargados");

    private final List<Models.ShowItem> loadedItems = new ArrayList<>();
    private Mode mode = Mode.HOME;
    private Mode searchScope = Mode.HOME;
    private boolean searchExplicit = true;
    private int page = 1;
    private String query = "";
    private Integer catalogGenreId;
    private String catalogGenreLabel = "Popular";
    private boolean hasMore = true;
    private SwingWorker<?, Void> activeWorker;

    MainFrame(List<Provider> providers) {
        super("Streamflix");
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Sin providers configurados");
        }
        this.providers = List.copyOf(providers);
        this.provider = preferredVodProvider(true);
        this.sportsDataProvider = new CompositeSportsProvider(
                new TheSportsDbProvider(),
                new SportScoreProvider());
        SportsChannelResolver sportsChannelResolver = new SportsChannelResolver(this.providers);
        this.sportsPlaybackService = new SportsPlaybackService(sportsChannelResolver);
        this.sportsHub = new SportsHubPanel(
                sportsDataProvider,
                event -> playSportsEvent(event, false),
                event -> playSportsEvent(event, true),
                () -> switchMode(Mode.LIVE));
        this.searchDebounce = new javax.swing.Timer(450, e -> runSearch(false));
        this.searchDebounce.setRepeats(false);

        setIconImages(BrandMark.windowIcons());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getRootPane().putClientProperty("JRootPane.titleBarBackground", Theme.SIDEBAR);
        getRootPane().putClientProperty("JRootPane.titleBarForeground", Theme.TEXT);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { MpvPlayer.shutdown(); }
        });
        addWindowStateListener(e -> EmbeddedPlayerWindow.onApplicationStateChanged(e.getNewState()));
        // Compact Windows desktops retain every primary control instead of clipping
        // navigation or the search affordance.
        setMinimumSize(new Dimension(960, 680));
        setSize(1480, 900);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        wireNavigation();
        add(buildWorkspace(), BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (mode != Mode.HOME) rebuildGridColumns();
            }
        });

        installShortcuts();
        updateNavigationState();
        updateHeader();
        loadHome();
        if (tmdbReady()) TmdbTitleIndex.warmUpTv();
    }

    @Override public void dispose() {
        try {
            if (activePlayerView != null) activePlayerView.stopPlayback();
            sportsHub.deactivate();
            MpvPlayer.shutdown();
        }
        finally { super.dispose(); }
    }

    private void wireNavigation() {
        homeButton.addActionListener(e -> switchMode(Mode.HOME));
        moviesButton.addActionListener(e -> openCatalog(Mode.MOVIES, null, "Popular"));
        seriesButton.addActionListener(e -> openCatalog(Mode.SERIES, null, "Popular"));
        liveButton.addActionListener(e -> switchMode(Mode.LIVE));
        sportsButton.addActionListener(e -> switchMode(Mode.SPORTS));
        favoritesButton.addActionListener(e -> switchMode(Mode.FAVORITES));
    }

    private JComponent buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(Theme.BG);

        JPanel north = new JPanel();
        north.setBackground(Theme.BG);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        topbar = buildTopbar();
        north.add(topbar);
        north.add(buildSectionHeader());

        workspace.add(north, BorderLayout.NORTH);
        workspace.add(buildContent(), BorderLayout.CENTER);
        return workspace;
    }

    private JComponent buildTopbar() {
        ResponsiveTopbar top = new ResponsiveTopbar();
        top.setBackground(new Color(8, 10, 15));
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JLabel logo = new JLabel("STREAMFLIX", BrandMark.markIcon(30), SwingConstants.LEFT);
        logo.setForeground(Theme.TEXT);
        logo.setFont(Theme.FONT_DISPLAY.deriveFont(19f));
        logo.setIconTextGap(9);
        javax.swing.border.Border logoPadding = new EmptyBorder(0, 0, 0, 10);
        logo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 0)), logoPadding));
        logo.setFocusable(true);
        logo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logo.setToolTipText("Ir a Inicio");
        logo.getAccessibleContext().setAccessibleName("STREAMFLIX, ir a Inicio");
        Action goHome = new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                switchMode(Mode.HOME);
            }
        };
        logo.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "goHome");
        logo.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "goHome");
        logo.getActionMap().put("goHome", goHome);
        logo.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) goHome.actionPerformed(null);
            }
        });
        logo.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) {
                logo.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.FOCUS), logoPadding));
            }

            @Override public void focusLost(FocusEvent event) {
                logo.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0, 0, 0, 0)), logoPadding));
            }
        });
        left.add(logo);

        for (JButton button : List.of(homeButton, moviesButton, seriesButton, liveButton, sportsButton, favoritesButton)) {
            left.add(button);
        }
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);

        search.putClientProperty("JTextField.placeholderText", "Buscar películas y series");
        search.putClientProperty("JTextField.showClearButton", true);
        search.setPreferredSize(new Dimension(245, 38));
        search.setToolTipText("Buscar películas y series · Ctrl+F");
        search.addActionListener(e -> {
            searchDebounce.stop();
            runSearch(true);
        });
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                String text = search.getText().trim();
                if (text.length() >= 2) {
                    searchDebounce.restart();
                } else {
                    searchDebounce.stop();
                    if (mode == Mode.SPORTS) sportsHub.search(text);
                    if (text.isEmpty() && mode == Mode.SEARCH) {
                        SwingUtilities.invokeLater(() -> {
                            if (mode == Mode.SEARCH && search.getText().trim().isEmpty()) {
                                leaveSearch();
                            }
                        });
                    }
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        });
        tools.add(search);

        JButton settings = Theme.button("Ajustes", StreamflixIcons.Glyph.SETTINGS);
        settings.addActionListener(e -> openSettings());
        tools.add(settings);

        top.setSections(left, tools);
        return top;
    }

    private JComponent buildSectionHeader() {
        sectionHeaderPanel.setBackground(Theme.BG);
        sectionHeaderPanel.setBorder(new EmptyBorder(18, 34, 10, 34));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionSubtitle.setFont(Theme.FONT.deriveFont(13f));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setFont(Theme.FONT.deriveFont(11.5f));

        heading.add(sectionTitle);
        heading.add(Box.createVerticalStrut(4));
        heading.add(sectionSubtitle);
        heading.add(Box.createVerticalStrut(3));
        heading.add(status);

        catalogFilters.setOpaque(false);
        catalogFilters.setBorder(new EmptyBorder(12, 0, 0, 0));

        JPanel headerContent = new JPanel();
        headerContent.setOpaque(false);
        headerContent.setLayout(new BoxLayout(headerContent, BoxLayout.Y_AXIS));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        catalogFilters.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerContent.add(heading);
        headerContent.add(catalogFilters);

        sectionHeaderPanel.add(headerContent, BorderLayout.WEST);
        return sectionHeaderPanel;
    }

    private JComponent buildContent() {
        contentStack.setBackground(Theme.BG);

        JPanel catalogBody = new JPanel(new BorderLayout());
        catalogBody.setBackground(Theme.BG);

        grid.setBackground(Theme.BG);
        grid.setBorder(new EmptyBorder(8, 34, 20, 34));
        rebuildGridColumns();
        catalogBody.add(grid, BorderLayout.NORTH);

        pagingBar.setOpaque(false);
        pagingBar.setBorder(new EmptyBorder(6, 0, 32, 0));
        resetButton.addActionListener(e -> {
            page = 1;
            loadedItems.clear();
            loadPage(false);
        });
        nextButton.addActionListener(e -> {
            page++;
            loadPage(true);
        });
        pagingBar.add(resetButton);
        pagingBar.add(pageLabel);
        pagingBar.add(nextButton);
        catalogBody.add(pagingBar, BorderLayout.SOUTH);

        catalogScroll.setViewportView(catalogBody);
        catalogScroll.setBorder(null);
        catalogScroll.getViewport().setBackground(Theme.BG);
        catalogScroll.getVerticalScrollBar().setUnitIncrement(30);
        catalogScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        catalogScroll.getVerticalScrollBar().addAdjustmentListener(e -> maybeLoadMore());

        pagingBar.setVisible(false);

        homeRoot.setBackground(Theme.BG);
        homeRoot.setLayout(new BoxLayout(homeRoot, BoxLayout.Y_AXIS));
        homeRoot.setBorder(new EmptyBorder(0, 0, 36, 0));

        JPanel homeViewport = new WidthTrackingPanel(new BorderLayout());
        homeViewport.setBackground(Theme.BG);
        homeViewport.add(homeRoot, BorderLayout.NORTH);

        homeScroll.setViewportView(homeViewport);
        homeScroll.setBorder(null);
        homeScroll.getViewport().setBackground(Theme.BG);
        homeScroll.getVerticalScrollBar().setUnitIncrement(28);
        homeScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        homeScroll.setWheelScrollingEnabled(false);
        homeScroll.addMouseWheelListener(e -> {
            if (e.isConsumed()) return;
            scrollBarBy(homeScroll.getVerticalScrollBar(), e.getPreciseWheelRotation(), 92);
            e.consume();
        });

        detailHost.setBackground(Theme.BG);
        playerHost.setBackground(Color.BLACK);
        contentStack.addCard(homeScroll, Mode.HOME.name());
        contentStack.addCard(catalogScroll, "CATALOG");
        contentStack.addCard(sportsHub, Mode.SPORTS.name());
        contentStack.addCard(detailHost, "DETAIL");
        contentStack.addCard(playerHost, "PLAYER");
        return contentStack;
    }

    private CatalogRamCache.CatalogKey currentCatalogKey() {
        return CatalogRamCache.CatalogKey.of(mode, provider, catalogGenreId, query, searchScope);
    }

    private void saveCurrentModeScroll() {
        if (mode == Mode.HOME) {
            catalogRamCache.updateHomeScroll(TmdbSettings.catalogLanguage(), homeScroll.getVerticalScrollBar().getValue());
        } else if (mode == Mode.MOVIES || mode == Mode.SERIES || mode == Mode.LIVE || mode == Mode.FAVORITES || mode == Mode.SEARCH) {
            catalogRamCache.updateScroll(currentCatalogKey(), catalogScroll.getVerticalScrollBar().getValue());
        }
    }

    private void switchMode(Mode newMode) {
        if (newMode == mode && newMode != Mode.HOME) return;
        if (mode == Mode.SPORTS && newMode != Mode.SPORTS) sportsHub.deactivate();
        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
        searchDebounce.stop();

        saveCurrentModeScroll();

        detailOpen = false;
        detailHistoryStack.clear();
        mode = newMode;
        if (newMode != Mode.MOVIES && newMode != Mode.SERIES) {
            catalogGenreId = null;
            catalogGenreLabel = "Popular";
        }
        page = 1;
        hasMore = true;
        query = "";
        search.setText("");

        if (newMode == Mode.MOVIES) provider = preferredVodProvider(true);
        else if (newMode == Mode.SERIES) provider = preferredVodProvider(false);
        else if (newMode == Mode.LIVE) provider = preferredLiveProvider();

        updateNavigationState();
        updateHeader();

        if (newMode == Mode.HOME) {
            CatalogRamCache.HomeEntry cachedHome = catalogRamCache.getHome(TmdbSettings.catalogLanguage());
            if (cachedHome != null && !cachedHome.isExpired()) {
                if (homeNeedsRefresh) {
                    homeNeedsRefresh = false;
                    List<Models.ShowItem> freshHistory = UserData.getHistory().stream().limit(8).toList();
                    HomeData updated = catalogRamCache.updateHomeHistory(TmdbSettings.catalogLanguage(), freshHistory);
                    renderHome(updated != null ? updated : cachedHome.data());
                } else if (homeRoot.getComponentCount() == 0) {
                    renderHome(cachedHome.data());
                }
                homeScroll.getVerticalScrollBar().setValue(cachedHome.scrollPosition());
                contentStack.showCard(Mode.HOME.name());
                status.setText(" ");
                return;
            }
            loadHome();
        } else if (newMode == Mode.SPORTS) {
            contentStack.showCard(Mode.SPORTS.name(), () -> {
                sportsHub.search("");
                sportsHub.activate();
            });
        } else {
            CatalogRamCache.CatalogKey key = currentCatalogKey();
            CatalogRamCache.CatalogEntry cached = catalogRamCache.get(key);
            if (cached != null && !cached.isExpired()) {
                loadedItems.clear();
                loadedItems.addAll(cached.items());
                page = cached.page();
                hasMore = cached.hasMore();
                renderCatalog(loadedItems);
                catalogScroll.getVerticalScrollBar().setValue(cached.scrollPosition());
                contentStack.showCard("CATALOG");
                configurePaging(false, false);
                pageLabel.setText(loadedItems.size() + " cargados");
                status.setText(" ");
                setBusy(false, " ");
                return;
            }
            loadedItems.clear();
            loadPage(false);
        }
    }

    private Provider preferredVodProvider(boolean movies) {
        String language = TmdbSettings.catalogLanguage();

        Provider preferredTmdb = ProviderRegistry.get("en".equals(language) ? "tmdb-en" : "tmdb-es");
        if (preferredTmdb != null && (movies ? preferredTmdb.supportsMovies() : preferredTmdb.supportsTvShows())) {
            return preferredTmdb;
        }

        Provider fallbackTmdb = ProviderRegistry.get("en".equals(language) ? "tmdb-es" : "tmdb-en");
        if (fallbackTmdb != null && (movies ? fallbackTmdb.supportsMovies() : fallbackTmdb.supportsTvShows())) {
            return fallbackTmdb;
        }

        // Defensive fallback only for builds where TMDb providers were not registered at all.
        for (Provider p : providers) {
            if (p instanceof M3uLiveProvider) continue;
            if (movies ? p.supportsMovies() : p.supportsTvShows()) return p;
        }
        return providers.get(0);
    }

    private Provider preferredLiveProvider() {
        Provider plutoEs = ProviderRegistry.get("pluto-es");
        if (plutoEs != null) return plutoEs;
        return providers.stream().filter(M3uLiveProvider.class::isInstance).findFirst().orElse(providers.get(0));
    }

    private boolean tmdbReady() {
        try { return TmdbSettings.hasApiKey(); }
        catch (TmdbException ignored) { return false; }
    }

    private void runSearch(boolean explicit) {
        String q = search.getText().trim();
        if (q.isBlank()) return;

        if (mode == Mode.SPORTS) {
            sportsHub.search(q);
            status.setText("Filtrando eventos deportivos");
            return;
        }

        boolean keepSearchFocus = search.isFocusOwner();
        boolean continuingSearch = mode == Mode.SEARCH;
        Mode requestedScope = continuingSearch
                ? searchScope
                : normalizedSearchScope(mode);
        searchScope = requestedScope;
        searchExplicit = explicit;

        detailOpen = false;
        provider = switch (requestedScope) {
            case SERIES -> preferredVodProvider(false);
            case LIVE -> preferredLiveProvider();
            default -> preferredVodProvider(true);
        };
        mode = Mode.SEARCH;
        catalogGenreId = null;
        catalogGenreLabel = "Popular";
        query = q;
        page = 1;
        hasMore = true;
        if (!continuingSearch) loadedItems.clear();
        updateNavigationState();
        updateHeader();
        loadPage(false);

        // Search runs in the background. Never steal keyboard focus from a user
        // who is still typing while intermediate results are loading.
        if (keepSearchFocus) {
            SwingUtilities.invokeLater(() -> {
                if (search.isDisplayable()) search.requestFocusInWindow();
            });
        }
    }

    private void leaveSearch() {
        saveCurrentModeScroll();
        Mode target = searchScope;
        if (target == Mode.MOVIES || target == Mode.SERIES) {
            openCatalog(target, null, "Popular");
        } else if (target == Mode.LIVE) {
            switchMode(Mode.LIVE);
        } else {
            switchMode(Mode.HOME);
        }
    }

    static Mode normalizedSearchScope(Mode source) {
        return switch (source) {
            case MOVIES, SERIES, LIVE -> source;
            default -> Mode.HOME;
        };
    }

    static List<Models.ShowItem> filterSearchResults(
            List<Models.ShowItem> items, Mode scope) {
        if (items == null || items.isEmpty()) return List.of();
        return switch (scope) {
            case MOVIES -> items.stream()
                    .filter(item -> item.type() == Models.ShowType.MOVIE)
                    .toList();
            case SERIES -> items.stream()
                    .filter(item -> item.type() == Models.ShowType.TV_SHOW)
                    .toList();
            default -> List.copyOf(items);
        };
    }

    private void loadHome() {
        contentStack.showCard(Mode.HOME.name());
        CatalogRamCache.HomeEntry cachedHome = catalogRamCache.getHome(TmdbSettings.catalogLanguage());
        if (cachedHome != null && !cachedHome.isExpired()) {
            if (homeNeedsRefresh) {
                homeNeedsRefresh = false;
                List<Models.ShowItem> freshHistory = UserData.getHistory().stream().limit(8).toList();
                HomeData updated = catalogRamCache.updateHomeHistory(TmdbSettings.catalogLanguage(), freshHistory);
                renderHome(updated != null ? updated : cachedHome.data());
            } else if (homeRoot.getComponentCount() == 0) {
                renderHome(cachedHome.data());
            }
            homeScroll.getVerticalScrollBar().setValue(cachedHome.scrollPosition());
            status.setText(" ");
            return;
        }

        status.setText("Preparando tu inicio…");
        homeRoot.removeAll();
        homeRoot.add(buildHomeLoadingCard());
        homeRoot.revalidate();
        homeRoot.repaint();

        Provider movieSource = preferredVodProvider(true);
        Provider seriesSource = preferredVodProvider(false);
        Provider liveSource = preferredLiveProvider();

        activeWorker = new SwingWorker<HomeData, Void>() {
            @Override protected HomeData doInBackground() {
                List<Models.ShowItem> history = UserData.getHistory();

                CompletableFuture<List<Models.ShowItem>> moviesFuture =
                        loadAsync(movieSource, () -> movieSource.movies(1), 10);
                CompletableFuture<List<Models.ShowItem>> seriesFuture =
                        loadAsync(seriesSource, () -> seriesSource.tvShows(1), 10);
                CompletableFuture<List<Models.ShowItem>> liveFuture =
                        loadAsync(liveSource, () -> liveSource.tvShows(1), 10);
                CompletableFuture<SportsSnapshot> sportsFuture = CompletableFuture.supplyAsync(() -> {
                    try { return sportsDataProvider.load(); }
                    catch (Exception ex) {
                        AppLog.warn("sports", "No se pudo cargar Deportes para Inicio", ex);
                        return new SportsSnapshot(List.of(), List.of(), List.of(), true, "");
                    }
                });

                List<CompletableFuture<HomeShelf>> shelfFutures = new ArrayList<>();
                if (movieSource instanceof TmdbProvider tmdb && tmdbReady()) {
                    shelfFutures.add(loadShelfAsync(
                            Mode.MOVIES, 27,
                            "Terror", "Historias para ver con las luces apagadas",
                            movieSource, () -> tmdb.moviesByGenre(27, 1), 8));
                    shelfFutures.add(loadShelfAsync(
                            Mode.MOVIES, 53,
                            "Suspenso", "Películas con tensión de principio a fin",
                            movieSource, () -> tmdb.moviesByGenre(53, 1), 8));
                    shelfFutures.add(loadShelfAsync(
                            Mode.MOVIES, 18,
                            "Drama", "Historias intensas y personajes memorables",
                            movieSource, () -> tmdb.moviesByGenre(18, 1), 8));
                    shelfFutures.add(loadShelfAsync(
                            Mode.MOVIES, 35,
                            "Comedia", "Algo más ligero para ver ahora",
                            movieSource, () -> tmdb.moviesByGenre(35, 1), 8));
                }

                List<HomeShelf> discovery = shelfFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(shelf -> !shelf.items().isEmpty())
                        .toList();

                return new HomeData(
                        history.stream().limit(8).toList(),
                        moviesFuture.join(),
                        seriesFuture.join(),
                        liveFuture.join(),
                        sportsFuture.join(),
                        discovery
                );
            }

            @Override protected void done() {
                if (isCancelled()) return;
                try {
                    HomeData data = get();
                    renderHome(data);
                    catalogRamCache.putHome(
                            TmdbSettings.catalogLanguage(), data, homeScroll.getVerticalScrollBar().getValue());
                    status.setText(" ");
                } catch (Exception ex) {
                    renderHomeError(ex.getCause() == null ? ex : ex.getCause());
                }
            }
        };
        activeWorker.execute();
    }

    private JComponent buildHomeLoadingCard() {
        JPanel container = new JPanel(new GridBagLayout());
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(60, 34, 60, 34));

        JPanel card = Theme.surface();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(560, 160));
        card.setMaximumSize(new Dimension(560, 160));

        JLabel heading = Theme.heading("Preparando tu inicio", 20f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel body = Theme.muted("<html><body style='width:455px'>Conectando con el catálogo para organizar tus películas, series y recomendaciones…</body></html>");
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.setMaximumSize(new Dimension(460, 4));
        progress.setPreferredSize(new Dimension(460, 4));
        progress.setForeground(Theme.ACCENT);
        progress.setBackground(Theme.PANEL_ALT);

        card.add(heading);
        card.add(Box.createVerticalStrut(10));
        card.add(body);
        card.add(Box.createVerticalStrut(18));
        card.add(progress);

        container.add(card);
        return container;
    }

    private void renderHome(HomeData data) {
        homeRoot.removeAll();

        Models.ShowItem hero = java.util.stream.Stream.concat(
                        data.movies().stream(), data.series().stream())
                .filter(item -> item.banner() != null && !item.banner().isBlank())
                .max(java.util.Comparator.comparingDouble(
                        item -> item.rating() == null ? 0.0 : item.rating()))
                .orElseGet(() -> !data.series().isEmpty()
                        ? data.series().get(0)
                        : !data.movies().isEmpty() ? data.movies().get(0) : null);
        if (hero != null) {
            homeRoot.add(new HeroPanel(hero, this::playHeroItem, this::openDetails));
            homeRoot.add(Box.createVerticalStrut(10));
        }

        homeRoot.add(continueWatchingSection(data.history()));
        homeRoot.add(Box.createVerticalStrut(30));

        if (!data.movies().isEmpty()) {
            homeRoot.add(homeSection(
                    "Películas populares",
                    "Títulos destacados del catálogo",
                    data.movies(),
                    () -> openCatalog(Mode.MOVIES, null, "Popular")));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        if (!data.series().isEmpty()) {
            homeRoot.add(homeSection(
                    "Series populares",
                    "Historias para seguir viendo",
                    data.series(),
                    () -> openCatalog(Mode.SERIES, null, "Popular")));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        for (HomeShelf shelf : data.discovery()) {
            homeRoot.add(homeSection(
                    shelf.title(),
                    shelf.subtitle(),
                    shelf.items(),
                    () -> openCatalog(shelf.mode(), shelf.genreId(), shelf.title())));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        List<SportsEvent> liveSports = data.sports().live().stream()
                .sorted(java.util.Comparator.comparing(SportsFavorites::matches).reversed()
                        .thenComparing(SportsEvent::startsAt,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .limit(6)
                .toList();
        if (!liveSports.isEmpty()) {
            homeRoot.add(sportsHomeSection(liveSports));
            homeRoot.add(Box.createVerticalStrut(30));
        }

        if (!data.live().isEmpty()) {
            homeRoot.add(homeSection(
                    "TV en vivo",
                    "Canales disponibles ahora",
                    data.live(),
                    () -> switchMode(Mode.LIVE)));
        }

        if (homeRoot.getComponentCount() == 0) {
            homeRoot.add(stateCard("No pudimos cargar el inicio",
                    "Prueba Películas, Series o TV desde la navegación superior.", null, null));
        }

        homeRoot.revalidate();
        homeRoot.repaint();
    }

    private JComponent continueWatchingSection(List<Models.ShowItem> items) {
        JPanel section = new JPanel(new BorderLayout(0, 12));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(new EmptyBorder(0, 34, 0, 34));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel label = Theme.heading("Continuar viendo", 21f);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = Theme.muted("Retoma donde lo dejaste");
        sub.setFont(Theme.FONT.deriveFont(12f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(label);
        heading.add(Box.createVerticalStrut(3));
        heading.add(sub);
        section.add(heading, BorderLayout.NORTH);

        if (items.isEmpty()) {
            JPanel empty = new JPanel(new BorderLayout());
            empty.setBackground(Theme.PANEL);
            empty.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(16, 18, 16, 18)));
            JLabel message = Theme.muted("Aún no tienes nada pendiente. Reproduce un título para retomarlo aquí.");
            empty.add(message, BorderLayout.WEST);
            section.add(empty, BorderLayout.CENTER);
            section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 126));
            return section;
        }

        int count = Math.min(8, items.size());
        int columns = Math.max(1, Math.min(4, count));
        int rows = (count + columns - 1) / columns;

        JPanel cards = new JPanel(new GridLayout(0, columns, 12, 12));
        cards.setOpaque(false);
        for (int i = 0; i < count; i++) cards.add(cardFor(items.get(i)));

        JPanel cardsWrap = new JPanel(new BorderLayout());
        cardsWrap.setOpaque(false);
        cardsWrap.add(cards, BorderLayout.WEST);
        section.add(cardsWrap, BorderLayout.CENTER);

        int contentHeight = rows * 172 + Math.max(0, rows - 1) * 12;
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentHeight + 64));
        return section;
    }

    private JComponent homeSection(
            String title, String subtitle, List<Models.ShowItem> items, Runnable viewMore) {
        JPanel section = new JPanel(new BorderLayout(0, 12));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));
        section.setBorder(new EmptyBorder(0, 34, 0, 34));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel label = Theme.heading(title, 21f);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = Theme.muted(subtitle);
        sub.setFont(Theme.FONT.deriveFont(12f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(label);
        text.add(Box.createVerticalStrut(3));
        text.add(sub);
        heading.add(text, BorderLayout.WEST);

        JPanel railActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        railActions.setOpaque(false);
        JButton previous = Theme.iconButton(StreamflixIcons.Glyph.CHEVRON_LEFT, "Anterior");
        JButton next = Theme.iconButton(StreamflixIcons.Glyph.CHEVRON_RIGHT, "Siguiente");
        previous.setToolTipText("Anterior");
        next.setToolTipText("Siguiente");
        previous.setPreferredSize(new Dimension(38, 34));
        next.setPreferredSize(new Dimension(38, 34));
        railActions.add(previous);
        railActions.add(next);

        if (viewMore != null) {
            JButton more = Theme.button("Ver más", StreamflixIcons.Glyph.MORE);
            more.addActionListener(e -> viewMore.run());
            railActions.add(more);
        }
        heading.add(railActions, BorderLayout.EAST);
        section.add(heading, BorderLayout.NORTH);

        JPanel rail = new JPanel();
        rail.setOpaque(false);
        rail.setLayout(new BoxLayout(rail, BoxLayout.X_AXIS));
        rail.setBorder(new EmptyBorder(1, 1, 8, 8));

        for (int i = 0; i < items.size(); i++) {
            rail.add(cardFor(items.get(i)));
            if (i < items.size() - 1) rail.add(Box.createHorizontalStrut(12));
        }

        JScrollPane scroll = new JScrollPane(rail);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar horizontal = scroll.getHorizontalScrollBar();
        horizontal.setUnitIncrement(34);
        scroll.setWheelScrollingEnabled(false);

        Runnable updateArrows = () -> {
            int max = Math.max(horizontal.getMinimum(),
                    horizontal.getMaximum() - horizontal.getVisibleAmount());
            previous.setEnabled(horizontal.getValue() > horizontal.getMinimum());
            next.setEnabled(horizontal.getValue() < max);
        };
        horizontal.addAdjustmentListener(e -> updateArrows.run());

        previous.addActionListener(e ->
                scrollBarBy(horizontal, -1.0, Math.max(240, scroll.getViewport().getWidth() - 100)));
        next.addActionListener(e ->
                scrollBarBy(horizontal, 1.0, Math.max(240, scroll.getViewport().getWidth() - 100)));

        MouseWheelListener railWheel = e -> {
            boolean canScrollHorizontally = horizontal.getMaximum() - horizontal.getMinimum()
                    > horizontal.getVisibleAmount();
            if (canScrollHorizontally) {
                scrollBarBy(horizontal, e.getPreciseWheelRotation(), 118);
            }
            // This listener exists only inside the card viewport. Headers, margins and
            // every other Home area keep the normal vertical page wheel behavior.
            e.consume();
        };
        installMouseWheelListenerRecursively(scroll.getViewport(), railWheel);

        scroll.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                updateArrows.run();
            }
        });
        SwingUtilities.invokeLater(updateArrows);

        scroll.setPreferredSize(new Dimension(800, 198));
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    private JComponent sportsHomeSection(List<SportsEvent> events) {
        JPanel section = new JPanel(new BorderLayout(0, 12));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 225));
        section.setBorder(new EmptyBorder(0, 34, 0, 34));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = Theme.heading("Deportes en vivo", 21f);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = Theme.muted("Eventos que están ocurriendo ahora");
        subtitle.setFont(Theme.FONT.deriveFont(12f));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(3));
        text.add(subtitle);
        heading.add(text, BorderLayout.WEST);

        JButton more = Theme.button("Ver Deportes", StreamflixIcons.Glyph.MORE);
        more.addActionListener(e -> switchMode(Mode.SPORTS));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(more);
        heading.add(actions, BorderLayout.EAST);
        section.add(heading, BorderLayout.NORTH);

        JPanel rail = new JPanel();
        rail.setOpaque(false);
        rail.setLayout(new BoxLayout(rail, BoxLayout.X_AXIS));
        rail.setBorder(new EmptyBorder(1, 1, 8, 8));
        for (int i = 0; i < events.size(); i++) {
            rail.add(new SportsCompactCard(events.get(i), event -> playSportsEvent(event, false)));
            if (i < events.size() - 1) rail.add(Box.createHorizontalStrut(12));
        }

        JScrollPane scroll = new JScrollPane(rail);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.getHorizontalScrollBar().setUnitIncrement(34);
        scroll.setPreferredSize(new Dimension(800, 166));
        section.add(scroll, BorderLayout.CENTER);
        return section;
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
        javax.swing.Timer activeTimer = (javax.swing.Timer) bar.getClientProperty("streamflix.smoothScrollTimer");
        Object targetProperty = bar.getClientProperty("streamflix.smoothScrollTarget");
        double base = activeTimer != null && activeTimer.isRunning() && targetProperty instanceof Double target
                ? target
                : bar.getValue();
        double target = Math.max(min, Math.min(max, base + preciseRotation * pixelsPerNotch));
        bar.putClientProperty("streamflix.smoothScrollTarget", target);

        if (activeTimer != null && activeTimer.isRunning()) return;

        javax.swing.Timer timer = new javax.swing.Timer(16, null);
        timer.setCoalesce(true);
        timer.addActionListener(e -> {
            Object value = bar.getClientProperty("streamflix.smoothScrollTarget");
            double desired = value instanceof Double d ? d : bar.getValue();
            int current = bar.getValue();
            double distance = desired - current;

            if (Math.abs(distance) <= 1.0) {
                bar.setValue((int) Math.round(desired));
                ((javax.swing.Timer) e.getSource()).stop();
                return;
            }

            int step = (int) Math.round(distance * 0.24);
            if (step == 0) step = distance > 0 ? 1 : -1;
            bar.setValue(Math.max(min, Math.min(max, current + step)));
        });
        bar.putClientProperty("streamflix.smoothScrollTimer", timer);
        timer.start();
    }

    private void renderHomeError(Throwable error) {
        homeRoot.removeAll();
        homeRoot.add(stateCard("No pudimos preparar el inicio",
                "Comprueba tu conexión e inténtalo de nuevo. Las demás secciones siguen disponibles.",
                "Reintentar", this::loadHome));
        homeRoot.revalidate();
        homeRoot.repaint();
    }

    private void loadPage(boolean append) {
        if (!detailOpen) contentStack.showCard("CATALOG");

        if (append && !hasMore) return;
        if (activeWorker != null && !activeWorker.isDone()) {
            if (append) return;
            activeWorker.cancel(true);
        }
        Provider requestProvider = provider;
        Mode requestMode = mode;
        int requestPage = page;
        String requestQuery = query;
        Mode requestSearchScope = searchScope;
        boolean requestSearchExplicit = searchExplicit;
        Integer requestGenreId = catalogGenreId;

        boolean preserveSearchResults = !append
                && requestMode == Mode.SEARCH
                && !loadedItems.isEmpty();
        if (!append && !preserveSearchResults) loadedItems.clear();

        if (requestMode != Mode.SEARCH && requestProvider instanceof TmdbProvider && !tmdbReady()) {
            renderTmdbSetup();
            status.setText("TMDb necesita una credencial gratuita");
            configurePaging(false, false);
            return;
        }

        if (append) {
            status.setText("Cargando más…");
        } else {
            setBusy(true, requestMode == Mode.SEARCH ? "Buscando…" : "Cargando…");
        }
        if (!append && !preserveSearchResults && loadedItems.isEmpty()) renderLoading(requestProvider.name());

        activeWorker = new SwingWorker<List<Models.ShowItem>, Void>() {
            @Override protected List<Models.ShowItem> doInBackground() throws Exception {
                List<Models.ShowItem> items = switch (requestMode) {
                    case MOVIES -> requestProvider instanceof TmdbProvider tmdb && requestGenreId != null
                            ? tmdb.moviesByGenre(requestGenreId, requestPage)
                            : requestProvider.movies(requestPage);
                    case SERIES -> requestProvider instanceof TmdbProvider tmdb && requestGenreId != null
                            ? tmdb.tvShowsByGenre(requestGenreId, requestPage)
                            : requestProvider.tvShows(requestPage);
                    case LIVE -> requestProvider.tvShows(requestPage);
                    case SEARCH -> {
                        if (requestSearchScope == Mode.LIVE) {
                            List<Models.ShowItem> results = requestProvider.search(requestQuery, requestPage);
                            yield filterSearchResults(results, requestSearchScope);
                        } else {
                            VodSearchCoordinator.SearchResult searchResult =
                                    VodSearchCoordinator.search(providers, requestQuery, requestSearchScope, requestPage);
                            yield searchResult.items();
                        }
                    }
                    case FAVORITES -> UserData.getFavorites();
                    case HOME, SPORTS -> List.of();
                };
                return requestMode == Mode.FAVORITES || requestMode == Mode.SEARCH
                        ? items
                        : withSource(requestProvider, items);
            }

            @Override protected void done() {
                if (isCancelled()) return;
                try {
                    List<Models.ShowItem> items = get();
                    mergeLoaded(items, append);

                    boolean incrementalEmpty = requestMode == Mode.SEARCH
                            && !requestSearchExplicit
                            && loadedItems.isEmpty();
                    if (incrementalEmpty) {
                        renderGridState(
                                "Sigue escribiendo",
                                "La búsqueda incremental aún no encontró una coincidencia. "
                                        + "Continúa escribiendo o pulsa Enter para buscar exactamente.",
                                null,
                                null);
                    } else {
                        renderCatalog(loadedItems);
                    }

                    boolean paged = requestMode == Mode.MOVIES || requestMode == Mode.SERIES
                            || requestMode == Mode.LIVE || requestMode == Mode.SEARCH;
                    hasMore = paged && !items.isEmpty();
                    if (!incrementalEmpty) {
                        catalogRamCache.put(
                                currentCatalogKey(), loadedItems, page, hasMore,
                                catalogScroll.getVerticalScrollBar().getValue());
                    }
                    configurePaging(false, false);
                    pageLabel.setText(loadedItems.size() + " cargados");
                    String resultStatus = incrementalEmpty
                            ? "Sigue escribiendo para afinar"
                            : loadedItems.isEmpty() ? "Sin resultados" : " ";
                    status.setText(resultStatus);
                    if (!append) setBusy(false, resultStatus);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (append && !loadedItems.isEmpty()) {
                        page = Math.max(1, page - 1);
                        status.setText("No se pudo cargar más");
                        configurePaging(false, false);
                    } else {
                        renderError(cause);
                        configurePaging(false, false);
                        setBusy(false, "No se pudo cargar");
                    }
                }
            }
        };
        activeWorker.execute();
    }

    private void mergeLoaded(List<Models.ShowItem> items, boolean append) {
        if (!append) loadedItems.clear();
        LinkedHashMap<String, Models.ShowItem> unique = new LinkedHashMap<>();
        for (Models.ShowItem existing : loadedItems) unique.put(itemKey(existing), existing);
        for (Models.ShowItem item : items) unique.putIfAbsent(itemKey(item), item);
        loadedItems.clear();
        loadedItems.addAll(unique.values());
    }

    private static String itemKey(Models.ShowItem item) {
        return (item.sourceProviderId() == null ? "" : item.sourceProviderId()) + "|" + item.id();
    }

    private void renderCatalog(List<Models.ShowItem> items) {
        grid.removeAll();
        rebuildGridColumns();

        if (items.isEmpty()) {
            String title = switch (mode) {
                case FAVORITES -> "Todavía no tienes títulos en Mi lista";
                default -> "No encontramos resultados";
            };
            String message = switch (mode) {
                case FAVORITES -> "Guarda títulos desde su ficha y aparecerán aquí.";
                default -> "Prueba otra búsqueda.";
            };
            renderGridState(title, message, null, null);
            return;
        }

        for (Models.ShowItem item : items) grid.add(cardFor(item));
        grid.revalidate();
        grid.repaint();
    }

    private JComponent cardFor(Models.ShowItem item) {
        String sourceId = item.sourceProviderId() != null
                ? item.sourceProviderId()
                : provider != null ? provider.id() : null;
        double progress = UserData.progressFraction(sourceId, item);
        return new LandscapeCard(item, this::openDetails, progress);
    }

    private void renderLoading(String source) {
        String detail = source != null && !source.isBlank()
                ? "Preparando títulos disponibles en " + source + "…"
                : "Preparando títulos…";
        renderGridState("Cargando catálogo", detail, null, null);
    }

    private void renderTmdbSetup() {
        renderGridState("Activa TMDb",
                "La build pública no incluye una credencial compartida de TMDb. Cada usuario configura una API key o Read Access Token propio una sola vez; para uso no comercial, TMDb ofrece acceso de desarrollador gratuito.",
                "Configurar TMDb", this::openSettings);
    }

    private void renderError(Throwable error) {
        renderGridState("No se pudo cargar",
                "Revisa tu conexión e inténtalo de nuevo. No necesitas cerrar ni reiniciar la aplicación.",
                "Reintentar", () -> loadPage(false));
    }

    private void renderGridState(String title, String message, String actionText, Runnable action) {
        grid.removeAll();
        grid.setLayout(new GridBagLayout());
        grid.add(stateCard(title, message, actionText, action));
        grid.revalidate();
        grid.repaint();
    }

    private JComponent stateCard(String title, String message, String actionText, Runnable action) {
        JPanel card = Theme.surface();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(560, 180));

        JLabel heading = Theme.heading(title, 20f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel body = Theme.muted("<html><body style='width:455px'>" + escapeHtml(message) + "</body></html>");
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(heading);
        card.add(Box.createVerticalStrut(9));
        card.add(body);

        if (actionText != null && action != null) {
            card.add(Box.createVerticalStrut(18));
            JButton actionButton = Theme.primaryButton(actionText);
            actionButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            actionButton.addActionListener(e -> action.run());
            card.add(actionButton);
        }
        return card;
    }

    private void maybeLoadMore() {
        if (detailOpen || !hasMore || loadedItems.isEmpty()) return;
        if (activeWorker != null && !activeWorker.isDone()) return;

        boolean paged = mode == Mode.MOVIES || mode == Mode.SERIES
                || mode == Mode.LIVE || mode == Mode.SEARCH;
        if (!paged) return;

        JScrollBar bar = catalogScroll.getVerticalScrollBar();
        int remaining = bar.getMaximum() - (bar.getValue() + bar.getVisibleAmount());
        if (remaining > 260) return;

        page++;
        loadPage(true);
    }

    private void configurePaging(boolean visible, boolean canLoadMore) {
        pagingBar.setVisible(false);
        resetButton.setVisible(false);
        nextButton.setVisible(false);
        nextButton.setEnabled(false);
        pageLabel.setVisible(false);
    }

    private void openSettings() {
        if (activePlayerView != null) {
            // Prevent SettingsDialog from overlaying playback and disrupting video presentation
            return;
        }
        new SettingsDialog(this).setVisible(true);
        if (tmdbReady()) TmdbTitleIndex.warmUpTv();

        catalogRamCache.invalidateAll();
        homeRoot.removeAll();

        if (mode == Mode.HOME) {
            provider = preferredVodProvider(true);
            loadHome();
        } else if (mode == Mode.MOVIES) {
            provider = preferredVodProvider(true);
            loadPage(false);
        } else if (mode == Mode.SERIES) {
            provider = preferredVodProvider(false);
            loadPage(false);
        } else if (mode == Mode.SEARCH && searchScope != Mode.LIVE) {
            provider = preferredVodProvider(searchScope != Mode.SERIES);
            loadPage(false);
        }
    }

    private void playHeroItem(Models.ShowItem item) {
        openDetails(item, true);
    }

    private void playSportsEvent(SportsEvent event, boolean manualChoice) {
        if (event == null) return;
        status.setText("Buscando señales deportivas…");
        new SwingWorker<SportsPlaybackPlan, Void>() {
            @Override protected SportsPlaybackPlan doInBackground() {
                return sportsPlaybackService.resolve(event);
            }

            @Override protected void done() {
                try {
                    SportsPlaybackPlan plan = get();
                    List<SportsChannelCandidate> candidates = plan.allCandidates();
                    if (candidates.isEmpty()) {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "No encontramos señales compatibles para este evento en los proveedores IPTV actuales.",
                                "Sin señal disponible", JOptionPane.INFORMATION_MESSAGE);
                        status.setText("Sin señal disponible");
                        return;
                    }

                    List<SportsChannelCandidate> verified = plan.verifiedCandidates();
                    if (!manualChoice && verified.isEmpty()) {
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Encontramos señales candidatas, pero ninguna respondió al health check. "
                                        + "Puedes revisarlas manualmente desde ‘Señales’.",
                                "Señales no verificadas", JOptionPane.INFORMATION_MESSAGE);
                        status.setText("Señales no verificadas");
                        return;
                    }

                    List<SportsChannelCandidate> ordered = manualChoice
                            ? chooseSportsCandidate(plan)
                            : candidates;
                    if (ordered.isEmpty()) {
                        status.setText(" ");
                        return;
                    }
                    openSportsPlayback(event, ordered);
                    status.setText(" ");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    AppLog.warn("sports", "No se pudo preparar reproducción deportiva", cause);
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "No pudimos preparar las señales del evento. Inténtalo nuevamente.",
                            "Error de Deportes", JOptionPane.ERROR_MESSAGE);
                    status.setText("No se pudo preparar la señal");
                }
            }
        }.execute();
    }

    private List<SportsChannelCandidate> chooseSportsCandidate(SportsPlaybackPlan plan) {
        List<SportsChannelHealth> ranked = plan.ranked();
        if (ranked.isEmpty()) return List.of();
        String[] choices = ranked.stream().map(value -> {
            SportsChannelCandidate candidate = value.candidate();
            String health = value.healthy() ? "✓" : "?";
            String country = candidate.broadcaster().country().isBlank()
                    ? "" : " · " + candidate.broadcaster().country();
            return health + " " + candidate.channel().title() + " · " + candidate.providerName()
                    + country + " · " + value.latencyMillis() + " ms";
        }).toArray(String[]::new);
        Object selected = JOptionPane.showInputDialog(this,
                "✓ = señal verificada. ? = señal no confirmada.\n"
                        + "Si la elegida falla, Streamflix probará las siguientes automáticamente.",
                "Señales del evento", JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
        if (selected == null) return List.of();
        int selectedIndex = -1;
        for (int i = 0; i < choices.length; i++) if (choices[i].equals(selected)) { selectedIndex = i; break; }
        if (selectedIndex < 0) return List.of();

        ArrayList<SportsChannelCandidate> ordered = new ArrayList<>();
        ordered.add(ranked.get(selectedIndex).candidate());
        for (int i = 0; i < ranked.size(); i++) {
            if (i != selectedIndex) ordered.add(ranked.get(i).candidate());
        }
        return List.copyOf(ordered);
    }

    private void openSportsPlayback(SportsEvent event, List<SportsChannelCandidate> candidates) {
        SportsResolvedProvider sportsProvider = new SportsResolvedProvider(event, candidates);
        openDetails(sportsProvider, sportsProvider.item(), true);
    }

    void openDetails(Models.ShowItem item) {
        openDetails(item, false);
    }

    private void openDetails(Models.ShowItem item, boolean autoPlay) {
        Provider itemProvider = item.sourceProviderId() == null ? null : ProviderRegistry.get(item.sourceProviderId());
        if (itemProvider == null) itemProvider = provider;
        openDetails(itemProvider, item, autoPlay);
    }

    private void openDetails(Provider itemProvider, Models.ShowItem item, boolean autoPlay) {
        if (itemProvider == null) throw new IllegalArgumentException("Provider requerido");

        // Save current focus and scroll before opening details
        if (!detailOpen) {
            lastFocusedComponent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            lastFocusedItemId = item.id();
            saveCurrentModeScroll();
            detailHistoryStack.clear();
        } else if (currentDetailProvider != null && currentDetailItem != null) {
            detailHistoryStack.push(new DetailRoute(currentDetailProvider, currentDetailItem, autoPlay));
        }

        currentDetailProvider = itemProvider;
        currentDetailItem = item;
        detailOpen = true;

        detailHost.removeAll();
        detailHost.add(new TitleDetailView(
                this,
                itemProvider,
                item,
                this::closeDetails,
                autoPlay,
                () -> homeNeedsRefresh = true,
                this::openDetails
        ), BorderLayout.CENTER);
        detailHost.revalidate();
        detailHost.repaint();

        contentStack.showCard("DETAIL");
        sectionHeaderPanel.setVisible(false);
    }

    private void closeDetails() {
        if (!detailOpen) return;

        if (!detailHistoryStack.isEmpty()) {
            DetailRoute prev = detailHistoryStack.pop();
            currentDetailProvider = prev.provider();
            currentDetailItem = prev.item();
            detailHost.removeAll();
            detailHost.add(new TitleDetailView(
                    this,
                    prev.provider(),
                    prev.item(),
                    this::closeDetails,
                    prev.autoPlay(),
                    () -> homeNeedsRefresh = true,
                    this::openDetails
            ), BorderLayout.CENTER);
            detailHost.revalidate();
            detailHost.repaint();
            contentStack.showCard("DETAIL");
            return;
        }

        detailOpen = false;
        currentDetailProvider = null;
        currentDetailItem = null;
        updateHeader();

        if (mode == Mode.HOME && homeNeedsRefresh) {
            homeNeedsRefresh = false;
            List<Models.ShowItem> freshHistory = UserData.getHistory().stream().limit(8).toList();
            HomeData updated = catalogRamCache.updateHomeHistory(TmdbSettings.catalogLanguage(), freshHistory);
            if (updated != null) {
                renderHome(updated);
            } else {
                loadHome();
                return;
            }
        }

        String destination = switch (mode) {
            case HOME -> Mode.HOME.name();
            case SPORTS -> Mode.SPORTS.name();
            default -> "CATALOG";
        };

        if (mode == Mode.HOME) {
            int savedScroll = catalogRamCache.getHomeScroll(TmdbSettings.catalogLanguage());
            homeScroll.getVerticalScrollBar().setValue(savedScroll);
        } else if (destination.equals("CATALOG")) {
            CatalogRamCache.CatalogEntry cached = catalogRamCache.get(currentCatalogKey());
            if (cached != null) {
                catalogScroll.getVerticalScrollBar().setValue(cached.scrollPosition());
            }
        }

        contentStack.showCard(destination, this::restoreFocusAfterDetails);
    }

    private void restoreFocusAfterDetails() {
        SwingUtilities.invokeLater(() -> {
            if (lastFocusedComponent != null && lastFocusedComponent.isDisplayable() && lastFocusedComponent.isShowing()) {
                lastFocusedComponent.requestFocusInWindow();
                return;
            }
            if (lastFocusedItemId != null) {
                if (focusCardInContainer(grid, lastFocusedItemId)) return;
                if (focusCardInContainer(homeRoot, lastFocusedItemId)) return;
            }
            JButton activeBtn = switch (mode) {
                case HOME -> homeButton;
                case MOVIES -> moviesButton;
                case SERIES -> seriesButton;
                case LIVE -> liveButton;
                case SPORTS -> sportsButton;
                case FAVORITES -> favoritesButton;
                default -> null;
            };
            if (activeBtn != null && activeBtn.isDisplayable()) {
                activeBtn.requestFocusInWindow();
            }
        });
    }

    private static boolean focusCardInContainer(Container container, String targetId) {
        if (container == null || targetId == null) return false;
        for (Component c : container.getComponents()) {
            if (c instanceof LandscapeCard card && card.item() != null && targetId.equals(card.item().id())) {
                card.requestFocusInWindow();
                return true;
            }
            if (c instanceof HeroPanel hero && hero.item() != null && targetId.equals(hero.item().id())) {
                hero.requestFocusInWindow();
                return true;
            }
            if (c instanceof Container sub) {
                if (focusCardInContainer(sub, targetId)) return true;
            }
        }
        return false;
    }

    EmbeddedPlayerView openEmbeddedPlayer(String title, Runnable onHistoryChanged) {
        // Guarantee clean video presentation by dismissing any dialogs before starting playback
        for (Window w : getOwnedWindows()) {
            if (w instanceof JDialog d && d.isShowing()) {
                d.dispose();
            }
        }

        if (activePlayerView != null) {
            activePlayerView.stopPlayback();
            playerHost.removeAll();
        }
        activePlayerView = new EmbeddedPlayerView(this, title, this::closeEmbeddedPlayer);
        activePlayerView.setOnPopOut(() -> {
            Models.Video vid = activePlayerView.currentVideo();
            String srv = activePlayerView.currentServerName();
            long req = activePlayerView.currentRequestId();
            double time = activePlayerView.lastKnownTime();
            closeEmbeddedPlayer();
            EmbeddedPlayerWindow pop = EmbeddedPlayerWindow.open(this, title);
            if (vid != null) {
                try {
                    pop.start(vid, srv, req, time);
                } catch (Exception ex) {
                    AppLog.warn("player", "Error al transferir a ventana pop-out", ex);
                }
            }
        });

        playerHost.removeAll();
        playerHost.add(activePlayerView, BorderLayout.CENTER);
        playerHost.revalidate();
        playerHost.repaint();

        sectionHeaderPanel.setVisible(false);
        if (topbar != null) topbar.setVisible(false);

        contentStack.showCard("PLAYER", () -> {
            if (activePlayerView != null) activePlayerView.requestFocusInWindow();
        });
        return activePlayerView;
    }

    void closeEmbeddedPlayer() {
        if (activePlayerView != null) {
            if (activePlayerView.isFullScreen()) {
                activePlayerView.exitFullscreen();
            }
            activePlayerView.stopPlayback();
            activePlayerView = null;
        }
        playerHost.removeAll();
        if (topbar != null) topbar.setVisible(true);

        if (detailOpen) {
            sectionHeaderPanel.setVisible(false);
            contentStack.showCard("DETAIL", () -> {
                detailHost.revalidate();
                detailHost.repaint();
                detailHost.requestFocusInWindow();
            });
        } else {
            updateHeader();
            String destination = switch (mode) {
                case HOME -> Mode.HOME.name();
                case SPORTS -> Mode.SPORTS.name();
                default -> "CATALOG";
            };
            contentStack.showCard(destination, () -> {
                restoreFocusAfterDetails();
                revalidate();
                repaint();
            });
        }
    }

    boolean isPlayerActive() {
        return activePlayerView != null;
    }

    private void rebuildGridColumns() {
        int usable = Math.max(900, getWidth() - 86);
        int columns = Math.max(2, Math.min(5, usable / 310));
        grid.setLayout(new GridLayout(0, columns, 18, 24));
    }

    private void updateNavigationState() {
        boolean searching = mode == Mode.SEARCH;
        Theme.setNavSelected(homeButton,
                mode == Mode.HOME || (searching && searchScope == Mode.HOME));
        Theme.setNavSelected(moviesButton,
                mode == Mode.MOVIES || (searching && searchScope == Mode.MOVIES));
        Theme.setNavSelected(seriesButton,
                mode == Mode.SERIES || (searching && searchScope == Mode.SERIES));
        Theme.setNavSelected(liveButton,
                mode == Mode.LIVE || (searching && searchScope == Mode.LIVE));
        Theme.setNavSelected(sportsButton, mode == Mode.SPORTS);
        Theme.setNavSelected(favoritesButton, mode == Mode.FAVORITES);
        updateSearchHint();
    }

    private void updateSearchHint() {
        Mode scope = mode == Mode.SEARCH ? searchScope : normalizedSearchScope(mode);
        String label = switch (scope) {
            case MOVIES -> "Buscar películas";
            case SERIES -> "Buscar series";
            case LIVE -> "Buscar canales";
            case SPORTS -> "Buscar equipos, ligas o deportes";
            default -> "Buscar películas y series";
        };
        search.putClientProperty("JTextField.placeholderText", label);
        search.setToolTipText(label + " · Ctrl+F");
    }

    private void openCatalog(Mode targetMode, Integer genreId, String genreLabel) {
        if (targetMode != Mode.MOVIES && targetMode != Mode.SERIES) {
            throw new IllegalArgumentException("Catalog mode required");
        }

        if (activeWorker != null && !activeWorker.isDone()) activeWorker.cancel(true);
        searchDebounce.stop();

        saveCurrentModeScroll();

        detailOpen = false;
        detailHistoryStack.clear();
        mode = targetMode;
        provider = preferredVodProvider(targetMode == Mode.MOVIES);
        catalogGenreId = genreId;
        catalogGenreLabel = genreLabel == null || genreLabel.isBlank() ? "Popular" : genreLabel;
        page = 1;
        hasMore = true;
        query = "";
        search.setText("");

        updateNavigationState();
        updateHeader();

        CatalogRamCache.CatalogKey key = currentCatalogKey();
        CatalogRamCache.CatalogEntry cached = catalogRamCache.get(key);
        if (cached != null && !cached.isExpired()) {
            loadedItems.clear();
            loadedItems.addAll(cached.items());
            page = cached.page();
            hasMore = cached.hasMore();
            renderCatalog(loadedItems);
            catalogScroll.getVerticalScrollBar().setValue(cached.scrollPosition());
            contentStack.showCard("CATALOG");
            configurePaging(false, false);
            pageLabel.setText(loadedItems.size() + " cargados");
            status.setText(" ");
            setBusy(false, " ");
            return;
        }

        loadedItems.clear();
        loadPage(false);
        SwingUtilities.invokeLater(() -> catalogScroll.getVerticalScrollBar().setValue(0));
    }

    private void renderCatalogFilters() {
        catalogFilters.removeAll();

        List<GenreFilter> filters = switch (mode) {
            case MOVIES -> List.of(
                    new GenreFilter("Popular", null),
                    new GenreFilter("Acción", 28),
                    new GenreFilter("Comedia", 35),
                    new GenreFilter("Drama", 18),
                    new GenreFilter("Terror", 27),
                    new GenreFilter("Suspenso", 53),
                    new GenreFilter("Ciencia ficción", 878),
                    new GenreFilter("Romance", 10749),
                    new GenreFilter("Documental", 99)
            );
            case SERIES -> List.of(
                    new GenreFilter("Popular", null),
                    new GenreFilter("Acción/Aventura", 10759),
                    new GenreFilter("Comedia", 35),
                    new GenreFilter("Drama", 18),
                    new GenreFilter("Crimen", 80),
                    new GenreFilter("Misterio", 9648),
                    new GenreFilter("Sci-Fi/Fantasía", 10765),
                    new GenreFilter("Documental", 99),
                    new GenreFilter("Animación", 16)
            );
            default -> List.of();
        };

        for (GenreFilter filter : filters) {
            JButton button = Theme.button(filter.label());
            boolean selected = java.util.Objects.equals(catalogGenreId, filter.genreId());
            Theme.setNavSelected(button, selected);
            button.addActionListener(e ->
                    openCatalog(mode, filter.genreId(), filter.label()));
            catalogFilters.add(button);
        }

        catalogFilters.setVisible(!filters.isEmpty());
        catalogFilters.revalidate();
        catalogFilters.repaint();
    }

    private void updateHeader() {
        String title = switch (mode) {
            case HOME -> "Inicio";
            case MOVIES -> "Películas";
            case SERIES -> "Series";
            case LIVE -> "TV en vivo";
            case SPORTS -> "Deportes";
            case SEARCH -> query.isBlank() ? "Resultados" : "Resultados para “" + query + "”";
            case FAVORITES -> "Mi lista";
        };
        sectionTitle.setText(title);
        sectionHeaderPanel.setVisible(mode != Mode.HOME);

        String subtitle = switch (mode) {
            case HOME -> "Películas, series y canales en un solo lugar";
            case FAVORITES -> "Títulos que guardaste";
            case LIVE -> "Canales en directo";
            case SPORTS -> "Partidos, carreras y eventos deportivos en vivo y próximos";
            case SEARCH -> switch (searchScope) {
                case MOVIES -> "Películas";
                case SERIES -> "Series";
                case LIVE -> "Canales en directo";
                default -> "Películas y series";
            };
            case MOVIES -> "Explora películas";
            case SERIES -> "Explora series";
            default -> "";
        };
        if ((mode == Mode.MOVIES || mode == Mode.SERIES)
                && catalogGenreLabel != null && !"Popular".equals(catalogGenreLabel)) {
            subtitle += " · " + catalogGenreLabel;
        }

        sectionSubtitle.setText(subtitle);
        renderCatalogFilters();
        status.setText(" ");
    }

    private void setBusy(boolean busy, String text) {
        status.setText(text);
        for (JButton button : List.of(homeButton, moviesButton, seriesButton, liveButton, sportsButton, favoritesButton)) {
            button.setEnabled(!busy);
        }
        // Search is asynchronous: disabling this field steals keyboard focus
        // halfway through typing as soon as the debounce starts a request.
        search.setEnabled(true);
        resetButton.setEnabled(!busy && page > 1);
        if (busy) nextButton.setEnabled(false);
    }

    private void installShortcuts() {
        JRootPane root = getRootPane();
        InputMap input = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = root.getActionMap();

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focus-search");
        actions.put("focus-search", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                search.requestFocusInWindow();
                search.selectAll();
            }
        });

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape-search");
        actions.put("escape-search", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (detailOpen) closeDetails();
                else if (mode == Mode.SEARCH) leaveSearch();
                else search.setText("");
            }
        });

        input.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "navigate-back");
        actions.put("navigate-back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (detailOpen) closeDetails();
            }
        });
    }

    @FunctionalInterface
    private interface Loader {
        List<Models.ShowItem> load() throws Exception;
    }

    private static List<Models.ShowItem> withSource(Provider source, List<Models.ShowItem> items) {
        if (source == null || items == null || items.isEmpty()) return items == null ? List.of() : items;
        return items.stream()
                .map(item -> item.sourceProviderId() == null ? item.withSourceProviderId(source.id()) : item)
                .toList();
    }

    private static List<Models.ShowItem> safeLoad(
            Provider source, Loader loader, int limit) {
        try {
            List<Models.ShowItem> items = loader.load();
            return items.stream().limit(limit).toList();
        } catch (Exception ex) {
            AppLog.warn("catalog",
                    "Falló la carga de " + (source == null ? "provider desconocido" : source.id()), ex);
            return List.of();
        }
    }

    private static CompletableFuture<List<Models.ShowItem>> loadAsync(
            Provider source, Loader loader, int limit) {
        return CompletableFuture.supplyAsync(() ->
                withSource(source, safeLoad(source, loader, limit)));
    }

    private static CompletableFuture<HomeShelf> loadShelfAsync(
            Mode mode, Integer genreId,
            String title, String subtitle, Provider source, Loader loader, int limit) {
        return loadAsync(source, loader, limit)
                .thenApply(items -> new HomeShelf(mode, genreId, title, subtitle, items));
    }

    private record GenreFilter(String label, Integer genreId) {}

    record HomeShelf(
            Mode mode,
            Integer genreId,
            String title,
            String subtitle,
            List<Models.ShowItem> items
    ) {}

    record HomeData(
            List<Models.ShowItem> history,
            List<Models.ShowItem> movies,
            List<Models.ShowItem> series,
            List<Models.ShowItem> live,
            SportsSnapshot sports,
            List<HomeShelf> discovery
    ) {
        HomeData withHistory(List<Models.ShowItem> newHistory) {
            return new HomeData(newHistory, movies, series, live, sports, discovery);
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Preserves the full keyboard-accessible navigation and search affordance on
     * compact desktop windows. Wide windows keep the single editorial toolbar;
     * narrower ones place tools on a second row rather than hiding controls.
     */
    private static final class ResponsiveTopbar extends JPanel {
        private static final int HORIZONTAL_PADDING = 24;
        private static final int VERTICAL_PADDING = 12;
        private static final int ROW_GAP = 8;
        private JPanel navigation;
        private JPanel tools;

        ResponsiveTopbar() {
            setLayout(null);
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) {
                    // Let the surrounding vertical toolbar recalculate its height when
                    // a user crosses the compact-layout threshold.
                    revalidate();
                }
            });
        }

        void setSections(JPanel navigation, JPanel tools) {
            this.navigation = navigation;
            this.tools = tools;
            add(navigation);
            add(tools);
        }

        @Override public Dimension getPreferredSize() {
            if (navigation == null || tools == null) return new Dimension(0, 62);
            int available = getParent() == null ? 1480 : getParent().getWidth();
            boolean compact = available > 0 && preferredSingleRowWidth() > available;
            int height = compact
                    ? navigation.getPreferredSize().height + tools.getPreferredSize().height + ROW_GAP + VERTICAL_PADDING * 2
                    : Math.max(navigation.getPreferredSize().height, tools.getPreferredSize().height) + VERTICAL_PADDING * 2;
            return new Dimension(preferredSingleRowWidth(), height);
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override public Dimension getMinimumSize() {
            return new Dimension(200, getPreferredSize().height);
        }

        @Override public void doLayout() {
            if (navigation == null || tools == null) return;
            int availableWidth = Math.max(0, getWidth() - HORIZONTAL_PADDING * 2);
            Dimension navigationSize = navigation.getPreferredSize();
            Dimension toolsSize = tools.getPreferredSize();
            boolean compact = navigationSize.width + toolsSize.width + 18 > availableWidth;
            if (compact) {
                navigation.setBounds(HORIZONTAL_PADDING, VERTICAL_PADDING, availableWidth, navigationSize.height);
                tools.setBounds(HORIZONTAL_PADDING, VERTICAL_PADDING + navigationSize.height + ROW_GAP,
                        availableWidth, toolsSize.height);
            } else {
                navigation.setBounds(HORIZONTAL_PADDING, VERTICAL_PADDING, navigationSize.width, navigationSize.height);
                tools.setBounds(HORIZONTAL_PADDING + availableWidth - toolsSize.width, VERTICAL_PADDING,
                        toolsSize.width, toolsSize.height);
            }
        }

        private int preferredSingleRowWidth() {
            if (navigation == null || tools == null) return 0;
            return navigation.getPreferredSize().width + tools.getPreferredSize().width + HORIZONTAL_PADDING * 2 + 18;
        }
    }
}
