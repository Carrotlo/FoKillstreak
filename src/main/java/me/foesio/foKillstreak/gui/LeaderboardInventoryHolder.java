package me.foesio.foKillstreak.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class LeaderboardInventoryHolder implements InventoryHolder {
    private Inventory inventory;

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Leaderboard inventory has not been created yet");
        }
        return inventory;
    }
}
