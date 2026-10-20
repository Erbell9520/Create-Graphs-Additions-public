package com.createstats.aerial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.createstats.aerial.AerialSnapshot.Fix;
import com.createstats.aerial.AerialSnapshot.Trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialCodecTest {
    @Test
    void aFullSnapshotRoundTripsExactly() throws IOException {
        final AerialSnapshot s = AerialFixtures.cruise();
        final byte[] bytes = AerialCodec.encode(s);
        final AerialSnapshot back = AerialCodec.decode(bytes, null);

        assertArrayEquals(bytes, AerialCodec.encode(back));
        assertEquals(s.name(), back.name());
        assertEquals(s.shipId(), back.shipId());
        assertEquals(s.body().position(), back.body().position());
        assertEquals(s.forces().size(), back.forces().size());
        assertEquals(s.propellers().get(2).rpm(), back.propellers().get(2).rpm(), 1.0e-4);
        assertEquals(s.trail().fixes().size(), back.trail().fixes().size());
        assertEquals(s.history().size(), back.history().size());
        assertEquals(s.envelope().topSpeed(), back.envelope().topSpeed(), 1.0e-4);
        assertEquals(s.envelope().bucketWidth(), back.envelope().bucketWidth(), 0.0);
        assertEquals(s.silhouette().cols(), back.silhouette().cols());
        assertArrayEquals(s.silhouette().side(), back.silhouette().side());
        assertArrayEquals(s.terrain().heights(), back.terrain().heights());
        assertEquals(s.route().active(), back.route().active());
        assertEquals(s.target().label(), back.target().label());

        final AirframeReadout a = AirframeMath.read(s);
        final AirframeReadout b = AirframeMath.read(back);
        assertEquals(a.support, b.support, 1.0e-5);
        assertEquals(a.heading, b.heading, 1.0e-5);
        assertEquals(a.ceilingY, b.ceilingY, 0.05);
        assertEquals(a.topSpeed, b.topSpeed, 1.0e-3);
    }

    @Test
    void aDeltaKeepsTheSectionsItDoesNotCarry() throws IOException {
        final AerialSnapshot full = AerialFixtures.cruise();
        final AerialSnapshot base = AerialCodec.decode(AerialCodec.encode(full), null);
        final AerialSnapshot moved = full.withBody(new AerialSnapshot.Body(full.body().position().plus(new V3(5, 0, 0)),
                full.body().orientation(), full.body().nose(), full.body().velocity(), full.body().spin()));
        int fast = 0;
        for (final AerialCodec.Section sec : AerialCodec.Section.values()) {
            if (sec.fast()) {
                fast |= sec.bit();
            }
        }
        final AerialSnapshot merged = AerialCodec.decode(AerialCodec.encode(moved, fast), base);
        assertEquals(moved.body().position(), merged.body().position(), "the fast section is replaced");
        assertEquals(base.trail().fixes().size(), merged.trail().fixes().size(), "the trail is kept");
        assertArrayEquals(base.terrain().heights(), merged.terrain().heights(), "the terrain is kept");
        assertEquals(base.envelope().topSpeed(), merged.envelope().topSpeed(), 0.0);
    }

    @Test
    void aPacketFromAnotherVersionOrOneThatReadsWrongIsRefused() {
        final byte[] good = AerialCodec.encode(AerialFixtures.cruise());
        final byte[] version = good.clone();
        version[0] = (byte) (AerialCodec.VERSION + 1);
        assertThrows(IOException.class, () -> AerialCodec.decode(version, null));
        assertThrows(IOException.class, () -> AerialCodec.decode(Arrays.copyOf(good, good.length - 3), null));
        assertThrows(IOException.class, () -> AerialCodec.decode(new byte[2], null));

        final byte[] trailing = Arrays.copyOf(good, good.length + 4);
        assertThrows(IOException.class, () -> AerialCodec.decode(trailing, null));
        final byte[] lying = good.clone();
        lying[8] = (byte) 0x7F;
        assertThrows(IOException.class, () -> AerialCodec.decode(lying, null));
    }

    @Test
    void unknownStaysUnknownOnTheWire() throws IOException {
        final AerialSnapshot s = new AerialSnapshot(1L, "", null, 0, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        final AerialSnapshot back = AerialCodec.decode(AerialCodec.encode(s), null);
        assertTrue(Double.isNaN(back.mass().mass()));
        assertTrue(Double.isNaN(back.air().pressure()));
        assertTrue(Double.isNaN(back.envelope().topSpeed()));
        assertEquals(null, back.target());
        assertEquals(null, back.docking());
    }

    @Test
    void aTrailFarFromTheOriginKeepsItsDetail() throws IOException {
        final List<Fix> fixes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            fixes.add(new Fix(new V3(29_000_000.0 + i * 0.25, 120.0, -29_000_000.0 - i * 0.125), 1000L + i * 20L));
        }
        final AerialSnapshot s = new AerialSnapshot(3000L, "", null, 0, null, null, null, null, null, null, null, null,
                null, null, null, null, null, new Trail(fixes), null, null, null);
        final AerialSnapshot back = AerialCodec.decode(AerialCodec.encode(s), null);
        for (int i = 0; i < fixes.size(); i++) {
            assertEquals(fixes.get(i).position().x(), back.trail().fixes().get(i).position().x(), 1.0e-3);
            assertEquals(fixes.get(i).position().z(), back.trail().fixes().get(i).position().z(), 1.0e-3);
            assertEquals(fixes.get(i).gameTime(), back.trail().fixes().get(i).gameTime());
        }
    }

    @Test
    void packetsStayWithinTheirBudgets() {
        final AerialSnapshot s = AerialFixtures.cruise();
        final byte[] full = AerialCodec.encode(s);
        int fastMask = 0;
        for (final AerialCodec.Section sec : AerialCodec.Section.values()) {
            if (sec.fast()) {
                fastMask |= sec.bit();
            }
        }
        final byte[] fast = AerialCodec.encode(s, fastMask);

        assertTrue(fast.length < 2_500, "fast packet is " + fast.length + " bytes");
        assertTrue(full.length < 24_000, "full packet is " + full.length + " bytes");
    }

    @Test
    void thePlannerSendsSlowSectionsOnlyWhenTheyChange() throws IOException {
        final AerialFeedPlanner planner = new AerialFeedPlanner();
        final AerialSnapshot s = AerialFixtures.cruise();
        final byte[] first = planner.next(s, 1000L);
        assertEquals(AerialCodec.ALL, AerialCodec.maskOf(first), "the first packet is complete");
        final byte[] second = planner.next(s, 1004L);
        final int mask = AerialCodec.maskOf(second);
        assertTrue((mask & AerialCodec.Section.BODY.bit()) != 0, "fast sections always go");
        assertFalse((mask & AerialCodec.Section.TERRAIN.bit()) != 0, "an unchanged terrain does not");
        assertFalse((mask & AerialCodec.Section.TRAIL.bit()) != 0);

        final List<Fix> more = new ArrayList<>(s.trail().fixes());
        more.add(new Fix(s.body().position(), 1008L));
        final AerialSnapshot extended = s.withRecorded(new Trail(more), s.history(), s.envelope());
        final int third = AerialCodec.maskOf(planner.next(extended, 1008L));
        assertTrue((third & AerialCodec.Section.TRAIL.bit()) != 0);
        assertFalse((third & AerialCodec.Section.HISTORY.bit()) != 0);

        assertEquals(AerialCodec.ALL, AerialCodec.maskOf(planner.next(extended,
                1000L + AerialFeedPlanner.REFRESH_TICKS)));

        AerialSnapshot client = AerialCodec.decode(first, null);
        client = AerialCodec.decode(second, client);
        assertNotNull(client.terrain());
        assertEquals(s.terrain().size(), client.terrain().size());
    }
}
