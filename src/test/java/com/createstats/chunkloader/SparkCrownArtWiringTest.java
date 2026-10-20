package com.createstats.chunkloader;

import com.createstats.chunkloader.core.CrownStuds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkCrownArtWiringTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");

    private static String source(String relative) throws IOException {
        final Path p = MAIN.resolve(relative);
        assertTrue(Files.exists(p), "missing source: " + p);

        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static JsonObject json(String relative) throws IOException {
        final Path p = ASSETS.resolve(relative);
        assertTrue(Files.exists(p), "missing asset: " + p);
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String statement(String text, String start) {
        final int at = text.indexOf(start);
        assertTrue(at >= 0, "not found, so this test guards nothing: " + start);
        final int end = text.indexOf(';', at);
        return text.substring(at, end < 0 ? text.length() : end + 1);
    }

    private static String call(String text, String start) {
        final int at = text.indexOf(start);
        assertTrue(at >= 0 && start.endsWith("("), "not found, so this test guards nothing: " + start);
        int depth = 0;
        for (int i = at + start.length() - 1; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return text.substring(at, i + 1);
            }
        }
        return text.substring(at);
    }

    @Test
    @DisplayName("the block entity's renderer is registered with it")
    void theRendererIsRegistered() throws IOException {
        final String reg = statement(source("registry/ModBlockEntities.java"), "SPARK_CROWN = ");
        assertTrue(reg.contains("blockEntity(\"spark_crown\""), "not the crown's registration: " + reg);
        assertTrue(Pattern.compile("\\.renderer\\(\\s*\\(\\)\\s*->\\s*com\\.createstats\\.renderer\\.SparkCrownRenderer::new\\s*\\)")
                .matcher(reg).find(), "no renderer: the crown, its light and its wires are not drawn: " + reg);
    }

    @Test
    @DisplayName("the item's renderer is registered for the crown's item, and its model hands it the drawing")
    void theItemRendererIsRegistered() throws IOException {
        final String setup = source("chunkloader/client/CrownClientSetup.java");
        final String reg = call(setup, "event.registerItem(");
        assertTrue(reg.contains("SparkCrownItemRenderer.get()"), "the item is drawn by nothing: " + reg);
        assertTrue(reg.contains("ModBlocks.SPARK_CROWN.asItem()"), "registered for another item: " + reg);
        assertTrue(setup.contains("@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = "
                + "EventBusSubscriber.Bus.MOD)"), "the registration handler is on no bus");
        assertTrue(Pattern.compile("@SubscribeEvent\\s+public static void itemRenderer\\(RegisterClientExtensionsEvent "
                + "event\\)").matcher(setup).find(), "the handler is not subscribed, or does not take the extensions"
                + " event: it never runs, and the item is drawn by nothing");
        final JsonObject item = json("models/item/spark_crown.json");
        assertEquals("minecraft:builtin/entity", item.get("parent").getAsString(),
                "a plain model would show a flat cabinet, not the folded crown");
        final JsonArray gui = item.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("rotation");
        assertEquals(315.0, gui.get(1).getAsDouble(), 1e-9,
                "turned 315 the front (nixies, dial) shows; the default 225 shows the back (memory item-gui-turn)");
        assertEquals(30.0, gui.get(0).getAsDouble(), 1e-9, "the three-quarter tip of every block item");
    }

    @Test
    @DisplayName("the block model's stud caps sit on CrownStuds' points, so every wire lands on its stud")
    void theCapsAreWhereTheWiresGo() throws IOException {
        final JsonObject model = json("models/block/spark_crown.json");
        int found = 0;
        for (JsonElement e : model.getAsJsonArray("elements")) {
            final JsonObject el = e.getAsJsonObject();
            if (!el.has("name") || !el.get("name").getAsString().matches("stud [AB] cap")) {
                continue;
            }
            final int stud = el.get("name").getAsString().charAt(5) == 'A' ? 0 : 1;
            final JsonArray from = el.getAsJsonArray("from");
            final JsonArray to = el.getAsJsonArray("to");
            final double[] at = CrownStuds.attachPoint(0, stud);
            assertEquals(at[0] * 16, (from.get(0).getAsDouble() + to.get(0).getAsDouble()) / 2, 1e-6, "cap x");
            assertEquals(at[1] * 16, (from.get(1).getAsDouble() + to.get(1).getAsDouble()) / 2, 1e-6, "cap y");
            assertEquals(at[2] * 16, from.get(2).getAsDouble(), 1e-6, "the cap's face is where the wire meets it");
            assertTrue(el.getAsJsonObject("faces").has("north"), "the cap's face is drawn");
            found++;
        }
        assertEquals(2, found, "two stud caps, named 'stud A cap' and 'stud B cap'");
        for (JsonElement e : model.getAsJsonArray("elements")) {
            for (String axis : new String[] {"from", "to"}) {
                for (JsonElement v : e.getAsJsonObject().getAsJsonArray(axis)) {
                    assertTrue(v.getAsDouble() >= 0 && v.getAsDouble() <= 16,
                            "the cabinet is one block: " + e.getAsJsonObject().get("name"));
                }
            }
        }
    }

    @Test
    @DisplayName("the blockstate turns the front-south model the way CrownStuds turns the stud points")
    void theBlockstateTurns() throws IOException {
        final JsonObject variants = json("blockstates/spark_crown.json").getAsJsonObject("variants");
        final String[] facings = {"south", "west", "north", "east"};
        for (String f : facings) {
            final JsonObject v = variants.getAsJsonObject("facing=" + f);
            assertEquals("create_stats:block/spark_crown", v.get("model").getAsString());
            final int y = v.has("y") ? v.get("y").getAsInt() : 0;
            assertEquals(90 * CrownStuds.turnsFor(f), y, "facing " + f);
        }
    }
}
