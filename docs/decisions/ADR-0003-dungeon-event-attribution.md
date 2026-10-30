# ADR-0003: Additive player attribution in infinite-dungeon-api

- **Status:** Accepted (user decision, 2026-10-30)
- **Date:** 2026-10-30

## Context

Dungeon quest tasks must attribute progress to players
(`docs/research/infinite-dungeon-integration-research.md`). In
`infinite-dungeon-api`, `FloorEnteredEvent` carries a player UUID, but
`LayerCompletedEvent` carries only dimension and layer, and `BossSlainEvent`
carries only dimension, layer and the boss entity. Without attribution,
layer-completion and boss-slain objectives cannot be credited to a player.

## Problem

How to obtain player attribution for layer completions and boss kills.

## Options considered

1. **Additive attribution in the api (change the event payloads).** Clean,
   exploit-resistant, but touches an established org repo.
2. **Correlate from `FloorEnteredEvent` only.** No upstream change; infer
   who completed a layer from who was present. Weak: cannot prove who killed
   a boss, and presence is not causation - exploitable.
3. **Defer dungeon integration.** Avoids the issue; delays the domain.

## Decision

Option 1, additively:

- `BossSlainEvent` gains the killing player's UUID where the kill is
  player-attributable; non-player kills carry a documented "unknown" value
  rather than a guess.
- `LayerCompletedEvent` gains the completing player's UUID, matching the
  core's completion validation.
- Existing constructors/signatures are preserved (overloads), so no existing
  consumer breaks; the api takes a minor version bump.
- Events keep the suite's UUID-not-entity discipline (listeners must not
  retain entities).
- The event states *what happened*; deciding individual vs shared quest
  credit remains the consumer's policy per ADR-0002.

## Rationale

- Attribution belongs where the fact is produced; downstream correlation
  guesses at facts it cannot know.
- The additive form is the org's established evolution path for these APIs
  and costs consumers nothing.
- The change is useless without correct attribution semantics, so the
  implementation must first read the core's firing sites (tracked as U-06);
  guessing semantics would be worse than no field at all.

## Consequences

- Work lands in `infinite-dungeon-api` (and possibly the core's firing
  sites) under its own PR, tests and version bump; the dungeon suite's CI
  must stay green.
- Phase 4 of this project depends on that release; until it ships, dungeon
  tasks that need attribution stay blocked (floor-entry tasks do not).
- Other suite consumers gain the same attribution - additive value beyond
  this project.
