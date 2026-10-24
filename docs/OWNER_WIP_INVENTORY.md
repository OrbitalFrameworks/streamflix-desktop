# Streamflix Desktop - Owner WIP Inventory and Reconciliation Audit

**Generated Date**: 2026-10-24T02:44:30Z
**Canonical Upstream Base**: `724bd724d69d15d2ca87f62ca4dd925288e36e11` (`origin/main`)
**Local Working Branch**: `work/streamflix-ux-resume-20261023` (base: `e1cc9d3`)
**Reconciled Candidate Branch**: `candidate/reconciled-ux-baseline` (commit: `6920b346e431abc75715113d99ce7718afd64332`)
**Git Bundle**: [`docs/wip-preservation/candidate-reconciled-ux-baseline.bundle`](wip-preservation/candidate-reconciled-ux-baseline.bundle)
**Unified Patch**: [`docs/wip-preservation/owner-valuable-wip.patch`](wip-preservation/owner-valuable-wip.patch)

## 1. Executive Summary

An exhaustive audit of all 44 uncommitted and untracked entries in the owner's working tree was conducted against canonical `origin/main` (`724bd72`).
- **Total uncommitted / untracked files audited**: 44
- **Category A (Byte-identical to `origin/main`)**: 27 files (incorporated in prior PRs #10–#13; no transplant needed).
- **Category B (Unique valuable owner WIP edits)**: 16 files (transplanted cleanly to candidate branch).
- **Category C (Untracked new tools / scripts)**: 1 file (`docs/screenshots/capture-xvfb.sh`; transplanted to candidate branch).
- **Working tree preservation**: All 44 files have been preserved bit-for-bit into `docs/wip-preservation/raw-wip/`. The local checkout was **not reset or cleaned** (`git reset`, `git clean`, `git restore` were not run).
- **Build & Verification**: The reconciled candidate branch compiled cleanly with Java 17 and passed all 30 deterministic test suites.

## 2. Category B & C: Unique Valuable Owner WIP Edits (17 Files)

These files contain unique, intentional improvements that refine typography, deterministic artwork fallback, keyboard focus outlines, antialiasing, rounded corner geometry, EDT timer disambiguation, and build isolation.

| File Path | Size (bytes) | SHA-256 (Local Disk) | SHA-256 (origin/main) | Key Enhancements |
|:---|:---:|:---:|:---:|:---|
| `DESIGN.md` | 9181 | `e43a97210894...` | `551484ddf370...` | Transversal Design Tokens & Component Geometry section (Theme spacing scale, corner radii, antialiasing, timers, honest artwork fallback). |
| `build.sh` | 3737 | `13c1bfd2750e...` | `a71964d1a5bb...` | Configurable `STREAMFLIX_BUILD_DIR` and `STREAMFLIX_LIB_DIR` to support hermetic/isolated builds. |
| `docs/screenshots/README.md` | 3168 | `0b1726b058ec...` | `bb17607dd753...` | Documentation and execution guide for reproducible Xvfb runtime GUI capture. |
| `docs/screenshots/capture-xvfb.sh` | 1854 | `92cbd4c2a712...` | `NEW_FILE` | Native Xvfb headless display capture script for executable runtime GUI verification. |
| `src/main/java/dev/streamflix/desktop/ArtworkPanel.java` | 6854 | `2f0e503ce170...` | `1edd0ebac2a2...` | Deterministic honest typographic fallback when cached images are unavailable without fabricating assets. |
| `src/main/java/dev/streamflix/desktop/EmbeddedPlayerView.java` | 48234 | `010498ec332e...` | `ae486136688e...` | Explicit `javax.swing.Timer` disambiguation to prevent CI ambiguity. |
| `src/main/java/dev/streamflix/desktop/EpisodeRow.java` | 5417 | `56dafacec90e...` | `d1c74929f84d...` | Antialiased rendering, `Theme.RADIUS_CONTROL` rounded background and border, rounded progress bar. |
| `src/main/java/dev/streamflix/desktop/HeroPanel.java` | 6218 | `9f8c79d46583...` | `523aee46163a...` | Refined heading size (40f), eyebrow contrast (#DCDCE0), Theme spacing scale for metadata, and fallback title binding. |
| `src/main/java/dev/streamflix/desktop/LandscapeCard.java` | 7821 | `945ec36316dd...` | `8ef673f14c56...` | Antialiased rounded card background and persistent 2 px #FFDD68 keyboard focus border. |
| `src/main/java/dev/streamflix/desktop/MainFrame.java` | 78276 | `4d1aaadd0eb8...` | `06b375a4b0d8...` | Navigation, focus outline styling, smooth transition host, explicit `javax.swing.Timer`. |
| `src/main/java/dev/streamflix/desktop/SmoothContentHost.java` | 13500 | `e825a5af2ecb...` | `96ccd7d73be1...` | Explicit `javax.swing.Timer` disambiguation for EDT transition timer. |
| `src/main/java/dev/streamflix/desktop/SportsCompactCard.java` | 3647 | `ab9e11b09f2a...` | `dd46fc66a255...` | Antialiased rounded card rendering, border hover luminescence, 2 px focus outline. |
| `src/main/java/dev/streamflix/desktop/SportsHubPanel.java` | 17814 | `07202add81f0...` | `b0a5c4a16734...` | Refined spacing, rounded panel styling, clean layout hierarchy. |
| `src/main/java/dev/streamflix/desktop/StreamingSkeleton.java` | 2751 | `b9fb926bfe4b...` | `172b47ee8376...` | Antialiased rounded rect placeholder shimmer geometry. |
| `src/main/java/dev/streamflix/desktop/Theme.java` | 15454 | `2741e7e047be...` | `ffe6c62ee06f...` | RoundedPanel component, spacing scale constants, button hover/press tactile darkening, focus constants. |
| `src/main/java/dev/streamflix/desktop/TitleDetailView.java` | 53916 | `5675b4d6677f...` | `31284a2d2c59...` | Refined badge geometry, metadata layout, antialiased card surfaces, and fallback title binding. |
| `src/main/java/dev/streamflix/desktop/TmdbGuideDialog.java` | 11372 | `a4765dedd6f9...` | `cde29690a0f8...` | Rounded copy dialog panel, `javax.swing.Timer` disambiguation. |

## 3. Category A: Byte-Identical to `origin/main` (27 Files)

These files match `origin/main` byte-for-byte. They were developed by the owner on `work/streamflix-ux-resume-20261023` and incorporated into upstream commits `16f0ad1`, `96f6ea9`, `53c0a71`, and `724bd72` (PR #10–#13). No transplanting was required.

| File Path | Size (bytes) | SHA-256 |
|:---|:---:|:---:|
| `PORT_STATUS.md` | 6543 | `e71346361234235a...` |
| `README.md` | 12507 | `3cf7df2ca7e04a20...` |
| `build.ps1` | 7605 | `5346a10ff72f65c9...` |
| `docs/NAVIGATION_UX_INVARIANTS.md` | 7391 | `382b11aa39a1604a...` |
| `docs/PLAYER_UX_INVARIANTS.md` | 4005 | `5aa096eda8167cb1...` |
| `docs/SEARCH_UX_INVARIANTS.md` | 3485 | `c71e5677d3bf0d88...` |
| `docs/screenshots/01-startup-loading.png` | 32015 | `85a28dbe703d7f99...` |
| `docs/screenshots/02-home-populated.png` | 361965 | `f509d36276c5e270...` |
| `docs/screenshots/03-search-view.png` | 163617 | `fde06a1da24aeacd...` |
| `docs/screenshots/04-detail-view.png` | 276562 | `a7df6498f54cdd0e...` |
| `docs/screenshots/05-settings-dialog.png` | 58521 | `690534bc2615a72f...` |
| `docs/screenshots/06-embedded-player.png` | 418791 | `fe07aa0af5696d22...` |
| `src/main/java/dev/streamflix/desktop/CatalogRamCache.java` | 8346 | `aef2f488634e81bc...` |
| `src/main/java/dev/streamflix/desktop/Diagnostics.java` | 5911 | `3fd9d8965b05042d...` |
| `src/main/java/dev/streamflix/desktop/EmbeddedPlayerWindow.java` | 14648 | `37772841a5ce5f45...` |
| `src/main/java/dev/streamflix/desktop/MpvBootstrap.java` | 12454 | `34a5d523cc6b3763...` |
| `src/main/java/dev/streamflix/desktop/ProviderFallbackCoordinator.java` | 9093 | `91baf7f92598456a...` |
| `src/main/java/dev/streamflix/desktop/ProviderHealthStats.java` | 14003 | `6ed326457b02beb0...` |
| `src/main/java/dev/streamflix/desktop/SettingsDialog.java` | 30372 | `0252e43494bbe570...` |
| `src/main/java/dev/streamflix/desktop/VodSearchCoordinator.java` | 15613 | `49dea09318bbedd4...` |
| `src/test/java/dev/streamflix/desktop/CatalogRamCacheTest.java` | 12096 | `1e80fd05551ceb13...` |
| `src/test/java/dev/streamflix/desktop/EmbeddedPlayerIntegrationTest.java` | 3904 | `0d94811f3384ee0f...` |
| `src/test/java/dev/streamflix/desktop/MpvBootstrapTest.java` | 3734 | `f6dca58374501fc4...` |
| `src/test/java/dev/streamflix/desktop/NavigationStateTest.java` | 14559 | `9c9f20093fe56b8e...` |
| `src/test/java/dev/streamflix/desktop/ProviderFallbackCoordinatorTest.java` | 12155 | `ff4771a089aa449e...` |
| `src/test/java/dev/streamflix/desktop/ProviderHealthStatsTest.java` | 5637 | `11cc086c9003ea64...` |
| `src/test/java/dev/streamflix/desktop/VodSearchCoordinatorTest.java` | 12091 | `c77856508df3a22d...` |

## 4. Preservation and Verification Evidence

### 4.1 Preservation Directory Structure
```text
docs/wip-preservation/
├── candidate-reconciled-ux-baseline.bundle (Git bundle containing commit 9193aa8 on upstream/main)
├── owner-valuable-wip.patch               (Unified git patch with all 17 valuable changes)
├── diffs/                                 (17 per-file diffs against origin/main)
└── raw-wip/                               (All 44 uncommitted/untracked files preserved bit-for-bit)
```

### 4.2 Verification on Candidate Branch
- **Compilation**: `javac --release 17` compiled 57 source files with zero errors or warnings.
- **Unit & Integration Tests**: 30 deterministic test suites executed and passed:
  - `JsonTest`: OK
  - `ProviderFixtureTest`: OK
  - `TmdbFixtureTest`: OK
  - `TmdbTitleIndexTest`: OK
  - `M3uPlaylistTest`: OK
  - `M3uLiveProviderTest`: OK
  - `SportsResolverTest`: OK
  - `SportsDataTest`: OK
  - `SportsPlaybackTest`: OK
  - `SportsFavoritesTest`: OK
  - `ExtractorFixtureTest`: OK
  - `DependencySmokeTest`: OK
  - `UserDataTest`: OK
  - `MpvPlayerTest`: OK
  - `MainFrameSearchTest`: OK
  - `PlaybackFallbackTest`: OK
  - `PlaybackRecoveryTest`: OK
  - `SubtitleAggregatorTest`: OK
  - `PlaybackServerStatsTest`: OK
  - `UpdateServiceTest`: OK
  - `ImageDiskCacheTest`: OK
  - `DiagnosticsTest`: OK
  - `MpvBootstrapTest`: OK
  - `BrandAssetsTest`: OK
  - `VodSearchCoordinatorTest`: OK
  - `ProviderHealthStatsTest`: OK
  - `ProviderFallbackCoordinatorTest`: OK
  - `CatalogRamCacheTest`: OK
  - `NavigationStateTest`: OK
  - `EmbeddedPlayerIntegrationTest`: OK

### 4.3 Working Tree Protection
The active checkout `/srv/orbital/workspaces/streamflix-desktop` on branch `work/streamflix-ux-resume-20261023` was maintained strictly non-destructively: **no `git reset`, `git clean`, or `git restore`** operations were executed.
