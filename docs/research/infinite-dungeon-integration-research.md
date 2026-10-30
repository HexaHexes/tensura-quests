# Infinite Dungeon integration research

**Scope:** the `infinite-dungeon` suite (`infinite-dungeon`,
`infinite-dungeon-api`, satellites) for event and state integration.
**Method:** direct source reading of `infinite-dungeon-api` (local repo,
org-owned). **Date:** 2026-10-30.

## Verified event contract (`infinite-dungeon-api`)

All events extend `DungeonEvent`, which carries the `ResourceKey<Level>` the
event happened in. The package documents them as **notification only**, fired
server-side by the core, with **no guaranteed ordering between events** -
consumers must treat each event as self-sufficient.

| Event | Payload | Player attribution |
|---|---|---|
| `FloorEnteredEvent` | dimension, `playerId` (UUID), layer (>=0), floor (>=0) | **Yes** |
| `LayerCompletedEvent` | dimension, layer | **No** |
| `BossSlainEvent` | dimension, layer, boss entity | **No** (entity only) |
| `GateUnlockedEvent` | dimension, gateId | **No** |

Design note on `FloorEnteredEvent`: it deliberately carries the player's UUID
rather than the player entity, so listeners cannot retain an entity - the same
discipline applies to any attribution we add.

## Decision D2 (approved): additive player attribution

`LayerCompletedEvent` and `BossSlainEvent` gain player identity **without
breaking existing signatures**, with a minor version bump of the api. The
attribution semantics to implement:

- `BossSlainEvent`: the killing player where a player kill is attributable;
  for non-player kills the field carries a documented "unknown" value rather
  than a guess (no fabricated attribution).
- `LayerCompletedEvent`: the completing player (the player whose run
  completed the layer), matching how the core validates completion.

Implementation requirements recorded for the change:

- Read the core's firing sites in `infinite-dungeon` to attribute correctly -
  the event classes alone cannot tell us who was "the" completer when several
  players are in a layer. Individual vs shared credit remains the *consumer's*
  policy (ADR-0002); the event must state what happened, not decide credit.
- Keep the UUID-not-entity discipline.
- The dungeon suite's own CI must stay green; add a GameTest asserting the
  payload.

## Consumer design (Phase 4)

- Tasks validate the dungeon context themselves: dimension must match the
  configured dungeon dimension(s), layer/floor must satisfy the configured
  requirement, and events from unrelated dimensions are rejected with a log
  line (SEC requirements).
- Individual vs shared progression is expressed via the completion policies
  of ADR-0002: the per-player counter increments for the acting player; the
  team task completes per policy (ANY by default, ALL configurable).
- The suite is actively expanding (dimension satellites for nether, end,
  undergarden, aether, better-nether, better-end, blue-skies, outer-end
  appeared during this investigation). Events must remain the stable contract;
  tasks therefore reference dimension configuration, never hardcoded
  dimension ids.

## Unverified

- Exact firing sites and multi-player attribution semantics in the core -
  read during the D2 implementation.
- Whether a layer can complete without any player present (edge case for the
  ALL policy).
