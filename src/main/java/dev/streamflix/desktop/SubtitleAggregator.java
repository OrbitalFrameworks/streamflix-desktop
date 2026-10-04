package dev.streamflix.desktop;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Strict identity gate plus deterministic subtitle merging for one playback target. */
final class SubtitleAggregator {
    private SubtitleAggregator() {}

    static List<Models.Server> compatibleServers(Provider selectedProvider, Models.ShowItem show,
                                                  Models.Episode episode, String providerItemId,
                                                  List<Models.Server> selectedServers) throws Exception {
        LinkedHashMap<String, Models.Server> compatible = new LinkedHashMap<>();
        addServers(compatible, selectedServers);

        if (!(selectedProvider instanceof TmdbProvider)
                || !hasStrictTmdbIdentity(show, episode, providerItemId)) {
            return List.copyOf(compatible.values());
        }

        for (Provider candidate : ProviderRegistry.all()) {
            if (candidate instanceof TmdbProvider) {
                addServers(compatible, candidate.servers(providerItemId));
            }
        }
        return List.copyOf(compatible.values());
    }

    static Models.Video merge(Models.Video primary, List<Models.Video> compatibleVideos) {
        if (primary == null) return null;
        LinkedHashMap<String, Models.Subtitle> subtitles = new LinkedHashMap<>();
        addSubtitles(subtitles, primary.subtitles());
        if (compatibleVideos != null) {
            for (Models.Video video : compatibleVideos) {
                if (video != null) addSubtitles(subtitles, video.subtitles());
            }
        }
        return new Models.Video(primary.source(), primary.headers(),
                MpvPlayer.orderedSubtitles(new ArrayList<>(subtitles.values())));
    }

    static boolean hasStrictTmdbIdentity(Models.ShowItem show, Models.Episode episode,
                                         String providerItemId) {
        if (show == null || show.id() == null || show.providerId() == null || providerItemId == null) {
            return false;
        }
        if (show.type() == Models.ShowType.MOVIE) {
            var match = java.util.regex.Pattern.compile("tmdb:movie:([1-9][0-9]*)").matcher(show.id());
            if (!match.matches() || episode != null) return false;
            String canonical = "movie/" + match.group(1);
            return canonical.equals(show.providerId()) && canonical.equals(providerItemId);
        }

        var match = java.util.regex.Pattern.compile("tmdb:tv:([1-9][0-9]*)").matcher(show.id());
        if (!match.matches() || episode == null || episode.seasonNumber() < 0 || episode.episodeNumber() < 1) {
            return false;
        }
        String series = "tv/" + match.group(1);
        String canonicalEpisode = series + "/season/" + episode.seasonNumber()
                + "/episode/" + episode.episodeNumber();
        return series.equals(show.providerId())
                && canonicalEpisode.equals(episode.id())
                && canonicalEpisode.equals(providerItemId);
    }

    private static void addServers(LinkedHashMap<String, Models.Server> out, List<Models.Server> servers) {
        if (servers == null) return;
        for (Models.Server server : servers) {
            if (server == null) continue;
            String source = server.src() == null || server.src().isBlank() ? server.id() : server.src();
            if (source != null && !source.isBlank()) out.putIfAbsent(normalizeUrl(source), server);
        }
    }

    private static void addSubtitles(LinkedHashMap<String, Models.Subtitle> out,
                                     List<Models.Subtitle> subtitles) {
        if (subtitles == null) return;
        for (Models.Subtitle subtitle : subtitles) {
            if (subtitle == null || subtitle.file() == null || subtitle.file().isBlank()) continue;
            String key = normalizeLabel(subtitle.label()) + "|" + normalizeUrl(subtitle.file());
            out.putIfAbsent(key, subtitle);
        }
    }

    private static String normalizeLabel(String value) {
        if (value == null) return "";
        if (MediaLanguage.matches(null, value, "es")) return "es";
        if (MediaLanguage.matches(null, value, "en")) return "en";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ");
    }

    private static String normalizeUrl(String value) {
        String stripped = value.strip();
        try {
            URI uri = URI.create(stripped).normalize();
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            return new URI(scheme, uri.getUserInfo(), host, uri.getPort(), uri.getPath(),
                    uri.getQuery(), uri.getFragment()).toString();
        } catch (Exception ignored) {
            return stripped;
        }
    }
}
