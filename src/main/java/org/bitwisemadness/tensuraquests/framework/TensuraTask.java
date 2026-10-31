package org.bitwisemadness.tensuraquests.framework;

import dev.architectury.utils.Env;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.progress.CounterPolicy;
import org.bitwisemadness.tensuraquests.progress.ProgressAttachments;

/**
 * Base class for every custom task type this mod registers.
 *
 * <p>Progress enters through
 * {@link #recordValidatedIntake(ServerPlayer, TeamData, long, String, String)}
 * only: intake validates that the quest file is server-side (NFR-002),
 * applies the pure {@link IntakeRules} decision to the mirrored
 * progress, adds the amount to the <em>acting player's personal
 * counter</em> (ADR-0002, FR-008), then mirrors the
 * <em>policy-relevant aggregate</em> over the team's personal counters
 * into FTB Quests' per-team {@link TeamData} - so the quest bar shows
 * exactly the value {@link CounterPolicy} is waiting on (FR-009,
 * FR-010). Every grant and rejection is logged with the calling
 * integration, the caller's reason and the quest, task, team and
 * player ids (FR-012, NFR-004).</p>
 *
 * <p>The completion policy is ordinary task configuration: SNBT, net
 * sync and the editor group, defaulting to {@link CounterPolicy#ANY}.
 * A member whose state is not loaded (offline) contributes 0 to the
 * snapshot - never completing a team whose members were not all
 * verified - and the next intake or login re-converges the mirror.</p>
 *
 * <p>Client sync carries configuration only; no client-supplied value
 * ever reaches the progress write.</p>
 */
public abstract class TensuraTask extends Task {

    /**
     * Editor lang key for the policy control. Set explicitly because
     * the default key derives from the per-task-type config group, and
     * this field lives on the shared base class.
     */
    private static final String POLICY_NAME_KEY = "ftbquests.task.tensura_quests.completion_policy";

    /**
     * The editor's display names for the policy options. Deliberately a
     * static field here rather than on {@link CounterPolicy}: building
     * it needs Minecraft classes, and the enum must stay loadable by
     * the Minecraft-free JUnit suite (issue #8).
     */
    private static final NameMap<CounterPolicy> POLICY_NAMES = NameMap
        .of(CounterPolicy.ANY, List.of(CounterPolicy.ANY, CounterPolicy.ALL))
        .id(policy -> policy.id)
        .name(policy -> Component.translatable(POLICY_NAME_KEY + "." + policy.id))
        .create();

    private CounterPolicy completionPolicy = CounterPolicy.ANY;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    protected TensuraTask(long id, Quest quest) {
        super(id, quest);
    }

    /**
     * The configured completion policy for this task (FR-009/FR-010).
     *
     * @return the policy; never null - defaults to {@link CounterPolicy#ANY}
     */
    public CounterPolicy getCompletionPolicy() {
        return completionPolicy;
    }

    /**
     * Set the completion policy. Used by the editor and by tests; quest
     * files normally carry it through {@link #readData}.
     *
     * @param completionPolicy the policy to apply to this task
     */
    public void setCompletionPolicy(CounterPolicy completionPolicy) {
        this.completionPolicy = completionPolicy;
    }

    /**
     * Record server-validated intake against this task.
     *
     * <p>FTB Quests clamps the mirrored write at
     * {@link #getMaxProgress()}, so an intake that crosses the
     * threshold completes the task through the normal quest flow.
     * Rejected intake moves neither the personal counter nor the
     * mirror.</p>
     *
     * @param player the acting player whose personal counter advances;
     *               must belong to the team the data describes
     * @param data the team whose mirrored progress advances; when null
     *             (or when the player is null) the attempt is rejected
     *             and logged
     * @param amount strictly positive count to add
     * @param integration calling integration id, logged verbatim
     * @param reason caller's reason for this grant, logged verbatim
     * @return the outcome; never null
     */
    public final IntakeOutcome recordValidatedIntake(ServerPlayer player, TeamData data, long amount, String integration, String reason) {
        String loggedIntegration = integration == null || integration.isBlank() ? "unspecified" : integration;
        String loggedReason = reason == null || reason.isBlank() ? "unspecified" : reason;

        if (data == null || player == null) {
            return logOutcome(IntakeOutcome.REJECTED_NO_PROGRESS_HOLDER, loggedIntegration, loggedReason, amount, data, player);
        }
        if (getQuestFile().getSide() != Env.SERVER) {
            return logOutcome(IntakeOutcome.REJECTED_NOT_SERVER_SIDE, loggedIntegration, loggedReason, amount, data, player);
        }

        long current = data.getProgress(this);
        IntakeOutcome outcome = IntakeRules.evaluate(current, getMaxProgress(), amount);
        if (outcome != IntakeOutcome.ACCEPTED) {
            return logOutcome(outcome, loggedIntegration, loggedReason, amount, data, player);
        }

        ProgressAttachments.increment(player, getId(), amount);
        mirrorAggregate(data, player);
        return logOutcome(IntakeOutcome.ACCEPTED, loggedIntegration, loggedReason, amount, data, player);
    }

