# Existing addons analysis

**Scope:** existing FTB Quests addons and adjacent integration mods, checked
for functionality overlap with this project. **Date:** 2026-10-30.

## Summary table

| Addon | Version seen | License | What it does | Overlap with us |
|---|---|---|---|---|
| ExtraQuests | 1.6.x / 1.5.10 for 1.21.1 (Modrinth) | MIT | Key-value tasks and rewards, console-command reward option, daily/weekly task scaffolding | None in our domains; useful as a registration-pattern reference |
| Quests Kill Task Tweaks | 0.3.1+1.21.1 (in live instance) | GPLv3 | Improves kill-credit contribution tracking for FTB Quests kill tasks | None; pattern reference only (see license note) |
| FTB XMod Compat | 21.1.14 for 1.21.1 | ARR (FTB) | KubeJS event bridge + gamestages fallback for FTB mods | None; relevant only if pack scripting later needs quest events |
| tensura_ftb (Tensura Compat: FTB) | 2.0.0.4 (in live instance) | see repo | By `io.github.manasmods`: Race, Spirit, Ability, Awakening, Existence **task types and rewards**, claim-safe Tensura abilities, subordinate kill credit | **Hard non-duplication boundary:** Tensura core progression is theirs |
| lc_ftb_hook | 4.4.2 (in live instance) | ARR (closed) | Lightman's Currency x FTB Chunks/claims: paid claims, wars, team links, tax collectors | Different domain (claims/economy infrastructure), no quest tasks |
| open-quests-api | seen on Modrinth search only | unknown | Unverified | Investigate before Phase 2 if it exposes a stable task API |

## Findings

### ExtraQuests (MIT, open source)

Adds generic key-value progress tasks and matching rewards, plus a command
reward variant. Good candidate for pack-level glue (for example, one-off
admin-driven objectives), but it models no domain state - it cannot read job
levels, dungeon layers or transactions. No dependency conflict with our stack.
Serves as a **reference implementation** of 1.21.1 task-type registration
patterns under a permissive license.

### Quests Kill Task Tweaks (GPLv3)

Fixes attribution of kill credit (including contribution from other players)
for vanilla FTB Quests kill tasks. Installed in the pack for kill quests.
**License note:** GPLv3; this project is LGPL-2.1. No code may be copied - the
per-player contribution-storage pattern may serve as a design reference only,
at the level of ideas observable from behavior and documentation.

### FTB XMod Compat (ARR)

Official FTB bridge adding KubeJS integration and gamestage gating. Not needed
for typed custom tasks; recorded so pack authors know stage-gated quest
variants are already available without custom Java.

### tensura_ftb 2.0.0.4 - the boundary

Ships `RaceTask`, `SpiritTask`, `AbilityTask`, `AwakeningTask`, `ExistenceTask`
and matching rewards (`RaceReward`, `SpiritReward`, `AbilityReward`,
`AwakeningReward`, `RandomAbilityReward`), plus mixins that gate Tensura
abilities inside FTB claims and give subordinates' kills to the owner's
quests. It is the **official Tensura/ManasCore FTB Quests integration**. This
project must never add task or reward types for race, spirit, ability,
awakening or existence state - those exist and are authoritative there. Our
scope stays: jobs, job slots, dungeon, economy, capitals.

### lc_ftb_hook 4.4.2

Bridges Lightman's Currency to FTB Chunks claims (paid claims, wars, tax
collector placement, team links). Closed source. No quest-objective overlap;
relevant background for the future capital/claims domain (Phase 6) so we do
not reinvent claim-based permissions.

## Conclusion

No existing addon covers job progression, job-slot entitlements, Infinite
Dungeon milestones, validated economy transactions or capital ownership for
FTB Quests on 1.21.1. Custom development is justified with no functional
duplication, given the tensura_ftb boundary above.
