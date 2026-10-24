# Streamflix Desktop - PR Handoff Package & Candidate Evaluation Build

> **ARCHIVO HISTÓRICO:** este handoff y sus SHA de candidato preceden al merge real del PR #14 y pueden contener afirmaciones de QA o identidades no confirmadas. No representan el estado actual de `main`. Para evidencia verificable consultar [VERIFIED_WINDOWS_GUI_2026-10-24.md](../VERIFIED_WINDOWS_GUI_2026-10-24.md).

## Metadata

- **Candidate Branch**: `candidate/reconciled-ux-baseline`
- **Candidate Commit SHA**: `6920b346e431abc75715113d99ce7718afd64332`
- **Upstream Target Branch**: `origin/main`
- **Upstream Target Base Commit**: `724bd724d69d15d2ca87f62ca4dd925288e36e11`
- **Candidate Bundle**: `docs/wip-preservation/candidate-reconciled-ux-baseline.bundle`
  - SHA-256: `5054170e558fcca03d4e5b0192570dbeb5cba2399a7c4faecb06028caba64094`
- **Unified Patch**: `docs/wip-preservation/owner-valuable-wip.patch`
  - SHA-256: `2b8e85efa1c751b247e0c325ca74a8ae3c804823afb94bb25784fa1d091ebbb9`
- **Evaluation Build JAR**: `docs/candidate-evaluation/streamflix-desktop.jar`
  - SHA-256: `8ebc9ebb688554be6fec73d3fb650eba6586215360476568765c57c39f06b778`
  - Runtime Dependencies: `docs/candidate-evaluation/lib/`

---

## 1. Consolidated Changes Summary

1. **Owner UX WIP Preservation & Reconciliation**:
   - Reconciled all 44 uncommitted and untracked files from the owner workspace.
   - Identified 27 files already cleanly merged into `origin/main` (PR #10–#13).
   - Preserved and cleanly integrated the 17 unique valuable owner WIP modifications.
   - Maintained raw bit-for-bit backups in `docs/wip-preservation/raw-wip/` without running destructive clean/reset operations.

2. **Cinematic Swing UI Redesign**:
   - Deep charcoal palette (`#0A0C11`, `#131720`, `#191E2A`, `#2A3140`), off-white typography (`#F6F7F9`), and restrained warm red (`#C92A36`) actions.
   - Antialiased rounded controls (`Theme.RADIUS_CONTROL` = 12 px, `Theme.RADIUS_COMPACT` = 6 px) in `EpisodeRow`, `HeroPanel`, `LandscapeCard`, `SportsCompactCard`, `Theme`, `TitleDetailView`, and `TmdbGuideDialog`.
   - Honest typographic fallback in `ArtworkPanel` preventing layout collapse when images are not yet cached, without fabricating fake imagery.
   - Persistent 2 px `#FFDD68` keyboard focus outlines independent of pointer hover.
   - Seamless embedded in-frame mpv playback inside `MainFrame` (`PLAYER` card) with full overlay controls and contextual `Escape` navigation return.

3. **AUD-001 Saturation Handler Fix in `VodSearchCoordinator.java`**:
   - Bounded thread pool (`CORE_POOL_SIZE`=16, `MAX_POOL_SIZE`=32, queue=64) with non-blocking `AbortPolicy`.
   - Verified that queue overflow catches `RejectedExecutionException`, marks providers as `REJECTED`, and completes immediately without executing queries inline on caller threads.

---

## 2. Verification Evidence

### 2.1 Deterministic Java 17 Test Suites (31 Suites Passed)
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
- `VodSearchCoordinatorTest`: OK (including `testExecutorSaturationUnderQueueOverflow` and `testBoundedExecutorConfiguration`)
- `ProviderHealthStatsTest`: OK
- `ProviderFallbackCoordinatorTest`: OK
- `CatalogRamCacheTest`: OK
- `NavigationStateTest`: OK
- `EmbeddedPlayerIntegrationTest`: OK
- `HttpRegressionTest`: OK

### 2.2 Native GUI E2E Multi-Viewport Visual Evidence
Executed via `WindowsNativeGuiE2E`:
- `01-home-cinematic-1480x900.png` (1480x900, 131,076 bytes, sha256: `1819759ad04a...`, 1041 unique colors sampled)
- `02-home-compact-1024x768.png` (1024x768, 87,578 bytes, sha256: `252dd683c88a...`, 653 unique colors sampled)
- `03-search-populated.png` (1280x800, 51,035 bytes, sha256: `f755fc86aa39...`, 553 unique colors sampled)
- `04-detail-view.png` (1280x800, 105,783 bytes, sha256: `f3630c0858d8...`, 642 unique colors sampled)
- `05-settings-dialog.png` (782x636, 27,391 bytes, sha256: `5718be88c226...`, 148 unique colors sampled)
- `06-embedded-player.png` (1280x800, 46,205 bytes, sha256: `670f486efe43...`, 239 unique colors sampled)
- `07-player-restored.png` (1280x800, 81,250 bytes, sha256: `8cb73a392277...`, 591 unique colors sampled)
- Manifest location: `docs/candidate-evaluation/gui-e2e/manifest.json`

---

## 3. Host Relay / Coordinator Handoff Instructions

To publish and merge this candidate from an egress-authorized host relay:

1. **Import Candidate Branch**:
   ```bash
   git fetch docs/wip-preservation/candidate-reconciled-ux-baseline.bundle candidate/reconciled-ux-baseline:candidate/reconciled-ux-baseline
   ```
   Or apply the patch onto origin/main:
   ```bash
   git checkout -b feat/cinematic-swing-ux-reconciled origin/main
   git am docs/wip-preservation/owner-valuable-wip.patch
   ```

2. **Push to Remote Origin**:
   ```bash
   git push origin feat/cinematic-swing-ux-reconciled
   ```

3. **Open Pull Request into `main`**:
   - Title: `feat(ux): cinematic Swing UI redesign, owner WIP reconciliation, and non-blocking saturation handler`
   - Target: `main`
   - Verify CI passes all deterministic checks and Windows native GUI smoke.
