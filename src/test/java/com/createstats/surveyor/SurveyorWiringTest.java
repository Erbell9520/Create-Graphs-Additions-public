package com.createstats.surveyor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyorWiringTest {
    private static final Path SRC = Path.of("src/main/java/com/createstats");

    private static String read(String rel) throws IOException {
        return Files.readString(SRC.resolve(rel), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static String statement(String s, int at) {
        if (at < 0) return "";
        int end = s.indexOf(';', at);
        return end < 0 ? "" : s.substring(at, end + 1);
    }

    static String body(String s, int at) {
        if (at < 0) return "";
        int open = s.indexOf('{', at);
        if (open < 0) return "";
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return s.substring(open, i + 1);
        }
        return "";
    }

    @Test
    void everyBridgeCallHasItsHook() throws IOException {
        Pattern call = Pattern.compile("SurveyorClientBridge\\.call\\(\"(\\w+)\",\\s*([\\w.]+)\\.class");
        TreeSet<String> calls = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    String type = m.group(2);
                    calls.add(m.group(1) + ":" + type.substring(type.lastIndexOf('.') + 1));
                }
            }
        }
        assertTrue(calls.size() >= 5, "the staff, the item and the three S2C packets call through the bridge: " + calls);
        String hooks = read("surveyor/client/SurveyorClientHooks.java");
        for (String c : calls) {
            String name = c.substring(0, c.indexOf(':'));
            String type = c.substring(c.indexOf(':') + 1);
            assertTrue(Pattern.compile("public static void " + name + "\\(" + type + " \\w+\\)").matcher(hooks).find(),
                    "SurveyorClientHooks has no public static void " + name + "(" + type + "): that bridge call does nothing");
        }
    }

    @Test
    void everyPathwayPacketIsRegisteredOnceTheWayItTravels() throws IOException {
        String packets = read("network/ModPackets.java");
        Map<String, String> ways = new LinkedHashMap<>();
        ways.put("SurveyorEditPacket", "playToServer");
        ways.put("SurveyorViewPacket", "playToServer");
        ways.put("RouteActionPacket", "playToServer");
        ways.put("PathwaySyncPacket", "playToClient");
        ways.put("RouteTrackPacket", "playToClient");
        ways.put("RoutePickedPacket", "playToClient");
        ways.put("PathwayHealthPacket", "playToClient");
        ways.put("PathwayTerrainPacket", "playToClient");
        ways.put("EditHistoryPacket", "playToClient");
        for (Map.Entry<String, String> e : ways.entrySet()) {
            String name = e.getKey();
            String type = "com.createstats.surveyor.network." + name + ".TYPE";
            assertEquals(1, packets.split(Pattern.quote(type), -1).length - 1, name + " is registered exactly once");
            String reg = statement(packets, packets.indexOf("registrar." + e.getValue() + "(" + type));
            assertTrue(reg.contains(name + ".STREAM_CODEC") && reg.contains(name + "::handle"),
                    name + " is registered with " + e.getValue() + ", its codec and its handler: " + reg);
        }
    }

    @Test
    void theStaffIsRegisteredAndInTheCatalogue() throws IOException {
        String items = read("registry/ModItems.java");
        String reg = statement(items, items.indexOf("SURVEYOR_STAFF = CreateStats.registrate().item(\"surveyor_staff\""));
        assertTrue(reg.contains("SurveyorStaffItem::new") && reg.contains("stacksTo(1)") && reg.contains(".register()"),
                "ModItems registers the staff, one to a stack: " + reg);
        assertTrue(read("registry/ModCreativeTab.java").contains("ModItems.SURVEYOR_STAFF::get"), "the staff has a place in the tab");
    }

    @Test
    void theHudPanelIsALayerBelowChatThatLeavesDepthAlone() throws IOException {
        String hud = read("surveyor/client/StaffHudPanel.java");
        String reg = statement(hud, hud.indexOf("event.registerBelow(VanillaGuiLayers.CHAT"));
        assertTrue(reg.contains("StaffHudPanel::render"), "the panel is registered as a layer below chat: " + reg);
        String render = body(hud, hud.indexOf("public static void render(GuiGraphics"));
        int off = render.indexOf("RenderSystem.depthMask(false)");
        int draw = render.indexOf("draw(g,");
        int finallyAt = render.indexOf("finally");
        int on = render.indexOf("RenderSystem.depthMask(true)");
        assertTrue(off >= 0 && draw > off && finallyAt > draw && on > finallyAt,
                "the panel draws with depth writes off and turns them back on in a finally: " + render);
    }

    @Test
    void leftClickWithTheStaffNeverMines() throws IOException {
        String events = read("surveyor/SurveyorEvents.java");
        String guard = body(events, events.indexOf("public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock"));
        assertTrue(guard.contains("instanceof SurveyorStaffItem") && guard.contains("setCanceled(true)"),
                "the server cancels a left-click on a block with the staff in hand: " + guard);
    }

    static String call(String s, int at) {
        if (at < 0) return "";
        int open = s.indexOf('(', at);
        if (open < 0) return "";
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')' && --depth == 0) return s.substring(at, i + 1);
        }
        return "";
    }

    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theStaffIsDrawnInTheHandAndHeard() throws IOException {
        JsonObject item = json(ASSETS.resolve("models/item/surveyor_staff.json"));
        assertEquals("neoforge:separate_transforms", item.get("loader").getAsString(), "hands and slots are drawn apart");
        assertEquals("minecraft:builtin/entity", item.getAsJsonObject("base").get("parent").getAsString(),
                "the hands go to the renderer");
        for (String view : new String[]{"gui", "ground", "fixed"}) {
            JsonObject icon = item.getAsJsonObject("perspectives").getAsJsonObject(view);
            assertTrue(icon != null && icon.getAsJsonObject("textures").get("layer0").getAsString().equals("create_stats:item/surveyor_staff"),
                    view + " shows the flat icon");
        }
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/item/surveyor_staff.png")), "the icon's texture is there");

        String setup = read("surveyor/client/StaffClientSetup.java");
        String reg = call(setup, setup.indexOf("event.registerItem("));
        assertTrue(reg.contains("SurveyorStaffRenderer.get()") && reg.contains("ModItems.SURVEYOR_STAFF.get()"),
                "the renderer is registered for the staff: " + reg);
        String parts = body(setup, setup.indexOf("public static void parts(ModelEvent.RegisterAdditional"));
        assertTrue(parts.contains("SurveyorStaffRenderer.PARTS") && parts.contains("ModelResourceLocation.standalone("),
                "every part model is registered to be baked: " + parts);

        String renderer = read("surveyor/client/SurveyorStaffRenderer.java");
        assertTrue(renderer.contains("\"item/surveyor_staff/jacob/\" + name"), "the parts are the Jacob staff's");
        Matcher m = Pattern.compile("public static final ResourceLocation (\\w+) = part\\(\"(\\w+)\"\\);").matcher(renderer);
        String array = statement(renderer, renderer.indexOf("public static final ResourceLocation[] PARTS"));
        String lifted = statement(renderer, renderer.indexOf("static final Set<ResourceLocation> LIFTED"));
        int count = 0;
        while (m.find()) {
            count++;
            String field = m.group(1);
            String name = m.group(2);
            assertTrue(Pattern.compile("\\b" + field + "\\b").matcher(array).find(), field + " is in PARTS: " + array);
            Path file = ASSETS.resolve("models/item/surveyor_staff/jacob/" + name + ".json");
            assertTrue(Files.isRegularFile(file), "the " + name + " model is on disk");
            JsonObject model = json(file);

            for (var el : model.getAsJsonArray("elements")) {
                for (String end : new String[]{"from", "to"}) {
                    for (var v : el.getAsJsonObject().getAsJsonArray(end)) {
                        double c = v.getAsDouble();
                        assertTrue(c >= -16.0 && c <= 32.0, name + "'s " + end + " " + c + " is outside the loader's -16..32");
                    }
                }
            }

            String credit = model.has("credit") ? model.get("credit").getAsString() : "";
            if (credit.startsWith("gen_staff_redesign.py")) {
                boolean low = credit.contains("units low");
                boolean lifts = Pattern.compile("\\b" + field + "\\b").matcher(lifted).find();
                assertEquals(low, lifts, name + (low ? " is written low but not lifted: " : " is lifted but not written low: ") + lifted);
            }
            if (name.startsWith("glass")) {
                for (var el : model.getAsJsonArray("elements")) {
                    for (var face : el.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                        assertTrue(face.getValue().getAsJsonObject().has("tintindex"),
                                "a glass face takes its mode's colour: " + name + " " + face.getKey());
                    }
                }
            }
        }
        assertEquals(16, count, "the staff, the compass and its needle, bubble, vanes, wheel, four glasses and detent, the reel, "
                + "its crank, line and hook");

        String sounds = read("registry/ModSounds.java");
        JsonObject soundsJson = json(ASSETS.resolve("sounds.json"));
        JsonObject lang = json(ASSETS.resolve("lang/en_us.json"));
        String staffInput = read("surveyor/client/StaffInput.java");
        String route = read("vccmk4/route/Vcc4Route.java");
        Matcher s = Pattern.compile("public static final DeferredHolder<SoundEvent, SoundEvent> (SURVEYOR_\\w+) =\\s*SOUNDS\\.register\\(\"(\\w+)\"")
                .matcher(sounds);
        int clips = 0;
        while (s.find()) {
            clips++;
            String name = s.group(2);
            JsonObject entry = soundsJson.getAsJsonObject(name);
            assertTrue(entry != null, name + " is in sounds.json");
            assertEquals("create_stats:" + name, entry.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
            assertTrue(lang.has(entry.get("subtitle").getAsString()), name + "'s subtitle has words");
            assertTrue(Files.isRegularFile(ASSETS.resolve("sounds/" + name + ".ogg")), name + ".ogg is there");
            assertTrue(staffInput.contains("ModSounds." + s.group(1) + ".get()") || route.contains("ModSounds." + s.group(1) + ".get()"),
                    s.group(1) + " is played somewhere");
        }
        assertEquals(8, clips, "six of the staff's and the recorder's two");
    }

    @Test
    void theScansStopWhereTheStatementStops() {
        String s = "a(x);\nb(y);";
        assertEquals("a(x);", statement(s, 0));
        assertEquals("{ c(); { d(); } }", body("void m() { c(); { d(); } } void n() { e(); }", 0));
        assertEquals(List.of(), List.of(statement(s, -1)).stream().filter(t -> !t.isEmpty()).toList());
        assertEquals("r(new X() { int f() { return g(1); } }, y)", call("r(new X() { int f() { return g(1); } }, y); z();", 0));
    }
}
