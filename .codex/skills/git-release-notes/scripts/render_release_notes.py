#!/usr/bin/env python3
"""Render release notes from a git commit range and a markdown template."""

from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
from pathlib import Path

DEFAULT_TEMPLATE = """# {{TITLE}}

Release date / 发布日期: {{DATE}}
Version / 版本: {{VERSION}}
Commit range / 提交范围: `{{RANGE}}`
Total commits / 提交数: {{COMMIT_COUNT}}

## Features / 功能
{{FEATURES}}

## Fixes / 修复
{{FIXES}}

## Breaking Changes / 破坏性变更
{{BREAKING_CHANGES}}
"""

PLACEHOLDERS = [
    "TITLE",
    "DATE",
    "VERSION",
    "RANGE",
    "COMMIT_COUNT",
    "AUTHORS",
    "RULES",
    "FEATURES",
    "FIXES",
    "PERF",
    "REFACTOR",
    "DOCS",
    "TESTS",
    "BUILD",
    "CI",
    "CHORE",
    "STYLE",
    "REVERT",
    "BREAKING_CHANGES",
    "OTHERS",
    "COMMITS_BULLETS",
    "COMMITS_RAW",
]

TYPE_LABELS = {
    "feat": "FEATURES",
    "fix": "FIXES",
    "perf": "PERF",
    "refactor": "REFACTOR",
    "docs": "DOCS",
    "test": "TESTS",
    "build": "BUILD",
    "ci": "CI",
    "chore": "CHORE",
    "style": "STYLE",
    "revert": "REVERT",
}

CONVENTIONAL_RE = re.compile(r"^(?P<type>[a-zA-Z]+)(\([^)]*\))?(?P<breaking>!)?:\s*(?P<summary>.+)$")
BRACKET_RE = re.compile(r"^\[(?P<type>[a-zA-Z]+)]\s*(?P<summary>.+)$")
VERSION_BUMP_RE = re.compile(r"\bbump(?:ing)?\s+version\b|\bversion\s+bump\b", re.IGNORECASE)
DOC_STRUCTURE_KEYWORDS = (
    "doc structure",
    "document structure",
    "documentation structure",
    "restructure docs",
    "docs restructure",
    "文档结构",
    "文档整理",
    "文档重构",
)

NON_FUNCTIONAL_SUBJECT_RE = re.compile(
    r"(\b(readme|docs?|documentation|changelog|release notes?|prettier|architecture)\b|"
    r"openai\.ya?ml|文档|架构文档)",
    re.IGNORECASE,
)

DOC_TOOLING_PREFIXES = (
    "dev-docs/",
    "documents/",
    ".codex/",
    ".github/",
    "docs/",
    "doc/",
)
DOC_TOOLING_EXACT = {"readme.md", "agents.md", "license"}
DOC_TOOLING_SUFFIXES = (".md", ".adoc", ".rst", ".txt")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate release notes from a git commit range")
    parser.add_argument("--range", dest="range_spec", required=True, help="Git commit range, e.g. v1.2.0..HEAD")
    parser.add_argument("--repo", default=".", help="Path to git repository")
    parser.add_argument("--template-file", help="Markdown template with placeholders like {{FEATURES}}")
    parser.add_argument("--rules-file", help="Text file containing template rules or extra constraints")
    parser.add_argument("--title", default="Release Notes / 发布说明", help="Title for {{TITLE}}")
    parser.add_argument("--version", default="", help="Version for {{VERSION}}")
    parser.add_argument("--date", default=dt.date.today().isoformat(), help="Date for {{DATE}} in YYYY-MM-DD")
    parser.add_argument("--output", help="Output file path (defaults to stdout)")
    parser.add_argument("--include-merges", action="store_true", help="Include merge commits")
    parser.add_argument(
        "--include-version-bump-commits",
        action="store_true",
        help="Do not filter version bump commits",
    )
    parser.add_argument(
        "--include-doc-structure-commits",
        action="store_true",
        help="Do not filter doc-structure adjustment commits",
    )
    parser.add_argument(
        "--include-dev-docs-add-commits",
        action="store_true",
        help="Do not filter doc-only commits that add/move files under dev-docs/",
    )
    parser.add_argument(
        "--include-non-functional-commits",
        action="store_true",
        help="Do not filter non-functional commits (docs/readme/formatting/tooling-only changes)",
    )
    parser.add_argument("--empty-value", default="- None / 无", help="Fallback text for empty sections")
    parser.add_argument("--print-placeholders", action="store_true", help="Print supported placeholders and exit")
    return parser.parse_args()


