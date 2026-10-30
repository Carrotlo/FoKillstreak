package me.foesio.foKillstreak.command;

import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.message.FoMessageService;
import me.foesio.foKillstreak.gui.KillstreakGuiManager;
import me.foesio.foKillstreak.message.PublicMessageDelivery;
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
            PublicMessageDelivery.send(messages, sender, "commands.no-permission");
            adminSounds.updateError(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            PublicMessageDelivery.send(messages, sender, "commands.only-player");
            return true;
        }

        guiManager.openLeaderboard(player);
        return true;
    }
}
