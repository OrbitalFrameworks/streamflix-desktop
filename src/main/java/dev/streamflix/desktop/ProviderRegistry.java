package dev.streamflix.desktop;

import java.util.List;

final class ProviderRegistry {
    private static final List<Provider> PROVIDERS = List.of(
            new TmdbProvider("en"),
            new TmdbProvider("es"),
            new M3uLiveProvider("iptv-spain", "IPTV Spain",
                    "https://iptv-org.github.io/iptv/languages/spa.m3u"),
            new M3uLiveProvider("iptv-all-world", "IPTV All World",
                    "https://iptv-org.github.io/iptv/index.m3u"),
            new M3uLiveProvider("pluto-mx", "Pluto TV MX",
                    "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_mx.m3u"),
            new M3uLiveProvider("pluto-es", "Pluto TV ES",
                    "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_es.m3u"),
            new M3uLiveProvider("pluto-us", "Pluto TV US",
                    "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_us.m3u"),
            new FanpelisProvider(),
            new RidoMoviesProvider(),
            new PelisflixHdProvider(),
            new AnimeWorldProvider(),
            new SeriesTurcasProvider(),
            new LaCartoonsProvider(),
            new AnimeSaturnProvider(),
            new AnimeUnityProvider(),
            new MegaKinoProvider()
    );

    private static volatile List<Provider> customProviders = null;

    private ProviderRegistry() {}

    static synchronized void registerCustomProviders(List<Provider> custom) {
        customProviders = (custom != null) ? List.copyOf(custom) : null;
    }

    static List<Provider> all() {
        List<Provider> custom = customProviders;
        return custom != null ? custom : PROVIDERS;
    }

    static Provider get(String id) {
        List<Provider> list = all();
        return list.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }
}
