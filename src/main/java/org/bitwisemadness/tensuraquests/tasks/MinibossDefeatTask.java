package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;

/**
 * Miniboss Defeat: counts floor-miniboss falls at the configured layer in
 * the configured dimensions, credited to the attributed killer (FR-032).
 *
 * <p>Consumes {@code MinibossSlainEvent} (the event added for exactly this
 * purpose - see infinite-dungeon-api#83; {@code BossSlainEvent} is the
 * guardian's, never a floor miniboss's). A kill with an absent killer
 * (environmental - ADR-0003's documented unknown) credits nobody. Only the
 * configured layer counts; other layers are ordinary context filtering.</p>
 */
public final class MinibossDefeatTask extends DungeonTask {

    /** Smallest configurable layer (the suite's own bound). */
    public static final int MIN_LAYER = 0;
    /** Config field name, shared by SNBT, net sync and the editor group. */
    private static final String REQUIRED_LAYER_KEY = "required_layer";

    private int requiredLayer = MIN_LAYER;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public MinibossDefeatTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireMinibossDefeatTaskType();
    }

    /** @return the configured layer whose minibosses count */
    public int getRequiredLayer() {
        return requiredLayer;
    }

    /** @param value the layer, clamped to >= {@value #MIN_LAYER} */
    public void setRequiredLayer(int value) {
        requiredLayer = Math.max(MIN_LAYER, value);
    }

    /**
     * Whether a slain miniboss is <em>this</em> task's subject.
     *
     * @param eventLayer the event's layer index
     * @return true when it is the configured layer
     */
    public boolean matchesContext(int eventLayer) {
        return requiredLayer == eventLayer;
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putInt(REQUIRED_LAYER_KEY, requiredLayer);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        requiredLayer = Math.max(MIN_LAYER, tag.getInt(REQUIRED_LAYER_KEY));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(requiredLayer);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        requiredLayer = Math.max(MIN_LAYER, buffer.readVarInt());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        super.fillConfigGroup(group);
        group.addInt(REQUIRED_LAYER_KEY, requiredLayer, value -> requiredLayer = Math.max(MIN_LAYER, value),
            MIN_LAYER, MIN_LAYER, Integer.MAX_VALUE);
    }
}
