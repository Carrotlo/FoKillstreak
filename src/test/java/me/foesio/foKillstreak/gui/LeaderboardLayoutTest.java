package me.foesio.foKillstreak.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardLayoutTest {
    @Test
    void keepsValidDistinctSlots() {
        assertEquals(new LeaderboardLayout.Slots(4, 22), LeaderboardLayout.resolve(27, 4, 22));
    }

    @Test
    void rejectsOutOfBoundsSlots() {
        assertEquals(new LeaderboardLayout.Slots(11, 15), LeaderboardLayout.resolve(27, -1, 99));
    }

    @Test
    void separatesOverlappingButtons() {
        assertEquals(new LeaderboardLayout.Slots(11, 15), LeaderboardLayout.resolve(27, 11, 11));
    }
}
