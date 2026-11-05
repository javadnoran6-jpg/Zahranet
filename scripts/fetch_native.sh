#!/usr/bin/env bash
# Fetches the two REAL native components into app/src/main/jniLibs/arm64-v8a:
#   libaether.so              <- official Aether release (CluvexStudio/Aether), checksum-verified when a .sha256 is published
#   libhev-socks5-tunnel.so   <- built from heiher/hev-socks5-tunnel with the Android NDK
# Needs: gh (GitHub CLI, authenticated or GH_TOKEN set), git, unzip/tar, file, sha256sum, Android NDK ($ANDROID_NDK_HOME).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/src/main/jniLibs/arm64-v8a
W=work
rm -rf "$W"; mkdir -p "$OUT" "$W/aether/x"

echo "== Aether core =="
ASSETS=$(gh release view -R CluvexStudio/Aether --json assets -q '.assets[].name')
PICK=$(echo "$ASSETS" | grep -iE 'android.*(arm64|aarch64)|(arm64|aarch64).*android' | grep -viE '\.sha256|\.sig|\.asc' | head -1 || true)
[ -n "$PICK" ] || PICK=$(echo "$ASSETS" | grep -iE 'linux.*(arm64|aarch64)|(arm64|aarch64).*linux' | grep -viE '\.sha256|\.sig|\.asc' | head -1 || true)
if [ -z "$PICK" ]; then
  echo "No arm64 Android/Linux asset found in the latest Aether release. Assets are:" >&2
  echo "$ASSETS" >&2
  echo "Download the right one manually and place it as $OUT/libaether.so" >&2
  exit 1
fi
echo "Using asset: $PICK"
gh release download -R CluvexStudio/Aether --pattern "$PICK*" --dir "$W/aether" --clobber
if [ -f "$W/aether/$PICK.sha256" ]; then
  EXP=$(awk '{print $1}' "$W/aether/$PICK.sha256" | head -1)
  ACT=$(sha256sum "$W/aether/$PICK" | awk '{print $1}')
  [ "$EXP" = "$ACT" ] || { echo "SHA-256 mismatch for $PICK" >&2; exit 1; }
  echo "SHA-256 verified"
else
  echo "WARNING: no .sha256 published for $PICK; checksum not verified" >&2
fi
case "$PICK" in
  *.zip)           unzip -qo "$W/aether/$PICK" -d "$W/aether/x" ;;
  *.tar.gz|*.tgz)  tar -xzf "$W/aether/$PICK" -C "$W/aether/x" ;;
  *)               cp "$W/aether/$PICK" "$W/aether/x/aether" ;;
esac
BIN=$(find "$W/aether/x" -type f \( -name aether -o -name 'aether-*' -o -name 'aether_*' \) ! -name '*.sha256' | head -1)
[ -n "$BIN" ] || { echo "aether binary not found inside $PICK" >&2; exit 1; }
if command -v file >/dev/null; then
  file "$BIN" | grep -qiE 'aarch64|ARM aarch64' || { echo "Binary is not arm64: $(file "$BIN")" >&2; exit 1; }
fi
cp "$BIN" "$OUT/libaether.so"
# Pluggable-transport helpers (pt/ folder) -> libpt_<name>.so ; the app links them back to filesDir/pt/<name>
find "$W/aether/x" -type f -path '*/pt/*' | while read -r f; do cp "$f" "$OUT/libpt_$(basename "$f").so"; done

echo "== hev-socks5-tunnel =="
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
[ -n "$NDK" ] && [ -x "$NDK/ndk-build" ] || { echo "Set ANDROID_NDK_HOME to an installed NDK" >&2; exit 1; }
mkdir -p "$W/hev"
git clone --recursive --depth 1 ${HEV_REF:+--branch "$HEV_REF"} https://github.com/heiher/hev-socks5-tunnel "$W/hev/jni"
( cd "$W/hev" && "$NDK/ndk-build" NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=jni/Android.mk APP_ABI=arm64-v8a APP_PLATFORM=android-26 )
cp "$W/hev/libs/arm64-v8a/libhev-socks5-tunnel.so" "$OUT/"
echo "OK:"; ls -la "$OUT"
