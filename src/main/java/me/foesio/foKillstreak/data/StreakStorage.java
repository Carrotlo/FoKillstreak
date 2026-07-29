package me.foesio.foKillstreak.data;

import me.foesio.core.config.ResourceFiles;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.JDBC;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StreakStorage {
    private static final long LEADERBOARD_REBUILD_INTERVAL_MILLIS = 1_000L;

    private final JavaPlugin plugin;
    private final Map<UUID, StreakRecord> cache = new HashMap<>();
    private final Map<LeaderboardType, List<StreakRecord>> leaderboardRecords = new EnumMap<>(LeaderboardType.class);
    private final Map<LeaderboardType, Map<UUID, Integer>> leaderboardRanks = new EnumMap<>(LeaderboardType.class);
    private Connection connection;
    private boolean leaderboardsDirty = true;
    private long lastLeaderboardRebuildMillis;

    public StreakStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean open() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().severe("Could not create plugin data folder.");
                return false;
            }

            Class.forName(JDBC.class.getName());
            File databaseFile = ResourceFiles.dataFile(plugin, "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_streaks (
                            uuid TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            current_streak INTEGER NOT NULL DEFAULT 0,
                            best_streak INTEGER NOT NULL DEFAULT 0,
                            updated_at INTEGER NOT NULL
                        )
                        """);
            }

            loadAll();
            return true;
        } catch (ClassNotFoundException | SQLException exception) {
            plugin.getLogger().severe("Could not open SQLite database: " + exception.getMessage());
            return false;
        }
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not close SQLite database: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    public synchronized StreakRecord addKill(Player player) {
        StreakRecord record = getOrCreate(player);
        record.currentStreak(record.currentStreak() + 1);
        if (record.currentStreak() > record.bestStreak()) {
            record.bestStreak(record.currentStreak());
        }
        record.updatedAt(System.currentTimeMillis());
        save(record);
        markLeaderboardsDirty();
        return record.copy();
    }

    public synchronized StreakRecord reset(Player player) {
        StreakRecord record = getOrCreate(player);
        boolean changed = record.currentStreak() != 0 || !record.name().equals(player.getName());
        record.name(player.getName());
        record.currentStreak(0);
        record.updatedAt(System.currentTimeMillis());
        if (changed) {
            save(record);
            markLeaderboardsDirty();
        }
        return record.copy();
    }

    public synchronized StreakRecord setStreak(UUID uuid, String name, int streak, LeaderboardType type) {
        StreakRecord record = cache.computeIfAbsent(uuid, ignored -> new StreakRecord(
                uuid,
                name,
                0,
                0,
                System.currentTimeMillis()
        ));
        record.name(name);
        if (type == LeaderboardType.CURRENT) {
            record.currentStreak(streak);
        } else {
            record.bestStreak(streak);
        }
        record.updatedAt(System.currentTimeMillis());
        save(record);
        markLeaderboardsDirty();
        return record.copy();
    }

    public synchronized Optional<StreakRecord> findByName(String name) {
        return cache.values().stream()
                .filter(record -> record.name().equalsIgnoreCase(name))
                .findFirst()
                .map(StreakRecord::copy);
    }

    public synchronized List<String> knownPlayerNames() {
        return cache.values().stream()
                .map(StreakRecord::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public synchronized int currentStreak(UUID uuid) {
        StreakRecord record = cache.get(uuid);
        return record == null ? 0 : record.currentStreak();
    }

    public synchronized int bestStreak(UUID uuid) {
        StreakRecord record = cache.get(uuid);
        return record == null ? 0 : record.bestStreak();
    }

    public synchronized List<LeaderboardEntry> top(LeaderboardType type, int limit) {
        refreshLeaderboardsIfDirty();
        List<StreakRecord> records = leaderboardRecords.getOrDefault(type, List.of());
        List<LeaderboardEntry> entries = new ArrayList<>();
        int size = Math.min(limit, records.size());
        for (int index = 0; index < size; index++) {
            StreakRecord record = records.get(index);
            entries.add(new LeaderboardEntry(record.uuid(), record.name(), value(record, type), index + 1));
        }
        return entries;
    }

    public synchronized int rank(UUID uuid, LeaderboardType type) {
        refreshLeaderboardsIfDirty();
        return leaderboardRanks.getOrDefault(type, Map.of()).getOrDefault(uuid, -1);
    }

    private void loadAll() throws SQLException {
        cache.clear();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT uuid, name, current_streak, best_streak, updated_at FROM player_streaks")) {
            while (result.next()) {
                UUID uuid = parseUuid(result.getString("uuid"));
                if (uuid == null) {
                    plugin.getLogger().warning("Skipping streak row with invalid UUID: " + result.getString("uuid"));
                    continue;
                }
                cache.put(uuid, new StreakRecord(
                        uuid,
                        result.getString("name"),
                        result.getInt("current_streak"),
                        result.getInt("best_streak"),
                        result.getLong("updated_at")
                ));
            }
        }
        markLeaderboardsDirty();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private StreakRecord getOrCreate(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> new StreakRecord(
                uuid,
                player.getName(),
                0,
                0,
                System.currentTimeMillis()
        ));
    }

    private void save(StreakRecord record) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_streaks (uuid, name, current_streak, best_streak, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    current_streak = excluded.current_streak,
                    best_streak = excluded.best_streak,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, record.uuid().toString());
            statement.setString(2, record.name());
            statement.setInt(3, record.currentStreak());
            statement.setInt(4, record.bestStreak());
            statement.setLong(5, record.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not save streak data for " + record.name() + ": " + exception.getMessage());
        }
    }

    private List<StreakRecord> sortedPositiveRecords(LeaderboardType type) {
        return cache.values().stream()
                .filter(record -> value(record, type) > 0)
                .sorted(Comparator
                        .comparingInt((StreakRecord record) -> value(record, type)).reversed()
                        .thenComparing(StreakRecord::name, String.CASE_INSENSITIVE_ORDER))
                .map(StreakRecord::copy)
                .toList();
    }

    private void refreshLeaderboardsIfDirty() {
        if (!leaderboardsDirty) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!leaderboardRecords.isEmpty() && now - lastLeaderboardRebuildMillis < LEADERBOARD_REBUILD_INTERVAL_MILLIS) {
            return;
        }

        for (LeaderboardType type : LeaderboardType.values()) {
            List<StreakRecord> records = sortedPositiveRecords(type);
            Map<UUID, Integer> ranks = new HashMap<>();
            for (int index = 0; index < records.size(); index++) {
                ranks.put(records.get(index).uuid(), index + 1);
            }
            leaderboardRecords.put(type, records);
            leaderboardRanks.put(type, Map.copyOf(ranks));
        }
        lastLeaderboardRebuildMillis = now;
        leaderboardsDirty = false;
    }

    private void markLeaderboardsDirty() {
        leaderboardsDirty = true;
    }

    private int value(StreakRecord record, LeaderboardType type) {
        return type == LeaderboardType.CURRENT ? record.currentStreak() : record.bestStreak();
    }
}
