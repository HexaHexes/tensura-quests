package org.bitwisemadness.tensuraquests.gametest;

import dev.architectury.utils.Env;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import io.github.lightman314.lightmanscurrency.api.events.TradeEvent;
import io.github.lightman314.lightmanscurrency.common.traders.commands.CommandTrader;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.compat.lightmanscurrency.LcPresence;
import org.bitwisemadness.tensuraquests.compat.lightmanscurrency.LightmansCurrencyAdapter;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.progress.ProgressAttachments;
import org.bitwisemadness.tensuraquests.tasks.PlayerTransactionTask;
import org.bitwisemadness.tensuraquests.tasks.SalesMilestoneTask;
import org.bitwisemadness.tensuraquests.tasks.TradeFact;
import org.bitwisemadness.tensuraquests.tasks.TradingPartnerTask;

/**
 * GameTests for the economy task family: registration behind the
 * INT-004 presence gate, FR-040 (Player Transaction, both sides
 * credited separately), FR-041 (Sales Milestone value accumulation),
 * FR-042 (Trading Partner distinctness), FR-043 (self-trade
 * rejection, logged), FR-044 (per-task team policy, both branches,
 * logged), the completed-player-trade-only boundary (SEC-004 event
 * discrimination + U-09's playerless rule), and config round-trips.
 *
 * <p><strong>Lightman's Currency contact (the adapter boundary).</strong>
 * This gametest source set imports {@code io.github.lightman314}
 * classes directly - deliberately: constructing real LC events is the
 * harness's job ({@link LcTrades}), and {@code AdapterIsolationTest}
 * documents the exemption by scanning src/main only.</p>
 *
 * <p><strong>Routing seam.</strong> The production path is bus ->
 * {@code factFrom} -> {@link LightmansCurrencyAdapter#route} into the
 * run's real quest file, which holds no economy tasks; tests extract
 * with {@code factFrom} (real events, real extraction) and route into
 * the isolated fixture file instead, plus one bus probe per suite
 * proving the live wiring throws nothing. Cancellation is covered by
 * construction: only {@code PostTradeEvent} has an extraction entry -
 * Pre/Cost events posted to the bus must leave fixture state
 * untouched (the discrimination assertions in
 * {@code only_completed_player_trades_credit}).</p>
 */
