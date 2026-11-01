#!/usr/bin/env python3
"""Tests for ci_plan.py: changed-file classification, matrix selection and the ci-gate decision.

Run: python3 -I -m unittest discover -s .github/scripts -p "test_*.py"
"""
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "ci_plan.py")
_spec = importlib.util.spec_from_file_location("ci_plan", SCRIPT)
ci_plan = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ci_plan)

PR_TIER = [(8, "ubuntu-latest"), (17, "ubuntu-latest"), (21, "ubuntu-latest"),
           (21, "windows-latest"), (21, "macos-latest")]
FULL_TIER = sorted((j, o) for j in (8, 11, 17, 21) for o in ("ubuntu-latest", "macos-latest", "windows-latest"))


def legs(plan):
    return sorted((e["java-version"], e["os"]) for e in plan["matrix"]["include"])


class DocsOnlyTest(unittest.TestCase):
    def test_docs_only(self):
        self.assertTrue(ci_plan.is_docs_only(
            ["docs/index.adoc", "README.md", "sub/dir/NOTES.md", "gh-pages/a/b.html", "LICENSE", "x/y/z.adoc"]))

    def test_mixed(self):
        self.assertFalse(ci_plan.is_docs_only(["README.md", "src/main/java/picocli/CommandLine.java"]))

    def test_code_only(self):
        self.assertFalse(ci_plan.is_docs_only(["build.gradle"]))

    def test_workflow_and_scripts_are_code(self):
        self.assertFalse(ci_plan.is_docs_only([".github/workflows/ci.yml"]))
        self.assertFalse(ci_plan.is_docs_only([".github/scripts/ci_plan.py"]))

    def test_lookalikes_are_code(self):
        self.assertFalse(ci_plan.is_docs_only(["LICENSE.txt"]))
        self.assertFalse(ci_plan.is_docs_only(["docsx/a.java"]))
        self.assertFalse(ci_plan.is_docs_only(["src/readme.mdx"]))

    def test_empty_list_is_not_docs_only(self):
        self.assertFalse(ci_plan.is_docs_only([]))
        self.assertFalse(ci_plan.is_docs_only(None))


class PlanTest(unittest.TestCase):
    def test_pr_default_tier(self):
        p = ci_plan.plan("pull_request", [], ["src/A.java"])
        self.assertTrue(p["run"])
        self.assertEqual(p["tier"], "pr")
        self.assertEqual(legs(p), sorted(PR_TIER))

    def test_pr_other_label_keeps_default_tier(self):
        p = ci_plan.plan("pull_request", ["bug"], ["src/A.java"])
        self.assertEqual(legs(p), sorted(PR_TIER))

    def test_pr_ci_full_label(self):
        p = ci_plan.plan("pull_request", ["bug", "ci-full"], ["src/A.java"])
        self.assertEqual(p["tier"], "full")
        self.assertEqual(legs(p), FULL_TIER)

    def test_push_to_develop_is_full(self):
        p = ci_plan.plan("push", [], ["src/A.java"], ref="refs/heads/develop")
        self.assertEqual(p["tier"], "full")
        self.assertEqual(legs(p), FULL_TIER)

    def test_push_to_other_branch_is_pr_tier(self):
        p = ci_plan.plan("push", [], ["src/A.java"], ref="refs/heads/topic")
        self.assertEqual(legs(p), sorted(PR_TIER))

    def test_schedule_is_full(self):
        p = ci_plan.plan("schedule", [], None, ref="refs/heads/develop")
        self.assertEqual(legs(p), FULL_TIER)

    def test_schedule_ignores_docs_only(self):
        p = ci_plan.plan("schedule", [], ["README.md"])
        self.assertTrue(p["run"])
        self.assertEqual(legs(p), FULL_TIER)

    def test_workflow_dispatch_is_full(self):
        p = ci_plan.plan("workflow_dispatch", [], [], ref="refs/heads/topic")
        self.assertEqual(legs(p), FULL_TIER)

    def test_docs_only_pr_runs_nothing(self):
        p = ci_plan.plan("pull_request", ["ci-full"], ["docs/a.adoc", "README.md"])
        self.assertFalse(p["run"])
        self.assertEqual(p["matrix"], {"include": []})

    def test_docs_only_push_to_develop_runs_nothing(self):
        p = ci_plan.plan("push", [], ["LICENSE"], ref="refs/heads/develop")
        self.assertFalse(p["run"])

    def test_mixed_pr_runs(self):
        p = ci_plan.plan("pull_request", [], ["docs/a.adoc", "build.gradle"])
        self.assertTrue(p["run"])

    def test_unknown_changes_run(self):
        p = ci_plan.plan("pull_request", [], None)
        self.assertTrue(p["run"])
        p = ci_plan.plan("pull_request", [], [])
        self.assertTrue(p["run"])

    def test_every_leg_has_exactly_the_matrix_keys_in_order(self):
        for e in ci_plan.plan("push", [], ["a"], ref="refs/heads/develop")["matrix"]["include"]:
            self.assertEqual(list(e), ["java-version", "os"])

    def test_labels_parsing(self):
        self.assertEqual(ci_plan.parse_labels('["a","ci-full"]'), ["a", "ci-full"])
        self.assertEqual(ci_plan.parse_labels(""), [])
        self.assertEqual(ci_plan.parse_labels("null"), [])
        self.assertEqual(ci_plan.parse_labels("a, ci-full"), ["a", "ci-full"])


