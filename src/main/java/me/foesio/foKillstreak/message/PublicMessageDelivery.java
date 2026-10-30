package me.foesio.foKillstreak.message;

import me.foesio.core.message.FoMessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** Sends configured public feedback without a plain-text check discarding sprite-only output. */
public final class PublicMessageDelivery {
    private PublicMessageDelivery() {
    }

    public static boolean send(FoMessageService messages, CommandSender sender, String path) {
        return send(messages, sender, path, Map.of());
    }

    public static boolean send(FoMessageService messages, CommandSender sender, String path,
                               Map<String, String> placeholders) {
        Boolean listResult = sendIfConfiguredList(messages, sender, path, placeholders);
        if (listResult != null) {
            return listResult;
        }
        if (sender instanceof Player player) {
            Component rendered = messages.renderComponent(player, path, "", placeholders);
            if (rendered.equals(Component.empty())) {
                return false;
            }
            player.sendMessage(rendered);
            return true;
        }
        String rendered = messages.render(path, "", placeholders);
        if (rendered.isEmpty()) {
            return false;
        }
        sender.sendMessage(rendered);
        return true;
    }

    private static Boolean sendIfConfiguredList(FoMessageService messages, CommandSender sender,
                                                String path, Map<String, String> placeholders) {
        if (!messages.config().isList(path)) {
            return null;
        }
        if (messages.config().getStringList(path).isEmpty()) {
            return false;
        }
        messages.send(sender, path, "", placeholders);
        return true;
    }
}
