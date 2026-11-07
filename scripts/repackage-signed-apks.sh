#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 4 ]]; then
  echo "usage: $0 <source.apks> <patched-base.apk> <output.apks> <work-dir>" >&2
  exit 2
fi
SOURCE_APKS="$(realpath "$1")"
PATCHED_BASE="$(realpath "$2")"
OUTPUT_APKS="$(realpath -m "$3")"
WORK="$(realpath -m "$4")"
: " ${ANDROID_HOME:?ANDROID_HOME is required}"
: " ${SDS_KEYSTORE:?SDS_KEYSTORE is required}"
: " ${SDS_KEY_ALIAS:?SDS_KEY_ALIAS is required}"
: " ${SDS_KEYSTORE_PASSWORD:?SDS_KEYSTORE_PASSWORD is required}"
: " ${SDS_KEY_PASSWORD:?SDS_KEY_PASSWORD is required}"
BT="$ANDROID_HOME/build-tools/36.0.0"
AAPT="$BT/aapt"; ZIPALIGN="$BT/zipalign"; APKSIGNER="$BT/apksigner"
for tool in "$AAPT" "$ZIPALIGN" "$APKSIGNER"; do
  [[ -x "$tool" ]] || { echo "missing Android tool: $tool" >&2; exit 1; }
done
rm -rf "$WORK"; mkdir -p "$WORK/original" "$WORK/signed"
python3 - "$SOURCE_APKS" "$WORK/original" <<'PY'
import pathlib, sys, zipfile
source, output = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
with zipfile.ZipFile(source) as z:
    files = [i for i in z.infolist() if not i.is_dir()]
    apk_files = [i for i in files if i.filename.lower().endswith(".apk")]
    if not any(i.filename == "base.apk" for i in apk_files) or len(apk_files) < 2:
        raise SystemExit("APKS archive must contain base.apk and configuration splits")
    for item in files:
        p = pathlib.PurePosixPath(item.filename)
        if p.is_absolute() or ".." in p.parts or len(p.parts) != 1:
            raise SystemExit(f"unsupported APKS member: {item.filename}")
        (output / item.filename).write_bytes(z.read(item))
    print("APKS members:", ", ".join(i.filename for i in apk_files))
PY
# Pin the three exact APK payloads supplied by the owner.
python3 - "$WORK/original" <<'PY'
import hashlib, pathlib, sys
root=pathlib.Path(sys.argv[1])
expected={
"base.apk":"6befd98c06155b2b4583f57cd76d7b721e342fed77025a94b653e03e18b5c8a8",
"split_config.arm64_v8a.apk":"4f9bbc031c799450cee4100687514e4b1dae8d082816f823840fdd2cd3344051",
"split_config.xhdpi.apk":"3b7ce2d70fe06334bd533c04ef6d1f97a1a0d741a17eef230985a241c9b5cb7a"}
if {p.name for p in root.glob("*.apk")} != set(expected):
    raise SystemExit("the uploaded build must contain exactly base, arm64_v8a and xhdpi APKs")
for name, wanted in expected.items():
    actual=hashlib.sha256((root/name).read_bytes()).hexdigest()
    print(name, actual)
    if actual != wanted: raise SystemExit(f"inner APK hash mismatch: {name}")
PY
for apk in "$WORK/original"/*.apk; do
  badging="$("$AAPT" dump badging "$apk")"
  grep -q "package: name='com.deepseek.chat' versionCode='296'" <<<"$badging" || {
    echo "wrong package/versionCode: $(basename "$apk")" >&2; exit 1;
  }
  if [[ "$(basename "$apk")" == "base.apk" ]]; then
    grep -q "versionName='2.7.5'" <<<"$badging" || { echo "wrong base versionName" >&2; exit 1; }
  fi
done
read_cert_digest() {
  local apk="$1"
  local report
  report="$("$APKSIGNER" verify --min-sdk-version 32 --print-certs "$apk" 2>&1)"
  printf '%s\n' "$report" > "$WORK/signed/$(basename "$apk").cert-report.txt"
  local digest
  digest="$(printf '%s\n' "$report" |
    sed -nE 's/.*certificate SHA-256 digest:[[:space:]]*([[:xdigit:]: ]+).*/\1/ip' |
    head -n 1 | tr -d ' :\r\n' | tr '[:upper:]' '[:lower:]')"
  [[ "$digest" =~ ^[0-9a-f]{64}$ ]] || {
    echo "could not parse SHA-256 signer certificate for $(basename "$apk")" >&2
    printf '%s\n' "$report" >&2
    return 1
  }
  printf '%s\n' "$digest"
}

