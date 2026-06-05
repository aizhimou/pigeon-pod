---
name: release-issue-responder
description: Find open GitHub issues that are covered by a specific release note, draft issue replies in the issue author's language, post approved comments with `gh issue comment`, and recommend whether each issue should be closed. Use when Codex needs to turn a shipped release into structured GitHub issue follow-up, especially for PigeonPod release-note-driven maintainer workflows.
---

# Release Issue Responder

## Overview

Use this skill to convert an existing release note into a disciplined issue-response pass. Read the release note, match it against open GitHub issues, draft one reply at a time for user approval, post the approved comment, then suggest whether the issue should be closed immediately or kept open for verification.

## Workflow

### 1. Build the release scope

- Read the target release note file first.
- Treat the release note text as the source of truth for what shipped.
- Extract the user-visible outcomes, not just keywords. Focus on what the user will feel after upgrading.
- If the user already published the release, keep the issue replies aligned with the final published note rather than with commit messages or local implementation details.

### 2. Discover candidate issues

- Use `gh issue list` to fetch open issues from the target repository.
- Keep the candidate set tight. Prefer issues whose request or bug report maps clearly to a release-note item.
- Treat direct feature matches, promised follow-ups from maintainer comments, and symptom-level bug matches as valid signals.
- Do not assume a title match is enough. Always inspect the issue body, and read comments when the scope or language may have shifted.

### 3. Verify each candidate before drafting

- Open each candidate issue with comments.
- Identify:
  - The issue opener's language.
  - The actual user need or pain point.
  - Whether later comments narrowed, corrected, or partially invalidated the original report.
- If a release item only partially overlaps with the issue, keep the wording conservative and avoid claiming full resolution.
- If the issue is too ambiguous or only loosely related, skip it rather than forcing a reply.

### 4. Draft the reply

- Draft the reply in the issue opener's language.
- Keep the tone friendly, concise, and trustworthy.
- Do not bluntly say "this issue is fixed" or "this request is implemented" unless the match is exact and the user has asked for direct wording.
- Instead, connect the release note to the user-visible effect. Explain what should now work better or what new flow is now available.
- Invite the user to upgrade to the latest version and try it.
- Welcome follow-up feedback if the issue persists.
- If the issue has relevant follow-up comments, acknowledge them in the reply when useful.

### 5. Approval gate

- Present one drafted reply to the user at a time.
- Do not post the comment until the user explicitly approves that specific wording.
- After approval, post it with `gh issue comment`.
- Then move to the close recommendation for that same issue before continuing to the next one.

### 6. Recommend whether to close the issue

- After posting the approved reply, tell the user whether the issue should be closed now or kept open.
- Provide a short reason tied to certainty:
  - Close now when the released feature directly satisfies the request, or when the shipped fix clearly covers the reported problem.
  - Keep open when the release improves the area but the original report is broader, environment-specific, or still needs user verification.
- Wait for the user's decision.
- If the user agrees to close, immediately run `gh issue close` before moving on to the next issue.
- If the user wants it left open, record that and continue.

## Reply Rules

- Mirror the issue opener's language. If the opener used Chinese, reply in Chinese. If the opener used English, reply in English.
- Prefer the opener's language over later commenters' language.
- Use the release note wording as evidence, but rewrite it into user-facing language.
- Mention the latest version explicitly when useful, for example `v1.24.0`.
- Avoid over-claiming. If the shipped change is adjacent rather than identical, say it should improve or cover the workflow rather than declaring a guaranteed fix.
- Do not paste large chunks of the release note verbatim.
- Keep the reply compact. Usually 2-3 short paragraphs are enough.

## Closing Heuristics

Close immediately when:
- The release adds the exact requested feature.
- The release note explicitly names the reported bug or symptom.
- The issue is a feature request and the shipped behavior clearly delivers the intended outcome.

Keep open when:
- The fix is related but not exact.
- The issue includes multiple possible root causes.
- Compatibility with third-party clients or environments still needs confirmation.
- The user specifically needs to verify their setup after upgrading.

## Suggested Command Pattern

- Read issues:
  - `gh issue list --repo <owner/repo> --state open --limit <n>`
  - `gh issue view <number> --repo <owner/repo> --json number,title,body,author`
  - `gh issue view <number> --repo <owner/repo> --comments`
- Post approved reply:
  - `gh issue comment <number> --repo <owner/repo> --body '<approved text>'`
- Close after user approval:
  - `gh issue close <number> --repo <owner/repo>`

## Output Pattern

For each issue, follow this loop:

1. State why the issue matches the release note.
2. Show the draft reply only.
3. Wait for user approval.
4. Post the approved comment.
5. Recommend close now or keep open, with one-sentence reasoning.
6. If the user agrees, close it immediately.
7. Move to the next issue.

At the end, summarize:
- Which issues were replied to.
- Which issues were closed.
- Which issues remain open for verification.
