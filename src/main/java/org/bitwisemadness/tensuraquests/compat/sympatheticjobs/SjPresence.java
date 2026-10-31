package org.bitwisemadness.tensuraquests.compat.sympatheticjobs;

import java.util.Optional;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

/**
 * Reads Sympathetic Jobs' installed version without loading a single
 * Sympathetic Jobs class.
 *
 * <p>The version gate runs from the mod constructor, on every
 * machine - including ones where Sympathetic Jobs is absent or the
 * wrong version - so the lookup must stay free of
 * {@code dev.fatsan.sympatheticjobs} references: touching those
 * classes before the gate has ruled would turn "disabled family" into
 * a linkage error. Only FML's own {@link ModList} is consulted; the
 * adapter that actually reads job state ({@link SympatheticJobs})
 * loads after an OK verdict, via the registered task types.</p>
 */
public final class SjPresence {

    /** FML mod id of Sympathetic Jobs (from its neoforge.mods.toml). */
    public static final String MOD_ID = "sympatheticjobs";

    private SjPresence() {
    }

    /**
     * The installed Sympathetic Jobs version.
     *
     * @return the version string from the mod container, empty when
     *         Sympathetic Jobs is not installed
     */
    public static Optional<String> installedVersion() {
        return ModList.get()
            .getModContainerById(MOD_ID)
            .map(ModContainer::getModInfo)
            .map(info -> String.valueOf(info.getVersion()));
    }
}
