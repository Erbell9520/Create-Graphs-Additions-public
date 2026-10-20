package com.createstats.powertrack.core;

import com.createstats.power.PortableFeDirectionLock;
import com.createstats.powertrack.core.RailTick.CarIn;
import com.createstats.powertrack.core.RailTick.FeederIn;
import com.createstats.powertrack.core.SectionExchange.Feeder;
import com.createstats.powertrack.core.SectionExchange.Role;
import com.createstats.powertrack.core.SectionExchange.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailTickTest {
    private static final int BASE = 8_192;
    private static final int CEILING = RailPower.sectionCeiling(BASE);
    private static final int SHOE = RailPower.collectorRate(BASE);
    private static final int BANK = 1_024_000;

    private static FeederIn supplyOn(int section) {
        return new FeederIn(section, new Feeder(10 + section, Role.SUPPLY, 8_192, 0, 0));
    }

    private static FeederIn collectOn(int section) {
        return new FeederIn(section, new Feeder(20 + section, Role.COLLECT, 0, 8_192, 0));
    }

    private static int sum(int[] values) {
        int s = 0;
        for (int v : values) {
            s += v;
        }
        return s;
    }

    @Test
    @DisplayName("a car across an insulator exchanges with one section: most shoes, then the leading shoe")
    void oneSectionPerCar() {
        assertEquals(0, RailTick.sectionOf(new int[]{0, 1}));
        assertEquals(1, RailTick.sectionOf(new int[]{1, 0}));
        assertEquals(0, RailTick.sectionOf(new int[]{1, 0, 0}));
        assertEquals(1, RailTick.sectionOf(new int[]{-1, 1}));
        assertEquals(-1, RailTick.sectionOf(new int[]{-1, -1}));
        assertEquals(-1, RailTick.sectionOf(new int[0]));
    }

    @Test
    @DisplayName("a car straddling a live and a collecting section is never a wire between them")
    void straddlingIsNotAWire() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        final RailTick.Result r = RailTick.run(List.of(supplyOn(0), collectOn(1)),
                List.of(new CarIn(1L, new int[]{0, 1}, BANK / 2, BANK, true, true)), 2, CEILING, SHOE, lock, 100L);
        assertEquals(0, r.carSection()[0]);

        assertEquals(SHOE, r.carDelta()[0]);
        assertEquals(0, r.feederDelta()[1]);
        assertEquals(0, sum(r.feederDelta()) + sum(r.carDelta()));
    }

    @Test
    @DisplayName("the loop guard: one train charging on one section and discharging on another, refused, this tick and the next")
    void oneDirectionPerTrain() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();

        final List<FeederIn> feeders = List.of(collectOn(0), supplyOn(1));
        final List<CarIn> cars = List.of(
                new CarIn(7L, new int[]{0, 0}, BANK, BANK, true, true),
                new CarIn(7L, new int[]{1, 1}, 0, BANK, true, true));
        final RailTick.Result first = RailTick.run(feeders, cars, 2, CEILING, SHOE, lock, 100L);
        assertEquals(-2 * SHOE, first.carDelta()[0], "section 0 runs first and car A discharges");
        assertEquals(0, first.carDelta()[1], "car B would make the train a wire");
        assertEquals(0, sum(first.feederDelta()) + sum(first.carDelta()));

        final RailTick.Result next = RailTick.run(feeders, cars, 2, CEILING, SHOE, lock, 101L);
        assertEquals(0, next.carDelta()[1], "and not by taking turns on the next tick either");

        final List<CarIn> later = List.of(new CarIn(7L, new int[]{-1, -1}, BANK, BANK, true, true), cars.get(1));
        assertEquals(0, RailTick.run(feeders, later, 2, CEILING, SHOE, lock, 102L).carDelta()[1]);
        assertEquals(2 * SHOE, RailTick.run(feeders, later, 2, CEILING, SHOE, lock, 104L).carDelta()[1]);
    }

    @Test
    @DisplayName("two trains are two ledgers: one charging and one discharging on separate sections is fine")
    void twoTrainsAreIndependent() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        final RailTick.Result r = RailTick.run(List.of(collectOn(0), supplyOn(1)), List.of(
                new CarIn(1L, new int[]{0}, BANK, BANK, true, true),
                new CarIn(2L, new int[]{1}, 0, BANK, true, true)), 2, CEILING, SHOE, lock, 5L);
        assertEquals(-SHOE, r.carDelta()[0]);
        assertEquals(SHOE, r.carDelta()[1]);
    }

    @Test
    @DisplayName("a station interface's claim on a car holds the rail off it the other way")
    void theStationsClaimHolds() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        final RailTick.Result r = RailTick.run(List.of(supplyOn(0)),
                List.of(new CarIn(1L, new int[]{0, 0}, 0, BANK, false, true)), 1, CEILING, SHOE, lock, 5L);
        assertEquals(0, r.carDelta()[0]);
    }

    @Test
    @DisplayName("a train on dead track is reported dead, so the train page can say so")
    void deadTrackIsReported() {
        final PortableFeDirectionLock lock = new PortableFeDirectionLock();
        final RailTick.Result r = RailTick.run(List.of(supplyOn(1)),
                List.of(new CarIn(1L, new int[]{0, 0}, 0, BANK, true, true)), 2, CEILING, SHOE, lock, 5L);
        assertEquals(State.DEAD, r.plans()[0].state());
        assertEquals(0, r.carDelta()[0]);
        assertTrue(r.plans()[1] != null);
    }
}
