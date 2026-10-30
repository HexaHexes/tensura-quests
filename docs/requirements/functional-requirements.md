# Functional and integration requirements

Requirements for the Tensura Quests ecosystem. Each requirement has a unique
id, priority (Must / Should / Could / Won't for this release), acceptance
criteria, verification method and implementation status. Status values:
`planned`, `in progress`, `implemented` (acceptance criteria verified),
`blocked`.

Traceability: requirement -> phase (see roadmap) -> module (see architecture)
-> test. A requirement is `implemented` only when its acceptance criteria are
verified by the listed method - code existing is not enough.

## Framework (Phase 2)

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| FR-001 | Register a custom task type through `FtbQuestsBridge` | Must | Type appears in FTB Quests editor with our namespace id | GameTest: registry contains the type after mod load | implemented (smoke: registration path exercised from Phase 2 prototype) |
| FR-002 | Task configuration survives quest file save/load | Must | SNBT round-trip preserves every config field | GameTest: writeData -> readData equality | planned |
| FR-003 | Task configuration syncs to client | Must | Net round-trip preserves every config field | GameTest: writeNetData -> readNetData equality | planned |
| FR-004 | Task progress updates server-side and completes at max | Must | Progress increments only from server-validated intake; completion fires at threshold | GameTest | planned |
| FR-005 | Progress persists across restarts | Must | Counters and TeamData progress survive server restart | Manual test procedure + NBT inspection | planned |
| FR-006 | Register a custom reward type | Must | Reward appears in editor; `claim` executes server-side | GameTest | planned |
| FR-007 | Reward failure is not silent | Must | Failed execution returns false from claim, logs reason, marks nothing granted | GameTest (forced failure path) | planned |
| FR-008 | Per-player counters persist independently of FTB Quests | Must | Counter store survives restart and disconnect; keyed by player | GameTest + restart procedure | planned |
| FR-009 | Completion policy ANY (default) | Must | Task completes when any one member's counter meets the requirement | GameTest with two simulated players | planned |
| FR-010 | Completion policy ALL (configurable) | Must | Task completes only when every member at check time meets it | GameTest with two simulated players | planned |
| FR-011 | Editor configuration group for every task/reward | Should | Every config field is editable in the quest editor | Manual editor check | planned |
| FR-012 | Structured observability logging | Must | Every progress grant and rejection logs the integration, reason and ids | Log inspection in tests | planned |

## Job domain (Phase 3)

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| FR-020 | Job Mastery task reads authoritative job level | Must | Completes when the configured job reaches the configured level; reads `JobsPlayerState`, not the active job | GameTest against Sympathetic Jobs | planned (blocked on nothing; D3 adapter) |
| FR-021 | Multi-Job Mastery task | Should | Configurable list of (job, level) pairs, all required | GameTest | planned |
| FR-022 | Job Participation task (has job) | Should | Completes when the player holds the configured job | GameTest | planned |
| FR-023 | Unlock Job Slot reward grants a slot via Sympathetic Jobs | Must | After claim, `effectiveSlotLimit` increases by the configured amount; persisted; idempotent per player | GameTest + restart procedure | planned |
| FR-024 | Slot reward fails loudly on SJ version mismatch | Must | Missing/changed internal classes produce a logged failure and false claim result | Unit test of the adapter gate | planned |
| FR-025 | Job Slot Unlock task (entitlement-based) | Could | Reads the player's slot entitlement, not a copied counter | GameTest | planned |

## Dungeon domain (Phase 4; depends on ADR-0003 release)

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| FR-030 | Floor Completion task | Must | Completes on `FloorEnteredEvent` for the configured layer/floor in the configured dimension | GameTest with fabricated events | blocked on ADR-0003 (needs attribution only for credit; event intake testable now) |
| FR-031 | Layer Completion task with player attribution | Must | Attributes to the completing player from the attributed event; policy per ADR-0002 | GameTest | blocked on ADR-0003 |
| FR-032 | Miniboss Defeat task | Must | Counts only boss-slain events in the dungeon dimension at the configured layer | GameTest | blocked on ADR-0003 |
| FR-033 | Reject events from unrelated dimensions | Must | Events outside the configured dimension set increment nothing and log | GameTest | planned |
| FR-034 | Dungeon Milestone task (layer N or deeper) | Should | Configurable threshold semantics | GameTest | planned |

## Economy domain (Phase 5; entry gate U-01/U-02)

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| FR-040 | Player Transaction task counts completed trades only | Must | Only completed-trade events count; cancelled trades never count | GameTest with LC present | blocked on U-01 |
| FR-041 | Sales Milestone task (accumulated value) | Must | Accumulates trade value per player; configurable threshold | GameTest | blocked on U-01 |
| FR-042 | Trading Partner task (distinct partners) | Should | Counts distinct counterparties; both sides of one trade never double-count | GameTest | blocked on U-01/U-02 |
| FR-043 | Self-trading and circular trades rejected | Must | Buyer==seller and same-owner accounts increment nothing | GameTest | blocked on U-01 |
| FR-044 | Team-member trades configurable | Should | Policy for trades within the same team is explicit per task | GameTest | blocked on U-01 |

## Capital domain (Phase 6; contract-only this cycle)

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| FR-050 | Publish capital integration contracts in tensura-quests-api | Must | Events/ids for plot purchase, contribution, development, permission exist and are documented | API review + producer compile test | planned |
| FR-051 | Own Capital Plot task | Won't (this cycle) | - | - | blocked on capital implementation |
| FR-052 | Capital Contribution task | Won't (this cycle) | - | - | blocked |

## Integration requirements

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| INT-001 | Consume FTB Teams as the only team identity | Must | No Better Party dependency; team lookups via FTB Teams API | Code review + multiplayer test | planned |
| INT-002 | Sympathetic Jobs contact isolated in one adapter | Must | Exactly one package touches `dev.fatsan.sympatheticjobs` | Code review (static check possible) | planned |
| INT-003 | FTB Quests contact isolated in `FtbQuestsBridge` + base classes | Must | Exactly one facade registers types | Code review | in progress |
| INT-004 | Optional integrations ModList-gated | Must | Missing mod disables only its own family with a log line | GameTest without the optional mod | planned |
| INT-005 | Api contracts leak no gameplay classes | Must | Api artifact has no gameplay imports | Static review + compile test | implemented (api has no contracts yet; rule recorded) |
| INT-006 | Events carry ids, not entities | Must | No event field exposes a live entity to long-lived listeners | Code review | planned |

## Status notes

- FR-001's "implemented" covers the Phase 2 prototype scope once merged; the
  full framework items land with it.
- Economy requirements are blocked by design until U-01/U-02 resolve; no
  economy code may be written against unverified APIs.
- `Won't (this cycle)` items stay in the spec for traceability into the next
  release.
