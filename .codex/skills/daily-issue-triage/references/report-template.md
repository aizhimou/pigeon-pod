# Daily Report Template

```md
# Daily Issues Report - YYYY-MM-DD

## Cursor Snapshot
- report_date:
- last_incremental_sync:
- selected_issues:
- carry_over_issues:

## Agent Report
### #123 Issue title
- Class: requirement | bug | discussion
- Source summary:
- Relevant touchpoints:
- Analysis:
- Recommendation:
- Proposed next action:
- Proposed task title:
- Proposed task description:
- Suggested fields:
  - Priority:
  - Size:
  - Estimate:
- Draft reply:
- Open questions:

## Developer Reply
### #123
Decision:
Priority Override:
Size Override:
Estimate Override:
Task Title Override:
Notes:

## Execution Receipt
- executor_run_at:
- project_items_created:
- issues_added_to_project:
- issue_replies_posted:
- cursor_updates:
```

## Writing Rules

- Keep one `### #issue-number` block per issue in both `Agent Report` and `Developer Reply`.
- `Draft reply` is optional for requirement and bug issues, but expected for discussion issues.
- Leave `Execution Receipt` empty in report mode.
