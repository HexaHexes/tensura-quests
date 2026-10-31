package org.bitwisemadness.tensuraquests.compat.sympatheticjobs;

/**
 * The Sympathetic Jobs version gate (COMP-003, D3, U-04).
 *
 * <p>The job task family compiles against Sympathetic Jobs
 * {@value #PINNED_VERSION} internals; an update that renames them must
 * produce a loud, actionable failure rather than a silent no-op or a
 * lazy linkage error at first poll. {@link #evaluate} is deliberately
 * pure string logic with no Minecraft, FML or Sympathetic Jobs
 * references: the JUnit suite exercises every outcome without any of
 * those on the classpath (issue #8), while the caller supplies the
 * installed version from FML ({@link SjPresence}).</p>
 */
public final class SjVersionGate {

    /** The Sympathetic Jobs version this build compiles and tests against. */
    public static final String PINNED_VERSION = "1.0.13";

    /** Outcome of comparing the installed version against the pin. */
    public enum Result {
        /** Installed version equals the pin - the family may register. */
        OK,
        /** Sympathetic Jobs is not installed - the family stays disabled (INT-004). */
        ABSENT,
        /** Installed version differs from the pin - loud failure (COMP-003). */
        MISMATCH,
    }

    /**
     * The verdict plus the installed version it was derived from.
     *
     * @param result the comparison outcome
     * @param installedVersion the trimmed installed version, {@code null}
     *                         when Sympathetic Jobs is absent
     */
    public record Verdict(Result result, String installedVersion) {

        /**
         * Whether the job task family may register: only a matching
         * pin qualifies.
         *
         * @return true when {@link Result#OK}
         */
        public boolean usable() {
            return result == Result.OK;
        }

        /**
         * A complete, human-readable log line body for this verdict -
         * the same text is used at registration time so the startup
         * log and the unit test cannot drift apart.
         *
         * @return the description; never null
         */
        public String describe() {
            return switch (result) {
                case OK -> "Sympathetic Jobs " + installedVersion + " matches the pinned version "
                    + PINNED_VERSION;
                case ABSENT -> "Sympathetic Jobs is not installed; the job task family is disabled "
                    + "(install Sympathetic Jobs " + PINNED_VERSION + " to enable it)";
                case MISMATCH -> "Sympathetic Jobs " + installedVersion + " is installed but "
                    + PINNED_VERSION + " is pinned; the job task family is disabled until the "
                    + "versions match (COMP-003)";
            };
        }
    }

    private SjVersionGate() {
    }

    /**
     * Compare an installed version against the pin.
     *
     * @param pinned the required version; normally
     *               {@link #PINNED_VERSION}
     * @param installed the installed version, {@code null} or blank
     *                  when Sympathetic Jobs is absent
     * @return the verdict; never null
     */
    public static Verdict evaluate(String pinned, String installed) {
        if (installed == null || installed.isBlank()) {
            return new Verdict(Result.ABSENT, null);
        }
        String trimmed = installed.trim();
        return new Verdict(trimmed.equals(pinned) ? Result.OK : Result.MISMATCH, trimmed);
    }
}
