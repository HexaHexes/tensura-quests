package org.bitwisemadness.tensuraquests.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Mock players for GameTests, plus the one network step the vanilla
 * mock skips. Shared, package-private helper (see {@code TestQuestFile}
 * and {@code CapturingAppender}) - any suite that triggers modded
 * payload sends must create its players here rather than calling
 * {@code GameTestHelper.makeMockServerPlayerInLevel} directly.
 *
 * <p><b>Why placement is replicated here</b> (bytecode-identical to
 * the vanilla helper's, down to the spectator/creative overrides):
 * mocks skip the network configuration phase, and some mods react to
 * <em>login</em> with modded sends - Lightman's Currency syncs its
 * master coin list at {@code EventPriority.HIGHEST}, inside
 * {@code placeNewPlayer}, before any post-placement fixup could run.
 * A listener of ours can never outrank it (same priority bucket;
 * LC registers during mod init, this class later - order loses).
 * Teaching the {@link Connection} <em>before</em>
 * {@code placeNewPlayer} is the only ordering that always wins: every
 * login-time and later send finds an ad-hoc channel already present
 * ({@link #acceptAllModdedPayloads}). This class lives in the gametest
 * source set, which joins the mod only for dev runs - production jars
 * contain none of it, and production clients negotiate properly.</p>
 */
final class MockPlayers {

    private MockPlayers() {
    }

    /**
     * A placed mock player whose connection accepts modded payloads
     * from the moment it exists.
     *
     * @param helper the gametest helper to build against
     * @return the freshly placed mock player
     */
    static ServerPlayer create(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-mock-player");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ClientInformation information = cookie.clientInformation();
        // Mirrors GameTestHelper$2 (vanilla's mock): a ServerPlayer
        // that can never read as spectator or creative.
        ServerPlayer mock = new ServerPlayer(server, level, profile, information) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // The EmbeddedChannel wrap is what gives the Connection its
        // channel (attributes live there), so teach second - both
        // still before placement, which is the ordering that matters.
        new EmbeddedChannel(connection);
        acceptAllModdedPayloads(connection);
        server.getPlayerList().placeNewPlayer(connection, mock, cookie);
        return mock;
    }

    /**
     * Teach a connection to accept every registered modded payload.
     *
     * <p>A real client negotiates channels during the configuration
     * phase; game-protocol connections created here have none, so
     * NeoForge's {@code NetworkRegistry.checkPacket} rejects every
     * modded payload ("Payload ... may not be sent to the client!") -
     * FTB Quests' progress sync on the first mirror write, Lightman's
     * Currency's coin-list sync at login. Registering every known
     * payload id as an ad-hoc channel - the same store a real client's
     * {@code c:register} writes to - declares the fake client to be
     * listening to whatever the server sends.</p>
     *
     * <p>Harness-only: production clients negotiate properly.
     * {@code NetworkRegistry} is {@code @ApiStatus.Internal} and its
     * registry field is private, hence the reflection - the dev runtime
     * loads NeoForge from the classpath, where {@code setAccessible} is
     * permitted.</p>
     *
     * @param connection the mock's connection, before placement
     */
    static void acceptAllModdedPayloads(Connection connection) {
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
            NetworkRegistry.onMinecraftRegister(connection, payloadIds);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("gametest mock could not learn the modded payload channels", e);
        }
    }
}
