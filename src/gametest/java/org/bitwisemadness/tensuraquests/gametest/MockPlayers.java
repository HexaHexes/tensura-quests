package org.bitwisemadness.tensuraquests.gametest;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Mock players for GameTests, plus the one network step the vanilla
 * mock skips. Shared, package-private helper (see {@code TestQuestFile}
 * and {@code CapturingAppender}) - any suite that triggers modded
 * payload sends must create its players here rather than calling
 * {@code GameTestHelper.makeMockServerPlayerInLevel} directly.
 */
final class MockPlayers {

    private MockPlayers() {
    }

    /**
     * A placed mock player whose connection accepts modded payloads.
     *
     * @param helper the gametest helper to create the mock with
     * @return the freshly placed mock player
     */
    static ServerPlayer create(GameTestHelper helper) {
        ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        acceptAllModdedPayloads(mock);
        return mock;
    }

    /**
     * Teach a mock's connection to accept every registered modded
     * payload.
     *
     * <p>A real client negotiates channels during the configuration
     * phase; {@code makeMockServerPlayerInLevel} places the player
     * without one, so NeoForge's {@code NetworkRegistry.checkPacket}
     * rejects FTB Quests' progress-sync payload ("Payload ... may not
     * be sent to the client!") the moment a policy mirror writes to
     * {@code TeamData}. Registering every known payload id as an ad-hoc
     * channel - the same store a real client's {@code c:register}
     * writes to - declares the fake client to be listening to whatever
     * the server sends.</p>
     *
     * <p>Harness-only: production clients negotiate properly.
     * {@code NetworkRegistry} is {@code @ApiStatus.Internal} and its
     * registry field is private, hence the reflection - the dev runtime
     * loads NeoForge from the classpath, where {@code setAccessible} is
     * permitted.</p>
     *
     * @param player the freshly created mock player
     */
    private static void acceptAllModdedPayloads(ServerPlayer player) {
        try {
            Field registrations = NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
            registrations.setAccessible(true);
            Set<ResourceLocation> payloadIds = new HashSet<>();
            for (Object protocolChannels : ((Map<?, ?>) registrations.get(null)).values()) {
                if (protocolChannels instanceof Map<?, ?> channelsByPayload) {
                    for (Object id : channelsByPayload.keySet()) {
                        if (id instanceof ResourceLocation payloadId) {
                            payloadIds.add(payloadId);
                        }
                    }
                }
            }
            if (payloadIds.isEmpty()) {
                throw new IllegalStateException("no modded payloads discovered for the gametest mock");
            }
            NetworkRegistry.onMinecraftRegister(player.connection.getConnection(), payloadIds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("gametest mock could not learn the modded payload channels", e);
        }
    }
}
