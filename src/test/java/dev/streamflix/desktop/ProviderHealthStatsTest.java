package dev.streamflix.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class ProviderHealthStatsTest {
    public static void main(String[] args) throws Exception {
        Path tempDir = Files.createTempDirectory("provider-health-test-");
        System.setProperty("streamflix.data.dir", tempDir.toString());
        try {
            testSuccessAndAverageLatency();
            testConsecutiveFailuresAndCooldown();
            testRankingOrder();
            testPersistenceAndSummary();
            testMemoryCachingAndEdtSafety();
            System.out.println("ProviderHealthStatsTest OK");
        } finally {
            System.clearProperty("streamflix.data.dir");
            try (var walk = Files.walk(tempDir)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    private static void testSuccessAndAverageLatency() {
        ProviderHealthStats.resetForTesting();

        ProviderHealthStats.recordSuccess("p1", 1000);
        ProviderHealthStats.recordSuccess("p1", 2000);

        ProviderHealthStats.Stats s = ProviderHealthStats.getStats("p1");
        require(s != null, "stats exist");
        require(s.successes() == 2, "2 successes");
        require(s.failures() == 0, "0 failures");
        require(s.consecutiveFailures() == 0, "0 consecutive failures");
        require(Math.abs(s.averageStartupMillis() - 1500.0) < 0.01, "average 1500ms");
    }

    private static void testConsecutiveFailuresAndCooldown() {
        ProviderHealthStats.resetForTesting();

        ProviderHealthStats.recordFailure("p-fail", 500);
        require(!ProviderHealthStats.isCoolingDown("p-fail"), "1 failure does not trigger cooldown");

        ProviderHealthStats.recordFailure("p-fail", 600);
        require(!ProviderHealthStats.isCoolingDown("p-fail"), "2 failures do not trigger cooldown");

        ProviderHealthStats.recordFailure("p-fail", 700);
        require(ProviderHealthStats.isCoolingDown("p-fail"), "3 failures trigger cooldown");

        // A single success resets consecutive failures
        ProviderHealthStats.recordSuccess("p-fail", 1200);
        require(!ProviderHealthStats.isCoolingDown("p-fail"), "success resets cooldown");
        require(ProviderHealthStats.getStats("p-fail").consecutiveFailures() == 0, "consecutive reset to 0");
    }

    private static void testRankingOrder() {
        ProviderHealthStats.resetForTesting();

        Provider pHealthy = dummy("healthy", "Healthy Provider");
        Provider pDegraded = dummy("degraded", "Degraded Provider");
        Provider pCooldown = dummy("cooldown", "Cooldown Provider");

        // Healthy has high successes
        for (int i = 0; i < 5; i++) ProviderHealthStats.recordSuccess("healthy", 800);

        // Degraded has many failures
        for (int i = 0; i < 2; i++) ProviderHealthStats.recordFailure("degraded", 3000);

        // Cooldown has 3 consecutive failures
        for (int i = 0; i < 3; i++) ProviderHealthStats.recordFailure("cooldown", 4000);

        List<Provider> ranked = ProviderHealthStats.rank(
                List.of(pCooldown, pDegraded, pHealthy), pHealthy);

        require(ranked.get(0).id().equals("healthy"), "healthy ranked first");
        require(ranked.get(1).id().equals("degraded"), "degraded ranked second");
        require(ranked.get(2).id().equals("cooldown"), "cooldown provider ranked last");
    }

    private static void testPersistenceAndSummary() {
        ProviderHealthStats.resetForTesting();
        ProviderHealthStats.recordSuccess("persisted-p", 950);

        String summary = ProviderHealthStats.summary();
        require(summary.contains("persisted-p"), "summary includes persisted provider");
        require(summary.contains("success=1"), "summary includes success count");
    }

    private static void testMemoryCachingAndEdtSafety() {
        ProviderHealthStats.resetForTesting();
        ProviderHealthStats.recordSuccess("p-mem", 800);

        ProviderHealthStats.Stats s = ProviderHealthStats.getStats("p-mem");
        require(s != null && s.successes() == 1, "in-memory stats returned immediately");

        ProviderHealthStats.recordFailure("p-mem", 1200);
        ProviderHealthStats.Stats updated = ProviderHealthStats.getStats("p-mem");
        require(updated != null && updated.failures() == 1, "memory stats updated without blocking");

        List<Provider> ranked = ProviderHealthStats.rank(List.of(dummy("p-mem", "Provider Mem")), null);
        require(!ranked.isEmpty(), "ranked non-empty without EDT blocking");
        ProviderHealthStats.flushForTesting();
    }

    private static Provider dummy(String id, String name) {
        return new Provider() {
            @Override public String id() { return id; }
            @Override public String name() { return name; }
            @Override public List<Models.ShowItem> movies(int page) { return List.of(); }
            @Override public List<Models.ShowItem> tvShows(int page) { return List.of(); }
            @Override public List<Models.ShowItem> search(String query, int page) { return List.of(); }
            @Override public List<Models.Episode> episodes(Models.ShowItem show) { return List.of(); }
            @Override public List<Models.Server> servers(String providerItemId) { return List.of(); }
        };
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError("Failed: " + label);
    }
}
