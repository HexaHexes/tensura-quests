# ADR-0001: Core + api repository split

- **Status:** Accepted (user decision, 2026-10-30)
- **Date:** 2026-10-30

## Context

The ecosystem must host custom FTB Quests task and reward types across five
domains (jobs, slots, dungeon, economy, capitals), and future gameplay
systems must be able to drive quest progress by firing events - most
imminently the Faction Capitals system, which does not exist yet and should
not need to depend on FTB Quests or on the quest implementation to publish
its integration contract.

## Problem

Choose the repository and artifact architecture: one modular mod, a
core-plus-api split, or one repo per domain.

## Options considered

1. **Single repo, one mod.** Simplest CI/docs; every consumer depends on the
   implementation mod for contracts.
2. **Core + api split (two repos).** Contracts live in a small published
   library; the implementation consumes it. Matches the org's existing
   `infinite-dungeon` / `infinite-dungeon-api` pattern.
3. **One repo per domain.** Maximum isolation; roughly quadruples CI, docs
   and publishing overhead for a single pack, with no distribution benefit.

## Decision

Option 2, effective immediately:

- `tensura-quests-api` (`mod_id` `tensura_quests_api`, group
  `org.bitwisemadness.tensuraquestsapi`) - pure contracts: task/reward type
  identifiers, outbound integration events (ids only, never live entities),
  shared version constants. **Must never depend on FTB Quests, the
  implementation, or any gameplay mod.** Published to the org's GitHub
  Packages registry from day one.
- `tensura-quests` (`mod_id` `tensura_quests`, group
  `org.bitwisemadness.tensuraquests`) - the implementation: framework,
  progress storage, integrations, concrete task/reward types. Consumes
  `api_version` from GitHub Packages; declares a minimum api range in its
  mods.toml.

## Rationale

- Producers (capital system, pack tooling) can compile against the api
  without dragging in FTB Quests; this is the whole point of the outbound
  contract.
- The pattern is already proven in this org (infinite-dungeon suite), so the
  CI, publishing and version-skew machinery is established.
- Option 3 was rejected as overhead without benefit for a single-pack
  project; option 1 was rejected because it forces contract consumers to
  depend on the implementation.

## Consequences

- Two repos mean two CI setups, two release trains and version-skew
  management. The api is semantic-versioned; the implementation declares a
  minimum range and pins `api_version` deliberately - the pin must always
  name a published version.
- The api must not become a dead artifact: its consumers are the capital
  system and pack content; contracts land with their domain designs, not
  speculatively.
- Cross-repo CI resolution needs the org's step-scoped `PACKAGE_READ_TOKEN`
  pattern on every Gradle step (pseudocore#85).
- If a second consumer never materializes, the split costs remain but stay
  small; a future consolidation would be its own ADR.
