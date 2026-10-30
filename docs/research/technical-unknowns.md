# Technical unknowns

Open questions from Phase 0, each with its resolution path, owner phase and
impact if unresolved. Nothing here may be treated as settled. **Last
updated:** 2026-10-30.

| # | Unknown | Resolution path | Gate | Impact if unresolved |
|---|---|---|---|---|
| U-01 | `TradeEvent.Post` parity on LC 2.3.0.6 (main branch targets newer MC) | `javap` the pinned jar `curse.maven:lightmans-currency-472521:9048585` | Phase 5 entry | Economy tasks cannot be designed |
| U-02 | Does LC 2.3.0.6 have direct player-to-player trading? | Same jar inspection + pack config review with the economy owner | Phase 5 design | "P2P transaction" and "distinct partners" tasks may need a different authoritative source or a new LC event |
| U-03 | Exact `/sjobs` slot subcommand surface | `javap` on `JobsCommands` constants; live-server trial | Phase 3 docs | Admin tooling docs only; D3 uses the state API, not commands |
| U-04 | Sympathetic Jobs internal-class stability across updates | Adapter isolation (D3), pinned 1.0.13, GameTest exercising the adapter | Continuous | Job-slot reward fails loudly on mismatch (accepted risk R3) |
| U-05 | FTB Quests registration API stability across 2101.1.x | `FtbQuestsBridge` facade; registration + serialization GameTests every build | Continuous | Same loud-failure discipline (accepted risk R4) |
| U-06 | `infinite-dungeon` core firing sites for layer/boss events (who is "the completer" in multiplayer) | Read core source during the D2 change | D2 implementation | Attribution would be guessed - not acceptable |
| U-07 | Capital system implementation itself does not exist | Co-design of contracts in `tensura-quests-api` with the capital owners | Phase 6 | Capital tasks stay blocked by design; contract-only scope accepted |
| U-08 | `QuestObject.getEffectiveId()` semantics in completion checks | Source read during Phase 2 | Phase 2 | Minor; affects how tasks reference quest objects |
| U-09 | Whether `TradeEvent.Post` fires for terminal/NPC sales with no player customer | Jar + source inspection in Phase 5 | Phase 5 | Sales-milestone attribution rules |
| U-10 | Whether a dungeon layer can complete with zero players present | Core source read with U-06 | Phase 4 | ALL-policy edge case |

## Resolved during Phase 0 (recorded for traceability)

- Per-player job-slot entitlement exists in Sympathetic Jobs
  (`JobsPlayerState.setSlotLimitOverride`) - resolved by bytecode inspection;
  drove decision D3.
- FTB Quests task progress is per-team only - resolved by source reading;
  drove decision D4/ADR-0002.
- The de facto team system is Better Party mirrored into FTB Teams - resolved
  from the live instance jars; our tasks consume FTB Teams only.
- The capital ownership system does not exist yet - resolved by repo and
  instance survey; drove the Phase 6 contract-only scope.
- tensura_ftb already covers Tensura core progression quest types - resolved
  from the live instance jar; hard non-duplication boundary.
