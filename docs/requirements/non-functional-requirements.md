# Non-functional, security, compatibility and testing requirements

Companion to `functional-requirements.md`; same id/priority/status
conventions.

## Non-functional

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| NFR-001 | Persistence across restart, world reload, disconnect, quest reload | Must | No progress or entitlement loss in any documented transition | Manual test matrix (Phase 7) | planned |
| NFR-002 | No client-authoritative progression | Must | Removing/forbidden client packets cannot grant progress; all intake server-side | Code review + adversarial test | planned |
| NFR-003 | Event intake is cheap | Should | Per-event work is O(1) map lookups; no world scans on the hot path | Profiling during Phase 7 | planned |
| NFR-004 | Diagnostic logging at every decision point | Must | Log lines exist for accept, reject (with reason), and reward failure | Log inspection | planned |
| NFR-005 | Clean env builds | Must | Fresh clone builds with documented credentials only | CI on every PR | implemented (both repos CI-validated) |
| NFR-006 | No emojis; house documentation voice | Must | Docs and logs match org style | Review | implemented |

## Security

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| SEC-001 | Duplicate events do not double-count | Must | Replayed/duplicate notifications increment at most once per logical occurrence | GameTest | planned |
| SEC-002 | Repeated reward claims are impossible | Must | Second claim of the same per-player reward fails | GameTest | planned |
| SEC-003 | Dungeon events validated against dimension + layer context | Must | Foreign-dimension events rejected (see FR-033) | GameTest | planned |
| SEC-004 | Transaction attribution cannot be spoofed by item movement | Must | Only completed-trade events count (see FR-040) | GameTest | blocked on U-01 |
| SEC-005 | Team-membership changes cannot steal or duplicate progress | Must | Counters are player-keyed; team change neither copies nor erases them | Multiplayer test | planned |
| SEC-006 | Ownership claims come only from the authoritative system | Must | No ownership data stored quest-side | Code review | planned (Phase 6) |
| SEC-007 | Job-slot state never duplicated | Must | No second slot counter anywhere; Sympathetic Jobs attachment is the only truth | Code review | planned |

## Compatibility

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| COMP-001 | Compile against the pinned FTB stack (matrix) | Must | CI builds with the pinned coordinates | CI | implemented |
| COMP-002 | Runtime accepts the live instance's newer FTB patch versions | Must | mods.toml ranges accept the instance versions | mods.toml review + instance smoke | implemented (ranges written) |
| COMP-003 | Sympathetic Jobs 1.0.13 pin enforced | Must | Version gate fails loudly on mismatch | Unit test | planned |
| COMP-004 | Missing optional mods do not break load | Must | Mod loads with only required deps | GameTest | planned |
| COMP-005 | Version drift recorded in the matrix | Must | Every drift gets a matrix row before adoption | Review | implemented (FTB drift recorded) |

## Testing

| ID | Requirement | Priority | Acceptance criteria | Verification | Status |
|---|---|---|---|---|---|
| TEST-001 | `expectedTests` stays in sync with `@GameTest` count | Must | CI gametest grading passes | CI | implemented |
| TEST-002 | Serialization round-trips covered per task/reward type | Must | Net + NBT equality tests per type | GameTest | planned |
| TEST-003 | Restart persistence procedures documented and executed per phase exit | Must | Written procedure run at each phase gate | Manual procedure | planned |
| TEST-004 | Multiplayer scenarios tested before release | Must | Two-player scenarios for every policy and attribution path | Manual test plan (Phase 7) | planned |
| TEST-005 | JUnit suites cover pure logic (codecs, policies, gates) | Must | Non-empty junit run per CI | CI | implemented (placeholder suites active) |
