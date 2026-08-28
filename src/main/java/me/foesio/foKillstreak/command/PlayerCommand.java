package me.foesio.foKillstreak.command;

import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.message.FoMessageService;
import me.foesio.foKillstreak.gui.KillstreakGuiManager;
import me.foesio.foKillstreak.message.MessageFallbacks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PlayerCommand implements CommandExecutor {
    private final FoMessageService messages;
    private final KillstreakGuiManager guiManager;
    private final FoAdminSounds adminSounds;

    public PlayerCommand(FoMessageService messages, KillstreakGuiManager guiManager, FoAdminSounds adminSounds) {
        this.messages = messages;
        this.guiManager = guiManager;
        this.adminSounds = adminSounds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fokillstreak.use")) {
            messages.send(sender, "commands.no-permission", MessageFallbacks.string("commands.no-permission"));
            adminSounds.updateError(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "commands.only-player", MessageFallbacks.string("commands.only-player"));
            return true;
        }

        guiManager.openLeaderboard(player);
        return true;
    }
}
