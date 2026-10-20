package com.createstats.book;

import com.createstats.book.content.Book;
import com.createstats.book.content.Part;
import com.createstats.book.content.Recipes;
import com.createstats.book.content.Sheet;
import com.createstats.book.layout.MadeText;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MadeTest {
    @Test
    void theRecipesReadAsTheFilesHaveThem() {
        final Recipes.Recipe wire = Recipes.read("copper_wire");
        assertEquals("minecraft:crafting_shaped", wire.type());
        assertEquals(List.of(new Recipes.Stack("minecraft:copper_ingot", 3, false, 0)), wire.inputs());
        assertEquals(new Recipes.Result("create_stats:copper_wire", 3, 100.0), wire.made());

        final Recipes.Recipe shielded = Recipes.read("sequenced_assembly/shielded_wire");
        assertEquals(List.of("create:deploying", "create:pressing", "create:filling"),
                shielded.steps().stream().map(Recipes.Step::type).toList());
        assertEquals("create:golden_sheet", shielded.steps().get(0).adds().id(), "the gold plate tag, shown as Create's");
        assertNull(shielded.steps().get(1).adds(), "a press adds nothing");
        assertEquals(new Recipes.Stack("create_stats:insulating_resin", 1, true, 50), shielded.steps().get(2).adds());
        assertEquals(95.0, shielded.made().chance(), 1.0e-9);
        assertEquals("create_stats:incomplete_shielded_wire", shielded.transitional());

        final Recipes.Recipe hv = Recipes.read("sequenced_assembly/hv_line");
        assertEquals(2, hv.loops());
        assertEquals(4, hv.steps().size());
        assertEquals(new Recipes.Result("create_stats:hv_line", 2, 92.0), hv.made());
        assertEquals(3, hv.results().size());

        final Recipes.Recipe arc = Recipes.read("arc/refined_copper");
        assertEquals(List.of(new Recipes.Stack("minecraft:copper_ingot", 3, false, 0),
                new Recipes.Stack("minecraft:sand", 1, false, 0)), arc.inputs());
        assertEquals(new Recipes.Result("create_stats:refined_copper", 2, 100.0), arc.made());

        final Recipes.Recipe clay = Recipes.read("compacting/unfired_insulator");
        assertEquals(List.of(new Recipes.Stack("minecraft:clay_ball", 2, false, 0),
                new Recipes.Stack("minecraft:bone_meal", 1, false, 0),
                new Recipes.Stack("minecraft:flint", 1, false, 0)), clay.inputs(), "like ingredients counted together");

        final Recipes.Recipe industrial = Recipes.read("mechanical_crafting/industrial_power_connector");
        assertEquals(List.of(new Recipes.Stack("create_stats:porcelain_insulator", 4, false, 0),
                new Recipes.Stack("create:electron_tube", 1, false, 0),
                new Recipes.Stack("create_stats:refined_copper_sheet", 2, false, 0),
                new Recipes.Stack("create_stats:advanced_power_connector", 2, false, 0),
                new Recipes.Stack("create:sturdy_sheet", 1, false, 0)), industrial.inputs(),
                "every key counted as often as the pattern uses it, in the order first used");
    }

    @Test
    void theRowsSayWhatTheFilesSay() throws IOException {
        final Map<String, String> lang = BookFixtures.lang();
        final Map<String, String> names = Map.of("create_stats:shielded_wire", "Shielded Wire",
                "create_stats:insulated_wire", "Insulated Wire", "create:golden_sheet", "Golden Sheet",
                "create_stats:incomplete_shielded_wire", "Incomplete Shielded Wire", "create_stats:hv_line", "HV Line",
                "create:sturdy_sheet", "Sturdy Sheet", "create_stats:refined_copper_sheet", "Refined Copper Sheet",
                "create_stats:incomplete_hv_line", "Incomplete HV Line");
        final MadeText.Words t = new MadeText.Words(
                (key, args) -> String.format(java.util.Locale.ROOT, lang.getOrDefault(key, key), args),
                id -> names.getOrDefault(id, id));
        final Recipes.Recipe shielded = Recipes.read("sequenced_assembly/shielded_wire");
        assertEquals("Shielded Wire, 95%", t.makes(shielded));
        assertEquals("Sequenced Assembly", t.machine(shielded));
        assertEquals("From Insulated Wire: deploy Golden Sheet, press, fill 50 mB of Insulating Resin. Otherwise "
                + "Insulated Wire, 5%. Between steps it is Incomplete Shielded Wire.", t.from(shielded));
        final Recipes.Recipe hv = Recipes.read("sequenced_assembly/hv_line");
        assertEquals("2x HV Line, 92%", t.makes(hv));
        assertEquals("From Sturdy Sheet, 2 times round: deploy Refined Copper Sheet, press, fill 100 mB of Insulating "
                + "Resin, deploy Shielded Wire. Otherwise Refined Copper Sheet, 5% or Sturdy Sheet, 3%. Between steps "
                + "it is Incomplete HV Line.", t.from(hv));
    }

    @Test
    void aSheetsMaterialsAreMadeOnIt() {
        int checked = 0;
        for (final Sheet s : Book.sheets()) {
            if (s.also().isEmpty()) {
                continue;
            }
            final Set<String> made = new HashSet<>();
            for (final Part p : s.parts()) {
                if (p instanceof Part.Made m) {
                    for (final String path : m.recipes()) {
                        final Recipes.Recipe r = Recipes.read(path);
                        r.results().forEach(x -> made.add(x.id()));
                        if (r.transitional() != null) {
                            made.add(r.transitional());
                        }
                    }
                }
            }
            for (final String id : s.also()) {
                assertTrue(made.contains(id), s.id() + " names " + id + " but makes it in no row");
                assertEquals(s.id(), Book.find(id).orElseThrow().id(), id + " is found where it is made");
                checked++;
            }
        }
        assertTrue(checked >= 6, "no material was checked");
    }
}
