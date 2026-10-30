package me.foesio.foKillstreak.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageDefaultMigrationTest {
    @Test
    void upgradesOnlyExactReleasedDefaultsAndPreservesRemovedSprites() throws Exception {
        YamlConfiguration previous = resource("migration/messages-v1.yml");
        YamlConfiguration newest = resource("messages.yml");
        YamlConfiguration owner = new YamlConfiguration();
        owner.loadFromString(previous.saveToString());
        owner.set("killstreak.cooldown-blocked", "{prefix}Wacht {cooldown} seconden");
        owner.set("broadcasts.milestone", null);

        assertTrue(MessageDefaultMigration.migrate(owner, previous, newest));
        assertEquals(newest.getString("tokens.prefix"), owner.getString("tokens.prefix"));
        assertEquals("{prefix}Wacht {cooldown} seconden", owner.getString("killstreak.cooldown-blocked"));
        assertFalse(owner.isSet("broadcasts.milestone"));
        assertFalse(MessageDefaultMigration.migrate(owner, previous, newest));
    }

    @Test
    void legacyMalformedPublicFieldsUseOnlyTheirNewestBundledDefaults() {
        YamlConfiguration newest = resource("messages.yml");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("tokens.theme", List.of("wrong type"));
        owner.set("commands.no-permission", null);
        owner.set("commands.only-player", "&dAlleen spelers");
        owner.set("killstreak.cooldown-blocked", 12);
        owner.set("broadcasts.milestone", List.of("&aÉén", "&bTwee"));

        List<String> repaired = MessageDefaultMigration.repairLegacyPublic(owner, newest);

        assertEquals(2, repaired.size());
        assertEquals(newest.getString("tokens.theme"), owner.getString("tokens.theme"));
        assertEquals(newest.getString("killstreak.cooldown-blocked"),
                owner.getString("killstreak.cooldown-blocked"));
        assertFalse(owner.isSet("commands.no-permission"));
        assertEquals("&dAlleen spelers", owner.getString("commands.only-player"));
        assertEquals(List.of("&aÉén", "&bTwee"), owner.getStringList("broadcasts.milestone"));
        assertTrue(MessageDefaultMigration.repairLegacyPublic(owner, newest).isEmpty());
    }

    @Test
    void malformedLegacyParentUsesOnlyItsBundledSection() {
        YamlConfiguration newest = resource("messages.yml");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("commands", 17);
        owner.set("broadcasts.milestone", "&aÉén speler");

        List<String> repaired = MessageDefaultMigration.repairLegacyPublic(owner, newest);

        assertEquals(1, repaired.size());
        assertEquals(newest.getString("commands.no-permission"), owner.getString("commands.no-permission"));
        assertEquals("&aÉén speler", owner.getString("broadcasts.milestone"));
        assertTrue(MessageDefaultMigration.repairLegacyPublic(owner, newest).isEmpty());
    }

    private YamlConfiguration resource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
