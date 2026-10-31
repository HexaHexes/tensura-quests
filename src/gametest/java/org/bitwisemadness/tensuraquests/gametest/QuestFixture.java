package org.bitwisemadness.tensuraquests.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.utils.Env;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.data.AbstractTeam;
import dev.ftb.mods.ftbteams.data.AbstractTeamBase;
import dev.ftb.mods.ftbteams.data.PlayerTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraquests.tasks.CounterTask;

/**
 * One isolated quest file with a chapter, a quest, a <em>real</em>
 * FTB Teams team and an acting member - the shared GameTest fixture
 * (package-private helper, like {@code TestQuestFile} and
 * {@code MockPlayers}).
 *
 * <p>Every test builds its own {@link TestQuestFile} subclass, so no
 * test mutates the run's real quest file. The fixture also registers
 * a real FTB Teams team for its players: the policy mirror resolves
 * membership through {@code TeamManager.getTeamByID}, and a synthetic
 * team UUID would mirror nothing (the same reason
 * {@code TeamData.getOnlineMembers} degrades to an empty list for
 * synthetic ids) - gametest runs therefore accumulate test teams in
 * FTB Teams' saved state.</p>
 */
final class QuestFixture {

    private static final long CHAPTER_ID = 6001L;
    private static final long QUEST_ID = 6002L;

    final TestQuestFile file;
    final Quest quest;
    final TeamData team;
    final ServerPlayer actor;

    private QuestFixture(TestQuestFile file, Quest quest, TeamData team, ServerPlayer actor) {
        this.file = file;
        this.quest = quest;
        this.team = team;
        this.actor = actor;
    }

    /**
     * Build the fixture: quest file, chapter, quest, an acting mock
     * player (through {@link MockPlayers}, so modded payload sends
     * reach them) and that player's team.
     *
     * @param helper the gametest helper to build against
     * @param side the quest file's side (server fixtures accept
     *             intake; client fixtures prove the server-side gate)
     * @return the fresh fixture
     */
    static QuestFixture create(GameTestHelper helper, Env side) {
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
        return new QuestFixture(file, quest, team, actor);
    }

    /**
     * A configured counter task attached to the fixture's quest -
     * the framework suite's workhorse.
     *
     * @param id the quest-file unique object id
     * @param target the completion threshold
     * @return the attached task
     */
    CounterTask addCounterTask(long id, long target) {
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
     * <p>Beyond the forward ranks, this also aligns the <em>reverse</em>
     * resolution FTB Teams maintains at login:
     * {@code getTeamForPlayerID} reads {@code knownPlayers ->
     * PlayerTeam.effectiveTeam}, which a raw ranks-only add leaves
     * pointing at the member's own personal team. The team-trade
     * policy (FR-044) compares <em>resolved</em> teams, so the
     * fixture must resolve like a real join would - through the two
     * public impl accessors, no reflection.</p>
     *
     * @param member the player to add as a full member
     */
    void addMember(ServerPlayer member) {
        AbstractTeamBase team = teamImpl();
        team.addMember(member.getUUID(), TeamRank.MEMBER);
        if (FTBTeamsAPI.api().getManager() instanceof TeamManagerImpl manager) {
            PlayerTeam personal = manager.getPersonalTeamForPlayerID(member.getUUID());
            if (personal != null && team instanceof AbstractTeam effective) {
                personal.setEffectiveTeam(effective);
            }
        }
    }

    /**
     * Drop a player from the fixture's team (SEC-005 churn).
     *
     * @param member the player to remove
     */
    void removeMember(ServerPlayer member) {
        teamImpl().removeMember(member.getUUID());
    }

    /**
     * Ensure a player has a team <em>and</em> team data in this
     * fixture's file - event-driven economy sides need both before an
     * intake can credit them. Players already in this fixture's team
     * resolve to it (same instance as {@link #team}); outsiders get
     * their own party.
     *
     * @param player the player to seat
     * @return their team data in this fixture's file
     */
    TeamData ensureOwnTeam(ServerPlayer player) {
        Team team = createOrReuseTeam(player);
        if (!team.getMembers().contains(player.getUUID())) {
            ((AbstractTeamBase) team).addMember(player.getUUID(), TeamRank.OWNER);
        }
        return file.getOrCreateTeamData(team);
    }

    private AbstractTeamBase teamImpl() {
        return (AbstractTeamBase) FTBTeamsAPI.api().getManager()
            .getTeamByID(team.getTeamId())
            .orElseThrow(() -> new IllegalStateException("fixture team missing from FTB Teams"));
    }

    /**
     * The fixture's team must exist in FTB Teams, not only in the
     * quest file: the policy mirror resolves membership through the
     * FTB Teams manager, where a synthetic UUID resolves to nothing.
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
