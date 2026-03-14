# Cursor And Files

## Paths

- Cursor: `dev-docs/issue-triage/CURSOR.md`
- Daily reports: `dev-docs/issue-triage/reports/YYYY-MM-DD.md`

## Cursor Shape

Keep the cursor human-readable and machine-parseable. Use a single fenced YAML block.

```md
# Issue Processing Cursor

```yaml
version: 1
last_incremental_sync: null
active: []
terminal: []
```
```

## Active Entry Shape

```yaml
- issue: 124
  kind: requirement
  state: analyzed_waiting_reply
  skill: requirements-analysis
  last_issue_updated_at: 2026-03-13T09:04:16Z
  last_report: 2026-03-14
  recommendation: approve_task
```

## Terminal Entry Shape

```yaml
- issue: 103
  final_state: deferred
  decided_at: 2026-03-14T10:00:00Z
```

## Allowed States

- `new`
- `analyzed_waiting_reply`
- `approved_for_task`
- `approved_for_reply`
- `need_more_info`
- `monitoring_discussion`
- `deferred`
- `done_synced`
- `closed_no_action`

## Selection Rules

On the first run for a repo:

1. enumerate all open issues
2. seed them into `active`
3. mark not-yet-reviewed issues as `new`
4. then start normal incremental behavior

Without this baseline, older open issues that predate `last_incremental_sync` can be skipped forever.

Choose issues in this order:

1. carry-over active issues that still need maintainer attention or execution
2. open issues updated after `last_incremental_sync`
3. optional backlog fill if daily capacity remains

Never deep-analyze all open issues by default.
