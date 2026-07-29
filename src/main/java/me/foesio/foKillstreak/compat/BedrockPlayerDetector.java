package me.foesio.foKillstreak.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

public final class BedrockPlayerDetector {
    private BedrockPlayerDetector() {
    }

    public static boolean isBedrock(Player player) {
        UUID uuid = player.getUniqueId();
        return isFloodgatePlayer(uuid) || isGeyserPlayer(uuid);
    }

    private static boolean isFloodgatePlayer(UUID uuid) {
        Plugin plugin = plugin("floodgate");
        if (plugin == null) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi", false, plugin.getClass().getClassLoader());
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
            return Boolean.TRUE.equals(result);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static boolean isGeyserPlayer(UUID uuid) {
        Plugin plugin = firstPlugin("Geyser-Spigot", "Geyser-Bukkit", "Geyser");
        if (plugin == null) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi", false, plugin.getClass().getClassLoader());
            Object api = apiClass.getMethod("api").invoke(null);
            Object connection = apiClass.getMethod("connectionByUuid", UUID.class).invoke(api, uuid);
            return connection != null;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static Plugin firstPlugin(String... names) {
        for (String name : names) {
            Plugin plugin = plugin(name);
            if (plugin != null) {
                return plugin;
            }
        }
        return null;
    }

    private static Plugin plugin(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }
}
