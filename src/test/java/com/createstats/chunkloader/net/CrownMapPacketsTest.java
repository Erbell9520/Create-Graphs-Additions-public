package com.createstats.chunkloader.net;

import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownPicks;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.map.CrownSampleShade;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownMapPacketsTest {
    private static final BlockPos CROWN = new BlockPos(158, 64, -382);

    @Test
    @DisplayName("crown_map_request round trips")
    void request() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CrownMapRequestPacket.STREAM_CODEC.encode(buf, new CrownMapRequestPacket(CROWN, -16, -48, 64));
        assertEquals(new CrownMapRequestPacket(CROWN, -16, -48, 64), CrownMapRequestPacket.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    @DisplayName("crown_map_data round trips samples, guesses, other crowns and the law")
    void data() {
        final long[] chunks = {CrownRegion.pack(9, -24), CrownRegion.pack(-17, 3)};
        final byte[] samples = new byte[2 * CrownSampleShade.PER_CHUNK];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (byte) (i * 7);
        }
        final byte[] guesses = new byte[6];
        for (int i = 0; i < guesses.length; i++) {
            guesses[i] = (byte) (13 + i);
        }
        final CrownLaw law = new CrownLaw(2_048, 15, 20, 40, 7);
        final CrownMapDataPacket sent = new CrownMapDataPacket(CROWN, law, chunks, samples, -16, -48, 3, 2, guesses,
                new long[] {CrownRegion.pack(33, -20), CrownRegion.pack(34, -19)});
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CrownMapDataPacket.STREAM_CODEC.encode(buf, sent);
        final int size = buf.readableBytes();
        final CrownMapDataPacket got = CrownMapDataPacket.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(CROWN, got.crown());
        assertEquals(law, got.law());
        assertArrayEquals(chunks, got.sampleChunks());
        assertArrayEquals(samples, got.samples());
        assertEquals(-16, got.guessX());
        assertEquals(-48, got.guessZ());
        assertEquals(3, got.guessW());
        assertEquals(2, got.guessH());
        assertArrayEquals(guesses, got.guesses());
        assertArrayEquals(sent.others(), got.others());
        assertTrue(size < 2 * (8 + CrownSampleShade.PER_CHUNK) + 64, "about the payload and no more: " + size);
    }

    @Test
    @DisplayName("a full slice is 64 KiB of samples and its keys: far under vanilla's 1 MiB")
    void sliceSize() {
        final int n = CrownMapDataPacket.SLICE;
        final CrownMapDataPacket p = new CrownMapDataPacket(CROWN, CrownLaw.DEFAULT, new long[n],
                new byte[n * CrownSampleShade.PER_CHUNK], 0, 0, 0, 0, new byte[0], new long[0]);
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CrownMapDataPacket.STREAM_CODEC.encode(buf, p);
        assertEquals(n * (8 + CrownSampleShade.PER_CHUNK), buf.readableBytes(), 64);
        assertTrue(buf.readableBytes() < (1 << 20) / 8);
    }

    @Test
    @DisplayName("crown_map_data refuses a count past the whole window, and a sample array that does not match its chunks")
    void dataRefusesBadCounts() {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(CROWN);
        for (int i = 0; i < 5; i++) {
            buf.writeVarInt(1);
        }
        buf.writeVarInt(1_000_000);
        assertThrows(IllegalArgumentException.class, () -> CrownMapDataPacket.STREAM_CODEC.decode(buf));
        assertThrows(IllegalArgumentException.class, () -> new CrownMapDataPacket(CROWN, CrownLaw.DEFAULT,
                new long[1], new byte[3], 0, 0, 0, 0, new byte[0], new long[0]));
    }

    @Test
    @DisplayName("crown_pick round trips its direction and its chunks in order, and refuses a wild count")
    void pick() {
        final long[] chunks = {CrownRegion.pack(12, -25), CrownRegion.pack(12, -24), CrownRegion.pack(12, -23)};
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CrownPickPacket.STREAM_CODEC.encode(buf, new CrownPickPacket(CROWN, true, chunks));
        final CrownPickPacket got = CrownPickPacket.STREAM_CODEC.decode(buf);
        assertEquals(CROWN, got.crown());
        assertTrue(got.drop());
        assertArrayEquals(chunks, got.chunks());
        final FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
        bad.writeBlockPos(CROWN);
        bad.writeBoolean(false);
        bad.writeVarInt(CrownPickPacket.MAX + 1);
        assertThrows(IllegalArgumentException.class, () -> CrownPickPacket.STREAM_CODEC.decode(bad));
    }

    @Test
    @DisplayName("crown_pick_reply carries verdict ordinals, the contract, and reads an unknown one as a refusal")
    void reply() {
        final List<CrownPicks.Verdict> v = List.of(CrownPicks.Verdict.PICK, CrownPicks.Verdict.PICK,
                CrownPicks.Verdict.BOARD_SUPPLY);
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CrownPickReplyPacket.STREAM_CODEC.encode(buf, CrownPickReplyPacket.of(CROWN, false, 3, v,
                CrownRegion.pack(12, -23)));
        final CrownPickReplyPacket got = CrownPickReplyPacket.STREAM_CODEC.decode(buf);
        assertEquals(v, got.verdictList());
        assertEquals(3, got.asked());
        assertEquals(CrownRegion.pack(12, -23), got.refusedAt());
        assertEquals(11, got.verdicts()[2], "BOARD_SUPPLY is ordinal 11");
        final CrownPickReplyPacket future = new CrownPickReplyPacket(CROWN, false, 1, new byte[] {42}, 0L);
        assertEquals(List.of(CrownPicks.Verdict.STATION_DARK_FOR_GOOD), future.verdictList());
    }
}
