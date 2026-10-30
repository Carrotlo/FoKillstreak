package me.foesio.foKillstreak;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.gui.publicgui.GuiLoadReport;
import me.foesio.core.gui.publicgui.PublicGuiService;
import me.foesio.core.placeholder.FoPlaceholders;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foKillstreak.command.AdminSetSubcommand;
import me.foesio.foKillstreak.api.KillstreakService;
import me.foesio.foKillstreak.command.PlayerCommand;
import me.foesio.foKillstreak.config.ConfigService;
import me.foesio.foKillstreak.config.GuiConfigService;
import me.foesio.foKillstreak.data.LeaderboardType;
import me.foesio.foKillstreak.data.StreakStorage;
import me.foesio.foKillstreak.gui.KillstreakGuiManager;
import me.foesio.foKillstreak.gui.LeaderboardGuiContract;
import me.foesio.foKillstreak.listener.KillstreakListener;
import me.foesio.foKillstreak.message.MessageFallbacks;
import me.foesio.foKillstreak.message.MessageDefaultMigration;
import me.foesio.foKillstreak.message.MessageMigrationBackup;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class FoKillstreak extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "fokillstreak";
    private static final int BSTATS_PLUGIN_ID = 32977;

    private FoCoreContext core;
    private ConfigService configService;
    private GuiConfigService guiConfigService;
    private PublicGuiService publicGuis;
    private FoMessageService messageService;
    private StreakStorage streakStorage;
    private KillstreakListener killstreakService;
    private KillstreakGuiManager guiManager;
    private UpdateNoticeService updateNotices;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;

    @Override
    public void onEnable() {
        boolean freshInstall = Files.notExists(getDataFolder().toPath());
        configService = new ConfigService(this);
        guiConfigService = new GuiConfigService(this);

        configService.load();
        if (freshInstall) {
            installFreshResource("messages.yml");
            installFreshResource("sounds.yml");
        }
        guiConfigService.initialize(freshInstall);
        if (guiConfigService.upgradedLegacyGuiFrom() == 1) {
            installFreshResource("sounds.yml");
        }
        refreshCoreContext();
        boolean messageMigrationReady = prepareMessageMigration(freshInstall);
        messageService = FoMessageService.loadPreservingRemovals(this,
                messageMigrationReady ? messageMigrations() : FoMessageMigrations.none());
        if (messageMigrationReady) {
            migrateSprites();
        }
        updateNotices = core.createUpdateNotices(messageService, MODRINTH_PROJECT_ID, adminSounds).start();

        streakStorage = new StreakStorage(this);
        if (!streakStorage.open()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        publicGuis = new PublicGuiService(this)
                .menu(KillstreakGuiManager.LEADERBOARD_MENU_ID,
                        getDataFolder().toPath().resolve("guis/leaderboard.yml"),
                        LeaderboardGuiContract.contract())
                .action("leaderboard.current", click -> guiManager.openLeaderboardTab(click.player(), LeaderboardType.CURRENT))
                .action("leaderboard.best", click -> guiManager.openLeaderboardTab(click.player(), LeaderboardType.BEST))
                .entryPoint(KillstreakGuiManager.LEADERBOARD_ENTRY_POINT, KillstreakGuiManager.LEADERBOARD_MENU_ID)
                .requireButton(KillstreakGuiManager.LEADERBOARD_MENU_ID, "current")
                .requireButton(KillstreakGuiManager.LEADERBOARD_MENU_ID, "best");
        guiManager = new KillstreakGuiManager(this, configService, guiConfigService, publicGuis, messageService, streakStorage, core.nativeDialogs(), sounds, editorSounds, guiSounds);
        publicGuis.start();
        if (guiConfigService.available()) {
            publicGuis.reload();
        }

        killstreakService = new KillstreakListener(this, configService, guiConfigService,
                messageService, streakStorage, sounds);
        getServer().getPluginManager().registerEvents(killstreakService, this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        registerCommands();
        getServer().getServicesManager().register(KillstreakService.class, killstreakService, this, ServicePriority.Normal);
        registerPlaceholderApi();
    }

    @Override
    public void onDisable() {
        if (killstreakService != null) {
            getServer().getServicesManager().unregister(KillstreakService.class, killstreakService);
            killstreakService = null;
        }
        if (publicGuis != null) {
            publicGuis.close();
            publicGuis = null;
        }
        if (streakStorage != null) {
            streakStorage.close();
        }
        if (core != null) {
            core.close();
            core = null;
        }
        sounds = null;
        adminSounds = null;
        editorSounds = null;
        guiSounds = null;
    }

    public boolean reloadPluginFiles() {
        FoReloadResult result = FoReloadRegistry.create()
                .add("config", configService::load)
                .add("leaderboard text", guiConfigService::stage)
                .add("public GUIs", () -> {
                    GuiLoadReport report = publicGuis.reload();
                    if (!report.successful()) {
                        throw new IllegalStateException("Leaderboard GUI validation failed; previous layout remains active");
                    }
                    guiConfigService.commitStaged();
                })
                .add("native dialogs", this::refreshCoreContext)
                .add("sounds", () -> {
                    if (!sounds.reloadChecked()) {
                        throw new IllegalStateException("Invalid sounds.yml; previous sounds remain active");
                    }
                })
                .addMessages(messageService)
                .add("leaderboard dialogs", () -> guiManager.reloadNativeDialogs(core.nativeDialogs()))
                .reload();

        if (!result.successful()) {
            getLogger().severe("Failed to reload " + result.failedStep() + ": " + result.errorMessage());
            return false;
        }
        return true;
    }

    public KillstreakGuiManager gui() {
        return guiManager;
    }

    public FoCoreContext core() {
        return core;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    private void refreshCoreContext() {
        FoCoreContext previousCore = core;
        FoCoreContext refreshedCore = FoPluginCore.create(this, configService.nativeDialogSettings());
        core = refreshedCore;
        if (sounds == null) {
            sounds = FoSoundService.loadPreservingRemovals(this);
            adminSounds = FoAdminSounds.create(sounds);
            editorSounds = FoEditorSounds.create(sounds);
            guiSounds = FoGuiSounds.create(sounds);
        }
        if (previousCore != null) {
            previousCore.close();
        }
        core.metrics(BSTATS_PLUGIN_ID);
        core.warnIfNativeDialogsUnavailable();
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .remove("updates")
                .remove("commands.version")
                .remove("native-dialogs.fallback-warning")
                .remove("editor.prompt-cooldown")
                .remove("editor.prompt-milestones")
                .remove("editor.prompt-reward-search")
                .remove("editor.prompt-reward-streak")
                .remove("editor.prompt-reward-command")
                .remove("gui.rewards.search-name")
                .remove("gui.rewards.search-lore")
                .remove("gui.rewards.search-none")
                .remove("gui.rewards.clear-search-name")
                .remove("gui.rewards.clear-search-lore")
                .remove("gui.rewards.previous-name")
                .remove("gui.rewards.next-name")
                .remove("gui.rewards.page-lore")
                .remove("gui.rewards.back-name")
                .remove("gui.rewards.back-lore")
                .build();
    }

    private void migrateSprites() {
        YamlConfiguration previousMessages = bundledYaml("migration/messages-v1.yml");
        YamlConfiguration newestMessages = bundledYaml("messages.yml");
        messageService.migrateToVersion(core.migrations(), 1,
                config -> MessageDefaultMigration.migrate(config, previousMessages, newestMessages));
        messageService.migrateToVersion(core.migrations(), 2, this::migrateEditorButtonNames);
        messageService.migrateToVersion(core.migrations(), 3, this::migrateAnnounceThresholdLore);
        messageService.migrateToVersion(core.migrations(), 4, config -> {
            List<String> repaired = MessageDefaultMigration.repairLegacyPublic(config, newestMessages);
            repaired.forEach(value -> getLogger().warning("Message migration substituted bundled default: " + value));
            boolean changed = replaceExact(config, "commands.reloaded",
                    "{prefix}:emerald: {good}Reloaded {theme}config.yml{muted}, {theme}messages.yml{muted}, and {theme}guis/{muted}.",
                    "{prefix}:emerald: {good}Reloaded {theme}config.yml{muted}, {theme}messages.yml{muted}, {theme}sounds.yml{muted}, {theme}guis/{muted}, and {theme}dialogs/{muted}.");
            if (!config.isSet("commands.leaderboard-unavailable")) {
                config.set("commands.leaderboard-unavailable",
                        newestMessages.getString("commands.leaderboard-unavailable"));
                changed = true;
            }
            return changed || !repaired.isEmpty();
        });
        messageService.reload();
    }

    private boolean prepareMessageMigration(boolean freshInstall) {
        try {
            MessageMigrationBackup.prepare(getDataFolder().toPath().resolve("messages.yml"),
                    getConfig().getInt("config-version", 0), freshInstall);
            return true;
        } catch (IOException exception) {
            getLogger().severe("Could not back up messages.yml; message migration remains pending: "
                    + exception.getMessage());
            return false;
        }
    }

    private YamlConfiguration bundledYaml(String resource) {
        try (InputStream stream = getResource(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled " + resource);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read bundled " + resource, exception);
        }
    }

    private void installFreshResource(String resource) {
        Path target = getDataFolder().toPath().resolve(resource);
        if (Files.notExists(target)) {
            saveResource(resource, false);
        }
    }

    private boolean migrateAnnounceThresholdLore(FileConfiguration config) {
        List<String> oldLore = List.of(
                "{white}Click to edit in chat.",
                "{white}Current: {theme}{threshold} kills"
        );
        if (!config.isList("gui.admin.announce-threshold-lore")
                || !config.getStringList("gui.admin.announce-threshold-lore").equals(oldLore)) {
            return false;
        }
        config.set("gui.admin.announce-threshold-lore", List.of(
                "{white}Click to edit in chat.",
                "{white}Sets the ended-streak announcement threshold.",
                "{white}Current: {theme}{threshold} kills"
        ));
        return true;
    }

    private boolean migrateEditorButtonNames(FileConfiguration config) {
        boolean changed = false;
        changed |= replaceExact(config, "gui.admin.player-death-reset-enabled-name", "{good}ᴘᴠᴘ ᴅᴇᴀᴛʜ ʀᴇꜱᴇᴛꜱ", "{good}PLAYER DEATH RESETS");
        changed |= replaceExact(config, "gui.admin.player-death-reset-disabled-name", "{bad}ᴀʟʟ ᴅᴇᴀᴛʜꜱ ʀᴇꜱᴇᴛ", "{bad}ALL DEATHS RESET");
        changed |= replaceExact(config, "gui.admin.broadcasts-enabled-name", "{good}ᴍɪʟᴇꜱᴛᴏɴᴇ ʙʀᴏᴀᴅᴄᴀꜱᴛꜱ", "{good}MILESTONE BROADCASTS");
        changed |= replaceExact(config, "gui.admin.broadcasts-disabled-name", "{bad}ᴍɪʟᴇꜱᴛᴏɴᴇ ʙʀᴏᴀᴅᴄᴀꜱᴛꜱ", "{bad}MILESTONE BROADCASTS");
        changed |= replaceExact(config, "gui.admin.ended-streak-enabled-name", "{good}ᴇɴᴅᴇᴅ-ꜱᴛʀᴇᴀᴋ ʙʀᴏᴀᴅᴄᴀꜱᴛꜱ", "{good}ENDED-STREAK BROADCASTS");
        changed |= replaceExact(config, "gui.admin.ended-streak-disabled-name", "{bad}ᴇɴᴅᴇᴅ-ꜱᴛʀᴇᴀᴋ ʙʀᴏᴀᴅᴄᴀꜱᴛꜱ", "{bad}ENDED-STREAK BROADCASTS");
        changed |= replaceExact(config, "gui.admin.announce-threshold-name", "{theme}ᴀɴɴᴏᴜɴᴄᴇ ᴛʜʀᴇꜱʜᴏʟᴅ", "{theme}ANNOUNCE THRESHOLD");
        changed |= replaceExact(config, "gui.admin.cooldown-enabled-name", "{good}ꜱᴀᴍᴇ-ᴠɪᴄᴛɪᴍ ᴄᴏᴏʟᴅᴏᴡɴ", "{good}SAME-VICTIM COOLDOWN");
        changed |= replaceExact(config, "gui.admin.cooldown-disabled-name", "{bad}ꜱᴀᴍᴇ-ᴠɪᴄᴛɪᴍ ᴄᴏᴏʟᴅᴏᴡɴ", "{bad}SAME-VICTIM COOLDOWN");
        changed |= replaceExact(config, "gui.admin.cooldown-seconds-name", "{theme}ᴄᴏᴏʟᴅᴏᴡɴ ꜱᴇᴄᴏɴᴅꜱ", "{theme}COOLDOWN SECONDS");
        changed |= replaceExact(config, "gui.admin.milestones-name", "{theme}ᴍɪʟᴇꜱᴛᴏɴᴇꜱ", "{theme}MILESTONES");
        changed |= replaceExact(config, "gui.admin.rewards-name", "{theme}ʀᴇᴡᴀʀᴅꜱ", "{theme}REWARDS");
        changed |= replaceExact(config, "gui.admin.reload-name", "{theme}ʀᴇʟᴏᴀᴅ ꜰɪʟᴇꜱ", "{theme}RELOAD FILES");
        changed |= replaceExact(config, "gui.rewards.reward-name", "{theme}{streak} ᴋɪʟʟꜱ", "{theme}{streak} KILLS");
        changed |= replaceExact(config, "gui.rewards.empty-name", "{bad}ɴᴏ ʀᴇᴡᴀʀᴅꜱ", "{bad}NO REWARDS");
        changed |= replaceExact(config, "gui.rewards.add-name", "{good}ᴀᴅᴅ ʀᴇᴡᴀʀᴅ", "{good}ADD REWARD");
        changed |= replaceExact(config, "gui.reward-sender.console-name", "{theme}ᴄᴏɴꜱᴏʟᴇ", "{theme}CONSOLE");
        changed |= replaceExact(config, "gui.reward-sender.player-name", "{theme}ᴘʟᴀʏᴇʀ", "{theme}PLAYER");
        changed |= replaceExact(config, "gui.reward-edit.streak-name", "{theme}ᴋɪʟʟꜱᴛʀᴇᴀᴋ", "{theme}KILLSTREAK");
        changed |= replaceExact(config, "gui.reward-edit.sender-name", "{theme}ꜱᴇɴᴅᴇʀ", "{theme}SENDER");
        changed |= replaceExact(config, "gui.reward-edit.command-name", "{theme}ᴄᴏᴍᴍᴀɴᴅ", "{theme}COMMAND");
        changed |= replaceExact(config, "gui.reward-edit.delete-name", "{bad}ᴅᴇʟᴇᴛᴇ", "{bad}DELETE");
        changed |= replaceExact(config, "gui.reward-delete.confirm-name", "{good}ᴄᴏɴꜰɪʀᴍ", "{good}CONFIRM");
        changed |= replaceExact(config, "gui.reward-delete.cancel-name", "{bad}ᴄᴀɴᴄᴇʟ", "{bad}CANCEL");
        return changed;
    }

    private boolean replaceExact(FileConfiguration config, String path, String oldValue, String newValue) {
        if (!oldValue.equals(config.getString(path))) {
            return false;
        }
        config.set(path, newValue);
        return true;
    }

    private void registerCommands() {
        FoAdminCommand.builder(this, messageService)
                .commandName("fokillstreakadmin")
                .permission("fokillstreak.admin")
                .versionCommand(false)
                .adminSounds(adminSounds)
                .adminMessages(adminMessages())
                .addSubcommand(versionSubcommand())
                .addSubcommand(reloadSubcommand())
                .addSubcommand(editorSubcommand())
                .addSubcommand(AdminSetSubcommand.create(streakStorage, killstreakService, adminSounds))
                .register();

        PlayerCommand playerCommand = new PlayerCommand(messageService, guiManager, adminSounds);
        PluginCommand player = getCommand("killstreak");
        if (player != null) {
            player.setExecutor(playerCommand);
        }
    }

    private FoAdminMessages adminMessages() {
        return FoAdminMessages.builder()
                .generalNoPermission("commands.no-permission", "{prefix}{bad}You do not have permission{muted}.")
                .generalPlayerOnly("commands.only-player", "{prefix}{bad}Player only{muted}.")
                .usage("commands.usage-admin", "{prefix}Use {theme}/fokillstreakadmin <version|reload|editor|set>{muted}.")
                .commandMissing("commands.reload-failed", "{prefix}{bad}Admin command missing from plugin.yml{muted}.")
                .commandFailed("commands.reload-failed", "{prefix}{bad}Command failed{muted}. Check console.")
                .build();
    }

    private FoAdminSubcommand versionSubcommand() {
        return FoAdminSubcommand.builder("version", context -> {
            if (context.args().length != 1) {
                messageService.send(context.sender(), "commands.usage-admin", MessageFallbacks.string("commands.usage-admin"));
                return true;
            }
            updateNotices.checkAndSendVersion(context.sender());
            return true;
        }).usage("version").build();
    }

    private FoAdminSubcommand reloadSubcommand() {
        return FoAdminSubcommand.builder("reload", context -> {
            if (context.args().length != 1) {
                messageService.send(context.sender(), "commands.usage-admin", MessageFallbacks.string("commands.usage-admin"));
                return true;
            }
            if (reloadPluginFiles()) {
                if (context.sender() instanceof org.bukkit.entity.Player player) {
                    adminSounds.reload(player);
                }
                messageService.send(context.sender(), "commands.reloaded", MessageFallbacks.string("commands.reloaded"));
            } else {
                if (context.sender() instanceof org.bukkit.entity.Player player) {
                    adminSounds.reloadError(player);
                }
                messageService.send(context.sender(), "commands.reload-failed", MessageFallbacks.string("commands.reload-failed"));
            }
            return true;
        }).usage("reload").build();
    }

    private FoAdminSubcommand editorSubcommand() {
        return FoAdminSubcommand.builder("editor", context -> {
            if (context.args().length != 1) {
                messageService.send(context.sender(), "commands.usage-admin", MessageFallbacks.string("commands.usage-admin"));
                return true;
            }
            guiManager.openAdminEditor(context.playerOrNull());
            return true;
        }).usage("editor").playerOnly().build();
    }

    private void registerPlaceholderApi() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found; placeholders disabled.");
            return;
        }
        FoPlaceholders placeholders = core.placeholders("fokillstreak")
                .author("Carrotio")
                .offlineFormattedRaw(
                        "killstreak",
                        player -> playerValue(player, uuid -> String.valueOf(streakStorage.currentStreak(uuid))),
                        player -> playerValue(player, uuid -> String.valueOf(streakStorage.currentStreak(uuid)))
                )
                .offlineFormattedRaw(
                        "best",
                        player -> playerValue(player, uuid -> String.valueOf(streakStorage.bestStreak(uuid))),
                        player -> playerValue(player, uuid -> String.valueOf(streakStorage.bestStreak(uuid)))
                )
                .offline("rank", player -> playerValue(player, uuid -> {
                    int rank = streakStorage.rank(uuid, LeaderboardType.CURRENT);
                    return rank > 0 ? String.valueOf(rank) : "";
                }));
        placeholders.registerIfAvailable();
    }

    private String playerValue(OfflinePlayer player, Function<UUID, String> value) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        return value.apply(player.getUniqueId());
    }
}
