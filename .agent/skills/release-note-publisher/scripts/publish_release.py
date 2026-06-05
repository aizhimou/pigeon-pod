#!/usr/bin/env python3

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path


TITLE_PATTERN = re.compile(r"^#\s+(.+?)\s*$")
TAG_FROM_TITLE_PATTERN = re.compile(r"^release\s+(.+)$", re.IGNORECASE)


def run_command(command, check=True):
    result = subprocess.run(command, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "command failed")
    return result


def parse_note_file(path):
    content = Path(path).read_text(encoding="utf-8")
    lines = content.splitlines()

    title = None
    body_start = None
    for index, line in enumerate(lines):
        if not line.strip():
            continue
        match = TITLE_PATTERN.match(line)
        if not match:
            raise RuntimeError("release note must start with an H1 title like '# release 1.24.0'")
        title = match.group(1).strip()
        body_start = index + 1
        break

    if title is None:
        raise RuntimeError("release note file is empty")

    body = "\n".join(lines[body_start:]).lstrip("\n").rstrip() + "\n"
    if not body.strip():
        raise RuntimeError("release note body is empty after removing the H1 title")

    return title, body


def infer_tag(title, explicit_tag):
    if explicit_tag:
        return explicit_tag

    match = TAG_FROM_TITLE_PATTERN.match(title)
    if match:
        return match.group(1).strip()

    raise RuntimeError("cannot infer tag from title; pass --tag explicitly")


def detect_mode(repo, tag, requested_mode):
    if requested_mode != "auto":
        return requested_mode

    result = run_command(["gh", "release", "view", tag, "--repo", repo], check=False)
    return "edit" if result.returncode == 0 else "create"


def build_command(repo, tag, title, body_path, mode, target):
    if mode == "edit":
        return ["gh", "release", "edit", tag, "--repo", repo, "--title", title, "--notes-file", body_path]

    command = ["gh", "release", "create", tag, "--repo", repo, "--title", title, "--notes-file", body_path]
    if target:
        command.extend(["--target", target])
    return command


def build_parser():
    parser = argparse.ArgumentParser(
        description="Create or update a GitHub release from a local markdown note."
    )
    parser.add_argument("--repo", required=True, help="GitHub repository in owner/name form.")
    parser.add_argument("--notes-file", required=True, help="Local markdown release note path.")
    parser.add_argument("--tag", help="Release tag. If omitted, infer it from the H1 title.")
    parser.add_argument(
        "--mode",
        choices=["auto", "create", "edit"],
        default="auto",
        help="Publish mode. Default: auto.",
    )
    parser.add_argument("--target", help="Target branch or commit for create mode.")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the parsed title, body, and gh command without calling GitHub.",
    )
    return parser


def main():
    parser = build_parser()
    args = parser.parse_args()

    try:
        title, body = parse_note_file(args.notes_file)
        tag = infer_tag(title, args.tag)
        mode = detect_mode(args.repo, tag, args.mode)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    if mode == "auto":
        mode = "create"

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as handle:
        handle.write(body)
        body_path = handle.name

    command = build_command(args.repo, tag, title, body_path, mode, args.target)

    try:
        if args.dry_run:
            payload = {
                "mode": mode,
                "repo": args.repo,
                "tag": tag,
                "title": title,
                "body": body,
                "command": command,
            }
            print(json.dumps(payload, ensure_ascii=False, indent=2))
            return 0

        result = run_command(command)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    finally:
        try:
            os.unlink(body_path)
        except OSError:
            pass

    if result.stdout.strip():
        print(result.stdout.strip())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
