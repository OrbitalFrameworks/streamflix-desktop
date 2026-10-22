package dev.streamflix.desktop;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asynchronous multi-provider VOD search coordinator with bounded per-provider timeouts,
 * canonical deduplication, relevance ranking, and candidate source tracking.
 */
final class VodSearchCoordinator {
    static final long DEFAULT_TIMEOUT_MS = 4500L;
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b");

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("vod-search-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    // In-memory registry of candidate sources per canonical item ID or semantic key.
    // Bounded with an LRU eviction policy to prevent unbounded memory growth over long-running sessions.
    static final int MAX_CANDIDATE_KEYS = 500;
    static final int MAX_CANDIDATES_PER_KEY = 20;
    private static final Map<String, List<ProviderCandidate>> CANDIDATES_BY_KEY =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<ProviderCandidate>> eldest) {
                    return size() > MAX_CANDIDATE_KEYS;
                }
            });

    record ProviderCandidate(
            String providerId,
            String providerItemId,
            String title,
            Models.ShowType type,
            String released
    ) {}

    enum ProviderSearchStatus { SUCCESS, TIMEOUT, ERROR, EMPTY, SKIPPED }

    record ProviderResult(
            String providerId,
            String providerName,
            ProviderSearchStatus status,
            int count,
            String errorMessage
    ) {}

    record SearchResult(
            List<Models.ShowItem> items,
            List<ProviderResult> providerResults,
            long durationMillis
    ) {
        int successfulCount() {
            int c = 0;
            for (ProviderResult r : providerResults) {
                if (r.status() == ProviderSearchStatus.SUCCESS) c++;
            }
            return c;
        }

        boolean hasPartialResults() {
            return !items.isEmpty() && providerResults.stream().anyMatch(
                    r -> r.status() == ProviderSearchStatus.ERROR || r.status() == ProviderSearchStatus.TIMEOUT);
        }
    }

    private VodSearchCoordinator() {}

    static boolean isVodProvider(Provider provider) {
        if (provider == null) return false;
        if (provider instanceof M3uLiveProvider) return false;
        if (provider instanceof SportsResolvedProvider) return false;
        return true;
    }

    static SearchResult search(List<Provider> allProviders, String query,
                               MainFrame.Mode scope, int page) {
        return search(allProviders, query, scope, page, DEFAULT_TIMEOUT_MS);
    }

    static SearchResult search(List<Provider> allProviders, String query,
                               MainFrame.Mode scope, int page, long timeoutMillis) {
        if (query == null || query.isBlank() || allProviders == null || allProviders.isEmpty()) {
            return new SearchResult(List.of(), List.of(), 0L);
        }

        long startedAt = System.currentTimeMillis();
        List<Provider> targetProviders = filterTargetProviders(allProviders, scope);

        List<CompletableFuture<ProviderSearchResult>> futures = new ArrayList<>();
        for (Provider provider : targetProviders) {
            futures.add(CompletableFuture.supplyAsync(() -> queryProvider(provider, query, scope, page), EXECUTOR));
        }

        List<ProviderResult> providerResults = new ArrayList<>();
        List<Models.ShowItem> rawCollected = new ArrayList<>();

        for (int i = 0; i < targetProviders.size(); i++) {
            Provider provider = targetProviders.get(i);
            CompletableFuture<ProviderSearchResult> future = futures.get(i);
            try {
                ProviderSearchResult result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                providerResults.add(result.summary());
                rawCollected.addAll(result.items());
            } catch (TimeoutException ex) {
                future.cancel(true);
                AppLog.warn("vod-search", "Timeout esperando respuesta de: " + provider.name());
                providerResults.add(new ProviderResult(provider.id(), provider.name(),
                        ProviderSearchStatus.TIMEOUT, 0, "Tiempo de espera agotado"));
            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                AppLog.warn("vod-search", "Error en proveedor " + provider.name() + ": " + cause.getMessage());
                providerResults.add(new ProviderResult(provider.id(), provider.name(),
                        ProviderSearchStatus.ERROR, 0, cause.getMessage()));
            }
        }

        List<Models.ShowItem> merged = deduplicateAndRank(rawCollected, query, scope);
        long elapsed = System.currentTimeMillis() - startedAt;
        return new SearchResult(merged, List.copyOf(providerResults), elapsed);
    }

    private static List<Provider> filterTargetProviders(List<Provider> allProviders, MainFrame.Mode scope) {
        List<Provider> filtered = new ArrayList<>();
        String prefLang = TmdbSettings.catalogLanguage();
        String preferredTmdbId = "en".equals(prefLang) ? "tmdb-en" : "tmdb-es";

        for (Provider p : allProviders) {
            if (!isVodProvider(p)) continue;

            // TMDb: only include the preferred language provider to avoid duplicate queries
            if (p instanceof TmdbProvider) {
                if (!p.id().equals(preferredTmdbId)) continue;
            }

            if (scope == MainFrame.Mode.MOVIES && !p.supportsMovies()) continue;
            if (scope == MainFrame.Mode.SERIES && !p.supportsTvShows()) continue;

            filtered.add(p);
        }
        return filtered;
    }

    private record ProviderSearchResult(ProviderResult summary, List<Models.ShowItem> items) {}

    private static ProviderSearchResult queryProvider(Provider provider, String query,
                                                      MainFrame.Mode scope, int page) {
        try {
            if (provider instanceof TmdbProvider tmdb) {
                if (!TmdbSettings.hasApiKey()) {
                    return new ProviderSearchResult(
                            new ProviderResult(provider.id(), provider.name(),
                                    ProviderSearchStatus.SKIPPED, 0, "Sin credencial de TMDb"),
                            List.of());
                }
                List<Models.ShowItem> tmdbResults = switch (scope) {
                    case MOVIES -> tmdb.searchMovies(query, page);
                    case SERIES -> tmdb.searchTv(query, page);
                    default -> tmdb.search(query, page);
                };
                List<Models.ShowItem> tagged = filterAndTag(tmdbResults, provider.id(), scope);
                return new ProviderSearchResult(
                        new ProviderResult(provider.id(), provider.name(),
                                tagged.isEmpty() ? ProviderSearchStatus.EMPTY : ProviderSearchStatus.SUCCESS,
                                tagged.size(), null),
                        tagged);
            }

            List<Models.ShowItem> results = provider.search(query, page);
            List<Models.ShowItem> tagged = filterAndTag(results, provider.id(), scope);
            return new ProviderSearchResult(
                    new ProviderResult(provider.id(), provider.name(),
                            tagged.isEmpty() ? ProviderSearchStatus.EMPTY : ProviderSearchStatus.SUCCESS,
                            tagged.size(), null),
                    tagged);
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return new ProviderSearchResult(
                    new ProviderResult(provider.id(), provider.name(),
                            ProviderSearchStatus.ERROR, 0, msg),
                    List.of());
        }
    }

    private static List<Models.ShowItem> filterAndTag(List<Models.ShowItem> items,
                                                      String providerId,
                                                      MainFrame.Mode scope) {
        if (items == null || items.isEmpty()) return List.of();
        ArrayList<Models.ShowItem> out = new ArrayList<>();
        for (Models.ShowItem item : items) {
            if (item == null) continue;
            if (scope == MainFrame.Mode.MOVIES && item.type() != Models.ShowType.MOVIE) continue;
            if (scope == MainFrame.Mode.SERIES && item.type() != Models.ShowType.TV_SHOW) continue;
            out.add(item.withSourceProviderId(providerId));
        }
        return out;
    }

    static List<Models.ShowItem> deduplicateAndRank(List<Models.ShowItem> items,
                                                    String query,
                                                    MainFrame.Mode scope) {
        if (items == null || items.isEmpty()) return List.of();

        // Pass 1: map semantic keys to canonical keys
        Map<String, String> semanticToCanonical = new HashMap<>();
        // TMDb items establish authoritative canonical IDs first
        for (Models.ShowItem item : items) {
            if (item.id() != null && item.id().startsWith("tmdb:")) {
                String semKey = semanticKey(item);
                if (!semKey.isBlank()) semanticToCanonical.putIfAbsent(semKey, item.id());
            }
        }

        // Pass 2: merge into deduplicated map and track candidate sources
        Map<String, Models.ShowItem> canonicalItems = new LinkedHashMap<>();
        for (Models.ShowItem item : items) {
            String semKey = semanticKey(item);
            String canonicalId = semanticToCanonical.get(semKey);
            if (canonicalId == null) {
                canonicalId = item.id();
                if (!semKey.isBlank()) semanticToCanonical.put(semKey, canonicalId);
            }

            Models.ShowItem existing = canonicalItems.get(canonicalId);
            if (existing == null) {
                canonicalItems.put(canonicalId, item);
            } else if (isBetterMetadata(item, existing)) {
                canonicalItems.put(canonicalId, item);
            }

            recordCandidate(canonicalId, item);
            if (!semKey.isBlank() && !semKey.equals(canonicalId)) {
                recordCandidate(semKey, item);
            }
        }

        // Pass 3: rank by relevance
        String normQuery = normalizeTitle(query);
        List<Models.ShowItem> sorted = new ArrayList<>(canonicalItems.values());
        sorted.sort((a, b) -> Double.compare(score(b, normQuery), score(a, normQuery)));

        return List.copyOf(sorted);
    }

    private static boolean isBetterMetadata(Models.ShowItem candidate, Models.ShowItem current) {
        // TMDb source is always preferred for catalog metadata
        boolean candIsTmdb = candidate.id() != null && candidate.id().startsWith("tmdb:");
        boolean currIsTmdb = current.id() != null && current.id().startsWith("tmdb:");
        if (candIsTmdb && !currIsTmdb) return true;
        if (!candIsTmdb && currIsTmdb) return false;

        // Richer fields prefer higher quality
        int candScore = 0;
        int currScore = 0;
        if (candidate.poster() != null && !candidate.poster().isBlank()) candScore += 3;
        if (current.poster() != null && !current.poster().isBlank()) currScore += 3;
        if (candidate.overview() != null && !candidate.overview().isBlank()) candScore += 2;
        if (current.overview() != null && !current.overview().isBlank()) currScore += 2;
        if (candidate.rating() != null && candidate.rating() > 0) candScore += 2;
        if (current.rating() != null && current.rating() > 0) currScore += 2;
        if (candidate.released() != null && !candidate.released().isBlank()) candScore += 1;
        if (current.released() != null && !current.released().isBlank()) currScore += 1;

        return candScore > currScore;
    }

    private static double score(Models.ShowItem item, String normQuery) {
        double score = 0.0;
        String normTitle = normalizeTitle(item.title());
        if (normTitle.equals(normQuery)) {
            score += 500.0;
        } else if (normTitle.startsWith(normQuery)) {
            score += 250.0;
        } else if (normTitle.contains(normQuery)) {
            score += 100.0;
        }

        if (item.id() != null && item.id().startsWith("tmdb:")) {
            score += 50.0;
        }
        if (item.rating() != null) {
            score += item.rating() * 8.0;
        }
        if (item.poster() != null && !item.poster().isBlank()) {
            score += 15.0;
        }
        return score;
    }

    static String semanticKey(Models.ShowItem item) {
        if (item == null || item.title() == null || item.title().isBlank()) return "";
        String normTitle = normalizeTitle(item.title());
        String year = extractYear(item.released());
        String type = item.type() != null ? item.type().name() : "";
        return normTitle + "#" + year + "#" + type;
    }

    static String normalizeTitle(String title) {
        if (title == null) return "";
        String stripped = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return stripped.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    static String extractYear(String released) {
        if (released == null) return "";
        Matcher m = YEAR_PATTERN.matcher(released);
        return m.find() ? m.group(1) : "";
    }

    static void recordCandidate(String key, Models.ShowItem item) {
        if (key == null || key.isBlank() || item == null) return;
        String providerId = item.sourceProviderId();
        if (providerId == null || providerId.isBlank()) return;

        ProviderCandidate candidate = new ProviderCandidate(
                providerId,
                item.providerId(),
                item.title(),
                item.type(),
                item.released()
        );

        CANDIDATES_BY_KEY.compute(key, (k, existing) -> {
            ArrayList<ProviderCandidate> list = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            boolean alreadyPresent = list.stream().anyMatch(
                    c -> c.providerId().equals(candidate.providerId())
                            && c.providerItemId().equals(candidate.providerItemId()));
            if (!alreadyPresent) {
                while (list.size() >= MAX_CANDIDATES_PER_KEY) {
                    list.remove(0);
                }
                list.add(candidate);
            }
            return List.copyOf(list);
        });
    }

    static List<ProviderCandidate> candidatesFor(String key) {
        if (key == null || key.isBlank()) return List.of();
        List<ProviderCandidate> list = CANDIDATES_BY_KEY.get(key);
        return list == null ? List.of() : list;
    }

    static int candidateKeyCount() {
        return CANDIDATES_BY_KEY.size();
    }

    static void clearCandidatesForTesting() {
        CANDIDATES_BY_KEY.clear();
    }
}
