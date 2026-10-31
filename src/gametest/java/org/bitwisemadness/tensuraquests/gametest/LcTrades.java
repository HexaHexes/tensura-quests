package org.bitwisemadness.tensuraquests.gametest;

import io.github.lightman314.lightmanscurrency.api.events.TradeEvent;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.api.ownership.builtin.PlayerOwner;
import io.github.lightman314.lightmanscurrency.api.traders.TradeContext;
import io.github.lightman314.lightmanscurrency.common.traders.commands.CommandTrader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Construction harness for Lightman's Currency trade events - the
 * gametest source set's documented exemption from the adapter
 * boundary (seed/verify state is the harness's job). Everything here
 * builds <em>real</em> LC objects through public constructors,
 * bytecode-verified under issue #6: a {@code CommandTrader} (its
 * constructor reads only the block at the position - air is fine -
 * and registers nothing global), the trader's own first trade slot,
 * a built {@link TradeContext} around a real player reference, and a
 * coin value on LC's own main chain ({@code CoinAPI.MAIN_CHAIN}).
 * Any construction that drifts off LC's pinned 2.3.0.6 surface fails
 * loudly here first.
 */
final class LcTrades {

    private LcTrades() {
    }

    /**
     * A fresh unowned trader at a position unique per seed.
     *
     * @param level the gametest level
     * @param seed distinct placement per test
     * @return the trader
     */
    static CommandTrader trader(ServerLevel level, long seed) {
        int x = 1000 + (int) (seed % 900);
        int z = -1000 - (int) ((seed / 900) % 900);
        return new CommandTrader(level, new BlockPos(x, 64, z));
    }

    /**
     * Put a player in charge of the trader - the seller side of every
     * trade at it.
     *
     * @param trader the trader to own
     * @param owner the owning player
     */
    static void own(CommandTrader trader, ServerPlayer owner) {
        trader.getOwner().SetOwner(PlayerOwner.of(PlayerReference.of(owner)));
    }

    /**
     * A completed-purchase context around a real player.
     *
     * @param trader the trader sold at
     * @param buyer the purchasing player
     * @return the built context
     */
    static TradeContext context(CommandTrader trader, ServerPlayer buyer) {
        return TradeContext.create(trader, PlayerReference.of(buyer)).build();
    }

    /**
     * A paid value on Lightman's Currency's main coin chain,
     * verified - a chain rename in a future LC would otherwise
     * silently zero the Sales Milestone tests. The chain id comes
     * from LC's own {@code CoinAPI.MAIN_CHAIN} constant rather than
     * a guessed name.
     *
     * @param amount core coin units
     * @return the value
     */
    static MoneyValue price(long amount) {
        // LC's fromNumber returns MoneyValue.empty() for non-positive
        // amounts by design - the free-trade case uses that same
        // canonical zero; the assertion below pins the core value for
        // every amount either way.
        MoneyValue value = amount <= 0
            ? MoneyValue.empty()
            : CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount);
        if (value.getCoreValue() != amount) {
            throw new IllegalStateException("coin chain '" + CoinAPI.MAIN_CHAIN + "' produced core=" + value.getCoreValue()
                + " (empty=" + value.isEmpty() + ") for amount " + amount);
        }
        return value;
    }

    /**
     * The completed-trade event exactly as LC's production path
     * builds it (trade, customer context, paid price, no taxes).
     *
     * @param trader the trader sold at
     * @param buyer the purchasing player
     * @param amount core coin units paid
     * @return the unposted event
     */
    static TradeEvent.PostTradeEvent purchase(CommandTrader trader, ServerPlayer buyer, long amount) {
        return new TradeEvent.PostTradeEvent(
            trader.getTrade(0), context(trader, buyer), price(amount), MoneyValue.free());
    }

    /**
     * A completed sale with no player customer (storage mode - the
     * U-09 case: commands, automation, terminal-side executions).
     *
     * @param trader the trader sold at
     * @param amount core coin units paid
     * @return the unposted event
     */
    static TradeEvent.PostTradeEvent playerlessPurchase(CommandTrader trader, long amount) {
        return new TradeEvent.PostTradeEvent(
            trader.getTrade(0), TradeContext.createStorageMode(trader), price(amount), MoneyValue.free());
    }

    /**
     * Post an event to the live bus - the wiring probe. Routes into
     * the run's REAL quest file (which holds no economy tasks), so
     * fixture assertions must stay untouched.
     *
     * @param event the event to post
     */
    static void post(net.neoforged.bus.api.Event event) {
        NeoForge.EVENT_BUS.post(event);
    }
}
