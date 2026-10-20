package com.createstats.vccmk4;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtaG1WiringTest {
    private static final char EM_DASH = (char) 0x2014;

    private static final String NAME = "VCC Mk IV Ground";

    private static final Path JAVA = Path.of("src/main/java/com/createstats");
    private static final Path BLOCKS = JAVA.resolve("registry/ModBlocks.java");
    private static final Path BLOCK_ENTITIES = JAVA.resolve("registry/ModBlockEntities.java");
    private static final Path TAB = JAVA.resolve("registry/ModCreativeTab.java");
    private static final Path MK4_BLOCK = JAVA.resolve("vccmk4/Vcc4Block.java");
    private static final Path BLOCK = JAVA.resolve("vccmk4/BtaG1Block.java");
    private static final Path BE = JAVA.resolve("vccmk4/BtaG1BE.java");
    private static final Path SHAPE = JAVA.resolve("vccmk4/core/ConsoleShape.java");
    private static final Path RENDERER = JAVA.resolve("renderer/BtaG1Renderer.java");
    private static final Path PARTIALS = JAVA.resolve("client/ModPartialModels.java");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path LOOT = Path.of("src/main/resources/data/create_stats/loot_table/blocks/bta_g1.json");
    private static final Path PICKAXE = Path.of("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json");

    private static final List<Path> NAMED_IN_CODE = List.of(JAVA.resolve("statsmk3/page/DriveArt.java"),
            JAVA.resolve("statsmk3/paint/DriveFacePainter.java"), JAVA.resolve("display/CustomScreenSourceFamilyCatalog.java"),
            JAVA.resolve("vccmk4/Vcc4Telemetry.java"));

    private static final List<Path> MINE = List.of(MK4_BLOCK, BLOCK, BE, SHAPE, RENDERER,
            JAVA.resolve("vccmk4/core/BtaG1State.java"), JAVA.resolve("vccmk4/core/BtaG1Motion.java"),
            Path.of("gen_bta_g1.py"), Path.of("render_bta_g1.py"), Path.of("gen_vcc_mk4.py"));

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String code(Path p) throws IOException {
        return read(p).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\n]*", "");
    }

    private static String statement(String source, String field) {
        int start = source.indexOf(field + " = CreateStats.registrate()");
        assertTrue(start >= 0, field + " is not registered");
        int end = source.indexOf(".register();", start);
        assertTrue(end > start, field + "'s registration has no .register()");
        return source.substring(start, end + ".register();".length());
    }

    private static String body(String source, String signature) {
        int at = source.indexOf(signature);
        assertTrue(at >= 0, "no method " + signature);
        int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("method " + signature + " never closes");
    }

    private static int count(String source, String needle) {
        int n = 0;
        for (int i = source.indexOf(needle); i >= 0; i = source.indexOf(needle, i + needle.length())) n++;
        return n;
    }

    @Test
    void theMkIvGroundKeepsItsIdAndBlockAndItsBlockEntityTypeBuildsAndDrawsTheNewBody() throws IOException {
        String blocks = code(BLOCKS);
        String block = statement(blocks, "VCC_MK4");
        assertTrue(block.contains(".block(\"vcc_mk4\", com.createstats.vccmk4.Vcc4Block::new)"),
                "the registry id and block class saved worlds know: " + block);
        assertTrue(block.contains(".addLayer(() -> RenderType::cutoutMipped)"), "the block model's own layer");
        assertTrue(block.contains(".simpleItem()"), "no item, nothing to place and nothing in the creative menu");
        assertEquals(1, count(blocks, ".block(\"vcc_mk4\""), "registered once");

        String entities = code(BLOCK_ENTITIES);
        String entity = statement(entities, "VCC_MK4");
        assertTrue(entity.contains(".blockEntity(\"vcc_mk4\", com.createstats.vccmk4.BtaG1BE::new)"),
                "the Mk IV's type must build the body's block entity, or its controls never move: " + entity);
        assertTrue(entity.contains(".validBlock(() -> ModBlocks.VCC_MK4.get())"), entity);
        assertTrue(entity.contains(".renderer(() -> com.createstats.renderer.BtaG1Renderer::new)"),
                "a block entity type with no renderer draws none of its moving parts and reports nothing wrong");
        assertFalse(entity.contains("BTA_G1"), "valid for the Mk IV Ground alone; the kept id has a type of its own");
        assertTrue(entities.contains("BlockEntityEntry<com.createstats.vccmk4.BtaG1BE> VCC_MK4;"),
                "declared as the type it builds, so a cast to the body's block entity can never meet a plain Vcc4BE");
    }

    @Test
    void theCandidatesIdIsKeptWithItsOwnTypeAndNoItem() throws IOException {
        String blocks = code(BLOCKS);
        String s = statement(blocks, "BTA_G1");
        assertTrue(s.contains(".block(\"bta_g1\", com.createstats.vccmk4.BtaG1Block::new)"),
                "an unregistered id loads as air: the owner's craft carries one: " + s);
        assertTrue(s.contains(".addLayer(() -> RenderType::cutoutMipped)"), "drawn exactly as the Mk IV Ground is");
        assertFalse(s.contains("simpleItem()") || s.contains(".item("),
                "no item: nothing places a new one, and it stays out of the creative menu and JEI");
        assertEquals(1, count(blocks, ".block(\"bta_g1\""), "registered once");

        String e = statement(code(BLOCK_ENTITIES), "BTA_G1");
        assertTrue(e.contains(".blockEntity(\"bta_g1\", com.createstats.vccmk4.BtaG1BE::new)"),
                "its own type: a console saved under it loads with its setup only if the id still exists: " + e);
        assertTrue(e.contains(".validBlock(() -> ModBlocks.BTA_G1.get())"), e);
        assertTrue(e.contains(".renderer(() -> com.createstats.renderer.BtaG1Renderer::new)"), "drawn exactly as the Mk IV Ground is");
    }

    @Test
    void theCreativeMenuListsTheGroundConsoleAndNeverTheKeptId() throws IOException {
        String tab = code(TAB);
        assertEquals(1, count(tab, "ModBlocks.VCC_MK4::asItem"), "the Mk IV Ground, once");
        assertEquals(0, count(tab, "ModBlocks.BTA_G1"), "the kept id has no item to list");
    }

    @Test
    void theMkIvGroundBuildsTheBodysBlockEntityAndTicksBothSidesOnOneType() throws IOException {
        String src = code(MK4_BLOCK);
        assertTrue(body(src, "BlockEntityType<? extends BtaG1BE> consoleType()").contains("return com.createstats.registry.ModBlockEntities.VCC_MK4.get();"));
        assertTrue(body(src, "BlockEntity newBlockEntity(").contains("new BtaG1BE(consoleType(), pos, state)"),
                "the block entity is built as the type the block answers, and the tickers key on the same answer");
        String ticker = body(src, "BlockEntityTicker<T> getTicker(");
        assertTrue(ticker.contains("if (type != consoleType()) return null;"), "keyed on its own type");
        assertTrue(ticker.contains("level.isClientSide"), "the two sides tick different things");

        assertTrue(ticker.contains("ConsoleFault.tick(be, ((BtaG1BE) be)::clientTick)"),
                "the client steps the controls' motion, through ConsoleFault");
        assertTrue(ticker.contains("ConsoleFault.tick(be, ((BtaG1BE) be)::tickServer)"),
                "and the server runs the Mk IV and publishes, through ConsoleFault");
        assertTrue(src.contains("builder.add(FACING);") && !src.contains("builder.add(FACING,"),
                "one property, the Mk IV's own, so every saved blockstate is still a valid one");
    }

    @Test
    void theHitboxTurnsWithTheFacing() throws IOException {
        String src = code(MK4_BLOCK);
        assertTrue(body(src, "VoxelShape getShape(").contains("return SHAPES.get(state.getValue(FACING));"),
                "one shape for every facing put the tower on the wrong side three times in four");
        String build = body(src, "Map<Direction, VoxelShape> buildShapes()");
        assertTrue(build.contains("{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}"),
                "quarter turns in the blockstate's order: north 0, east 90, south 180, west 270");
        assertTrue(build.contains("ConsoleShape.boxes(turns)"), "the boxes ConsoleShapeTest pins, turned by the tested rule");
    }

    @Test
    void theKeptIdIsTheMkIvGroundButForItsTypeAndItsDrop() throws IOException {
        String src = code(BLOCK);
        assertTrue(src.contains("public class BtaG1Block extends Vcc4Block"), "everything the Mk IV Ground does, inherited");
        assertTrue(src.contains("simpleCodec(BtaG1Block::new)"));
        assertTrue(body(src, "MapCodec<? extends HorizontalDirectionalBlock> codec()").contains("return CODEC;"));
        assertTrue(body(src, "BlockEntityType<? extends BtaG1BE> consoleType()").contains("return ModBlockEntities.BTA_G1.get();"),
                "a console saved under the candidate's type loads into it");
        assertTrue(body(src, "ItemStack getCloneItemStack(").contains("new ItemStack(ModBlocks.VCC_MK4.get())"),
                "a middle click gives a Mk IV Ground: the block has no item of its own");
        for (String own : new String[]{"newBlockEntity(", "getTicker(", "getShape(", "appendHoverText("}) {
            assertFalse(src.contains(own), own + " must stay the Mk IV Ground's, or the two ids drift apart");
        }
    }

    @Test
    void theBlockEntityRunsTheMkIvFirstThenPublishesAndNeverSavesTheAnimation() throws IOException {
        String src = code(BE);
        assertTrue(src.contains("public class BtaG1BE extends Vcc4BE"));
        String tick = body(src, "void tickServer()");
        int brain = tick.indexOf("serverTick();");
        int capture = tick.indexOf("capture()");
        int send = tick.indexOf("sendBlockUpdated");
        assertTrue(brain >= 0 && capture > brain && send > capture,
                "the Mk IV drives first, so what is published is what it just decided");
        assertTrue(tick.contains("now.equals(sent)"), "an unchanged state sends nothing");
        String captured = body(src, "BtaG1State capture()");
        assertTrue(captured.contains(".with(BtaG1State.GREETING, setup().greeting)"),
                "the sweep follows the Mk IV's own Greet the driver switch");
        assertTrue(captured.contains(".with(BtaG1State.SEATED, seatedDriver())"), "and only a seated driver gets it");
        assertFalse(src.contains("saveAdditional"),
                "it saves exactly what a Mk IV saves, so a console saved before the promotion loads into it unchanged");
        assertTrue(src.contains("static final String ANIM"));
        assertFalse(src.contains("addToGoggleTooltip"), "the goggle header is the Mk IV's own: the block's name");
    }

    @Test
    void theAnimationRidesTheOrdinaryBlockEntitySyncAndNeverReplacesAWholeUpdate() throws IOException {
        String src = code(BE);
        String changed = body(src, "void setChanged()");
        assertTrue(changed.contains("fullDirty = true;") && changed.contains("super.setChanged();"),
                "the Mk IV's own sync must make the next update whole, or goggle text could be replaced by seven bytes");
        String packet = body(src, "Packet<ClientGamePacketListener> getUpdatePacket()");
        assertTrue(packet.contains("animOnly && !fullDirty"));
        assertTrue(packet.contains("ClientboundBlockEntityDataPacket.create(this, (be, registries) -> animationTag())"));
        assertTrue(packet.contains("return super.getUpdatePacket();"), "otherwise the Mk IV's own full update");
        assertTrue(body(src, "CompoundTag getUpdateTag(").contains("putByteArray(ANIM"),
                "a client that joins mid-drive must start in the right pose");
        assertTrue(body(src, "void onDataPacket(").contains("tag.size() == 1"), "only a tag that is nothing but the animation is one");
        assertTrue(body(src, "void loadAdditional(").contains("BtaG1State.decode"));
    }

    @Test
    void theRendererDrawsTheGlassLastAndTranslucentAndTheLampsFullBrightAndUnshaded() throws IOException {
        String src = code(RENDERER);
        assertEquals(1, count(src, "RenderType.translucent()"), "one part is glass");
        assertTrue(count(src, "RenderType.cutoutMipped()") == 1, "everything else on the block's own layer, obtained once");
        int lamps = src.indexOf("for (Lamp lamp : Lamp.values())");
        int glass = src.indexOf("RenderType.translucent()");
        assertTrue(lamps > 0 && glass > lamps, "the glass goes on last, over the needle and the lit ticks");
        int haloPass = src.indexOf("halos(f, ms, buffer);");
        assertTrue(haloPass > lamps && haloPass < glass, "the halos go after the lit lamps and before the glass");
        String halos = body(src, "void halos(");
        assertTrue(halos.contains("RenderType.entityTranslucentEmissive(HALO_CARD)"), "the mod's own soft glow layer");
        assertTrue(halos.contains("BtaG1Motion.haloAlpha("), "the opacity is the tested curve, not a number typed here");
        assertTrue(src.contains("textures/block/lamp_halo.png"), "and the mod's shared soft card");
        assertTrue(body(src, "void corner(").contains("LightTexture.FULL_BRIGHT"), "a halo is full bright, or it dies in a dark room");
        String glow = body(src, "void glow(");
        assertTrue(glow.contains("LightTexture.FULL_BRIGHT") && glow.contains(".disableDiffuse()"),
                "a lamp is one flat colour at full brightness, whatever the room");
        assertTrue(body(src, "boolean shouldRenderOffScreen(").contains("return false;"),
                "drawn off screen it would put every console into the unculled list");
        assertTrue(src.contains("Axis.YP.rotationDegrees(-degrees)"), "a blockstate y of A is a rotation of minus A about +Y");
    }

    @Test
    void everyPartialModelIsDeclaredAndDrawn() throws IOException {
        String partials = code(PARTIALS);
        Matcher m = Pattern.compile("BTA_G1_([A-Z_]+) = block\\(\"bta_g1/([a-z_]+)\"\\)").matcher(partials);
        int n = 0;
        String renderer = code(RENDERER);
        while (m.find()) {
            n++;
            assertTrue(renderer.contains("BTA_G1_" + m.group(1)), "the renderer never draws " + m.group(1));
        }
        assertEquals(15, n);
    }

    @Test
    void thePlayerReadsOneNameEverywhere() throws IOException {
        JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
        for (String key : new String[]{"block.create_stats.vcc_mk4", "block.create_stats.bta_g1", "gui.create_stats.vcc_mk4.title",
                "gui.create_stats.vcc_mk4.title_short", "create_stats.display_source.vcc_ground"}) {
            assertTrue(lang.has(key), key);
            assertEquals(NAME, lang.get(key).getAsString(), key);
        }
        assertEquals("VCC Mk IV Aerial", lang.get("block.create_stats.vcc_mk4_aerial").getAsString(), "the Aerial keeps its name");
        assertTrue(lang.has("block.create_stats.vcc_mk4.tooltip") && !lang.get("block.create_stats.vcc_mk4.tooltip").getAsString().isBlank());
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : lang.entrySet()) {
            String v = e.getValue().getAsString();
            if (v.contains("Simplified Ground Vehicle") || v.contains("VCC Ground Mk IV") || v.contains("BTA_G1")) stale.add(e.getKey());
        }
        assertTrue(stale.isEmpty(), "lang rows still naming the console by an old name: " + stale);
        for (Path p : NAMED_IN_CODE) {
            assertFalse(code(p).contains("\"VCC Ground Mk IV") || code(p).contains("Display Link a VCC Ground"),
                    p + " still shows the old name");
        }
    }

    @Test
    void theKeptIdDropsAMkIvGroundAndBothMineWithAPickaxe() throws IOException {
        String loot = read(LOOT);
        assertTrue(loot.contains("\"create_stats:vcc_mk4\""), "broken, it gives a Mk IV Ground");
        assertFalse(loot.contains("\"create_stats:bta_g1\""), "an item that no longer exists: the table fails to load and drops nothing");
        String tag = read(PICKAXE);
        assertTrue(tag.contains("\"create_stats:vcc_mk4\"") && tag.contains("\"create_stats:bta_g1\""),
                "a metal desk; the candidate was in the tag and its body now is the Mk IV Ground");
    }
}
