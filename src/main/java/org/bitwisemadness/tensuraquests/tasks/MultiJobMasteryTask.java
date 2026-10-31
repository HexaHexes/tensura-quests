package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.regex.Pattern;
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
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SympatheticJobs;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;

/**
 * Multi-Job Mastery: completes when <em>every</em> configured
 * (job, level) pair is satisfied at once (FR-021).
 *
 * <p>The player's counter converges to
 * {@code sum(min(currentLevel, requiredLevel))} over all pairs and
 * the maximum is {@code sum(requiredLevel)} - a sum that reaches its
 * maximum exactly when every term does, so completion is equivalent
 * to "all pairs satisfied" while the bar still moves smoothly as each
 * job levels. Pairs are stored as canonical {@link JobRequirement}
 * strings (the save, wire and editor format alike).</p>
 *
 * <p>A pair naming an unknown job contributes 0 (and warns once): the
 * task can never complete until the configuration names real jobs,
 * while the known pairs still move the visible bar.</p>
 */
public final class MultiJobMasteryTask extends JobTask {

    /**
     * Largest accepted pair count: bounds the editor and the wire
     * format, and keeps the summed maximum inside a long (16 pairs x
     * Integer.MAX_VALUE required levels each).
     */
    public static final int MAX_REQUIREMENTS = 16;
    /** Smallest sensible maximum progress when no pairs are configured. */
    public static final long MIN_MAX_PROGRESS = 1L;

    /** Config field name, shared by SNBT, net sync and the editor group. */
    private static final String REQUIREMENTS_KEY = "requirements";
    /** Editor element pattern; parsing is stricter (see JobRequirement). */
    private static final Pattern EDITOR_PATTERN = Pattern.compile(JobRequirement.FORMAT_PATTERN);
    /** Editor placeholder shown when a fresh task has no pairs yet. */
    private static final String DEFAULT_ENTRY = "miner:1";

    private List<JobRequirement> requirements = new ArrayList<>();

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public MultiJobMasteryTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireMultiJobMasteryTaskType();
    }

    @Override
    public long getMaxProgress() {
        long sum = 0L;
        for (JobRequirement requirement : requirements) {
            sum += requirement.level();
        }
        // No pairs (or an unfixed save) means the task can never be
        // satisfied: keep a real threshold over a free completion.
        return Math.max(MIN_MAX_PROGRESS, sum);
    }

    /**
     * @return an immutable snapshot of the configured pairs
     */
    public List<JobRequirement> getRequirements() {
        return List.copyOf(requirements);
    }

    /**
     * Replaces the configured pairs; entries above
     * {@value #MAX_REQUIREMENTS} are dropped with a warning.
     *
     * @param value the new pairs; null clears them
     */
    public void setRequirements(List<JobRequirement> value) {
        requirements = sanitize(value);
    }

    @Override
    protected OptionalLong desiredProgress(ServerPlayer player) {
        long sum = 0L;
        for (JobRequirement requirement : requirements) {
            OptionalInt level = SympatheticJobs.level(player, requirement.job());
            if (level.isEmpty()) {
                warnUnknownJob(requirement.job());
                continue;
            }
            sum += Math.min(level.getAsInt(), requirement.level());
        }
        return OptionalLong.of(sum);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        ListTag entries = new ListTag();
        for (String formatted : formatAll(requirements)) {
            entries.add(StringTag.valueOf(formatted));
        }
        tag.put(REQUIREMENTS_KEY, entries);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        List<JobRequirement> parsed = new ArrayList<>();
        for (Tag entry : tag.getList(REQUIREMENTS_KEY, Tag.TAG_STRING)) {
            String raw = entry.getAsString();
            JobRequirement.parse(raw).ifPresentOrElse(parsed::add, () ->
                TensuraQuests.LOGGER.warn("multi job mastery task {} drops malformed requirement '{}'",
                    getId(), raw));
        }
        requirements = sanitize(parsed);
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(requirements.size());
        for (JobRequirement requirement : requirements) {
            buffer.writeUtf(requirement.format());
        }
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_REQUIREMENTS) {
            throw new IllegalStateException("multi job mastery task " + getId()
                + " received " + count + " requirements; expected 0.." + MAX_REQUIREMENTS);
        }
        List<JobRequirement> parsed = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Malformed entries are impossible on the wire (we wrote
            // them), but drop rather than trust if they appear.
            JobRequirement.parse(buffer.readUtf()).ifPresent(parsed::add);
        }
        requirements = sanitize(parsed);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        // Client-only, like the upstream task overrides: the editor
        // path never runs on a dedicated server.
        super.fillConfigGroup(group);
        List<String> editable = formatAll(requirements);
        group.addList(REQUIREMENTS_KEY, editable,
            new StringConfig(EDITOR_PATTERN),
            values -> requirements = sanitize(parseAll(values)),
            DEFAULT_ENTRY);
    }

    private static List<String> formatAll(List<JobRequirement> source) {
        List<String> formatted = new ArrayList<>(source.size());
        for (JobRequirement requirement : source) {
            formatted.add(requirement.format());
        }
        return formatted;
    }

    private static List<JobRequirement> parseAll(List<String> raw) {
        List<JobRequirement> parsed = new ArrayList<>();
        for (String entry : raw) {
            JobRequirement.parse(entry).ifPresent(parsed::add);
        }
        return parsed;
    }

    private static List<JobRequirement> sanitize(List<JobRequirement> value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        if (value.size() <= MAX_REQUIREMENTS) {
            return new ArrayList<>(value);
        }
        TensuraQuests.LOGGER.warn("multi job mastery keeps only the first {} requirements ({} given)",
            MAX_REQUIREMENTS, value.size());
        return new ArrayList<>(value.subList(0, MAX_REQUIREMENTS));
    }
}
