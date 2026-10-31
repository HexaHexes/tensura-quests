package org.bitwisemadness.tensuraquests.compat.lightmanscurrency;

import net.neoforged.fml.ModList;

/**
 * Lightman's Currency presence probe for the INT-004 family gate.
 *
 * <p>Like its Sympathetic Jobs counterpart ({@code SjPresence}), this
 * class touches only FML - never a {@code io.github.lightman314}
 * class - so the disabled-family outcome cannot turn into a linkage
 * error: the adapter (which loads LC classes) is only reached after
 * this probe has ruled the mod present.</p>
 */
public final class LcPresence {

    /** FML mod id of Lightman's Currency (from its neoforge.mods.toml). */
    public static final String MOD_ID = "lightmanscurrency";

    private LcPresence() {
    }

    /**
     * Whether Lightman's Currency is installed. Presence-only by
     * design (the #14 spec gates on ModList, not a version pin): an
     * API-incompatible future LC fails loudly at listener
     * registration via linkage instead of silently no-opping - see
     * the implementation notes on #14.
     *
     * @return true when the mod is loaded
     */
    public static boolean isInstalled() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
