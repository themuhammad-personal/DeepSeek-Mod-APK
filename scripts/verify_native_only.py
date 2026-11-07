#!/usr/bin/env python3
"""Prove that the patched app stays a native Android app.

The rule of this project is that every feature is rebuilt with the native
toolkit of Android: no web view, no injected JavaScript, no HTML page that is
served to the app.

This script checks that:

  * the extension does not use the web kit of Android at all,
  * the extension does not contain a JavaScript or a HTML payload,
  * the patched APK does not add a web asset to the original APK.

Usage:
    python3 scripts/verify_native_only.py <patched.apk> [<original.apk>]
"""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

try:  # androguard logs through loguru, which ignores the stdlib logging switch.
    from loguru import logger as _loguru_logger

    _loguru_logger.remove()
except Exception:  # pragma: no cover - loguru is optional
    pass

EXTENSION_PACKAGE = "Lcom/themuhammad/deepseekmod/extension/"

# Types of the web kit of Android. The extension must not use them.
WEB_TYPES = (
    "Landroid/webkit/WebView;",
    "Landroid/webkit/WebViewClient;",
    "Landroid/webkit/WebChromeClient;",
    "Landroid/webkit/JavascriptInterface;",
)

# Payloads that would mean that code is injected into the app.
WEB_PAYLOADS = (
    "javascript:",
    "<script",
    "evaluateJavascript",
    "loadUrl(\"javascript",
    "addJavascriptInterface",
    "data:text/html",
    "text/html",
)

# Files that would mean that the app carries a web interface.
WEB_ASSETS = (".js", ".mjs", ".html", ".htm", ".css", ".wasm")

ASSET_DIRECTORIES = ("assets/", "res/raw/", "res/drawable-*/")


def extension_classes(apk: Path):
    """Every class of the extension with its strings and its references."""
    from androguard.core.dex import DEX

    result = []
    with zipfile.ZipFile(apk) as archive:
        for name in sorted(
            item for item in archive.namelist() if item.endswith(".dex") and not item.endswith("/")
        ):
            dex = DEX(archive.read(name))
            for cls in dex.get_classes():
                if not cls.get_name().startswith(EXTENSION_PACKAGE):
                    continue
                strings: list[str] = []
                references: list[str] = []
                for method in cls.get_methods():
                    try:
                        instructions = list(method.get_instructions())
                    except Exception:
                        continue
                    for instruction in instructions:
                        output = instruction.get_output()
                        if instruction.get_name().startswith("const-string"):
                            match = re.search(r'"((?:[^"\\]|\\.)*)"', output)
                            if match:
                                strings.append(match.group(1))
                        for reference in re.findall(r"L[A-Za-z0-9_$/]+;", output):
                            references.append(reference)
                result.append((cls.get_name(), strings, references))
    return result


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print(__doc__)
        return 2
    patched = Path(sys.argv[1])
    original = Path(sys.argv[2]) if len(sys.argv) > 2 else None
    for path in (patched, original):
        if path is not None and not path.exists():
            print(f"the APK {path} does not exist", file=sys.stderr)
            return 2

    failures = 0
    classes = extension_classes(patched)
    print(f"extension classes in the patched APK: {len(classes)}")
    if not classes:
        failures += 1
        print("FAILED: the extension is not in the patched APK")

    for name, strings, references in classes:
        for web_type in WEB_TYPES:
            if web_type in references:
                failures += 1
                print(f"FAILED: {name} uses {web_type}")
        for payload in WEB_PAYLOADS:
            for literal in strings:
                if payload in literal:
                    failures += 1
                    print(f"FAILED: {name} carries the payload {literal!r}")

    if failures == 0:
        print("  OK      no class of the extension uses the web kit of Android")
        print("  OK      no class of the extension carries a JavaScript or a HTML payload")

    if original is not None:
        with zipfile.ZipFile(patched) as archive:
            patched_names = set(archive.namelist())
        with zipfile.ZipFile(original) as archive:
            original_names = set(archive.namelist())
        added = sorted(patched_names - original_names)
        web_assets = [
            name
            for name in added
            if name.lower().endswith(WEB_ASSETS)
            or any(name.startswith(prefix) for prefix in ASSET_DIRECTORIES)
        ]
        print(f"files that the patches added to the APK: {len(added)}")
        if web_assets:
            failures += 1
            print("FAILED: the patched APK carries a web asset")
            for name in web_assets[:10]:
                print(f"          {name}")
        else:
            print("  OK      the patched APK does not carry a web asset")

    print()
    if failures:
        print(f"FAILED: {failures} problem(s)")
        return 1
    print("PASS: the patched app is a native Android app")
    return 0


if __name__ == "__main__":
    sys.exit(main())
