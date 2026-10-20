package com.createstats.chunkloader.map;

public final class CrownGuess {
    public static final int PAPER = 0xFFD6BE96;

    public static final double FAINT = 0.35;

    public enum Kind {
        NONE(0),
        WATER(0xFF308CBE),
        SAND(0xFFDECE96),
        GRASS(0xFF70A046),
        SNOW(0xFFECF0F4);

        public final int argb;

        Kind(int argb) {
            this.argb = argb;
        }

        static Kind of(int ordinal) {
            final Kind[] all = values();
            return ordinal > 0 && ordinal < all.length ? all[ordinal] : NONE;
        }
    }

    public static final int SHALLOW = 6;
    public static final int DEEP = 16;

    public static final int STEP = 2;

    public static final int SUNK = 3;

    private CrownGuess() {
    }

    public static Kind classify(boolean oceanOrRiver, boolean sandy, boolean snowy, int surface, int seaLevel) {
        if (oceanOrRiver || surface < seaLevel - SUNK) {
            return Kind.WATER;
        }
        if (snowy) {
            return Kind.SNOW;
        }
        return sandy ? Kind.SAND : Kind.GRASS;
    }

    public static int shade(Kind kind, int surface, int northSurface, int seaLevel) {
        if (kind == Kind.WATER) {
            final int depth = seaLevel - surface;
            return depth < SHALLOW ? CrownSampleShade.HIGH : depth > DEEP ? CrownSampleShade.LOW : CrownSampleShade.NORMAL;
        }
        final int rise = surface - northSurface;
        return rise >= STEP ? CrownSampleShade.HIGH : rise <= -STEP ? CrownSampleShade.LOW : CrownSampleShade.NORMAL;
    }

    public static byte encode(Kind kind, int shade) {
        return kind == Kind.NONE ? 0 : (byte) ((kind.ordinal() << 2) | (shade & 3));
    }

    public static Kind kind(byte b) {
        return Kind.of((b & 0xFF) >>> 2);
    }

    public static int shadeOf(byte b) {
        return b & 3;
    }

    public static int argb(byte b) {
        final Kind k = kind(b);
        if (k == Kind.NONE) {
            return PAPER;
        }
        final double m = switch (shadeOf(b)) {
            case CrownSampleShade.LOW -> 180.0 / 220.0;
            case CrownSampleShade.HIGH -> 255.0 / 220.0;
            default -> 1.0;
        };
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            final double c = Math.min(255.0, ((k.argb >> shift) & 0xFF) * m);
            final double p = (PAPER >> shift) & 0xFF;
            out |= ((int) Math.round(c * (1 - FAINT) + p * FAINT) & 0xFF) << shift;
        }
        return out;
    }

    public static int surface(double depthAt0, double depthAt1, int y0, int y1) {
        final double slope = depthAt1 - depthAt0;
        if (!Double.isFinite(depthAt0) || !Double.isFinite(depthAt1) || Math.abs(slope) < 1e-9 || y1 == y0) {
            return Integer.MIN_VALUE;
        }
        final double y = y0 - depthAt0 * (y1 - y0) / slope;
        return Double.isFinite(y) ? (int) Math.floor(Math.max(-4096, Math.min(4096, y))) : Integer.MIN_VALUE;
    }
}
