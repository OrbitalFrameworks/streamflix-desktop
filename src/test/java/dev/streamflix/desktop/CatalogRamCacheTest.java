package dev.streamflix.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CatalogRamCacheTest {
    public static void main(String[] args) throws Exception {
        testPutAndGet();
        testCacheMiss();
        testTtlExpiration();
        testLruEviction();
        testMemoryBounding();
        testInvalidateByMode();
        testInvalidateAll();
        testCatalogKeyScopeNormalization();
        testHomeCacheAndHistoryUpdate();
        testConcurrency();
        System.out.println("CatalogRamCacheTest OK");
    }

    private static void testPutAndGet() {
        CatalogRamCache cache = new CatalogRamCache();
        CatalogRamCache.CatalogKey key = new CatalogRamCache.CatalogKey(
                MainFrame.Mode.MOVIES, "tmdb-es", null, "", null, "es");

        List<Models.ShowItem> items = List.of(sampleItem("m1", Models.ShowType.MOVIE));
        cache.put(key, items, 1, true, 120);

        CatalogRamCache.CatalogEntry entry = cache.get(key);
        check(entry != null, "entry must be cached");
        check(entry.items().size() == 1, "items size match");
        check("m1".equals(entry.items().get(0).id()), "item id match");
        check(entry.page() == 1, "page match");
        check(entry.hasMore(), "hasMore match");
        check(entry.scrollPosition() == 120, "scroll match");

        cache.updateScroll(key, 350);
        check(cache.get(key).scrollPosition() == 350, "updated scroll match");
    }

    private static void testCacheMiss() {
        CatalogRamCache cache = new CatalogRamCache();
        CatalogRamCache.CatalogKey key = new CatalogRamCache.CatalogKey(
                MainFrame.Mode.SERIES, "tmdb-es", null, "unknown", null, "es");
        check(cache.get(key) == null, "uncached key returns null");
    }

    private static void testTtlExpiration() throws Exception {
        // Cache with 50 ms TTL
        CatalogRamCache cache = new CatalogRamCache(10, 50L);
        CatalogRamCache.CatalogKey key = new CatalogRamCache.CatalogKey(
                MainFrame.Mode.MOVIES, "tmdb-es", 28, "", null, "es");

        cache.put(key, List.of(sampleItem("m2", Models.ShowType.MOVIE)), 1, false, 0);
        check(cache.get(key) != null, "entry present immediately");

        Thread.sleep(70L);
        check(cache.get(key) == null, "entry expired after TTL");
        check(cache.isEmpty(), "cache is empty after expiration");
    }

    private static void testLruEviction() {
        // Max capacity 3 entries
        CatalogRamCache cache = new CatalogRamCache(3, 60000L);

        CatalogRamCache.CatalogKey k1 = new CatalogRamCache.CatalogKey(MainFrame.Mode.MOVIES, "p", 1, "", null, "es");
        CatalogRamCache.CatalogKey k2 = new CatalogRamCache.CatalogKey(MainFrame.Mode.MOVIES, "p", 2, "", null, "es");
        CatalogRamCache.CatalogKey k3 = new CatalogRamCache.CatalogKey(MainFrame.Mode.MOVIES, "p", 3, "", null, "es");
        CatalogRamCache.CatalogKey k4 = new CatalogRamCache.CatalogKey(MainFrame.Mode.MOVIES, "p", 4, "", null, "es");

        cache.put(k1, List.of(sampleItem("1", Models.ShowType.MOVIE)), 1, false, 0);
        cache.put(k2, List.of(sampleItem("2", Models.ShowType.MOVIE)), 1, false, 0);
        cache.put(k3, List.of(sampleItem("3", Models.ShowType.MOVIE)), 1, false, 0);

        // Access k1 so k2 becomes the eldest
        check(cache.get(k1) != null, "k1 hit");

        // Insert k4, should evict k2
        cache.put(k4, List.of(sampleItem("4", Models.ShowType.MOVIE)), 1, false, 0);

        check(cache.get(k1) != null, "k1 still present");
        check(cache.get(k2) == null, "k2 was evicted by LRU");
        check(cache.get(k3) != null, "k3 still present");
        check(cache.get(k4) != null, "k4 present");
        check(cache.size() == 3, "capacity bounded to 3");
    }

    private static void testMemoryBounding() {
        CatalogRamCache cache = new CatalogRamCache();
        CatalogRamCache.CatalogKey key = new CatalogRamCache.CatalogKey(
                MainFrame.Mode.MOVIES, "tmdb-es", null, "", null, "es");

        List<Models.ShowItem> oversized = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            oversized.add(sampleItem("item-" + i, Models.ShowType.MOVIE));
        }

        cache.put(key, oversized, 1, true, 0);
        CatalogRamCache.CatalogEntry entry = cache.get(key);
        check(entry != null, "entry present");
        check(entry.items().size() <= CatalogRamCache.MAX_ITEMS_PER_ENTRY,
                "items list must be bounded to MAX_ITEMS_PER_ENTRY");
    }

    private static void testInvalidateByMode() {
        CatalogRamCache cache = new CatalogRamCache();
        CatalogRamCache.CatalogKey kMovie = new CatalogRamCache.CatalogKey(MainFrame.Mode.MOVIES, "p", 1, "", null, "es");
        CatalogRamCache.CatalogKey kSeries = new CatalogRamCache.CatalogKey(MainFrame.Mode.SERIES, "p", 1, "", null, "es");

        cache.put(kMovie, List.of(sampleItem("m", Models.ShowType.MOVIE)), 1, false, 0);
        cache.put(kSeries, List.of(sampleItem("s", Models.ShowType.TV_SHOW)), 1, false, 0);

        cache.invalidate(MainFrame.Mode.MOVIES);

        check(cache.get(kMovie) == null, "movie invalidated");
        check(cache.get(kSeries) != null, "series preserved");
    }

    private static void testInvalidateAll() {
        CatalogRamCache cache = new CatalogRamCache();
        CatalogRamCache.CatalogKey key = new CatalogRamCache.CatalogKey(MainFrame.Mode.MOVIES, "p", 1, "", null, "es");
        cache.put(key, List.of(sampleItem("m", Models.ShowType.MOVIE)), 1, false, 0);

        MainFrame.HomeData homeData = new MainFrame.HomeData(
                List.of(), List.of(), List.of(), List.of(),
                new SportsSnapshot(List.of(), List.of(), List.of(), true, ""),
                List.of()
        );
        cache.putHome("es", homeData, 50);

        cache.invalidateAll();

        check(cache.get(key) == null, "catalog cleared");
        check(cache.getHome("es") == null, "home cleared");
        check(cache.isEmpty(), "cache is empty");
    }

    private static void testCatalogKeyScopeNormalization() {
        Provider testProvider = new Provider() {
            @Override public String name() { return "tmdb-es"; }
            @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
            @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }
            @Override public List<Models.ShowItem> search(String query, int page) { return List.of(); }
            @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }
            @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
        };
        CatalogRamCache.CatalogKey k1 = CatalogRamCache.CatalogKey.of(
                MainFrame.Mode.MOVIES, testProvider, null, "", MainFrame.Mode.HOME);
        CatalogRamCache.CatalogKey k2 = CatalogRamCache.CatalogKey.of(
                MainFrame.Mode.MOVIES, testProvider, null, "", MainFrame.Mode.SERIES);
        check(k1.equals(k2), "non-search modes must produce identical catalog keys regardless of prior search scope");
        check(k1.searchScope() == null, "catalog key searchScope must be normalized to null for non-search modes");

        CatalogRamCache.CatalogKey s1 = CatalogRamCache.CatalogKey.of(
                MainFrame.Mode.SEARCH, testProvider, null, "avatar", MainFrame.Mode.MOVIES);
        CatalogRamCache.CatalogKey s2 = CatalogRamCache.CatalogKey.of(
                MainFrame.Mode.SEARCH, testProvider, null, "avatar", MainFrame.Mode.SERIES);
        check(!s1.equals(s2), "search mode must differentiate by searchScope");
        check(s1.searchScope() == MainFrame.Mode.MOVIES, "search scope preserved");
    }

    private static void testHomeCacheAndHistoryUpdate() {
        CatalogRamCache cache = new CatalogRamCache();
        Models.ShowItem movie = sampleItem("m1", Models.ShowType.MOVIE);
        Models.ShowItem history1 = sampleItem("h1", Models.ShowType.MOVIE);

        MainFrame.HomeData homeData = new MainFrame.HomeData(
                List.of(history1),
                List.of(movie),
                List.of(),
                List.of(),
                new SportsSnapshot(List.of(), List.of(), List.of(), true, ""),
                List.of()
        );

        cache.putHome("es", homeData, 240);

        CatalogRamCache.HomeEntry entry = cache.getHome("es");
        check(entry != null, "home cached");
        check(entry.scrollPosition() == 240, "home scroll preserved");
        check(entry.data().movies().size() == 1, "movies shelf preserved");
        check(entry.data().history().size() == 1, "initial history preserved");

        // Update history in-place without refetching remote shelves
        Models.ShowItem history2 = sampleItem("h2", Models.ShowType.TV_SHOW);
        MainFrame.HomeData updated = cache.updateHomeHistory("es", List.of(history2, history1));

        check(updated != null, "updated home data returned");
        check(updated.history().size() == 2, "updated history size");
        check("h2".equals(updated.history().get(0).id()), "new item in history");
        check(updated.movies().size() == 1, "remote shelves completely intact");

        CatalogRamCache.HomeEntry refreshedEntry = cache.getHome("es");
        check(refreshedEntry.data().history().size() == 2, "cache stores updated history");
        check(refreshedEntry.scrollPosition() == 240, "scroll preserved after history update");

        cache.updateHomeScroll("es", 480);
        check(cache.getHomeScroll("es") == 480, "home scroll updated and read");
    }

    private static void testConcurrency() throws Exception {
        CatalogRamCache cache = new CatalogRamCache(20, 10000L);
        int threadCount = 8;
        int operationsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        CatalogRamCache.CatalogKey key = new CatalogRamCache.CatalogKey(
                                (i % 2 == 0) ? MainFrame.Mode.MOVIES : MainFrame.Mode.SERIES,
                                "provider-" + (i % 4),
                                i % 5,
                                "q" + (i % 10),
                                null,
                                "es"
                        );
                        cache.put(key, List.of(sampleItem("item-" + threadId + "-" + i, Models.ShowType.MOVIE)), 1, true, i * 10);
                        cache.get(key);
                        if (i % 25 == 0) {
                            cache.invalidate(MainFrame.Mode.MOVIES);
                        }
                    }
                } catch (Throwable ex) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        check(latch.await(5, TimeUnit.SECONDS), "threads completed in time");
        executor.shutdown();
        check(errors.get() == 0, "concurrent operations completed without error");
    }

    private static Models.ShowItem sampleItem(String id, Models.ShowType type) {
        return new Models.ShowItem(
                id, id, "Title " + id, "overview", "2024", 120, 8.5,
                "http://poster/" + id, "http://banner/" + id, type, "provider-test");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }
}
