---
name: issue-review-reply
description: Review PigeonPod GitHub issues end to end. Use when the user asks what an issue means, whether it is valid, how to reply, whether to add it to the GitHub Project, or to turn it into a tracked task. Read the issue and comments, inspect relevant local docs and code, explain the real requirement or bug precisely, draft a maintainer reply, and only after explicit approval perform GitHub writes. When creating a task, first recommend `Priority`, `Size`, and `Estimate`, wait for maintainer confirmation or overrides, then write those values into the project task fields.
---

# Issue Review Reply

Use this skill for PigeonPod issue handling that mixes technical review with GitHub execution.

Keep analysis and execution separate. Do not post comments, add project items, or create tasks until the maintainer explicitly approves the action.

## When To Use

Use this skill when the user asks to:

- explain what a GitHub issue really means
- verify whether an issue is real against the current codebase
- draft or post an issue reply
- add an issue to the `pigeon-pod` GitHub Project
- create a tracked project task from an issue or maintainer decision

If the task is mainly a deep bug investigation, use `bug-analysis` for the analysis step. If it is mainly a requirement or feature evaluation, use `requirements-analysis` for the analysis step. This skill still owns the final issue reply and GitHub project execution.

## Workflow

### 1. Read the issue first

- Fetch the issue with comments:
  - `gh issue view <number> --json number,title,body,state,author,labels,url,comments`
- Identify:
  - the issue opener's language
  - the concrete user pain or requested outcome
  - whether later comments narrowed or changed the scope

Do not infer the meaning from the title alone.

### 2. Build the minimum local context

Read only the minimum repo context needed to judge the issue:

- `README.md`
- `dev-docs/architecture/architecture-design-zh.md`
- relevant backend or frontend files discovered with `rg`

Useful commands:

```bash
rg -n "keyword|module|config|error" backend frontend dev-docs README.md
rg --files backend/src/main/java frontend/src dev-docs
```

Always tie your conclusion back to actual code or documented architecture. Do not answer issue questions from memory alone.

### 3. Explain what the issue really means

Translate the issue into maintainer-facing language:

- what exact scenario the reporter is describing
- what condition must be true for the problem or request to matter
- why the current implementation behaves that way
- whether the thread is best treated as `bug`, `requirement`, or `discussion`

Keep this explanation concrete. The maintainer should understand both the user request and the code reality after one short answer.

### 4. Recommend the next action

Choose one primary recommendation:

- `reply_only`
- `reply_and_add_issue_to_project`
- `reply_and_create_task`
- `need_more_info`
- `no_action`

Default to the smallest honest action. Do not force every issue into a task.

When a valid feature request or bug report is already concrete enough to track directly, prefer `reply_and_add_issue_to_project`.

Use `reply_and_create_task` only when at least one of these is true:

- the original issue is too discussion-oriented to serve as a clean delivery item
- the implementation scope needs to be rewritten more explicitly than the issue text
- one issue needs to be split into one or more separate engineering tasks
- the maintainer explicitly asks for a standalone task instead of tracking the issue itself

### 5. Draft the reply

Reply rules:

- Use the issue opener's language.
- Thank the reporter when appropriate, but keep it concise.
- Do not promise implementation unless the maintainer explicitly tells you to.
- Do not exaggerate certainty if the issue is environment-specific or only partially confirmed.
- Keep the reply compact and direct.

### 6. Approval gates

Before any GitHub write:

- show the maintainer the draft reply
- if a task is proposed, show the draft task title and description
- recommend `Priority`, `Size`, and `Estimate` with one short rationale each
- wait for explicit approval or override

No explicit approval, no write action.

## Task Recommendation Rules

When the maintainer asks to generate a task, recommend these fields before creating it.

### Priority

- `P0`: security risk, data loss, persistent production breakage, or release-blocking regression
- `P1`: meaningful user-facing bug or feature that should land in the next planned cycle
- `P2`: valid backlog work, lower urgency improvement, or research/design-heavy follow-up

### Size

- `XS`: tiny change, usually one small isolated edit, under 0.5 day
- `S`: small scoped change, usually 0.5-2 days
- `M`: moderate cross-file change, usually 2-4 days
- `L`: broader cross-layer change or design-heavy work, usually 4-7 days
- `XL`: multi-release, high-uncertainty, or architecture-heavy work

### Estimate

