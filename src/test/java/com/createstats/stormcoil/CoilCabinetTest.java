package com.createstats.stormcoil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilCabinetTest {
    private static final double FOIL = CoilCabinet.JAR_R + CoilCabinet.FOIL_PROUD;

    @Test
    @DisplayName("one jar per volley the capacitor holds, one mark per strike the bank holds")
    void countsAreTheLaw() {
        assertEquals(CoilLaw.CAPACITOR_VOLLEYS, CoilCabinet.JARS);
        assertEquals(CoilCabinet.JARS, CoilCabinet.PER_SHELF * CoilCabinet.SHELF_Y.length);
        assertEquals(CoilCabinet.PER_SHELF, CoilCabinet.JAR_X.length);
        assertEquals(CoilLaw.STRIKES_HELD, CoilCabinet.MARKS);
    }

    @Test
    @DisplayName("a volley fired is one jar gone dark; a recharge lights them again in turn")
    void aJarAVolley() {
        for (CoilStage stage : CoilStage.values()) {
            final int size = stage.capacitorSize();
            final int volley = stage.zapCost * stage.targets();
            for (int held = 0; held <= CoilLaw.CAPACITOR_VOLLEYS; held++) {
                final double full = held * (double) volley / size;
                assertEquals(held, CoilCabinet.lit(full, CoilCabinet.JARS), stage + ", " + held + " volleys held");
                if (held > 0) {
                    final double part = (held - 0.5) * volley / size;
                    assertEquals(held, CoilCabinet.lit(part, CoilCabinet.JARS));
                    assertEquals(0.5, CoilCabinet.level(part, held - 1, CoilCabinet.JARS), 1e-9);
                }
            }
        }
        assertEquals(0, CoilCabinet.lit(0.0, CoilCabinet.JARS), "empty: every jar dark");
        for (int k = 0; k < CoilCabinet.JARS; k++) {
            assertEquals(1.0, CoilCabinet.level(1.0, k, CoilCabinet.JARS), "full: every jar lit");
            assertTrue(CoilCabinet.level(0.999, k, CoilCabinet.JARS) > 0.9, "jar " + k + " nearly full");
        }
        assertEquals(3, CoilCabinet.lit(3.0 / CoilLaw.STRIKES_HELD, CoilCabinet.MARKS), "three strikes, three marks");
    }

    @Test
    @DisplayName("the jars stand inside the bay, clear of each other, the riser and the shelf above")
    void jarsFit() {
        for (int k = 0; k < CoilCabinet.JARS; k++) {
            final double x = CoilCabinet.jarX(k);
            final double y = CoilCabinet.jarY(k);
            assertTrue(x - FOIL > CoilCabinet.RISER_X1, "jar " + k + " runs into the riser");
            assertTrue(x + FOIL < CoilCabinet.BAY_X1, "jar " + k + " runs into the bay wall");
            final double z = CoilCabinet.jarZ(k);
            assertTrue(z - FOIL > 0.2 && z + FOIL < CoilCabinet.BAY_DEPTH - 0.15, "jar " + k + " is not inside the bay");
            if (k >= CoilCabinet.PER_SHELF) {
                assertTrue(z - FOIL > CoilCabinet.UPPER_SHELF_Z, "upper jar " + k + " hangs off its shelf's front");
            } else {
                assertTrue(z - FOIL < CoilCabinet.UPPER_SHELF_Z, "lower jar " + k + " hides under the upper shelf");
            }
            final double top = y + CoilCabinet.KNOB_Y + CoilCabinet.KNOB_R;
            final double ceiling = k < CoilCabinet.PER_SHELF ? CoilCabinet.SHELF_Y[1] - CoilCabinet.SHELF_THICK
                    : CoilCabinet.BAY_TOP;
            assertTrue(top < ceiling, "jar " + k + "'s knob hits what is above it: " + top + " against " + ceiling);
        }
        for (int i = 0; i + 1 < CoilCabinet.PER_SHELF; i++) {
            assertTrue(CoilCabinet.JAR_X[i + 1] - CoilCabinet.JAR_X[i] > 2 * FOIL + 0.3, "jars " + i + " and " + (i + 1));
        }
        assertTrue(CoilCabinet.FOIL_TOP < CoilCabinet.GLASS_TOP / 2, "the foil hides the larger part of the glass");
        assertTrue(CoilCabinet.FOIL_TOP < CoilCabinet.GLASS_TOP && CoilCabinet.GLASS_TOP < CoilCabinet.CAP_TOP
                && CoilCabinet.CAP_TOP < CoilCabinet.ROD_TOP && CoilCabinet.ROD_TOP < CoilCabinet.KNOB_Y);
    }

    @Test
    @DisplayName("the gap's wheel turns clear of the glass and the walls, and its studs pass the electrodes across a gap")
    void gapFits() {
        assertTrue(CoilCabinet.GAP_Z - CoilCabinet.STUD_OUT - CoilCabinet.STUD_HALF > CoilCabinet.GLASS_Z,
                "a stud sweeps through the window glass");
        assertTrue(CoilCabinet.GAP_X - CoilCabinet.STUD_OUT > CoilCabinet.HOUSE_X0
                && CoilCabinet.GAP_X + CoilCabinet.STUD_OUT < CoilCabinet.HOUSE_X1);
        assertTrue(CoilCabinet.DISC_R > CoilCabinet.STUD_IN && CoilCabinet.STUD_OUT > CoilCabinet.DISC_R);
        assertTrue(CoilCabinet.ELECTRODE_X[0] > CoilCabinet.WINDOW_X0 && CoilCabinet.ELECTRODE_X[1] < CoilCabinet.WINDOW_X1,
                "both electrodes, and their sparks, are seen through the window");
        assertEquals(CoilCabinet.SPARK_GAP, CoilCabinet.GAP_X - CoilCabinet.STUD_OUT - CoilCabinet.ELECTRODE_X[0], 1e-12);
        assertTrue(CoilCabinet.DISC_Y0 > CoilCabinet.HOUSE_FLOOR && CoilCabinet.DISC_Y1 < CoilCabinet.HOUSE_CEILING);
        assertEquals(0, 360 % CoilMotion.STUDS, "studs evenly round the wheel");
        assertEquals(0.0, 180.0 % (360.0 / CoilMotion.STUDS), "a stud reaches both electrodes at once, a series gap");
    }

    @Test
    @DisplayName("the storm marks fit the west face of the spark house, and the security lamp's spot is clear")
    void marksAndLampSpot() {
        final double last = CoilCabinet.markZ(CoilCabinet.MARKS - 1) + CoilCabinet.MARK_W;
        assertTrue(CoilCabinet.markZ(0) > 1.0 && last < 15.0, "the marks run off the west wall: " + last);
        assertTrue(CoilCabinet.MARK_PITCH > CoilCabinet.MARK_W, "the marks overlap");
        assertTrue(CoilCabinet.MARK_Y0 > CoilCabinet.HOUSE_FLOOR && CoilCabinet.MARK_Y1 < CoilCabinet.HOUSE_CEILING);
        final double[] lamp = CoilCabinet.SECURITY_LAMP;
        assertTrue(lamp[0] - 1 >= CoilCabinet.WINDOW_X1 && lamp[0] + 1 <= 16, "the lamp's spot is on the left post");
        assertTrue(lamp[1] - 1 >= CoilCabinet.HOUSE_FLOOR && lamp[1] + 1 <= CoilCabinet.HOUSE_CEILING);
    }

    @Test
    @DisplayName("the Targets lamps say what the coil arcs at now: its own setting, or its house's rule when governed")
    void targetLamps() {
        assertArrayEquals(new boolean[] {true, false, false},
                CoilCabinet.targetLamps(CoilRules.Policy.own(CoilTargets.Mode.HOSTILE)));
        assertArrayEquals(new boolean[] {false, true, false},
                CoilCabinet.targetLamps(CoilRules.Policy.own(CoilTargets.Mode.TRANSFORM)));
        assertArrayEquals(new boolean[] {false, false, false},
                CoilCabinet.targetLamps(CoilRules.Policy.own(CoilTargets.Mode.STORM_ONLY)), "Storm only arcs at nothing");
        assertArrayEquals(new boolean[] {true, false, true}, CoilCabinet.targetLamps(CoilRules.Policy.governed(
                new CoilRules.Rule(true, false, true, CoilRules.NO_MEMBERS), true)), "hostile mobs and strangers");
        assertArrayEquals(new boolean[] {false, false, true}, CoilCabinet.targetLamps(CoilRules.Policy.governed(
                new CoilRules.Rule(false, false, false, 3), true)), "members below a clearance are players too");
        assertArrayEquals(new boolean[] {false, false, false},
                CoilCabinet.targetLamps(CoilRules.Policy.governed(CoilRules.Rule.NOTHING, true)), "a stood-down rule");
        assertArrayEquals(new boolean[] {false, false, false}, CoilCabinet.targetLamps(null));
        for (CoilTargets.Mode mode : CoilTargets.Mode.values()) {
            final boolean[] lit = CoilCabinet.targetLamps(CoilRules.Policy.own(mode));
            assertEquals(mode.zaps(), lit[0] || lit[1] || lit[2], "a lamp lit for " + mode + " that arcs at nothing");
        }
    }

    @Test
    @DisplayName("the Height ladder fills as the column rises; the notch it was set to blinks amber while blocked")
    void ladder() {
        for (int stage = 1; stage <= 4; stage++) {
            for (int k = 1; k <= 4; k++) {
                assertEquals(k <= stage ? CoilCabinet.Notch.LIT : CoilCabinet.Notch.DARK,
                        CoilCabinet.notch(k, stage, stage, false, 0), "stage " + stage + " notch " + k);
            }
        }
        assertEquals(CoilCabinet.Notch.DARK, CoilCabinet.notch(3, 2.4, 4, false, 0), "not yet halfway to 3");
        assertEquals(CoilCabinet.Notch.LIT, CoilCabinet.notch(3, 2.6, 4, false, 0), "past halfway: lit on the way up");
        int on = 0;
        int off = 0;
        for (long t = 0; t < 4 * CoilCabinet.BLINK_TICKS; t++) {
            final CoilCabinet.Notch n = CoilCabinet.notch(4, 3.0, 4, true, t);
            on += n == CoilCabinet.Notch.BLOCKED_ON ? 1 : 0;
            off += n == CoilCabinet.Notch.BLOCKED_OFF ? 1 : 0;
            assertEquals(CoilCabinet.Notch.DARK, CoilCabinet.notch(4, 3.0, 4, false, t), "no blink unless blocked");
        }
        assertEquals(on, off, "a blink is half on, half off");
        assertTrue(on > 0);
        assertEquals(CoilCabinet.Notch.LIT, CoilCabinet.notch(3, 3.0, 4, true, 0), "what it reached stays lit");
    }

    @Test
    @DisplayName("a volley's return runs down its strap, lands on its lug, and goes round the bus to the terminal")
    void earthReturn() {
        assertEquals(1.0, CoilCabinet.strapHead(0), 1e-12, "starts at the rod's ball");
        assertEquals(0.0, CoilCabinet.strapHead(CoilCabinet.RETURN_STRAP), 1e-12, "reaches the lug");
        assertEquals(0.0, CoilCabinet.busHead(CoilCabinet.RETURN_STRAP), 1e-12, "and goes on along the bus at once");
        assertEquals(1.0, CoilCabinet.busHead(CoilCabinet.RETURN_STRAP + CoilCabinet.RETURN_BUS), 1e-12);
        assertEquals(1.0, CoilCabinet.terminalFlash(CoilCabinet.RETURN_STRAP + CoilCabinet.RETURN_BUS), 1e-12,
                "the terminal flashes as it arrives");
        assertEquals(0.0, CoilCabinet.terminalFlash(1.0), "not before");
        assertEquals(0.0, CoilCabinet.terminalFlash(CoilCabinet.RETURN_TICKS + 0.5), "nor after the return is over");
        final double hi = 16 + CoilCabinet.BUS_OUT;
        for (int c = 0; c < 4; c++) {
            final double[][] path = CoilCabinet.busPath(c);
            assertArrayEquals(CoilCabinet.lug(c), path[0], "corner " + c + " starts at its lug");
            assertArrayEquals(new double[] {CoilCabinet.TERMINAL_X, hi}, path[path.length - 1], "and ends at the terminal");
            for (int i = 0; i + 1 < path.length; i++) {
                assertTrue(path[i][0] == path[i + 1][0] || path[i][1] == path[i + 1][1], "the bus runs square");
                for (double[] p : new double[][] {path[i], path[i + 1]}) {
                    final boolean onRing = Math.abs(p[0] + CoilCabinet.BUS_OUT) < 1e-9 || Math.abs(p[0] - hi) < 1e-9
                            || Math.abs(p[1] + CoilCabinet.BUS_OUT) < 1e-9 || Math.abs(p[1] - hi) < 1e-9;
                    assertTrue(onRing, "corner " + c + " leaves the bus at " + p[0] + ", " + p[1]);
                }
            }
        }
    }

    @Test
    @DisplayName("the settings face: bezels round their boxes, apart; lamps and ladder on the plate, clear of the hit discs")
    void settingsFace() {
        assertTrue(CoilCabinet.ICON_HALF < CoilCabinet.BEZEL_IN, "the box's icon is cut by its bezel");
        assertTrue(CoilCabinet.TARGETS_Z + CoilCabinet.BEZEL_OUT < CoilCabinet.HEIGHT_Z - CoilCabinet.BEZEL_OUT,
                "the bezels overlap");
        for (double z : new double[] {CoilCabinet.TARGETS_Z, CoilCabinet.HEIGHT_Z}) {
            assertTrue(z - CoilCabinet.BEZEL_OUT > CoilCabinet.PLATE_Z0 && z + CoilCabinet.BEZEL_OUT < CoilCabinet.PLATE_Z1);
            assertTrue(CoilCabinet.BOX_Y - CoilCabinet.BEZEL_OUT > CoilCabinet.PLATE_Y0
                    && CoilCabinet.BOX_Y + CoilCabinet.BEZEL_OUT < CoilCabinet.PLATE_Y1);
        }
        assertTrue(CoilCabinet.LAMP_Z1 < CoilCabinet.TARGETS_Z - CoilCabinet.BOX_HALF, "a lamp sits in Targets' hit disc");
        assertTrue(CoilCabinet.LAMP_Z1 < CoilCabinet.TARGETS_Z - CoilCabinet.BEZEL_OUT, "a lamp sits on the bezel");
        assertTrue(CoilCabinet.LADDER_Z0 > CoilCabinet.HEIGHT_Z + CoilCabinet.BOX_HALF, "a notch sits in Height's hit disc");
        assertTrue(CoilCabinet.LADDER_Z0 > CoilCabinet.HEIGHT_Z + CoilCabinet.BEZEL_OUT, "a notch sits on the bezel");
        assertTrue(CoilCabinet.LAMP_Z0 > CoilCabinet.PLATE_Z0 && CoilCabinet.LADDER_Z1 < CoilCabinet.PLATE_Z1);
        for (int i = 0; i < CoilCabinet.LAMP_Y.length; i++) {
            assertTrue(CoilCabinet.LAMP_Y[i] > CoilCabinet.PLATE_Y0
                    && CoilCabinet.LAMP_Y[i] + CoilCabinet.LAMP_H < CoilCabinet.PLATE_Y1);
            if (i > 0) {
                assertTrue(CoilCabinet.LAMP_Y[i] > CoilCabinet.LAMP_Y[i - 1] + CoilCabinet.LAMP_H, "lamps overlap");
            }
        }
        assertEquals(4, CoilCabinet.NOTCH_Y.length, "a notch a stage");
        for (int i = 1; i < CoilCabinet.NOTCH_Y.length; i++) {
            assertTrue(CoilCabinet.NOTCH_Y[i] > CoilCabinet.NOTCH_Y[i - 1] + CoilCabinet.NOTCH_H, "notches overlap");
        }
        assertTrue(CoilCabinet.NOTCH_Y[3] + CoilCabinet.NOTCH_H < CoilCabinet.PLATE_Y1);
    }

    @Test
    @DisplayName("the transformer fits its bay: windings clear the walls, the back, the face and their legs")
    void transformerFits() {
        final double out = CoilCabinet.WIND_R + CoilCabinet.WIND_W;
        final double in = CoilCabinet.WIND_R - CoilCabinet.WIND_W;
        assertTrue(in > Math.hypot(CoilCabinet.LEG_HALF_X, CoilCabinet.LEG_HALF_Z), "a winding cuts its leg");
        assertTrue(CoilCabinet.LEG_X[0] - out > CoilCabinet.XF_X0 && CoilCabinet.LEG_X[1] + out < CoilCabinet.XF_X1);
        assertTrue(CoilCabinet.LEG_X[0] + out < CoilCabinet.LEG_X[1] - out, "the windings touch");
        assertTrue(CoilCabinet.LEG_Z - out > CoilCabinet.XF_BACK && CoilCabinet.LEG_Z + out < 16.0,
                "a winding pokes through the back panel or the south face");
        assertTrue(CoilCabinet.WIND_Y0 - CoilCabinet.WIND_W > CoilCabinet.YOKE_LOW
                && CoilCabinet.WIND_Y1 + CoilCabinet.WIND_W < CoilCabinet.YOKE_HIGH, "a winding runs into a yoke");
        assertTrue(CoilCabinet.YOKE_Y0 > CoilCabinet.XF_FLOOR && CoilCabinet.YOKE_Y1 < CoilCabinet.XF_TOP);
        assertTrue(CoilCabinet.DIAL_X - CoilCabinet.DIAL_R > 0 && CoilCabinet.DIAL_X + CoilCabinet.DIAL_R < CoilCabinet.XF_X0,
                "the LOAD gauge runs off its post");
        for (double deg = -CoilCabinet.NEEDLE_SWEEP; deg <= CoilCabinet.NEEDLE_SWEEP; deg += 5) {
            final double a = Math.toRadians(deg);
            final double tx = CoilCabinet.NEEDLE_LEN * Math.sin(a);
            final double ty = CoilCabinet.NEEDLE_Y + CoilCabinet.NEEDLE_LEN * Math.cos(a) - CoilCabinet.DIAL_Y;
            assertTrue(Math.hypot(tx, ty) < CoilCabinet.DIAL_R - 0.1, "the needle's tip leaves its dial at " + deg);
        }
    }
}
