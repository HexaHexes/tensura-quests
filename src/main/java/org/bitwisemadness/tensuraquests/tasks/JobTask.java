package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SympatheticJobs;
import org.bitwisemadness.tensuraquests.framework.TensuraTask;

/**
 * Base class for the three Sympathetic Jobs task types (FR-020..FR-022).
 *
 * <p>Job state is polled, not evented: FTB Quests'
 * {@code autoSubmitOnPlayerTick} contract calls
 * {@link #submitTask(TeamData, ServerPlayer, ItemStack)} for this
 * task every {@value #POLL_INTERVAL_TICKS} game ticks, per online
 * player, guarded upstream by "not completed" and "quest can start"
 * and wrapped in the file's player context. Each call evaluates the
 * player's own Sympathetic Jobs state into the progress their
 * <em>personal counter</em> should hold, and hands the delta to
 * {@link TensuraTask#pollState} - the validated intake that mirrors
 * the team's policy aggregate (ADR-0002). Because the delta converges
 * to zero after one successful poll per state change, a quiet team
 * costs one counter read per interval, and rejections cannot
 * spam.</p>
 *
 * <p>{@link #checkOnLogin()} keeps FTB Quests' default of {@code
 * true}, so the login check re-runs the same evaluation when a player
 * rejoins - which also re-converges any team mirror an offline member
 * left pessimistic.</p>
 *
 * <p>Unknown job names (a save written against different job ids) are
 * warned about once per task instance and never satisfy the task;
 * they cannot otherwise corrupt progress (INT-002 keeps every Sympathetic
 * Jobs contact in the adapter package).</p>
 */
public abstract class JobTask extends TensuraTask {

    /**
     * Poll cadence in game ticks: one evaluation per second per
     * online player and job task. Job levels change on the order of
     * seconds-to-minutes, so this is invisible in play and cheap on
     * the server (the FTB-side list iteration happens regardless of
     * the interval - the value only gates our body).
     */
    public static final int POLL_INTERVAL_TICKS = 20;

    /** Integration id logged with every intake decision (FR-012). */
    public static final String INTEGRATION = "sympathetic_jobs";

    /** Job names already warned about, so a bad save logs once, not per tick. */
    private final Set<String> warnedUnknownJobs = new HashSet<>();

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    protected JobTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public final int autoSubmitOnPlayerTick() {
        return POLL_INTERVAL_TICKS;
    }

    /**
     * The polled evaluation: read this player's job state and route
     * the delta into validated intake. Called by FTB Quests' tick
     * dispatch; tests call it directly to drive a poll without
     * waiting on server ticks.
     *
     * @param data the player's team data, as handed over by FTB Quests
     * @param player the online player whose state to evaluate
     * @param stack unused - state tasks consume nothing
     */
    @Override
    public final void submitTask(TeamData data, ServerPlayer player, ItemStack stack) {
        pollState(data, player, desiredProgress(player), INTEGRATION);
    }

    /**
     * The value this player's personal counter should reach, derived
     * from their own job state - never above the task's target, so a
     * satisfied player completes exactly at the threshold.
     *
     * @param player the online player to evaluate
     * @return the desired counter value, or empty when the state
     *         cannot be evaluated (unknown job name)
     */
    protected abstract OptionalLong desiredProgress(ServerPlayer player);

    /**
     * Record an unknown job name so the save's author hears about it
     * once per task instance instead of once per tick.
     *
     * @param jobName the unresolvable name from the configuration
     */
    protected final void warnUnknownJob(String jobName) {
        if (warnedUnknownJobs.add(jobName)) {
            TensuraQuests.LOGGER.warn("job task {} references unknown job '{}'; it cannot contribute progress until the configuration is fixed",
                getId(), jobName);
        }
    }

    /**
     * The editor's job dropdown, built from the adapter's vocabulary
     * with Sympathetic Jobs' own display names - shared by the two
     * single-job task types. Client-side only, and only reachable
     * when the family registered (Sympathetic Jobs present and
     * pinned).
     *
     * @return the name map over every known job id
     */
    static NameMap<String> jobNameMap() {
        List<String> names = SympatheticJobs.jobNames();
        String first = names.isEmpty() ? "miner" : names.get(0);
        return NameMap.of(first, names)
            .id(name -> name)
            .name(SympatheticJobs::displayName)
            .create();
    }
}
