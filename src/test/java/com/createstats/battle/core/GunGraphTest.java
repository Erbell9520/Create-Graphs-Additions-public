package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunGraphTest {
    private static final long MOUNT = 1000;
    private static final long MOUNT2 = 2000;

    private static GunGraph line(GunGraph g, long engine, long gov, long base, long mount, int axis, int input) {
        int e = g.node(engine, true);
        int a = g.node(base, false);
        int b = g.node(base + 1, false);
        int c = g.node(base + 2, false);
        g.edge(e, a);
        g.edge(b, c);
        g.edge(c, g.terminal(mount, axis));
        g.governor(gov, a, b, input);
        return g;
    }

    @Test
    void aGovernorWithAKnownInputDrivesTheAxisItsOutputReaches() {
        GunGraph g = line(new GunGraph(), 1, 10, 100, MOUNT, GunGraph.YAW, 0);
        line(g, 2, 20, 200, MOUNT, GunGraph.PITCH, 0);
        GunGraph.Drive d = g.solve(new long[]{MOUNT}).get(0);
        assertEquals(10, d.yaw());
        assertEquals(20, d.pitch());
        assertFalse(d.fault());
    }

    @Test
    void withNoKnownInputTheEngineSideIsTheInput() {
        GunGraph g = line(new GunGraph(), 1, 10, 100, MOUNT, GunGraph.YAW, -1);
        assertEquals(10, g.solve(new long[]{MOUNT}).get(0).yaw());
    }

    @Test
    void withNoEngineTurningTheSideThatReachesAMountIsTheOutput() {
        GunGraph g = new GunGraph();
        int a = g.node(100, false);
        int b = g.node(101, false);
        g.edge(b, g.terminal(MOUNT, GunGraph.PITCH));
        g.governor(10, a, b, -1);
        assertEquals(10, g.solve(new long[]{MOUNT}).get(0).pitch());
    }

    @Test
    void theInputSideNeverCounts() {
        GunGraph g = new GunGraph();
        int a = g.node(100, false);
        int b = g.node(101, false);
        g.edge(a, g.terminal(MOUNT, GunGraph.YAW));
        g.governor(10, a, b, 0);
        GunGraph.Drive d = g.solve(new long[]{MOUNT}).get(0);
        assertFalse(d.hasYaw());
    }

    @Test
    void theWalkStopsAtAnotherGovernor() {
        GunGraph g = new GunGraph();
        int a = g.node(100, false);
        int b = g.node(101, false);
        int c = g.node(102, false);
        int d = g.node(103, false);
        g.governor(10, a, b, 0);
        g.edge(b, g.node(20, false));
        g.governor(20, b, c, 0);
        g.edge(c, d);
        g.edge(d, g.terminal(MOUNT, GunGraph.YAW));
        GunGraph.Drive drive = g.solve(new long[]{MOUNT}).get(0);
        assertEquals(20, drive.yaw());
        assertFalse(drive.yawConflict());
    }

    @Test
    void twoGovernorsOnOneAxisAreAConflict() {
        GunGraph g = line(new GunGraph(), 1, 10, 100, MOUNT, GunGraph.YAW, 0);
        line(g, 2, 20, 200, MOUNT, GunGraph.YAW, 0);
        GunGraph.Drive d = g.solve(new long[]{MOUNT}).get(0);
        assertTrue(d.yawConflict());
        assertEquals(10, d.yaw(), "the lower key is kept, so the report is stable");
        assertTrue(d.fault());
    }

    @Test
    void oneGovernorOnBothAxesIsShared() {
        GunGraph g = new GunGraph();
        int a = g.node(100, false);
        int b = g.node(101, false);
        g.edge(b, g.terminal(MOUNT, GunGraph.YAW));
        g.edge(b, g.terminal(MOUNT, GunGraph.PITCH));
        g.governor(10, a, b, 0);
        GunGraph.Drive d = g.solve(new long[]{MOUNT}).get(0);
        assertTrue(d.shared());
        assertTrue(d.fault());
    }

    @Test
    void oneGovernorOnTheSameAxisOfTwoMountsIsABattery() {
        GunGraph g = line(new GunGraph(), 1, 10, 100, MOUNT, GunGraph.YAW, 0);
        int shaft = g.node(102, false);
        g.edge(shaft, g.terminal(MOUNT2, GunGraph.YAW));
        List<GunGraph.Drive> drives = g.solve(new long[]{MOUNT, MOUNT2});
        assertEquals(10, drives.get(0).yaw());
        assertEquals(10, drives.get(1).yaw());
        assertFalse(drives.get(0).fault(), "a battery is not a fault");
    }

    @Test
    void rotationDoesNotPassThroughAMount() {
        GunGraph g = new GunGraph();
        int a = g.node(100, false);
        int b = g.node(101, false);
        int t1 = g.terminal(MOUNT, GunGraph.YAW);
        g.edge(b, t1);
        g.edge(t1, g.terminal(MOUNT2, GunGraph.YAW));
        g.governor(10, a, b, 0);
        List<GunGraph.Drive> drives = g.solve(new long[]{MOUNT, MOUNT2});
        assertTrue(drives.get(0).hasYaw());
        assertFalse(drives.get(1).hasYaw());
    }

    @Test
    void aGovernorPressedAgainstAMountFaceDrivesIt() {
        GunGraph g = new GunGraph();
        int in = g.node(100, false);
        g.edge(g.node(1, true), in);
        g.governor(10, g.terminal(MOUNT, GunGraph.PITCH), in, 1);
        assertEquals(10, g.solve(new long[]{MOUNT}).get(0).pitch());

        GunGraph idle = new GunGraph();
        int in2 = idle.node(100, false);
        idle.edge(idle.node(1, true), in2);
        idle.governor(10, idle.terminal(MOUNT, GunGraph.PITCH), in2, -1);
        GunGraph.Drive d = idle.solve(new long[]{MOUNT}).get(0);
        assertEquals(10, d.pitch());
        assertFalse(d.fault());
    }

    @Test
    void aMountNobodyReachesHasNoDrive() {
        GunGraph g = line(new GunGraph(), 1, 10, 100, MOUNT, GunGraph.YAW, 0);
        GunGraph.Drive d = g.solve(new long[]{MOUNT2}).get(0);
        assertFalse(d.hasYaw());
        assertFalse(d.hasPitch());
    }
}
