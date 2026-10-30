package me.foesio.foKillstreak.config;

import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.foKillstreak.gui.LeaderboardGuiContract;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

/** One-time translation of the released leaderboard resource into Core's public GUI schema. */
final class LeaderboardGuiMigration {
    static final int CURRENT_SCHEMA_VERSION = 3;

    private static final List<String> NATIVE_PATHS = List.of(
            "title", "body-width", "limit", "symbol", "line-format", "empty-player",
            "empty-streak", "first-color", "second-color", "third-color", "default-color",
            "title-color", "inactive-button-color", "tooltip-color", "current-button",
            "best-button", "selected-button-color", "selected-current-button",
            "selected-best-button", "switch-tooltip");

    private LeaderboardGuiMigration() {
    }

    static Result migrate(YamlConfiguration old, YamlConfiguration oldDefaults,
                          YamlConfiguration guiDefaults, YamlConfiguration textDefaults)
            throws InvalidConfigurationException {
        int version = old.getInt("schema-version", 1);
        if (version < 1 || version > 2) {
            throw new InvalidConfigurationException("Unsupported leaderboard schema " + version);
        }
        YamlConfiguration gui = copy(guiDefaults);
        YamlConfiguration text = copy(textDefaults);
        List<String> replacements = new ArrayList<>();

        transfer(old, oldDefaults, gui, "title", "title", true);
        transfer(old, oldDefaults, gui, "rows", "rows", true);
        transfer(old, oldDefaults, gui, version == 1 ? "filler-material" : "filler.material", "filler.material", true);
        if (version >= 2) {
            for (String path : List.of("filler.name", "filler.lore", "filler.custom-model-data")) {
                transfer(old, oldDefaults, gui, path, path, false);
            }
            normalizeModel(gui, "filler.custom-model-data");
        }
        for (String button : List.of("current", "best")) {
            for (String key : List.of("slot", "material", "name")) {
                transfer(old, oldDefaults, gui, button + "." + key, "buttons." + button + "." + key,
                        !key.equals("name"));
            }
            if (version >= 2) {
                transfer(old, oldDefaults, gui, button + ".custom-model-data",
                        "buttons." + button + ".custom-model-data", false);
                normalizeModel(gui, "buttons." + button + ".custom-model-data");
                String path = button + ".lore";
                if (old.isSet(path) && !old.get(path).equals(oldDefaults.get(path))) {
                    Object value = old.get(path);
                    if (value instanceof List<?> lines) {
                        gui.set("buttons." + button + ".lore", lines.stream()
                                .map(line -> "{leaderboard}".equals(line) ? "{options}" : line).toList());
                    } else {
                        gui.set("buttons." + button + ".lore", value);
                    }
                } else if (!old.isSet(path) && oldDefaults.isSet(path)) {
                    gui.set("buttons." + button + ".lore", null);
                }
            }
        }
        if (version >= 2) {
            transfer(old, oldDefaults, text, "leaderboard-limit", "inventory.limit", false);
            transfer(old, oldDefaults, text, "lines.separator", "inventory.separator", false);
            for (String nativeKey : NATIVE_PATHS) {
                transfer(old, oldDefaults, text, "native." + nativeKey, "native." + nativeKey, false);
            }
        }
        for (String key : List.of("entry", "empty", "position", "unranked")) {
            transfer(old, oldDefaults, text, "lines." + key, "inventory." + key, false);
        }

        validateAndRepairGui(gui, guiDefaults, replacements);
        validateAndRepairText(text, textDefaults, replacements);
        return new Result(version, gui, text, List.copyOf(replacements));
    }

    private static void transfer(YamlConfiguration old, YamlConfiguration oldDefaults,
                                 YamlConfiguration target, String oldPath, String newPath, boolean required)
            throws InvalidConfigurationException {
        if (!old.isSet(oldPath)) {
            if (oldDefaults.isSet(oldPath)) {
                if (required) {
                    throw new InvalidConfigurationException("Required legacy key is missing: " + oldPath);
                }
                target.set(newPath, null);
            }
            return;
        }
        Object value = old.get(oldPath);
        if (!oldDefaults.isSet(oldPath) || !value.equals(oldDefaults.get(oldPath))) {
            target.set(newPath, value);
        }
    }

