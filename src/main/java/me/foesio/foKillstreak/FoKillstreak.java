package me.foesio.foKillstreak;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.placeholder.FoPlaceholders;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foKillstreak.command.AdminSetSubcommand;
import me.foesio.foKillstreak.command.PlayerCommand;
import me.foesio.foKillstreak.config.ConfigService;
import me.foesio.foKillstreak.config.GuiConfigService;
import me.foesio.foKillstreak.data.LeaderboardType;
import me.foesio.foKillstreak.data.StreakStorage;
import me.foesio.foKillstreak.gui.KillstreakGuiManager;
import me.foesio.foKillstreak.listener.KillstreakListener;
import me.foesio.foKillstreak.message.MessageFallbacks;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class FoKillstreak extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "fokillstreak";
    private static final int BSTATS_PLUGIN_ID = 32977;

    private FoCoreContext core;
    private ConfigService configService;
    private GuiConfigService guiConfigService;
    private FoMessageService messageService;
    private StreakStorage streakStorage;
    private KillstreakGuiManager guiManager;
    private UpdateNoticeService updateNotices;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;

    @Override
    public void onEnable() {
        configService = new ConfigService(this);
        guiConfigService = new GuiConfigService(this);

        configService.load();
        guiConfigService.load();
        refreshCoreContext();
        messageService = FoMessageService.load(this, messageMigrations());
        migrateSprites();
        updateNotices = core.createUpdateNotices(messageService, MODRINTH_PROJECT_ID, adminSounds).start();

        streakStorage = new StreakStorage(this);
        if (!streakStorage.open()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        guiManager = new KillstreakGuiManager(this, configService, guiConfigService, messageService, streakStorage, core.nativeDialogs(), sounds, editorSounds, guiSounds);

        getServer().getPluginManager().registerEvents(new KillstreakListener(configService, messageService, streakStorage, sounds), this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        registerCommands();
        registerPlaceholderApi();
    }

    @Override
    public void onDisable() {
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
                .add("guis", guiConfigService::load)
                .add("native dialogs", this::refreshCoreContext)
                .add("sounds", sounds::reload)
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
            sounds = core.createSounds();
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
        messageService.migrateToVersion(core.migrations(), 1, config -> {
            boolean changed = false;
            changed |= FoMessageService.addMissingToken(config, "tokens.prefix", ":diamond_sword:", null);
            changed |= FoMessageService.addMissingToken(config, "commands.usage-admin", ":paper:");
            changed |= FoMessageService.addMissingToken(config, "commands.reloaded", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "commands.reload-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "commands.player-not-found", ":player_head:");
            changed |= FoMessageService.addMissingToken(config, "commands.streak-set", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "killstreak.cooldown-blocked", ":clock:");
            changed |= FoMessageService.addMissingToken(config, "broadcasts.milestone", ":fire_charge:");
            changed |= FoMessageService.addMissingToken(config, "editor.saved", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "editor.save-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "editor.reward-added", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "editor.reward-deleted", ":lava_bucket:");
            return changed;
        });
        messageService.migrateToVersion(core.migrations(), 2, this::migrateEditorButtonNames);
        messageService.migrateToVersion(core.migrations(), 3, this::migrateAnnounceThresholdLore);
        messageService.reload();
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
                .addSubcommand(AdminSetSubcommand.create(streakStorage, adminSounds))
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
