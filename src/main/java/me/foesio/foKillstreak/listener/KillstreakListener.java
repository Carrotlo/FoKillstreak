package me.foesio.foKillstreak.listener;

import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foKillstreak.api.ChangeCause;
import me.foesio.foKillstreak.api.ChangeResult;
import me.foesio.foKillstreak.api.CreditResult;
import me.foesio.foKillstreak.api.KillstreakService;
import me.foesio.foKillstreak.api.StreakLeaderboardEntry;
import me.foesio.foKillstreak.api.StreakSnapshot;
import me.foesio.foKillstreak.api.StreakType;
import me.foesio.foKillstreak.api.event.KillCreditPreEvent;
import me.foesio.foKillstreak.api.event.KillstreakChangeEvent;
import me.foesio.foKillstreak.config.ConfigService;
import me.foesio.foKillstreak.config.GuiConfigService;
import me.foesio.foKillstreak.config.ConfigService.RewardCommand;
import me.foesio.foKillstreak.config.ConfigService.RewardSender;
import me.foesio.foKillstreak.data.LeaderboardEntry;
import me.foesio.foKillstreak.data.LeaderboardType;
import me.foesio.foKillstreak.data.StreakRecord;
import me.foesio.foKillstreak.data.StreakStorage;
import me.foesio.foKillstreak.message.PublicMessageDelivery;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KillstreakListener implements Listener, KillstreakService {
    private static final long COOLDOWN_CLEANUP_INTERVAL_MILLIS = 60_000L;
    private static final int MAX_SAME_VICTIM_COOLDOWNS = 20_000;

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final GuiConfigService publicText;
    private final FoMessageService messages;
    private final StreakStorage storage;
    private final FoSoundService sounds;
    private final KillCreditCooldowns sameVictimCooldowns = new KillCreditCooldowns(
            MAX_SAME_VICTIM_COOLDOWNS, COOLDOWN_CLEANUP_INTERVAL_MILLIS);
    private final Set<CreditKey> inFlightCredits = ConcurrentHashMap.newKeySet();

    public KillstreakListener(JavaPlugin plugin, ConfigService config, GuiConfigService publicText, FoMessageService messages, StreakStorage storage, FoSoundService sounds) {
        this.plugin = plugin;
        this.config = config;
        this.publicText = publicText;
        this.messages = messages;
        this.storage = storage;
        this.sounds = sounds;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        int victimStreak = storage.currentStreak(victim.getUniqueId());

        if (shouldResetVictim(killer, victim)) {
            ChangeResult reset = reset(victim.getUniqueId(), victim.getName(),
                    ChangeCause.DEATH, killer == null ? null : killer.getUniqueId());
            if (reset == ChangeResult.CHANGED) {
                sounds.play(victim, "killstreak.lost");
            }
            if (reset == ChangeResult.SAVE_FAILED) {
                return;
            }
        }
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        maybeBroadcastEndedStreak(killer, victim, victimStreak);

        creditKill(killer, victim);
    }

    @Override
    public Optional<StreakSnapshot> getStreak(UUID uuid) {
        if (uuid == null || !plugin.isEnabled()) {
            return Optional.empty();
        }
        return storage.find(uuid).map(this::snapshot);
    }

    @Override
    public List<StreakLeaderboardEntry> getTop(StreakType type, int limit) {
        if (type == null || limit <= 0 || !plugin.isEnabled()) {
            return List.of();
        }
        return storage.top(internalType(type), limit).stream()
                .map(this::entry)
                .toList();
    }

    @Override
    public int getRank(UUID uuid, StreakType type) {
        if (uuid == null || type == null || !plugin.isEnabled()) {
            return -1;
        }
        return storage.rank(uuid, internalType(type));
    }

    @Override
    public ChangeResult setStreak(UUID uuid, String name, StreakType type, int value) {
        return change(uuid, name, type, value, ChangeCause.API, null);
    }

    public ChangeResult setStreakFromAdmin(UUID uuid, String name, StreakType type, int value) {
        return change(uuid, name, type, value, ChangeCause.ADMIN, null);
    }

    @Override
    public ChangeResult resetCurrent(UUID uuid, String name) {
        return reset(uuid, name, ChangeCause.API, null);
    }

    @Override
    public CreditResult creditKill(Player killer, Player victim) {
        if (!plugin.isEnabled()) {
            return CreditResult.UNAVAILABLE;
        }
        if (killer == null || victim == null || !killer.isOnline() || !victim.isOnline()
                || killer.getUniqueId().equals(victim.getUniqueId())) {
            return CreditResult.INVALID;
        }

        CreditKey credit = new CreditKey(killer.getUniqueId(), victim.getUniqueId());
        if (!inFlightCredits.add(credit)) {
            return CreditResult.IN_PROGRESS;
        }
        try {
            return creditKillOnce(killer, victim);
        } finally {
            inFlightCredits.remove(credit);
        }
    }

    private CreditResult creditKillOnce(Player killer, Player victim) {
        long remainingCooldown = remainingCooldownSeconds(killer.getUniqueId(), victim.getUniqueId());
        if (remainingCooldown > 0) {
            notifyCooldown(killer, victim, remainingCooldown);
            return CreditResult.COOLDOWN;
        }
        if (storage.currentStreak(killer.getUniqueId()) == Integer.MAX_VALUE) {
            return CreditResult.INVALID;
        }

        KillCreditPreEvent pre = new KillCreditPreEvent(!Bukkit.isPrimaryThread(), killer, victim,
                storage.find(killer.getUniqueId()).map(this::snapshot).orElse(null));
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) {
            return CreditResult.CANCELLED;
        }

        KillCreditCooldowns.Reservation reservation = reserveCooldown(killer.getUniqueId(), victim.getUniqueId());
        if (reservation.status() == KillCreditCooldowns.Status.COOLDOWN) {
            notifyCooldown(killer, victim, remainingCooldownSeconds(killer.getUniqueId(), victim.getUniqueId()));
            return CreditResult.COOLDOWN;
        }
        if (reservation.status() == KillCreditCooldowns.Status.CAPACITY_REACHED) {
            PublicMessageDelivery.send(messages, killer, "killstreak.capacity-blocked");
            sounds.play(killer, "killstreak.denied");
            return CreditResult.CAPACITY_REACHED;
        }

        boolean saved = false;
        try {
            StreakStorage.Mutation mutation = storage.addKill(killer);
            if (mutation.status() == StreakStorage.MutationStatus.INVALID) {
                return CreditResult.INVALID;
            }
            if (mutation.status() == StreakStorage.MutationStatus.SAVE_FAILED) {
                return CreditResult.SAVE_FAILED;
            }
            saved = true;
            StreakRecord record = mutation.current();
            fireChange(mutation, ChangeCause.KILL, victim.getUniqueId());
            sounds.play(killer, "killstreak.kill");
            if (runRewards(killer, record)) {
                sounds.play(killer, "killstreak.reward");
            }
            maybeBroadcastMilestone(killer, record);
            return CreditResult.CREDITED;
        } finally {
            if (!saved) {
                sameVictimCooldowns.release(reservation);
            }
        }
    }

    private void notifyCooldown(Player killer, Player victim, long remainingCooldown) {
        if (config.getBoolean("same-victim-cooldown.notify-killer", true)) {
            PublicMessageDelivery.send(messages, killer, "killstreak.cooldown-blocked", Map.of(
                    "victim", victim.getName(),
                    "cooldown", publicText.formatNumber(Math.max(1, remainingCooldown))
            ));
        }
        sounds.play(killer, "killstreak.denied");
    }

    private ChangeResult change(UUID uuid, String name, StreakType type, int value,
                                ChangeCause cause, UUID otherPlayer) {
        if (!plugin.isEnabled()) {
            return ChangeResult.UNAVAILABLE;
        }
        if (uuid == null || name == null || name.isBlank() || type == null || value < 0) {
            return ChangeResult.INVALID;
        }
        return completeChange(storage.setStreak(uuid, name, value, internalType(type)), cause, otherPlayer);
    }

    private ChangeResult reset(UUID uuid, String name, ChangeCause cause, UUID otherPlayer) {
        if (!plugin.isEnabled()) {
            return ChangeResult.UNAVAILABLE;
        }
        if (uuid == null || name == null || name.isBlank()) {
            return ChangeResult.INVALID;
        }
        return completeChange(storage.reset(uuid, name), cause, otherPlayer);
    }

    private ChangeResult completeChange(StreakStorage.Mutation mutation, ChangeCause cause, UUID otherPlayer) {
        if (mutation.status() == StreakStorage.MutationStatus.SAVE_FAILED) {
            return ChangeResult.SAVE_FAILED;
        }
        if (mutation.status() == StreakStorage.MutationStatus.INVALID) {
            return ChangeResult.INVALID;
        }
        if (mutation.status() == StreakStorage.MutationStatus.UNCHANGED) {
            return ChangeResult.UNCHANGED;
        }
        fireChange(mutation, cause, otherPlayer);
        return ChangeResult.CHANGED;
    }

    private void fireChange(StreakStorage.Mutation mutation, ChangeCause cause, UUID otherPlayer) {
        if (mutation.status() == StreakStorage.MutationStatus.CHANGED) {
            Bukkit.getPluginManager().callEvent(new KillstreakChangeEvent(!Bukkit.isPrimaryThread(),
                    mutation.previous() == null ? null : snapshot(mutation.previous()),
                    snapshot(mutation.current()), cause, otherPlayer));
        }
    }

    private StreakSnapshot snapshot(StreakRecord record) {
        return new StreakSnapshot(record.uuid(), record.name(), record.currentStreak(),
                record.bestStreak(), record.updatedAt());
    }

    private StreakLeaderboardEntry entry(LeaderboardEntry entry) {
        return new StreakLeaderboardEntry(entry.uuid(), entry.name(), entry.streak(), entry.rank());
    }

    private LeaderboardType internalType(StreakType type) {
        return type == StreakType.CURRENT ? LeaderboardType.CURRENT : LeaderboardType.BEST;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        sameVictimCooldowns.clear(player);
    }

    private long remainingCooldownSeconds(UUID killer, UUID victim) {
        if (!config.getBoolean("same-victim-cooldown.enabled", true)) {
            return 0;
        }

        return sameVictimCooldowns.remainingSeconds(killer, victim, System.currentTimeMillis());
    }

    private KillCreditCooldowns.Reservation reserveCooldown(UUID killer, UUID victim) {
        int seconds = config.getBoolean("same-victim-cooldown.enabled", true)
                ? config.getInt("same-victim-cooldown.seconds", 60) : 0;
        return sameVictimCooldowns.reserve(killer, victim, seconds, System.currentTimeMillis());
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

        broadcast("broadcasts.milestone", Map.of(
                "player", killer.getName(),
                "streak", publicText.formatNumber(record.currentStreak()),
                "best_streak", publicText.formatNumber(record.bestStreak()),
                "milestone", publicText.formatNumber(record.currentStreak())
        ));
        sounds.play(killer, "killstreak.milestone");
    }

    private void maybeBroadcastEndedStreak(Player killer, Player victim, int victimStreak) {
        if (!config.getBoolean("broadcasts.announce-ended-streak", false)
                || victimStreak < config.getAnnounceThreshold()) {
            return;
        }

        broadcast("broadcasts.streak-ended", Map.of(
                "killer", killer.getName(),
                "victim", victim.getName(),
                "streak", publicText.formatNumber(victimStreak)
        ));
    }

    private void broadcast(String path, Map<String, String> placeholders) {
        PublicMessageDelivery.send(messages, Bukkit.getConsoleSender(), path, placeholders);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PublicMessageDelivery.send(messages, viewer, path, placeholders);
        }
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

    private record CreditKey(UUID killer, UUID victim) {
    }
}
