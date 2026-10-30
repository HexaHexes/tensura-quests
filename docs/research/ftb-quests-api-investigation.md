# FTB Quests API investigation

**Scope:** FTB Quests on Minecraft 1.21.1 / NeoForge, version line 2101.1.x
(brief pin: 2101.1.36). **Method:** direct source reading of the
`1.21.1/main` branch of `FTBTeam/FTB-Quests` on GitHub, plus the mod metadata
of the jar shipped in the live pack instance. **Date:** 2026-10-30.

## Registration surface

| API | Purpose | Source | Status |
|---|---|---|---|
| `TaskTypes.register(ResourceLocation, TaskType.Provider, Supplier<Icon>)` | Register a custom task type | `quest/task/TaskTypes.java` (interface static, `TYPES.computeIfAbsent`) | Verified |
| `RewardTypes.register(name, RewardType.Provider, Supplier<Icon>, boolean availableByDefault)` | Register a custom reward type | `quest/reward/RewardTypes.java` | Verified |
| `TaskType` constructor | Package-private; instances only come from `register` | `quest/task/TaskType.java` | Verified |
| `CustomTaskEvent` | Runtime hook for the built-in `custom` task type | `events/CustomTaskEvent.java` | Verified (not used by this project) |

Both registries are plain static `LinkedHashMap`s. Registration happens at class
load, which for a NeoForge mod means loading the interface from the mod
constructor (or a `RegistryEvent`-style entry). Ids use our namespace, e.g.
`tensura_quests:job_mastery`.

**Stability caveat (documented risk, R4):** `TaskTypes`/`RewardTypes` live in
`dev.ftb.mods.ftbquests.quest.*`, **outside** the `dev.ftb.mods.ftbquests.api`
package that `FTBQuestsAPI`'s javadoc declares as the only stable surface
("don't count on classes and methods outside the api package to remain
unchanged"). Every FTB Quests class this project touches goes through the
`FtbQuestsBridge` facade, and the GameTest suite re-verifies registration and
serialization on every build.

## Task contract

From `quest/task/Task.java` (abstract base):

- Constructor `Task(long id, Quest quest)`; id is the quest-file object id.
- `getMaxProgress()` - tasks with a finite max auto-complete when progress
  reaches it; override for open-ended tasks.
- `submitTask(TeamData, ServerPlayer, ItemStack)` - interaction hook (right
  click with item); default no-op.
- `checkOnLogin()` / `autoSubmitOnPlayerTick()` - polling hooks for
  state-derived tasks. `autoSubmitOnPlayerTick` returns `TickTask`, enabling
  delay/throttle; this is the correct hook for "check the player's job level"
  style tasks.
- Serialization: `writeData(CompoundTag)` / `readData(CompoundTag)` for quest
  SNBT (the saved quest file), `writeNetData` / `readNetData`
  (`RegistryFriendlyByteBuf`) for client sync of configuration, and
  `fillConfigGroup(ConfigGroup)` for the quest-editor configuration UI.
- Display name resolves through the translation key pattern
  `ftbquests.task.<namespace>.<path>`.

## Progress and completion

From `quest/task/Task.java` and `quest/TeamData.java`:

- Progress lives in `TeamData.taskProgress`, a `Long2ObjectMap` keyed by task
  object id - **per FTB Team, not per player**. `TeamData.addProgress` /
  `setProgress` clamp to `[0, maxProgress]`, sync to online team members via
  `UpdateTaskMessage`, and fire `ObjectStartedEvent` / `ObjectCompletedEvent`.
- Stock FTB Quests has no per-player task progress. `PerPlayerData` holds only
  editor/pin state. Any per-player semantics must be stored by the addon and
  mirrored into team progress (ADR-0002).
- Persistence: `TeamData.serializeNBT` writes into the per-team
  `FTBQUESTS_DATA` saved data; survives restarts and quest reloads
  (reconciled by `readData` on file load).

## Reward contract

From `quest/reward/Reward.java`:

- `claim(ServerPlayer, boolean check)` returns `boolean` - the exact hook for
  idempotent, verifiable rewards. `check=true` honours `ClaimRewardEvent.Pre`
  (cancellable by other mods); `false` is the automated path.
- Claims are tracked **per player** (`QuestKey` = player UUID + reward id), so
  duplicate-claim protection exists per reward.
- Per-reward team scope is a `Tristate` (forced team-wide, forced per-player,
  or follow file default) - relevant for job-slot rewards, which must always
  be per-player.

## Client sync and editor

Configuration (`writeNetData`) reaches the client automatically; the quest
editor picks up new task/reward types from the registries with no extra work.
Icons come from the registration `Supplier<Icon>`.

## Versions and coordinates

| Version | File | CurseMaven coordinate |
|---|---|---|
| 2101.1.36 (brief pin) | ftb-quests-neoforge-2101.1.36.jar | `curse.maven:ftb-quests-289412:8885017` |
| 2101.1.37 (live instance) | ftb-quests-neoforge-2101.1.37.jar | `curse.maven:ftb-quests-289412:9298629` |

Required dependencies of FTB Quests (from the shipped `neoforge.mods.toml` of
2101.1.37): `architectury [13.0.8,)`, `ftblibrary [2101.1.36,)`,
`ftbteams [2101.1.9,)`.

## Limitations recorded

1. Registration API is outside the stable `api` package (see caveat above).
2. Task progress is per-team only (see ADR-0002 for the per-player overlay).
3. `Task`/`Reward` are abstract classes, not interfaces - inheritance is
   mandatory, so the base classes are pinned with the version.
