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
#                          is not shared with the internet. An .apkm is also
#                          accepted: the base APK is taken out of it.
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
SOURCE_FORMAT="${SDS_SOURCE_FORMAT:-zip}"
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
    --source-format) SOURCE_FORMAT="$2"; shift 2 ;;
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

# 3. The pinned source of the official app. Preserve its extension so an
#    APKM is never mistaken for an extracted ZIP after it is copied to WORK.
say "the pinned source of the official app"
case "$SOURCE_FORMAT" in
  zip|apkm|apk) ;;
  *) die "source format must be zip, apkm or apk" ;;
esac
PINNED_SOURCE="$WORK/pinned-source.$SOURCE_FORMAT"
if [[ -n "$SOURCE_FILE" ]]; then
  [[ -f "$SOURCE_FILE" ]] || die "the file $SOURCE_FILE does not exist"
  case "${SOURCE_FILE##*.}" in
    zip|apkm|apk) PINNED_SOURCE="$WORK/pinned-source.${SOURCE_FILE##*.}" ;;
    ZIP|APKM|APK) PINNED_SOURCE="$WORK/pinned-source.${SOURCE_FILE##*.}" ;;
    *) die "source file must be a .zip, .apkm or .apk file" ;;
  esac
  cp -f "$SOURCE_FILE" "$PINNED_SOURCE"
else
  [[ "$SOURCE_URL" == https://* ]] || die "the source address must use https"
  curl --proto '=https' --tlsv1.2 --fail --location --retry 3 --max-time 900 \
    "$SOURCE_URL" --output "$PINNED_SOURCE"
fi
echo "$SOURCE_SHA256  $PINNED_SOURCE" | sha256sum --check \
  || die "the source checksum does not match the pinned SHA-256"

# 4. Prepare the APK that corresponds to the source format.
say "the target APK"
if [[ "$PINNED_SOURCE" == *.apkm || "$PINNED_SOURCE" == *.APKM ]]; then
  # Split packages contain a base APK plus optional configuration splits. The
  # inspected Morphe hooks live in base.apk; the output here is a patched base APK.
  unzip -o -q "$PINNED_SOURCE" base.apk -d "$WORK"
  [[ -s "$WORK/base.apk" ]] || die "the APKM does not contain base.apk"
  cp -f "$WORK/base.apk" "$WORK/deepseek-pinned.apk"
  echo "the base APK of the APKM is the target"
elif [[ "$PINNED_SOURCE" == *.apk || "$PINNED_SOURCE" == *.APK ]]; then
  cp -f "$PINNED_SOURCE" "$WORK/deepseek-pinned.apk"
  echo "the supplied APK is the target"
else
  python3 "$ROOT/scripts/rebuild-pinned-apk.py" \
    "$PINNED_SOURCE" "$WORK/deepseek-pinned-unaligned.apk"
  "$BUILD_TOOLS/zipalign" -f -p 4 \
    "$WORK/deepseek-pinned-unaligned.apk" "$WORK/deepseek-pinned.apk"
fi

# 4a. The fingerprints of this build have to resolve before anything is
#     patched. The script reads the version code of the APK, so it checks the
#     set of this build and proves that the set of the other build is absent.
say "the fingerprints of the target"
python3 -m pip install --quiet --disable-pip-version-check androguard
python3 "$ROOT/scripts/verify_fingerprints.py" "$WORK/deepseek-pinned.apk"

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

# 7. Sign the patched APK. CI may provide a persistent keystore through
#    environment variables; otherwise a one-run local key is generated.
say "the signature"
KEYSTORE="$WORK/sds-local.keystore"
KEY_ALIAS="${SDS_KEY_ALIAS:-sds}"
KEYSTORE_PASSWORD="${SDS_KEYSTORE_PASSWORD:-sds-local}"
KEY_PASSWORD="${SDS_KEY_PASSWORD:-sds-local}"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" \
    -dname "CN=Super DeepSeek Local, OU=Local, O=Local, L=Local, S=BD, C=BD" \
    >/dev/null
  echo "a new signing key was generated at $KEYSTORE"
  echo "keep this key to install future builds as updates"
fi
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:$KEY_PASSWORD" \
  --out "$WORK/deepseek-patched-signed.apk" \
  "$WORK/deepseek-patched-unsigned.apk"
"$BUILD_TOOLS/zipalign" -f -p 4 \
  "$WORK/deepseek-patched-signed.apk" "$WORK/deepseek-patched.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$WORK/deepseek-patched.apk" | head -5

# 8. The proof of the hook call sites.
say "the proof of the hook call sites"
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
