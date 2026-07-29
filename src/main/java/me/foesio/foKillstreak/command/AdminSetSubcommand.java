package me.foesio.foKillstreak.command;

import me.foesio.core.command.FoAdminCommandContext;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.foKillstreak.data.LeaderboardType;
import me.foesio.foKillstreak.data.StreakRecord;
import me.foesio.foKillstreak.data.StreakStorage;
import me.foesio.foKillstreak.message.MessageFallbacks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AdminSetSubcommand {
    private static final List<String> STREAK_TYPES = List.of("best", "current");

    private final StreakStorage storage;

    private AdminSetSubcommand(StreakStorage storage) {
        this.storage = storage;
    }

    public static FoAdminSubcommand create(StreakStorage storage) {
        AdminSetSubcommand command = new AdminSetSubcommand(storage);
        return FoAdminSubcommand.builder("set", command::execute)
                .usage("set <player> <streak> <best|current>")
                .tabCompleter(command::tabComplete)
                .build();
    }

    private boolean execute(FoAdminCommandContext context) {
        if (context.args().length != 4) {
            context.messages().send(context.sender(), "commands.usage-set", MessageFallbacks.string("commands.usage-set"));
            return true;
        }

        TargetPlayer target = resolveTarget(context.arg(1));
        if (target == null) {
            context.messages().send(context.sender(), "commands.player-not-found", MessageFallbacks.string("commands.player-not-found"));
            return true;
        }

        int streak = parseNonNegativeInt(context.arg(2));
        if (streak < 0) {
            context.messages().send(context.sender(), "commands.invalid-streak", MessageFallbacks.string("commands.invalid-streak"));
            return true;
        }

        LeaderboardType type = parseType(context.arg(3));
        if (type == null) {
            context.messages().send(context.sender(), "commands.invalid-streak-type", MessageFallbacks.string("commands.invalid-streak-type"));
            return true;
        }

        storage.setStreak(target.uuid(), target.name(), streak, type);
        context.messages().send(context.sender(), "commands.streak-set", MessageFallbacks.string("commands.streak-set"), Map.of(
                "player", target.name(),
                "streak", String.valueOf(streak),
                "type", type == LeaderboardType.CURRENT ? "current" : "best"
        ));
        return true;
    }

    private List<String> tabComplete(FoAdminCommandContext context) {
        if (context.args().length == 2) {
            return knownPlayerSuggestions();
        }

        if (context.args().length == 3) {
            return List.of("0", "5", "10", "25", "50");
        }

        if (context.args().length == 4) {
            return STREAK_TYPES;
        }

        return List.of();
    }

    private TargetPlayer resolveTarget(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return new TargetPlayer(online.getUniqueId(), online.getName());
        }

        Optional<StreakRecord> stored = storage.findByName(input);
        if (stored.isPresent()) {
            StreakRecord record = stored.get();
            return new TargetPlayer(record.uuid(), record.name());
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && name.equalsIgnoreCase(input)) {
                return new TargetPlayer(offlinePlayer.getUniqueId(), name);
            }
        }

        return null;
    }

    private LeaderboardType parseType(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "current" -> LeaderboardType.CURRENT;
            case "best" -> LeaderboardType.BEST;
            default -> null;
        };
    }

    private int parseNonNegativeInt(String input) {
        var parsed = LargeNumberParser.parse(input);
        if (parsed.isEmpty() || parsed.get().signum() < 0 || parsed.get().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            return -1;
        }

        try {
            return parsed.get().setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException exception) {
            return -1;
        }
    }

    private List<String> knownPlayerSuggestions() {
        Set<String> suggestions = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            suggestions.add(player.getName());
        }
        suggestions.addAll(storage.knownPlayerNames());
        return new ArrayList<>(suggestions);
    }

    private record TargetPlayer(UUID uuid, String name) {
    }
}
