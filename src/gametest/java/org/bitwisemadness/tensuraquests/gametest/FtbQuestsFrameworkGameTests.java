package org.bitwisemadness.tensuraquests.gametest;

import dev.architectury.utils.Env;
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.framework.IntakeOutcome;
import org.bitwisemadness.tensuraquests.tasks.CounterTask;

/**
 * GameTests for the FTB Quests integration framework: type
 * registration (FR-001), SNBT and net round-trips (FR-002, FR-003),
 * server-validated progress to completion with logging (FR-004,
 * FR-012) and TeamData persistence (FR-005's automated half).
 *
 * <p>Every test builds an isolated {@link BaseQuestFile} subclass, so
 * no test mutates the run's real quest file. Keep the
 * {@code expectedTests} count in {@code build.gradle} in sync with the
 * number of {@code @GameTest} methods here (TEST-001).</p>
 */
@GameTestHolder(TensuraQuests.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FtbQuestsFrameworkGameTests {

    private static final long CHAPTER_ID = 6001L;
    private static final long QUEST_ID = 6002L;
    private static final String INTEGRATION = "tensura_quests:selftest";

    private FtbQuestsFrameworkGameTests() {
    }

    /** FR-001: the type is registered under our namespace after mod load. */
    @GameTest(template = "empty")
    public static void counter_task_type_registered(GameTestHelper helper) {
        Optional<TaskType> found = FtbQuestsBridge.findTaskType(FtbQuestsBridge.COUNTER_TASK_ID);
        helper.assertTrue(found.isPresent(), "counter task type missing from the FTB Quests registry");
        TaskType type = found.get();
        helper.assertTrue(FtbQuestsBridge.COUNTER_TASK_ID.equals(type.getTypeId()),
            "registered type id is " + type.getTypeId() + ", expected " + FtbQuestsBridge.COUNTER_TASK_ID);

        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask task = fixture.addCounterTask(6101L, 5L);
        helper.assertTrue(task.getType() == type, "getType() must return the registered instance");
        helper.assertTrue(task.getMaxProgress() == 5L, "max progress must follow the configured target");
        helper.succeed();
    }

    /** FR-002: writeData -> readData preserves config; absent fields stay additive. */
    @GameTest(template = "empty")
    public static void counter_task_snbt_round_trip(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask source = fixture.addCounterTask(6201L, 1L);
        source.setTarget(42L);

        CompoundTag tag = new CompoundTag();
        source.writeData(tag, registries);
        CounterTask copy = fixture.addCounterTask(6202L, 1L);
        copy.readData(tag, registries);
        helper.assertTrue(copy.getTarget() == 42L, "SNBT round-trip lost the target: " + copy.getTarget());

        CounterTask legacy = fixture.addCounterTask(6203L, 9L);
        legacy.readData(new CompoundTag(), registries);
        helper.assertTrue(legacy.getTarget() == CounterTask.DEFAULT_TARGET,
            "a save without the target field must fall back to the default, got " + legacy.getTarget());
        helper.succeed();
    }

    /** FR-003: writeNetData -> readNetData preserves config. */
    @GameTest(template = "empty")
    public static void counter_task_net_round_trip(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask source = fixture.addCounterTask(6301L, 1L);
        source.setTarget(77L);

        ByteBuf raw = Unpooled.buffer();
        RegistryFriendlyByteBuf outgoing = new RegistryFriendlyByteBuf(raw, registries);
        source.writeNetData(outgoing);
        raw.readerIndex(0);
        RegistryFriendlyByteBuf incoming = new RegistryFriendlyByteBuf(raw, registries);
        CounterTask copy = fixture.addCounterTask(6302L, 1L);
        copy.readNetData(incoming);
        helper.assertTrue(copy.getTarget() == 77L, "net round-trip lost the target: " + copy.getTarget());
        helper.succeed();
    }

    /**
     * FR-004 and FR-012: intake grants only server-side, completes at
     * the threshold, rejects every invalid attempt, and every decision
     * is logged with integration, reason and ids.
     */
    @GameTest(template = "empty")
    public static void counter_intake_progresses_and_logs(GameTestHelper helper) {
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask task = fixture.addCounterTask(6401L, 3L);
        TeamData team = fixture.team;

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            helper.assertTrue(
                task.recordValidatedIntake(team, 1L, INTEGRATION, "first grant") == IntakeOutcome.ACCEPTED,
                "first intake must be accepted");
            helper.assertTrue(team.getProgress(task) == 1L,
                "progress must be 1 after the first grant, got " + team.getProgress(task));
            helper.assertFalse(team.isCompleted(task), "task must not complete below the threshold");

            helper.assertTrue(
                task.recordValidatedIntake(team, 0L, INTEGRATION, "zero amount") == IntakeOutcome.REJECTED_INVALID_AMOUNT,
                "zero intake must be rejected");
            helper.assertTrue(
                task.recordValidatedIntake(team, -2L, INTEGRATION, "negative amount") == IntakeOutcome.REJECTED_INVALID_AMOUNT,
                "negative intake must be rejected");
            helper.assertTrue(team.getProgress(task) == 1L,
                "rejected intake must not move progress, got " + team.getProgress(task));

            helper.assertTrue(
                task.recordValidatedIntake(team, 50L, INTEGRATION, "crossing grant") == IntakeOutcome.ACCEPTED,
                "intake crossing the threshold must be accepted");
            helper.assertTrue(team.getProgress(task) == 3L,
                "progress must clamp at the target, got " + team.getProgress(task));
            helper.assertTrue(team.isCompleted(task), "task must be completed at the threshold");

            helper.assertTrue(
                task.recordValidatedIntake(team, 1L, INTEGRATION, "after completion") == IntakeOutcome.REJECTED_ALREADY_COMPLETE,
                "intake after completion must be rejected");
            helper.assertTrue(
                task.recordValidatedIntake(null, 1L, INTEGRATION, "missing team") == IntakeOutcome.REJECTED_NO_PROGRESS_HOLDER,
                "intake without team data must be rejected");

            Fixture clientFixture = newFixture(helper, Env.CLIENT);
            CounterTask clientTask = clientFixture.addCounterTask(6402L, 3L);
            helper.assertTrue(
                clientTask.recordValidatedIntake(clientFixture.team, 1L, INTEGRATION, "client side") == IntakeOutcome.REJECTED_NOT_SERVER_SIDE,
                "a client-side quest file must reject intake");

            helper.assertTrue(capture.hasMessage("intake ACCEPTED", "integration=" + INTEGRATION,
                    "reason=first grant", "team=" + team.getTeamId()),
                "accepted intake not logged with integration, reason and ids");
            helper.assertTrue(capture.hasMessage("intake REJECTED_INVALID_AMOUNT",
                    "reason=zero amount", "task=6401"),
                "rejected intake not logged with outcome, reason and ids");
            helper.assertTrue(capture.hasMessage("intake REJECTED_NOT_SERVER_SIDE", "task=6402"),
                "client-side rejection not logged with ids");
            helper.succeed();
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
    }

    /**
     * FR-005 support: task progress and completion survive a TeamData
     * NBT round-trip, the same serialization a server restart performs
     * (the manual restart procedure stays on the issue).
     */
    @GameTest(template = "empty")
    public static void team_data_progress_survives_nbt_round_trip(GameTestHelper helper) {
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask task = fixture.addCounterTask(6501L, 4L);
        TeamData team = fixture.team;

        helper.assertTrue(task.recordValidatedIntake(team, 2L, INTEGRATION, "persistence grant") == IntakeOutcome.ACCEPTED,
            "intake for the persistence test must be accepted");
        TeamData restored = new TeamData(team.getTeamId(), fixture.file);
        restored.deserializeNBT(team.serializeNBT());
        helper.assertTrue(restored.getProgress(task) == 2L,
            "restored progress must be 2, got " + restored.getProgress(task));
        helper.assertFalse(restored.isCompleted(task), "restored task must not be completed yet");

        helper.assertTrue(task.recordValidatedIntake(team, 2L, INTEGRATION, "completing grant") == IntakeOutcome.ACCEPTED,
            "completing intake must be accepted");
        helper.assertTrue(team.isCompleted(task), "task must be completed at the threshold");
        TeamData restoredCompleted = new TeamData(team.getTeamId(), fixture.file);
        restoredCompleted.deserializeNBT(team.serializeNBT());
        helper.assertTrue(restoredCompleted.getProgress(task) == 4L,
            "restored progress must be 4, got " + restoredCompleted.getProgress(task));
        helper.assertTrue(restoredCompleted.isCompleted(task), "completion must survive the round-trip");
        helper.succeed();
    }

    /** One quest file with a single chapter, quest and team. */
    private static final class Fixture {

        private final TestQuestFile file;
        private final Quest quest;
        private final TeamData team;

        private Fixture(TestQuestFile file, Quest quest, TeamData team) {
            this.file = file;
            this.quest = quest;
            this.team = team;
        }

        private CounterTask addCounterTask(long id, long target) {
            CounterTask task = new CounterTask(id, quest);
            quest.addTask(task);
            task.setTarget(target);
            return task;
        }
    }

    private static Fixture newFixture(GameTestHelper helper, Env side) {
        TestQuestFile file = new TestQuestFile(side, helper.getLevel().registryAccess());
        ChapterGroup group = file.getDefaultChapterGroup();
        Chapter chapter = new Chapter(CHAPTER_ID, file, group);
        group.addChapter(chapter);
        Quest quest = new Quest(QUEST_ID, chapter);
        chapter.addQuest(quest);
        TeamData team = file.getOrCreateTeamData(UUID.randomUUID());
        return new Fixture(file, quest, team);
    }
}
