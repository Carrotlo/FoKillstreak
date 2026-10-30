package me.foesio.foKillstreak.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardLoreTemplatesTest {
    @Test
    void expandsDynamicLeaderboardAtConfiguredPosition() {
        List<String> result = LeaderboardLoreTemplates.expand(
                List.of("Header", "{leaderboard}", "Footer"),
                List.of("#1 Alex - 12", "", "Your position: #4")
        );

        assertEquals(List.of("Header", "#1 Alex - 12", "", "Your position: #4", "Footer"), result);
    }

    @Test
    void explicitEmptyLoreStaysEmpty() {
        assertEquals(List.of(), LeaderboardLoreTemplates.expand(List.of(), List.of("dynamic")));
    }
}
