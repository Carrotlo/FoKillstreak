package me.foesio.foKillstreak.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory same-victim cooldowns. Capacity failure denies credit instead of bypassing protection. */
final class KillCreditCooldowns {
    private final int maxEntries;
    private final long cleanupIntervalMillis;
    private final Map<Key, Long> expiresAt = new HashMap<>();
    private long lastCleanupMillis;

    KillCreditCooldowns(int maxEntries, long cleanupIntervalMillis) {
        this.maxEntries = maxEntries;
        this.cleanupIntervalMillis = cleanupIntervalMillis;
    }

    synchronized long remainingSeconds(UUID killer, UUID victim, long now) {
        Key key = new Key(killer, victim);
        Long expiry = expiresAt.get(key);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - now;
        if (remaining <= 0) {
            expiresAt.remove(key);
            return 0;
        }
        return (remaining + 999) / 1000;
    }

    synchronized Reservation reserve(UUID killer, UUID victim, int seconds, long now) {
        if (seconds <= 0) {
            return new Reservation(Status.NOT_REQUIRED, null, 0);
        }
        Key key = new Key(killer, victim);
        Long existing = expiresAt.get(key);
        if (existing != null && existing > now) {
            return new Reservation(Status.COOLDOWN, key, existing);
        }
        cleanup(now, false);
        if (expiresAt.size() >= maxEntries) {
            cleanup(now, true);
            if (expiresAt.size() >= maxEntries) {
                return new Reservation(Status.CAPACITY_REACHED, null, 0);
            }
        }
        long expiry = now + seconds * 1000L;
        expiresAt.put(key, expiry);
        return new Reservation(Status.RESERVED, key, expiry);
    }

    synchronized void release(Reservation reservation) {
        if (reservation.status() == Status.RESERVED) {
            expiresAt.remove(reservation.key(), reservation.expiresAt());
        }
    }

    synchronized void clear(UUID player) {
        expiresAt.keySet().removeIf(key -> key.killer().equals(player) || key.victim().equals(player));
    }

    private void cleanup(long now, boolean force) {
        if (!force && expiresAt.size() < maxEntries && now - lastCleanupMillis < cleanupIntervalMillis) {
            return;
        }
        lastCleanupMillis = now;
        expiresAt.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    enum Status { RESERVED, NOT_REQUIRED, COOLDOWN, CAPACITY_REACHED }

    record Reservation(Status status, Key key, long expiresAt) {
    }

    record Key(UUID killer, UUID victim) {
    }
}
