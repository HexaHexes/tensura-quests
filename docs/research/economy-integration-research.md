# Economy integration research (Lightman's Currency)

**Scope:** Lightman's Currency (LC) and related pack economy mods, for
transaction-verified economy tasks. **Method:** source tree and API reading of
`Lightman314/LightmansCurrency` (GitHub, `main` branch), cross-checked
against the version pinned in the pack. **Date:** 2026-10-30.

## Verified (on LC `main`)

- A public API package exists: `io.github.lightman314.lightmanscurrency.api`
  with `LCApi`, `MoneyAPI` (money value handling, `MoneyView`, `MoneyStats`),
  bank-account API, notifications, and trader APIs.
- **`TradeEvent`** exists at `api/trader/event/TradeEvent.java`: a cancellable
  `Event` fired around trades with a nested **`Post`** notification carrying
  the completed trade context - customer (`TradeCustomer`), trader data, and
  the price receipts (including `MoneyPrice` with the amount paid and taxes).
  This is the correct intake point for "a genuine transaction completed".
- The `api` package is annotated `@ApiStatus`-controlled in places; treat the
  exact event field set as version-sensitive.

## The version gap (critical)

- The pack ships **LC 2.3.0.6** (pinned as
  `curse.maven:lightmans-currency-472521:9048585`, the coordinate used by
  `lightmans-tensura`).
- The GitHub `main` branch targets a **newer Minecraft** (it uses
  `Identifier` where 1.21.1 uses `ResourceLocation`), so main is not evidence
  for 2.3.0.6's exact surface.
- **Parity of `TradeEvent.Post` on 2.3.0.6 is UNVERIFIED.** Resolution path:
  `javap` the pinned jar (the same technique that verified Sympathetic Jobs),
  run before Phase 5 design is written. This is Phase 5's entry gate.

## Player-to-player trading (open question)

The brief's economy tasks include player-to-player transactions. Whether LC
2.3.0.6 has a direct P2P trade flow (as opposed to trader-block transactions
and bounties) is **unverified**. Consequences if it does not exist:

- "Complete a player-to-player transaction" and "trade with N distinct
  players" tasks have no authoritative event source in LC alone.
- Fallback options to evaluate then: (a) define the transaction via specific
  LC trader types owned by players, (b) use LC Bounties flows, (c) request an
  additive P2P trade event from the economy domain owner. Decision deferred
  until the jar inspection - no requirement may be invented before then.

## Anti-exploit ground rules (from this research)

- Count only completed-trade events (`Post`), never inventory diffs.
- Attribute buyer and seller separately; both sides of one trade must not
  double-count toward the same "distinct partners" requirement.
- Self-trades (buyer UUID == seller UUID) and trades between accounts of the
  same owning player are rejected by policy, not by luck.
- Cancelled/refunded trades never reach `Post` and therefore never count -
  to be confirmed against 2.3.0.6 behavior in Phase 5 tests.

## Adjacent components (no overlap, context only)

- `lightmans-tensura` (org) replaces Tensura coins with LC coins/wallets;
  verified LC surface on our MC version there: `WalletItem`, `WalletData`,
  `ModDataComponents`.
- `lc_ftb_hook` 4.4.2 (ARR) is claims/wars infrastructure, not a trade-event
  source.
- Lightman's Tensura Economy, Lightman's Currency Bounties, Bountiful,
  Bountiful NPC, ExtraBounties: pack components noted in the brief; none
  verified as transaction sources in this phase - revisit in Phase 5 design.

## Unverified

- `TradeEvent.Post` field parity on 2.3.0.6 (javap gate, Phase 5).
- P2P trading existence on 2.3.0.6 (Phase 5).
- Whether `TradeEvent.Post` fires for terminal/NPC sales with a null customer
  (affects "sales milestone" attribution rules).
