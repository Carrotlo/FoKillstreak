package me.foesio.foKillstreak.data;

import java.util.UUID;

public final class StreakRecord {
    private final UUID uuid;
    private String name;
    private int currentStreak;
    private int bestStreak;
    private long updatedAt;

    public StreakRecord(UUID uuid, String name, int currentStreak, int bestStreak, long updatedAt) {
        this.uuid = uuid;
        this.name = name;
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
        this.updatedAt = updatedAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public int currentStreak() {
        return currentStreak;
    }

    public void currentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public void bestStreak(int bestStreak) {
        this.bestStreak = bestStreak;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public void updatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public StreakRecord copy() {
        return new StreakRecord(uuid, name, currentStreak, bestStreak, updatedAt);
    }
}
