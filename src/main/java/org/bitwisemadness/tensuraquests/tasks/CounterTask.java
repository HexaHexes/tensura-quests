package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.framework.TensuraTask;

/**
 * The first custom task type: a generic counter that completes when
 * server-validated intake reaches its configured target.
 *
 * <p>The type proves the full FTB Quests lifecycle - registration
 * through {@link FtbQuestsBridge}, editor configuration, SNBT and net
 * round-trips, server-side progress and persistence - with
 * domain-neutral values. The job, dungeon and economy tasks of later
 * issues inherit this shape from {@link TensuraTask}.</p>
 */
public final class CounterTask extends TensuraTask {

    /** Smallest allowed target: at zero the task would already be complete. */
    public static final long MIN_TARGET = 1L;
    /** Target for newly created tasks and for saves predating the field. */
    public static final long DEFAULT_TARGET = 1L;
    /** Largest allowed target; intake past it is clamped by FTB Quests. */
    public static final long MAX_TARGET = Long.MAX_VALUE;

    /** Config field name, shared by SNBT, net sync and the editor group. */
    private static final String TARGET_KEY = "target";

    private long target = DEFAULT_TARGET;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public CounterTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireCounterTaskType();
    }

    @Override
    public long getMaxProgress() {
        return target;
    }

    /**
     * @return the current completion threshold
     */
    public long getTarget() {
        return target;
    }

    /**
     * @param value the new threshold, clamped into
     *              [{@value #MIN_TARGET}, {@value #MAX_TARGET}]
     */
    public void setTarget(long value) {
        target = clampTarget(value);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putLong(TARGET_KEY, target);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        if (!tag.contains(TARGET_KEY)) {
            // Additive field: saves written before it existed keep the default.
            target = DEFAULT_TARGET;
            return;
        }
        long stored = tag.getLong(TARGET_KEY);
        target = clampTarget(stored);
        if (target != stored) {
            TensuraQuests.LOGGER.warn("counter task {} stored target {} outside [{}, {}]; using {}",
                getId(), stored, MIN_TARGET, MAX_TARGET, target);
        }
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarLong(target);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        target = clampTarget(buffer.readVarLong());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        // Client-only, like AdvancementTask's override upstream: the
        // editor path never runs on a dedicated server.
        super.fillConfigGroup(group);
        group.addLong(TARGET_KEY, target, value -> target = clampTarget(value),
            DEFAULT_TARGET, MIN_TARGET, MAX_TARGET);
    }

    private static long clampTarget(long value) {
        if (value < MIN_TARGET) {
            return MIN_TARGET;
        }
        return Math.min(value, MAX_TARGET);
    }
}
