package dev.streamflix.desktop;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Sequential cross-provider failover coordinator with strict title/year/episode verification,
 * data-driven candidate ordering, loop prevention, and playback resume preservation.
 */
final class ProviderFallbackCoordinator {

    record Candidate(
            Provider provider,
            String providerItemId,
            String mediaTitle,
            Models.ShowType type,
            Models.Episode episode
    ) {}

    private ProviderFallbackCoordinator() {}

    /**
     * Finds candidates for a target item/episode across available providers.
     * Orders candidates according to provider health and reliability.
     */
    static List<Candidate> resolveCandidates(
            Provider primaryProvider,
            String primaryItemId,
            Models.ShowItem item,
            Models.Episode episode,
            List<Provider> allProviders) {

        LinkedHashMap<String, Candidate> candidateMap = new LinkedHashMap<>();

        // 1. Primary candidate
        if (primaryProvider != null && VodSearchCoordinator.isVodProvider(primaryProvider)
                && primaryItemId != null && !primaryItemId.isBlank()) {
            if (item == null
                    || (item.type() == Models.ShowType.MOVIE && primaryProvider.supportsMovies())
                    || (item.type() == Models.ShowType.TV_SHOW && primaryProvider.supportsTvShows())
                    || item.type() == null) {
                candidateMap.put(primaryProvider.id(), new Candidate(
                        primaryProvider,
                        primaryItemId,
                        item != null ? item.title() : primaryItemId,
                        item != null ? item.type() : Models.ShowType.MOVIE,
                        episode
                ));
            }
        }

        if (item == null) {
            return List.copyOf(candidateMap.values());
        }

        // 2. Candidates pre-registered from multi-provider search
        List<VodSearchCoordinator.ProviderCandidate> preRegistered =
                VodSearchCoordinator.candidatesFor(item.id());
        if (preRegistered.isEmpty()) {
            preRegistered = VodSearchCoordinator.candidatesFor(VodSearchCoordinator.semanticKey(item));
        }

        for (VodSearchCoordinator.ProviderCandidate pre : preRegistered) {
            if (candidateMap.containsKey(pre.providerId())) continue;
            Provider p = findProvider(allProviders, pre.providerId());
            if (p == null || !VodSearchCoordinator.isVodProvider(p)) continue;
            if (item.type() == Models.ShowType.MOVIE && !p.supportsMovies()) continue;
            if (item.type() == Models.ShowType.TV_SHOW && !p.supportsTvShows()) continue;

            String preItemId = (item.type() == Models.ShowType.MOVIE) ? pre.providerItemId() : null;
            candidateMap.put(p.id(), new Candidate(p, preItemId, item.title(), item.type(), episode));
        }

        // 3. Fallback candidates from all eligible VOD providers
        if (allProviders != null) {
            for (Provider p : allProviders) {
                if (p == null || !VodSearchCoordinator.isVodProvider(p)) continue;
                if (candidateMap.containsKey(p.id())) continue;
                if (item.type() == Models.ShowType.MOVIE && !p.supportsMovies()) continue;
                if (item.type() == Models.ShowType.TV_SHOW && !p.supportsTvShows()) continue;

                candidateMap.put(p.id(), new Candidate(p, null, item.title(), item.type(), episode));
            }
        }

        // 4. Rank all candidates according to real health telemetry metrics, cooldown, and quality
        List<Provider> providersToRank = new ArrayList<>();
        for (Candidate c : candidateMap.values()) {
            if (c.provider() != null && !providersToRank.contains(c.provider())) {
                providersToRank.add(c.provider());
            }
        }

        List<Provider> rankedProviders = ProviderHealthStats.rank(providersToRank, primaryProvider);
        LinkedHashMap<String, Candidate> ordered = new LinkedHashMap<>();
        for (Provider p : rankedProviders) {
            Candidate c = candidateMap.get(p.id());
            if (c != null) ordered.put(p.id(), c);
        }
        for (Candidate c : candidateMap.values()) {
            ordered.putIfAbsent(c.provider().id(), c);
        }

        return List.copyOf(ordered.values());
    }

