#!/usr/bin/env python3
"""Prove that the patches do not touch the login of the official app.

The patches of this project hook the prompt of a request and the streaming
path of the answer. Nothing else of the app may change, because a change in
the login, in the session or in the anti-fraud code would break the official
sign in.

This script therefore compares the patched APK with the unpatched APK method
by method and only accepts the changes that the patches are supposed to make:

  * the method that serializes the prompt of a request,
  * the streaming method that replaces the content of an answer,
  * the streaming method that appends to it,
  * the onCreate of the launcher Activity, where the console and the chat
    entry are attached.

Every other method has to be identical, and every class that is new has to
belong to the extension of this project.

Usage:
    python3 scripts/verify_login_untouched.py <patched.apk> <original.apk>
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

# The patcher merges the Kotlin runtime into the app, because the patch bundle
# is written in Kotlin. Those classes are the standard library of Kotlin and
# the annotations of the Kotlin compiler. They are not a change of the app.
KOTLIN_RUNTIME = (
    "Lkotlin/",
    "Lkotlinx/",
    "Lorg/intellij/lang/annotations/",
    "Lorg/jetbrains/annotations/",
)

# The methods that the patches are allowed to change. The names are the ones
# of the build that is being patched, so the script reads the version code of
# the APK and picks the matching set.
PROFILES = {
    "293": {
        "prompt": ("Ljy1;", "e", "(Lw94;Ljava/lang/Object;)V"),
        "set": ("Lv44;", "l", "(Ljava/lang/String;Lc26;Lgv1;)V"),
        "append": ("Lv44;", "m", "(Lc26;Ljava/lang/String;)V"),
    },
    "294": {
        "prompt": ("La02;", "b", "(Lwb4;Ljava/lang/Object;)V"),
        "set": ("Lv64;", "l", "(Ljava/lang/String;Ls46;Lxw1;)V"),
        "append": ("Lv64;", "m", "(Ls46;Ljava/lang/String;)V"),
    },
    "295": {
        "prompt": ("Loy1;", "e", "(Laa4;Ljava/lang/Object;)V"),
        "set": ("Ly44;", "l", "(Ljava/lang/String;Lh26;Liv1;)V"),
        "append": ("Ly44;", "m", "(Lh26;Ljava/lang/String;)V"),
    },
    "296": {
        "prompt": ("Lf02;", "b", "(Lac4;Ljava/lang/Object;)V"),
        "set": ("Ly64;", "l", "(Ljava/lang/String;Lx46;Lzw1;)V"),
        "append": ("Ly64;", "m", "(Lx46;Ljava/lang/String;)V"),
    },
}

MAIN_ACTIVITY_ON_CREATE = ("Lcom/deepseek/chat/MainActivity;", "onCreate", "(Landroid/os/Bundle;)V")


def load_methods(apk: Path) -> dict[tuple[str, str, str], list[str]]:
    """Every method of the APK, with the instructions it contains."""
    from androguard.core.dex import DEX

    with zipfile.ZipFile(apk) as archive:
        dex_names = sorted(
            name for name in archive.namelist() if name.endswith(".dex") and not name.endswith("/")
        )
        if not dex_names:
            raise SystemExit(f"{apk} contains no DEX file")
        methods: dict[tuple[str, str, str], list[str]] = {}
        for name in dex_names:
            dex = DEX(archive.read(name))
            for cls in dex.get_classes():
                for member in cls.get_methods():
                    try:
                        instructions = list(member.get_instructions())
                    except Exception:
                        continue
                    key = (
                        cls.get_name(),
                        member.get_name(),
                        member.get_descriptor().replace(" ", ""),
                    )
                    methods[key] = [
                        f"{instruction.get_name()} {instruction.get_output()}" for instruction in instructions
                    ]
        return methods


def version_code_of(apk: Path) -> str:
    from androguard.core.apk import APK

    return str(APK(str(apk)).get_androidversion_code())


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    patched = Path(sys.argv[1])
    original = Path(sys.argv[2])
    for path in (patched, original):
        if not path.exists():
            print(f"the APK {path} does not exist", file=sys.stderr)
            return 2

    version_code = version_code_of(patched)
    profile = PROFILES.get(version_code)
    if profile is None:
        print(
            f"the version code {version_code} of the patched APK is not inspected. "
            f"The inspected builds are: {', '.join(sorted(PROFILES))}."
        )
        return 1

    print(f"patched APK: {patched}")
    print(f"original APK: {original}")
    print(f"version code: {version_code}")
    print()

    patched_methods = load_methods(patched)
    original_methods = load_methods(original)
    print(f"methods in the patched APK: {len(patched_methods)}")
    print(f"methods in the original APK: {len(original_methods)}")
    print()

    allowed = {
        profile["prompt"],
        profile["set"],
        profile["append"],
        MAIN_ACTIVITY_ON_CREATE,
    }

    failures = 0

    # 1. Every method of the original app has to still be there.
    missing = sorted(set(original_methods) - set(patched_methods))
    if missing:
        failures += 1
        print(f"FAILED: {len(missing)} methods of the original app are missing in the patched APK")
        for item in missing[:10]:
            print(f"          {item[0]}->{item[1]}{item[2]}")

    # 2. Only the methods that the patches hook may differ.
    changed = []
    for key in sorted(set(original_methods) & set(patched_methods)):
        if original_methods[key] != patched_methods[key]:
            changed.append(key)
    unexpected = [key for key in changed if key not in allowed]
    print(f"methods that the patches changed: {len(changed)}")
    for key in changed:
        marker = "OK     " if key in allowed else "FAILED "
        print(f"  {marker}{key[0]}->{key[1]}{key[2]}")
    if unexpected:
        failures += 1
        print(f"FAILED: {len(unexpected)} methods changed that no patch is allowed to change")
        for key in unexpected[:10]:
            print(f"          {key[0]}->{key[1]}{key[2]}")

    # 2b. The methods that the patches hook have to have changed, otherwise
    #     the proof above would prove nothing.
    not_hooked = sorted(allowed - set(changed))
    if not_hooked:
        failures += 1
        print("FAILED: the patches did not change the methods that they hook")
        for key in not_hooked:
            print(f"          {key[0]}->{key[1]}{key[2]}")

    # 3. Every new class has to belong to the extension of this project or to
    #    the Kotlin runtime, which the patcher merges into the app.
    original_classes = {key[0] for key in original_methods}
    new_classes = sorted({key[0] for key in patched_methods} - original_classes)
    foreign = [
        name
        for name in new_classes
        if not name.startswith(EXTENSION_PACKAGE)
        and not name.startswith(KOTLIN_RUNTIME)
    ]
    runtime = [name for name in new_classes if name.startswith(KOTLIN_RUNTIME)]
    print(f"classes that were added: {len(new_classes)}")
    print(f"  of those {len(runtime)} classes of the Kotlin runtime")
    prefixes: dict[str, int] = {}
    for name in new_classes:
        parts = name.split("/")
        prefix = "/".join(parts[:3]) if len(parts) > 2 else name
        prefixes[prefix] = prefixes.get(prefix, 0) + 1
    print("  the added classes by package: " + ", ".join(
        f"{prefix} ({count})" for prefix, count in sorted(prefixes.items())
    ))
    if foreign:
        failures += 1
        print("FAILED: classes that do not belong to the extension were added")
        for name in foreign[:10]:
            print(f"          {name}")
    else:
        print("  OK      every added class belongs to the extension or to the Kotlin runtime")

    # 4. The extension must not change the classes of the login.
    login_words = ("login", "signin", "auth", "token", "captcha", "session")
    touched_login = []
    for key in changed:
        for word in login_words:
            if word in key[0].lower() or word in key[1].lower():
                touched_login.append(key)
                break
    if touched_login:
        failures += 1
        print("FAILED: a method of the login or of the session was changed")
        for key in touched_login[:10]:
            print(f"          {key[0]}->{key[1]}{key[2]}")
    else:
        print("  OK      no method of the login, of the session or of the anti-fraud code was changed")

    print()
    if failures:
        print(f"FAILED: {failures} problem(s)")
        return 1
    print("PASS: only the hooked methods changed, the login is untouched")
    return 0


if __name__ == "__main__":
    sys.exit(main())
