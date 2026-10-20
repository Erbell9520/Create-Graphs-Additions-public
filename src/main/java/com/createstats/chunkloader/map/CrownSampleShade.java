package com.createstats.chunkloader.map;

public final class CrownSampleShade {
    public static final int STEP = 2;

    public static final int SIDE = 16 / STEP;

    public static final int PER_CHUNK = SIDE * SIDE;

    public static final int LOW = 0;
    public static final int NORMAL = 1;
    public static final int HIGH = 2;

    public static final int MAX_DEPTH = 12;

    private CrownSampleShade() {
    }

    public static int index(int localX, int localZ) {
        return (localZ / STEP) * SIDE + localX / STEP;
    }

    public static int readAt(int sample) {
        return sample * STEP + STEP / 2;
    }

    public static int water(int depth, int blockX, int blockZ) {
        final double d = Math.min(MAX_DEPTH, Math.max(0, depth)) * 0.1 + ((blockX + blockZ) & 1) * 0.2;
        return d < 0.5 ? HIGH : d > 0.9 ? LOW : NORMAL;
    }

    public static int land(int y, int northY) {
        return y > northY ? HIGH : y < northY ? LOW : NORMAL;
    }

    public static byte pack(int colourId, int brightness) {
        return colourId <= 0 ? 0 : (byte) ((colourId << 2) | (brightness & 3));
    }
}
