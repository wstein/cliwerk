#!/usr/bin/env python3
"""Summarizes the japicmp XML report of task apiCompat (gradle/api-compat.gradle).

Usage: api-compat-summary.py <api-compat.xml> <api-compat.txt> <summary.md>

Writes a Markdown table of all findings (one row per incompatible class or member) to <summary.md>
and prints GitHub Actions workflow commands to stdout, so the findings are also visible as
annotations of the check run. Never fails because of findings; exits 1 only if no report exists.
"""
import os
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


def findings(root):
    rows = []

    def walk(element, owner):
        if element.tag == "class" and element.get("fullyQualifiedName"):
            owner = element.get("fullyQualifiedName")
        if element.tag in MEMBER_TAGS:
            changes = change_types(element)
            incompatible = [c for c in changes if is_false(c[1]) or is_false(c[2])]
            if not changes and (is_false(element.get("binaryCompatible")) or is_false(element.get("sourceCompatible"))) \
                    and element.get("changeStatus") == "REMOVED":
                incompatible = [(element.get("changeStatus"), element.get("binaryCompatible"), element.get("sourceCompatible"))]
            for kind, binary, source in incompatible:
                rows.append({
                    "owner": owner or "?",
                    "member": describe(element),
                    "status": element.get("changeStatus", ""),
                    "kind": kind,
                    "binary": "incompatible" if is_false(binary) else ("compatible" if binary else "?"),
                    "source": "incompatible" if is_false(source) else ("compatible" if source else "?"),
                })
        for child in element:
            walk(child, owner)

    walk(root, None)
    return rows


def escape_command(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main(xml_path, txt_path, summary_path):
    lines = ["## API compatibility with upstream picocli", ""]
    rows = None
    if os.path.exists(xml_path):
        rows = findings(ET.parse(xml_path).getroot())
        lines.append("Findings (binary-incompatible changes of package `picocli`): **%d**" % len(rows))
        lines.append("")
        if rows:
            lines.append("| # | Class | Member | Status | Change | Binary | Source |")
            lines.append("|---|---|---|---|---|---|---|")
            for i, r in enumerate(rows, 1):
                lines.append("| %d | `%s` | `%s` | %s | %s | %s | %s |" % (
                    i, r["owner"], r["member"].replace("|", "\\|"), r["status"], r["kind"], r["binary"], r["source"]))
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
    for r in (rows or [])[:10]:
        print("::warning title=API incompatible with upstream picocli::%s" % escape_command(
            "%s %s: %s %s (binary %s, source %s)" % (r["owner"], r["member"], r["status"], r["kind"], r["binary"], r["source"])))
    # ... and the whole summary in notices (at most 10 per step), so it can be read from the check run
    chunk = 30000
    parts = [summary[i:i + chunk] for i in range(0, len(summary), chunk)][:10]
    for n, part in enumerate(parts, 1):
        print("::notice title=API compatibility report (part %d of %d)::%s" % (n, len(parts), escape_command(part)))
    return 0 if rows is not None or os.path.exists(txt_path) else 1


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:4]))
