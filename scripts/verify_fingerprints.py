#!/usr/bin/env python3
"""Verify that the Morphe fingerprints of this project resolve against a real build.

The Morphe patch bundle only proves that the patch code compiles. This harness
proves that every fingerprint that the patches use really resolves against the
DEX files of the build that is being patched, and that the instruction level
checks of the patches hold for that build.

The fingerprints are not copied into this script. They are read out of the
Kotlin sources, so the script can never drift away from the patch bundle.

For every fingerprint the harness checks:

  1. the class exists,
  2. the method exists with the exact descriptor,
  3. every filter of the fingerprint matches:
     - a string filter needs a const-string with that literal,
     - a field access filter needs an instruction with that field,
     - a method call filter needs an instruction with that call, and when the
       filter is marked as "immediately after" the call has to follow the
     previous filter directly,
  4. the instruction shape that the patches rely on holds:
     - the prompt field read is followed by the serializer call and both use
       the same register,
     - the streaming SET update passes exactly one argument,
     - the streaming APPEND update passes two arguments and the second one is
       the register that the StringBuilder produced.

When a second build is given, the harness also proves that the fingerprint set
of one build does not resolve against the other build, so a fingerprint can
never be used for a build it was not derived from.

Usage:
    python3 scripts/verify_fingerprints.py <apk|dex-dir> [<apk|dex-dir>]
    python3 scripts/verify_fingerprints.py <apk|dex-dir> --version-code 294
"""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path

logging_disabled = False

try:  # androguard logs through loguru, which ignores the stdlib logging switch.
    from loguru import logger as _loguru_logger

    _loguru_logger.remove()
except Exception:  # pragma: no cover - loguru is optional
    pass

REPO_ROOT = Path(__file__).resolve().parent.parent
PATCHES_DIR = (
    REPO_ROOT
    / "patches"
    / "src"
    / "main"
    / "kotlin"
    / "com"
    / "themuhammad"
    / "deepseekmod"
    / "patches"
)

# The fingerprint sources, one file per inspected build.
SOURCE_FILES = {
    "shared": "DeepSeekFingerprints.kt",
    "293": "DeepSeekFingerprints293.kt",
    "294": "DeepSeekFingerprints294.kt",
}

OBJECT_PATTERN = re.compile(r"internal object (\w+)\s*:\s*Fingerprint\s*\(")
CONST_STRING_PATTERN = re.compile(r'"((?:[^"\\]|\\.)*)"')


# ---------------------------------------------------------------------------
# Reading the Kotlin fingerprint definitions
# ---------------------------------------------------------------------------


def _split_top_level(text: str) -> list[str]:
    """Split a comma separated list, ignoring commas inside brackets."""
    parts: list[str] = []
    depth = 0
    current: list[str] = []
    for character in text:
        if character in "([{":
            depth += 1
        elif character in ")]}":
            depth -= 1
        if character == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(character)
    if current:
        parts.append("".join(current).strip())
    return [part for part in parts if part]


