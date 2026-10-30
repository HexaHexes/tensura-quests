# Tensura Quests

Custom FTB Quests task and reward types for the Tensura RPG modpack. It adds
objectives and payouts that vanilla FTB Quests cannot express - job mastery,
job-slot unlocks, Infinite Dungeon milestones, economy transactions and Faction
Capitals - while FTB Quests itself keeps doing what it always did: the
questbook, dependencies, completion and rewards display.

## What it does

- **Job objectives** - quest tasks that read your real Sympathetic Jobs state:
  reach a mastery level in a job, hold several jobs at once, or unlock a job
  slot. The job system stays the source of truth; the quest only reads it.
- **Job-slot rewards** - a quest reward that grants an extra job slot through
  Sympathetic Jobs' own entitlement state, so the unlock persists everywhere
  the job system looks.
- **Dungeon milestones** - tasks that count real Infinite Dungeon progress:
  floors entered, layers completed, minibosses slain, validated against the
  dungeon dimension you were actually in.
- **Economy tasks** - objectives driven by genuine Lightman's Currency
  transactions, not by carrying items around.
- **Faction Capitals tasks** - ownership and contribution objectives, once the
  capital system ships its contracts.

Every task and reward validates against the gameplay system that owns the
state. Nothing here duplicates a gameplay system, and no progress is granted
from client-side data.

## Getting it in-game

Install the mod (and its dependencies) on the server; the new task and reward
types then appear in the FTB Quests editor when you build a quest. Players
need no actions beyond playing: progress updates automatically from the
gameplay systems.

## Dependencies

| | Mod | Why |
|---|---|---|
| Required | Minecraft | - |
| Required | NeoForge | - |
| Required | FTB Quests | Hosts the custom task and reward types |
| Required | FTB Library | FTB Quests' own dependency |
| Required | FTB Teams | Quest progress is keyed by team; parties sync into FTB Teams via Better Party X FTB Teams |
| Required | Tensura Quests API | The contracts this mod implements |
| Optional | Sympathetic Jobs | Job tasks and job-slot rewards; absent, those types refuse to progress |
| Optional | Infinite Dungeon + API | Dungeon tasks; absent, those types refuse to progress |
| Optional | Lightman's Currency | Economy tasks; absent, those types refuse to progress |

## Credits

- Built on [NeoForge](https://neoforged.net/).
- Uses [FTB Quests](https://github.com/FTBTeam/FTB-Quests) (All Rights
  Reserved, FTB Team).

## License

GNU Lesser General Public License v2.1 - see [`LICENSE`](LICENSE).
Copyright (c) 2026 Bitwise Madness.

## AI disclosure

Parts of this project were written with the assistance of AI coding tools.
