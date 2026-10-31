package org.bitwisemadness.tensuraquests.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/**
 * The completion policy as pure code (FR-009, FR-010): the aggregate
 * is what mirrors into FTB Quests' visible progress, so these
 * assertions pin the exact relationship between policy, aggregate and
 * completion - including the ADR-0002 joiner consequence.
 */
public class CounterPolicyTest {

    @Test
    public void anyTakesTheHighestMemberCounter() {
        assertEquals(OptionalLong.of(9), CounterPolicy.ANY.aggregate(new long[] {2, 9, 5}));
    }

    @Test
    public void allTakesTheLowestMemberCounter() {
        assertEquals(OptionalLong.of(2), CounterPolicy.ALL.aggregate(new long[] {2, 9, 5}));
    }

    @Test
    public void singleMemberIsItsOwnAggregateUnderBothPolicies() {
        assertEquals(OptionalLong.of(7), CounterPolicy.ANY.aggregate(new long[] {7}));
        assertEquals(OptionalLong.of(7), CounterPolicy.ALL.aggregate(new long[] {7}));
    }

    @Test
    public void emptyTeamHasNoAggregate() {
        assertEquals(OptionalLong.empty(), CounterPolicy.ANY.aggregate(new long[0]));
        assertEquals(OptionalLong.empty(), CounterPolicy.ALL.aggregate(new long[0]));
    }

    @Test
    public void allJoinerWithoutProgressDragsTheAggregateToZero() {
        // The documented ADR-0002 consequence: a joiner at 0 keeps an
        // ALL task incomplete, and the mirrored bar visibly at zero.
        assertEquals(OptionalLong.of(0), CounterPolicy.ALL.aggregate(new long[] {10, 0}));
        // ANY is unaffected - one qualifying member still qualifies.
        assertEquals(OptionalLong.of(10), CounterPolicy.ANY.aggregate(new long[] {10, 0}));
    }

    @Test
    public void aggregateMeetsTargetOnlyWhenThePolicyIsSatisfied() {
        long target = 5;
        // ALL: incomplete while anyone is short, complete once all qualify.
        assertTrue(CounterPolicy.ALL.aggregate(new long[] {5, 4}).orElse(0) < target);
        assertTrue(CounterPolicy.ALL.aggregate(new long[] {5, 5}).orElse(0) >= target);
        // ANY: complete as soon as one member qualifies.
        assertTrue(CounterPolicy.ANY.aggregate(new long[] {5, 0}).orElse(0) >= target);
        assertTrue(CounterPolicy.ANY.aggregate(new long[] {4, 3}).orElse(0) < target);
    }
}
