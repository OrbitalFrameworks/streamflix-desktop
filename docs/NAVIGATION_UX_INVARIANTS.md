# Navigation UX and RAM Cache Invariants

Streamflix Desktop implements a fast, responsive navigation architecture on Java Swing designed to eliminate redundant network operations, prevent skeleton flickering, and provide seamless state and focus continuity across sections and detail views.

---

## 1. Smooth, Interruptible Motion and Accessibility

All primary section transitions (Home, Movies, Series, Live, Sports, Details) are managed by `SmoothContentHost`.

### Rules
- **Snappy Crossfade**: Transitions use an interruptible 140 ms crossfade driven by 16 ms timer intervals on the EDT. The outgoing view snapshot fades out gracefully while the incoming view is immediately rendered underneath. Interactive smoothness and physical frame rates depend on native display hardware and compositor behavior, and are tracked as native validation debt.
- **EDT Safety**: The transition uses a single coalesced Swing `Timer` running exclusively on the Event Dispatch Thread (EDT). No background threads manipulate Swing hierarchies during transitions.
- **Immediate Interruption**: Any user mouse click immediately terminates the running transition (`stopTransition()`), achieving zero interaction latency.
- **Superseded Route Cancellation**: If a user switches sections rapidly before a previous transition concludes, pending callbacks for obsolete routes are canceled (`cancelTransition()`), preventing invalid state or focus leaks.
- **Reduced Motion Support**:
  - Automatically queries the Windows OS desktop property `win.animations.enabled`. When false, motion is disabled.
  - Supports the JVM property `-Dstreamflix.reducedMotion=true` (or `=1`).
  - When reduced motion is active or when running in headless/unrealized environments, transitions execute instantaneously (0 ms duration) without timer overhead.

---

## 2. Bounded In-Memory RAM Caching (`CatalogRamCache`)

To prevent repeated network requests, catalog pages and Home data are cached in a bounded, thread-safe RAM cache.

### Invariants
- **Capacity Bounds**: Maximum 32 catalog entries retained concurrently via an LRU (`LinkedHashMap` access-order) eviction policy.
- **Item Bounding**: Each catalog entry is capped at `MAX_ITEMS_PER_ENTRY = 300` items to safeguard heap memory against runaway queries.
- **TTL Expiration**: Entries expire after `DEFAULT_TTL_MS = 5 minutes`. Expired entries are evicted automatically upon access or during size checks.
- **Canonical Keying**: Catalog entries are keyed by `(Mode, providerId, genreId, normalizedQuery, searchScope, language)`.
- **In-Memory Home Shelf Cache**:
  - Complete Home data (`movies`, `series`, `live`, `sports`, `discovery`) is cached in `HomeEntry`.
  - In-place history updates: when a user watches a title and returns Home, `updateHomeHistory()` updates the "Continuar viendo" rail without triggering a network refetch of remote shelves.
- **Deterministic Invalidation**:
  - `invalidate(Mode)`: clears cached items for a specific section (e.g. on manual refresh).
  - `invalidateAll()`: triggered upon user settings changes (such as TMDb catalog language change or API key configuration).

---

## 3. State, Scroll and Focus Continuity

- **Scroll Preservation**:
  - Scroll positions are continuously saved per section (`saveCurrentModeScroll()`).
  - Switching between Home, Movies, Series, and Search restores the exact previous vertical scroll offset.
  - Closing detail views restores the origin section's exact scroll offset.
- **Keyboard and Focus Restoration**:
  - Opening a detail view records the active focus component and target item ID.
  - Returning from details restores focus to the originating `LandscapeCard` or `HeroPanel` play button via recursive container search (`focusCardInContainer`), falling back to the section navigation button.
- **Hierarchical Detail Backstack**:
  - Navigating from one detail view to another (e.g., related titles or franchise sequels) pushes the previous route onto `detailHistoryStack`.
  - Closing details pops the stack sequentially until returning to the root catalog/home view.

---

## 4. Architectural Characteristics vs. Uncached Baseline

| Scenario | Baseline (Without RAM Cache / Instant Navigation) | Optimized (SmoothContentHost + CatalogRamCache) | Observed Mechanism & Benefit |
|---|---|---|---|
| **Tab switch (Home ↔ Movies ↔ Series)** | Remote network I/O + JSON parse + skeleton flash | Instantaneous in-memory lookup (`CatalogRamCache.get`) | Zero skeleton flicker, zero redundant HTTP calls while TTL is valid |
| **Return from Title Details to Home** | Full remote reload of Home rails | RAM cache hit + in-place history update (`updateHomeHistory`) | Zero remote round-trips for remote rails; immediate paint |
| **Scroll position retention** | Reset to 0 (top of page on every revisit) | Preserved to exact vertical pixel offset | Seamless return to previously browsed content offset |
| **Detail-to-detail navigation** | Lost parent context (returns directly to catalog) | Full LIFO backstack unwinding (`detailHistoryStack`) | Native desktop back-navigation fidelity across nested items |
| **EDT safety during motion** | Abrupt card replacement or uncoordinated repaints | Single coalesced Swing timer with wall-clock interpolation | Fluid visual continuity without EDT stalls or thread race conditions |

> [!NOTE]
> Physical display frame rates and network latency depend on local hardware and external server responsiveness. Continuous integration test suites run in headless environments and verify logical state transitions, cache eviction, and thread safety rather than measuring interactive panel FPS. Interactive display smoothness and 60 fps pacing remain documented validation debt requiring native verification.

---

## 5. Verification and Test Coverage

Deterministic test suites registered in `build.sh` and `build.ps1`:
1. `dev.streamflix.desktop.CatalogRamCacheTest`:
   - `testPutAndGet`: Verifies storage, retrieval, and scroll updating.
   - `testCacheMiss`: Verifies null return on uncached queries.
   - `testTtlExpiration`: Verifies automatic eviction after TTL expiration.
   - `testLruEviction`: Verifies eviction of eldest entries when capacity exceeds limit.
   - `testMemoryBounding`: Verifies item clamping at `MAX_ITEMS_PER_ENTRY`.
   - `testInvalidateByMode`: Verifies isolated invalidation per mode.
   - `testInvalidateAll`: Verifies full cache flushing.
   - `testHomeCacheAndHistoryUpdate`: Verifies in-place history updates without remote shelf refetch.
   - `testConcurrency`: Verifies multi-threaded thread-safety under heavy concurrent load.

2. `dev.streamflix.desktop.NavigationStateTest`:
   - `testReducedMotionProperty`: Verifies behavior under `-Dstreamflix.reducedMotion`.
   - `testSmoothContentHostInstantCardSwitch`: Verifies card switching and completion callbacks.
   - `testTransitionInterruption`: Verifies interruption safety.
   - `testDetailNavigationStack`: Verifies LIFO unwinding of detail backstack.