def run_git(repo: Path, git_args: list[str]) -> str:
    cmd = ["git", "-C", str(repo), *git_args]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "git command failed")
    return result.stdout


def read_template(template_file: str | None) -> str:
    if not template_file:
        return DEFAULT_TEMPLATE
    return Path(template_file).read_text(encoding="utf-8")


def read_rules(rules_file: str | None) -> str:
    if not rules_file:
        return ""
    return Path(rules_file).read_text(encoding="utf-8").strip()


def collect_commits(repo: Path, range_spec: str, include_merges: bool) -> list[dict[str, str]]:
    args = ["log", range_spec, "--pretty=format:%H%x1f%an%x1f%s%x1f%b%x1e"]
    if not include_merges:
        args.insert(1, "--no-merges")

    raw = run_git(repo, args)
    commits: list[dict[str, str]] = []

    for record in raw.split("\x1e"):
        record = record.strip("\n\r")
        if not record:
            continue
        fields = record.split("\x1f", 3)
        if len(fields) < 3:
            continue
        commit_hash = fields[0].strip()
        author = fields[1].strip()
        subject = fields[2].strip()
        body = fields[3].strip() if len(fields) > 3 else ""
        commits.append(
            {
                "hash": commit_hash,
                "short_hash": commit_hash[:7],
                "author": author,
                "subject": subject,
                "body": body,
            }
        )

    return commits


def get_commit_file_changes(repo: Path, commit_hash: str) -> list[tuple[str, str]]:
    raw = run_git(repo, ["show", "--name-status", "--pretty=format:", commit_hash])
    changes: list[tuple[str, str]] = []
    for line in raw.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        parts = stripped.split("\t")
        if len(parts) < 2:
            continue
        status = parts[0].strip()
        path = parts[-1].strip()
        changes.append((status, path))
    return changes


def should_exclude_version_bump(subject: str) -> bool:
    subject_lower = subject.lower()
    if VERSION_BUMP_RE.search(subject_lower):
        return True
    if re.search(r"\brelease\b.*\bv?\d+\.\d+(\.\d+)?\b", subject_lower):
        return True
    return False


def should_exclude_doc_structure(subject: str) -> bool:
    subject_lower = subject.lower()
    return any(keyword in subject_lower for keyword in DOC_STRUCTURE_KEYWORDS)


def should_exclude_dev_docs_add(changes: list[tuple[str, str]]) -> bool:
    if not changes:
        return False
    has_dev_docs_add_or_move = any(
        path.startswith("dev-docs/") and status[:1] in {"A", "R", "C"}
        for status, path in changes
    )
    non_doc_paths = [
        path
        for _, path in changes
        if not path.startswith("dev-docs/")
        and not path.startswith("documents/")
        and not path.startswith(".codex/")
    ]
    return has_dev_docs_add_or_move and not non_doc_paths


def is_doc_or_tooling_path(path: str) -> bool:
    normalized = path.strip().lower()
    if not normalized:
        return False
    if normalized.startswith(DOC_TOOLING_PREFIXES):
        return True
    if normalized in DOC_TOOLING_EXACT:
        return True
    return normalized.endswith(DOC_TOOLING_SUFFIXES)


def should_exclude_non_functional(subject: str, changes: list[tuple[str, str]]) -> bool:
    if NON_FUNCTIONAL_SUBJECT_RE.search(subject):
        return True
    if not changes:
        return False
    return all(is_doc_or_tooling_path(path) for _, path in changes)


def filter_commits(
    repo: Path,
    commits: list[dict[str, str]],
    include_version_bump_commits: bool,
    include_doc_structure_commits: bool,
    include_dev_docs_add_commits: bool,
    include_non_functional_commits: bool,
) -> list[dict[str, str]]:
    kept: list[dict[str, str]] = []
    for commit in commits:
        subject = commit["subject"]
        if not include_version_bump_commits and should_exclude_version_bump(subject):
            continue
        if not include_doc_structure_commits and should_exclude_doc_structure(subject):
            continue

        changes = get_commit_file_changes(repo, commit["hash"])
        if not include_dev_docs_add_commits and should_exclude_dev_docs_add(changes):
            continue
        if not include_non_functional_commits and should_exclude_non_functional(subject, changes):
            continue

        category, is_breaking = categorize_commit(commit)
        if not is_breaking and category not in {"FEATURES", "FIXES"}:
            continue

        kept.append(commit)
    return kept


