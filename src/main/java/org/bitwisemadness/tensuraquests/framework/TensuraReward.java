package org.bitwisemadness.tensuraquests.framework;

import dev.architectury.utils.Env;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.bitwisemadness.tensuraquests.TensuraQuests;

/**
 * Base class for every custom reward type this mod registers.
 *
 * <p>FTB Quests marks a reward claimed <em>before</em> invoking
 * {@code claim}, and {@code claim} itself returns void: a failed
 * execution would otherwise burn the claim and tell nobody. This base
 * class restores the loud-failure discipline the architecture demands
 * (FR-006, FR-007): subclasses implement {@link #executeClaim(ServerPlayer)}
 * and return a {@link ClaimResult}; the base class logs every decision
 * with the reward, quest, team and player ids, and a failed claim is
 * un-marked through {@link TeamData#resetReward} so the player can try
 * again once the cause clears - a failure never burns a reward.</p>
 *
 * <p>Claims execute server-side only (NFR-002): a non-server quest file
 * refuses intake before the subclass ever runs. Every claim is personal -
 * FTB Quests keys claims per player through {@code QuestKey} regardless
 * of team scope (SEC-002) - and this base class additionally forces the
 * per-player team scope: the upstream {@code team} Tristate has no
 * setter, but it is read from and written to the {@code team_reward} NBT
 * key, so load and save both pin it to false, and the editor's team
 * control is locked so quest authors see it is not theirs to change. A
 * freshly constructed instance holds the upstream DEFAULT until its
 * first save or load normalises it - the private field cannot be set
 * earlier - but the claim log and the persisted state are always
 * per-player.</p>
 */
public abstract class TensuraReward extends Reward {

    /** Upstream NBT key of the private team-scope Tristate. */
    private static final String TEAM_SCOPE_KEY = "team_reward";

    /**
     * Lang key upstream sets on the team-scope editor control, read from
     * the pinned FTB Quests build's {@code Reward.fillConfigGroup}.
     */
    private static final String TEAM_SCOPE_NAME_KEY = "ftbquests.reward.team_reward";

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this reward belongs to
     */
    protected TensuraReward(long id, Quest quest) {
        super(id, quest);
    }

    /**
     * Execute the reward's effect for the claiming player.
     *
     * <p>Implementations run server-side, must not throw, and must not
     * mutate anything before they know the effect can complete: a
     * returned failure means nothing was granted and the base class
     * releases the claim again.</p>
     *
     * @param player the player claiming the reward
     * @return {@link ClaimResult#granted()} when the effect executed,
     *         otherwise a failure carrying the reason
     */
    protected abstract ClaimResult executeClaim(ServerPlayer player);

    /**
     * @return the integration id logged with every claim decision;
     *         domain rewards override this to name their gameplay system
     */
    protected String integration() {
        return TensuraQuests.MOD_ID;
    }

    @Override
    public final void claim(ServerPlayer player, boolean check) {
        if (getQuestFile().getSide() != Env.SERVER) {
            TensuraQuests.LOGGER.warn(
                "claim REJECTED_NOT_SERVER_SIDE integration={} reward={} quest={} player={}",
                integration(), getId(), getQuest().getId(), player.getUUID());
            return;
        }

        ClaimResult result = executeClaim(player);
        Optional<TeamData> teamData = getQuestFile().getTeamData(player);
        String team = teamData.isPresent() ? teamData.get().getTeamId().toString() : "unknown";

        if (result.isGranted()) {
            TensuraQuests.LOGGER.info(
                "claim GRANTED integration={} reward={} quest={} team={} player={}",
                integration(), getId(), getQuest().getId(), team, player.getUUID());
            return;
        }

        TensuraQuests.LOGGER.warn(
            "claim FAILED integration={} reason={} reward={} quest={} team={} player={}",
            integration(), result.reason(), getId(), getQuest().getId(), team, player.getUUID());

        // The caller marked this claim before invoking us; release every
        // mark of this player on this reward so a failed execution never
        // burns the claim. resetReward is a no-op (false) where absent.
        boolean unmarked = false;
        for (TeamData data : getQuestFile().getAllTeamData()) {
            if (data.resetReward(player.getUUID(), this)) {
                unmarked = true;
            }
        }
        if (!unmarked) {
            TensuraQuests.LOGGER.warn(
                "claim FAILED but no claim mark found to release reward={} player={}",
                getId(), player.getUUID());
        }

        // No silent nothing: the player is told the reward did not grant.
        player.sendSystemMessage(Component.translatable("tensura_quests.reward.claim_failed", result.reason()));
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider registries) {
        // Force per-player scope before the upstream reader runs: the
        // private team Tristate is loaded from this key and has no setter.
        // The caller's tag is copied so the load path stays side-effect
        // free for whoever owns the NBT.
        CompoundTag forced = tag.copy();
        forced.putBoolean(TEAM_SCOPE_KEY, false);
        super.readData(forced, registries);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeData(tag, registries);
        // Persist the forced scope explicitly: a fresh instance still
        // holds the upstream DEFAULT, which super would omit.
        tag.putBoolean(TEAM_SCOPE_KEY, false);
    }

    /**
     * Fills the reward's editor group: upstream controls plus our locked
     * team-scope control.
     *
     * <p>Client-only by contract: FTB Quests marks every
     * {@code fillConfigGroup} in its hierarchy - {@code Reward},
     * {@code QuestObject}, {@code QuestObjectBase} and each concrete
     * override - {@code @OnlyIn(Dist.CLIENT)} because only the GUI ever
     * builds editor groups, and a dedicated server strips them all. This
     * override must carry the annotation too, or it would outlive its
     * stripped {@code super} and fail with {@code NoSuchMethodError} on
     * the first server-side call. The lock is factored into
     * {@link #lockTeamScopeControl} because that half is plain
     * config-group logic and stays testable on any dist.</p>
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup group) {
        super.fillConfigGroup(group);
        lockTeamScopeControl(group);
    }

    /**
     * Lock the upstream team-scope editor control so quest authors see
     * the per-player scope is ours to decide, not theirs. Matched by the
     * lang key upstream sets on the control ({@code TEAM_SCOPE_NAME_KEY}),
     * never by position in the group.
     *
     * <p>Deliberately outside {@link #fillConfigGroup(ConfigGroup)}:
     * that method is client-only and does not exist on a dedicated
     * server, while this lock is pure {@link ConfigGroup} work - which
     * is what lets the GameTest seed a group and verify it server-side.</p>
     *
     * @param group the editor group to lock the team-scope control in
     */
    public void lockTeamScopeControl(ConfigGroup group) {
        for (ConfigValue<?> value : group.getValues()) {
            if (TEAM_SCOPE_NAME_KEY.equals(value.getNameKey())) {
                value.setCanEdit(false);
            }
        }
    }
}
