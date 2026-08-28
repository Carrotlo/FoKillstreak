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
import org.bukkit.plugin.java.JavaPlugin;

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
            updateNotices.sendVersion(context.sender());
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
