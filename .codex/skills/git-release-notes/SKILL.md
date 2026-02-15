---
name: git-release-notes
description: Generate release notes directly from a specified git commit range and user-provided template rules. Use when users ask to create changelogs/release notes for GitHub Releases from commits, tags, or branches, especially when they provide a formatting template, required sections, or publishing constraints.
---

# Git Release Notes

Generate copy-ready release notes from commit messages in a git range.

## Output Standard (Required)

1. Bilingual order must always be `English / 中文`.
2. Only keep these note sections:
- `Features / 功能`
- `Fixes / 修复`
- `Breaking Changes / 破坏性变更`
3. Exclude non-functional changes from release notes by default, including docs/doc-structure/readme/formatting/tooling-only updates.
4. Output file must be written under `dev-docs/release-notes/` unless the user explicitly asks for a different path.

## Workflow

1. Collect required inputs:
- Commit range (for example `v1.2.0..v1.3.0` or `HEAD~20..HEAD`).
- Optional metadata: version, title, date.

2. Use the fixed bilingual template:
- `.codex/skills/git-release-notes/assets/release-note-template-bilingual.md`

3. Build notes with the script:

```bash
python3 .codex/skills/git-release-notes/scripts/render_release_notes.py \
  --range "v1.2.0..HEAD" \
  --template-file .codex/skills/git-release-notes/assets/release-note-template-bilingual.md \
  --version "v1.3.0" \
  --output "dev-docs/release-notes/release-notes-$(date +%F).md"
```

4. Post-process the generated list items into bilingual bullets in strict `English / 中文` order.

5. Return final markdown that can be pasted into GitHub Release body, and include the output file path.

## Default Exclusions (Enabled)

By default, the script excludes these commits from release notes:

1. Version bump commits (for example `Bump version to ...`).
2. Documentation-structure adjustment commits (matched by subject keywords).
3. Doc-only commits that add/move files under `dev-docs/`.
4. Non-functional commits (docs/readme/formatting/tooling-only changes).
5. Commits that are not `Features`, `Fixes`, or `Breaking Changes`.

Use flags to include them when needed:

```bash
--include-version-bump-commits
--include-doc-structure-commits
--include-dev-docs-add-commits
--include-non-functional-commits
```

## Fast Commands

List available placeholders:

```bash
python3 .codex/skills/git-release-notes/scripts/render_release_notes.py --print-placeholders --range HEAD~1..HEAD
```

Render with the bilingual release-note template:

```bash
python3 .codex/skills/git-release-notes/scripts/render_release_notes.py \
  --range "HEAD~10..HEAD" \
  --template-file .codex/skills/git-release-notes/assets/release-note-template-bilingual.md \
  --output "dev-docs/release-notes/release-notes-$(date +%F).md"
```

## Quality Checks

1. Confirm range direction is correct (`older..newer`).
2. Confirm commit count is non-zero.
3. Confirm template placeholders are replaced (no `{{...}}` remains unless intentional).
4. Confirm only these sections exist: `Features / 功能`, `Fixes / 修复`, `Breaking Changes / 破坏性变更`.
5. Confirm list text order is `English / 中文`.
6. Confirm docs/format/readme/tooling-only commits are excluded by default.
7. Confirm output file is saved under `dev-docs/release-notes/`.
8. Keep wording factual; avoid inventing changes not present in commit messages.

## Resources

- `scripts/render_release_notes.py`: Extract commit messages and render markdown from placeholders.
- `references/template-placeholders.md`: Placeholder definitions and template examples.
- `assets/release-note-template-bilingual.md`: Standard bilingual template required by this skill.
