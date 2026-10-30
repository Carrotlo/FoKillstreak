package me.foesio.foKillstreak.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class GuiConfigServiceTest {
    @TempDir Path temporary;

    @Test
    void freshInstallCopiesCurrentResourcesWithoutMigrationBackup() throws Exception {
        Path root = temporary.resolve("fresh");
        GuiConfigService service = service(root);
        service.initialize(true);

        assertTrue(service.available());
        assertEquals(0, service.upgradedLegacyGuiFrom());
        assertArrayEquals(resource("guis/leaderboard.yml"), Files.readAllBytes(root.resolve("guis/leaderboard.yml")));
        assertArrayEquals(resource("dialogs/leaderboard.yml"), Files.readAllBytes(root.resolve("dialogs/leaderboard.yml")));
        assertEquals("3", Files.readString(root.resolve("guis/.leaderboard-schema-version")));
        assertFalse(Files.exists(root.resolve("guis/leaderboard.yml.gui-v1-backup")));
        assertFalse(Files.exists(root.resolve("guis/leaderboard.yml.gui-v2-backup")));
    }

    @Test
    void upgradeRetainsExactOriginalBackupAndInstallsBothCurrentFiles() throws Exception {
        Path root = temporary.resolve("upgrade");
        Files.createDirectories(root.resolve("guis"));
        Path gui = root.resolve("guis/leaderboard.yml");
        String original = new String(resource("migration/leaderboard-v1.yml"), StandardCharsets.UTF_8)
                .replace("filler-material: LIGHT_GRAY_STAINED_GLASS_PANE", "filler-material: BLACK_STAINED_GLASS_PANE")
                .replace("title: \"&8ꜰᴏᴋɪʟʟꜱᴛʀᴇᴀᴋ\"", "title: '&dÜbersicht'");
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);
        Files.write(gui, originalBytes);

        GuiConfigService service = service(root);
        service.initialize(false);

        assertTrue(service.available());
        assertEquals(1, service.upgradedLegacyGuiFrom());
        assertArrayEquals(originalBytes, Files.readAllBytes(root.resolve("guis/leaderboard.yml.gui-v1-backup")));
        YamlConfiguration active = YamlConfiguration.loadConfiguration(gui.toFile());
        assertEquals("&dÜbersicht", active.getString("title"));
        assertEquals("BLACK_STAINED_GLASS_PANE", active.getString("filler.material"));
        assertFalse(active.isSet("current"));
        assertTrue(Files.isRegularFile(root.resolve("dialogs/leaderboard.yml")));
        assertEquals("3", Files.readString(root.resolve("guis/.leaderboard-schema-version")));
        byte[] after = Files.readAllBytes(gui);
        service.initialize(false);
        assertEquals(0, service.upgradedLegacyGuiFrom(), "a current GUI must not reinstall a removed sounds.yml");
        assertArrayEquals(after, Files.readAllBytes(gui), "current schema must not be rewritten on restart");
        assertArrayEquals(originalBytes, Files.readAllBytes(root.resolve("guis/leaderboard.yml.gui-v1-backup")));
    }

    @Test
    void unparseableOldFileRemainsUntouchedAndMigrationPending() throws Exception {
        Path root = temporary.resolve("broken");
        Files.createDirectories(root.resolve("guis"));
        Path gui = root.resolve("guis/leaderboard.yml");
        byte[] invalid = "current: [\n".getBytes(StandardCharsets.UTF_8);
        Files.write(gui, invalid);

        GuiConfigService service = service(root);
        service.initialize(false);

        assertFalse(service.available());
        assertEquals("1234567", service.formatNumber(1234567),
                "unavailable public text must not introduce an implicit locale");
        assertEquals(0, service.upgradedLegacyGuiFrom());
        assertArrayEquals(invalid, Files.readAllBytes(gui));
        assertFalse(Files.exists(root.resolve("dialogs/leaderboard.yml")));
        assertFalse(Files.exists(root.resolve("guis/leaderboard.yml.gui-v1-backup")));
    }

    @Test
    void v2UpgradeDoesNotClaimThePriorSoundFileWasAbsent() throws Exception {
        Path root = temporary.resolve("v2");
        Files.createDirectories(root.resolve("guis"));
        Files.write(root.resolve("guis/leaderboard.yml"), resource("migration/leaderboard-v2.yml"));

        GuiConfigService service = service(root);
        service.initialize(false);

        assertTrue(service.available());
        assertEquals(2, service.upgradedLegacyGuiFrom());
        assertFalse(Files.exists(root.resolve("sounds.yml")));
    }

    @Test
    void failedMultiFileCommitRestoresThePreAttemptGuiBytes() throws Exception {
        Path root = temporary.resolve("rollback");
        Files.createDirectories(root.resolve("guis"));
        Path gui = root.resolve("guis/leaderboard.yml");
        byte[] original = resource("migration/leaderboard-v1.yml");
        Files.write(gui, original);
        Path blockedVersion = root.resolve("guis/.leaderboard-schema-version");
        Files.createDirectory(blockedVersion);
        Files.writeString(blockedVersion.resolve("keep"), "owner data");

        GuiConfigService service = service(root);
        service.initialize(false);

        assertFalse(service.available());
        assertArrayEquals(original, Files.readAllBytes(gui));
        assertArrayEquals(original, Files.readAllBytes(root.resolve("guis/leaderboard.yml.gui-v1-backup")));
        assertFalse(Files.exists(root.resolve("dialogs/leaderboard.yml")));
        assertEquals("owner data", Files.readString(blockedVersion.resolve("keep")));
    }

    @Test
    void currentReloadKeepsOmissionsAndBadStagedTextCannotReplaceCache() throws Exception {
        Path root = temporary.resolve("reload");
        GuiConfigService service = service(root);
        service.initialize(true);
        Path file = root.resolve("dialogs/leaderboard.yml");
        YamlConfiguration edited = YamlConfiguration.loadConfiguration(file.toFile());
        edited.set("native.title", "&dÉén ranglijst");
        edited.set("inventory.separator", null);
        edited.set("number-locale", "de-DE");
        edited.save(file.toFile());
        byte[] ownerBytes = Files.readAllBytes(file);

        service.stage();
        assertEquals("{white}Top Killstreaks", service.getString("native.title", ""));
        service.commitStaged();
        assertEquals("&dÉén ranglijst", service.getString("native.title", ""));
        assertEquals("1.234.567", service.formatNumber(1234567));
        assertFalse(service.has("inventory.separator"));
        assertArrayEquals(ownerBytes, Files.readAllBytes(file));
        edited.set("native.limit", -1);
        edited.save(file.toFile());

        assertThrows(IllegalStateException.class, service::stage);
        assertEquals("&dÉén ranglijst", service.getString("native.title", ""));
        assertFalse(service.has("inventory.separator"));
    }

    @Test
    void coldStartupRepairsOnlyInvalidTextInMemoryAndReloadRejectsIt() throws Exception {
        Path root = temporary.resolve("cold-text");
        GuiConfigService service = service(root);
        service.initialize(true);
        Path file = root.resolve("dialogs/leaderboard.yml");
        YamlConfiguration edited = YamlConfiguration.loadConfiguration(file.toFile());
        edited.set("native.title", "&dÉén ranglijst");
        edited.set("native.button-order", List.of("current", 7, "best"));
        edited.set("inventory.separator", null);
        edited.set("inventory.limit", null);
        edited.save(file.toFile());
        byte[] ownerBytes = Files.readAllBytes(file);

        GuiConfigService restarted = service(root);
        restarted.initialize(false);
        assertTrue(restarted.available());
        assertEquals("&dÉén ranglijst", restarted.getString("native.title", ""));
        assertEquals(10, restarted.getInt("inventory.limit", 0));
        assertEquals(List.of("current", "best"), restarted.getStringList("native.button-order"));
        assertFalse(restarted.has("inventory.separator"));
        assertArrayEquals(ownerBytes, Files.readAllBytes(file), "startup repair must not write owner text");
        assertThrows(IllegalStateException.class, restarted::stage);
        assertEquals("&dÉén ranglijst", restarted.getString("native.title", ""));
    }

    private GuiConfigService service(Path root) {
        return new GuiConfigService(root,
                path -> getClass().getClassLoader().getResourceAsStream(path), Logger.getAnonymousLogger());
    }

    private byte[] resource(String path) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }
}
