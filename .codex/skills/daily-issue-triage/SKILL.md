---
name: daily-issue-triage
description: Generate and process the daily PigeonPod issue triage report. Use when reviewing open GitHub issues incrementally with a text cursor, classifying issues as requirement, bug, or discussion, drafting maintainer-facing analysis, or executing developer-approved follow-up actions from the daily report.
---

# Daily Issue Triage

## Overview

Use this skill for the PigeonPod daily issue workflow:

1. Read the cursor and select only incremental issues that need attention.
2. Analyze each selected issue and write a daily report for the maintainer.
3. Later, read the maintainer reply in that same report and execute only the explicitly approved actions.

This skill is for the issue triage pipeline itself. For issue analysis, delegate to:

- `requirements-analysis` for feature requests and enhancement requests
- `bug-analysis` for bug reports and broken behavior
- the discussion rubric in [references/discussion-rubric.md](references/discussion-rubric.md) for product-boundary or support-style discussions

## File System Contract

Working files live in `dev-docs/issue-triage/`, not under `.codex/`.

Read [references/cursor-and-files.md](references/cursor-and-files.md) before running the workflow. It defines:

- `dev-docs/issue-triage/CURSOR.md`
- `dev-docs/issue-triage/reports/YYYY-MM-DD.md`
- issue states and cursor update rules

## Mode Selection

Choose one mode before doing any work.

- `report` mode
  Use when generating today's report for the maintainer.
- `execute` mode
  Use when the maintainer has filled in `Developer Reply` and you need to apply the approved actions.

If the user does not specify a mode, infer it:

- Empty or missing `Developer Reply` means `report`
- Filled `Developer Reply` plus a request to act means `execute`

## Report Mode

### Step 1: Build Local Context

Read only the minimum needed local context first:

- `README.md`
- `dev-docs/architecture/architecture-design-zh.md`
- `dev-docs/issue-triage/CURSOR.md`
- the latest one or two reports under `dev-docs/issue-triage/reports/`

Do not deep-read every open issue every day.

### Step 2: Select Issues Incrementally

If this is the first run for a repo, do a one-time full baseline before normal incremental selection:

- list all current open issues
- seed every open issue into the cursor `active` list
- mark unreviewed issues as `state: new`
- only after the baseline is written should `last_incremental_sync` advance

This prevents older open issues from becoming invisible once incremental sync starts.

Use the cursor to choose issues in this order:

1. carry-over issues in active cursor states
2. open issues created or updated after `last_incremental_sync`
3. optional backlog fill only if the report still has capacity

Target a small, reviewable set. Default to 3-7 deep analyses per daily report unless the user says otherwise.

### Step 3: Classify Each Issue

Classify each selected issue as exactly one of:

- `requirement`
- `bug`
- `discussion`

Routing rules:

- Feature request, enhancement, new capability, delivery tradeoff: `requirement`
- Broken behavior, crash, incorrect result, stuck state, regression: `bug`
- Product-boundary discussion, usage guidance, integration discussion, support-style thread: `discussion`

### Step 4: Analyze

For each issue:

- open the issue and relevant comments
- inspect the most relevant repo docs and code
- use the routed skill or rubric
- keep the output decision-oriented

For discussions, do not force them into implementation tasks. Prefer `reply_only` or `reply_and_monitor` when that better fits the thread.

### Step 5: Write The Daily Report

Write the report to `dev-docs/issue-triage/reports/YYYY-MM-DD.md`.

Use the template in [references/report-template.md](references/report-template.md).

Rules:

- one report file per day
- analyzer writes only `Cursor Snapshot` and `Agent Report`
- analyzer may scaffold empty `Developer Reply` sections for each issue
- analyzer must not write `Execution Receipt`

### Step 6: Update Cursor For Report Completion

After the report is written:

- if this was the first run, keep every still-open unreviewed issue in `active` with `state: new`
- update `last_incremental_sync`
- move reviewed issues into the correct active state
- do not mark an issue terminal until maintainer intent is clear

## Execute Mode

### Step 1: Read The Reply Grammar

Read [references/reply-grammar.md](references/reply-grammar.md) and parse only explicit decisions.

Never infer action from vague prose if the decision line is missing or ambiguous.

### Step 2: Execute Only Approved Actions

Allowed actions:

- `approve_task`
- `add_issue_to_project`
- `approve_reply`
- `need_info`
- `defer`
- `reject`
- `reanalyze`

Execution policy:

- no explicit decision, no action
- project writes require explicit approval
- issue replies require explicit approval
- if a task is approved, prefer creating a project draft item unless the developer explicitly asks to add the original issue to the project

### Step 3: Update The Report And Cursor

When execution finishes:

- append `Execution Receipt` to the same daily report
- update the cursor state for every processed issue
- keep a concise audit trail of what was created, posted, deferred, or skipped

## Discussion Issue Policy

Read [references/discussion-rubric.md](references/discussion-rubric.md) when an issue is neither a clear bug nor a clear feature request.

Default posture:

- clarify product boundaries
- avoid promising implementation
- prefer a concise, technically honest reply draft
- only promote a discussion into a task when the thread has converged on a real product requirement

## Quality Bar

- Do not deep-analyze every open issue every day.
- Keep the cursor authoritative.
- Separate analysis from execution.
- Keep maintainer review lightweight: concise report, explicit recommendation, explicit next action.
- Prefer adding evidence from local code and docs over speculation.
- For task creation or issue replies, act only on explicit maintainer approval.
