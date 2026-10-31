package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SympatheticJobs;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;

/**
 * Job Participation: completes when the player holds the configured
 * job at all, regardless of level (FR-022).
 *
 * <p>Maximum progress is fixed at 1: the player's counter converges
 * to 1 while they own the job and stays 0 otherwise, so under every
 * completion policy the mirrored bar reads exactly "does this team
 * have someone (ANY) / everyone (ALL) doing this job". Ownership is
 * read from Sympathetic Jobs' own list - never from the active job
 * (FR-020), never from our own copy (SEC-007).</p>
 */
public final class JobParticipationTask extends JobTask {

    /** Total progress: holding the job is a one-step condition. */
    public static final long TARGET = 1L;
    /** Job for newly created tasks and saves predating the field. */
    public static final String DEFAULT_JOB = "miner";

    /** Config field name, shared by SNBT, net sync and the editor group. */
    private static final String JOB_KEY = "job";

    private String job = DEFAULT_JOB;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public JobParticipationTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireJobParticipationTaskType();
    }

    @Override
    public long getMaxProgress() {
        return TARGET;
    }

    /**
     * @return the configured job id, lowercase; never blank
     */
    public String getJob() {
        return job;
    }

    /**
     * @param value the job id; blank falls back to {@link #DEFAULT_JOB}
     */
    public void setJob(String value) {
        job = normalizeJob(value);
    }

    @Override
    protected OptionalLong desiredProgress(ServerPlayer player) {
        Optional<Boolean> holds = SympatheticJobs.hasJob(player, job);
        if (holds.isEmpty()) {
            warnUnknownJob(job);
            return OptionalLong.empty();
        }
        return OptionalLong.of(Boolean.TRUE.equals(holds.get()) ? TARGET : 0L);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putString(JOB_KEY, job);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        // Absent key reads as "" and normalises to the default.
        job = normalizeJob(tag.getString(JOB_KEY));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(job);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        job = normalizeJob(buffer.readUtf());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        // Client-only, like the upstream task overrides: the editor
        // path never runs on a dedicated server.
        super.fillConfigGroup(group);
        group.addEnum(JOB_KEY, job, this::setJob, jobNameMap());
    }

    private static String normalizeJob(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_JOB;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
