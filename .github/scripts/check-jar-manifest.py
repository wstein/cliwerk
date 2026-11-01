#!/usr/bin/env python3
"""Checks the manifest of the built core jar build/libs/cliwerk-<version>.jar.

The jar file is named cliwerk, but the identity inside must stay that of upstream picocli (drop-in,
docs/FORK.adoc, decisions D1 and D5): Implementation-Title picocli,
Bundle-SymbolicName picocli and an Export-Package that contains package picocli. bnd is always applied
in CI and release, so missing OSGi headers (no Bundle-ManifestVersion) fail the check.
"""
import glob
import re
import sys
import zipfile

jars = [j for j in glob.glob("build/libs/cliwerk-*.jar")
        if not re.search(r"-(sources|javadoc|tests|test-sources)\.jar$", j)]
if len(jars) != 1:
    sys.exit("expected exactly one build/libs/cliwerk-<version>.jar, found: %s" % jars)
with zipfile.ZipFile(jars[0]) as z:
    text = z.read("META-INF/MANIFEST.MF").decode("utf-8")
text = text.replace("\r\n", "\n")
text = text.split("\n\n", 1)[0]  # main section only; per-entry sections must not override it
text = text.replace("\n ", "")  # unfold continuation lines
manifest = dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)
print("%s:" % jars[0])
for key in ("Implementation-Title", "Specification-Title", "Bundle-SymbolicName", "Export-Package"):
    print("  %s: %s" % (key, manifest.get(key)))


def export_parts(value):
    """Names and attribute/directive parts of an Export-Package value: clauses split on commas and
    parts on semicolons, both outside double quotes. Only an exact part equal to a package name matches."""
    return [part.strip()
            for clause in re.findall(r'(?:[^,"]|"[^"]*")+', value)
            for part in re.findall(r'(?:[^;"]|"[^"]*")+', clause)]


errors = []
if manifest.get("Implementation-Title") != "picocli":
    errors.append("Implementation-Title must be picocli")
if "Bundle-ManifestVersion" not in manifest:
    errors.append("OSGi headers missing (no Bundle-ManifestVersion): bnd was not applied")
else:
    if manifest.get("Bundle-SymbolicName", "").split(";")[0].strip() != "picocli":
        errors.append("Bundle-SymbolicName must be picocli")
    if "picocli" not in export_parts(manifest.get("Export-Package", "")):
        errors.append("Export-Package must contain picocli")
if errors:
    sys.exit("jar manifest check failed: " + "; ".join(errors))
