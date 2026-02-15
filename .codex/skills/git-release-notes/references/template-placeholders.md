# Template Placeholders

Use `{{PLACEHOLDER_NAME}}` syntax in your markdown template.

Note: by default, commits for version bump, doc-structure adjustment, doc-only `dev-docs/` add/move changes, and non-functional updates (docs/readme/formatting/tooling-only) are filtered out before placeholder rendering.

The default skill output keeps only `Features`, `Fixes`, and `Breaking Changes`, and uses `English / 中文` order.

## Metadata

- `{{TITLE}}`: Release title.
- `{{DATE}}`: Release date.
- `{{VERSION}}`: Version string.
- `{{RANGE}}`: Git range used to generate notes.
- `{{COMMIT_COUNT}}`: Number of commits found.
- `{{AUTHORS}}`: Comma-separated author list.
- `{{RULES}}`: Raw text loaded from `--rules-file`.

## Grouped Sections

- `{{FEATURES}}`: Conventional commits with `feat:`.
- `{{FIXES}}`: Conventional commits with `fix:`.
- `{{PERF}}`: Conventional commits with `perf:`.
- `{{REFACTOR}}`: Conventional commits with `refactor:`.
- `{{DOCS}}`: Conventional commits with `docs:`.
- `{{TESTS}}`: Conventional commits with `test:`.
- `{{BUILD}}`: Conventional commits with `build:`.
- `{{CI}}`: Conventional commits with `ci:`.
- `{{CHORE}}`: Conventional commits with `chore:`.
- `{{STYLE}}`: Conventional commits with `style:`.
- `{{REVERT}}`: Conventional commits with `revert:`.
- `{{BREAKING_CHANGES}}`: Commits marked with `!` or `BREAKING CHANGE`.
- `{{OTHERS}}`: Non-conventional or unmatched commits.

## Full Logs

- `{{COMMITS_BULLETS}}`: Bullet list of all commit subjects with short hash.
- `{{COMMITS_RAW}}`: Raw commit subjects/bodies.

## Example Template

```md
# {{TITLE}}

Release date / 发布日期: {{DATE}}
Version / 版本: {{VERSION}}
Commit range / 提交范围: `{{RANGE}}`
Total commits / 提交数: {{COMMIT_COUNT}}

## Features / 功能
<!-- Bullet format: - English summary / 中文摘要 -->
{{FEATURES}}

## Fixes / 修复
<!-- Bullet format: - English summary / 中文摘要 -->
{{FIXES}}

## Breaking Changes / 破坏性变更
<!-- Bullet format: - English summary / 中文摘要 -->
{{BREAKING_CHANGES}}
```
