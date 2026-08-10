package com.createstats.pathfinder.planner;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PathRoutePlannerSmoothingTest {

    private static final int Y = 64;

    // A rectangular field of fully passable cells (same height, no hazards).
    private static Map<Long, PathRoutePlanner.Cell> field(int x0, int z0, int x1, int z1) {
        Map<Long, PathRoutePlanner.Cell> m = new HashMap<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                m.put(PathRoutePlanner.cellKey(x, z), new PathRoutePlanner.Cell(x, Y, z, 0));
            }
        }
        return m;
    }

    private static List<PathRoutePlanner.Cell> cellPath(int... xz) {
        List<PathRoutePlanner.Cell> p = new ArrayList<>();
        for (int i = 0; i + 1 < xz.length; i += 2) {
            p.add(new PathRoutePlanner.Cell(xz[i], Y, xz[i + 1], 0));
        }
        return p;
    }

    private static double segLen(Vec3 a, Vec3 b) {
        return Math.hypot(b.x - a.x, b.z - a.z);
    }

    private static double turnDeg(Vec3 a, Vec3 b, Vec3 c) {
        double ax = b.x - a.x, az = b.z - a.z, bx = c.x - b.x, bz = c.z - b.z;
        double al = Math.hypot(ax, az), bl = Math.hypot(bx, bz);
        if (al < 1e-6 || bl < 1e-6) return 0.0;
        double dot = Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (al * bl)));
        return Math.toDegrees(Math.acos(dot));
    }

    @Test
    void resampleProducesEvenSpacingAndKeepsEndpoints() {
        // Three collinear points (resample intentionally returns 2-point inputs unchanged).
        List<Vec3> line = List.of(new Vec3(0, Y, 0), new Vec3(8, Y, 0), new Vec3(20, Y, 0));
        List<Vec3> out = PathRoutePlanner.resample(line, 2.0);
        assertTrue(out.size() >= 10, "expected roughly 10 points, got " + out.size());
        assertEquals(0.0, out.get(0).x, 1e-6);
        assertEquals(20.0, out.get(out.size() - 1).x, 1e-6);
        for (int i = 1; i < out.size() - 1; i++) {
            assertEquals(2.0, segLen(out.get(i - 1), out.get(i)), 1e-6, "interior spacing should equal the step");
        }
    }

    @Test
    void resampleHandlesTinyPolylineWithoutCrashing() {
        List<Vec3> tiny = List.of(new Vec3(0, Y, 0), new Vec3(0.5, Y, 0));
        List<Vec3> out = PathRoutePlanner.resample(tiny, 2.0);
        assertNotNull(out);
        assertEquals(0.0, out.get(0).x, 1e-6);
        assertEquals(0.5, out.get(out.size() - 1).x, 1e-6);
    }

    @Test
    void localRadiusIsInfiniteOnAStraightLine() {
        List<Vec3> line = new ArrayList<>();
        for (int i = 0; i <= 20; i++) line.add(new Vec3(i, Y, 0));
        double r = PathRoutePlanner.localRadius(line, 10, 2.0);
        assertTrue(Double.isInfinite(r) || r > 1000.0, "straight line should read as (near) infinite radius, got " + r);
    }

    @Test
    void localRadiusMatchesCircleRadius() {
        double radius = 15.0;
        List<Vec3> arc = new ArrayList<>();
        for (double ang = 0; ang <= Math.PI / 2; ang += 0.05) {
            arc.add(new Vec3(radius * Math.cos(ang), Y, radius * Math.sin(ang)));
        }
        double r = PathRoutePlanner.localRadius(arc, arc.size() / 2, 2.0);
        // Menger curvature of three points on a circle is the exact circumradius, so this is tight.
        assertEquals(radius, r, 1.5, "curvature radius of a circular arc should match its radius");
    }

    @Test
    void speedProfileRunsFullSpeedOnStraights() {
        List<Vec3> line = new ArrayList<>();
        for (int i = 0; i <= 20; i += 2) line.add(new Vec3(i, Y, 0));
        List<Float> speeds = PathRoutePlanner.vehicleSpeeds(line, 16.0f, 0, 4.0);
        for (float s : speeds) assertEquals(16.0f, s, 0.01f, "a straight path should hold the capped speed");
    }

    @Test
    void speedProfileSlowsIntoATightCurve() {
        double radius = 5.0;
        List<Vec3> arc = new ArrayList<>();
        for (double ang = 0; ang <= Math.PI; ang += 0.15) {
            arc.add(new Vec3(radius * Math.cos(ang), Y, radius * Math.sin(ang)));
        }
        List<Float> speeds = PathRoutePlanner.vehicleSpeeds(arc, 16.0f, 0, 4.0);
        float min = Float.MAX_VALUE;
        for (float s : speeds) min = Math.min(min, s);
        // sqrt(a_lat * R) = sqrt(3.2 * 5) ~ 4.0, well under the 16 cap.
        assertTrue(min < 6.0f, "a tight curve should force a low speed, got min " + min);
    }

    @Test
    void speedProfileBrakesBeforeACorner() {
        // Long straight approach, then a sharp near-90-degree kink.
        List<Vec3> path = new ArrayList<>();
        for (int x = 0; x <= 20; x += 2) path.add(new Vec3(x, Y, 0));
        for (int z = 2; z <= 12; z += 2) path.add(new Vec3(20, Y, z));
        List<Float> speeds = PathRoutePlanner.vehicleSpeeds(path, 16.0f, 0, 4.0);
        int cornerIndex = 10; // the (20,0) vertex where the path turns
        // A few points before the corner the speed must already be reduced (braking early), not still full.
        assertTrue(speeds.get(cornerIndex - 2) < 16.0f, "should be braking before the corner");
    }

    @Test
    void stringPullCollapsesAStaircaseInOpenField() {
        Map<Long, PathRoutePlanner.Cell> grid = field(-2, -2, 12, 12);
        // 8-connected staircase from (0,0) to (8,8).
        List<PathRoutePlanner.Cell> staircase = cellPath(0, 0, 1, 0, 1, 1, 2, 1, 2, 2, 3, 2, 3, 3,
            4, 3, 4, 4, 5, 4, 5, 5, 6, 5, 6, 6, 7, 6, 7, 7, 8, 7, 8, 8);
        List<PathRoutePlanner.Cell> pulled = PathRoutePlanner.stringPull(staircase, grid, 0.5);
        assertEquals(2, pulled.size(), "clear line of sight should collapse the staircase to its endpoints");
        assertEquals(0, pulled.get(0).x());
        assertEquals(8, pulled.get(1).x());
    }

    @Test
    void stringPullKeepsACornerWhenAWallBlocksTheDirectLine() {
        Map<Long, PathRoutePlanner.Cell> grid = field(-1, -1, 12, 12);
        // Vertical wall at x=6 for z=0..8 (hole cells), forcing a detour up and over.
        for (int z = 0; z <= 8; z++) grid.remove(PathRoutePlanner.cellKey(6, z));
        List<PathRoutePlanner.Cell> around = cellPath(0, 0, 0, 10, 12, 10, 12, 0);
        List<PathRoutePlanner.Cell> pulled = PathRoutePlanner.stringPull(around, grid, 0.5);
        assertTrue(pulled.size() >= 3, "the wall must prevent a straight shortcut, keeping a corner");
    }

    @Test
    void filletRoundsARightAngleInOpenField() {
        Map<Long, PathRoutePlanner.Cell> grid = field(-4, -4, 16, 16);
        List<Vec3> corner = List.of(new Vec3(0, Y, 0), new Vec3(10, Y, 0), new Vec3(10, Y, 10));
        List<Vec3> out = PathRoutePlanner.filletCorners(corner, grid, 0.5, 4.0);
        assertTrue(out.size() > 3, "a right angle should be rounded into several arc points, got " + out.size());
        double maxTurn = 0.0;
        for (int i = 1; i < out.size() - 1; i++) {
            maxTurn = Math.max(maxTurn, turnDeg(out.get(i - 1), out.get(i), out.get(i + 1)));
        }
        assertTrue(maxTurn < 60.0, "the rounded corner should have no sharp kink, max turn was " + maxTurn);
    }

    @Test
    void smoothedPathNeverLeavesADrivableCell() {
        Map<Long, PathRoutePlanner.Cell> grid = field(-4, -4, 20, 20);
        // An L-shaped A* path with a hard right angle.
        List<PathRoutePlanner.Cell> raw = cellPath(0, 0, 4, 0, 8, 0, 12, 0, 12, 4, 12, 8, 12, 12);
        List<Vec3> smoothed = PathRoutePlanner.smoothPath(raw, grid, 0.5, 4.0);
        assertTrue(smoothed.size() >= 2);
        for (Vec3 p : smoothed) {
            int cx = (int) Math.floor(p.x);
            int cz = (int) Math.floor(p.z);
            assertTrue(grid.containsKey(PathRoutePlanner.cellKey(cx, cz)),
                "smoothed point (" + p.x + "," + p.z + ") landed on a non-drivable cell");
        }
    }

    @Test
    void smoothedPathHasNoSharpKinks() {
        Map<Long, PathRoutePlanner.Cell> grid = field(-4, -4, 20, 20);
        List<PathRoutePlanner.Cell> raw = cellPath(0, 0, 4, 0, 8, 0, 12, 0, 12, 4, 12, 8, 12, 12);
        List<Vec3> smoothed = PathRoutePlanner.smoothPath(raw, grid, 0.5, 4.0);
        double maxTurn = 0.0;
        for (int i = 1; i < smoothed.size() - 1; i++) {
            maxTurn = Math.max(maxTurn, turnDeg(smoothed.get(i - 1), smoothed.get(i), smoothed.get(i + 1)));
        }
        assertTrue(maxTurn < 55.0, "the smoothed L path should have no near-90 kink, max turn was " + maxTurn);
    }

    @Test
    void filletNearAnObstacleStaysInsideDrivableCells() {
        // [2]
        Map<Long, PathRoutePlanner.Cell> grid = field(-4, -4, 16, 16);
        grid.remove(PathRoutePlanner.cellKey(8, 8)); // obstacle just inside the corner
        List<Vec3> corner = List.of(new Vec3(0, Y, 4), new Vec3(10, Y, 4), new Vec3(10, Y, 14));
        List<Vec3> out = PathRoutePlanner.filletCorners(corner, grid, 0.5, 4.0);
        for (Vec3 p : out) {
            assertFalse((int) Math.floor(p.x) == 8 && (int) Math.floor(p.z) == 8,
                "fillet must never emit a point on the obstacle cell (8,8)");
        }
    }
}
