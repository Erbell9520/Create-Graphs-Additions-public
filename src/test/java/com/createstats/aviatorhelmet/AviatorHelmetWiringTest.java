package com.createstats.aviatorhelmet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AviatorHelmetWiringTest {
    private static final Path JAVA = Path.of("src/main/java/com/createstats");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path DATA = Path.of("src/main/resources/data/create_stats");

    private static final String[] SOUNDS = {"aviator_goggles_down", "aviator_goggles_up", "aviator_helmet_tone"};

    @Test
    void everyWiringSiteIsPresent() throws IOException {
        final List<String> problems = new ArrayList<>();

        final String clientSetup = read(JAVA.resolve("aviatorhelmet/client/AviatorHelmetClientSetup.java"));
        if (read(ASSETS.resolve("models/item/aviator_helmet.json")).contains("\"builtin/entity\"")) {
            final String reg = statement(clientSetup, "event.registerItem(");
            if (!reg.contains("AviatorHelmetItemRenderer") || !reg.contains("ModBlocks.AVIATOR_HELMET.asItem()")) {
                problems.add("the item model is builtin/entity but AviatorHelmetItemRenderer is not registered for the helmet");
            }
        } else {
            problems.add("models/item/aviator_helmet.json is missing or not builtin/entity");
        }
        for (String key : new String[]{"GOGGLES", "TONE"}) {
            if (!clientSetup.contains("event.register(AviatorHelmetKeys." + key + ")")) {
                problems.add("key mapping " + key + " is not registered");
            }
        }
        if (!statement(clientSetup, "registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS").contains("AviatorHud::renderLenses")) {
            problems.add("the lenses layer is not registered: no tint and no rims in first person");
        }
        if (!statement(clientSetup, "registerAbove(VanillaGuiLayers.HOTBAR").contains("AviatorHud::renderHud")) {
            problems.add("the sight layer is not registered: no HUD");
        }
        if (!Files.isRegularFile(ASSETS.resolve("textures/gui/aviator_helmet_lenses.png"))) {
            problems.add("textures/gui/aviator_helmet_lenses.png is missing: the lens field would draw magenta");
        }

        final String hud = read(JAVA.resolve("aviatorhelmet/client/AviatorHud.java"));
        for (String layer : new String[]{"renderLenses", "renderHud"}) {
            final String body = body(hud, "public static void " + layer + "(");
            if (!body.contains("RenderSystem.depthMask(false)")) {
                problems.add("AviatorHud." + layer + " writes depth: Jade's tooltip is hidden under it");
            }
            if (!body.contains("RenderSystem.depthMask(true)")) {
                problems.add("AviatorHud." + layer + " leaves depth writes off for every layer after it");
            }
        }
        if (!statement(body(hud, "public static void renderHud("), "AviatorModes.hudInView(").contains("isPassenger()")) {
            problems.add("the sight is not gated by AviatorModes.hudInView with the rider test: F5 in a ship drops it");
        }
        if (!body(hud, "public static void renderLenses(").contains("AviatorModes.lensesInView(")) {
            problems.add("the lenses layer is not gated by AviatorModes.lensesInView: the tint would draw in third person");
        }

        final String partials = read(JAVA.resolve("client/ModPartialModels.java"));
        for (String[] p : new String[][]{{"AVIATOR_HELMET_CAP =", "aviator_helmet"},
                {"AVIATOR_HELMET_GOGGLES =", "aviator_helmet_goggles"}, {"AVIATOR_HELMET_STRAP =", "aviator_helmet_strap"}}) {
            if (!statement(partials, p[0]).contains("block(\"" + p[1] + "\")")) {
                problems.add("the " + p[1] + " partial is not declared in ModPartialModels");
            }
            if (!Files.isRegularFile(ASSETS.resolve("models/block/" + p[1] + ".json"))) {
                problems.add("models/block/" + p[1] + ".json is missing");
            }
        }
        final String itemRenderer = body(read(JAVA.resolve("aviatorhelmet/client/AviatorHelmetItemRenderer.java")),
                "public void renderByItem(");
        for (String part : new String[]{"AVIATOR_HELMET_CAP", "AVIATOR_HELMET_GOGGLES", "AVIATOR_HELMET_STRAP"}) {
            if (!itemRenderer.contains("ModPartialModels." + part)) {
                problems.add("AviatorHelmetItemRenderer never draws " + part);
            }
        }
        final String renderer = body(read(JAVA.resolve("aviatorhelmet/client/AviatorHelmetRenderer.java")), "protected void renderSafe(");
        for (String part : new String[]{"AVIATOR_HELMET_GOGGLES", "AVIATOR_HELMET_STRAP"}) {
            if (!renderer.contains("ModPartialModels." + part)) {
                problems.add("AviatorHelmetRenderer never draws " + part + ": a placed helmet would have none");
            }
        }

        final String setup = read(JAVA.resolve("aviatorhelmet/AviatorHelmetSetup.java"));
        if (!statement(setup, "addIsWearingPredicate(").contains("AviatorHelmets::gogglesDown")) {
            problems.add("the goggle predicate is not registered: goggles down would not count as goggles");
        }
        final String registrar = statement(setup, "playToServer(");
        if (!registrar.contains("playToServer(AviatorHelmetPacket.TYPE")) {
            problems.add("AviatorHelmetPacket is not registered: V and H go nowhere");
        }
        if (!registrar.contains("playToClient(AerialHudPacket.TYPE")) {
            problems.add("AerialHudPacket is not registered: the sight would never have figures");
        }
        for (String subscriber : new String[]{"AviatorHatHider", "AviatorHelmetKeys"}) {
            if (!read(JAVA.resolve("aviatorhelmet/client/" + subscriber + ".java"))
                    .contains("@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)")) {
                problems.add(subscriber + " is not a client event subscriber: its events never arrive");
            }
        }

        final String block = statement(read(JAVA.resolve("registry/ModBlocks.java")), "block(\"aviator_helmet\"");
        if (!block.contains(".item(com.createstats.aviatorhelmet.AviatorHelmetItem::new)") || block.contains("simpleItem()")) {
            problems.add("the helmet block is not registered with its own AviatorHelmetItem");
        }
        final String be = statement(read(JAVA.resolve("registry/ModBlockEntities.java")), "blockEntity(\"aviator_helmet\"");
        if (!be.contains(".renderer(() -> com.createstats.aviatorhelmet.client.AviatorHelmetRenderer::new)")) {
            problems.add("the helmet block entity has no renderer: a placed helmet would have no goggles");
        }
        final String component = statement(read(JAVA.resolve("registry/ModDataComponents.java")), "register(\"aviator_goggles\"");
        if (!component.contains(".persistent(") || !component.contains(".networkSynchronized(")) {
            problems.add("the goggles component is not both saved and synced: nobody else would see them move");
        }
        if (!read(ASSETS.resolve("blockstates/aviator_helmet.json")).contains("facing=west")) {
            problems.add("blockstates/aviator_helmet.json is missing or has no facings");
        }
        final String loot = read(DATA.resolve("loot_table/blocks/aviator_helmet.json"));
        if (!loot.contains("\"minecraft:copy_components\"") || !loot.contains("\"create_stats:aviator_goggles\"")) {
            problems.add("breaking a placed helmet would drop nothing, or drop it without its goggles and tone");
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
        for (HudTone tone : HudTone.values()) {
            if (!lang.contains("\"hud.create_stats.aviator_helmet.tone." + tone.id + "\":")) {
                problems.add("no lang row naming the " + tone.id + " tone");
            }
        }
        for (String key : new String[]{AviatorKeyNames.GOGGLES, AviatorKeyNames.TONE, "block.create_stats.aviator_helmet",
                "hud.create_stats.aviator_helmet.tone", "item.create_stats.aviator_helmet.tooltip.goggles",
                "item.create_stats.aviator_helmet.tooltip.down", "item.create_stats.aviator_helmet.tooltip.tone"}) {
            if (!lang.contains("\"" + key + "\":")) {
                problems.add("en_us.json has no row for " + key);
            }
        }

        final String aerial = read(JAVA.resolve("vccmk4/air/AerialBE.java"));
        final String tick = body(aerial, "public void serverTick(");
        if (!statement(tick, "p.displayClientMessage(").contains("statusLine(r)")
                || !tick.contains("!AviatorHelmets.hudShowing(p)")) {
            problems.add("AerialBE's action-bar line is not stepped aside for AviatorHelmets.hudShowing");
        }
        if (!tick.contains("sendHud(server, craft, r)")) {
            problems.add("AerialBE.serverTick never sends the helmet its figures");
        }
        if (!body(aerial, "private void sendHud(").contains("PacketDistributor.sendToPlayer(p, packet)")) {
            problems.add("AerialBE.sendHud sends nothing");
        }
        if (aerial.contains("contains(\"aviator_helmet\")")) {
            problems.add("AerialBE still recognises the helmet by its item path");
        }
        if (!tick.contains("readTerrain(server, r)")) {
            problems.add("AerialBE.serverTick never reads the ground ahead: the terrain guard is blind");
        }
        final String pilot = read(JAVA.resolve("vccmk4/air/core/AerialPilot.java"));
        if (!statement(pilot, "AltitudeLaw.Plan plan = AltitudeLaw.plan(s.y(), s.vy(),").contains("s.vy(), flown,")) {
            problems.add("AerialPilot plans for the held height, not the height flown: the terrain guard's floor is never flown");
        }

        assertTrue(problems.isEmpty(), "Aviator Helmet wiring:\n  " + String.join("\n  ", problems));
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
