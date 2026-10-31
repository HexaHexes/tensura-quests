package org.bitwisemadness.tensuraquests.compat.sympatheticjobs;

import dev.fatsan.sympatheticjobs.api.JobId;
import dev.fatsan.sympatheticjobs.common.state.JobsPlayerState;
import dev.fatsan.sympatheticjobs.common.state.JobsStateManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The single read-path adapter onto Sympathetic Jobs (D3, INT-002,
 * FR-020..FR-022).
 *
 * <p>This class - together with {@link SjVersionGate} and
 * {@link SjPresence} in this package - is the only code allowed to
 * reference {@code dev.fatsan.sympatheticjobs} (enforced by
 * {@code SympatheticJobsIsolationTest}). Sympathetic Jobs 1.0.13 is
 * closed source (ARR), so everything here was verified against the
 * pinned jar's bytecode and is exercised by the GameTest suite: an
 * internal rename breaks exactly one package plus its tests, and the
 * version gate keeps a mismatched jar from loading this class at
 * all.</p>
 *
 * <p>Reads only - Sympathetic Jobs owns job state (SEC-007): nothing
 * in this adapter adds, spends or mutates jobs, levels or XP. Job
 * vocabulary is the {@link JobId} enum itself (12 jobs), resolved
 * case-insensitively through {@link JobId#byId(String)} so quest
 * files and the editor agree on the lowercase ids.</p>
 */
public final class SympatheticJobs {

    private static final List<String> JOB_NAMES = jobNamesFromJobIds();

    private SympatheticJobs() {
    }

    /**
     * Every job id Sympathetic Jobs exposes, in enum order - the
     * vocabulary quest authors pick from.
     *
     * @return an unmodifiable list of lowercase job ids
     */
    public static List<String> jobNames() {
        return List.copyOf(JOB_NAMES);
    }

    /**
     * A player's level in a job, ownership-gated exactly as
     * Sympathetic Jobs defines it ({@code JobsPlayerState.level}
     * returns 0 for jobs the player does not hold).
     *
     * @param player the player to read; server-side callers only
     * @param jobName job id, case-insensitive
     * @return the level, or empty when the name is no job at all
     */
    public static OptionalInt level(ServerPlayer player, String jobName) {
        Optional<JobId> job = JobId.byId(jobName);
        if (job.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(JobsStateManager.get(player).level(job.get()));
    }

    /**
     * Whether a player holds a job (their personal state's ownership
     * list, independent of the active job - XP flows to every
     * acquired job, FR-020).
     *
     * @param player the player to read; server-side callers only
     * @param jobName job id, case-insensitive
     * @return true/false when the name is a real job, empty when it
     *         is not
     */
    public static Optional<Boolean> hasJob(ServerPlayer player, String jobName) {
        Optional<JobId> job = JobId.byId(jobName);
        if (job.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(JobsStateManager.get(player).hasJob(job.get()));
    }

    /**
     * Sympathetic Jobs' own display name for a job, for the quest
     * editor's dropdown; falls back to the raw id for an unknown
     * name so a bad config entry is still visible rather than blank.
     *
     * @param jobName job id, case-insensitive
     * @return the display component; never null
     */
    public static Component displayName(String jobName) {
        return JobId.byId(jobName)
            .map(JobId::displayName)
            .orElseGet(() -> Component.literal(String.valueOf(jobName)));
    }

    private static List<String> jobNamesFromJobIds() {
        List<String> names = new ArrayList<>();
        for (JobId id : JobId.ALL) {
            names.add(id.id().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(names);
    }
}
