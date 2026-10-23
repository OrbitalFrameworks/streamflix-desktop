# Port status

Upstream reference reviewed: `streamflix-reborn2/streamflix` at commit `91b27174c8864b5365c0966fd18221b428c2700a` (2026-09-07).

## Current Windows state

Current public stable release: **1.3.9**.

| Area | Status | Notes |
|---|---|---|
| Windows desktop UI | Implemented | Swing/JVM, mouse + keyboard, HiDPI-aware |
| Fluid navigation & RAM cache | Implemented | 140 ms interruptible crossfade, reduced-motion compliance, bounded in-memory catalog/Home cache with TTL, view recycling, backstack for detail views, and scroll/focus preservation |
| Visual identity / iconography | Implemented in 1.3.8 | Streamflix S app mark, Windows package icon and DPI-independent vector controls replace legacy Unicode glyphs |
| Movies / Series / Search | Implemented | Concurrent multi-provider VOD search across TMDb and all registered VOD providers with bounded timeouts, canonical deduplication, relevance ranking, and candidate tracking |
| Catalog filters | Implemented | Popular and genre filters with progressive loading |
| Home rails | Implemented | Contextual horizontal wheel, arrows, Ver más |
| Seasons / episodes | Implemented | Includes specials and progress-aware episode UI |
| History / favorites | Implemented | Persistent local data with atomic writes |
| Continue watching | Implemented | Movie and episode resume |
| Direct HLS/MP4 playback | Implemented | In-frame embedded player in MainFrame without secondary windows, full overlay controls without resize flash, mpv + JSON IPC, runtime auto-provisioned from pinned upstream release, and explicit pop-out mode |
| Playback fallback | Implemented | Multi-provider sequential failover with provider health ranking, quality signals, cooldown heuristics, resume preservation, and server-level ranking |
| Playback recovery | Implemented | Stall detection, bitrate downgrade and resume |
| IPTV / Pluto | Implemented | Spain / world IPTV and Pluto MX/ES/US |
| Sports hub | Implemented in 1.3.9 | Free-only multi-source schedules (TheSportsDB + SportScore), auto-refresh, favorites, Home live rail, country-aware multi-broadcaster IPTV resolution, health ranking and signal fallback |
| In-app updates | Implemented in 1.3.6 | GitHub Releases + SHA-256 + rollback-capable portable updater |
| Persistent image cache | Implemented in 1.3.6 | %LOCALAPPDATA%, bounded and expiring |
| Diagnostics / logs | Implemented in 1.3.6 | Local rotating log and privacy-filtered diagnostic ZIP |
| Chromecast | Not implemented | Windows parity remains future work |
| Profiles / Supabase | Out of scope | No Streamflix login planned for desktop |
## Validation

Validated on Windows:

- Java sources compile with `javac --release 17`.
- Deterministic tests run on every local build.
- `jpackage --type app-image` produces `StreamflixDesktop.exe` with bundled Java runtime.
- Packaged `--self-test` verifies runtime dependencies, provider registry, version consistency, build-time mpv and updater write access before the public package is stripped of the build-time mpv binary.
- Real movie and series playback paths have been validated through mpv.
- The image cache was observed writing real catalog images under `%LOCALAPPDATA%\Streamflix\cache\images`.
- `release.ps1` creates versioned and stable-name EXE, MSI and ZIP assets with SHA-256 files.
- The pinned mpv archive was independently re-downloaded from the exact upstream GitHub release and matched GitHub's published SHA-256.
- A clean public-package first-playback provisioning test with no mpv in PATH/global locations provisioned the runtime into a temporary `%LOCALAPPDATA%\Streamflix\runtime\mpv` in 4 seconds, then left Streamflix alive and responsive.
- Public ZIP inspection confirmed zero `mpv.exe` entries while retaining the four mpv provenance/license notice files.
- A real public portable update from 1.3.6 → 1.3.7 completed end to end in an isolated profile: release detection, ZIP download, published SHA-256 match, staged replacement, relaunch into 1.3.7, no rollback directory left behind and no update error log.

Deterministic gates currently include JSON, provider/TMDb/M3U fixtures, extractors, dependency smoke,
user data, mpv player, playback fallback/server ranking, updater parsing, image disk cache, diagnostics privacy,
mpv bootstrap, concurrent VOD search, provider health stats, playback failover coordinator, catalog RAM cache,
navigation state/motion tests, and embedded player integration tests.

## Release engineering

- `.github/workflows/ci.yml` runs deterministic builds/tests on Windows and Linux.
- `.github/workflows/release.yml` is tag-driven and refuses a tag that does not match `VERSION`.
- Release jobs download the pinned mpv archive for build/self-test, remove the build-time mpv binary from the public package, and publish stable-name and versioned EXE, MSI and ZIP assets with their SHA-256 files.
- The per-user EXE is the recommended download; the MSI is intended for managed/admin deployment; the portable ZIP remains available for advanced use and updater compatibility.
- Public installations provision mpv directly from the pinned upstream GitHub release on the first playback attempt that needs it, after SHA-256 verification; Streamflix does not redistribute `mpv.exe` in its ZIP.
- The application checks GitHub Releases asynchronously; no Streamflix backend is required.
- User settings, favorites and history live outside the application directory and are not replaced by updates.

## Remaining audit items

- Re-run the public updater E2E periodically across future release boundaries; the 1.3.6 → 1.3.7 portable path has already passed. Installed EXE/MSI users should update by running the newer installer over the previous installation; do not replace Windows Installer-managed application files with the portable updater.
- Multi-provider VOD fallback allows transparent recovery across registered providers when a single route (e.g. VixSrc) is unavailable or degraded.
- Some public provider sites can change HTML, domain or anti-bot behavior without notice.
- `MainFrame` and `EmbeddedPlayerWindow` remain large classes and should be decomposed incrementally, not rewritten wholesale.
- More silent provider/extractor fallback catches should be converted to structured diagnostics where doing so does not create noisy logs.
- On-demand mpv provisioning at the first playback attempt that needs it depends on availability of the pinned upstream GitHub asset; the already provisioned runtime remains local and reusable if GitHub is later unavailable.
