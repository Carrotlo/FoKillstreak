package me.foesio.foKillstreak.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MessageMigrationBackupTest {
    @TempDir Path temporary;

    @Test
    void upgradeKeepsOneExactOriginalBackupAcrossIntermediateVersionSteps() throws Exception {
        Path messages = temporary.resolve("messages.yml");
        byte[] original = "broadcasts:\n  milestone: 'Één {streak}'\n".getBytes(StandardCharsets.UTF_8);
        Files.write(messages, original);

        MessageMigrationBackup.prepare(messages, 0, false);
        Files.writeString(messages, "broadcasts:\n  milestone: updated\n");
        MessageMigrationBackup.prepare(messages, 2, false);

        assertArrayEquals(original, Files.readAllBytes(temporary.resolve("messages.yml.gui-v0-to-v4-backup")));
        assertFalse(Files.exists(temporary.resolve("messages.yml.gui-v2-to-v4-backup")));
    }

    @Test
    void currentOrDeletedMessagesDoNotCreateBackup() throws Exception {
        Path messages = temporary.resolve("messages.yml");
        MessageMigrationBackup.prepare(messages, 0, false);
        Files.writeString(messages, "commands: {}\n");
        MessageMigrationBackup.prepare(messages, 4, false);
        assertFalse(Files.exists(temporary.resolve("messages.yml.gui-v0-to-v4-backup")));
    }

    @Test
    void blockedBackupPathDoesNotPermitAnUnprotectedMigration() throws Exception {
        Path messages = temporary.resolve("messages.yml");
        Files.writeString(messages, "commands: {}\n");
        Files.createDirectory(temporary.resolve("messages.yml.gui-v0-to-v4-backup"));

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> MessageMigrationBackup.prepare(messages, 0, false));
        assertEquals("commands: {}\n", Files.readString(messages));
    }

    @Test
    void freshBundledMessagesDoNotGetALegacyBackup() throws Exception {
        Path messages = temporary.resolve("messages.yml");
        Files.writeString(messages, "commands:\n  only-player: Fresh default\n");

        MessageMigrationBackup.prepare(messages, 0, true);

        assertFalse(Files.exists(temporary.resolve("messages.yml.gui-v0-to-v4-backup")));
    }

    @Test
    void olderUpgradeBackupDoesNotReplaceThisUpgradesSnapshot() throws Exception {
        Path messages = temporary.resolve("messages.yml");
        Files.writeString(messages, "current: Één\n");
        Files.writeString(temporary.resolve("messages.yml.gui-v0-backup"), "older: snapshot\n");

        MessageMigrationBackup.prepare(messages, 2, false);

        assertEquals("current: Één\n",
                Files.readString(temporary.resolve("messages.yml.gui-v2-to-v4-backup")));
        assertEquals("older: snapshot\n", Files.readString(temporary.resolve("messages.yml.gui-v0-backup")));
    }
}
