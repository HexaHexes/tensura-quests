package org.bitwisemadness.tensuraquests.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import dev.architectury.utils.Env;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.data.AbstractTeamBase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.framework.IntakeOutcome;
import org.bitwisemadness.tensuraquests.progress.CounterPolicy;
import org.bitwisemadness.tensuraquests.progress.PersonalCounterStore;
import org.bitwisemadness.tensuraquests.progress.ProgressAttachments;
import org.bitwisemadness.tensuraquests.tasks.CounterTask;

/**
 * GameTests for the FTB Quests integration framework: type
 * registration (FR-001), SNBT and net round-trips incl. the completion
 * policy (FR-002, FR-003, FR-010), server-validated progress to
 * completion with logging (FR-004, FR-012), TeamData persistence
 * (FR-005's automated half), the ANY and ALL completion policies over
 * two simulated members (FR-009, FR-010), counter isolation across
 * membership churn (SEC-005) and the attachment codec round-trip
 * (FR-008's automated half).
 *
 * <p>Every test builds an isolated {@link BaseQuestFile} subclass, so
 * no test mutates the run's real quest file. The fixture also
 * registers a <em>real</em> FTB Teams team for its players: the policy
 * mirror resolves membership through {@code TeamManager.getTeamByID},
 * and a synthetic team UUID would mirror nothing (the same reason
 * {@code TeamData.getOnlineMembers} degrades to an empty list for
 * synthetic ids) - gametest runs therefore accumulate test teams in
 * FTB Teams' saved state. Keep the {@code expectedTests} count in
 * {@code build.gradle} in sync with the number of {@code @GameTest}
 * methods here (TEST-001).</p>
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

    /** FR-002: writeData -> readData preserves config and policy; absent fields stay additive. */
    @GameTest(template = "empty")
    public static void counter_task_snbt_round_trip(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask source = fixture.addCounterTask(6201L, 1L);
        source.setTarget(42L);
        source.setCompletionPolicy(CounterPolicy.ALL);

        CompoundTag tag = new CompoundTag();
        source.writeData(tag, registries);
        CounterTask copy = fixture.addCounterTask(6202L, 1L);
        copy.readData(tag, registries);
        helper.assertTrue(copy.getTarget() == 42L, "SNBT round-trip lost the target: " + copy.getTarget());
        helper.assertTrue(copy.getCompletionPolicy() == CounterPolicy.ALL,
            "SNBT round-trip lost the completion policy: " + copy.getCompletionPolicy());

        CounterTask legacy = fixture.addCounterTask(6203L, 9L);
        legacy.readData(new CompoundTag(), registries);
        helper.assertTrue(legacy.getTarget() == CounterTask.DEFAULT_TARGET,
            "a save without the target field must fall back to the default, got " + legacy.getTarget());
        helper.assertTrue(legacy.getCompletionPolicy() == CounterPolicy.ANY,
            "a save without the policy field must fall back to ANY, got " + legacy.getCompletionPolicy());
        helper.succeed();
    }

    /** FR-003: writeNetData -> readNetData preserves config and policy. */
    @GameTest(template = "empty")
    public static void counter_task_net_round_trip(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask source = fixture.addCounterTask(6301L, 1L);
        source.setTarget(77L);
        source.setCompletionPolicy(CounterPolicy.ALL);

        ByteBuf raw = Unpooled.buffer();
        RegistryFriendlyByteBuf outgoing = new RegistryFriendlyByteBuf(raw, registries);
        source.writeNetData(outgoing);
        raw.readerIndex(0);
        RegistryFriendlyByteBuf incoming = new RegistryFriendlyByteBuf(raw, registries);
        CounterTask copy = fixture.addCounterTask(6302L, 1L);
        copy.readNetData(incoming);
        helper.assertTrue(copy.getTarget() == 77L, "net round-trip lost the target: " + copy.getTarget());
        helper.assertTrue(copy.getCompletionPolicy() == CounterPolicy.ALL,
            "net round-trip lost the completion policy: " + copy.getCompletionPolicy());
        helper.succeed();
    }

    /**
     * FR-004 and FR-012: intake grants only server-side, completes at
     * the threshold, rejects every invalid attempt, and every decision
     * is logged with integration, reason and ids. Single-member team:
     * the policy mirror equals the member's own counter, so progress
     * matches the pre-policy behaviour exactly.
     */
    @GameTest(template = "empty")
    public static void counter_intake_progresses_and_logs(GameTestHelper helper) {
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask task = fixture.addCounterTask(6401L, 3L);
        TeamData team = fixture.team;
        ServerPlayer actor = fixture.actor;

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            helper.assertTrue(
                task.recordValidatedIntake(actor, team, 1L, INTEGRATION, "first grant") == IntakeOutcome.ACCEPTED,
                "first intake must be accepted");
            helper.assertTrue(team.getProgress(task) == 1L,
                "progress must be 1 after the first grant, got " + team.getProgress(task));
            helper.assertFalse(team.isCompleted(task), "task must not complete below the threshold");
            helper.assertTrue(ProgressAttachments.readCount(actor, task.getId()) == 1L,
                "the personal counter must mirror the grant, got " + ProgressAttachments.readCount(actor, task.getId()));

            helper.assertTrue(
                task.recordValidatedIntake(actor, team, 0L, INTEGRATION, "zero amount") == IntakeOutcome.REJECTED_INVALID_AMOUNT,
                "zero intake must be rejected");
            helper.assertTrue(
                task.recordValidatedIntake(actor, team, -2L, INTEGRATION, "negative amount") == IntakeOutcome.REJECTED_INVALID_AMOUNT,
                "negative intake must be rejected");
            helper.assertTrue(team.getProgress(task) == 1L,
                "rejected intake must not move progress, got " + team.getProgress(task));

            helper.assertTrue(
                task.recordValidatedIntake(actor, team, 50L, INTEGRATION, "crossing grant") == IntakeOutcome.ACCEPTED,
                "intake crossing the threshold must be accepted");
            helper.assertTrue(team.getProgress(task) == 3L,
                "progress must clamp at the target, got " + team.getProgress(task));
            helper.assertTrue(team.isCompleted(task), "task must be completed at the threshold");

            helper.assertTrue(
                task.recordValidatedIntake(actor, team, 1L, INTEGRATION, "after completion") == IntakeOutcome.REJECTED_ALREADY_COMPLETE,
                "intake after completion must be rejected");
            helper.assertTrue(
                task.recordValidatedIntake(actor, null, 1L, INTEGRATION, "missing team") == IntakeOutcome.REJECTED_NO_PROGRESS_HOLDER,
                "intake without team data must be rejected");
            helper.assertTrue(
                task.recordValidatedIntake(null, team, 1L, INTEGRATION, "missing player") == IntakeOutcome.REJECTED_NO_PROGRESS_HOLDER,
                "intake without an acting player must be rejected");

            Fixture clientFixture = newFixture(helper, Env.CLIENT);
            CounterTask clientTask = clientFixture.addCounterTask(6402L, 3L);
            helper.assertTrue(
                clientTask.recordValidatedIntake(clientFixture.actor, clientFixture.team, 1L, INTEGRATION, "client side") == IntakeOutcome.REJECTED_NOT_SERVER_SIDE,
                "a client-side quest file must reject intake");

            helper.assertTrue(capture.hasMessage("intake ACCEPTED", "integration=" + INTEGRATION,
                    "reason=first grant", "team=" + team.getTeamId(), "player=" + actor.getUUID()),
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

        helper.assertTrue(task.recordValidatedIntake(fixture.actor, team, 2L, INTEGRATION, "persistence grant") == IntakeOutcome.ACCEPTED,
            "intake for the persistence test must be accepted");
        TeamData restored = new TeamData(team.getTeamId(), fixture.file);
        restored.deserializeNBT(team.serializeNBT());
        helper.assertTrue(restored.getProgress(task) == 2L,
            "restored progress must be 2, got " + restored.getProgress(task));
        helper.assertFalse(restored.isCompleted(task), "restored task must not be completed yet");

        helper.assertTrue(task.recordValidatedIntake(fixture.actor, team, 2L, INTEGRATION, "completing grant") == IntakeOutcome.ACCEPTED,
            "completing intake must be accepted");
        helper.assertTrue(team.isCompleted(task), "task must be completed at the threshold");
        TeamData restoredCompleted = new TeamData(team.getTeamId(), fixture.file);
        restoredCompleted.deserializeNBT(team.serializeNBT());
        helper.assertTrue(restoredCompleted.getProgress(task) == 4L,
            "restored progress must be 4, got " + restoredCompleted.getProgress(task));
        helper.assertTrue(restoredCompleted.isCompleted(task), "completion must survive the round-trip");
        helper.succeed();
    }

    /**
     * FR-009: ANY - the default policy - completes the task as soon as
     * one member's personal counter meets the target. The mirrored
     * progress is the max over the team's counters, and only the
     * contributing member's counter moves.
     */
    @GameTest(template = "empty")
    public static void any_policy_completes_when_one_member_qualifies(GameTestHelper helper) {
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask task = fixture.addCounterTask(6601L, 3L);
        ServerPlayer second = MockPlayers.create(helper);
        fixture.addMember(second);
        TeamData team = fixture.team;

        helper.assertTrue(task.getCompletionPolicy() == CounterPolicy.ANY,
            "the default completion policy must be ANY");
        helper.assertTrue(
            task.recordValidatedIntake(fixture.actor, team, 3L, INTEGRATION, "any grant") == IntakeOutcome.ACCEPTED,
            "the first member's intake must be accepted");
        helper.assertTrue(team.isCompleted(task),
            "ANY must complete when a single member reaches the target");
        helper.assertTrue(team.getProgress(task) == 3L,
            "the mirrored progress must be the max member counter, got " + team.getProgress(task));
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 3L,
            "the contributing member must hold the intake in their own counter");
        helper.assertTrue(ProgressAttachments.readCount(second, task.getId()) == 0L,
            "the other member's counter must stay untouched");

        helper.assertTrue(
            task.recordValidatedIntake(fixture.actor, team, 1L, INTEGRATION, "after completion") == IntakeOutcome.REJECTED_ALREADY_COMPLETE,
            "intake after completion must be rejected");
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 3L,
            "rejected intake must not move the personal counter");
        helper.succeed();
    }

    /**
     * FR-010: ALL completes only when every member at check time meets
     * the target. The mirror is the min over the team's counters, so a
     * member without progress - the ADR-0002 joiner case - keeps the
     * task incomplete and the visible bar at zero until they
     * contribute.
     */
    @GameTest(template = "empty")
    public static void all_policy_waits_for_every_member(GameTestHelper helper) {
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask task = fixture.addCounterTask(6602L, 3L);
        task.setCompletionPolicy(CounterPolicy.ALL);
        ServerPlayer second = MockPlayers.create(helper);
        fixture.addMember(second);
        TeamData team = fixture.team;

        helper.assertTrue(
            task.recordValidatedIntake(fixture.actor, team, 3L, INTEGRATION, "first half") == IntakeOutcome.ACCEPTED,
            "the first member's intake must be accepted");
        helper.assertFalse(team.isCompleted(task),
            "ALL must stay incomplete while one member has no progress");
        helper.assertTrue(team.getProgress(task) == 0L,
            "the mirror must be the min member counter, got " + team.getProgress(task));

        helper.assertTrue(
            task.recordValidatedIntake(second, team, 3L, INTEGRATION, "second half") == IntakeOutcome.ACCEPTED,
            "the second member's intake must be accepted");
        helper.assertTrue(team.isCompleted(task),
            "ALL must complete once every member meets the target");
        helper.assertTrue(team.getProgress(task) == 3L,
            "the mirror must rise with the slowest member, got " + team.getProgress(task));
        helper.succeed();
    }

    /**
     * SEC-005: counters are player-keyed - a team membership change
     * neither copies nor erases them. The team's mirror follows the
     * membership on its next evaluation; the leaver's counter stays
     * theirs, untouched by later intake.
     */
    @GameTest(template = "empty")
    public static void membership_churn_keeps_player_counters(GameTestHelper helper) {
        Fixture fixture = newFixture(helper, Env.SERVER);
        CounterTask task = fixture.addCounterTask(6603L, 5L);
        ServerPlayer second = MockPlayers.create(helper);
        fixture.addMember(second);
        TeamData team = fixture.team;

        helper.assertTrue(
            task.recordValidatedIntake(fixture.actor, team, 2L, INTEGRATION, "before churn") == IntakeOutcome.ACCEPTED,
            "intake before the membership change must be accepted");
        helper.assertTrue(
            task.recordValidatedIntake(second, team, 1L, INTEGRATION, "second member") == IntakeOutcome.ACCEPTED,
            "the second member's intake must be accepted");
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 2L
                && ProgressAttachments.readCount(second, task.getId()) == 1L,
            "each member must hold their own counter before the churn");

        // The contributing member leaves the team.
        fixture.removeMember(second);

        helper.assertTrue(ProgressAttachments.readCount(second, task.getId()) == 1L,
            "leaving the team must not erase the leaver's counter");
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 2L,
            "leaving the team must not copy counters to the remaining members");

        // Later intake writes only the acting member's counter.
        helper.assertTrue(
            task.recordValidatedIntake(fixture.actor, team, 1L, INTEGRATION, "after churn") == IntakeOutcome.ACCEPTED,
            "intake after the membership change must be accepted");
        helper.assertTrue(ProgressAttachments.readCount(fixture.actor, task.getId()) == 3L,
            "the remaining member's counter must advance");
        helper.assertTrue(ProgressAttachments.readCount(second, task.getId()) == 1L,
            "the leaver's counter must stay untouched by later intake");
        helper.succeed();
    }

    /**
     * FR-008 (automated half): the attachment codec round-trips the
     * counters and the layout version; the restart procedure itself
     * stays with the Phase 7 manual matrix.
     */
    @GameTest(template = "empty")
    public static void counter_store_codec_round_trip(GameTestHelper helper) {
        PersonalCounterStore store = PersonalCounterStore.empty();
        store.increment(6801L, 5L);
        store.increment(6801L, 2L);
        store.increment(6802L, 1L);
        helper.assertTrue(store.get(6801L) == 7L,
            "increments must accumulate into one counter, got " + store.get(6801L));

        DataResult<Tag> encoded = PersonalCounterStore.CODEC.encodeStart(NbtOps.INSTANCE, store);
        helper.assertTrue(encoded.result().isPresent(), "the counter store must encode to NBT");
        DataResult<PersonalCounterStore> decoded =
            PersonalCounterStore.CODEC.parse(NbtOps.INSTANCE, encoded.result().orElseThrow());
        helper.assertTrue(decoded.result().isPresent(), "the counter store must decode from NBT");
        PersonalCounterStore restored = decoded.result().orElseThrow();
        helper.assertTrue(restored.get(6801L) == 7L,
            "the round-trip must keep counter 6801, got " + restored.get(6801L));
        helper.assertTrue(restored.get(6802L) == 1L,
            "the round-trip must keep counter 6802, got " + restored.get(6802L));
        helper.assertTrue(restored.get(9999L) == 0L, "an untouched task must read 0");
        helper.assertTrue(restored.version() == PersonalCounterStore.CURRENT_VERSION,
            "the round-trip must keep the layout version, got " + restored.version());

        // A corrupt key is skipped instead of failing the whole store.
        CompoundTag counters = new CompoundTag();
        counters.putLong("not-a-task-id", 4L);
        counters.putLong("6803", 9L);
        CompoundTag raw = new CompoundTag();
        raw.putInt("version", PersonalCounterStore.CURRENT_VERSION);
        raw.put("counters", counters);
        DataResult<PersonalCounterStore> partial = PersonalCounterStore.CODEC.parse(NbtOps.INSTANCE, raw);
        helper.assertTrue(partial.result().isPresent(),
            "a corrupt key must not fail the whole store");
        PersonalCounterStore survivor = partial.result().orElseThrow();
        helper.assertTrue(survivor.get(6803L) == 9L,
            "valid keys next to a corrupt one must survive, got " + survivor.get(6803L));
        helper.assertTrue(survivor.get(1L) == 0L, "the corrupt key must contribute nothing");
        helper.succeed();
    }

    /** One quest file with a chapter, quest, a real FTB Teams team and an acting member. */
    private static final class Fixture {

        private final TestQuestFile file;
        private final Quest quest;
        private final TeamData team;
        private final ServerPlayer actor;

        private Fixture(TestQuestFile file, Quest quest, TeamData team, ServerPlayer actor) {
            this.file = file;
            this.quest = quest;
            this.team = team;
            this.actor = actor;
        }

        private CounterTask addCounterTask(long id, long target) {
            CounterTask task = new CounterTask(id, quest);
            quest.addTask(task);
            task.setTarget(target);
            return task;
        }

        /**
         * Add a player to the fixture's team. The public Team API is
         * read-only for membership (INT-001 keeps main src on the API),
         * so tests reach the team impl - fixture-only code.
         *
         * @param member the player to add as a full member
         */
        private void addMember(ServerPlayer member) {
            teamImpl().addMember(member.getUUID(), TeamRank.MEMBER);
        }

        /**
         * Drop a player from the fixture's team (SEC-005 churn).
         *
         * @param member the player to remove
         */
        private void removeMember(ServerPlayer member) {
            teamImpl().removeMember(member.getUUID());
        }

        private AbstractTeamBase teamImpl() {
            return (AbstractTeamBase) FTBTeamsAPI.api().getManager()
                .getTeamByID(team.getTeamId())
                .orElseThrow(() -> new IllegalStateException("fixture team missing from FTB Teams"));
        }
    }

    private static Fixture newFixture(GameTestHelper helper, Env side) {
        TestQuestFile file = new TestQuestFile(side, helper.getLevel().registryAccess());
        ChapterGroup group = file.getDefaultChapterGroup();
        Chapter chapter = new Chapter(CHAPTER_ID, file, group);
        group.addChapter(chapter);
        Quest quest = new Quest(QUEST_ID, chapter);
        chapter.addQuest(quest);
        ServerPlayer actor = MockPlayers.create(helper);
        Team ftbTeam = createOrReuseTeam(actor);
        if (!ftbTeam.getMembers().contains(actor.getUUID())) {
            // createPartyTeam is expected to seat its creator; assert
            // rather than assume, so a behavioural change fails loudly.
            ((AbstractTeamBase) ftbTeam).addMember(actor.getUUID(), TeamRank.OWNER);
        }
        TeamData team = file.getOrCreateTeamData(ftbTeam);
        return new Fixture(file, quest, team, actor);
    }

    /**
     * The fixture's team must exist in FTB Teams, not only in the quest
     * file: the policy mirror resolves membership through the FTB Teams
     * manager, where a synthetic UUID resolves to nothing.
     *
     * @param actor the player the team is created for
     * @return a team the actor belongs to
     */
    private static Team createOrReuseTeam(ServerPlayer actor) {
        TeamManager manager = FTBTeamsAPI.api().getManager();
        Optional<Team> existing = manager.getTeamForPlayerID(actor.getUUID());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return manager.createPartyTeam(actor, "gametest-" + actor.getUUID(), "", Color4I.WHITE);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("gametest team creation failed", e);
        }
    }
}
