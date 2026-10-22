package dev.streamflix.desktop;

import java.util.List;

public final class ProviderFallbackCoordinatorTest {
    public static void main(String[] args) {
        testContentVerificationMatch();
        testCandidateResolution();
        testCandidateTargetResolutionMovie();
        testCandidateTargetResolutionEpisode();
        testContentSubstitutionPrevention();
        System.out.println("ProviderFallbackCoordinatorTest OK");
    }

    private static void testContentVerificationMatch() {
        Models.ShowItem target = new Models.ShowItem(
                "t1", "p1", "Interstellar", "Overview", "2014-11-07",
                169, 8.7, null, null, Models.ShowType.MOVIE, "src1");

        Models.ShowItem exactMatch = new Models.ShowItem(
                "c1", "cp1", "Interstellar", "Overview", "2014",
                169, 8.7, null, null, Models.ShowType.MOVIE, "src2");

        Models.ShowItem yearToleranceMatch = new Models.ShowItem(
                "c2", "cp2", "Interstellar", "Overview", "2015",
                null, null, null, null, Models.ShowType.MOVIE, "src3");

        Models.ShowItem wrongYear = new Models.ShowItem(
                "c3", "cp3", "Interstellar", "Overview", "2000",
                null, null, null, null, Models.ShowType.MOVIE, "src4");

        Models.ShowItem typeMismatch = new Models.ShowItem(
                "c4", "cp4", "Interstellar", "Overview", "2014",
                null, null, null, null, Models.ShowType.TV_SHOW, "src5");

        Models.ShowItem unrelated = new Models.ShowItem(
                "c5", "cp5", "The Matrix", "Overview", "1999",
                null, null, null, null, Models.ShowType.MOVIE, "src6");

        require(ProviderFallbackCoordinator.verifyMatch(target, exactMatch), "exact match verified");
        require(ProviderFallbackCoordinator.verifyMatch(target, yearToleranceMatch), "1-year tolerance verified");
        require(!ProviderFallbackCoordinator.verifyMatch(target, wrongYear), "different year rejected");
        require(!ProviderFallbackCoordinator.verifyMatch(target, typeMismatch), "type mismatch rejected");
        require(!ProviderFallbackCoordinator.verifyMatch(target, unrelated), "unrelated title rejected");
    }

    private static void testCandidateResolution() {
        Provider primary = dummy("prov-primary", "Primary Provider", true, true);
        Provider alt1 = dummy("prov-alt1", "Alt 1", true, true);
        Provider alt2 = dummy("prov-alt2", "Alt 2", true, true);
        Provider live = new M3uLiveProvider("prov-live", "Live", "https://example.com/live.m3u");

        Models.ShowItem item = new Models.ShowItem(
                "item-1", "prov-item-1", "Gladiator", null, "2000",
                155, 8.5, null, null, Models.ShowType.MOVIE, "prov-primary");

        List<ProviderFallbackCoordinator.Candidate> candidates =
                ProviderFallbackCoordinator.resolveCandidates(primary, "prov-item-1", item, null, List.of(primary, alt1, alt2, live));

        require(!candidates.isEmpty(), "candidates non-empty");
        require(candidates.get(0).provider().id().equals("prov-primary"), "primary candidate first");
        boolean liveIncluded = candidates.stream().anyMatch(c -> c.provider().id().equals("prov-live"));
        require(!liveIncluded, "live provider excluded from VOD fallback");
    }

    private static void testCandidateTargetResolutionMovie() {
        Models.ShowItem target = new Models.ShowItem(
                "m-1", "primary-id", "Dune", null, "2021",
                155, 8.1, null, null, Models.ShowType.MOVIE, "prov-a");

        Provider alt = new MockSearchProvider("prov-alt", "Alt", List.of(
                new Models.ShowItem("alt-dune-id", "alt-item-dune", "Dune", null, "2021",
                        155, 8.1, null, null, Models.ShowType.MOVIE, "prov-alt")
        ), List.of());

        ProviderFallbackCoordinator.Candidate unresolved = new ProviderFallbackCoordinator.Candidate(
                alt, null, "Dune", Models.ShowType.MOVIE, null);

        ProviderFallbackCoordinator.Candidate resolved =
                ProviderFallbackCoordinator.resolveCandidateTarget(unresolved, target, null);

        require(resolved != null, "resolved not null");
        require("alt-item-dune".equals(resolved.providerItemId()), "resolved correct providerItemId");
    }

