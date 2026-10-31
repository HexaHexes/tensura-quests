package org.bitwisemadness.tensuraquests.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The economy decision layer as pure code (SEC-004's rule half,
 * FR-041, FR-043, FR-044): self-trade rejection, team policy, value
 * sanitation and side/counterparty symmetry - all pinned without
 * Minecraft or Lightman's Currency on the classpath (issue #8). The
 * completed-trade-only boundary itself lives in the adapter's single
 * subscription and is covered by the event-discrimination GameTest.
 */
public class EconomyRulesTest {

    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    public void selfTradesAreRejected() {
        TradeFact self = new TradeFact(BUYER, Optional.of(BUYER), 100L);
        assertEquals(Optional.of(EconomyRules.Rejection.SELF_TRADE), EconomyRules.reject(self));
    }

    @Test
    public void tradesBetweenDifferentPlayersAndOwnerlessTradesAreAccepted() {
        TradeFact twoPlayers = new TradeFact(BUYER, Optional.of(SELLER), 100L);
        TradeFact ownerless = new TradeFact(BUYER, Optional.empty(), 100L);
        assertEquals(Optional.empty(), EconomyRules.reject(twoPlayers));
        assertEquals(Optional.empty(), EconomyRules.reject(ownerless));
    }

    @Test
    public void teamPolicyDeniesSameTeamTradesByDefaultShape() {
        // Default (deny) without the flag, allow only when explicitly on.
        assertTrue(!EconomyRules.teamTradeAllowed(true, false), "same-team trades must be denied by default");
        assertTrue(EconomyRules.teamTradeAllowed(true, true), "the explicit policy must permit them");
        assertTrue(EconomyRules.teamTradeAllowed(false, false), "different teams always count");
        assertTrue(EconomyRules.teamTradeAllowed(false, true), "different teams count under either policy");
    }

    @Test
    public void salesContributionFloorsAtZero() {
        assertEquals(42L, EconomyRules.salesContribution(42L));
        assertEquals(0L, EconomyRules.salesContribution(0L), "free trades are real but add no coins");
        assertEquals(0L, EconomyRules.salesContribution(-5L), "a negative value must never subtract progress");
    }

    @Test
    public void tradeFactClampsNegativeValuesAndDefaultsSeller() {
        TradeFact clamped = new TradeFact(BUYER, Optional.empty(), -9L);
        assertEquals(0L, clamped.value());
        TradeFact noSeller = new TradeFact(BUYER, null, 5L);
        assertEquals(Optional.empty(), noSeller.sellerId());
        assertThrows(IllegalArgumentException.class, () -> new TradeFact(null, Optional.of(SELLER), 1L));
    }

    @Test
    public void counterpartiesResolveSymmetrically() {
        TradeFact fact = new TradeFact(BUYER, Optional.of(SELLER), 7L);
        assertEquals(Optional.of(SELLER), fact.counterpartyOf(TradeFact.Side.BUYER));
        assertEquals(Optional.of(BUYER), fact.counterpartyOf(TradeFact.Side.SELLER));

        TradeFact ownerless = new TradeFact(BUYER, Optional.empty(), 7L);
        assertEquals(Optional.empty(), ownerless.counterpartyOf(TradeFact.Side.BUYER),
            "an ownerless trader gives the buyer no counterparty to record");
        assertEquals(Optional.of(BUYER), ownerless.counterpartyOf(TradeFact.Side.SELLER),
            "the ownerless trader's non-existent seller side still names its counterparty shape");
    }
}
