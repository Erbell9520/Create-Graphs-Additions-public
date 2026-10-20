package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveCodecTest {
    private static SeismicArchive site() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        g.strike(a, SiteGround.OWNER_IRON_RIG, "c:ores/iron", 64, 2_395_242L);
        g.strike(a, SiteGround.OWNER_DIAMOND_RIG, "c:ores/diamond", 64, 2_395_087L);
        g.strike(a, SiteGround.DEEP_RIG, "c:ores/diamond", 64, 2_400_000L);
        g.strike(a, SiteGround.OWNER_IRON_RIG, SeismicCatalog.VOIDS, 64, 2_401_000L);
        g.strike(a, SiteGround.DEEP_RIG, SeismicCatalog.LIQUIDS, 48, 2_402_000L);
        final long[] holes = ArchiveBlow.holes(185, -340, 48, (cx, cz) -> cx != 12);
        g.strike(a, SiteGround.EAST_RIG, "c:ores/copper", 48, 2_403_000L, holes[0], holes[1], 9);
        return a;
    }

    @Test
    @DisplayName("a round trip keeps every blow, flag and cell")
    void roundTrip() throws IOException {
        final SeismicArchive a = site();
        final byte[] bytes = ArchiveCodec.encode(a);
        final SeismicArchive b = ArchiveCodec.decode(bytes);
        assertEquals(a.blows().size(), b.blows().size());
        assertEquals(a.cellTotal(), b.cellTotal());
        for (int i = 0; i < a.blows().size(); i++) {
            final ArchiveBlow p = a.blows().get(i);
            final ArchiveBlow q = b.blows().get(i);
            assertEquals(p.id, q.id);
            assertEquals(p.x, q.x);
            assertEquals(p.y, q.y);
            assertEquals(p.z, q.z);
            assertEquals(p.target, q.target);
            assertEquals(p.reach, q.reach);
            assertEquals(p.at, q.at);
            assertEquals(p.floor, q.floor);
            assertEquals(p.holeLo, q.holeLo);
            assertEquals(p.holeHi, q.holeHi);
            assertEquals((float) p.front(), (float) q.front());
            assertEquals((float) p.recorded(), (float) q.recorded());
            assertEquals(p.full(), q.full());
            assertTrue(q.done(), "no survey survives a restart: every loaded blow is closed");
            assertEquals(p.veinsFound(), q.veinsFound());
            assertEquals(p.blocksFound(), q.blocksFound());
            assertEquals(p.nearestFound(), q.nearestFound());
            assertArrayEquals(p.cells(), q.cells());
            assertEquals(p.surfaceX0(), q.surfaceX0());
            assertEquals(p.surfaceZ0(), q.surfaceZ0());
            assertEquals(p.surfaceN(), q.surfaceN());
            assertArrayEquals(p.surface(), q.surface());
            assertTrue(p.surfaceN() >= 11, "a bowl records its surface: " + p.surfaceN());
        }
        final ArchiveBlow next = b.begin(0, 64, 0, "c:ores/gold", 32, 2_500_000L, -64, 0L, 0L);
        assertTrue(next.id > a.blows().get(a.blows().size() - 1).id, "ids carry on past the saved ones");
    }

    @Test
    @DisplayName("cells cost about a byte each in the save")
    void compact() {
        final SeismicArchive a = site();
        final byte[] bytes = ArchiveCodec.encode(a);
        assertTrue(bytes.length < a.cellTotal() * 1.6 + 4_096, bytes.length + " bytes for " + a.cellTotal() + " cells");
    }

    @Test
    @DisplayName("a damaged, truncated or foreign save is refused")
    void damage() {
        final byte[] good = ArchiveCodec.encode(site());
        final byte[] flipped = good.clone();
        flipped[good.length / 2] ^= 0x10;
        assertThrows(IOException.class, () -> ArchiveCodec.decode(flipped));
        assertThrows(IOException.class, () -> ArchiveCodec.decode(Arrays.copyOf(good, good.length - 7)));
        assertThrows(IOException.class, () -> ArchiveCodec.decode(new byte[] {1, 2, 3}));
        assertThrows(IOException.class, () -> ArchiveCodec.decode(null));

        final ArchiveCodec.Out out = new ArchiveCodec.Out();
        out.int32(ArchiveCodec.MAGIC);
        out.byte8(ArchiveCodec.VERSION);
        out.varint(1);
        out.varint(0);
        out.varint(Integer.MAX_VALUE);
        final byte[] body = out.toByteArray();
        final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(body);
        final byte[] forged = Arrays.copyOf(body, body.length + 4);
        final int c = (int) crc.getValue();
        forged[body.length] = (byte) (c >>> 24);
        forged[body.length + 1] = (byte) (c >>> 16);
        forged[body.length + 2] = (byte) (c >>> 8);
        forged[body.length + 3] = (byte) c;
        assertThrows(IOException.class, () -> ArchiveCodec.decode(forged));
    }

    @Test
    @DisplayName("an empty archive round trips")
    void empty() throws IOException {
        final SeismicArchive b = ArchiveCodec.decode(ArchiveCodec.encode(new SeismicArchive()));
        assertEquals(0, b.blows().size());
        assertEquals(0L, b.cellTotal());
    }
}
