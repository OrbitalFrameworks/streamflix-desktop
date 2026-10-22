package dev.streamflix.desktop;

import java.util.*;

/**
 * Bounded thread-safe in-memory RAM cache for catalog pages and Home data.
 * Protects against redundant network fetches, eliminates blank skeleton flashes
 * when switching tabs or returning from details, and maintains responsive UI state.
 */
final class CatalogRamCache {
    static final int DEFAULT_MAX_ENTRIES = 32;
    static final int MAX_ITEMS_PER_ENTRY = 300;
    static final long DEFAULT_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    public record CatalogKey(
            MainFrame.Mode mode,
            String providerId,
            Integer genreId,
            String query,
            MainFrame.Mode searchScope,
            String language
    ) {
        public static CatalogKey of(
                MainFrame.Mode mode,
                Provider provider,
                Integer genreId,
                String query,
                MainFrame.Mode searchScope
        ) {
            String pid = provider != null ? provider.id() : "";
            String lang = TmdbSettings.catalogLanguage();
            String normQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            return new CatalogKey(mode, pid, genreId, normQuery, searchScope, lang);
        }
    }

    public static final class CatalogEntry {
        private final List<Models.ShowItem> items;
        private final int page;
        private final boolean hasMore;
        private volatile int scrollPosition;
        private final long cachedAt;

        public CatalogEntry(List<Models.ShowItem> items, int page, boolean hasMore,
                            int scrollPosition, long cachedAt) {
            List<Models.ShowItem> nonNull = new ArrayList<>(items != null ? items.size() : 0);
            if (items != null) {
                for (Models.ShowItem it : items) {
                    if (it != null) nonNull.add(it);
                }
            }
            this.items = List.copyOf(nonNull);
            this.page = page;
            this.hasMore = hasMore;
            this.scrollPosition = Math.max(0, scrollPosition);
            this.cachedAt = cachedAt;
        }

        public List<Models.ShowItem> items() { return items; }
        public int page() { return page; }
        public boolean hasMore() { return hasMore; }
        public int scrollPosition() { return scrollPosition; }
        public long cachedAt() { return cachedAt; }

        public void setScrollPosition(int scroll) {
            this.scrollPosition = Math.max(0, scroll);
        }

        public boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - cachedAt > ttlMs;
        }

        public boolean isExpired() {
            return isExpired(DEFAULT_TTL_MS);
        }
    }

    public static final class HomeEntry {
        private volatile MainFrame.HomeData data;
        private volatile int scrollPosition;
        private volatile long cachedAt;

        public HomeEntry(MainFrame.HomeData data, int scrollPosition, long cachedAt) {
            this.data = data;
            this.scrollPosition = Math.max(0, scrollPosition);
            this.cachedAt = cachedAt;
        }

        public MainFrame.HomeData data() { return data; }
        public int scrollPosition() { return scrollPosition; }
        public long cachedAt() { return cachedAt; }

        public void setScrollPosition(int scroll) {
            this.scrollPosition = Math.max(0, scroll);
        }

        public void updateHistory(List<Models.ShowItem> newHistory) {
            if (this.data != null) {
                this.data = this.data.withHistory(newHistory);
                this.cachedAt = System.currentTimeMillis();
            }
        }

        public boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - cachedAt > ttlMs;
        }

        public boolean isExpired() {
            return isExpired(DEFAULT_TTL_MS);
        }
    }

    private final int maxEntries;
    private final long defaultTtlMs;
    private final LinkedHashMap<CatalogKey, CatalogEntry> entries;
    private final Map<String, HomeEntry> homeEntries = new HashMap<>();

    public CatalogRamCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_TTL_MS);
    }

    public CatalogRamCache(int maxEntries, long defaultTtlMs) {
        this.maxEntries = Math.max(1, maxEntries);
        this.defaultTtlMs = Math.max(1L, defaultTtlMs);
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CatalogKey, CatalogEntry> eldest) {
                return size() > CatalogRamCache.this.maxEntries;
            }
        };
    }

    public synchronized CatalogEntry get(CatalogKey key) {
        if (key == null) return null;
        CatalogEntry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.isExpired(defaultTtlMs)) {
            entries.remove(key);
            return null;
        }
        return entry;
    }

    public synchronized void put(CatalogKey key, List<Models.ShowItem> items, int page,
                                 boolean hasMore, int scrollPosition) {
        if (key == null || items == null) return;
        List<Models.ShowItem> bounded = items.size() > MAX_ITEMS_PER_ENTRY
                ? items.subList(0, MAX_ITEMS_PER_ENTRY)
                : items;
        CatalogEntry entry = new CatalogEntry(
                bounded, page, hasMore, scrollPosition, System.currentTimeMillis());
        entries.put(key, entry);
    }

    public synchronized void updateScroll(CatalogKey key, int scrollPosition) {
        if (key == null) return;
        CatalogEntry entry = entries.get(key);
        if (entry != null && !entry.isExpired(defaultTtlMs)) {
            entry.setScrollPosition(scrollPosition);
        }
    }

    public synchronized HomeEntry getHome(String language) {
        String lang = language == null ? "" : language;
        HomeEntry entry = homeEntries.get(lang);
        if (entry == null) return null;
        if (entry.isExpired(defaultTtlMs)) {
            homeEntries.remove(lang);
            return null;
        }
        return entry;
    }

    public synchronized void putHome(String language, MainFrame.HomeData data, int scrollPosition) {
        if (data == null) return;
        String lang = language == null ? "" : language;
        homeEntries.put(lang, new HomeEntry(data, scrollPosition, System.currentTimeMillis()));
    }

    public synchronized void updateHomeScroll(String language, int scrollPosition) {
        String lang = language == null ? "" : language;
        HomeEntry entry = homeEntries.get(lang);
        if (entry != null && !entry.isExpired(defaultTtlMs)) {
            entry.setScrollPosition(scrollPosition);
        }
    }

    public synchronized int getHomeScroll(String language) {
        String lang = language == null ? "" : language;
        HomeEntry entry = homeEntries.get(lang);
        return entry != null && !entry.isExpired(defaultTtlMs) ? entry.scrollPosition() : 0;
    }

    public synchronized MainFrame.HomeData updateHomeHistory(String language, List<Models.ShowItem> newHistory) {
        String lang = language == null ? "" : language;
        HomeEntry entry = homeEntries.get(lang);
        if (entry != null && !entry.isExpired(defaultTtlMs)) {
            entry.updateHistory(newHistory);
            return entry.data();
        }
        return null;
    }

    public synchronized void invalidate(MainFrame.Mode mode) {
        if (mode == null) return;
        if (mode == MainFrame.Mode.HOME) {
            homeEntries.clear();
        } else {
            entries.entrySet().removeIf(e -> e.getKey().mode() == mode);
        }
    }

    public synchronized void invalidateHome() {
        homeEntries.clear();
    }

    public synchronized void invalidateAll() {
        entries.clear();
        homeEntries.clear();
    }

    public synchronized int size() {
        entries.entrySet().removeIf(e -> e.getValue().isExpired(defaultTtlMs));
        return entries.size();
    }

    public synchronized boolean isEmpty() {
        return size() == 0;
    }

    public synchronized void clear() {
        invalidateAll();
    }
}
