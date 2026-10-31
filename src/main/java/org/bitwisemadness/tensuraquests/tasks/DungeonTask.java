package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.framework.TensuraTask;

/**
 * Base class for the four dungeon progression task types (FR-030..FR-034).
 *
 * <p>Dungeon tasks are <em>event-driven</em>: the suite's lifecycle events
 * reach the adapter ({@code DungeonIntake}), which validates dimension
 * context, resolves the attributed player and hands each candidate its
 * one-unit intake through {@link #intake}. This base owns what they share -
 * the configured dimension set (open-ended: the suite keeps adding satellite
 * dimensions, so nothing is ever hardcoded - FR-033/SEC-003), the target
 * threshold, and the validated credit step through
 * {@link TensuraTask#recordValidatedIntake} with integration
 * {@value #INTEGRATION} and a per-event reason (FR-012).</p>
 *
 * <p>Every event arrives with a dimension; tasks configured for other
 * dimensions increment nothing and log the rejection visibly - the spec's
 * "no progress plus a log line". Layer/floor mismatches and already-completed
 * tasks are ordinary filtering and stay silent.</p>
 */
public abstract class DungeonTask extends TensuraTask {

    /** Integration id logged with every dungeon intake decision (FR-012). */
    public static final String INTEGRATION = "infinite_dungeon";

    /** Smallest allowed target: at zero the task would already be complete. */
    public static final long MIN_TARGET = 1L;
    /** Target for newly created tasks and saves predating the field. */
    public static final long DEFAULT_TARGET = 1L;
    /** Largest allowed target; intake past it is clamped by FTB Quests. */
    public static final long MAX_TARGET = Long.MAX_VALUE;
    /** Bounds the configured dimension list (wire format + editor). */
    public static final int MAX_DIMENSIONS = 16;
    /** Editor default element - a real id so the pattern accepts it. */
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";
    /** Editor/entry pattern: a resource location's shape. */
    public static final String DIMENSION_PATTERN = "[a-z0-9_.-]+:[a-z0-9_./-]+";

    /** Config field names, shared by SNBT, net sync and the editor group. */
    private static final String DIMENSIONS_KEY = "dimensions";
    private static final String TARGET_KEY = "target";

    private List<String> dimensions = new ArrayList<>();
    private long target = DEFAULT_TARGET;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    protected DungeonTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public long getMaxProgress() {
        return target;
    }

    /**
     * @return an immutable snapshot of the configured dimension ids
     *         (lowercase {@code namespace:path}); empty matches nothing -
     *         an unconfigured dungeon task never completes, by design
     */
    public List<String> getDimensions() {
        return List.copyOf(dimensions);
    }

    /**
     * @param value the new dimension ids; null/oversized input is
     *              normalized (dropped entries beyond {@value #MAX_DIMENSIONS})
     */
    public void setDimensions(List<String> value) {
        dimensions = sanitizeDimensions(value);
    }

    /**
     * Whether an event from this dimension may count for this task.
     *
     * @param dimensionId the event's dimension id string
     * @return true when configured (pure - JUnit and intake share it)
     */
    public boolean matchesDimension(String dimensionId) {
        return dimensions.contains(dimensionId);
    }

    /**
     * @return the completion threshold (units of this task's event kind)
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

    /**
     * The one intake gate every dungeon event runs through, in order of
     * specificity: a foreign dimension is a visible rejection (FR-033),
     * a context mismatch (wrong layer/floor) is ordinary filtering, and
     * a completed task is a silent no-op - only then does one unit of
     * validated progress move.
     *
     * @param player the online, attributed player being credited
     * @param data their team data, or null (intake logs the rejection)
     * @param eventDimensionId the event's dimension id string
     * @param contextMatches whether the event's layer/floor is this
     *                       task's configured subject
     * @param reason caller's reason, logged verbatim
     */
    public final void intake(ServerPlayer player, TeamData data, String eventDimensionId,
            boolean contextMatches, String reason) {
        if (!matchesDimension(eventDimensionId)) {
            TensuraQuests.LOGGER.warn(
                "dungeon intake skipped reason=foreign_dimension task={} event_dimension={} configured={}",
                getId(), eventDimensionId, dimensions);
            return;
        }
        if (!contextMatches) {
            return; // not this task's subject: ordinary filtering, no log
        }
        if (data != null && data.isCompleted(this)) {
            return; // already done: no re-intake noise
        }
        recordValidatedIntake(player, data, 1L, INTEGRATION, reason);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        List<String> parsed = new ArrayList<>();
        for (Tag entry : tag.getList(DIMENSIONS_KEY, Tag.TAG_STRING)) {
            String id = entry.getAsString().toLowerCase(Locale.ROOT);
            if (id.matches(DIMENSION_PATTERN)) {
                parsed.add(id);
            } else {
                TensuraQuests.LOGGER.warn("dungeon task {} drops malformed dimension '{}'", getId(), id);
            }
        }
        dimensions = sanitizeDimensions(parsed);
        if (!tag.contains(TARGET_KEY)) {
            // Additive field: saves written before it existed keep the default.
            target = DEFAULT_TARGET;
        } else {
            long stored = tag.getLong(TARGET_KEY);
            target = clampTarget(stored);
            if (target != stored) {
                TensuraQuests.LOGGER.warn("dungeon task {} stored target {} outside [{}, {}]; using {}",
                    getId(), stored, MIN_TARGET, MAX_TARGET, target);
            }
        }
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        ListTag entries = new ListTag();
        for (String id : dimensions) {
            entries.add(StringTag.valueOf(id));
        }
        tag.put(DIMENSIONS_KEY, entries);
        tag.putLong(TARGET_KEY, target);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_DIMENSIONS) {
            throw new IllegalStateException("dungeon task " + getId() + " received " + count
                + " dimensions; expected 0.." + MAX_DIMENSIONS);
        }
        List<String> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            read.add(buffer.readUtf().toLowerCase(Locale.ROOT));
        }
        dimensions = sanitizeDimensions(read);
        target = clampTarget(buffer.readVarLong());
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(dimensions.size());
        for (String id : dimensions) {
            buffer.writeUtf(id);
        }
        buffer.writeVarLong(target);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        // Client-only like the whole fillConfigGroup chain.
        super.fillConfigGroup(group);
        List<String> editable = new ArrayList<>(dimensions);
        group.addList(DIMENSIONS_KEY, editable,
            new StringConfig(java.util.regex.Pattern.compile(DIMENSION_PATTERN)),
            values -> dimensions = sanitizeDimensions(values),
            DEFAULT_DIMENSION);
        group.addLong(TARGET_KEY, target, value -> target = clampTarget(value),
            DEFAULT_TARGET, MIN_TARGET, MAX_TARGET);
    }

    private static List<String> sanitizeDimensions(List<String> value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> kept = new ArrayList<>();
        for (String id : value) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            // One validation point for every path (setter, editor, SNBT, net):
            // a bare or malformed id can never match an event's namespaced
            // dimension id, so keeping it would be a silent dead config.
            if (!normalized.matches(DIMENSION_PATTERN)) {
                continue;
            }
            if (kept.size() >= MAX_DIMENSIONS) {
                TensuraQuests.LOGGER.warn("dungeon task keeps only the first {} dimensions ({} given)",
                    MAX_DIMENSIONS, value.size());
                break;
            }
            kept.add(normalized);
        }
        return kept;
    }

    private static long clampTarget(long value) {
        if (value < MIN_TARGET) {
            return MIN_TARGET;
        }
        return Math.min(value, MAX_TARGET);
    }
}
