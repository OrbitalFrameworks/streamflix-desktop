package dev.streamflix.desktop;

import java.util.List;

public final class VodSearchCoordinatorTest {
    public static void main(String[] args) {
        testScopeFilteringAndIsolation();
        testTimeoutAndResilience();
        testCanonicalDeduplicationAndCandidates();
        testRelevanceRanking();
        System.out.println("VodSearchCoordinatorTest OK");
    }

    private static void testScopeFilteringAndIsolation() {
        Provider movieOnly = new TestVodProvider("provider-movies", "Movies Source", true, false, 0);
        Provider seriesOnly = new TestVodProvider("provider-series", "Series Source", false, true, 0);
        Provider allVod = new TestVodProvider("provider-all", "All Source", true, true, 0);
        Provider live = new M3uLiveProvider("iptv-live", "Live TV", "https://example.com/playlist.m3u");

        List<Provider> all = List.of(movieOnly, seriesOnly, allVod, live);

        // Movies scope: must not query seriesOnly or live
        VodSearchCoordinator.SearchResult movieResult =
                VodSearchCoordinator.search(all, "test", MainFrame.Mode.MOVIES, 1, 1000L);
        require(!movieResult.items().isEmpty(), "movie items non empty");
        for (Models.ShowItem item : movieResult.items()) {
            require(item.type() == Models.ShowType.MOVIE, "only movies in MOVIES scope");
        }
        boolean liveFound = movieResult.providerResults().stream()
                .anyMatch(r -> r.providerId().equals("iptv-live"));
        require(!liveFound, "live provider isolated from VOD search");

        // Series scope: must not query movieOnly
        VodSearchCoordinator.SearchResult seriesResult =
                VodSearchCoordinator.search(all, "test", MainFrame.Mode.SERIES, 1, 1000L);
        require(!seriesResult.items().isEmpty(), "series items non empty");
        for (Models.ShowItem item : seriesResult.items()) {
            require(item.type() == Models.ShowType.TV_SHOW, "only series in SERIES scope");
        }
    }

    private static void testTimeoutAndResilience() {
        Provider fast = new TestVodProvider("provider-fast", "Fast Source", true, true, 10);
        Provider slow = new TestVodProvider("provider-slow", "Slow Source", true, true, 3000);
        Provider broken = new TestErrorProvider("provider-broken", "Broken Source");

        List<Provider> providers = List.of(fast, slow, broken);

        // Search with short 200 ms timeout so slow provider times out
        VodSearchCoordinator.SearchResult result =
                VodSearchCoordinator.search(providers, "matrix", MainFrame.Mode.HOME, 1, 200L);

        require(!result.items().isEmpty(), "fast results preserved despite slow/broken providers");
        require(result.hasPartialResults(), "partial results detected");

        boolean slowTimedOut = result.providerResults().stream()
                .anyMatch(r -> r.providerId().equals("provider-slow")
                        && r.status() == VodSearchCoordinator.ProviderSearchStatus.TIMEOUT);
        require(slowTimedOut, "slow provider timed out");

        boolean brokenRecorded = result.providerResults().stream()
                .anyMatch(r -> r.providerId().equals("provider-broken")
                        && r.status() == VodSearchCoordinator.ProviderSearchStatus.ERROR);
        require(brokenRecorded, "broken provider error recorded");
    }

    private static void testCanonicalDeduplicationAndCandidates() {
        VodSearchCoordinator.clearCandidatesForTesting();

        Models.ShowItem itemA = new Models.ShowItem(
                "cand-1", "cand-1", "Inception", "Overview A", "2010-07-16",
                148, 8.8, "https://example.com/posterA.jpg", null, Models.ShowType.MOVIE, "source-a");

        Models.ShowItem itemB = new Models.ShowItem(
                "cand-2", "cand-2", "Inception", "Overview B", "2010",
                null, null, null, null, Models.ShowType.MOVIE, "source-b");

        List<Models.ShowItem> merged =
                VodSearchCoordinator.deduplicateAndRank(List.of(itemA, itemB), "Inception", MainFrame.Mode.MOVIES);

        require(merged.size() == 1, "deduplicated into one item");
        Models.ShowItem winner = merged.get(0);
        require(winner.id().equals("cand-1"), "richer metadata item won");

        List<VodSearchCoordinator.ProviderCandidate> candidates =
                VodSearchCoordinator.candidatesFor("cand-1");
        require(candidates.size() == 2, "both candidates tracked for canonical item");
    }

    private static void testRelevanceRanking() {
        Models.ShowItem exact = new Models.ShowItem(
                "id-1", "id-1", "Dune", null, "2021", null, 8.0, null, null, Models.ShowType.MOVIE, "src");
        Models.ShowItem prefix = new Models.ShowItem(
                "id-2", "id-2", "Dune: Part Two", null, "2024", null, 8.5, null, null, Models.ShowType.MOVIE, "src");
        Models.ShowItem interior = new Models.ShowItem(
                "id-3", "id-3", "The Dune Warrior", null, "1990", null, 6.0, null, null, Models.ShowType.MOVIE, "src");

        List<Models.ShowItem> ranked =
                VodSearchCoordinator.deduplicateAndRank(List.of(interior, prefix, exact), "Dune", MainFrame.Mode.MOVIES);

        require(ranked.size() == 3, "all 3 items preserved");
        require(ranked.get(0).title().equals("Dune"), "exact match ranks first");
        require(ranked.get(1).title().equals("Dune: Part Two"), "prefix match ranks second");
        require(ranked.get(2).title().equals("The Dune Warrior"), "interior match ranks third");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }

    private static class TestVodProvider implements Provider {
        private final String id;
        private final String name;
        private final boolean movies;
        private final boolean series;
        private final long delayMs;

        TestVodProvider(String id, String name, boolean movies, boolean series, long delayMs) {
            this.id = id;
            this.name = name;
            this.movies = movies;
            this.series = series;
            this.delayMs = delayMs;
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }
        @Override public boolean supportsMovies() { return movies; }
        @Override public boolean supportsTvShows() { return series; }

        @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
        @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }

        @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
            if (delayMs > 0) Thread.sleep(delayMs);
            Models.ShowItem movie = new Models.ShowItem(
                    id + ":m:1", "m1", query + " Movie", "Overview", "2024",
                    120, 7.5, null, null, Models.ShowType.MOVIE, id);
            Models.ShowItem show = new Models.ShowItem(
                    id + ":s:1", "s1", query + " Series", "Overview", "2024",
                    null, 8.0, null, null, Models.ShowType.TV_SHOW, id);
            if (movies && series) return List.of(movie, show);
            if (movies) return List.of(movie);
            return List.of(show);
        }

        @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }
        @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
    }

    private static class TestErrorProvider implements Provider {
        private final String id;
        private final String name;

        TestErrorProvider(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }
        @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
        @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }

        @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
            throw new java.io.IOException("Remote network failure simulated");
        }

        @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }
        @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
    }
}
