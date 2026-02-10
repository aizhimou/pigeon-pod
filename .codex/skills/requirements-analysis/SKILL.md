---
name: requirements-analysis
description: Analyze product/feature requirements for the PigeonPod project with software engineering rigor. Use when users ask to evaluate a requirement's value, feasibility, architecture fit, implementation impact, risk, delivery scope, or tradeoffs. Always inspect current repository docs/code first (especially architecture and README files), then use MCP tools (including Context7) to verify external library/framework/API constraints before concluding.
---

# Requirements Analysis

Analyze requirements against PigeonPod goals, current architecture, and implementation reality.

## Follow This Workflow

1. Restate the requirement in one short paragraph.
2. Identify requirement type: `feature`, `enhancement`, `bugfix`, `non-functional`, `integration`, or `migration`.
3. Define expected user value and business value.
4. Read relevant project docs and code before giving conclusions.
5. Use MCP/Context7 to confirm dependency or API constraints when external libraries/services are involved.
6. Evaluate architecture fit, implementation complexity, data impact, and operational impact.
7. Propose an implementation strategy with phased scope (`MVP`, `next`, `later`).
8. Output a decision with explicit rationale and open questions.

## Read Local Context First

Prioritize these files for PigeonPod:

- `README.md`
- `dev-docs/architecture/architecture-design-en.md`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/*.sql`
- Relevant backend packages under `backend/src/main/java/top/asimov/pigeon/`
- Relevant frontend routes/components under `frontend/src/pages/` and `frontend/src/components/`

Use fast discovery commands when needed:

```bash
rg -n "keyword|concept|module" backend/src/main/java frontend/src dev-docs README.md
rg --files dev-docs/
```

## Use Context7 and MCP Deliberately

Use Context7/MCP when the requirement depends on framework/library/service behavior, version constraints, configuration, or integration details.

Typical triggers:

- Spring Boot/MyBatis-Plus/Sa-Token behavior or config decisions
- React/Mantine/React Router/i18next/Axios constraints
- YouTube Data API v3 limits/quotas/contract details
- RSS/Podcasting namespace compatibility details
- yt-dlp options/behavior and compatibility implications

Rules:

- Resolve library ID first, then query focused questions.
- Prefer primary/official docs and version-aware guidance.
- Distinguish facts from inference.
- If docs conflict with local implementation, prioritize local code reality and call out the gap.

## Evaluate With These Dimensions

Assess each dimension explicitly:

1. Value Alignment: Match with PigeonPod core goals (YouTube-to-podcast conversion, auto-sync/download, feed usability, operations simplicity).
2. Feasibility: Confirm technical possibility with current stack and constraints.
3. Architecture Fit: Check compatibility with backend service boundaries, scheduler/event flow, DB schema, and frontend route/state model.
4. Data and Migration Impact: Identify new fields/tables, migration requirements, backfill, and backward compatibility.
5. API and Contract Impact: Identify REST/RSS contract changes and consumer compatibility risks.
6. Security and Compliance: Review auth, permissions, secrets/API keys, abuse vectors.
7. Performance and Cost: Estimate queue pressure, I/O/download load, external API quota consumption, and storage growth.
8. Testability and Operability: Define unit/integration/e2e coverage and monitoring/logging needs.

## Produce This Output Format

Use this structure in final analysis:

```markdown
## Requirement Summary
- User request:
- Requirement type:
- Assumptions:

## Value Assessment
- User value:
- Product/business value:
- Priority suggestion: High/Medium/Low

## Feasibility and Architecture Fit
- Current touchpoints:
- Proposed changes:
- Architecture fit verdict: Good/Partial/Poor

## Impact Analysis
- Backend impact:
- Frontend impact:
- Database/migration impact:
- External dependency impact:
- Security/performance/ops impact:

## Delivery Plan
- MVP scope:
- Non-MVP scope:
- Estimated complexity: S/M/L/XL
- Key risks and mitigations:

## Decision
- Recommendation: Proceed / Proceed with constraints / Defer / Reject
- Reasoning:
- Open questions:
```

## Decision Heuristics

- Recommend `Proceed` when value is clear, fit is good, and risk is manageable.
- Recommend `Proceed with constraints` when value is high but scope/risk needs staged delivery.
- Recommend `Defer` when value exists but prerequisites are missing.
- Recommend `Reject` when requirement conflicts with core goals or creates disproportional cost/risk.

## Quality Bar

Before finalizing, verify all checks:

- Base conclusions on repository evidence, not assumptions only.
- Confirm external-library/API claims through Context7/MCP when relevant.
- Separate facts, assumptions, and unknowns.
- Include at least one feasible implementation path.
- Include explicit tradeoffs and rollback/fallback considerations.
