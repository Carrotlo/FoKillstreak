package me.foesio.foKillstreak.gui;

import me.foesio.core.gui.publicgui.PublicGuiContract;

import java.util.Map;
import java.util.Set;

public final class LeaderboardGuiContract {
    private LeaderboardGuiContract() {
    }

    public static PublicGuiContract contract() {
        return new PublicGuiContract(Set.of("leaderboard.current", "leaderboard.best"),
                Map.of(), Set.of(), PublicGuiContract.InteractionMode.CORE);
    }
}
