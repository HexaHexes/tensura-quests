package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;

/**
 * Sales Milestone: accumulates the coin value of completed sales for
 * the trader's owner up to a configurable threshold (FR-041).
 *
 * <p>Seller side only - a customer buying does not "sell" anything.
 * The contribution is the trade's paid core value (freed/free trades
 * contribute zero and simply move nothing), clamped non-negative by
 * {@link EconomyRules#salesContribution}. Team policy, trade
 * rejections and online-side resolution are shared with the other
 * economy tasks in {@link EconomyTask}.</p>
 */
public final class SalesMilestoneTask extends EconomyTask {

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public SalesMilestoneTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireSalesMilestoneTaskType();
    }

    @Override
    public void onTrade(TradeFact fact, BaseQuestFile file, MinecraftServer server) {
        if (!passesTeamPolicy(fact)) {
            return;
        }
        long contribution = EconomyRules.salesContribution(fact.value());
        if (contribution <= 0) {
            return; // free trades are real but add no coins: nothing to credit
        }
        Optional<ServerPlayer> seller = sidePlayer(fact, TradeFact.Side.SELLER, server);
        seller.ifPresent(p -> credit(file, p, contribution, "sale value " + contribution));
    }
}
