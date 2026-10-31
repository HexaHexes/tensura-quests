package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.framework.IntakeOutcome;
import org.bitwisemadness.tensuraquests.progress.ProgressAttachments;

/**
 * Trading Partner: counts the distinct counterparties the player has
 * completed trades with, up to a configured count (FR-042).
 *
 * <p>Distinctness lives in the shared counter store's per-task
 * partner set: a side's counterparty is recorded only when its
 * credit is <em>accepted</em>, so repeated trades with the same
 * partner never inflate the count (and a rejected credit - team
 * policy, completed task, teamless player - leaves the partner
 * unrecorded so a later eligible trade still counts once). One trade
 * adds at most one partner to each side's set, and self-trades cannot
 * reach here at all (the adapter's trade-level rejection runs before
 * routing), so both sides of one trade never double-count toward the
 * same requirement.</p>
 */
public final class TradingPartnerTask extends EconomyTask {

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public TradingPartnerTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireTradingPartnerTaskType();
    }

    @Override
    public void onTrade(TradeFact fact, BaseQuestFile file, MinecraftServer server) {
        if (!passesTeamPolicy(fact)) {
            return;
        }
        creditSide(fact, file, server, TradeFact.Side.BUYER);
        creditSide(fact, file, server, TradeFact.Side.SELLER);
    }

    private void creditSide(TradeFact fact, BaseQuestFile file, MinecraftServer server, TradeFact.Side side) {
        Optional<UUID> counterparty = fact.counterpartyOf(side);
        if (counterparty.isEmpty()) {
            return; // ownerless trader: the buyer has no counterparty to record
        }
        Optional<ServerPlayer> player = sidePlayer(fact, side, server);
        if (player.isEmpty()) {
            return; // offline side: their partner set only exists while online
        }
        ServerPlayer credited = player.get();
        if (ProgressAttachments.hasPartner(credited, getId(), counterparty.get())) {
            return; // already counted (FR-042 distinctness): silent no-op
        }
        IntakeOutcome outcome = credit(file, credited, 1L, "new trading partner");
        if (outcome == IntakeOutcome.ACCEPTED) {
            ProgressAttachments.addPartner(credited, getId(), counterparty.get());
        }
        // Rejected outcomes stay unrecorded, so the partner can still
        // count on a later eligible trade (see class javadoc).
    }
}
