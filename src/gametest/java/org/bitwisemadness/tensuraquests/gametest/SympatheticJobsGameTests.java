package org.bitwisemadness.tensuraquests.gametest;

import dev.architectury.utils.Env;
import dev.fatsan.sympatheticjobs.api.JobId;
import dev.fatsan.sympatheticjobs.common.state.JobProgress;
import dev.fatsan.sympatheticjobs.common.state.JobsPlayerState;
import dev.fatsan.sympatheticjobs.common.state.JobsStateManager;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.tensuraquests.TensuraQuests;
import org.bitwisemadness.tensuraquests.compat.sympatheticjobs.SympatheticJobs;
import org.bitwisemadness.tensuraquests.framework.FtbQuestsBridge;
import org.bitwisemadness.tensuraquests.progress.ProgressAttachments;
import org.bitwisemadness.tensuraquests.tasks.JobMasteryTask;
import org.bitwisemadness.tensuraquests.tasks.JobParticipationTask;
import org.bitwisemadness.tensuraquests.tasks.JobRequirement;
import org.bitwisemadness.tensuraquests.tasks.JobTask;
import org.bitwisemadness.tensuraquests.tasks.MultiJobMasteryTask;

/**
 * GameTests for the Sympathetic Jobs job task family: registration
 * behind the version gate, the adapter read path, and FR-020
 * (Job Mastery), FR-021 (Multi-Job Mastery) and FR-022 (Job
 * Participation) driven through the exact
 * {@code submitTask(TeamData, ServerPlayer)} entry FTB Quests' tick
 * dispatch calls.
 *
 * <p><strong>Sympathetic Jobs contact (INT-002).</strong> This gametest
 * source set imports {@code dev.fatsan.sympatheticjobs} directly -
 * deliberately, and only here: seeding and verifying job state is the
 * harness's job. The INT-002 static check (JUnit) scans src/main only
 * and documents that exemption; production code touches Sympathetic
 * Jobs through the adapter package alone.</p>
 *
 * <p><strong>Gate premise (COMP-003).</strong> The gametest runtime
 * carries pinned Sympathetic Jobs 1.0.13 (build.gradle), so the gate
 * rules OK at boot and the family registers - the registration
 * assertions below are the OK-path integration proof of the gate;
 * the ABSENT and MISMATCH outcomes are pinned by
 * {@code SjVersionGateTest}.</p>
 *
 * <p>Job state seeding writes Sympathetic Jobs' own attachment
 * through its public state API: {@code addJob} then
 * {@code setProgress} (both are ownership-gated), mutating the live
 * state object {@code JobsStateManager.get} returns - no sync or
 * reconcile call, so no packets leave the harness.</p>
 */
