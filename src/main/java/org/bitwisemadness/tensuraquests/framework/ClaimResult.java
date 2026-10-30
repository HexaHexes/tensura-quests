package org.bitwisemadness.tensuraquests.framework;

/**
 * Result of one server-side reward claim attempt (FR-006, FR-007).
 *
 * <p>FTB Quests' {@code Reward.claim} returns void and the caller marks
 * the reward claimed before invoking it, so the loud-failure contract
 * lives in our own layer: the claim executor returns this result, the
 * base class logs it with the ids involved, and a failed result un-marks
 * the claim so the reward is never burned by a failure. The class is
 * pure Java so the value contract can be reasoned about without a
 * Minecraft classpath.</p>
 */
public final class ClaimResult {

    private static final ClaimResult GRANTED = new ClaimResult(true, "");

    private final boolean granted;
    private final String reason;

    private ClaimResult(boolean granted, String reason) {
        this.granted = granted;
        this.reason = reason;
    }

    /**
     * @return the shared success result
     */
    public static ClaimResult granted() {
        return GRANTED;
    }

    /**
     * @param reason why execution refused; blank or null becomes
     *               {@code "unspecified"} so a failure is never logged
     *               without a cause
     * @return the failure result carrying the reason
     */
    public static ClaimResult failed(String reason) {
        return new ClaimResult(false, reason == null || reason.isBlank() ? "unspecified" : reason);
    }

    /**
     * @return true when the reward's effect executed
     */
    public boolean isGranted() {
        return granted;
    }

    /**
     * @return the failure reason; empty for a granted result
     */
    public String reason() {
        return reason;
    }
}
