package com.createstats.chunkloader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownMapWiringTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");

    private static String read(String relative) throws IOException {
        final Path p = MAIN.resolve(relative);
        assertTrue(Files.exists(p), "missing source: " + p);
        return strip(Files.readString(p, StandardCharsets.UTF_8));
    }

    private static String strip(String s) {
        return s.replace("\r\n", "\n").replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String text, String signature) {
        final int at = text.indexOf(signature);
        assertTrue(at >= 0, "not found, so this test guards nothing: " + signature);
        final int end = text.indexOf("\n    }", at);
        return text.substring(at, end < 0 ? text.length() : end);
    }

    private static String flat(String s) {
        return s.replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("use with an empty hand opens the map on the client and claims on the server; the interim controls are gone")
    void useOpensTheMap() throws IOException {
        final String block = read("block/SparkCrownBlock.java");
        final String use = body(block, "public static InteractionResult use(");
        final int gate = use.indexOf("if (!player.getMainHandItem().isEmpty())");
        final int pass = use.indexOf("return InteractionResult.PASS;");
        final int client = use.indexOf("if (level.isClientSide()) {");
        final int open = use.indexOf("CrownScreenAccess.openMap(pos);");
        final int success = use.indexOf("return InteractionResult.SUCCESS;");
        assertTrue(gate >= 0 && pass > gate, "the empty-hand gate must pass first, or wire never lands: " + use);
        assertTrue(client > pass && open > client && success > open,
                "the client branch must open the map before it answers: " + use);
        assertTrue(use.indexOf("be.claimIfUnowned(server);") > success, "the server must still claim an unowned crown");
        assertFalse(block.contains("interimUse") || read("blockentity/SparkCrownBE.java").contains("interimUse"),
                "the interim spiral pick and sneak drop are gone (part 3)");
        assertTrue(read("block/SparkCrownStructureBlock.java").contains("SparkCrownBlock.use(level, station, player)"),
                "a cell must answer as the crown does");
    }

    @Test
    @DisplayName("the common side reaches the screen only by reflection, and the names it reflects exist")
    void theBridge() throws IOException {
        final String bridge = read("chunkloader/CrownScreenAccess.java");
        assertTrue(bridge.contains("\"com.createstats.gui.crown.CrownMapClient\""), "the bridge names another class");
        assertTrue(body(bridge, "private static void invoke(").contains("if (FMLEnvironment.dist != Dist.CLIENT)"),
                "a dedicated server must never look for a client class");
        final String client = read("gui/crown/CrownMapClient.java");
        final String[][] hooks = {
                {"invoke(\"open\", BlockPos.class, crown)", "public static void open(BlockPos "},
                {"invoke(\"data\", CrownMapDataPacket.class, packet)", "public static void data(CrownMapDataPacket "},
                {"invoke(\"reply\", CrownPickReplyPacket.class, packet)", "public static void reply(CrownPickReplyPacket "}};
        for (String[] h : hooks) {
            assertTrue(bridge.contains(h[0]), "the bridge does not call " + h[0]);
            assertTrue(client.contains(h[1]), "the client has no " + h[1] + ": the reflection finds nothing");
        }
        assertTrue(body(client, "public static void open(BlockPos ").contains("new CrownMapScreen(be)"),
                "open must open the screen");
    }

    @Test
    @DisplayName("the four payloads are registered once each, the right way round, under protocol 60 or later")
    void packetsRegistered() throws IOException {
        final String packets = flat(read("network/ModPackets.java"));
        final String[][] payloads = {{"playToServer", "CrownMapRequestPacket"}, {"playToClient", "CrownMapDataPacket"},
                {"playToServer", "CrownPickPacket"}, {"playToClient", "CrownPickReplyPacket"}};
        for (String[] p : payloads) {
            final String q = "com.createstats.chunkloader.net." + p[1];
            final String line = "registrar." + p[0] + "(" + q + ".TYPE, " + q + ".STREAM_CODEC, " + q + "::handle);";
            assertTrue(packets.contains(line), "not registered as " + p[0] + ": " + p[1]);
            assertEquals(1, count(packets, q + ".TYPE"), p[1] + " registered more than once");
        }
        final Matcher v = Pattern.compile("\\.versioned\\(\"(\\d+)\"\\)").matcher(packets);
        assertTrue(v.find() && Integer.parseInt(v.group(1)) >= 60,
                "four new payloads: a client on an older jar must be told at connect time");
    }

    @Test
    @DisplayName("the screen imports nothing of the server's and reads the crown's synced copy (INV-15)")
    void theScreenStaysOnTheClient() throws IOException {
        final List<Path> files;
        try (Stream<Path> s = Files.list(MAIN.resolve("gui/crown"))) {
            files = s.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(files.size() >= 3, "the screen's package is missing");
        final String[] banned = {"import net.minecraft.server.level.", "import net.minecraft.server.MinecraftServer",
                "ServerLevel", "ServerPlayer", "MinecraftServer", "CrownRegistry", "CrownTickets", "CrownSamples",
                "CrownGuesses", "CrownMapService", "FeWireNetwork", "togglePick(", "getSingleplayerServer().getLevel(",
                "getSingleplayerServer().overworld("};
        final List<String> found = new ArrayList<>();
        for (Path f : files) {
            final String text = strip(Files.readString(f, StandardCharsets.UTF_8));
            for (String b : banned) {
                if (text.contains(b)) {
                    found.add(f.getFileName() + ": " + b);
                }
            }
        }
        assertTrue(found.isEmpty(), "the map reads a server object: " + found);
        final String screen = read("gui/crown/CrownMapScreen.java");
        final String crown = body(screen, "private CrownMapTooltip.Crown crown()");
        assertTrue(crown.contains("be.picks()") && crown.contains("be.crown()") && crown.contains("be.clientBoard()"),
                "the judge must run on the synced copy: " + crown);
        assertTrue(screen.contains("CrownMapTooltip.verdict(c, CrownRegion.x(chunk), CrownRegion.z(chunk))"),
                "a click must be judged by the server's own judge before it is sent");
    }

    @Test
    @DisplayName("a request never loads a chunk and is answered only within Sable's reach of the crown")
    void requestsNeverLoad() throws IOException {
        final String service = read("chunkloader/map/CrownMapService.java");
        final String crownFor = body(service, "private static SparkCrownBE crownFor(");
        final int loaded = crownFor.indexOf("level.isLoaded(pos)");
        final int be = crownFor.indexOf("level.getBlockEntity(pos)");
        assertTrue(loaded >= 0 && be > loaded, "isLoaded before getBlockEntity, or a packet loads chunks: " + crownFor);
        assertTrue(crownFor.contains("SableBridge.isPlayerInInteractionRangeFor(be, player)"),
                "reach as Sable measures it, never a raw distance");
        assertTrue(body(service, "public static void answer(").contains("crownFor(player, p.crown())"),
                "the picture must be asked through the check");
        assertTrue(body(service, "public static void pick(").contains("crownFor(player, p.crown())"),
                "a click must be asked through the check");
        final String samples = read("chunkloader/map/CrownSamples.java");
        assertTrue(samples.contains("getChunkSource().getChunkNow("), "samples read the loaded copy");
        assertFalse(Pattern.compile("level\\.getChunk\\(|getChunkAt\\(|getBlockEntity\\(|level\\.getBlockState\\(")
                .matcher(samples).find(), "a sample must never load a chunk");
    }

    @Test
    @DisplayName("every click and paint goes through togglePick, in order, stopping at the first refusal")
    void picksGoThroughTheDoor() throws IOException {
        final String pick = body(read("chunkloader/map/CrownMapService.java"), "public static void pick(");
        final int skip = pick.indexOf("if (be.hasPick(chunk) != p.drop()) {");
        final int toggle = pick.indexOf("be.togglePick(player, chunk)");
        final int stop = pick.indexOf("if (!v.changes()) {");
        assertTrue(skip >= 0 && toggle > skip, "a chunk already the way the paint wants must not be toggled back: " + pick);
        assertTrue(stop > toggle && pick.indexOf("break;", stop) > stop, "the first refusal must stop the rest: " + pick);
        assertTrue(read("chunkloader/net/CrownPickReplyPacket.java").contains("verdicts.get(i).ordinal()"),
                "the reply carries ordinals, the contract");
    }

    private static int count(String text, String what) {
        int n = 0;
        for (int at = text.indexOf(what); at >= 0; at = text.indexOf(what, at + what.length())) {
            n++;
        }
        return n;
    }
}
