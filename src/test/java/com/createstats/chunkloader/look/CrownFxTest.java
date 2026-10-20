package com.createstats.chunkloader.look;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownFxTest {
    static final class Count implements CrownFx.Sink {
        int glows;
        int strokes;
        int bands;
        int floorBands;
        int flares;
        int stars;
        int fronts;
        int sparks;
        double floorAlpha;

        @Override
        public void glow(double x, double y, double z, double radius, int rgb, double alpha, double core,
                         double nudge) {
            glows++;
        }

        @Override
        public void stroke(double ax, double ay, double az, double bx, double by, double bz, double core,
                           double sheath, int coreRgb, int sheathRgb, double alpha) {
            strokes++;
        }

        @Override
        public void band(double y, double r0, double r1, double a0, double a1, int rgb, double alpha0,
                         double alpha1, boolean floor) {
            bands++;
            if (floor) {
                floorBands++;
                floorAlpha = Math.max(floorAlpha, Math.max(alpha0, alpha1));
            }
        }

        @Override
        public void flare(double x, double y, double z, double halfH, double halfV, double width, int coreRgb,
                          int glowRgb, double alpha) {
            flares++;
        }

        @Override
        public void star(double x, double y, double z, double size, double alpha) {
            stars++;
        }

        @Override
        public void front(double x0, double y0, double x1, double y1, double z, int rgb, double alpha) {
            fronts++;
        }

        @Override
        public void spark(double x, double y, double z) {
            sparks++;
        }
    }

    private static Count emit(CrownPose p) {
        final CrownAnchors a = new CrownAnchors();
        new CrownMesh().build(p, a, (q, uv, nx, ny, nz, m, g) -> {
        }, CrownMesh.LOD_FULL);
        final Count c = new Count();
        new CrownFx().emit(p, a, c, CrownMesh.LOD_FULL);
        return c;
    }

    private static CrownPose fiveAtTheHorn(boolean feed) {
        final CrownPose p = CrownPose.settled(5);
        p.spin = 0.0;

        p.tierAngle[0] = -36.0;
        p.feed = feed;
        return p;
    }

    @Test
    @DisplayName("horn-to-pod sparks are power going in: they fire while it arrives and stop the moment it does not")
    void hornSparksOnlyWhileFed() {
        final Count fed = emit(fiveAtTheHorn(true));
        final Count cut = emit(fiveAtTheHorn(false));
        assertTrue(fed.strokes > 0, "a pod at the horn with power arriving strikes it");
        assertTrue(fed.sparks > 0, "and throws sparks");
        assertEquals(0, cut.strokes, "with nothing arriving there is no spark to the horn (one ring: no ring arcs)");
        assertEquals(0, cut.sparks);
    }

    @Test
    @DisplayName("a dark crown lights nothing but nothing: no glow, no floor light, no sparks")
    void aDarkCrownIsDark() {
        final CrownPose p = CrownPose.item();
        final Count c = emit(p);
        assertEquals(0, c.glows);
        assertEquals(0, c.bands);
        assertEquals(0, c.strokes);
        assertEquals(0, c.fronts, "no numerals with nothing held");
    }

    @Test
    @DisplayName("the floor sigil is the fed ULTIMATE form's alone")
    void theSigilIsTheUltimates() {
        final Count twenty = emit(CrownPose.settled(20));
        final Count full = emit(CrownPose.settled(25));
        assertTrue(full.floorBands > twenty.floorBands + 20, "the sigil's circles, bands and 25 ticks at 25");
        final CrownPose riding = CrownPose.settled(25);
        riding.sigilAlpha = 0.0;
        assertEquals(twenty.floorBands, emit(riding).floorBands, "no sigil once it fades (riding through)");
    }

    @Test
    @DisplayName("the nixies show the count held: two numerals' dots, each with its glow")
    void theNixiesShowTheCount() {
        final CrownPose p = CrownPose.settled(13);
        final Count c = emit(p);
        final int dots = dots(1) + dots(3);
        assertEquals(2 * dots, c.fronts, "every lit dot and its halo");
        p.nixieLit = false;
        assertEquals(0, emit(p).fronts, "blinking off");
        final CrownPose seven = CrownPose.settled(7);
        assertEquals(2 * dots(7), emit(seven).fronts, "one numeral below ten");
    }

    private static int dots(int d) {
        int n = 0;
        for (String row : CrownFx.DIGITS[d]) {
            for (int i = 0; i < row.length(); i++) {
                n += row.charAt(i) == '1' ? 1 : 0;
            }
        }
        return n;
    }

    @Test
    @DisplayName("the floor light is as bright as the draw, and sags with the store")
    void theFloorLightFollowsTheDraw() {
        final CrownPose one = CrownPose.settled(1);
        final CrownPose twenty = CrownPose.settled(20);
        assertTrue(emit(twenty).floorAlpha > emit(one).floorAlpha * 1.5, "more draw, more light on the floor");
        final CrownPose sagging = CrownPose.settled(20);
        sagging.dim = 0.5;
        assertTrue(emit(sagging).floorAlpha < emit(twenty).floorAlpha * 0.6, "riding through, it sags");
    }

    @Test
    @DisplayName("the release flash is one hard star at every pod")
    void theReleaseFlash() {
        final CrownPose p = CrownPose.settled(25);
        for (int k = 0; k < 25; k++) {
            p.ink[k] = CrownPose.INK_OFF;
            p.glow[k] = 0;
        }
        p.flash = 1.0;
        assertEquals(25, emit(p).stars);
        p.flash = 0.0;
        assertEquals(0, emit(p).stars);
    }

    static final class Finite implements CrownFx.Sink {
        int checked;

        private void check(String what, double... values) {
            for (double v : values) {
                assertTrue(Double.isFinite(v), what + " carries " + v);
            }
            checked++;
        }

        @Override
        public void glow(double x, double y, double z, double radius, int rgb, double alpha, double core,
                         double nudge) {
            check("a glow", x, y, z, radius, alpha, core, nudge);
        }

        @Override
        public void stroke(double ax, double ay, double az, double bx, double by, double bz, double core,
                           double sheath, int coreRgb, int sheathRgb, double alpha) {
            check("a stroke", ax, ay, az, bx, by, bz, core, sheath, alpha);
        }

        @Override
        public void band(double y, double r0, double r1, double a0, double a1, int rgb, double alpha0,
                         double alpha1, boolean floor) {
            check("a band", y, r0, r1, a0, a1, alpha0, alpha1);
        }

        @Override
        public void flare(double x, double y, double z, double halfH, double halfV, double width, int coreRgb,
                          int glowRgb, double alpha) {
            check("a flare", x, y, z, halfH, halfV, width, alpha);
        }

        @Override
        public void star(double x, double y, double z, double size, double alpha) {
            check("a star", x, y, z, size, alpha);
        }

        @Override
        public void front(double x0, double y0, double x1, double y1, double z, int rgb, double alpha) {
            check("a numeral", x0, y0, x1, y1, z, alpha);
        }

        @Override
        public void spark(double x, double y, double z) {
            check("a spark", x, y, z);
        }
    }

    @Test
    @DisplayName("every light the effects emit is a finite number, through a whole growth, blackout and comeback")
    void everyLightIsFinite() {
        final CrownMotion m = new CrownMotion();
        final CrownMesh mesh = new CrownMesh();
        final CrownFx fx = new CrownFx();
        final CrownAnchors a = new CrownAnchors();
        final CrownPose p = new CrownPose();
        final Finite sink = new Finite();
        final CrownMesh.Sink quads = (q, uv, nx, ny, nz, mat, g) -> {
            for (float v : q) {
                assertTrue(Float.isFinite(v), "a quad corner carries " + v);
            }
        };
        m.tick(CrownMotionTest.fed(1));
        for (int i = 0; i < 700; i++) {
            final int n = i < 400 ? Math.min(25, 1 + i / 12) : 25;
            final com.createstats.chunkloader.core.CrownState s = i < 450 ? CrownMotionTest.fed(n)
                    : i < 550 ? CrownMotionTest.state(25, 25, com.createstats.chunkloader.core.CrownHold.Phase.RIDING,
                    1.0 - (i - 450) / 100.0, false, 1)
                    : i < 620 ? CrownMotionTest.state(0, 0, com.createstats.chunkloader.core.CrownHold.Phase.DARK, 0,
                    false, 1)
                    : CrownMotionTest.state((i - 620) / 10 + 1, (i - 620) / 10 + 1,
                    com.createstats.chunkloader.core.CrownHold.Phase.FED, 0.3, true, 2);
            m.tick(s);
            for (float partial : new float[] {0.25f, 1.0f}) {
                m.pose(partial, p);
                mesh.build(p, a, quads, CrownMesh.LOD_FULL);
                fx.emit(p, a, sink, CrownMesh.LOD_FULL);
            }
        }
        assertTrue(sink.checked > 100_000, "the walk lit almost nothing: " + sink.checked);
    }

    @Test
    @DisplayName("something in the way: the cell is outlined, and the ring that wants to stand there is dashed in")
    void somethingInTheWay() {
        final CrownPose p = CrownPose.settled(5);
        p.spin = 0;
        p.tierAngle[0] = 0;
        p.feed = false;
        final int before = emit(p).strokes;
        p.blocked = true;
        p.blockUp = 1;
        p.wantForm = 9;
        final int after = emit(p).strokes;
        assertEquals(before + 12 + 12, after, "twelve edges, and the second ring dashed (12 dashes)");
    }
}
