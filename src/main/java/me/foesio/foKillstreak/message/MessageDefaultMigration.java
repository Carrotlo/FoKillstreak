package me.foesio.foKillstreak.message;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Advance known old bundled wording without changing translations or removed sprites. */
public final class MessageDefaultMigration {
    private static final List<String> PUBLIC_TEXT_PATHS = List.of(
            "commands.no-permission", "commands.only-player", "commands.leaderboard-unavailable",
            "killstreak.cooldown-blocked", "broadcasts.milestone", "broadcasts.streak-ended");
    private static final List<String> PUBLIC_TOKEN_PATHS = List.of(
            "tokens.prefix", "tokens.theme", "tokens.muted", "tokens.white", "tokens.good", "tokens.bad");
    private static final List<String> SPRITE_PATHS = List.of(
            "tokens.prefix", "commands.usage-admin", "commands.reloaded",
            "commands.reload-failed", "commands.player-not-found", "commands.streak-set",
            "killstreak.cooldown-blocked", "broadcasts.milestone", "editor.saved",
            "editor.save-failed", "editor.reward-added", "editor.reward-deleted");

    private MessageDefaultMigration() {
    }

    public static boolean migrate(FileConfiguration active, FileConfiguration previousDefaults,
                                  FileConfiguration newestDefaults) {
        boolean changed = false;
        for (String path : SPRITE_PATHS) {
            if (!active.isSet(path) || !previousDefaults.isSet(path) || !newestDefaults.isSet(path)) {
                continue;
            }
            if (Objects.equals(active.get(path), previousDefaults.get(path))
                    && !Objects.equals(active.get(path), newestDefaults.get(path))) {
                active.set(path, newestDefaults.get(path));
                changed = true;
            }
        }
        return changed;
    }

    /** Repairs only malformed values in a versioned older message file. Missing values stay missing. */
    public static List<String> repairLegacyPublic(FileConfiguration active, FileConfiguration newestDefaults) {
        List<String> replacements = new ArrayList<>();
        LinkedHashSet<String> parents = new LinkedHashSet<>();
        for (String path : PUBLIC_TEXT_PATHS) {
            parents.add(path.substring(0, path.indexOf('.')));
        }
        for (String path : PUBLIC_TOKEN_PATHS) {
            parents.add(path.substring(0, path.indexOf('.')));
        }
        for (String parent : parents) {
            if (active.isSet(parent) && !active.isConfigurationSection(parent)) {
                Object invalid = active.get(parent);
                if (newestDefaults.getConfigurationSection(parent) == null) {
                    throw new IllegalStateException("No bundled section to repair " + parent);
                }
                active.set(parent, null);
                active.createSection(parent, newestDefaults.getConfigurationSection(parent).getValues(false));
                replacements.add(parent + "=" + invalid + " (invalid message section) -> newest bundled section");
            }
        }
        for (String path : PUBLIC_TEXT_PATHS) {
            repair(active, newestDefaults, path, true, replacements);
        }
        for (String path : PUBLIC_TOKEN_PATHS) {
            repair(active, newestDefaults, path, false, replacements);
        }
        return List.copyOf(replacements);
    }

    private static void repair(FileConfiguration active, FileConfiguration defaults, String path,
                               boolean allowList, List<String> replacements) {
        if (!active.isSet(path)) {
            return;
        }
        Object value = active.get(path);
        boolean valid = value instanceof String || allowList && value instanceof List<?> lines
                && lines.stream().allMatch(String.class::isInstance);
        if (valid) {
            return;
        }
        Object replacement = defaults.get(path);
        if (replacement == null) {
            throw new IllegalStateException("No bundled default to repair " + path);
        }
        active.set(path, replacement);
        replacements.add(path + "=" + value + " (invalid message type) -> " + replacement);
    }
}
