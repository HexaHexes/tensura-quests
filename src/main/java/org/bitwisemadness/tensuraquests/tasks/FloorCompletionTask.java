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
 * Floor Completion: completes when a player enters the configured floor of
 * the configured layer in one of the configured dimensions (FR-030).
 *
 * <p>The intake unit is one {@code FloorEnteredEvent} matching the exact
 * layer/floor - that event already attributes its player ({@code playerId}),
 * so this task shipped even before the D2 attribution work. The target
 * counts visits (default 1 = reach it once); re-crossings keep counting
 * until the threshold completes.</p>
 */
public final class FloorCompletionTask extends DungeonTask {

    /** Smallest layer/floor index (the suite's own bound). */
    public static final int MIN_INDEX = 0;
    /** Config field names, shared by SNBT, net sync and the editor group. */
    private static final String LAYER_KEY = "layer";
    private static final String FLOOR_KEY = "floor";

    private int layer = MIN_INDEX;
    private int floor = MIN_INDEX;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public FloorCompletionTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireFloorCompletionTaskType();
    }

    /** @return the configured layer index */
    public int getLayer() {
        return layer;
    }

    /** @param value the layer index, clamped to >= {@value #MIN_INDEX} */
    public void setLayer(int value) {
        layer = Math.max(MIN_INDEX, value);
    }

    /** @return the configured floor index within the layer */
    public int getFloor() {
        return floor;
    }

    /** @param value the floor index, clamped to >= {@value #MIN_INDEX} */
    public void setFloor(int value) {
        floor = Math.max(MIN_INDEX, value);
    }

    /**
     * Whether an entered floor is <em>this</em> task's subject.
     *
     * @param eventLayer the event's layer index
     * @param eventFloor the event's floor index
     * @return true when both match the configuration
     */
    public boolean matchesContext(int eventLayer, int eventFloor) {
        return layer == eventLayer && floor == eventFloor;
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putInt(LAYER_KEY, layer);
        tag.putInt(FLOOR_KEY, floor);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        // Absent keys read as 0 via getInt - the additive default.
        layer = Math.max(MIN_INDEX, tag.getInt(LAYER_KEY));
        floor = Math.max(MIN_INDEX, tag.getInt(FLOOR_KEY));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(layer);
        buffer.writeVarInt(floor);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        layer = Math.max(MIN_INDEX, buffer.readVarInt());
        floor = Math.max(MIN_INDEX, buffer.readVarInt());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        super.fillConfigGroup(group);
        group.addInt(LAYER_KEY, layer, value -> layer = Math.max(MIN_INDEX, value),
            MIN_INDEX, MIN_INDEX, Integer.MAX_VALUE);
        group.addInt(FLOOR_KEY, floor, value -> floor = Math.max(MIN_INDEX, value),
            MIN_INDEX, MIN_INDEX, Integer.MAX_VALUE);
    }
}
