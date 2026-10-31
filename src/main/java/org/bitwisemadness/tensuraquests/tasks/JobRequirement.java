package org.bitwisemadness.tensuraquests.tasks;

import java.util.Locale;
import java.util.Optional;

/**
 * One (job, level) pair of a Multi-Job Mastery task, in its canonical
 * {@code jobid:level} string form.
 *
 * <p>The pair exists first as a string: it is the SNBT save format,
 * the net-sync format and the quest editor's list element, so one
 * parser defines the contract for all three (FR-021). Keeping this
 * class Minecraft-free lets the JUnit suite pin the grammar without a
 * Minecraft classpath (issue #8); job <em>validity</em> (whether the
 * id names a real Sympathetic Jobs job) is checked at poll time by
 * the adapter, because this class must not know about Sympathetic
 * Jobs either (INT-002).</p>
 *
 * @param job lowercase job id, e.g. {@code miner}
 * @param level required level, at least {@value #MIN_LEVEL}
 */
public record JobRequirement(String job, int level) {

    /** Smallest satisfiable level; 0 would already hold for everyone. */
    public static final int MIN_LEVEL = 1;

    /**
     * The editor's element pattern: structural shape only - job
     * validity is the poll-time adapter's concern.
     */
    public static final String FORMAT_PATTERN = "[A-Za-z_]+:[0-9]+";

    /**
     * Canonicalize the job id: ids are lowercase everywhere (Sympathetic
     * Jobs' {@code JobId.byId} resolves case-insensitively, so any
     * casing a quest author types is accepted on read).
     */
    public JobRequirement {
        if (job != null) {
            job = job.trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Parse one entry.
     *
     * <p>Accepts {@code miner:10} and case/whitespace variants
     * ({@code MINER:10}, {@code miner : 10}); rejects a missing or
     * empty job, a non-numeric or out-of-range level, and anything
     * outside {@value #FORMAT_PATTERN} in spirit (letters and
     * underscores only in the job part).</p>
     *
     * @param raw the raw string from a save, the wire or the editor
     * @return the parsed pair, empty when the entry is malformed
     */
    public static Optional<JobRequirement> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String entry = raw.trim();
        int colon = entry.indexOf(':');
        if (colon <= 0 || colon == entry.length() - 1) {
            return Optional.empty();
        }
        String job = entry.substring(0, colon).trim();
        if (job.isEmpty() || !job.chars().allMatch(JobRequirement::isJobChar)) {
            return Optional.empty();
        }
        int level;
        try {
            level = Integer.parseInt(entry.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (level < MIN_LEVEL) {
            return Optional.empty();
        }
        return Optional.of(new JobRequirement(job, level));
    }

    /**
     * The canonical wire form of this pair - what
     * {@link #parse(String)} round-trips.
     *
     * @return {@code job:level}
     */
    public String format() {
        return job + ':' + level;
    }

    private static boolean isJobChar(int c) {
        return c == '_' || Character.isLetter(c);
    }
}
