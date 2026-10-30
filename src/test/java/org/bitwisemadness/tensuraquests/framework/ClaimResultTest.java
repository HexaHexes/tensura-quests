package org.bitwisemadness.tensuraquests.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Value-contract coverage of the claim result (FR-006, FR-007): the
 * success singleton, and the guarantee that a failure always carries a
 * non-blank reason so no rejection is ever logged without a cause. The
 * Minecraft-bound claim flow - marking, releasing and logging - is
 * exercised by the reward GameTest suite.
 */
class ClaimResultTest {

    @Test
    void grantedResultIsTheSharedSingleton() {
        assertSame(ClaimResult.granted(), ClaimResult.granted());
        assertTrue(ClaimResult.granted().isGranted());
        assertEquals("", ClaimResult.granted().reason());
    }

    @Test
    void failedCarriesTheGivenReason() {
        ClaimResult result = ClaimResult.failed("integration refused");
        assertFalse(result.isGranted());
        assertEquals("integration refused", result.reason());
    }

    @Test
    void blankReasonNormalisesToUnspecified() {
        assertEquals("unspecified", ClaimResult.failed("").reason());
        assertEquals("unspecified", ClaimResult.failed("   ").reason());
        assertEquals("unspecified", ClaimResult.failed(null).reason());
    }
}
