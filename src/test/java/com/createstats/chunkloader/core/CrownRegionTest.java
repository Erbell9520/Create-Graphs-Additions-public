package com.createstats.chunkloader.core;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownRegionTest {
    @Test
    @DisplayName("a region is floor(chunk / 32): chunk -1 is in region -1, not 0")
    void regionsFloor() {
        assertEquals(0, CrownRegion.regionOf(0));
        assertEquals(0, CrownRegion.regionOf(31));
        assertEquals(1, CrownRegion.regionOf(32));
        assertEquals(-1, CrownRegion.regionOf(-1));
        assertEquals(-1, CrownRegion.regionOf(-32));
        assertEquals(-2, CrownRegion.regionOf(-33));
        assertEquals(-512, CrownRegion.firstBlock(-1));
        assertEquals(-1, CrownRegion.lastBlock(-1));
        assertEquals(1_535, CrownRegion.lastBlock(2));
        assertTrue(CrownRegion.sameRegion(-1, 5, -32, 31));
        assertFalse(CrownRegion.sameRegion(-1, 5, 0, 5));
    }

    @Test
    @DisplayName("the packing is Minecraft's own ChunkPos.asLong, both ways, negative coordinates included")
    void thePackingIsChunkPos() {
        final int[][] samples = {{0, 0}, {1, -1}, {-1, 1}, {123_456, -654_321}, {-1_875_000, 1_875_000}, {31, -33}};
        for (int[] s : samples) {
            final long ours = CrownRegion.pack(s[0], s[1]);
            assertEquals(ChunkPos.asLong(s[0], s[1]), ours);
            assertEquals(s[0], CrownRegion.x(ours));
            assertEquals(s[1], CrownRegion.z(ours));
            assertEquals(ChunkPos.getX(ours), CrownRegion.x(ours));
            assertEquals(ChunkPos.getZ(ours), CrownRegion.z(ours));
        }
    }

    @Test
    @DisplayName("a Sable plot chunk is one 20.48 million blocks out on both axes")
    void plotChunks() {
        assertTrue(CrownRegion.isPlotChunk(1_280_000, 1_280_000));
        assertTrue(CrownRegion.isPlotChunk(1_280_010, 1_281_000));
        assertFalse(CrownRegion.isPlotChunk(1_279_999, 1_280_000));
        assertFalse(CrownRegion.isPlotChunk(1_280_000, -5));
        assertFalse(CrownRegion.isPlotChunk(0, 0));
    }

    @Test
    @DisplayName("the spiral starts at the station's own chunk, then ring 1 from its north-west corner clockwise")
    void theSpiralOrder() {
        assertEquals(CrownRegion.pack(10, 10), CrownRegion.spiralAt(10, 10, 0));
        assertEquals(CrownRegion.pack(9, 9), CrownRegion.spiralAt(10, 10, 1), "north-west corner");
        assertEquals(CrownRegion.pack(10, 9), CrownRegion.spiralAt(10, 10, 2), "then east along the north edge");
        assertEquals(CrownRegion.pack(11, 9), CrownRegion.spiralAt(10, 10, 3));
        assertEquals(CrownRegion.pack(11, 10), CrownRegion.spiralAt(10, 10, 4), "then south down the east edge");
        assertEquals(CrownRegion.pack(9, 10), CrownRegion.spiralAt(10, 10, 8), "ring 1 ends on the west edge");
        assertEquals(CrownRegion.pack(8, 8), CrownRegion.spiralAt(10, 10, 9), "ring 2 starts at its corner");
        final Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            final long c = CrownRegion.spiralAt(10, 10, i);
            assertTrue(seen.add(c), "no chunk twice");
            assertTrue(Math.abs(CrownRegion.x(c) - 10) <= 2 && Math.abs(CrownRegion.z(c) - 10) <= 2,
                    "the first 25 are the 5 x 5 round it");
        }
    }

    @Test
    @DisplayName("the next pick skips what is picked and what is outside the region, and 25 fit from a corner")
    void theNextPickStaysInTheRegion() {
        final Set<Long> picked = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            final OptionalLong next = CrownRegion.nextInSpiral(0, 0, c -> !picked.contains(c));
            assertTrue(next.isPresent(), "a 32 x 32 region has room for 25 from any corner");
            final long c = next.getAsLong();
            assertTrue(CrownRegion.sameRegion(0, 0, CrownRegion.x(c), CrownRegion.z(c)), "inside the region");
            assertTrue(picked.add(c));
        }
        assertTrue(picked.contains(CrownRegion.pack(0, 0)), "the station's own chunk first");
        for (long c : picked) {
            assertTrue(CrownRegion.x(c) <= 4 && CrownRegion.z(c) <= 4, "within ring 4 of the corner");
        }

        final OptionalLong far = CrownRegion.nextInSpiral(-1, -1, c -> c != CrownRegion.pack(-1, -1));
        assertTrue(far.isPresent());
        assertEquals(-1, CrownRegion.regionOf(CrownRegion.x(far.getAsLong())));
        assertEquals(-1, CrownRegion.regionOf(CrownRegion.z(far.getAsLong())));
        assertTrue(CrownRegion.nextInSpiral(5, 5, c -> false).isEmpty(), "nothing acceptable, nothing picked");
    }
}
