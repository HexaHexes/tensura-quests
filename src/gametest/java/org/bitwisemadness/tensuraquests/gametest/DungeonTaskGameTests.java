package org.bitwisemadness.tensuraquests.gametest;

import dev.architectury.utils.Env;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bitwisemadness.infinitedungeonapi.event.FloorEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.event.LayerCompletedEvent;
import org.bitwisemadness.infinitedungeonapi.event.LayerEnteredEvent;
import org.bitwisemadness.infinitedungeonapi.event.MinibossSlainEvent;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.compat.infinitedungeon.DungeonIntake;
import org.bitwisemadness.tensuraquests.compat.infinitedungeon.DungeonPresence;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.progress.ProgressAttachments;
import org.bitwisemadness.tensuraquests.tasks.DungeonMilestoneTask;
import org.bitwisemadness.tensuraquests.tasks.FloorCompletionTask;
import org.bitwisemadness.tensuraquests.tasks.LayerCompletionTask;
import org.bitwisemadness.tensuraquests.tasks.MinibossDefeatTask;

/**
 * GameTests for the dungeon progression family: registration behind the
 * INT-004 presence gate, FR-030 (floor completion through the live bus and
 * the fixture seam), FR-033/SEC-003 (foreign-dimension rejection with its
 * visible log), FR-031 (attributed layer completion - unknown completer
 * credits nobody), FR-032 (miniboss layer filtering + unknown-killer
 * credit), FR-034 (milestone at/below/deeper threshold), and config
 * round-trips.
 *
 * <p><strong>Infinite Dungeon contact (the adapter boundary).</strong>
 * This gametest source set imports the published event classes directly -
 * fabricating events is the harness's job - and
 * {@code AdapterIsolationTest} documents the exemption by scanning src/main
 * only. Attribution credit flows through the same counter store and ADR-0002
 * policy mirror as every other family; offline or unknown players are never
 * credited (the adapter routes nothing without an attributable, online
 * player).</p>
 */
