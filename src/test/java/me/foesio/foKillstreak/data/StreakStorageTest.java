package me.foesio.foKillstreak.data;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class StreakStorageTest {
    @TempDir Path temporary;

    @Test
    void killResetAndRestartKeepPersistedValues() {
        Path database = temporary.resolve("data.db");
        UUID uuid = UUID.randomUUID();
        StreakStorage storage = storage(database);
        assertTrue(storage.open());
        assertEquals(StreakStorage.MutationStatus.CHANGED,
                storage.setStreak(uuid, "Player", 3, LeaderboardType.CURRENT).status());
        StreakStorage.Mutation kill = storage.addKill(player(uuid, "Player"));
        assertEquals(StreakStorage.MutationStatus.CHANGED, kill.status());
        assertEquals(3, kill.previous().currentStreak());
        assertEquals(4, kill.current().currentStreak());
        assertEquals(4, kill.current().bestStreak());
        assertEquals(StreakStorage.MutationStatus.CHANGED, storage.reset(uuid, "Player").status());
        assertEquals(StreakStorage.MutationStatus.UNCHANGED, storage.reset(uuid, "Player").status());
        storage.close();

        StreakStorage restarted = storage(database);
        assertTrue(restarted.open());
        assertEquals(0, restarted.currentStreak(uuid));
        assertEquals(4, restarted.bestStreak(uuid));
        assertEquals(1, restarted.rank(uuid, LeaderboardType.BEST));
        restarted.close();
    }

    @Test
    void failedSaveLeavesCacheUnchanged() {
        UUID uuid = UUID.randomUUID();
        StreakStorage storage = storage(temporary.resolve("data.db"));
        assertTrue(storage.open());
        storage.setStreak(uuid, "Player", 2, LeaderboardType.CURRENT);
        storage.close();

        assertEquals(StreakStorage.MutationStatus.SAVE_FAILED,
                storage.setStreak(uuid, "Player", 9, LeaderboardType.CURRENT).status());
        assertEquals(StreakStorage.MutationStatus.SAVE_FAILED, storage.addKill(player(uuid, "Player")).status());
        assertEquals(StreakStorage.MutationStatus.SAVE_FAILED, storage.reset(uuid, "Player").status());
        assertEquals(2, storage.currentStreak(uuid));
        assertEquals(0, storage.bestStreak(uuid));
    }

    @Test
    void explicitZeroSetCreatesRecordButUnknownResetDoesNot() {
        Path database = temporary.resolve("data.db");
        UUID setUuid = UUID.randomUUID();
        UUID resetUuid = UUID.randomUUID();
        StreakStorage storage = storage(database);
        assertTrue(storage.open());

        assertEquals(StreakStorage.MutationStatus.UNCHANGED,
                storage.setStreak(setUuid, "Player", 0, LeaderboardType.CURRENT).status());
        assertTrue(storage.find(setUuid).isPresent());
        assertEquals(StreakStorage.MutationStatus.UNCHANGED, storage.reset(resetUuid, "Other").status());
        assertTrue(storage.find(resetUuid).isEmpty());
        storage.close();

        StreakStorage restarted = storage(database);
        assertTrue(restarted.open());
        assertTrue(restarted.find(setUuid).isPresent());
        assertTrue(restarted.find(resetUuid).isEmpty());
        restarted.close();
    }

    private StreakStorage storage(Path database) {
        return new StreakStorage(database.toFile(), Logger.getLogger("StreakStorageTest"));
    }

    private Player player(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
