package com.createstats.surveyor.core;

import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.SteerPreset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverSweepTest {
    private static final UUID OWNER = new UUID(3, 4);

    static Pathway track() {
        return RouteFollowerSimTest.pathway(RouteFollowerSimTest.oval(60, 22, 64), PathKind.LOOP);
    }

    record Case(String name, SteerPreset preset, int axles, double wheelbase, double halfTrack, int lock, boolean speedSteer,
                boolean trailer, boolean heavy, int steerTicks, int throttleTicks) {
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        Object[][] layouts = {
                {SteerPreset.FRONT, 2, 3.0, 1.0},
                {SteerPreset.TWIN_FRONT, 4, 6.0, 1.5},
                {SteerPreset.FRONT_REAR, 3, 5.0, 1.5},
                {SteerPreset.ALL, 2, 5.0, 2.0},
                {SteerPreset.SKID, 3, 4.0, 1.5},
        };
        for (Object[] l : layouts) {
            for (int lock : new int[]{15, 30}) {
                for (boolean speedSteer : new boolean[]{false, true}) {
                    for (boolean trailer : new boolean[]{false, true}) {
                        for (boolean heavy : new boolean[]{false, true}) {
                            SteerPreset p = (SteerPreset) l[0];
                            String name = String.format(Locale.ROOT, "%s lock %d%s%s %s", p.key(), lock,
                                    speedSteer ? " speed-lock" : "", trailer ? " trailer" : "", heavy ? "heavy" : "light");
                            out.add(new Case(name, p, (int) l[1], (double) l[2], (double) l[3], lock, speedSteer, trailer,
                                    heavy, 8, 6));
                        }
                    }
                }
            }
        }

        out.add(new Case("owner's car, ramps 2 and 0", SteerPreset.ALL, 2, 5.0, 2.0, 30, true, true, false, 2, 0));
        out.add(new Case("owner's car, ramps 30 and 60", SteerPreset.ALL, 2, 5.0, 2.0, 30, true, true, false, 30, 60));
        return out;
    }

    static RouteTrace.Measures run(Case c, Pathway p) {
        DriveModel.Options o = new DriveModel.Options(true, c.speedSteer(), c.steerTicks(), c.lock(), 256, 50,
                c.throttleTicks(), DriveModel.Options.AS_RAMP, true, true);
        double k = c.heavy() ? 11.18 : 12.03;
        double tau = c.heavy() ? 1.66 : 0.665;
        MkIvSim sim = MkIvSim.of(c.preset(), c.axles(), c.wheelbase(), c.halfTrack(), o, k, tau, c.trailer())
                .at(0, 0.3, 90).noisy(0.15, 0.02);
        PlantModel plant = new PlantModel(k, tau, tau / 5.2, 1000, 100, 0.0);
        if (c.preset() == SteerPreset.SKID) plant.seedSkid(2.0 * k / sim.track);
        RouteRunner runner = new RouteRunner(p, plant);
        return RouteSim.drive(sim, runner, 20 * 120).measures();
    }

    @Test
    void aWrongModelIsTakenUpByTheFeedback() {
        Pathway p = OwnerPathways.plaza();
        double[][] wrong = {{1.1, 0.7}, {0.9, 1.5}, {1.1, 2.5}};
        List<String> failures = new ArrayList<>();
        for (double[] w : wrong) {
            MkIvSim sim = MkIvSim.carrier().at(268.0, -2.5, 270.0).atY(63.0).noisy(0.15, 0.02);
            PlantModel believed = new PlantModel(sim.k * w[0], sim.tau * w[1], sim.tau * w[1] / 5.2, 1000, 100, 0.0);
            RouteTrace.Measures m = RouteSim.drive(sim, new RouteRunner(p, believed), 20 * 120).measures();
            System.out.printf(Locale.ROOT, "model K x%.1f tau x%.1f: %s%n", w[0], w[1], m);
            if (m.jolts() > 0 || m.surgesPerMinute() > 2.0 || m.speedErrorRms() > 0.3
                    || m.worstSlowing() > p.comfort().decel * 1.25 + 1.0e-9) {
                failures.add("K x" + w[0] + " tau x" + w[1] + ": " + m);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void everyLayoutOnEverySettingDrivesCalmly() {
        Pathway p = track();
        List<String> failures = new ArrayList<>();
        StringBuilder table = new StringBuilder();
        for (Case c : cases()) {
            RouteTrace.Measures m = run(c, p);
            table.append(String.format(Locale.ROOT, "%-44s %s%n", c.name(), m));
            Comfort comfort = p.comfort();
            List<String> bad = new ArrayList<>();
            if (m.jolts() > 0) bad.add("jolts");
            if (m.surgesPerMinute() > 2.0) bad.add("surges");
            if (m.speedErrorRms() > 0.3) bad.add("speed error");
            if (m.worstSlowing() > comfort.decel * 1.25 + 1.0e-9) bad.add("slowing");
            if (m.sideChangesPerSecond() >= 0.25) bad.add("weave");
            if (m.wheelHuntPerSecond() > 0.5) bad.add("hunting");
            if (m.crossTrackRms() > 0.35 || m.crossTrackMax() > 1.0) bad.add("cross-track");
            if (!bad.isEmpty()) failures.add(c.name() + ": " + String.join(", ", bad) + " (" + m + ")");
        }
        System.out.print(table);
        assertTrue(failures.isEmpty(), failures.size() + " of " + cases().size() + " fail a bound:\n" + String.join("\n", failures));
    }
}
