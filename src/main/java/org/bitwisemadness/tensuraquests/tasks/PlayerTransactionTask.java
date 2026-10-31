package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;

/**
 * Player Transaction: counts completed trades the player takes part
 * in - purchases as the customer and sales as the trader owner -
 * each side of one trade credited to its own player (FR-040,
 * FR-042's separate-attribution rule).
 *
 * <p>Only completed-trade events reach here: cancelled trades never
 * produce one, so they never show progress. Both sides run through
 * the shared team policy, trade-level rejections and online-side
 * resolution in {@link EconomyTask}; an ownerless trader credits only
 * its customer.</p>
 */
public final class PlayerTransactionTask extends EconomyTask {

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public PlayerTransactionTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requirePlayerTransactionTaskType();
    }

    @Override
    public void onTrade(TradeFact fact, BaseQuestFile file, MinecraftServer server) {
        if (!passesTeamPolicy(fact)) {
            return;
        }
        creditSide(fact, file, server, TradeFact.Side.BUYER, "completed purchase");
        creditSide(fact, file, server, TradeFact.Side.SELLER, "completed sale");
    }

    private void creditSide(TradeFact fact, BaseQuestFile file, MinecraftServer server,
            TradeFact.Side side, String reason) {
        Optional<ServerPlayer> player = sidePlayer(fact, side, server);
        player.ifPresent(p -> credit(file, p, 1L, reason));
    }
}
