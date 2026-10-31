package org.bitwisemadness.tensuraquests.rewards;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import java.util.OptionalInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SympatheticJobs;
import org.bitwisemadness.tensuraquests.framework.ClaimResult;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.framework.TensuraReward;
import org.bitwisemadness.tensuraquests.tasks.JobTask;

/**
 * Unlock Job Slot: permanently raises the claiming player's
 * Sympathetic Jobs slot entitlement by the configured amount
 * (FR-023, decision D3).
 *
 * <p>Sympathetic Jobs owns slot truth: the claim goes through the
 * single adapter's write path ({@link SympatheticJobs#grantJobSlots}),
 * which raises the per-player override, lets SJ persist it in its own
 * player attachment and sync it, and <em>verifies</em> the effective
 * limit actually rose - a clamp at SJ's total-job cap or an off-pin
 * version rolls back / refuses and the claim fails loudly
 * (FR-024/FR-007), never granting a partial slot silently. No slot
 * counter of ours exists anywhere (SEC-007), the scope is the forced
 * per-player one from {@link TensuraReward} (a teammate's claim can
 * never grant your slots or theirs), and FTB Quests' per-player claim
 * mark keeps the claim single-shot (SEC-002).</p>
 */
public final class UnlockJobSlotReward extends TensuraReward {

    /** Smallest grant: the reward must add at least one slot. */
    public static final int MIN_SLOTS = 1;
    /** Grant for newly created rewards and saves predating the field. */
    public static final int DEFAULT_SLOTS = 1;
    /**
     * Largest grant: Sympathetic Jobs' total job count (its {@code
     * JobId.ALL} length, bytecode-verified as {@code MAX_SLOTS = 12} -
     * a literal here, referenced without importing SJ classes). The
     * entitlement clamps there anyway; the editor just stops at the
     * same wall.
     */
    public static final int MAX_SLOTS = 12;

    /** Config field name, shared by SNBT, net sync and the editor group. */
    private static final String SLOTS_KEY = "slots";

    private int slots = DEFAULT_SLOTS;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this reward belongs to
     */
    public UnlockJobSlotReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FtbQuestsBridge.requireUnlockJobSlotRewardType();
    }

    /**
     * @return the number of slots this reward grants
     */
    public int getSlots() {
        return slots;
    }

    /**
     * @param value the new grant, clamped into
     *              [{@value #MIN_SLOTS}, {@value #MAX_SLOTS}]
     */
    public void setSlots(int value) {
        slots = clampSlots(value);
    }

    @Override
    protected String integration() {
        // Same integration id the job tasks log under: one gameplay
        // system, one label in the intake/claim trail.
        return JobTask.INTEGRATION;
    }

    @Override
    protected ClaimResult executeClaim(ServerPlayer player) {
        OptionalInt granted = SympatheticJobs.grantJobSlots(player, slots);
        if (granted.isEmpty()) {
            // The adapter already logged the precise reason (version
            // gate or clamped entitlement); the player gets the honest
            // failure and TensuraReward releases the claim mark.
            return ClaimResult.failed("job slots could not be granted: "
                + "the slot limit did not rise by " + slots + " (see the server log)");
        }
        return ClaimResult.granted();
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putInt(SLOTS_KEY, slots);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        if (!tag.contains(SLOTS_KEY)) {
            // Additive field: saves written before it existed keep the default.
            slots = DEFAULT_SLOTS;
            return;
        }
        int stored = tag.getInt(SLOTS_KEY);
        slots = clampSlots(stored);
        if (slots != stored) {
            TensuraQuests.LOGGER.warn(
                "unlock job slot reward {} stored slots {} outside [{}, {}]; using {}",
                getId(), stored, MIN_SLOTS, MAX_SLOTS, slots);
        }
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(slots);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        slots = clampSlots(buffer.readVarInt());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        // Client-only like the whole fillConfigGroup chain (rationale
        // on TensuraReward.fillConfigGroup).
        super.fillConfigGroup(group);
        group.addInt(SLOTS_KEY, slots, value -> slots = clampSlots(value),
            DEFAULT_SLOTS, MIN_SLOTS, MAX_SLOTS);
    }

    private static int clampSlots(int value) {
        if (value < MIN_SLOTS) {
            return MIN_SLOTS;
        }
        return Math.min(value, MAX_SLOTS);
    }
}
