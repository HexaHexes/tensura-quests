package org.bitwisemadness.tensuraquests.gametest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Log4j appender collecting this mod's log lines for the FR-012 style
 * assertions, shared by the framework and reward GameTest suites.
 *
 * <p>Attached to the mod's logger only for the duration of one test and
 * removed in {@code finally}, so no suite sees another suite's lines.
 * Note for future runs: while an appender is attached dynamically,
 * lines reach the capture but may not reach the run's file appender
 * (log4j reconfiguration timing); production logging is unaffected
 * because nothing attaches appenders outside tests.</p>
 */
final class CapturingAppender extends AbstractAppender {

    private final List<String> messages = new CopyOnWriteArrayList<>();

    CapturingAppender() {
        super("tensura_quests_framework_capture", null, PatternLayout.createDefaultLayout(),
            false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        messages.add(event.getMessage().getFormattedMessage());
    }

    /**
     * @param fragments every fragment that must appear in one log line
     * @return true when a captured line contains all fragments
     */
    boolean hasMessage(String... fragments) {
        return countMessages(fragments) > 0;
    }

    /**
     * @param fragments every fragment that must appear in a log line
     * @return how many captured lines contain all fragments
     */
    int countMessages(String... fragments) {
        int count = 0;
        for (String message : messages) {
            boolean all = true;
            for (String fragment : fragments) {
                if (!message.contains(fragment)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                count++;
            }
        }
        return count;
    }
}
