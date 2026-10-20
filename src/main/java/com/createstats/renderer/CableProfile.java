package com.createstats.renderer;

import com.createstats.power.CableTier;

import java.util.List;

// data not a switch. two defects shipped from the switch version at once
public final class CableProfile {
    public static final int ATLAS = 32;

    public static final int STRIP_OFFSET = 8;

    public static final double PULSE_STEP = 0.7;

    public record Region(int u, int v) {
    }

    public record Skin(Region top, Region flank, Region end, Region cut) {
        static Skin uniform(Region all) {
            return new Skin(all, all, all, all);
        }

        static Skin of(Region top, Region flank, Region end) {
            return new Skin(top, flank, end, end);
        }
    }

    public record Band(float c0, float c1, float n0, float n1, Skin skin, boolean dyed) {
        public float half() {
            return Math.max(8.0f - c0, c1 - 8.0f);
        }
    }

    public record Part(float a0, float a1, float c0, float c1, float n0, float n1, Skin skin,
                       boolean dyed) {
    }

    public record Grade(String atlas, List<Band> bands, List<Part> hardware, int spacing,
                        List<Part> fitting, List<Part> splice, boolean nested, boolean strands,
                        Skin gland, Region paint) {
        public float thicknessPx() {
            float t = 0.0f;
            for (Band b : bands) {
                t = Math.max(t, b.n1());
            }
            return t;
        }

        public float halfWidthPx() {
            float h = 0.0f;
            for (Band b : bands) {
                h = Math.max(h, b.half());
            }
            return h;
        }

        public float reachPx() {
            float r = thicknessPx();
            for (List<Part> parts : List.of(hardware, fitting, splice)) {
                for (Part p : parts) {
                    r = Math.max(r, p.n1());
                }
            }
            return r;
        }
    }

    private static Region r(int u, int v) {
        return new Region(u, v);
    }

    private static final Region S_PAINT = r(8, 16);
    private static final Skin S_STEEL = Skin.uniform(r(16, 16));

    static final Grade SURFACE = new Grade(
            "cable_skin_surface",
            List.of(
                    new Band(7, 9, 0, 2, new Skin(r(0, 0), r(0, 2), r(0, 16), r(2, 16)), false),

                    new Band(7.5f, 8.5f, 2, 2.1f, Skin.of(r(0, 4), r(0, 4), S_PAINT), true)),
            List.of(new Part(7.25f, 8.75f, 6.6f, 9.4f, 0, 2.45f, S_STEEL, false)),
            3,
            List.of(),
            List.of(),
            false, true, S_STEEL, S_PAINT);

    private static final Region T_PAINT = r(0, 20);
    private static final Skin T_PAINTED = Skin.uniform(T_PAINT);

    static final Grade TRAY = new Grade(
            "cable_skin_tray",
            List.of(
                    new Band(6, 10, 0, 2, Skin.of(r(0, 0), r(0, 4), r(0, 16)), false),
                    new Band(7, 9, 2, 3, Skin.of(r(0, 6), r(0, 8), r(4, 16)), false)),

            List.of(
                    new Part(7, 9, 5.85f, 10.15f, 0, 2.15f, T_PAINTED, true),
                    new Part(7, 9, 6.85f, 9.15f, 2.15f, 3.15f, T_PAINTED, true)),
            2,

            List.of(
                    new Part(0, 0.75f, 5.5f, 10.5f, 0, 3.4f,
                            Skin.of(r(10, 20), r(11, 20), r(12, 20)), false),
                    new Part(0.75f, 1.25f, 5.4f, 10.6f, 0, 3.5f, T_PAINTED, true)),
            List.of(),
            false, true, Skin.uniform(r(20, 20)), T_PAINT);

    private static final Region D_PAINT = r(0, 20);
    private static final Skin D_PAINTED = Skin.uniform(D_PAINT);

    static final Grade DUCT = new Grade(
            "cable_skin_duct",
            List.of(
                    new Band(5, 11, 0, 2, Skin.of(r(0, 0), r(0, 6), r(0, 16)), false),
                    new Band(6, 10, 2, 3, Skin.of(r(0, 8), r(0, 12), r(6, 16)), false)),

            List.of(
                    new Part(7, 9, 4, 12, 0, 3.6f, Skin.of(r(10, 16), r(12, 16), r(14, 16)), false),
                    new Part(7.5f, 8.5f, 3.85f, 12.15f, 0, 3.75f, D_PAINTED, true)),
            2,
            List.of(
                    new Part(0, 0.75f, 4.5f, 11.5f, 0, 3.4f,
                            Skin.of(r(22, 16), r(23, 16), r(24, 16)), false),
                    new Part(0.75f, 1.25f, 4.4f, 11.6f, 0, 3.5f, D_PAINTED, true)),
            List.of(),
            false, true, Skin.uniform(r(12, 22)), D_PAINT);

    private static final Region K_PAINT = r(14, 16);
    private static final Skin K_PAINTED = Skin.uniform(K_PAINT);
    private static final Skin K_ALU = Skin.uniform(r(4, 16));
    private static final Skin K_CORE = new Skin(r(0, 0), r(0, 2), r(0, 16), r(2, 16));

    static final float[] CORES = {-2.5f, 0.0f, 2.5f};

    static final Grade TRUNK = new Grade(
            "cable_skin_trunk",
            List.of(
                    new Band(8 + CORES[0] - 1, 8 + CORES[0] + 1, 0, 2, K_CORE, false),
                    new Band(8 + CORES[1] - 1, 8 + CORES[1] + 1, 0, 2, K_CORE, false),
                    new Band(8 + CORES[2] - 1, 8 + CORES[2] + 1, 0, 2, K_CORE, false)),
            List.of(

                    new Part(7, 9, 3.9f, 12.1f, 2, 2.5f, K_ALU, false),
                    new Part(7, 9, 3.4f, 3.9f, 0, 2, K_ALU, false),
                    new Part(7, 9, 12.1f, 12.6f, 0, 2, K_ALU, false),
                    new Part(7, 9, 2.4f, 3.4f, 0, 0.5f, K_ALU, false),
                    new Part(7, 9, 12.6f, 13.6f, 0, 0.5f, K_ALU, false),

                    tape(4.5f, CORES[0]), tape(4.5f, CORES[1]), tape(4.5f, CORES[2]),
                    tape(10.5f, CORES[0]), tape(10.5f, CORES[1]), tape(10.5f, CORES[2])),
            2,
            List.of(),
            List.of(
                    new Part(3.5f, 12.5f, 3.5f, 12.5f, 0, 3.25f,
                            Skin.of(r(22, 16), r(14, 26), r(14, 26)), false),
                    new Part(6, 10, 6, 10, 3.25f, 3.4f, K_PAINTED, true)),
            true, false, K_ALU, K_PAINT);

    private static Part tape(float a0, float core) {
        return new Part(a0, a0 + 1, 8 + core - 1.1f, 8 + core + 1.1f, 0, 2.1f, K_PAINTED, true);
    }

    public static final float SPLICE_HALF = 4.5f;

    public static Grade of(CableTier tier) {
        return switch (tier) {
            case SURFACE -> SURFACE;
            case TRAY -> TRAY;
            case DUCT -> DUCT;
            case TRUNK -> TRUNK;
        };
    }

    private CableProfile() {
    }
}
