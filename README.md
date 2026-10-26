# Streamflix Desktop for Windows

Streamflix Desktop is a Windows/JVM port of the Streamflix Reborn provider architecture. It keeps the provider/extractor model, replaces Android-only UI and player components with desktop implementations, and packages as a native Windows app image.

## Download for Windows

**Recommended:** [download the Streamflix Desktop EXE installer](https://github.com/Angel52781/streamflix-desktop/releases/latest/download/StreamflixDesktop-Setup.exe). It installs per user and does not require a machine-wide deployment.

Run `StreamflixDesktop-Setup.exe` and follow the Windows installer. The installer includes the Java runtime. On the first playback attempt that needs mpv, Streamflix downloads the pinned runtime from its upstream GitHub release, verifies its SHA-256, and stores it under `%LOCALAPPDATA%\\Streamflix\\runtime\\mpv`.

- **Managed/admin deployment:** [download the MSI installer](https://github.com/Angel52781/streamflix-desktop/releases/latest/download/StreamflixDesktop-Setup.msi). The MSI is intended for machine-wide installation managed by an administrator or deployment tooling.
- **Portable/advanced use:** [download the portable ZIP](https://github.com/Angel52781/streamflix-desktop/releases/latest/download/StreamflixDesktop-windows.zip), extract it, and run `StreamflixDesktop.exe`. This package is also retained for compatibility with the in-app updater.

You can also browse versioned releases and checksums on the [GitHub Releases page](https://github.com/Angel52781/streamflix-desktop/releases). GitHub's automatic **Source code (zip)** and **Source code (tar.gz)** downloads are source snapshots for developers; they are not Windows installers and do not contain a ready-to-run packaged application.

## Current stable release

Source version: **1.4.1** (check GitHub Releases for actual published availability)

Version **1.4.1** builds on the 1.4.0 native UX release with more discreet top-navigation keyboard focus, full-resolution TMDb backdrops on catalog cards, reversible pop-out playback and a true monitor-bounds fullscreen path. The Sports Hub, provider fallback and verified mpv provisioning remain supported. Published availability depends on the actual GitHub release, not this source declaration alone.

### Core experience

- Java 17-compatible JVM application with Swing + FlatLaf
- Streamflix visual identity with a native Windows app icon, branded window surfaces and a coherent DPI-independent vector control icon system
- Cinematic desktop navigation with Home, Movies, Series, Live TV, Sports and Mi lista
- Dedicated Sports hub with live/today/upcoming event sections and multi-sport filters
- Home discovery rails for Horror, Thriller, Drama and Comedy
- Streaming-style rail navigation with visible previous/next controls and contextual horizontal wheel zones
- "Ver más" actions route Home shelves into filtered Movie/Series catalogs
- Movie and Series catalog filters for popular content and genres
- Unified TMDb catalog with **English (en-US)** or **Spanish (es-ES)** selected from Settings
- TMDb API key or Read Access Token configured locally from Settings
- High-resolution TMDb posters/backdrops with HiDPI-aware image rendering
- Scoped, focus-stable search with ES/EN TMDb fallback merging and a cached daily TV-title prefix index for partial Series queries
- Visible horizontal season navigation
- Streaming-style episode rows with stills, synopsis and playback progress
- Continue Watching with persistent movie/episode progress and resume
- Persistent Mi lista/history under %APPDATA%\\Streamflix
- Atomic settings/userdata writes and test data-directory isolation
- Persistent bounded image cache under `%LOCALAPPDATA%\\Streamflix\\cache\\images`
- Local rotating diagnostics log with credential redaction
- In-app GitHub Release checks with SHA-256 verified portable updates
- Progressive catalog loading while scrolling

### Playback

- mpv as the playback engine, provisioned once from a pinned upstream GitHub release with SHA-256 verification
- mpv video embedded directly inside MainFrame content view without secondary windows; pop-out window available as explicit secondary action
- Real fullscreen acquisition on Windows with seamless overlay controls
- Loading state while extraction/player startup is in progress (honest progress, no fake percentages)
- Automatic server fallback based on playback startup, not extraction success alone
- Local server ranking learns which hosts start faster and fail less on this machine
- Manual server selection
- Playback headers forwarded to mpv
- Pause/resume, seek ±10 s, timeline, volume, audio-track and subtitle selection
- Stable in-frame and windowed player chrome with zero canvas resize flash; controls auto-hide only in fullscreen
- Configurable quality: Automatic, Data Saver, Balanced, High and Maximum
- Automatic HLS quality starts below mpv's maximum-bitrate default and can downgrade after sustained buffering
- Configurable preferred audio and subtitle language
- TMDb playback currently certified through VixSrc for movies and episodes in EN/ES

### Catalog/provider inventory

Metadata / VOD:

- TMDb (EN)
- TMDb (ES)
- FanPelis
- RidoMovies
- PelisflixHD
- AnimeWorld
- Series Turcas
- La Cartoons
- AnimeSaturn
- AnimeUnity
- MEGAKino

Live TV:

- IPTV Spain
- IPTV All World
- Pluto TV MX
- Pluto TV ES
- Pluto TV US

Sports data and playback:

- Free-only aggregation: TheSportsDB v1 public access plus Powered by [SportScore](https://sportscore.com/); no paid sports API is required
- TheSportsDB queries are partitioned by sport and deduplicated to improve free coverage without exceeding its documented free request cadence
- Broadcaster metadata preserves country/region and Streamflix prioritizes local/LatAm/international options before unrelated regions
- All reported broadcasters are resolved against the registered M3U providers with tolerant channel-name matching and quality-aware ranking
- Candidate streams receive a lightweight health check before automatic playback; startup and mid-stream failure can fall back to another signal
- Sports refresh automatically while the Sports section is open, and live events can also appear on Home
- Teams and competitions can be followed locally in `sports-favorites.json`
- `STREAMFLIX_SPORTS_COUNTRY` can override the OS country used for sports broadcaster prioritization
- Development-only sports fallback diagnostics can be enabled with `-Dstreamflix.dev=true`; Settings then exposes a button that simulates a live signal failure

Third-party providers can change or disappear without notice. A provider is not considered permanently healthy merely because it worked in a previous release.

## TMDb configuration

See [TMDb setup](docs/TMDB_SETUP.md) for the current account, credential and in-app setup steps.

Streamflix accepts either:

1. STREAMFLIX_TMDB_API_KEY
2. %APPDATA%\\Streamflix\\settings.json → tmdbApiKey

The environment variable has precedence.

The public desktop build currently uses a **BYOK (bring your own key)** model: each user obtains and configures their own TMDb API key or Read Access Token once. One credential works for both TMDb EN and TMDb ES. No real API key is stored in the repository or public binaries.

Settings includes an in-app step-by-step TMDb setup guide with direct links and editable/copyable examples for legitimate personal, educational/open-source or testing use.

## Build requirements

- Windows
- Complete JDK 17+ (java, javac, jar)
- jpackage
- WiX Toolset 3.0+ when creating the Windows installer (`release.ps1`)
- mpv for local packaging/self-test (`tools\\mpv\\mpv.exe` or `STREAMFLIX_MPV`); `.\\setup-mpv.ps1` provisions the pinned build

The build verifies locked dependencies before compilation. Public release ZIPs do **not** redistribute `mpv.exe`; `release.ps1` removes the build-time copy after the packaged self-test, and the application provisions mpv on the first playback attempt that needs it.

### Build the app image

PowerShell:

    .\\build.ps1

Output:

    dist\\StreamflixDesktop\\StreamflixDesktop.exe

### Build JAR only

    .\\build.ps1 -JarOnly

### Run development diagnostics

Build the JAR, then launch Streamflix with the development-only Diagnostics tab enabled:

    .\\build.ps1 -JarOnly
    java -Dstreamflix.dev=true -jar .\\build\\streamflix-desktop.jar

Open `Configuración -> Diagnóstico` while a sports event is playing and use
`Simular caída de señal deportiva` to verify automatic signal fallback.

### Create Windows installers + portable release

    .\\release.ps1

The release script builds from source, runs the packaged `--self-test`, then creates versioned/stable EXE, MSI and portable ZIP assets with SHA-256 checksums. `StreamflixDesktop-Setup.exe` is the recommended download, `StreamflixDesktop-Setup.msi` is for managed/admin deployment, and `StreamflixDesktop-windows.zip` remains available for portable/advanced use and as the stable asset consumed by the in-app updater.

## Deterministic test gates

The build currently runs:

- JsonTest
- ProviderFixtureTest
- TmdbFixtureTest
- TmdbTitleIndexTest
- MainFrameSearchTest
- M3uPlaylistTest
- M3uLiveProviderTest
- ExtractorFixtureTest
- DependencySmokeTest
- UserDataTest
- MpvPlayerTest
- PlaybackFallbackTest
- PlaybackRecoveryTest
- SubtitleAggregatorTest
- PlaybackServerStatsTest
- UpdateServiceTest
- ImageDiskCacheTest
- DiagnosticsTest
- MpvBootstrapTest
- BrandAssetsTest

Additional opt-in live gates validate real third-party/network behavior and are intentionally not part of deterministic builds.

## Architecture

    Swing desktop UI
          |
          +--> ProviderRegistry
          |      |-- TMDb EN / ES
          |      |-- VOD providers
          |      \`-- M3U live providers
          |
          +--> Server discovery / fallback
          |
          +--> ExtractorRegistry
          |
          +--> mpv embedded window + JSON IPC
          |
          +--> GitHub Releases updater + SHA-256 verification
          |
          +--> %APPDATA%\\Streamflix
          |      |-- settings.json
          |      |-- favorites.json
          |      \`-- history.json
          |
          \`--> %LOCALAPPDATA%\\Streamflix
                 |-- cache\\images
                 |-- logs\\streamflix.log
                 \`-- runtime\\mpv

## Known limitations

- TMDb has one certified playback route in the current release (VixSrc); additional independent TMDb playback routes remain desirable for resilience.
- Some upstream providers rely on unstable public websites and may require maintenance after domain/HTML changes.
- Chromecast/casting parity is not included in the Windows release.
- Supabase/user-profile sync from Android is intentionally not included; the desktop app has no Streamflix login requirement.
- **Updating an existing installation:** In Settings → Buscar actualizaciones, writable portable installations can download, verify (SHA-256) and replace the app automatically. For installations created by the EXE/MSI installer, use the new release's installer **even if the old app offers a portable "Actualizar ahora" action**: the current self-updater checks write access rather than the Windows Installer registration and must not overwrite installer-managed files. Run the newer installer over the previous installation; do not manually uninstall first. User settings, favorites, history and the locally provisioned mpv runtime remain in separate profile folders and are not removed by normal application updates.
- A real public portable 1.3.6 → 1.3.7 update was validated end to end with SHA-256 verification, replacement, relaunch and no rollback/error log. Windows installer upgrades have not been separately validated for the 1.3.8 → 1.3.9 transition.
- First playback setup requires network access to the pinned upstream mpv GitHub release; once provisioned, the runtime is reused from `%LOCALAPPDATA%`.
- DRM/paywall bypass is intentionally out of scope.
- Sports availability remains best-effort: free public schedules and third-party IPTV streams can be incomplete, geo-restricted, stale or unavailable, and Streamflix does not bypass DRM or subscription access.

## Upstream and license

This project derives/ports functionality from Streamflix Reborn and retains the applicable Apache 2.0 licensing/attribution. See LICENSE and THIRD_PARTY_NOTICES.md.

The application does not host media. Users are responsible for ensuring they have the right to access content supplied by third-party sources.
