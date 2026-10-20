package com.createstats.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeRulesTest {
    @Test
    @DisplayName("floor heights cluster into storeys, and a step up is not a new floor")
    void storeys() {
        assertEquals(List.of(64, 69), FloorBands.storeys(List.of(64, 64, 65, 69, 69, 70)));
        assertEquals(List.of(60), FloorBands.storeys(List.of(60)));
        assertEquals(List.of(), FloorBands.storeys(List.of()));
    }

    @Test
    @DisplayName("a fixture is on the highest storey at or below its floor, or the lowest when below all")
    void storeyOf() {
        final List<Integer> s = List.of(64, 69, 74);
        assertEquals(0, FloorBands.storeyOf(s, 64));
        assertEquals(0, FloorBands.storeyOf(s, 65));
        assertEquals(1, FloorBands.storeyOf(s, 69));
        assertEquals(2, FloorBands.storeyOf(s, 80));
        assertEquals(0, FloorBands.storeyOf(s, 50));
        assertEquals(-1, FloorBands.storeyOf(List.of(), 64));
    }

    @Test
    @DisplayName("storeys are named from where the panel is, never guessed as ground floor")
    void names() {
        assertEquals(new FloorBands.Name("this", 0), FloorBands.name(1, 1));
        assertEquals(new FloorBands.Name("up_one", 1), FloorBands.name(2, 1));
        assertEquals(new FloorBands.Name("down_one", 1), FloorBands.name(0, 1));
        assertEquals(new FloorBands.Name("up", 3), FloorBands.name(4, 1));
        assertEquals(new FloorBands.Name("down", 2), FloorBands.name(0, 2));
    }

    @Test
    @DisplayName("energy lands in the hour it was used, and the chart ends at the current hour")
    void meterBooks() {
        final DayMeter m = new DayMeter();
        m.add(500, 100);
        m.add(900, 50);
        m.add(1_200, 7);
        final int[] chart = m.chart(1_999);
        assertEquals(7, chart[23]);
        assertEquals(150, chart[22]);
    }

    @Test
    @DisplayName("a night slept through clears the hours nobody lived through, and only those")
    void sleepClearsSkippedHours() {
        final DayMeter m = new DayMeter();
        m.add(0, 10);
        m.add(11_000, 20);
        m.add(24_000 + 2_000, 30);
        final int[] chart = m.chart(26_000);
        assertEquals(30, chart[23]);
        assertEquals(20, chart[8], "hour 11 is fifteen hours ago, still on the day chart");
        long sum = 0;
        for (int v : chart) {
            sum += v;
        }
        assertEquals(50, sum, "hour 0 is twenty-six hours ago: its slot was reused and cleared");
    }

    @Test
    @DisplayName("time set backwards clears everything rather than filing yesterday under today")
    void backwardsClears() {
        final DayMeter m = new DayMeter();
        m.add(10_000, 99);
        m.add(3_000, 1);
        final int[] chart = m.chart(3_000);
        long sum = 0;
        for (int v : chart) {
            sum += v;
        }
        assertEquals(1, sum);
    }

    @Test
    @DisplayName("a total saturates instead of wrapping negative (INV-14)")
    void saturates() {
        final DayMeter m = new DayMeter();
        m.add(0, Long.MAX_VALUE - 5);
        m.add(10, 100);
        assertEquals(Integer.MAX_VALUE, m.chart(10)[23]);
        assertEquals(Long.MAX_VALUE, DayMeter.saturatingAdd(Long.MAX_VALUE - 1, 5));
    }

    @Test
    @DisplayName("a saved meter comes back as it was; a malformed save is ignored, not trusted")
    void loadRoundTrip() {
        final DayMeter a = new DayMeter();
        a.add(4_500, 42);
        final DayMeter b = new DayMeter();
        b.load(a.raw(), a.lastHour());
        assertArrayEquals(a.chart(4_500), b.chart(4_500));
        final DayMeter c = new DayMeter();
        c.load(new long[3], 4);
        assertEquals(-1, c.lastHour());
    }

    @Test
    @DisplayName("the first reading after a load fires nothing: a chunk loading is not an arrival")
    void presenceFirstIsSilent() {
        final Presence p = new Presence();
        assertEquals(Presence.Edge.NONE, p.update(1, 0));
        final Presence q = new Presence();
        assertEquals(Presence.Edge.NONE, q.update(0, 0));
    }

    @Test
    @DisplayName("stepping onto the porch is not leaving")
    void briefStepOut() {
        final Presence p = new Presence();
        p.update(1, 0);
        assertEquals(Presence.Edge.NONE, p.update(0, 20));
        assertEquals(Presence.Edge.NONE, p.update(0, 20 + Presence.LEAVE_TICKS - 1));
        assertEquals(Presence.Edge.NONE, p.update(1, 20 + Presence.LEAVE_TICKS));
        assertTrue(p.isHome());
    }

    @Test
    @DisplayName("empty long enough is the last one out; back soon after is not coming home")
    void leaveAndReturn() {
        final Presence p = new Presence();
        p.update(2, 0);
        p.update(0, 100);
        assertEquals(Presence.Edge.LAST_OUT, p.update(0, 100 + Presence.LEAVE_TICKS));
        assertEquals(Presence.Edge.NONE, p.update(1, 100 + Presence.LEAVE_TICKS + 40),
                "back within a minute: nobody came home, somebody forgot their pick");
        p.update(0, 2_000);
        assertEquals(Presence.Edge.LAST_OUT, p.update(0, 2_000 + Presence.LEAVE_TICKS));
        assertEquals(Presence.Edge.FIRST_HOME,
                p.update(1, 2_000 + Presence.LEAVE_TICKS + Presence.AWAY_TICKS));
    }

    @Test
    @DisplayName("walking into an empty room lights it once; staying does not keep relighting it")
    void followOnEdgeOnly() {
        final FollowPeople f = new FollowPeople();
        assertEquals(FollowPeople.Action.NONE, f.update(false, 0));
        assertEquals(FollowPeople.Action.ON, f.update(true, 20));
        assertEquals(FollowPeople.Action.NONE, f.update(true, 40));
        assertEquals(FollowPeople.Action.NONE, f.update(true, 4_000));
    }

    @Test
    @DisplayName("the room goes dark only after standing empty, and a return in time cancels it")
    void followOffAfterDelay() {
        final FollowPeople f = new FollowPeople();
        f.update(false, 0);
        f.update(true, 20);
        assertEquals(FollowPeople.Action.NONE, f.update(false, 100));
        assertEquals(FollowPeople.Action.NONE, f.update(false, 100 + FollowPeople.OFF_TICKS - 1));
        assertEquals(FollowPeople.Action.ON, f.update(true, 100 + FollowPeople.OFF_TICKS - 1),
                "back before the delay is still an arrival; the lamps are lit, so switching them on is a no-op");
        assertEquals(FollowPeople.Action.NONE, f.update(true, 100 + FollowPeople.OFF_TICKS + 5));
        f.update(false, 2_000);
        assertEquals(FollowPeople.Action.OFF, f.update(false, 2_000 + FollowPeople.OFF_TICKS));
        assertEquals(FollowPeople.Action.NONE, f.update(false, 2_000 + 2 * FollowPeople.OFF_TICKS));
    }

    @Test
    @DisplayName("an arrival in daylight lights nothing and switches nothing off later; the same arrival after dark does both")
    void followOnlyWhenDark() {
        final FollowPeople day = new FollowPeople();
        day.update(false, 0, false);
        assertEquals(FollowPeople.Action.NONE, day.update(true, 20, false), "a sunlit room is not lit on entry");
        assertEquals(FollowPeople.Action.NONE, day.update(true, 40, false), "and the stay is not a second arrival");
        day.update(false, 100, false);
        assertEquals(FollowPeople.Action.NONE, day.update(false, 100 + FollowPeople.OFF_TICKS, false),
                "nothing was lit, so nothing is switched off");
        final FollowPeople night = new FollowPeople();
        night.update(false, 0, true);
        assertEquals(FollowPeople.Action.ON, night.update(true, 20, true));
        night.update(false, 100, true);
        assertEquals(FollowPeople.Action.OFF, night.update(false, 100 + FollowPeople.OFF_TICKS, true));

        final FollowPeople dusk = new FollowPeople();
        dusk.update(false, 0, false);
        dusk.update(true, 20, false);
        assertEquals(FollowPeople.Action.ON, dusk.update(true, 4_000, true), "sunset in an occupied room");
        assertEquals(FollowPeople.Action.NONE, dusk.update(true, 4_020, true), "once");
        dusk.update(false, 5_000, true);
        assertEquals(FollowPeople.Action.OFF, dusk.update(false, 5_000 + FollowPeople.OFF_TICKS, true),
                "what the rule lit at dusk it switches off when the room empties");

        final FollowPeople dawn = new FollowPeople();
        dawn.update(false, 0, true);
        assertEquals(FollowPeople.Action.ON, dawn.update(true, 20, true));
        assertEquals(FollowPeople.Action.NONE, dawn.update(true, 8_000, false), "day breaks, nothing moves");
        assertEquals(FollowPeople.Action.NONE, dawn.update(true, 8_020, false));
    }

    @Test
    @DisplayName("lamps this rule never lit are not its to switch off")
    void followLeavesOthersAlone() {
        final FollowPeople f = new FollowPeople();
        f.update(true, 0);
        f.update(false, 20);
        assertEquals(FollowPeople.Action.NONE, f.update(false, 20 + FollowPeople.OFF_TICKS));
    }

    @Test
    @DisplayName("a scene changes what it would switch, dim or recolour, and nothing else")
    void sceneChanges() {
        final Map<Long, SceneDiff.State> now = Map.of(
                1L, new SceneDiff.State(true, 6, 0),
                2L, new SceneDiff.State(true, 9, -1),
                3L, new SceneDiff.State(false, 4, 3),
                4L, new SceneDiff.State(true, -1, -1));
        final Map<Long, SceneDiff.State> scene = Map.of(
                1L, new SceneDiff.State(true, 1, 0),
                2L, new SceneDiff.State(true, 9, -1),
                3L, new SceneDiff.State(false, 9, 3),
                4L, new SceneDiff.State(false, -1, -1),
                5L, new SceneDiff.State(true, 1, 1));
        assertEquals(java.util.Set.of(1L, 4L), SceneDiff.changes(now, scene));
    }
}
