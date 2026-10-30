package me.foesio.foKillstreak.gui;

import java.util.ArrayList;
import java.util.List;

final class LeaderboardLoreTemplates {
    static final String LEADERBOARD_EXPANSION = "{leaderboard}";

    private LeaderboardLoreTemplates() {
    }

    static List<String> expand(List<String> templates, List<String> leaderboardLines) {
        List<String> expanded = new ArrayList<>();
        for (String template : templates) {
            if (LEADERBOARD_EXPANSION.equals(template)) {
                expanded.addAll(leaderboardLines);
            } else {
                expanded.add(template);
            }
        }
        return expanded;
    }
}
