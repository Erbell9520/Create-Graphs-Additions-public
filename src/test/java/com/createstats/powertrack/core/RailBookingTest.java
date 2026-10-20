package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailBookingTest {
    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) {
            s += v;
        }
        return s;
    }

    @Test
    @DisplayName("in a consistent world settling changes nothing")
    void consistentChangesNothing() {
        final int[] feeders = {-1500, -548};
        final int[] cars = {1024, 1024};
        assertArrayEquals(feeders, RailTick.settle(feeders, cars, cars));
    }

    @Test
    @DisplayName("a car that took less leaves the rest with the feeders that gave it: nothing created, nothing lost")
    void carTookLess() {
        final int[] feeders = {-1500, -548};
        final int[] planned = {1024, 1024};
        final int[] actual = {1024, 300};
        final int[] settled = RailTick.settle(feeders, planned, actual);
        assertEquals(0, sum(settled) + sum(actual));
        assertEquals(-776, settled[0]);
        assertEquals(-548, settled[1]);
    }

    @Test
    @DisplayName("a car that gave less takes the rest back from the feeders that received it")
    void carGaveLess() {
        final int[] feeders = {700, 1348};
        final int[] planned = {-1024, -1024};
        final int[] actual = {-1024, -100};
        final int[] settled = RailTick.settle(feeders, planned, actual);
        assertEquals(0, sum(settled) + sum(actual));
        assertTrue(settled[0] >= 0 && settled[1] >= 0);
    }

    @Test
    @DisplayName("feeder to feeder carry and cars together still conserve after settling")
    void mixed() {
        final int[] feeders = {-3000, 952};
        final int[] planned = {1024, 1024};
        final int[] actual = {1000, 1024};
        final int[] settled = RailTick.settle(feeders, planned, actual);
        assertEquals(0, sum(settled) + sum(actual));
        assertEquals(952, settled[1], "the carry to the other feeder is untouched");
    }

    @Test
    @DisplayName("feeder to feeder over a section: never more than the ceiling or either feeder's dial, and every FE accounted")
    void feederToFeederBounded() {
        final int ceiling = RailPower.sectionCeiling(8192);
        final int shoe = RailPower.collectorRate(8192);
        for (int[] dials : new int[][]{{4096, 2048}, {8192, 8192}, {16384, 32768}, {256, 8192}}) {
            int supplyBuffer = 0;
            int collectBuffer = 0;
            long fromCircuitA = 0;
            long intoCircuitB = 0;
            int car = 0;
            final int carCapacity = 400_000;
            for (int tick = 0; tick < 200; tick++) {
                fromCircuitA += dials[0] - supplyBuffer;
                supplyBuffer = dials[0];
                intoCircuitB += collectBuffer;
                collectBuffer = 0;
                final boolean trainHere = tick % 50 < 20;
                final List<SectionExchange.Feeder> feeders = List.of(
                        new SectionExchange.Feeder(1, SectionExchange.Role.SUPPLY, Math.min(supplyBuffer, dials[0]), 0, 50),
                        new SectionExchange.Feeder(2, SectionExchange.Role.COLLECT, 0, dials[1] - collectBuffer, 50));
                final List<SectionExchange.Car> cars = trainHere
                        ? List.of(new SectionExchange.Car(9, 2, car, carCapacity, true, true)) : List.of();
                final SectionExchange.Plan plan = SectionExchange.plan(feeders, cars, ceiling, shoe);
                assertTrue(plan.railFlow() <= ceiling, "over the section ceiling");
                assertTrue(-plan.feederDelta()[0] <= dials[0], "the supplier gave more than its dial");
                assertTrue(plan.feederDelta()[1] <= dials[1], "the collector took more than its dial");
                supplyBuffer += plan.feederDelta()[0];
                collectBuffer += plan.feederDelta()[1];
                if (!cars.isEmpty()) {
                    car += plan.carDelta()[0];
                }
                assertTrue(supplyBuffer >= 0 && collectBuffer >= 0);
            }

            assertEquals(fromCircuitA, intoCircuitB + car + supplyBuffer + collectBuffer, "dials " + dials[0] + "/" + dials[1]);
        }
    }

    @Test
    @DisplayName("two feeders on one circuit never pass FE between them over the rail")
    void sameCircuitNeverLoops() {
        final List<SectionExchange.Feeder> feeders = List.of(
                new SectionExchange.Feeder(7, SectionExchange.Role.SUPPLY, 8192, 0, 50),
                new SectionExchange.Feeder(7, SectionExchange.Role.COLLECT, 0, 8192, 50));
        final SectionExchange.Plan plan = SectionExchange.plan(feeders, List.of(), 8192, 1024);
        assertEquals(0, plan.railFlow());
        assertArrayEquals(new int[]{0, 0}, plan.feederDelta());
    }

    @Test
    @DisplayName("a section's lamps follow its feeders, never its traffic")
    void lamps() {
        final SectionExchange.Feeder supplying = new SectionExchange.Feeder(1, SectionExchange.Role.SUPPLY, 500, 0, 50);
        final SectionExchange.Feeder dry = new SectionExchange.Feeder(1, SectionExchange.Role.SUPPLY, 0, 0, 50);
        final SectionExchange.Feeder collecting = new SectionExchange.Feeder(2, SectionExchange.Role.COLLECT, 0, 800, 50);
        final SectionExchange.Feeder full = new SectionExchange.Feeder(2, SectionExchange.Role.COLLECT, 0, 0, 50);
        final SectionExchange.Feeder idle = new SectionExchange.Feeder(3, SectionExchange.Role.IDLE, 0, 0, 50);
        assertEquals(SectionExchange.State.DEAD, SectionExchange.energised(List.of()));
        assertEquals(SectionExchange.State.LIVE, SectionExchange.energised(List.of(supplying)));
        assertEquals(SectionExchange.State.LIVE, SectionExchange.energised(List.of(collecting, supplying)));
        assertEquals(SectionExchange.State.COLLECTING, SectionExchange.energised(List.of(collecting, dry)));
        assertEquals(SectionExchange.State.IDLE, SectionExchange.energised(List.of(dry, full, idle)));
    }

    @Test
    @DisplayName("a disagreement on one section lands only in that section's feeders, never in another's (KI-141)")
    void settledSectionBySection() {
        final int[] feeders = {-1024, 1024};
        final int[] feederSection = {0, 1};
        final int[] planned = {1024, -1024};
        final int[] actual = {824, -824};
        final int[] carSection = {0, 1};
        final int[] settled = RailTick.settle(feeders, feederSection, planned, actual, carSection);
        assertEquals(0, sum(settled) + sum(actual));
        assertEquals(-824, settled[0], "section 0's supplier keeps what its car did not take");
        assertEquals(824, settled[1], "section 1's collector gives back what its car did not give");

        final int[] netted = RailTick.settle(feeders, planned, actual);
        assertEquals(-1024, netted[0], "the old netting: a section's supplier short, the FE gone to another section");
        assertEquals(1024, netted[1]);

        final int[] four = {-500, -1024, 300, 700};
        final int[] fourSection = {1, 0, 1, 0};
        final int[] cars = {1024, 500, -300, -700};
        final int[] carsSection = {0, 1, 1, 0};

        final int[] tookLess = {824, 500, -300, -700};
        final int[] a = RailTick.settle(four, fourSection, cars, tookLess, carsSection);
        assertEquals(0, sum(a) + sum(tookLess));
        assertEquals(-500, a[0], "section 1's supplier is not paid back for section 0's car");
        assertEquals(-824, a[1], "section 0's supplier keeps what its car did not take");

        final int[] gaveLess = {1024, 500, -300, -600};
        final int[] b = RailTick.settle(four, fourSection, cars, gaveLess, carsSection);
        assertEquals(0, sum(b) + sum(gaveLess));
        assertEquals(300, b[2], "section 1's collector is not docked for section 0's car");
        assertEquals(600, b[3], "section 0's collector gives back what its car did not give");
    }
}
