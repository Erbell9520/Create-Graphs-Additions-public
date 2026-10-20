package com.createstats.stormcoil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilAimTest {
    private static final class Field {
        final CoilAim aim = new CoilAim();
        final CoilStores stores = new CoilStores();
        final CoilStores.Terms terms = CoilLaw.terms(CoilStage.TWO, true, CoilLaw.DEFAULT_BASE);
        final Set<Integer> present = new HashSet<>();
        final List<Long> aims = new ArrayList<>();
        final List<Long> fires = new ArrayList<>();
        final List<Integer> struck = new ArrayList<>();
        long paid;

        Field() {
            stores.restore(terms.capacitorSize(), 0);
        }

        void tick(long now) {
            switch (aim.step(now)) {
                case LOOK -> {
                    if (present.isEmpty() || !stores.canPay(terms.zapCost())) {
                        aim.nothing(now);
                    } else {
                        aim.aim(now, present.stream().mapToInt(Integer::intValue).sorted().limit(2).toArray());
                        aims.add(now);
                    }
                }
                case FIRE -> {
                    final int[] kept = CoilAim.kept(aim.ids(), present::contains);
                    int hit = 0;
                    for (int id : kept) {
                        if (!stores.payZap(terms.zapCost())) {
                            break;
                        }
                        paid += terms.zapCost();
                        struck.add(id);
                        hit++;
                    }
                    if (hit > 0) {
                        fires.add(now);
                    }
                    aim.fired(now, hit);
                }
                case WAIT -> {
                }
            }
        }
    }

    @Test
    @DisplayName("aimed first, fired WIND_UP ticks later; nothing is paid at the aim")
    void windUpThenFire() {
        final Field f = new Field();
        f.present.add(7);
        for (long t = 0; t < CoilLaw.WIND_UP; t++) {
            f.tick(t);
            assertEquals(0, f.paid, "paid during the wind-up at " + t);
            assertTrue(f.fires.isEmpty(), "fired during the wind-up at " + t);
        }
        assertEquals(List.of(0L), f.aims);
        assertTrue(f.aim.aiming());
        assertArrayEquals(new int[] {7}, f.aim.ids());
        f.tick(CoilLaw.WIND_UP);
        assertEquals(List.of((long) CoilLaw.WIND_UP), f.fires, "the volley fires WIND_UP after its aim");
        assertEquals(f.terms.zapCost(), f.paid, "one arc, paid at the fire");
        assertFalse(f.aim.aiming());
    }

    @Test
    @DisplayName("a sustained fight still fires a volley every ZAP_INTERVAL")
    void cadenceKept() {
        final Field f = new Field();
        f.present.add(3);
        f.present.add(4);
        for (long t = 0; t < 400; t++) {
            f.stores.restore(f.terms.capacitorSize(), 0);
            f.tick(t);
        }
        assertTrue(f.fires.size() >= 18, "fired " + f.fires.size() + " volleys in 20 s");
        assertEquals(CoilLaw.WIND_UP, f.fires.get(0) - f.aims.get(0), "the first volley waits for its wind-up");
        for (int i = 1; i < f.fires.size(); i++) {
            assertEquals(CoilLaw.ZAP_INTERVAL, f.fires.get(i) - f.fires.get(i - 1), "volleys " + (i - 1) + " and " + i);
            assertEquals(CoilLaw.WIND_UP, f.fires.get(i) - f.aims.get(i), "every volley is wound up first");
        }
    }

    @Test
    @DisplayName("a target that leaves in the wind-up is dropped; the rest still fire")
    void leaverDropped() {
        final Field f = new Field();
        f.present.add(1);
        f.present.add(2);
        f.tick(0);
        f.present.remove(1);
        for (long t = 1; t <= CoilLaw.WIND_UP; t++) {
            f.tick(t);
        }
        assertEquals(List.of(2), f.struck, "only the one still there is struck");
        assertEquals(f.terms.zapCost(), f.paid, "and only it is paid for");
    }

    @Test
    @DisplayName("every target gone: nothing fires, nothing is paid, it looks again soon")
    void allGone() {
        final Field f = new Field();
        f.present.add(9);
        f.tick(0);
        f.present.clear();
        for (long t = 1; t <= CoilLaw.WIND_UP; t++) {
            f.tick(t);
        }
        assertTrue(f.fires.isEmpty());
        assertEquals(0, f.paid);
        assertEquals(f.terms.capacitorSize(), f.stores.capacitor(), "the capacitor is untouched");
        assertEquals(CoilAim.Step.WAIT, f.aim.step(CoilLaw.WIND_UP + CoilAim.RETRY - 1));
        assertEquals(CoilAim.Step.LOOK, f.aim.step(CoilLaw.WIND_UP + CoilAim.RETRY));
    }

    @Test
    @DisplayName("an empty field is looked at every RETRY ticks; a reset looks at once and drops the aim")
    void retryAndReset() {
        final CoilAim aim = new CoilAim();
        assertEquals(CoilAim.Step.LOOK, aim.step(0));
        aim.nothing(0);
        assertEquals(CoilAim.Step.WAIT, aim.step(CoilAim.RETRY - 1));
        assertEquals(CoilAim.Step.LOOK, aim.step(CoilAim.RETRY));
        aim.aim(20, new int[] {1, 2, 3, 4, 5, 6});
        assertEquals(CoilAim.MAX, aim.ids().length, "never more than a volley's worth");
        aim.reset();
        assertFalse(aim.aiming());
        assertEquals(0, aim.ids().length);
        assertEquals(CoilAim.Step.LOOK, aim.step(21));
    }

    @Test
    @DisplayName("the wind-up's progress runs 0 to 1 over WIND_UP ticks, and is 0 with no aim")
    void progress() {
        assertEquals(0.0, CoilAim.progress(100, Long.MIN_VALUE));
        assertEquals(0.0, CoilAim.progress(100, 100));
        assertEquals(0.5, CoilAim.progress(100 + CoilLaw.WIND_UP / 2.0, 100), 1e-12);
        assertEquals(1.0, CoilAim.progress(100 + CoilLaw.WIND_UP + 5, 100));
        assertEquals(0.0, CoilAim.progress(90, 100), "never negative before the aim");
        assertEquals(CoilLaw.ZAP_INTERVAL, CoilAim.fireAt(CoilAim.nextAim(0)), "the next fire a second after this one");
    }
}
