#!/usr/bin/env python3
"""Checks the manifest of the built core jar build/libs/cliwerk-<version>.jar.

The jar file is named cliwerk, but the identity inside must stay that of upstream picocli (drop-in,
docs/FORK.adoc, decisions D1 and D5): Implementation-Title picocli and, when bnd is applied,
Bundle-SymbolicName picocli and an Export-Package that contains package picocli.
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
text = re.sub(r"\r?\n ", "", text)  # unfold continuation lines
manifest = dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)
print("%s:" % jars[0])
for key in ("Implementation-Title", "Specification-Title", "Bundle-SymbolicName", "Export-Package"):
    print("  %s: %s" % (key, manifest.get(key)))

errors = []
if manifest.get("Implementation-Title") != "picocli":
    errors.append("Implementation-Title must be picocli")
if "Bundle-ManifestVersion" in manifest:  # bnd is applied
    if manifest.get("Bundle-SymbolicName") != "picocli":
        errors.append("Bundle-SymbolicName must be picocli")
    if not re.search(r"(^|,)picocli(;|,|$)", manifest.get("Export-Package", "")):
        errors.append("Export-Package must contain picocli")
else:
    print("  (no OSGi headers: bnd plugin not applied in this build)")
if errors:
    sys.exit("jar manifest check failed: " + "; ".join(errors))
