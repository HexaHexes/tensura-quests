package org.bitwisemadness.tensuraquests.framework;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import java.util.ArrayList;
import java.util.List;

/**
 * Event-dispatch candidate lookup over a quest file's object hierarchy.
 *
 * <p>Deliberately <em>not</em> {@code BaseQuestFile.collect}: that method
 * iterates the file's id map, which only fills during deserialization - a
 * programmatically built fixture graph collects nothing through it (proven by
 * the economy suite's first routing run, credited zero everywhere while
 * pre-dispatch rejections still logged). This walk uses the collection-based
 * children chain instead - groups, chapters, quests, tasks/rewards - which is
 * the same structure upstream's own task walkers use, correct for both the
 * loaded server file and fixtures, and bounded by the file's object count
 * with no world or entity scans (NFR-003).</p>
 */
public final class QuestObjects {

    private QuestObjects() {
    }

    /**
     * Every object of one type under the file, depth-first.
     *
     * @param file the quest file to walk (its children are the chapter groups)
     * @param type the concrete type to collect
     * @param <T> the type to collect
     * @return all matches in document order; empty when the file has none
     */
    public static <T> List<T> collect(BaseQuestFile file, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (QuestObjectBase root : file.getChildren()) {
            // getChildren() is declared on QuestObject; every node of the
            // group-chapter-quest-task chain is one.
            if (root instanceof QuestObject node) {
                collectInto(node, type, found);
            }
        }
        return found;
    }

    private static <T> void collectInto(QuestObject object, Class<T> type, List<T> found) {
        if (type.isInstance(object)) {
            found.add(type.cast(object));
        }
        for (QuestObjectBase child : object.getChildren()) {
            if (child instanceof QuestObject node) {
                collectInto(node, type, found);
            }
        }
    }
}
