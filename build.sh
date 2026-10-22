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
mkdir -p lib
deps=()
while IFS='|' read -r relpath hash url; do
    [[ -z "$relpath" || "$relpath" == \#* ]] && continue
    url="${url%$'\r'}"
    target="lib/${relpath}"
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
[[ ! -L build ]] || { echo 'Refusing linked build directory' >&2; exit 1; }
rm -rf "$ROOT/build"
mkdir -p build/classes build/test-classes build/lib
cp "${deps[@]}" build/lib/
sep=:
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) sep=';'; export MSYS2_ARG_CONV_EXCL='*';; esac
dep_cp="$(IFS="$sep"; echo "${deps[*]}")"
sources=()
while IFS= read -r -d '' file; do sources+=("$file"); done < <(find src/main/java -name '*.java' -print0 | LC_ALL=C sort -z)
tests=()
while IFS= read -r -d '' file; do tests+=("$file"); done < <(find src/test/java -name '*.java' -print0 | LC_ALL=C sort -z)
java -version
javac --release 17 -encoding UTF-8 -cp "$dep_cp" -d build/classes "${sources[@]}"
flat_deps=()
for d in "${deps[@]}"; do flat_deps+=("lib/${d##*/}"); done
printf 'Manifest-Version: 1.0\nMain-Class: dev.streamflix.desktop.App\nImplementation-Version: %s\nClass-Path: %s\n\n' "$version" "${flat_deps[*]}" > build/MANIFEST.MF
jar --create --file build/streamflix-desktop.jar --date=2020-01-01T00:00:00Z --manifest build/MANIFEST.MF -C build/classes .
javac --release 17 -encoding UTF-8 -cp "build/classes${sep}${dep_cp}" -d build/test-classes "${tests[@]}"
for test in JsonTest ProviderFixtureTest TmdbFixtureTest TmdbTitleIndexTest M3uPlaylistTest M3uLiveProviderTest SportsResolverTest SportsDataTest SportsPlaybackTest SportsFavoritesTest ExtractorFixtureTest DependencySmokeTest UserDataTest MpvPlayerTest MainFrameSearchTest PlaybackFallbackTest PlaybackRecoveryTest SubtitleAggregatorTest PlaybackServerStatsTest UpdateServiceTest ImageDiskCacheTest DiagnosticsTest MpvBootstrapTest BrandAssetsTest VodSearchCoordinatorTest ProviderHealthStatsTest ProviderFallbackCoordinatorTest CatalogRamCacheTest NavigationStateTest; do
    java -cp "build/streamflix-desktop.jar${sep}build/test-classes" "dev.streamflix.desktop.$test"
done
echo "Built: $ROOT/build/streamflix-desktop.jar (keep sibling lib directory)"
if [[ "${1:-}" == '--run' ]]; then
    shift
    exec java -jar build/streamflix-desktop.jar "$@"
elif [[ $# -gt 0 ]]; then
    echo 'Usage: bash build.sh [--run [application arguments]]' >&2
    exit 2
fi
