package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficGapTest {
    private static PathSpline loop100() {
        return PathSpline.of(Pathway.create(UUID.randomUUID(), "t", 0, new UUID(0, 1))
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{25, 64, 0}, new double[]{25, 64, 25}, new double[]{0, 64, 25}))
                .withKind(PathKind.LOOP));
    }

    @Test
    void theRoomRunsFrontToRear() {
        PathSpline sp = loop100();
        TrafficGap.Car[] cars = {new TrafficGap.Car(10, 2, 3, 5), new TrafficGap.Car(30, 2, 3, 5)};
        TrafficGap.Room r = TrafficGap.roomOf(sp, cars, 0);
        assertEquals(1, r.ahead());
        assertEquals(20 - 2 - 3, r.gap(), 1.0e-9);
    }

    @Test
    void aheadWrapsAcrossNodeOne() {
        PathSpline sp = loop100();
        double end = sp.length() - 2.0;
        TrafficGap.Car[] cars = {new TrafficGap.Car(end, 1, 1, 5), new TrafficGap.Car(3.0, 1, 1, 5)};
        TrafficGap.Room r = TrafficGap.roomOf(sp, cars, 0);
        assertEquals(1, r.ahead(), "the car just past node 1 is ahead, not a lap behind");
        assertEquals(5.0 - 2.0, r.gap(), 1.0e-9);

        TrafficGap.Room back = TrafficGap.roomOf(sp, cars, 1);
        assertEquals(0, back.ahead());
        assertTrue(back.gap() > sp.length() - 10.0);
    }

    @Test
    void theCapStopsItAtTheStandoff() {
        assertEquals(0.0, TrafficGap.speedCap(3.0, 3.0, 1.6, 0.0), 1.0e-9);
        assertEquals(Math.sqrt(2 * 1.6 * 5.0), TrafficGap.speedCap(8.0, 3.0, 1.6, 0.0), 1.0e-9);
        assertTrue(TrafficGap.speedCap(8.0, 3.0, 1.6, 6.0) > TrafficGap.speedCap(8.0, 3.0, 1.6, 0.0), "credit for a moving leader");
        assertEquals(Double.POSITIVE_INFINITY, TrafficGap.speedCap(Double.POSITIVE_INFINITY, 3.0, 1.6, 0.0));
    }

    @Test
    void onAOneWayLineNobodyIsAheadOfTheLeader() {
        PathSpline sp = PathSpline.of(Pathway.create(UUID.randomUUID(), "t", 0, new UUID(0, 1))
                .appendAll(List.of(new double[]{0, 64, 0}, new double[]{100, 64, 0})));
        TrafficGap.Car[] cars = {new TrafficGap.Car(80, 1, 1, 5), new TrafficGap.Car(20, 1, 1, 5)};
        assertEquals(-1, TrafficGap.roomOf(sp, cars, 0).ahead());
        assertEquals(0, TrafficGap.roomOf(sp, cars, 1).ahead());
    }
}
