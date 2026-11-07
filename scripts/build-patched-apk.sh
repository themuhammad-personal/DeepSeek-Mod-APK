#!/usr/bin/env bash
# Build a patched DeepSeek 2.7.4 on this machine.
#
# The CI does the same thing, but it deletes the APK when the job ends and it
# never publishes it. This script does it on your own machine, so the result is
# a file you can install on a phone.
#
# Nothing of the official app is written into this repository: the pinned
# extraction is downloaded into the build directory, the patched APK is written
# into the build directory and the signing key is generated locally.
#
# Usage:
#   bash scripts/build-patched-apk.sh --mpp <bundle.mpp>
#   bash scripts/build-patched-apk.sh            # builds the bundle with gradle
#
# Options:
#   --mpp <file>          the Morphe bundle to apply. Without it the script
#                          builds the bundle with gradle, which needs access to
#                          the Morphe package registry.
#   --source-file <file>  the pinned extraction bundle that is already on this
#                          machine. Use it when the file lives in a Drive that
#                          is not shared with the internet.
#   --source-url <url>    the address of the pinned extraction bundle.
#   --source-sha256 <sh>  the checksum of that bundle.
#   --out <directory>     where the result is written. Default: build-local.
#   --proroot-dir <dir>   a folder with the five libproroot*.so files of the
#                          Linux Studio. They are added to the APK and the
#                          runtime only runs when every checksum matches.
#
# Requirements: a JDK 21, python3, curl, unzip and the Android SDK with the
# build tools 36.0.0 (for zipalign and apksigner).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/build-local"
MPP=""
SOURCE_FILE=""
SOURCE_URL="${SDS_SOURCE_URL:-https://drive.usercontent.google.com/download?id=1zKivuIhspDNuEpI74Q0QLtAP7ZtDfMM0&export=download&confirm=t}"
SOURCE_SHA256="${SDS_SOURCE_SHA256:-b0ec418ec9b8a273479acd8fed73acefbdbddd34162684c50e1737abb70d74de}"
OUT="$WORK"
PROROOT_DIR="${SDS_PROROOT_DIR:-}"

MORPHE_CLI_VERSION="2.1.1"
MORPHE_CLI_SHA256="3a2efefd567b44dde15971a58d5aa649a32aba30781d3400dc08d688c720168f"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mpp) MPP="$2"; shift 2 ;;
    --source-file) SOURCE_FILE="$2"; shift 2 ;;
    --source-url) SOURCE_URL="$2"; shift 2 ;;
    --source-sha256) SOURCE_SHA256="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --proroot-dir) PROROOT_DIR="$2"; shift 2 ;;
    -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

say() { printf '\n== %s\n' "$1"; }
die() { printf '\nERROR: %s\n' "$1" >&2; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || die "$1 is needed but was not found"; }
need python3
need java
need curl
need unzip

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BUILD_TOOLS="$SDK/build-tools/36.0.0"
[[ -x "$BUILD_TOOLS/zipalign" ]] || die "zipalign was not found in $BUILD_TOOLS (set ANDROID_HOME)"
[[ -x "$BUILD_TOOLS/apksigner" ]] || die "apksigner was not found in $BUILD_TOOLS (set ANDROID_HOME)"

mkdir -p "$OUT"
WORK="$(cd "$OUT" && pwd)"

# 1. The Morphe command line tool.
say "the Morphe command line tool"
if [[ ! -f "$WORK/morphe-desktop.jar" ]]; then
  curl --fail --location --retry 3 \
    "https://github.com/MorpheApp/morphe-desktop/releases/download/v${MORPHE_CLI_VERSION}/morphe-desktop-${MORPHE_CLI_VERSION}-all.jar" \
    --output "$WORK/morphe-desktop.jar"
fi
echo "$MORPHE_CLI_SHA256  $WORK/morphe-desktop.jar" | sha256sum --check --status \
  || die "the checksum of the Morphe tool does not match the pinned one"

# 2. The Morphe bundle with the patches.
say "the Morphe bundle"
if [[ -z "$MPP" ]]; then
  command -v gradle >/dev/null 2>&1 || die "gradle is needed to build the bundle, or pass --mpp <file>"
  ( cd "$ROOT" && gradle --no-daemon buildAndroid )
  MPP="$(find "$ROOT/patches/build/libs" -maxdepth 1 -name '*.mpp' -print -quit)"
  [[ -n "$MPP" ]] || die "the bundle was not built"