    /**
     * Route a state-derived evaluation into validated intake - the
     * polling entry point for tasks whose progress mirrors live game
     * state (job levels, dungeon floors).
     *
     * <p>Only a <em>positive delta</em> between the desired value and
     * this player's own counter reaches {@link #recordValidatedIntake}:
     * a converged player costs one counter read and no log line, and
     * an evaluation at or below the current counter (a lowered
     * requirement, a dropped level) is skipped rather than clawed
     * back - personal counters are high-water marks, matching the
     * intake-only write model. The reason string carries the desired
     * value, so accepted and rejected intake lines both explain what
     * the poll saw.</p>
     *
     * @param data the team whose mirrored progress may advance; null
     *             or an empty evaluation is skipped silently - a
     *             poll runs many times per minute and rejections are
     *             logged by the intake itself
     * @param player the player whose state was evaluated and whose
     *               personal counter advances on acceptance
     * @param desired the value the player's counter should reach for
     *                their evaluated state; empty when unevaluable
     * @param integration calling integration id, logged verbatim
     */
    protected final void pollState(TeamData data, ServerPlayer player, OptionalLong desired, String integration) {
        if (data == null || player == null || desired.isEmpty()) {
            return;
        }
        long wanted = desired.getAsLong();
        long delta = wanted - ProgressAttachments.readCount(player, getId());
        if (delta <= 0) {
            return;
        }
        recordValidatedIntake(player, data, delta, integration, "auto-submit poll; desired=" + wanted);
    }

    /**
     * Evaluate {@link #completionPolicy} over the team's members and
     * mirror the result into FTB Quests' visible progress.
     *
     * <p>Membership comes from FTB Teams (INT-001) - every member, not
     * just the online ones: an offline member's attachment is not
     * loaded, so they contribute 0, which keeps FR-010 sound (a team
     * can never complete while a member is unverified) at the cost of
     * a pessimistic bar until the next intake or that member's login
     * re-converges it. A team FTB Teams does not know, or one with no
     * members, mirrors nothing rather than publishing a misleading
     * value.</p>
     *
     * @param data the team whose progress to mirror
     * @param context any online team member - only used to reach the
     *                server's player list
     */
    private void mirrorAggregate(TeamData data, ServerPlayer context) {
        Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamByID(data.getTeamId());
        if (team.isEmpty()) {
            return;
        }
        Set<UUID> members = team.get().getMembers();
        if (members.isEmpty()) {
            return;
        }

        long[] counters = new long[members.size()];
        int index = 0;
        for (UUID memberId : members) {
            ServerPlayer member = context.getServer().getPlayerList().getPlayer(memberId);
            counters[index++] = member == null ? 0L : ProgressAttachments.readCount(member, getId());
        }

        OptionalLong aggregate = completionPolicy.aggregate(counters);
        aggregate.ifPresent(value -> data.setProgress(this, value));
    }

    /**
     * Emit the one log line per intake decision and hand the outcome
     * back so callers can branch on it.
     */
    private IntakeOutcome logOutcome(IntakeOutcome outcome, String integration, String reason, long amount, TeamData data, ServerPlayer player) {
        String format = "intake {} integration={} reason={} amount={} progress={}/{} quest={} task={} team={} player={}";
        Object[] arguments = {
            outcome.name(),
            integration,
            reason,
            amount,
            data == null ? 0L : data.getProgress(this),
            getMaxProgress(),
            getQuest().getId(),
            getId(),
            data == null ? "none" : data.getTeamId(),
            player == null ? "none" : player.getUUID(),
        };
        if (outcome == IntakeOutcome.ACCEPTED) {
            TensuraQuests.LOGGER.info(format, arguments);
        } else {
            TensuraQuests.LOGGER.warn(format, arguments);
        }
        return outcome;
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        completionPolicy = POLICY_NAMES.get(tag.getString("completion_policy"));
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putString("completion_policy", POLICY_NAMES.getName(completionPolicy));
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        completionPolicy = POLICY_NAMES.read(buffer);
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        POLICY_NAMES.write(buffer, completionPolicy);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        super.fillConfigGroup(group);
        group.addEnum("completion_policy", completionPolicy, value -> completionPolicy = value, POLICY_NAMES)
            .setNameKey(POLICY_NAME_KEY);
    }
}
