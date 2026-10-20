package com.createstats.ground;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundWiringCoverageTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static String method(String source, String declaration) {
        final int start = source.indexOf(declaration);
        assertTrue(start >= 0, declaration + " is gone; this guard has lost its anchor");
        return block(source, start);
    }

    static String block(String source, int start) {
        final int open = source.indexOf('{', start);
        assertTrue(open > start, "no block after " + source.substring(start, Math.min(source.length(), start + 60)));
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError("unclosed block; the file is malformed");
    }

    static String statement(String source, int start) {
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '(' || c == '{') {
                depth++;
            } else if (c == ')' || c == '}') {
                depth--;
            } else if (c == ';' && depth == 0) {
                return source.substring(start, i + 1);
            }
        }
        throw new AssertionError("unterminated statement");
    }

    static boolean hasStatement(String source, String head, String... needles) {
        for (int at = source.indexOf(head); at >= 0; at = source.indexOf(head, at + 1)) {
            final String st = statement(source, at);
            boolean all = true;
            for (final String n : needles) {
                all &= st.contains(n);
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    static int depthAt(String body, int index) {
        int depth = 0;
        for (int i = 0; i < index; i++) {
            final char c = body.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return depth;
    }

    static String declaration(String source, String head) {
        final int at = source.indexOf(head);
        assertTrue(at >= 0, head + " is gone; this guard has lost its anchor");
        return source.substring(at, source.indexOf('{', at));
    }

    @Test
    @DisplayName("the console is marked, and is a source")
    void theConsoleIsMarkedAndIsASource() throws IOException {
        assertTrue(declaration(read("vccmk4/Vcc4Block.java"), "public class Vcc4Block ").contains("GroundTelemetryBlock"),
                "Vcc4Block is not a GroundTelemetryBlock: a Display Link on the console offers no ground source");
        assertTrue(declaration(read("vccmk4/Vcc4BE.java"), "public class Vcc4BE ").contains("GroundTelemetrySource"),
                "Vcc4BE is not a GroundTelemetrySource: the panel's heartbeat branch never matches it");
    }

    @Test
    @DisplayName("the console builds its snapshot whether or not anyone drives")
    void theSnapshotIsBuiltWhoeverDrives() throws IOException {
        final String tick = method(read("vccmk4/Vcc4BE.java"), "public void serverTick() {");
        final int at = tick.indexOf("telemetry.tick(server, now);");
        assertTrue(at >= 0, "serverTick never ticks the telemetry, so no panel ever gets a snapshot");
        assertEquals(1, depthAt(tick, at),
                "the telemetry tick sits inside a branch: a parked or towed vehicle's wall would go blank");
    }

    @Test
    @DisplayName("the recorder is saved and loaded with the console, and kept out of the update tag")
    void theRecorderIsSavedAndKeptOffTheWire() throws IOException {
        final String be = read("vccmk4/Vcc4BE.java");
        assertTrue(be.contains("static final String GROUND_RECORDER = \"GroundRecorder\";"),
                "the recorder's NBT key has moved: saved odometers would load as zero");
        assertTrue(method(be, "protected void saveAdditional(").contains(
                        "tag.putByteArray(GROUND_RECORDER, telemetry.saveRecorder());"),
                "the recorder is not saved: the odometer and the trail reset at every restart");
        final String load = method(be, "protected void loadAdditional(");
        assertTrue(hasStatement(load, "if (tag.contains(GROUND_RECORDER", "telemetry.loadRecorder(tag.getByteArray(GROUND_RECORDER))"),
                "the recorder is never loaded back");
        assertTrue(method(be, "public CompoundTag getUpdateTag(").contains("tag.remove(GROUND_RECORDER);"),
                "the recorder rides the update tag: its whole history goes to every client on every block update");
    }

    @Test
    @DisplayName("a Display Link offers the ground source on any block marked GroundTelemetryBlock")
    void theDisplayLinkOffersTheSource() throws IOException {
        final String mod = read("CreateStats.java");
        assertTrue(hasStatement(mod, "GROUND_SOURCE = ", "DISPLAY_SOURCES.register(\"vcc_ground\"",
                        "GroundDisplaySource::new"),
                "the vcc_ground Display Source is not registered, so no Display Link can ever select it");
        final String mappings = method(mod, "private void registerStatsLinkMappings(");
        assertTrue(hasStatement(mappings, "final com.createstats.display.GroundDisplaySource groundSource",
                        "GROUND_SOURCE.get()"),
                "registerStatsLinkMappings does not fetch the ground source");
        assertTrue(hasStatement(mappings, "DisplaySource.BY_BLOCK.addProvider(", "GroundTelemetryBlock", "groundSource"),
                "no BY_BLOCK provider answers for GroundTelemetryBlock, so a Display Link on the console offers nothing");
    }

    @Test
    @DisplayName("the source passes the panel's opt-in gate and is named in the lang file")
    void theSourceIsAStatsLinkWithAName() throws IOException {
        final String src = read("display/GroundDisplaySource.java");
        final String decl = declaration(src, "public class GroundDisplaySource ");
        assertTrue(decl.contains("extends DisplaySource"), "GroundDisplaySource is no DisplaySource");
        assertTrue(decl.contains("StatsLinkSource"),
                "GroundDisplaySource is not a StatsLinkSource: StatsDisplayTarget's opt-in gate refuses the link");
        assertTrue(method(src, "protected String getTranslationKey(").contains("return \"vcc_ground\";"),
                "the source's translation key has moved off vcc_ground");
        assertTrue(Files.readString(LANG, StandardCharsets.UTF_8).contains("\"create_stats.display_source.vcc_ground\":"),
                "en_us.json has no create_stats.display_source.vcc_ground, so the link's source list shows a raw key");
    }

    @Test
    @DisplayName("a Stats Display takes the link as a heartbeat, forgets it when unselected, and names it")
    void thePanelTakesTheHeartbeat() throws IOException {
        final String target = read("display/StatsDisplayTarget.java");
        final String accept = method(target, "public void acceptText(");
        final int gate = accept.indexOf("if (!supportedSourceType.isEmpty() && !isStatsDisplayLinkSelected(context))");
        assertTrue(gate >= 0, "the opt-in gate in acceptText has moved; this guard has lost its anchor");
        assertTrue(block(accept, gate).contains("panelTarget.groundLinks().remove(sourcePos);"),
                "a ground link switched to another source is never dropped from the panel");
        final int clear = accept.indexOf("panelTarget.clearStatsDisplayLinkOptInRequired();");
        final int branch = accept.indexOf("if (sourceBE instanceof com.createstats.ground.GroundTelemetrySource)");
        assertTrue(branch >= 0, "acceptText has no ground branch, so the link never reaches GroundLinks");
        assertTrue(clear >= 0 && branch > clear,
                "the ground branch runs before the opt-in gate is passed, so an unselected link would still heartbeat");
        final String body = block(accept, branch);
        assertTrue(body.contains("panelTarget.groundLinks().heartbeat(sourcePos, gameTick);") && body.contains("return;"),
                "the ground branch does not heartbeat and return, so its text would be parsed as a channel");
        final String type = method(target, "public static String getStatsDisplayLinkSourceType(");
        final int at = type.indexOf("if (sourceBE instanceof com.createstats.ground.GroundTelemetrySource)");
        assertTrue(at >= 0 && block(type, at).contains("return com.createstats.blockentity.GroundLinks.SOURCE_TYPE;"),
                "getStatsDisplayLinkSourceType does not name the console, so the opt-in prompt and the family miss it");
    }

    @Test
    @DisplayName("the panel ticks, saves, syncs and loads its ground links")
    void thePanelKeepsItsLinks() throws IOException {
        final String be = read("blockentity/StatsDisplayPanelBE.java");
        assertTrue(method(be, "public void tick() {").contains("this.groundLinks.serverTick(serverLevel, serverLevel.getGameTime());"),
                "the panel never pulls the ground feed");
        assertTrue(method(be, "protected void saveAdditional(").contains("this.groundLinks.save(tag, false);"),
                "ground links are not saved, so a reload forgets them until the Display Link beats again");
        assertTrue(method(be, "private void saveSyncAdditional(").contains("this.groundLinks.save(tag, true);"),
                "ground links are not in the update tag, so the client never knows the panel is linked");
        assertTrue(method(be, "public CompoundTag getUpdateTag(").contains("this.saveSyncAdditional(tag, registries);"),
                "the update tag no longer comes from saveSyncAdditional; this guard's premise has moved");
        assertTrue(method(be, "protected void loadAdditional(").contains("this.groundLinks.load(tag, "),
                "ground links are never loaded, from disk or from the wire");
    }

    @Test
    @DisplayName("both ground packets are registered, one each way")
    void thePacketsAreRegistered() throws IOException {
        final String packets = read("network/ModPackets.java");
        assertTrue(hasStatement(packets, "registrar.playToClient(GroundFeedPacket.TYPE", "GroundFeedPacket::handle"),
                "the feed packet is not registered: the first snapshot sent disconnects the viewer");
        assertTrue(hasStatement(packets, "registrar.playToServer(GroundCommandPacket.TYPE", "GroundCommandPacket::handle"),
                "the command packet is not registered: the studio's buttons disconnect the player");
    }

    @Test
    @DisplayName("the family, the page and the faces are all reachable")
    void thePagesAndFacesAreReachable() throws IOException {
        final String catalog = read("display/CustomScreenSourceFamilyCatalog.java");
        assertTrue(hasStatement(catalog, "register(def(\"vehicle.vcc_ground\"", "GroundLinks.SOURCE_TYPE"),
                "no source family claims the console's source type");
        assertTrue(read("statsmk3/page/DrivePage.java").contains("public static final String FAMILY = \"vehicle.vcc_ground\";"),
                "DrivePage handles a different family from the one the catalog registers");
        assertTrue(read("statsmk3/page/Mk3PageRegistry.java").contains("register(new DrivePage());"),
                "the Drive page is not registered, so the studio never offers it");
        final String painter = read("statsmk3/paint/PanelPainter.java");
        final int drive = painter.indexOf("if (model.faceMode != null && model.faceMode.isDrive())");
        assertTrue(drive >= 0 && block(painter, drive).contains("DriveFacePainter.paint(s, area, model, lod);"),
                "the painter never draws the Drive faces");
        final String cache = read("statsmk3/paint/PanelModelCache.java");
        final int fill = cache.indexOf("if (settings.faceMode.isDrive() && be != null)");
        assertTrue(fill >= 0, "the model cache never fills the Drive frame");
        final String filled = block(cache, fill);
        assertTrue(filled.contains("this.model.driveLinked = controller.groundLinks().hasLinks();")
                        && filled.contains("this.model.drive = controller.groundLinks().feed().frame("),
                "the model cache does not hand the painter the controller's ground frame");
    }
}
