# Capital system integration research

**Scope:** the Faction Capitals system described in the project brief (plot
ownership, purchases, contributions, development, permissions, migrations) and
what actually exists in the codebase today. **Date:** 2026-10-30.

## Headline finding

**The capital system does not exist as code yet.** The brief describes it as
authoritative for plot ownership, purchases, contributions, development,
permissions and migrations - none of that is implemented in any repository of
the org or in the live pack instance. What exists today is the *placement*
layer:

## What exists (verified from source)

### `tensura-faction-spawn`

- Datapack-driven race-to-faction-group mapping via ManasCore race tags
  (`data/<ns>/tags/manascore_race/races/<group>.json`).
- On first race pick or reincarnation, teleports the player to the nearest
  registered capital city to world spawn; evolution never teleports.
- Capitals are discovered from structure tags; capital cities ship in
  separate datapack/mod repos.
- No ownership, no plots, no economy.

### `tensura-capital-mortals`

- Registers capital city *structures* for Humans, Elves and Dwarves (gated
  town, ancient-forest clearing, mountain gatehouse). Pure worldgen content.

### Adjacent, third-party

- `lc_ftb_hook` 4.4.2 (ARR, closed) implements Lightman's Currency paid
  claims and wars over FTB Chunks regions - a claims/permissions system, but
  not capital plots, and not ours to build on.
- `tensura-compat-ftb` / `tensura_ftb` handles Tensura abilities inside FTB
  claims; no capital concepts.

## Consequences for this project

1. **Phase 6 scope is contract definition only** (agreed): define the outbound
   event contracts in `tensura-quests-api` that a future capital
   implementation must fire (plot purchased, contribution recorded, capital
   developed, permission granted), so quest tasks can be written against a
   stable interface before the implementation lands.
2. Capital tasks (own plot, contribute, develop, use service) cannot be
   implemented or tested until an authoritative implementation fires those
   events or exposes query state. No ownership data may ever be stored on the
   quest side (established rule: the capital system stays the single owner).
3. Ownership tasks must respect the established migration rule: purchases
   remain anchored to original recorded world coordinates across migrations.
   Quest progress keyed to a plot must key on the plot's stable identity, not
   its coordinates - this constraint is written into the contract now so a
   future implementation cannot design itself into violating it.

## Co-design requirement

The capital contracts require sign-off from whoever owns
`tensura-faction-spawn`/`tensura-capital-mortals` before Phase 6
implementation. Open design questions are tracked in
`technical-unknowns.md`.
