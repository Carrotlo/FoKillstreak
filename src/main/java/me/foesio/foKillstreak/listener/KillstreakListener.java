package me.foesio.foKillstreak.listener;

import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foKillstreak.config.ConfigService;
import me.foesio.foKillstreak.config.ConfigService.RewardCommand;
import me.foesio.foKillstreak.config.ConfigService.RewardSender;
import me.foesio.foKillstreak.data.StreakRecord;
import me.foesio.foKillstreak.data.StreakStorage;
import me.foesio.foKillstreak.message.MessageFallbacks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KillstreakListener implements Listener {
    private static final long COOLDOWN_CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_SAME_VICTIM_COOLDOWNS = 20_000;

    private final ConfigService config;
    private final FoMessageService messages;
    private final StreakStorage storage;
    private final FoSoundService sounds;
    private final Map<CooldownKey, Long> sameVictimCooldowns = new ConcurrentHashMap<>();
    private long lastCooldownCleanupMillis;

    public KillstreakListener(ConfigService config, FoMessageService messages, StreakStorage storage, FoSoundService sounds) {
        this.config = config;
        this.messages = messages;
        this.storage = storage;
        this.sounds = sounds;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (shouldResetVictim(killer, victim)) {
            boolean lostStreak = storage.currentStreak(victim.getUniqueId()) > 0;
            storage.reset(victim);
            if (lostStreak) {
                sounds.play(victim, "killstreak.lost");
            }
        }
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        long remainingCooldown = remainingCooldownSeconds(killer.getUniqueId(), victim.getUniqueId());
        if (remainingCooldown > 0) {
            if (config.getBoolean("same-victim-cooldown.notify-killer", true)) {
                messages.send(killer, "killstreak.cooldown-blocked", MessageFallbacks.string("killstreak.cooldown-blocked"), Map.of(
                        "victim", victim.getName(),
                        "cooldown", String.valueOf(remainingCooldown)
                ));
            }
            sounds.play(killer, "killstreak.denied");
            return;
        }

        rememberKill(killer.getUniqueId(), victim.getUniqueId());
        StreakRecord record = storage.addKill(killer);
        sounds.playWithPitchVariation(killer, "killstreak.kill", 0.04f);
        if (runRewards(killer, record)) {
            sounds.play(killer, "killstreak.reward");
        }
        maybeBroadcastMilestone(killer, record);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        sameVictimCooldowns.keySet().removeIf(key -> key.includes(player));
    }

    private long remainingCooldownSeconds(UUID killer, UUID victim) {
        if (!config.getBoolean("same-victim-cooldown.enabled", true)) {
            return 0;
        }

        CooldownKey key = new CooldownKey(killer, victim);
        Long expiresAt = sameVictimCooldowns.get(key);
        if (expiresAt == null) {
            return 0;
        }

        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            sameVictimCooldowns.remove(key);
            return 0;
        }
        return (long) Math.ceil(remainingMillis / 1000.0);
    }

    private void rememberKill(UUID killer, UUID victim) {
        if (!config.getBoolean("same-victim-cooldown.enabled", true)) {
            return;
        }

        int seconds = config.getInt("same-victim-cooldown.seconds", 60);
        if (seconds <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanupExpiredCooldowns(now, false);
        if (sameVictimCooldowns.size() >= MAX_SAME_VICTIM_COOLDOWNS) {
            cleanupExpiredCooldowns(now, true);
            if (sameVictimCooldowns.size() >= MAX_SAME_VICTIM_COOLDOWNS) {
                return;
            }
        }

        sameVictimCooldowns.put(new CooldownKey(killer, victim), now + seconds * 1000L);
    }

    @SuppressWarnings("deprecation")
    private void maybeBroadcastMilestone(Player killer, StreakRecord record) {
        if (!config.getBoolean("broadcasts.enabled", true)) {
            return;
        }

        List<Integer> milestones = config.getMilestones();
        if (!milestones.contains(record.currentStreak())) {
            return;
        }

        Bukkit.broadcastMessage(messages.render("broadcasts.milestone", MessageFallbacks.string("broadcasts.milestone"), Map.of(
                "player", killer.getName(),
                "streak", String.valueOf(record.currentStreak()),
                "best_streak", String.valueOf(record.bestStreak()),
                "milestone", String.valueOf(record.currentStreak())
        )));
        sounds.play(killer, "killstreak.milestone");
    }

    private boolean runRewards(Player killer, StreakRecord record) {
        boolean dispatched = false;
        for (RewardCommand reward : config.getRewardsForStreak(record.currentStreak())) {
            String command = applyRewardPlaceholders(killer, record, reward.command());
            if (command.isBlank()) {
                continue;
            }

            CommandSender sender = reward.sender() == RewardSender.CONSOLE ? Bukkit.getConsoleSender() : killer;
            Bukkit.dispatchCommand(sender, command);
            dispatched = true;
        }
        return dispatched;
    }

    private String applyRewardPlaceholders(Player killer, StreakRecord record, String command) {
        String rendered = CommandPlaceholders.applyPlayer(stripLeadingSlash(command), killer);
        return CommandPlaceholders.apply(rendered, Map.of(
                "streak", String.valueOf(record.currentStreak()),
                "best_streak", String.valueOf(record.bestStreak())
        ));
    }

    private String stripLeadingSlash(String command) {
        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private boolean shouldResetVictim(Player killer, Player victim) {
        if (!config.getBoolean("death-reset.only-by-player", true)) {
            return true;
        }
        return killer != null && !killer.getUniqueId().equals(victim.getUniqueId());
    }

    private void cleanupExpiredCooldowns(long now, boolean force) {
        if (!force
                && sameVictimCooldowns.size() < MAX_SAME_VICTIM_COOLDOWNS
                && now - lastCooldownCleanupMillis < COOLDOWN_CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        lastCooldownCleanupMillis = now;
        sameVictimCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private record CooldownKey(UUID killer, UUID victim) {
        private boolean includes(UUID player) {
            return killer.equals(player) || victim.equals(player);
        }
    }
}
