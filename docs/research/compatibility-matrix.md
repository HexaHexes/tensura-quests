# Compatibility matrix

Pinned dependency versions for compilation and runtime, with verification
sources and known drift. Every row names the exact coordinate used in
`build.gradle` (CurseMaven pins are file ids, never floating versions).
**Last verified:** 2026-10-30.

## Platform

| Component | Pinned version | Verification |
|---|---|---|
| Minecraft | 1.21.1 | Brief; live instance |
| NeoForge | 21.1.259 | Brief; org template; live instance |
| JDK | 21 | Org template toolchain |

## FTB stack (compile + runtime pins)

| Mod | Compile pin | Coordinate | Live instance | Drift note |
|---|---|---|---|---|
| FTB Quests | 2101.1.36 (brief) | `curse.maven:ftb-quests-289412:8885017` | **2101.1.37** (`9298629`) | Instance is one patch ahead of the brief; mod range `[2101.1.36,)` accepts both. Re-pin if a 2101.1.37-only API appears |
| FTB Library | 2101.1.37 (brief) | `curse.maven:ftb-library-forge-404465:9008089` | **2101.1.39** (`9350706`) | Same situation; range `[2101.1.37,)` |
| FTB Teams | 2101.1.11 | `curse.maven:ftb-teams-404468:8724782` | 2101.1.11 | Matches |
| Architectury API | 1.21.1 build | `curse.maven:architectury-419699:8492726` | present | FTB Quests requires `[13.0.8,)` |
| FTB Chunks | 2101.1.22 (context) | not a compile dep | 2101.1.22 | Pack context only |

## Gameplay integrations

| Mod | Pin | Source of verification | Status |
|---|---|---|---|
| Sympathetic Jobs | 1.0.13 | Modrinth CDN jar, `javap` inspection (CurseForge 1633306) | Closed source (ARR); adapter-isolated per D3 |
| Lightman's Currency | 2.3.0.6 | `curse.maven:lightmans-currency-472521:9048585` (coordinate from `lightmans-tensura`) | **`TradeEvent` parity unverified on this version** - Phase 5 gate |
| Infinite Dungeon API | repo `main` (local) | org source | D2 attribution change pending |
| Better Party | 1.1.7 | live instance jar | Not a direct dependency; party authority behind FTB Teams |
| Better Party X FTB Teams | 1.0.1 | live instance jar | The bridge that makes FTB Teams the quest-side team truth |
| Quest Kill Task Tweaks | 0.3.1+1.21.1 | live instance jar | Pack-side; GPLv3, no code reuse |

## Non-duplication boundary

| Mod | Version | Boundary |
|---|---|---|
| tensura_ftb (Tensura Compat: FTB, `io.github.manasmods`) | 2.0.0.4 | Owns Tensura race/spirit/ability/awakening/existence quest types and rewards. This project must not duplicate them |
| lc_ftb_hook | 4.4.2 | Owns LC claims/wars over FTB Chunks; not a quest integration |

## This project's artifacts

| Artifact | Version | Consumed by |
|---|---|---|
| `org.bitwisemadness.tensuraquestsapi:tensura_quests_api` | 1.0.0-alpha.1 (published to GitHub Packages) | `tensura-quests` (`compileOnly`/`runtimeOnly`/`testImplementation`, range `[1.0.0-alpha.1,)` in mods.toml) |
| `tensura_quests` | 1.0.0-alpha.1 | The pack |

## Team identity chain (runtime topology)

```text
Better Party 1.1.7  (authoritative party manager)
        |  PartySyncService (~1s mirror, preserves team ids, corrects manual FTB edits)
        v
FTB Teams 2101.1.11  (what FTB Quests TeamData keys by; our only team input)
```

## Policy

- Re-verify this matrix whenever a pin moves. A pin moves only with a reason
  recorded here.
- The brief's pins govern compilation; the live instance drift is recorded,
  not silently adopted. Aligning the brief to the instance is a
  maintainer decision.
