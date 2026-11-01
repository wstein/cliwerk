#!/usr/bin/env python3
"""Tests for check-jar-manifest.py: builds fixture jars in a temp dir and runs the script there.

Run: python3 -I -m unittest discover -s .github/scripts -p "test_*.py"
"""
import os
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-jar-manifest.py")

GOOD = {
    "Manifest-Version": "1.0",
    "Implementation-Title": "picocli",
    "Bundle-ManifestVersion": "2",
    "Bundle-SymbolicName": "picocli",
    "Export-Package": "picocli;version=\"4.7.7\"",
}


def manifest_text(headers, crlf=False):
    nl = "\r\n" if crlf else "\n"
    return nl.join("%s: %s" % kv for kv in headers.items()) + nl + nl


class CheckJarManifestTest(unittest.TestCase):
    def run_script(self, jar_names, headers=None, raw=None):
        with tempfile.TemporaryDirectory() as tmp:
            os.makedirs(os.path.join(tmp, "build", "libs"))
            for name in jar_names:
                with zipfile.ZipFile(os.path.join(tmp, "build", "libs", name), "w") as z:
                    z.writestr("META-INF/MANIFEST.MF", raw if raw is not None else manifest_text(headers))
            return subprocess.run([sys.executable, "-I", SCRIPT], cwd=tmp,
                                  capture_output=True, text=True)

    def test_correct_manifest_passes(self):
        r = self.run_script(["cliwerk-1.0.jar"], GOOD)
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_wrapped_export_package_and_crlf_pass(self):
        raw = manifest_text(dict(GOOD, **{"Export-Package": "picocli;version=\"4.7.7\""}), crlf=True)
        raw = raw.replace("Export-Package: picocli;", "Export-Package: pic\r\n ocli;")
        r = self.run_script(["cliwerk-1.0.jar"], raw=raw)
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_missing_osgi_headers_fails(self):
        headers = {k: v for k, v in GOOD.items() if not k.startswith(("Bundle-", "Export-"))}
        r = self.run_script(["cliwerk-1.0.jar"], headers)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("OSGi", r.stderr)

    def test_wrong_symbolic_name_fails(self):
        r = self.run_script(["cliwerk-1.0.jar"], dict(GOOD, **{"Bundle-SymbolicName": "cliwerk"}))
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("Bundle-SymbolicName", r.stderr)

    def test_wrong_implementation_title_fails(self):
        r = self.run_script(["cliwerk-1.0.jar"], dict(GOOD, **{"Implementation-Title": "cliwerk"}))
        self.assertNotEqual(r.returncode, 0)

    def test_export_package_without_picocli_fails(self):
        r = self.run_script(["cliwerk-1.0.jar"], dict(GOOD, **{"Export-Package": "picocli.x;version=1"}))
        self.assertNotEqual(r.returncode, 0)

    def test_export_package_with_prefixed_name_fails(self):
        r = self.run_script(["cliwerk-1.0.jar"], dict(GOOD, **{"Export-Package": "xpicocli;version=1"}))
        self.assertNotEqual(r.returncode, 0)

    def test_export_package_with_space_after_comma_passes(self):
        r = self.run_script(["cliwerk-1.0.jar"], dict(GOOD, **{"Export-Package": "a, picocli"}))
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_export_package_picocli_only_inside_directive_fails(self):
        r = self.run_script(["cliwerk-1.0.jar"],
                            dict(GOOD, **{"Export-Package": 'a;uses:="x,picocli,y"'}))
        self.assertNotEqual(r.returncode, 0)

    def test_export_package_picocli_only_as_attribute_value_fails(self):
        r = self.run_script(["cliwerk-1.0.jar"], dict(GOOD, **{"Export-Package": "a;foo=picocli"}))
        self.assertNotEqual(r.returncode, 0)

    def test_symbolic_name_with_directive_passes(self):
        r = self.run_script(["cliwerk-1.0.jar"],
                            dict(GOOD, **{"Bundle-SymbolicName": "picocli;singleton:=true"}))
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_per_entry_section_does_not_override_main_headers(self):
        raw = manifest_text(GOOD) + "Name: picocli/Foo.class\nImplementation-Title: other\n\n"
        r = self.run_script(["cliwerk-1.0.jar"], raw=raw)
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_javadoc_jar_is_ignored(self):
        r = self.run_script(["cliwerk-1.0.jar", "cliwerk-1.0-javadoc.jar"], GOOD)
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_two_jars_fail_but_sources_jar_is_ignored(self):
        self.assertNotEqual(self.run_script(["cliwerk-1.0.jar", "cliwerk-2.0.jar"], GOOD).returncode, 0)
        self.assertEqual(self.run_script(["cliwerk-1.0.jar", "cliwerk-1.0-sources.jar"], GOOD).returncode, 0)


if __name__ == "__main__":
    unittest.main()
