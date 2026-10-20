package com.createstats.vccmk4.air;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialCockpitModelTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path MODELS = ASSETS.resolve("models/block/vcc_mk4_aerial_cockpit");
    private static final Path TEXTURES = ASSETS.resolve("textures/block");
    private static final Path RENDERER = Path.of("src/main/java/com/createstats/renderer/AerialCockpitRenderer.java");
    private static final Path PARTIALS = Path.of("src/main/java/com/createstats/client/ModPartialModels.java");
    private static final Path GEN = Path.of("gen_vcc_mk4_aerial_cockpit.py");
    private static final String REF = "create_stats:block/vcc_mk4_aerial_cockpit/";
    private static final double[] ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0};

    private static final List<String> MOVING = List.of("shaft", "tip", "tail", "cap", "bug", "earth", "lever", "knob",
            "bar", "horn_l", "horn_r", "boss", "card", "lens", "wing_l", "wing_r", "dot");

    private static String source(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static JsonObject load(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static double[] arr(JsonArray a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static JsonObject element(JsonObject model, String name) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("name") && o.get("name").getAsString().equals(name)) return o;
        }
        throw new AssertionError("no element " + name);
    }

    private static double[] centre(JsonObject e) {
        double[] f = arr(e.getAsJsonArray("from"));
        double[] t = arr(e.getAsJsonArray("to"));
        return new double[]{(f[0] + t[0]) / 2.0, (f[1] + t[1]) / 2.0, (f[2] + t[2]) / 2.0};
    }

    private static double renderer(String src, String name) {
        Matcher m = Pattern.compile("\\b" + name + " = (-?[0-9.]+)f").matcher(src);
        assertTrue(m.find(), "AerialCockpitRenderer has no " + name);
        return Double.parseDouble(m.group(1));
    }

    private static List<Path> partialFiles() throws IOException {
        try (Stream<Path> walk = Files.list(MODELS)) {
            return walk.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("body.json") && !p.getFileName().toString().equals("item.json"))
                    .sorted().toList();
        }
    }

    private static String stem(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - ".json".length());
    }

    @Test
    void everyPartialIsDeclaredHasItsFileAndIsDrawn() throws IOException {
        String declared = source(PARTIALS);
        String r = source(RENDERER);
        Matcher m = Pattern.compile("(AERIAL_[A-Z_]+) = block\\(\"vcc_mk4_aerial_cockpit/([a-z_]+)\"\\)").matcher(declared);
        TreeSet<String> names = new TreeSet<>();
        while (m.find()) {
            names.add(m.group(2));
            assertTrue(Files.exists(MODELS.resolve(m.group(2) + ".json")), m.group(1) + " names a model that is not there");
            assertTrue(r.contains("ModPartialModels." + m.group(1)), m.group(1) + " is declared but never drawn");
        }
        TreeSet<String> files = new TreeSet<>();
        for (Path p : partialFiles()) files.add(stem(p));
        assertEquals(files, names, "every moving part's model is a declared partial, and nothing else is in the folder");
    }

    @Test
    void theRendererLeansThePanelsPartsWhereTheModelLeansThePanels() throws IOException {
        String r = source(RENDERER);
        JsonObject body = load(MODELS.resolve("body.json"));
        for (String[] p : new String[][]{{"panel", "UPPER"}, {"lower_panel", "LOWER"}}) {
            JsonObject rot = element(body, p[0]).getAsJsonObject("rotation");
            assertEquals("x", rot.get("axis").getAsString());
            assertEquals(renderer(r, p[1] + "_ANGLE"), rot.get("angle").getAsDouble(), 1.0e-9, p[0] + " rake");
            double[] o = arr(rot.getAsJsonArray("origin"));
            assertEquals(renderer(r, p[1] + "_Y"), o[1], 1.0e-4, p[0] + " hinge y");
            assertEquals(renderer(r, p[1] + "_Z"), o[2], 1.0e-4, p[0] + " hinge z");
        }
    }

    @Test
    void theHandControlsAndTheCompassTurnAboutWhereTheyAreAuthored() throws IOException {
        String r = source(RENDERER);
        double[] boss = centre(element(load(MODELS.resolve("yoke.json")), "boss"));
        assertEquals(renderer(r, "YOKE_X"), boss[0], 1.0e-4, "the yoke turns about its own boss");
        assertEquals(renderer(r, "YOKE_Y"), boss[1], 1.0e-4);
        for (String[] l : new String[][]{{"throttle_l", "LEVER_L_X"}, {"throttle_r", "LEVER_R_X"}}) {
            JsonObject lever = element(load(MODELS.resolve(l[0] + ".json")), "lever");
            double[] from = arr(lever.getAsJsonArray("from"));
            double[] c = centre(lever);
            assertEquals(renderer(r, l[1]), c[0], 1.0e-4, l[0] + " pivots on its own axis");
            assertTrue(from[1] > renderer(r, "LEVER_Y") && from[1] < renderer(r, "LEVER_Y") + 0.5,
                    l[0] + " stands up from just above its pivot, its foot inside the quadrant's top plate");
            assertEquals(renderer(r, "LEVER_Z"), c[2], 1.0e-4);
        }
        JsonObject card = element(load(MODELS.resolve("compass.json")), "card");
        double[] c = centre(card);
        assertEquals(renderer(r, "COMPASS_X"), c[0], 1.0e-4, "the card turns about its own centre");
        assertEquals(renderer(r, "COMPASS_Z"), c[2], 1.0e-4);
        assertEquals(renderer(r, "COMPASS_Y"), arr(card.getAsJsonArray("from"))[1], 1.0e-4, "lying on its bowl");
    }

    @Test
    void everyElementTurnsByAnAngleAModelAllowsAndEveryTextureItNamesExists() throws IOException {
        List<Path> all = new ArrayList<>(partialFiles());
        all.add(MODELS.resolve("body.json"));
        all.add(MODELS.resolve("item.json"));
        List<String> problems = new ArrayList<>();
        for (Path file : all) {
            JsonObject m = load(file);
            JsonObject tex = m.getAsJsonObject("textures");
            if (!tex.has("particle") || tex.get("particle").getAsString().isEmpty()) problems.add(file + ": no particle");
            for (Map.Entry<String, JsonElement> t : tex.entrySet()) {
                String ref = t.getValue().getAsString();
                if (!ref.startsWith("create_stats:block/")) {
                    problems.add(file + ": " + t.getKey() + " is not ours: " + ref);
                    continue;
                }
                Path png = TEXTURES.resolve(ref.substring("create_stats:block/".length()) + ".png");
                if (!Files.exists(png)) problems.add(file + ": " + t.getKey() + " names a missing " + png);
            }
            for (JsonElement je : m.getAsJsonArray("elements")) {
                JsonObject e = je.getAsJsonObject();
                if (!e.has("rotation")) continue;
                double a = e.getAsJsonObject("rotation").get("angle").getAsDouble();
                boolean ok = false;
                for (double allowed : ANGLES) ok |= Math.abs(a - allowed) < 1.0e-9;
                if (!ok) problems.add(file + ": an element turned " + a + " degrees, which a model file cannot");
                for (JsonElement fe : e.getAsJsonObject("faces").asMap().values()) {
                    String slot = fe.getAsJsonObject().get("texture").getAsString();
                    if (!slot.startsWith("#") || !tex.has(slot.substring(1))) problems.add(file + ": face names " + slot);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyStaticFaceIsTwoTexelsAUnitOrOneFlatColour() throws IOException {
        JsonObject body = load(MODELS.resolve("body.json"));
        BufferedImage sheet = ImageIO.read(TEXTURES.resolve("vcc_mk4_aerial_cockpit/body.png").toFile());
        double per = sheet.getWidth() / 16.0;
        assertEquals(sheet.getWidth(), sheet.getHeight(), "a square sheet: one scale for u and v");
        List<String> problems = new ArrayList<>();
        int faces = 0;
        for (JsonElement je : body.getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            String name = e.has("name") ? e.get("name").getAsString() : "?";
            double[] f = arr(e.getAsJsonArray("from"));
            double[] t = arr(e.getAsJsonArray("to"));
            for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("faces").entrySet()) {
                double[] uv = arr(fe.getValue().getAsJsonObject().getAsJsonArray("uv"));
                double du = Math.abs(uv[2] - uv[0]) * per;
                double dv = Math.abs(uv[3] - uv[1]) * per;
                double[] size = switch (fe.getKey()) {
                    case "up", "down" -> new double[]{t[0] - f[0], t[2] - f[2]};
                    case "north", "south" -> new double[]{t[0] - f[0], t[1] - f[1]};
                    default -> new double[]{t[2] - f[2], t[1] - f[1]};
                };
                faces++;

                boolean flat = du < 1.0e-6 && dv < 1.0e-6;
                boolean dense = dense(du, size[0]) && dense(dv, size[1]);
                if (!flat && !dense) {
                    problems.add(String.format("%s.%s: %.3f x %.3f texels on %.3f x %.3f units", name, fe.getKey(), du, dv,
                            size[0], size[1]));
                }
            }
        }
        assertTrue(faces > 50, "the body has its faces");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static boolean dense(double texels, double units) {
        double want = 2.0 * units;
        if (want < 1.0 - 1.0e-6) return Math.abs(texels - 1.0) < 1.0e-3;
        return Math.abs(texels - want) < 2.0e-3;
    }

    @Test
    void theStaticBodyHoldsNoMovingPart() throws IOException {
        for (JsonElement je : load(MODELS.resolve("body.json")).getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            String name = e.has("name") ? e.get("name").getAsString() : "";
            assertFalse(MOVING.contains(name), "a moving part baked into the static body would stand still in it: " + name);
        }
    }

    @Test
    void theBlockstateTurnsTheBodyTheWayTheRendererTurnsItsParts() throws IOException {
        JsonObject variants = load(ASSETS.resolve("blockstates/vcc_mk4_aerial.json")).getAsJsonObject("variants");
        assertEquals(4, variants.size(), "one property, facing: every saved blockstate is still a valid one");
        String[][] want = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};
        for (String[] w : want) {
            JsonObject v = variants.getAsJsonObject("facing=" + w[0]);
            assertEquals(REF + "body", v.get("model").getAsString(), w[0]);
            assertEquals(Integer.parseInt(w[1]), v.has("y") ? v.get("y").getAsInt() : 0, w[0]);
        }
        String r = source(RENDERER);
        assertTrue(r.contains("case EAST -> 90.0f;") && r.contains("case SOUTH -> 180.0f;") && r.contains("case WEST -> 270.0f;"),
                "the renderer turns its parts by the blockstate's own angles");
        assertTrue(r.contains("Axis.YP.rotationDegrees(-degrees)"), "a blockstate y of A is a turn of minus A about +Y");
    }

    @Test
    void theItemShowsTheCockpitAtRestWithItsInstrumentFaceTurnedToTheSlot() throws IOException {
        JsonObject item = load(ASSETS.resolve("models/item/vcc_mk4_aerial.json"));
        assertEquals(REF + "item", item.get("parent").getAsString());
        JsonObject model = load(MODELS.resolve("item.json"));
        assertEquals("minecraft:block/block", model.get("parent").getAsString(), "the block's own lighting and hand poses");
        double[] gui = arr(model.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("rotation"));
        assertEquals(30.0, gui[0], 1.0e-9);
        assertEquals(315.0, gui[1], 1.0e-9, "turned 315: the pilot's side, where the instruments face, toward the slot");
        assertEquals(0.0, gui[2], 1.0e-9);
        TreeSet<String> names = new TreeSet<>();
        for (JsonElement je : model.getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            if (e.has("name")) names.add(e.get("name").getAsString());
        }
        for (String part : new String[]{"bar", "horn_l", "boss", "lever", "knob", "card", "earth", "wing_l", "panel"}) {
            assertTrue(names.contains(part), "the item shows the " + part);
        }
    }
}
