package org.bitwisemadness.tensuraquests.tasks;

import java.util.Optional;

/**
 * Pure anti-exploit and fairness decisions for economy intake
 * (SEC-004, FR-043, FR-044, U-09).
 *
 * <p>Extracted from the task classes so the JUnit suite exercises
 * every branch without Minecraft or Lightman's Currency on the
 * classpath (issue #8): this class only sees UUIDs, booleans and
 * numbers. Playerless completions (U-09's terminal/command/storage
 * cases) never become a {@link TradeFact} at all - the adapter drops
 * them at extraction - which is why no rule here mentions them. The
 * event subscription itself is the SEC-004 boundary: only
 * completed-trade events reach here, never inventory movement; that
 * wiring lives in the adapter, verified by code review and the
 * event-discrimination GameTest.</p>
 */
public final class EconomyRules {

    private EconomyRules() {
    }

    /** Why a completed trade may not be credited at all. */
    public enum Rejection {
        /** Buyer and seller are the same player (FR-043). */
        SELF_TRADE,
    }

    /**
     * Trade-level accept/reject before any task is considered. Runs
     * once per event, so its rejections log once rather than once per
     * matching task.
     *
     * @param fact the completed trade
     * @return empty when the trade may credit tasks, the rejection
     *         reason otherwise
     */
    public static Optional<Rejection> reject(TradeFact fact) {
        if (fact.sellerId().isPresent() && fact.buyerId().equals(fact.sellerId().get())) {
            return Optional.of(Rejection.SELF_TRADE);
        }
        return Optional.empty();
    }

    /**
     * Whether a trade counts for a task with the given team-trade
     * policy (FR-044): same-team trades count only when the task
     * explicitly allows them - the default denies, so self-servicing
     * alts cannot farm milestones. "Same team" is resolved by the
     * caller (FTB Teams is not visible from this class); a trade with
     * no player seller (ownerless trader) is never a team trade.
     *
     * @param sameTeam whether both players resolve to the same FTB
     *                 Teams team
     * @param allowTeamTrades the task's configured policy
     * @return true when this trade may count for that task
     */
    public static boolean teamTradeAllowed(boolean sameTeam, boolean allowTeamTrades) {
        return !sameTeam || allowTeamTrades;
    }

    /**
     * The value a Sales Milestone trade contributes (FR-041): the paid
     * core value, floored at zero - free trades are real trades but
     * add no coins, and a zero contribution simply moves nothing.
     *
     * @param paidValue the trade's paid value
     * @return a non-negative contribution
     */
    public static long salesContribution(long paidValue) {
        return Math.max(0L, paidValue);
    }
}
