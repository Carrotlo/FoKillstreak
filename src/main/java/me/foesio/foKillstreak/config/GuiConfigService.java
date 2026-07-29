package me.foesio.foKillstreak.config;

import me.foesio.core.config.ResourceFiles;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class GuiConfigService {
    private static final String LEADERBOARD_PATH = "guis/leaderboard.yml";

    private final JavaPlugin plugin;
    private FileConfiguration leaderboard;

    public GuiConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = ResourceFiles.saveDefault(plugin, LEADERBOARD_PATH);
        leaderboard = YamlConfiguration.loadConfiguration(file);
        if (backfillMissingDefaults()) {
            try {
                leaderboard.save(file);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not backfill " + LEADERBOARD_PATH + " defaults: " + exception.getMessage());
            }
        }
    }

    public String getString(String path, String fallback) {
        return leaderboard.getString(path, fallback);
    }

    public int getInt(String path, int fallback) {
        return leaderboard.getInt(path, fallback);
    }

    private boolean backfillMissingDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource(LEADERBOARD_PATH)) {
            if (stream == null) {
                return false;
            }
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load bundled " + LEADERBOARD_PATH + " defaults: " + exception.getMessage());
            return false;
        }

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || leaderboard.contains(key)) {
                continue;
            }
            leaderboard.set(key, defaults.get(key));
            changed = true;
        }
        return changed;
    }
}
