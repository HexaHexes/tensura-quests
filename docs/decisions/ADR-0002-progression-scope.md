# ADR-0002: Per-player counters gate team task completion

- **Status:** Accepted (user decision, 2026-10-30)
- **Date:** 2026-10-30

## Context

Stock FTB Quests stores task progress in `TeamData.taskProgress`, keyed by
task id **per FTB Team** (verified in source, see
`docs/research/ftb-quests-api-investigation.md`). Many of our objectives are
personal - job mastery, individual dungeon floors, personal transaction
volumes - and would be wrongly shared if the stock per-team counter were used
directly: any member's action would progress everyone.

## Problem

Decide how individual progression is represented without forking FTB Quests
and without breaking its completion model.

## Options considered

1. **Team-shared only (stock semantics).** Simplest; unfair for personal
   objectives.
2. **Per-player counters gate the team task.** Our mod persists per-player
   counters in its own storage; the FTB task completes for the team when a
   configured rule over those counters is met.
3. **Both policies, chosen per quest chapter later.** Most flexible; larger
   editor and test surface, deferred decision.

## Decision

Option 2, with the completion policy configurable per task:

- The default policy is **ANY**: the task completes when any single team
  member's personal counter satisfies the requirement.
- **ALL** is available per task: every team member (at the moment of check)
  must satisfy it.
- The policy lives in the task's configuration (`writeData`/`readNetData`/
  `fillConfigGroup`), so quest authors choose per task without new code.
- Per-player counters are persisted by this mod (NeoForge player attachment
  for player-keyed state), never by FTB Quests, and survive restarts,
  disconnects and quest reloads.
- The FTB-visible progress value mirrors the policy-relevant aggregate so the
  quest bar still moves for players.

## Rationale

- Personal progression stays personal; team completion semantics stay
  explicit and author-visible rather than accidental.
- A single mechanism (counters + policy) covers both current needs and the
  obvious future ones, without FTB Quests forks.
- Option 3's flexibility is partially retained: the policy enum can grow a
  third mode later additively.

## Consequences

- Our storage becomes a persistence surface: it must be versioned and tested
  across restarts, disconnects and team membership changes (membership
  churn is expected with Better Party mirroring).
- ALL-policy tasks must handle the "team changed since progress" case
  explicitly - a joiner without progress keeps the task incomplete; this is
  intended and must be documented for quest authors.
- Every custom task must declare which policy applies; there is no silent
  default into stock team semantics for personal domains.
