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
  `1.0.13` - the job-slot adapter touches internal classes, see Gotchas),
  Infinite Dungeon suite, Lightman's Currency (pinned `2.3.0.6`).

**Test:** `./gradlew runGameTestServer` - `TensuraQuestsGameTests` (1 test).
`expectedTests = 1` in `build.gradle` must match the `@GameTest` count. The
run carries the FTB stack via `runtimeOnly` and is namespace-gated to
`tensura_quests`.

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
- Never terminate `runGameTestServer` by closing its output pipe early
  (e.g. `Select-Object -First N`): the orphaned game JVM keeps ticking
  `[ ]` forever without completing the batch, which looks exactly like a
  real test hang. Kill the whole process tree instead, and trust the
  `run/logs/latest.log` summary line over pipeline exit codes.
- Sympathetic Jobs is closed-source (All Rights Reserved) and the job-slot
  entitlement lives in non-`api` internal classes (`JobsStateManager`,
  `JobsPlayerState`). Every touch of `dev.fatsan.sympatheticjobs` goes through
  the single SJ adapter, pinned to `1.0.13`; a version mismatch must fail
  loudly with a log line, never silently.
- FTB Quests' registration APIs (`TaskTypes.register`, `RewardTypes.register`)
  live outside its stable `api` package. All FTB Quests surface contact goes
  through `FtbQuestsBridge`; the GameTest suite must cover registration and
  serialization on every build.
- Stock FTB Quests task progress is per FTB Team. Per-player counters live in
  this mod's own persisted storage (see ADR-0002); never assume a task's
  built-in progress is per player.
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
