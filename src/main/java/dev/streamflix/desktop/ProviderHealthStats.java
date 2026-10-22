package dev.streamflix.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Local provider reliability, startup latency, quality, and health ranking telemetry.
 *
 * Provides data-driven ranking and cooldown heuristics to ensure fast, reliable playback
 * and graceful multi-provider failover without storing sensitive data.
 */
final class ProviderHealthStats {
    private static final String DATA_DIR_PROPERTY = "streamflix.data.dir";
    private static final Object LOCK = new Object();
    private static final long COOLDOWN_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long AGING_DURATION_MILLIS = TimeUnit.DAYS.toMillis(7);

    // Known full-HD / high-reliability provider IDs
    private static final Set<String> HIGH_QUALITY_PROVIDERS = Set.of(
            "tmdb-en", "tmdb-es", "ridomovies", "rido-movies", "pelisflixhd", "fanpelis"
    );

    record Stats(
            int successes,
            int failures,
            int consecutiveFailures,
            double averageStartupMillis,
            long lastSuccessMillis,
            long lastFailureMillis,
            long updatedAt
    ) {
        Stats(int successes, int failures, double averageStartupMillis, long updatedAt) {
            this(successes, failures, 0, averageStartupMillis, 0L, 0L, updatedAt);
        }
    }

    private ProviderHealthStats() {}

    static void recordSuccess(String providerId, long startupMillis) {
        if (providerId == null || providerId.isBlank()) return;
        update(providerId, Math.max(1L, startupMillis), true);
    }

    static void recordFailure(String providerId, long elapsedMillis) {
        if (providerId == null || providerId.isBlank()) return;
        update(providerId, Math.max(1L, elapsedMillis), false);
    }

    static List<Provider> rank(List<Provider> providers, Provider preferred) {
        if (providers == null || providers.size() <= 1) {
            return providers == null ? List.of() : List.copyOf(providers);
        }

        Map<String, Stats> stats = readStats();
        String preferredId = preferred == null ? null : preferred.id();
        String catalogLang = TmdbSettings.catalogLanguage();

        ArrayList<Provider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator
                .comparingDouble((Provider p) -> score(p.id(), stats.get(p.id()), p.id().equals(preferredId), catalogLang))
                .thenComparing(Provider::name, String.CASE_INSENSITIVE_ORDER));

