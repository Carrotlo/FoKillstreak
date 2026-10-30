package me.foesio.foKillstreak.api;

import java.util.UUID;

/** Immutable stored streak values. An administrative edit can make best lower than current. */
public record StreakSnapshot(UUID uuid, String name, int current, int best, long updatedAt) {
}
