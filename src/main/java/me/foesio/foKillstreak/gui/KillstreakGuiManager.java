package me.foesio.foKillstreak.gui;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.number.DurationParser;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.text.FoText;
import me.foesio.core.text.PromptNormalizer;
import me.foesio.foKillstreak.FoKillstreak;
import me.foesio.foKillstreak.config.ConfigService;
import me.foesio.foKillstreak.config.ConfigService.RewardCommand;
import me.foesio.foKillstreak.config.ConfigService.RewardSender;
import me.foesio.foKillstreak.config.GuiConfigService;
import me.foesio.foKillstreak.data.LeaderboardEntry;
import me.foesio.foKillstreak.data.LeaderboardType;
import me.foesio.foKillstreak.data.StreakStorage;
import me.foesio.foKillstreak.message.MessageFallbacks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public final class KillstreakGuiManager implements Listener {
    private static final Material ADMIN_FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final GuiButtonConfig BUTTONS = GuiButtonConfig.defaults();
    private static final long LEADERBOARD_OPEN_COOLDOWN_MILLIS = 500L;
    private static final long LEADERBOARD_TAB_COOLDOWN_MILLIS = 200L;
    private static final int ADMIN_SIZE = 27;
    private static final int SLOT_RELOAD = 10;
    private static final int SLOT_REWARDS = 11;
    private static final int SLOT_MILESTONES = 12;
    private static final int SLOT_BROADCASTS = 13;
    private static final int SLOT_COOLDOWN_TOGGLE = 14;
    private static final int SLOT_COOLDOWN_SECONDS = 15;
    private static final int SLOT_PLAYER_DEATH_RESET = 16;
    private static final int REWARD_EDITOR_SIZE = 27;
    private static final int REWARD_EDITOR_BACK_SLOT = 22;

    private final FoKillstreak plugin;
    private final ConfigService config;
    private final GuiConfigService guiConfig;
    private final FoMessageService messages;
    private final StreakStorage storage;
    private final FoSoundService sounds;
    private final FoEditorSounds editorSounds;
    private final FoGuiSounds guiSounds;
    private LeaderboardDialogService leaderboardDialogs;
    private final Map<UUID, Long> leaderboardOpenTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> leaderboardTabTimes = new ConcurrentHashMap<>();
    private final Map<UUID, RewardBrowserState> rewardBrowserStates = new ConcurrentHashMap<>();
    private final Map<UUID, RewardEditState> rewardEditStates = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRewardState> pendingRewardStates = new ConcurrentHashMap<>();
    private final Map<UUID, RewardDeleteState> rewardDeleteStates = new ConcurrentHashMap<>();

    public KillstreakGuiManager(FoKillstreak plugin, ConfigService config, GuiConfigService guiConfig, FoMessageService messages, StreakStorage storage, NativeDialogSupport nativeDialogs, FoSoundService sounds, FoEditorSounds editorSounds, FoGuiSounds guiSounds) {
        this.plugin = plugin;
        this.config = config;
        this.guiConfig = guiConfig;
        this.messages = messages;
        this.storage = storage;
        this.sounds = sounds;
        this.editorSounds = editorSounds;
        this.guiSounds = guiSounds;
        reloadNativeDialogs(nativeDialogs);
    }

    public void reloadNativeDialogs(NativeDialogSupport nativeDialogs) {
        leaderboardDialogs = LeaderboardDialogServiceFactory.create(plugin, nativeDialogs, guiConfig, messages, storage, this::openLeaderboardFallback);
    }

    public void openLeaderboard(Player player) {
        if (!canOpenLeaderboard(player)) {
            return;
        }
        guiSounds.open(player);
        openLeaderboard(player, LeaderboardType.CURRENT);
    }

    void openLeaderboardTab(Player player, LeaderboardType type) {
        if (!canUseLeaderboardAction(player, leaderboardTabTimes, LEADERBOARD_TAB_COOLDOWN_MILLIS)) {
            return;
        }
        sounds.play(player, "gui.select");
        openLeaderboard(player, type);
    }

    private void openLeaderboard(Player player, LeaderboardType type) {
        if (config.nativeLeaderboardDialogsEnabled() && leaderboardDialogs.open(player, type)) {
            return;
        }
        openLeaderboardFallback(player);
    }

    private void openLeaderboardFallback(Player player) {
        int size = guiRows("rows", 3) * 9;
        Inventory inventory = Bukkit.createInventory(null, size, FoText.color(guiConfig.getString("title", "&8ꜰᴏᴋɪʟʟꜱᴛʀᴇᴀᴋ")));
        fill(inventory, guiMaterial("filler-material", Material.LIGHT_GRAY_STAINED_GLASS_PANE));

        inventory.setItem(guiSlot("current.slot", 11, size), item(
                guiMaterial("current.material", Material.DIAMOND_SWORD),
                messages.renderTemplate(guiConfig.getString("current.name", "{theme}ᴄᴜʀʀᴇɴᴛ ᴋɪʟʟꜱᴛʀᴇᴀᴋ"), Map.of()),
                leaderboardLore(player.getUniqueId(), LeaderboardType.CURRENT)
        ));
        inventory.setItem(guiSlot("best.slot", 15, size), item(
                guiMaterial("best.material", Material.NETHER_STAR),
                messages.renderTemplate(guiConfig.getString("best.name", "{theme}ʙᴇꜱᴛ ᴋɪʟʟꜱᴛʀᴇᴀᴋ"), Map.of()),
                leaderboardLore(player.getUniqueId(), LeaderboardType.BEST)
        ));

        player.openInventory(inventory);
    }

    private void openRewardBrowser(Player player) {
        RewardBrowserState state = rewardBrowserStates.getOrDefault(player.getUniqueId(), new RewardBrowserState(0, ""));
        openRewardBrowser(player, state.page(), state.search());
    }

    private void openRewardBrowser(Player player, int requestedPage, String search) {
        String normalizedSearch = PromptNormalizer.normalize(search);
        List<RewardCommand> rewards = filteredRewards(normalizedSearch);
        List<EntryBrowserRequest.Entry> entries = new ArrayList<>();
        for (int index = 0; index < rewards.size(); index++) {
            RewardCommand reward = rewards.get(index);
            entries.add(EntryBrowserRequest.Entry.of(
                    rewardBrowserId(reward),
                    rewardBrowserItem(reward, index + 1)
            ));
        }

        int maxPage = entries.isEmpty()
                ? 0
                : (entries.size() - 1) / EntryBrowserRequest.ENTRY_SLOTS.length;
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        rewardBrowserStates.put(player.getUniqueId(), new RewardBrowserState(page, normalizedSearch));

        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title(message("gui.rewards.title"))
                .entries(entries)
                .page(page)
                .filter(normalizedSearch)
                .buttons(BUTTONS)
                .showBack(true)
                .addButton(item(
                        Material.ANVIL,
                        message("gui.rewards.add-name"),
                        messageList("gui.rewards.add-lore", Map.of())
                ))
                .build());
    }

    private void openRewardSenderSelector(Player player, int streak) {
        openRewardSenderSelector(player, streak, false);
    }

    private void openRewardSenderSelector(Player player, int streak, boolean playOpenSound) {
        pendingRewardStates.put(player.getUniqueId(), new PendingRewardState(streak));
        if (playOpenSound) {
            editorSounds.open(player);
        }
        Inventory inventory = Bukkit.createInventory(null, REWARD_EDITOR_SIZE, message("gui.reward-sender.title"));
        fill(inventory, ADMIN_FILLER);

        inventory.setItem(11, item(
                Material.COMMAND_BLOCK,
                message("gui.reward-sender.console-name"),
                messageList("gui.reward-sender.console-lore", Map.of("streak", String.valueOf(streak)))
        ));
        inventory.setItem(15, item(
                Material.PLAYER_HEAD,
                message("gui.reward-sender.player-name"),
                messageList("gui.reward-sender.player-lore", Map.of("streak", String.valueOf(streak)))
        ));
        inventory.setItem(REWARD_EDITOR_BACK_SLOT, BUTTONS.back());

        player.openInventory(inventory);
    }

    private void openRewardEdit(Player player, RewardCommand reward) {
        openRewardEdit(player, reward.streak(), reward.configKey(), reward.index());
    }

    private void enterRewardEdit(Player player, RewardCommand reward) {
        editorSounds.open(player);
        openRewardEdit(player, reward);
    }

    private void openRewardEdit(Player player, int streak, String configKey, int index) {
        RewardCommand reward = findReward(streak, configKey, index);
        if (reward == null) {
            editorSounds.error(player);
            player.sendMessage(message("editor.reward-missing"));
            openRewardBrowser(player);
            return;
        }

        rewardEditStates.put(player.getUniqueId(), new RewardEditState(streak, configKey, index));
        Inventory inventory = Bukkit.createInventory(null, REWARD_EDITOR_SIZE, message("gui.reward-edit.title"));
        fill(inventory, ADMIN_FILLER);

        inventory.setItem(10, item(
                Material.NETHER_STAR,
                message("gui.reward-edit.streak-name"),
                messageList("gui.reward-edit.streak-lore", Map.of("streak", String.valueOf(reward.streak())))
        ));
        inventory.setItem(12, item(
                Material.REPEATER,
                message("gui.reward-edit.sender-name"),
                messageList("gui.reward-edit.sender-lore", Map.of(
                        "sender", reward.sender().name().toLowerCase(Locale.ROOT),
                        "console", senderOption(reward.sender(), RewardSender.CONSOLE),
                        "player", senderOption(reward.sender(), RewardSender.PLAYER)
                ))
        ));
        inventory.setItem(14, item(
                Material.COMMAND_BLOCK,
                message("gui.reward-edit.command-name"),
                messageList("gui.reward-edit.command-lore", Map.of("command", reward.command()))
        ));
        inventory.setItem(16, item(
                Material.LAVA_BUCKET,
                message("gui.reward-edit.delete-name"),
                messageList("gui.reward-edit.delete-lore", Map.of())
        ));
        inventory.setItem(REWARD_EDITOR_BACK_SLOT, BUTTONS.back());

        player.openInventory(inventory);
    }

    private void openRewardDeleteConfirm(Player player, RewardEditState state) {
        RewardCommand reward = findReward(state.streak(), state.configKey(), state.index());
        if (reward == null) {
            editorSounds.error(player);
            player.sendMessage(message("editor.reward-missing"));
            openRewardBrowser(player);
            return;
        }

        rewardDeleteStates.put(player.getUniqueId(), new RewardDeleteState(state.streak(), state.configKey(), state.index()));
        editorSounds.open(player);
        Inventory inventory = Bukkit.createInventory(null, REWARD_EDITOR_SIZE, message("gui.reward-delete.title"));
        fill(inventory, ADMIN_FILLER);

        inventory.setItem(11, item(
                Material.LIME_WOOL,
                message("gui.reward-delete.confirm-name"),
                messageList("gui.reward-delete.confirm-lore", Map.of(
                        "streak", String.valueOf(reward.streak()),
                        "command", reward.command()
                ))
        ));
        inventory.setItem(15, item(
                Material.RED_WOOL,
                message("gui.reward-delete.cancel-name"),
                messageList("gui.reward-delete.cancel-lore", Map.of())
        ));

        player.openInventory(inventory);
    }

    public void openAdminEditor(Player player) {
        openAdminEditor(player, true);
    }

    private void openAdminEditor(Player player, boolean playOpenSound) {
        if (playOpenSound) {
            editorSounds.open(player);
        }
        Inventory inventory = Bukkit.createInventory(null, ADMIN_SIZE, message("gui.admin.title"));
        fill(inventory, ADMIN_FILLER);

        boolean playerDeathOnly = config.getBoolean("death-reset.only-by-player", true);
        boolean broadcasts = config.getBoolean("broadcasts.enabled", true);
        boolean cooldown = config.getBoolean("same-victim-cooldown.enabled", true);
        String playerDeathNamePath = playerDeathOnly ? "gui.admin.player-death-reset-enabled-name" : "gui.admin.player-death-reset-disabled-name";
        String broadcastsNamePath = broadcasts ? "gui.admin.broadcasts-enabled-name" : "gui.admin.broadcasts-disabled-name";
        String cooldownNamePath = cooldown ? "gui.admin.cooldown-enabled-name" : "gui.admin.cooldown-disabled-name";

        inventory.setItem(SLOT_PLAYER_DEATH_RESET, item(
                playerDeathOnly ? Material.LIME_DYE : Material.RED_DYE,
                message(playerDeathNamePath),
                messageList("gui.admin.player-death-reset-lore", Map.of("state", state(playerDeathOnly)))
        ));
        inventory.setItem(SLOT_BROADCASTS, item(
                broadcasts ? Material.LIME_DYE : Material.RED_DYE,
                message(broadcastsNamePath),
                messageList("gui.admin.broadcasts-lore", Map.of("state", state(broadcasts)))
        ));
        inventory.setItem(SLOT_COOLDOWN_TOGGLE, item(
                cooldown ? Material.LIME_DYE : Material.RED_DYE,
                message(cooldownNamePath),
                messageList("gui.admin.cooldown-lore", Map.of("state", state(cooldown)))
        ));
        inventory.setItem(SLOT_COOLDOWN_SECONDS, item(
                Material.CLOCK,
                message("gui.admin.cooldown-seconds-name"),
                messageList("gui.admin.cooldown-seconds-lore", Map.of(
                        "seconds", String.valueOf(config.getInt("same-victim-cooldown.seconds", 60))
                ))
        ));
        inventory.setItem(SLOT_MILESTONES, item(
                Material.PAPER,
                message("gui.admin.milestones-name"),
                messageList("gui.admin.milestones-lore", Map.of(
                        "milestones", config.getMilestones().stream().map(String::valueOf).collect(Collectors.joining(", "))
                ))
        ));
        inventory.setItem(SLOT_REWARDS, item(
                Material.CHEST,
                message("gui.admin.rewards-name"),
                messageList("gui.admin.rewards-lore", Map.of(
                        "count", String.valueOf(rewardCount())
                ))
        ));
        inventory.setItem(SLOT_RELOAD, item(
                Material.EMERALD,
                message("gui.admin.reload-name"),
                messageList("gui.admin.reload-lore", Map.of())
        ));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (title.equals(FoText.color(guiConfig.getString("title", "&8ꜰᴏᴋɪʟʟꜱᴛʀᴇᴀᴋ")))) {
            event.setCancelled(true);
            return;
        }

        if (isAdminEditorTitle(title) && !player.hasPermission("fokillstreak.admin")) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(message("commands.no-permission"));
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof EntryBrowserHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                handleRewardBrowserClick(player, event.getSlot(), holder);
            }
            return;
        }

        if (title.equals(message("gui.admin.title"))) {
            event.setCancelled(true);
            handleAdminEditorClick(player, event.getInventory().getSize(), event.getRawSlot());
        } else if (title.equals(message("gui.reward-sender.title"))) {
            event.setCancelled(true);
            handleRewardSenderClick(player, event.getRawSlot());
        } else if (title.equals(message("gui.reward-edit.title"))) {
            event.setCancelled(true);
            handleRewardEditClick(player, event.getRawSlot());
        } else if (title.equals(message("gui.reward-delete.title"))) {
            event.setCancelled(true);
            handleRewardDeleteClick(player, event.getRawSlot());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EntryBrowserHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (plugin.core().inventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }

        if (event.getView().getTitle().equals(message("gui.reward-delete.title"))) {
            rewardDeleteStates.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        leaderboardOpenTimes.remove(player);
        leaderboardTabTimes.remove(player);
        rewardBrowserStates.remove(player);
        rewardEditStates.remove(player);
        pendingRewardStates.remove(player);
        rewardDeleteStates.remove(player);
    }

    private void handleAdminEditorClick(Player player, int size, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= size) {
            return;
        }

        if (rawSlot == SLOT_PLAYER_DEATH_RESET) {
            toggle(player, "death-reset.only-by-player", "PvP death resets");
        } else if (rawSlot == SLOT_BROADCASTS) {
            toggle(player, "broadcasts.enabled", "milestone broadcasts");
        } else if (rawSlot == SLOT_COOLDOWN_TOGGLE) {
            toggle(player, "same-victim-cooldown.enabled", "same-victim cooldown");
        } else if (rawSlot == SLOT_COOLDOWN_SECONDS) {
            beginCooldownPrompt(player);
        } else if (rawSlot == SLOT_MILESTONES) {
            beginMilestonesPrompt(player);
        } else if (rawSlot == SLOT_REWARDS) {
            editorSounds.open(player);
            openRewardBrowser(player, 0, "");
        } else if (rawSlot == SLOT_RELOAD) {
            if (plugin.reloadPluginFiles()) {
                plugin.adminSounds().reload(player);
                player.sendMessage(message("commands.reloaded"));
            } else {
                plugin.adminSounds().reloadError(player);
                player.sendMessage(message("commands.reload-failed"));
            }
            openAdminEditor(player, false);
        }
    }

    private void handleRewardBrowserClick(Player player, int rawSlot, EntryBrowserHolder holder) {
        EntryBrowserClick click = EntryBrowserMenus.handleClick(rawSlot, holder);
        int page = holder.request().page();
        String search = holder.request().filter();
        switch (click.action()) {
            case ENTRY -> {
                RewardCommand reward = findRewardByBrowserId(click.entryId());
                if (reward == null) {
                    editorSounds.error(player);
                    player.sendMessage(message("editor.reward-missing"));
                    openRewardBrowser(player, page, search);
                } else {
                    enterRewardEdit(player, reward);
                }
            }
            case ADD -> {
                editorSounds.add(player);
                beginRewardStreakPrompt(player);
            }
            case BACK -> {
                editorSounds.back(player);
                openAdminEditor(player, false);
            }
            case SEARCH -> {
                editorSounds.search(player);
                beginRewardSearchPrompt(player, new RewardBrowserState(page, search));
            }
            case CLEAR_SEARCH -> {
                editorSounds.clearSearch(player);
                player.sendMessage(message("editor.search-cleared"));
                openRewardBrowser(player, 0, "");
            }
            case PREVIOUS_PAGE -> {
                editorSounds.previousPage(player);
                openRewardBrowser(player, page - 1, search);
            }
            case NEXT_PAGE -> {
                editorSounds.nextPage(player);
                openRewardBrowser(player, page + 1, search);
            }
            case NONE -> {
                // Filler and inactive navigation slots intentionally do nothing.
            }
        }
    }

    private void handleRewardSenderClick(Player player, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= REWARD_EDITOR_SIZE) {
            return;
        }

        PendingRewardState state = pendingRewardStates.get(player.getUniqueId());
        if (state == null) {
            openRewardBrowser(player);
            return;
        }

        if (rawSlot == 11) {
            editorSounds.add(player);
            beginAddRewardCommandPrompt(player, state.streak(), RewardSender.CONSOLE);
        } else if (rawSlot == 15) {
            editorSounds.add(player);
            beginAddRewardCommandPrompt(player, state.streak(), RewardSender.PLAYER);
        } else if (rawSlot == REWARD_EDITOR_BACK_SLOT) {
            editorSounds.back(player);
            pendingRewardStates.remove(player.getUniqueId());
            openRewardBrowser(player);
        }
    }

    private void handleRewardEditClick(Player player, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= REWARD_EDITOR_SIZE) {
            return;
        }

        RewardEditState state = rewardEditStates.get(player.getUniqueId());
        if (state == null) {
            openRewardBrowser(player);
            return;
        }

        RewardCommand reward = findReward(state.streak(), state.configKey(), state.index());
        if (reward == null) {
            editorSounds.error(player);
            player.sendMessage(message("editor.reward-missing"));
            openRewardBrowser(player);
            return;
        }

        if (rawSlot == 12) {
            RewardSender next = reward.sender() == RewardSender.CONSOLE ? RewardSender.PLAYER : RewardSender.CONSOLE;
            if (config.updateRewardSender(reward.configKey(), reward.index(), next)) {
                editorSounds.cycle(player);
                player.sendMessage(message("editor.saved", Map.of("setting", "reward sender")));
            } else {
                editorSounds.error(player);
                player.sendMessage(message("editor.save-failed", Map.of("setting", "reward sender")));
            }
            openRewardEdit(player, reward.streak(), reward.configKey(), reward.index());
        } else if (rawSlot == 14) {
            beginEditRewardCommandPrompt(player, state);
        } else if (rawSlot == 16) {
            openRewardDeleteConfirm(player, state);
        } else if (rawSlot == REWARD_EDITOR_BACK_SLOT) {
            editorSounds.back(player);
            openRewardBrowser(player);
        }
    }

    private void handleRewardDeleteClick(Player player, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= REWARD_EDITOR_SIZE) {
            return;
        }

        RewardDeleteState state = rewardDeleteStates.get(player.getUniqueId());
        if (state == null) {
            openRewardBrowser(player);
            return;
        }

        if (rawSlot == 11) {
            RewardCommand reward = findReward(state.streak(), state.configKey(), state.index());
            if (reward == null) {
                editorSounds.error(player);
                player.sendMessage(message("editor.reward-missing"));
            } else if (config.removeReward(reward.configKey(), reward.index())) {
                editorSounds.delete(player);
                player.sendMessage(message("editor.reward-deleted", Map.of("streak", String.valueOf(state.streak()))));
            } else {
                editorSounds.error(player);
                player.sendMessage(message("editor.save-failed", Map.of("setting", "reward")));
            }
            rewardDeleteStates.remove(player.getUniqueId());
            rewardEditStates.remove(player.getUniqueId());
            openRewardBrowser(player);
        } else if (rawSlot == 15) {
            editorSounds.back(player);
            rewardDeleteStates.remove(player.getUniqueId());
            openRewardEdit(player, state.streak(), state.configKey(), state.index());
        }
    }

    private void toggle(Player player, String path, String settingName) {
        boolean newValue = !config.getBoolean(path, true);
        if (config.setAndSave(path, newValue)) {
            editorSounds.toggle(player, newValue);
            player.sendMessage(message("editor.saved", Map.of("setting", settingName)));
        } else {
            editorSounds.error(player);
            player.sendMessage(message("editor.save-failed", Map.of("setting", settingName)));
        }
        openAdminEditor(player, false);
    }

    private void beginCooldownPrompt(Player player) {
        editorSounds.open(player);
        openTextInput(player, new TextDialogRequest(
                "Cooldown",
                List.of("Use seconds or a duration like 1m."),
                "Seconds",
                String.valueOf(config.getInt("same-victim-cooldown.seconds", 60)),
                "",
                DialogButton.save(),
                DialogButton.cancel("Back"),
                320,
                300,
                32,
                true,
                true,
                false
        ), input -> {
            var duration = DurationParser.parse(input);
            if (duration.isEmpty()) {
                editorSounds.error(player);
                player.sendMessage(message("editor.invalid-number"));
                openAdminEditor(player, false);
                return;
            }

            long parsedSeconds = duration.get().secondsFloor();
            if (parsedSeconds <= 0 || parsedSeconds > Integer.MAX_VALUE) {
                editorSounds.error(player);
                player.sendMessage(message("editor.invalid-number"));
                openAdminEditor(player, false);
                return;
            }
            int seconds = (int) parsedSeconds;

            if (config.setAndSave("same-victim-cooldown.seconds", seconds)) {
                editorSounds.save(player);
                player.sendMessage(message("editor.saved", Map.of("setting", "cooldown seconds")));
            } else {
                editorSounds.error(player);
                player.sendMessage(message("editor.save-failed", Map.of("setting", "cooldown seconds")));
            }
            openAdminEditor(player, false);
        }, () -> openAdminEditor(player, false));
    }

    private void beginMilestonesPrompt(Player player) {
        editorSounds.open(player);
        openTextInput(player, new TextDialogRequest(
                "Milestones",
                List.of("Use comma-separated whole numbers."),
                "Milestones",
                config.getMilestones().stream().map(String::valueOf).collect(Collectors.joining(",")),
                "",
                DialogButton.save(),
                DialogButton.cancel("Back"),
                320,
                300,
                128,
                true,
                true,
                false
        ), input -> {
            List<Integer> milestones = parseMilestones(input);
            if (milestones.isEmpty()) {
                editorSounds.error(player);
                player.sendMessage(message("editor.invalid-list"));
                openAdminEditor(player, false);
                return;
            }

            if (config.setAndSave("broadcasts.milestones", milestones)) {
                editorSounds.save(player);
                player.sendMessage(message("editor.saved", Map.of("setting", "milestones")));
            } else {
                editorSounds.error(player);
                player.sendMessage(message("editor.save-failed", Map.of("setting", "milestones")));
            }
            openAdminEditor(player, false);
        }, () -> openAdminEditor(player, false));
    }

    private void beginRewardSearchPrompt(Player player) {
        beginRewardSearchPrompt(
                player,
                rewardBrowserStates.getOrDefault(player.getUniqueId(), new RewardBrowserState(0, ""))
        );
    }

    private void beginRewardSearchPrompt(Player player, RewardBrowserState state) {
        openTextInput(player, new TextDialogRequest(
                "Search",
                List.of("Search by streak, sender, or command."),
                "Search",
                state.search(),
                "reward id or command",
                DialogButton.search(),
                DialogButton.cancel(),
                320,
                300,
                80,
                true,
                true,
                false
        ), input -> {
            player.sendMessage(message("editor.search-applied", Map.of("search", input)));
            openRewardBrowser(player, 0, input);
        }, () -> openRewardBrowser(player, state.page(), state.search()));
    }

    private void beginRewardStreakPrompt(Player player) {
        openTextInput(player, new TextDialogRequest(
                "Reward Streak",
                List.of("Use a whole number above zero."),
                "Killstreak",
                "",
                "",
                DialogButton.confirm("Next"),
                DialogButton.cancel("Back"),
                320,
                300,
                32,
                true,
                true,
                false
        ), input -> {
            int streak = parsePositiveInt(input);
            if (streak <= 0) {
                editorSounds.error(player);
                player.sendMessage(message("editor.invalid-reward-streak"));
                openRewardBrowser(player);
                return;
            }

            openRewardSenderSelector(player, streak, true);
        }, () -> openRewardBrowser(player));
    }

    private void beginAddRewardCommandPrompt(Player player, int streak, RewardSender sender) {
        openTextInput(player, rewardCommandRequest(streak, sender, ""), input -> {
            String command = normalizeCommand(input);
            if (command.isBlank()) {
                editorSounds.error(player);
                player.sendMessage(message("editor.invalid-reward-command"));
                openRewardSenderSelector(player, streak);
                return;
            }

            if (config.addReward(streak, sender, command)) {
                pendingRewardStates.remove(player.getUniqueId());
                editorSounds.save(player);
                player.sendMessage(message("editor.reward-added", Map.of("streak", String.valueOf(streak))));
                openRewardBrowser(player, 0, "");
            } else {
                editorSounds.error(player);
                player.sendMessage(message("editor.save-failed", Map.of("setting", "reward")));
                openRewardBrowser(player);
            }
        }, () -> openRewardSenderSelector(player, streak));
    }

    private void beginEditRewardCommandPrompt(Player player, RewardEditState state) {
        RewardCommand reward = findReward(state.streak(), state.configKey(), state.index());
        if (reward == null) {
            editorSounds.error(player);
            player.sendMessage(message("editor.reward-missing"));
            openRewardBrowser(player);
            return;
        }

        editorSounds.open(player);
        openTextInput(player, rewardCommandRequest(reward.streak(), reward.sender(), reward.command()), input -> {
            String command = normalizeCommand(input);
            if (command.isBlank()) {
                editorSounds.error(player);
                player.sendMessage(message("editor.invalid-reward-command"));
                openRewardEdit(player, state.streak(), state.configKey(), state.index());
                return;
            }

            if (config.updateRewardCommand(state.configKey(), state.index(), command)) {
                editorSounds.save(player);
                player.sendMessage(message("editor.saved", Map.of("setting", "reward command")));
            } else {
                editorSounds.error(player);
                player.sendMessage(message("editor.save-failed", Map.of("setting", "reward command")));
            }
            openRewardEdit(player, state.streak(), state.configKey(), state.index());
        }, () -> openRewardEdit(player, state.streak(), state.configKey(), state.index()));
    }

    private TextDialogRequest rewardCommandRequest(int streak, RewardSender sender, String initialValue) {
        return new TextDialogRequest(
                "Reward Command",
                List.of("Use {player}, {uuid}, {streak}, or {best_streak}.", "Reward: " + streak + " kills via " + sender.name().toLowerCase(Locale.ROOT) + "."),
                "Command",
                initialValue,
                "",
                DialogButton.save(),
                DialogButton.cancel("Back"),
                340,
                320,
                256,
                true,
                true,
                false
        );
    }

    private void openTextInput(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        EditorDialogInputs.openTextFromInventory(
                plugin,
                plugin.core().inventoryCloseSuppressor(),
                plugin.core().dialogService(),
                player,
                request,
                input -> onSubmit.accept(input.trim()),
                onCancel
        );
    }

    private List<String> leaderboardLore(UUID uuid, LeaderboardType type) {
        List<LeaderboardEntry> top = storage.top(type, 10);
        List<String> lore = new ArrayList<>();
        for (int rank = 1; rank <= 10; rank++) {
            if (top.size() >= rank) {
                LeaderboardEntry entry = top.get(rank - 1);
                lore.add(messages.renderTemplate(guiConfig.getString("lines.entry", "{theme}#{rank} {white}{player} {muted}- {theme}{streak}"), Map.of(
                        "rank", String.valueOf(entry.rank()),
                        "player", entry.name(),
                        "streak", String.valueOf(entry.streak())
                )));
            } else {
                lore.add(messages.renderTemplate(guiConfig.getString("lines.empty", "{muted}#{rank} Empty"), Map.of("rank", String.valueOf(rank))));
            }
        }

        lore.add(FoText.color("&r"));
        int rank = storage.rank(uuid, type);
        int streak = type == LeaderboardType.CURRENT ? storage.currentStreak(uuid) : storage.bestStreak(uuid);
        if (rank > 0) {
            lore.add(messages.renderTemplate(guiConfig.getString("lines.position", "{white}Your position: {theme}#{rank} {muted}({streak})"), Map.of(
                    "rank", String.valueOf(rank),
                    "streak", String.valueOf(streak)
            )));
        } else {
            lore.add(messages.renderTemplate(guiConfig.getString("lines.unranked", "{white}Your position: {muted}Unranked"), Map.of()));
        }
        return lore;
    }

    private int rewardCount() {
        return allRewards().size();
    }

    private List<RewardCommand> allRewards() {
        List<RewardCommand> rewards = new ArrayList<>();
        for (int streak : config.getRewardStreaks()) {
            rewards.addAll(config.getRewardsForStreak(streak));
        }
        return rewards;
    }

    private List<RewardCommand> filteredRewards(String search) {
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return allRewards();
        }

        List<RewardCommand> filtered = new ArrayList<>();
        for (RewardCommand reward : allRewards()) {
            String haystack = (reward.streak() + " " + reward.sender().name() + " " + reward.command()).toLowerCase(Locale.ROOT);
            if (haystack.contains(query)) {
                filtered.add(reward);
            }
        }
        return filtered;
    }

    private ItemStack rewardBrowserItem(RewardCommand reward, int displayIndex) {
        return item(
                Material.COMMAND_BLOCK,
                message("gui.rewards.reward-name", Map.of("streak", String.valueOf(reward.streak()))),
                messageList("gui.rewards.reward-lore", Map.of(
                        "index", String.valueOf(displayIndex),
                        "streak", String.valueOf(reward.streak()),
                        "sender", reward.sender().name().toLowerCase(Locale.ROOT),
                        "command", reward.command()
                ))
        );
    }

    private String rewardBrowserId(RewardCommand reward) {
        return reward.configKey() + "#" + reward.index();
    }

    private RewardCommand findRewardByBrowserId(String browserId) {
        if (browserId == null) {
            return null;
        }
        int separator = browserId.lastIndexOf('#');
        if (separator <= 0 || separator == browserId.length() - 1) {
            return null;
        }
        try {
            return findReward(
                    0,
                    browserId.substring(0, separator),
                    Integer.parseInt(browserId.substring(separator + 1))
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private RewardCommand findReward(int streak, String configKey, int index) {
        List<Integer> streaks = streak > 0 ? List.of(streak) : config.getRewardStreaks();
        for (int rewardStreak : streaks) {
            for (RewardCommand reward : config.getRewardsForStreak(rewardStreak)) {
                if (reward.configKey().equals(configKey) && reward.index() == index) {
                    return reward;
                }
            }
        }
        return null;
    }

    private String senderOption(RewardSender selected, RewardSender sender) {
        String key = selected == sender ? "gui.reward-edit.sender-selected" : "gui.reward-edit.sender-unselected";
        return message(key, Map.of("sender", sender.name().toLowerCase(Locale.ROOT)));
    }

    private List<Integer> parseMilestones(String input) {
        String[] parts = input.split(",");
        Set<Integer> parsed = new LinkedHashSet<>();
        for (String part : parts) {
            var value = LargeNumberParser.parsePositiveInt(part);
            if (value.isEmpty()) {
                return List.of();
            }
            parsed.add(value.getAsInt());
        }
        List<Integer> milestones = new ArrayList<>(parsed);
        milestones.sort(Integer::compareTo);
        return milestones;
    }

    private int parsePositiveInt(String input) {
        return LargeNumberParser.parsePositiveInt(input).orElse(-1);
    }

    private String normalizeCommand(String input) {
        String command = PromptNormalizer.normalize(input);
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        return command;
    }

    private String state(boolean enabled) {
        String path = enabled ? "gui.admin.state-enabled" : "gui.admin.state-disabled";
        return message(path);
    }

    private String message(String path) {
        return messages.render(path, MessageFallbacks.string(path));
    }

    private String message(String path, Map<String, String> placeholders) {
        return messages.render(path, MessageFallbacks.string(path), placeholders);
    }

    private List<String> messageList(String path, Map<String, String> placeholders) {
        List<String> rendered = messages.renderList(path, MessageFallbacks.list(path), placeholders);
        if (rendered.size() == 1 && rendered.get(0).equals(path)) {
            Map<String, String> safePlaceholders = placeholders == null ? Map.of() : placeholders;
            return MessageFallbacks.list(path).stream()
                    .map(line -> messages.renderTemplate(line, safePlaceholders))
                    .toList();
        }
        return rendered;
    }

    private boolean canOpenLeaderboard(Player player) {
        return canUseLeaderboardAction(player, leaderboardOpenTimes, LEADERBOARD_OPEN_COOLDOWN_MILLIS);
    }

    private boolean canUseLeaderboardAction(Player player, Map<UUID, Long> actionTimes, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Long previous = actionTimes.put(player.getUniqueId(), now);
        return previous == null || now - previous >= cooldownMillis;
    }

    private boolean isAdminEditorTitle(String title) {
        return title.equals(message("gui.admin.title"))
                || title.equals(message("gui.rewards.title"))
                || title.equals(message("gui.reward-sender.title"))
                || title.equals(message("gui.reward-edit.title"))
                || title.equals(message("gui.reward-delete.title"));
    }

    private void fill(Inventory inventory, Material material) {
        ItemStack filler = item(material, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material guiMaterial(String path, Material fallback) {
        String configured = guiConfig.getString(path, fallback.name());
        Material material = MaterialTypes.match(configured);
        return material == null ? fallback : material;
    }

    private int guiRows(String path, int fallback) {
        int rows = guiConfig.getInt(path, fallback);
        return Math.max(3, Math.min(6, rows));
    }

    private int guiSlot(String path, int fallback, int inventorySize) {
        int slot = guiConfig.getInt(path, fallback);
        if (slot < 0 || slot >= inventorySize) {
            return Math.max(0, Math.min(inventorySize - 1, fallback));
        }
        return slot;
    }

    private record RewardBrowserState(int page, String search) {
    }

    private record RewardEditState(int streak, String configKey, int index) {
    }

    private record PendingRewardState(int streak) {
    }

    private record RewardDeleteState(int streak, String configKey, int index) {
    }
}