fi
[[ -f "$MPP" ]] || die "the bundle $MPP does not exist"

# 3. The pinned extraction of the official app.
say "the pinned extraction of the official app"
if [[ -n "$SOURCE_FILE" ]]; then
  [[ -f "$SOURCE_FILE" ]] || die "the file $SOURCE_FILE does not exist"
  cp -f "$SOURCE_FILE" "$WORK/pinned-extraction.zip"
fi
if [[ ! -f "$WORK/pinned-extraction.zip" ]]; then
  [[ "$SOURCE_URL" == https://* ]] || die "the source address must use https"
  curl --proto '=https' --tlsv1.2 --fail --location --retry 3 --max-time 900 \
    "$SOURCE_URL" --output "$WORK/pinned-extraction.zip"
fi
echo "$SOURCE_SHA256  $WORK/pinned-extraction.zip" | sha256sum --check \
  || die "the checksum of the extraction bundle does not match the pinned one"

# 4. The target APK.
say "the target APK"
python3 "$ROOT/scripts/rebuild-pinned-apk.py" \
  "$WORK/pinned-extraction.zip" "$WORK/deepseek-pinned-unaligned.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 \
  "$WORK/deepseek-pinned-unaligned.apk" "$WORK/deepseek-pinned.apk"

# 5. The patches.
say "the patches"
java -Xms1024m -Xmx4g -jar "$WORK/morphe-desktop.jar" \
  patch --patches "$MPP" --exclusive --validate --bytecode-mode FULL \
  --enable "DeepSeek 2.7.4 compatibility check" \
  --enable "Native Agent hook points (experimental)" \
  --enable "Super DeepSeek native Agent Console" \
  --enable "Super DeepSeek native Agent chat entry" \
  --result-file "$WORK/patch-result.json" \
  --out "$WORK/deepseek-patched-unsigned.apk" \
  "$WORK/deepseek-pinned.apk"

python3 - "$WORK/patch-result.json" <<'PYRESULT'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
applied = data.get("appliedPatches") or []
failed = data.get("failedPatches") or []
for name in failed:
    print("FAILED PATCH:", name)
    print((name.get("error") or "")[:2000])
if failed or data.get("success") is not True:
    sys.exit("the patches did not apply cleanly")
print("applied patches:")
for name in applied:
    print(" -", name)
PYRESULT

# 6. The optional runtime of the Linux Studio.
# The five files of the PRoot runtime are not redistributable and they are not
# part of this repository. Put them into a folder and point the script at it.
if [[ -n "$PROROOT_DIR" ]]; then
  say "the runtime of the Linux Studio"
  python3 "$ROOT/scripts/inject_native_libs.py" \
    "$WORK/deepseek-patched-unsigned.apk" "$PROROOT_DIR"
fi

# 7. The signature. The key is generated on this machine and never leaves it.
say "the signature"
KEYSTORE="$WORK/sds-local.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" \
    -alias sds -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass sds-local -keypass sds-local \
    -dname "CN=Super DeepSeek Local, OU=Local, O=Local, L=Local, S=Local, C=BD" \
    >/dev/null
  echo "a local signing key was created in $KEYSTORE"
  echo "keep it: without it you cannot update the app later"
fi
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:sds-local --key-pass pass:sds-local \
  --out "$WORK/deepseek-patched-signed.apk" \
  "$WORK/deepseek-patched-unsigned.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 \
  "$WORK/deepseek-patched-signed.apk" "$WORK/deepseek-patched.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$WORK/deepseek-patched.apk" | head -5

# 8. The proof of the hook call sites.
say "the proof of the hook call sites"
python3 -m pip install --quiet --disable-pip-version-check androguard
python3 "$ROOT/scripts/verify_patched_hooks.py" \
  "$WORK/deepseek-patched.apk" "$WORK/deepseek-pinned.apk"

say "done"
echo "the patched APK: $WORK/deepseek-patched.apk"
sha256sum "$WORK/deepseek-patched.apk"
stat -c 'size: %s bytes' "$WORK/deepseek-patched.apk"
cat <<'MESSAGE'

Install it on a phone that has no official DeepSeek, or remove the official
one first (that removes its data). The patched app has another signature, so
Android cannot update the official installation in place.

The patches are off by default inside the app. Open the chat, tap the "Agent"
pill in the corner and turn the switches on one by one.
MESSAGE
