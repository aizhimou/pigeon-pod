# PigeonPod Release Note Style

## Format Contract

- Save local files under `dev-docs/release-notes/v<version>_<YYYY-MM-DD>.md`.
- Keep the first line as `# release <version>` in the local markdown file.
- Do not place that H1 inside the GitHub release body. Use it as the GitHub release title.
- Default section structure:

```md
# release 1.24.0

## Changelog

### Features
- ...

### Fixes
- ...


## 更新日志

### 新增功能
1. ...

### 修复问题
1. ...
```

- Do not add `Breaking Changes` or `### 破坏性变更` unless the user explicitly asks for them.
- Do not include issue numbers, PR numbers, or commit hashes in the final note.

## Writing Style

- Write for end users, not for maintainers.
- Prefer user-visible outcomes over implementation details.
- Keep sentences short, direct, and specific.
- Merge low-level commits into a smaller number of product-level bullets.
- Preserve the actual scope of the release. Do not exaggerate.
- Mention internal refactors only when they fix a user-visible problem or remove an obvious workflow limitation.

## Bilingual Alignment

- Draft both English and Chinese sections with the same meaning and ordering.
- If the user edits only the Chinese section, treat the final Chinese version as the source of truth.
- Rewrite the English section to match the confirmed Chinese scope, tone, and emphasis.
- Polish both languages after each review pass so the final document reads as one coherent release note, not a literal translation.

## Commit Interpretation Rules

- Start from the latest published GitHub release tag.
- Read commit messages from that release commit up to the latest commit on `release`.
- Use commit subjects and bodies as evidence, but do not mirror them literally.
- Ignore noise such as merge commits, chore-only commits, and internal wording unless they clearly imply user-facing changes.
- When multiple commits support the same user-facing change, merge them into one bullet.
