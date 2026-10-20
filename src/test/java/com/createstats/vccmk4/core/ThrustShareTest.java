package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrustShareTest {
    private static final boolean[] ONE_WAY_2 = {false, false};
    private static final boolean[] TWO_WAY_2 = {true, true};

    @Test
    void aForwardAndARearwardThrusterDeliverTheWholePushNotHalf() {
        double[] ceiling = {533.0, 533.0};
        double[] clear = {1.0, 1.0};
        double[] along = {1.0, -1.0};
        double[] u = ThrustShare.share(300.0, ceiling, clear, along, ONE_WAY_2);
        assertEquals(300.0 / 533.0, u[0], 1e-9, "the forward one takes the lot");
        assertEquals(0.0, u[1], 1e-9, "the rearward one cannot push forward");
        assertEquals(300.0, ThrustShare.delivered(u, ceiling, along), 1e-9);
        double[] back = ThrustShare.share(-200.0, ceiling, clear, along, ONE_WAY_2);
        assertEquals(0.0, back[0], 1e-9);
        assertEquals(200.0 / 533.0, back[1], 1e-9, "and backwards it is the rearward one");
        assertEquals(-200.0, ThrustShare.delivered(back, ceiling, along), 1e-9);
    }

    @Test
    void aLeftAndARightThrusterDeliverTheWholeSwayTorque() {
        double[] ceiling = {800.0, 800.0};
        double[] clear = {1.0, 1.0};
        double[] arm = {4.0, -4.0};
        double[] u = ThrustShare.share(1200.0, ceiling, clear, arm, ONE_WAY_2);
        assertEquals(1200.0 / 3200.0, u[0], 1e-9);
        assertEquals(0.0, u[1], 1e-9, "the one that would turn it the wrong way stays quiet");
        assertEquals(1200.0, ThrustShare.delivered(u, ceiling, arm), 1e-9);
    }

    @Test
    void anObstructedThrusterIsNeverAskedPastItsClearShare() {
        double[] ceiling = {533.0, 533.0};
        double[] clear = {0.6, 1.0};
        double[] along = {1.0, 1.0};
        double[] u = ThrustShare.share(10000.0, ceiling, clear, along, ONE_WAY_2);
        assertEquals(0.6, u[0], 1e-9, "a command past its clear share does nothing, so it is not given");
        assertEquals(1.0, u[1], 1e-9);
        assertEquals(533.0 * 1.6, ThrustShare.capacity(10000.0, ceiling, clear, along, ONE_WAY_2), 1e-9);
        double[] small = ThrustShare.share(100.0, ceiling, clear, along, ONE_WAY_2);
        assertEquals(small[0] / 0.6, small[1] / 1.0, 1e-9, "both run at the same fraction of their capacity");
        assertEquals(100.0, ThrustShare.delivered(small, ceiling, along), 1e-9);
    }

    @Test
    void aStarvingThrustersShareMovesToTheOthers() {
        double[] full = {533.0, 533.0};
        double[] clear = {1.0, 1.0};
        double[] along = {1.0, 1.0};
        double[] before = ThrustShare.share(400.0, full, clear, along, ONE_WAY_2);
        assertEquals(before[0], before[1], 1e-9);

        double[] starving = {106.6, 533.0};
        double[] after = ThrustShare.share(400.0, starving, clear, along, ONE_WAY_2);
        assertEquals(400.0, ThrustShare.delivered(after, starving, along), 1e-9, "the trailer still gets its force");
        assertTrue(after[1] > before[1], "the healthy one takes more");
        assertEquals(after[0], after[1], 1e-9, "at the same fraction of what each can give");
    }

    @Test
    void twoWayPropellersWithEqualGainsReproduceTheEvenSplit() {
        double[] ceiling = {1200.0, 1200.0};
        double[] clear = {1.0, 1.0};
        double[] along = {1.0, -1.0};
        double[] u = ThrustShare.share(600.0, ceiling, clear, along, TWO_WAY_2);

        assertEquals(600.0 / 2 / 1.0 / 1200.0, u[0], 1e-9);
        assertEquals(600.0 / 2 / -1.0 / 1200.0, u[1], 1e-9, "the one facing backwards spins the other way");
        assertEquals(600.0, ThrustShare.delivered(u, ceiling, along), 1e-9);
        double[] unequal = {1200.0, 300.0};
        double[] v = ThrustShare.share(600.0, unequal, clear, along, TWO_WAY_2);
        assertEquals(v[0], -v[1], 1e-9, "unequal ones share by strength rather than clamping the weaker");
        assertEquals(600.0, ThrustShare.delivered(v, unequal, along), 1e-9);
    }

    @Test
    void whatNoJetCanMakeStaysUnmet() {
        double[] ceiling = {100.0};
        double[] clear = {1.0};
        double[] along = {1.0};
        double[] u = ThrustShare.share(1000.0, ceiling, clear, along, new boolean[]{false});
        assertEquals(1.0, u[0], 1e-9);
        assertEquals(100.0, ThrustShare.delivered(u, ceiling, along), 1e-9);
        assertEquals(0.0, ThrustShare.share(0.0, ceiling, clear, along, new boolean[]{false})[0], 1e-9);
        assertEquals(0.0, ThrustShare.share(Double.NaN, ceiling, clear, along, new boolean[]{false})[0], 1e-9);
        assertEquals(0.0, ThrustShare.share(50.0, new double[]{0.0}, clear, along, new boolean[]{false})[0], 1e-9, "no ceiling, no command");
        assertEquals(0.0, ThrustShare.share(50.0, ceiling, clear, new double[]{0.0}, new boolean[]{false})[0], 1e-9, "no arm, no command");
    }

    @Test
    void aVerticalOrSidewaysJetContributesByItsAlignmentOnly() {
        double[] ceiling = {533.0, 533.0};
        double[] clear = {1.0, 1.0};
        double[] along = {1.0, 0.5};
        double[] u = ThrustShare.share(400.0, ceiling, clear, along, ONE_WAY_2);
        assertEquals(u[0], u[1], 1e-9, "both at the same fraction of capacity");
        assertEquals(400.0, ThrustShare.delivered(u, ceiling, along), 1e-9, "the angled one counts half per unit of thrust");
    }
}
