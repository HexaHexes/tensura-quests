package org.bitwisemadness.tensuraquests.compat.sympatheticjobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The version gate as pure logic (COMP-003): a matching pin is the
 * only OK outcome, absence disables quietly-but-visibly, and any
 * other installed version fails loudly with both versions named -
 * the exact text the startup log emits.
 */
public class SjVersionGateTest {

    @Test
    public void matchingVersionIsUsable() {
        SjVersionGate.Verdict verdict = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "1.0.13");
        assertEquals(SjVersionGate.Result.OK, verdict.result());
        assertTrue(verdict.usable(), "a matching pin must let the job family register");
        assertEquals("1.0.13", verdict.installedVersion());
    }

    @Test
    public void surroundingWhitespaceDoesNotBreakTheMatch() {
        SjVersionGate.Verdict verdict = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "  1.0.13 ");
        assertEquals(SjVersionGate.Result.OK, verdict.result());
        assertEquals("1.0.13", verdict.installedVersion(), "the stored version is trimmed");
    }

    @Test
    public void absentSympatheticJobsDisablesQuietly() {
        SjVersionGate.Verdict fromNull = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, null);
        SjVersionGate.Verdict fromBlank = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "   ");
        assertEquals(SjVersionGate.Result.ABSENT, fromNull.result());
        assertEquals(SjVersionGate.Result.ABSENT, fromBlank.result());
        assertFalse(fromNull.usable(), "an absent mod must not register the family");
        assertNull(fromNull.installedVersion());
    }

    @Test
    public void anyOtherVersionFailsLoudly() {
        SjVersionGate.Verdict verdict = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "1.0.9");
        assertEquals(SjVersionGate.Result.MISMATCH, verdict.result());
        assertFalse(verdict.usable(), "an off-pin mod must not register the family");
        assertEquals("1.0.9", verdict.installedVersion());
    }

    @Test
    public void mismatchDescriptionNamesBothVersions() {
        SjVersionGate.Verdict verdict = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "1.0.9");
        String description = verdict.describe();
        assertTrue(description.contains("1.0.9"), "the failure must name the installed version: " + description);
        assertTrue(description.contains(SjVersionGate.PINNED_VERSION),
            "the failure must name the pinned version: " + description);
    }

    @Test
    public void everyOutcomeDescribesItself() {
        String present = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "1.0.13").describe();
        String absent = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, null).describe();
        String mismatch = SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "9.9.9").describe();
        assertTrue(present.contains(SjVersionGate.PINNED_VERSION), "OK text names the pin: " + present);
        assertTrue(absent.contains("not installed"), "ABSENT text says the mod is missing: " + absent);
        assertTrue(mismatch.contains("9.9.9") && mismatch.contains(SjVersionGate.PINNED_VERSION),
            "MISMATCH text names both versions: " + mismatch);
    }

    @Test
    public void claimProceedsOnlyUnderAnOkVerdict() {
        // FR-024's decision: the write path may touch Sympathetic Jobs
        // state only on an exact pin match.
        assertTrue(SjVersionGate.claimBlocked(
                SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "1.0.13")).isEmpty(),
            "an exact pin match must allow the claim");
        assertTrue(SjVersionGate.claimBlocked(
                SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, null)).isPresent(),
            "an absent jar must block the claim");
        var blocked = SjVersionGate.claimBlocked(
            SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION, "9.9.9"));
        assertTrue(blocked.isPresent(), "an off-pin jar must block the claim");
        assertTrue(blocked.orElseThrow().contains("9.9.9")
                && blocked.orElseThrow().contains(SjVersionGate.PINNED_VERSION),
            "the blocked claim must carry the failure text naming both versions: " + blocked.orElseThrow());
    }
}
