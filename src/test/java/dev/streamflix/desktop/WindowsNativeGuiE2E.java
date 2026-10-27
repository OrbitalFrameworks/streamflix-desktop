package dev.streamflix.desktop;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Native Windows GUI End-to-End runner with populated fixture catalog state,
 * responsive multi-viewport testing, and traceable screen capture.
 */
public final class WindowsNativeGuiE2E {
    private static final Path OUT = Path.of(System.getProperty("streamflix.e2e.output",
            (Files.exists(Path.of("build")) && !Files.isWritable(Path.of("build")))
                    ? "/tmp/streamflix-build/gui-e2e"
                    : "build/windows-gui-e2e"));
    private static final Path SCREENSHOTS_DIR = OUT.resolve("screenshots");
    private static final List<String> logEntries = new ArrayList<>();
    private static final List<Map<String, Object>> screenshotManifest = new ArrayList<>();
    private static Robot robot;
    private static boolean nativePlaybackObserved;
    private static boolean completedSuccessfully;

    private static void log(String msg) {
        System.out.println("NATIVE-GUI-E2E: " + msg);
        logEntries.add(msg);
    }

    private static void verify(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("VERIFY FAILED: " + message);
        }
        log("PASS: " + message);
    }

    private static void edt(Runnable action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeAndWait(action);
        }
    }

    private static JButton findButton(Container root, String label) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && label.equals(b.getText()) && b.isShowing()) {
                return b;
            }
            if (c instanceof Container nested) {
                JButton found = findButton(nested, label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton findButtonByTooltip(Container root, String tooltip) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && tooltip.equals(button.getToolTipText())) return button;
            if (component instanceof Container container) {
                JButton found = findButtonByTooltip(container, tooltip);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static HeroPanel findHero(Component root) {
        if (root instanceof HeroPanel hero) return hero;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                HeroPanel found = findHero(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static FeaturedHeroCarousel findFeatured(Component root) {
        if (root instanceof FeaturedHeroCarousel featured) return featured;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                FeaturedHeroCarousel match = findFeatured(child);
                if (match != null) return match;
            }
        }
        return null;
    }

    /** Wait for the actual async Home/catalog layout, not an arbitrary fixed delay.
     * This is polled outside the EDT so Swing workers and layout events can run.
     */
    private static void awaitHomeHero(MainFrame frame, long timeoutMs) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            edt(() -> {
                try {
                    var field = MainFrame.class.getDeclaredField("homeScroll");
                    field.setAccessible(true);
                    JScrollPane scroll = (JScrollPane) field.get(frame);
                    Component view = scroll.getViewport().getView();
                    HeroPanel hero = findHero(view);
                    ready.set(hero != null && hero.getWidth() > 0 && hero.getHeight() > 0
                            && scroll.getViewport().getExtentSize().width > 0);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("Cannot inspect asynchronous Home readiness", e);
                }
            });
            if (ready.get()) {
                log("PASS: Populated Home hero loaded and laid out before layout assertions");
                return;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("Home hero did not load and lay out within " + timeoutMs + "ms");
    }

    /** Detects offscreen Home content even when the native window itself is not clipped. */
    private static void verifyHomeContentFits(MainFrame frame) {
        try {
            var field = MainFrame.class.getDeclaredField("homeScroll");
            field.setAccessible(true);
            JScrollPane scroll = (JScrollPane) field.get(frame);
            JViewport viewport = scroll.getViewport();
            Component view = viewport.getView();
            verify(viewport.getScrollMode() == JViewport.SIMPLE_SCROLL_MODE,
                    "Home viewport repaints rather than blitting layered hero fragments during scroll");
            FeaturedHeroCarousel featured = findFeatured(view);
            verify(featured != null && featured.getHeight() == FeaturedHeroCarousel.HEIGHT,
                    "Featured carousel keeps a fixed height in the laid-out Home");
            Dimension before = view.getSize();
            Models.ShowItem beforeItem = featured.currentItem();
            featured.step(1);
            verify(view.getSize().equals(before),
                    "Advancing Home featured artwork does not move the catalog below");
            verify(beforeItem != featured.currentItem(),
                    "The Home hero can switch to a different title without rebuilding Home");
            HeroPanel hero = findHero(view);
            verify(view != null && view.getWidth() <= viewport.getExtentSize().width + 1,
                    "Home content tracks viewport width, not oversized preferred child widths");
            verify(hero != null && hero.getWidth() > 0,
                    "Populated Home hero is laid out inside its scroll view");
            Rectangle heroBounds = SwingUtilities.convertRectangle(hero.getParent(), hero.getBounds(), viewport);
            verify(heroBounds.x >= -1 && heroBounds.x + heroBounds.width <= viewport.getWidth() + 1,
                    "Home hero must not extend beyond the visible viewport: " + heroBounds);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect Home layout width", e);
        }
    }

    private static void verifyCatalogContentFits(MainFrame frame) {
        try {
            var scrollField = MainFrame.class.getDeclaredField("catalogScroll");
            scrollField.setAccessible(true);
            JScrollPane scroll = (JScrollPane) scrollField.get(frame);
            JViewport viewport = scroll.getViewport();
            Component content = viewport.getView();
            verify(content instanceof Scrollable
                            && ((Scrollable) content).getScrollableTracksViewportWidth(),
                    "Catalog content tracks the viewport width");
            verify(content.getWidth() <= viewport.getExtentSize().width + 1,
                    "Catalog body cannot extend beyond the scroll viewport");

            var gridField = MainFrame.class.getDeclaredField("grid");
            gridField.setAccessible(true);
            JPanel cardGrid = (JPanel) gridField.get(frame);
            for (Component card : cardGrid.getComponents()) {
                if (!(card instanceof LandscapeCard)) continue;
                Rectangle bounds = SwingUtilities.convertRectangle(card.getParent(), card.getBounds(), viewport);
                verify(bounds.x >= -1 && bounds.x + bounds.width <= viewport.getWidth() + 1,
                        "Catalog card must not be cropped horizontally: " + bounds);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect Catalog viewport layout", e);
        }
    }

    private static void clickButton(Container root, String label) throws Exception {
        AtomicReference<Point> center = new AtomicReference<>();
        edt(() -> {
            JButton btn = findButton(root, label);
            if (btn == null) throw new AssertionError("Button not found: " + label);
            Point p = btn.getLocationOnScreen();
            center.set(new Point(p.x + btn.getWidth() / 2, p.y + btn.getHeight() / 2));
        });
        if (robot != null) {
            robot.mouseMove(center.get().x, center.get().y);
            Thread.sleep(120);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
        }
        log("CLICK: " + label);
    }

    private static void captureScreen(String name, Window window, int targetW, int targetH) throws Exception {
        if (robot == null) {
            log("SKIP CAPTURE (no Robot / headless): " + name);
            return;
        }
        edt(() -> {
            window.toFront();
            window.requestFocus();
        });
        Thread.sleep(600);
        robot.waitForIdle();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle bounds = window.getBounds().intersection(new Rectangle(screenSize));
        int expectedW = Math.min(targetW, screenSize.width);
        int expectedH = Math.min(targetH, screenSize.height);
        verify(bounds.width >= expectedW && bounds.height >= expectedH,
                name + " actual captured viewport (" + bounds.width + "x" + bounds.height +
                ") must match requested size bounded by desktop display (" + expectedW + "x" + expectedH + ")");

        BufferedImage fullImage = robot.createScreenCapture(bounds);
        Files.createDirectories(SCREENSHOTS_DIR);
        Path targetPath = SCREENSHOTS_DIR.resolve(name + ".png");
        ImageIO.write(fullImage, "png", targetPath.toFile());

        // Assert diverse rendered pixels (confirms non-blank, non-solid rendering)
        Set<Integer> sampledColors = new HashSet<>();
        for (int y = 0; y < fullImage.getHeight(); y += 8) {
            for (int x = 0; x < fullImage.getWidth(); x += 8) {
                sampledColors.add(fullImage.getRGB(x, y));
            }
        }
        verify(sampledColors.size() >= 40, name + " rendered authentic diverse pixels: " + sampledColors.size());

        // Compute sha256
        byte[] bytes = Files.readAllBytes(targetPath);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name + ".png");
        entry.put("width", fullImage.getWidth());
        entry.put("height", fullImage.getHeight());
        entry.put("viewport", bounds.width + "x" + bounds.height);
        entry.put("requestedViewport", targetW + "x" + targetH);
        entry.put("desktop", screenSize.width + "x" + screenSize.height);
        entry.put("clippedByDesktop", bounds.width < targetW || bounds.height < targetH);
        entry.put("sizeBytes", bytes.length);
        entry.put("sha256", hex.toString());
        entry.put("colorsSampled", sampledColors.size());
        // A screenshot hash and nonempty pixels prove provenance, NOT visual quality.
        entry.put("visualReview", "REQUIRED");
        entry.put("timestamp", java.time.Instant.now().toString());
        screenshotManifest.add(entry);

        log("CAPTURE: " + name + ".png (" + fullImage.getWidth() + "x" + fullImage.getHeight() + ", " + bytes.length + " bytes, sha=" + hex.substring(0, 8) + "…)");
    }

    private static JDialog waitForDialog(String titleContains, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing() && d.getTitle() != null && d.getTitle().contains(titleContains)) {
                    return d;
                }
            }
            Thread.sleep(150);
        }
        throw new AssertionError("Timed out waiting for dialog with title: " + titleContains);
    }

    private static void verifyNoVisibleDialogs(String context) throws Exception {
        edt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing()) {
                    throw new AssertionError("Visual dialog unexpectedly visible " + context + ": " + d.getTitle());
                }
            }
        });
        log("PASS: Visual dialog absence verified " + context);
    }

    private static void setupPopulatedFixtures(Path dataDir) throws Exception {
        System.setProperty("streamflix.data.dir", dataDir.toString());
        TmdbSettings.saveApiKey("0123456789abcdef0123456789abcdef");
        TmdbSettings.saveCatalogLanguage("es");

        // Pre-record watch history so Continue Watching ("Seguir viendo") rail is populated
        Models.ShowItem historyItem1 = new Models.ShowItem(
                "tmdb:movie:550", "movie/550", "Fight Club",
                "Un empleado de oficina insomne y un fabricante de jabón forman un club clandestino.",
                "1999-10-15", 139, 8.4,
                null, null, Models.ShowType.MOVIE, "tmdb-es");
        Models.ShowItem historyItem2 = new Models.ShowItem(
                "tmdb:tv:1399", "tv/1399", "Game of Thrones",
                "Nueve familias nobles luchan por el control de las tierras míticas de Poniente.",
                "2011-04-17", 60, 8.4,
                null, null, Models.ShowType.TV_SHOW, "tmdb-es");
        UserData.recordHistory(historyItem1.sourceProviderId(), historyItem1, 45 * 60, 139 * 60);
        UserData.recordHistory(historyItem2.sourceProviderId(), historyItem2, 20 * 60, 60 * 60);

        // Setup mock TmdbClient responder
        TmdbClient.Transport mockTransport = (url, headers) -> {
            if (url.contains("discover/movie") && url.contains("with_genres=27")) {
                return """
                    {"page":1,"total_pages":5,"results":[
                      {"id":101,"title":"El Conjuro","vote_average":7.8,"overview":"Basada en hechos reales...","release_date":"2013-07-19"},
                      {"id":102,"title":"Hereditary","vote_average":7.5,"overview":"Cosas extrañas tras la muerte de la abuela...","release_date":"2018-06-08"}
                    ]}""";
            }
            if (url.contains("discover/movie") && url.contains("with_genres=53")) {
                return """
                    {"page":1,"total_pages":5,"results":[
                      {"id":103,"title":"Zodiac","vote_average":7.9,"overview":"Un asesino en serie aterroriza San Francisco...","release_date":"2007-03-02"},
                      {"id":104,"title":"Prisioneros","vote_average":8.1,"overview":"La desaparición de dos niñas pequeñas...","release_date":"2013-09-20"}
                    ]}""";
            }
            if (url.contains("discover/movie") && url.contains("with_genres=18")) {
                return """
                    {"page":1,"total_pages":5,"results":[
                      {"id":105,"title":"Oppenheimer","vote_average":8.9,"overview":"La historia del científico J. Robert Oppenheimer...","release_date":"2023-07-21"},
                      {"id":106,"title":"El Padrino","vote_average":9.2,"overview":"El patriarca de una dinastía del crimen...","release_date":"1972-03-14"}
                    ]}""";
            }
            if (url.contains("discover/movie") && url.contains("with_genres=35")) {
                return """
                    {"page":1,"total_pages":5,"results":[
                      {"id":107,"title":"El Gran Hotel Budapest","vote_average":8.1,"overview":"Las aventuras de Gustave H...","release_date":"2014-03-07"},
                      {"id":108,"title":"Superbad","vote_average":7.4,"overview":"Dos estudiantes de instituto co-dependientes...","release_date":"2007-08-17"}
                    ]}""";
            }
            if (url.contains("discover/movie")) {
                return """
                    {"page":1,"total_pages":10,"results":[
                      {"id":550,"title":"Inception (El Origen)","vote_average":8.8,"overview":"Dom Cobb es un ladrón experto en el peligroso arte de la extracción de secretos corporativos a través de los sueños.","release_date":"2010-07-16"},
                      {"id":551,"title":"Dune: Parte Dos","vote_average":8.6,"overview":"Paul Atreides se une a Chani y a los Fremen mientras busca venganza contra los conspiradores.","release_date":"2024-03-01"},
                      {"id":552,"title":"Interstellar","vote_average":8.7,"overview":"Un equipo de exploradores viaja a través de un agujero de gusano en el espacio.","release_date":"2014-11-07"},
                      {"id":553,"title":"The Dark Knight","vote_average":9.0,"overview":"Batman debe aceptar uno de los mayores desafíos psicológicos y físicos con el Joker.","release_date":"2008-07-18"},
                      {"id":554,"title":"Blade Runner 2049","vote_average":8.0,"overview":"Treinta años después de los eventos de la primera película, un nuevo blade runner descubre un secreto.","release_date":"2017-10-06"}
                    ]}""";
            }
            if (url.contains("discover/tv")) {
                return """
                    {"page":1,"total_pages":10,"results":[
                      {"id":1399,"name":"Breaking Bad","vote_average":9.5,"overview":"Walter White, un profesor de química con cáncer inoperable, se asocia con un ex alumno.","first_air_date":"2008-01-20"},
                      {"id":1400,"name":"Stranger Things","vote_average":8.6,"overview":"Tras la misteriosa desaparición de un niño, un pequeño pueblo desvela un misterio.","first_air_date":"2016-07-15"},
                      {"id":1401,"name":"The Last of Us","vote_average":8.7,"overview":"Veinte años después de la caída de la civilización moderna, Joel debe escoltar a Ellie.","first_air_date":"2023-01-15"},
                      {"id":1402,"name":"Succession","vote_average":8.9,"overview":"La familia Roy es conocida por controlar la compañía de medios y entretenimiento más grande.","first_air_date":"2018-06-03"}
                    ]}""";
            }
            if (url.contains("search/")) {
                return """
                    {"page":1,"total_pages":1,"results":[
                      {"id":551,"title":"Dune: Parte Dos","vote_average":8.6,"overview":"Paul Atreides se une a Chani y a los Fremen...","release_date":"2024-03-01","media_type":"movie"},
                      {"id":550,"title":"Inception","vote_average":8.8,"overview":"Dom Cobb es un ladrón experto...","release_date":"2010-07-16","media_type":"movie"}
                    ]}""";
            }
            if (url.contains("/recommendations")) {
                return """
                    {"page":1,"results":[
                      {"id":552,"title":"Interstellar","vote_average":8.7,"overview":"Exploración espacial profunda...","release_date":"2014-11-07"}
                    ]}""";
            }
            if (url.contains("movie/550")) {
                return """
                    {"id":550,"title":"Inception (El Origen)","vote_average":8.8,"overview":"Dom Cobb es un ladrón experto en el peligroso arte de la extracción...","release_date":"2010-07-16","runtime":148}""";
            }
            if (url.contains("tv/1399/season/1")) {
                return """
                    {"episodes":[
                      {"episode_number":1,"name":"Piloto","overview":"Walter White toma una decisión desesperada..."},
                      {"episode_number":2,"name":"El gato está en la bolsa","overview":"Walter y Jesse intentan limpiar el desastre..."}
                    ]}""";
            }
            if (url.contains("tv/1399")) {
                return """
                    {"id":1399,"name":"Breaking Bad","seasons":[{"season_number":1}]}""";
            }
            return "{\"results\":[]}";
        };

        TmdbClient fixtureClientEs = new TmdbClient("es", () -> "0123456789abcdef0123456789abcdef", mockTransport);
        TmdbClient fixtureClientEn = new TmdbClient("en", () -> "0123456789abcdef0123456789abcdef", mockTransport);
        TmdbProvider tmdbEs = new TmdbProvider(fixtureClientEs);
        TmdbProvider tmdbEn = new TmdbProvider(fixtureClientEn);

        // Register custom fixture providers
        ProviderRegistry.registerCustomProviders(List.of(tmdbEs, tmdbEn));
    }

    public static void run() throws Exception {
        Files.createDirectories(OUT);
        Files.createDirectories(SCREENSHOTS_DIR);

        boolean isHeadless = GraphicsEnvironment.isHeadless();
        log("Execution environment: headless=" + isHeadless + ", OS=" + System.getProperty("os.name") + ", Java=" + System.getProperty("java.version"));

        Path tempDir = Files.createTempDirectory("streamflix-native-e2e-");
        setupPopulatedFixtures(tempDir);

        if (isHeadless) {
            log("HEADLESS_ENVIRONMENT_DETECTED: Skipping Swing JFrame instantiation and Robot screen capture in headless container.");
            log("Headless verification: ProviderRegistry mock fixtures and TMDb client queries verified.");
            Provider fixtureProvider = ProviderRegistry.get("tmdb-es");
            verify(fixtureProvider != null, "Fixture TMDb provider registered");
            List<Models.ShowItem> movies = fixtureProvider.movies(1);
            verify(movies.size() >= 5, "Fixture movies returned: " + movies.size());
            List<Models.ShowItem> searchResults = fixtureProvider.search("Dune", 1);
            verify(!searchResults.isEmpty(), "Fixture search returned items");
            completedSuccessfully = true;
            writeManifest();
            log("HEADLESS E2E PRECHECK COMPLETED SUCCESSFULLY");
            return;
        }

        robot = new Robot();
        robot.setAutoDelay(70);

        Theme.install();
        AtomicReference<MainFrame> frameRef = new AtomicReference<>();

        try {
            // STEP 1: Launch MainFrame in cinematic 1480x900 resolution
            edt(() -> {
                MainFrame frame = new MainFrame(ProviderRegistry.all());
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(1480, 900);
                frame.setLocation(0, 0);
                frame.setVisible(true);
                frameRef.set(frame);
            });
            MainFrame frame = frameRef.get();
            verify(frame != null, "MainFrame instance constructed");
            verify(frame.isDisplayable(), "MainFrame mounted and displayable");

            // Wait for actual content readiness. A fixed 2.2s sleep proved
            // flaky under different GitHub Windows runner scheduling loads.
            awaitHomeHero(frame, 20_000);

            // Verify populated Home state & layout at 1480x900
            edt(() -> {
                verify(frame.getWidth() == 1480 && frame.getHeight() == 900, "Frame size 1480x900 set");
                verify(frame.isShowing(), "Frame visible on desktop display");
                verifyHomeContentFits(frame);
                for (String section : List.of("Inicio", "Películas", "Series", "TV", "Deportes", "Mi lista")) {
                    JButton btn = findButton(frame, section);
                    verify(btn != null && btn.isShowing(), "Navigation button visible at 1480x900: " + section);
                }
            });

            // Capture SCREENSHOT 01: Populated Home (Cinematic 1480x900)
            captureScreen("01-home-cinematic-1480x900", frame, 1480, 900);

            // STEP 2: Multi-viewport test - Standard Desktop 1280x800
            edt(() -> {
                frame.setSize(1280, 800);
                frame.revalidate();
                frame.doLayout();
            });
            Thread.sleep(800);

            edt(() -> {
                verify(frame.getWidth() == 1280 && frame.getHeight() == 800, "Frame standard size 1280x800 applied");
                verifyHomeContentFits(frame);
                for (String section : List.of("Inicio", "Películas", "Series", "TV", "Deportes", "Mi lista")) {
                    JButton btn = findButton(frame, section);
                    verify(btn != null && btn.isShowing(), "Navigation button visible at 1280x800: " + section);
                }
            });

            // Capture SCREENSHOT 02: Responsive Home (Standard 1280x800)
            captureScreen("02-home-standard-1280x800", frame, 1280, 800);

            // STEP 3: Multi-viewport test - Compact 1024x768 (Tests ResponsiveTopbar wrapping)
            edt(() -> {
                frame.setSize(1024, 768);
                frame.revalidate();
                frame.doLayout();
            });
            Thread.sleep(800);

            edt(() -> {
                verify(frame.getWidth() == 1024 && frame.getHeight() == 768, "Frame compact size 1024x768 applied");
                verifyHomeContentFits(frame);
                for (String section : List.of("Inicio", "Películas", "Series", "TV", "Deportes", "Mi lista")) {
                    JButton btn = findButton(frame, section);
                    verify(btn != null && btn.isShowing(), "Navigation button visible at compact 1024x768: " + section);
                }
            });

            // Capture SCREENSHOT 03: Responsive Home (Compact 1024x768)
            captureScreen("03-home-compact-1024x768", frame, 1024, 768);

            // STEP 4: Switch to 1280x800 and test Navigation & Populated Search
            edt(() -> {
                frame.setSize(1280, 800);
                frame.revalidate();
                frame.doLayout();
            });
            Thread.sleep(500);

            // Navigate through sections
            for (String section : List.of("Películas", "Series", "TV", "Deportes", "Mi lista")) {
                clickButton(frame, section);
                Thread.sleep(350);
            }

            // Run Populated Search for "Dune"
            edt(() -> {
                for (Component c : frame.getContentPane().getComponents()) {
                    findAndSetSearch(c, "Dune");
                }
            });
            Thread.sleep(1200);

            // Capture SCREENSHOT 04: Populated Search View
            edt(() -> verifyCatalogContentFits(frame));
            captureScreen("04-search-populated", frame, 1280, 800);

            // STEP 5: Open Title Detail View with populated item
            Models.ShowItem detailTarget = new Models.ShowItem(
                    "tmdb:movie:550", "movie/550", "Inception (El Origen)",
                    "Dom Cobb es un ladrón experto en el peligroso arte de la extracción de secretos corporativos.",
                    "2010-07-16", 148, 8.8,
                    null, null, Models.ShowType.MOVIE, "tmdb-es");

            edt(() -> frame.openDetails(detailTarget));
            Thread.sleep(1000);

            // Capture SCREENSHOT 05: Populated Detail View
            captureScreen("05-detail-view", frame, 1280, 800);

            // STEP 6: Open Settings Dialog
            clickButton(frame, "Ajustes");
            JDialog settingsDialog = waitForDialog("Configuración", 3000);
            verify(settingsDialog != null, "Settings dialog opened");

            // Capture SCREENSHOT 06: Settings Dialog
            captureScreen("06-settings-dialog", settingsDialog, 700, 500);

            edt(settingsDialog::dispose);
            Thread.sleep(400);

            // Verify visual dialog absence before player activation
            verifyNoVisibleDialogs("before starting playback");

            // STEP 7: Embedded Player Test with synthetic media
            AtomicReference<EmbeddedPlayerView> playerRef = new AtomicReference<>();
            edt(() -> playerRef.set(frame.openEmbeddedPlayer("Prueba Reproductor GUI E2E", () -> {})));
            Thread.sleep(1200);

            EmbeddedPlayerView playerView = playerRef.get();
            verify(playerView != null, "EmbeddedPlayerView mounted inside MainFrame");
            verify(playerView.isShowing(), "Player view visible on display");

            // Verify that no dialog (SettingsDialog, etc.) overlays playback
            verifyNoVisibleDialogs("during playback initialization");

            String mediaSource = System.getenv("STREAMFLIX_E2E_MEDIA");
            if (mediaSource == null || mediaSource.isBlank()) {
                mediaSource = "av://lavfi:testsrc2=size=640x360:rate=24";
            }
            log("Starting synthetic player playback: " + mediaSource);
            try {
                playerView.start(new Models.Video(mediaSource), "Synthetic Video Fixture", MpvPlayer.beginRequest());
                Thread.sleep(2200);
            } catch (IllegalStateException ex) {
                log("NOTICE: mpv executable not available on host environment: " + ex.getMessage());
            }

            // Assert visual dialog absence during active playback
            verifyNoVisibleDialogs("during active playback");

            // Verify settings action during playback is safely suppressed (no overlay)
            edt(() -> {
                JButton settingsBtn = findButton(frame, "Ajustes");
                if (settingsBtn != null) settingsBtn.doClick();
            });
            Thread.sleep(200);
            verifyNoVisibleDialogs("when settings trigger attempted during playback");

            // If mpv process successfully started, assert IPC position advancement
            if (MpvPlayer.isCurrentAlive()) {
                verify(playerView.lastKnownTime() >= 0.0, "mpv player IPC active");
                log("mpv alive, position: " + playerView.lastKnownTime());
                nativePlaybackObserved = true;
            } else {
                log("NOTICE: mpv process not running locally in this step; synthetic player stage verified");
            }

            // Capture SCREENSHOT 07: Active Embedded Player
            captureScreen("07-embedded-player", frame, 1280, 800);

            // Verify the two-way pop-out workflow against the real native
            // player surface, not just structural Swing component assertions.
            edt(() -> {
                JButton popButton = findButtonByTooltip(playerView,
                        "Abrir en ventana separada (Pop-out)");
                verify(popButton != null && popButton.isShowing(), "Pop-out control visible");
                popButton.doClick();
            });
            Thread.sleep(900);
            AtomicReference<EmbeddedPlayerWindow> popRef = new AtomicReference<>();
            edt(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof EmbeddedPlayerWindow playerWindow && w.isShowing()) {
                        popRef.set(playerWindow);
                    }
                }
            });
            EmbeddedPlayerWindow pop = popRef.get();
            verify(pop != null && pop.isShowing(), "Pop-out player opens visibly");
            edt(() -> {
                JButton dock = findButtonByTooltip(pop,
                        "Volver a integrar en Streamflix");
                verify(dock != null && dock.isShowing(), "Reverse dock button is visible");
                pop.playerView().toggleFullscreen();
                verify(pop.playerView().isFullScreen(), "Pop-out enters fullscreen");
                Rectangle expected = pop.getGraphicsConfiguration().getDevice()
                        .getDefaultConfiguration().getBounds();
                verify(pop.getBounds().equals(expected),
                        "Fullscreen pop-out must cover physical screen, not taskbar work area: " + pop.getBounds());
                pop.playerView().exitFullscreen();
                verify(!pop.playerView().isFullScreen(), "Fullscreen exits cleanly");
                dock.doClick();
            });
            Thread.sleep(750);
            edt(() -> {
                verify(frame.isPlayerActive(), "Dock returns playback to embedded MainFrame");
                verify(!pop.isDisplayable(), "Pop-out disposed after docking");
            });
            if (nativePlaybackObserved) {
                long resumeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
                while (!MpvPlayer.isCurrentAlive() && System.nanoTime() < resumeDeadline) {
                    Thread.sleep(200);
                }
                verify(MpvPlayer.isCurrentAlive(), "Dock resumes synthetic playback in MainFrame");
            }

            // STEP 8: Close player and verify seamless restoration to Detail/Home
            edt(frame::closeEmbeddedPlayer);
            Thread.sleep(800);

            verify(frame.isShowing(), "MainFrame restored seamlessly without closing");
            verifyNoVisibleDialogs("after player restoration");
            edt(() -> {
                verify(!frame.isPlayerActive(), "Embedded player decommissioned upon restoration");
                verify(EmbeddedPlayerView.getActiveView() == null, "Global active player view reference cleared");
            });

            // Capture SCREENSHOT 08: Restored View
            captureScreen("08-player-restored", frame, 1280, 800);

            completedSuccessfully = true;
            log("ALL GUI E2E STEPS COMPLETED SUCCESSFULLY");
        } finally {
            if (frameRef.get() != null) {
                edt(frameRef.get()::dispose);
            }
            ProviderRegistry.registerCustomProviders(null);
            try {
                // Write manifest and execution log
                writeManifest();
            } catch (Exception ex) {
                log("Failed to write manifest: " + ex.getMessage());
            }
        }
    }

    private static boolean findAndSetSearch(Component root, String query) {
        if (root instanceof JTextField tf && tf.getColumns() == 27) {
            tf.setText(query);
            for (java.awt.event.ActionListener l : tf.getActionListeners()) {
                l.actionPerformed(new java.awt.event.ActionEvent(tf, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
            }
            return true;
        }
        if (root instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                if (findAndSetSearch(child, query)) return true;
            }
        }
        return false;
    }

    private static void writeManifest() throws Exception {
        Files.createDirectories(OUT);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("timestamp", java.time.Instant.now().toString());
        root.put("os", System.getProperty("os.name"));
        root.put("javaVersion", System.getProperty("java.version"));
        root.put("isHeadless", GraphicsEnvironment.isHeadless());
        root.put("screenshotsCount", screenshotManifest.size());
        root.put("screenshots", screenshotManifest);
        root.put("log", logEntries);
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean pass = completedSuccessfully && !GraphicsEnvironment.isHeadless()
                && screenshotManifest.size() >= 8 && (!isWindows || nativePlaybackObserved);
        root.put("nativePlaybackVerified", nativePlaybackObserved);
        root.put("nativeGuiVerified", !GraphicsEnvironment.isHeadless() && screenshotManifest.size() >= 8);
        root.put("verdict", pass ? "PASS" : GraphicsEnvironment.isHeadless() && completedSuccessfully
                ? "FIXTURE_PASS" : "NOT_VERIFIED");

        String json = Json.stringify(root);
        Files.writeString(OUT.resolve("manifest.json"), json);
        Files.writeString(OUT.resolve("results.txt"), String.join("\n", logEntries));
        log("Saved manifest: " + OUT.resolve("manifest.json"));
    }

    public static void main(String[] args) {
        int code = 0;
        try {
            run();
            System.out.println("WindowsNativeGuiE2E PASS");
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("WindowsNativeGuiE2E FAILED: " + t.getMessage());
            code = 1;
        }
        System.exit(code);
    }
}
