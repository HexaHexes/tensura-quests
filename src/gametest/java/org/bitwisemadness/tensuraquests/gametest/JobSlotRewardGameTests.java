package org.bitwisemadness.tensuraquests.gametest;

import dev.architectury.utils.Env;
import dev.fatsan.sympatheticjobs.common.state.JobsStateManager;
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
import java.util.OptionalInt;
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
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SjPresence;
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SjVersionGate;
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SympatheticJobs;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.rewards.UnlockJobSlotReward;

/**
 * GameTests for the Unlock Job Slot reward: registration behind the
 * Sympathetic Jobs version gate, FR-023 (grant raises
 * {@code effectiveSlotLimit} exactly by the configured amount and
 * lives in Sympathetic Jobs' own override/attachment), SEC-002
 * (single-shot per player), per-player isolation (a teammate's claim
 * never grants your slots), FR-024 (a forced off-pin version blocks
 * the write path with a logged failure and no state change), the
 * FR-007 discipline on a clamped grant (nothing granted, claim
 * released), and config round-trips.
 *
 * <p>Claims go through {@link TeamData#claimReward}, the same server
 * path the claim packet handler uses, on an isolated quest file.
 * Restart persistence of the override is Sympathetic Jobs' own
 * attachment behaviour (manual Phase 7 matrix); the override's
 * presence after the grant is what this suite pins here.</p>
 */
