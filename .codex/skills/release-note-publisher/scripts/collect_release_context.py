#!/usr/bin/env python3

import argparse
import json
import subprocess
import sys


def run_command(command):
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "command failed")
    return result.stdout


def build_parser():
    parser = argparse.ArgumentParser(
        description="Collect the latest published GitHub release baseline and local commits since that release."
    )
    parser.add_argument("--repo", required=True, help="GitHub repository in owner/name form.")
    parser.add_argument("--branch", default="release", help="Local branch to inspect. Default: release.")
    parser.add_argument(
        "--include-prerelease",
        action="store_true",
        help="Allow prereleases when selecting the latest published release.",
    )
    return parser


def load_latest_release(repo, include_prerelease):
    response = run_command(["gh", "api", f"repos/{repo}/releases?per_page=20"])
    releases = json.loads(response)

    for release in releases:
        if release.get("draft"):
            continue
        if release.get("prerelease") and not include_prerelease:
            continue
        return release

    raise RuntimeError("no published release found")


def resolve_commit_sha(repo, ref_name):
    response = run_command(["gh", "api", f"repos/{repo}/commits/{ref_name}"])
    payload = json.loads(response)
    return payload["sha"]


def load_branch_head(branch):
    return run_command(["git", "rev-parse", branch]).strip()


def load_commits(baseline_sha, branch):
    format_string = "%H%x1f%s%x1f%b%x1e"
    response = run_command(
        ["git", "log", "--reverse", f"--format={format_string}", f"{baseline_sha}..{branch}"]
    )

    commits = []
    for chunk in response.split("\x1e"):
        chunk = chunk.strip()
        if not chunk:
            continue
        sha, subject, body = chunk.split("\x1f", 2)
        commits.append(
            {
                "sha": sha.strip(),
                "subject": subject.strip(),
                "body": body.strip(),
            }
        )
    return commits


def main():
    parser = build_parser()
    args = parser.parse_args()

    try:
        latest_release = load_latest_release(args.repo, args.include_prerelease)
        baseline_tag = latest_release["tag_name"]
        baseline_sha = resolve_commit_sha(args.repo, baseline_tag)
        head_sha = load_branch_head(args.branch)
        commits = load_commits(baseline_sha, args.branch)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    payload = {
        "repo": args.repo,
        "branch": args.branch,
        "latest_release": {
            "tag": baseline_tag,
            "title": latest_release.get("name") or baseline_tag,
            "published_at": latest_release.get("published_at"),
            "commit_sha": baseline_sha,
            "is_prerelease": bool(latest_release.get("prerelease")),
        },
        "head": {
            "branch": args.branch,
            "commit_sha": head_sha,
        },
        "range": f"{baseline_sha}..{args.branch}",
        "commit_count": len(commits),
        "commits": commits,
    }

    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
