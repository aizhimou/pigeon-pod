# Template Placeholders

Use `{{PLACEHOLDER_NAME}}` syntax in your markdown template.

Note: by default, commits for version bump, doc-structure adjustment, and doc-only `dev-docs/` add/move changes are filtered out before placeholder rendering.

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

> Version: {{VERSION}}
> Date: {{DATE}}
> Range: {{RANGE}}

## Highlights
{{FEATURES}}

## Fixes
{{FIXES}}

## Breaking Changes
{{BREAKING_CHANGES}}

## Full Changelog
{{COMMITS_BULLETS}}
```
