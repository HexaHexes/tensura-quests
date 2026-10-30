package org.bitwisemadness.tensuraquests.rewards;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.framework.ClaimResult;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.framework.TensuraReward;

/**
 * The first custom reward type: sends the claiming player the configured
 * message, proving the reliable claim lifecycle before domain rewards
 * build on it (issue #9).
 *
 * <p>The effect is deliberately domain-neutral - like {@code CounterTask}
 * for tasks - so the lifecycle itself is what the GameTests exercise:
 * registration through the facade, editor configuration, SNBT and net
 * round-trips, server-side claim execution, and the loud-failure path.
 * A blank message is a real misconfiguration, and this reward treats it
 * as one: the claim fails with a reason, nothing is granted, the claim
 * mark is released, and the player is told - rather than the reward
 * silently doing nothing and staying burned.</p>
 */
public final class MessageReward extends TensuraReward {

    /** Config field name, shared by SNBT, net sync and the editor group. */
    private static final String MESSAGE_KEY = "message";

    /** Target for newly created rewards and for saves predating the field. */
    public static final String DEFAULT_MESSAGE = "";

    private String message = DEFAULT_MESSAGE;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this reward belongs to
     */
    public MessageReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FtbQuestsBridge.requireMessageRewardType();
    }

    /**
     * @return the configured message text
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param value the new message text; null becomes the default
     */
    public void setMessage(String value) {
        message = value == null ? DEFAULT_MESSAGE : value;
    }

    @Override
    protected ClaimResult executeClaim(ServerPlayer player) {
        if (message.isBlank()) {
            // A blank message means the quest author never configured the
            // effect; refuse loudly instead of granting nothing.
            return ClaimResult.failed("message is blank");
        }
        player.sendSystemMessage(Component.literal(message));
        return ClaimResult.granted();
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putString(MESSAGE_KEY, message);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        // Absent field (saves predating it) falls back to the default;
        // upstream's getString returns "" for an absent key anyway.
        message = tag.contains(MESSAGE_KEY) ? tag.getString(MESSAGE_KEY) : DEFAULT_MESSAGE;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(message);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        message = buffer.readUtf();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        // Client-only like the whole fillConfigGroup chain (rationale on
        // TensuraReward.fillConfigGroup): a dedicated server strips it.
        super.fillConfigGroup(group);
        group.addString(MESSAGE_KEY, message, this::setMessage, DEFAULT_MESSAGE);
    }
}
