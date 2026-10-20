package com.createstats.statsmk3.page;

import java.util.List;
import java.util.Locale;

import com.createstats.driverhelmet.HudTheme;
import com.createstats.driverhelmet.LedBar;
import com.createstats.driverhelmet.LedFont;
import com.createstats.ground.DriveMath;
import com.createstats.ground.DriveReadout;
import com.createstats.ground.GroundAlerts;
import com.createstats.ground.GroundFrame;
import com.createstats.ground.GroundSnapshot;
import com.createstats.ground.GroundSnapshot.Jet;
import com.createstats.ground.GroundSnapshot.Lamp;
import com.createstats.ground.GroundSnapshot.Shape;
import com.createstats.ground.GroundSnapshot.Tie;
import com.createstats.ground.GroundSnapshot.Unit;
import com.createstats.ground.GroundSnapshot.Wheel;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vehiclelight.HailSequence;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DriveKit {
    public enum Look {
        CLUSTER, RACING
    }

    static final HudTheme THEME = HudTheme.TELEMETRY;

    private DriveKit() {
    }

    public record Plan(boolean train, boolean geometry, boolean parts, boolean labels) {
        public static final Plan FULL = new Plan(true, true, true, true);
        public static final Plan DASH = new Plan(false, true, false, false);
        public static final Plan TRAIN = new Plan(true, false, true, true);
    }

    // faces have no scissor, carry the box and cut
    public record View(float cx, float cy, double midX, double midZ, float k, Rect box) {
        float sx(double x) {
            return this.cx + (float) ((x - this.midX) * this.k);
        }

        float sy(double z) {
            return this.cy - (float) ((z - this.midZ) * this.k);
        }

        boolean holds(float x, float y, float radius) {
            return x - radius >= this.box.x && x + radius <= this.box.right() && y - radius >= this.box.y
                    && y + radius <= this.box.bottom();
        }
    }

    public static View top(PanelSurface s, Rect box, GroundFrame f, Plan plan) {
        final GroundSnapshot snap = f.snapshot();
        final DriveReadout r = f.readout();
        final double[] b = extent(snap, plan.train());
        final double w = Math.max(2.0, b[1] - b[0]);
        final double h = Math.max(2.0, b[3] - b[2]);

        final float k = (float) Math.max(0.05, Math.min(box.w / (w + 1.0), box.h / (h + 1.0)));
        final View v = new View(box.centerX(), box.centerY(), (b[0] + b[1]) * 0.5, (b[2] + b[3]) * 0.5, k, box);
        s.clip(box);
        try {
            if (plan.geometry()) {
                geometry(s, v, r);
            }
            for (final Unit u : snap.units()) {
                if (!plan.train() && !u.isTractor()) {
                    continue;
                }
                body(s, v, snap, u);
            }
            if (plan.train()) {
                for (final Tie t : snap.ties()) {
                    tie(s, v, t);
                }
            }
            s.advanceLayer();
            for (final DriveReadout.WheelRead wr : r.wheels) {
                final Wheel wheel = wr.wheel();
                if (!plan.train() && wheel.unit() != 0) {
                    continue;
                }
                tyre(s, v, unitOf(snap, wheel.unit()), wr);
            }
            if (plan.parts()) {
                for (final Jet j : snap.jets()) {
                    if (plan.train() || j.unit() == 0) {
                        jet(s, v, unitOf(snap, j.unit()), j);
                    }
                }
                for (final Lamp l : snap.lights().lamps()) {
                    if (plan.train() || l.unit() == 0) {
                        lamp(s, v, unitOf(snap, l.unit()), l, snap, f.tick());
                    }
                }
            }
            if (snap.setup().preset() == SteerPreset.SKID) {
                tracks(s, v, r);
            }
            centreOfMass(s, v, 0.0, 0.0);
            if (plan.labels() && plan.train()) {
                for (final Unit u : snap.units()) {
                    if (!u.isTractor()) {
                        final float lx = v.sx(u.x());
                        final float ly = v.sy(u.z());
                        final float half = s.textWidth(u.name(), Text.MICRO) * 0.5f;
                        if (lx - half >= box.x && lx + half <= box.right()
                                && v.holds(lx, ly, Text.lineHeight(Text.MICRO) * 0.5f)) {
                            s.textCenterV(u.name(), lx, ly, Tokens.INK_3, Text.MICRO);
                        }
                    }
                }
            }
        } finally {
            s.unclip();
        }
        return v;
    }

    static double[] extent(GroundSnapshot snap, boolean train) {
        double minX = -1.5;
        double maxX = 1.5;
        double minZ = -2.0;
        double maxZ = 2.0;
        boolean any = false;
        for (final Shape shape : snap.shapes()) {
            final Unit u = unitOf(snap, shape.unit());
            if (shape.isEmpty() || (!train && !u.isTractor())) {
                continue;
            }
            final double[][] corners = {{shape.originX(), shape.originZ()},
                    {shape.originX() + shape.width(), shape.originZ()},
                    {shape.originX(), shape.originZ() + shape.length()},
                    {shape.originX() + shape.width(), shape.originZ() + shape.length()}};
            for (final double[] c : corners) {
                final double[] p = DriveMath.place(u, c[0], c[1]);
                if (!any) {
                    minX = maxX = p[0];
                    minZ = maxZ = p[1];
                    any = true;
                }
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
        }
        for (final Wheel w : snap.wheels()) {
            final Unit u = unitOf(snap, w.unit());
            if (!train && !u.isTractor()) {
                continue;
            }
            final double[] p = DriveMath.place(u, w.x(), w.z());
            final double rr = Float.isFinite(w.radius()) ? w.radius() : 0.5;
            if (!any) {
                minX = maxX = p[0];
                minZ = maxZ = p[1];
                any = true;
            }
            minX = Math.min(minX, p[0] - 0.4);
            maxX = Math.max(maxX, p[0] + 0.4);
            minZ = Math.min(minZ, p[1] - rr);
            maxZ = Math.max(maxZ, p[1] + rr);
        }
        return new double[]{minX, maxX, minZ, maxZ};
    }

    static Unit unitOf(GroundSnapshot snap, int index) {
        for (final Unit u : snap.units()) {
            if (u.index() == index) {
                return u;
            }
        }
        return Unit.TRACTOR;
    }

    static void body(PanelSurface s, View v, GroundSnapshot snap, Unit u) {
        Shape shape = null;
        for (final Shape sh : snap.shapes()) {
            if (sh.unit() == u.index()) {
                shape = sh;
            }
        }
        final int fill = u.isTractor() ? Tokens.LINE_HI : Tokens.LINE;
        if (shape == null || shape.isEmpty()) {
            footprint(s, v, snap, u, fill);
            return;
        }
        final double cell = shape.cell();
        // column height shading, it's a measurement

        final int[] height = columnHeights(shape);
        int tallest = 1;
        for (final int h : height) {
            tallest = Math.max(tallest, h);
        }
        final int lowest = lowestHeight(height);
        for (int row = 0; row < shape.topRows(); row++) {
            int col = 0;
            while (col < shape.cols()) {
                if (!shape.topAt(col, row)) {
                    col++;
                    continue;
                }
                final int start = col;
                final int h = height[col];
                while (col < shape.cols() && shape.topAt(col, row) && height[col] == h) {
                    col++;
                }
                final float lift = tallest > lowest ? (h - lowest) / (float) (tallest - lowest) : 0.0f;
                final int shade = Tokens.mix(fill, Tokens.INK_3, 0.08f + 0.42f * lift);
                final double x0 = shape.originX() + row * cell;
                final double z0 = shape.originZ() + start * cell;
                quad(s, v, u, x0 - 0.01, z0 - 0.01, x0 + cell + 0.01, shape.originZ() + col * cell + 0.01, shade);
            }
        }

        final int edge = u.isTractor() ? Tokens.INK_3 : Tokens.LINE_HI;
        final float t = Math.max(1.0f, v.k() * 0.08f);
        for (int row = 0; row < shape.topRows(); row++) {
            for (int col = 0; col < shape.cols(); col++) {
                if (!shape.topAt(col, row)) {
                    continue;
                }
                final double x0 = shape.originX() + row * cell;
                final double z0 = shape.originZ() + col * cell;
                if (!shape.topAt(col, row - 1)) {
                    segment(s, v, u, x0, z0, x0, z0 + cell, t, edge);
                }
                if (!shape.topAt(col, row + 1)) {
                    segment(s, v, u, x0 + cell, z0, x0 + cell, z0 + cell, t, edge);
                }
                if (!shape.topAt(col - 1, row)) {
                    segment(s, v, u, x0, z0, x0 + cell, z0, t, edge);
                }
                if (!shape.topAt(col + 1, row)) {
                    segment(s, v, u, x0, z0 + cell, x0 + cell, z0 + cell, t, edge);
                }
            }
        }
    }

    static int[] columnHeights(Shape shape) {
        final int[] out = new int[shape.cols()];
        for (int col = 0; col < shape.cols(); col++) {
            for (int row = shape.sideRows() - 1; row >= 0; row--) {
                if (shape.sideAt(col, row)) {
                    out[col] = row + 1;
                    break;
                }
            }
        }
        return out;
    }

    static int lowestHeight(int[] height) {
        int lowest = Integer.MAX_VALUE;
        for (final int h : height) {
            if (h > 0) {
                lowest = Math.min(lowest, h);
            }
        }
        return lowest == Integer.MAX_VALUE ? 0 : lowest;
    }

    static void footprint(PanelSurface s, View v, GroundSnapshot snap, Unit u, int fill) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (final Wheel w : snap.wheels()) {
            if (w.unit() != u.index()) {
                continue;
            }
            minX = Math.min(minX, w.x());
            maxX = Math.max(maxX, w.x());
            minZ = Math.min(minZ, w.z());
            maxZ = Math.max(maxZ, w.z());
        }
        if (!Double.isFinite(minX)) {
            minX = -1.0;
            maxX = 1.0;
            minZ = -1.5;
            maxZ = 1.5;
        }
        quad(s, v, u, minX + 0.3, minZ - 0.8, maxX - 0.3, maxZ + 0.8, fill);
    }

    static void quad(PanelSurface s, View v, Unit u, double x0, double z0, double x1, double z1, int color) {
        final double[] a = DriveMath.place(u, x0, z0);
        final double[] b = DriveMath.place(u, x1, z0);
        final double[] c = DriveMath.place(u, x1, z1);
        final double[] d = DriveMath.place(u, x0, z1);
        Shapes.quad(s, v.sx(a[0]), v.sy(a[1]), v.sx(b[0]), v.sy(b[1]), v.sx(c[0]), v.sy(c[1]), v.sx(d[0]), v.sy(d[1]),
                color, color, color, color);
    }

    static void segment(PanelSurface s, View v, Unit u, double x0, double z0, double x1, double z1, float t, int color) {
        final double[] a = DriveMath.place(u, x0, z0);
        final double[] b = DriveMath.place(u, x1, z1);
        Shapes.line(s, v.sx(a[0]), v.sy(a[1]), v.sx(b[0]), v.sy(b[1]), t, color);
    }

    static void tyre(PanelSurface s, View v, Unit u, DriveReadout.WheelRead wr) {
        final Wheel w = wr.wheel();
        final double hl = Float.isFinite(w.radius()) ? Math.max(0.25, w.radius()) : 0.5;
        final double hw = Math.min(0.32, hl * 0.6);
        final double th = Math.toRadians(w.angle());
        final double fx = Math.sin(th);
        final double fz = Math.cos(th);
        final double rx = Math.cos(th);
        final double rz = -Math.sin(th);
        final double[][] c = {
                {w.x() + fx * hl + rx * hw, w.z() + fz * hl + rz * hw},
                {w.x() + fx * hl - rx * hw, w.z() + fz * hl - rz * hw},
                {w.x() - fx * hl - rx * hw, w.z() - fz * hl - rz * hw},
                {w.x() - fx * hl + rx * hw, w.z() - fz * hl + rz * hw}};
        final float[] px = new float[4];
        final float[] py = new float[4];
        for (int i = 0; i < 4; i++) {
            final double[] p = DriveMath.place(u, c[i][0], c[i][1]);
            px[i] = v.sx(p[0]);
            py[i] = v.sy(p[1]);
        }

        int color = w.driven() ? Tokens.OVERLAY : Tokens.SURFACE;
        if (wr.spinning()) {
            color = Tokens.CAUTION;
        } else if (wr.locking()) {
            color = Tokens.CRITICAL;
        }
        if (Float.isFinite(w.grip()) && w.grip() < 0.999f && wr.touching()) {
            color = Tokens.fade(color, 0.35f + 0.65f * Math.max(0.0f, w.grip()));
        }
        final float t = Math.max(1.0f, v.k() * 0.09f);
        if (wr.touching()) {
            Shapes.quad(s, px[0], py[0], px[1], py[1], px[2], py[2], px[3], py[3], color, color, color, color);
        }
        final int outline = wr.touching() ? (w.driven() ? Tokens.INK_2 : Tokens.INK_3) : Tokens.alpha(Tokens.INK_3, 0.7f);
        for (int i = 0; i < 4; i++) {
            final int j = (i + 1) % 4;
            Shapes.line(s, px[i], py[i], px[j], py[j], t, outline);
        }
    }

    static void centreOfMass(PanelSurface s, View v, double x, double z) {
        final float cx = v.sx(x);
        final float cy = v.sy(z);
        final float rad = Math.max(3.0f, Math.min(9.0f, v.k() * 0.45f));
        Shapes.circle(s, cx, cy, rad + 1.0f, Tokens.CANVAS);
        Shapes.circle(s, cx, cy, rad, Tokens.INK);
        Shapes.quad(s, cx, cy, cx + rad, cy, cx + rad * 0.72f, cy - rad * 0.72f, cx, cy - rad, Tokens.CANVAS,
                Tokens.CANVAS, Tokens.CANVAS, Tokens.CANVAS);
        Shapes.quad(s, cx, cy, cx - rad, cy, cx - rad * 0.72f, cy + rad * 0.72f, cx, cy + rad, Tokens.CANVAS,
                Tokens.CANVAS, Tokens.CANVAS, Tokens.CANVAS);
    }

    static void geometry(PanelSurface s, View v, DriveReadout r) {
        if (!Double.isFinite(r.centreX)) {
            return;
        }
        final float cx = v.sx(r.centreX);
        final float cy = v.sy(r.centreZ);
        final int col = Tokens.alpha(Tokens.INK_3, 0.9f);

        final Rect inside = v.box().inset(1.0f);
        for (final DriveReadout.WheelRead wr : r.wheels) {
            final Wheel w = wr.wheel();
            if (w.unit() != 0 || !w.steered()) {
                continue;
            }
            final float[] c = clipSegment(inside, v.sx(w.x()), v.sy(w.z()), cx, cy);
            if (c != null) {
                dashed(s, c[0], c[1], c[2], c[3], 1.0f, col, 4.0f);
            }
        }
        if (Double.isFinite(r.pathRadius) && r.pathRadius < 400.0) {
            final float rad = (float) (r.pathRadius * v.k());
            final double start = Math.atan2(v.sy(0.0) - cy, v.sx(0.0) - cx);
            final double reach = Math.min(Math.PI * 0.5, 14.0 * v.k() / Math.max(1.0f, rad));
            final double sweep = r.centreX > 0 ? reach : -reach;
            arc(s, inside, cx, cy, rad, 1.2f, start - sweep, sweep * 2.0, Tokens.alpha(Tokens.INK_2, 0.55f));
        }
        if (v.holds(cx, cy, 5.0f)) {
            Shapes.line(s, cx - 4.0f, cy, cx + 4.0f, cy, 1.0f, Tokens.INK_2);
            Shapes.line(s, cx, cy - 4.0f, cx, cy + 4.0f, 1.0f, Tokens.INK_2);
        }
    }

    static float[] clipSegment(Rect r, float x0, float y0, float x1, float y1) {
        final float dx = x1 - x0;
        final float dy = y1 - y0;
        final float[] p = {-dx, dx, -dy, dy};
        final float[] q = {x0 - r.x, r.right() - x0, y0 - r.y, r.bottom() - y0};
        float t0 = 0.0f;
        float t1 = 1.0f;
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0f) {
                if (q[i] < 0.0f) {
                    return null;
                }
                continue;
            }
            final float t = q[i] / p[i];
            if (p[i] < 0.0f) {
                if (t > t1) {
                    return null;
                }
                t0 = Math.max(t0, t);
            } else {
                if (t < t0) {
                    return null;
                }
                t1 = Math.min(t1, t);
            }
        }
        return new float[]{x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy};
    }

    static void line(PanelSurface s, Rect r, float x0, float y0, float x1, float y1, float t, int color) {
        final float[] c = clipSegment(r.inset(t * 0.5f), x0, y0, x1, y1);
        if (c != null) {
            Shapes.line(s, c[0], c[1], c[2], c[3], t, color);
        }
    }

    static void arc(PanelSurface s, Rect r, float cx, float cy, float rad, float t, double from, double sweep,
                    int color) {
        final int n = (int) Math.max(8, Math.min(160, Math.abs(sweep) * rad / 3.0));
        float px = cx + (float) Math.cos(from) * rad;
        float py = cy + (float) Math.sin(from) * rad;
        for (int i = 1; i <= n; i++) {
            final double a = from + sweep * i / n;
            final float x = cx + (float) Math.cos(a) * rad;
            final float y = cy + (float) Math.sin(a) * rad;
            line(s, r, px, py, x, y, t, color);
            px = x;
            py = y;
        }
    }

    static void dashed(PanelSurface s, float x0, float y0, float x1, float y1, float t, int color, float dash) {
        final float dx = x1 - x0;
        final float dy = y1 - y0;
        final float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0f) {
            return;
        }
        final int n = (int) Math.min(200, len / (dash * 2.0f));
        for (int i = 0; i <= n; i++) {
            final float a = i * dash * 2.0f / len;
            final float bb = Math.min(1.0f, (i * dash * 2.0f + dash) / len);
            if (a >= 1.0f) {
                break;
            }
            Shapes.line(s, x0 + dx * a, y0 + dy * a, x0 + dx * bb, y0 + dy * bb, t, color);
        }
    }

    static void tie(PanelSurface s, View v, Tie t) {
        final float ax = v.sx(t.ax());
        final float ay = v.sy(t.az());
        final float bx = v.sx(t.bx());
        final float by = v.sy(t.bz());
        final float th = Math.max(1.0f, v.k() * 0.1f);
        switch (t.kind().style()) {
            case LINE -> {
                final boolean slack = Float.isFinite(t.reading()) && t.reading() < -0.1f;
                if (slack) {
                    final float mx = (ax + bx) * 0.5f;
                    final float my = (ay + by) * 0.5f;
                    final float nx = -(by - ay);
                    final float ny = bx - ax;
                    final float nl = (float) Math.max(1.0e-3, Math.sqrt(nx * nx + ny * ny));
                    final float sag = (float) Math.min(0.35 * Math.hypot(bx - ax, by - ay), -t.reading() * v.k() * 0.8);
                    final float cx = mx + nx / nl * sag;
                    final float cy = my + ny / nl * sag;
                    float px = ax;
                    float py = ay;
                    for (int i = 1; i <= 10; i++) {
                        final float u = i / 10.0f;
                        final float qx = (1 - u) * (1 - u) * ax + 2 * (1 - u) * u * cx + u * u * bx;
                        final float qy = (1 - u) * (1 - u) * ay + 2 * (1 - u) * u * cy + u * u * by;
                        line(s, v.box(), px, py, qx, qy, th, Tokens.INK_2);
                        px = qx;
                        py = qy;
                    }
                } else {
                    line(s, v.box(), ax, ay, bx, by, th, Tokens.INK_2);
                }
            }
            case ZIGZAG -> {
                final float dx = bx - ax;
                final float dy = by - ay;
                final float len = (float) Math.max(1.0e-3, Math.sqrt(dx * dx + dy * dy));
                final float nx = -dy / len;
                final float ny = dx / len;
                final int coils = 8;
                final float amp = Math.max(2.0f, v.k() * 0.25f);
                float px = ax;
                float py = ay;
                for (int i = 1; i <= coils * 2; i++) {
                    final float u = i / (float) (coils * 2);
                    final float side = i == coils * 2 ? 0.0f : (i % 2 == 0 ? -amp : amp);
                    final float qx = ax + dx * u + nx * side;
                    final float qy = ay + dy * u + ny * side;
                    line(s, v.box(), px, py, qx, qy, th, Tokens.INK_2);
                    px = qx;
                    py = qy;
                }
            }
            case BAR -> line(s, v.box(), ax, ay, bx, by, th * 2.2f, Tokens.INK);
            default -> {
            }
        }
    }

    static void jet(PanelSurface s, View v, Unit u, Jet j) {
        final double[] p = DriveMath.place(u, j.x(), j.z());
        final float x = v.sx(p[0]);
        final float y = v.sy(p[1]);
        final float half = Math.max(2.0f, v.k() * 0.35f);
        if (!v.holds(x, y, half)) {
            return;
        }
        Shapes.rect(s, x - half, y - half, half * 2.0f, half * 2.0f, Tokens.INK_3);
        final double horizontal = Math.hypot(j.axis().x(), j.axis().z());
        if (!j.lit() || horizontal < 0.3) {
            return;
        }
        final double share = Float.isFinite(j.force()) && Float.isFinite(j.ceiling()) && j.ceiling() > 0f
                ? Math.min(1.0, j.force() / j.ceiling()) : Math.min(1.0, Math.abs(j.command()));
        final double yaw = Math.toRadians(Float.isFinite(u.yaw()) ? u.yaw() : 0.0);
        final double ax = j.axis().x() / horizontal;
        final double az = j.axis().z() / horizontal;
        final double wx = ax * Math.cos(yaw) + az * Math.sin(yaw);
        final double wz = -ax * Math.sin(yaw) + az * Math.cos(yaw);
        final float len = (float) (v.k() * (0.8 + 2.4 * share));
        final float ex = x + (float) wx * len;
        final float ey = y - (float) wz * len;
        final int col = Tokens.SERIES[5];
        line(s, v.box(), x, y, ex, ey, Math.max(1.5f, v.k() * 0.14f), col);
        final float head = Math.max(3.0f, v.k() * 0.4f);
        if (!v.holds(ex, ey, head)) {
            return;
        }
        final float hx = (float) wx * head;
        final float hy = -(float) wz * head;
        s.tri(ex + hx, ey + hy, col, ex - hy * 0.6f, ey + hx * 0.6f, col, ex + hy * 0.6f, ey - hx * 0.6f, col);
    }

    static void lamp(PanelSurface s, View v, Unit u, Lamp l, GroundSnapshot snap, long tick) {
        final double[] p = DriveMath.place(u, l.x(), l.z());
        final float x = v.sx(p[0]);
        final float y = v.sy(p[1]);
        final int lens = switch (l.colour()) {
            case 1 -> Tokens.LAMP_WHITE;
            case 2 -> Tokens.LAMP_RED;
            case 3 -> Tokens.LAMP_AMBER;
            default -> Tokens.INK_3;
        };
        final boolean lit = l.source() == LinkSource.LEFT ? DriveMath.indicator(snap, true, tick)
                : l.source() == LinkSource.RIGHT ? DriveMath.indicator(snap, false, tick) : l.strength() > 0;
        final float rad = Math.max(1.5f, v.k() * 0.2f);
        if (!v.holds(x, y, rad)) {
            return;
        }
        if (lit) {
            final float level = l.colour() == 2 && l.strength() < 15 ? 0.55f : 1.0f;
            // small halo, a big one reads as a fire
            final float glow = rad * 1.9f;
            if (v.holds(x, y, glow)) {
                Shapes.circle(s, x, y, glow, Tokens.alpha(lens, 0.18f * level));
            }
            Shapes.circle(s, x, y, rad, Tokens.alpha(lens, 0.6f + 0.4f * level));
        } else {
            Shapes.circle(s, x, y, rad, Tokens.mix(Tokens.CANVAS, lens, 0.28f));
        }
    }

    static void tracks(PanelSurface s, View v, DriveReadout r) {
        if (!Double.isFinite(r.leftSpeed) || !Double.isFinite(r.trackWidth)) {
            return;
        }
        final double half = r.trackWidth * 0.5 + 0.9;
        arrow(s, v, -half, r.leftSpeed, Tokens.SERIES[0]);
        arrow(s, v, half, r.rightSpeed, Tokens.SERIES[1]);
    }

    static void arrow(PanelSurface s, View v, double x, double speed, int col) {
        if (Math.abs(speed) < 0.05) {
            return;
        }
        final double len = Math.min(6.0, 0.6 * Math.abs(speed) + 0.8) * Math.signum(speed);
        final float x0 = v.sx(x);
        final float y0 = v.sy(-len * 0.5);
        final float y1 = v.sy(len * 0.5);
        final float t = Math.max(1.5f, v.k() * 0.15f);
        line(s, v.box(), x0, y0, x0, y1, t, col);
        final float head = Math.max(3.0f, v.k() * 0.45f) * (float) Math.signum(speed);
        if (v.holds(x0, y1 - head * 0.5f, Math.abs(head))) {
            s.tri(x0, y1 - head, col, x0 - Math.abs(head) * 0.7f, y1, col, x0 + Math.abs(head) * 0.7f, y1, col);
        }
    }

    public static String speedText(DriveReadout r) {
        return Long.toString(Math.round(Math.abs(r.speedKmh)));
    }

    public static void led(PanelSurface s, String text, float x, float y, float h, int on, int ghost) {
        final float pitch = h / LedFont.ROWS;
        final float dot = pitch * 0.78f;
        final float cell = LedFont.COLS * pitch;
        final float gap = pitch;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            final float x0 = x + i * (cell + gap);
            for (int row = 0; row < LedFont.ROWS; row++) {
                for (int col = 0; col < LedFont.COLS; col++) {
                    final boolean lit = LedFont.lit(c, col, row);
                    final int color = lit ? on : ghost;
                    if (((color >>> 24) & 0xFF) == 0) {
                        continue;
                    }
                    Shapes.rect(s, x0 + col * pitch, y + row * pitch, dot, dot, color);
                }
            }
        }
    }

    public static float ledWidth(String text, float h) {
        if (text.isEmpty()) {
            return 0.0f;
        }
        final float pitch = h / LedFont.ROWS;
        return text.length() * LedFont.COLS * pitch + (text.length() - 1) * pitch;
    }

    static int hud(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static float speed(PanelSurface s, Rect r, DriveReadout rd, Look look, boolean check) {
        return speed(s, r, rd, look, check, fieldDigits(rd));
    }

    public static float speed(PanelSurface s, Rect r, DriveReadout rd, Look look, boolean check, int field) {
        final String zeros = "000".substring(0, Math.max(1, Math.min(3, field)));
        final String text = check ? zeros.replace('0', '8') : speedText(rd);
        if (look == Look.RACING) {
            final String padded = text.length() < zeros.length() ? zeros.substring(text.length()) + text : text;
            final float h = Math.min(r.h, r.w / Math.max(1.0f, ledWidth(padded, 1.0f)));
            final float x = r.right() - ledWidth(padded, h);
            final int on = hud(THEME.primary);
            final int ghost = Tokens.alpha(hud(THEME.primary), 0.08f);

            final int lead = padded.length() - text.length();
            led(s, padded.substring(0, lead), x, r.y, h, ghost, ghost);
            led(s, text, x + (lead == 0 ? 0.0f : ledWidth(padded.substring(0, lead), h) + h / LedFont.ROWS), r.y, h, on,
                    ghost);
            return h;
        }
        final float scale = fit(s, text, r.w, r.h, Text.HERO * 3.0f);
        s.textRight(text, r.right(), r.y, Tokens.INK, scale);
        return Text.lineHeight(scale);
    }

    public record SpeedLayout(Rect digits, float unitX, float unitY, float unitScale) {
    }

    public static SpeedLayout speedWithUnit(PanelSurface s, Rect r, DriveReadout rd, Look look, boolean check) {
        final int field = Math.max(fieldDigits(rd), check ? 0 : speedText(rd).length());
        final String widest = "888".substring(0, Math.min(3, field));
        final float gap = Math.max(2.0f, r.h * 0.05f);

        float besideH = digitsHeight(s, widest, r.w, r.h, look);
        for (int i = 0; i < 3; i++) {
            final float u = unitScale(besideH);
            besideH = digitsHeight(s, widest, r.w - s.textWidth("km/h", u) - gap, r.h, look);
        }

        float underU = Text.MICRO;
        float underH = digitsHeight(s, widest, r.w, r.h - Text.lineHeight(underU) - gap, look);
        for (int i = 0; i < 3; i++) {
            underU = Math.max(Text.MICRO, Math.min(Text.SUB, underH / 7.0f * 0.24f));
            underH = digitsHeight(s, widest, r.w, r.h - Text.lineHeight(underU) - gap, look);
        }
        final boolean beside = besideH >= underH;
        final float digitsH = beside ? besideH : underH;
        final float unit = beside ? unitScale(digitsH) : underU;
        final float fieldW = look == Look.RACING ? ledWidth(widest, digitsH) : s.textWidth(widest, digitsH / 7.0f);
        final float unitW = s.textWidth("km/h", unit);
        final float groupW = beside ? fieldW + gap + unitW : Math.max(fieldW, unitW);

        final float lineH = look == Look.RACING ? digitsH : digitsH * Text.LINE_HEIGHT / 7.0f;
        final float groupH = beside ? lineH : digitsH + gap + Text.lineHeight(unit);
        final float x0 = r.x + Math.max(0.0f, (r.w - groupW) * 0.5f);
        final float top = r.y + Math.max(0.0f, (r.h - groupH) * 0.5f);
        final Rect digits = new Rect(beside ? x0 : x0 + groupW - fieldW, top, fieldW, digitsH);
        if (look == Look.RACING) {
            speed(s, digits, rd, look, check, field);
        } else {
            s.textRight(check ? widest : speedText(rd), digits.right(), digits.y, Tokens.INK, digitsH / 7.0f);
        }
        final float unitX = beside ? digits.right() + gap : digits.right() - unitW;
        final float unitY = beside ? digits.bottom() - unit * 7.0f : digits.bottom() + gap;
        s.text("km/h", unitX, unitY, look == Look.RACING ? hud(THEME.text) : Tokens.INK_2, unit);
        return new SpeedLayout(digits, unitX, unitY, unit);
    }

    static int fieldDigits(DriveReadout rd) {
        double most = Math.abs(rd.speedKmh);
        if (rd.model.known()) {
            most = Math.max(most, rd.model.cruise() * DriveMath.KMH);
        }
        final double top = rd.snapshot.odometer().topSpeed();
        if (Double.isFinite(top)) {
            most = Math.max(most, top * DriveMath.KMH);
        }
        return most >= 99.5 ? 3 : 2;
    }

    static float unitScale(float digitsH) {
        return Math.max(Text.MICRO, Math.min(Text.SUB, digitsH / 7.0f * 0.3f));
    }

    static float digitsHeight(PanelSurface s, String widest, float w, float h, Look look) {
        if (w <= 1.0f || h <= 1.0f) {
            return 1.0f;
        }
        if (look == Look.RACING) {
            return Math.min(h, w / Math.max(1.0f, ledWidth(widest, 1.0f)));
        }

        final float scale = Math.min(Text.HERO * 3.0f, Math.min(w / Math.max(1.0f, s.textWidth(widest, 1.0f)),
                h / Text.LINE_HEIGHT));
        return scale * 7.0f;
    }

    public static void gear(PanelSurface s, Rect r, char gear, Look look, boolean check) {
        final char g = check ? '8' : gear;
        if (look == Look.RACING) {
            final int on = hud(g == 'R' || g == 'B' ? THEME.bad : THEME.primary);
            Shapes.roundedRectOutline(s, r, 2.0f, Math.max(1.0f, r.h * 0.04f), hud(THEME.stripe));
            final float h = Math.min(r.h * 0.72f, r.w * 0.72f * LedFont.ROWS / LedFont.COLS);
            final float w = ledWidth(String.valueOf(g), h);
            led(s, String.valueOf(g), r.centerX() - w * 0.5f, r.centerY() - h * 0.5f, h, on, 0);
            return;
        }
        final int border = g == 'B' ? Tokens.CRITICAL : (g == 'H' || g == 'P' ? Tokens.CAUTION : Tokens.LINE_HI);
        Shapes.roundedRect(s, r, Tokens.R_CHIP, Tokens.RAISED);
        Shapes.roundedRectOutline(s, r, Tokens.R_CHIP, Math.max(1.0f, r.h * 0.035f), border);
        final String text = String.valueOf(g);
        final float scale = fit(s, text, r.w * 0.8f, r.h * 0.8f, Text.HERO * 2.0f);
        s.textCenterV(text, r.centerX(), r.centerY() + Text.lineHeight(scale) * 0.06f, Tokens.INK, scale);
    }

    record Telltale(String legend, boolean lit, int color) {
    }

    static List<Telltale> telltales(GroundFrame f, boolean check) {
        final GroundSnapshot snap = f.snapshot();
        final GroundSnapshot.Drive d = snap.drive();
        final boolean driving = d.driving();
        final boolean left = check || DriveMath.indicator(snap, true, f.tick());
        final boolean right = check || DriveMath.indicator(snap, false, f.tick());
        return List.of(
                new Telltale("<", left, Tokens.LAMP_AMBER),
                new Telltale("BRK", check || (d.braking() && !d.holding() && !d.parked()), Tokens.CRITICAL),
                new Telltale("BST", check || d.boosting(), Tokens.ACCENT),
                new Telltale("HLD", check || d.holding(), Tokens.CAUTION),
                new Telltale("PRK", check || d.parked(), Tokens.CAUTION),
                new Telltale("BEAM", check || (driving && snap.lights().beam()), Tokens.BEAM_BLUE),
                new Telltale(">", right, Tokens.LAMP_AMBER));
    }

    public static void telltaleRow(PanelSurface s, Rect r, GroundFrame f, Look look, boolean check) {
        final List<Telltale> all = new java.util.ArrayList<>(telltales(f, check));
        final float h = r.h;
        final float scale = Math.max(0.5f, Math.min(Text.TITLE, h / Text.LINE_HEIGHT * 0.72f));
        final float gap = Math.max(2.0f, h * 0.25f);
        float total = slotsWidth(s, all, h, scale, gap);
        for (final String drop : new String[]{"BEAM", "BST", "HLD"}) {
            if (total <= r.w) {
                break;
            }
            all.removeIf(t -> t.legend().equals(drop));
            total = slotsWidth(s, all, h, scale, gap);
        }
        if (total > r.w) {
            return;
        }

        final float spread = all.size() > 1 ? gap + (r.w - total) / (all.size() - 1) : gap;
        float x = r.x;
        for (final Telltale t : all) {
            final boolean arrow = t.legend().equals("<") || t.legend().equals(">");
            final float w = slotWidth(s, t, h, scale);
            final Rect cap = new Rect(x, r.y, w, h);
            x += w + spread;
            if (look == Look.RACING) {
                final int col = t.lit() ? hud(t.legend().equals("BRK") ? THEME.bad : t.legend().equals("BST") ? THEME.cool
                        : t.legend().equals("BEAM") ? THEME.cool : THEME.warn) : Tokens.alpha(hud(THEME.text), 0.18f);
                if (t.lit()) {
                    Shapes.roundedRect(s, cap, 1.5f, Tokens.alpha(col, 0.22f));
                }
                Shapes.roundedRectOutline(s, cap, 1.5f, 1.0f, col);
                if (arrow) {
                    chevron(s, cap, t.legend().equals("<"), col);
                } else {
                    s.textCenterV(t.legend(), cap.centerX(), cap.centerY(), col, scale);
                }
            } else if (arrow) {
                chevron(s, cap, t.legend().equals("<"), t.lit() ? t.color() : Tokens.alpha(Tokens.INK_3, 0.28f));
            } else if (t.lit()) {
                Shapes.roundedRect(s, cap, Tokens.R_CHIP, Tokens.alpha(t.color(), 0.18f));
                s.textCenterV(t.legend(), cap.centerX(), cap.centerY(), t.color(), scale);
            }
        }
    }

    static float slotWidth(PanelSurface s, Telltale t, float h, float scale) {
        final boolean arrow = t.legend().equals("<") || t.legend().equals(">");
        return arrow ? h * 1.1f : s.textWidth(t.legend(), scale) + h * 0.5f;
    }

    static float slotsWidth(PanelSurface s, List<Telltale> all, float h, float scale, float gap) {
        float w = 0.0f;
        for (final Telltale t : all) {
            w += slotWidth(s, t, h, scale);
        }
        return w + gap * Math.max(0, all.size() - 1);
    }

    static void chevron(PanelSurface s, Rect r, boolean left, int col) {
        final float cx = r.centerX();
        final float cy = r.centerY();
        final float hw = r.w * 0.34f;
        final float hh = r.h * 0.34f;
        if (left) {
            s.tri(cx - hw, cy, col, cx + hw * 0.6f, cy - hh, col, cx + hw * 0.6f, cy + hh, col);
        } else {
            s.tri(cx + hw, cy, col, cx - hw * 0.6f, cy + hh, col, cx - hw * 0.6f, cy - hh, col);
        }
    }

    public static void pedal(PanelSurface s, Rect r, double value, boolean signed, int color) {
        Shapes.roundedRect(s, r, r.h * 0.5f, Tokens.RAISED);
        if (!Double.isFinite(value)) {
            return;
        }
        final double v = DriveMath.clamp(value, -1.0, 1.0);
        if (signed) {
            final float mid = r.centerX();
            final float w = (float) (r.w * 0.5 * Math.abs(v));
            if (w > 0.5f) {
                Shapes.roundedRect(s, new Rect(v >= 0 ? mid : mid - w, r.y, w, r.h), r.h * 0.5f, color);
            }
            Shapes.rect(s, mid - 0.5f, r.y - 1.0f, 1.0f, r.h + 2.0f, Tokens.INK_3);
        } else {
            final float w = (float) (r.w * Math.max(0.0, v));
            if (w > 0.5f) {
                Shapes.roundedRect(s, new Rect(r.x, r.y, w, r.h), r.h * 0.5f, color);
            }
        }
    }

    public static void shiftBar(PanelSurface s, Rect r, GroundFrame f, double seconds, boolean check) {
        final DriveReadout rd = f.readout();
        final LedBar.Warning warning = rd.locking ? LedBar.Warning.LOCK : rd.spinning ? LedBar.Warning.SPIN
                : LedBar.Warning.NONE;
        final LedBar.Frame frame = check ? LedBar.live(1.0, LedBar.Warning.NONE, seconds)
                : LedBar.live(f.snapshot().drive().throttle(), warning, seconds);
        final float gap = Math.max(1.0f, r.w * 0.012f);
        final float w = (r.w - gap * (LedBar.LEDS - 1)) / LedBar.LEDS;
        for (int i = 0; i < LedBar.LEDS; i++) {
            final int role = frame.role()[i];
            final float level = frame.level()[i];
            final int rgb = switch (role) {
                case LedBar.LOW -> THEME.shiftLow;
                case LedBar.MID -> THEME.shiftMid;
                case LedBar.HIGH -> THEME.shiftHigh;
                case LedBar.BAD -> THEME.bad;
                case LedBar.COOL -> THEME.cool;
                default -> THEME.text;
            };
            final int col = role == LedBar.OFF ? Tokens.alpha(hud(THEME.text), 0.10f)
                    : Tokens.alpha(hud(rgb), 0.25f + 0.75f * level);
            Shapes.roundedRect(s, new Rect(r.x + i * (w + gap), r.y, w, r.h), Math.min(w, r.h) * 0.3f, col);
        }
    }

    public static void frictionCircle(PanelSurface s, float cx, float cy, float rad, GroundFrame f) {
        final DriveReadout r = f.readout();
        Shapes.circle(s, cx, cy, rad, Tokens.SURFACE);
        Shapes.arc(s, cx, cy, rad, 1.0f, 0.0f, (float) (Math.PI * 2.0), Tokens.LINE_HI);
        Shapes.arc(s, cx, cy, rad * 0.5f, 1.0f, 0.0f, (float) (Math.PI * 2.0), Tokens.LINE);
        Shapes.line(s, cx - rad, cy, cx + rad, cy, 1.0f, Tokens.LINE);
        Shapes.line(s, cx, cy - rad, cx, cy + rad, 1.0f, Tokens.LINE);
        final double peak = f.snapshot().log().peak();
        if (Double.isFinite(peak) && peak > 0.02) {
            Shapes.arc(s, cx, cy, (float) Math.min(1.0, peak) * rad, 1.0f, 0.0f, (float) (Math.PI * 2.0),
                    Tokens.alpha(Tokens.SERIES[0], 0.6f));
        }
        final float dx = (float) (DriveMath.clamp(r.latG, -1.2, 1.2) * rad);
        final float dy = (float) (-DriveMath.clamp(r.lonG, -1.2, 1.2) * rad);
        Shapes.circle(s, cx + dx, cy + dy, Math.max(2.0f, rad * 0.09f), Tokens.SERIES[0]);
    }

    public enum Boot {
        DARK, CHECK, WELCOME, LIVE;

        public static Boot at(GroundFrame f) {
            final double t = f.sinceHelm();
            if (!f.snapshot().drive().helmStill() || !Double.isFinite(t) || t >= HailSequence.RUN_END) {
                return LIVE;
            }
            if (t < HailSequence.DARK) {
                return DARK;
            }
            if (t < HailSequence.CHECK_END) {
                return CHECK;
            }
            return WELCOME;
        }
    }

    public static float fit(PanelSurface s, String text, float w, float h, float max) {
        final float byWidth = w / Math.max(1.0f, s.textWidth(text, 1.0f));
        final float byHeight = h / Text.lineHeight(1.0f);
        return Math.max(0.1f, Math.min(max, Math.min(byWidth, byHeight)));
    }

    public static String fitting(PanelSurface s, float width, float scale, String... wordings) {
        for (final String w : wordings) {
            if (s.textWidth(w, scale) <= width) {
                return w;
            }
        }
        return null;
    }

    public static int alertColor(GroundAlerts.Level level) {
        return level == GroundAlerts.Level.CRITICAL ? Tokens.CRITICAL
                : level == GroundAlerts.Level.CAUTION ? Tokens.CAUTION : Tokens.INK_2;
    }

    static String num(double v, int decimals) {
        if (!Double.isFinite(v)) {
            return "--";
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    static String signed(double v, int decimals) {
        if (!Double.isFinite(v)) {
            return "--";
        }
        return String.format(Locale.ROOT, "%+." + decimals + "f", v);
    }
}
