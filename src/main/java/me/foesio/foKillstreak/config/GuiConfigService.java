package me.foesio.foKillstreak.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.text.NumberFormat;
import java.util.function.Function;
import java.util.logging.Logger;

/** Owns leaderboard text and the one-time split from the released GUI layout. */
public final class GuiConfigService {
    private static final String GUI_RESOURCE = "guis/leaderboard.yml";
    private static final String TEXT_RESOURCE = "dialogs/leaderboard.yml";
    private static final String VERSION_FILE = "guis/.leaderboard-schema-version";

    private final Path dataRoot;
    private final Function<String, InputStream> resources;
    private final Logger logger;
    private YamlConfiguration leaderboardText;
    private YamlConfiguration stagedText;
    private boolean readyForGui;
    private int upgradedLegacyGuiFrom;

    public GuiConfigService(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath(), plugin::getResource, plugin.getLogger());
    }

    GuiConfigService(Path dataRoot, Function<String, InputStream> resources, Logger logger) {
        this.dataRoot = dataRoot;
        this.resources = resources;
        this.logger = logger;
    }

    public void initialize(boolean freshInstall) {
        upgradedLegacyGuiFrom = 0;
        Path gui = path(GUI_RESOURCE);
        Path text = path(TEXT_RESOURCE);
        try {
            if (freshInstall) {
                installDefault(GUI_RESOURCE, gui);
                installDefault(TEXT_RESOURCE, text);
                Files.writeString(path(VERSION_FILE), String.valueOf(LeaderboardGuiMigration.CURRENT_SCHEMA_VERSION),
                        StandardCharsets.UTF_8);
            } else if (Files.isRegularFile(gui)) {
                YamlConfiguration active = read(gui);
                if (active.isSet("current") || active.isSet("best") || active.isSet("schema-version")) {
                    int originalVersion = active.getInt("schema-version", 1);
                    migrate(gui, text, active);
                    upgradedLegacyGuiFrom = originalVersion;
                }
            }
            load();
            readyForGui = true;
        } catch (IOException | InvalidConfigurationException | IllegalStateException exception) {
            readyForGui = false;
            logger.severe("Leaderboard configuration unavailable; owner files were retained: "
                    + exception.getMessage());
        }
    }

    /** A current-schema reload reads only; bad candidates leave the previous cache active. */
    public void load() {
        leaderboardText = readValidatedText(true);
        readyForGui = true;
    }

    public void stage() {
        stagedText = readValidatedText(false);
    }

    public void commitStaged() {
        if (stagedText == null) {
            throw new IllegalStateException("No validated leaderboard text is staged");
        }
        leaderboardText = stagedText;
        stagedText = null;
        readyForGui = true;
    }

    private YamlConfiguration readValidatedText(boolean startup) {
        try {
            YamlConfiguration gui = read(path(GUI_RESOURCE));
            if (gui.isSet("current") || gui.isSet("best") || gui.isSet("schema-version")) {
                throw new InvalidConfigurationException("Legacy leaderboard GUI still needs migration");
            }
            YamlConfiguration candidate = read(path(TEXT_RESOURCE));
            YamlConfiguration defaults = bundled(TEXT_RESOURCE);
            for (int attempt = 0; attempt <= defaults.getKeys(true).size(); attempt++) {
                String invalid = invalidTextField(candidate, defaults);
                if (invalid == null) {
                    return candidate;
                }
                if (!startup || !defaults.isSet(invalid)) {
                    throw new InvalidConfigurationException(invalid + " is invalid");
                }
                Object replacement = defaults.get(invalid);
                logger.warning("Leaderboard startup substituted bundled default for invalid "
                        + invalid + ": " + candidate.get(invalid) + " -> " + replacement);
                candidate.set(invalid, replacement);
            }
            throw new InvalidConfigurationException("Too many invalid leaderboard text fields");
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Invalid " + TEXT_RESOURCE + ": " + exception.getMessage(), exception);
        }
    }

    public boolean available() {
        return readyForGui && leaderboardText != null;
    }

    /** The released v1 resource had no sounds.yml; a missing v2 sound file is ambiguous. */
    public int upgradedLegacyGuiFrom() {
        return upgradedLegacyGuiFrom;
    }

    public String getString(String path, String ignoredFallback) {
        return leaderboardText == null ? "" : leaderboardText.getString(path, "");
    }

    public int getInt(String path, int ignoredFallback) {
        return leaderboardText == null ? 0 : leaderboardText.getInt(path, 0);
    }

    public List<String> getStringList(String path) {
        return leaderboardText == null ? List.of() : leaderboardText.getStringList(path);
    }

    public boolean has(String path) {
        return leaderboardText != null && leaderboardText.isSet(path);
    }

    public String formatNumber(long value) {
        if (leaderboardText == null) {
            return Long.toString(value);
        }
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag(
                getString("number-locale", "")));
        return format.format(value);
    }

    private void migrate(Path gui, Path text, YamlConfiguration old)
            throws IOException, InvalidConfigurationException {
        if (Files.exists(text)) {
            throw new IOException("Existing " + text + " needs manual review before the legacy split");
        }
        int oldVersion = old.getInt("schema-version", 1);
        YamlConfiguration oldDefaults = bundled("migration/leaderboard-v" + oldVersion + ".yml");
        LeaderboardGuiMigration.Result result = LeaderboardGuiMigration.migrate(
                old, oldDefaults, bundled(GUI_RESOURCE), bundled(TEXT_RESOURCE));
        validateText(result.text());
        Path backup = gui.resolveSibling(gui.getFileName() + ".gui-v" + oldVersion + "-backup");
        byte[] oldGuiBytes = Files.readAllBytes(gui);
        byte[] oldTextBytes = Files.exists(text) ? Files.readAllBytes(text) : null;
        if (Files.notExists(backup)) {
            Files.copy(gui, backup);
        }
        try {
            replace(gui, result.gui().saveToString());
            replace(text, result.text().saveToString());
            replace(path(VERSION_FILE), String.valueOf(LeaderboardGuiMigration.CURRENT_SCHEMA_VERSION));
        } catch (IOException exception) {
            Files.write(gui, oldGuiBytes);
            if (oldTextBytes == null) {
                Files.deleteIfExists(text);
            } else {
                Files.write(text, oldTextBytes);
            }
            throw exception;
        }
        result.replacements().forEach(value -> logger.warning(
                "Leaderboard migration substituted bundled default: " + value));
        logger.info("Upgraded leaderboard GUI schema " + result.fromVersion()
                + " -> " + LeaderboardGuiMigration.CURRENT_SCHEMA_VERSION + "; backup: " + backup.getFileName());
    }

    private void validateText(YamlConfiguration value) throws IOException, InvalidConfigurationException {
        String invalid = invalidTextField(value, bundled(TEXT_RESOURCE));
        if (invalid != null) {
            throw new InvalidConfigurationException(invalid + " is invalid");
        }
    }

    private String invalidTextField(YamlConfiguration value, YamlConfiguration defaults) {
        for (String field : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(field) || !value.isSet(field)) {
                continue;
            }
            Object expected = defaults.get(field);
            Object actual = value.get(field);
            boolean valid = expected instanceof String ? actual instanceof String
                    : expected instanceof Integer ? actual instanceof Integer
                    : expected instanceof List<?> && actual instanceof List<?> lines
                    && lines.stream().allMatch(String.class::isInstance);
            if (!valid) {
                return field;
            }
            if (field.endsWith("-color") && !LeaderboardGuiMigration.validColor((String) actual)) {
                return field;
            }
        }
        for (String path : List.of("inventory.limit", "native.limit", "native.body-width",
                "native.button-width", "native.columns")) {
            if (!value.isInt(path) || value.getInt(path) < 1 || value.getInt(path) >
                    (path.endsWith("width") ? 1024 : path.endsWith("columns") ? 2 : 100)) {
                return path;
            }
        }
        if (!value.isList("native.button-order")
                || !value.getStringList("native.button-order").equals(List.of("current", "best"))
                && !value.getStringList("native.button-order").equals(List.of("best", "current"))) {
            return "native.button-order";
        }
        for (String path : List.of("inventory.entry", "inventory.empty", "inventory.position",
                "inventory.unranked", "number-locale", "native.title", "native.line-format", "native.current-button",
                "native.best-button", "native.selected-current-button", "native.selected-best-button")) {
            if (!value.isString(path)) {
                return path;
            }
        }
        if (value.getString("number-locale").isBlank()) {
            return "number-locale";
        }
        try {
            new Locale.Builder().setLanguageTag(value.getString("number-locale")).build();
        } catch (java.util.IllformedLocaleException exception) {
            return "number-locale";
        }
        return null;
    }

    private void installDefault(String resource, Path destination) throws IOException {
        if (Files.exists(destination)) {
            throw new IOException("Fresh install has existing " + destination);
        }
        Files.createDirectories(destination.getParent());
        try (InputStream stream = resources.apply(resource)) {
            if (stream == null) {
                throw new IOException("Missing bundled " + resource);
            }
            Files.copy(stream, destination);
        }
    }

    private YamlConfiguration bundled(String resource) throws IOException, InvalidConfigurationException {
        try (InputStream stream = resources.apply(resource)) {
            if (stream == null) {
                throw new IOException("Missing bundled " + resource);
            }
            YamlConfiguration value = new YamlConfiguration();
            value.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return value;
        }
    }

    private YamlConfiguration read(Path file) throws IOException, InvalidConfigurationException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Missing " + file);
        }
        YamlConfiguration value = new YamlConfiguration();
        value.load(file.toFile());
        return value;
    }

    private void replace(Path destination, String contents) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path path(String relative) {
        return dataRoot.resolve(relative);
    }
}
