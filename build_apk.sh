#!/usr/bin/env bash
# build_apk.sh
# -----------------------------------------------------------------------------
# Standalone POSIX build script for Double Power Key (former VivoToMirPay).
#
# Usage:
#   ./build_apk.sh                # builds release APK signed with debug key by default
#   ./build_apk.sh debug          # builds debug APK
#   ./build_apk.sh release \
#       /path/to/keystore.jks STORE_PASS keyAlias KEY_PASS
#
# Requirements:
#   * JDK 17+ on PATH (java/javac)
#   * Android SDK with platforms;android-35 and build-tools;35.0.0
#     (set ANDROID_HOME)
#   * Either Gradle on PATH OR no Gradle — falls back to ./gradlew, which
#     downloads the right Gradle version on first run.
# -----------------------------------------------------------------------------

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo "== Double Power Key build script =="
echo "Repo root: $ROOT"

if [ -z "${ANDROID_HOME:-}" ]; then
  echo "ANDROID_HOME not set — gradlew will try to find Android SDK via local.properties."
fi

# Pick Gradle command: prefer system gradle, else use bundled wrapper.
if command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
elif [ -x "$ROOT/gradlew" ]; then
  GRADLE="$ROOT/gradlew"
else
  echo "Gradle not found on PATH and ./gradlew is missing." >&2
  exit 1
fi

echo "Using Gradle: $GRADLE"

BUILD_TYPE="${1:-release}"
case "$BUILD_TYPE" in
  debug|release) ;;
  *)
    echo "Unknown build type: $BUILD_TYPE (expected: debug|release)"; exit 1 ;;
esac

# Optional explicit keystore info: ./build_apk.sh release KEYSTORE STORE_PASS ALIAS KEY_PASS
if [ "$BUILD_TYPE" = "release" ] && [ "${2:-}" != "" ]; then
  export VVTM_KEYSTORE="$2"
  export VVTM_KEYSTORE_PASSWORD="$3"
  export VVTM_KEY_ALIAS="$4"
  export VVTM_KEY_PASSWORD="$5"
  echo "Using custom keystore: $VVTM_KEYSTORE (alias $VVTM_KEY_ALIAS)"
fi

TASK="assemble$(echo "$BUILD_TYPE" | sed 's/^./\U&/')"
echo "Running task: $TASK"

"$GRADLE" -p "$ROOT" "$TASK"

OUT_DIR="app/build/outputs/apk/$BUILD_TYPE"
APK="$OUT_DIR/app-$BUILD_TYPE.apk"
if [ -f "$APK" ]; then
  echo ""
  echo "Build OK. APK:"
  echo "  $ROOT/$APK"
else
  echo "APK not found at expected path: $ROOT/$APK" >&2
  exit 1
fi
