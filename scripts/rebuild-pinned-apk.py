#!/usr/bin/env python3
"""Rebuild the pinned DeepSeek 2.7.4 APK from its pinned extraction.

The pinned official APK (com.deepseek.chat 2.7.4, versionCode 293,
SHA-256 a868bad448c87444f6c825b5ddbfa23756ad34178ab08503d107ff8e29b4f022) is not
redistributable and is not stored in this repository. The pinned extraction produced
from that exact binary is however sufficient to rebuild an APK whose payload bytes
are identical to the original:

  * classes.dex / classes2.dex / classes3.dex are copied byte for byte,
  * AndroidManifest.xml is the original binary manifest,
  * resources.arsc, res/, assets/, lib/, kotlin/, okhttp3/ and META-INF/ are copied
    byte for byte.

What cannot be reproduced is the ZIP container metadata of the original file
(entry order, timestamps, compression choices) and the APK Signature Scheme v2/v3
signing block, because a plain extraction does not retain them. The rebuilt APK is
therefore content equivalent to the pinned target, not byte identical to it, and it
is not signed with DeepSeek's certificate. Morphe signs its own output with a
generated key.

Usage:
    python3 scripts/rebuild-pinned-apk.py <extraction.zip> <output.apk>

The extraction zip must contain a single root directory with the extracted APK tree.
"""

from __future__ import annotations

import hashlib
import sys
import zipfile
from pathlib import Path

# SHA-256 of the payload entries of the pinned official APK. These are the values
# recorded by the pinned extraction and they are checked before anything is written.
PINNED_ENTRY_SHA256 = {
    "AndroidManifest.xml": "8d42bee09da9f71a48041524400415470501de8026883c43e670673ead34a6f3",
    "classes.dex": "22abff1cb40791dcca6919b33c8c2f7cc5989664f4ac12b8609dd3a122c25611",
    "classes2.dex": "44a73c2882f67d708f1f1a34a601e8af82c71f8c9d2cfe0b9c69f3f488e3981c",
    "classes3.dex": "13ce4064e36a6e49fa02309c665969cae7fe8d281b17c7c8fcc95cda42ee9783",
    "resources.arsc": "b3c85ccc31baeaf26515c3d65a1f86e2f96607018e13093f271a8d74d6f684a5",
}

# Entries that Android expects to be stored uncompressed so they can be mapped
# directly from the APK. Mirrors the doNotCompress list of the pinned apktool decode.
STORED_SUFFIXES = (".arsc", ".png", ".webp", ".m4a", ".dex", ".prof", ".profm")
STORED_NAMES = ("resources.arsc",)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def should_store(name: str) -> bool:
    if name in STORED_NAMES:
        return True
    if name.startswith("assets/dexopt/"):
        return True
    return name.endswith(STORED_SUFFIXES)


def resolve_root(names: list[str]) -> str:
    roots = {name.split("/", 1)[0] for name in names if "/" in name}
    if len(roots) != 1:
        raise SystemExit(f"expected exactly one root directory in the extraction, found {sorted(roots)}")
    root = roots.pop()
    return root + "/"


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2

    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    if not source.is_file():
        raise SystemExit(f"extraction archive not found: {source}")

    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        root = resolve_root(names)
        entries = [name for name in names if not name.endswith("/") and name.startswith(root)]

        print(f"extraction: {source}")
        print(f"root:       {root}")
        print(f"entries:    {len(entries)}")

        payload = {}
        for name in entries:
            payload[name[len(root):]] = archive.read(name)

    print("checking pinned payload hashes:")
    for name, expected in sorted(PINNED_ENTRY_SHA256.items()):
        if name not in payload:
            raise SystemExit(f"pinned entry missing from the extraction: {name}")
        actual = sha256(payload[name])
        status = "OK" if actual == expected else "MISMATCH"
        print(f"  {status:8} {name} {actual}")
        if actual != expected:
            raise SystemExit(f"pinned entry hash mismatch for {name}")

    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, "w", allowZip64=True) as out:
        for name in entries:
            relative = name[len(root):]
            info = zipfile.ZipInfo(relative, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED if should_store(relative) else zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            out.writestr(info, payload[relative])

    print(f"rebuilt:    {target}")
    print(f"size:       {target.stat().st_size} bytes")
    print(f"sha256:     {sha256(target.read_bytes())}")
    print(
        "note: this APK is content equivalent to the pinned target but is not byte identical\n"
        "      to it and is not signed with DeepSeek's certificate."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