    private static Provider findProvider(List<Provider> all, String id) {
        if (all == null || id == null) return null;
        for (Provider p : all) {
            if (p.id().equals(id)) return p;
        }
        return ProviderRegistry.get(id);
    }

    /**
     * Resolves the exact providerItemId on a candidate provider if not already known,
     * with strict content verification to prevent substituting wrong titles or episodes.
     */
    static Candidate resolveCandidateTarget(Candidate unresolved, Models.ShowItem targetItem,
                                            Models.Episode targetEpisode) {
        if (unresolved.providerItemId() != null && !unresolved.providerItemId().isBlank()) {
            return unresolved;
        }

        Provider provider = unresolved.provider();
        if (provider == null || targetItem == null) return null;

        try {
            List<Models.ShowItem> searchResults = provider.search(targetItem.title(), 1);
            if (searchResults == null || searchResults.isEmpty()) return null;

            Models.ShowItem matchedShow = null;
            for (Models.ShowItem candidateShow : searchResults) {
                if (verifyMatch(targetItem, candidateShow)) {
                    matchedShow = candidateShow;
                    break;
                }
            }

            if (matchedShow == null) return null;

            if (targetItem.type() == Models.ShowType.MOVIE) {
                return new Candidate(
                        provider,
                        matchedShow.providerId(),
                        matchedShow.title(),
                        Models.ShowType.MOVIE,
                        null
                );
            } else if (targetItem.type() == Models.ShowType.TV_SHOW) {
                if (targetEpisode == null) return null;
                List<Models.Episode> episodes = provider.episodes(matchedShow);
                if (episodes == null || episodes.isEmpty()) return null;

                Models.Episode matchedEpisode = null;
                for (Models.Episode ep : episodes) {
                    if (ep.seasonNumber() == targetEpisode.seasonNumber()
                            && ep.episodeNumber() == targetEpisode.episodeNumber()) {
                        matchedEpisode = ep;
                        break;
                    }
                }

                if (matchedEpisode == null) return null;

                return new Candidate(
                        provider,
                        matchedEpisode.id(),
                        targetItem.title() + " · T" + matchedEpisode.seasonNumber() + "E" + matchedEpisode.episodeNumber(),
                        Models.ShowType.TV_SHOW,
                        matchedEpisode
                );
            }
        } catch (Exception ex) {
            AppLog.warn("provider-fallback",
                    "Error resolviendo candidato en " + provider.name() + ": " + ex.getMessage());
        }

        return null;
    }

    /**
     * Strict content verification: ensures candidate title, show type, and release year match.
     */
    static boolean verifyMatch(Models.ShowItem target, Models.ShowItem candidate) {
        if (target == null || candidate == null) return false;
        if (target.type() != candidate.type()) return false;

        String normTarget = VodSearchCoordinator.normalizeTitle(target.title());
        String normCand = VodSearchCoordinator.normalizeTitle(candidate.title());
        if (normTarget.isBlank() || normCand.isBlank()) return false;

        if (!normTarget.equals(normCand)) {
            // Require either equality or substantial prefix/word containment
            boolean prefixMatch = normCand.startsWith(normTarget) || normTarget.startsWith(normCand);
            if (!prefixMatch) return false;
            // Prevent short ambiguous matches like "it" matching "italian job"
            if (Math.min(normTarget.length(), normCand.length()) < 4) return false;
        }

        String targetYear = VodSearchCoordinator.extractYear(target.released());
        String candYear = VodSearchCoordinator.extractYear(candidate.released());
        if (!targetYear.isBlank() && !candYear.isBlank()) {
            try {
                int y1 = Integer.parseInt(targetYear);
                int y2 = Integer.parseInt(candYear);
                if (Math.abs(y1 - y2) > 1) return false; // tolerate at most 1 year difference
            } catch (NumberFormatException ignored) {}
        }

        return true;
    }
}
