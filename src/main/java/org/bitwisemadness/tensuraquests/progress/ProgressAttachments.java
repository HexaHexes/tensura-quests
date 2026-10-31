package org.bitwisemadness.tensuraquests.progress;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.bitwisemadness.tensuraquests.TensuraQuests;

/**
 * The per-player counter attachment and its access points (ADR-0002,
 * FR-008).
 *
 * <p>Registration follows the pattern bytecode-verified in
 * Sympathetic Jobs 1.0.13: a {@link DeferredRegister} over
 * {@link NeoForgeRegistries#ATTACHMENT_TYPES} wired to the mod event
 * bus. The attachment serializes through {@link PersonalCounterStore}'s
 * versioned codec and is marked {@code copyOnDeath} so progress
 * survives respawns as it survives restarts and disconnects
 * (NFR-001).</p>
 *
 * <p>Reads distinguish the two access shapes the policy needs:
 * {@link #readCount} materialises nothing (a member who never
 * progressed reads as 0 without gaining a phantom attachment), while
 * the intake path uses {@link #increment}, which materialises the
 * store for the acting player only.</p>
 */
public final class ProgressAttachments {

    /** The single attachment registry this mod registers into. */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TensuraQuests.MOD_ID);

    /** One versioned {@link PersonalCounterStore} per player. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PersonalCounterStore>> PERSONAL_COUNTERS =
        ATTACHMENT_TYPES.register("personal_counters", () -> AttachmentType.builder(PersonalCounterStore::empty)
            .serialize(PersonalCounterStore.CODEC)
            .copyOnDeath()
            .build());

    private ProgressAttachments() {
    }

    /**
     * Register the attachment types. Called once from the mod
     * constructor on the mod event bus.
     *
     * @param modEventBus the bus the DeferredRegister listens on
     */
    public static void init(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    /**
     * A member's counter for one task without materialising an
     * attachment: members who never progressed read as 0 and stay
     * absent from the save.
     *
     * @param player an online member - offline members read as 0 by
     *               the caller, because no platform API loads their state
     * @param taskId the quest-file unique task id
     * @return the stored counter, 0 when never touched
     */
    public static long readCount(ServerPlayer player, long taskId) {
        Optional<PersonalCounterStore> store = player.getExistingData(PERSONAL_COUNTERS.get());
        return store.map(s -> s.get(taskId)).orElse(0L);
    }

    /**
     * Add to a player's counter, materialising their store on first
     * intake. Server-side validated intake only.
     *
     * @param player the acting player
     * @param taskId the quest-file unique task id
     * @param amount strictly positive increment
     */
    public static void increment(ServerPlayer player, long taskId, long amount) {
        player.getData(PERSONAL_COUNTERS.get()).increment(taskId, amount);
    }
}
