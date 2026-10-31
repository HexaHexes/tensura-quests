package org.bitwisemadness.tensuraquests;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.progress.ProgressAttachments;
import org.slf4j.Logger;

/**
 * Tensura Quests entry point.
 *
 * <p>Registers custom FTB Quests task and reward types that connect quest
 * progression to the authoritative gameplay systems of the Tensura RPG pack:
 * Sympathetic Jobs, the Infinite Dungeon suite, Lightman's Currency and the
 * future Faction Capitals system. FTB Quests remains the questbook and
 * completion layer; this mod never owns gameplay state it does not
 * author.</p>
 *
 * <p>The contracts this mod implements live in {@code tensura_quests_api};
 * every integration is isolated behind an adapter and gated on its mod being
 * present, so a missing optional mod disables only its own task and reward
 * families.</p>
 */
@Mod(TensuraQuests.MOD_ID)
public final class TensuraQuests {

    public static final String MOD_ID = "tensura_quests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TensuraQuests(IEventBus modBus) {
        // Registration is a class-load side effect of FTB Quests'
        // TaskTypes, so the bridge runs here: the built-in types
        // register when that class first loads, then ours join the
        // same static registry (see FtbQuestsBridge, INT-003).
        FtbQuestsBridge.registerTypes();
        // The per-player counter store is a NeoForge attachment
        // (ADR-0002), registered on the mod bus like Sympathetic
        // Jobs registers its own state attachment.
        ProgressAttachments.init(modBus);
        LOGGER.info("Tensura Quests initialized");
    }
}
