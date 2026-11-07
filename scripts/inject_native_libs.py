#!/usr/bin/env python3
"""Add the native libraries of the Linux Studio to a patched APK.

The Morphe patcher merges the DEX of an extension, but it does not carry
native libraries. The five files of the PRoot runtime therefore have to be put
into the APK after the patches were applied and before it is signed.

Every file that ends in `.so` is copied into `lib/arm64-v8a` of the APK, so the
runtime ends up in the native library directory of the app, which is the only
place a file may be executed from.

Usage:
    python3 scripts/inject_native_libs.py <apk> <folder> [<folder> ...]

The folders hold the `.so` files. Nothing is overwritten that is already in the
APK, so a second run does not change anything.
"""

from __future__ import annotations

import shutil
import sys
import zipfile
from pathlib import Path

ABI = "arm64-v8a"


def collect(folders: list[Path]) -> list[tuple[Path, str]]:
    found: list[tuple[Path, str]] = []
    for folder in folders:
        if not folder.is_dir():
            continue
        for item in sorted(folder.iterdir()):
            if item.is_file() and item.suffix == ".so":
                found.append((item, f"lib/{ABI}/{item.name}"))
    return found


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    apk = Path(sys.argv[1])
    if not apk.is_file():
        print(f"the APK {apk} does not exist")
        return 1
    libraries = collect([Path(folder) for folder in sys.argv[2:]])
    if not libraries:
        print("no .so file was found in the given folders")
        return 1

    temporary = apk.with_suffix(apk.suffix + ".libs")
    with zipfile.ZipFile(apk, "r") as source, zipfile.ZipFile(
            temporary, "w", zipfile.ZIP_DEFLATED) as target:
        existing = set(source.namelist())
        for item in source.infolist():
            data = source.read(item.filename)
            info = zipfile.ZipInfo(item.filename, date_time=item.date_time)
            info.compress_type = item.compress_type
            info.external_attr = item.external_attr
            target.writestr(info, data)
        for path, name in libraries:
            if name in existing:
                print(f"skipped, the APK already holds {name}")
                continue
            target.writestr(name, path.read_bytes())
            print(f"added {name} ({path.stat().st_size} bytes)")
    shutil.move(str(temporary), str(apk))
    print(f"the APK now holds {len(libraries)} native libraries")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
