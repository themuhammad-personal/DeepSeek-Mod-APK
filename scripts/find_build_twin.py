#!/usr/bin/env python3
"""Find the twin of a known seam in another build of the official app.

Every release of the app is obfuscated on its own, so the class names of the
chat path change with every build. The *shape* of the code does not: the
streaming path of a build is the method with the same instruction sequence as
the streaming path of the build that was inspected before, only the names
changed.

This script therefore stores the shape of the three known seams of the build
with the version code 296 and looks for the methods of another build that have
the same shape. The result is the twin of the seam in that build, and the twin
is what the fingerprint of that build has to name.

The script never guesses: it only reports a twin when the match is unique and
the similarity is high enough.

Usage:
    python3 scripts/find_build_twin.py <apk|dex-dir> [--json]
"""

from __future__ import annotations

import argparse
import difflib
import json
import re
import sys
import zipfile
from pathlib import Path

try:  # androguard logs through loguru, which ignores the stdlib logging switch.
    from loguru import logger as _loguru_logger

    _loguru_logger.remove()
except Exception:  # pragma: no cover - loguru is optional
    pass

REPO_ROOT = Path(__file__).resolve().parent.parent
SIGNATURE_FILE = REPO_ROOT / "scripts" / "reference_seams.json"

# The seams of the build with the version code 296. They are the reference of
# the search, because that build was inspected completely and its instruction
# sequences are stored in the fingerprint source of the project.
REFERENCE_SEAMS = {
    "promptSerializer": ("Lf02;", "b", "(Lac4;Ljava/lang/Object;)V"),
    "responseContentSet": ("Ly64;", "l", "(Ljava/lang/String;Lx46;Lzw1;)V"),
    "responseContentAppend": ("Ly64;", "m", "(Lx46;Ljava/lang/String;)V"),
}

CONST_STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
IGET = re.compile(r"iget(?P<kind>[-/]\w+)?\s+[^,]+,\s*(?P<reg>[vp]\d+),\s*(?P<owner>L[^;]+;)->(?P<name>\w+)\s+(?P<type>L[^;]+;|[ZBCSIJFD])")
INVOKE = re.compile(r"invoke-(?P<kind>\w+)\s+[^,]+(?:,\s*[^,]+)*?,\s*(?P<callee>L[^;]+;)->(?P<name>\w+)\((?P<parameters>[^)]*)\)(?P<return>[\w;\[/]+)")
# The ratio that a twin has to reach. The app code of two releases is nearly
# the same, so a twin is normally far above this value.
MINIMUM_RATIO = 0.80


def field_kind(field_type: str) -> str:
    """The kind of a field type. The classes of the app change with every
    release, the platform types do not."""
    if field_type == "Ljava/lang/String;":
        return "string"
    if field_type == "Ljava/util/List;":
        return "list"
    if field_type == "Ljava/lang/Integer;":
        return "integer"
    if field_type.startswith("L"):
        return "object"
    return field_type


def token_of(instruction) -> str | None:
    """The token of an instruction, with the names of the app left out."""
    name = instruction.get_name()
    output = instruction.get_output()
    if name.startswith("const-string"):
        match = CONST_STRING.search(output)
        return "const-string:" + (match.group(1) if match else "")
    if name.startswith("invoke"):
        # The name of the method and the number of its arguments are stable
        # over the releases, the classes and the descriptors are not.
        hit = INVOKE.search(f"{name} {output}")
        if hit is None:
            return name
        parameters = hit.group("parameters").replace(" ", "")
        count = len([item for item in parameters.split("L") if item]) if parameters else 0
        return f"call:{hit.group('name')}/{count}"
    if name.startswith("iget") or name.startswith("iput"):
        # Only the type of the field is stable, its name and its class are not.
        hit = IGET.search(f"{name} {output}")
        if hit is None:
            return name
        return f"field:{field_kind(hit.group('type'))}"
    return name


