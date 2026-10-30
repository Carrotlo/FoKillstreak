package me.foesio.foKillstreak.gui;

import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiFrame;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.PublicGuiStartupFallback;
import me.foesio.core.gui.publicgui.PublicGuiText;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardPublicGuiTest {
    @Test
    void configuredNameLoreAndMovedActionUseTheSameSlot() throws Exception {
        YamlConfiguration owner = defaults();
        owner.set("buttons.current.slot", 10);
        owner.set("buttons.current.name", "&aGrün Ü");
        owner.set("buttons.current.lore", List.of("&bÉén", "{options}"));
        owner.set("buttons.current.amount", 2);
        owner.set("buttons.current.custom-model-data", 123);
        owner.set("buttons.current.item-flags", List.of("HIDE_ATTRIBUTES"));
        owner.set("buttons.current.glow", true);
        PublicGuiFrame frame = plan(owner);

        assertEquals("&aGrün Ü", frame.visuals().get(10).fixed().name());
        assertEquals(2, frame.visuals().get(10).fixed().amount());
        assertEquals(123, frame.visuals().get(10).fixed().customModelData());
        assertTrue(frame.visuals().get(10).fixed().glow());
        assertEquals(1, frame.visuals().get(10).fixed().itemFlags().size());
        assertEquals(List.of("&bÉén", "Číslo 3"), PublicGuiText.lore(
                frame.visuals().get(10).fixed().lore(), Map.of(),
                frame.visuals().get(10).cycle(), frame.visuals().get(10).selection(), "test"));
        assertEquals("leaderboard.current", frame.bindings().get(10).action());
        assertFalse(frame.bindings().containsKey(11));
    }

    @Test
    void removedLoreAndDisabledButtonCreateNoGhostAction() throws Exception {
        YamlConfiguration owner = defaults();
        owner.set("buttons.current.lore", List.of());
        owner.set("buttons.best.enabled", false);
        PublicGuiFrame frame = plan(owner);

        assertTrue(frame.visuals().get(11).fixed().lore().isEmpty());
        assertFalse(frame.bindings().containsKey(15));
        assertEquals("filler", frame.visuals().get(15).path());
        owner.set("filler.enabled", false);
        PublicGuiFrame withoutFiller = plan(owner);
        assertFalse(withoutFiller.visuals().containsKey(15));
    }

    @Test
    void removedSpriteTokenDoesNotReturnFromTheTemplate() throws Exception {
        YamlConfiguration owner = defaults();
        owner.set("buttons.current.name", ":diamond_sword: &aName");
        assertTrue(plan(owner).visuals().get(11).fixed().name().contains(":diamond_sword:"));
        owner.set("buttons.current.name", "&aName");
        assertEquals("&aName", plan(owner).visuals().get(11).fixed().name());
    }

    @Test
    void configuredCycleStylesTheViewerPositionSeparatelyFromRankRows() throws Exception {
        YamlConfiguration owner = defaults();
        owner.set("buttons.current.cycle.selected-format", "&a▶ {option}");
        owner.set("buttons.current.cycle.unselected-format", "&7• {option}");
        PublicGuiDefinition definition = PublicGuiSchema.parse(KillstreakGuiManager.LEADERBOARD_MENU_ID,
                "guis/leaderboard.yml", owner, LeaderboardGuiContract.contract());

        assertEquals(List.of("&7• Rang één", "&a▶ Mijn positie"), PublicGuiText.lore(
                definition.buttons().get("current").item().lore(), Map.of(),
                definition.buttons().get("current").cycle(),
                new PublicGuiText.CycleSelection(List.of("Rang één", "Mijn positie"), 1),
                "buttons.current.lore"));
    }

    @Test
    void migratedStyleTokenUsesTheOwnerPaletteInTitleAndItemName() throws Exception {
        YamlConfiguration owner = defaults();
        owner.set("title", "{theme}Ranglijst");
        owner.set("buttons.current.name", "{theme}Huidige reeks");
        PublicGuiDefinition definition = PublicGuiSchema.parse(KillstreakGuiManager.LEADERBOARD_MENU_ID,
                "guis/leaderboard.yml", owner, LeaderboardGuiContract.contract());
        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1,
                Map.of(KillstreakGuiManager.LEADERBOARD_MENU_ID, definition)),
                KillstreakGuiManager.LEADERBOARD_MENU_ID,
                new PublicGuiRenderInput(Map.of("theme", "#aabbcc"), Map.of(), Map.of(
                        "current", new PublicGuiText.CycleSelection(List.of("Rang één", "Mijn positie"), 1),
                        "best", new PublicGuiText.CycleSelection(List.of("Best"), -1)), Map.of()));

        assertEquals("#aabbccRanglijst", frame.title());
        assertEquals("#aabbccHuidige reeks", PublicGuiText.scalar(
                frame.visuals().get(11).fixed().name(), frame.visuals().get(11).placeholders(),
                "buttons.current.name"));
        assertEquals("Huidige reeks", PublicGuiText.scalar(
                frame.visuals().get(11).fixed().name(), Map.of("theme", ""),
                "buttons.current.name"));
    }

    @Test
    void invalidStartupFieldUsesOnlyItsBundledDefaultInMemory() throws Exception {
        YamlConfiguration owner = defaults();
        owner.set("rows", "broken");
        owner.set("buttons.best.name", "&dPersönlich");
        String original = owner.saveToString();
        java.util.ArrayList<PublicGuiStartupFallback.Replacement> replacements = new java.util.ArrayList<>();

        PublicGuiDefinition definition = PublicGuiStartupFallback.parse(
                KillstreakGuiManager.LEADERBOARD_MENU_ID, "guis/leaderboard.yml", owner,
                defaults(), LeaderboardGuiContract.contract(), replacements::add);

        assertEquals(3, definition.rows());
        assertEquals("&dPersönlich", definition.buttons().get("best").item().name());
        assertEquals("rows", replacements.getFirst().path());
        assertTrue(original.contains("broken"));
    }

    private PublicGuiFrame plan(YamlConfiguration yaml) throws Exception {
        PublicGuiDefinition definition = PublicGuiSchema.parse(KillstreakGuiManager.LEADERBOARD_MENU_ID,
                "guis/leaderboard.yml", yaml, LeaderboardGuiContract.contract());
        Map<String, PublicGuiText.CycleSelection> cycles = new java.util.HashMap<>();
        if (definition.buttons().get("current").enabled()) {
            cycles.put("current", new PublicGuiText.CycleSelection(List.of("Číslo 3"), -1));
        }
        if (definition.buttons().get("best").enabled()) {
            cycles.put("best", new PublicGuiText.CycleSelection(List.of("Best"), -1));
        }
        return PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of(KillstreakGuiManager.LEADERBOARD_MENU_ID, definition)),
                KillstreakGuiManager.LEADERBOARD_MENU_ID,
                new PublicGuiRenderInput(Map.of(), Map.of(), cycles, Map.of()));
    }

    private YamlConfiguration defaults() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("guis/leaderboard.yml");
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
