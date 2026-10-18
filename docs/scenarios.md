# Scenario Playbooks

Shared governance applies to every scenario below.

**Decision lineage & metrics (all scenarios):** At each state transition, the orchestration layer records audit-grade lineage: prompt/response history (fixture agent payloads), dependency execution graph snapshot, gate decisions (approve/reject + reason + actor), MTTR contributions, retry counts, rollback counts, and agent execution latency. Events are persisted via the audit log (`GET /api/v1/orchestration/runs/{id}/audit`) and aggregated into Micrometer metrics (`GET /api/v1/orchestration/metrics`: success rate, retries, rollbacks, MTTR, mean latency).

---

## Greenfield

**Requirement:** Build a URL shortener with core APIs, analytics, and reliability features from scratch.

**Decomposition:**

1. Requirements normalization
2. Architecture (bounded contexts)
3. Parallel implement-api + implement-ui
4. Sync barrier into testing. If tests fail: increment retry counter, feed test execution logs back into the developer agent, and re-run (max 3 attempts). If max retries are reached: trigger automated rollback and pause the workflow for human intervention (safe-stop / gated re-plan).
5. Documentation
6. Release readiness (human approval gate)

**Dynamic re-planning & error handling:**

| Condition | Behavior |
|---|---|
| Transient stage failure | Bounded retry (configured `max-retries`, demo default 2–3); stage attempt counter incremented; prior failure output attached as input context |
| Test gate failure | Logs fed back to implement agents; re-enter testing after sync barrier; stop after max retries |
| Max retries exhausted | Automated rollback of succeeded stages, run marked failed/rolled back, workflow paused for human decision |
| Human reject at release | Safe-stop or explicit re-plan with updated requirement text |

**Validation:** Entry/exit policy gates, open-redirect controls in domain service, release approval required.

**Lineage:** Save prompt/response history, dependency execution graph, MTTR, retry counts, and agent execution latency to the audit log at each state transition (including every retry and rollback).

---

## Brownfield

**Requirement:** Add link expiration and click analytics export to the existing URL shortener service.

**Impacted modules:** `UrlShortenerService`, `UrlShortenerController`, `ShortUrl.expiresAt`, analytics export endpoint.

**Orchestration differences:** `implement-api` requires human approval before continuing (change-control gate).

**Dynamic re-planning & error handling:** On post-change test failure, apply the same bounded retry loop as greenfield (feed failure logs to the developer agent, max 3 attempts). Exhaustion triggers rollback of the brownfield change set and pauses for human intervention before any further merge/release.

**Validation:** Change-control approval on API implementation, regression through testing barrier, release gate.

**Lineage:** Save prompt/response history, dependency execution graph, MTTR, retry counts, and agent execution latency to the audit log at each state transition (including change-control gate decisions).

---

## Ambiguous

**Requirement:** Make the shortener more reliable and enterprise ready.

**Clarifying assumptions recorded by the requirements agent:**

- SLO target 99.9% for redirect path
- Authn deferred; admin APIs localhost-only for prototype
- Single-region deployment

**Scope chosen under governance:** rate limiting, health, policy guardrails, release gates.

**Formal release gate policies (checked before release):**

| Gate | Checks performed |
|---|---|
| Security | Open-redirect validation (scheme/host allow-list) and rate-limiting checks on create/redirect paths |
| Compliance | Vulnerability scanning posture on dependencies (declared in release readiness artifact; fail gate on unresolved critical findings) |
| SLO | Synthetic load / redirect latency check ensuring redirect latency under 10 ms to support the 99.9% redirect-path SLO |

A failed gate increments the retry/re-plan path where remediable; persistent failure pauses for human approval or safe-stop rather than silent release.

**Lineage:** Save prompt/response history, dependency execution graph, MTTR, retry counts, agent execution latency, recorded assumptions, and per-gate pass/fail evidence to the audit log at each state transition.
