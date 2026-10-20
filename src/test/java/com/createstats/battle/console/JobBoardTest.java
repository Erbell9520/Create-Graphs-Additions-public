package com.createstats.battle.console;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.GunArc;
import com.createstats.battle.core.GunState;
import com.createstats.battle.core.PlaceName;
import com.createstats.battle.core.Places;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobBoardTest {
    private static final long T = 100_000L;

    private static BattleReport report(String key, boolean powered, String fire, List<BattleReport.GunLine> guns) {
        BattleReport b = BattleReport.empty(key, false, powered);
        return new BattleReport(b.key(), b.ground(), "Skyhook", powered, true, T, -1, T - 100, 3141, 44, 5, 0, 0, false, 0,
                BattleReport.BOW_THRUST, 33, b.options(), 0, b.comparatorMode(), b.systems(), List.of(), T - 80, fire, List.of(),
                List.of(), 1, 1, new byte[0], "", 0, -1L, true, false, BattleReport.TargetLine.NONE, guns, guns.size(), -1L, "");
    }

    private static BattleReport.GunLine gun(int id, GunState st, int mode, int load) {
        return new BattleReport.GunLine(id, "G" + id, st.ordinal(), mode, 1, load, 0f, 0f, 0, 0f, 0f, "", GunArc.FREE,
                BattleReport.GunLine.NO_MOUNT);
    }

    private static DetailView detail(String key, int[] burning, DetailView.Place... places) {
        return new DetailView(key, 1, new int[0], new int[0], new byte[0], burning, List.of(places), T);
    }

    private static DetailView.Place place(int id, String name, int size) {
        return new DetailView.Place(id, name, Places.KIND_DAMAGE, size, 1000 + id);
    }

    private static List<String> keys(JobBoard b) {
        List<String> out = new ArrayList<>();
        for (JobBoard.Job j : b.jobs()) out.add(j.key());
        return out;
    }

    private static int number(JobBoard b, String key) {
        for (JobBoard.Job j : b.jobs()) if (j.key().equals(key)) return j.number();
        throw new AssertionError("no job " + key);
    }

    @Test
    void fireFirstThenGunFaultsThenGunAndPropulsionPlacesThenRepairsLargestFirstThenWhatWaits() {
        String engine = PlaceName.marker("Engine room");
        BattleReport r = report("s:a", true, engine, List.of(
                gun(4, GunState.NO_PITCH_DRIVE, GunState.MODE_FIRE, 1),
                gun(3, GunState.EMPTY, GunState.MODE_TRACK, 0),
                gun(1, GunState.READY, GunState.MODE_FIRE, 1)));
        JobBoard b = new JobBoard();
        b.update(r, detail("s:a", new int[]{7, 8, 9},
                place(1, PlaceName.ship(2, 0, 1), 20),
                place(2, PlaceName.propeller(2), 1),
                place(3, PlaceName.ship(1, 0, 0), 14),
                place(4, PlaceName.envelope(0), 6),
                place(5, engine, 3)), T);
        assertEquals(List.of("fire", "load:3", "gun:4", "place:2", "place:1", "place:3", "place:4", "place:5"), keys(b));
        JobBoard.Job fire = b.jobs().get(0);
        assertEquals(3, fire.count(), "the fire counts its burning blocks");
        assertEquals(7, fire.anchor());
        assertEquals(JobBoard.WAIT, b.jobs().get(7).urgency(), "the burning room's repair waits on the fire");
        for (int i = 0; i < 8; i++) assertEquals(i + 1, b.jobs().get(i).number(), "numbers are handed out in order at first");
    }

    @Test
    void anEmptyGunStaysAJobWhenTheTargetIsClearedAndItReadsIdle() {
        JobBoard b = new JobBoard();
        b.update(report("s:a", true, "", List.of(gun(3, GunState.EMPTY, GunState.MODE_TRACK, 0))), null, T);
        b.update(report("s:a", true, "", List.of(gun(3, GunState.IDLE, GunState.MODE_TRACK, 0))), null, T + 20);
        assertEquals(List.of("load:3"), keys(b));
        assertTrue(b.done(T + 20).isEmpty());
    }

    @Test
    void aNumberStaysWithItsJobAndIsNotReusedWhileAnyJobIsOpen() {
        JobBoard b = new JobBoard();
        DetailView two = detail("s:a", new int[0], place(1, PlaceName.ship(2, 0, 1), 20), place(3, PlaceName.ship(1, 0, 0), 14));
        b.update(report("s:a", true, "", List.of()), two, T);
        assertEquals(1, number(b, "place:1"));
        assertEquals(2, number(b, "place:3"));

        b.update(report("s:a", true, "", List.of()), detail("s:a", new int[0], place(3, PlaceName.ship(1, 0, 0), 14),
                place(4, PlaceName.envelope(1), 30)), T + 20);
        assertEquals(List.of("place:4", "place:3"), keys(b));
        assertEquals(3, number(b, "place:4"));
        assertEquals(2, number(b, "place:3"));

        b.update(report("s:a", true, "", List.of()), detail("s:a", new int[0]), T + 40);
        b.update(report("s:a", true, "", List.of()), detail("s:a", new int[0], place(9, PlaceName.ship(0, 1, 1), 2)), T + 60);
        assertEquals(1, number(b, "place:9"));
    }

    @Test
    void aJobThatLeavesIsStruckThroughForAMinuteNewestFirst() {
        JobBoard b = new JobBoard();
        b.update(report("s:a", true, PlaceName.marker("Engine room"), List.of(gun(3, GunState.EMPTY, GunState.MODE_TRACK, 0))),
                detail("s:a", new int[]{5}, place(1, PlaceName.ship(2, 0, 1), 20)), T);
        b.update(report("s:a", true, "", List.of(gun(3, GunState.SLEWING, GunState.MODE_TRACK, 240))),
                detail("s:a", new int[0], place(1, PlaceName.ship(2, 0, 1), 20)), T + 100);
        List<JobBoard.Done> done = b.done(T + 100);
        assertEquals(2, done.size());
        assertTrue(done.stream().anyMatch(d -> d.kind() == JobBoard.Kind.FIRE), "the fire is out");
        assertTrue(done.stream().anyMatch(d -> d.kind() == JobBoard.Kind.LOAD && d.gunId() == 3), "G3 was loaded");
        assertTrue(b.done(T + 100 + JobBoard.DONE_TICKS + 1).isEmpty(), "gone after a minute");
    }

    @Test
    void nothingCountsAsDoneWithoutPowerWithoutDetailOrOnHold() {
        JobBoard b = new JobBoard();
        DetailView d = detail("s:a", new int[0], place(1, PlaceName.ship(2, 0, 1), 20));
        b.update(report("s:a", true, "", List.of(gun(4, GunState.NO_PITCH_DRIVE, GunState.MODE_FIRE, 1),
                gun(3, GunState.EMPTY, GunState.MODE_TRACK, 0))), d, T);

        b.update(report("s:a", false, "", List.of(gun(4, GunState.NO_POWER, GunState.MODE_FIRE, 1),
                gun(3, GunState.NO_POWER, GunState.MODE_TRACK, 0))), d, T + 20);
        assertEquals(List.of("load:3", "gun:4", "place:1"), keys(b));

        b.update(report("s:a", true, "", List.of(gun(4, GunState.NO_PITCH_DRIVE, GunState.MODE_FIRE, 1),
                gun(3, GunState.EMPTY, GunState.MODE_TRACK, 0))), null, T + 40);
        b.update(report("s:a", true, "", List.of(gun(4, GunState.NO_PITCH_DRIVE, GunState.MODE_FIRE, 1),
                gun(3, GunState.EMPTY, GunState.MODE_TRACK, 0))), detail("s:other", new int[0]), T + 60);
        assertEquals(List.of("load:3", "gun:4", "place:1"), keys(b));

        b.update(report("s:a", true, "", List.of(gun(4, GunState.HOLD, GunState.MODE_HOLD, 1),
                gun(3, GunState.EMPTY, GunState.MODE_TRACK, 0))), d, T + 80);
        assertEquals(List.of("load:3", "place:1"), keys(b));
        assertTrue(b.done(T + 80).isEmpty(), "none of that was a job done");
    }

    @Test
    void anotherShipsReportStartsAClearBoard() {
        JobBoard b = new JobBoard();
        b.update(report("s:a", true, "", List.of()), detail("s:a", new int[0], place(1, PlaceName.ship(2, 0, 1), 20)), T);
        b.update(report("s:b", true, "", List.of()), detail("s:b", new int[0]), T + 20);
        assertTrue(b.jobs().isEmpty());
        assertTrue(b.done(T + 20).isEmpty(), "another ship's places were not patched");
    }

    @Test
    void aSectionThatBrokeAwayWaits() {
        JobBoard b = new JobBoard();
        b.update(report("s:a", true, "", List.of()), detail("s:a", new int[0],
                new DetailView.Place(1, PlaceName.brokeAway(), Places.KIND_BROKE_AWAY, 300, 1),
                place(2, PlaceName.ship(2, 0, 1), 20)), T);
        assertEquals(List.of("place:2", "place:1"), keys(b));
        assertEquals(JobBoard.WAIT, b.jobs().get(1).urgency());
    }
}
