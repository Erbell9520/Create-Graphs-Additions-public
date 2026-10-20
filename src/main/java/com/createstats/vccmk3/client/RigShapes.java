package com.createstats.vccmk3.client;

import com.createstats.vccmk3.model.Airframe;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RigShapes {
    public enum Kind {
        PANEL,

        DISC,

        WHEEL,

        BLADE
    }

    public record SlotShape(String key, Kind kind, int[] xs, int[] ys,
                            int anchorX, int anchorY, int radius, int side, boolean steered) {
        public boolean contains(double mx, double my) {
            if (kind == Kind.DISC) {
                double dx = mx - anchorX;
                double dy = my - anchorY;
                return dx * dx + dy * dy <= (double) radius * radius;
            }
            boolean in = false;
            for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                if ((ys[i] > my) != (ys[j] > my)
                        && mx < (double) (xs[j] - xs[i]) * (my - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                    in = !in;
                }
            }
            return in;
        }
    }

    public static double designHeight(Airframe frame) {
        return switch (frame) {
            case FIXED_WING -> 600.0;
            case AIRSHIP -> 660.0;
            case GROUND -> 540.0;

            case HYBRID -> 520.0;
        };
    }

    public static double designWidth(Airframe frame) {
        return switch (frame) {
            case FIXED_WING -> 620.0;
            case AIRSHIP -> 460.0;
            case GROUND -> 420.0;
            case HYBRID -> 520.0;
        };
    }

    private RigShapes() {}

    public interface Project {
        int x(double dx);

        int y(double dy);
    }

    public static Project projector(int cx, int cy, double scale) {
        return new Project() {
            @Override
            public int x(double dx) {
                return (int) Math.round(cx + dx * scale);
            }

            @Override
            public int y(double dy) {
                return (int) Math.round(cy + dy * scale);
            }
        };
    }

    public static Map<String, SlotShape> layout(Airframe frame, Project p) {
        return switch (frame) {
            case FIXED_WING -> fixedWing(p);
            case AIRSHIP -> airship(p);
            case GROUND -> ground(p);

            case HYBRID -> new LinkedHashMap<>();
        };
    }

    public static final double SPAN_ROOT = 18.0;
    public static final double SPAN_TIP = 300.0;
    public static final double TAIL_ROOT = 14.0;
    public static final double TAIL_TIP = 140.0;

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double wingT(double ax) {
        return Math.max(0.0, Math.min(1.0, (ax - SPAN_ROOT) / (SPAN_TIP - SPAN_ROOT)));
    }

    public static double wingLe(double ax) {
        return lerp(-120.0, -46.0, wingT(ax));
    }

    public static double wingHinge(double ax) {
        return lerp(-6.0, -14.0, wingT(ax));
    }

    public static double wingTe(double ax) {
        return lerp(34.0, 4.0, wingT(ax));
    }

    private static double tailT(double ax) {
        return Math.max(0.0, Math.min(1.0, (ax - TAIL_ROOT) / (TAIL_TIP - TAIL_ROOT)));
    }

    public static double tailLe(double ax) {
        return lerp(150.0, 178.0, tailT(ax));
    }

    public static double tailHinge(double ax) {
        return lerp(186.0, 198.0, tailT(ax));
    }

    public static double tailTe(double ax) {
        return lerp(206.0, 212.0, tailT(ax));
    }

    private static SlotShape panel(String key, Project p, int side, double a, double b,
                                   java.util.function.DoubleUnaryOperator front,
                                   java.util.function.DoubleUnaryOperator back) {
        int[] xs = {p.x(side * a), p.x(side * b), p.x(side * b), p.x(side * a)};
        int[] ys = {p.y(front.applyAsDouble(a)), p.y(front.applyAsDouble(b)),
                p.y(back.applyAsDouble(b)), p.y(back.applyAsDouble(a))};
        double mid = (a + b) / 2.0;
        int ax = p.x(side * mid);
        int ay = p.y((front.applyAsDouble(mid) + back.applyAsDouble(mid)) / 2.0);
        return new SlotShape(key, Kind.PANEL, xs, ys, ax, ay, 0, side, false);
    }

    private static Map<String, SlotShape> fixedWing(Project p) {
        Map<String, SlotShape> m = new LinkedHashMap<>();
        m.put("NOSE_ENGINE", new SlotShape("NOSE_ENGINE", Kind.DISC, new int[0], new int[0],
                p.x(0), p.y(-272), Math.max(8, p.x(27) - p.x(0)), 0, false));
        for (int side : new int[] {-1, 1}) {
            String s = side < 0 ? "PORT" : "STBD";
            m.put(s + "_ENGINE", new SlotShape(s + "_ENGINE", Kind.DISC, new int[0], new int[0],
                    p.x(side * 150), p.y(-92), Math.max(7, p.x(23) - p.x(0)), side, false));
            m.put(s + "_FLAP", panel(s + "_FLAP", p, side, 46, 152, RigShapes::wingHinge, RigShapes::wingTe));
            m.put(s + "_AILERON", panel(s + "_AILERON", p, side, 172, 292, RigShapes::wingHinge, RigShapes::wingTe));
            m.put(s + "_ELEVATOR", panel(s + "_ELEVATOR", p, side, 30, 134, RigShapes::tailHinge, RigShapes::tailTe));
        }
        m.put("RUDDER", new SlotShape("RUDDER", Kind.BLADE,
                new int[] {p.x(-8), p.x(8), p.x(8), p.x(-8)},
                new int[] {p.y(214), p.y(214), p.y(262), p.y(262)},
                p.x(0), p.y(238), 0, 0, false));
        m.put("AIRBRAKE", new SlotShape("AIRBRAKE", Kind.PANEL,
                new int[] {p.x(-26), p.x(26), p.x(26), p.x(-26)},
                new int[] {p.y(48), p.y(48), p.y(82), p.y(82)},
                p.x(0), p.y(65), 0, 0, false));
        return m;
    }

    public static final double ENV_NOSE = -336.0;
    public static final double ENV_TAIL = 268.0;
    public static final double ENV_HALF = 116.0;

    public static double envelopeHalf(double y) {
        double t = (y - ENV_NOSE) / (ENV_TAIL - ENV_NOSE);
        if (t <= 0.0 || t >= 1.0) return 0.0;
        if (t < 0.22) {
            double u = (0.22 - t) / 0.22;
            return ENV_HALF * Math.sqrt(Math.max(0.0, 1.0 - u * u));
        }
        if (t < 0.46) return ENV_HALF;
        double u = (t - 0.46) / 0.54;
        return ENV_HALF * Math.max(0.0, 1.0 - Math.pow(u, 1.7));
    }

    private static SlotShape box(String key, Project p, double x0, double y0, double x1, double y1) {
        int[] xs = {p.x(x0), p.x(x1), p.x(x1), p.x(x0)};
        int[] ys = {p.y(y0), p.y(y0), p.y(y1), p.y(y1)};
        return new SlotShape(key, Kind.PANEL, xs, ys, p.x((x0 + x1) / 2), p.y((y0 + y1) / 2), 0, 0, false);
    }

    private static Map<String, SlotShape> airship(Project p) {
        Map<String, SlotShape> m = new LinkedHashMap<>();
        m.put("BURNER", box("BURNER", p, -58, -150, 58, -86));
        m.put("VENT", box("VENT", p, -58, -70, 58, -12));
        m.put("BALLAST", box("BALLAST", p, -42, 96, 42, 142));
        for (int side : new int[] {-1, 1}) {
            String s = side < 0 ? "PORT" : "STBD";
            m.put(s + "_ENGINE", new SlotShape(s + "_ENGINE", Kind.DISC, new int[0], new int[0],
                    p.x(side * 158), p.y(44), Math.max(7, p.x(25) - p.x(0)), side, false));
            m.put(s + "_ELEVATOR", new SlotShape(s + "_ELEVATOR", Kind.PANEL,
                    new int[] {p.x(side * 98), p.x(side * 214), p.x(side * 214), p.x(side * 98)},
                    new int[] {p.y(224), p.y(234), p.y(262), p.y(256)},
                    p.x(side * 156), p.y(242), 0, side, false));
        }

        m.put("UPPER_RUDDER", new SlotShape("UPPER_RUDDER", Kind.BLADE,
                new int[] {p.x(-23), p.x(-5), p.x(-5), p.x(-23)},
                new int[] {p.y(248), p.y(248), p.y(302), p.y(302)},
                p.x(-14), p.y(275), 0, -1, false));
        m.put("LOWER_RUDDER", new SlotShape("LOWER_RUDDER", Kind.BLADE,
                new int[] {p.x(5), p.x(23), p.x(23), p.x(5)},
                new int[] {p.y(248), p.y(248), p.y(302), p.y(302)},
                p.x(14), p.y(275), 0, 1, false));
        return m;
    }

    public static final int[] AXLE_Y = {-180, 0, 180};
    public static final String[] AXLE_NAME = {"FRONT", "MID", "REAR"};

    private static Map<String, SlotShape> ground(Project p) {
        Map<String, SlotShape> m = new LinkedHashMap<>();
        for (int a = 0; a < AXLE_Y.length; a++) {
            for (int side : new int[] {-1, 1}) {
                String key = AXLE_NAME[a] + (side < 0 ? "_LEFT" : "_RIGHT");
                int cx = p.x(side * 140);
                int cy = p.y(AXLE_Y[a]);
                int hw = Math.max(4, p.x(15) - p.x(0));
                int hh = Math.max(9, p.y(35) - p.y(0));
                m.put(key, new SlotShape(key, Kind.WHEEL,
                        new int[] {cx - hw, cx + hw, cx + hw, cx - hw},
                        new int[] {cy - hh, cy - hh, cy + hh, cy + hh},
                        cx, cy, 0, side, a == 0));
            }
        }
        return m;
    }
}
