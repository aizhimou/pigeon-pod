# Developer Reply Grammar

## Required Line

Every issue block must contain exactly one decision line:

```md
Decision: approve_task
```

## Allowed Decisions

- `approve_task`
- `add_issue_to_project`
- `approve_reply`
- `need_info`
- `defer`
- `reject`
- `reanalyze`

## Optional Override Lines

```md
Priority Override: P1
Size Override: M
Estimate Override: 3
Task Title Override: Improve playlist initialization memory usage
Notes: Keep the scope narrow. Do not promise filename templating.
```

## Parsing Rules

- Parse only exact field names.
- Ignore free text outside the current issue block.
- If `Decision:` is missing or ambiguous, do nothing for that issue.
- `approve_reply` requires a usable reply draft in the report or explicit reply text in `Notes:`.
- `approve_task` means create a project draft item unless the developer explicitly asked for `add_issue_to_project`.
