package org.bitwisemadness.tensuraquests.framework;

/**
 * Pure decision rules for server-validated progress intake (FR-004).
 *
 * <p>Extracted from the task base class so the JUnit suite can exercise
 * every branch without loading Minecraft or FTB Quests classes. The
 * server-side check itself lives in {@link TensuraTask}; this class
 * only sees numbers.</p>
 */
public final class IntakeRules {

    private IntakeRules() {
    }

    /**
     * Decide whether {@code amount} may advance a task from
     * {@code currentProgress} towards {@code maxProgress}.
     *
     * <p>Checks run in order of specificity: an unusable amount is
     * reported before an exhausted task, so the rejection log names the
     * first real reason. A target at or below zero cannot be satisfied
     * further and reports as already complete.</p>
     *
     * @param currentProgress the team's current progress for the task
     * @param maxProgress the task's completion threshold
     * @param amount the requested intake, must be strictly positive
     * @return {@link IntakeOutcome#ACCEPTED} when intake may proceed
     */
    public static IntakeOutcome evaluate(long currentProgress, long maxProgress, long amount) {
        if (amount <= 0) {
            return IntakeOutcome.REJECTED_INVALID_AMOUNT;
        }
        if (maxProgress <= 0 || currentProgress >= maxProgress) {
            return IntakeOutcome.REJECTED_ALREADY_COMPLETE;
        }
        return IntakeOutcome.ACCEPTED;
    }
}
