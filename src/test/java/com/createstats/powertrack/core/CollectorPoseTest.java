package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorPoseTest {
    private static final double D = 1e-9;

    @Test
    @DisplayName("on a level rail the sole rests exactly on the cap")
    void restsOnTheCap() {
        final double a = CollectorPose.angleOnRail(RailProfile.CAP_TOP);
        assertEquals(RailProfile.CAP_TOP - 16.0, CollectorPose.soleAt(a), 1e-9);
        assertTrue(a > CollectorPose.STOP_DEG && a < 60, "angle " + a);
    }

    @Test
    @DisplayName("the arm rides every height a ramp passes through, and the sole follows the rail exactly")
    void ridesTheRamp() {
        double last = Double.MAX_VALUE;
        for (double top = RailProfile.CAP_TOP; top >= CollectorPose.lowestTouchPx(); top -= 0.25) {
            final double a = CollectorPose.angleOnRail(top);
            assertEquals(top - 16.0, CollectorPose.soleAt(a), 1e-9, "top " + top);
            assertTrue(a <= last, "a lower rail must hang the arm lower");
            last = a;
        }
    }

    @Test
    @DisplayName("with nothing under it, or a rail below its reach, the arm rests on its stop")
    void stop() {
        assertEquals(CollectorPose.STOP_DEG, CollectorPose.angleFor(-40), D);
        assertEquals(CollectorPose.STOP_DEG, CollectorPose.angleOnRail(RailProfile.SLEEPER_TOP), D);
    }

    @Test
    @DisplayName("contact visibly begins where the rail begins: the stop sits within half a pixel of a ramp's toe")
    void stopMeetsTheToe() {
        final double toe = RailProfile.CAP_TOP - RailProfile.RAMP_DROP;
        final double lowest = CollectorPose.lowestTouchPx();
        assertTrue(lowest >= toe && lowest - toe < 0.5, "lowest touch " + lowest + " against a toe at " + toe);
    }

    @Test
    @DisplayName("a rail out of reach above never folds the arm flat")
    void ceiling() {
        assertEquals(CollectorPose.MAX_DEG, CollectorPose.angleFor(10), D);
    }

    @Test
    @DisplayName("the shoe stands about a third of a block from its block's centre on a level rail, trailing")
    void reach() {
        final double off = CollectorPose.shoeOffsetBlocks(CollectorPose.angleOnRail(RailProfile.CAP_TOP));
        assertTrue(off > 0.2 && off < 0.45, "offset " + off);
    }

    @Test
    @DisplayName("the rail's profile: the cap clears Create's large bogie, the ramp is the conducting ramp")
    void profile() {
        assertTrue(RailProfile.CAP_TOP < 5.875);
        assertTrue(RailProfile.CAP_TOP > RailProfile.RUNNING_RAIL_TOP, "a fourth rail stands proud of the running rails");
        assertEquals(SectionGraph.RAMP * 16.0, RailProfile.RAMP_LEN, D);
        assertTrue(RailProfile.CAP_TOP - RailProfile.RAMP_DROP > RailProfile.SLEEPER_TOP, "a toe never sinks into a sleeper");
        final SectionGraph.Piece p = new SectionGraph.Piece(0, 0, 10, 0.5, 9.5, SectionGraph.RailEnd.RAMP_END,
                SectionGraph.RailEnd.THROUGH);
        assertEquals(RailProfile.CAP_TOP - RailProfile.RAMP_DROP, RailProfile.topPx(p, 0.5), D);
        assertEquals(RailProfile.CAP_TOP, RailProfile.topPx(p, 0.5 + SectionGraph.RAMP), D);
        assertEquals(RailProfile.CAP_TOP, RailProfile.topPx(p, 9.5), D);
        assertEquals(RailProfile.CAP_TOP - RailProfile.RAMP_DROP / 2, RailProfile.topPx(p, 0.5 + SectionGraph.RAMP / 2), D);
        assertTrue(RailProfile.bottomPx(RailProfile.CAP_TOP - RailProfile.RAMP_DROP) > RailProfile.SLEEPER_TOP);
        assertEquals(List.of(), List.of());
    }

    private static final SectionGraph.Piece RAIL = new SectionGraph.Piece(0, 0, 10, 2.0, 8.0,
            SectionGraph.RailEnd.RAMP_INSULATED, SectionGraph.RailEnd.RAMP_INSULATED);

    private static CollectorPose.RailUnder under(double centre) {
        return offset -> {
            final double at = centre + offset;
            return RAIL.railAt(at) ? RailProfile.topPx(RAIL, at) - 16.0 : Double.NaN;
        };
    }

    private static boolean drawnOnMetal(double centre, CollectorPose.Rest rest) {
        final double top = under(centre).topPx(CollectorPose.shoeOffsetBlocks(rest.angleDeg()));
        return !Double.isNaN(top) && Math.abs(CollectorPose.soleAt(rest.angleDeg()) - top) <= CollectorPose.TOUCH_PX + 1e-3;
    }

    @Test
    @DisplayName("on a level rail the arm rests where the old fixed reach said, and touches")
    void restsOnALevelRail() {
        final CollectorPose.Rest rest = CollectorPose.rest(under(5.0));
        assertTrue(rest.touching());
        assertEquals(CollectorPose.angleOnRail(RailProfile.CAP_TOP), rest.angleDeg(), 1e-6);
    }

    @Test
    @DisplayName("with nothing under the shoe at its stop the arm stays on its stop and touches nothing")
    void restsOnItsStopOverAGap() {
        final CollectorPose.Rest rest = CollectorPose.rest(under(0.5));
        assertEquals(CollectorPose.STOP_DEG, rest.angleDeg(), D);
        assertTrue(!rest.touching());
    }

    @Test
    @DisplayName("contact is exactly the drawn shoe on metal, at every place across both ramp ends (KI-140)")
    void contactIsTheDrawnShoe() {
        int touching = 0;
        for (double centre = 0.5; centre <= 9.0; centre += 1.0 / 64.0) {
            final CollectorPose.Rest rest = CollectorPose.rest(under(centre));
            assertTrue(rest.angleDeg() >= CollectorPose.STOP_DEG - D && rest.angleDeg() <= CollectorPose.MAX_DEG + D);
            assertEquals(drawnOnMetal(centre, rest), rest.touching(), "at " + centre + ", angle " + rest.angleDeg());
            if (rest.touching()) {
                touching++;

                final double top = under(centre).topPx(CollectorPose.shoeOffsetBlocks(rest.angleDeg()));
                assertTrue(CollectorPose.soleAt(rest.angleDeg()) >= top - 1e-3, "sunk at " + centre);
            }
        }
        assertTrue(touching > 300, "the shoe touched along most of the rail: " + touching);
    }

    @Test
    @DisplayName("arriving shoe first, the level-rail reach over a ramp's toe is not contact: the drawn shoe still hangs (KI-140)")
    void theOldReachStruckEarly() {
        final double reach = CollectorPose.shoeOffsetBlocks(CollectorPose.angleOnRail(RailProfile.CAP_TOP));
        final double centre = RAIL.railFrom() + 0.01 - reach;
        assertTrue(RAIL.railAt(centre + reach), "the old rule called this contact");
        final CollectorPose.Rest rest = CollectorPose.rest(under(centre));
        assertTrue(!rest.touching(), "the drawn shoe hangs on its stop a quarter block back, in the air");
    }

    @Test
    @DisplayName("leaving over a descending ramp the arm rests on the ramp, never on its stop inside it (KI-140)")
    void leavingRestsOnTheRamp() {
        for (double centre = 7.0; centre < 8.0; centre += 1.0 / 32.0) {
            final CollectorPose.Rest rest = CollectorPose.rest(under(centre));
            final double top = under(centre).topPx(CollectorPose.shoeOffsetBlocks(rest.angleDeg()));
            if (!Double.isNaN(top)) {
                assertTrue(CollectorPose.soleAt(rest.angleDeg()) >= top - 1e-3,
                        "the shoe was drawn inside the ramp at " + centre);
            }
        }
    }

    @Test
    @DisplayName("where stepping would overshoot, the solve still finds the lowest angle the rail allows")
    void stepsThatOvershootStillSettle() {
        final double stopOff = CollectorPose.shoeOffsetBlocks(CollectorPose.STOP_DEG);
        final double stopSole = CollectorPose.soleAt(CollectorPose.STOP_DEG);
        final CollectorPose.RailUnder steep = o -> stopSole + 0.5 - (o - stopOff) * 16.0 * 30.0;
        final CollectorPose.Rest rest = CollectorPose.rest(steep);
        assertTrue(rest.touching());
        final double a = rest.angleDeg();
        final double gap = CollectorPose.soleAt(a) - steep.topPx(CollectorPose.shoeOffsetBlocks(a));
        assertTrue(gap >= -1e-3 && gap < 0.05, "the sole rests on the rail, not in it nor above it: " + gap);
        final double lower = a - 0.01;
        assertTrue(CollectorPose.soleAt(lower) < steep.topPx(CollectorPose.shoeOffsetBlocks(lower)),
                "a lower angle would put the sole inside the rail, so this is the lowest the rail allows");
    }

    @Test
    @DisplayName("a rail just reaching the sole on the stop counts, a hair under it does not")
    void touchAtTheStop() {
        final double sole = CollectorPose.soleAt(CollectorPose.STOP_DEG);
        assertTrue(CollectorPose.rest(o -> sole).touching());
        assertEquals(CollectorPose.STOP_DEG, CollectorPose.rest(o -> sole).angleDeg(), D);
        assertTrue(!CollectorPose.rest(o -> sole - 0.2).touching());
    }
}
