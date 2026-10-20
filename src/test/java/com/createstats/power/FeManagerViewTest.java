package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeManagerViewTest {
    @Test
    @DisplayName("each terminal kind says whether it draws a burst: a controller output does, a capped leg does not")
    void drawsBursts() {
        assertTrue(FeManagerView.leg(0, 0, false).drawsBursts());
        assertTrue(FeManagerView.input(0, 0).drawsBursts());
        final FeManagerView out = FeManagerView.output(0, 0, false);
        assertFalse(out.takesSources(), "still no ordinary source read off an output (KI-39)");
        assertTrue(out.drawsBursts(), "but a burst is, or it burns off whole (KI-73)");
        final FeManagerView capped = FeManagerView.leg(10, 20, true, 700).refusingSources();
        assertFalse(capped.takesSources());
        assertFalse(capped.drawsBursts());
        assertTrue(capped.servesLoads() && capped.holdsCharging());
        assertEquals(700, capped.unserved(), "the branch is still short; this circuit just cannot help");
    }

    @Test
    @DisplayName("the five-part constructor keeps its old meaning: nothing unserved, a burst drawn where a source is")
    void oldShape() {
        final FeManagerView v = new FeManagerView(1, 2, false, true, false);
        assertEquals(0, v.unserved());
        assertFalse(v.drawsBursts());
        assertTrue(new FeManagerView(1, 2, false, false, true).drawsBursts());
        assertEquals(0, FeManagerView.leg(1, 2, false, -50).unserved(), "never negative");
    }

    @Test
    @DisplayName("two stamps on one circuit: the larger shortfall, not the sum, and either side drawing is drawing")
    void merge() {
        final FeManagerView a = FeManagerView.leg(0, 0, false, 300).refusingSources();
        final FeManagerView b = FeManagerView.output(0, 0, false, 900);
        final FeManagerView both = a.merge(b);
        assertEquals(900, both.unserved(), "one manager seen through two terminals is one shortfall");
        assertTrue(both.drawsBursts());
        assertEquals(both, both.merge(null));
    }

    @Test
    @DisplayName("KI-95: whether the manager refills stores is off by default, survives a capped leg, and unions on merge")
    void storesFunded() {
        assertFalse(FeManagerView.leg(0, 0, false, 0).fundsStores(), "no manager funded stores before KI-95");
        assertFalse(new FeManagerView(1, 2, false, true, true, 5, true).fundsStores(), "the seven-part shape");
        final FeManagerView input = FeManagerView.input(10, 20, 300).withStoresFunded(true);
        assertTrue(input.fundsStores());
        assertFalse(input.servesLoads(), "an input is still never served; only a store is refilled there");
        assertTrue(input.withStoresFunded(true) == input, "no copy when nothing changes");
        assertTrue(FeManagerView.leg(0, 0, false).withStoresFunded(true).refusingSources().fundsStores(),
                "a leg behind a limiter still has its capacitors refilled");
        assertTrue(FeManagerView.output(0, 0, false).merge(input).fundsStores(), "either side funding is funding");
    }
}
