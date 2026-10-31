package org.bitwisemadness.tensuraquests.compat.infinitedungeon;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.bitwisemadness.infinitedungeonapi.event.FloorEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.event.LayerCompletedEvent;
import org.bitwisemadness.infinitedungeonapi.event.LayerEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.event.MinibossSlainEvent;
import org.bitwisemadness.tensuraquests.framework.QuestObjects;
import org.bitwisemadness.tensuraquests.tasks.DungeonMilestoneTask;
import org.bitwisemadness.tensuraquests.tasks.FloorCompletionTask;
import org.bitwisemadness.tensuraquests.tasks.LayerCompletionTask;
import org.bitwisemadness.tensuraquests.tasks.MinibossDefeatTask;

/**
 * The single contact point between this mod and the Infinite Dungeon event
 * surface (the discipline applied to the suite's published API).
 *
 * <p>Four subscriptions - floor entry, layer entry, layer completion and
 * miniboss slain - each routed the same way: resolve the file
 * ({@link ServerQuestFile#getInstance()}, production only), resolve the
 * <em>online</em> player the event attributes (offline sides have no counter
 * attachment to write - documented, symmetric with the other adapters), then
 * hand every candidate task of the type through {@link DungeonTask}'s shared
 * gate. Attributed-or-nothing: an absent completer/killer (ADR-0003 unknown)
 * routes nothing at all - presence is never converted into credit. Events are
 * server-fired notifications with no ordering promise (api contract), so each
 * intake is self-sufficient; the public {@code route} overloads are the
 * GameTest harness's entry into an isolated fixture file.</p>
 */
public final class DungeonIntake {

    private DungeonIntake() {
    }

    /**
     * Subscribe all four listeners. Called only after the INT-004 presence
     * probe has ruled the event surface present, so the event classes load
     * for the first time here.
     */
    public static void registerListeners() {
        NeoForge.EVENT_BUS.addListener((Consumer<FloorEnteredEvent>) DungeonIntake::onFloorEntered);
        NeoForge.EVENT_BUS.addListener((Consumer<LayerEnteredEvent>) DungeonIntake::onLayerEntered);
        NeoForge.EVENT_BUS.addListener((Consumer<LayerCompletedEvent>) DungeonIntake::onLayerCompleted);
        NeoForge.EVENT_BUS.addListener((Consumer<MinibossSlainEvent>) DungeonIntake::onMinibossSlain);
    }

    private static void onFloorEntered(FloorEnteredEvent event) {
        ServerQuestFile.getInstance().ifPresent(file -> route(event, file, file.server));
    }

    private static void onLayerEntered(LayerEnteredEvent event) {
        ServerQuestFile.getInstance().ifPresent(file -> route(event, file, file.server));
    }

    private static void onLayerCompleted(LayerCompletedEvent event) {
        ServerQuestFile.getInstance().ifPresent(file -> route(event, file, file.server));
    }

    private static void onMinibossSlain(MinibossSlainEvent event) {
        ServerQuestFile.getInstance().ifPresent(file -> route(event, file, file.server));
    }

    /**
     * Floor intake: the entered player credits every FloorCompletionTask
     * whose dimension and layer/floor match. Public: the harness routes
     * fixture events here.
     *
     * @param event the floor-entry notification
     * @param file the quest file whose tasks should see it
     * @param server the server, to resolve the attributed player
     */
    public static void route(FloorEnteredEvent event, BaseQuestFile file, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(event.playerId());
        if (player == null) {
            return; // offline side: no attachment to write (documented)
        }
        TeamData data = file.getTeamData(player).orElse(null);
        String dimensionId = event.dimension().location().toString();
        String reason = "floor entered layer=" + event.layer() + " floor=" + event.floor();
        for (FloorCompletionTask task : QuestObjects.collect(file, FloorCompletionTask.class)) {
            task.intake(player, data, dimensionId, task.matchesContext(event.layer(), event.floor()), reason);
        }
    }

    /**
     * Milestone intake: the entering player credits every
     * DungeonMilestoneTask whose dimension matches and whose threshold the
     * reached layer satisfies.
     *
     * @param event the layer-entry notification
     * @param file the quest file whose tasks should see it
     * @param server the server, to resolve the attributed player
     */
    public static void route(LayerEnteredEvent event, BaseQuestFile file, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(event.playerId());
        if (player == null) {
            return; // offline side: no attachment to write (documented)
        }
        TeamData data = file.getTeamData(player).orElse(null);
        String dimensionId = event.dimension().location().toString();
        String reason = "entered layer " + event.layer();
        for (DungeonMilestoneTask task : QuestObjects.collect(file, DungeonMilestoneTask.class)) {
            task.intake(player, data, dimensionId, task.matchesContext(event.layer()), reason);
        }
    }

    /**
     * Layer-completion intake: routes only when the completion carries an
     * attributed completer - an unknown completer credits nobody
     * (ADR-0003; presence must not become credit).
     *
     * @param event the layer-completion notification
     * @param file the quest file whose tasks should see it
     * @param server the server, to resolve the attributed completer
     */
    public static void route(LayerCompletedEvent event, BaseQuestFile file, MinecraftServer server) {
        if (event.completingPlayer().isEmpty()) {
            return; // environmental or legacy completion: nobody to credit
        }
        ServerPlayer player = server.getPlayerList().getPlayer(event.completingPlayer().get());
        if (player == null) {
            return; // attributed but offline: no attachment to write
        }
        TeamData data = file.getTeamData(player).orElse(null);
        String dimensionId = event.dimension().location().toString();
        String reason = "layer " + event.layer() + " completed (attributed)";
        for (LayerCompletionTask task : QuestObjects.collect(file, LayerCompletionTask.class)) {
            task.intake(player, data, dimensionId, true, reason);
        }
    }

    /**
     * Miniboss intake: routes only when the kill carries an attributed
     * killer (ADR-0003; an unknown killer credits nobody), then filters
     * per task on the configured layer.
     *
     * @param event the miniboss-slain notification
     * @param file the quest file whose tasks should see it
     * @param server the server, to resolve the attributed killer
     */
    public static void route(MinibossSlainEvent event, BaseQuestFile file, MinecraftServer server) {
        if (event.killingPlayer().isEmpty()) {
            return; // environmental kill: nobody to credit
        }
        ServerPlayer player = server.getPlayerList().getPlayer(event.killingPlayer().get());
        if (player == null) {
            return; // attributed but offline: no attachment to write
        }
        TeamData data = file.getTeamData(player).orElse(null);
        String dimensionId = event.dimension().location().toString();
        String reason = "miniboss slain at layer " + event.layer();
        for (MinibossDefeatTask task : QuestObjects.collect(file, MinibossDefeatTask.class)) {
            task.intake(player, data, dimensionId, task.matchesContext(event.layer()), reason);
        }
    }
}
