package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverHelmetWiringTest {
    private static final Path JAVA = Path.of("src/main/java/com/createstats");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path DATA = Path.of("src/main/resources/data/create_stats");

    private static final String[] SOUNDS = {
        "driver_helmet_visor_open", "driver_helmet_visor_close",
        "driver_helmet_night_vision_on", "driver_helmet_night_vision_off", "driver_helmet_theme",
    };

    @Test
    void everyWiringSiteIsPresent() throws IOException {
        final List<String> problems = new ArrayList<>();

        final String clientSetup = read(JAVA.resolve("driverhelmet/client/DriverHelmetClientSetup.java"));
        final String itemModel = read(ASSETS.resolve("models/item/driver_helmet.json"));
        if (itemModel.contains("\"builtin/entity\"")) {
            final String reg = statement(clientSetup, "event.registerItem(");
            if (!reg.contains("DriverHelmetItemRenderer") || !reg.contains("ModBlocks.DRIVER_HELMET.asItem()")) {
                problems.add("the item model is builtin/entity but DriverHelmetItemRenderer is not registered for the helmet item");
            }
        }
        for (String key : new String[]{"VISOR", "NIGHT_VISION", "THEME"}) {
            if (!clientSetup.contains("event.register(DriverHelmetKeys." + key + ")")) {
                problems.add("key mapping " + key + " is not registered");
            }
        }
        if (!statement(clientSetup, "registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS").contains("DriverHelmetHud::renderVisor")) {
            problems.add("the visor layer is not registered: no tint, frame or lip in first person");
        }
        if (!statement(clientSetup, "registerAbove(VanillaGuiLayers.HOTBAR").contains("DriverHelmetHud::renderHud")) {
            problems.add("the HUD layer is not registered");
        }
        if (!Files.isRegularFile(ASSETS.resolve("textures/gui/driver_helmet_visor.png"))) {
            problems.add("textures/gui/driver_helmet_visor.png is missing: the visor frame would draw magenta");
        }

        final String hud = read(JAVA.resolve("driverhelmet/client/DriverHelmetHud.java"));
        for (String layer : new String[]{"renderVisor", "renderHud"}) {
            final String body = body(hud, "public static void " + layer + "(");
            if (!body.contains("RenderSystem.depthMask(false)")) {
                problems.add("DriverHelmetHud." + layer + " writes depth: Jade's tooltip is hidden under it");
            }
            if (!body.contains("RenderSystem.depthMask(true)")) {
                problems.add("DriverHelmetHud." + layer + " leaves depth writes off for every layer after it");
            }
        }

        if (!statement(hud, "VisorModes.hudInView(").contains("isPassenger()")
                || !body(hud, "public static void renderHud(").contains("hudHelmet()")) {
            problems.add("the HUD is not gated by VisorModes.hudInView with the rider test: F5 in a vehicle drops it");
        }
        if (!body(hud, "public static void renderVisor(").contains("VisorModes.glassInView(")) {
            problems.add("the visor layer is not gated by VisorModes.glassInView: the glass would draw in third person");
        }

        final String mk4 = read(JAVA.resolve("vccmk4/Vcc4BE.java"));
        final int at = mk4.indexOf("private String statusLine(");
        final String line = at < 0 ? "" : mk4.substring(at, Math.max(at, mk4.indexOf("return line.toString();", at)));
        for (String token : new String[]{"\"VCC  \"", "\"  %.1f b/s\"", "\"  drive \"", "\"  steer \"",
                "\"  BOOST\"", "\"  | \""}) {
            if (!line.contains(token)) {
                problems.add("Vcc4BE.statusLine no longer writes " + token + ": DashLine must be updated with it");
            }
        }

        final int tick = mk4.indexOf("public void serverTick(");
        final int tickEnd = tick < 0 ? -1 : mk4.indexOf("\n    }\n", tick);
        final String serverTick = tick < 0 ? "" : mk4.substring(tick, Math.max(tick, tickEnd));
        if (!statement(serverTick, "DriverTelemetry.tick(").contains("scan::governorFor")) {
            problems.add("Vcc4BE.serverTick no longer calls DriverTelemetry.tick with its wheels: no wheel data on the HUD");
        }
        if (!mk4.contains("\"%+d%%\"")) {
            problems.add("Vcc4BE.pct no longer formats as %+d%%: DashLine must be updated with it");
        }

        final String partials = read(JAVA.resolve("client/ModPartialModels.java"));
        if (!statement(partials, "DRIVER_HELMET_VISOR =").contains("block(\"driver_helmet_visor\")")) {
            problems.add("the visor partial is not declared in ModPartialModels");
        }
        if (!Files.isRegularFile(ASSETS.resolve("models/block/driver_helmet_visor.json"))) {
            problems.add("models/block/driver_helmet_visor.json is missing");
        }

        final String setup = read(JAVA.resolve("driverhelmet/DriverHelmetSetup.java"));
        if (!statement(setup, "addIsWearingPredicate(").contains("DriverHelmets::gogglesUp")) {
            problems.add("the goggle predicate is not registered");
        }
        if (!statement(setup, "playToServer(").contains("DriverHelmetPacket.TYPE")) {
            problems.add("DriverHelmetPacket is not registered");
        }
        if (!statement(setup, "playToServer(").contains("playToClient(DriverTelemetryPacket.TYPE")) {
            problems.add("DriverTelemetryPacket is not registered: the HUD's wheel and g figures would never arrive");
        }

        final String registrar = statement(setup, "playToServer(");
        if (!registrar.contains("playToServer(HelmetGlowPacket.TYPE") || !registrar.contains("playToClient(HelmetGlowSync.TYPE")) {
            problems.add("the Punk glow's packets are not both registered: nobody else would see the glow move");
        }
        if (!Files.isRegularFile(ASSETS.resolve("textures/entity/driver_helmet_glow.png"))) {
            problems.add("textures/entity/driver_helmet_glow.png is missing: the glow would draw magenta");
        }
        final String itemRenderer = read(JAVA.resolve("driverhelmet/client/DriverHelmetItemRenderer.java"));
        if (!body(itemRenderer, "public void renderByItem(").contains("glow(ps, buffers")) {
            problems.add("DriverHelmetItemRenderer never draws the Punk glow");
        }
        for (String subscriber : new String[]{"HudSession", "TopClearance"}) {
            if (!read(JAVA.resolve("driverhelmet/client/" + subscriber + ".java"))
                    .contains("@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)")) {
                problems.add(subscriber + " is not a client event subscriber: its ticks and events never arrive");
            }
        }

        final String block = statement(read(JAVA.resolve("registry/ModBlocks.java")), "block(\"driver_helmet\"");
        if (!block.contains(".item(com.createstats.driverhelmet.DriverHelmetItem::new)") || block.contains("simpleItem()")) {
            problems.add("the helmet block is not registered with its own DriverHelmetItem");
        }
        final String be = statement(read(JAVA.resolve("registry/ModBlockEntities.java")), "blockEntity(\"driver_helmet\"");
        if (!be.contains(".renderer(() -> com.createstats.driverhelmet.client.DriverHelmetRenderer::new)")) {
            problems.add("the helmet block entity has no renderer: a placed helmet would have no visor");
        }

        if (!read(ASSETS.resolve("blockstates/driver_helmet.json")).contains("facing=west")) {
            problems.add("blockstates/driver_helmet.json is missing or has no facings");
        }
        if (!Files.isRegularFile(DATA.resolve("loot_table/blocks/driver_helmet.json"))) {
            problems.add("the helmet has no loot table: breaking it would drop nothing");
        }

        final String soundsJson = read(ASSETS.resolve("sounds.json"));
        final String modSounds = read(JAVA.resolve("registry/ModSounds.java"));
        final String lang = read(ASSETS.resolve("lang/en_us.json"));
        for (String name : SOUNDS) {
            if (!modSounds.contains("SOUNDS.register(\"" + name + "\"")) {
                problems.add("ModSounds does not register " + name);
            }
            if (!soundsJson.contains("\"create_stats:" + name + "\"")) {
                problems.add("sounds.json has no entry for " + name);
            }
            if (!Files.isRegularFile(ASSETS.resolve("sounds/" + name + ".ogg"))) {
                problems.add(name + ".ogg is missing");
            }
            if (!lang.contains("\"subtitles.create_stats." + name + "\":")) {
                problems.add("no subtitle row for " + name);
            }
        }
        for (HudTheme theme : HudTheme.values()) {
            if (!lang.contains("\"hud.create_stats.driver_helmet.theme." + theme.id + "\":")) {
                problems.add("no lang row naming the " + theme.id + " theme");
            }
        }

        final String blockstate = read(ASSETS.resolve("blockstates/driver_helmet.json"));
        final String partialList = statement(partials, "DRIVER_HELMET_VISOR_LIVERIES =");
        for (HudTheme theme : HudTheme.values()) {
            final boolean first = theme.ordinal() == 0;
            final String tex = first ? "driver_helmet" : "driver_helmet_" + theme.id;
            if (!Files.isRegularFile(ASSETS.resolve("textures/block/" + tex + ".png"))) {
                problems.add("the " + theme.id + " livery has no texture " + tex + ".png");
            }
            final String shell = first ? "driver_helmet" : "driver_helmet_" + theme.id;
            final String visorModel = first ? "driver_helmet_visor" : "driver_helmet_visor_" + theme.id;
            for (String model : new String[]{shell, visorModel}) {
                final String json = read(ASSETS.resolve("models/block/" + model + ".json"));
                if (!first && !json.contains("\"create_stats:block/" + tex + "\"")) {
                    problems.add("models/block/" + model + ".json is missing or not painted " + tex);
                }
            }
            for (String facing : new String[]{"north", "east", "south", "west"}) {
                final String key = "\"facing=" + facing + ",livery=" + theme.ordinal() + "\"";
                final int at2 = blockstate.indexOf(key);
                final int end = at2 < 0 ? -1 : blockstate.indexOf('}', at2);
                if (at2 < 0 || !blockstate.substring(at2, end).contains("\"create_stats:block/" + shell + "\"")) {
                    problems.add("blockstate has no " + key + " drawing " + shell);
                }
            }

            if (!partialList.contains(first ? "DRIVER_HELMET_VISOR," : "block(\"" + visorModel + "\")")) {
                problems.add("DRIVER_HELMET_VISOR_LIVERIES has no visor for " + theme.id);
            }
        }
        final String loot = read(DATA.resolve("loot_table/blocks/driver_helmet.json"));
        if (!loot.contains("\"minecraft:copy_components\"") || !loot.contains("\"create_stats:helmet_visor\"")) {
            problems.add("breaking a placed helmet would drop it without its theme and odometer (no copy_components)");
        }
        for (String key : new String[]{HelmetKeyNames.VISOR, HelmetKeyNames.NIGHT_VISION, HelmetKeyNames.THEME,
                HelmetKeyNames.CATEGORY, "block.create_stats.driver_helmet"}) {
            if (!lang.contains("\"" + key + "\":")) {
                problems.add("en_us.json has no row for " + key);
            }
        }

        assertTrue(problems.isEmpty(), "Driver Helmet wiring:\n  " + String.join("\n  ", problems));
    }

    static String statement(String source, String anchor) {
        final int at = source.indexOf(anchor);
        if (at < 0) {
            return "";
        }
        int start = at;
        while (start > 0 && ";{}".indexOf(source.charAt(start - 1)) < 0) {
            start--;
        }
        int depth = 0;
        int end = at;
        for (; end < source.length(); end++) {
            final char c = source.charAt(end);
            if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (c == ';' && depth <= 0) {
                break;
            }
        }
        return source.substring(start, Math.min(source.length(), end + 1)).replaceAll("//[^\\n]*", "");
    }

    static String body(String source, String signature) {
        final int at = source.indexOf(signature);
        final int open = at < 0 ? -1 : source.indexOf('{', at);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        int end = open;
        for (; end < source.length(); end++) {
            final char c = source.charAt(end);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                break;
            }
        }
        return source.substring(open, Math.min(source.length(), end + 1)).replaceAll("//[^\\n]*", "");
    }

    private static String read(Path path) throws IOException {
        return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
    }
}
