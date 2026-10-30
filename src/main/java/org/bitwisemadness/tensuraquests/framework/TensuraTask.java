package org.bitwisemadness.tensuraquests.framework;

import dev.architectury.utils.Env;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import org.bitwisemadness.tensuraquests.TensuraQuests;

/**
 * Base class for every custom task type this mod registers.
 *
 * <p>Progress enters through {@link #recordValidatedIntake(TeamData,
 * long, String, String)} only: intake validates that the quest file is
 * server-side (NFR-002), applies the pure {@link IntakeRules}
 * decision, mirrors the amount into FTB Quests' per-team
 * {@link TeamData} progress (ADR-0002 keeps per-player accounting as a
 * later layer), and logs every grant and rejection with the calling
 * integration, the caller's reason and the quest, task and team ids
 * (FR-012, NFR-004).</p>
 *
 * <p>Client sync carries configuration only; no client-supplied value
 * ever reaches the progress write.</p>
 */
public abstract class TensuraTask extends Task {

    /**
     * @param id the quest-file unique object id
     * @param quest the quest this task belongs to
     */
    protected TensuraTask(long id, Quest quest) {
        super(id, quest);
    }

    /**
     * Record server-validated intake against this task.
     *
     * <p>FTB Quests clamps the write at {@link #getMaxProgress()}, so
     * an intake that crosses the threshold completes the task through
     * the normal quest flow.</p>
     *
     * @param data the team whose progress advances; when null the
     *             attempt is rejected and logged
     * @param amount strictly positive count to add
     * @param integration calling integration id, logged verbatim
     * @param reason caller's reason for this grant, logged verbatim
     * @return the outcome; never null
     */
    public final IntakeOutcome recordValidatedIntake(TeamData data, long amount, String integration, String reason) {
        String loggedIntegration = integration == null || integration.isBlank() ? "unspecified" : integration;
        String loggedReason = reason == null || reason.isBlank() ? "unspecified" : reason;

        if (data == null) {
            return logOutcome(IntakeOutcome.REJECTED_NO_PROGRESS_HOLDER, loggedIntegration, loggedReason, amount, null);
        }
        if (getQuestFile().getSide() != Env.SERVER) {
            return logOutcome(IntakeOutcome.REJECTED_NOT_SERVER_SIDE, loggedIntegration, loggedReason, amount, data);
        }

        long current = data.getProgress(this);
        IntakeOutcome outcome = IntakeRules.evaluate(current, getMaxProgress(), amount);
        if (outcome != IntakeOutcome.ACCEPTED) {
            return logOutcome(outcome, loggedIntegration, loggedReason, amount, data);
        }

        data.addProgress(this, amount);
        return logOutcome(IntakeOutcome.ACCEPTED, loggedIntegration, loggedReason, amount, data);
    }

    /**
     * Emit the one log line per intake decision and hand the outcome
     * back so callers can branch on it.
     */
    private IntakeOutcome logOutcome(IntakeOutcome outcome, String integration, String reason, long amount, TeamData data) {
        String format = "intake {} integration={} reason={} amount={} progress={}/{} quest={} task={} team={}";
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
        };
        if (outcome == IntakeOutcome.ACCEPTED) {
            TensuraQuests.LOGGER.info(format, arguments);
        } else {
            TensuraQuests.LOGGER.warn(format, arguments);
        }
        return outcome;
    }
}
