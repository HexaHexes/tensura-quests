# Job system integration research (Sympathetic Jobs)

**Scope:** Sympathetic Jobs 1.0.13 for NeoForge 1.21.1 - per-player job-slot
support, state APIs, persistence. **Method:** `javap` bytecode inspection of
the released jar (Modrinth CDN, `sympatheticjobs-1.0.13.jar`, CurseForge
project 1633306, published 2026-10-10). The mod is **closed source, All
Rights Reserved** - no source reading is possible and no code may be copied.
**Date:** 2026-10-30.

## Verified API surface (bytecode-level)

### `dev.fatsan.sympatheticjobs.api.JobId` (the published api package)

- Enum with exactly the 12 jobs our design assumes: MINER, ARTISAN, BREWER,
  FIGHTER, HUNTER, ENCHANTER, EXPLORER, FARMER, COOK, SHAMAN, LUMBERJACK,
  GUARDIAN.
- Implements `StringRepresentable`; carries `CODEC`, `NAMESPACE`, `ALL`, and
  an `AbilityKind` sub-enum.

### `dev.fatsan.sympatheticjobs.common.state.JobsStateManager` (internal package)

- `effectiveSlotLimit(ServerPlayer) -> int` - the authoritative current slot
  limit for a player.
- `hasFreeSlot(ServerPlayer) -> boolean`
- `get(ServerPlayer) -> JobsPlayerState` / `set(...)`
- `reconcileAndSync(ServerPlayer)` / `sync(...)` - recompute derived state and
  push to the client after a mutation.
- `awardXp(ServerPlayer, JobId, double)`, `removeJob(...)`, `addJob(...)` via
  the state, cooldown helpers.

### `dev.fatsan.sympatheticjobs.common.state.JobsPlayerState` (internal package)

- `slotLimitOverride()` / `setSlotLimitOverride(...)` - **a per-player slot
  limit override exists and is settable at runtime.** This is the entitlement
  mechanism for job-slot unlock rewards (decision D3).
- `level(JobId)`, `progress(JobId) -> JobProgress`, `hasJob(JobId)` - the
  authoritative per-player job state that Job Mastery tasks read.
- Serializable via a `CODEC`.

### Persistence and lifecycle

- `JobsAttachments.PLAYER_STATE` - the state is a **persistent NeoForge
  player attachment**, so slot overrides and job progress survive restarts and
  re-joins without any help from us.
- `JobGrantReconciler` - reconciles job grants keyed by
  `ResourceLocation` grant sources (a data-driven grant pattern exists; exact
  data format unverified, see unknowns).
- Commands register under `/sjobs` (aliases `sjobs`, `sympatheticjobs`);
  per-player slot management commands exist per the mod's description, but the
  exact subcommand surface is not yet enumerated.

## Design consequences

1. **D3 (approved):** the Unlock Job Slot reward calls
   `JobsPlayerState.setSlotLimitOverride` + `JobsStateManager.reconcileAndSync`
   directly, behind a single adapter class, compiled against the pinned
   1.0.13 jar. A version mismatch must produce a logged reward failure, never
   a silent no-op.
2. Job Mastery tasks read `JobsStateManager.get(player).level(jobId)` through
   the same adapter. Because XP flows to all acquired jobs regardless of the
   active one (established design), mastery must read job state, never the
   "active job" - the state API supports exactly that.
3. We do **not** keep a second slot counter anywhere; Sympathetic Jobs'
   attachment is the only slot truth (established design rule).

## Risks

- **R3:** the entitlement lives in non-`api` internal classes of a closed mod.
  Mitigation: single adapter, pinned version, loud failure, compatibility
  matrix row. A Sympathetic Jobs update that renames or removes these classes
  breaks only the adapter, and the GameTest that exercises it.
- The jar's internals are verified at bytecode level only; behavioral details
  (when the reconciler runs, how overrides interact with config limits) are
  assumed from signatures and will be confirmed in Phase 3 integration tests.

## Unverified

- Exact `/sjobs` slot subcommand syntax (admin tooling docs, Phase 3).
- The grant-source data format behind `JobGrantReconciler` (Phase 3; the
  direct state API makes it unnecessary for slots).
- Whether `setSlotLimitOverride` accepts lowering below the config default and
  how it interacts with already-acquired jobs (Phase 3 test).
