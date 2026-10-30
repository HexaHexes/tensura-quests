package org.bitwisemadness.tensuraquests.gametest;

import dev.architectury.utils.Env;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;

/**
 * Isolated quest file standing in for ServerQuestFile/ClientQuestFile in
 * GameTests, shared by the framework and reward suites.
 *
 * <p>Every test builds its own instance, so no test mutates the run's
 * real quest file. {@code defaultPerTeamReward} flips the file-level
 * reward scope default so the per-player forcing tests can prove the
 * override wins even against a team-shared default.</p>
 */
final class TestQuestFile extends BaseQuestFile {

    private final Env side;
    private final HolderLookup.Provider registries;
    private final boolean defaultPerTeamReward;

    TestQuestFile(Env side, HolderLookup.Provider registries) {
        this(side, registries, false);
    }

    TestQuestFile(Env side, HolderLookup.Provider registries, boolean defaultPerTeamReward) {
        this.side = side;
        this.registries = registries;
        this.defaultPerTeamReward = defaultPerTeamReward;
    }

    @Override
    public Env getSide() {
        return side;
    }

    @Override
    public HolderLookup.Provider holderLookup() {
        return registries;
    }

    @Override
    public boolean deleteObjects(List<Long> ids) {
        return false;
    }

    @Override
    public boolean isPlayerOnTeam(Player player, TeamData data) {
        return false;
    }

    @Override
    public String getLocale() {
        return "en_us";
    }

    @Override
    public boolean isDefaultPerTeamReward() {
        return defaultPerTeamReward;
    }
}
