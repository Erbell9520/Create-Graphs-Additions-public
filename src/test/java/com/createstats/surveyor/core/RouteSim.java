package com.createstats.surveyor.core;

final class RouteSim {
    private RouteSim() {
    }

    interface Obstacles {
        double ahead(MkIvSim sim, RouteRunner runner);

        Obstacles NONE = (sim, runner) -> Double.POSITIVE_INFINITY;
    }

    static final class World {
        Obstacles obstacles = Obstacles.NONE;
        boolean player;
        boolean block;
        Obstacles hazard = Obstacles.NONE;
        String hazardWhat = "";

        Obstacles gap = Obstacles.NONE;
        double speedAhead;
    }

    static RouteTrace drive(MkIvSim sim, RouteRunner runner, int ticks) {
        return drive(sim, runner, ticks, new World());
    }

    static RouteTrace drive(MkIvSim sim, RouteRunner runner, int ticks, Obstacles obstacles, boolean player) {
        World w = new World();
        w.obstacles = obstacles;
        w.player = player;
        return drive(sim, runner, ticks, w);
    }

    static RouteTrace drive(MkIvSim sim, RouteRunner runner, int ticks, World w) {
        RouteTrace t = new RouteTrace(ticks + 1);
        runner.start(sim.tick, true);
        for (int i = 0; i < ticks; i++) {
            step(sim, runner, w, t);
        }
        return t;
    }

    static RouteRunner.Command step(MkIvSim sim, RouteRunner runner, World w, RouteTrace t) {
        double obstacle = w.obstacles.ahead(sim, runner);
        double hazard = w.hazard.ahead(sim, runner);
        double gap = w.gap.ahead(sim, runner);
        boolean seen = Double.isFinite(obstacle);
        RouteRunner.Command c = runner.tick(sim.sensors(obstacle, w.player && seen, w.block && seen, gap, w.speedAhead,
                true, hazard, Double.isFinite(hazard) ? w.hazardWhat : ""));
        sim.step(c);
        if (t != null) {
            t.begin(sim.tick);
            runner.traceInto(t);
            sim.traceInto(t);
            t.end();
        }
        return c;
    }
}
