package me.foesio.foKillstreak.api;

import java.util.UUID;

/** Immutable leaderboard row with a one-based rank. */
public record StreakLeaderboardEntry(UUID uuid, String name, int streak, int rank) {
}
