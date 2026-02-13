---
name: git-release-notes
description: Generate release notes directly from a specified git commit range and user-provided template rules. Use when users ask to create changelogs/release notes for GitHub Releases from commits, tags, or branches, especially when they provide a formatting template, required sections, or publishing constraints.
---

# Git Release Notes

Generate copy-ready release notes from commit messages in a git range.

## Workflow

1. Collect required inputs:
- Commit range (for example `v1.2.0..v1.3.0` or `HEAD~20..HEAD`).
- Template rules (strict markdown template preferred).
- Optional metadata: version, title, date.

2. Build notes with the script:

```bash
python3 .codex/skills/git-release-notes/scripts/render_release_notes.py \
  --range "v1.2.0..HEAD" \
  --template-file /tmp/release-template.md \
  --rules-file /tmp/release-rules.txt \
  --version "v1.3.0" \
  --title "PigeonPod v1.3.0" \
  --output /tmp/release-notes.md
```

3. If the user only gives natural-language rules, convert those rules into a template that uses supported placeholders from `references/template-placeholders.md`, then run the script.

4. Return final markdown that can be pasted into GitHub Release body.

## Default Exclusions

By default, the script excludes these commits from release notes:

1. Version bump commits (for example `Bump version to ...`).
2. Documentation-structure adjustment commits (matched by subject keywords).
3. Doc-only commits that add/move files under `dev-docs/`.

Use flags to include them when needed:

```bash
--include-version-bump-commits
--include-doc-structure-commits
--include-dev-docs-add-commits
```

## Fast Commands

List available placeholders:

```bash
python3 .codex/skills/git-release-notes/scripts/render_release_notes.py --print-placeholders --range HEAD~1..HEAD
```

Render with built-in default template:

```bash
python3 .codex/skills/git-release-notes/scripts/render_release_notes.py --range "HEAD~10..HEAD"
```

## Quality Checks

1. Confirm range direction is correct (`older..newer`).
2. Confirm commit count is non-zero.
3. Confirm template placeholders are replaced (no `{{...}}` remains unless intentional).
4. Confirm filtered commits are expected (version bump/docs structure/dev-docs additions are excluded by default).
5. Keep wording factual; avoid inventing changes not present in commit messages.

## Resources

- `scripts/render_release_notes.py`: Extract commit messages and render markdown from placeholders.
- `references/template-placeholders.md`: Placeholder definitions and template examples.
