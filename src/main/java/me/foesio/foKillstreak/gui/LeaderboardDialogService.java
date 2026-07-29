package me.foesio.foKillstreak.gui;

import me.foesio.foKillstreak.data.LeaderboardType;
import org.bukkit.entity.Player;

public interface LeaderboardDialogService {
    boolean open(Player player, LeaderboardType type);
}
