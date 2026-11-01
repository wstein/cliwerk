#!/usr/bin/env python3
"""Plans the CI build matrix and decides the ci-gate result.

plan: classifies the changed files (docs-only or not) and selects the matrix tier from the event and the
      PR labels. Writes `run`, `tier` and `matrix` (JSON, usable with fromJSON) to $GITHUB_OUTPUT.
gate: exits 0 iff the build job succeeded, or was skipped because the change was docs-only.

Tiers: the PR tier (ubuntu JDK 8/17/21, windows JDK 21, macos JDK 21) is the default for pull requests.
The full tier (JDK 8/11/17/21 on ubuntu, macos and windows) runs on push to develop, on schedule, on
workflow_dispatch and on pull requests that carry the label ci-full.

Run the tests: python3 -I -m unittest discover -s .github/scripts -p "test_*.py"
"""
import argparse
import json
import os
import subprocess
import sys

FULL_LABEL = "ci-full"
DEVELOP_REF = "refs/heads/develop"
OSES = ("ubuntu-latest", "macos-latest", "windows-latest")
FULL_JAVA = (8, 11, 17, 21)
PR_LEGS = ((8, "ubuntu-latest"), (17, "ubuntu-latest"), (21, "ubuntu-latest"),
           (21, "windows-latest"), (21, "macos-latest"))
DOCS_PREFIXES = ("docs/", "gh-pages/")
DOCS_SUFFIXES = (".md", ".adoc")
DOCS_FILES = ("LICENSE",)


def is_docs_path(path):
    return path.startswith(DOCS_PREFIXES) or path.endswith(DOCS_SUFFIXES) or path in DOCS_FILES


def is_docs_only(files):
    """True iff there is at least one changed file and every one is documentation.
    An empty or unknown change list is never docs-only, so that the build runs when in doubt."""
    return bool(files) and all(is_docs_path(f) for f in files)


def parse_labels(text):
    text = (text or "").strip()
    if not text:
        return []
    try:
        value = json.loads(text)
    except ValueError:
        return [s.strip() for s in text.split(",") if s.strip()]
    if isinstance(value, list):
        return [str(v) for v in value]
    return []


def matrix_for(full):
    if full:
        legs = [(j, o) for j in FULL_JAVA for o in OSES]
    else:
        legs = list(PR_LEGS)
    return {"include": [{"java-version": j, "os": o} for j, o in legs]}


def plan(event, labels, files, ref=""):
    scheduled = event in ("schedule", "workflow_dispatch")
    if not scheduled and is_docs_only(files):
        return {"run": False, "tier": "none", "matrix": {"include": []}}
    full = scheduled or (event == "push" and ref == DEVELOP_REF) or FULL_LABEL in (labels or [])
    return {"run": True, "tier": "full" if full else "pr", "matrix": matrix_for(full)}


def gate_ok(plan_result, run, build_result):
    if plan_result != "success":
        return False
    if build_result == "success":
        return True
    return build_result == "skipped" and run == "false"


def changed_files(base, head):
    """Files changed between base and head (three-dot), or None if git cannot tell."""
    if not base or not head or set(base) == {"0"}:
        return None
    r = subprocess.run(["git", "diff", "--name-only", "%s...%s" % (base, head)],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print("git diff failed, assuming code changes: %s" % r.stderr.strip(), file=sys.stderr)
        return None
    return [line for line in r.stdout.splitlines() if line]


def write_outputs(values):
    path = os.environ.get("GITHUB_OUTPUT")
    lines = "".join("%s=%s\n" % kv for kv in values.items())
    if path:
        with open(path, "a") as f:
            f.write(lines)
    print(lines, end="")


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("plan")
    p.add_argument("--event", required=True)
    p.add_argument("--labels", default="")
    p.add_argument("--ref", default="")
    p.add_argument("--base", default="")
    p.add_argument("--head", default="")
    g = sub.add_parser("gate")
    g.add_argument("--plan-result", required=True)
    g.add_argument("--run", default="")
    g.add_argument("--build-result", required=True)
    args = ap.parse_args(argv)
    if args.cmd == "plan":
        files = None if args.event in ("schedule", "workflow_dispatch") else changed_files(args.base, args.head)
        result = plan(args.event, parse_labels(args.labels), files, args.ref)
        print("changed files: %s" % ("unknown" if files is None else len(files)))
        write_outputs({"run": "true" if result["run"] else "false", "tier": result["tier"],
                       "matrix": json.dumps(result["matrix"], separators=(",", ":"))})
        return 0
    ok = gate_ok(args.plan_result, args.run, args.build_result)
    print("plan=%s run=%s build=%s -> %s" % (args.plan_result, args.run, args.build_result, "ok" if ok else "FAILED"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
