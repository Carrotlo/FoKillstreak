package me.foesio.foKillstreak.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public FoKillstreak integration service, available through Bukkit ServicesManager while enabled. */
public interface KillstreakService {
    /** Returns stored values, or empty when this UUID has no record. */
    Optional<StreakSnapshot> getStreak(UUID uuid);

    /** Returns positive-streak leaderboard entries. The leaderboard may lag changes by one second. */
    List<StreakLeaderboardEntry> getTop(StreakType type, int limit);

    /** Returns a one-based rank, or -1 when the player is not ranked. May lag by one second. */
    int getRank(UUID uuid, StreakType type);

    /** Sets one stored field for an online or offline player. Does not grant rewards. */
    ChangeResult setStreak(UUID uuid, String name, StreakType type, int value);

    /** Sets the current streak to zero without changing the best streak or granting rewards. */
    ChangeResult resetCurrent(UUID uuid, String name);

    /** Credits one eligible PvP kill. Does not reset the victim; callers must prevent duplicate death credit. */
    CreditResult creditKill(Player killer, Player victim);
}
