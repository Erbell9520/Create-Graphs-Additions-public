package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreatOrderTest {
    private static ThreatOrder.Contact ship(int i, double range, double rate, double approach) {
        return new ThreatOrder.Contact(i, ThreatOrder.Kind.SHIP, false, range, rate, approach, false, false);
    }

    private static ThreatOrder.Contact mob(int i, double range, boolean hostile, boolean targetsCrew) {
        return new ThreatOrder.Contact(i, ThreatOrder.Kind.MOB, false, range, 0, 0, hostile, targetsCrew);
    }

    private static ThreatOrder.Contact player(int i, double range) {
        return new ThreatOrder.Contact(i, ThreatOrder.Kind.PLAYER, false, range, 0, 0, false, false);
    }

    private static List<Integer> indices(List<ThreatOrder.Contact> cs) {
        List<Integer> out = new ArrayList<>();
        for (ThreatOrder.Contact c : cs) out.add(c.index());
        return out;
    }

    @Test
    void theBandsComeInThePlansOrder() {
        List<ThreatOrder.Contact> in = List.of(
                player(0, 10),
                mob(1, 20, false, false),
                ship(2, 300, 1.0, 0),
                mob(3, 40, true, false),
                mob(4, 60, true, true),
                ship(5, 500, -3.0, 50),
                new ThreatOrder.Contact(6, ThreatOrder.Kind.MOB, true, 90, 0, 0, false, false));
        assertEquals(List.of(6, 5, 4, 3, 2, 1, 0), indices(ThreatOrder.order(in)));
    }

    @Test
    void closingShipsSortBySoonestApproachNotRange() {
        List<ThreatOrder.Contact> in = List.of(ship(0, 100, -1.0, 40), ship(1, 400, -20.0, 12));
        assertEquals(List.of(1, 0), indices(ThreatOrder.order(in)));
    }

    @Test
    void aShipDriftingSlowerThanHalfAMetreASecondIsNotClosing() {
        assertEquals(4, ThreatOrder.band(ship(0, 100, -0.4, 5)));
        assertEquals(1, ThreatOrder.band(ship(0, 100, -0.6, 5)));
    }

    @Test
    void withinABandNearerComesFirst() {
        List<ThreatOrder.Contact> in = List.of(mob(0, 30, true, false), mob(1, 10, true, false));
        assertEquals(List.of(1, 0), indices(ThreatOrder.order(in)));
    }

    @Test
    void playersAreLastAndTheListIsCapped() {
        List<ThreatOrder.Contact> in = new ArrayList<>();
        in.add(player(0, 1));
        for (int i = 1; i <= 30; i++) in.add(mob(i, 100 + i, false, false));
        List<ThreatOrder.Contact> out = ThreatOrder.order(in);
        assertEquals(ThreatOrder.MAX, out.size());
        assertEquals(1, out.get(0).index());
        assertEquals(ThreatOrder.PLAYER_BAND, ThreatOrder.band(player(0, 1)));
    }
}
