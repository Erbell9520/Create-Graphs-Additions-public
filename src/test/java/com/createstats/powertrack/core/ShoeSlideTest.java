package com.createstats.powertrack.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoeSlideTest {
    private static final double REACH = CollectorPose.CONTACT_HALF_WIDTH_PX / 16.0;

    private static final double ALONG = CollectorPose.shoeOffsetBlocks(CollectorPose.angleOnRail(RailProfile.CAP_TOP));
    private static final double FLOOR = 64.0;

    private static final double Y = FLOOR + 1.5;
    private static final double[] ALONG_X = {1, 0};
    private static final double[] SIDE_Z = {0, 1};

    private static RailLayout straightAlongX(double z) {
        return RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, 40, true, new double[]{0.5, FLOOR, z, 40.5, FLOOR, z})),
                List.of());
    }

    private static CollectorPose.Rest rest(RailLayout l, double[] c, double[] trail, double[] side, double slide) {
        final double floor = c[1] - 0.5;
        final double sx = c[0] + side[0] * slide;
        final double sz = c[2] + side[1] * slide;
        return CollectorPose.rest(off -> {
            final RailLayout.Hit h = l.nearest(sx + trail[0] * off, c[1], sz + trail[1] * off, REACH, 1.0, 2.0);
            return h != null && h.contact() ? (h.railTopY() - floor) * 16.0 : Double.NaN;
        });
    }

    @Test
    @DisplayName("centred, or a block to either side, the shoe lands on the rail and touches")
    void centredOrABlockToEitherSide() {
        final RailLayout l = straightAlongX(0.5);
        for (int offset = -1; offset <= 1; offset++) {
            final double[] c = {20.5, Y, 0.5 + offset};
            final ShoeSlide.Result r = ShoeSlide.onCar(l::nearest, c, ALONG_X, SIDE_Z, offset, ALONG);
            assertTrue(r.found(), "offset " + offset);
            assertEquals(-offset, r.slide(), 1e-9, "the sled runs to the centre line, offset " + offset);
            assertTrue(rest(l, c, ALONG_X, SIDE_Z, r.slide()).touching(), "offset " + offset);

            assertEquals(offset == 0, rest(l, c, ALONG_X, SIDE_Z, 0.0).touching(), "unslid, offset " + offset);
        }
    }

    @Test
    @DisplayName("two blocks off the centre line the sled runs out of travel and touches nothing")
    void twoBlocksOffDoesNotReach() {
        final RailLayout l = straightAlongX(0.5);
        final double[] c = {20.5, Y, 2.5};
        final ShoeSlide.Result r = ShoeSlide.onCar(l::nearest, c, ALONG_X, SIDE_Z, 2, ALONG);
        assertEquals(-ShoeSlide.MAX, r.slide(), 1e-9);
        assertFalse(rest(l, c, ALONG_X, SIDE_Z, r.slide()).touching());
    }

    @Test
    @DisplayName("height still decides: two blocks above the track the slide finds no rail and the arm reaches none")
    void twoBlocksUpFindsNothing() {
        final RailLayout l = straightAlongX(0.5);
        final double[] c = {20.5, Y + 1, 0.5};
        final ShoeSlide.Result r = ShoeSlide.onCar(l::nearest, c, ALONG_X, SIDE_Z, 0, ALONG);
        assertFalse(r.found());
        assertFalse(rest(l, c, ALONG_X, SIDE_Z, r.slide()).touching());
    }

    @Test
    @DisplayName("beside a second track two blocks over, the shoe goes to the car's own rail, whichever side it hangs")
    void theCarsOwnTrack() {
        final RailLayout l = RailLayout.build(List.of(
                new RailLayout.EdgeIn(1, 2, 40, true, new double[]{0.5, FLOOR, 0.5, 40.5, FLOOR, 0.5}),
                new RailLayout.EdgeIn(3, 4, 40, true, new double[]{0.5, FLOOR, 2.5, 40.5, FLOOR, 2.5})), List.of());

        final double[] c = {20.5, Y, 1.5};
        final ShoeSlide.Result lower = ShoeSlide.onCar(l::nearest, c, ALONG_X, SIDE_Z, 1, ALONG);
        assertEquals(1, lower.hit().lo());
        assertEquals(-1.0, lower.slide(), 1e-9);
        final ShoeSlide.Result upper = ShoeSlide.onCar(l::nearest, c, ALONG_X, SIDE_Z, -1, ALONG);
        assertEquals(3, upper.hit().lo());
        assertEquals(1.0, upper.slide(), 1e-9);
    }

    @Test
    @DisplayName("on a curve the shoe between the bogies follows the rail off the car's chord")
    void followsACurve() {
        final double r = 12;
        final int n = 129;
        final double[] xyz = new double[3 * n];
        for (int i = 0; i < n; i++) {
            final double a = Math.PI / 2 * i / (n - 1);
            xyz[3 * i] = r * Math.cos(a);
            xyz[3 * i + 1] = FLOOR;
            xyz[3 * i + 2] = r * Math.sin(a);
        }
        final RailLayout l = RailLayout.build(List.of(new RailLayout.EdgeIn(1, 2, Math.PI * r / 2, true, xyz)), List.of());
        final double half = Math.asin(3.0 / r);
        final double mid = Math.PI / 4;
        final double[] b0 = {r * Math.cos(mid - half), r * Math.sin(mid - half)};
        final double[] b1 = {r * Math.cos(mid + half), r * Math.sin(mid + half)};
        double tx = b1[0] - b0[0];
        double tz = b1[1] - b0[1];
        final double m = Math.hypot(tx, tz);
        tx /= m;
        tz /= m;
        final double[] trail = {tx, tz};
        final double[] side = {-tz, tx};

        final double[] c = {(b0[0] + b1[0]) / 2 - trail[0] * ALONG, Y, (b0[1] + b1[1]) / 2 - trail[1] * ALONG};
        assertFalse(rest(l, c, trail, side, 0.0).touching(), "the case under test: unslid, the chord leaves the rail");
        final ShoeSlide.Result res = ShoeSlide.onCar(l::nearest, c, trail, side, 0, ALONG);
        assertTrue(res.found());
        assertTrue(Math.abs(res.slide()) > REACH, "a real correction: " + res.slide());
        assertTrue(rest(l, c, trail, side, res.slide()).touching());
    }

    @Test
    @DisplayName("a placed collector finds the rail beside it, lays its gear along the line, and rests under itself with none")
    void placed() {
        final RailLayout l = straightAlongX(0.5);
        final double[] c = {20.5, Y, 1.5};

        assertEquals(Boolean.TRUE, ShoeSlide.alongX(l::nearest, c));
        final ShoeSlide.Result r = ShoeSlide.free(l::nearest, c, ALONG_X, SIDE_Z, ALONG);
        assertNotNull(r.hit());
        assertEquals(-1.0, r.slide(), 1e-9);
        final ShoeSlide.Result none = ShoeSlide.free(l::nearest, new double[]{20.5, Y, 5.5}, ALONG_X, SIDE_Z, ALONG);
        assertNull(none.hit());
        assertEquals(0.0, none.slide(), 0.0);
        assertNull(ShoeSlide.alongX(l::nearest, new double[]{20.5, Y, 5.5}));
    }

    @Test
    @DisplayName("with no rail under the car the sled rests under its centre line, ready for the rail ahead")
    void restsUnderTheCentreLine() {
        final RailLayout l = straightAlongX(30.5);
        final ShoeSlide.Result r = ShoeSlide.onCar(l::nearest, new double[]{20.5, Y, 1.5}, ALONG_X, SIDE_Z, 1, ALONG);
        assertFalse(r.found());
        assertEquals(-1.0, r.slide(), 1e-9);
    }
}
