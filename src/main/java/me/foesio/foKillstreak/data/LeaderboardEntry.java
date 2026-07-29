package me.foesio.foKillstreak.data;

import java.util.UUID;

public record LeaderboardEntry(UUID uuid, String name, int streak, int rank) {
}
