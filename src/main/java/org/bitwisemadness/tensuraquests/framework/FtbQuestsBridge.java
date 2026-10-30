package org.bitwisemadness.tensuraquests.framework;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.rewards.MessageReward;
import org.bitwisemadness.tensuraquests.tasks.CounterTask;

/**
 * The only contact point between this mod and FTB Quests'
 * registration surface (INT-003).
 *
 * <p>{@code TaskTypes} and {@code RewardTypes} register as class-load
 * side effects and live outside FTB Quests' stable {@code api} package
 * (accepted risk R4, U-05). This class is the sole importer of that
 * registration surface: an upstream refactor breaks exactly one file,
 * the GameTest suite notices on the next build, and no domain code
 * holds unstable imports. Task and reward classes still return the
 * registered type from {@code getType()}, which is their contract
 * rather than the registry itself.</p>
 */
public final class FtbQuestsBridge {

    /** Registration id of the first custom task type. */
    public static final ResourceLocation COUNTER_TASK_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "counter");

    /** Translation key of the counter task's editor display name. */
    public static final String COUNTER_TASK_NAME_KEY = "ftbquests.task.tensura_quests.counter";

    /** Registration id of the first custom reward type. */
    public static final ResourceLocation MESSAGE_REWARD_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "message");

    /** Translation key of the message reward's editor display name. */
    public static final String MESSAGE_REWARD_NAME_KEY = "ftbquests.reward.tensura_quests.message";

    private static TaskType counterTaskType;
    private static RewardType messageRewardType;

    private FtbQuestsBridge() {
    }

    /**
     * Register every task and reward type this mod ships. Called once
     * from the mod constructor: the first reference to {@link TaskTypes}
     * or {@link RewardTypes} runs its static initialiser (registering
     * the built-in types), then adds ours to the same registries.
     * Registration is idempotent - {@code computeIfAbsent} keeps the
     * first instance per id.
     */
    public static void registerTypes() {
        counterTaskType = TaskTypes.register(
            COUNTER_TASK_ID,
            CounterTask::new,
            () -> Icon.getIcon("minecraft:item/repeater"));
        counterTaskType.setDisplayName(Component.translatable(COUNTER_TASK_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests task type {} (internalId={})",
            counterTaskType.getTypeId(), counterTaskType.internalId);

        messageRewardType = RewardTypes.register(
            MESSAGE_REWARD_ID,
            MessageReward::new,
            () -> Icon.getIcon("minecraft:item/writable_book"));
        messageRewardType.setDisplayName(Component.translatable(MESSAGE_REWARD_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests reward type {} (internalId={})",
            messageRewardType.getTypeId(), messageRewardType.internalId);
    }

    /**
     * The registered counter task type.
     *
     * @throws IllegalStateException if registration never ran - a
     *         silently missing type would surface much later, as an
     *         unresolvable id in a saved quest file
     */
    public static TaskType requireCounterTaskType() {
        if (counterTaskType == null) {
            throw new IllegalStateException(
                "counter task type missing: FtbQuestsBridge.registerTypes() must run from the mod constructor");
        }
        return counterTaskType;
    }

    /**
     * The registered message reward type.
     *
     * @throws IllegalStateException if registration never ran - a
     *         silently missing type would surface much later, as an
     *         unresolvable id in a saved quest file
     */
    public static RewardType requireMessageRewardType() {
        if (messageRewardType == null) {
            throw new IllegalStateException(
                "message reward type missing: FtbQuestsBridge.registerTypes() must run from the mod constructor");
        }
        return messageRewardType;
    }

    /**
     * Read-only registry lookup by id, so tests and framework code
     * never import the registry themselves.
     *
     * @param id the task type id to resolve
     * @return the registered type, empty when nothing is registered
     *         under that id
     */
    public static Optional<TaskType> findTaskType(ResourceLocation id) {
        return Optional.ofNullable(TaskTypes.TYPES.get(id));
    }

    /**
     * Read-only registry lookup by id, so tests and framework code
     * never import the registry themselves.
     *
     * @param id the reward type id to resolve
     * @return the registered type, empty when nothing is registered
     *         under that id
     */
    public static Optional<RewardType> findRewardType(ResourceLocation id) {
        return Optional.ofNullable(RewardTypes.TYPES.get(id));
    }
}
