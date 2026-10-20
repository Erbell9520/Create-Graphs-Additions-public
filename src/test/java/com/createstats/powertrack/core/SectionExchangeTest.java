package com.createstats.powertrack.core;

import com.createstats.powertrack.core.SectionExchange.Car;
import com.createstats.powertrack.core.SectionExchange.Feeder;
import com.createstats.powertrack.core.SectionExchange.Plan;
import com.createstats.powertrack.core.SectionExchange.Role;
import com.createstats.powertrack.core.SectionExchange.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionExchangeTest {
    private static final int BASE = 8_192;
    private static final int CEILING = RailPower.sectionCeiling(BASE);
    private static final int SHOE = RailPower.collectorRate(BASE);
    private static final int BANK = 1_024_000;

    private static Feeder supply(long grid, int available) {
        return new Feeder(grid, Role.SUPPLY, available, 0, 0);
    }

    private static Feeder collect(long grid, int room, int floor) {
        return new Feeder(grid, Role.COLLECT, 0, room, floor);
    }

    private static Car car(int shoes, int stored) {
        return new Car(1L, shoes, stored, BANK, true, true);
    }

    private static int sum(int[] values) {
        int s = 0;
        for (int v : values) {
            s += v;
        }
        return s;
    }

    private static void conserves(Plan p) {
        assertEquals(0, sum(p.feederDelta()) + sum(p.carDelta()), "the plan made or lost FE");
    }

    @Test
    @DisplayName("a feeder with nothing on its section moves nothing, and a section with no feeder is dead")
    void deadAndIdle() {
        assertEquals(State.DEAD, SectionExchange.plan(List.of(), List.of(car(2, 0)), CEILING, SHOE).state());
        final Plan idle = SectionExchange.plan(List.of(supply(1, 8_192)), List.of(), CEILING, SHOE);
        assertEquals(State.IDLE, idle.state());
        assertEquals(0, idle.railFlow());
    }

    @Test
    @DisplayName("a live rail charges a car at its shoes' rating, never more, and one shoe is half of two")
    void shoesSetTheCarsRate() {
        final Plan two = SectionExchange.plan(List.of(supply(1, 8_192)), List.of(car(2, 0)), CEILING, SHOE);
        assertEquals(State.LIVE, two.state());
        assertArrayEquals(new int[]{2 * SHOE}, two.carDelta());
        assertArrayEquals(new int[]{-2 * SHOE}, two.feederDelta());
        final Plan one = SectionExchange.plan(List.of(supply(1, 8_192)), List.of(car(1, 0)), CEILING, SHOE);
        assertArrayEquals(new int[]{SHOE}, one.carDelta());
        final Plan five = SectionExchange.plan(List.of(supply(1, 8_192)), List.of(car(5, 0)), CEILING, SHOE);
        assertArrayEquals(new int[]{2 * SHOE}, five.carDelta(), "a third shoe carries nothing more");
        conserves(two);
        conserves(one);
        conserves(five);
    }

    @Test
    @DisplayName("ten cars on one section share the ceiling equally, and the rail carries no more than one tray")
    void theCeilingIsShared() {
        final List<Car> cars = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cars.add(car(2, 0));
        }
        final Plan p = SectionExchange.plan(List.of(supply(1, 32_768)), cars, CEILING, SHOE);
        assertEquals(CEILING, p.railFlow());
        assertEquals(10 * 2 * SHOE, p.wanted());
        for (int d : p.carDelta()) {
            assertTrue(d == CEILING / 10 || d == CEILING / 10 + 1, "unfair share " + d);
        }
        conserves(p);
    }

    @Test
    @DisplayName("a nearly full car takes only its room, and the rest goes to the others")
    void roomIsRespected() {
        final Plan p = SectionExchange.plan(List.of(supply(1, 8_192)),
                List.of(car(2, BANK - 100), car(2, 0)), CEILING, SHOE);
        assertArrayEquals(new int[]{100, 2 * SHOE}, p.carDelta());
        conserves(p);
    }

    @Test
    @DisplayName("two supplying feeders share the load between their buffers")
    void twoFeedersShare() {
        final Plan p = SectionExchange.plan(List.of(supply(1, 8_192), supply(2, 8_192)),
                List.of(car(2, 0)), CEILING, SHOE);
        assertArrayEquals(new int[]{-SHOE, -SHOE}, p.feederDelta());
        conserves(p);
    }

    @Test
    @DisplayName("a feeder with less in its buffer than the cars want gives what it has, and no more")
    void supplyBinds() {
        final Plan p = SectionExchange.plan(List.of(supply(1, 700)), List.of(car(2, 0), car(2, 0)), CEILING, SHOE);
        assertEquals(700, p.railFlow());
        assertArrayEquals(new int[]{350, 350}, p.carDelta());
        conserves(p);
    }

    @Test
    @DisplayName("collecting takes from cars down to the highest train floor on the section, into the room there is")
    void collectRespectsTheFloor() {
        final int half = BANK / 2;
        final Plan p = SectionExchange.plan(List.of(collect(1, 8_192, 30), collect(2, 8_192, 50)),
                List.of(car(2, half + 300), car(2, BANK)), CEILING, SHOE);
        assertEquals(State.COLLECTING, p.state());
        assertArrayEquals(new int[]{-300, -2 * SHOE}, p.carDelta());
        conserves(p);
        final Plan full = SectionExchange.plan(List.of(collect(1, 100, 0)), List.of(car(2, BANK)), CEILING, SHOE);
        assertArrayEquals(new int[]{-100}, full.carDelta(), "a pull is sized by what the feeder can take");
        conserves(full);
    }

    @Test
    @DisplayName("on a live rail nothing discharges: a car charges or waits, it never feeds a live rail")
    void liveRailNeverDrainsACar() {
        final Plan p = SectionExchange.plan(List.of(supply(1, 4_000), collect(2, 8_192, 0)),
                List.of(car(2, BANK)), CEILING, SHOE);
        assertEquals(0, p.carDelta()[0]);
        conserves(p);
    }

    @Test
    @DisplayName("a rail between two substations carries FE between them, but never round one circuit")
    void railAsAConductor() {
        final Plan between = SectionExchange.plan(List.of(supply(1, 6_000), collect(2, 8_192, 0)),
                List.of(), CEILING, SHOE);
        assertArrayEquals(new int[]{-6_000, 6_000}, between.feederDelta());
        assertEquals(State.LIVE, between.state());
        final Plan loop = SectionExchange.plan(List.of(supply(1, 6_000), collect(1, 8_192, 0)),
                List.of(), CEILING, SHOE);
        assertArrayEquals(new int[]{0, 0}, loop.feederDelta(), "the same circuit at both ends is a loop");
        assertEquals(State.IDLE, loop.state());
    }

    @Test
    @DisplayName("trains come first: a feeder-to-feeder transfer only gets what the cars left of the ceiling")
    void carsBeforeTransfer() {
        final List<Car> cars = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            cars.add(car(2, 0));
        }
        final Plan p = SectionExchange.plan(List.of(supply(1, 32_768), collect(2, 32_768, 0)), cars, CEILING, SHOE);
        assertEquals(6 * SHOE, sum(p.carDelta()));
        assertEquals(CEILING - 6 * SHOE, p.feederDelta()[1]);
        assertEquals(CEILING, p.railFlow());
        conserves(p);
    }

    @Test
    @DisplayName("a car the loop guard holds is skipped, and the others still charge")
    void refusedCarsAreSkipped() {
        final Plan p = SectionExchange.plan(List.of(supply(1, 8_192)), List.of(
                new Car(9L, 2, 0, BANK, false, true), car(2, 0)), CEILING, SHOE);
        assertArrayEquals(new int[]{0, 2 * SHOE}, p.carDelta());
        conserves(p);
    }

    @Test
    @DisplayName("five thousand random sections: every plan conserves, stays under the ceiling and within every cap")
    void randomSectionsConserve() {
        final Random r = new Random(20261002L);
        for (int run = 0; run < 5_000; run++) {
            final List<Feeder> fs = new ArrayList<>();
            for (int i = r.nextInt(4); i > 0; i--) {
                final Role role = Role.values()[r.nextInt(3)];
                fs.add(new Feeder(r.nextInt(3), role, r.nextInt(9_000), r.nextInt(9_000), r.nextInt(10) * 10));
            }
            final List<Car> cs = new ArrayList<>();
            for (int i = r.nextInt(6); i > 0; i--) {
                final int cap = r.nextBoolean() ? 0 : 1 + r.nextInt(BANK);
                cs.add(new Car(r.nextInt(3), r.nextInt(4), cap == 0 ? 0 : r.nextInt(cap + 1), cap,
                        r.nextInt(5) > 0, r.nextInt(5) > 0));
            }
            final int ceiling = r.nextInt(3) == 0 ? r.nextInt(3_000) : CEILING;
            final Plan p = SectionExchange.plan(fs, cs, ceiling, SHOE);
            conserves(p);
            assertTrue(p.railFlow() <= ceiling, "run " + run + " over the ceiling");
            for (int j = 0; j < cs.size(); j++) {
                final Car c = cs.get(j);
                final int d = p.carDelta()[j];
                assertTrue(Math.abs(d) <= Math.min(2, c.shoes()) * SHOE, "run " + run + " car over its shoes");
                assertTrue(c.stored() + d >= 0 && c.stored() + d <= Math.max(c.capacity(), c.stored()),
                        "run " + run + " car out of its banks");
                assertTrue(d <= 0 || c.mayCharge(), "run " + run + " charged a held car");
                assertTrue(d >= 0 || c.mayDischarge(), "run " + run + " drained a held car");
            }
            for (int i = 0; i < fs.size(); i++) {
                final Feeder f = fs.get(i);
                final int d = p.feederDelta()[i];
                assertTrue(d >= -f.available() || f.role() != Role.SUPPLY, "run " + run + " overdrew a feeder");
                assertTrue(d <= f.room() || f.role() != Role.COLLECT, "run " + run + " overfilled a feeder");
                assertTrue(d == 0 || f.role() != Role.IDLE, "run " + run + " moved an idle feeder");
            }
        }
    }

    @Test
    @DisplayName("a supplier's panel is told what the cars could take even while its buffer is dry, never what they could give (KI-142)")
    void wantedIsTheCarsDemandWhileAnySupplierStands() {
        final List<Car> two = List.of(car(2, 0), car(1, BANK - 300));
        final int demand = 2 * SHOE + 300;

        assertEquals(demand, SectionExchange.plan(List.of(supply(1, 8_192)), two, CEILING, SHOE).wanted());

        final Plan dry = SectionExchange.plan(List.of(supply(1, 0)), two, CEILING, SHOE);
        assertEquals(0, dry.railFlow());
        assertEquals(demand, dry.wanted(), "a dry supplier reported the cars as wanting nothing");

        final Plan mixed = SectionExchange.plan(List.of(supply(1, 0), collect(2, 8_192, 0)),
                List.of(car(2, BANK), car(1, BANK - 300)), CEILING, SHOE);
        assertTrue(mixed.railFlow() > 0, "the cars give to the collector");
        assertEquals(300, mixed.wanted(), "only the car with room could take, 300 FE");

        final Plan collectOnly = SectionExchange.plan(List.of(collect(2, 8_192, 0)), List.of(car(2, 5_000)), CEILING, SHOE);
        assertEquals(2 * SHOE, collectOnly.wanted());
    }

    @Test
    @DisplayName("water-filling is exact and fair")
    void waterFill() {
        assertArrayEquals(new int[]{3, 4, 3}, SectionExchange.waterFill(10, new int[]{3, 5, 4}));
        assertArrayEquals(new int[]{4, 3, 3}, SectionExchange.waterFill(10, new int[]{9, 9, 9}));
        assertArrayEquals(new int[]{1, 0, 0}, SectionExchange.waterFill(1, new int[]{9, 9, 9}));
        assertArrayEquals(new int[]{2, 0, 5}, SectionExchange.waterFill(100, new int[]{2, 0, 5}));
        assertArrayEquals(new int[]{0, 0}, SectionExchange.waterFill(0, new int[]{2, 2}));
    }
}