    private static void validateAndRepairGui(YamlConfiguration gui, YamlConfiguration defaults,
                                             List<String> replacements) throws InvalidConfigurationException {
        for (int attempt = 0; attempt < 32; attempt++) {
            try {
                PublicGuiSchema.parse("killstreak.leaderboard", "guis/leaderboard.yml", gui,
                        LeaderboardGuiContract.contract());
                return;
            } catch (GuiDefinitionException exception) {
                String path = exception.key();
                if (path == null || path.isBlank()) {
                    throw new InvalidConfigurationException("Cannot isolate invalid migrated GUI field " + path
                            + ": " + exception.getMessage());
                }
                Object invalid = gui.get(path);
                Object replacement = defaults.get(path);
                if (java.util.Objects.equals(invalid, replacement)) {
                    if (path.equals("buttons.best.slot")
                            && !java.util.Objects.equals(gui.get("buttons.current.slot"),
                                    defaults.get("buttons.current.slot"))) {
                        path = "buttons.current.slot";
                    } else if (path.endsWith(".slot") && !java.util.Objects.equals(gui.get("rows"),
                            defaults.get("rows"))) {
                        path = "rows";
                    } else {
                        throw new InvalidConfigurationException("Bundled default did not repair " + path
                                + ": " + exception.getMessage());
                    }
                    invalid = gui.get(path);
                    replacement = defaults.get(path);
                }
                replacements.add(path + "=" + invalid + " (" + exception.getMessage() + ") -> " + replacement);
                gui.set(path, replacement);
            }
        }
        throw new InvalidConfigurationException("Too many invalid leaderboard GUI fields");
    }

    private static void normalizeModel(YamlConfiguration gui, String path) {
        Object model = gui.get(path);
        if (model instanceof String text && text.matches("[0-9]+")) {
            try {
                gui.set(path, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                // Field-level validation applies the newest bundled default.
            }
        }
    }

    private static void validateAndRepairText(YamlConfiguration text, YamlConfiguration defaults,
                                              List<String> replacements) throws InvalidConfigurationException {
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || !text.isSet(path)) {
                continue;
            }
            Object value = text.get(path);
            Object expected = defaults.get(path);
            boolean valid = expected instanceof String ? value instanceof String
                    : expected instanceof Integer ? value instanceof Integer
                    : expected instanceof List<?> && value instanceof List<?> lines
                    && lines.stream().allMatch(String.class::isInstance);
            if (valid && value instanceof Integer number) {
                valid = path.endsWith("limit") ? number >= 1 && number <= 100
                        : path.endsWith("columns") ? number >= 1 && number <= 2
                        : number >= 1 && number <= 1024;
            }
            if (valid && path.equals("native.button-order")) {
                valid = value.equals(List.of("current", "best")) || value.equals(List.of("best", "current"));
            }
            if (valid && path.endsWith("-color")) {
                valid = validColor((String) value);
            }
            if (valid && path.equals("number-locale")) {
                valid = !((String) value).isBlank();
                try {
                    new java.util.Locale.Builder().setLanguageTag((String) value).build();
                } catch (java.util.IllformedLocaleException exception) {
                    valid = false;
                }
            }
            if (!valid) {
                replacements.add(path + "=" + value + " (invalid type or range) -> " + expected);
                text.set(path, expected);
            }
        }
        for (String path : List.of("inventory.limit", "native.limit", "native.body-width")) {
            if (!text.isSet(path)) {
                throw new InvalidConfigurationException("Required leaderboard text field is missing: " + path);
            }
        }
    }

    static boolean validColor(String value) {
        return value != null && (value.matches("(?i)&?#[0-9a-f]{6}")
                || value.matches("\\{(?:theme|muted|white|yellow|good|bad)}")
                || value.matches("(?i)[&§][0-9a-f]")
                || value.matches("(?i)§x(?:§[0-9a-f]){6}"));
    }

    private static YamlConfiguration copy(YamlConfiguration source) throws InvalidConfigurationException {
        YamlConfiguration copy = new YamlConfiguration();
        copy.loadFromString(source.saveToString());
        return copy;
    }

    record Result(int fromVersion, YamlConfiguration gui, YamlConfiguration text, List<String> replacements) {
    }
}
