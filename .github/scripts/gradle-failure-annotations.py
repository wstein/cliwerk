#!/usr/bin/env python3
"""Makes a failed Gradle run diagnosable without access to the job log.

Usage: gradle-failure-annotations.py <gradle.log> [<title>] [<tail lines>]

Prints GitHub Actions workflow commands, so the error is readable as check run annotations
(GET /repos/{owner}/{repo}/check-runs/{id}/annotations):
- one ::error with Gradle's "What went wrong" section (and the first "Caused by" lines),
- the last <tail lines> (default 80) lines of the log as ::error annotations of 20 lines each
  (GitHub keeps at most 10 error annotations per step).
Also appends both to $GITHUB_STEP_SUMMARY when it is set. Always exits 0.
"""
import os
import re
import sys

CHUNK = 20


def escape_data(text):
    # https://docs.github.com/en/actions/reference/workflow-commands-for-github-actions
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text):
    return escape_data(text).replace(":", "%3A").replace(",", "%2C")


def what_went_wrong(lines):
    block, inside = [], False
    for line in lines:
        if line.startswith("* What went wrong:"):
            inside = True
            continue
        if inside and line.startswith("* ") and block:
            inside = False
        if inside:
            block.append(line)
    causes = [l.strip() for l in lines if re.match(r"\s*Caused by: ", l)]
    return [l for l in block if l.strip()], causes[:15]


def main(log_path, title="gradle", tail=80):
    try:
        with open(log_path, encoding="utf-8", errors="replace") as f:
            lines = [l.rstrip("\n") for l in f]
    except OSError as e:
        print("::error title=%s::cannot read %s: %s" % (escape_property(title), log_path, escape_data(str(e))))
        return 0
    wrong, causes = what_went_wrong(lines)
    tail_lines = lines[-int(tail):]
    if wrong or causes:
        text = "\n".join(wrong + ([""] + causes if causes else []))
        print("::error title=%s (what went wrong)::%s" % (escape_property(title), escape_data(text)))
    chunks = [tail_lines[i:i + CHUNK] for i in range(0, len(tail_lines), CHUNK)]
    for n, chunk in enumerate(chunks, 1):
        print("::error title=%s (log tail %d of %d)::%s" % (
            escape_property(title), n, len(chunks), escape_data("\n".join(chunk))))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("## %s failed\n\n" % title)
            if wrong or causes:
                f.write("### What went wrong\n\n```\n%s\n```\n\n" % "\n".join(wrong + causes))
            f.write("<details><summary>Last %d lines of the Gradle log</summary>\n\n```\n%s\n```\n\n</details>\n"
                    % (len(tail_lines), "\n".join(tail_lines)))
    return 0


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    sys.exit(main(args[0], *(args[1:2] or ["gradle"]), *(args[2:3] or [80])))
