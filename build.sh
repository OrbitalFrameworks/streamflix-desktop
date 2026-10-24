#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
if [[ -n "${STREAMFLIX_JDK:-}" ]]; then
    export PATH="$STREAMFLIX_JDK/bin:$STREAMFLIX_JDK:$PATH"
elif [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
for tool in java javac jar curl; do command -v "$tool" >/dev/null || { echo "Missing tool: $tool" >&2; exit 1; }; done
sha256() {
    if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d ' ' -f 1
    else shasum -a 256 "$1" | cut -d ' ' -f 1; fi
}
version="$(tr -d '\r\n' < VERSION)"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid VERSION' >&2; exit 1; }
if [[ -z "${STREAMFLIX_LIB_DIR:-}" ]]; then
    if [[ -d "/srv/orbital/workspaces/streamflix-java-validation-20261023/lib" ]]; then
        LIB_DIR="/srv/orbital/workspaces/streamflix-java-validation-20261023/lib"
    elif [[ ! -w "$ROOT" && ! -d "$ROOT/lib" ]]; then
        LIB_DIR="/tmp/streamflix-lib"
    else
        LIB_DIR="$ROOT/lib"
    fi
else
    LIB_DIR="$STREAMFLIX_LIB_DIR"
fi

if [[ -z "${STREAMFLIX_BUILD_DIR:-}" ]]; then
    if [[ ! -w "$ROOT" || (-e "$ROOT/build" && ! -w "$ROOT/build") ]]; then
        BUILD_DIR="/tmp/streamflix-build"
    else
        BUILD_DIR="$ROOT/build"
    fi
else
    BUILD_DIR="$STREAMFLIX_BUILD_DIR"
fi
mkdir -p "$LIB_DIR"
deps=()
while IFS='|' read -r relpath hash url; do
    [[ -z "$relpath" || "$relpath" == \#* ]] && continue
    url="${url%$'\r'}"
    target="${LIB_DIR}/${relpath}"
    mkdir -p "$(dirname "$target")"
    if [[ ! -f "$target" ]]; then
        partial="$target.part"
        trap 'rm -f "$partial"' EXIT
        curl --fail --location --silent --show-error --retry 2 --output "$partial" "$url"
        [[ "$(sha256 "$partial")" == "$hash" ]] || { echo "SHA-256 mismatch: $target" >&2; exit 1; }
        mv "$partial" "$target"
        trap - EXIT
    fi
    [[ "$(sha256 "$target")" == "$hash" ]] || { echo "SHA-256 mismatch: $target. Remove corrupt cache entry and retry." >&2; exit 1; }
    deps+=("$target")
done < dependencies.lock
[[ ! -L "$BUILD_DIR" ]] || { echo 'Refusing linked build directory' >&2; exit 1; }
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/test-classes" "$BUILD_DIR/lib"
cp "${deps[@]}" "$BUILD_DIR/lib/"
sep=:
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) sep=';'; export MSYS2_ARG_CONV_EXCL='*';; esac
dep_cp="$(IFS="$sep"; echo "${deps[*]}")"
sources=()
while IFS= read -r -d '' file; do sources+=("$file"); done < <(find src/main/java -name '*.java' -print0 | LC_ALL=C sort -z)
tests=()
while IFS= read -r -d '' file; do tests+=("$file"); done < <(find src/test/java -name '*.java' -print0 | LC_ALL=C sort -z)
java -version
javac --release 17 -encoding UTF-8 -cp "$dep_cp" -d "$BUILD_DIR/classes" "${sources[@]}"
flat_deps=()
for d in "${deps[@]}"; do flat_deps+=("lib/${d##*/}"); done
printf 'Manifest-Version: 1.0\nMain-Class: dev.streamflix.desktop.App\nImplementation-Version: %s\nClass-Path: %s\n\n' "$version" "${flat_deps[*]}" > "$BUILD_DIR/MANIFEST.MF"
jar --create --file "$BUILD_DIR/streamflix-desktop.jar" --date=2020-01-01T00:00:00Z --manifest "$BUILD_DIR/MANIFEST.MF" -C "$BUILD_DIR/classes" .
javac --release 17 -encoding UTF-8 -cp "$BUILD_DIR/classes${sep}${dep_cp}" -d "$BUILD_DIR/test-classes" "${tests[@]}"
for test in JsonTest ProviderFixtureTest TmdbFixtureTest TmdbTitleIndexTest M3uPlaylistTest M3uLiveProviderTest SportsResolverTest SportsDataTest SportsPlaybackTest SportsFavoritesTest ExtractorFixtureTest DependencySmokeTest UserDataTest MpvPlayerTest MainFrameSearchTest PlaybackFallbackTest PlaybackRecoveryTest SubtitleAggregatorTest PlaybackServerStatsTest UpdateServiceTest ImageDiskCacheTest DiagnosticsTest MpvBootstrapTest BrandAssetsTest ArtworkPanelTest VodSearchCoordinatorTest ProviderHealthStatsTest ProviderFallbackCoordinatorTest CatalogRamCacheTest NavigationStateTest EmbeddedPlayerIntegrationTest HttpRegressionTest; do
    java -cp "$BUILD_DIR/streamflix-desktop.jar${sep}$BUILD_DIR/test-classes" "dev.streamflix.desktop.$test"
done
echo "Built: $BUILD_DIR/streamflix-desktop.jar (keep sibling lib directory)"
if [[ "${1:-}" == 'test' || "${1:-}" == '--test' ]]; then
    exit 0
elif [[ "${1:-}" == '--gui-e2e' ]]; then
    java -Dstreamflix.e2e.output="${STREAMFLIX_E2E_OUTPUT:-$BUILD_DIR/gui-e2e}" \
        -cp "$BUILD_DIR/streamflix-desktop.jar${sep}$BUILD_DIR/test-classes" \
        dev.streamflix.desktop.WindowsNativeGuiE2E
    exit 0
elif [[ "${1:-}" == '--run' ]]; then
    shift
    exec java -jar "$BUILD_DIR/streamflix-desktop.jar" "$@"
elif [[ $# -gt 0 ]]; then
    echo 'Usage: bash build.sh [--run [application arguments]]' >&2
    exit 2
fi
