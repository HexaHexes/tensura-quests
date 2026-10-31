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
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SjVersionGate;
import org.bitwisemadness.tensuraquests.rewards.MessageReward;
import org.bitwisemadness.tensuraquests.rewards.UnlockJobSlotReward;
import org.bitwisemadness.tensuraquests.tasks.CounterTask;
import org.bitwisemadness.tensuraquests.tasks.JobMasteryTask;
import org.bitwisemadness.tensuraquests.tasks.JobParticipationTask;
import org.bitwisemadness.tensuraquests.tasks.MultiJobMasteryTask;
import org.bitwisemadness.tensuraquests.tasks.PlayerTransactionTask;
import org.bitwisemadness.tensuraquests.tasks.SalesMilestoneTask;
import org.bitwisemadness.tensuraquests.tasks.TradingPartnerTask;

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

    /** Registration id of the Job Mastery task type (FR-020). */
    public static final ResourceLocation JOB_MASTERY_TASK_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "job_mastery");

    /** Translation key of the job mastery task's editor display name. */
    public static final String JOB_MASTERY_TASK_NAME_KEY = "ftbquests.task.tensura_quests.job_mastery";

    /** Registration id of the Multi-Job Mastery task type (FR-021). */
    public static final ResourceLocation MULTI_JOB_MASTERY_TASK_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "multi_job_mastery");

    /** Translation key of the multi job mastery task's editor display name. */
    public static final String MULTI_JOB_MASTERY_TASK_NAME_KEY = "ftbquests.task.tensura_quests.multi_job_mastery";

    /** Registration id of the Job Participation task type (FR-022). */
    public static final ResourceLocation JOB_PARTICIPATION_TASK_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "job_participation");

    /** Translation key of the job participation task's editor display name. */
    public static final String JOB_PARTICIPATION_TASK_NAME_KEY = "ftbquests.task.tensura_quests.job_participation";

    /** Registration id of the Player Transaction task type (FR-040). */
    public static final ResourceLocation PLAYER_TRANSACTION_TASK_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "player_transaction");

    /** Translation key of the player transaction task's editor display name. */
    public static final String PLAYER_TRANSACTION_TASK_NAME_KEY = "ftbquests.task.tensura_quests.player_transaction";

    /** Registration id of the Sales Milestone task type (FR-041). */
    public static final ResourceLocation SALES_MILESTONE_TASK_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "sales_milestone");

    /** Translation key of the sales milestone task's editor display name. */
    public static final String SALES_MILESTONE_TASK_NAME_KEY = "ftbquests.task.tensura_quests.sales_milestone";

    /** Registration id of the Trading Partner task type (FR-042). */
    public static final ResourceLocation TRADING_PARTNER_TASK_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "trading_partner");

    /** Translation key of the trading partner task's editor display name. */
    public static final String TRADING_PARTNER_TASK_NAME_KEY = "ftbquests.task.tensura_quests.trading_partner";

    /** Registration id of the Unlock Job Slot reward type (FR-023). */
    public static final ResourceLocation UNLOCK_JOB_SLOT_REWARD_ID =
        ResourceLocation.fromNamespaceAndPath(TensuraQuests.MOD_ID, "unlock_job_slot");

    /** Translation key of the unlock job slot reward's editor display name. */
    public static final String UNLOCK_JOB_SLOT_REWARD_NAME_KEY = "ftbquests.reward.tensura_quests.unlock_job_slot";

    private static TaskType counterTaskType;
    private static RewardType messageRewardType;
    private static RewardType unlockJobSlotRewardType;
    private static TaskType jobMasteryTaskType;
    private static TaskType multiJobMasteryTaskType;
    private static TaskType jobParticipationTaskType;
    private static TaskType playerTransactionTaskType;
    private static TaskType salesMilestoneTaskType;
    private static TaskType tradingPartnerTaskType;

    private FtbQuestsBridge() {
    }

    /**
     * Register the task and reward types that ship unconditionally.
     * Called once from the mod constructor: the first reference to
     * {@link TaskTypes} or {@link RewardTypes} runs its static
     * initialiser (registering the built-in types), then adds ours to
     * the same registries. Registration is idempotent -
     * {@code computeIfAbsent} keeps the first instance per id. The
     * Sympathetic Jobs family registers separately through
     * {@link #registerJobTaskTypes()}, behind the version gate.
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
     * Register the Sympathetic Jobs task family - called only after
     * the version gate has ruled the installed Sympathetic Jobs
     * acceptable (COMP-003), so the task classes and the adapter they
     * touch are never loaded on a machine where the family stays
     * disabled (INT-004).
     */
    public static void registerJobTaskTypes() {
        jobMasteryTaskType = TaskTypes.register(
            JOB_MASTERY_TASK_ID,
            JobMasteryTask::new,
            () -> Icon.getIcon("minecraft:item/experience_bottle"));
        jobMasteryTaskType.setDisplayName(Component.translatable(JOB_MASTERY_TASK_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests task type {} (internalId={})",
            jobMasteryTaskType.getTypeId(), jobMasteryTaskType.internalId);

        multiJobMasteryTaskType = TaskTypes.register(
            MULTI_JOB_MASTERY_TASK_ID,
            MultiJobMasteryTask::new,
            () -> Icon.getIcon("minecraft:item/enchanted_book"));
        multiJobMasteryTaskType.setDisplayName(Component.translatable(MULTI_JOB_MASTERY_TASK_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests task type {} (internalId={})",
            multiJobMasteryTaskType.getTypeId(), multiJobMasteryTaskType.internalId);

        jobParticipationTaskType = TaskTypes.register(
            JOB_PARTICIPATION_TASK_ID,
            JobParticipationTask::new,
            () -> Icon.getIcon("minecraft:item/player_head"));
        jobParticipationTaskType.setDisplayName(Component.translatable(JOB_PARTICIPATION_TASK_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests task type {} (internalId={})",
            jobParticipationTaskType.getTypeId(), jobParticipationTaskType.internalId);
    }

    /**
     * Register the Unlock Job Slot reward - called from the SAME
     * Sympathetic Jobs version gate as the job task family (one
     * gate, one family): an off-pin jar registers neither, and the
     * write path re-checks the gate at claim time anyway (FR-024).
     */
    public static void registerJobSlotReward() {
        unlockJobSlotRewardType = RewardTypes.register(
            UNLOCK_JOB_SLOT_REWARD_ID,
            UnlockJobSlotReward::new,
            () -> Icon.getIcon("minecraft:item/gold_nugget"));
        unlockJobSlotRewardType.setDisplayName(Component.translatable(UNLOCK_JOB_SLOT_REWARD_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests reward type {} (internalId={})",
            unlockJobSlotRewardType.getTypeId(), unlockJobSlotRewardType.internalId);
    }

    /**
     * Register the Lightman's Currency economy task family - called
     * only after the INT-004 presence probe has ruled the mod
     * installed, mirroring the job family's gate discipline: the task
     * classes (and the adapter they never import) stay unloaded when
     * the family is disabled.
     */
    public static void registerEconomyTaskTypes() {
        playerTransactionTaskType = TaskTypes.register(
            PLAYER_TRANSACTION_TASK_ID,
            PlayerTransactionTask::new,
            () -> Icon.getIcon("minecraft:item/emerald"));
        playerTransactionTaskType.setDisplayName(Component.translatable(PLAYER_TRANSACTION_TASK_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests task type {} (internalId={})",
            playerTransactionTaskType.getTypeId(), playerTransactionTaskType.internalId);

        salesMilestoneTaskType = TaskTypes.register(
            SALES_MILESTONE_TASK_ID,
            SalesMilestoneTask::new,
            () -> Icon.getIcon("minecraft:item/gold_ingot"));
        salesMilestoneTaskType.setDisplayName(Component.translatable(SALES_MILESTONE_TASK_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests task type {} (internalId={})",
            salesMilestoneTaskType.getTypeId(), salesMilestoneTaskType.internalId);

        tradingPartnerTaskType = TaskTypes.register(
            TRADING_PARTNER_TASK_ID,
            TradingPartnerTask::new,
            () -> Icon.getIcon("minecraft:item/map"));
        tradingPartnerTaskType.setDisplayName(Component.translatable(TRADING_PARTNER_TASK_NAME_KEY));
        TensuraQuests.LOGGER.info("registered FTB Quests task type {} (internalId={})",
            tradingPartnerTaskType.getTypeId(), tradingPartnerTaskType.internalId);
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
     * The registered job mastery task type.
     *
     * @throws IllegalStateException if the job family never
     *         registered - Sympathetic Jobs absent or off-pin at
     *         startup; a quest file referencing this type then fails
     *         loudly at construction instead of silently stalling
     */
    public static TaskType requireJobMasteryTaskType() {
        return requireJobTaskType(jobMasteryTaskType, "job_mastery");
    }

    /**
     * The registered multi job mastery task type.
     *
     * @throws IllegalStateException if the job family never
     *         registered - see {@link #requireJobMasteryTaskType()}
     */
    public static TaskType requireMultiJobMasteryTaskType() {
        return requireJobTaskType(multiJobMasteryTaskType, "multi_job_mastery");
    }

    /**
     * The registered job participation task type.
     *
     * @throws IllegalStateException if the job family never
     *         registered - see {@link #requireJobMasteryTaskType()}
     */
    public static TaskType requireJobParticipationTaskType() {
        return requireJobTaskType(jobParticipationTaskType, "job_participation");
    }

    private static TaskType requireJobTaskType(TaskType type, String name) {
        if (type == null) {
            throw new IllegalStateException(name + " task type missing: the Sympathetic Jobs version gate "
                + "must pass (SjVersionGate " + SjVersionGate.PINNED_VERSION + " pinned) before "
                + "FtbQuestsBridge.registerJobTaskTypes() runs");
        }
        return type;
    }

    /**
     * The registered player transaction task type.
     *
     * @throws IllegalStateException if the economy family never
     *         registered - Lightman's Currency absent at startup; a
     *         quest file referencing this type then fails loudly at
     *         construction instead of silently stalling
     */
    public static TaskType requirePlayerTransactionTaskType() {
        return requireEconomyTaskType(playerTransactionTaskType, "player_transaction");
    }

    /**
     * The registered sales milestone task type.
     *
     * @throws IllegalStateException if the economy family never
     *         registered - see {@link #requirePlayerTransactionTaskType()}
     */
    public static TaskType requireSalesMilestoneTaskType() {
        return requireEconomyTaskType(salesMilestoneTaskType, "sales_milestone");
    }

    /**
     * The registered trading partner task type.
     *
     * @throws IllegalStateException if the economy family never
     *         registered - see {@link #requirePlayerTransactionTaskType()}
     */
    public static TaskType requireTradingPartnerTaskType() {
        return requireEconomyTaskType(tradingPartnerTaskType, "trading_partner");
    }

    private static TaskType requireEconomyTaskType(TaskType type, String name) {
        if (type == null) {
            throw new IllegalStateException(name + " task type missing: the Lightman's Currency presence probe "
                + "(LcPresence) must pass before FtbQuestsBridge.registerEconomyTaskTypes() runs");
        }
        return type;
    }

    /**
     * The registered unlock job slot reward type.
     *
     * @throws IllegalStateException if the Sympathetic Jobs family
     *         never registered - SJ absent or off-pin at startup; a
     *         quest file referencing this type then fails loudly at
     *         construction instead of silently promising slots
     */
    public static RewardType requireUnlockJobSlotRewardType() {
        if (unlockJobSlotRewardType == null) {
            throw new IllegalStateException("unlock_job_slot reward type missing: the Sympathetic Jobs version gate "
                + "must pass (SjVersionGate " + SjVersionGate.PINNED_VERSION + " pinned) before "
                + "FtbQuestsBridge.registerJobSlotReward() runs");
        }
        return unlockJobSlotRewardType;
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