    private static void testCandidateTargetResolutionEpisode() {
        Models.ShowItem series = new Models.ShowItem(
                "s-1", "primary-s1", "Breaking Bad", null, "2008",
                null, 9.5, null, null, Models.ShowType.TV_SHOW, "prov-a");
        Models.Episode targetEpisode = new Models.Episode(
                "ep-s1e1", 1, 1, "Pilot", "Overview", null);

        Models.ShowItem candidateShow = new Models.ShowItem(
                "alt-bb", "alt-bb-id", "Breaking Bad", null, "2008",
                null, 9.5, null, null, Models.ShowType.TV_SHOW, "prov-alt");
        Models.Episode matchedEpisode = new Models.Episode(
                "alt-ep-1-1", 1, 1, "Pilot", "Overview", null);

        Provider alt = new MockSearchProvider("prov-alt", "Alt",
                List.of(candidateShow), List.of(matchedEpisode));

        ProviderFallbackCoordinator.Candidate unresolved = new ProviderFallbackCoordinator.Candidate(
                alt, null, "Breaking Bad", Models.ShowType.TV_SHOW, targetEpisode);

        ProviderFallbackCoordinator.Candidate resolved =
                ProviderFallbackCoordinator.resolveCandidateTarget(unresolved, series, targetEpisode);

        require(resolved != null, "resolved not null");
        require("alt-ep-1-1".equals(resolved.providerItemId()), "resolved matching episode id");
    }

    private static void testContentSubstitutionPrevention() {
        Models.ShowItem series = new Models.ShowItem(
                "s-1", "primary-s1", "Stranger Things", null, "2016",
                null, 8.7, null, null, Models.ShowType.TV_SHOW, "prov-a");
        Models.Episode targetEpisode = new Models.Episode(
                "ep-s4e9", 4, 9, "The Piggyback", "Overview", null);

        // Alternative provider only has Season 1 episodes
        Models.ShowItem candidateShow = new Models.ShowItem(
                "alt-st", "alt-st-id", "Stranger Things", null, "2016",
                null, 8.7, null, null, Models.ShowType.TV_SHOW, "prov-alt");
        Models.Episode onlySeasonOne = new Models.Episode(
                "alt-ep-1-1", 1, 1, "Chapter One", "Overview", null);

        Provider alt = new MockSearchProvider("prov-alt", "Alt",
                List.of(candidateShow), List.of(onlySeasonOne));

        ProviderFallbackCoordinator.Candidate unresolved = new ProviderFallbackCoordinator.Candidate(
                alt, null, "Stranger Things", Models.ShowType.TV_SHOW, targetEpisode);

        ProviderFallbackCoordinator.Candidate resolved =
                ProviderFallbackCoordinator.resolveCandidateTarget(unresolved, series, targetEpisode);

        require(resolved == null, "prevented substitution when requested episode is missing");
    }

    private static Provider dummy(String id, String name, boolean movies, boolean series) {
        return new Provider() {
            @Override public String id() { return id; }
            @Override public String name() { return name; }
            @Override public boolean supportsMovies() { return movies; }
            @Override public boolean supportsTvShows() { return series; }
            @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
            @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }
            @Override public List<Models.ShowItem> search(String query, int page) { return List.of(); }
            @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }
            @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
        };
    }

    private static class MockSearchProvider implements Provider {
        private final String id;
        private final String name;
        private final List<Models.ShowItem> searchResults;
        private final List<Models.Episode> episodes;

        MockSearchProvider(String id, String name, List<Models.ShowItem> searchResults,
                           List<Models.Episode> episodes) {
            this.id = id;
            this.name = name;
            this.searchResults = searchResults;
            this.episodes = episodes;
        }

        @Override public String id() { return id; }
        @Override public String name() { return name; }
        @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
        @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }
        @Override public List<Models.ShowItem> search(String query, int page) { return searchResults; }
        @Override public List<Models.Episode> episodes(Models.ShowItem show) { return episodes; }
        @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }
}
