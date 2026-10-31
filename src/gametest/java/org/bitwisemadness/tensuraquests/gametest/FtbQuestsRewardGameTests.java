package org.bitwisemadness.tensuraquests.gametest;

import dev.architectury.utils.Env;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.framework.TensuraReward;
import org.bitwisemadness.tensuraquests.rewards.MessageReward;

/**
 * GameTests for the custom reward lifecycle: registration (FR-006),
 * SNBT and net round-trips (FR-002, FR-003), server-side claim
 * execution (FR-006), the loud-failure path with claim release and
 * retry (FR-007), duplicate-claim protection (SEC-002) and the forced
 * per-player team scope.
 *
 * <p>Claims go through {@link TeamData#claimReward}, the same server
 * path the claim packet handler uses after its completion check, on an
 * isolated quest file so no test mutates the run's real file. Keep the
 * {@code expectedTests} count in {@code build.gradle} in sync with the
 * total {@code @GameTest} count across all suites (TEST-001).</p>
 */
@GameTestHolder(TensuraQuests.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FtbQuestsRewardGameTests {

    private static final long CHAPTER_ID = 7001L;
    private static final long QUEST_ID = 7002L;
    private static final long REWARD_ID = 7101L;

    private FtbQuestsRewardGameTests() {
    }

    /** FR-006/INT-003: the reward type registers via the bridge only. */
    @GameTest(template = "empty")
    public static void message_reward_type_registered(GameTestHelper helper) {
        Optional<RewardType> found = FtbQuestsBridge.findRewardType(FtbQuestsBridge.MESSAGE_REWARD_ID);
        helper.assertTrue(found.isPresent(), "message reward type missing from the FTB Quests registry");
        RewardType type = found.get();
        helper.assertTrue(FtbQuestsBridge.MESSAGE_REWARD_ID.equals(type.getTypeId()),
            "registered type id is " + type.getTypeId() + ", expected " + FtbQuestsBridge.MESSAGE_REWARD_ID);

        Fixture fixture = newFixture(helper);
        MessageReward reward = fixture.addMessageReward(REWARD_ID);
        helper.assertTrue(reward.getType() == type, "getType() must return the registered instance");
        helper.succeed();
    }

    /** FR-002: writeData -> readData preserves the message; absent field stays additive. */
    @GameTest(template = "empty")
    public static void message_reward_snbt_round_trip(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Fixture fixture = newFixture(helper);
        MessageReward source = fixture.addMessageReward(7201L);
        source.setMessage("Welcome to the dungeon!");

        CompoundTag tag = new CompoundTag();
        source.writeData(tag, registries);
        MessageReward copy = fixture.addMessageReward(7202L);
        copy.readData(tag, registries);
        helper.assertTrue("Welcome to the dungeon!".equals(copy.getMessage()),
            "SNBT round-trip lost the message: " + copy.getMessage());

        MessageReward legacy = fixture.addMessageReward(7203L);
        legacy.setMessage("stale");
        legacy.readData(new CompoundTag(), registries);
        helper.assertTrue(MessageReward.DEFAULT_MESSAGE.equals(legacy.getMessage()),
            "a save without the message field must fall back to the default, got " + legacy.getMessage());
        helper.succeed();
    }

    /** FR-003: writeNetData -> readNetData preserves the message. */
    @GameTest(template = "empty")
    public static void message_reward_net_round_trip(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Fixture fixture = newFixture(helper);
        MessageReward source = fixture.addMessageReward(7301L);
        source.setMessage("Net synced text");

        ByteBuf raw = Unpooled.buffer();
        RegistryFriendlyByteBuf outgoing = new RegistryFriendlyByteBuf(raw, registries);
        source.writeNetData(outgoing);
        raw.readerIndex(0);
        RegistryFriendlyByteBuf incoming = new RegistryFriendlyByteBuf(raw, registries);
        MessageReward copy = fixture.addMessageReward(7302L);
        copy.readNetData(incoming);
        helper.assertTrue("Net synced text".equals(copy.getMessage()),
            "net round-trip lost the message: " + copy.getMessage());
        helper.succeed();
    }

    /** FR-006: a claim executes server-side, marks the reward claimed and logs with ids. */
    @GameTest(template = "empty")
    public static void claim_grants_and_marks(GameTestHelper helper) {
        Fixture fixture = newFixture(helper);
        fixture.completeQuest();
        MessageReward reward = fixture.addMessageReward(REWARD_ID);
        reward.setMessage("A voice echoes: well done.");
        TeamData team = fixture.team;
        ServerPlayer player = MockPlayers.create(helper);
        helper.assertTrue(team.isCompleted(fixture.quest),
            "fixture quest must be completed before the claim path runs");

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            helper.assertFalse(team.isRewardClaimed(player.getUUID(), reward),
                "reward must start unclaimed");
            team.claimReward(player, reward, false);
            helper.assertTrue(team.isRewardClaimed(player.getUUID(), reward),
                "a successful claim must be recorded");
            helper.assertTrue(capture.hasMessage("claim GRANTED", "integration=tensura_quests",
                    "reward=" + REWARD_ID, "quest=" + QUEST_ID, "player=" + player.getUUID()),
                "granted claim not logged with integration and ids");
            helper.succeed();
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
    }

    /**
     * FR-007: a failed execution logs the reason, grants nothing, and
     * releases the claim so the reward can be claimed again once the
     * cause clears - never a silent, burned reward.
     */
    @GameTest(template = "empty")
    public static void failed_claim_logs_releases_and_stays_claimable(GameTestHelper helper) {
        Fixture fixture = newFixture(helper);
        fixture.completeQuest();
        MessageReward reward = fixture.addMessageReward(REWARD_ID);
        reward.setMessage("");  // blank: a real misconfiguration
        TeamData team = fixture.team;
        ServerPlayer player = MockPlayers.create(helper);

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            team.claimReward(player, reward, false);
            helper.assertFalse(team.isRewardClaimed(player.getUUID(), reward),
                "a failed claim must not stay marked as granted");
            helper.assertTrue(capture.hasMessage("claim FAILED", "reason=message is blank",
                    "reward=" + REWARD_ID, "player=" + player.getUUID()),
                "failed claim not logged with reason and ids");

            // The cause clears; the same player can still claim it.
            reward.setMessage("Fixed configuration");
            team.claimReward(player, reward, false);
            helper.assertTrue(team.isRewardClaimed(player.getUUID(), reward),
                "a reward must be claimable again after a failed claim");
            helper.assertTrue(capture.hasMessage("claim GRANTED", "reward=" + REWARD_ID),
                "the retry must be logged as granted");
            helper.succeed();
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
    }

    /** SEC-002: a second claim of the same per-player reward does not re-execute. */
    @GameTest(template = "empty")
    public static void duplicate_claim_does_not_reexecute(GameTestHelper helper) {
        Fixture fixture = newFixture(helper);
        fixture.completeQuest();
        MessageReward reward = fixture.addMessageReward(REWARD_ID);
        reward.setMessage("Only once");
        TeamData team = fixture.team;
        ServerPlayer player = MockPlayers.create(helper);

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            team.claimReward(player, reward, false);
            helper.assertTrue(team.isRewardClaimed(player.getUUID(), reward),
                "first claim must be recorded");
            var claimTime = team.getRewardClaimTime(player.getUUID(), reward);
            helper.assertTrue(claimTime.isPresent(), "claim time must be recorded");

            team.claimReward(player, reward, false);
            helper.assertTrue(team.getRewardClaimTime(player.getUUID(), reward).equals(claimTime),
                "a duplicate claim must not update the claim time");
            helper.assertTrue(capture.countMessages("claim GRANTED", "reward=" + REWARD_ID) == 1,
                "a duplicate claim must not execute the effect again");
            helper.succeed();
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
    }

    /**
     * Per-player team scope is forced at the persistence boundary and
     * locked in the editor: a save explicitly asking for
     * {@code team_reward=true} normalises to personal on load, the
     * forced value is what gets persisted, and the team control is not
     * editable. A freshly constructed instance holds the upstream
     * DEFAULT (the private field has no setter) until that first
     * normalisation, which is what the save/load assertions cover.
     *
     * <p>The editor half runs through
     * {@link TensuraReward#lockTeamScopeControl} instead of
     * {@code fillConfigGroup}: upstream marks that method
     * {@code @OnlyIn(Dist.CLIENT)} - our overrides match - so this
     * dedicated server strips it, and only the lock's own config work
     * (not the client-only population around it) can execute here. The
     * call wiring inside {@code fillConfigGroup} is therefore reviewed,
     * not run; the closing hook assertions pin our side of the
     * contract.</p>
     */
    @GameTest(template = "empty")
    public static void per_player_scope_is_forced(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestQuestFile file = new TestQuestFile(Env.SERVER, registries, true);
        ChapterGroup group = file.getDefaultChapterGroup();
        Chapter chapter = new Chapter(CHAPTER_ID, file, group);
        group.addChapter(chapter);
        Quest quest = new Quest(QUEST_ID, chapter);
        chapter.addQuest(quest);
        UUID teamId = UUID.randomUUID();
        file.getOrCreateTeamData(teamId);

        // An explicit team_reward=true in a save must normalise to per-player.
        CompoundTag asked = new CompoundTag();
        asked.putBoolean("team_reward", true);
        asked.putString("message", "personal");
        MessageReward loaded = new MessageReward(7402L, quest);
        quest.addReward(loaded);
        loaded.readData(asked, registries);
        helper.assertFalse(loaded.isTeamReward(),
            "team_reward=true must be forced to per-player on load even when the file default is team-shared");

        // The forced value is what the save emits.
        CompoundTag saved = new CompoundTag();
        loaded.writeData(saved, registries);
        helper.assertTrue(saved.contains("team_reward"),
            "writeData must persist the forced per-player scope explicitly");
        helper.assertFalse(saved.getBoolean("team_reward"),
            "writeData must persist team_reward=false, got " + saved.getBoolean("team_reward"));

        // The editor's team control is locked: the scope is not the
        // quest author's to change. The editor path that populates this
        // group (fillConfigGroup) is @OnlyIn(Dist.CLIENT) and stripped
        // here, so the group is seeded the way upstream populates it and
        // the lock itself - dist-agnostic config work - runs directly.
        ConfigGroup config = new ConfigGroup("test_reward");
        ConfigValue<Boolean> teamControl = config
            .addBool("team", true, value -> {}, true)
            .setNameKey("ftbquests.reward.team_reward");
        ConfigValue<Boolean> otherControl = config
            .addBool("autoclaim", false, value -> {}, false)
            .setNameKey("ftbquests.reward.autoclaim");
        helper.assertTrue(teamControl.getCanEdit(),
            "a fresh editor control must start editable");

        MessageReward editable = new MessageReward(7403L, quest);
        quest.addReward(editable);
        editable.lockTeamScopeControl(config);
        helper.assertFalse(teamControl.getCanEdit(),
            "the team-scope editor control must be locked");
        helper.assertTrue(otherControl.getCanEdit(),
            "only the team-scope editor control may be locked");

        // Our fillConfigGroup overrides must carry @OnlyIn(CLIENT) like
        // every upstream one; a hook still present here means the
        // annotation was lost and a server-side call would die at the
        // stripped super - the failure this suite once chased.
        assertClientOnlyEditorHook(helper, TensuraReward.class);
        assertClientOnlyEditorHook(helper, MessageReward.class);
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

        private MessageReward addMessageReward(long id) {
            MessageReward reward = new MessageReward(id, quest);
            quest.addReward(reward);
            return reward;
        }

        /**
         * Mark the quest completed for the team through the upstream
         * mechanism, mirroring what task completion does in production:
         * the claim packet handler refuses rewards on incomplete quests,
         * so every claim test starts from a completed one.
         */
        private void completeQuest() {
            team.setCompleted(QUEST_ID, new Date());
        }
    }

    private static Fixture newFixture(GameTestHelper helper) {
        TestQuestFile file = new TestQuestFile(Env.SERVER, helper.getLevel().registryAccess());
        ChapterGroup group = file.getDefaultChapterGroup();
        Chapter chapter = new Chapter(CHAPTER_ID, file, group);
        group.addChapter(chapter);
        Quest quest = new Quest(QUEST_ID, chapter);
        chapter.addQuest(quest);
        TeamData team = file.getOrCreateTeamData(UUID.randomUUID());
        return new Fixture(file, quest, team);
    }

    /**
     * The editor hook must be client-only: either stripped from this
     * dedicated server (the normal case - {@code @OnlyIn(Dist.CLIENT)}
     * did its job) or present <em>and</em> carrying that annotation.
     * A present, unannotated hook is the defect this guards: it would
     * run on a server and die at the stripped upstream super with the
     * NoSuchMethodError this suite once chased.
     *
     * @param helper the GameTest helper to report through
     * @param cls the reward class whose {@code fillConfigGroup} to check
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
