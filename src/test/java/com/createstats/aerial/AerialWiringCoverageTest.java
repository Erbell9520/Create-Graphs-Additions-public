package com.createstats.aerial;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialWiringCoverageTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8);
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

    @Test
    @DisplayName("a Display Link offers the Aerial source on any block marked AerialTelemetryBlock")
    void theDisplayLinkOffersTheSource() throws IOException {
        final String mod = read("CreateStats.java");
        assertTrue(hasStatement(mod, "AERIAL_SOURCE = ", "DISPLAY_SOURCES.register(\"vcc_aerial\"",
                        "AerialDisplaySource::new"),
                "the vcc_aerial Display Source is not registered, so no Display Link can ever select it");
        final String mappings = method(mod, "private void registerStatsLinkMappings(");
        assertTrue(hasStatement(mappings, "final com.createstats.display.AerialDisplaySource aerialSource",
                        "AERIAL_SOURCE.get()"),
                "registerStatsLinkMappings does not fetch the Aerial source");
        assertTrue(hasStatement(mappings, "DisplaySource.BY_BLOCK.addProvider(", "AerialTelemetryBlock", "aerialSource"),
                "no BY_BLOCK provider answers for AerialTelemetryBlock, so a Display Link on the Aerial offers nothing");
    }

    @Test
    @DisplayName("the source passes the panel's opt-in gate and is named in the lang file")
    void theSourceIsAStatsLinkWithAName() throws IOException {
        final String src = read("display/AerialDisplaySource.java");
        final int header = src.indexOf("public class AerialDisplaySource ");
        assertTrue(header >= 0, "AerialDisplaySource has lost its class declaration");
        final String decl = src.substring(header, src.indexOf('{', header));
        assertTrue(decl.contains("extends DisplaySource"), "AerialDisplaySource is no DisplaySource");
        assertTrue(decl.contains("StatsLinkSource"),
                "AerialDisplaySource is not a StatsLinkSource: StatsDisplayTarget's opt-in gate refuses the link");
        assertTrue(method(src, "protected String getTranslationKey(").contains("return \"vcc_aerial\";"),
                "the source's translation key has moved off vcc_aerial");
        assertTrue(Files.readString(LANG, StandardCharsets.UTF_8).contains("\"create_stats.display_source.vcc_aerial\":"),
                "en_us.json has no create_stats.display_source.vcc_aerial, so the link's source list shows a raw key");
    }

    @Test
    @DisplayName("a Stats Display takes the link as a heartbeat, forgets it when unselected, and names it")
    void thePanelTakesTheHeartbeat() throws IOException {
        final String target = read("display/StatsDisplayTarget.java");
        final String accept = method(target, "public void acceptText(");
        final int gate = accept.indexOf("if (!supportedSourceType.isEmpty() && !isStatsDisplayLinkSelected(context))");
        assertTrue(gate >= 0, "the opt-in gate in acceptText has moved; this guard has lost its anchor");
        assertTrue(block(accept, gate).contains("panelTarget.aerialLinks().remove(sourcePos);"),
                "an Aerial link switched to another source is never dropped from the panel");
        final int clear = accept.indexOf("panelTarget.clearStatsDisplayLinkOptInRequired();");
        final int branch = accept.indexOf("if (sourceBE instanceof com.createstats.aerial.AerialTelemetrySource)");
        assertTrue(branch >= 0, "acceptText has no Aerial branch, so the link never reaches AerialLinks");
        assertTrue(clear >= 0 && branch > clear,
                "the Aerial branch runs before the opt-in gate is passed, so an unselected link would still heartbeat");
        final String body = block(accept, branch);
        assertTrue(body.contains("panelTarget.aerialLinks().heartbeat(sourcePos, gameTick);") && body.contains("return;"),
                "the Aerial branch does not heartbeat and return, so its text would be parsed as a channel");
        final String type = method(target, "public static String getStatsDisplayLinkSourceType(");
        final int at = type.indexOf("if (sourceBE instanceof com.createstats.aerial.AerialTelemetrySource)");
        assertTrue(at >= 0 && block(type, at).contains("return com.createstats.blockentity.AerialLinks.SOURCE_TYPE;"),
                "getStatsDisplayLinkSourceType does not name the Aerial, so the opt-in prompt and the family miss it");
    }

    @Test
    @DisplayName("the panel ticks, saves, syncs and loads its Aerial links")
    void thePanelKeepsItsLinks() throws IOException {
        final String be = read("blockentity/StatsDisplayPanelBE.java");
        assertTrue(method(be, "public void tick() {").contains("this.aerialLinks.serverTick(serverLevel, serverLevel.getGameTime());"),
                "the panel never pulls the Aerial feed");
        assertTrue(method(be, "protected void saveAdditional(").contains("this.aerialLinks.save(tag, false);"),
                "Aerial links are not saved, so a reload forgets them until the Display Link beats again");
        assertTrue(method(be, "private void saveSyncAdditional(").contains("this.aerialLinks.save(tag, true);"),
                "Aerial links are not in the update tag, so the client never knows the panel is linked");
        assertTrue(method(be, "public CompoundTag getUpdateTag(").contains("this.saveSyncAdditional(tag, registries);"),
                "the update tag no longer comes from saveSyncAdditional; this guard's premise has moved");
        assertTrue(method(be, "protected void loadAdditional(").contains("this.aerialLinks.load(tag, "),
                "Aerial links are never loaded, from disk or from the wire");
    }

    @Test
    @DisplayName("both Aerial packets are registered, one each way")
    void thePacketsAreRegistered() throws IOException {
        final String packets = read("network/ModPackets.java");
        assertTrue(hasStatement(packets, "registrar.playToClient(AerialFeedPacket.TYPE", "AerialFeedPacket::handle"),
                "the feed packet is not registered: the first snapshot sent disconnects the viewer");
        assertTrue(hasStatement(packets, "registrar.playToServer(AerialCommandPacket.TYPE", "AerialCommandPacket::handle"),
                "the command packet is not registered: the studio's buttons disconnect the player");
    }

    @Test
    @DisplayName("the family, the page and the faces are all reachable")
    void thePagesAndFacesAreReachable() throws IOException {
        final String catalog = read("display/CustomScreenSourceFamilyCatalog.java");
        assertTrue(hasStatement(catalog, "register(def(\"" + "flight.vcc_aerial" + "\"", "AerialLinks.SOURCE_TYPE"),
                "no source family claims the Aerial's source type");
        final String page = read("statsmk3/page/AeroPage.java");
        assertTrue(page.contains("public static final String FAMILY = \"flight.vcc_aerial\";"),
                "AeroPage handles a different family from the one the catalog registers");
        assertTrue(read("statsmk3/page/Mk3PageRegistry.java").contains("register(new AeroPage());"),
                "the Flight page is not registered, so the studio never offers it");
        final String painter = read("statsmk3/paint/PanelPainter.java");
        final int aero = painter.indexOf("if (model.faceMode != null && model.faceMode.isAero())");
        assertTrue(aero >= 0 && block(painter, aero).contains("AeroFacePainter.paint(s, area, model, lod);"),
                "the painter never draws the Aero faces");
        final String cache = read("statsmk3/paint/PanelModelCache.java");
        final int fill = cache.indexOf("if (settings.faceMode.isAero() && be != null)");
        assertTrue(fill >= 0, "the model cache never fills the Aero frame");
        final String filled = block(cache, fill);
        assertTrue(filled.contains("this.model.aeroLinked = controller.aerialLinks().hasLinks();")
                        && filled.contains("this.model.aero = controller.aerialLinks().feed().frame(System.nanoTime());"),
                "the model cache does not hand the painter the controller's Aerial frame");
    }

    @Test
    @DisplayName("the force budget is read while a panel is reading the ship")
    void theForcesAreReadWhileAPanelReads() throws IOException {
        final String be = read("vccmk4/air/AerialBE.java");
        assertTrue(method(be, "public AerialSnapshot aerialSnapshot() {").contains("lastDemand = level.getGameTime();"),
                "aerialSnapshot() no longer marks the demand: the forces are never read, and the Lift tab says so forever");
        final String tick = method(be, "public void serverTick() {");
        final int keep = tick.indexOf("if (craft != null && demanded(now)) {");
        assertTrue(keep >= 0 && block(tick, keep).contains("AerialForces.keepTracking("),
                "the tick no longer keeps Sable's per-force tracking on: sail lift, drag and levitation go unrecorded");
        final String build = method(be, "private void buildSnapshot(");
        final int read = build.indexOf("if (demanded(now)) {");
        assertTrue(read >= 0 && block(build, read).contains("forces = AerialForces.read("),
                "buildSnapshot no longer reads the force budget into the snapshot");
    }

    @Test
    @DisplayName("every Aero face mode is filled, has a composition, and is offered by a tab")
    void everyAeroFaceModeIsReachable() throws IOException {
        final String settings = read("statsmk3/Mk3Settings.java");
        final String faceEnum = block(settings, settings.indexOf("public enum FaceMode"));
        final List<String> modes = new ArrayList<>();
        final Matcher m = Pattern.compile("(?m)^\\s+(AERO_[A-Z_]+)[,;]").matcher(faceEnum);
        while (m.find()) {
            modes.add(m.group(1));
        }
        assertTrue(modes.size() >= 6, "the Aero face modes are gone from FaceMode: " + modes);
        final String isAero = method(faceEnum, "public boolean isAero()");
        final String face = method(read("statsmk3/paint/AeroBridgeFace.java"),
                "public static void paint(PanelSurface s, Rect area, AerialFrame f, Lod lod, AeroSkin k, Mk3Settings.FaceMode mode)");
        final String tabs = method(read("statsmk3/page/AeroView.java"), "static Mk3Settings.FaceMode faceFor(int tab)");
        for (final String mode : modes) {
            assertTrue(isAero.contains("this == " + mode),
                    mode + " is not in isAero(): the model cache never hands its face a frame, and the wall stays dark");
            assertTrue(tabs.contains("Mk3Settings.FaceMode." + mode), "no Flight page tab puts " + mode + " on the wall");
            if (!"AERO_DECK".equals(mode)) {
                assertTrue(face.contains("case " + mode + " ->"),
                        mode + " has no composition in AeroBridgeFace, so it silently draws the deck instead");
            }
        }
    }

    @Test
    @DisplayName("every flight instrument is in the catalogue, and every channel it reads is published")
    void theFlightInstrumentsAreFed() throws IOException {
        final String instruments = read("statsmk3/instrument/FlightInstruments.java");
        final String registry = method(read("statsmk3/instrument/InstrumentRegistry.java"), "    static {");
        final Matcher classes = Pattern.compile("public static final class (\\w+) implements Instrument").matcher(instruments);
        int n = 0;
        while (classes.find()) {
            n++;
            assertTrue(registry.contains("register(new FlightInstruments." + classes.group(1) + "());"),
                    classes.group(1) + " is never registered, so Compose never offers it");
        }
        assertTrue(n >= 5, "the flight instruments are gone: " + n);

        final String channels = read("aerial/AerialChannels.java");
        final Matcher partners = Pattern.compile("partner\\(ctx, \"(\\w+)\"\\)").matcher(instruments);
        int p = 0;
        while (partners.find()) {
            p++;
            assertTrue(channels.contains("put(sink, \"" + partners.group(1) + "\""),
                    "the instruments read aero_" + partners.group(1) + ", which AerialChannels never publishes");
        }
        assertTrue(p >= 8, "the instruments' partner lookups are gone: " + p);
    }

    @Test
    @DisplayName("the client's feed hands the painters the telegraph's ring")
    void theFeedHandsOnTheRing() throws IOException {
        final String links = read("blockentity/AerialLinks.java");
        assertTrue(method(links, "synchronized void accept(byte[] data)").contains("this.rungNanos = now;"),
                "the feed no longer notices the bell count rising, so the repeater never marks a ring");
        assertTrue(hasStatement(method(links, "public synchronized AerialFrame frame(long nowNanos)"),
                        "this.cached = new AerialFrame(", "rungAgo"),
                "the frame no longer carries the ring to the painters");
    }
}