Use numeric working days because the current project field stores estimates as numbers.

Prefer values like:

- `0.5`
- `1`
- `2`
- `3`
- `5`

Use the smallest credible estimate. If uncertainty is high, say so explicitly and bias upward rather than pretending precision.

### Confirmation rule

For every task proposal:

1. propose task title and body
2. propose `Priority`, `Size`, and `Estimate`
3. wait for maintainer confirmation or overrides
4. only then create the task and write the confirmed field values

If the maintainer overrides only one field, keep the other confirmed recommendations.

## GitHub Project Rules

Current project target for PigeonPod:

- owner: `aizhimou`
- project: `pigeon-pod`
- project number: `4`
- project id: `PVT_kwHOATBRas4BHRuz`

Always re-read fields before editing in case the project changed:

```bash
gh project field-list 4 --owner aizhimou --format json
```

Current field ids and options:

- `Status`: field `PVTSSF_lAHOATBRas4BHRuzzg4FCew`
  - `Backlog`: `f75ad846`
  - `In progress`: `47fc9ee4`
  - `In review`: `df73e18b`
  - `Done`: `98236657`
- `Priority`: field `PVTSSF_lAHOATBRas4BHRuzzg4FCmU`
  - `P0`: `79628723`
  - `P1`: `0a877460`
  - `P2`: `da944a9c`
- `Size`: field `PVTSSF_lAHOATBRas4BHRuzzg4FCmY`
  - `XS`: `6c6483d2`
  - `S`: `f784b110`
  - `M`: `7515a9f1`
  - `L`: `817d0097`
  - `XL`: `db339eb2`
- `Estimate`: field `PVTF_lAHOATBRas4BHRuzzg4FCmc`

## Execution Patterns

### Post an approved reply

```bash
gh issue comment <number> --body "<approved reply>"
```

Then verify the returned comment URL or re-read the issue comments.

### Add the original issue to the project

This is the default project action when the issue itself is already a good tracking item.

```bash
gh project item-add 4 --owner aizhimou --url https://github.com/aizhimou/pigeon-pod/issues/<number>
```

Verify with one of:

```bash
gh issue view <number> --json projectItems
gh project item-list 4 --owner aizhimou --limit 200 --format json
```

### Create a new draft task in the project

Use this only when the maintainer explicitly wants a standalone implementation task, or when the issue is not a good project card in its original form.

1. Create the draft item:

```bash
gh project item-create 4 --owner aizhimou --title "<task title>" --body "<task body>" --format json
```

2. Capture the created item id from the JSON output. If needed, resolve it with:

```bash
gh project item-list 4 --owner aizhimou --limit 200 --format json
```

3. Set fields one at a time:

```bash
gh project item-edit --id <item-id> --project-id PVT_kwHOATBRas4BHRuz --field-id PVTSSF_lAHOATBRas4BHRuzzg4FCew --single-select-option-id f75ad846
gh project item-edit --id <item-id> --project-id PVT_kwHOATBRas4BHRuz --field-id PVTSSF_lAHOATBRas4BHRuzzg4FCmU --single-select-option-id <priority-option-id>
gh project item-edit --id <item-id> --project-id PVT_kwHOATBRas4BHRuz --field-id PVTSSF_lAHOATBRas4BHRuzzg4FCmY --single-select-option-id <size-option-id>
gh project item-edit --id <item-id> --project-id PVT_kwHOATBRas4BHRuz --field-id PVTF_lAHOATBRas4BHRuzzg4FCmc --number <estimate>
```

Default new tasks to `Backlog` unless the maintainer explicitly wants a different initial status.

## Output Pattern

When answering the maintainer, keep the output in this order:

1. issue meaning and conclusion
2. key evidence from code or docs
3. recommended next action
4. draft reply, if requested
5. proposed task title, body, `Priority`, `Size`, and `Estimate`, if requested
6. wait for approval
7. after execution, report verification results with links or concrete confirmations

## Quality Bar

Before finalizing:

- Base the conclusion on the current repository, not generic assumptions.
- Keep issue analysis and GitHub writes separate.
- Re-check repo owner and project target before project writes.
- Verify every write action after execution.
- If a project name or target seems mistyped, confirm it by reading GitHub data before acting.
- Do not skip the `Priority` / `Size` / `Estimate` confirmation step when creating tasks.
