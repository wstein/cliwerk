#!/usr/bin/env python3
"""Summarizes the japicmp XML report of task apiCompat (gradle/api-compat.gradle).

Usage: api-compat-summary.py <api-compat.xml> <api-compat.txt> <summary.md> [<api-compat-exceptions.txt>]

Writes a Markdown table of all findings (one row per incompatible class or member) to <summary.md>
and prints GitHub Actions workflow commands to stdout, so the findings are also visible as
annotations of the check run. Never fails because of findings; exits 1 only if no report exists.

Exceptions of kind `annotation-element` in the exceptions file (`annotation-element <class>#<name>() | <why>`)
are evaluated here, not by japicmp: a finding is "excepted" only if it is METHOD_ABSTRACT_ADDED_TO_CLASS
(and nothing else) of exactly that parameterless method of an annotation type (classType ANNOTATION) of
package picocli. Excepted findings are listed separately and do not count as findings; all others do.
An exception that matches no finding is reported as stale.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

MEMBER_TAGS = {"method", "constructor", "field", "superclass", "interface", "annotation", "class"}


def is_false(value):
    return value is not None and value.lower() == "false"


def change_types(element):
    changes = element.find("compatibilityChanges")
    if changes is None:
        return []
    result = []
    for change in changes:
        kind = change.get("type") or (change.text or "").strip()
        # older japicmp versions write the change type as text without per-change flags
        binary = change.get("binaryCompatible", element.get("binaryCompatible"))
        source = change.get("sourceCompatible", element.get("sourceCompatible"))
        result.append((kind, binary, source))
    return result


def describe(element):
    tag = element.tag
    if tag == "class":
        return "class"
    name = element.get("name") or element.get("fullyQualifiedName") or ""
    params = element.find("parameters")
    if tag in ("method", "constructor"):
        types = [p.get("type", "?") for p in params] if params is not None else []
        return "%s %s(%s)" % (tag, name, ", ".join(types))
    return ("%s %s" % (tag, name)).strip()


EXCEPTION_LINE = re.compile(r"^(\S+)\s+(\S+)\s*\|\s*(\S.*)$")
ELEMENT_PATTERN = re.compile(r"^(picocli\.[\w$.]+)#(\w+)\(\)$")
ACCEPTED_CHANGE = "METHOD_ABSTRACT_ADDED_TO_CLASS"


def read_exceptions(path):
    """Returns {(class, method name): justification} of the annotation-element lines; other kinds are japicmp's."""
    result = {}
    if not path or not os.path.exists(path):
        return result
    with open(path, encoding="utf-8") as f:
        for number, raw in enumerate(f, 1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            m = EXCEPTION_LINE.match(line)
            if not m:
                raise SystemExit("%s:%d: expected '<kind> <pattern> | <justification>', got: %s" % (path, number, raw.rstrip()))
            if m.group(1) != "annotation-element":
                continue
            p = ELEMENT_PATTERN.match(m.group(2))
            if not p:
                raise SystemExit("%s:%d: annotation-element needs the form picocli.Class$Nested#name() (no wildcards), got: %s"
                                 % (path, number, m.group(2)))
            result[(p.group(1), p.group(2))] = m.group(3)
    return result


def findings(root):
    rows = []

    def walk(element, owner, annotation):
        if element.tag == "class" and element.get("fullyQualifiedName"):
            owner = element.get("fullyQualifiedName")
            class_type = element.find("classType")
            annotation = class_type is not None and class_type.get("oldType") == "ANNOTATION" \
                and class_type.get("newType") == "ANNOTATION"
        if element.tag in MEMBER_TAGS:
            changes = change_types(element)
            incompatible = [c for c in changes if is_false(c[1]) or is_false(c[2])]
            if not changes and (is_false(element.get("binaryCompatible")) or is_false(element.get("sourceCompatible"))) \
                    and element.get("changeStatus") == "REMOVED":
                incompatible = [(element.get("changeStatus"), element.get("binaryCompatible"), element.get("sourceCompatible"))]
            for kind, binary, source in incompatible:
                rows.append({
                    "owner": owner or "?",
                    "annotation": bool(annotation),
                    "name": element.get("name") or "",
                    "parameters": len(element.find("parameters")) if element.find("parameters") is not None else 0,
                    "changes": [c[0] for c in incompatible],
                    "member": describe(element),
                    "status": element.get("changeStatus", ""),
                    "kind": kind,
                    "binary": "incompatible" if is_false(binary) else ("compatible" if binary else "?"),
                    "source": "incompatible" if is_false(source) else ("compatible" if source else "?"),
                })
        for child in element:
            walk(child, owner, annotation)

    walk(root, None, False)
    return rows


def split_excepted(rows, exceptions):
    """Splits rows into (open, excepted, stale exception keys)."""
    open_rows, excepted, used = [], [], set()
    for r in rows:
        key = (r["owner"], r["name"])
        if r["annotation"] and r["member"].startswith("method ") and r["parameters"] == 0 \
                and key in exceptions and r["changes"] == [ACCEPTED_CHANGE]:
            r["justification"] = exceptions[key]
            excepted.append(r)
            used.add(key)
        else:
            open_rows.append(r)
    return open_rows, excepted, sorted(set(exceptions) - used)


def escape_command(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def table(lines, rows, extra=None):
    lines.append("| # | Class | Member | Status | Change | Binary | Source |" + (" Justification |" if extra else ""))
    lines.append("|---|---|---|---|---|---|---|" + ("---|" if extra else ""))
    for i, r in enumerate(rows, 1):
        lines.append("| %d | `%s` | `%s` | %s | %s | %s | %s |%s" % (
            i, r["owner"], r["member"].replace("|", "\\|"), r["status"], r["kind"], r["binary"], r["source"],
            (" %s |" % r["justification"]) if extra else ""))
    lines.append("")


def main(xml_path, txt_path, summary_path, exceptions_path=None):
    lines = ["## API compatibility with upstream picocli", ""]
    rows = None
    open_rows = excepted = stale = []
    if os.path.exists(xml_path):
        rows = findings(ET.parse(xml_path).getroot())
        open_rows, excepted, stale = split_excepted(rows, read_exceptions(exceptions_path))
        lines.append("Findings (binary- or source-incompatible changes of package `picocli`): **%d** not excepted, **%d** excepted"
                     % (len(open_rows), len(excepted)))
        lines.append("")
        if open_rows:
            table(lines, open_rows)
        if excepted:
            lines.append("Excepted (`gradle/api-compat-exceptions.txt`, kind `annotation-element`): **%d**" % len(excepted))
            lines.append("")
            table(lines, excepted, extra=True)
        if stale:
            lines.append("Stale exceptions (match no finding, remove them): %s" % ", ".join("`%s#%s()`" % k for k in stale))
            lines.append("")
    else:
        lines.append("No XML report found at `%s`; the check did not get to compare the jars." % xml_path)
        lines.append("")
    if os.path.exists(txt_path):
        with open(txt_path, encoding="utf-8", errors="replace") as f:
            txt = f.read()
        lines += ["<details><summary>japicmp text report</summary>", "", "```", txt.rstrip(), "```", "", "</details>", ""]
    summary = "\n".join(lines) + "\n"
    with open(summary_path, "w", encoding="utf-8") as f:
        f.write(summary)

    # annotations: one per finding (GitHub shows at most 10 warnings per step) ...
    if rows is not None:
        print("::notice title=API compatibility result::%d not excepted, %d excepted, %d stale exceptions"
              % (len(open_rows), len(excepted), len(stale)))
    for k in stale:
        print("::warning title=Stale API compatibility exception::%s" % escape_command("%s#%s() matches no finding" % k))
    for r in open_rows[:10]:
        print("::warning title=API incompatible with upstream picocli::%s" % escape_command(
            "%s %s: %s %s (binary %s, source %s)" % (r["owner"], r["member"], r["status"], r["kind"], r["binary"], r["source"])))
    # ... and the whole summary in notices (at most 10 per step), so it can be read from the check run
    chunk = 30000
    parts = [summary[i:i + chunk] for i in range(0, len(summary), chunk)][:10]
    for n, part in enumerate(parts, 1):
        print("::notice title=API compatibility report (part %d of %d)::%s" % (n, len(parts), escape_command(part)))
    return 0 if rows is not None or os.path.exists(txt_path) else 1


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:5]))
