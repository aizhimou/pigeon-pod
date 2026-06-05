---
name: bug-analysis
description: Analyze software bugs for the PigeonPod project with a bugfix-first workflow. Use when users report broken behavior, regressions, incorrect results, crashes, data inconsistencies, sync/download failures, or ask for root-cause analysis, fix strategy, repro analysis, severity assessment, or regression-risk evaluation. Read current repository docs and code first, then use MCP tools including Context7 only when framework, library, API, or external-service behavior must be verified.
---

# Bug Analysis

Analyze bug reports against PigeonPod's current implementation, expected behavior, and operational constraints.

## Follow This Workflow

1. Restate the bug in one short paragraph, including the observed symptom.
2. Classify the bug: `frontend`, `backend`, `integration`, `data`, `performance`, `security`, `ops`, or `unknown`.
3. State the expected result, actual result, known environment, and current reproduction confidence.
4. Read relevant project docs, code, tests, logs, and migrations before forming conclusions.
5. Reproduce the issue when feasible; if reproduction is not possible, state exactly what evidence exists and what is missing.
6. Bound the impact surface: affected users, affected flows, data risk, frequency, and severity.
7. Identify the root cause or leading hypotheses, and separate confirmed facts from inference.
8. Propose the smallest safe fix, plus alternatives only when they materially change risk or scope.
9. Define the verification plan: tests, manual checks, logging/monitoring, and rollback or containment.
10. Output a bugfix recommendation with confidence, rationale, and open questions.

## Read Local Context First

Prioritize these inputs:

- `README.md`
- `dev-docs/architecture/architecture-design-en.md`
- Relevant backend packages under `backend/src/main/java/top/asimov/pigeon/`
- Relevant frontend routes/components under `frontend/src/pages/` and `frontend/src/components/`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/*.sql`
- Existing tests, logs, stack traces, screenshots, issue reports, or reproduction notes provided in the task

Use fast discovery commands when needed:

```bash
rg -n "error|exception|symptom|module|endpoint|field" backend/src/main/java frontend/src dev-docs README.md
rg --files backend/src/test frontend/src
```

## Use Context7 and MCP Deliberately

Use Context7/MCP only when the bug may depend on framework, library, API, or external-service behavior that should be verified instead of assumed.

Typical triggers:

- Spring Boot/MyBatis-Plus/Sa-Token behavior or lifecycle issues
- React/Mantine/React Router/i18next/Axios runtime or integration behavior
- SQLite, Flyway, or SQL compatibility behavior
- YouTube Data API v3, RSS, or yt-dlp contract/compatibility issues

Rules:

- Resolve the library ID first, then query a focused question.
- Prefer primary or official documentation.
- Distinguish confirmed behavior from inferred behavior.
- If docs conflict with local implementation, prioritize local code reality and call out the mismatch.

## Evaluate With These Dimensions

Assess each dimension explicitly:

1. Reproduction Quality: Is the issue reproducible, intermittent, environment-specific, or currently unverified?
2. Impact and Severity: Who is affected, what breaks, and how severe is the operational or user impact?
3. Root-Cause Confidence: What is proven, what is strongly suspected, and what remains unknown?
4. Fix Scope: What code paths, APIs, data flows, migrations, or configs likely need to change?
5. Regression Risk: What existing behavior could break after the fix?
6. Data and Recovery Risk: Is data already corrupted, missing, duplicated, or hard to recover?
7. Security and Abuse Risk: Does the bug create auth, permission, leakage, or integrity problems?
8. Testability and Observability: What tests, logs, metrics, and alerts are missing or needed?

## Produce This Output Format

Use this structure in final analysis:

```markdown
## Bug Summary
- User report:
- Bug class:
- Severity suggestion: P0/P1/P2/P3
- Reproduction status:
- Assumptions:

## Expected vs Actual
- Expected behavior:
- Actual behavior:
- Known environment:
- Evidence reviewed:

## Impact Assessment
- Affected users or systems:
- Functional impact:
- Data/security/ops impact:
- Frequency or trigger pattern:

## Root Cause Analysis
- Current touchpoints:
- Confirmed facts:
- Leading hypothesis or root cause:
- Confidence: High/Medium/Low

## Fix Strategy
- Recommended fix:
- Alternative options:
- Regression risks:
- Required tests or checks:

## Decision
- Recommendation: Fix now / Fix in planned release / Need more evidence / Not a bug
- Reasoning:
- Open questions:
```

## Decision Heuristics

- Recommend `Fix now` when impact is high, user-visible, data-affecting, security-relevant, or likely to regress core flows.
- Recommend `Fix in planned release` when the issue is real but contained and the workaround or impact is acceptable.
- Recommend `Need more evidence` when reproduction and root-cause confidence are too weak to justify implementation.
- Recommend `Not a bug` when the report is actually expected behavior, a feature gap, or a configuration issue outside the product contract.

If the request is mainly about product scope, feature design, or delivery tradeoffs rather than broken behavior, use `requirements-analysis` instead.

## Quality Bar

Before finalizing, verify all checks:

- Separate symptom, trigger, impact, and root cause.
- Base conclusions on repository evidence, logs, traces, tests, or confirmed docs.
- Mark uncertainty explicitly when reproduction or evidence is incomplete.
- Prefer the smallest maintainable fix that addresses the actual failure mode.
- Include validation steps and at least one regression-prevention measure.
