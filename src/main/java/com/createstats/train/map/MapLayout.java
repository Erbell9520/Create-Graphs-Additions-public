package com.createstats.train.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// two geometries and a blend
public final class MapLayout {
    public enum Mode {
        GEOGRAPHIC("Geographic", "true positions, real track"),

        SCHEMATIC("Schematic", "straightened, evenly spaced"),

        STRIP("Line strip", "one service end to end");

        public final String label;
        public final String hint;

        Mode(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    private static final double LOG_LIFT = 6.0;

    private static final double GRID_DENSITY = 2.6;

    private final RailMap map;

    // keyed by node identity, indexes shift when anyone builds a siding
    private final Map<UUID, float[]> pins = new HashMap<>();

    private float[] geoX = new float[0];
    private float[] geoY = new float[0];
    private float[] schemX = new float[0];
    private float[] schemY = new float[0];
    private float[] stripX = new float[0];
    private float[] stripY = new float[0];

    private boolean[] onStrip = new boolean[0];

    private float gridStep = 16.0f;

    private float schemCentreX;
    private float schemCentreY;

    public MapLayout(RailMap map) {
        this.map = map;
        solve();
    }

    public float gridStep() {
        return this.gridStep;
    }

    public static boolean arrangeable(Mode mode) {
        return mode == Mode.SCHEMATIC;
    }

    public void pin(int node, float x, float y) {
        final RailMap.Node n = this.map.node(node);
        if (n == null || n.id == null) {
            return;
        }
        final float[] at = {x, y};
        this.pins.put(n.id, at);
        n.pinned = true;
        n.pinX = x;
        n.pinY = y;
        if (node < this.schemX.length) {
            this.schemX[node] = x;
            this.schemY[node] = y;
        }
    }

    public void unpin(int node) {
        final RailMap.Node n = this.map.node(node);
        if (n == null || n.id == null) {
            return;
        }
        this.pins.remove(n.id);
        n.pinned = false;
        solveSchematic();
    }

    public boolean isPinned(int node) {
        final RailMap.Node n = this.map.node(node);
        return n != null && n.pinned;
    }

    public boolean anyPinned() {
        return !this.pins.isEmpty();
    }

    public void clearPins() {
        this.pins.clear();
        for (final RailMap.Node n : this.map.nodes()) {
            n.pinned = false;
        }
        solveSchematic();
    }

    public Map<UUID, float[]> pins() {
        return this.pins;
    }

    public void restorePins(Map<UUID, float[]> saved) {
        if (saved == null || saved.isEmpty()) {
            return;
        }
        for (int i = 0; i < this.map.nodes().size(); i++) {
            final RailMap.Node n = this.map.nodes().get(i);
            final float[] at = n.id == null ? null : saved.get(n.id);
            if (at != null) {
                pin(i, at[0], at[1]);
            }
        }
    }

    public float snapX(float x) {
        return snap(x, this.schemCentreX);
    }

    public float snapY(float y) {
        return snap(y, this.schemCentreY);
    }

    private float snap(float v, float origin) {
        if (this.gridStep <= 0.0f) {
            return v;
        }
        return origin + Math.round((v - origin) / this.gridStep) * this.gridStep;
    }

    public float x(int node, Mode from, Mode to, float blend) {
        return lerp(pick(node, from, true), pick(node, to, true), blend);
    }

    public float y(int node, Mode from, Mode to, float blend) {
        return lerp(pick(node, from, false), pick(node, to, false), blend);
    }

    public float x(int node, Mode mode) {
        return pick(node, mode, true);
    }

    public float y(int node, Mode mode) {
        return pick(node, mode, false);
    }

    public boolean visible(int node, Mode mode) {
        return mode != Mode.STRIP || (node >= 0 && node < this.onStrip.length && this.onStrip[node]);
    }

    public float presence(int node, Mode from, Mode to, float blend) {
        final float a = visible(node, from) ? 1.0f : 0.0f;
        final float b = visible(node, to) ? 1.0f : 0.0f;
        return lerp(a, b, blend);
    }

    private float pick(int node, Mode mode, boolean horizontal) {
        if (node < 0 || node >= this.geoX.length) {
            return 0.0f;
        }
        final RailMap.Node n = this.map.nodes().get(node);
        if (mode == Mode.SCHEMATIC && n.pinned) {
            return horizontal ? n.pinX : n.pinY;
        }
        return switch (mode) {
            case SCHEMATIC -> horizontal ? this.schemX[node] : this.schemY[node];
            case STRIP -> horizontal ? this.stripX[node] : this.stripY[node];
            default -> horizontal ? this.geoX[node] : this.geoY[node];
        };
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void solve() {
        final int n = this.map.nodes().size();
        this.geoX = new float[n];
        this.geoY = new float[n];
        this.schemX = new float[n];
        this.schemY = new float[n];
        this.stripX = new float[n];
        this.stripY = new float[n];
        this.onStrip = new boolean[n];
        if (n == 0) {
            return;
        }

        for (int i = 0; i < n; i++) {
            final RailMap.Node node = this.map.nodes().get(i);
            this.geoX[i] = (float) node.x;
            this.geoY[i] = (float) node.z;
        }
        solveSchematic();
    }

    private void solveSchematic() {
        final int n = this.geoX.length;

        double cx = 0.0;
        double cy = 0.0;
        for (int i = 0; i < n; i++) {
            cx += this.geoX[i];
            cy += this.geoY[i];
        }
        cx /= n;
        cy /= n;
        this.schemCentreX = (float) cx;
        this.schemCentreY = (float) cy;

        final double[] radii = new double[n];
        for (int i = 0; i < n; i++) {
            radii[i] = Math.hypot(this.geoX[i] - cx, this.geoY[i] - cy);
        }
        final double[] sorted = radii.clone();
        Arrays.sort(sorted);
        final double maxR = sorted[Math.min(n - 1, (int) (n * 0.95))];
        if (maxR < 1.0) {
            for (int i = 0; i < n; i++) {
                final double a = i * (Math.PI * 2.0 / n);
                this.schemX[i] = (float) (cx + Math.cos(a) * 32.0);
                this.schemY[i] = (float) (cy + Math.sin(a) * 32.0);
            }
            this.gridStep = 16.0f;
            return;
        }

        this.gridStep = (float) (maxR * 2.0 / Math.max(4.0, Math.sqrt(n) * GRID_DENSITY));

        final double[] px = new double[n];
        final double[] py = new double[n];
        for (int i = 0; i < n; i++) {
            final double dx = this.geoX[i] - cx;
            final double dy = this.geoY[i] - cy;
            final double r = Math.hypot(dx, dy);
            if (r < 1.0e-4) {
                px[i] = cx;
                py[i] = cy;
                continue;
            }

            final double expanded =
                    Math.log1p(r / maxR * LOG_LIFT) / Math.log1p(LOG_LIFT) * maxR;
            px[i] = cx + dx / r * expanded;
            py[i] = cy + dy / r * expanded;
        }

        final Map<Long, Integer> taken = new HashMap<>();

        for (int i = 0; i < n; i++) {
            final RailMap.Node node = this.map.nodes().get(i);
            final float[] at = node.id == null ? null : this.pins.get(node.id);
            if (at == null) {
                continue;
            }
            node.pinned = true;
            node.pinX = at[0];
            node.pinY = at[1];
            this.schemX[i] = at[0];
            this.schemY[i] = at[1];
            taken.put(cellOf(at[0], at[1], cx, cy), i);
        }

        final Integer[] order = orderByDegree(n);
        for (final int i : order) {
            if (this.map.nodes().get(i).pinned) {
                continue;
            }
            long cell = cellOf(px[i], py[i], cx, cy);
            if (taken.containsKey(cell)) {
                cell = nearestFreeCell(cell, taken);
            }
            taken.put(cell, i);
            final int gx = (int) (cell >> 32);
            final int gy = (int) cell;
            this.schemX[i] = (float) (cx + gx * this.gridStep);
            this.schemY[i] = (float) (cy + gy * this.gridStep);
        }
    }

    private Integer[] orderByDegree(int n) {
        final int[] degree = new int[n];
        for (final RailMap.Edge e : this.map.edges()) {
            if (e.a < n) {
                degree[e.a] += 1 + e.lines.size();
            }
            if (e.b < n) {
                degree[e.b] += 1 + e.lines.size();
            }
        }
        final Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(degree[b], degree[a]));
        return order;
    }

    private long cellOf(double x, double y, double cx, double cy) {
        final int gx = (int) Math.round((x - cx) / this.gridStep);
        final int gy = (int) Math.round((y - cy) / this.gridStep);
        return ((long) gx << 32) | (gy & 0xFFFFFFFFL);
    }

    private static long nearestFreeCell(long wanted, Map<Long, Integer> taken) {
        final int wx = (int) (wanted >> 32);
        final int wy = (int) wanted;
        for (int ring = 1; ring < 64; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    final long candidate = ((long) (wx + dx) << 32) | ((wy + dy) & 0xFFFFFFFFL);
                    if (!taken.containsKey(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return wanted;
    }

    public void buildStrip(int lineIndex) {
        Arrays.fill(this.onStrip, false);
        final RailMap.Line line = lineIndex >= 0 && lineIndex < this.map.lines().size()
                ? this.map.lines().get(lineIndex) : null;
        if (line == null || line.nodes.isEmpty()) {
            return;
        }
        final List<Integer> stops = new ArrayList<>(line.nodes);
        if (line.loop) {
            stops.add(stops.get(0));
        }

        final float spacing = Math.max(this.gridStep * 1.4f, 24.0f);
        final float totalWidth = spacing * Math.max(1, stops.size() - 1);

        double cx = 0.0;
        double cy = 0.0;
        for (int i = 0; i < this.geoX.length; i++) {
            cx += this.geoX[i];
            cy += this.geoY[i];
        }
        cx /= Math.max(1, this.geoX.length);
        cy /= Math.max(1, this.geoY.length);

        for (int i = 0; i < stops.size(); i++) {
            final int node = stops.get(i);
            if (node < 0 || node >= this.stripX.length) {
                continue;
            }

            if (this.onStrip[node] && i > 0) {
                continue;
            }
            this.stripX[node] = (float) (cx - totalWidth * 0.5 + i * spacing);
            this.stripY[node] = (float) cy;
            this.onStrip[node] = true;
        }
    }

    public int edgePath(RailMap.Edge edge, Mode from, Mode to, float blend, float[][] out) {
        final float ax = x(edge.a, from, to, blend);
        final float ay = y(edge.a, from, to, blend);
        final float bx = x(edge.b, from, to, blend);
        final float by = y(edge.b, from, to, blend);

        final float geoWeight = weightOf(Mode.GEOGRAPHIC, from, to, blend);
        if (geoWeight > 0.995f && edge.surveyed()) {
            final int points = edge.path.length / 2;
            final float[] buffer = ensure(out, points * 2);
            System.arraycopy(edge.path, 0, buffer, 0, points * 2);
            return points;
        }
        if (geoWeight > 0.02f && edge.surveyed()) {
            return blendedSurvey(edge, ax, ay, bx, by, geoWeight, out);
        }
        if (geoWeight > 0.5f) {
            final float[] buffer = ensure(out, 4);
            buffer[0] = ax;
            buffer[1] = ay;
            buffer[2] = bx;
            buffer[3] = by;
            return 2;
        }
        return octilinear(ax, ay, bx, by, out);
    }

    private static float weightOf(Mode mode, Mode from, Mode to, float blend) {
        float w = 0.0f;
        if (from == mode) {
            w += 1.0f - blend;
        }
        if (to == mode) {
            w += blend;
        }
        return w;
    }

    private int blendedSurvey(RailMap.Edge edge, float ax, float ay, float bx, float by,
                              float geoWeight, float[][] out) {
        final int points = edge.path.length / 2;
        if (points < 2) {
            return octilinear(ax, ay, bx, by, out);
        }
        final float[] buffer = ensure(out, points * 2);
        final float sx = edge.path[0];
        final float sy = edge.path[1];
        final float ex = edge.path[(points - 1) * 2];
        final float ey = edge.path[(points - 1) * 2 + 1];
        final float spanX = ex - sx;
        final float spanY = ey - sy;
        final float spanSq = spanX * spanX + spanY * spanY;

        for (int i = 0; i < points; i++) {
            final float px = edge.path[i * 2];
            final float py = edge.path[i * 2 + 1];

            final float t = spanSq < 1.0e-4f
                    ? i / (float) (points - 1)
                    : clamp01(((px - sx) * spanX + (py - sy) * spanY) / spanSq);
            final float straightX = ax + (bx - ax) * t;
            final float straightY = ay + (by - ay) * t;
            buffer[i * 2] = straightX + (px - straightX) * geoWeight;
            buffer[i * 2 + 1] = straightY + (py - straightY) * geoWeight;
        }
        return points;
    }

    public static int octilinear(float ax, float ay, float bx, float by, float[][] out) {
        final float[] buffer = ensure(out, 8);
        final float dx = bx - ax;
        final float dy = by - ay;
        final float adx = Math.abs(dx);
        final float ady = Math.abs(dy);

        buffer[0] = ax;
        buffer[1] = ay;
        if (adx < 0.5f || ady < 0.5f || Math.abs(adx - ady) < 0.5f) {
            buffer[2] = bx;
            buffer[3] = by;
            return 2;
        }
        final float sx = Math.signum(dx);
        final float sy = Math.signum(dy);
        if (adx > ady) {
            final float run = (adx - ady) * 0.5f;
            buffer[2] = ax + sx * run;
            buffer[3] = ay;
            buffer[4] = buffer[2] + sx * ady;
            buffer[5] = ay + sy * ady;
        } else {
            final float run = (ady - adx) * 0.5f;
            buffer[2] = ax;
            buffer[3] = ay + sy * run;
            buffer[4] = ax + sx * adx;
            buffer[5] = buffer[3] + sy * adx;
        }
        buffer[6] = bx;
        buffer[7] = by;
        return 4;
    }

    public static float[] along(float[] path, int points, float t) {
        if (points < 2) {
            return new float[]{path[0], path[1], 1.0f, 0.0f};
        }
        float total = 0.0f;
        for (int i = 0; i < points - 1; i++) {
            total += dist(path, i, i + 1);
        }
        if (total < 1.0e-4f) {
            return new float[]{path[0], path[1], 1.0f, 0.0f};
        }
        float want = clamp01(t) * total;
        for (int i = 0; i < points - 1; i++) {
            final float segment = dist(path, i, i + 1);
            if (want <= segment || i == points - 2) {
                final float f = segment < 1.0e-4f ? 0.0f : want / segment;
                final float x0 = path[i * 2];
                final float y0 = path[i * 2 + 1];
                final float x1 = path[(i + 1) * 2];
                final float y1 = path[(i + 1) * 2 + 1];
                final float dx = x1 - x0;
                final float dy = y1 - y0;
                final float len = Math.max(1.0e-4f, (float) Math.sqrt(dx * dx + dy * dy));
                return new float[]{x0 + dx * f, y0 + dy * f, dx / len, dy / len};
            }
            want -= segment;
        }
        return new float[]{path[0], path[1], 1.0f, 0.0f};
    }

    private static float dist(float[] path, int a, int b) {
        final float dx = path[b * 2] - path[a * 2];
        final float dy = path[b * 2 + 1] - path[a * 2 + 1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static void offsetPath(float[] path, int points, float offset, float[] out) {
        if (points < 2 || Math.abs(offset) < 1.0e-4f) {
            System.arraycopy(path, 0, out, 0, points * 2);
            return;
        }
        for (int i = 0; i < points; i++) {
            float nx = 0.0f;
            float ny = 0.0f;
            if (i > 0) {
                final float[] n = normal(path, i - 1, i);
                nx += n[0];
                ny += n[1];
            }
            if (i < points - 1) {
                final float[] n = normal(path, i, i + 1);
                nx += n[0];
                ny += n[1];
            }
            final float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len < 1.0e-4f) {
                out[i * 2] = path[i * 2];
                out[i * 2 + 1] = path[i * 2 + 1];
                continue;
            }

            final float miter = Math.min(2.5f, 2.0f / Math.max(0.4f, len));
            out[i * 2] = path[i * 2] + nx / len * offset * miter;
            out[i * 2 + 1] = path[i * 2 + 1] + ny / len * offset * miter;
        }
    }

    private static float[] normal(float[] path, int a, int b) {
        final float dx = path[b * 2] - path[a * 2];
        final float dy = path[b * 2 + 1] - path[a * 2 + 1];
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-4f) {
            return new float[]{0.0f, 0.0f};
        }
        return new float[]{-dy / len, dx / len};
    }

    private static float[] ensure(float[][] holder, int size) {
        if (holder[0] == null || holder[0].length < size) {
            holder[0] = new float[Math.max(size, 16)];
        }
        return holder[0];
    }

    private static float clamp01(float t) {
        return t < 0.0f ? 0.0f : Math.min(t, 1.0f);
    }
}
