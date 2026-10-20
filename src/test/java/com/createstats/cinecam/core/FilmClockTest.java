package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilmClockTest {
    private static final double TOL = 1e-4;

    private record Drawn(long frame, double moment, long missed, boolean late) {
    }

    private static List<Drawn> play(double[] gapsMs, double tickMs, int fps, double speed) {
        return play(gapsMs, tickMs, fps, speed, 1, false);
    }

    private static List<Drawn> play(double[] gapsMs, double tickMs, int fps, double speed, int max, boolean catchUp) {
        double tpf = FilmClock.ticksPerFrame(fps, speed);
        double ms = 7.0;
        double lastNow = Double.NaN;
        double start = Double.NaN;
        long[] last = {-1};
        long prevTick = (long) Math.floor(ms / tickMs);
        List<Drawn> out = new ArrayList<>();
        for (double gap : gapsMs) {
            ms += gap;
            long tick = (long) Math.floor(ms / tickMs);
            if (catchUp && !Double.isNaN(start)) {
                for (long k = prevTick; k < tick; k++) {
                    for (FilmClock.Plan p : FilmClock.catchUp(start, tpf, last[0], k, 12)) take(out, p, k, start, tpf, last);
                }
            }
            prevTick = tick;
            float partial = (float) (ms / tickMs - tick);
            double now = tick - 1 + partial;
            double next = Double.isNaN(lastNow) || now < lastNow ? now : now + (now - lastNow);
            lastNow = now;
            if (Double.isNaN(start)) start = now;
            for (FilmClock.Plan p : FilmClock.draws(start, tpf, last[0], tick, partial, next, max)) take(out, p, tick, start, tpf, last);
        }
        return out;
    }

    private static void take(List<Drawn> out, FilmClock.Plan p, long tick, double start, double tpf, long[] last) {
        assertTrue(p.partial() >= 0f && p.partial() <= 1f, "a moment between the two ticks the frame interpolates");
        assertTrue(p.frame() > last[0], "never a frame twice");
        double moment = tick - 1 + p.partial();
        double own = FilmClock.timeOf(start, p.frame(), tpf);
        boolean late = Math.abs(moment - own) > TOL;
        assertEquals(late, p.late(), "the plan says which frames are late");
        if (late) {
            assertEquals(0f, p.partial(), 0f, "a late frame is drawn at the start of the frame's two ticks");
            assertTrue(moment > own && moment - own <= tpf / 2 + TOL, "and late by under half a frame of film");
        }
        assertTrue(out.isEmpty() || moment > out.get(out.size() - 1).moment(), "the film never steps back");
        out.add(new Drawn(p.frame(), moment, p.missed(), late));
        last[0] = p.frame();
    }

    @Test
    void extraPassesDrawWhatTheGameCannot() {
        List<Drawn> alone = play(steady(400, 40), 50.0, 60, 1.0, 1, false);
        assertTrue(missed(alone) > 100, "alone a 40 fps game holds a third of a 60 fps film");
        List<Drawn> d = play(steady(400, 40), 50.0, 60, 1.0, 2, false);
        assertEquals(0, missed(d), "with an extra pass where needed, none");
        assertEquals(0, late(d));
        for (int i = 1; i < d.size(); i++) assertEquals(1.0 / 3.0, d.get(i).moment() - d.get(i - 1).moment(), TOL);

        long frames = steady(600, 90).length;
        assertTrue(play(steady(600, 90), 50.0, 60, 1.0, 2, false).size() <= frames, "a 90 fps game needs no extras");
    }

    @Test
    void aStallIsCaughtUpBetweenTheTicks() {
        double[] gaps = steady(300, 75);
        gaps[150] = 200.0;
        List<Drawn> held = play(gaps, 50.0, 60, 1.0, 1, false);
        assertTrue(missed(held) >= 8, "uncaught, a 200 ms stall holds about ten frames of a 60 fps film: " + missed(held));
        List<Drawn> caught = play(gaps, 50.0, 60, 1.0, 1, true);
        assertEquals(0, missed(caught), "caught up between the ticks, none");
        for (int i = 1; i < caught.size(); i++) {
            assertEquals(1.0 / 3.0, caught.get(i).moment() - caught.get(i - 1).moment(), TOL, "every frame at its moment");
        }
    }

    @Test
    void aCatchUpTakesTheWholeTickInOrder() {
        double tpf = 1.0 / 3.0;

        List<FilmClock.Plan> d = FilmClock.catchUp(0.0, tpf, 11, 5, 12);
        assertEquals(4, d.size(), "12, 13, 14 and 15 (at 5.0, the tick itself)");
        for (int i = 0; i < d.size(); i++) {
            assertEquals(12 + i, d.get(i).frame());
            assertEquals(i / 3.0, d.get(i).partial(), TOL);
            assertEquals(0, d.get(i).missed());
        }
        assertEquals(2, FilmClock.catchUp(0.0, tpf, 11, 5, 2).size(), "no more than asked");
        assertTrue(FilmClock.catchUp(0.0, tpf, 15, 5, 12).isEmpty(), "nothing left in the tick");
    }

    private static double[] jitter(int n, double loFps, double hiFps, long seed) {
        Random r = new Random(seed);
        double[] g = new double[n];
        for (int i = 0; i < n; i++) g[i] = 1000.0 / (loFps + r.nextDouble() * (hiFps - loFps));
        return g;
    }

    private static double[] steady(int n, double fps) {
        double[] g = new double[n];
        java.util.Arrays.fill(g, 1000.0 / fps);
        return g;
    }

    private static long missed(List<Drawn> d) {
        return d.stream().mapToLong(Drawn::missed).sum();
    }

    private static long late(List<Drawn> d) {
        return d.stream().filter(Drawn::late).count();
    }

    @Test
    void theOwnersGameFilmsEveryFrameAtItsMoment() {
        for (int fps : new int[]{60, 30}) {
            for (long seed = 1; seed <= 20; seed++) {
                List<Drawn> d = play(jitter(1200, 60, 75, seed), 50.0, fps, 1.0);
                assertEquals(0, missed(d), fps + " fps, none held, seed " + seed);
                assertEquals(0, late(d), fps + " fps, none late, seed " + seed);
                for (int i = 0; i < d.size(); i++) assertEquals(i, d.get(i).frame(), "every frame in turn, seed " + seed);

                for (int i = 1; i < d.size(); i++) {
                    assertEquals(20.0 / fps, d.get(i).moment() - d.get(i - 1).moment(), TOL, "even steps, seed " + seed);
                }
            }
        }
    }

    @Test
    void theSmoothRateFilmsEverySpeedWhole() {
        for (int fps : FilmFormat.RATES) {
            for (FilmFormat.Speed v : FilmFormat.Speed.values()) {
                for (boolean slowed : new boolean[]{false, true}) {
                    if (slowed && !v.slow()) continue;
                    int smooth = FilmClock.smoothFps(fps, v.factor(), slowed);
                    double tickMs = 50.0 / (slowed ? v.factor() : 1.0);
                    String at = fps + " fps at " + v.label() + (slowed ? " slowed" : "") + ", game " + smooth;
                    List<Drawn> d = play(steady(1500, smooth), tickMs, fps, v.factor());
                    assertEquals(0, missed(d) + late(d), at);
                    for (long seed = 1; seed <= 3; seed++) {
                        d = play(jitter(1500, smooth, smooth * 1.33, seed), tickMs, fps, v.factor());
                        assertEquals(0, missed(d) + late(d), at + " jittering, seed " + seed);
                    }
                }
            }
        }
    }

    @Test
    void theSmoothRateIsWhatEachTickNeeds() {
        assertEquals(50, FilmClock.smoothFps(24, 1.0, false), "two frames of film in some ticks, a quarter to spare");
        assertEquals(50, FilmClock.smoothFps(25, 1.0, false));
        assertEquals(50, FilmClock.smoothFps(30, 1.0, false));
        assertEquals(75, FilmClock.smoothFps(50, 1.0, false), "three");
        assertEquals(75, FilmClock.smoothFps(60, 1.0, false));
        assertEquals(75, FilmClock.smoothFps(30, 0.5, false), "slow motion in a normal world: three a tick");
        assertEquals(38, FilmClock.smoothFps(30, 0.5, true), "and in a world slowed to match, at ten ticks a second");
        assertEquals(75, FilmClock.smoothFps(60, 0.25, true), "twelve a tick at five ticks a second");
        assertEquals(25, FilmClock.smoothFps(30, 10.0, false), "a time-lapse: a frame of film a tick at most");
    }

    @Test
    void aSlowGameHoldsOnlyWhatItCannotDraw() {
        List<Drawn> d = play(steady(400, 40), 50.0, 60, 1.0);
        long held = missed(d);
        long frames = d.get(d.size() - 1).frame() + 1;
        assertEquals(frames, d.size() + held, "every frame of film is drawn or held, none lost");
        assertEquals(1.0 / 3.0, held / (double) frames, 0.01, "a third held, which is what 40 against 60 leaves");
    }

    @Test
    void aGameBarelyFasterThanItsFilmHoldsFew() {
        for (int fps : new int[]{24, 30, 60}) {
            List<Drawn> d = play(steady(1200, fps * 1.1), 50.0, fps, 1.0);
            double frames = d.get(d.size() - 1).frame() + 1;
            assertTrue(missed(d) / frames < 0.15, fps + " fps at x1.1 holds " + missed(d) / frames);
        }

        List<Drawn> d = play(steady(1800, 36), 50.0, 30, 1.0);
        assertEquals(0, missed(d), "none held");
        assertTrue(late(d) > 0, "some drawn late, under half a frame each (checked in play)");
    }

    @Test
    void aTimeLapseDrawsFewerFrames() {
        double tpf = FilmClock.ticksPerFrame(30, 10.0);
        assertEquals(20.0 / 3.0, tpf, 1e-12);
        List<Drawn> d = play(steady(60 * 30, 60), 50.0, 30, 10.0);
        assertEquals(0, missed(d) + late(d));
        assertEquals(90, d.size(), 1, "thirty seconds of play, three seconds of film");
        for (int i = 1; i < d.size(); i++) assertEquals(tpf, d.get(i).moment() - d.get(i - 1).moment(), TOL);
        for (int fps : FilmFormat.RATES) {
            assertEquals(20.0 * 60.0 / fps, FilmClock.ticksPerFrame(fps, 60.0), 1e-9, "60x: a minute of play per second of film");
        }
    }

    @Test
    void slowMotionInASlowedWorldCostsWhatNormalDoes() {
        List<Drawn> d = play(steady(600, 45), 100.0, 30, 0.5);
        assertEquals(0, missed(d) + late(d), "a 45 fps game films 30 fps slow motion whole when the world slows with it");
        for (int i = 1; i < d.size(); i++) assertEquals(1.0 / 3.0, d.get(i).moment() - d.get(i - 1).moment(), TOL);

        assertEquals(400, d.size(), 2);
    }

    @Test
    void slowMotionInANormalWorldNeedsMoreFrames() {
        assertEquals(0, missed(play(steady(900, 100), 50.0, 30, 0.5)), "100 fps covers 30 fps at 1/2x (60 needed)");
        assertTrue(missed(play(steady(900, 45), 50.0, 30, 0.5)) > 0, "45 fps does not");
    }

    @Test
    void aFrameWaitsForItsMomentOnlyWhileTheTickHasFramesEnough() {
        double tpf = 1.0 / 3.0;

        assertNull(FilmClock.plan(0.0, tpf, 0, 1, 0.2f, 0.25));

        FilmClock.Plan early = FilmClock.plan(0.0, tpf, 0, 1, 0.2f, 0.6);
        assertEquals(1, early.frame());
        assertEquals(1.0 / 3.0, early.partial(), TOL);

        FilmClock.Plan behind = FilmClock.plan(0.0, tpf, 0, 1, 0.9f, 1.0);
        assertEquals(1, behind.frame());
        assertEquals(1.0 / 3.0, behind.partial(), TOL, "frame 1 at 1/3, behind the clock's 0.9");

        FilmClock.Plan ahead = FilmClock.plan(0.0, tpf, 2, 1, 0.9f, 1.3);
        assertEquals(3, ahead.frame());
        assertEquals(1.0, ahead.partial(), TOL);
        assertNull(FilmClock.plan(0.0, tpf, 2, 1, 0.9f, 0.95));

        assertEquals(3, FilmClock.plan(0.0, tpf, 2, 1, 0.9f, 0.9).frame());

        assertNull(FilmClock.plan(0.0, tpf, 3, 1, 0.99f, 1.5), "frame 4 (4/3) waits for tick 2");
    }

    @Test
    void aMissedFrameIsDrawnLateOrHeld() {
        double tpf = 1.0 / 3.0;

        FilmClock.Plan held = FilmClock.plan(0.0, tpf, 0, 3, 0.1f, 2.2);
        assertEquals(6, held.frame(), "the first frame from tick 2 on");
        assertEquals(5, held.missed(), "frames 1 to 5 held");
        assertEquals(0.0, held.partial(), TOL);

        assertEquals(6, FilmClock.plan(0.1, tpf, 4, 3, 0.1f, 2.2).frame());

        FilmClock.Plan late = FilmClock.plan(0.2, tpf, 4, 3, 0.1f, 2.15);
        assertEquals(5, late.frame());
        assertEquals(0, late.missed());
        assertEquals(0.0, late.partial(), 0.0);

        FilmClock.Plan scarce = FilmClock.plan(0.2, tpf, 4, 3, 0.1f, 2.6);
        assertEquals(6, scarce.frame());
        assertEquals(1, scarce.missed(), "frame 5 held");
        assertEquals(0.2, scarce.partial(), TOL);
    }

    @Test
    void theTakeEndsAtTheLastMomentCome() {
        assertEquals(-1, FilmClock.frameAt(10.0, 9.0, 0.5), "before the first frame");
        assertEquals(0, FilmClock.frameAt(10.0, 10.0, 0.5));
        assertEquals(3, FilmClock.frameAt(10.0, 11.75, 0.5));
        assertEquals(4, FilmClock.frameAt(10.0, 12.0, 0.5), "a moment exactly on a frame is that frame");
    }

    @Test
    void aFreezeIsHeldAtMostTwoSeconds() {
        assertEquals(0, FilmClock.repeatsBefore(-1, 50, 60), "the first frame holds nothing before it");
        assertEquals(0, FilmClock.repeatsBefore(9, 10, 60));
        assertEquals(4, FilmClock.repeatsBefore(10, 15, 60));
        assertEquals((long) FilmClock.MAX_HOLD_SECONDS * 60, FilmClock.repeatsBefore(0, 100_000, 60));
        assertEquals(1.5, FilmClock.seconds(90, 60), 1e-12);
        for (int fps : FilmFormat.RATES) {
            assertEquals(2L * fps, FilmClock.repeatsBefore(0, 1_000_000, fps), "held two seconds at most");
            assertEquals(1.0, FilmClock.seconds(fps, fps), 1e-12);
        }
    }

    @Test
    void anHourIsAnHour() {
        for (int fps : FilmFormat.RATES) {
            double tpf = FilmClock.ticksPerFrame(fps, 1.0);
            long frames = 3600L * fps;
            assertEquals(72_000.0, FilmClock.timeOf(0.0, frames, tpf), 1e-6, fps + " fps");
            assertEquals(frames, FilmClock.frameAt(0.0, 72_000.0, tpf), fps + " fps");
        }
    }
}
