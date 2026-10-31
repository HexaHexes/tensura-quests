package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.OptionalLong;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SympatheticJobs;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;

/**
 * Job Mastery: completes when the configured job reaches the
 * configured level (FR-020).
 *
 * <p>The player's counter converges to
 * {@code min(currentLevel, requiredLevel)}, so the mirrored team
 * progress <em>is</em> the qualifying level under any completion
 * policy and the task completes exactly at the threshold through the
 * normal intake path. The level read is ownership-gated by
 * Sympathetic Jobs itself (a job the player does not hold reads 0),
 * and reads job state rather than the active job: XP flows to every
 * acquired job (FR-020).</p>
 */
public final class JobMasteryTask extends JobTask {

    /** Smallest allowed requirement: level 1 is the first real level. */
    public static final int MIN_REQUIRED_LEVEL = 1;
    /** Requirement for newly created tasks and saves predating the field. */
    public static final int DEFAULT_REQUIRED_LEVEL = 1;
    /** Largest allowed requirement; Sympathetic Jobs levels are unbounded from our side. */
    public static final int MAX_REQUIRED_LEVEL = Integer.MAX_VALUE;
    /** Job for newly created tasks and saves predating the field. */
    public static final String DEFAULT_JOB = "miner";

    /** Config field names, shared by SNBT, net sync and the editor group. */
    private static final String JOB_KEY = "job";
    private static final String LEVEL_KEY = "required_level";

    private String job = DEFAULT_JOB;
    private int requiredLevel = DEFAULT_REQUIRED_LEVEL;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public JobMasteryTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireJobMasteryTaskType();
    }

    @Override
    public long getMaxProgress() {
        return requiredLevel;
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

    /**
     * @return the level at which this task completes
     */
    public int getRequiredLevel() {
        return requiredLevel;
    }

    /**
     * @param value the new requirement, clamped into
     *              [{@value #MIN_REQUIRED_LEVEL}, {@value #MAX_REQUIRED_LEVEL}]
     */
    public void setRequiredLevel(int value) {
        requiredLevel = clampLevel(value);
    }

    @Override
    protected OptionalLong desiredProgress(ServerPlayer player) {
        OptionalInt level = SympatheticJobs.level(player, job);
        if (level.isEmpty()) {
            warnUnknownJob(job);
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.min(level.getAsInt(), requiredLevel));
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putString(JOB_KEY, job);
        tag.putInt(LEVEL_KEY, requiredLevel);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        job = normalizeJob(tag.getString(JOB_KEY));
        if (!tag.contains(LEVEL_KEY)) {
            // Additive field: saves written before it existed keep the default.
            requiredLevel = DEFAULT_REQUIRED_LEVEL;
            return;
        }
        int stored = tag.getInt(LEVEL_KEY);
        requiredLevel = clampLevel(stored);
        if (requiredLevel != stored) {
            TensuraQuests.LOGGER.warn("job mastery task {} stored required_level {} outside [{}, {}]; using {}",
                getId(), stored, MIN_REQUIRED_LEVEL, MAX_REQUIRED_LEVEL, requiredLevel);
        }
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(job);
        buffer.writeVarInt(requiredLevel);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        job = normalizeJob(buffer.readUtf());
        requiredLevel = clampLevel(buffer.readVarInt());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        // Client-only, like the upstream task overrides: the editor
        // path never runs on a dedicated server.
        super.fillConfigGroup(group);
        group.addEnum(JOB_KEY, job, this::setJob, jobNameMap());
        group.addInt(LEVEL_KEY, requiredLevel, value -> requiredLevel = clampLevel(value),
            DEFAULT_REQUIRED_LEVEL, MIN_REQUIRED_LEVEL, MAX_REQUIRED_LEVEL);
    }

    private static String normalizeJob(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_JOB;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static int clampLevel(int value) {
        if (value < MIN_REQUIRED_LEVEL) {
            return MIN_REQUIRED_LEVEL;
        }
        return Math.min(value, MAX_REQUIRED_LEVEL);
    }
}