def categorize_commit(commit: dict[str, str]) -> tuple[str, bool]:
    subject = commit["subject"]
    body_upper = commit["body"].upper()
    match = CONVENTIONAL_RE.match(subject)

    if match:
        commit_type = match.group("type").lower()
        is_breaking = bool(match.group("breaking")) or "BREAKING CHANGE" in body_upper
        return TYPE_LABELS.get(commit_type, "OTHERS"), is_breaking

    bracket_match = BRACKET_RE.match(subject)
    if bracket_match:
        commit_type = bracket_match.group("type").lower()
        is_breaking = "BREAKING CHANGE" in body_upper
        return TYPE_LABELS.get(commit_type, "OTHERS"), is_breaking

    is_breaking = "BREAKING CHANGE" in body_upper
    return "OTHERS", is_breaking


def to_bullets(lines: list[str], empty_value: str) -> str:
    if not lines:
        return empty_value
    return "\n".join(f"- {line}" for line in lines)


def build_placeholder_values(
    commits: list[dict[str, str]],
    range_spec: str,
    title: str,
    version: str,
    date: str,
    rules: str,
    empty_value: str,
) -> dict[str, str]:
    sections: dict[str, list[str]] = {key: [] for key in TYPE_LABELS.values()}
    sections["OTHERS"] = []
    breaking_changes: list[str] = []
    commit_bullets: list[str] = []
    raw_messages: list[str] = []

    for commit in commits:
        line = f"{commit['subject']} ({commit['short_hash']})"
        commit_bullets.append(line)
        raw_messages.append(commit["subject"])
        if commit["body"]:
            raw_messages.append(commit["body"])

        category, is_breaking = categorize_commit(commit)
        sections.setdefault(category, []).append(line)
        if is_breaking:
            breaking_changes.append(line)

    authors = sorted({commit["author"] for commit in commits if commit["author"]})

    values = {
        "TITLE": title,
        "DATE": date,
        "VERSION": version,
        "RANGE": range_spec,
        "COMMIT_COUNT": str(len(commits)),
        "AUTHORS": ", ".join(authors) if authors else empty_value,
        "RULES": rules or empty_value,
        "COMMITS_BULLETS": to_bullets(commit_bullets, empty_value),
        "COMMITS_RAW": "\n\n".join(raw_messages) if raw_messages else empty_value,
        "BREAKING_CHANGES": to_bullets(breaking_changes, empty_value),
        "OTHERS": to_bullets(sections.get("OTHERS", []), empty_value),
    }

    for placeholder in TYPE_LABELS.values():
        values[placeholder] = to_bullets(sections.get(placeholder, []), empty_value)

    return values


def render_template(template: str, values: dict[str, str]) -> str:
    output = template
    for key, value in values.items():
        output = output.replace(f"{{{{{key}}}}}", value)
    return output


def write_output(content: str, output_file: str | None) -> None:
    if not output_file:
        print(content)
        return
    Path(output_file).write_text(content, encoding="utf-8")


def main() -> int:
    args = parse_args()

    if args.print_placeholders:
        print("\n".join(PLACEHOLDERS))
        return 0

    repo = Path(args.repo).resolve()

    try:
        template = read_template(args.template_file)
        rules = read_rules(args.rules_file)
        commits = collect_commits(repo, args.range_spec, args.include_merges)
        commits = filter_commits(
            repo,
            commits,
            args.include_version_bump_commits,
            args.include_doc_structure_commits,
            args.include_dev_docs_add_commits,
            args.include_non_functional_commits,
        )
        values = build_placeholder_values(
            commits,
            args.range_spec,
            args.title,
            args.version,
            args.date,
            rules,
            args.empty_value,
        )
        rendered = render_template(template, values)
        write_output(rendered, args.output)
        return 0
    except Exception as exc:  # pragma: no cover - script-level error handling
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
