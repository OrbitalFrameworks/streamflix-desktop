#!/usr/bin/env bash
# Captures the running Swing executable inside an isolated Xvfb display.
# It deliberately does not read TMDb credentials or write user preferences.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DISPLAY_ID="${STREAMFLIX_XVFB_DISPLAY:-:99}"
OUTPUT_DIR="${1:-$ROOT/docs/screenshots/runtime}"
JAR_PATH="${STREAMFLIX_JAR:-$ROOT/build/streamflix-desktop.jar}"
SCREEN_SIZE="${STREAMFLIX_CAPTURE_SIZE:-1280x800x24}"

if [[ -n "${STREAMFLIX_JDK:-}" ]]; then
    export PATH="$STREAMFLIX_JDK/bin:$STREAMFLIX_JDK:$PATH"
elif [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
else
    for candidate in /usr/lib/jvm/* /opt/jdk* /opt/java* /tmp/jdk*; do
        if [[ -x "$candidate/bin/java" ]]; then
            export PATH="$candidate/bin:$PATH"
            break
        fi
    done
fi

for tool in Xvfb ffmpeg java; do
    command -v "$tool" >/dev/null || {
        echo "Missing required tool: $tool" >&2
        exit 1
    }
done
[[ -f "$JAR_PATH" ]] || {
    echo "Build the JAR first (bash build.sh); missing: $JAR_PATH" >&2
    exit 1
}

mkdir -p "$OUTPUT_DIR"
Xvfb "$DISPLAY_ID" -screen 0 "$SCREEN_SIZE" -nolisten tcp >/tmp/streamflix-xvfb.log 2>&1 &
XVFB_PID=$!
DISPLAY="$DISPLAY_ID" java -Djava.awt.headless=false -jar "$JAR_PATH" >/tmp/streamflix-capture.log 2>&1 &
APP_PID=$!
cleanup() {
    kill "$APP_PID" "$XVFB_PID" 2>/dev/null || true
    wait "$APP_PID" "$XVFB_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Keep this delay explicit and short: it captures the actual startup state,
# including the honest empty-cache/loading state when no local catalog exists.
sleep 4
ffmpeg -hide_banner -loglevel error -y -f x11grab -video_size "${SCREEN_SIZE%x*}" \
    -i "${DISPLAY_ID}.0" -frames:v 1 "$OUTPUT_DIR/01-runtime-startup.png"
echo "Captured $OUTPUT_DIR/01-runtime-startup.png"
