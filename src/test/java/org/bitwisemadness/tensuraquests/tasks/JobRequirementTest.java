package org.bitwisemadness.tensuraquests.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Multi-Job Mastery entry grammar as pure code (FR-021): one
 * parser defines the SNBT, net and editor contract, so its branches
 * are pinned here without a Minecraft classpath (issue #8). Job
 * <em>validity</em> is deliberately not checked here - that is the
 * adapter's poll-time concern (INT-002 keeps this class free of
 * Sympathetic Jobs references).
 */
public class JobRequirementTest {

    @Test
    public void parsesTheCanonicalForm() {
        Optional<JobRequirement> parsed = JobRequirement.parse("miner:10");
        assertTrue(parsed.isPresent(), "the canonical form must parse");
        assertEquals("miner", parsed.orElseThrow().job());
        assertEquals(10, parsed.orElseThrow().level());
        assertEquals("miner:10", parsed.orElseThrow().format());
    }

    @Test
    public void acceptsAnyCasingAndWhitespace() {
        Optional<JobRequirement> parsed = JobRequirement.parse("  FARMER : 2 ");
        assertTrue(parsed.isPresent(), "quest authors may type any casing");
        assertEquals(new JobRequirement("farmer", 2), parsed.orElseThrow(),
            "the canonical form is lowercase");
        assertEquals("farmer:2", parsed.orElseThrow().format());
    }

    @Test
    public void roundTripsThroughItsOwnFormat() {
        JobRequirement requirement = new JobRequirement("lumberjack", 25);
        assertEquals(Optional.of(requirement), JobRequirement.parse(requirement.format()));
    }

    @Test
    public void rejectsStructuralGarbage() {
        assertTrue(JobRequirement.parse(null).isEmpty(), "null must not parse");
        assertTrue(JobRequirement.parse("").isEmpty(), "an empty entry must not parse");
        assertTrue(JobRequirement.parse("miner").isEmpty(), "a missing colon must not parse");
        assertTrue(JobRequirement.parse(":10").isEmpty(), "a missing job must not parse");
        assertTrue(JobRequirement.parse("miner:").isEmpty(), "a missing level must not parse");
        assertTrue(JobRequirement.parse("m1ner:5").isEmpty(), "digits in the job id must not parse");
        assertTrue(JobRequirement.parse("m!ner:5").isEmpty(), "punctuation in the job id must not parse");
    }

    @Test
    public void rejectsUnsatisfiableLevels() {
        assertTrue(JobRequirement.parse("miner:0").isEmpty(), "level 0 would hold for everyone");
        assertTrue(JobRequirement.parse("miner:-5").isEmpty(), "negative levels must not parse");
        assertTrue(JobRequirement.parse("miner:ten").isEmpty(), "a non-numeric level must not parse");
        assertTrue(JobRequirement.parse("miner:999999999999").isEmpty(),
            "a level beyond an int must not parse");
    }

    @Test
    public void theEditorPatternIsShapeOnlyAndTheParserIsAuthoritative() {
        Pattern editor = Pattern.compile(JobRequirement.FORMAT_PATTERN);
        assertTrue(editor.matcher("miner:10").matches(), "the canonical form must match the editor pattern");
        assertTrue(editor.matcher("MINER:10").matches(), "any casing must match the editor pattern");
        assertFalse(editor.matcher("garbage").matches(), "garbage must fail the editor pattern");
        // The pattern validates shape only; the parser rejects what
        // the shape allows but sense forbids - the commit path parses.
        assertTrue(editor.matcher("miner:0").matches(), "level 0 has the right shape");
        assertTrue(JobRequirement.parse("miner:0").isEmpty(), "but the parser must refuse it");
    }
}
