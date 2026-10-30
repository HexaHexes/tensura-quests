package org.bitwisemadness.tensuraquests.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic coverage of the intake decision rules (FR-004). The
 * Minecraft-bound halves of the lifecycle - SNBT, net sync, team
 * progress and logging - are exercised by the GameTest suite; this
 * suite runs without a Minecraft classpath, matching the template's
 * deliberately bare test source set.
 */
class IntakeRulesTest {

    @Test
    void acceptsPositiveAmountBelowTarget() {
        assertEquals(IntakeOutcome.ACCEPTED, IntakeRules.evaluate(0L, 5L, 1L));
        assertEquals(IntakeOutcome.ACCEPTED, IntakeRules.evaluate(4L, 5L, 99L));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertEquals(IntakeOutcome.REJECTED_INVALID_AMOUNT, IntakeRules.evaluate(0L, 5L, 0L));
        assertEquals(IntakeOutcome.REJECTED_INVALID_AMOUNT, IntakeRules.evaluate(0L, 5L, -3L));
    }

    @Test
    void rejectsIntakeAtOrBeyondTarget() {
        assertEquals(IntakeOutcome.REJECTED_ALREADY_COMPLETE, IntakeRules.evaluate(5L, 5L, 1L));
        assertEquals(IntakeOutcome.REJECTED_ALREADY_COMPLETE, IntakeRules.evaluate(6L, 5L, 1L));
    }

    @Test
    void rejectsDegenerateTargets() {
        assertEquals(IntakeOutcome.REJECTED_ALREADY_COMPLETE, IntakeRules.evaluate(0L, 0L, 1L));
        assertEquals(IntakeOutcome.REJECTED_ALREADY_COMPLETE, IntakeRules.evaluate(0L, -1L, 1L));
    }

    @Test
    void invalidAmountIsReportedBeforeCompletion() {
        assertEquals(IntakeOutcome.REJECTED_INVALID_AMOUNT, IntakeRules.evaluate(5L, 5L, 0L));
    }
}
