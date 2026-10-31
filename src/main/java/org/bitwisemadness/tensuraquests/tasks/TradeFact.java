package org.bitwisemadness.tensuraquests.tasks;

import java.util.Optional;
import java.util.UUID;

/**
 * One completed trade, reduced to what intake needs (FR-040..FR-044).
 *
 * <p>Extracted once by the economy adapter from Lightman's Currency's
 * completed-trade event and handed to the tasks unchanged: this record
 * is deliberately free of Lightman's Currency (and Minecraft) types -
 * UUIDs and a long - so the decision layer ({@link EconomyRules}) and
 * the record itself stay JUnit-testable without either on the
 * classpath, and so no task ever re-touches the event surface
 * (INT-002's single-package rule, applied to the economy adapter).</p>
 *
 * <p>Both counterparties are first-class: the buyer executed the
 * trade, the seller owns the trader it happened at. A trader with no
 * player owner (ownerless/community/command-driven) yields an empty
 * seller - buyer-side credit still applies, seller-side does not.</p>
 *
 * @param buyerId the customer's player id (always present - the
 *                adapter drops playerless completions before this
 *                point, U-09's documented rule)
 * @param sellerId the trader owner's player id, empty when the trader
 *                 has no player owner
 * @param value the trade's paid value in core coin units (0 for free
 *              trades; never negative)
 */
public record TradeFact(UUID buyerId, Optional<UUID> sellerId, long value) {

    public TradeFact {
        if (buyerId == null) {
            throw new IllegalArgumentException("buyerId must not be null");
        }
        if (sellerId == null) {
            sellerId = Optional.empty();
        }
        if (value < 0) {
            value = 0;
        }
    }

    /**
     * The counterparty for one side of the trade: the buyer's
     * counterparty is the seller and vice versa.
     *
     * @param side whose perspective to resolve
     * @return the other side's player id, empty when that side has no
     *         player owner
     */
    public Optional<UUID> counterpartyOf(Side side) {
        return side == Side.BUYER ? sellerId : Optional.of(buyerId);
    }

    /** The two sides of a completed trade, credited separately (FR-042). */
    public enum Side {
        BUYER,
        SELLER,
    }
}