class GateTest(unittest.TestCase):
    def test_success(self):
        self.assertTrue(ci_plan.gate_ok("success", "true", "success"))

    def test_docs_only_skipped(self):
        self.assertTrue(ci_plan.gate_ok("success", "false", "skipped"))

    def test_skipped_although_it_should_have_run(self):
        self.assertFalse(ci_plan.gate_ok("success", "true", "skipped"))

    def test_failed_or_cancelled_build(self):
        self.assertFalse(ci_plan.gate_ok("success", "true", "failure"))
        self.assertFalse(ci_plan.gate_ok("success", "true", "cancelled"))

    def test_plan_not_successful(self):
        self.assertFalse(ci_plan.gate_ok("failure", "", "skipped"))
        self.assertFalse(ci_plan.gate_ok("cancelled", "", "skipped"))
        self.assertFalse(ci_plan.gate_ok("success", "false", "failure"))


def git(cwd, *args):
    subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@example.invalid", "-c", "commit.gpgsign=false",
                    *args], cwd=cwd, check=True, capture_output=True, text=True)


class CliTest(unittest.TestCase):
    def run_cli(self, args, cwd, env_extra=None):
        out = os.path.join(cwd, "github_output")
        env = {"PATH": os.environ.get("PATH", ""), "GITHUB_OUTPUT": out}
        env.update(env_extra or {})
        r = subprocess.run([sys.executable, "-I", SCRIPT, *args], cwd=cwd, env=env, capture_output=True, text=True)
        outputs = {}
        if os.path.exists(out):
            with open(out) as f:
                outputs = dict(line.rstrip("\n").split("=", 1) for line in f if "=" in line)
        return r, outputs

    def make_repo(self, tmp, second_commit_files):
        git(tmp, "init", "-q")
        with open(os.path.join(tmp, "a.txt"), "w") as f:
            f.write("a")
        git(tmp, "add", "-A")
        git(tmp, "commit", "-q", "-m", "one")
        for name in second_commit_files:
            os.makedirs(os.path.dirname(os.path.join(tmp, name)) or tmp, exist_ok=True)
            with open(os.path.join(tmp, name), "w") as f:
                f.write("b")
        git(tmp, "add", "-A")
        git(tmp, "commit", "-q", "-m", "two")

    def test_plan_docs_only_from_git(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.make_repo(tmp, ["docs/x.adoc", "README.md"])
            r, o = self.run_cli(["plan", "--event", "pull_request", "--labels", "[]",
                                 "--base", "HEAD~1", "--head", "HEAD"], tmp)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertEqual(o["run"], "false")
            self.assertEqual(json.loads(o["matrix"]), {"include": []})

    def test_plan_code_change_from_git(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.make_repo(tmp, ["docs/x.adoc", "src/A.java"])
            r, o = self.run_cli(["plan", "--event", "pull_request", "--labels", '["ci-full"]',
                                 "--base", "HEAD~1", "--head", "HEAD"], tmp)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertEqual(o["run"], "true")
            self.assertEqual(o["tier"], "full")
            self.assertEqual(len(json.loads(o["matrix"])["include"]), 12)

    def test_plan_unresolvable_base_runs(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.make_repo(tmp, ["README.md"])
            r, o = self.run_cli(["plan", "--event", "push", "--ref", "refs/heads/develop",
                                 "--base", "0" * 40, "--head", "HEAD"], tmp)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertEqual(o["run"], "true")

    def test_plan_schedule_needs_no_git(self):
        with tempfile.TemporaryDirectory() as tmp:
            r, o = self.run_cli(["plan", "--event", "schedule", "--ref", "refs/heads/develop"], tmp)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertEqual(o["tier"], "full")
            self.assertEqual(len(json.loads(o["matrix"])["include"]), 12)

    def test_gate_exit_codes(self):
        with tempfile.TemporaryDirectory() as tmp:
            r, _ = self.run_cli(["gate", "--plan-result", "success", "--run", "false", "--build-result", "skipped"], tmp)
            self.assertEqual(r.returncode, 0, r.stderr)
            r, _ = self.run_cli(["gate", "--plan-result", "success", "--run", "true", "--build-result", "failure"], tmp)
            self.assertNotEqual(r.returncode, 0)


if __name__ == "__main__":
    unittest.main()