def _closing_parenthesis(text: str, start: int) -> int:
    depth = 0
    for index in range(start, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("unbalanced parentheses in the fingerprint source")


def _argument_map(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for argument in _split_top_level(text):
        key, separator, value = argument.partition("=")
        if separator:
            values[key.strip()] = value.strip()
    return values


def _string_list(value: str) -> list[str]:
    inner = value[value.index("(") + 1 : _closing_parenthesis(value, value.index("("))]
    return [
        match.group(1)
        for match in CONST_STRING_PATTERN.finditer(inner)
        if match
    ]


def _parse_filter(element: str) -> dict:
    element = element.strip()
    if element.startswith("string("):
        literal = CONST_STRING_PATTERN.search(element)
        return {"kind": "string", "value": literal.group(1) if literal else ""}
    if element.startswith("fieldAccess("):
        inner = element[element.index("(") + 1 : _closing_parenthesis(element, element.index("("))]
        values = _argument_map(inner)
        return {
            "kind": "fieldAccess",
            "definingClass": values.get("definingClass", "").strip('"'),
            "name": values.get("name", "").strip('"'),
            "type": values.get("type", "").strip('"'),
            "opcode": values.get("opcode", ""),
        }
    if element.startswith("methodCall("):
        inner = element[element.index("(") + 1 : _closing_parenthesis(element, element.index("("))]
        values = _argument_map(inner)
        return {
            "kind": "methodCall",
            "definingClass": values.get("definingClass", "").strip('"'),
            "name": values.get("name", "").strip('"'),
            "parameters": _string_list(values.get("parameters", "listOf()")),
            "returnType": values.get("returnType", "").strip('"'),
            "immediatelyAfter": "MatchAfterImmediately()" in inner,
        }
    raise ValueError(f"unsupported fingerprint filter: {element}")


def parse_fingerprints(path: Path) -> list[dict]:
    text = path.read_text(encoding="utf-8")
    fingerprints = []
    for match in OBJECT_PATTERN.finditer(text):
        name = match.group(1)
        open_index = match.end() - 1
        close_index = _closing_parenthesis(text, open_index)
        values = _argument_map(text[open_index + 1 : close_index])
        filters = []
        raw_filters = values.get("filters")
        if raw_filters:
            inner = raw_filters[
                raw_filters.index("(") + 1 : _closing_parenthesis(raw_filters, raw_filters.index("("))
            ]
            filters = [_parse_filter(element) for element in _split_top_level(inner)]
        fingerprints.append(
            {
                "name": name,
                "definingClass": values.get("definingClass", "").strip('"'),
                "method": values.get("name", "").strip('"'),
                "returnType": values.get("returnType", "").strip('"'),
                "parameters": _string_list(values.get("parameters", "listOf()")),
                "filters": filters,
            }
        )
    return fingerprints


def load_fingerprint_sets() -> dict[str, list[dict]]:
    sets: dict[str, list[dict]] = {}
    for key, name in SOURCE_FILES.items():
        path = PATCHES_DIR / name
        if not path.is_file():
            raise SystemExit(f"the fingerprint source {path} is missing")
        sets[key] = parse_fingerprints(path)
    return sets


# ---------------------------------------------------------------------------
# Reading the DEX files
# ---------------------------------------------------------------------------


def load_dex(target: Path):
    from androguard.core.dex import DEX

    if target.is_dir():
        dex_files = sorted(target.glob("*.dex"))
        if not dex_files:
            raise SystemExit(f"{target} contains no DEX file")
        return [(path.name, DEX(path.read_bytes())) for path in dex_files]
    with zipfile.ZipFile(target) as archive:
        dex_names = sorted(
            name for name in archive.namelist() if name.endswith(".dex") and not name.endswith("/")
        )
        if not dex_names:
            raise SystemExit(f"{target} contains no DEX file")
        return [(name, DEX(archive.read(name))) for name in dex_names]


def read_version_code(target: Path) -> str | None:
    """Read the version code of an APK, when the target is an APK."""
    if target.is_dir():
        return None
    try:
        from androguard.core.apk import APK
    except Exception:  # pragma: no cover - androguard is optional here
        return None
    apk = APK(str(target))
    return str(apk.get_androidversion_code())


def find_class(dexes, descriptor: str):
    for _, dex in dexes:
        klass = dex.get_class(descriptor)
        if klass is not None:
            return klass
    return None


def find_method(dexes, class_descriptor: str, name: str, descriptor: str):
    klass = find_class(dexes, class_descriptor)
    if klass is None:
        return None
    for method in klass.get_methods():
        if method.get_name() != name:
            continue
        if method.get_descriptor().replace(" ", "") == descriptor:
            return method
    return None


def instructions_of(method) -> list:
    code = method.get_code()
    if code is None:
        return []
    return list(code.get_bc().get_instructions())


def parse_instruction(instruction) -> tuple[list[str], str]:
    """Return the registers and the reference of an instruction.

    androguard renders a call as "v1, v2, Lfoo;->bar()V" and a field access as
    "v1, v2, Lfoo;->name Ltype;".
    """
    output = instruction.get_output().replace(" ", "")
    if "->" in output:
        head, tail = output.split("->", 1)
        parts = [item for item in head.split(",") if item]
        registers = [item for item in parts if item.startswith("v")]
        owner = parts[-1] if parts else ""
        return registers, f"{owner}->{tail}"
    registers = [item for item in output.split(",") if item.startswith("v")]
    return registers, output


def split_reference(reference: str) -> tuple[str, str]:
    owner, _, tail = reference.partition("->")
    return owner, tail


def field_of(reference: str) -> tuple[str, str] | None:
    owner, tail = split_reference(reference)
    if "(" in tail or not tail:
        return None
    if tail[0] == "L":
        return None
    match = re.match(r"([A-Za-z0-9_$]+)(L[^;]*;|[ZBCSIJFD])", tail)
    if not match:
        return None
    return owner, match.group(1)


def split_parameters(text: str) -> list[str]:
    """Split a descriptor list into the single descriptors.

    The descriptors of a method are separated by nothing in the DEX format, so
    "Lqv9;ILjava/lang/String;" is the parameter list "Lqv9;" and "I" and
    "Ljava/lang/String;".
    """
    parameters: list[str] = []
    index = 0
    while index < len(text):
        character = text[index]
        if character == "L":
            end = text.find(";", index)
            if end < 0:
                return parameters
            parameters.append(text[index : end + 1])
            index = end + 1
            continue
        if character == "[":
            start = index
            while index < len(text) and text[index] == "[":
                index += 1
            if index < len(text) and text[index] == "L":
                end = text.find(";", index)
                if end < 0:
                    return parameters
                index = end + 1
            elif index < len(text):
                index += 1
            parameters.append(text[start:index])
            continue
        if character in "ZBCSIJFDV":
            parameters.append(character)
            index += 1
            continue
        index += 1
    return parameters


def call_of(reference: str) -> tuple[str, str, list[str], str] | None:
    owner, tail = split_reference(reference)
    if "(" not in tail:
        return None
    name, _, rest = tail.partition("(")
    parameters, _, return_type = rest.partition(")")
    return owner, name, split_parameters(parameters), return_type


def const_string_of(instruction) -> str | None:
    if not instruction.get_name().startswith("const-string"):
        return None
    match = CONST_STRING_PATTERN.search(instruction.get_output())
    return match.group(1) if match else None


# ---------------------------------------------------------------------------
# The checks
# ---------------------------------------------------------------------------


def check_fingerprint(dexes, fingerprint: dict) -> list[str]:
    problems: list[str] = []
    descriptor = "(" + "".join(fingerprint["parameters"]) + ")" + fingerprint["returnType"]
    klass = find_class(dexes, fingerprint["definingClass"])
    if klass is None:
        return [f"the class {fingerprint['definingClass']} does not exist"]
    method = find_method(dexes, fingerprint["definingClass"], fingerprint["method"], descriptor)
    if method is None:
        return [
            f"the method {fingerprint['definingClass']}->{fingerprint['method']}{descriptor} "
            "does not exist"
        ]
    instructions = instructions_of(method)

    previous_index = -1
    for filter_ in fingerprint["filters"]:
        if filter_["kind"] == "string":
            if not any(const_string_of(instruction) == filter_["value"] for instruction in instructions):
                problems.append(f"the string \"{filter_['value']}\" is not in the method")
            continue
        if filter_["kind"] == "fieldAccess":
            found = -1
            for index, instruction in enumerate(instructions):
                _, reference = parse_instruction(instruction)
                field = field_of(reference)
                if field is None:
                    continue
                owner, name = field
                if owner != filter_["definingClass"] or name != filter_["name"]:
                    continue
                if filter_["type"] and filter_["type"] not in reference:
                    continue
                found = index
                break
            if found < 0:
                problems.append(
                    f"the field {filter_['definingClass']}->{filter_['name']} is not read in the method"
                )
                previous_index = -1
                continue
            previous_index = found
            continue
        if filter_["kind"] == "methodCall":
            found = -1
            if filter_["immediatelyAfter"]:
                # The call has to be the instruction right after the previous filter.
                if previous_index >= 0 and previous_index + 1 < len(instructions):
                    _, reference = parse_instruction(instructions[previous_index + 1])
                    call = call_of(reference)
                    if call is not None:
                        owner, name, parameters, return_type = call
                        if (
                            owner == filter_["definingClass"]
                            and name == filter_["name"]
                            and parameters == filter_["parameters"]
                            and (not filter_["returnType"] or return_type == filter_["returnType"])
                        ):
                            found = previous_index + 1
                if found < 0:
                    problems.append(
                        f"the call {filter_['definingClass']}->{filter_['name']} does not follow "
                        "the previous filter directly"
                    )
                    previous_index = -1
                    continue
                previous_index = found
                continue
            for index, instruction in enumerate(instructions):
                _, reference = parse_instruction(instruction)
                call = call_of(reference)
                if call is None:
                    continue
                owner, name, parameters, return_type = call
                if owner != filter_["definingClass"] or name != filter_["name"]:
                    continue
                if parameters != filter_["parameters"]:
                    continue
                if filter_["returnType"] and return_type != filter_["returnType"]:
                    continue
                found = index
                break
            if found < 0:
                problems.append(
                    f"the call {filter_['definingClass']}->{filter_['name']} is not in the method"
                )
                previous_index = -1
                continue
            if filter_["immediatelyAfter"]:
                if previous_index < 0 or found != previous_index + 1:
                    problems.append(
                        f"the call {filter_['definingClass']}->{filter_['name']} does not follow "
                        "the previous filter directly"
                    )
            previous_index = found

    problems.extend(check_instruction_shape(fingerprint, instructions))
    return problems


def check_instruction_shape(fingerprint: dict, instructions: list) -> list[str]:
    """Check the instruction shape that the Morphe patch relies on.

    The classes and the names are taken from the filters of the fingerprint, so
    the check follows the build that the fingerprint belongs to.
    """
    problems: list[str] = []
    name = fingerprint["name"]
    field_filter = next((item for item in fingerprint["filters"] if item["kind"] == "fieldAccess"), None)
    call_filter = next((item for item in fingerprint["filters"] if item["kind"] == "methodCall"), None)

    if "PromptSerializer" in name and field_filter and call_filter:
        field_index = -1
        value_register = None
        for index, instruction in enumerate(instructions):
            _, reference = parse_instruction(instruction)
            field = field_of(reference)
            if field is None:
                continue
            owner, field_name = field
            if owner != field_filter["definingClass"] or field_name != field_filter["name"]:
                continue
            registers, _ = parse_instruction(instruction)
            if len(registers) < 2:
                continue
            field_index = index
            value_register = registers[0]
            break
        if field_index < 0:
            problems.append("the prompt field read was not found")
            return problems
        if field_index + 1 >= len(instructions):
            problems.append("the prompt serializer call does not follow the prompt field read")
            return problems
        registers, reference = parse_instruction(instructions[field_index + 1])
        call = call_of(reference)
        if call is None or call[0] != call_filter["definingClass"] or call[1] != call_filter["name"]:
            problems.append("the prompt serializer call does not follow the prompt field read")
            return problems
        if not registers:
            problems.append("the prompt serializer call has no register")
            return problems
        if registers[-1] != value_register:
            problems.append(
                "the prompt serializer call does not use the register of the prompt field read"
            )

    if "ContentSet" in name and call_filter:
        update = find_content_update(instructions, call_filter, register_count=2)
        if update is None:
            problems.append("the content update with one argument was not found")

    if "ContentAppend" in name and call_filter:
        update = find_content_update(instructions, call_filter, register_count=3)
        if update is None:
            problems.append("the content update with two arguments was not found")
            return problems
        index, registers = update
        if len(registers) != 3:
            problems.append("the content update does not pass exactly two arguments")
            return problems
        content_register = registers[-1]
        builder_index = -1
        for position, instruction in enumerate(instructions):
            if position >= index:
                break
            _, reference = parse_instruction(instruction)
            call = call_of(reference)
            if call and call[0] == "Ljava/lang/StringBuilder;" and call[1] == "toString":
                builder_index = position
        if builder_index < 0:
            problems.append("the content is not built with a StringBuilder")
            return problems
        if builder_index + 1 >= len(instructions):
            problems.append("the built content is not moved into a register")
            return problems
        move = instructions[builder_index + 1]
        if not move.get_name().startswith("move-result-object"):
            problems.append("the built content is not moved into a register")
            return problems
        moved, _ = parse_instruction(move)
        if moved and moved[0] != content_register:
            problems.append("the content update does not use the register of the built content")

    return problems


def find_content_update(instructions: list, call_filter: dict, register_count: int):
    """Find the call that updates the content holder of the answer."""
    for index, instruction in enumerate(instructions):
        if not instruction.get_name().startswith("invoke"):
            continue
        registers, reference = parse_instruction(instruction)
        call = call_of(reference)
        if call is None:
            continue
        owner, name, parameters, _ = call
        if owner != call_filter["definingClass"] or name != call_filter["name"]:
            continue
        if parameters != call_filter["parameters"]:
            continue
        if len(registers) != register_count:
            continue
        return index, registers
    return None


# ---------------------------------------------------------------------------
# The program
# ---------------------------------------------------------------------------


def verify(target: Path, sets: dict[str, list[dict]], version_code: str | None) -> int:
    """Check every fingerprint set against the build.

    The set of the build that is being patched has to resolve completely. The
    sets of the other builds must not resolve at all, because a fingerprint of
    one build may never be used for another build.
    """
    dexes = load_dex(target)
    failures = 0
    print(f"target: {target}")
    print(f"dex files: {', '.join(name for name, _ in dexes)}")
    if version_code:
        print(f"version code of the target: {version_code}")
    print()

    expected_key = None
    if version_code:
        expected_key = version_code.strip()
        if expected_key not in sets:
            print(
                f"FAILED: there is no fingerprint set for the version code {expected_key}. "
                "The patches would refuse to patch this build."
            )
            return 1

    for key, fingerprints in sets.items():
        if not fingerprints:
            continue
        print(f"fingerprint set {key}:")
        resolved_any = False
        resolved_all = True
        for fingerprint in fingerprints:
            problems = check_fingerprint(dexes, fingerprint)
            descriptor = (
                "(" + "".join(fingerprint["parameters"]) + ")" + fingerprint["returnType"]
            )
            label = f"{fingerprint['definingClass']}->{fingerprint['method']}{descriptor}"
            if problems:
                resolved_all = False
                print(f"  absent  {fingerprint['name']:42s} {label}")
                for problem in problems:
                    print(f"          - {problem}")
            else:
                resolved_any = True
                print(f"  present {fingerprint['name']:42s} {label}")

        if key == "shared":
            if not resolved_all:
                failures += 1
                print("  FAILED  the fingerprint that every build shares did not resolve")
        elif expected_key is not None and key == expected_key:
            if not resolved_all:
                failures += 1
                print(f"  FAILED  the fingerprint set of the version code {expected_key} did not resolve")
        elif expected_key is not None:
            if resolved_any:
                failures += 1
                print(
                    f"  FAILED  a fingerprint of the set {key} resolves in a build with the "
                    f"version code {expected_key}"
                )
            else:
                print(f"  OK      none of the fingerprints of the set {key} exists in this build")
        else:
            if resolved_any and not resolved_all:
                failures += 1
                print(f"  FAILED  the fingerprint set {key} only resolves partially")
        print()

    return failures


def cross_check(first: Path, second: Path, sets: dict[str, list[dict]]) -> int:
    """Prove that the fingerprint set of one build does not resolve in the other."""
    print("cross check: a fingerprint set must not resolve against another build")
    first_dexes = load_dex(first)
    second_dexes = load_dex(second)
    failures = 0
    for key, fingerprints in sets.items():
        if not fingerprints:
            continue
        resolved = 0
        for fingerprint in fingerprints:
            descriptor = (
                "(" + "".join(fingerprint["parameters"]) + ")" + fingerprint["returnType"]
            )
            method = find_method(
                second_dexes, fingerprint["definingClass"], fingerprint["method"], descriptor
            )
            if method is not None:
                resolved += 1
                print(
                    f"  FAILED  the fingerprint {fingerprint['name']} of the set {key} "
                    "also exists in the other build"
                )
        if resolved == 0:
            print(f"  OK      none of the fingerprints of the set {key} exists in the other build")
        else:
            failures += 1
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path, help="an APK or a directory with DEX files")
    parser.add_argument(
        "other",
        nargs="?",
        type=Path,
        help="a second build, to prove that the fingerprint sets do not mix",
    )
    parser.add_argument(
        "--version-code",
        default=None,
        help="the version code of the target, when it cannot be read from the APK",
    )
    arguments = parser.parse_args()

    if not arguments.target.exists():
        print(f"the target {arguments.target} does not exist", file=sys.stderr)
        return 2

    sets = load_fingerprint_sets()
    version_code = arguments.version_code or read_version_code(arguments.target)

    failures = verify(arguments.target, sets, version_code)
    if arguments.other:
        failures += cross_check(arguments.target, arguments.other, sets)

    if failures:
        print(f"FAILED: {failures} problem(s)")
        return 1
    print("PASS: every fingerprint resolves and every instruction shape holds")
    return 0


if __name__ == "__main__":
    sys.exit(main())
