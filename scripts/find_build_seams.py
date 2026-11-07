#!/usr/bin/env python3
"""Find the chat seams of a build of the official DeepSeek app.

Every release of the app is compiled and obfuscated on its own, so the class
names of the chat path change with every build. This script finds those
seams without assuming any name: it only looks at the shape of the code.

  * the string writer of a request is the interface method that is called the
    most often with the descriptor (object, int, string),
  * the prompt serializer is the method that writes a string field of its own
    data class with that writer and that is preceded by a check-cast to that
    data class,
  * the streaming path is the class that has a method with the descriptor
    (string, X, Y) that replaces the content of a state holder, and a method
    with the descriptor (X, string) that appends to it. The state holder is
    the class that has a method (object) and a method (object, object) that
    returns a boolean.

The result is printed as a small report that can be read out of the log of a
CI run, and the same script is used by the tests to prove that it reproduces
the fingerprint sets that were derived by hand.

Usage:
    python3 scripts/find_build_seams.py <apk|dex-dir>
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

CONTENT = "content"
WRITER_DESCRIPTOR = re.compile(r"^\(L[^;]*;ILjava/lang/String;\)V$")
IGET = re.compile(
    r"iget(?P<kind>[-/]\w+)?\s+(?P<obj>[vp]\d+),\s*(?P<reg>[vp]\d+),\s*"
    r"(?P<owner>L[^;]+;)->(?P<name>\w+)\s+(?P<type>L[^;]+;|[ZBCSIJFD])"
)
INVOKE = re.compile(
    r"invoke-(?P<kind>\w+)\s+(?P<registers>[^,]+(?:,\s*[^,]+)*?),\s*"
    r"(?P<callee>L[^;]+;)->(?P<name>\w+)\((?P<parameters>[^)]*)\)(?P<return>[\w;\[/]+)"
)
CONST_STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')


def normalize(descriptor: str) -> str:
    return descriptor.replace(" ", "")


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


class Method:
    """The parts of a method that the search needs."""

    def __init__(self, defining_class: str, name: str, descriptor: str):
        self.defining_class = defining_class
        self.name = name
        self.descriptor = normalize(descriptor)
        self.strings: list[str] = []
        # (index, owner, name, type)
        self.field_reads: list[tuple[int, str, str, str]] = []
        # (index, callee, name, parameters, return type)
        self.calls: list[tuple[int, str, str, list[str], str]] = []
        self.static_fields: set[str] = set()

    @property
    def label(self) -> str:
        return f"{self.defining_class}->{self.name}{self.descriptor}"


def load_methods(target: Path) -> list[Method]:
    """Read every method of every DEX file of the target."""
    from androguard.core.dex import DEX

    payloads: list[tuple[str, bytes]] = []
    if target.is_dir():
        for path in sorted(target.glob("*.dex")):
            payloads.append((path.name, path.read_bytes()))
    else:
        with zipfile.ZipFile(target) as archive:
            for name in sorted(
                item for item in archive.namelist() if item.endswith(".dex") and not item.endswith("/")
            ):
                payloads.append((name, archive.read(name)))
    if not payloads:
        raise SystemExit(f"{target} contains no DEX file")

    methods: list[Method] = []
    for _, payload in payloads:
        dex = DEX(payload)
        for cls in dex.get_classes():
            class_name = cls.get_name()
            for member in cls.get_methods():
                method = Method(class_name, member.get_name(), member.get_descriptor())
                try:
                    instructions = list(member.get_instructions())
                except Exception:
                    methods.append(method)
                    continue
                for index, instruction in enumerate(instructions):
                    name = instruction.get_name()
                    output = instruction.get_output()
                    if name.startswith("const-string"):
                        match = CONST_STRING.search(output)
                        if match:
                            method.strings.append(match.group(1))
                    elif name.startswith("iget") or name.startswith("iput"):
                        hit = IGET.search(f"{name} {output}")
                        if hit:
                            method.field_reads.append(
                                (
                                    index,
                                    hit.group("owner"),
                                    hit.group("name"),
                                    hit.group("type"),
                                )
                            )
                    elif name.startswith("invoke"):
                        hit = INVOKE.search(f"{name} {output}")
                        if hit:
                            method.calls.append(
                                (
                                    index,
                                    hit.group("callee"),
                                    hit.group("name"),
                                    split_parameters(hit.group("parameters")),
                                    hit.group("return"),
                                )
                            )
                    elif name.startswith("sget-object") or name.startswith("sget"):
                        hit = re.search(r"L[^;]+;->(\w+)", output)
                        if hit:
                            method.static_fields.add(hit.group(1))
                methods.append(method)
    return methods


def holders(methods: list[Method]) -> set[str]:
    """The classes that can hold the streamed content of an answer."""
    shapes: dict[str, set[str]] = {}
    for method in methods:
        shapes.setdefault(method.defining_class, set()).add(
            method.name + method.descriptor
        )
    result = set()
    for class_name, members in shapes.items():
        has_setter = any(
            member.endswith("(Ljava/lang/Object;)V") for member in members
        )
        has_update = any(
            member.endswith("(Ljava/lang/Object;Ljava/lang/Object;)Z") for member in members
        )
        if has_setter and has_update:
            result.add(class_name)
    return result


def find_writer(methods: list[Method]) -> tuple[str, str] | None:
    """The interface method that writes the string fields of a request."""
    counter: dict[tuple[str, str], int] = {}
    for method in methods:
        for _, callee, name, parameters, _ in method.calls:
            if WRITER_DESCRIPTOR.match("(" + "".join(parameters) + ")V"):
                counter[(callee, name)] = counter.get((callee, name), 0) + 1
    if not counter:
        return None
    (callee, name), _ = max(counter.items(), key=lambda item: item[1])
    return callee, name


def find_prompt_serializer(
    methods: list[Method], writer: tuple[str, str]
) -> tuple[Method, str, str, str] | None:
    """The method that writes the prompt of a request.

    The prompt is the string field that is read right in front of the call of
    the string writer, so the field read and the call have to follow each
    other directly. The serializer of the chat request is the one that writes
    the most string fields, because it carries the whole request.
    """
    writer_class, writer_name = writer
    best: tuple[int, Method, str, str, str] | None = None
    for method in methods:
        if method.descriptor.count("(") != 1:
            continue
        if not method.descriptor.endswith("Ljava/lang/Object;)V"):
            continue
        # the class of the request owns the descriptor of the request
        if "descriptor" not in method.static_fields:
            continue
        string_reads = {
            index: (owner, name, type)
            for index, owner, name, type in method.field_reads
            if type == "Ljava/lang/String;"
        }
        for index, callee, name, parameters, _ in method.calls:
            if callee != writer_class or name != writer_name:
                continue
            if not parameters or parameters[-1] != "Ljava/lang/String;":
                continue
            # the field that is read right in front of the call is the prompt
            prompt = string_reads.get(index - 1)
            if prompt is None:
                continue
            count = len(string_reads)
            if best is None or count > best[0]:
                best = (count, method, prompt[0], prompt[1], prompt[2])
    if best is None:
        return None
    _, method, field_class, field_name, field_type = best
    return method, field_class, field_name, field_type


def find_stream_seams(
    methods: list[Method], holder_classes: set[str]
) -> tuple[Method, Method, str] | None:
    """The class that replaces and that appends the streamed content.

    The SET path has the descriptor (string, X, Y) and the APPEND path has the
    descriptor (X, string). Both have to live in the same class and both have
    to use the same state holder.
    """
    set_candidates: list[Method] = []
    append_candidates: list[Method] = []
    for method in methods:
        if CONTENT not in method.strings:
            continue
        parameters = method.descriptor[method.descriptor.index("(") + 1 : -1]
        arguments = split_parameters(parameters)
        if len(arguments) == 3 and arguments[0] == "Ljava/lang/String;":
            if any(
                callee in holder_classes and call_parameters == ["Ljava/lang/Object;"]
                for _, callee, _, call_parameters, _ in method.calls
            ):
                set_candidates.append(method)
        elif len(arguments) == 2 and arguments[1] == "Ljava/lang/String;":
            if any(
                callee in holder_classes
                and call_parameters == ["Ljava/lang/Object;", "Ljava/lang/Object;"]
                for _, callee, _, call_parameters, _ in method.calls
            ):
                append_candidates.append(method)
    for set_method in set_candidates:
        for append_method in append_candidates:
            if set_method.defining_class != append_method.defining_class:
                continue
            holders_of_set = {
                callee
                for _, callee, _, call_parameters, _ in set_method.calls
                if callee in holder_classes and call_parameters == ["Ljava/lang/Object;"]
            }
            holders_of_append = {
                callee
                for _, callee, _, call_parameters, _ in append_method.calls
                if callee in holder_classes
                and call_parameters == ["Ljava/lang/Object;", "Ljava/lang/Object;"]
            }
            shared = holders_of_set & holders_of_append
            if shared:
                return set_method, append_method, sorted(shared)[0]
    return None


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    target = Path(sys.argv[1])
    if not target.exists():
        print(f"the target {target} does not exist", file=sys.stderr)
        return 2

    methods = load_methods(target)
    holder_classes = holders(methods)
    writer = find_writer(methods)
    if writer is None:
        print("no string writer was found in this build")
        return 1
    serializer = find_prompt_serializer(methods, writer)
    seams = find_stream_seams(methods, holder_classes)

    print(f"methods that were read: {len(methods)}")
    print()
    print(f"string writer: {writer[0]}->{writer[1]}")
    if serializer is None:
        print("prompt serializer: NOT FOUND")
    else:
        method, field_class, field_name, field_type = serializer
        print(f"prompt serializer: {method.label}")
        print(f"prompt field: {field_class}->{field_name} {field_type}")
    if seams is None:
        print("streaming seams: NOT FOUND")
    else:
        set_method, append_method, holder = seams
        print(f"streaming SET: {set_method.label}")
        print(f"streaming APPEND: {append_method.label}")
        print(f"content holder: {holder}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
