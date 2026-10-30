package me.foesio.foKillstreak.gui;

final class LeaderboardLayout {
    private LeaderboardLayout() {
    }

    static Slots resolve(int inventorySize, int currentSlot, int bestSlot) {
        int current = validOrFallback(currentSlot, 11, inventorySize);
        int best = validOrFallback(bestSlot, 15, inventorySize);
        if (best == current) {
            best = validOrFallback(15, 15, inventorySize);
            if (best == current) {
                best = firstDifferentSlot(current, inventorySize);
            }
        }
        return new Slots(current, best);
    }

    private static int validOrFallback(int slot, int fallback, int inventorySize) {
        if (slot >= 0 && slot < inventorySize) {
            return slot;
        }
        return Math.max(0, Math.min(inventorySize - 1, fallback));
    }

    private static int firstDifferentSlot(int occupied, int inventorySize) {
        for (int slot = 0; slot < inventorySize; slot++) {
            if (slot != occupied) {
                return slot;
            }
        }
        return occupied;
    }

    record Slots(int current, int best) {
    }
}
