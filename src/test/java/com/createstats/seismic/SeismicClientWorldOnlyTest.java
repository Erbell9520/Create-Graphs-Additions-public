package com.createstats.seismic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicClientWorldOnlyTest {
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path CLIENT = MAIN.resolve("com/createstats/seismic/client/SeismicClient.java");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String code(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    private static String flat(String s) {
        return s.replaceAll("\\s+", " ");
    }

    private static String body(String src, String head) {
        final int at = src.indexOf(head);
        if (at < 0) {
            return null;
        }
        final int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            final char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return src.substring(open, i + 1);
            }
        }
        return null;
    }

    private static String guard(String body, String call) {
        final int at = body.indexOf(call);
        assertTrue(at >= 0, "no " + call + " left to guard");
        final int start = body.lastIndexOf("if (", at);
        assertTrue(start >= 0, call + " stands under no if at all");
        final int end = body.indexOf(") {", start);
        assertTrue(end >= 0 && end < at, call + "'s nearest if is not the one round it");
        return body.substring(start, end + 1);
    }

    @Test
    void aRigIsInTheWorldOnlyInTheClientsOwnLevel() throws IOException {
        final String client = code(read(CLIENT));
        final String in = body(client, "static boolean inWorld(SeismicScannerBE be)");
        assertNotNull(in, "SeismicClient.inWorld is gone: nothing tells a Ponder scene's rig from one in the world");
        assertTrue(flat(in).contains("final Level level = be.getLevel(); return level != null && level == "
                        + "Minecraft.getInstance().level;"),
                "a rig is in the world only when its level is the client's own level: " + flat(in));
    }

    @Test
    void aRigInAnotherLevelIsNoDistanceFromThePlayer() throws IOException {
        final String client = code(read(CLIENT));
        final String away = body(client, "static double away(SeismicScannerBE be)");
        assertNotNull(away, "SeismicClient.away is gone");
        final String flat = flat(away);
        assertTrue(flat.contains("if (mc.player == null || !inWorld(be)) { return Double.POSITIVE_INFINITY; }"),
                "away must be infinite for a rig in another level, before it is measured: " + flat);
        assertTrue(flat.indexOf("!inWorld(be)") < flat.indexOf("middle(be)"),
                "the level is asked before the rig is projected (a Ponder level is no Sable world)");
        final String tick = flat(body(client, "public static void tick(SeismicScannerBE be)"));
        assertTrue(tick.contains("final double away = away(be);"), "the tick measures the player by away");
        assertTrue(tick.contains("final boolean near = away <= EARSHOT * EARSHOT;"),
                "the ratchet's and the pen's earshot is measured by away, so no scene rig is ever near");
        assertTrue(guard(tick, "SeismicReveals.atRig(be, now)").contains("away <="),
                "a rig is told the player stands at it only by away, which no scene rig has");
    }

    @Test
    void theHumAndTheShakeAreForRigsInTheWorld() throws IOException {
        final String client = code(read(CLIENT));
        final String tick = flat(body(client, "public static void tick(SeismicScannerBE be)"));
        assertTrue(guard(tick, "SeismicWinchSound.keep(level, pos)").contains("inWorld(be)"),
                "the winch's hum plays in the world's sound engine: only a rig in the world may start it");
        final String blow = flat(body(client, "private static void blow(SeismicScannerBE be, long now, boolean near)"));
        assertTrue(guard(blow, "SeismicShake.blow(be, be.blowStrength())").contains("inWorld(be)"),
                "the shake moves the player's own camera: only a rig in the world may give it");
    }

    @Test
    void theHumIsMeasuredWhereTheRigStandsInTheWorld() throws IOException {
        final String winch = code(read(MAIN.resolve("com/createstats/seismic/client/SeismicWinchSound.java")));
        final String keep = flat(body(winch, "public static void keep(Level level, BlockPos pos)"));
        assertTrue(keep.contains("SeismicClient.away(be) > REACH * REACH"),
                "the hum starts by SeismicClient.away, which projects a craft's rig into the world: " + keep);
        assertTrue(keep.contains("hum.place(be);"), "a new hum is placed where the rig stands in the world");
        final String place = flat(body(winch, "private void place(SeismicScannerBE be)"));
        assertTrue(place.contains("SableBridge.worldPointOf(be,"), "the hum's place is projected off the plot: " + place);
        final String tick = flat(body(winch, "public void tick()"));
        assertTrue(tick.contains("place(be);") && tick.indexOf("place(be);") < tick.indexOf("mc.player.distanceToSqr(x, y, z)"),
                "each tick follows the craft before the player's distance is taken: " + tick);
        assertTrue(!winch.contains("distanceToSqr(pos.getX()"), "no distance is taken from the raw block position");
    }

    @Test
    void onlyTheGuardedClientTickReachesThePlayer() throws IOException {
        final List<String> calls = List.of("SeismicReveals.atRig(", "SeismicWinchSound.keep(", "SeismicShake.blow(");
        final int[] count = new int[calls.size()];
        final List<String> where = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (final Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                final String src = code(read(p));
                for (int i = 0; i < calls.size(); i++) {
                    for (int at = src.indexOf(calls.get(i)); at >= 0; at = src.indexOf(calls.get(i), at + 1)) {
                        count[i]++;
                        if (!p.endsWith(Path.of("com/createstats/seismic/client/SeismicClient.java"))) {
                            where.add(calls.get(i) + " in " + MAIN.relativize(p));
                        }
                    }
                }
            }
        }
        assertTrue(where.isEmpty(), "only SeismicClient's guarded tick may reach the player's world: " + where);
        for (int i = 0; i < calls.size(); i++) {
            assertEquals(1, count[i], calls.get(i) + " is called " + count[i] + " times; the guard covers one");
        }
    }
}