        return List.copyOf(sorted);
    }

    static boolean isCoolingDown(String providerId) {
        if (providerId == null || providerId.isBlank()) return false;
        Stats stats = getStats(providerId);
        if (stats == null) return false;
        return isCoolingDown(stats);
    }

    private static boolean isCoolingDown(Stats stats) {
        if (stats.consecutiveFailures() < 3) return false;
        long elapsedSinceFailure = System.currentTimeMillis() - stats.lastFailureMillis();
        return elapsedSinceFailure >= 0 && elapsedSinceFailure < COOLDOWN_DURATION_MILLIS;
    }

    static double score(String providerId, Stats stats, boolean isPreferred, String preferredLanguage) {
        if (stats == null) {
            double base = 3000.0;
            if (isPreferred) base -= 1500.0;
            if (isLanguageMatch(providerId, preferredLanguage)) base -= 400.0;
            if (isHighQuality(providerId)) base -= 300.0;
            return base;
        }

        long age = System.currentTimeMillis() - stats.updatedAt();
        if (stats.updatedAt() <= 0 || age > AGING_DURATION_MILLIS) {
            double base = 3000.0;
            if (isPreferred) base -= 1500.0;
            if (isLanguageMatch(providerId, preferredLanguage)) base -= 400.0;
            if (isHighQuality(providerId)) base -= 300.0;
            return base;
        }

        int total = stats.successes() + stats.failures();
        double failureRate = total == 0 ? 0.0 : (double) stats.failures() / total;

        double score = stats.averageStartupMillis();
        score += failureRate * 5000.0;
        score += stats.consecutiveFailures() * 1200.0;
        score -= Math.min(10, stats.successes()) * 120.0;

        if (isCoolingDown(stats)) {
            score += 10000.0;
        }
        if (isPreferred) {
            score -= 1500.0;
        }
        if (isLanguageMatch(providerId, preferredLanguage)) {
            score -= 400.0;
        }
        if (isHighQuality(providerId)) {
            score -= 300.0;
        }

        return score;
    }

    static boolean isHighQuality(String providerId) {
        return providerId != null && HIGH_QUALITY_PROVIDERS.contains(providerId.toLowerCase(Locale.ROOT));
    }

    static boolean isLanguageMatch(String providerId, String preferredLanguage) {
        if (providerId == null || preferredLanguage == null) return false;
        String id = providerId.toLowerCase(Locale.ROOT);
        if ("es".equalsIgnoreCase(preferredLanguage)) {
            return id.contains("-es") || id.contains("pelis") || id.contains("turcas") || id.contains("cartoons");
        } else if ("en".equalsIgnoreCase(preferredLanguage)) {
            return id.contains("-en") || id.contains("rido") || id.contains("kino");
        }
        return false;
    }

    static Stats getStats(String providerId) {
        if (providerId == null || providerId.isBlank()) return null;
        synchronized (LOCK) {
            return readStatsUnlocked().get(providerId);
        }
    }

    static void resetForTesting() {
        synchronized (LOCK) {
            Path file = statsFile();
            try { Files.deleteIfExists(file); } catch (IOException ignored) {}
        }
    }

    private static void update(String providerId, long startupMillis, boolean success) {
        synchronized (LOCK) {
            Map<String, Stats> stats = readStatsUnlocked();
            Stats prev = stats.getOrDefault(providerId, new Stats(0, 0, 0, 0.0, 0L, 0L, 0L));

            int successes = prev.successes() + (success ? 1 : 0);
            int failures = prev.failures() + (success ? 0 : 1);
            int consecutiveFailures = success ? 0 : prev.consecutiveFailures() + 1;

            double average = prev.averageStartupMillis();
            if (success) {
                average = prev.successes() == 0
                        ? startupMillis
                        : (prev.averageStartupMillis() * prev.successes() + startupMillis) / successes;
            }

            long now = System.currentTimeMillis();
            long lastSuccess = success ? now : prev.lastSuccessMillis();
            long lastFailure = success ? prev.lastFailureMillis() : now;

            stats.put(providerId, new Stats(
                    successes, failures, consecutiveFailures, average, lastSuccess, lastFailure, now));
            writeStats(stats);
        }
    }

    private static Map<String, Stats> readStats() {
        synchronized (LOCK) {
            return readStatsUnlocked();
        }
    }

    private static Map<String, Stats> readStatsUnlocked() {
        Path file = statsFile();
        LinkedHashMap<String, Stats> out = new LinkedHashMap<>();
        try {
            if (!Files.isRegularFile(file)) return out;
            Object parsed = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?>)) return out;

            for (var entry : Json.object(parsed).entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?>)) continue;
                Map<String, Object> value = Json.object(entry.getValue());

                Integer successes = Json.integer(value.get("successes"));
                Integer failures = Json.integer(value.get("failures"));
                Integer consecutive = Json.integer(value.get("consecutiveFailures"));
                Double average = Json.decimal(value.get("averageStartupMillis"));

                long lastSuccess = 0L;
                if (value.get("lastSuccessMillis") instanceof Number n) lastSuccess = n.longValue();

                long lastFailure = 0L;
                if (value.get("lastFailureMillis") instanceof Number n) lastFailure = n.longValue();

                long updated = 0L;
                if (value.get("updatedAt") instanceof Number n) updated = n.longValue();

                out.put(entry.getKey(), new Stats(
                        Math.max(0, successes == null ? 0 : successes),
                        Math.max(0, failures == null ? 0 : failures),
                        Math.max(0, consecutive == null ? 0 : consecutive),
                        Math.max(0.0, average == null ? 0.0 : average),
                        Math.max(0L, lastSuccess),
                        Math.max(0L, lastFailure),
                        Math.max(0L, updated)
                ));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void writeStats(Map<String, Stats> stats) {
        Path target = statsFile();
        Path dir = target.getParent();
        Path temp = null;
        try {
            if (dir != null) Files.createDirectories(dir);
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            for (var entry : stats.entrySet()) {
                Stats value = entry.getValue();
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                map.put("successes", value.successes());
                map.put("failures", value.failures());
                map.put("consecutiveFailures", value.consecutiveFailures());
                map.put("averageStartupMillis", value.averageStartupMillis());
                map.put("lastSuccessMillis", value.lastSuccessMillis());
                map.put("lastFailureMillis", value.lastFailureMillis());
                map.put("updatedAt", value.updatedAt());
                root.put(entry.getKey(), map);
            }

            temp = Files.createTempFile(dir == null ? Path.of(".") : dir, "provider-health-", ".tmp");
            Files.writeString(temp, Json.stringify(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        }
    }

    private static Path statsFile() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize().resolve("provider-health-stats.json");
        }

        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        return base.resolve("Streamflix").resolve("provider-health-stats.json").toAbsolutePath().normalize();
    }

    static String summary() {
        Map<String, Stats> map = readStats();
        StringBuilder sb = new StringBuilder();
        sb.append("providerStatsCount=").append(map.size()).append('\n');
        for (var entry : map.entrySet()) {
            Stats s = entry.getValue();
            sb.append("providerStat=").append(entry.getKey())
                    .append("|success=").append(s.successes())
                    .append("|fail=").append(s.failures())
                    .append("|consecutive=").append(s.consecutiveFailures())
                    .append("|avgMs=").append(Math.round(s.averageStartupMillis()))
                    .append("|cooling=").append(isCoolingDown(s))
                    .append('\n');
        }
        return sb.toString();
    }
}
