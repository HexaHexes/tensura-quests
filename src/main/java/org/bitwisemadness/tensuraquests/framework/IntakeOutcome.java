package org.bitwisemadness.tensuraquests.framework;

/**
 * Result of one server-validated progress intake attempt against a
 * custom task (FR-004).
 *
 * <p>Accepted or rejected, every attempt is logged with this outcome,
 * the calling integration, the caller's reason and the quest, task and
 * team ids (FR-012, NFR-004). The enum is pure Java so the decision
 * rules behind it can be unit-tested without a Minecraft classpath.</p>
 */
public enum IntakeOutcome {

    /** Progress was written to the team's task progress. */
    ACCEPTED,
    /** Rejected: the quest file is not server-side, so progress may not move (NFR-002). */
    REJECTED_NOT_SERVER_SIDE,
    /** Rejected: the amount is zero or negative. */
    REJECTED_INVALID_AMOUNT,
    /** Rejected: progress already reached the task's maximum. */
    REJECTED_ALREADY_COMPLETE,
    /** Rejected: no team progress holder was supplied. */
    REJECTED_NO_PROGRESS_HOLDER
}
