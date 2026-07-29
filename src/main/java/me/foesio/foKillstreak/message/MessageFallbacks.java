package me.foesio.foKillstreak.message;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public final class MessageFallbacks {
    private static final Map<String, String> STRINGS = Map.ofEntries(
            entry("commands.no-permission", "{prefix}{bad}No permission{muted}."),
            entry("commands.only-player", "{prefix}{bad}Player only{muted}."),
            entry("commands.usage-admin", "{prefix}Use {theme}/fokillstreakadmin <version|reload|editor|set>{muted}."),
            entry("commands.usage-set", "{prefix}Use {theme}/fokillstreakadmin set <player> <streak> <best|current>{muted}."),
            entry("commands.reloaded", "{prefix}{good}Reloaded {theme}config.yml{muted}, {theme}messages.yml{muted}, and {theme}guis/{muted}."),
            entry("commands.reload-failed", "{prefix}{bad}Reload failed{muted}. Check console."),
            entry("commands.player-not-found", "{prefix}{bad}Player not found{muted}. They must be online or known to the server."),
            entry("commands.invalid-streak", "{prefix}{bad}Invalid streak{muted}. Use a whole number of {theme}0{muted} or higher."),
            entry("commands.invalid-streak-type", "{prefix}{bad}Invalid type{muted}. Use {theme}best{muted} or {theme}current{muted}."),
            entry("commands.streak-set", "{prefix}{good}Set {theme}{player}{muted}'s {theme}{type}{muted} streak to {theme}{streak}{muted}."),
            entry("killstreak.cooldown-blocked", "{prefix}{bad}No streak gained{muted}. Wait {theme}{cooldown}s{muted} before killing {theme}{victim}{muted} again."),
            entry("broadcasts.milestone", "{prefix}{theme}{player}{muted} reached a {theme}{streak}{muted} killstreak."),
            entry("editor.saved", "{prefix}{good}Saved {theme}{setting}{muted}."),
            entry("editor.save-failed", "{prefix}{bad}Could not save {theme}{setting}{muted}. Check console."),
            entry("editor.cancelled", "{prefix}{bad}Cancelled{muted}."),
            entry("editor.invalid-number", "{prefix}{bad}Invalid number{muted}. Use a whole number above zero."),
            entry("editor.invalid-list", "{prefix}{bad}Invalid list{muted}. Use comma-separated whole numbers, like {theme}5,10,15{muted}."),
            entry("editor.invalid-reward-streak", "{prefix}{bad}Invalid reward streak{muted}. Use a whole number above {theme}0{muted}."),
            entry("editor.invalid-reward-command", "{prefix}{bad}Invalid reward command{muted}. Type a command without a leading slash."),
            entry("editor.reward-added", "{prefix}{good}Added reward for {theme}{streak}{muted} kills."),
            entry("editor.reward-deleted", "{prefix}{good}Deleted reward for {theme}{streak}{muted} kills."),
            entry("editor.reward-missing", "{prefix}{bad}Reward missing{muted}. It may have been edited already."),
            entry("editor.search-applied", "{prefix}{good}Search applied{muted}: {theme}{search}"),
            entry("editor.search-cleared", "{prefix}{good}Search cleared{muted}."),
            entry("gui.admin.title", "&8ꜰᴏᴋɪʟʟꜱᴛʀᴇᴀᴋ ᴇᴅɪᴛᴏʀ"),
            entry("gui.admin.player-death-reset-enabled-name", "{good}ᴘᴠᴘ ᴅᴇᴀᴛʜ ʀᴇꜱᴇᴛꜱ"),
            entry("gui.admin.player-death-reset-disabled-name", "{bad}ᴀʟʟ ᴅᴇᴀᴛʜꜱ ʀᴇꜱᴇᴛ"),
            entry("gui.admin.broadcasts-enabled-name", "{good}ᴍɪʟᴇꜱᴛᴏɴᴇ ʙʀᴏᴀᴅᴄᴀꜱᴛꜱ"),
            entry("gui.admin.broadcasts-disabled-name", "{bad}ᴍɪʟᴇꜱᴛᴏɴᴇ ʙʀᴏᴀᴅᴄᴀꜱᴛꜱ"),
            entry("gui.admin.cooldown-enabled-name", "{good}ꜱᴀᴍᴇ-ᴠɪᴄᴛɪᴍ ᴄᴏᴏʟᴅᴏᴡɴ"),
            entry("gui.admin.cooldown-disabled-name", "{bad}ꜱᴀᴍᴇ-ᴠɪᴄᴛɪᴍ ᴄᴏᴏʟᴅᴏᴡɴ"),
            entry("gui.admin.cooldown-seconds-name", "{theme}ᴄᴏᴏʟᴅᴏᴡɴ ꜱᴇᴄᴏɴᴅꜱ"),
            entry("gui.admin.milestones-name", "{theme}ᴍɪʟᴇꜱᴛᴏɴᴇꜱ"),
            entry("gui.admin.rewards-name", "{theme}ʀᴇᴡᴀʀᴅꜱ"),
            entry("gui.admin.reload-name", "{theme}ʀᴇʟᴏᴀᴅ ꜰɪʟᴇꜱ"),
            entry("gui.admin.state-enabled", "{good}Enabled"),
            entry("gui.admin.state-disabled", "{bad}Disabled"),
            entry("gui.rewards.title", "&8ꜰᴏᴋɪʟʟꜱᴛʀᴇᴀᴋ ʀᴇᴡᴀʀᴅꜱ"),
            entry("gui.rewards.reward-name", "{theme}{streak} ᴋɪʟʟꜱ"),
            entry("gui.rewards.empty-name", "{bad}ɴᴏ ʀᴇᴡᴀʀᴅꜱ"),
            entry("gui.rewards.add-name", "{good}ᴀᴅᴅ ʀᴇᴡᴀʀᴅ"),
            entry("gui.reward-sender.title", "&8ꜱᴇʟᴇᴄᴛ ʀᴇᴡᴀʀᴅ ꜱᴇɴᴅᴇʀ"),
            entry("gui.reward-sender.console-name", "{theme}ᴄᴏɴꜱᴏʟᴇ"),
            entry("gui.reward-sender.player-name", "{theme}ᴘʟᴀʏᴇʀ"),
            entry("gui.reward-edit.title", "&8ᴇᴅɪᴛ ʀᴇᴡᴀʀᴅ"),
            entry("gui.reward-edit.streak-name", "{theme}ᴋɪʟʟꜱᴛʀᴇᴀᴋ"),
            entry("gui.reward-edit.sender-name", "{theme}ꜱᴇɴᴅᴇʀ"),
            entry("gui.reward-edit.sender-selected", "{theme}{sender}"),
            entry("gui.reward-edit.sender-unselected", "{white}{sender}"),
            entry("gui.reward-edit.command-name", "{theme}ᴄᴏᴍᴍᴀɴᴅ"),
            entry("gui.reward-edit.delete-name", "{bad}ᴅᴇʟᴇᴛᴇ"),
            entry("gui.reward-delete.title", "&8ᴅᴇʟᴇᴛᴇ ʀᴇᴡᴀʀᴅ"),
            entry("gui.reward-delete.confirm-name", "{good}ᴄᴏɴꜰɪʀᴍ"),
            entry("gui.reward-delete.cancel-name", "{bad}ᴄᴀɴᴄᴇʟ")
    );

    private static final Map<String, List<String>> LISTS = Map.ofEntries(
            entry("gui.admin.player-death-reset-lore", List.of(
                    "{white}Click to toggle natural death resets.",
                    "{white}Current: {state}"
            )),
            entry("gui.admin.broadcasts-lore", List.of(
                    "{white}Click to toggle milestone broadcasts.",
                    "{white}Current: {state}"
            )),
            entry("gui.admin.cooldown-lore", List.of(
                    "{white}Click to toggle farming protection.",
                    "{white}Current: {state}"
            )),
            entry("gui.admin.cooldown-seconds-lore", List.of(
                    "{white}Click to edit in chat.",
                    "{white}Current: {theme}{seconds}s"
            )),
            entry("gui.admin.milestones-lore", List.of(
                    "{white}Click to edit in chat.",
                    "{white}Current: {theme}{milestones}"
            )),
            entry("gui.admin.rewards-lore", List.of(
                    "{white}Click to edit killstreak rewards.",
                    "{white}Configured: {theme}{count}"
            )),
            entry("gui.admin.reload-lore", List.of(
                    "{white}Reload config.yml and messages.yml."
            )),
            entry("gui.rewards.reward-lore", List.of(
                    "{white}Sender: {theme}{sender}",
                    "{white}Command: {theme}{command}",
                    "{white}Click to edit."
            )),
            entry("gui.rewards.empty-lore", List.of(
                    "{white}Click Add Reward below."
            )),
            entry("gui.rewards.add-lore", List.of(
                    "{white}Create killstreak reward."
            )),
            entry("gui.reward-sender.console-lore", List.of(
                    "{white}Run reward command from console.",
                    "{white}Streak: {theme}{streak}"
            )),
            entry("gui.reward-sender.player-lore", List.of(
                    "{white}Run reward command as killer.",
                    "{white}Streak: {theme}{streak}"
            )),
            entry("gui.reward-edit.streak-lore", List.of(
                    "{white}Reward runs at {theme}{streak}{white} kills."
            )),
            entry("gui.reward-edit.sender-lore", List.of(
                    "{white}Click to cycle sender.",
                    "{console}",
                    "{player}"
            )),
            entry("gui.reward-edit.command-lore", List.of(
                    "{white}Click to edit in chat.",
                    "{white}Current: {theme}{command}"
            )),
            entry("gui.reward-edit.delete-lore", List.of(
                    "{white}Open delete confirmation."
            )),
            entry("gui.reward-delete.confirm-lore", List.of(
                    "{white}Delete reward for {theme}{streak}{white} kills.",
                    "{white}{command}"
            )),
            entry("gui.reward-delete.cancel-lore", List.of(
                    "{white}Keep this reward."
            ))
    );

    private MessageFallbacks() {
    }

    public static String string(String path) {
        return STRINGS.getOrDefault(path, "");
    }

    public static List<String> list(String path) {
        return LISTS.getOrDefault(path, List.of(""));
    }
}
