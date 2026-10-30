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
import java.util.logging.Logger;

public final class StreakStorage {
    private static final long LEADERBOARD_REBUILD_INTERVAL_MILLIS = 1_000L;

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final Logger logger;
    private final Map<UUID, StreakRecord> cache = new HashMap<>();
    private final Map<LeaderboardType, List<StreakRecord>> leaderboardRecords = new EnumMap<>(LeaderboardType.class);
    private final Map<LeaderboardType, Map<UUID, Integer>> leaderboardRanks = new EnumMap<>(LeaderboardType.class);
    private Connection connection;
    private boolean leaderboardsDirty = true;
    private long lastLeaderboardRebuildMillis;

    public StreakStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = null;
        this.logger = plugin.getLogger();
    }

    StreakStorage(File databaseFile, Logger logger) {
        this.plugin = null;
        this.databaseFile = databaseFile;
        this.logger = logger;
    }

    public boolean open() {
        try {
            File file = databaseFile == null ? ResourceFiles.dataFile(plugin, "data.db") : databaseFile;
            File folder = file.getParentFile();
            if (folder != null && !folder.exists() && !folder.mkdirs()) {
                logger.severe("Could not create plugin data folder.");
                return false;
            }

            Class.forName(JDBC.class.getName());
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

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
            logger.severe("Could not open SQLite database: " + exception.getMessage());
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
            logger.warning("Could not close SQLite database: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    public synchronized Mutation addKill(Player player) {
        StreakRecord previous = cache.get(player.getUniqueId());
        StreakRecord record = previous == null ? empty(player.getUniqueId(), player.getName()) : previous.copy();
        if (record.currentStreak() == Integer.MAX_VALUE) {
            return new Mutation(MutationStatus.INVALID, copyOrNull(previous), copyOrNull(previous));
        }
        record.currentStreak(record.currentStreak() + 1);
        if (record.currentStreak() > record.bestStreak()) {
            record.bestStreak(record.currentStreak());
        }
        record.updatedAt(System.currentTimeMillis());
        return commit(previous, record, true);
    }

    public synchronized Mutation reset(UUID uuid, String name) {
        StreakRecord previous = cache.get(uuid);
        StreakRecord record = previous == null ? empty(uuid, name) : previous.copy();
        boolean valueChanged = record.currentStreak() != 0;
        boolean nameChanged = !record.name().equals(name);
        if (!valueChanged && !nameChanged) {
            return new Mutation(MutationStatus.UNCHANGED, copyOrNull(previous), copyOrNull(previous));
        }
        record.name(name);
        record.currentStreak(0);
        record.updatedAt(System.currentTimeMillis());
        return commit(previous, record, valueChanged);
    }

    public synchronized Mutation setStreak(UUID uuid, String name, int streak, LeaderboardType type) {
        StreakRecord previous = cache.get(uuid);
        StreakRecord record = previous == null ? empty(uuid, name) : previous.copy();
        boolean valueChanged = (type == LeaderboardType.CURRENT ? record.currentStreak() : record.bestStreak()) != streak;
        boolean nameChanged = !record.name().equals(name);
        if (!valueChanged && !nameChanged && previous != null) {
            return new Mutation(MutationStatus.UNCHANGED, copyOrNull(previous), copyOrNull(previous));
        }
        record.name(name);
        if (type == LeaderboardType.CURRENT) {
            record.currentStreak(streak);
        } else {
            record.bestStreak(streak);
        }
        record.updatedAt(System.currentTimeMillis());
        return commit(previous, record, valueChanged);
    }

    public synchronized Optional<StreakRecord> find(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid)).map(StreakRecord::copy);
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
                    logger.warning("Skipping streak row with invalid UUID: " + result.getString("uuid"));
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

    private StreakRecord empty(UUID uuid, String name) {
        return new StreakRecord(uuid, name, 0, 0, System.currentTimeMillis());
    }

    private StreakRecord copyOrNull(StreakRecord record) {
        return record == null ? null : record.copy();
    }

    private Mutation commit(StreakRecord previous, StreakRecord record, boolean valueChanged) {
        if (!save(record)) {
            return new Mutation(MutationStatus.SAVE_FAILED, copyOrNull(previous), copyOrNull(previous));
        }
        cache.put(record.uuid(), record);
        markLeaderboardsDirty();
        return new Mutation(valueChanged ? MutationStatus.CHANGED : MutationStatus.UNCHANGED,
                copyOrNull(previous), record.copy());
    }

    private boolean save(StreakRecord record) {
        if (connection == null) {
            logger.severe("Could not save streak data for " + record.name() + ": database is closed");
            return false;
        }
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
            return true;
        } catch (SQLException exception) {
            logger.severe("Could not save streak data for " + record.name() + ": " + exception.getMessage());
            return false;
        }
    }

    public enum MutationStatus { CHANGED, UNCHANGED, INVALID, SAVE_FAILED }

    public record Mutation(MutationStatus status, StreakRecord previous, StreakRecord current) {
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
