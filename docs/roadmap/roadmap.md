# Roadmap

Dependency-aware phases for the Tensura Quests ecosystem. Phases advance on
exit criteria, not dates. Status: `done`, `in progress`, `planned`,
`blocked`.

| Phase | Scope | Status |
|---|---|---|
| 0 Discovery and feasibility | Research, ADRs, repo architecture | done (this docs set; U-01..U-10 tracked) |
| 1 Repository and foundation | Both repos, CI, builds, docs | in progress (scaffold merged pending; CI wiring fix in this PR) |
| 2 FTB Quests integration framework | First custom task + reward, persistence, policies | planned (next) |
| 3 Job integration | Mastery tasks, slot-unlock reward, SJ adapter | planned |
| 4 Infinite Dungeon integration | Event intake, attribution, validation | blocked on ADR-0003 release |
| 5 Economy integration | Transaction tasks, anti-exploit | blocked on U-01/U-02 |
| 6 Capital integration | Contracts only this cycle | blocked on capital implementation |
| 7 Hardening and release | Multiplayer matrix, performance, docs, release | planned |

## Phase details

### Phase 0 - Discovery and Feasibility (done)

Deliverables: eight research documents, ADR-0001/0002/0003, requirements
specifications, this roadmap. Exit criteria met: research complete, unknowns
documented (U-01..U-10), architecture approved (user decision 2026-10-30),
blockers identified (capital non-existence; ADR-0003 dependency; LC parity
gate).

### Phase 1 - Repository and Development Foundation (in progress)

Deliverables: `tensura-quests-api` and `tensura-quests` from
`neoforge-mod-template`, GitHub Packages publishing, CI wiring including the
cross-repo read token, docs tree. Exit criteria: clean env builds (verified
locally and by api CI), mod loads in a dev instance (GameTest server),
CI validates compilation (api green; implementation green pending this PR),
repository documentation complete (README, agents/REPO.md, this docs tree).

### Phase 2 - FTB Quests Integration Framework (next)

Deliverables: `FtbQuestsBridge`, one counter-based custom task proving
registration -> configuration -> serialization -> server-side progress ->
persistence, one custom reward proving claim semantics, per-player counter
store with ANY/ALL policies (ADR-0002), diagnostics logging. Exit criteria:
custom task in editor; config survives reload; progress server-side;
persisted across restart; reward reliable; GameTests green with
`expectedTests` synced.

### Phase 3 - Job Integration

Deliverables: SJ adapter (single package, pinned 1.0.13, loud version gate),
Job Mastery / Multi-Job Mastery / Job Participation tasks, Unlock Job Slot
reward via `setSlotLimitOverride` + `reconcileAndSync`. Exit criteria: job
state completes quests; slots granted and persisted; established job design
respected (no active-job dependence, no second slot counter); `/sjobs`
subcommand surface documented (U-03).

### Phase 4 - Infinite Dungeon Integration

Depends on: ADR-0003 release of attributed events. Deliverables: event
intake with dimension/layer/floor validation, Floor/Layer/Boss tasks,
individual vs shared credit via ADR-0002 policies. Exit criteria: valid
events credit the right player; invalid events increment nothing and log;
multiplayer behavior verified.

### Phase 5 - Economy Integration

Entry gate: U-01 (`TradeEvent.Post` parity on 2.3.0.6) and U-02 (P2P
existence) resolved by jar inspection. Deliverables: transaction intake,
Player Transaction / Sales Milestone / Trading Partner tasks, anti-exploit
rules (self-trade, double-count, cancellation). Exit criteria: completed
transactions credit; cancelled/invalid ones never do; attribution correct in
multiplayer edge cases.

### Phase 6 - Capital Integration (contract-only this cycle)

Deliverables: capital contracts in `tensura-quests-api` (plot identity
stable across migrations per the established migration rule), co-design
sign-off with the capital owners. Actual capital tasks remain `Won't` until
an authoritative implementation exists (FR-051/052).

### Phase 7 - Hardening and Release

Deliverables: full compatibility matrix re-verification, persistence and
sync validation, multiplayer test plan executed, performance pass, complete
docs, reproducible release artifacts, Modrinth/CurseForge listing if
configured. Exit criteria: all Must requirements pass or are explicitly
deferred with reasons; known limitations documented.

## Change log of planning decisions

- 2026-10-30: Phase 6 reduced to contract definition (capital system does
  not exist yet - capital-system-integration-research.md).
- 2026-10-30: Phase 4 made dependent on the ADR-0003 upstream release.
- 2026-10-30: Phase 5 made gated on U-01/U-02 instead of being freely
  schedulable.