@GameTestHolder(TensuraQuests.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobSlotRewardGameTests {

    private static final long CHAPTER_ID = 7501L;
    private static final long QUEST_ID = 7502L;
    private static final long REWARD_ID = 7601L;

    private JobSlotRewardGameTests() {
    }

    /**
     * Registration behind the family gate (INT-004/COMP-003): the
     * reward type exists because the gate ruled OK at boot - asserted
     * here as the environment premise this whole suite rests on.
     */
    @GameTest(template = "empty")
    public static void job_slot_reward_type_registered(GameTestHelper helper) {
        Optional<RewardType> found = FtbQuestsBridge.findRewardType(FtbQuestsBridge.UNLOCK_JOB_SLOT_REWARD_ID);
        helper.assertTrue(found.isPresent(), "unlock job slot reward type missing from the FTB Quests registry");
        RewardType type = found.get();
        helper.assertTrue(FtbQuestsBridge.UNLOCK_JOB_SLOT_REWARD_ID.equals(type.getTypeId()),
            "registered type id is " + type.getTypeId() + ", expected " + FtbQuestsBridge.UNLOCK_JOB_SLOT_REWARD_ID);

        helper.assertTrue(SjVersionGate.evaluate(SjVersionGate.PINNED_VERSION,
                SjPresence.installedVersion().orElse(null)).usable(),
            "the gametest runtime must ship the pinned Sympathetic Jobs for the family to register");
        Fixture fixture = newFixture(helper);
        UnlockJobSlotReward reward = fixture.addUnlockReward(REWARD_ID);
        helper.assertTrue(reward.getType() == type, "getType() must return the registered instance");
        helper.succeed();
    }

    /** FR-023: the claim raises the effective limit by exactly the configured amount, verified and marked. */
    @GameTest(template = "empty")
    public static void claim_grants_and_marks(GameTestHelper helper) {
        Fixture fixture = newFixture(helper);
        fixture.completeQuest();
        UnlockJobSlotReward reward = fixture.addUnlockReward(REWARD_ID);
        reward.setSlots(2);
        TeamData team = fixture.team;
        ServerPlayer player = MockPlayers.create(helper);
        int before = JobsStateManager.effectiveSlotLimit(player);

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            helper.assertFalse(team.isRewardClaimed(player.getUUID(), reward), "reward must start unclaimed");
            team.claimReward(player, reward, false);

            helper.assertTrue(team.isRewardClaimed(player.getUUID(), reward), "a successful claim must be recorded");
            helper.assertTrue(JobsStateManager.effectiveSlotLimit(player) == before + 2,
                "the effective slot limit must rise by exactly the configured amount: " + before + " -> "
                    + JobsStateManager.effectiveSlotLimit(player));
            helper.assertTrue(JobsStateManager.get(player).slotLimitOverride().isPresent()
                    && JobsStateManager.get(player).slotLimitOverride().getAsInt() == before + 2,
                "the entitlement must live in Sympathetic Jobs' own override (its attachment persists it)");
            helper.assertTrue(capture.hasMessage("claim GRANTED", "integration=sympathetic_jobs",
                    "reward=" + REWARD_ID, "player=" + player.getUUID()),
                "granted claim not logged with the job integration and ids");
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
        helper.succeed();
    }

    /** SEC-002: a second claim of the same reward does not grant again. */
    @GameTest(template = "empty")
    public static void duplicate_claim_does_not_double_grant(GameTestHelper helper) {
        Fixture fixture = newFixture(helper);
        fixture.completeQuest();
        UnlockJobSlotReward reward = fixture.addUnlockReward(REWARD_ID);
        reward.setSlots(1);
        TeamData team = fixture.team;
        ServerPlayer player = MockPlayers.create(helper);
        int before = JobsStateManager.effectiveSlotLimit(player);

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            team.claimReward(player, reward, false);
            var claimTime = team.getRewardClaimTime(player.getUUID(), reward);
            helper.assertTrue(claimTime.isPresent(), "claim time must be recorded");

            team.claimReward(player, reward, false);
            helper.assertTrue(team.getRewardClaimTime(player.getUUID(), reward).equals(claimTime),
                "a duplicate claim must not update the claim time");
            helper.assertTrue(capture.countMessages("claim GRANTED", "reward=" + REWARD_ID) == 1,
                "a duplicate claim must not execute the effect again");
            helper.assertTrue(JobsStateManager.effectiveSlotLimit(player) == before + 1,
                "the limit must rise once, not twice: " + JobsStateManager.effectiveSlotLimit(player));
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
        helper.succeed();
    }

    /** Per-player scope: each claim grants only its own claimer. */
    @GameTest(template = "empty")
    public static void per_player_claims_grant_only_the_claimer(GameTestHelper helper) {
        Fixture fixture = newFixture(helper);
        fixture.completeQuest();
        UnlockJobSlotReward reward = fixture.addUnlockReward(REWARD_ID);
        reward.setSlots(1);
        TeamData team = fixture.team;
        ServerPlayer claimerA = MockPlayers.create(helper);
        ServerPlayer claimerB = MockPlayers.create(helper);
        int beforeA = JobsStateManager.effectiveSlotLimit(claimerA);
        int beforeB = JobsStateManager.effectiveSlotLimit(claimerB);

        team.claimReward(claimerA, reward, false);
        helper.assertTrue(JobsStateManager.effectiveSlotLimit(claimerA) == beforeA + 1,
            "the claimer must receive their slots");
        helper.assertTrue(JobsStateManager.effectiveSlotLimit(claimerB) == beforeB,
            "a teammate's claim must never grant your slots (got "
                + JobsStateManager.effectiveSlotLimit(claimerB) + ", expected " + beforeB + ")");
        helper.assertFalse(team.isRewardClaimed(claimerB.getUUID(), reward),
            "the teammate must not be marked as having claimed");

        team.claimReward(claimerB, reward, false);
        helper.assertTrue(JobsStateManager.effectiveSlotLimit(claimerB) == beforeB + 1,
            "the second claimer receives their own slots");
        helper.assertTrue(JobsStateManager.effectiveSlotLimit(claimerA) == beforeA + 1,
            "the first claimer's limit is untouched by the second claim");
        helper.succeed();
    }

    /**
     * FR-024: a forced off-pin installed version blocks the write
     * path - logged failure, empty result, zero state change - through
     * the adapter's per-call test seam (no global state for the
     * parallel batch to race on). The reward's mapping of that empty
     * result onto a released claim is exercised by the clamp test.
     */
    @GameTest(template = "empty")
    public static void forced_version_gate_failure_grants_nothing(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.create(helper);
        int before = JobsStateManager.effectiveSlotLimit(player);

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            OptionalInt result = SympatheticJobs.grantJobSlots(player, 3, "9.9.9");
            helper.assertTrue(result.isEmpty(), "an off-pin version must refuse the grant");
            helper.assertTrue(JobsStateManager.effectiveSlotLimit(player) == before,
                "a refused grant must not touch the entitlement, got "
                    + JobsStateManager.effectiveSlotLimit(player) + ", expected " + before);
            helper.assertTrue(capture.hasMessage("job slot grant failed", "reason=version_gate",
                    "9.9.9", SjVersionGate.PINNED_VERSION),
                "the blocked grant must log both versions (FR-024)");
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
        helper.succeed();
    }

    /**
     * FR-007 + the entitlement clamp: a raise past Sympathetic Jobs'
     * total-job cap cannot be silently partial - the adapter restores
     * the previous entitlement exactly, the claim fails loudly with
     * the adapter's reason, and FTB Quests releases the claim mark so
     * nothing was granted and the reward stays claimable.
     */
    @GameTest(template = "empty")
    public static void capped_grant_fails_loudly_and_releases_the_claim(GameTestHelper helper) {
        Fixture fixture = newFixture(helper);
        fixture.completeQuest();
        UnlockJobSlotReward reward = fixture.addUnlockReward(REWARD_ID);
        reward.setSlots(3); // 12 (cap) + 3 would have to clamp
        TeamData team = fixture.team;
        ServerPlayer player = MockPlayers.create(helper);
        JobsStateManager.get(player).setSlotLimitOverride(UnlockJobSlotReward.MAX_SLOTS);
        helper.assertTrue(JobsStateManager.effectiveSlotLimit(player) == UnlockJobSlotReward.MAX_SLOTS,
            "the test must start at the cap");

        CapturingAppender capture = new CapturingAppender();
        Logger coreLogger = (Logger) LogManager.getLogger(TensuraQuests.class.getName());
        coreLogger.addAppender(capture);
        capture.start();
        try {
            team.claimReward(player, reward, false);
            helper.assertFalse(team.isRewardClaimed(player.getUUID(), reward),
                "a failed grant must not stay marked as claimed (FR-007)");
            helper.assertTrue(JobsStateManager.effectiveSlotLimit(player) == UnlockJobSlotReward.MAX_SLOTS,
                "the failed grant must leave the entitlement exactly as it was, got "
                    + JobsStateManager.effectiveSlotLimit(player));
            helper.assertTrue(capture.hasMessage("claim FAILED", "integration=sympathetic_jobs",
                    "reward=" + REWARD_ID), "the failed claim must log with integration and ids");
            helper.assertTrue(capture.hasMessage("reason=slot_limit_clamped",
                    "before=12", "requested=15", "after=12"),
                "the adapter must log the clamp with the full arithmetic");
        } finally {
            capture.stop();
            coreLogger.removeAppender(capture);
        }
        helper.succeed();
    }

    /**
     * Config codecs plus the editor-hook discipline: SNBT and net
     * round-trips, additive defaults, clamping of stored values, and
     * the {@code @OnlyIn(Dist.CLIENT)} annotation every
     * {@code fillConfigGroup} override in this hierarchy must carry
     * (the #9 defect class).
     */
    @GameTest(template = "empty")
    public static void job_slot_reward_config_round_trips(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Fixture fixture = newFixture(helper);

        UnlockJobSlotReward source = fixture.addUnlockReward(7602L);
        source.setSlots(5);
        CompoundTag tag = new CompoundTag();
        source.writeData(tag, registries);
        UnlockJobSlotReward copy = fixture.addUnlockReward(7603L);
        copy.readData(tag, registries);
        helper.assertTrue(copy.getSlots() == 5, "SNBT round-trip lost the slots: " + copy.getSlots());

        UnlockJobSlotReward legacy = fixture.addUnlockReward(7604L);
        legacy.setSlots(9);
        legacy.readData(new CompoundTag(), registries);
        helper.assertTrue(UnlockJobSlotReward.DEFAULT_SLOTS == legacy.getSlots(),
            "a save without the field must fall back to the default, got " + legacy.getSlots());

        CompoundTag outOfRange = new CompoundTag();
        outOfRange.putInt("slots", 99);
        UnlockJobSlotReward clamped = fixture.addUnlockReward(7605L);
        clamped.readData(outOfRange, registries);
        helper.assertTrue(clamped.getSlots() == UnlockJobSlotReward.MAX_SLOTS,
            "an out-of-range stored grant must clamp to the cap, got " + clamped.getSlots());

        UnlockJobSlotReward netSource = fixture.addUnlockReward(7606L);
        netSource.setSlots(7);
        ByteBuf raw = Unpooled.buffer();
        netSource.writeNetData(new RegistryFriendlyByteBuf(raw, registries));
        raw.readerIndex(0);
        UnlockJobSlotReward netCopy = fixture.addUnlockReward(7607L);
        netCopy.readNetData(new RegistryFriendlyByteBuf(raw, registries));
        helper.assertTrue(netCopy.getSlots() == 7, "net round-trip lost the slots: " + netCopy.getSlots());

        assertClientOnlyEditorHook(helper, UnlockJobSlotReward.class);
        helper.succeed();
    }

    // --- helpers -------------------------------------------------------------

    /** One quest file with a chapter, quest and team - reward-suite shape. */
    private static final class Fixture {

        private final TestQuestFile file;
        private final Quest quest;
        private final TeamData team;

        private Fixture(TestQuestFile file, Quest quest, TeamData team) {
            this.file = file;
            this.quest = quest;
            this.team = team;
        }

        private UnlockJobSlotReward addUnlockReward(long id) {
            UnlockJobSlotReward reward = new UnlockJobSlotReward(id, quest);
            quest.addReward(reward);
            return reward;
        }

        /** Complete the quest upstream-style so the claim path accepts rewards. */
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