@GameTestHolder(TensuraQuests.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SympatheticJobsGameTests {

    private SympatheticJobsGameTests() {
    }

    /**
     * Registration + hooks (FR-020..FR-022 support, COMP-003 OK
     * path): all three types exist under our namespace, instances
     * resolve their registered type, the adapter vocabulary is
     * available, and the polling/login hooks carry the values FTB
     * Quests' dispatch requires (interval &gt; 0 to enter the
     * auto-submit set; checkOnLogin keeping the default re-check so a
     * rejoining member re-converges their team's mirror).
     */
    @GameTest(template = "empty")
    public static void job_task_types_registered(GameTestHelper helper) {
        ResourceLocation[] ids = {
            FtbQuestsBridge.JOB_MASTERY_TASK_ID,
            FtbQuestsBridge.MULTI_JOB_MASTERY_TASK_ID,
            FtbQuestsBridge.JOB_PARTICIPATION_TASK_ID,
        };
        for (ResourceLocation id : ids) {
            Optional<TaskType> found = FtbQuestsBridge.findTaskType(id);
            helper.assertTrue(found.isPresent(), "job task type missing from the FTB Quests registry: " + id);
            helper.assertTrue(id.equals(found.get().getTypeId()),
                "registered type id is " + found.get().getTypeId() + ", expected " + id);
        }

        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        JobMasteryTask mastery = addMastery(fixture, 6701L, "miner", 3);
        Optional<TaskType> registered = FtbQuestsBridge.findTaskType(FtbQuestsBridge.JOB_MASTERY_TASK_ID);
        helper.assertTrue(mastery.getType() == registered.orElseThrow(),
            "getType() must return the registered instance");
        helper.assertTrue(mastery.autoSubmitOnPlayerTick() == JobTask.POLL_INTERVAL_TICKS
                && mastery.autoSubmitOnPlayerTick() > 0,
            "the auto-submit interval must be positive for FTB Quests to poll this task, got "
                + mastery.autoSubmitOnPlayerTick());
        helper.assertTrue(mastery.checkOnLogin(), "job tasks must keep the login re-check by default");

        List<String> vocabulary = SympatheticJobs.jobNames();
        helper.assertFalse(vocabulary.isEmpty(), "the adapter must expose Sympathetic Jobs' job vocabulary");
        helper.assertTrue(vocabulary.containsAll(List.of("miner", "farmer", "cook")),
            "unexpected job vocabulary: " + vocabulary);
        helper.succeed();
    }

    /**
     * FR-020: Job Mastery reads {@code JobsPlayerState} - the level in
     * the <em>configured</em> job while a different job is active
     * (XP flows to every held job, so the active job is irrelevant) -
     * and drives the personal counter to the qualifying level, which
     * mirrors and completes through the normal intake path.
     */
    @GameTest(template = "empty")
    public static void job_mastery_reads_state_to_completion(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        JobMasteryTask task = addMastery(fixture, 6721L, "miner", 3);
        ServerPlayer player = fixture.actor;
        TeamData team = fixture.team;

        // No job yet: evaluation is 0, nothing moves.
        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 0L,
            "a player holding no jobs must not progress job mastery, got " + team.getProgress(task));

        // A different job must be active while the configured job's level counts. Sympathetic Jobs
        // makes the first acquired job active (slot limits gate switching, but levels never consult
        // the active job - XP flows to every held job), so the decoy is seeded and the setup asserted.
        seedJobLevel(player, "farmer", 1);
        seedJobLevel(player, "miner", 2);
        JobsPlayerState state = JobsStateManager.get(player);
        helper.assertTrue(requireJob("farmer").equals(state.activeJob()),
            "the test needs a different active job to prove level reads are not active-job reads, active="
                + state.activeJob());

        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 2L,
            "progress must mirror the miner level while another job is active, got " + team.getProgress(task));
        helper.assertFalse(team.isCompleted(task), "level 2 of 3 must not complete the task");
        helper.assertTrue(ProgressAttachments.readCount(player, task.getId()) == 2L,
            "the player's personal counter must hold the evaluated level, got "
                + ProgressAttachments.readCount(player, task.getId()));

        // The configured job reaches the required level: complete.
        seedJobLevel(player, "miner", 3);
        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 3L,
            "progress must reach the required level, got " + team.getProgress(task));
        helper.assertTrue(team.isCompleted(task), "the required level must complete the task");
        helper.assertTrue(ProgressAttachments.readCount(player, task.getId()) == 3L,
            "the personal counter must equal the qualifying level at completion");

        // Converged: another evaluation changes nothing.
        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 3L,
            "a converged poll must not move progress, got " + team.getProgress(task));
        helper.succeed();
    }

    /**
     * FR-021: Multi-Job Mastery completes only when every configured
     * pair holds; partial satisfaction shows as the summed progress
     * toward the summed maximum.
     */
    @GameTest(template = "empty")
    public static void multi_job_mastery_requires_every_pair(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        MultiJobMasteryTask task = addMulti(fixture, 6731L,
            List.of(new JobRequirement("miner", 3), new JobRequirement("farmer", 2)));
        ServerPlayer player = fixture.actor;
        TeamData team = fixture.team;

        helper.assertTrue(task.getMaxProgress() == 5L,
            "the maximum must be the sum of the required levels, got " + task.getMaxProgress());

        // Only the first pair satisfied: progress = 3 of 5, still incomplete.
        seedJobLevel(player, "miner", 3);
        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 3L,
            "partial satisfaction must show as its share of the sum, got " + team.getProgress(task));
        helper.assertFalse(team.isCompleted(task),
            "one satisfied pair must not complete a multi job task");

        // Every pair satisfied: the sum reaches its maximum and completes.
        seedJobLevel(player, "farmer", 2);
        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 5L,
            "full satisfaction must reach the summed maximum, got " + team.getProgress(task));
        helper.assertTrue(team.isCompleted(task), "every pair held must complete the task");
        helper.assertTrue(ProgressAttachments.readCount(player, task.getId()) == 5L,
            "the personal counter must hold the summed evaluation, got "
                + ProgressAttachments.readCount(player, task.getId()));
        helper.succeed();
    }

    /**
     * FR-022: Job Participation completes the moment the player holds
     * the configured job - no level required - and stays at zero
     * without it.
     */
    @GameTest(template = "empty")
    public static void job_participation_requires_holding_the_job(GameTestHelper helper) {
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);
        JobParticipationTask task = addParticipation(fixture, 6741L, "cook");
        ServerPlayer player = fixture.actor;
        TeamData team = fixture.team;

        helper.assertTrue(task.getMaxProgress() == 1L,
            "participation must be a one-step task, got " + task.getMaxProgress());
        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 0L,
            "holding no jobs must not progress participation, got " + team.getProgress(task));
        helper.assertFalse(team.isCompleted(task), "participation must not complete without the job");

        seedJobHold(player, "cook");
        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 1L,
            "acquiring the job must complete participation, got " + team.getProgress(task));
        helper.assertTrue(team.isCompleted(task), "holding the job must complete the task");
        helper.assertTrue(ProgressAttachments.readCount(player, task.getId()) == 1L,
            "the personal counter must hold the participation flag");

        // Idempotent: holding the job longer changes nothing.
        task.submitTask(team, player);
        helper.assertTrue(team.getProgress(task) == 1L,
            "a converged participation poll must not move progress");
        helper.succeed();
    }

    /**
     * Config codecs for all three types: SNBT and net round-trips
     * plus additive defaults for saves predating each field, and the
     * multi task's malformed-entry drop.
     */
    @GameTest(template = "empty")
    public static void job_task_config_round_trips(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        RegistryAccess wire = helper.getLevel().registryAccess();
        QuestFixture fixture = QuestFixture.create(helper, Env.SERVER);

        // Job Mastery: SNBT round-trip and additive defaults.
        JobMasteryTask masterySource = addMastery(fixture, 6751L, "farmer", 7);
        CompoundTag masteryTag = new CompoundTag();
        masterySource.writeData(masteryTag, registries);
        JobMasteryTask masteryCopy = addMastery(fixture, 6752L, "miner", 1);
        masteryCopy.readData(masteryTag, registries);
        helper.assertTrue("farmer".equals(masteryCopy.getJob()),
            "SNBT round-trip lost the job: " + masteryCopy.getJob());
        helper.assertTrue(masteryCopy.getRequiredLevel() == 7,
            "SNBT round-trip lost the required level: " + masteryCopy.getRequiredLevel());
        JobMasteryTask masteryLegacy = addMastery(fixture, 6753L, "cook", 9);
        masteryLegacy.readData(new CompoundTag(), registries);
        helper.assertTrue(JobMasteryTask.DEFAULT_JOB.equals(masteryLegacy.getJob())
                && masteryLegacy.getRequiredLevel() == JobMasteryTask.DEFAULT_REQUIRED_LEVEL,
            "a save without the job fields must fall back to the defaults, got "
                + masteryLegacy.getJob() + "/" + masteryLegacy.getRequiredLevel());

        // Job Participation: net round-trip and additive default.
        JobParticipationTask participationSource = addParticipation(fixture, 6754L, "cook");
        ByteBuf participationRaw = Unpooled.buffer();
        participationSource.writeNetData(new RegistryFriendlyByteBuf(participationRaw, wire));
        participationRaw.readerIndex(0);
        JobParticipationTask participationCopy = addParticipation(fixture, 6755L, "miner");
        participationCopy.readNetData(new RegistryFriendlyByteBuf(participationRaw, wire));
        helper.assertTrue("cook".equals(participationCopy.getJob()),
            "net round-trip lost the job: " + participationCopy.getJob());
        JobParticipationTask participationLegacy = addParticipation(fixture, 6756L, "shaman");
        participationLegacy.readData(new CompoundTag(), registries);
        helper.assertTrue(JobParticipationTask.DEFAULT_JOB.equals(participationLegacy.getJob()),
            "a save without the job field must fall back to the default, got " + participationLegacy.getJob());

        // Multi-Job Mastery: SNBT round-trip, empty-save threshold, malformed drop, net round-trip.
        MultiJobMasteryTask multiSource = addMulti(fixture, 6757L,
            List.of(new JobRequirement("miner", 3), new JobRequirement("farmer", 2)));
        CompoundTag multiTag = new CompoundTag();
        multiSource.writeData(multiTag, registries);
        MultiJobMasteryTask multiCopy = addMulti(fixture, 6758L, List.of());
        multiCopy.readData(multiTag, registries);
        helper.assertTrue(multiCopy.getRequirements().equals(multiSource.getRequirements()),
            "SNBT round-trip lost the requirements: " + multiCopy.getRequirements());

        MultiJobMasteryTask multiLegacy = addMulti(fixture, 6759L, List.of());
        multiLegacy.readData(new CompoundTag(), registries);
        helper.assertTrue(multiLegacy.getRequirements().isEmpty(),
            "a save without the requirements field must fall back to none");
        helper.assertTrue(multiLegacy.getMaxProgress() == MultiJobMasteryTask.MIN_MAX_PROGRESS,
            "an empty multi task must keep a real threshold rather than complete for free, got "
                + multiLegacy.getMaxProgress());

        CompoundTag withGarbage = new CompoundTag();
        ListTag entries = new ListTag();
        entries.add(StringTag.valueOf("garbage"));
        entries.add(StringTag.valueOf("miner:0"));
        entries.add(StringTag.valueOf("MINER:5"));
        withGarbage.put("requirements", entries);
        MultiJobMasteryTask multiSurvivor = addMulti(fixture, 6760L, List.of());
        multiSurvivor.readData(withGarbage, registries);
        helper.assertTrue(multiSurvivor.getRequirements().equals(List.of(new JobRequirement("miner", 5))),
            "malformed entries must drop while valid ones survive, got "
                + multiSurvivor.getRequirements());

        MultiJobMasteryTask multiNet = addMulti(fixture, 6761L,
            List.of(new JobRequirement("cook", 4)));
        ByteBuf multiRaw = Unpooled.buffer();
        multiNet.writeNetData(new RegistryFriendlyByteBuf(multiRaw, wire));
        multiRaw.readerIndex(0);
        MultiJobMasteryTask multiNetCopy = addMulti(fixture, 6762L, List.of());
        multiNetCopy.readNetData(new RegistryFriendlyByteBuf(multiRaw, wire));
        helper.assertTrue(multiNetCopy.getRequirements().equals(multiNet.getRequirements()),
            "net round-trip lost the requirements: " + multiNetCopy.getRequirements());
        helper.succeed();
    }

    // --- helpers -------------------------------------------------------------

    private static JobId requireJob(String name) {
        return JobId.byId(name).orElseThrow(() -> new IllegalStateException("unknown test job " + name));
    }

    /**
     * Seed a job level in Sympathetic Jobs' own state: ownership
     * first ({@code level} reads 0 for jobs the player does not
     * hold), then the level. The state object returned by
     * {@code JobsStateManager.get} <em>is</em> the attachment value,
     * so mutating it in place is the write; no sync call is made.
     */
    private static void seedJobLevel(ServerPlayer player, String jobName, int level) {
        JobsPlayerState state = JobsStateManager.get(player);
        JobId job = requireJob(jobName);
        boolean owns = state.hasJob(job) || state.addJob(job);
        if (!owns) {
            throw new IllegalStateException("test seeding could not grant job " + jobName
                + " - held jobs: " + state.ownedJobs());
        }
        state.setProgress(job, new JobProgress(level, 0.0));
    }

    /** Seed job ownership only (FR-022), same live-state write. */
    private static void seedJobHold(ServerPlayer player, String jobName) {
        JobsPlayerState state = JobsStateManager.get(player);
        JobId job = requireJob(jobName);
        if (!state.hasJob(job) && !state.addJob(job)) {
            throw new IllegalStateException("test seeding could not grant job " + jobName);
        }
    }

    private static JobMasteryTask addMastery(QuestFixture fixture, long id, String job, int level) {
        JobMasteryTask task = new JobMasteryTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setJob(job);
        task.setRequiredLevel(level);
        return task;
    }

    private static JobParticipationTask addParticipation(QuestFixture fixture, long id, String job) {
        JobParticipationTask task = new JobParticipationTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setJob(job);
        return task;
    }

    private static MultiJobMasteryTask addMulti(QuestFixture fixture, long id, List<JobRequirement> requirements) {
        MultiJobMasteryTask task = new MultiJobMasteryTask(id, fixture.quest);
        fixture.quest.addTask(task);
        task.setRequirements(requirements);
        return task;
    }
}
