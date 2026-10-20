package com.createstats.vccmk4.air;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialDeckModelTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path MODELS = ASSETS.resolve("models/block");
    private static final Path TEXTURES = ASSETS.resolve("textures/block");
    private static final Path RENDERER = Path.of("src/main/java/com/createstats/renderer/AerialCockpitRenderer.java");
    private static final String DECK = "vcc_mk4_aerial_flight_deck";
    private static final double[] ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0};

    private static JsonObject load(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static double[] arr(com.google.gson.JsonArray a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static double renderer(String src, String name) {
        Matcher m = Pattern.compile("\\b" + name + " = (-?[0-9.]+)f").matcher(src);
        assertTrue(m.find(), "AerialCockpitRenderer has no " + name);
        return Double.parseDouble(m.group(1));
    }

    private static double[] span(JsonObject model, String name, double dx, double[] into) {
        for (JsonElement je : model.getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            if (!e.has("name") || !e.get("name").getAsString().equals(name)) continue;
            into[0] = Math.min(into[0], arr(e.getAsJsonArray("from"))[0] + dx);
            into[1] = Math.max(into[1], arr(e.getAsJsonArray("to"))[0] + dx);
        }
        return into;
    }

    private static double centreX(String name) throws IOException {
        double[] s = {Double.MAX_VALUE, -Double.MAX_VALUE};
        span(load(MODELS.resolve(DECK + "/body_left.json")), name, 0.0, s);
        span(load(MODELS.resolve(DECK + "/body_right.json")), name, 16.0, s);
        assertTrue(s[0] < s[1], "the deck's body has no " + name);
        return (s[0] + s[1]) / 2.0;
    }

    private static double centreX1x1(String name) throws IOException {
        double[] s = span(load(MODELS.resolve("vcc_mk4_aerial_cockpit/body.json")), name, 0.0,
                new double[]{Double.MAX_VALUE, -Double.MAX_VALUE});
        assertTrue(s[0] < s[1], "the 1x1's body has no " + name);
        return (s[0] + s[1]) / 2.0;
    }

    @Test
    void theRendererMovesTheSharedPartsWhereTheDecksBodyHasThem() throws IOException {
        String r = Files.readString(RENDERER, StandardCharsets.UTF_8);
        assertEquals(centreX("quadrant") - centreX1x1("quadrant"), renderer(r, "DECK_THROTTLES"), 1.0e-4,
                "the throttle levers stand on the deck's own quadrant");
        assertEquals(centreX("compass_foot") - centreX1x1("compass_foot"), renderer(r, "DECK_CENTRE"), 1.0e-4,
                "the compass card lies on the deck's own bowl, and the annunciators under it");

        List<Double> heads = new ArrayList<>();
        for (String half : new String[]{"body_left", "body_right"}) {
            for (JsonElement je : load(MODELS.resolve(DECK + "/" + half + ".json")).getAsJsonArray("elements")) {
                JsonObject e = je.getAsJsonObject();
                if (e.has("name") && e.get("name").getAsString().equals("column_head")) {
                    double dx = half.equals("body_right") ? 16.0 : 0.0;
                    heads.add((arr(e.getAsJsonArray("from"))[0] + arr(e.getAsJsonArray("to"))[0]) / 2.0 + dx);
                }
            }
        }
        heads.sort(Double::compare);
        assertEquals(2, heads.size(), "two yokes' columns");
        assertEquals(heads.get(1) - heads.get(0), renderer(r, "DECK_FIRST_OFFICER"), 1.0e-4, "the first officer's set");
        assertEquals(heads.get(0), renderer(r, "YOKE_X"), 1.0e-4, "the captain's at the 1x1's own place");
        assertEquals(31.5, renderer(r, "DECK_PANEL_X1"), 1.0e-9, "the deck's lit marks span its panels' art");
    }

    @Test
    void theTrimWheelTurnsAboutItsOwnHubAndStaysInsideItsBlock() throws IOException {
        String r = Files.readString(RENDERER, StandardCharsets.UTF_8);
        JsonObject wheel = load(MODELS.resolve(DECK + "/trim_wheel.json"));
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        double[] hub = null;
        for (JsonElement je : wheel.getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            double[] f = arr(e.getAsJsonArray("from"));
            double[] t = arr(e.getAsJsonArray("to"));
            if (e.get("name").getAsString().equals("hub")) {
                hub = new double[]{(f[0] + t[0]) / 2.0, (f[1] + t[1]) / 2.0, (f[2] + t[2]) / 2.0};
            }
            if (e.has("rotation")) {
                assertEquals("x", e.getAsJsonObject("rotation").get("axis").getAsString(), "its axle runs along x");
            }
            if (!e.get("name").getAsString().startsWith("rim")) continue;
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], f[k]);
                hi[k] = Math.max(hi[k], t[k]);
            }
        }
        assertTrue(hub != null, "the wheel has a hub");
        assertEquals(renderer(r, "TRIM_X"), hub[0], 1.0e-4);
        assertEquals(renderer(r, "TRIM_Y"), hub[1], 1.0e-4);
        assertEquals(renderer(r, "TRIM_Z"), hub[2], 1.0e-4);

        double reach = 0.0;
        for (JsonElement je : wheel.getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            if (!e.get("name").getAsString().startsWith("rim")) continue;
            double[] f = arr(e.getAsJsonArray("from"));
            double[] t = arr(e.getAsJsonArray("to"));
            for (double y : new double[]{f[1], t[1]}) {
                for (double z : new double[]{f[2], t[2]}) reach = Math.max(reach, Math.hypot(y - hub[1], z - hub[2]));
            }
        }
        assertTrue(hub[2] + reach <= 16.0 + 1.0e-6, "the rim at its farthest stays out of the seat behind: " + (hub[2] + reach));
        assertTrue(hi[0] <= centreX("quadrant") - 1.75 + 1.0e-6, "the rim stands beside the quadrant, not in it");
    }

    @Test
    void bothHalvesKeepTwoTexelsAUnitAcrossTheirCut() throws IOException {
        BufferedImage sheet = ImageIO.read(TEXTURES.resolve(DECK + "/body.png").toFile());
        double perU = sheet.getWidth() / 16.0;
        double perV = sheet.getHeight() / 16.0;
        List<String> problems = new ArrayList<>();
        for (String half : new String[]{"body_left", "body_right"}) {
            for (JsonElement je : load(MODELS.resolve(DECK + "/" + half + ".json")).getAsJsonArray("elements")) {
                JsonObject e = je.getAsJsonObject();
                double[] f = arr(e.getAsJsonArray("from"));
                double[] t = arr(e.getAsJsonArray("to"));
                for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("faces").entrySet()) {
                    double[] uv = arr(fe.getValue().getAsJsonObject().getAsJsonArray("uv"));
                    double du = Math.abs(uv[2] - uv[0]) * perU;
                    double dv = Math.abs(uv[3] - uv[1]) * perV;
                    if (du < 1.0e-6 && dv < 1.0e-6) continue;
                    double[] size = switch (fe.getKey()) {
                        case "up", "down" -> new double[]{t[0] - f[0], t[2] - f[2]};
                        case "north", "south" -> new double[]{t[0] - f[0], t[1] - f[1]};
                        default -> new double[]{t[2] - f[2], t[1] - f[1]};
                    };
                    if (!dense(du, size[0]) || !dense(dv, size[1])) {
                        problems.add(String.format("%s %s.%s: %.3f x %.3f texels on %.3f x %.3f units", half,
                                e.has("name") ? e.get("name").getAsString() : "?", fe.getKey(), du, dv, size[0], size[1]));
                    }
                    for (int k = 0; k < 3; k++) {
                        if (f[k] < -1.0e-6 || t[k] > 16.0 + 1.0e-6) problems.add(half + " reaches out of its block: " + e);
                    }
                }
                if (e.has("rotation")) {
                    double a = e.getAsJsonObject("rotation").get("angle").getAsDouble();
                    boolean ok = false;
                    for (double allowed : ANGLES) ok |= Math.abs(a - allowed) < 1.0e-9;
                    if (!ok) problems.add(half + ": a turn of " + a);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static boolean dense(double texels, double units) {
        double want = 2.0 * units;
        if (want < 1.0 - 1.0e-6) return Math.abs(texels - 1.0) < 1.0e-3;
        return Math.abs(texels - want) < 2.0e-3;
    }

    @Test
    void theItemShowsBothPilotsControlsAtRestAndEveryTextureItNamesExists() throws IOException {
        JsonObject item = load(MODELS.resolve(DECK + "/item.json"));
        assertEquals("minecraft:block/block", item.get("parent").getAsString());
        double[] gui = arr(item.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("rotation"));
        assertEquals(315.0, gui[1], 1.0e-9, "the pilots' side, where the instruments face, toward the slot");
        TreeSet<String> names = new TreeSet<>();
        int yokes = 0;
        for (JsonElement je : item.getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            if (!e.has("name")) continue;
            String n = e.get("name").getAsString();
            names.add(n);
            if (n.equals("boss")) yokes++;
        }
        assertEquals(2, yokes, "a yoke for each pilot");
        for (String part : new String[]{"bar", "lever", "knob", "card", "earth", "shaft", "rim0", "panel"}) {
            assertTrue(names.contains(part), "the item shows the " + part);
        }
        for (Map.Entry<String, JsonElement> t : item.getAsJsonObject("textures").entrySet()) {
            String ref = t.getValue().getAsString();
            assertTrue(ref.startsWith("create_stats:block/"), ref);
            assertTrue(Files.exists(TEXTURES.resolve(ref.substring("create_stats:block/".length()) + ".png")), ref);
        }
        for (String lume : new String[]{"lume_upper.png", "lume_lower.png"}) {
            assertTrue(Files.exists(TEXTURES.resolve(DECK + "/" + lume)), "the deck's own lit marks: " + lume);
        }
    }
}
