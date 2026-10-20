package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationSurveyTest {
    private static final FrequencyTable TABLE = FrequencyTable.build(
            Map.of("minecraft:zombie", 31.5, "minecraft:skeleton", 34.7, "minecraft:witch", 37.1,
                    "minecraft:phantom", 49.1, "minecraft:bat", 64.0),
            List.of());

    @Test
    void theSamplesAreCappedNearestFirstAndInsideTheSphere() {
        final List<int[]> s = StationSurvey.samples(64);

        assertEquals(585, s.size());
        assertTrue(s.size() <= StationSurvey.MAX_SAMPLES);
        assertEquals(0, s.get(0)[0] * s.get(0)[0] + s.get(0)[1] * s.get(0)[1] + s.get(0)[2] * s.get(0)[2],
                "the repeller's own block is sampled first");
        int prev = 0;
        for (int[] p : s) {
            final int d2 = p[0] * p[0] + p[1] * p[1] + p[2] * p[2];
            assertTrue(d2 <= 64 * 64, "outside the sphere");
            assertTrue(d2 >= prev, "not nearest first");
            prev = d2;
        }
        assertEquals(33, StationSurvey.samples(8).size());
        assertEquals(137, StationSurvey.samples(16).size());
    }

    @Test
    void aMobIsOfferedFromTheSmallestNotchThatReachesIt() {
        assertEquals(8, StationSurvey.notchReaching(0.0));
        assertEquals(8, StationSurvey.notchReaching(8.0));
        assertEquals(12, StationSurvey.notchReaching(8.01));
        assertEquals(24, StationSurvey.notchReaching(21.3));
        assertEquals(64, StationSurvey.notchReaching(64.0));
        assertEquals(0, StationSurvey.notchReaching(64.5), "past the largest radius, never offered");
    }

    @Test
    void eachMobKeepsItsNearestSightingInFrequencyOrder() {
        final List<StationSurvey.Station> st = StationSurvey.merge(List.of(
                new StationSurvey.Sighting("minecraft:skeleton", 30.0),
                new StationSurvey.Sighting("minecraft:zombie", 50.0),
                new StationSurvey.Sighting("minecraft:skeleton", 9.0),
                new StationSurvey.Sighting("minecraft:witch", 70.0),
                new StationSurvey.Sighting("minecraft:phantom", 0.0)),
                TABLE, null, Set.of("minecraft:phantom"));
        assertEquals(List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:phantom"),
                st.stream().map(StationSurvey.Station::id).toList(), "the witch hut is past 64 blocks");
        assertEquals(12, st.get(1).minRadius(), "the nearest skeleton wins");
        assertEquals(52, st.get(0).minRadius());
        assertTrue(st.get(2).column());
        assertFalse(st.get(0).column());
        assertEquals(315, st.get(0).tenths());
    }

    @Test
    void theTunedStationNeverVanishesFromItsOwnDial() {
        final List<StationSurvey.Station> st = StationSurvey.merge(
                List.of(new StationSurvey.Sighting("minecraft:zombie", 4.0)), TABLE, "minecraft:witch", Set.of());
        assertEquals(2, st.size());
        final StationSurvey.Station witch = st.get(1);
        assertEquals("minecraft:witch", witch.id());
        assertFalse(witch.seen(), "kept, and flagged as not seen in range");
        assertEquals(List.of("minecraft:zombie", "minecraft:witch"),
                StationSurvey.inRange(st, 8, "minecraft:witch").stream().map(StationSurvey.Station::id).toList());
        assertEquals(List.of("minecraft:zombie"),
                StationSurvey.inRange(st, 8, null).stream().map(StationSurvey.Station::id).toList());
    }

    @Test
    void scrollingOutBringsMobsOntoTheDial() {
        final List<StationSurvey.Station> st = StationSurvey.merge(List.of(
                new StationSurvey.Sighting("minecraft:zombie", 5.0),
                new StationSurvey.Sighting("minecraft:bat", 40.0)), TABLE, null, Set.of());
        assertEquals(1, StationSurvey.inRange(st, 36, null).size());
        assertEquals(2, StationSurvey.inRange(st, 40, null).size());
        assertTrue(StationSurvey.inRange(StationSurvey.merge(List.of(), TABLE, null, Set.of()), 64, null).isEmpty(),
                "nothing spawns here is an empty dial, which the ring says in words");
    }
}