def load_methods(target: Path):
    from androguard.core.dex import DEX

    payloads: list[bytes] = []
    if target.is_dir():
        payloads = [path.read_bytes() for path in sorted(target.glob("*.dex"))]
    else:
        with zipfile.ZipFile(target) as archive:
            payloads = [
                archive.read(name)
                for name in sorted(
                    item for item in archive.namelist() if item.endswith(".dex") and not item.endswith("/")
                )
            ]
    if not payloads:
        raise SystemExit(f"{target} contains no DEX file")
    methods = []
    for payload in payloads:
        dex = DEX(payload)
        for cls in dex.get_classes():
            for member in cls.get_methods():
                try:
                    instructions = list(member.get_instructions())
                except Exception:
                    continue
                tokens = [token for token in (token_of(item) for item in instructions) if token]
                if not tokens:
                    continue
                methods.append(
                    {
                        "class": cls.get_name(),
                        "name": member.get_name(),
                        "descriptor": member.get_descriptor().replace(" ", ""),
                        "tokens": tokens,
                        "instructions": [(item.get_name(), item.get_output()) for item in instructions],
                    }
                )
    return methods


def find_twin(methods, reference_tokens: list[str]) -> tuple[dict, float] | None:
    """The method of the build that has the same shape as the reference."""
    best = None
    best_ratio = 0.0
    second_ratio = 0.0
    for method in methods:
        tokens = method["tokens"]
        if abs(len(tokens) - len(reference_tokens)) > len(reference_tokens) // 3:
            continue
        matcher = difflib.SequenceMatcher(a=reference_tokens, b=tokens, autojunk=False)
        ratio = matcher.quick_ratio()
        if ratio < MINIMUM_RATIO:
            continue
        ratio = matcher.ratio()
        if ratio > best_ratio:
            second_ratio = best_ratio
            best_ratio = ratio
            best = method
        elif ratio > second_ratio:
            second_ratio = ratio
    if best is None:
        return None
    if second_ratio > best_ratio - 0.01:
        return None
    return best, best_ratio


def write_signature(target: Path) -> int:
    """Write the shape of the seams of the reference build into the file."""
    methods = load_methods(target)
    index = {
        (method["class"], method["name"], method["descriptor"]): method
        for method in methods
    }
    signatures = {}
    for key, (class_name, name, descriptor) in REFERENCE_SEAMS.items():
        method = index.get((class_name, name, descriptor))
        if method is None:
            print(f"the reference seam {class_name}->{name}{descriptor} is not in {target}")
            return 1
        signatures[key] = {
            "reference": f"{class_name}->{name}{descriptor}",
            "tokens": method["tokens"],
        }
    SIGNATURE_FILE.write_text(json.dumps(signatures, indent=1), encoding="utf-8")
    print(f"the shape of {len(signatures)} seams was written to {SIGNATURE_FILE}")
    return 0


def split_parameters(text: str) -> list[str]:
    """Split a descriptor list into the single descriptors."""
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
        if character in "ZBCSIJFDV":
            parameters.append(character)
            index += 1
            continue
        index += 1
    return parameters


def class_shapes(methods: list[dict]) -> dict[str, set[str]]:
    """The members of every class, so that the holder can be recognised."""
    shapes: dict[str, set[str]] = {}
    for method in methods:
        shapes.setdefault(method["class"], set()).add(method["name"] + method["descriptor"])
    return shapes


def is_holder(class_name: str, shapes: dict[str, set[str]]) -> bool:
    """A holder of the streamed content has a setter and an update method."""
    members = shapes.get(class_name, set())
    has_setter = any(member.endswith("(Ljava/lang/Object;)V") for member in members)
    has_update = any(
        member.endswith("(Ljava/lang/Object;Ljava/lang/Object;)Z") for member in members
    )
    return has_setter and has_update


