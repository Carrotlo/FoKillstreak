package me.foesio.foKillstreak.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import me.foesio.foKillstreak.FoKillstreak;
import me.foesio.foKillstreak.config.GuiConfigService;
import me.foesio.foKillstreak.data.LeaderboardEntry;
import me.foesio.foKillstreak.data.LeaderboardType;
import me.foesio.foKillstreak.data.StreakStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaperDialogLeaderboardService implements LeaderboardDialogService {
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)#[0-9a-f]{6}");
    private static final int BUTTON_WIDTH = 150;
    private static final ClickCallback.Options TAB_CLICK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(ClickCallback.DEFAULT_LIFETIME)
            .build();

    private final FoKillstreak plugin;
    private final NativeDialogSupport nativeDialogs;
    private final GuiConfigService guiConfig;
    private final FoMessageService messages;
    private final StreakStorage storage;
    private final LeaderboardDialogServiceFactory.Fallback fallback;

    public PaperDialogLeaderboardService(FoKillstreak plugin, NativeDialogSupport nativeDialogs, GuiConfigService guiConfig, FoMessageService messages, StreakStorage storage, LeaderboardDialogServiceFactory.Fallback fallback) {
        this.plugin = plugin;
        this.nativeDialogs = nativeDialogs;
        this.guiConfig = guiConfig;
        this.messages = messages;
        this.storage = storage;
        this.fallback = fallback;
    }

    @Override
    public boolean open(Player player, LeaderboardType type) {
        if (!nativeDialogs.canUseNativeDialogs(player)) {
            return false;
        }

        try {
            Dialog dialog = createDialog(type);
            player.closeInventory();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                try {
                    player.showDialog(dialog);
                } catch (RuntimeException | LinkageError exception) {
                    fallbackAfterFailure(player, exception);
                }
            });
            return true;
        } catch (RuntimeException | LinkageError exception) {
            fallbackAfterFailure(player, exception);
            return true;
        }
    }

    private Dialog createDialog(LeaderboardType type) {
        Component title = component(guiString("native.title", "Top Killstreaks"), color("native.title-color", 0xffffff));
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .externalTitle(title)
                        .body(leaderboardBodies(type))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.multiAction(List.of(tabButton(type, LeaderboardType.CURRENT), tabButton(type, LeaderboardType.BEST)))
                        .columns(2)
                        .build())
        );
    }

    private List<DialogBody> leaderboardBodies(LeaderboardType type) {
        int limit = leaderboardLimit();
        int width = guiInt("native.body-width", 260);
        List<LeaderboardEntry> top = storage.top(type, limit);
        List<DialogBody> bodies = new ArrayList<>();
        String format = guiString("native.line-format", "{rank_color}#{rank} {white}{player} &8- &c{skull} {white}{streak}");
        String skull = guiString("native.symbol", "☠");
        String emptyPlayer = guiString("native.empty-player", "---");
        String emptyStreak = guiString("native.empty-streak", "---");
        for (int rank = 1; rank <= limit; rank++) {
            if (top.size() >= rank) {
                LeaderboardEntry entry = top.get(rank - 1);
                bodies.add(DialogBody.plainMessage(leaderboardLine(format, entry.rank(), entry.name(), String.valueOf(entry.streak()), skull), width));
            } else {
                bodies.add(DialogBody.plainMessage(leaderboardLine(format, rank, emptyPlayer, emptyStreak, skull), width));
            }
        }
        return bodies;
    }

    private Component leaderboardLine(String format, int rank, String player, String streak, String skull) {
        String line = messages.renderTemplate(format, Map.of(
                "rank", String.valueOf(rank),
                "rank_color", rankColorToken(rank),
                "player", player,
                "streak", streak,
                "skull", skull
        ));
        return LegacyComponentSerializer.legacySection().deserialize(line).colorIfAbsent(rankTextColor(rank));
    }

    private ActionButton tabButton(LeaderboardType selected, LeaderboardType buttonType) {
        boolean isSelected = selected == buttonType;
        ActionButton.Builder builder = ActionButton.builder(component(tabButtonLabel(buttonType, isSelected), tabButtonColor(isSelected)))
                .width(BUTTON_WIDTH);
        if (isSelected) {
            return builder.action(null).build();
        }
        builder.tooltip(component(guiString("native.switch-tooltip", "Switch leaderboard"), color("native.tooltip-color", 0xa7b8b0)));
        return builder.action(DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player target) {
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.gui().openLeaderboardTab(target, buttonType));
            }
        }, TAB_CLICK_OPTIONS)).build();
    }

    private String tabButtonLabel(LeaderboardType type, boolean selected) {
        if (selected) {
            return guiString(nativeSelectedButtonPath(type), selectedButtonFallbackLabel(type));
        }
        return guiString(nativeButtonPath(type), fallbackButtonLabel(type));
    }

    private TextColor tabButtonColor(boolean selected) {
        return selected ? color("native.selected-button-color", 0xa7b8b0) : color("native.inactive-button-color", 0xffffff);
    }

    private void fallbackAfterFailure(Player player, Throwable exception) {
        if (!nativeDialogs.runtimeDisabled()) {
            nativeDialogs.disableForSession("Native leaderboard dialog failed: " + exception.getClass().getSimpleName());
            String message = exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage();
            plugin.getLogger().warning("Native leaderboard dialog failed at runtime; using inventory fallback: " + message);
        }
        fallback.open(player);
    }

    private String nativeButtonPath(LeaderboardType type) {
        return type == LeaderboardType.CURRENT ? "native.current-button" : "native.best-button";
    }

    private String nativeSelectedButtonPath(LeaderboardType type) {
        return type == LeaderboardType.CURRENT ? "native.selected-current-button" : "native.selected-best-button";
    }

    private String fallbackButtonLabel(LeaderboardType type) {
        if (type == LeaderboardType.CURRENT) {
            return "{white}Current";
        }
        return "{white}Best";
    }

    private String selectedButtonFallbackLabel(LeaderboardType type) {
        if (type == LeaderboardType.CURRENT) {
            return "{muted}☠ Current";
        }
        return "{muted}☠ Best";
    }

    private TextColor rankTextColor(int rank) {
        return switch (rank) {
            case 1 -> color("native.first-color", 0xffd84d);
            case 2 -> color("native.second-color", 0xc9d1d9);
            case 3 -> color("native.third-color", 0xd9822b);
            default -> color("native.default-color", 0xa7b8b0);
        };
    }

    private String rankColorToken(int rank) {
        String configured = switch (rank) {
            case 1 -> guiString("native.first-color", "#ffd84d");
            case 2 -> guiString("native.second-color", "#c9d1d9");
            case 3 -> guiString("native.third-color", "#d9822b");
            default -> guiString("native.default-color", "#a7b8b0");
        };
        return messages.renderTemplate(configured, Map.of());
    }

    private Component component(String text, TextColor color) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(FoText.color(messages.renderTemplate(text, Map.of())));
        return color == null ? component : component.colorIfAbsent(color);
    }

    private TextColor color(String path, int fallback) {
        String configured = guiString(path, String.format(Locale.ROOT, "#%06x", fallback));
        TextColor parsed = parseTextColor(configured);
        if (parsed != null) {
            return parsed;
        }
        parsed = parseTextColor(messages.renderTemplate(configured, Map.of()));
        return parsed == null ? TextColor.color(fallback) : parsed;
    }

    private TextColor parseTextColor(String value) {
        if (value == null) {
            return null;
        }

        Matcher matcher = HEX_PATTERN.matcher(value);
        if (matcher.find()) {
            return parseHexColor(matcher.group().substring(1));
        }

        for (int index = 0; index + 13 < value.length(); index++) {
            if (value.charAt(index) != '\u00A7' || Character.toLowerCase(value.charAt(index + 1)) != 'x') {
                continue;
            }

            StringBuilder hex = new StringBuilder(6);
            boolean valid = true;
            for (int offset = 2; offset <= 12; offset += 2) {
                if (value.charAt(index + offset) != '\u00A7') {
                    valid = false;
                    break;
                }
                hex.append(value.charAt(index + offset + 1));
            }
            if (valid) {
                return parseHexColor(hex.toString());
            }
        }
        return null;
    }

    private TextColor parseHexColor(String hex) {
        try {
            return TextColor.color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int guiInt(String path, int fallback) {
        return guiConfig.getInt(path, fallback);
    }

    private int leaderboardLimit() {
        return Math.max(1, guiInt("native.limit", 10));
    }

    private String guiString(String path, String fallback) {
        return guiConfig.getString(path, fallback);
    }
}
