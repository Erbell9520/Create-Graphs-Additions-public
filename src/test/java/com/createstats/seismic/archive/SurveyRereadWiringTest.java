package com.createstats.seismic.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyRereadWiringTest {
    private static final String J = "src/main/java/com/createstats/";
    private static final String BE = J + "blockentity/SeismicScannerBE.java";

    @Test
    @DisplayName("the rig answers only a player at it, measured in the world, never mid-wave, after the bucket")
    void theRigAnswersOnlyAtTheRig() {
        final String be = code(read(BE));
        final String reread = body(be, "public void reread(ServerPlayer player,");
        assertNotNull(reread, "SeismicScannerBE has no reread(ServerPlayer, ...)");
        final int wave = reread.indexOf("if (survey != null)");
        final int mid = reread.indexOf("final Vec3 mid = worldMiddle();");
        final int reach = reread.indexOf("if (mid == null || !at(player, mid, WITNESS_RADIUS))");
        final int bucket = reread.indexOf("REREADS.allow(player.getUUID(),");
        final int read = reread.indexOf("SeismicArchiveHook.stamp(");
        assertTrue(wave >= 0, "a read-back is refused while the wave runs");
        assertTrue(mid >= 0 && reach > mid, "the distance is to the rig's middle in the world, and refuses when unreadable");
        assertTrue(bucket > reach, "the bucket is drawn on only by a player at the rig");
        assertTrue(read > bucket, "nothing is read before the distance and the bucket pass");
        final String middle = body(be, "private Vec3 worldMiddle()");
        assertNotNull(middle);
        assertTrue(middle.contains("SableBridge.worldPointOf(this,"),
                "on a Sable craft the block position is the plot, twenty million blocks out: project it");
        for (final String sig : new String[] {"private List<UUID> witnessesOf(", "private List<UUID> feltBy("}) {
            final String b = body(be, sig);
            assertNotNull(b, sig);
            assertTrue(b.contains("worldMiddle()") && b.contains("WITNESS_RADIUS") && !b.contains("worldPosition.getX()"),
                    sig + " must measure from where the rig stands, the read-back's own measure");
        }
    }

    @Test
    @DisplayName("the answer goes to the asker alone, and to a waiting asker only if still at the rig")
    void theAnswerGoesToTheAskerAlone() {
        final String be = code(read(BE));
        final String answer = body(be, "private void answer(");
        assertNotNull(answer);
        assertTrue(answer.contains("PacketDistributor.sendToPlayer(player, pkt)"), "the maps go to the asker");
        assertTrue(answer.contains("SurveyReread.toSend("), "only what the asker lacks");
        final String done = body(be, "private void readBackDone(");
        assertNotNull(done);
        assertTrue(done.contains("at(p, mid, WITNESS_RADIUS)") && done.indexOf("at(p, mid, WITNESS_RADIUS)")
                        < done.indexOf("answer(server, p,"),
                "a player who walked off while the record was read is not sent it");
        assertTrue(!be.contains("sendToPlayersTrackingChunk") && !be.contains("sendToAllPlayers"),
                "an outline sent to every player near the chunk is x-ray for people who never stood at the rig");
    }

    @Test
    @DisplayName("who only felt a survey is sent to the recorder; who felt a blow that made none hears why")
    void theFeltAreSentToTheRecorder() {
        final String report = body(code(read(BE)), "private void report()");
        assertNotNull(report);
        final int far = report.indexOf("final Component far = lastHow == 0");
        assertTrue(far >= 0, "the felt line depends on how the blow went");
        final int recorder = report.indexOf("\"block.create_stats.seismic_scanner.report.too_far\"", far);
        final int reason = report.indexOf(": line;", far);
        assertTrue(recorder > far && reason > recorder,
                "a survey can be read back at the recorder, so they are sent there; a failed blow left nothing to read, "
                        + "so they hear its reason, as the witnesses do");
        assertTrue(report.contains("p.displayClientMessage(far, true)"), "and the felt-it bystanders are the ones told");
    }

    @Test
    @DisplayName("the record is regrouped off the server tick and kept until it changes")
    void theReadIsOffTheTick() {
        final String hook = code(read(J + "seismic/archive/SeismicArchiveHook.java"));
        final String back = body(hook, "public static <T> void readBack(");
        assertNotNull(back);
        final int copy = back.indexOf(".newestOfEachTarget(");
        final int worker = back.indexOf("READER.execute(");
        final int regroup = back.indexOf("SurveyRecall.of(");
        final int home = back.indexOf("server.execute(");
        assertTrue(copy >= 0 && worker > copy, "the blows are copied on the server thread, then handed over");
        assertTrue(regroup > worker && home > regroup, "the regrouping runs on the worker, the answer on the server");
        final int fin = back.indexOf("finally");
        assertTrue(fin > regroup && home > fin,
                "the answer goes home from a finally: a read that dies of an Error must still free the rig's readingAt");
        final int guard = back.indexOf("if (server.isSameThread())");
        assertTrue(guard > home && back.indexOf("done.accept(result)") > guard,
                "the answer is taken only on the server thread: a stopped server runs what it is handed on the reader's");
        final String reread = body(code(read(BE)), "public void reread(ServerPlayer player,");
        assertTrue(reread.contains("now.same(readBackAt)") && reread.contains("answer(server, player, holds)"),
                "a read-back still good for the record is answered from what was kept");
    }

    @Test
    @DisplayName("the request is registered server-bound under a bumped protocol, and its handler never loads a chunk")
    void theRequestIsRegistered() {
        final String packets = code(read(J + "network/ModPackets.java"));
        assertTrue(packets.contains("registrar.playToServer(SeismicRereadPacket.TYPE, SeismicRereadPacket.STREAM_CODEC, "
                + "SeismicRereadPacket::handle)"), "seismic_reread is not registered: no read-back ever arrives");
        final Matcher v = Pattern.compile("versioned\\(\"(\\d+)\"\\)").matcher(packets);
        assertTrue(v.find() && Integer.parseInt(v.group(1)) >= 59,
                "seismic_reread and the station in seismic_veins need the protocol bumped past 58");
        final String handle = body(code(read(J + "network/SeismicRereadPacket.java")), "public static void handle(");
        assertNotNull(handle);
        final int loaded = handle.indexOf("level.isLoaded(packet.rig())");
        final int be = handle.indexOf("level.getBlockEntity(packet.rig())");
        assertTrue(loaded >= 0 && be > loaded, "a request must never load the chunk it names");
        assertTrue(handle.contains("rig.reread(player, packet.held())"), "the rig decides, with its own checks");
    }

    @Test
    @DisplayName("the client asks only from a resting look at the recorder, and only when the rule says it lacks")
    void theClientAsksOnlyFromTheRecorder() {
        final String reveals = code(read(J + "seismic/client/SeismicReveals.java"));
        final String send = "PacketDistributor.sendToServer(new SeismicRereadPacket(";
        assertEquals(1, count(reveals, send), "one place sends the request");
        final String ask = body(reveals, "private static void maybeAsk(");
        assertNotNull(ask);
        assertTrue(ask.contains(send) && ask.indexOf("SurveyReread.shouldAsk(") >= 0
                        && ask.indexOf("SurveyReread.shouldAsk(") < ask.indexOf(send),
                "the request goes only after the rule says this client lacks something");
        assertEquals(1, count(reveals, "maybeAsk(rig, now)"), "one caller");
        final String look = body(reveals, "private static void look(");
        assertNotNull(look);
        final int dwell = look.indexOf("++dwellTicks < DWELL_TICKS");
        final int gate = look.indexOf("if (onRecorder)");
        final int call = look.indexOf("maybeAsk(rig, now)");
        assertTrue(dwell >= 0 && gate > dwell && call > gate,
                "the ask comes only after the look has rested, and only on the recorder itself");
    }

    @Test
    @DisplayName("a map is keyed by the station its packet names, which the rig puts on every packet")
    void theStationTravels() {
        final String be = code(read(BE));
        final String packets = body(be, "static List<SeismicVeinsPacket> packets(");
        assertNotNull(packets);
        final int made = count(packets, "new SeismicVeinsPacket(");
        assertTrue(made > 0 && made == count(packets, "new SeismicVeinsPacket(rig, at, station,"),
                "every packet names its station, the empty opening one too");
        final String send = body(be, "private void sendVeins(");
        assertTrue(send != null && send.contains("survey.target().id()"), "a live survey's packets name its target");
        final String target = body(code(read(J + "seismic/client/SeismicReveals.java")), "private static String targetOf(");
        assertNotNull(target);
        final int station = target.indexOf("packet.station()");
        final int tuning = target.indexOf("be.tunedStation()");
        assertTrue(station >= 0 && (tuning < 0 || station < tuning),
                "the client reads the packet's station first, never the rig's tuning, which may have changed since");
        final String accept = body(code(read(J + "seismic/client/SeismicReveals.java")), "public static void accept(");
        assertNotNull(accept);
        assertTrue(accept.contains("SurveyReread.route(held == null ? SurveyReread.NEVER : held.impactAt,")
                        && accept.contains("case DROP -> {\n                return;"),
                "the client stores a packet by the pinned rule: a read-back never replaces a witness's own map");
    }

    @Test
    @DisplayName("on a Sable craft the client measures from where the rig stands: at-the-rig, earshot and the shake")
    void theClientMeasuresInTheWorld() {
        final String client = code(read(J + "seismic/client/SeismicClient.java"));
        final String middle = body(client, "static Vec3 middle(SeismicScannerBE be)");
        assertNotNull(middle);
        assertTrue(middle.contains("SableBridge.worldPointOf(be,"),
                "the rig's middle must be projected off a craft's plot, twenty million blocks out");
        final String tick = body(client, "public static void tick(SeismicScannerBE be)");
        assertNotNull(tick);
        assertTrue(tick.contains("final double away = away(be);")
                        && tick.contains("if (away <= SeismicScannerBE.WITNESS_RADIUS * SeismicScannerBE.WITNESS_RADIUS)")
                        && tick.indexOf("SeismicReveals.atRig(be, now)") > tick.indexOf("WITNESS_RADIUS"),
                "the recorder may be read only at the rig, by the server's own measure, so an ask is one it answers");
        final String blow = body(client, "private static void blow(SeismicScannerBE be, long now, boolean near)");
        assertNotNull(blow);
        assertTrue(blow.contains("SeismicShake.blow(be, be.blowStrength())") && !blow.contains("SeismicShake.blow(pos"),
                "the shake is felt from the plate in the world: from the plot it shook nobody on deck");
        final String shake = body(code(read(J + "seismic/client/SeismicShake.java")),
                "public static void blow(SeismicScannerBE be, float blow)");
        assertNotNull(shake);
        assertTrue(shake.contains("SableBridge.worldPointOf(be,") && shake.contains("if (plate != null)"),
                "the shake projects the plate, and an unreadable pose shakes nobody");
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) {
            n++;
        }
        return n;
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String body(String code, String signature) {
        final int at = code.indexOf(signature);
        if (at < 0) {
            return null;
        }
        final int open = code.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(code, i, c);
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        return null;
    }

    private static String code(String s) {
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                final int close = skipLiteral(s, i, c);
                out.append(s, i, Math.min(s.length(), close + 1));
                i = close;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                final int nl = s.indexOf('\n', i);
                i = nl < 0 ? s.length() : nl - 1;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                final int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 1;
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int skipLiteral(String s, int start, char quote) {
        for (int i = start + 1; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return s.length();
    }
}
