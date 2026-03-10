---
name: release-note-publisher
description: Draft, refine, and publish bilingual PigeonPod GitHub release notes from commits on the `release` branch. Use when Codex needs to compare commits since the latest published GitHub release, write a new local release note under `dev-docs/release-notes`, align English and Chinese release-note sections after user edits, or create/update a GitHub Release while keeping the markdown H1 as the GitHub release title instead of the release body.
---

# Release Note Publisher

## Overview

Use this skill to turn `release` branch commit history into a user-facing bilingual release note, iterate with the user on the local markdown draft, and publish the final version to GitHub without leaking the local H1 title into the GitHub release body.

Read [references/release-style.md](references/release-style.md) before drafting or polishing copy.

## Workflow

### 1. Determine the release target

- Prefer an explicit version from the user.
- If the user does not give one, infer it only from an existing local draft file, an existing tag, or an existing GitHub release.
- Do not invent a version number.
- Save the draft under `dev-docs/release-notes/v<version>_<YYYY-MM-DD>.md`.

### 2. Collect the baseline and commit range

- Run:

```bash
python3 .codex/skills/release-note-publisher/scripts/collect_release_context.py --repo <owner/repo> --branch release
```

- Use the latest published GitHub release as the baseline.
- Let the script resolve the latest release tag to a commit SHA, then inspect commits from that commit to the latest local `release` branch commit.
- If the local `release` branch is stale or missing tags, fetch `origin/release` and tags before drafting.
- Treat the script output as evidence, not final copy.

### 3. Draft the local release note

- Follow [references/release-style.md](references/release-style.md).
- Use only user-visible changes, fixes, and behavior changes in the final note.
- Merge related commit messages into one concise bullet when they represent the same outcome.
- Omit `Breaking Changes` unless the user explicitly asks for it.
- Omit issue numbers, PR numbers, commit hashes, and raw internal wording.

### 4. Run the review loop

- Show the local draft to the user before publishing.
- Expect the user to edit the file directly.
- If the user edits only the Chinese section, treat the final Chinese version as the source of truth.
- Rewrite the English section to match the confirmed Chinese content and tone.
- Do one final pass to keep English and Chinese aligned in scope, ordering, and emphasis.

### 5. Publish to GitHub

- Keep the first H1 in the local markdown file.
- Do not include that H1 in the GitHub release body.
- Use the H1 text as the GitHub release title.
- Run a dry run first when needed:

```bash
python3 .codex/skills/release-note-publisher/scripts/publish_release.py --repo <owner/repo> --notes-file <path> --tag <tag> --target release --dry-run
```

- After the user confirms, run the same command without `--dry-run`.
- In `auto` mode, the publish script edits an existing release for the tag or creates a new release if the tag does not exist yet.

## Resources

### `scripts/collect_release_context.py`

Resolve the latest published GitHub release, its tag, its release commit SHA, and the local commit log from that baseline to the target branch.

### `scripts/publish_release.py`

Parse the local markdown note, strip the first H1 from the body, and create or update the GitHub release with `gh`.

### `references/release-style.md`

Use this file for format rules, bilingual alignment rules, and PigeonPod-specific copywriting guidance.
