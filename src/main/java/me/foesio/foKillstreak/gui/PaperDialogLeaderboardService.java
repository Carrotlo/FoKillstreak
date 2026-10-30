package me.foesio.foKillstreak.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.message.FoMessageService;
import me.foesio.foKillstreak.FoKillstreak;
import me.foesio.foKillstreak.config.GuiConfigService;
import me.foesio.foKillstreak.data.LeaderboardEntry;
import me.foesio.foKillstreak.data.LeaderboardType;
import me.foesio.foKillstreak.data.StreakStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaperDialogLeaderboardService implements LeaderboardDialogService {
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)#[0-9a-f]{6}");
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
            Dialog dialog = createDialog(player, type);
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

    private Dialog createDialog(Player player, LeaderboardType type) {
        Component title = component(player, guiString("native.title"), color("native.title-color"));
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .externalTitle(title)
                        .body(leaderboardBodies(player, type))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.multiAction(guiConfig.getStringList("native.button-order").stream()
                        .map(id -> tabButton(player, type, id.equals("current") ? LeaderboardType.CURRENT : LeaderboardType.BEST))
                        .toList())
                        .columns(guiInt("native.columns"))
                        .build())
        );
    }

    private List<DialogBody> leaderboardBodies(Player player, LeaderboardType type) {
        int limit = leaderboardLimit();
        int width = guiInt("native.body-width");
        List<LeaderboardEntry> top = storage.top(type, limit);
        List<DialogBody> bodies = new ArrayList<>();
        String format = guiString("native.line-format");
        String skull = guiString("native.symbol");
        String emptyPlayer = guiString("native.empty-player");
        String emptyStreak = guiString("native.empty-streak");
        for (int rank = 1; rank <= limit; rank++) {
            if (top.size() >= rank) {
                LeaderboardEntry entry = top.get(rank - 1);
                bodies.add(DialogBody.plainMessage(leaderboardLine(player, format, entry.rank(), entry.name(), guiConfig.formatNumber(entry.streak()), skull), width));
            } else {
                bodies.add(DialogBody.plainMessage(leaderboardLine(player, format, rank, emptyPlayer, emptyStreak, skull), width));
            }
        }
        return bodies;
    }

    private Component leaderboardLine(Player viewer, String format, int rank, String player, String streak, String skull) {
        String line = messages.renderTemplateForViewer(viewer, format, Map.of(
                "rank", guiConfig.formatNumber(rank),
                "rank_color", rankColorToken(rank),
                "player", player,
                "streak", streak,
                "skull", skull
        ));
        Component rendered = DialogIcons.render(viewer, line);
        TextColor configuredColor = rankTextColor(rank);
        return configuredColor == null ? rendered : rendered.colorIfAbsent(configuredColor);
    }

    private ActionButton tabButton(Player player, LeaderboardType selected, LeaderboardType buttonType) {
        boolean isSelected = selected == buttonType;
        ActionButton.Builder builder = ActionButton.builder(component(player, tabButtonLabel(buttonType, isSelected), tabButtonColor(isSelected)))
                .width(guiInt("native.button-width"));
        if (isSelected) {
            return builder.action(null).build();
        }
        if (guiConfig.has("native.switch-tooltip") && !guiString("native.switch-tooltip").isEmpty()) {
            builder.tooltip(component(player, guiString("native.switch-tooltip"), color("native.tooltip-color")));
        }
        return builder.action(DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player target) {
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.gui().openLeaderboardTab(target, buttonType));
            }
        }, TAB_CLICK_OPTIONS)).build();
    }

    private String tabButtonLabel(LeaderboardType type, boolean selected) {
        if (selected) {
            return guiString(nativeSelectedButtonPath(type));
        }
        return guiString(nativeButtonPath(type));
    }

    private TextColor tabButtonColor(boolean selected) {
        return selected ? color("native.selected-button-color") : color("native.inactive-button-color");
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

    private TextColor rankTextColor(int rank) {
        return switch (rank) {
            case 1 -> color("native.first-color");
            case 2 -> color("native.second-color");
            case 3 -> color("native.third-color");
            default -> color("native.default-color");
        };
    }

    private String rankColorToken(int rank) {
        String configured = switch (rank) {
            case 1 -> guiString("native.first-color");
            case 2 -> guiString("native.second-color");
            case 3 -> guiString("native.third-color");
            default -> guiString("native.default-color");
        };
        return messages.renderTemplate(configured, Map.of());
    }

    private Component component(Player viewer, String text, TextColor color) {
        Component component = DialogIcons.render(viewer,
                messages.renderTemplateForViewer(viewer, text, Map.of()));
        return color == null ? component : component.colorIfAbsent(color);
    }

    private TextColor color(String path) {
        String configured = guiString(path);
        TextColor parsed = parseTextColor(configured);
        if (parsed != null) {
            return parsed;
        }
        parsed = parseTextColor(messages.renderTemplate(configured, Map.of()));
        return parsed;
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

    private int guiInt(String path) {
        return guiConfig.getInt(path, 0);
    }

    private int leaderboardLimit() {
        return guiInt("native.limit");
    }

    private String guiString(String path) {
        return guiConfig.getString(path, "");
    }
}
