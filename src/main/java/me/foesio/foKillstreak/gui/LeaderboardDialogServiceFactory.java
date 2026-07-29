package me.foesio.foKillstreak.gui;

import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.message.FoMessageService;
import me.foesio.foKillstreak.FoKillstreak;
import me.foesio.foKillstreak.config.GuiConfigService;
import me.foesio.foKillstreak.data.LeaderboardType;
import me.foesio.foKillstreak.data.StreakStorage;
import org.bukkit.entity.Player;

public final class LeaderboardDialogServiceFactory {
    private LeaderboardDialogServiceFactory() {
    }

    public static LeaderboardDialogService create(FoKillstreak plugin, NativeDialogSupport nativeDialogs, GuiConfigService guiConfig, FoMessageService messages, StreakStorage storage, Fallback fallback) {
        if (!nativeDialogs.canUseNativeDialogs()) {
            return (player, type) -> false;
        }

        try {
            Class<?> implementation = Class.forName("me.foesio.foKillstreak.gui.PaperDialogLeaderboardService");
            return (LeaderboardDialogService) implementation
                    .getConstructor(FoKillstreak.class, NativeDialogSupport.class, GuiConfigService.class, FoMessageService.class, StreakStorage.class, Fallback.class)
                    .newInstance(plugin, nativeDialogs, guiConfig, messages, storage, fallback);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Native leaderboard dialog failed to initialize; using inventory fallback: " + exception.getMessage());
            return (player, type) -> false;
        }
    }

    @FunctionalInterface
    public interface Fallback {
        void open(Player player);
    }
}
