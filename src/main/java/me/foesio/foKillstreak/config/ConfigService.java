package me.foesio.foKillstreak.config;

import me.foesio.core.config.ResourceFiles;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.dialog.NativeDialogSettings;
import me.foesio.core.number.LargeNumberParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigService {
    private static final String NATIVE_LEADERBOARDS_PATH = "native-dialogs.leaderboards";

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;
    private List<Integer> milestones = List.of();
    private Map<Integer, List<RewardCommand>> rewardsByStreak = Map.of();
    private List<Integer> rewardStreaks = List.of();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = ResourceFiles.dataFile(plugin, "config.yml");
    }

    public void load() {
        ResourceFiles.saveDefault(plugin, "config.yml");

        config = YamlConfiguration.loadConfiguration(file);
        boolean missingBundledDefaults = false;
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                missingBundledDefaults = hasMissingDefaults(defaults);
                config.options().copyDefaults(true);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load config.yml", exception);
        }

        boolean missingNativeDialogDefaults = !config.contains(NativeDialogConfigDefaults.ENABLED_PATH, false)
                || !config.contains(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH, false);
        NativeDialogConfigDefaults.addDefaults(config);
        if (missingBundledDefaults || missingNativeDialogDefaults) {
            try {
                config.save(file);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not backfill config.yml defaults: " + exception.getMessage());
            }
        }
        reloadMilestoneCache();
        reloadRewardCache();
    }

    public boolean getBoolean(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    public int getInt(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    public String getString(String path, String fallback) {
        return config.getString(path, fallback);
    }

    public NativeDialogSettings nativeDialogSettings() {
        return NativeDialogSettings.fromConfig(config.getConfigurationSection("native-dialogs"));
    }

    public boolean nativeLeaderboardDialogsEnabled() {
        return config.getBoolean(NATIVE_LEADERBOARDS_PATH, true);
    }

    public List<Integer> getMilestones() {
        return milestones;
    }

    public int getAnnounceThreshold() {
        int threshold = config.getInt("broadcasts.announce-threshold", 5);
        return threshold > 0 ? threshold : 5;
    }

    private void reloadMilestoneCache() {
        List<Integer> configured = config.getIntegerList("broadcasts.milestones");
        Set<Integer> unique = new LinkedHashSet<>();
        for (int milestone : configured) {
            if (milestone > 0) {
                unique.add(milestone);
            }
        }
        List<Integer> milestones = new ArrayList<>(unique);
        milestones.sort(Integer::compareTo);
        this.milestones = List.copyOf(milestones);
    }

    public List<Integer> getRewardStreaks() {
        return rewardStreaks;
    }

    public List<RewardCommand> getRewardsForStreak(int streak) {
        if (streak <= 0) {
            return List.of();
        }
        return rewardsByStreak.getOrDefault(streak, List.of());
    }

    public boolean addReward(int streak, RewardSender sender, String command) {
        String rewardKey = String.valueOf(streak);
        List<Map<String, Object>> rewards = getRewardMapList(rewardKey);
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("sender", sender.name());
        reward.put("command", command);
        rewards.add(reward);
        return setAndSave(rewardPath(rewardKey), rewards);
    }

    public boolean removeReward(String rewardKey, int index) {
        List<Map<String, Object>> rewards = getRewardMapList(rewardKey);
        if (index < 0 || index >= rewards.size()) {
            return false;
        }

        rewards.remove(index);
        Object value = rewards.isEmpty() ? null : rewards;
        return setAndSave(rewardPath(rewardKey), value);
    }

    public boolean updateRewardSender(String rewardKey, int index, RewardSender sender) {
        List<Map<String, Object>> rewards = getRewardMapList(rewardKey);
        if (index < 0 || index >= rewards.size()) {
            return false;
        }

        rewards.get(index).put("sender", sender.name());
        return setAndSave(rewardPath(rewardKey), rewards);
    }

    public boolean updateRewardCommand(String rewardKey, int index, String command) {
        List<Map<String, Object>> rewards = getRewardMapList(rewardKey);
        if (index < 0 || index >= rewards.size()) {
            return false;
        }

        rewards.get(index).put("command", command);
        return setAndSave(rewardPath(rewardKey), rewards);
    }

    public boolean setAndSave(String path, Object value) {
        config.set(path, value);
        try {
            config.save(file);
            load();
            return true;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("Could not save config.yml: " + exception.getMessage());
            try {
                load();
            } catch (RuntimeException reloadException) {
                plugin.getLogger().severe("Could not restore config.yml after failed save: " + reloadException.getMessage());
            }
            return false;
        }
    }

    private void reloadRewardCache() {
        ConfigurationSection section = config.getConfigurationSection("rewards");
        if (section == null) {
            rewardsByStreak = Map.of();
            rewardStreaks = List.of();
            return;
        }

        Map<Integer, List<RewardCommand>> groupedRewards = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Set<Integer> streaks = parseRewardStreakKey(key);
            if (streaks.isEmpty()) {
                plugin.getLogger().warning("Ignoring reward with invalid streak key: " + key);
                continue;
            }

            List<Map<?, ?>> entries = config.getMapList(rewardPath(key));
            for (int index = 0; index < entries.size(); index++) {
                Map<?, ?> entry = entries.get(index);
                RewardSender sender = RewardSender.parse(String.valueOf(entry.get("sender")));
                Object commandValue = entry.get("command");
                if (!(commandValue instanceof String command) || command.isBlank()) {
                    continue;
                }
                if (sender == null) {
                    plugin.getLogger().warning("Ignoring reward in key " + key + " with invalid sender: " + entry.get("sender"));
                    continue;
                }

                for (int streak : streaks) {
                    if (streak > 0) {
                        groupedRewards.computeIfAbsent(streak, ignored -> new ArrayList<>())
                                .add(new RewardCommand(streak, key, index, sender, command.trim()));
                    }
                }
            }
        }

        List<Integer> streaks = new ArrayList<>(groupedRewards.keySet());
        streaks.sort(Integer::compareTo);

        Map<Integer, List<RewardCommand>> immutableRewards = new LinkedHashMap<>();
        for (int streak : streaks) {
            immutableRewards.put(streak, List.copyOf(groupedRewards.getOrDefault(streak, List.of())));
        }
        rewardsByStreak = Map.copyOf(immutableRewards);
        rewardStreaks = List.copyOf(streaks);
    }

    private List<Map<String, Object>> getRewardMapList(String rewardKey) {
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList(rewardPath(rewardKey))) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> value : entry.entrySet()) {
                if (value.getKey() != null) {
                    copy.put(String.valueOf(value.getKey()), value.getValue());
                }
            }
            rewards.add(copy);
        }
        return rewards;
    }

    private String rewardPath(String rewardKey) {
        return "rewards." + rewardKey;
    }

    private Set<Integer> parseRewardStreakKey(String key) {
        Set<Integer> streaks = new LinkedHashSet<>();
        for (String part : key.split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            var parsed = LargeNumberParser.parsePositiveInt(part);
            if (parsed.isEmpty()) {
                return Set.of();
            }
            streaks.add(parsed.getAsInt());
        }
        return streaks;
    }

    private boolean hasMissingDefaults(YamlConfiguration defaults) {
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !config.contains(key, false)) {
                return true;
            }
        }
        return false;
    }

    public record RewardCommand(int streak, String configKey, int index, RewardSender sender, String command) {
    }

    public enum RewardSender {
        CONSOLE,
        PLAYER;

        public static RewardSender parse(String input) {
            if (input == null) {
                return null;
            }
            try {
                return RewardSender.valueOf(input.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
