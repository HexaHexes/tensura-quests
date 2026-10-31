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
 * Dungeon Milestone: completes when a player enters the configured layer
 * <em>or deeper</em> in one of the configured dimensions (FR-034).
 *
 * <p>Consumes {@code LayerEnteredEvent} - the per-player movement event the
 * depth tracker fires with {@code playerId} already attributed, so no D2
 * dependency. The threshold is at-least semantics: any entry at
 * {@code >= requiredLayer} counts; entries below it are ordinary context
 * filtering. The target counts qualifying entries (default 1 = reach the
 * depth once).</p>
 */
public final class DungeonMilestoneTask extends DungeonTask {

    /** Smallest configurable threshold (surface layer). */
    public static final int MIN_LAYER = 0;
    /** Threshold for newly created tasks and saves predating the field. */
    public static final int DEFAULT_REQUIRED_LAYER = 1;
    /** Config field name, shared by SNBT, net sync and the editor group. */
    private static final String REQUIRED_LAYER_KEY = "required_layer";

    private int requiredLayer = DEFAULT_REQUIRED_LAYER;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public DungeonMilestoneTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireDungeonMilestoneTaskType();
    }

    /** @return the depth threshold - entries at or below-index this count */
    public int getRequiredLayer() {
        return requiredLayer;
    }

    /** @param value the threshold, clamped to >= {@value #MIN_LAYER} */
    public void setRequiredLayer(int value) {
        requiredLayer = Math.max(MIN_LAYER, value);
    }

    /**
     * Whether an entered layer reaches the milestone (at-least semantics).
     *
     * @param eventLayer the event's layer index
     * @return true when the player entered at or beyond the threshold
     */
    public boolean matchesContext(int eventLayer) {
        return eventLayer >= requiredLayer;
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putInt(REQUIRED_LAYER_KEY, requiredLayer);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        if (!tag.contains(REQUIRED_LAYER_KEY)) {
            // Additive field: saves written before it existed keep the default.
            requiredLayer = DEFAULT_REQUIRED_LAYER;
            return;
        }
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
            DEFAULT_REQUIRED_LAYER, MIN_LAYER, Integer.MAX_VALUE);
    }
}
