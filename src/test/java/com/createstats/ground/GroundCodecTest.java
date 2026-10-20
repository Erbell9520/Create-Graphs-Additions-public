package com.createstats.ground;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundCodecTest {
    @Test
    void everyFixtureRoundTripsByteForByte() throws IOException {
        for (final GroundFixtures.Named n : GroundFixtures.all()) {
            final byte[] once = GroundCodec.encode(n.snapshot());
            final GroundSnapshot back = GroundCodec.decode(once, null);
            assertArrayEquals(once, GroundCodec.encode(back), n.name());
        }
    }

    @Test
    void everySectionRoundTripsOnItsOwn() throws IOException {
        final GroundSnapshot s = GroundFixtures.roadTrain();

        final GroundSnapshot geometry = GroundCodec.decode(GroundCodec.encode(s, GroundCodec.Section.WHEEL_GEOMETRY.bit()), null);
        for (final GroundCodec.Section sec : GroundCodec.Section.values()) {
            final byte[] packet = GroundCodec.encode(s, sec.bit());
            assertEquals(sec.bit(), GroundCodec.maskOf(packet), sec.name());
            final GroundSnapshot base = sec == GroundCodec.Section.WHEEL_STATE ? geometry : GroundSnapshot.empty(0L);
            final GroundSnapshot back = GroundCodec.decode(packet, base);
            assertArrayEquals(GroundCodec.section(s, sec), GroundCodec.section(back, sec), sec.name());
        }
    }

    @Test
    void aFastPacketKeepsThePreviousSlowSections() throws IOException {
        final GroundSnapshot s = GroundFixtures.car();
        final GroundSnapshot first = GroundCodec.decode(GroundCodec.encode(s), null);
        int fast = 0;
        for (final GroundCodec.Section sec : GroundCodec.Section.values()) {
            if (sec.fast()) {
                fast |= sec.bit();
            }
        }
        final GroundSnapshot next = GroundCodec.decode(GroundCodec.encode(GroundFixtures.braking(), fast), first);
        assertArrayEquals(GroundCodec.section(first, GroundCodec.Section.TRAIL), GroundCodec.section(next, GroundCodec.Section.TRAIL));
        assertArrayEquals(GroundCodec.section(first, GroundCodec.Section.SHAPES), GroundCodec.section(next, GroundCodec.Section.SHAPES));
        assertEquals(GroundFixtures.braking().body().forwardSpeed(), next.body().forwardSpeed(), 1e-6);
    }

    @Test
    void aWheelsTwoHalvesJoinAgain() throws IOException {
        final GroundSnapshot s = GroundFixtures.braking();
        final GroundSnapshot geometryOnly = GroundCodec.decode(GroundCodec.encode(s, GroundCodec.Section.WHEEL_GEOMETRY.bit()), null);
        final GroundSnapshot joined = GroundCodec.decode(GroundCodec.encode(s, GroundCodec.Section.WHEEL_STATE.bit()),
                geometryOnly);
        assertEquals(s.wheels().size(), joined.wheels().size());
        for (int i = 0; i < s.wheels().size(); i++) {
            assertEquals(s.wheels().get(i), joined.wheels().get(i), "wheel " + i);
        }
    }

    @Test
    void geometryWithNoStateYetReadsUnknownNotAtRest() throws IOException {
        final GroundSnapshot s = GroundFixtures.car();
        final GroundSnapshot g = GroundCodec.decode(GroundCodec.encode(s, GroundCodec.Section.WHEEL_GEOMETRY.bit()), null);
        assertTrue(Float.isNaN(g.wheels().get(0).slip()));
        assertTrue(Float.isNaN(g.wheels().get(0).grip()));
    }

    @Test
    void aTruncatedPacketIsRefused() {
        final byte[] full = GroundCodec.encode(GroundFixtures.car());
        for (final int cut : new int[]{3, 12, full.length / 2, full.length - 1}) {
            assertThrows(IOException.class, () -> GroundCodec.decode(Arrays.copyOf(full, cut), null), "cut at " + cut);
        }
    }

    @Test
    void anotherVersionIsRefused() {
        final byte[] full = GroundCodec.encode(GroundFixtures.car());
        full[0] = (byte) (GroundCodec.VERSION + 1);
        assertThrows(IOException.class, () -> GroundCodec.decode(full, null));
        assertEquals(0, GroundCodec.maskOf(full));
    }

    @Test
    void aSectionThatReadsShortFailsItsPacket() {
        final Map<GroundCodec.Section, byte[]> m = new EnumMap<>(GroundCodec.Section.class);
        final byte[] body = GroundCodec.section(GroundFixtures.car(), GroundCodec.Section.BODY);
        m.put(GroundCodec.Section.BODY, Arrays.copyOf(body, body.length + 3));
        assertThrows(IOException.class, () -> GroundCodec.decode(GroundCodec.join(m), null));
    }

    @Test
    void theHistorySurvivesItsQuantisation() throws IOException {
        final GroundSnapshot s = GroundFixtures.car();
        final GroundSnapshot back = GroundCodec.decode(GroundCodec.encode(s), null);
        final float[] a = s.history().speed();
        final float[] b = back.history().speed();
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 0.006f, "speed sample " + i);
        }
    }

    @Test
    void aTrailFarFromTheOriginKeepsItsPrecision() throws IOException {
        final GroundSnapshot s = GroundFixtures.car();
        final GroundSnapshot back = GroundCodec.decode(GroundCodec.encode(s), null);
        final GroundSnapshot.Fix a = s.trail().fixes().get(s.trail().fixes().size() - 1);
        final GroundSnapshot.Fix b = back.trail().fixes().get(back.trail().fixes().size() - 1);
        assertEquals(a.position().x(), b.position().x(), 0.01);
        assertEquals(a.position().z(), b.position().z(), 0.01);
        assertEquals(a.gameTime(), b.gameTime());
    }

    @Test
    void thePlannerSendsASlowSectionOnlyWhenItChanged() throws IOException {
        final GroundFeedPlanner p = new GroundFeedPlanner();
        final GroundSnapshot s = GroundFixtures.car();
        final int first = GroundCodec.maskOf(p.next(s, 1000L));
        assertEquals(GroundCodec.ALL, first, "the first packet is complete");
        final int second = GroundCodec.maskOf(p.next(s, 1004L));
        for (final GroundCodec.Section sec : GroundCodec.Section.values()) {
            assertEquals(sec.fast(), (second & sec.bit()) != 0, sec.name());
        }
        final int refresh = GroundCodec.maskOf(p.next(s, 1000L + GroundFeedPlanner.REFRESH_TICKS));
        assertEquals(GroundCodec.ALL, refresh, "a full resend every refresh");
    }
}
