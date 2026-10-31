package org.bitwisemadness.tensuraquests.compat.lightmanscurrency;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import io.github.lightman314.lightmanscurrency.api.events.TradeEvent;
import io.github.lightman314.lightmanscurrency.api.misc.player.OwnerData;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.api.ownership.Owner;
import io.github.lightman314.lightmanscurrency.api.traders.TradeContext;
import io.github.lightman314.lightmanscurrency.api.traders.TraderData;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.framework.QuestObjects;
import org.bitwisemadness.tensuraquests.tasks.EconomyRules;
import org.bitwisemadness.tensuraquests.tasks.PlayerTransactionTask;
import org.bitwisemadness.tensuraquests.tasks.SalesMilestoneTask;
import org.bitwisemadness.tensuraquests.tasks.TradeFact;
import org.bitwisemadness.tensuraquests.tasks.TradingPartnerTask;

/**
 * The single read-path adapter onto Lightman's Currency (D3's
 * discipline applied to the economy domain, #14).
 *
 * <p>This package - with {@link LcPresence} - is the only code
 * allowed to reference {@code io.github.lightman314.*} in production
 * (enforced by {@code AdapterIsolationTest}): extraction happens
 * here, once, and everything downstream sees only the MC-free
 * {@link TradeFact}. LC 2.3.0.6 is closed source (ARR), so the whole
 * surface used here was bytecode-verified under issue #6.</p>
 *
 * <p><b>The intake boundary (SEC-004).</b> Exactly one subscription:
 * {@link TradeEvent.PostTradeEvent} on the NeoForge bus - the event
 * LC constructs in {@code TraderData.runPostTradeEvent} <em>after</em>
 * payment, rule hooks and trade persistence, i.e. only for completed
 * trades. Cancelled trades never produce it; inventory or money
 * movement never reaches this class. Playerless completions
 * (commands, storage mode, automation - U-09's answer) carry no
 * customer and are dropped at extraction, before any task is looked
 * up.</p>
 *
 * <p><b>Routing.</b> Each accepted fact walks the file's economy
 * tasks ({@code collect(Class)} - bounded by the file's task count,
 * no world or entity scans, NFR-003) and each task applies its own
 * sides, team policy and contributions. The file comes from
 * {@link ServerQuestFile#getInstance()}; {@link #route} is public so
 * the GameTest harness can drive real facts into an isolated fixture
 * file instead of the run's real one.</p>
 */
public final class LightmansCurrencyAdapter {

    private LightmansCurrencyAdapter() {
    }

    /**
     * Subscribe the completed-trade listener. Called only after the
     * INT-004 presence probe has ruled Lightman's Currency installed,
     * so LC classes load for the first time here, never before.
     */
    public static void registerListener() {
        NeoForge.EVENT_BUS.addListener(
            (Consumer<TradeEvent.PostTradeEvent>) LightmansCurrencyAdapter::onPostTrade);
    }

    /**
     * The bus entry: server-authoritative customer guard (the buyer
     * is executing the trade right now, so an online server player
     * always exists - anything else is not attributable) plus the
     * U-09 playerless skip, then extract and route.
     */
    private static void onPostTrade(TradeEvent.PostTradeEvent event) {
        Optional<TradeFact> fact = factFrom(event);
        if (fact.isEmpty()) {
            return; // playerless completion (U-09): nothing to attribute
        }
        PlayerReference reference = event.getPlayerReference();
        Player buyer = reference == null ? null : reference.getPlayer();
        if (!(buyer instanceof ServerPlayer serverPlayer)) {
            return; // not server-side attributable: SEC keeps client data out
        }
        ServerQuestFile.getInstance()
            .ifPresent(file -> route(fact.get(), file, serverPlayer.getServer()));
    }

    /**
     * Extract the neutral fact from a completed-trade event. Public
     * and side-effect-free so GameTests verify the extraction against
     * real (LC-constructed) events, including the playerless case.
     *
     * @param event the completed trade LC posted
     * @return the fact, empty when there is no player customer to
     *         attribute (U-09's documented rule)
     */
    public static Optional<TradeFact> factFrom(TradeEvent.PostTradeEvent event) {
        if (!event.hasPlayerReference()) {
            return Optional.empty();
        }
        PlayerReference buyer = event.getPlayerReference();
        if (buyer == null) {
            return Optional.empty();
        }
        long value = 0L;
        if (event.getPricePaid() != null) {
            value = Math.max(0L, event.getPricePaid().getCoreValue());
        }
        return Optional.of(new TradeFact(buyer.id, sellerId(event), value));
    }

    /**
     * Route one accepted fact to every economy task in the file -
     * trade-level rejections (FR-043) run here, once per event rather
     * than once per task. Public: the GameTest harness's intake entry
     * point.
     *
     * <p>Candidate lookup walks the file's children hierarchy
     * (groups - chapters - quests - tasks), not
     * {@code BaseQuestFile.collect}: collect iterates the id map that
     * only fills during file <em>deserialization</em>, so a
     * programmatically built fixture graph would silently collect
     * nothing - the hierarchy walk is collection-based and correct
     * for both the loaded server file and fixtures (it is the same
     * structure upstream's own task walkers use), bounded by the
     * file's object count with no world or entity scans (NFR-003).</p>
     *
     * @param fact the completed trade
     * @param file the quest file whose tasks should see it (the real
     *             server file in production, a fixture file in tests)
     * @param server the server, to resolve online sides
     */
    public static void route(TradeFact fact, BaseQuestFile file, MinecraftServer server) {
        Optional<EconomyRules.Rejection> rejection = EconomyRules.reject(fact);
        if (rejection.isPresent()) {
            TensuraQuests.LOGGER.warn("trade intake rejected reason={} buyer={} seller={} value={}",
                rejection.get(), fact.buyerId(), fact.sellerId().orElse(null), fact.value());
            return;
        }
        for (PlayerTransactionTask task : QuestObjects.collect(file, PlayerTransactionTask.class)) {
            task.onTrade(fact, file, server);
        }
        for (SalesMilestoneTask task : QuestObjects.collect(file, SalesMilestoneTask.class)) {
            task.onTrade(fact, file, server);
        }
        for (TradingPartnerTask task : QuestObjects.collect(file, TradingPartnerTask.class)) {
            task.onTrade(fact, file, server);
        }
    }

    /**
     * The trader owner's player id: an ownerless, community or
     * team-owned trader yields empty (only its customer side can be
     * credited - team-owned traders credit no seller, documented on
     * #14).
     */
    private static Optional<UUID> sellerId(TradeEvent.PostTradeEvent event) {
        TradeContext context = event.getContext();
        if (context == null || !context.hasTrader()) {
            return Optional.empty();
        }
        // getTrader() is non-null past hasTrader() (upstream contract,
        // SpotBugs-verified) and so is asPlayerReference()'s return;
        // the ownerless/team-owned shape surfaces as PlayerReference.NULL.
        TraderData trader = context.getTrader();
        OwnerData owner = trader.getOwner();
        if (!owner.hasOwner()) {
            return Optional.empty();
        }
        Owner current = owner.getValidOwner();
        if (current == null || current.isNull()) {
            return Optional.empty();
        }
        PlayerReference reference = current.asPlayerReference();
        if (reference == PlayerReference.NULL) {
            return Optional.empty();
        }
        return Optional.of(reference.id);
    }
}
