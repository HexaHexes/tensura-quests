package org.bitwisemadness.tensuraquests.tasks;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;

/**
 * Layer Completion: completes when a layer's guardian falls for the first
 * time in a configured dimension, credited to the attributed completer
 * (FR-031).
 *
 * <p>The event's {@code completingPlayer} is the attributable killer of the
 * unlocking fall (ADR-0003); an absent completer - environmental fall or
 * legacy payload - credits <em>nobody</em>: presence is not causation, and a
 * fabricated credit would be worse than none. The adapter resolves that
 * player online and hands the one-unit credit here, where the shared
 * dimension gate and the ADR-0002 policy mirror do the rest. No extra layer
 * config: "clear a layer" counts any layer the configured dimensions
 * complete in (the milestone task is the one that pins a depth).</p>
 */
public final class LayerCompletionTask extends DungeonTask {

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    public LayerCompletionTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsBridge.requireLayerCompletionTaskType();
    }
}
