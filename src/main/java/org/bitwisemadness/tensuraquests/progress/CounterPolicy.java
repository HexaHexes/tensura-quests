package org.bitwisemadness.tensuraquests.progress;

import java.util.OptionalLong;

/**
 * Completion policy over a team's personal counters (ADR-0002, FR-009,
 * FR-010).
 *
 * <p>{@link #ANY} (the default) completes the task when a single
 * member's counter meets the requirement; {@link #ALL} requires every
 * member at check time. The policy also selects the <em>policy-relevant
 * aggregate</em> that mirrors into FTB Quests' visible progress:
 * {@code max} for ANY and {@code min} for ALL - so {@code aggregate >=
 * target} is exactly the policy's completion condition and the quest
 * bar shows the value the policy is waiting on (an ALL-joiner at zero
 * drags the bar to zero, the documented ADR-0002 consequence).</p>
 *
 * <p>This enum is deliberately Minecraft-free: the test source set has
 * no Minecraft classpath (issue #8), so the aggregate rule stays
 * JUnit-testable as pure code. The editor {@code NameMap} and the SNBT
 * and net plumbing live on {@code TensuraTask} with the rest of the
 * task configuration.</p>
 */
public enum CounterPolicy {
    ANY("any"),
    ALL("all");

    /** Stable id used in SNBT and as the lang-key suffix. */
    public final String id;

    CounterPolicy(String id) {
        this.id = id;
    }

    /**
     * The policy-relevant aggregate of the given member counters.
     *
     * @param memberCounters one value per team member at check time;
     *                       members whose state is not loaded count as 0
     * @return the aggregate, or empty when the team has no members to
     *         evaluate - the caller must not mirror over an empty team
     */
    public OptionalLong aggregate(long[] memberCounters) {
        if (memberCounters.length == 0) {
            return OptionalLong.empty();
        }
        long aggregate = this == ALL ? Long.MAX_VALUE : Long.MIN_VALUE;
        for (long counter : memberCounters) {
            aggregate = this == ALL ? Math.min(aggregate, counter) : Math.max(aggregate, counter);
        }
        return OptionalLong.of(aggregate);
    }
}
