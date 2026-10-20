package com.createstats.tuner;

import com.createstats.network.TunerApplyPacket;
import com.createstats.network.TunerStationsPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TunerWiringTest {
    private static final Path PACKETS = Path.of("src/main/java/com/createstats/network/ModPackets.java");
    private static final Path APPLY = Path.of("src/main/java/com/createstats/network/TunerApplyPacket.java");
    private static final Path SURVEY = Path.of("src/main/java/com/createstats/network/TunerSurveyPacket.java");

    private static String code(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "method not found, so this test now guards nothing: " + signature);
        final int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces after " + signature);
    }

    private static boolean registered(String source, String direction, String packet) {
        final Matcher m = Pattern.compile("registrar\\.(\\w+)\\(([^;]*);").matcher(source);
        while (m.find()) {
            if (m.group(1).equals(direction) && m.group(2).contains(packet + ".TYPE")
                    && m.group(2).contains(packet + ".STREAM_CODEC") && m.group(2).contains(packet + "::handle")) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyTunerPacketIsRegisteredInItsOwnDirection() throws IOException {
        final String src = code(PACKETS);
        assertTrue(registered(src, "playToServer", "TunerSurveyPacket"), "TunerSurveyPacket, client to server");
        assertTrue(registered(src, "playToClient", "TunerStationsPacket"), "TunerStationsPacket, server to client");
        assertTrue(registered(src, "playToServer", "TunerApplyPacket"), "TunerApplyPacket, client to server");
    }

    @Test
    void bothServerHandlersCheckReachAndTheApplyClamps() throws IOException {
        final String apply = body(code(APPLY), "public static void handle(");
        assertTrue(apply.contains("reach(player, packet.pos)"), apply);
        assertTrue(apply.contains("tunerRange().clamp(packet.range)"), apply);
        final String survey = body(code(SURVEY), "public static void handle(");
        assertTrue(survey.contains("TunerApplyPacket.reach(player, packet.pos)"), survey);
    }

    @Test
    void theStationsCodecReadsBackWhatItWrote() {
        final List<TunerStation> stations = List.of(
                new TunerStation("minecraft:zombie", "entity.minecraft.zombie", "31.5 kHz", "entity:minecraft:zombie",
                        0xFFC8553D, 8, 0, 315),
                new TunerStation("minecraft:phantom", "entity.minecraft.phantom", "58.0 kHz", "entity:minecraft:phantom",
                        0xFF3D8BC8, 8, TunerStation.STYLE_COLUMN, 580));
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        final TunerStationsPacket sent = new TunerStationsPacket(new BlockPos(3, -60, 9), stations);
        TunerStationsPacket.STREAM_CODEC.encode(buf, sent);
        final TunerStationsPacket got = TunerStationsPacket.STREAM_CODEC.decode(buf);
        assertEquals(sent.pos(), got.pos());
        assertEquals(stations, got.stations());
        assertEquals(0, buf.readableBytes(), "the reader must consume exactly what the writer wrote");

        final FriendlyByteBuf b2 = new FriendlyByteBuf(Unpooled.buffer());
        final TunerApplyPacket apply = new TunerApplyPacket(new BlockPos(1, 2, 3), "minecraft:creeper", 48);
        TunerApplyPacket.STREAM_CODEC.encode(b2, apply);
        assertEquals(apply, TunerApplyPacket.STREAM_CODEC.decode(b2));
    }
}
