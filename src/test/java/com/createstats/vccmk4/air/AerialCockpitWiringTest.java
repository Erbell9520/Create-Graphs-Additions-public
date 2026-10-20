package com.createstats.vccmk4.air;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialCockpitWiringTest {
    private static final char EM_DASH = (char) 0x2014;

    private static final Path JAVA = Path.of("src/main/java/com/createstats");
    private static final Path BLOCKS = JAVA.resolve("registry/ModBlocks.java");
    private static final Path BLOCK_ENTITIES = JAVA.resolve("registry/ModBlockEntities.java");
    private static final Path BLOCK = JAVA.resolve("vccmk4/air/AerialBlock.java");
    private static final Path BE = JAVA.resolve("vccmk4/air/AerialBE.java");
    private static final Path RENDERER = JAVA.resolve("renderer/AerialCockpitRenderer.java");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path OLD_GEN = Path.of("gen_vcc_mk4_aerial_art.py");

    private static final List<Path> MINE = List.of(RENDERER, JAVA.resolve("vccmk4/air/core/AerialCockpitState.java"),
            JAVA.resolve("vccmk4/air/core/AerialCockpitMotion.java"), JAVA.resolve("vccmk4/air/core/CockpitShape.java"),
            Path.of("gen_vcc_mk4_aerial_cockpit.py"), Path.of("aerial_cockpit_kit.py"),
            Path.of("render_vcc_mk4_aerial_cockpit.py"));

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

    private static JsonObject json(Path p) throws IOException {
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theAerialKeepsItsIdAndItsTypeDrawsTheCockpit() throws IOException {
        String block = statement(code(BLOCKS), "VCC_MK4_AERIAL");
        assertTrue(block.contains(".block(\"vcc_mk4_aerial\", com.createstats.vccmk4.air.AerialBlock::new)"),
                "the registry id and class saved ships know: " + block);
        assertTrue(block.contains(".addLayer(() -> RenderType::cutoutMipped)"), "the body's own layer: cut-out lenses and slots");
        String type = statement(code(BLOCK_ENTITIES), "VCC_MK4_AERIAL");
        assertTrue(type.contains(".blockEntity(\"vcc_mk4_aerial\", com.createstats.vccmk4.air.AerialBE::new)"), type);
        assertTrue(type.contains(".renderer(() -> com.createstats.renderer.AerialCockpitRenderer::new)"),
                "a type with no renderer draws the body with every needle, lamp and the yoke missing, and says nothing");
    }

    @Test
    void bothSidesTickAndTheHitboxTurnsWithTheFacing() throws IOException {
        String src = code(BLOCK);
        String ticker = body(src, "BlockEntityTicker<T> getTicker(");
        assertTrue(ticker.contains("if (type != ModBlockEntities.VCC_MK4_AERIAL.get()) return null;"), "keyed on its own type");
        assertTrue(ticker.contains("if (level.isClientSide) return (lvl, p, st, be) -> ConsoleFault.tick(be, ((AerialBE) be)::clientTick);"),
                "the client steps the instruments' motion, under the hard rule");
        assertTrue(ticker.contains("ConsoleFault.tick(be, ((AerialBE) be)::serverTick)"), "and the server flies the ship");
        assertTrue(body(src, "VoxelShape getShape(").contains("return SHAPES.get(state.getValue(FACING));"),
                "one shape for every facing puts the quadrant on the wrong side three times in four");
        String build = body(src, "Map<Direction, VoxelShape> buildShapes()");
        assertTrue(build.contains("{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}"),
                "quarter turns in the blockstate's order");
        assertTrue(build.contains("CockpitShape.boxes(CockpitShape.NORTH, turns)"), "the boxes CockpitShapeTest holds to the model");
        assertTrue(src.contains("builder.add(FACING);") && !src.contains("builder.add(FACING,"),
                "one property, as before: every saved Aerial's blockstate is still a valid one");
    }

    @Test
    void theServerSendsTheCockpitAfterItFliesAndOnlyWhenItChanged() throws IOException {
        String src = code(BE);
        String tick = body(src, "public void serverTick()");
        int act = tick.indexOf("command = pilot.tick(");
        int publish = tick.indexOf("publish(server, now)");
        int cockpit = tick.indexOf("cockpitTick(server, craft, r);");
        int end = tick.indexOf("cost.endTick();");
        assertTrue(act > 0 && publish > act && cockpit > publish && end > cockpit,
                "the cockpit shows what the Aerial just decided, and is counted in the tick's cost");
        String sent = body(src, "private void cockpitTick(");
        assertTrue(sent.contains("if (now.equals(cockpitSent)) return;"), "an unchanged state sends nothing");
        assertTrue(sent.contains("cockpitOnly = true;") && sent.contains("sendBlockUpdated"));
    }

    @Test
    void everyInstrumentReadsTheAerialsOwnNumber() throws IOException {
        String cap = body(code(BE), "private AerialCockpitState captureCockpit(");
        for (String fact : new String[]{
                "banner(r).alert()",
                "c.order()",
                "c.engine()",
                "c.turn()",
                "AltitudeLaw.referenceSpeed(AltitudeLaw.lookaheadError(s.y(), s.vy(), flown), lim) / lim.climb()",
                "setup.trim",
                "s.y()", "c.targetAltitude()",
                "s.vy()", "s.airForward()",
                "c.targetHeading()", "s.yawRate()",
                "pilot.feel().turn",
                "AerialCockpitMotion.attitude(q.x, q.y, q.z, q.w, f.getStepX(), f.getStepZ())"}) {
            assertTrue(cap.contains(fact), "the cockpit no longer reads " + fact);
        }
        assertTrue(cap.contains("!pilot.staffHeld()"), "a staff-held ship's plan aims at where it is: no climb on the yoke");
    }

    @Test
    void theCockpitRidesTheOrdinarySyncAndNeverReplacesAWholeUpdateOrReachesTheSave() throws IOException {
        String src = code(BE);
        String changed = body(src, "public void setChanged()");
        assertTrue(changed.contains("cockpitWhole = true;") && changed.contains("super.setChanged();"),
                "the goggle lines' own sync must make the next update whole, or 25 bytes could replace them");
        String packet = body(src, "Packet<ClientGamePacketListener> getUpdatePacket()");
        assertTrue(packet.contains("cockpitOnly && !cockpitWhole"));
        assertTrue(packet.contains("ClientboundBlockEntityDataPacket.create(this, (be, registries) -> cockpitTag())"));
        assertTrue(packet.contains("return ClientboundBlockEntityDataPacket.create(this);"), "otherwise the whole update");
        assertTrue(body(src, "CompoundTag getUpdateTag(").contains("tag.putByteArray(COCKPIT, cockpit.encode());"),
                "a client that arrives with the ship in flight starts with its instruments in place");
        String data = body(src, "public void onDataPacket(");
        assertTrue(data.contains("tag.size() == 1 && tag.contains(COCKPIT, Tag.TAG_BYTE_ARRAY)"),
                "only a tag that is nothing but the cockpit is one");
        assertTrue(data.contains("super.onDataPacket(net, pkt, registries);"));
        assertTrue(body(src, "protected void loadAdditional(").contains("AerialCockpitState.decode(tag.getByteArray(COCKPIT))"));
        assertFalse(body(src, "protected void saveAdditional(").contains("COCKPIT"),
                "never saved: a ship's instruments are a fact about this tick, and a saved Aerial loads unchanged");
        String client = body(src, "public void clientTick()");
        assertTrue(client.contains("cockpitPose.tick(cockpit);"), "the client's pose chases what the server last said");
        assertTrue(client.contains("SableBridge.subLevelAccessOf(this)"), "and finds its craft for the renderer's pose read");
    }

    @Test
    void theRendererReadsTheDrawnPoseAndDrawsEachTwoColourLensOnce() throws IOException {
        String src = code(RENDERER);
        assertEquals(1, src.split("RenderType\\.cutoutMipped\\(\\)", -1).length - 1, "the block's own layer, obtained once");
        assertTrue(src.contains("SableBridge.renderOrientation(craft, partialTick)"),
                "heading and attitude from the ship as Sable draws it this frame, not a tick behind");
        assertTrue(src.contains("glow(modelOf(lamp), false, f.shown(lamp), state, ms, cutout)")
                        && src.contains("glow(modelOf(Lamp.CAUTION), true, f.shown(Lamp.CAUTION), state, ms, cutout)"),
                "a lens lit in both colours at once would fight over its pixels");
        assertTrue(src.contains("AerialCockpitMotion.haloAlpha(f.shown(lamp))"), "and its halo blooms in one colour");
        assertFalse(src.contains("f.lamp(lamp)"), "no lamp is drawn at its raw level");
        assertTrue(src.contains("AerialCockpitMotion.earthStrips("), "the horizon's earth is cut to its dial at every pitch");
        assertTrue(src.contains("return ModConfiguration.cappedViewDistance(64);"), "the mod's view distance cap");
    }
}