sign_one() {
  local input="$1" name="$2"
  local unsigned="$WORK/signed/$name.unsigned.apk"
  local aligned="$WORK/signed/$name.aligned.apk"
  local output="$WORK/signed/$name"

  # Remove a prior JAR/v1 signature block (Morphe may have signed the patched
  # base already). The final API-32+ package uses APK Signature Scheme v2/v3.
  cp "$input" "$unsigned"
  zip -q -d "$unsigned" 'META-INF/MANIFEST.MF' 'META-INF/*.SF' \
    'META-INF/*.RSA' 'META-INF/*.DSA' 'META-INF/*.EC' >/dev/null 2>&1 || true

  "$ZIPALIGN" -f -p 4 "$unsigned" "$aligned"
  echo "Signing $name for minSdk 32" >&2
  "$APKSIGNER" sign --min-sdk-version 32 --v1-signing-enabled true \
    --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
    --ks "$SDS_KEYSTORE" --ks-key-alias "$SDS_KEY_ALIAS" \
    --ks-pass env:SDS_KEYSTORE_PASSWORD --key-pass env:SDS_KEY_PASSWORD \
    --out "$output" "$aligned"
  "$APKSIGNER" verify --min-sdk-version 32 --verbose "$output" >/dev/null
  read_cert_digest "$output"
}
sign_one "$PATCHED_BASE" base.apk > "$WORK/signed/base.cert"
for split in "$WORK/original"/split*.apk; do
  [[ -f "$split" ]] || continue
  sign_one "$split" "$(basename "$split")" > "$WORK/signed/$(basename "$split").cert"
done
CERT="$(cat "$WORK/signed/base.cert")"
[[ "$CERT" =~ ^[0-9a-f]{64}$ ]] || { echo "invalid base signer digest" >&2; exit 1; }
for signed in "$WORK/signed"/*.apk; do
  "$APKSIGNER" verify --min-sdk-version 32 --verbose "$signed" >/dev/null
  cert="$(read_cert_digest "$signed")"
  [[ "$cert" == "$CERT" ]] || { echo "signer mismatch: $(basename "$signed")" >&2; exit 1; }
  badging="$("$AAPT" dump badging "$signed")"
  grep -q "package: name='com.deepseek.chat' versionCode='296'" <<<"$badging"
  if [[ "$(basename "$signed")" == "base.apk" ]]; then
    grep -q "versionName='2.7.5'" <<<"$badging"
  fi
done
python3 - "$SOURCE_APKS" "$WORK/signed" "$OUTPUT_APKS" <<'PY'
import pathlib, sys, zipfile
source, signed, output = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), pathlib.Path(sys.argv[3])
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(source) as src, zipfile.ZipFile(output, "w", allowZip64=True) as dst:
    for item in src.infolist():
        if item.is_dir(): continue
        name=pathlib.PurePosixPath(item.filename).name
        candidate=signed/name
        data=candidate.read_bytes() if name.lower().endswith(".apk") and candidate.is_file() else src.read(item)
        info=zipfile.ZipInfo(item.filename, item.date_time)
        info.compress_type=zipfile.ZIP_STORED
        info.external_attr=item.external_attr
        dst.writestr(info,data)
with zipfile.ZipFile(output) as z:
    names=[n for n in z.namelist() if n.lower().endswith(".apk")]
    if len(names)!=3 or "base.apk" not in names: raise SystemExit(f"bad output APK set: {names}")
    print("Output APKS:", ", ".join(names))
PY
unzip -tq "$OUTPUT_APKS"
sha256sum "$OUTPUT_APKS" | tee "$OUTPUT_APKS.sha256"
echo "All three APKs are signed with certificate $CERT"