@GameTestHolder(TensuraQuests.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightmansCurrencyGameTests {

    private LightmansCurrencyGameTests() {
    }

    /**
     * Registration + gate premise: all three types under our
     * namespace, instances resolve their registered type, and the
     * presence probe that allowed registration reports LC installed
     * (the gametest runtime pins 2.3.0.6 via build.gradle).
     */
    @GameTest(template = "empty")
    public static void economy_task_types_registered(GameTestHelper helper) {
        helper.assertTrue(LcPresence.isInstalled(),
            "the gametest runtime must ship Lightman's Currency for this suite to mean anything");
        ResourceIds[] ids = {
            new ResourceIds(FtbQuestsBridge.PLAYER_TRANSACTION_TASK_ID),
            new ResourceIds(FtbQuestsBridge.SALES_MILESTONE_TASK_ID),
            new ResourceIds(FtbQuestsBridge.TRADING_PARTNER_TASK_ID),
        };
        for (ResourceIds id : ids) {
            Optional<TaskType> found = FtbQuestsBridge.findTaskType(id.id);
            helper.assertTrue(found.isPresent(), "economy task type missing from the FTB Quests registry: " + id.id);
            helper.assertTrue(id.id.equals(found.get().getTypeId()),
                "registered type id is " + found.get().getTypeId() + ", expected " + id.id);
        }

        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        PlayerTransactionTask task = addTransaction(fixture, 6901L, 1L);
        Optional<TaskType> registered = FtbQuestsBridge.findTaskType(FtbQuestsBridge.PLAYER_TRANSACTION_TASK_ID);
        helper.assertTrue(task.getType() == registered.orElseThrow(),
            "getType() must return the registered instance");
        helper.succeed();
    }

    /**
     * FR-040 + FR-042's separate attribution: two completed purchases
     * count for the customer AND the trader owner, each side into its
     * own team's progress and its own personal counter. Includes the
     * bus wiring probe (posting routes only into the real file).
     */
    @GameTest(template = "empty")
    public static void player_transaction_counts_both_sides(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        PlayerTransactionTask task = addTransaction(fixture, 6911L, 2L);
        ServerPlayer outsider = MockPlayers.create(helper);
        TeamData outsiderTeam = fixture.ensureOwnTeam(outsider);
        CommandTrader trader = LcTrades.trader(helper.getLevel(), 6911L);
        LcTrades.own(trader, outsider);

        // Bus wiring probe: routes into the REAL file (no economy
        // tasks in it), so fixture state must not move and nothing
        // may throw.
        LcTrades.post(LcTrades.purchase(trader, fixture.actor, 10L));
        helper.assertTrue(fixture.team.getProgress(task) == 0L,
            "the bus probe must not touch fixture state, got " + fixture.team.getProgress(task));

        for (int trade = 0; trade < 2; trade++) {
            TradeFact fact = factOf(LcTrades.purchase(trader, fixture.actor, 10L));
            helper.assertTrue(fact.buyerId().equals(fixture.actor.getUUID())
                    && fact.sellerId().orElseThrow().equals(outsider.getUUID())
                    && fact.value() == 10L,
                "extraction lost the trade shape: " + fact);
            LightmansCurrencyAdapter.route(fact, fixture.file, helper.getLevel().getServer());
        }

        helper.assertTrue(fixture.team.getProgress(task) == 2L,
            "both purchases must count for the customer, got " + fixture.team.getProgress(task));
        helper.assertTrue(fixture.team.isCompleted(task), "two of two trades must complete the task");
        helper.assertTrue(outsiderTeam.getProgress(task) == 2L,
            "the seller must be credited separately from the buyer (FR-042), got "
                + outsiderTeam.getProgress(task));
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 2L
                && ProgressAttachments.readCount(outsider, task.getId()) == 2L,
            "each side must hold its own personal counter");
        helper.succeed();
    }

    /**
     * FR-041: Sales Milestone accumulates the paid value for the
     * trader's owner only - the customer earns nothing on this task
     * type - and free trades add zero rather than subtracting or
     * counting as one.
     */
    @GameTest(template = "empty")
    public static void sales_milestone_accumulates_value(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        SalesMilestoneTask task = addSales(fixture, 6912L, 100L);
        ServerPlayer outsider = MockPlayers.create(helper);
        TeamData outsiderTeam = fixture.ensureOwnTeam(outsider);
        CommandTrader trader = LcTrades.trader(helper.getLevel(), 6912L);
        LcTrades.own(trader, outsider);

        LightmansCurrencyAdapter.route(factOf(LcTrades.purchase(trader, fixture.actor, 40L)),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(outsiderTeam.getProgress(task) == 40L,
            "the paid value must accumulate for the seller, got " + outsiderTeam.getProgress(task));
        helper.assertTrue(fixture.team.getProgress(task) == 0L,
            "the buyer must not earn sales value on this task, got " + fixture.team.getProgress(task));

        LightmansCurrencyAdapter.route(factOf(LcTrades.purchase(trader, fixture.actor, 0L)),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(outsiderTeam.getProgress(task) == 40L,
            "a free trade must add nothing (not even one), got " + outsiderTeam.getProgress(task));

        LightmansCurrencyAdapter.route(factOf(LcTrades.purchase(trader, fixture.actor, 60L)),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(outsiderTeam.getProgress(task) == 100L,
            "value must accumulate to the threshold, got " + outsiderTeam.getProgress(task));
        helper.assertTrue(outsiderTeam.isCompleted(task), "the threshold must complete the task");
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 0L,
            "the buyer must hold no sales counter");
        helper.succeed();
    }

    /**
     * FR-042: distinct counterparties only - repeating a trade with
     * the same partner counts once per side, a new partner counts
     * once, and both sides earn each other independently.
     */
    @GameTest(template = "empty")
    public static void trading_partner_counts_distinct_counterparties(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        TradingPartnerTask task = addPartner(fixture, 6913L, 2L);
        ServerPlayer partnerB = MockPlayers.create(helper);
        ServerPlayer partnerC = MockPlayers.create(helper);
        TeamData teamB = fixture.ensureOwnTeam(partnerB);
        TeamData teamC = fixture.ensureOwnTeam(partnerC);
        CommandTrader traderB = LcTrades.trader(helper.getLevel(), 6913L);
        CommandTrader traderC = LcTrades.trader(helper.getLevel(), 6940L);
        LcTrades.own(traderB, partnerB);
        LcTrades.own(traderC, partnerC);

        // First trade with B: both sides earn one partner.
        LightmansCurrencyAdapter.route(factOf(LcTrades.purchase(traderB, fixture.actor, 5L)),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 1L,
            "the customer must count one distinct partner, got "
                + ProgressAttachments.readCount(fixture.actor, task.getId()));
        helper.assertTrue(teamB.getProgress(task) == 1L,
            "the seller must count the customer as a partner, got " + teamB.getProgress(task));

        // Repeating the same partner counts once (double-count guard).
        LightmansCurrencyAdapter.route(factOf(LcTrades.purchase(traderB, fixture.actor, 5L)),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 1L,
            "a repeated partner must not count twice, got "
                + ProgressAttachments.readCount(fixture.actor, task.getId()));
        helper.assertTrue(teamB.getProgress(task) == 1L,
            "the seller's partner count must stay distinct too, got " + teamB.getProgress(task));
        helper.assertFalse(fixture.team.isCompleted(task), "one partner must not complete a two-partner task");

        // A new partner counts and completes.
        LightmansCurrencyAdapter.route(factOf(LcTrades.purchase(traderC, fixture.actor, 5L)),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 2L,
            "the new partner must count, got " + ProgressAttachments.readCount(fixture.actor, task.getId()));
        helper.assertTrue(fixture.team.isCompleted(task), "two distinct partners must complete the task");
        helper.assertTrue(teamC.getProgress(task) == 1L,
            "the second seller must earn the customer as a partner too");
        helper.succeed();
    }

    /**
     * FR-043: a player trading with their own trader increments
     * nothing anywhere, and the rejection logs with its reason
     * (FR-012) before any task runs.
     */
    @GameTest(template = "empty")
    public static void self_trades_never_count(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        PlayerTransactionTask task = addTransaction(fixture, 6914L, 1L);
        CommandTrader trader = LcTrades.trader(helper.getLevel(), 6914L);
        LcTrades.own(trader, fixture.actor);

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            TradeFact fact = factOf(LcTrades.purchase(trader, fixture.actor, 50L));
            LightmansCurrencyAdapter.route(fact, fixture.file, helper.getLevel().getServer());

            helper.assertTrue(fixture.team.getProgress(task) == 0L,
                "a self-trade must not progress anything, got " + fixture.team.getProgress(task));
            helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 0L,
                "a self-trade must not move the personal counter");
            helper.assertTrue(capture.hasMessage("trade intake rejected", "reason=SELF_TRADE",
                    "buyer=" + fixture.actor.getUUID()),
                "the self-trade rejection must log its reason and ids");
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
        helper.succeed();
    }

    /**
     * FR-044: same-team trades count for nobody under the default
     * policy (logged with reason), and count once the task's explicit
     * policy allows them.
     */
    @GameTest(template = "empty")
    public static void team_member_trades_follow_task_policy(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        PlayerTransactionTask task = addTransaction(fixture, 6915L, 1L);
        ServerPlayer teammate = MockPlayers.create(helper);
        fixture.addMember(teammate); // same team as the fixture actor
        CommandTrader trader = LcTrades.trader(helper.getLevel(), 6915L);
        LcTrades.own(trader, teammate);

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            TradeFact fact = factOf(LcTrades.purchase(trader, fixture.actor, 5L));

            LightmansCurrencyAdapter.route(fact, fixture.file, helper.getLevel().getServer());
            helper.assertTrue(fixture.team.getProgress(task) == 0L,
                "a same-team trade must count for nobody by default, got " + fixture.team.getProgress(task));
            helper.assertTrue(capture.hasMessage("trade intake rejected", "reason=team_policy",
                    "task=6915"),
                "the policy rejection must log its reason and task id");

            task.setAllowTeamTrades(true);
            LightmansCurrencyAdapter.route(fact, fixture.file, helper.getLevel().getServer());
            helper.assertTrue(fixture.team.getProgress(task) == 1L,
                "the explicit policy must let the trade count, got " + fixture.team.getProgress(task));
            helper.assertTrue(fixture.team.isCompleted(task), "the allowed trade must complete the task");
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
        helper.succeed();
    }

    /**
     * SEC-004 + U-09 + the cancellation claim: only completed trades
     * with a player customer can credit anything. A playerless
     * completion extracts to nothing; Pre/Cost events posted to the
     * live bus leave fixture state untouched (the listener subscribes
     * to {@code PostTradeEvent} only); a real completed player trade
     * in the same setup does credit - the contrast that makes the
     * discrimination assertions meaningful.
     */
    @GameTest(template = "empty")
    public static void only_completed_player_trades_credit(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        PlayerTransactionTask task = addTransaction(fixture, 6916L, 1L);
        ServerPlayer outsider = MockPlayers.create(helper);
        fixture.ensureOwnTeam(outsider);
        CommandTrader trader = LcTrades.trader(helper.getLevel(), 6916L);
        LcTrades.own(trader, outsider);

        // U-09: playerless completions (terminal-side, command or
        // storage-mode executions) extract to nothing at all.
        helper.assertTrue(LightmansCurrencyAdapter.factFrom(
                LcTrades.playerlessPurchase(trader, 50L)).isEmpty(),
            "a completed sale without a player customer must extract to nothing (U-09)");

        // Non-completed trade events: posted to the live bus, they
        // must leave fixture state untouched (our only subscription
        // is PostTradeEvent).
        LcTrades.post(new TradeEvent.PreTradeEvent(trader.getTrade(0), LcTrades.context(trader, fixture.actor)));
        LcTrades.post(new TradeEvent.TradeCostEvent(trader.getTrade(0), LcTrades.context(trader, fixture.actor)));
        helper.assertTrue(fixture.team.getProgress(task) == 0L,
            "pre/cost events must not credit anything, got " + fixture.team.getProgress(task));

        // The contrast: one real completed player trade does credit.
        LightmansCurrencyAdapter.route(factOf(LcTrades.purchase(trader, fixture.actor, 5L)),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(task) == 1L,
            "a completed player trade must credit, got " + fixture.team.getProgress(task));
        helper.assertTrue(fixture.team.isCompleted(task), "the completed trade must complete the task");
        helper.succeed();
    }

    /**
     * Config codecs: SNBT and net round-trips plus additive defaults
     * for saves predating the fields, for every economy task type's
     * shared configuration (target + team policy).
     */
    @GameTest(template = "empty")
    public static void economy_task_config_round_trips(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        RegistryAccess wire = helper.getLevel().registryAccess();
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);

        // SNBT round-trip + additive defaults on Player Transaction.
        PlayerTransactionTask transactionSource = addTransaction(fixture, 6917L, 42L);
        transactionSource.setAllowTeamTrades(true);
        CompoundTag transactionTag = new CompoundTag();
        transactionSource.writeData(transactionTag, registries);
        PlayerTransactionTask transactionCopy = addTransaction(fixture, 6918L, 1L);
        transactionCopy.readData(transactionTag, registries);
        helper.assertTrue(transactionCopy.getTarget() == 42L,
            "SNBT round-trip lost the target: " + transactionCopy.getTarget());
        helper.assertTrue(transactionCopy.getAllowTeamTrades(),
            "SNBT round-trip lost the team policy");
        PlayerTransactionTask transactionLegacy = addTransaction(fixture, 6919L, 9L);
        transactionLegacy.readData(new CompoundTag(), registries);
        helper.assertTrue(transactionLegacy.getTarget() == PlayerTransactionTask.DEFAULT_TARGET
                && !transactionLegacy.getAllowTeamTrades(),
            "a save without the fields must fall back to the defaults, got "
                + transactionLegacy.getTarget() + "/" + transactionLegacy.getAllowTeamTrades());

        // Net round-trip on Sales Milestone.
        SalesMilestoneTask salesSource = addSales(fixture, 6920L, 777L);
        salesSource.setAllowTeamTrades(true);
        io.netty.buffer.ByteBuf salesRaw = io.netty.buffer.Unpooled.buffer();
        salesSource.writeNetData(new RegistryFriendlyByteBuf(salesRaw, wire));
        salesRaw.readerIndex(0);
        SalesMilestoneTask salesCopy = addSales(fixture, 6921L, 1L);
        salesCopy.readNetData(new RegistryFriendlyByteBuf(salesRaw, wire));
        helper.assertTrue(salesCopy.getTarget() == 777L, "net round-trip lost the target: " + salesCopy.getTarget());
        helper.assertTrue(salesCopy.getAllowTeamTrades(), "net round-trip lost the team policy");

        // SNBT round-trip on Trading Partner.
        TradingPartnerTask partnerSource = addPartner(fixture, 6922L, 3L);
        CompoundTag partnerTag = new CompoundTag();
        partnerSource.writeData(partnerTag, registries);
        TradingPartnerTask partnerCopy = addPartner(fixture, 6923L, 1L);
        partnerCopy.readData(partnerTag, registries);
        helper.assertTrue(partnerCopy.getTarget() == 3L,
            "SNBT round-trip lost the target: " + partnerCopy.getTarget());
        helper.assertFalse(partnerCopy.getAllowTeamTrades(),
            "the team policy must round-trip as written (false)");
        helper.succeed();
    }

    // --- helpers -------------------------------------------------------------

    private record ResourceIds(ResourceLocation id) {
    }

    private static TradeFact factOf(TradeEvent.PostTradeEvent event) {
        return LightmansCurrencyAdapter.factFrom(event)
            .orElseThrow(() -> new IllegalStateException("extraction dropped a completed player trade"));
    }

    private static PlayerTransactionTask addTransaction(QuestFixture fixture, long id, long target) {
        PlayerTransactionTask task = new PlayerTransactionTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setTarget(target);
        return task;
    }

    private static SalesMilestoneTask addSales(QuestFixture fixture, long id, long target) {
        SalesMilestoneTask task = new SalesMilestoneTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setTarget(target);
        return task;
    }

    private static TradingPartnerTask addPartner(QuestFixture fixture, long id, long target) {
        TradingPartnerTask task = new TradingPartnerTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setTarget(target);
        return task;
    }
}