def describe(method: dict, shapes: dict[str, set[str]], holder: str | None = None) -> dict:
    """The names that the fingerprint of the twin has to name.

    The content holder is the type of the field that the method reads, the
    method that replaces the content is the call on that holder with one
    argument and the method that appends to it is the call on that holder with
    two arguments. The prompt field is the string field that is read right in
    front of the call of the string writer.
    """
    result = {
        "contentHolderClass": holder,
        "contentHolderSet": None,
        "contentHolderAppend": None,
        "promptFieldClass": None,
        "promptFieldName": None,
        "promptFieldType": None,
        "promptWriterClass": None,
        "promptWriterName": None,
    }
    string_reads: dict[int, tuple[str, str, str]] = {}
    field_types: list[str] = []
    for index, (name, output) in enumerate(method["instructions"]):
        if name.startswith("iget") or name.startswith("iput"):
            hit = IGET.search(f"{name} {output}")
            if hit is None:
                continue
            if hit.group("type") == "Ljava/lang/String;":
                string_reads[index] = (hit.group("owner"), hit.group("name"), hit.group("type"))
            elif hit.group("type").startswith("L"):
                field_types.append(hit.group("type"))
        if not name.startswith("invoke"):
            continue
        hit = INVOKE.search(f"{name} {output}")
        if hit is None:
            continue
        arguments = split_parameters(hit.group("parameters").replace(" ", ""))
        callee = hit.group("callee")
        if len(arguments) == 3 and arguments[-1] == "Ljava/lang/String;":
            result["promptWriterClass"] = callee
            result["promptWriterName"] = hit.group("name")
            prompt = string_reads.get(index - 1)
            # The prompt of the request is written with the key 2. The
            # instruction in front of the field read is the key.
            key = None
            if index >= 2:
                key_name, key_output = method["instructions"][index - 2]
                if key_name.startswith("const/") and key_output.strip().endswith(", 2"):
                    key = "2"
            if prompt is not None and key == "2":
                result["promptFieldClass"] = prompt[0]
                result["promptFieldName"] = prompt[1]
                result["promptFieldType"] = prompt[2]
        elif len(arguments) == 1 and arguments[0] == "Ljava/lang/Object;":
            if callee in field_types:
                result["contentHolderClass"] = callee
                result["contentHolderSet"] = hit.group("name")
        elif (
            len(arguments) == 2
            and arguments[0] == "Ljava/lang/Object;"
            and arguments[1] == "Ljava/lang/Object;"
        ):
            if callee in field_types:
                result["contentHolderClass"] = callee
                result["contentHolderAppend"] = hit.group("name")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path, help="an APK or a directory with DEX files")
    parser.add_argument("--json", action="store_true", help="print the result as JSON")
    parser.add_argument(
        "--write-signature",
        action="store_true",
        help="write the shape of the reference seams instead of searching for twins",
    )
    arguments = parser.parse_args()

    if not arguments.target.exists():
        print(f"the target {arguments.target} does not exist", file=sys.stderr)
        return 2

    if arguments.write_signature:
        return write_signature(arguments.target)

    signatures = json.loads(SIGNATURE_FILE.read_text(encoding="utf-8"))
    methods = load_methods(arguments.target)
    shapes = class_shapes(methods)
    result = {"target": str(arguments.target), "methods": len(methods), "twins": {}}

    for key, entry in signatures.items():
        reference = entry["tokens"]
        candidates = methods
        if key == "responseContentAppend":
            # The APPEND path lives in the same class as the SET path, so the
            # search is limited to the class of the SET twin. That removes the
            # copies of the method that some builds have.
            set_twin = result["twins"].get("responseContentSet", {})
            if not set_twin.get("found"):
                result["twins"][key] = {"found": False}
                continue
            candidates = [
                method for method in methods if method["class"] == set_twin["class"]
            ]
        twin = find_twin(candidates, reference)
        if twin is None:
            result["twins"][key] = {"found": False}
            continue
        method, ratio = twin
        result["twins"][key] = {
            "found": True,
            "ratio": round(ratio, 4),
            "class": method["class"],
            "name": method["name"],
            "descriptor": method["descriptor"],
            "details": describe(
                method,
                shapes,
                result["twins"].get("responseContentSet", {})
                .get("details", {})
                .get("contentHolderClass")
                if key == "responseContentAppend"
                else None,
            ),
        }

    if arguments.json:
        print(json.dumps(result, indent=1))
    else:
        print(f"target: {result['target']}")
        print(f"methods that were read: {result['methods']}")
        for key, entry in result["twins"].items():
            if not entry["found"]:
                print(f"{key}: NOT FOUND")
                continue
            print(
                f"{key}: {entry['class']}->{entry['name']}{entry['descriptor']} "
                f"(similarity {entry['ratio']})"
            )
            for name, value in (entry.get("details") or {}).items():
                if value is not None:
                    print(f"    {name}: {value}")
    return 0 if all(entry["found"] for entry in result["twins"].values()) else 1


if __name__ == "__main__":
    sys.exit(main())
