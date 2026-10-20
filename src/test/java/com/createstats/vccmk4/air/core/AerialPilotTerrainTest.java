package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialPilotTerrainTest {
    private static final AerialPilot.Keys SEATED = new AerialPilot.Keys(true, 0.0, 0.0, 0.0, 0.0, false);

    private static final int READ_EVERY = 10;
    private static final double HALF_LENGTH = 6.0;
    private static final double HALF_WIDTH = 3.0;

    private final AirPlant plant = new AirPlant();
    private final AerialPilot pilot = new AerialPilot();
    private final BurnerDither dither = new BurnerDither();
    private int ticks;

    private AerialPilot.Command fly(AerialPilot.Keys k, double[] destination, double[] track) {
        if (ticks++ % READ_EVERY == 0) {
            double[] u = track != null ? track : TerrainGuard.track(pilot.mode() == AerialPilot.Mode.GO_TO, destination,
                    plant.x, plant.z, plant.sense().vx(), plant.sense().vz(), plant.heading);
            pilot.terrain(plant.profile(u[0], u[1], HALF_LENGTH, HALF_WIDTH));
        }
        return plant.fly(pilot, dither, k, destination);
    }

    private void run(AerialPilot.Keys k, double seconds) {
        for (int i = 0; i < (int) Math.round(seconds / AirPlant.DT); i++) fly(k, null, new double[] {1.0, 0.0});
    }

    private void airborne() {
        plant.heading = 90.0;
        plant.trim();
        plant.signals = dither.signals(plant.gas, plant.dials);
        run(SEATED, 20.0);
    }

    private void ridge(double x0) {
        plant.ground = (px, pz) -> {
            double d = px - x0;
            if (d < 80.0 || d > 150.0) return plant.groundY;
            if (d < 105.0) return 60.0 + 35.0 * (d - 80.0) / 25.0;
            if (d <= 125.0) return 95.0;
            return 95.0 - 35.0 * (d - 125.0) / 25.0;
        };
    }

    private double keelClearance() {
        return plant.y - plant.keelBelow - plant.groundAt(plant.x, plant.z);
    }

    @Test
    void goToClimbsOverARidgeAndSettlesBackToItsHeight() {
        airborne();
        pilot.cycleLeaveMode();
        assertEquals(LeaveMode.GO_TO, pilot.cycleLeaveMode());
        double x0 = plant.x;
        ridge(x0);
        double[] target = {x0 + 240.0, plant.z};
        double worst = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        boolean climbed = false;
        int t = 0;
        for (; t < 20 * 300 && pilot.mode() != AerialPilot.Mode.ANCHOR; t++) {
            AerialPilot.Command c = fly(AerialPilot.Keys.NONE, target, null);
            worst = Math.min(worst, keelClearance());
            highest = Math.max(highest, plant.y);
            climbed |= pilot.guard().verdict() == TerrainGuard.Verdict.CLIMB;
            assertEquals(80.0, c.targetAltitude(), 1.0e-6, "the height held stays the ship's own");
        }
        assertTrue(climbed, "the guard climbed for the ridge");
        assertEquals(AerialPilot.Mode.ANCHOR, pilot.mode(), "arrived, in " + t / 20 + " s");
        assertTrue(worst > TerrainGuard.CLEARANCE * 0.5, "the keel came within " + worst + " blocks of the ground");
        assertTrue(highest > 95.0 + plant.keelBelow + TerrainGuard.CLEARANCE * 0.5, "it went over the top: " + highest);
        for (int i = 0; i < 20 * 60; i++) fly(AerialPilot.Keys.NONE, target, null);
        assertEquals(80.0, plant.y, 1.5, "and back down to its own height past the ridge");
    }

    @Test
    void keepCourseStopsShortOfGroundAboveItsCeiling() {
        airborne();
        pilot.setup().altitudeCap = 90.0;
        assertEquals(LeaveMode.KEEP_COURSE, pilot.cycleLeaveMode());

        for (int i = 0; i < 2; i++) {
            run(new AerialPilot.Keys(true, 1.0, 0.0, 0.0, 0.0, false), 0.1);
            run(SEATED, 0.05);
        }
        run(SEATED, 20.0);
        double x0 = plant.x;
        double wall = x0 + 150.0;
        plant.ground = (px, pz) -> px >= wall ? 120.0 : plant.groundY;
        AerialPilot.Command c = null;
        for (int i = 0; i < 20 * 120; i++) {
            c = fly(AerialPilot.Keys.NONE, null, new double[] {1.0, 0.0});
            assertTrue(plant.x < wall, "flew into the wall at x " + plant.x);
        }
        assertEquals(AerialPilot.Mode.KEEP_COURSE, c.mode());
        assertTrue(Math.abs(plant.u) < 0.5, "stopped: " + plant.u);
        assertTrue(wall - plant.x > HALF_LENGTH, "short of the wall: " + (wall - plant.x));
        assertTrue(c.notes().stream().anyMatch(n -> n.startsWith("Terrain ahead above the ceiling")), c.notes().toString());
    }

    @Test
    void thePilotIsWarnedAndNeverOverruled() {
        airborne();
        double x0 = plant.x;
        ridge(x0);

        for (int i = 0; i < 2; i++) {
            run(new AerialPilot.Keys(true, 1.0, 0.0, 0.0, 0.0, false), 0.1);
            run(SEATED, 0.05);
        }
        boolean warned = false;
        for (int i = 0; i < 20 * 60 && plant.x < x0 + 70.0; i++) {
            AerialPilot.Command c = fly(SEATED, null, new double[] {1.0, 0.0});
            if (c.notes().stream().anyMatch(n -> n.startsWith("Terrain ahead"))) {
                warned = true;
                assertEquals(c.targetAltitude(), pilot.flownAltitude(), 1.0e-9, "the pilot's height is flown, not the guard's");
            }
        }
        assertTrue(warned, "warned before the ridge");
    }
}
