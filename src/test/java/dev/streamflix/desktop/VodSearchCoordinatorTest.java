package dev.streamflix.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VodSearchCoordinatorTest {
    public static void main(String[] args) {
        testBoundedExecutorConfiguration();
        testVodProviderFiltering();
        testScopeFilteringAndIsolation();
        testTimeoutAndResilience();
        testDeadlineBasedOverallTimeout();
        testInterruptAndCancellationPropagation();
        testCanonicalDeduplicationAndCandidates();
        testCandidateLruEviction();
        testCandidatePerKeyBounding();
        testRelevanceRanking();
        testExecutorSaturationUnderQueueOverflow();
        System.out.println("VodSearchCoordinatorTest OK");
    }

    private static void testBoundedExecutorConfiguration() {
        ThreadPoolExecutor executor = VodSearchCoordinator.executor();
        require(executor != null, "executor non-null");
        require(executor.getCorePoolSize() == 16, "corePoolSize is 16");
        require(executor.getMaximumPoolSize() == 32, "maximumPoolSize is 32");
        require(executor.getQueue().remainingCapacity() <= 64, "bounded queue capacity");
        require(!(executor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy),
                "must not use CallerRunsPolicy");
        require(executor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy,
                "must use non-blocking AbortPolicy saturation handler");
    }

    private static void testDeadlineBasedOverallTimeout() {
        // Create 4 slow providers that would each take 2000 ms
        Provider slow1 = new TestVodProvider("slow-1", "Slow 1", true, true, 2000);
        Provider slow2 = new TestVodProvider("slow-2", "Slow 2", true, true, 2000);
        Provider slow3 = new TestVodProvider("slow-3", "Slow 3", true, true, 2000);
        Provider slow4 = new TestVodProvider("slow-4", "Slow 4", true, true, 2000);
        Provider fast = new TestVodProvider("fast-1", "Fast 1", true, true, 5);

        List<Provider> providers = List.of(slow1, slow2, slow3, slow4, fast);

        long start = System.currentTimeMillis();
        // Deadline of 300 ms: total time must be bounded near 300 ms, not 4 * 300 ms or 4 * 2000 ms
        VodSearchCoordinator.SearchResult result =
                VodSearchCoordinator.search(providers, "dune", MainFrame.Mode.HOME, 1, 300L);
        long elapsed = System.currentTimeMillis() - start;

        require(elapsed < 900L, "Overall deadline must bound total search time (elapsed: " + elapsed + " ms)");
        require(!result.items().isEmpty(), "Fast provider results preserved");

        int timeouts = 0;
        for (VodSearchCoordinator.ProviderResult pr : result.providerResults()) {
            if (pr.status() == VodSearchCoordinator.ProviderSearchStatus.TIMEOUT) {
                timeouts++;
            }
        }
        require(timeouts == 4, "All 4 slow providers must time out (got: " + timeouts + ")");
    }

    private static void testInterruptAndCancellationPropagation() {
        java.util.concurrent.atomic.AtomicBoolean wasInterrupted = new java.util.concurrent.atomic.AtomicBoolean(false);
        Provider interruptible = new Provider() {
            @Override public String id() { return "interruptible"; }
            @Override public String name() { return "Interruptible"; }
            @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
            @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }
            @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    wasInterrupted.set(true);
                    throw ex;
                }
                return List.of();
            }
            @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }
            @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
        };

        VodSearchCoordinator.SearchResult res =
                VodSearchCoordinator.search(List.of(interruptible), "test", MainFrame.Mode.HOME, 1, 150L);

        require(res.providerResults().get(0).status() == VodSearchCoordinator.ProviderSearchStatus.TIMEOUT,
                "Slow provider marked as TIMEOUT");
        // Give worker thread a moment to process interrupt
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        require(wasInterrupted.get(), "Worker thread must receive interrupt on timeout cancellation");
    }

    private static void testVodProviderFiltering() {
        require(!VodSearchCoordinator.isVodProvider(null), "null is not vod provider");
        Provider live = new M3uLiveProvider("live", "Live", "https://example.com/playlist.m3u");
        require(!VodSearchCoordinator.isVodProvider(live), "M3uLiveProvider is not vod provider");

        SportsEvent event = new SportsEvent("ev1", "Soccer", "League", "Match", "Team A", "Team B",
                null, null, null, null, null, null, null, List.of());
        Provider sportsResolved = new SportsResolvedProvider(event, List.of());
        require(!VodSearchCoordinator.isVodProvider(sportsResolved), "SportsResolvedProvider is not vod provider");

        Provider testVod = new TestVodProvider("vod-test", "VOD Source", true, true, 0);
        require(VodSearchCoordinator.isVodProvider(testVod), "TestVodProvider is vod provider");
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

    private static void testCandidateLruEviction() {
        VodSearchCoordinator.clearCandidatesForTesting();
        int overflow = 20;
        int total = VodSearchCoordinator.MAX_CANDIDATE_KEYS + overflow;
        for (int i = 0; i < total; i++) {
            Models.ShowItem item = new Models.ShowItem(
                    "cand-" + i, "cand-" + i, "Title " + i, null, "2020",
                    null, null, null, null, Models.ShowType.MOVIE, "source-test");
            VodSearchCoordinator.recordCandidate("key-" + i, item);
        }

        require(VodSearchCoordinator.candidateKeyCount() == VodSearchCoordinator.MAX_CANDIDATE_KEYS,
                "candidate cache capacity is bounded at MAX_CANDIDATE_KEYS");
        require(VodSearchCoordinator.candidatesFor("key-0").isEmpty(),
                "eldest key evicted under LRU policy");
        require(!VodSearchCoordinator.candidatesFor("key-" + (total - 1)).isEmpty(),
                "recently added key retained");
    }

    private static void testCandidatePerKeyBounding() {
        VodSearchCoordinator.clearCandidatesForTesting();
        String canonicalKey = "canonical-key-bound-test";
        int totalCandidatesToAdd = 30;

        for (int i = 0; i < totalCandidatesToAdd; i++) {
            Models.ShowItem item = new Models.ShowItem(
                    "cand-" + i, "provider-item-" + i, "Candidate Title", "Overview", "2024",
                    120, 8.0, null, null, Models.ShowType.MOVIE, "provider-" + i);
            VodSearchCoordinator.recordCandidate(canonicalKey, item);
        }

        List<VodSearchCoordinator.ProviderCandidate> candidates =
                VodSearchCoordinator.candidatesFor(canonicalKey);

        require(candidates.size() == VodSearchCoordinator.MAX_CANDIDATES_PER_KEY,
                "per-key candidates must not exceed MAX_CANDIDATES_PER_KEY (expected "
                        + VodSearchCoordinator.MAX_CANDIDATES_PER_KEY + ", got " + candidates.size() + ")");

        int expectedOldestRemaining = totalCandidatesToAdd - VodSearchCoordinator.MAX_CANDIDATES_PER_KEY;
        require(candidates.get(0).providerId().equals("provider-" + expectedOldestRemaining),
                "oldest candidates must be evicted FIFO (first element should be provider-"
                        + expectedOldestRemaining + ", got " + candidates.get(0).providerId() + ")");

        require(candidates.get(candidates.size() - 1).providerId().equals("provider-" + (totalCandidatesToAdd - 1)),
                "most recently added candidate should be at the end of the list");
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

    private static void testExecutorSaturationUnderQueueOverflow() {
        ThreadPoolExecutor executor = VodSearchCoordinator.executor();
        require(executor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy,
                "executor must use AbortPolicy saturation handler");

        // Await idle executor state from previous tests before calibrating saturation
        long drainDeadline = System.currentTimeMillis() + 4000;
        while ((executor.getActiveCount() > 0 || !executor.getQueue().isEmpty()) && System.currentTimeMillis() < drainDeadline) {
            try { Thread.sleep(25); } catch (InterruptedException ignored) {}
        }
        executor.getQueue().clear();

        AtomicBoolean releaseBarrier = new AtomicBoolean(false);
        CountDownLatch barrierLatch = new CountDownLatch(1);
        CountDownLatch runningLatch = new CountDownLatch(VodSearchCoordinator.MAX_POOL_SIZE);
        List<Future<?>> blockerFutures = new ArrayList<>();

        // Deterministic barrier task: clears residual interrupt status and blocks inside a loop
        // until releaseBarrier is set to true. Even if interrupted during saturation setup or suite scheduling,
        // it continues to block so the executing worker thread cannot exit and cannot dequeue
        // tasks from the workQueue.
        Runnable barrierTask = () -> {
            Thread.interrupted(); // clear any residual interrupt from prior tests
            runningLatch.countDown();
            while (!releaseBarrier.get()) {
                try {
                    barrierLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    // Under full-suite scheduling or interrupt propagation, keep blocking
                    // until releaseBarrier is explicitly set so this worker never returns
                    // to getTask() and never dequeues another task during setup.
                }
            }
        };

        try {
            // Deterministically saturate all 32 worker threads and all 64 queue slots.
            // Submitting until RejectedExecutionException guarantees 100% saturation without
            // race conditions or off-by-one capacity estimation.
            boolean rejected = false;
            while (!rejected) {
                try {
                    blockerFutures.add(executor.submit(barrierTask));
                } catch (java.util.concurrent.RejectedExecutionException ex) {
                    rejected = true;
                }
            }

            // Ensure all 32 worker threads are actively occupied
            boolean allRunning;
            try {
                allRunning = runningLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new AssertionError("Interrupted while saturating executor", e);
            }
            require(allRunning, "all 32 pool threads must be actively running blocker tasks");
            require(executor.getQueue().remainingCapacity() == 0, "executor work queue must be fully saturated (remaining capacity 0)");

            // Create test providers that would each sleep for 10 seconds if executed
            AtomicBoolean inlineExecuted = new AtomicBoolean(false);
            Thread callerThread = Thread.currentThread();

            Provider overflowSlowProvider1 = new Provider() {
                @Override public String id() { return "overflow-slow-1"; }
                @Override public String name() { return "Overflow Slow 1"; }
                @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
                @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }
                @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
                    if (Thread.currentThread() == callerThread) {
                        inlineExecuted.set(true);
                    }
                    Thread.sleep(10000);
                    return List.of(new Models.ShowItem("slow:1", "s1", "Slow Movie", null, "2024", 120, 7.0, null, null, Models.ShowType.MOVIE, "overflow-slow-1"));
                }
                @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }
                @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
            };

            Provider overflowSlowProvider2 = new Provider() {
                @Override public String id() { return "overflow-slow-2"; }
                @Override public String name() { return "Overflow Slow 2"; }
                @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
                @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }
                @Override public List<Models.ShowItem> search(String query, int page) throws Exception {
                    if (Thread.currentThread() == callerThread) {
                        inlineExecuted.set(true);
                    }
                    Thread.sleep(10000);
                    return List.of();
                }
                @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }
                @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
            };

            long searchStart = System.currentTimeMillis();
            // Search with 4500 ms deadline under queue saturation
            VodSearchCoordinator.SearchResult res =
                    VodSearchCoordinator.search(List.of(overflowSlowProvider1, overflowSlowProvider2), "overflow-test", MainFrame.Mode.HOME, 1, 4500L);
            long elapsed = System.currentTimeMillis() - searchStart;

            // 1. Proves search returns strictly within the 4.5s deadline under queue overflow
            require(elapsed < 4500L, "Search under queue overflow must strictly complete within deadline (elapsed: " + elapsed + " ms)");
            require(elapsed < 1000L, "Non-blocking saturation handler must return immediately without waiting for overflow tasks (elapsed: " + elapsed + " ms)");

            // 2. Proves no query was executed inline on coordinating thread
            require(!inlineExecuted.get(), "Provider query must never execute inline on coordinating caller thread");

            // 3. Proves overflow providers are immediately recorded as rejected / empty
            require(res.items().isEmpty(), "No items returned from rejected overflow providers");
            require(res.providerResults().size() == 2, "Both overflow providers recorded in results");
            for (VodSearchCoordinator.ProviderResult pr : res.providerResults()) {
                require(pr.status() == VodSearchCoordinator.ProviderSearchStatus.REJECTED,
                        "Provider " + pr.providerId() + " must have REJECTED status under saturation (got: " + pr.status() + ")");
                require(pr.count() == 0, "Rejected provider must have 0 count");
                require(pr.errorMessage() != null && !pr.errorMessage().isBlank(), "Rejected provider must have error message");
            }
        } finally {
            // Release blockers to restore pool state
            releaseBarrier.set(true);
            barrierLatch.countDown();
            for (Future<?> f : blockerFutures) {
                f.cancel(true);
            }
        }

        // Verify pool recovers and can process searches again after saturation is cleared
        long postDrainDeadline = System.currentTimeMillis() + 5000;
        while ((executor.getActiveCount() > 0 || !executor.getQueue().isEmpty()) && System.currentTimeMillis() < postDrainDeadline) {
            try { Thread.sleep(25); } catch (InterruptedException ignored) {}
        }

        Provider recoveredProvider = new TestVodProvider("recovered-fast", "Recovered Fast", true, true, 5);
        VodSearchCoordinator.SearchResult postResult =
                VodSearchCoordinator.search(List.of(recoveredProvider), "recovery-check", MainFrame.Mode.HOME, 1, 1000L);
        require(!postResult.items().isEmpty(), "Executor must successfully process searches after saturation clears");
        require(postResult.providerResults().get(0).status() == VodSearchCoordinator.ProviderSearchStatus.SUCCESS,
                "Recovered provider must succeed");
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
