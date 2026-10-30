package me.foesio.foKillstreak.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardGuiMigrationTest {
    @Test
    void v1KeepsCustomValuesAndMovesOldDefaultToNewGeneratedStructure() throws Exception {
        YamlConfiguration old = resource("migration/leaderboard-v1.yml");
        old.set("title", "&dÜbersicht");
        old.set("filler-material", "BLACK_STAINED_GLASS_PANE");
        old.set("current.name", null);
        old.set("lines.entry", "{player} → {streak} #{rank}");

        LeaderboardGuiMigration.Result migrated = LeaderboardGuiMigration.migrate(old,
                resource("migration/leaderboard-v1.yml"), resource("guis/leaderboard.yml"),
                resource("dialogs/leaderboard.yml"));

        assertEquals(1, migrated.fromVersion());
        assertEquals("&dÜbersicht", migrated.gui().getString("title"));
        assertEquals("BLACK_STAINED_GLASS_PANE", migrated.gui().getString("filler.material"));
        assertFalse(migrated.gui().isSet("buttons.current.name"));
        assertEquals("{player} → {streak} #{rank}", migrated.text().getString("inventory.entry"));
        assertEquals("{options}", migrated.gui().getStringList("buttons.current.lore").getFirst());
        assertFalse(migrated.gui().isSet("current"));
    }

    @Test
    void v2ReplacesOnlyInvalidFieldsAndKeepsCustomSiblings() throws Exception {
        YamlConfiguration old = resource("migration/leaderboard-v2.yml");
        old.set("current.slot", -1);
        old.set("current.material", "INVALID_MATERIAL");
        old.set("filler.lore", "not a list");
        old.set("current.custom-model-data", "invalid-model");
        old.set("current.name", "&aVertaling");
        old.set("current.lore", List.of("&bExtra", "{leaderboard}"));
        old.set("native.limit", -8);
        old.set("native.first-color", "banana");
        old.set("native.title", "&aRanglijst");

        LeaderboardGuiMigration.Result migrated = LeaderboardGuiMigration.migrate(old,
                resource("migration/leaderboard-v2.yml"), resource("guis/leaderboard.yml"),
                resource("dialogs/leaderboard.yml"));

        assertEquals(2, migrated.fromVersion());
        assertEquals(11, migrated.gui().getInt("buttons.current.slot"));
        assertEquals("DIAMOND_SWORD", migrated.gui().getString("buttons.current.material"));
        assertEquals(List.of(), migrated.gui().getStringList("filler.lore"));
        assertFalse(migrated.gui().isSet("buttons.current.custom-model-data"));
        assertEquals("&aVertaling", migrated.gui().getString("buttons.current.name"));
        assertEquals(List.of("&bExtra", "{options}"), migrated.gui().getStringList("buttons.current.lore"));
        assertEquals(10, migrated.text().getInt("native.limit"));
        assertEquals("&aRanglijst", migrated.text().getString("native.title"));
        assertEquals("#ffd84d", migrated.text().getString("native.first-color"));
        assertEquals("&r", migrated.text().getString("inventory.separator"));
        assertTrue(migrated.replacements().stream().anyMatch(line -> line.startsWith("buttons.current.slot=")));
    }

    @Test
    void removedLegacySeparatorStaysAbsentDuringTheSplit() throws Exception {
        YamlConfiguration old = resource("migration/leaderboard-v2.yml");
        old.set("lines.separator", null);

        LeaderboardGuiMigration.Result migrated = LeaderboardGuiMigration.migrate(old,
                resource("migration/leaderboard-v2.yml"), resource("guis/leaderboard.yml"),
                resource("dialogs/leaderboard.yml"));

        assertFalse(migrated.text().isSet("inventory.separator"));
    }

    @Test
    void invalidLegacyDialogTextUsesNewestDefaultWithoutLosingTranslatedSibling() throws Exception {
        YamlConfiguration old = resource("migration/leaderboard-v2.yml");
        old.set("native.current-button", 7);
        old.set("native.title", "&dÉén ranglijst");

        LeaderboardGuiMigration.Result migrated = LeaderboardGuiMigration.migrate(old,
                resource("migration/leaderboard-v2.yml"), resource("guis/leaderboard.yml"),
                resource("dialogs/leaderboard.yml"));

        assertEquals("{white}Current", migrated.text().getString("native.current-button"));
        assertEquals("&dÉén ranglijst", migrated.text().getString("native.title"));
        assertTrue(migrated.replacements().stream().anyMatch(line -> line.startsWith("native.current-button=")));
    }

    @Test
    void missingLegacyRequiredFieldBlocksMigration() throws Exception {
        YamlConfiguration old = resource("migration/leaderboard-v1.yml");
        old.set("current.slot", null);
        assertThrows(InvalidConfigurationException.class, () -> LeaderboardGuiMigration.migrate(old,
                resource("migration/leaderboard-v1.yml"), resource("guis/leaderboard.yml"),
                resource("dialogs/leaderboard.yml")));
    }

    @Test
    void conflictingLegacySlotsRepairOnlyTheConflictingCustomization() throws Exception {
        YamlConfiguration old = resource("migration/leaderboard-v1.yml");
        old.set("current.slot", 15);
        old.set("best.name", "&dBeste");

        LeaderboardGuiMigration.Result migrated = LeaderboardGuiMigration.migrate(old,
                resource("migration/leaderboard-v1.yml"), resource("guis/leaderboard.yml"),
                resource("dialogs/leaderboard.yml"));

        assertEquals(11, migrated.gui().getInt("buttons.current.slot"));
        assertEquals(15, migrated.gui().getInt("buttons.best.slot"));
        assertEquals("&dBeste", migrated.gui().getString("buttons.best.name"));
    }

    private YamlConfiguration resource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "Missing test resource " + path);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
