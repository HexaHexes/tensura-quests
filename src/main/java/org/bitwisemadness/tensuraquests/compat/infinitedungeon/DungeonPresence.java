package org.bitwisemadness.tensuraquests.compat.infinitedungeon;

import net.neoforged.fml.ModList;

/**
 * Infinite Dungeon suite presence probe for the INT-004 family gate.
 *
 * <p>Like its Sympathetic Jobs and Lightman's Currency counterparts, this
 * class touches only FML - never an {@code org.bitwisemadness.infinitedungeon*}
 * class - so the disabled-family outcome cannot become a linkage error: the
 * intake (which loads the event classes) is only reached after this probe
 * rules the surface present. The probe targets the API mod id: the published
 * {@code infinite_dungeon_api} ships with the core and carries every event
 * type the intake subscribes to, so its presence <em>is</em> the event
 * surface's presence (and it is what the dev runtime puts on the classpath).</p>
 */
public final class DungeonPresence {

    /** FML mod id of the Infinite Dungeon API (its gradle mod_id). */
    public static final String MOD_ID = "infinite_dungeon_api";

    private DungeonPresence() {
    }

    /**
     * Whether the Infinite Dungeon event surface is installed.
     *
     * @return true when the mod is loaded
     */
    public static boolean isInstalled() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
