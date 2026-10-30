# Architecture overview

Tensura Quests is the FTB Quests integration layer of the Tensura RPG pack.
It registers custom task and reward types; it never owns gameplay state it
does not author, and FTB Quests keeps questbook presentation, dependencies,
completion and reward display.

## Repositories (ADR-0001)

```text
tensura-quests-api          pure contracts; no FTB Quests, no gameplay deps
  published: org.bitwisemadness.tensuraquestsapi:tensura_quests_api
        ^
        | compileOnly / runtimeOnly / testImplementation (GitHub Packages)
        |
tensura-quests              the implementation mod
  required: NeoForge, FTB Quests, FTB Library, FTB Teams, tensura-quests-api
  optional: Sympathetic Jobs, Infinite Dungeon suite, Lightman's Currency
```

## Module layout (tensura-quests)

```text
org.bitwisemadness.tensuraquests/
  framework/      registration facade (FtbQuestsBridge), base task/reward
                  classes, config codecs, event intake validation
  progress/       per-player persisted counters + completion policies (ADR-0002)
  integrations/   sjobs/ dungeon/ economy/ capital/ - each optional, each
                  isolated behind one adapter, each ModList-gated
  tasks/ rewards/ concrete types - thin, delegating to modules
  diagnostics/    structured logging: why a task progressed, why an event
                  was rejected, which integration produced it
```

## Data flow

```text
authoritative gameplay system          this mod                     FTB Quests
(Sympathetic Jobs, dungeon, LC,   ->  intake: validate context ->  TeamData progress
 future capital system)                (dimension, layer, ids)      mirrors policy
        |                                  |                            |
        | state APIs (read)                | per-player counters        | SNBT +
        | events (notifications)           | (own persistence)          | client sync
        v                                  v                            v
   owns the truth                    logs accept/reject            quest completes
                                     and reasons                   -> rewards claim
                                                                    (our rewards call
                                                                     back into the
                                                                     state APIs)
```

## Core rules

1. **Server authority.** All validation and progress runs server-side; client
   data never grants permanent progression or rewards.
2. **Single ownership.** Job state belongs to Sympathetic Jobs, dungeon state
   to the dungeon suite, money to Lightman's Currency, capital state to the
   (future) capital system, team identity to FTB Teams. We read; we never
   keep a competing copy.
3. **Team identity chain.** Better Party is the party authority; Better Party
   X FTB Teams mirrors parties into FTB Teams; our tasks consume FTB Teams
   only (see compatibility matrix).
4. **Per-player progress is ours.** ADR-0002: personal counters live in our
   persisted storage; the FTB task completes per its policy.
5. **Optional means isolated.** A missing optional mod disables only its own
   task/reward family, with a log line - never a crash, never a silent
   success.
6. **Loud failures.** A reward that cannot execute returns failure and logs;
   it is never recorded as granted.
7. **Version pins are law.** Integration points compile against pinned
   versions (compatibility matrix); version-sensitive contact is funneled
   through one facade/adapter per external system.

## Non-duplication boundary

`tensura_ftb` (ManasCore, `io.github.manasmods`) owns Tensura
race/spirit/ability/awakening/existence quest content. This project covers
jobs, job slots, dungeon, economy and capitals only.
