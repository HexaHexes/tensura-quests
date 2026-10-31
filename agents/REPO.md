# Repo rules - Tensura Quests

Repo-specific rules and notes. The org-wide `AGENTS.md` beside this file is
maintained in [`neoforge-mod-template`](https://github.com/HexaHexes/neoforge-mod-template)
and pulled in verbatim - **never hand-edit it.** Everything in this file is this
repo's own and is edited freely; keep it aligned with the org rules rather than
restating them.

**Repo:** `HexaHexes/tensura-quests` - **Mod ID:** `tensura_quests` - **Branch:** `main`
**Platform:** Minecraft 1.21.1 / NeoForge 21.1.259 / JDK 21 - **Jar:** `build/libs/tensura_quests-<version>.jar`

The implementation mod of the Tensura RPG FTB Quests integration ecosystem. It
registers custom FTB Quests task and reward types - job progression, job-slot
unlocks, Infinite Dungeon milestones, economy transactions, Faction Capitals -
each validated against the authoritative gameplay system that owns the state.
FTB Quests keeps questbook presentation and completion; this mod owns neither
gameplay state nor quest structure.

**Dependencies**
- Required: Minecraft, NeoForge, FTB Quests (`2101.1.36+`), FTB Library,
  FTB Teams, Tensura Quests API (`api_version` in `gradle.properties`).
- Optional (gated at runtime with ModList checks; a missing mod disables only
  its own task/reward family, never the framework): Sympathetic Jobs (pinned
  `1.0.13` via `maven.modrinth:sympathetic-jobs`, with its required
  Sympathetic Attributes `1.1.6` as a dev-runtime coordinate - the job task
  family registers only while the pin holds, see Gotchas), Infinite Dungeon
  suite, Lightman's Currency (pinned `2.3.0.6` via
  `curse.maven:lightmans-currency-472521:9048585` - the economy family
  registers on presence alone, INT-004, see Gotchas).

