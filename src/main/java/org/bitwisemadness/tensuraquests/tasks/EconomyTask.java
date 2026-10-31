package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.framework.IntakeOutcome;
import org.bitwisemadness.tensuraquests.framework.TensuraTask;

/**
 * Base class for the three economy task types (FR-040..FR-044).
 *
 * <p>Unlike the polled job tasks, economy tasks are
 * <em>event-driven</em>: Lightman's Currency's completed-trade event
 * reaches the adapter, which routes a {@link TradeFact} to every
 * economy task in the file. Each task decides its own sides and
 * contributions; this base owns what they share - the target
 * threshold, the per-task team-trade policy (FR-044, default deny),
 * team resolution through FTB Teams (INT-001), online-side resolution
 * and the validated {@link #credit} step that lands every accepted
 * grant through {@link TensuraTask#recordValidatedIntake} with the
 * integration id and a reason (FR-012).</p>
 *
 * <p>Anti-exploit ground rules (SEC-004): intake exists ONLY on the
 * completed-trade event path - never inventory movement; trade-level
 * rejections (self-trade) run once in the adapter before routing;
 * this base additionally enforces the per-task team policy and skips
 * offline sides (a player's attachment only exists while they are
 * online - documented limitation, symmetric for both sides).</p>
 */
public abstract class EconomyTask extends TensuraTask {

    /** Integration id logged with every economy intake decision (FR-012). */
    public static final String INTEGRATION = "lightmanscurrency";

    /** Smallest allowed target: at zero the task would already be complete. */
    public static final long MIN_TARGET = 1L;
    /** Target for newly created tasks and saves predating the field. */
    public static final long DEFAULT_TARGET = 1L;
    /** Largest allowed target; intake past it is clamped by FTB Quests. */
    public static final long MAX_TARGET = Long.MAX_VALUE;

    /** Config field names, shared by SNBT, net sync and the editor group. */
    private static final String TARGET_KEY = "target";
    private static final String TEAM_TRADES_KEY = "allow_team_trades";

    private long target = DEFAULT_TARGET;
    private boolean allowTeamTrades = false;

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    protected EconomyTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public long getMaxProgress() {
        return target;
    }

    /**
     * @return the completion threshold (trades, coins or distinct
     *         partners, depending on the task type)
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
     * The per-task team-trade policy (FR-044): when false (default),
     * trades where buyer and seller share an FTB Teams team count for
     * nobody.
     *
     * @return whether same-team trades count for this task
     */
    public boolean getAllowTeamTrades() {
        return allowTeamTrades;
    }

    /**
     * @param value the new team-trade policy
     */
    public void setAllowTeamTrades(boolean value) {
        allowTeamTrades = value;
    }

    /**
     * Handle one completed trade. Called by the adapter's router for
     * every instance of this task type in the file, after trade-level
     * rejections.
     *
     * @param fact the completed trade
     * @param file the quest file the task lives in
     * @param server the server, to resolve online sides
     */
    public abstract void onTrade(TradeFact fact, BaseQuestFile file, MinecraftServer server);

    /**
     * The per-task team policy gate: resolves whether buyer and
     * seller share an FTB Teams team and asks
     * {@link EconomyRules#teamTradeAllowed}. A trade without a player
     * seller (ownerless trader) is never a team trade.
     *
     * @param fact the completed trade
     * @return true when this trade may count for this task
     */
    protected final boolean passesTeamPolicy(TradeFact fact) {
        boolean sameTeam = fact.sellerId()
            .map(seller -> sameTeam(fact.buyerId(), seller))
            .orElse(false);
        boolean allowed = EconomyRules.teamTradeAllowed(sameTeam, allowTeamTrades);
        if (!allowed) {
            // FR-012: a policy rejection is an intake decision and logs
            // like every other one - once per task per trade.
            TensuraQuests.LOGGER.warn("trade intake rejected reason=team_policy task={} buyer={} seller={} value={}",
                getId(), fact.buyerId(), fact.sellerId().orElse(null), fact.value());
        }
        return allowed;
    }

    /**
     * The online player on one side of the trade, empty when that side
     * has no player or is offline (their counter attachment exists
     * only while online - documented: offline sides are not credited).
     *
     * @param fact the completed trade
     * @param side whose perspective to resolve
     * @param server the server's player list
     * @return the online side player, empty when none
     */
    protected final Optional<ServerPlayer> sidePlayer(TradeFact fact, TradeFact.Side side, MinecraftServer server) {
        UUID id = side == TradeFact.Side.BUYER ? fact.buyerId() : fact.sellerId().orElse(null);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getPlayerList().getPlayer(id));
    }

    /**
     * The validated credit step: every accepted economy grant lands
     * here, through the same intake path as every other task type
     * (policy mirror, personal counter, FR-012 logging).
     *
     * @param file the quest file the task lives in
     * @param player the online side player being credited
     * @param amount strictly positive contribution
     * @param reason caller's reason, logged verbatim
     * @return the intake outcome; never null
     */
    protected final IntakeOutcome credit(BaseQuestFile file, ServerPlayer player, long amount, String reason) {
        if (amount <= 0) {
            return IntakeOutcome.REJECTED_INVALID_AMOUNT;
        }
        Optional<TeamData> data = file.getTeamData(player);
        return recordValidatedIntake(player, data.orElse(null), amount, INTEGRATION, reason);
    }

    private static boolean sameTeam(UUID buyerId, UUID sellerId) {
        TeamManager manager = FTBTeamsAPI.api().getManager();
        Optional<Team> buyerTeam = manager.getTeamForPlayerID(buyerId);
        Optional<Team> sellerTeam = manager.getTeamForPlayerID(sellerId);
        return buyerTeam.isPresent() && sellerTeam.isPresent()
            && buyerTeam.get().getId().equals(sellerTeam.get().getId());
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readData(tag, registries);
        if (!tag.contains(TARGET_KEY)) {
            // Additive field: saves written before it existed keep the default.
            target = DEFAULT_TARGET;
        } else {
            long stored = tag.getLong(TARGET_KEY);
            target = clampTarget(stored);
            if (target != stored) {
                TensuraQuests.LOGGER.warn("economy task {} stored target {} outside [{}, {}]; using {}",
                    getId(), stored, MIN_TARGET, MAX_TARGET, target);
            }
        }
        allowTeamTrades = tag.getBoolean(TEAM_TRADES_KEY);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        tag.putLong(TARGET_KEY, target);
        tag.putBoolean(TEAM_TRADES_KEY, allowTeamTrades);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        target = clampTarget(buffer.readVarLong());
        allowTeamTrades = buffer.readBoolean();
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarLong(target);
        buffer.writeBoolean(allowTeamTrades);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        // Client-only, like the upstream task overrides: the editor
        // path never runs on a dedicated server.
        super.fillConfigGroup(group);
        group.addLong(TARGET_KEY, target, value -> target = clampTarget(value),
            DEFAULT_TARGET, MIN_TARGET, MAX_TARGET);
        group.addBool(TEAM_TRADES_KEY, allowTeamTrades, value -> allowTeamTrades = value, false);
    }

    private static long clampTarget(long value) {
        if (value < MIN_TARGET) {
            return MIN_TARGET;
        }
        return Math.min(value, MAX_TARGET);
    }
}
