package com.createstats.stormcoil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilStoresTest {
    private static final int BASE = CoilLaw.DEFAULT_BASE;

    private static CoilStores.Terms terms(CoilStage stage, boolean zaps) {
        return CoilLaw.terms(stage, zaps, BASE);
    }

    @Test
    @DisplayName("random operations never carry IN's FE out, never fill the bank but by lightning, and conserve")
    void neverConductsAndConserves() {
        final Random rng = new Random(20261009L);
        for (int run = 0; run < 200; run++) {
            final CoilStores s = new CoilStores();
            long now = rng.nextInt(1000);
            for (int step = 0; step < 400; step++) {
                final CoilStage stage = CoilStage.values()[rng.nextInt(4)];
                final CoilStores.Terms t = terms(stage, rng.nextInt(5) != 0);
                final int bankBefore = s.bank();
                final int op = rng.nextInt(6);
                switch (op) {
                    case 0 -> s.receiveIn(rng.nextInt(20_000), rng.nextBoolean(), now, t);
                    case 1 -> s.extractOut(rng.nextInt(80_000), rng.nextBoolean(), now, t);
                    case 2 -> {
                        if (rng.nextInt(20) == 0) {
                            s.strike(t);
                        }
                    }
                    case 3 -> s.payZap(stage.zapCost);
                    case 4 -> now++;
                    default -> now += 1 + rng.nextInt(3);
                }
                if (op != 2) {
                    assertTrue(s.bank() <= bankBefore, "the bank rose without a strike (op " + op + ")");
                }
                assertTrue(s.totalOut() <= s.totalStruck(), "OUT gave more than lightning ever banked");
                assertEquals(s.totalIn() + s.totalStruck(),
                        (long) s.capacitor() + s.bank() + s.totalOut() + s.totalZapped(), "FE created or lost");
                assertTrue(s.capacitor() >= 0 && s.bank() >= 0);
            }
        }
    }

    @Test
    @DisplayName("IN fills the capacitor up to its allowance a tick, and a simulate books nothing")
    void inAllowance() {
        final CoilStores s = new CoilStores();
        final CoilStores.Terms t = terms(CoilStage.FOUR, true);

        final int in = CoilLaw.inRate(CoilStage.FOUR, BASE);
        assertEquals(4 * BASE, in);
        assertEquals(in, s.receiveIn(1_000_000, true, 5, t));
        assertEquals(0, s.capacitor());
        assertEquals(in, s.receiveIn(1_000_000, false, 5, t));
        assertEquals(0, s.receiveIn(1_000_000, false, 5, t), "a second pass in one tick took a second allowance");
        assertEquals(in, s.receiveIn(1_000_000, false, 6, t));
        assertEquals(in, s.inDrawn(7), "the readout is the last whole tick");
        assertEquals(2 * in, s.capacitor());
    }

    @Test
    @DisplayName("IN draws nothing while the bank can pay the next arc, when full, or in Storm only")
    void inGate() {
        final CoilStores s = new CoilStores();
        final CoilStores.Terms t = terms(CoilStage.TWO, true);
        s.strike(t);
        assertFalse(s.inDraws(t));
        assertEquals(0, s.receiveIn(5000, false, 1, t));
        final CoilStores empty = new CoilStores();
        assertEquals(0, empty.receiveIn(5000, false, 1, terms(CoilStage.TWO, false)), "Storm only took power");
        empty.restore(CoilStage.TWO.capacitorSize(), 0);
        assertEquals(0, empty.receiveIn(5000, false, 2, terms(CoilStage.TWO, true)), "a full capacitor took power");

        final CoilStores low = new CoilStores();
        low.restore(0, CoilStage.TWO.zapCost - 1);
        assertTrue(low.receiveIn(5000, false, 3, terms(CoilStage.TWO, true)) > 0);
    }

    @Test
    @DisplayName("OUT gives the bank at most one HV Line a tick and nothing of the capacitor")
    void outAllowance() {
        final CoilStores s = new CoilStores();
        final CoilStores.Terms t = terms(CoilStage.FOUR, true);
        s.restore(CoilStage.FOUR.capacitorSize(), 0);
        assertEquals(0, s.extractOut(10_000, false, 1, t), "OUT carried the capacitor's FE");
        s.strike(t);
        assertEquals(CoilLaw.outRate(BASE), s.extractOut(Integer.MAX_VALUE, false, 2, t));
        assertEquals(0, s.extractOut(Integer.MAX_VALUE, false, 2, t));
        assertEquals(CoilLaw.strikeFe(BASE) - CoilLaw.outRate(BASE), s.bank());
    }

    @Test
    @DisplayName("an arc pays from the bank first, then the capacitor, and only whole")
    void zapPaysBankFirst() {
        final CoilStores s = new CoilStores();
        s.restore(30_000, 5_000);
        assertTrue(s.payZap(20_000));
        assertEquals(0, s.bank());
        assertEquals(15_000, s.capacitor());
        assertFalse(s.payZap(20_000), "paid an arc it could not afford");
        assertEquals(15_000, s.capacitor());
    }

    @Test
    @DisplayName("a strike banks at most the room left; the bank holds ten")
    void bankFills() {
        final CoilStores s = new CoilStores();
        final CoilStores.Terms t = terms(CoilStage.ONE, true);
        for (int i = 0; i < CoilLaw.STRIKES_HELD; i++) {
            assertEquals(CoilLaw.strikeFe(BASE), s.strike(t));
        }
        assertEquals(0, s.strike(t));
        assertEquals(CoilLaw.bankSize(BASE), s.bank());
    }

    @Test
    @DisplayName("the shared-circuit duty is one role at a time and follows the bank")
    void duty() {
        final CoilStores s = new CoilStores();
        final CoilStores.Terms zaps = terms(CoilStage.THREE, true);
        final CoilStores.Terms storm = terms(CoilStage.THREE, false);
        assertEquals(CoilStores.Duty.LOAD, s.duty(zaps));
        s.restore(0, CoilStage.THREE.zapCost - 1);
        assertEquals(CoilStores.Duty.LOAD, s.duty(zaps), "a bank short of an arc leaves IN to charge");
        assertEquals(CoilStores.Duty.SOURCE, s.duty(storm), "with no arcs wanted any banked FE goes out");
        s.restore(0, CoilStage.THREE.zapCost);
        assertEquals(CoilStores.Duty.SOURCE, s.duty(zaps));
    }
}
