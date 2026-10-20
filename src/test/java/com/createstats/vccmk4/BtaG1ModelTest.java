package com.createstats.vccmk4;

import com.createstats.vccmk4.core.BtaG1Motion;
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

class BtaG1ModelTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path MODELS = ASSETS.resolve("models/block");
    private static final Path TEXTURES = ASSETS.resolve("textures/block");
    private static final Path RENDERER = Path.of("src/main/java/com/createstats/renderer/BtaG1Renderer.java");
    private static final Path PARTIALS = Path.of("src/main/java/com/createstats/client/ModPartialModels.java");

    private static final List<String> FREE = List.of("wheel", "needle");
    private static final double[] ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0};

    private static JsonObject load(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<Path> partialFiles() throws IOException {
        try (Stream<Path> walk = Files.list(MODELS.resolve("bta_g1"))) {
            return walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static String stem(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - ".json".length());
    }

    private static double[] arr(JsonArray a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static double[] dims(double[] from, double[] to, String face) {
        double[] s = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
        return switch (face) {
            case "up", "down" -> new double[]{s[0], s[2]};
            case "north", "south" -> new double[]{s[0], s[1]};
            default -> new double[]{s[2], s[1]};
        };
    }

    private static double[] autoUv(double[] f, double[] t, String face) {
        return switch (face) {
            case "up" -> new double[]{f[0], f[2], t[0], t[2]};
            case "down" -> new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case "north" -> new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case "south" -> new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case "west" -> new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            default -> new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }

    @Test
    void everyFaceIsExactlyTwoTexelsPerUnitAndTheStaticOnesStartOnAWholeTexel() throws IOException {
        List<String> problems = new ArrayList<>();
        int faces = 0;
        List<Path> all = new ArrayList<>(partialFiles());
        all.add(MODELS.resolve("bta_g1.json"));
        all.add(ASSETS.resolve("models/item/bta_g1.json"));
        for (Path file : all) {
            boolean free = FREE.contains(stem(file));
            for (JsonElement e : load(file).getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                double[] f = arr(el.getAsJsonArray("from"));
                double[] t = arr(el.getAsJsonArray("to"));
                for (Map.Entry<String, JsonElement> entry : el.getAsJsonObject("faces").entrySet()) {
                    faces++;
                    JsonObject face = entry.getValue().getAsJsonObject();
                    double[] uv = face.has("uv") ? arr(face.getAsJsonArray("uv")) : autoUv(f, t, entry.getKey());
                    double[] d = dims(f, t, entry.getKey());
                    String at = file.getFileName() + " " + el.get("name").getAsString() + "." + entry.getKey();
                    if (Math.abs(Math.abs(uv[2] - uv[0]) - d[0]) > 1e-6 || Math.abs(Math.abs(uv[3] - uv[1]) - d[1]) > 1e-6) {
                        problems.add(at + ": window " + (uv[2] - uv[0]) + " x " + (uv[3] - uv[1]) + " for a face " + d[0] + " x " + d[1]);
                    }
                    if (free) continue;
                    for (double v : uv) {
                        if (Math.abs(v * 2.0 - Math.round(v * 2.0)) > 1e-6) {
                            problems.add(at + ": window edge " + v + " is off the texel grid");
                        }
                    }
                }
            }
        }
        assertTrue(faces > 300, "only " + faces + " faces scanned; the walk has stopped finding the models");
        assertTrue(problems.isEmpty(), "texel density and alignment:\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyElementTurnsOnlyByAnAngleAModelFileAllows() throws IOException {
        List<String> problems = new ArrayList<>();
        List<Path> all = new ArrayList<>(partialFiles());
        all.add(MODELS.resolve("bta_g1.json"));
        all.add(ASSETS.resolve("models/item/bta_g1.json"));
        for (Path file : all) {
            for (JsonElement e : load(file).getAsJsonArray("elements")) {
                JsonObject el = e.getAsJsonObject();
                if (!el.has("rotation")) continue;
                double angle = el.getAsJsonObject("rotation").get("angle").getAsDouble();
                boolean ok = false;
                for (double a : ANGLES) ok |= a == angle;
                if (!ok) problems.add(file.getFileName() + " " + el.get("name").getAsString() + ": " + angle);
            }
        }
        assertTrue(problems.isEmpty(), "vanilla refuses a rotation other than 0, 22.5 or 45 either way, and the whole model fails to load:\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void everyTextureAModelNamesExistsIsThirtyTwoSquareAndTheParticleIsSet() throws IOException {
        List<String> problems = new ArrayList<>();
        List<Path> all = new ArrayList<>(partialFiles());
        all.add(MODELS.resolve("bta_g1.json"));
        all.add(ASSETS.resolve("models/item/bta_g1.json"));
        for (Path file : all) {
            JsonObject model = load(file);
            JsonObject textures = model.getAsJsonObject("textures");
            String particle = textures.get("particle") == null ? "" : textures.get("particle").getAsString();
            if (particle.isEmpty()) problems.add(file.getFileName() + ": empty particle texture crashes vanilla's parser");
            TreeSet<String> used = new TreeSet<>();
            for (JsonElement e : model.getAsJsonArray("elements")) {
                for (Map.Entry<String, JsonElement> f : e.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    String ref = f.getValue().getAsJsonObject().get("texture").getAsString();
                    if (!ref.startsWith("#")) {
                        problems.add(file.getFileName() + ": texture " + ref + " is not a #slot");
                        continue;
                    }
                    String slot = ref.substring(1);
                    if (!textures.has(slot)) problems.add(file.getFileName() + ": slot " + slot + " is not declared");
                    used.add(slot);
                }
            }
            for (String slot : used) {
                if (!textures.has(slot)) continue;
                String id = textures.get(slot).getAsString();
                Path png = TEXTURES.resolve(id.substring(id.indexOf("block/") + "block/".length()) + ".png");
                if (!Files.isRegularFile(png)) {
                    problems.add(file.getFileName() + ": " + id + " has no file");
                    continue;
                }
                BufferedImage img = ImageIO.read(png.toFile());
                if (img.getWidth() != 32 || img.getHeight() != 32) {
                    problems.add(png.getFileName() + " is " + img.getWidth() + "x" + img.getHeight() + ", not the 32 texel sheet the two texels a unit rule assumes");
                }
            }
        }
        assertTrue(problems.isEmpty(), "textures:\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyLampIsAFlatQuadFacingTheDriverJustProudOfTheArtItLights() throws IOException {
        for (Path file : partialFiles()) {
            String name = stem(file);
            if (!(name.startsWith("lens_") || name.startsWith("strip_") || name.equals("dial_lit"))) continue;
            JsonArray elements = load(file).getAsJsonArray("elements");
            assertEquals(1, elements.size(), name + " is one quad");
            JsonObject el = elements.get(0).getAsJsonObject();
            double[] f = arr(el.getAsJsonArray("from"));
            double[] t = arr(el.getAsJsonArray("to"));
            assertEquals(f[2], t[2], 0.0, name + " is zero thick, so it cannot z-fight with a side of its own");
            assertEquals(1, el.getAsJsonObject("faces").size(), name + " has only the face the driver sees");
            assertTrue(el.getAsJsonObject("faces").has("south"), name);
            double surface = name.startsWith("strip_") ? 5.5 : 8.0;
            assertEquals(surface + 0.05, f[2], 1.0e-9, name + " stands 0.05 of a unit proud of the art under it");
        }
    }

    private static double renderer(String source, String name) {
        Matcher m = Pattern.compile("(?m)\\b" + name + "\\s*=\\s*(-?[0-9.]+)f(\\s*/\\s*16f)?;").matcher(source);
        assertTrue(m.find(), "BtaG1Renderer no longer defines " + name);
        return Double.parseDouble(m.group(1));
    }

    private static JsonObject element(JsonObject model, String name) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            if (e.getAsJsonObject().get("name").getAsString().equals(name)) return e.getAsJsonObject();
        }
        throw new AssertionError("no element named " + name);
    }

    private static String source(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    void theRendererPlacesThePanelsPartsWhereTheModelLeansThePanel() throws IOException {
        String r = source(RENDERER);
        JsonObject block = load(MODELS.resolve("bta_g1.json"));
        for (String name : new String[]{"panel", "bezel_top", "bezel_bottom", "bezel_left", "bezel_right"}) {
            JsonObject rot = element(block, name).getAsJsonObject("rotation");
            assertEquals(renderer(r, "RAKE_DEGREES"), rot.get("angle").getAsDouble(), 1.0e-9, name + " rake");
            double[] o = arr(rot.getAsJsonArray("origin"));
            assertEquals(renderer(r, "HINGE_X"), o[0], 1.0e-6, name + " hinge x");
            assertEquals(renderer(r, "HINGE_Y"), o[1], 1.0e-6, name + " hinge y");
            assertEquals(renderer(r, "HINGE_Z"), o[2], 1.0e-6, name + " hinge z");
        }
        JsonObject dial = load(MODELS.resolve("bta_g1/dial_lit.json"));
        JsonObject quad = dial.getAsJsonArray("elements").get(0).getAsJsonObject();
        double[] f = arr(quad.getAsJsonArray("from"));
        double[] t = arr(quad.getAsJsonArray("to"));
        assertEquals(renderer(r, "DIAL_X"), (f[0] + t[0]) / 2.0, 1.0e-6, "the dial's centre, x");
        assertEquals(renderer(r, "DIAL_Y"), (f[1] + t[1]) / 2.0, 1.0e-6, "the dial's centre, y");
        assertEquals(renderer(r, "DIAL_Z"), 8.0, 1.0e-9, "the panel's face plane");
    }

    @Test
    void theRendererPlacesTheHandControlsAndTheWheelWhereTheRestPoseModelStandsThem() throws IOException {
        String r = source(RENDERER);
        JsonObject item = load(ASSETS.resolve("models/item/bta_g1.json"));

        JsonObject throttle = element(item, "throttle_shaft");
        double[] tf = arr(throttle.getAsJsonArray("from"));
        double[] tt = arr(throttle.getAsJsonArray("to"));
        assertEquals(renderer(r, "THROTTLE_X"), (tf[0] + tt[0]) / 2.0, 1.0e-6);
        assertEquals(renderer(r, "THROTTLE_Y"), tf[1], 1.0e-6, "the shaft starts at its pivot");
        assertEquals(renderer(r, "THROTTLE_Z"), (tf[2] + tt[2]) / 2.0, 1.0e-6);

        JsonObject brake = element(item, "brake_shaft");
        double[] bf = arr(brake.getAsJsonArray("from"));
        double[] bt = arr(brake.getAsJsonArray("to"));
        assertEquals(renderer(r, "BRAKE_X"), (bf[0] + bt[0]) / 2.0, 1.0e-6);
        assertEquals(renderer(r, "BRAKE_Y"), bf[1], 1.0e-6);
        assertEquals(renderer(r, "BRAKE_Z"), (bf[2] + bt[2]) / 2.0, 1.0e-6);
        JsonObject brakeRot = brake.getAsJsonObject("rotation");
        assertEquals(BtaG1Motion.BRAKE_RELEASED_DEG, brakeRot.get("angle").getAsDouble(), 1.0e-9,
                "the item shows the brake released, at the angle the console's motion model says released is");
        double[] po = arr(brakeRot.getAsJsonArray("origin"));
        assertEquals(renderer(r, "BRAKE_X"), po[0], 1.0e-6);
        assertEquals(renderer(r, "BRAKE_Y"), po[1], 1.0e-6);
        assertEquals(renderer(r, "BRAKE_Z"), po[2], 1.0e-6);

        JsonObject flat = element(item, "wheel_flat");
        double[] o = arr(flat.getAsJsonObject("rotation").getAsJsonArray("origin"));
        assertEquals(renderer(r, "WHEEL_X"), o[0], 1.0e-3, "the item's wheel turns about the hub the renderer turns about");
        assertEquals(renderer(r, "WHEEL_Y"), o[1], 1.0e-3);
        assertEquals(renderer(r, "WHEEL_Z"), o[2], 1.0e-3);
        assertEquals(90.0 + renderer(r, "WHEEL_RAKE_DEGREES"), flat.getAsJsonObject("rotation").get("angle").getAsDouble(), 1.0e-9,
                "a horizontal wheel tilted this far faces where the renderer's tilted-back wheel faces");
    }

    @Test
    void theWheelSitsOnTheAxisOfItsColumn() throws IOException {
        String r = source(RENDERER);
        JsonObject column = element(load(MODELS.resolve("bta_g1.json")), "column");
        JsonObject rot = column.getAsJsonObject("rotation");
        double[] base = arr(rot.getAsJsonArray("origin"));
        double lean = Math.toRadians(rot.get("angle").getAsDouble());
        double climb = renderer(r, "WHEEL_Y") - base[1];
        assertEquals(base[2] + climb * Math.tan(lean), renderer(r, "WHEEL_Z"), 1.0e-3,
                "the hub is up the column's own axis, so the wheel is on its post and not beside it");
        assertEquals(base[0], renderer(r, "WHEEL_X"), 1.0e-6);
    }

    @Test
    void theMovingPartsAreAuthoredAboutThePointTheRendererTurnsThemAbout() throws IOException {
        for (String lever : new String[]{"throttle", "brake"}) {
            JsonObject shaft = element(load(MODELS.resolve("bta_g1/" + lever + ".json")), lever + "_shaft");
            double[] f = arr(shaft.getAsJsonArray("from"));
            double[] t = arr(shaft.getAsJsonArray("to"));
            assertEquals(8.0, f[1], 1.0e-9, lever + " starts at its pivot, y 8");
            assertEquals(8.0, (f[0] + t[0]) / 2.0, 1.0e-9, lever + " is centred on x 8");
            assertEquals(8.0, (f[2] + t[2]) / 2.0, 1.0e-9, lever + " is centred on z 8");
        }

        JsonObject wheel = element(load(MODELS.resolve("bta_g1/wheel.json")), "hub");
        double[] hf = arr(wheel.getAsJsonArray("from"));
        double[] ht = arr(wheel.getAsJsonArray("to"));
        assertEquals(8.0, (hf[0] + ht[0]) / 2.0, 1.0e-9, "the wheel's hub is at the block centre, x");
        assertEquals(8.0, (hf[1] + ht[1]) / 2.0, 1.0e-9, "and y");
        JsonObject needle = element(load(MODELS.resolve("bta_g1/needle.json")), "needle_cap");
        double[] nf = arr(needle.getAsJsonArray("from"));
        double[] nt = arr(needle.getAsJsonArray("to"));
        assertEquals(8.0, (nf[0] + nt[0]) / 2.0, 1.0e-9, "the needle's hub is at the block centre, x");
        assertEquals(8.0, (nf[1] + nt[1]) / 2.0, 1.0e-9, "and y");
    }

    @Test
    void everyPartialModelTheRendererNeedsIsDeclaredAndHasAFile() throws IOException {
        String src = source(PARTIALS);
        Matcher m = Pattern.compile("BTA_G1_[A-Z_]+ = block\\(\"(bta_g1/[a-z_]+)\"\\)").matcher(src);
        List<String> declared = new ArrayList<>();
        while (m.find()) declared.add(m.group(1));
        assertEquals(15, declared.size(), "the wheel, needle, two levers, dial back-light, glass, four cells and five strip lamps");
        for (String path : declared) {
            assertTrue(Files.isRegularFile(MODELS.resolve(path + ".json")), path + " has no model file");
        }
        List<String> files = new ArrayList<>();
        for (Path p : partialFiles()) files.add("bta_g1/" + stem(p));
        assertEquals(new TreeSet<>(files), new TreeSet<>(declared), "no partial model file goes unused, and none is missing");
        String r = source(RENDERER);
        for (String name : new TreeSet<>(declared)) {
            String constant = "BTA_G1_" + name.substring("bta_g1/".length()).toUpperCase(java.util.Locale.ROOT);
            assertTrue(r.contains(constant), "BtaG1Renderer never draws " + constant);
        }
    }

    @Test
    void theBlockstateFacesTheModelTheWayTheRendererTurnsIt() throws IOException {
        for (String id : new String[]{"vcc_mk4", "bta_g1"}) {
            JsonObject variants = load(ASSETS.resolve("blockstates/" + id + ".json")).getAsJsonObject("variants");
            assertEquals(4, variants.size(), id);
            int[] expected = {0, 90, 180, 270};
            String[] facing = {"north", "east", "south", "west"};
            for (int i = 0; i < 4; i++) {
                JsonObject v = variants.getAsJsonObject("facing=" + facing[i]);
                assertEquals("create_stats:block/bta_g1", v.get("model").getAsString(), id + " wears this body");
                assertEquals(expected[i], v.has("y") ? v.get("y").getAsInt() : 0, id + " " + facing[i]);
                assertFalse(v.has("x") || v.has("uvlock"), id + ": only the turn the renderer also makes");
            }
        }
        String r = source(RENDERER);
        assertTrue(r.contains("Axis.YP.rotationDegrees(-degrees)"),
                "a blockstate y of A is a clockwise turn from above, a rotation of MINUS A about +Y: the sign three other renderers warn about");
    }

    @Test
    void theStaticModelHoldsNoMovingPartAndTheItemModelHoldsThemAtRest() throws IOException {
        JsonObject block = load(MODELS.resolve("bta_g1.json"));
        JsonObject item = load(ASSETS.resolve("models/item/bta_g1.json"));
        for (String moving : new String[]{"throttle_shaft", "brake_shaft", "wheel_flat"}) {
            boolean inBlock = false;
            for (JsonElement e : block.getAsJsonArray("elements")) inBlock |= e.getAsJsonObject().get("name").getAsString().equals(moving);
            assertFalse(inBlock, moving + " is drawn by the renderer, so the block model must not draw it too");
            element(item, moving);
        }
        assertTrue(item.getAsJsonArray("elements").size() > block.getAsJsonArray("elements").size());
    }

    @Test
    void theMkIvGroundsItemIsThisBodysItem() throws IOException {
        JsonObject item = load(ASSETS.resolve("models/item/vcc_mk4.json"));
        assertEquals("create_stats:item/bta_g1", item.get("parent").getAsString());
        assertEquals(1, item.size(), "a parent and nothing else");
    }

    @Test
    void theFrontPlateReadsMkIv() throws IOException {
        BufferedImage img = ImageIO.read(TEXTURES.resolve("bta_g1_front.png").toFile());
        int x0 = 3, y0 = 12, w = 26, h = 7;
        int ink = 0x443316;
        Map<Character, String[]> glyphs = Map.of(
                'M', new String[]{"101", "111", "111", "101", "101"},
                'K', new String[]{"101", "110", "100", "110", "101"},
                'I', new String[]{"111", "010", "010", "010", "111"},
                'V', new String[]{"101", "101", "101", "101", "010"});
        TreeSet<Integer> want = new TreeSet<>();
        int[][] words = {{'M', 4}, {'K', 8}, {'I', 15}, {'V', 19}};
        for (int[] g : words) {
            String[] rows = glyphs.get((char) g[0]);
            for (int gy = 0; gy < 5; gy++) {
                for (int gx = 0; gx < 3; gx++) {
                    if (rows[gy].charAt(gx) == '1') want.add((1 + gy) * w + g[1] + gx);
                }
            }
        }
        TreeSet<Integer> got = new TreeSet<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x0 + x, y0 + y) & 0xFFFFFF) == ink) got.add(y * w + x);
            }
        }
        assertEquals(want, got, "the plate's legend is not MK IV in the generator's glyphs and places");
    }
}
