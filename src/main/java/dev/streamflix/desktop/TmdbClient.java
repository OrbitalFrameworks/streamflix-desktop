package dev.streamflix.desktop;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Small TMDb v3 metadata client using the desktop HTTP and JSON helpers. */
final class TmdbClient {
    @FunctionalInterface interface KeySource { String get() throws TmdbException; }
    @FunctionalInterface interface Transport {
        String get(String url, Map<String, String> headers) throws IOException, InterruptedException;
    }

    private final String language;
    private final KeySource keys;
    private final Transport transport;

    TmdbClient(String language) { this(language, TmdbSettings::apiKey, new Http()::get); }

    TmdbClient(String language, KeySource keys, Transport transport) {
        this.language = switch (language == null ? "" : language.strip().toLowerCase(Locale.ROOT)) {
            case "en", "en-us" -> "en-US";
            case "es", "es-es" -> "es-ES";
            default -> throw new IllegalArgumentException("TMDb supports EN/en-US and ES/es-ES.");
        };
        this.keys = Objects.requireNonNull(keys);
        this.transport = Objects.requireNonNull(transport);
    }

    String language() { return language; }

    TmdbClient withLanguage(String alternateLanguage) {
        return new TmdbClient(alternateLanguage, keys, transport);
    }

    Map<String, Object> get(String path, Map<String, String> parameters) throws IOException, InterruptedException {
        if (!path.matches("(?:discover/(?:movie|tv)|search/(?:multi|movie|tv)|movie/[1-9][0-9]*(?:/recommendations)?|tv/[1-9][0-9]*(?:/(?:recommendations|season/[0-9]+))?)")) {
            throw new TmdbException("Invalid metadata request.");
        }
        String key = keys.get();
        if (key == null || key.isBlank()) {
            throw new TmdbException("API key missing. Set STREAMFLIX_TMDB_API_KEY or tmdbApiKey in %APPDATA%/Streamflix/settings.json.");
        }
        String credential = key.strip();
        boolean v3ApiKey = credential.matches("(?i)[a-f0-9]{32}");
        StringBuilder url = new StringBuilder("https://api.themoviedb.org/3/").append(path).append('?');
        if (v3ApiKey) url.append("api_key=").append(Http.encode(credential)).append('&');
        url.append("language=").append(language);
        parameters.forEach((name, value) -> {
            if (!List.of("page", "query", "include_adult", "sort_by", "with_genres").contains(name)) {
                throw new IllegalArgumentException("TMDb: Invalid metadata parameter.");
            }
            url.append('&').append(name).append('=').append(Http.encode(value));
        });
        String body;
        try {
            Map<String, String> headers = v3ApiKey
                    ? Map.of("Accept", "application/json")
                    : Map.of("Accept", "application/json", "Authorization", "Bearer " + credential);
            body = transport.get(url.toString(), headers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("TMDb: Metadata request interrupted.");
        } catch (IOException | RuntimeException e) {
            // Http errors include the full URL (and therefore api_key). Drop the cause too.
            String message = Objects.toString(e.getMessage(), "");
            if (message.startsWith("HTTP 401 ") || message.startsWith("HTTP 403 ")) {
                throw new TmdbException("API access denied. Check your API key.");
            }
            if (message.startsWith("HTTP 429 ")) {
                throw new TmdbException("Request limit reached. Please try again later.");
            }
            throw new TmdbException("Metadata request failed. Check your connection and try again.");
        }
        Map<String, Object> root;
        try {
            Object parsed = Json.parse(body);
            if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException();
            root = Json.object(parsed);
        } catch (RuntimeException e) {
            throw new TmdbException("Invalid metadata response.");
        }
        if (Boolean.FALSE.equals(root.get("success"))) {
            throw new TmdbException("Metadata request rejected. Check your API key and try again.");
        }
        return root;
    }

    static List<Object> array(Map<String, Object> root, String field) throws TmdbException {
        if (!(root.get(field) instanceof List<?>)) throw new TmdbException("Invalid metadata response.");
        return Json.array(root.get(field));
    }
}