@GameTestHolder(TensuraQuests.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DungeonTaskGameTests {

    private DungeonTaskGameTests() {
    }

    /**
     * Registration + gate premise + dispatch shape: all four types under our
     * namespace, the presence probe that allowed registration, and
     * {@code autoSubmitOnPlayerTick() == 0} - dungeon tasks are event-driven
     * and must never enter FTB Quests' polled auto-submit set.
     */
    @GameTest(template = "empty")
    public static void dungeon_task_types_registered(GameTestHelper helper) {
        helper.assertTrue(DungeonPresence.isInstalled(),
            "the gametest runtime must ship infinite_dungeon_api for this suite to mean anything");
        ResourceLocation[] ids = {
            FtbQuestsBridge.FLOOR_COMPLETION_TASK_ID,
            FtbQuestsBridge.LAYER_COMPLETION_TASK_ID,
            FtbQuestsBridge.MINIBOSS_DEFEAT_TASK_ID,
            FtbQuestsBridge.DUNGEON_MILESTONE_TASK_ID,
        };
        for (ResourceLocation id : ids) {
            Optional<TaskType> found = FtbQuestsBridge.findTaskType(id);
            helper.assertTrue(found.isPresent(), "dungeon task type missing from the FTB Quests registry: " + id);
            helper.assertTrue(id.equals(found.get().getTypeId()),
                "registered type id is " + found.get().getTypeId() + ", expected " + id);
        }

        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        FloorCompletionTask task = addFloor(fixture, 8001L, 0, 0);
        Optional<TaskType> registered = FtbQuestsBridge.findTaskType(FtbQuestsBridge.FLOOR_COMPLETION_TASK_ID);
        helper.assertTrue(task.getType() == registered.orElseThrow(),
            "getType() must return the registered instance");
        helper.assertTrue(task.autoSubmitOnPlayerTick() == 0,
            "dungeon tasks are event-driven and must not enter the polled auto-submit set, got "
                + task.autoSubmitOnPlayerTick());
        helper.succeed();
    }

    /**
     * FR-030: floor entries for the configured floor credit one unit each
     * through the full intake path (counter + policy mirror + FR-012 log),
     * a live-bus probe proves the wired listener leaves fixture state alone,
     * and a different floor is ordinary silent filtering.
     */
    @GameTest(template = "empty")
    public static void floor_completion_counts_configured_floor(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        FloorCompletionTask task = addFloor(fixture, 8011L, 2, 5);
        task.setTarget(2L);
        task.setDimensions(List.of(helper.getLevel().dimension().location().toString()));
        ServerPlayer player = fixture.actor;

        // Live-bus probe: the wired listener routes into the REAL quest file,
        // which holds no dungeon tasks - fixture state must not move.
        NeoForge.EVENT_BUS.post(
            new FloorEnteredEvent(helper.getLevel().dimension(), player.getUUID(), 2, 5));
        helper.assertTrue(fixture.team.getProgress(task) == 0L,
            "the bus probe must not touch fixture state, got " + fixture.team.getProgress(task));

        // A different floor: silent context filtering, no progress.
        DungeonIntake.route(new FloorEnteredEvent(helper.getLevel().dimension(), player.getUUID(), 2, 9),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(task) == 0L,
            "a floor outside the configuration must not count, got " + fixture.team.getProgress(task));

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            for (int visit = 0; visit < 2; visit++) {
                DungeonIntake.route(new FloorEnteredEvent(helper.getLevel().dimension(), player.getUUID(), 2, 5),
                    fixture.file, helper.getLevel().getServer());
            }
            helper.assertTrue(fixture.team.getProgress(task) == 2L,
                "each configured floor entry must count once, got " + fixture.team.getProgress(task));
            helper.assertTrue(fixture.team.isCompleted(task), "two of two visits must complete the task");
            helper.assertTrue(ProgressAttachments.readCount(player, task.getId()) == 2L,
                "the personal counter must hold the visits, got "
                    + ProgressAttachments.readCount(player, task.getId()));
            helper.assertTrue(capture.hasMessage("intake ACCEPTED", "integration=infinite_dungeon",
                    "reason=floor entered layer=2 floor=5", "task=8011", "player=" + player.getUUID()),
                "accepted intake not logged with integration, reason and ids");
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
        helper.succeed();
    }

    /**
     * FR-033/SEC-003: an event from a dimension the task does not configure
     * increments nothing and says so with a visible reason line.
     */
    @GameTest(template = "empty")
    public static void foreign_dimension_increments_nothing_and_logs(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        FloorCompletionTask task = addFloor(fixture, 8012L, 0, 0);
        task.setDimensions(List.of(helper.getLevel().dimension().location().toString()));
        ServerPlayer player = fixture.actor;

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            ResourceLocation foreign = ResourceLocation.fromNamespaceAndPath("minecraft", "the_end");
            DungeonIntake.route(new FloorEnteredEvent(
                    ResourceKey.create(Registries.DIMENSION, foreign),
                    player.getUUID(), 0, 0), fixture.file, helper.getLevel().getServer());

            helper.assertTrue(fixture.team.getProgress(task) == 0L,
                "a foreign-dimension event must not increment, got " + fixture.team.getProgress(task));
            helper.assertTrue(ProgressAttachments.readCount(player, task.getId()) == 0L,
                "a foreign-dimension event must not touch the personal counter");
            helper.assertTrue(capture.hasMessage("dungeon intake skipped", "reason=foreign_dimension",
                    "task=8012", "event_dimension=minecraft:the_end"),
                "the foreign-dimension rejection must log its reason and ids");
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
        helper.succeed();
    }

    /**
     * FR-031: the attributed completer is credited (counter + mirror), and
     * an unknown completer credits nobody - presence must never become
     * credit (ADR-0003).
     */
    @GameTest(template = "empty")
    public static void layer_completion_credits_the_attributed_completer(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        LayerCompletionTask task = addLayer(fixture, 8021L);
        task.setDimensions(List.of(helper.getLevel().dimension().location().toString()));
        ServerPlayer player = fixture.actor;

        // Unknown completer first: nothing routes.
        DungeonIntake.route(new LayerCompletedEvent(helper.getLevel().dimension(), 3),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(task) == 0L,
            "an unattributed completion must credit nobody, got " + fixture.team.getProgress(task));

        DungeonIntake.route(new LayerCompletedEvent(helper.getLevel().dimension(), 3, player.getUUID()),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(task) == 1L,
            "the attributed completer must be credited, got " + fixture.team.getProgress(task));
        helper.assertTrue(fixture.team.isCompleted(task), "one of one layers must complete the task");
        helper.assertTrue(ProgressAttachments.readCount(player, task.getId()) == 1L,
            "the completer's personal counter must hold the credit");
        helper.succeed();
    }

    /**
     * FR-032: only the configured layer counts, and an unknown killer
     * credits nobody - exactly the acceptance's ordering.
     */
    @GameTest(template = "empty")
    public static void miniboss_defeat_credits_only_at_the_configured_layer(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        MinibossDefeatTask task = addMiniboss(fixture, 8031L, 4);
        task.setDimensions(List.of(helper.getLevel().dimension().location().toString()));
        ServerPlayer player = fixture.actor;
        LivingEntity boss = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(boss != null, "the miniboss must be creatable");

        // Unknown killer: no credit at any layer.
        DungeonIntake.route(new MinibossSlainEvent(helper.getLevel().dimension(), 4, 0, boss, null),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(task) == 0L,
            "an unknown-kill must credit nobody, got " + fixture.team.getProgress(task));

        // Right killer, wrong layer: silent context filtering.
        DungeonIntake.route(new MinibossSlainEvent(helper.getLevel().dimension(), 9, 0, boss, player.getUUID()),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(task) == 0L,
            "another layer's miniboss must not count, got " + fixture.team.getProgress(task));

        DungeonIntake.route(new MinibossSlainEvent(helper.getLevel().dimension(), 4, 0, boss, player.getUUID()),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(task) == 1L,
            "the attributed kill at the configured layer must count, got " + fixture.team.getProgress(task));
        helper.assertTrue(fixture.team.isCompleted(task), "one of one minibosses must complete the task");
        helper.assertTrue(ProgressAttachments.readCount(player, task.getId()) == 1L,
            "the killer's personal counter must hold the credit");
        helper.succeed();
    }

    /**
     * FR-034: at-least semantics across two thresholds in one file - below
     * neither, at the first (only it completes), beyond (the deeper one too).
     */
    @GameTest(template = "empty")
    public static void milestone_completes_at_or_beyond_the_threshold(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        DungeonMilestoneTask shallow = addMilestone(fixture, 8041L, 3);
        DungeonMilestoneTask deep = addMilestone(fixture, 8042L, 5);
        shallow.setDimensions(List.of(helper.getLevel().dimension().location().toString()));
        deep.setDimensions(List.of(helper.getLevel().dimension().location().toString()));
        ServerPlayer player = fixture.actor;

        DungeonIntake.route(new LayerEnteredEvent(helper.getLevel().dimension(), player.getUUID(), 2),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(shallow) == 0L && fixture.team.getProgress(deep) == 0L,
            "a layer below both thresholds must count for neither");

        DungeonIntake.route(new LayerEnteredEvent(helper.getLevel().dimension(), player.getUUID(), 3),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(shallow) == 1L,
            "entering the threshold layer must complete the at-least task");
        helper.assertTrue(fixture.team.isCompleted(shallow), "the at-least task must be completed at its layer");
        helper.assertTrue(fixture.team.getProgress(deep) == 0L,
            "the deeper threshold must stay incomplete at layer 3");

        DungeonIntake.route(new LayerEnteredEvent(helper.getLevel().dimension(), player.getUUID(), 7),
            fixture.file, helper.getLevel().getServer());
        helper.assertTrue(fixture.team.getProgress(deep) == 1L,
            "a deeper layer must complete the deeper threshold");
        helper.succeed();
    }

    /**
     * Config codecs for the family: dimensions + target round-trips, the
     * per-type fields, additive defaults, malformed-dimension drops, the
     * empty-dimension-list contract, and the {@code @OnlyIn(Dist.CLIENT)}
     * hook discipline every {@code fillConfigGroup} override must carry.
     */
    @GameTest(template = "empty")
    public static void dungeon_task_config_round_trips(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        RegistryAccess wire = helper.getLevel().registryAccess();
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);

        // Floor: SNBT round-trips everything; a legacy save falls back.
        FloorCompletionTask floorSource = addFloor(fixture, 8051L, 2, 7);
        floorSource.setDimensions(List.of("minecraft:the_end", "minecraft:the_nether"));
        floorSource.setTarget(4L);
        CompoundTag floorTag = new CompoundTag();
        floorSource.writeData(floorTag, registries);
        FloorCompletionTask floorCopy = addFloor(fixture, 8052L, 0, 0);
        floorCopy.readData(floorTag, registries);
        helper.assertTrue(floorCopy.getDimensions()
                .equals(List.of("minecraft:the_end", "minecraft:the_nether")),
            "SNBT round-trip lost the dimensions: " + floorCopy.getDimensions());
        helper.assertTrue(floorCopy.getTarget() == 4L && floorCopy.getLayer() == 2 && floorCopy.getFloor() == 7,
            "SNBT round-trip lost the floor config: " + floorCopy.getTarget() + "/"
                + floorCopy.getLayer() + "/" + floorCopy.getFloor());
        FloorCompletionTask floorLegacy = addFloor(fixture, 8053L, 0, 0);
        floorLegacy.readData(new CompoundTag(), registries);
        helper.assertTrue(floorLegacy.getDimensions().isEmpty() && floorLegacy.getTarget() == 1L,
            "a save without the fields must fall back to the defaults");
        helper.assertFalse(floorLegacy.matchesDimension("minecraft:overworld"),
            "an empty dimension list must listen to nothing");

        // Malformed dimensions drop loudly instead of corrupting the config.
        CompoundTag malformed = new CompoundTag();
        ListTag entries = new ListTag();
        entries.add(StringTag.valueOf("NOT A DIMENSION"));
        entries.add(StringTag.valueOf("minecraft:the_end"));
        malformed.put("dimensions", entries);
        malformed.putLong("target", 1L);
        FloorCompletionTask survivor = addFloor(fixture, 8054L, 0, 0);
        survivor.readData(malformed, registries);
        helper.assertTrue(survivor.getDimensions().equals(List.of("minecraft:the_end")),
            "malformed dimensions must drop while valid ones survive, got " + survivor.getDimensions());

        // Net round-trip on the milestone (dimensions + threshold + target).
        DungeonMilestoneTask milestoneSource = addMilestone(fixture, 8055L, 9);
        milestoneSource.setDimensions(List.of("minecraft:the_end"));
        milestoneSource.setTarget(3L);
        ByteBuf raw = Unpooled.buffer();
        milestoneSource.writeNetData(new RegistryFriendlyByteBuf(raw, wire));
        raw.readerIndex(0);
        DungeonMilestoneTask milestoneCopy = addMilestone(fixture, 8056L, 1);
        milestoneCopy.readNetData(new RegistryFriendlyByteBuf(raw, wire));
        helper.assertTrue(milestoneCopy.getRequiredLayer() == 9
                && milestoneCopy.getTarget() == 3L
                && milestoneCopy.getDimensions().equals(List.of("minecraft:the_end")),
            "net round-trip lost the milestone config: " + milestoneCopy.getRequiredLayer() + "/"
                + milestoneCopy.getTarget() + "/" + milestoneCopy.getDimensions());

        // Miniboss required_layer survives SNBT.
        MinibossDefeatTask minibossSource = addMiniboss(fixture, 8057L, 6);
        CompoundTag minibossTag = new CompoundTag();
        minibossSource.writeData(minibossTag, registries);
        MinibossDefeatTask minibossCopy = addMiniboss(fixture, 8058L, 0);
        minibossCopy.readData(minibossTag, registries);
        helper.assertTrue(minibossCopy.getRequiredLayer() == 6,
            "SNBT round-trip lost the required layer: " + minibossCopy.getRequiredLayer());

        assertClientOnlyEditorHook(helper, FloorCompletionTask.class);
        helper.succeed();
    }

    // --- helpers -------------------------------------------------------------

    private static FloorCompletionTask addFloor(QuestFixture fixture, long id, int layer, int floor) {
        FloorCompletionTask task = new FloorCompletionTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setLayer(layer);
        task.setFloor(floor);
        return task;
    }

    private static LayerCompletionTask addLayer(QuestFixture fixture, long id) {
        LayerCompletionTask task = new LayerCompletionTask(id, fixture.quest);
        fixture.quest.addTask(task);
        return task;
    }

    private static MinibossDefeatTask addMiniboss(QuestFixture fixture, long id, int requiredLayer) {
        MinibossDefeatTask task = new MinibossDefeatTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setRequiredLayer(requiredLayer);
        return task;
    }

    private static DungeonMilestoneTask addMilestone(QuestFixture fixture, long id, int requiredLayer) {
        DungeonMilestoneTask task = new DungeonMilestoneTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setRequiredLayer(requiredLayer);
        return task;
    }

    /**
     * The editor hook must be client-only: either stripped from this
     * dedicated server (the normal case) or present and carrying
     * {@code @OnlyIn(Dist.CLIENT)} - a present, unannotated hook would
     * die at the stripped upstream super (the #9 defect class).
     */
    private static void assertClientOnlyEditorHook(GameTestHelper helper, Class<?> cls) {
        Method hook = null;
        for (Method candidate : cls.getDeclaredMethods()) {
            if ("fillConfigGroup".equals(candidate.getName())) {
                hook = candidate;
                break;
            }
        }
        if (hook == null) {
            return;
        }
        OnlyIn onlyIn = hook.getAnnotation(OnlyIn.class);
        helper.assertTrue(onlyIn != null && onlyIn.value() == Dist.CLIENT,
            cls.getSimpleName() + ".fillConfigGroup must be @OnlyIn(Dist.CLIENT), found " + onlyIn);
    }
}