**Test:** `./gradlew runGameTestServer` - `TensuraQuestsGameTests` (1
namespace-gate test), `FtbQuestsFrameworkGameTests` (9 framework tests:
registration, SNBT and net round-trips incl. the completion policy,
intake to completion with logging, TeamData persistence, the ANY and
ALL policies over two members, membership-churn counter isolation, the
attachment codec round-trip incl. partner sets), 
`FtbQuestsRewardGameTests` (7 reward tests: registration, SNBT and net
round-trips, claim execution, failed claim release-and-retry,
duplicate-claim protection, per-player scope forcing),
`SympatheticJobsGameTests` (5 job tests: registration behind the
version gate + polling/login hooks, FR-020/021/022 driven through the
tick-dispatch entry, config round-trips incl. the
malformed-requirement drop), `LightmansCurrencyGameTests` (8 economy
tests: registration behind the presence gate, FR-040..FR-044 driven
through the adapter's extraction + router seam, event-type
discrimination and U-09's playerless rule, config round-trips) and
`JobSlotRewardGameTests` (7 slot-reward tests: registration behind the
SJ gate, FR-023 grant + effective-limit read-back, SEC-002
single-shot, per-player isolation, forced version-gate block, clamped
grant failing loudly with claim release, config round-trips +
`@OnlyIn` hook), 37 total. `expectedTests = 37` in `build.gradle`
must match the `@GameTest` count. The run carries the FTB stack,
Sympathetic Jobs + Attributes and Lightman's Currency via
`runtimeOnly` and is namespace-gated to `tensura_quests`.
`TestQuestFile`, `QuestFixture`, `CapturingAppender`, `MockPlayers`
and `LcTrades` (real LC event construction) are shared,
package-private helpers in the gametest source set; MC-free JUnit
logic (`IntakeRules`, `ClaimResult`, `CounterPolicy`, `JobRequirement`,
`EconomyRules`, `SjVersionGate` incl. the FR-024 claim gate, plus the
two-boundary `AdapterIsolationTest` source scan) lives in `src/test`
(36 tests, 8 classes).

**Remote:** `github.com/HexaHexes/tensura-quests` (private, default branch
`main`).

---

## Gotchas (do not regress them)

- `tensura-quests-api` resolves from GitHub Packages (`api_version` in
  `gradle.properties` must name a **published** version - publish the api
  repo before bumping this repo's dependency). Every Gradle CI step needs the
  step-scoped `PACKAGE_READ_TOKEN` (pseudocore#85 pattern) - the workflow's
  own token cannot read a package owned by another repository. The template's
  workflows predate this pattern; do not regress the env blocks added to the
  junit, runData, GameTest and release build steps.
- Sympathetic Jobs is closed-source (All Rights Reserved) and the job-slot
  entitlement lives in non-`api` internal classes (`JobsStateManager`,
  `JobsPlayerState`). Every touch of `dev.fatsan.sympatheticjobs` goes through
  the single SJ adapter package (`compat/sympatheticjobs`, INT-002 - enforced
  by `AdapterIsolationTest`, which scans src/main and exempts the
  gametest harness, the only other legal contact). The family registers only
  after `SjVersionGate` matches the `1.0.13` pin: `SjPresence` reads the
  installed version from FML *without loading any SJ class* (loading them
  before the gate rules would turn "disabled" into a linkage error), an
  absent mod logs INFO, a mismatch logs ERROR with both versions, and only
  OK calls `FtbQuestsBridge.registerJobTaskTypes()` +
  `registerJobSlotReward()`. The slot-reward **write path** re-checks the
  gate per claim (`SjVersionGate.claimBlocked`: a blocked claim logs ERROR
  and yields false, FR-024), raises the override, then *verifies*
  `effectiveSlotLimit` actually rose - SJ clamps the override to its
  12-job total, so an over-cap raise rolls back to the exact previous
  entitlement and fails loudly (nothing partial is ever granted). SJ's own
  attachment persists grants (SEC-007: we hold no slot counter), and the
  adapter's 3-arg `grantJobSlots(..., installedVersion)` is the per-call
  test seam for the forced-gate GameTest (no global state for the parallel
  batch to race on).
- Sympathetic Jobs semantics that surprised us (bytecode-verified, matters
  for the slot reward too): `level()` and `setProgress()` are gated on
  *ownership* (`hasJob`), `addJob` caps at `MAX_SLOTS` (= all 12 jobs), and
  the **first acquired job auto-becomes the active one** while
  `setActiveJob` additionally requires the job inside the *usable* slot
  limit. Job tasks therefore read levels regardless of the active job
  (FR-020), and tests seed state by mutating the live object
  `JobsStateManager.get` returns - never via sync/reconcile calls, which
  would send packets at mocks.
- State-derived tasks (jobs today, dungeon floors next) poll through FTB
  Quests' `autoSubmitOnPlayerTick` (a positive interval puts the task in the
  dispatch set; the handler pre-checks completion and can-start) and route
  through `TensuraTask.pollState`: only a *positive* personal-counter delta
  reaches intake, so converged players and repeated evaluations stay silent -
  never call `recordValidatedIntake` in a raw tick loop.
- FTB Quests' registration APIs (`TaskTypes.register`, `RewardTypes.register`)
  live outside its stable `api` package. All FTB Quests surface contact goes
  through `FtbQuestsBridge`; the GameTest suite must cover registration and
  serialization on every build. Facade discipline (INT-003): the bridge is the
  only class importing `TaskTypes` and the only site that registers a type -
  registering anywhere else splits the choke point that makes accepted risk
  R4 survivable. Task classes may return the registered `TaskType` from
  `getType()` and touch no other registry surface.
- Stock FTB Quests task progress is per FTB Team. Per-player counters live in
  this mod's own persisted storage (see ADR-0002); never assume a task's
  built-in progress is per player. The FTB-visible value mirrors the
  policy-relevant aggregate (`CounterPolicy`: max for ANY, min for ALL).
- Gametest mock players never run the network configuration handshake, so
  NeoForge's `NetworkRegistry.checkPacket` rejects every modded payload sent
  to them ("may not be sent to the client") - FTB Quests' progress sync on
  the first mirror write, and Lightman's Currency's coin-list sync *during
  login itself*. Never call `GameTestHelper.makeMockServerPlayerInLevel`
  directly: `MockPlayers.create` replicates vanilla's placement (byte-identical
  recipe) and teaches the connection every registered payload id *before*
  `placeNewPlayer`, the only ordering that wins - LC registers its own
  HIGHEST-priority login listener during mod init, so a post-placement fixup
  and a HIGHEST listener of ours both arrive too late. Every suite routes
  through it (reward tests included).
- Lightman's Currency contact lives only in `compat/lightmanscurrency`
  (extraction + router), enforced by `AdapterIsolationTest`, which scans
  `src/main` for *both* integration roots and exempts the gametest harness.
  The intake boundary is one subscription: `TradeEvent.PostTradeEvent` on the
  NeoForge bus - completed trades only, never inventory or money movement
  (SEC-004); playerless completions (command, storage-mode, automation)  extract to nothing at all (U-09's rule). Gating is presence-only per the
  #14 spec: a future API-breaking LC fails loudly at listener registration
  via linkage rather than silently no-opping. Distinct-partner sets live
  inside `PersonalCounterStore` (now layout version 2 - additive `partners`
  field, v1 saves decode with empty sets).
- The pack's team identity is FTB Teams, mirrored from Better Party by
  Better Party X FTB Teams. Never depend on Better Party directly; treat FTB
  team membership as the derived-but-authoritative-for-quests value.
- `tensura_ftb` (ManasCore, `io.github.manasmods`) already ships Race/Spirit/
  Ability/Awakening/Existence task and reward types. Never duplicate Tensura
  core progression here.

## Naming - this repo

- Repo and folder: `tensura-quests`; `mod_id`: `tensura_quests`; Java base
  package `org.bitwisemadness.tensuraquests`, with the Maven `group` in
  `build.gradle` matching.

## CI & release model - this repo

- `ci.yml` runs the template's three checks (junit, gametest, version-bump).
- `release.yml` cuts the GitHub release for `v<mod_version>` on a push to
  `main` (pre-releases only when the repo variable `ALPHA_RELEASES` is true).
- `publish.yml` uploads the artifact to the org's GitHub Packages registry on
  release publish; `modrinth.yml` publishes to Modrinth when the repo secrets
  are configured.
