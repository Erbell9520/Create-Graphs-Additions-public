package com.createstats.vccmk4.air.core;

import com.createstats.vcu.core.Axis6;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevitationTest {
    private static final double G = 11.0;
    private static final AerialPilot.Keys SEATED = new AerialPilot.Keys(true, 0.0, 0.0, 0.0, 0.0, false);

    private static Levitation.Cluster levitite(double blocks) {
        return new Levitation.Cluster(10.0, blocks, 1.0, true, false, true, 2.0, 0.1, 3.0);
    }

    @Test
    void levititeHoldsTheShipsWeightAndNeverMore() {
        Levitation lev = Levitation.of(List.of(levitite(120.0)), G);
        assertEquals(600.0 * G, lev.lift(600.0, G), 1.0e-9, "1200 kpg of levitite under a 600 kpg ship holds exactly its weight");
        assertEquals(1200.0 * G, lev.lift(1500.0, G), 1.0e-9, "too little levitite lifts what it can, all of it");
        assertTrue(lev.any());
    }

    @Test
    void aMaterialThatLiftsItselfLiftsInFull() {
        Levitation.Cluster free = new Levitation.Cluster(4.0, 50.0, 1.0, false, false, false, 0.0, 0.0, 0.0);
        Levitation lev = Levitation.of(List.of(levitite(20.0), free), G);
        assertEquals(G * (200.0 + 200.0), lev.lift(600.0, G), 1.0e-9);
        assertEquals(G * (200.0 + 100.0), lev.lift(100.0, G), 1.0e-9, "the free part is not capped by the ship's mass");
    }

    @Test
    void theBrakingIsLevititesOwnAndTheEnvelopesAreLeftToTheirPrior() {
        Levitation.Cluster envelope = new Levitation.Cluster(0.0, 0.33 * 100.0, 0.9, false, true, false, 0.0, 1.0, 0.0);
        Levitation lev = Levitation.of(List.of(levitite(10.0), envelope), G);
        assertEquals(10.0 * (2.0 + 0.1) * G, lev.drag(), 1.0e-9);
        assertEquals(0.0, Levitation.of(List.of(envelope), G).lift(600.0, G), 0.0);
        assertFalse(Levitation.of(List.of(envelope), G).any());
        Levitation.Cluster noTransition = new Levitation.Cluster(10.0, 10.0, 1.0, true, false, false, 2.0, 0.1, 0.0);
        assertEquals(10.0 * 0.1, Levitation.of(List.of(noTransition), G).drag(), 1.0e-9, "no transition speed, no slow friction (Sable)");
    }

    @Test
    void aPressureMaterialLiftsWithTheAirAndNothingReadIsNothing() {
        Levitation.Cluster thin = new Levitation.Cluster(10.0, 10.0, 0.5, true, true, false, 0.0, 0.0, 0.0);
        assertEquals(50.0 * G, Levitation.of(List.of(thin), G).lift(600.0, G), 1.0e-9);
        assertEquals(0.0, Levitation.of(List.of(), G).lift(600.0, G), 0.0);
        assertEquals(0.0, Levitation.of(null, G).lift(600.0, G), 0.0);
        assertEquals(0.0, Levitation.NONE.lift(600.0, G), 0.0);
    }

    private static final class Ship {
        final PressureModel air = PressureModel.sable(63.0);
        double mass = 600.0;
        double blocks = 120.0;
        double y = 120.0;
        double vy;
        double maxUp = 3000.0;
        double maxDown = 3000.0;
        double up;

        double friction() {
            double rate = blocks * (0.1 + 2.0 * Math.exp(-vy * vy / 6.0)) * G + VerticalObserver.UNIVERSAL_DRAG * mass;
            return -rate * vy;
        }

        void step() {
            double lift = Math.min(10.0 * blocks, mass) * G;
            vy += (lift - mass * G + up + friction()) / mass * AerialPilot.DT;
            y += vy * AerialPilot.DT;
        }

        AerialPilot.Sense sense() {
            double p = air.at(y);
            PressureModel fitted = PressureModel.measured(y, p, air.at(y + PressureModel.PROBE), PressureModel.PROBE, 63.0);
            return new AerialPilot.Sense(y, vy, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, mass, G, 60000.0, fitted, p, List.of(), 0.0,
                    y - 60.0, 320.0, new AerialPilot.Thrust(0.0, 0.0, 0.0, 0.0, 0.0, maxUp, maxDown, 0.0));
        }

        Levitation levitation() {
            return Levitation.of(List.of(levitite(blocks)), G);
        }

        AerialPilot pilot(boolean counted) {
            AerialPilot pilot = new AerialPilot();
            Levitation lev = counted ? levitation() : Levitation.NONE;
            pilot.levitation(lev);
            pilot.dragPrior(VerticalObserver.physicsDrag(0, mass) + lev.drag());
            return pilot;
        }

        List<AerialPilot.Command> fly(AerialPilot pilot, double seconds) {
            List<AerialPilot.Command> out = new ArrayList<>();
            int ticks = (int) Math.round(seconds / AerialPilot.DT);
            for (int i = 0; i < ticks; i++) {
                AerialPilot.Command c = pilot.tick(sense(), SEATED, null);
                up = Math.max(-maxDown, Math.min(maxUp, c.wrench()[Axis6.UP.index()]));
                pilot.applied(0.0, up, 0.0);
                step();
                out.add(c);
            }
            return out;
        }
    }

    private static double meanAbsUp(List<AerialPilot.Command> cs, int from) {
        double s = 0.0;
        for (int i = from; i < cs.size(); i++) s += Math.abs(cs.get(i).wrench()[Axis6.UP.index()]);
        return s / (cs.size() - from);
    }

    @Test
    void withoutTheCountTheThrustersCarryHalfTheShip() {
        Ship ship = new Ship();
        AerialPilot pilot = ship.pilot(false);
        List<AerialPilot.Command> cs = ship.fly(pilot, 30.0);
        double weight = ship.mass * G;
        assertTrue(meanAbsUp(cs, cs.size() / 2) > 0.3 * weight,
                "the control: as 1.15.38 flew it, the thrusters hold " + meanAbsUp(cs, cs.size() / 2) / weight + " of the weight");
    }

    @Test
    void aFloatingShipHoldsItsHeightWithItsThrustersIdle() {
        Ship ship = new Ship();
        AerialPilot pilot = ship.pilot(true);
        double y0 = ship.y;
        List<AerialPilot.Command> cs = ship.fly(pilot, 30.0);
        double weight = ship.mass * G;
        double most = 0.0;
        for (AerialPilot.Command c : cs) most = Math.max(most, Math.abs(c.wrench()[Axis6.UP.index()]));
        assertTrue(most < 0.02 * weight, "the thrusters never asked for more than " + most / weight + " of the weight");
        assertEquals(y0, ship.y, 0.1);
        assertTrue(cs.get(cs.size() - 1).notes().isEmpty(), "nothing to say: " + cs.get(cs.size() - 1).notes());
    }

    @Test
    void itClimbsAndComesDownOnItsThrusters() {
        Ship ship = new Ship();
        AerialPilot pilot = ship.pilot(true);
        ship.fly(pilot, 2.0);
        double y0 = ship.y;
        pilot.nudgeAltitude(12.0, ship.y);
        List<AerialPilot.Command> climb = ship.fly(pilot, 30.0);
        assertEquals(y0 + 12.0, ship.y, 0.3, "climbed to the height held");
        double weight = ship.mass * G;
        assertTrue(meanAbsUp(climb, climb.size() - 100) < 0.02 * weight, "and idles there: " + meanAbsUp(climb, climb.size() - 100) / weight);

        pilot.nudgeAltitude(-12.0, ship.y);
        List<AerialPilot.Command> sink = ship.fly(pilot, 30.0);
        double hardestDown = 0.0;
        for (AerialPilot.Command c : sink) hardestDown = Math.min(hardestDown, c.wrench()[Axis6.UP.index()]);
        assertTrue(hardestDown < -0.01 * weight, "levitite holds the weight, so coming down takes a push down");
        assertEquals(y0, ship.y, 0.3, "came down to the height held");
        assertTrue(meanAbsUp(sink, sink.size() - 100) < 0.02 * weight);
    }

    @Test
    void theClimbStopsOnTheMark() {
        Ship ship = new Ship();
        AerialPilot pilot = ship.pilot(true);
        ship.fly(pilot, 2.0);
        double target = ship.y + 12.0;
        pilot.nudgeAltitude(12.0, ship.y);
        double top = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 600; i++) {
            ship.fly(pilot, AerialPilot.DT);
            top = Math.max(top, ship.y);
        }
        assertTrue(top < target + 0.5, "overshot by " + (top - target));
    }

    @Test
    void tooLittleLevititeLeavesTheRestToTheThrusters() {
        Ship ship = new Ship();
        ship.blocks = 40.0;
        AerialPilot pilot = ship.pilot(true);
        double y0 = ship.y;
        List<AerialPilot.Command> cs = ship.fly(pilot, 30.0);
        double gap = (ship.mass - 400.0) * G;
        double mean = 0.0;
        for (int i = cs.size() - 100; i < cs.size(); i++) mean += cs.get(i).wrench()[Axis6.UP.index()];
        mean /= 100.0;
        assertEquals(gap, mean, 0.03 * gap, "the thrusters carry the 200 kpg the levitite cannot");
        assertEquals(y0, ship.y, 0.3);
    }

    @Test
    void thrustersThatOnlyPushDownStillFlyAFloatingShip() {
        Ship ship = new Ship();
        ship.maxUp = 0.0;
        AerialPilot pilot = ship.pilot(true);
        ship.fly(pilot, 2.0);
        double y0 = ship.y;
        pilot.nudgeAltitude(-8.0, ship.y);
        ship.fly(pilot, 30.0);
        assertEquals(y0 - 8.0, ship.y, 0.3);
    }

    @Test
    void aFloatingShipSaysWhatItLacks() {
        Ship still = new Ship();
        still.maxUp = 0.0;
        still.maxDown = 0.0;
        List<AerialPilot.Command> cs = still.fly(still.pilot(true), 1.0);
        assertTrue(cs.get(cs.size() - 1).notes().contains(AerialPilot.LEVITITE_STILL), "no thruster at all: " + cs.get(cs.size() - 1).notes());
        assertFalse(cs.get(cs.size() - 1).notes().stream().anyMatch(n -> n.startsWith("No balloon")),
                "a floating ship is not told it has nothing to hold it up");

        Ship upOnly = new Ship();
        upOnly.maxDown = 0.0;
        AerialPilot pilot = upOnly.pilot(true);
        upOnly.fly(pilot, 1.0);
        pilot.nudgeAltitude(-8.0, upOnly.y);
        cs = upOnly.fly(pilot, 1.0);
        assertTrue(cs.get(cs.size() - 1).notes().contains(AerialPilot.LEVITITE_NO_DOWN), "cannot come down: " + cs.get(cs.size() - 1).notes());
    }
}
