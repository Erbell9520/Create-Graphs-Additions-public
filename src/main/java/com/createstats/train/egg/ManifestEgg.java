package com.createstats.train.egg;

// six girls, one motion
public final class ManifestEgg {
    public static final String NAME = "Manifest";

    public static final int CARRIAGES = 6;

    public static final double MIN_SPEED = 0.01;

    public static final int BEAT_TICKS = 10;

    public static final int HALF_BEAT_TICKS = BEAT_TICKS / 2;

    public static final int BEATS_PER_BAR = 4;
    public static final int BAR_TICKS = BEAT_TICKS * BEATS_PER_BAR;

    public static final int SHOW_TICKS = 160;

    public static final int ROLL_CALL_SPACING = 3;

    public static final double AUDIENCE_RADIUS = 16.0;

    public static final int[] COLOURS = {0x3CE05A, 0xB45CFF, 0xFF6FB5, 0x4FA8FF, 0xFF5A5A, 0xFFE04F};

    public static final int[] ROLL_CALL_NOTES = {6, 10, 13, 15, 18, 22};

    public static final float[] ROLL_CALL_PITCHES = notePitches(ROLL_CALL_NOTES);

    private ManifestEgg() {
    }

    public static boolean isManifest(String name, int carriages) {
        return carriages == CARRIAGES && name != null && NAME.equalsIgnoreCase(name.strip());
    }

    public static boolean matches(String name, int carriages, double speed) {
        return Math.abs(speed) > MIN_SPEED && isManifest(name, carriages);
    }

    public static boolean isBeat(long gameTime) {
        return Math.floorMod(gameTime, (long) BEAT_TICKS) == 0;
    }

    public static boolean isMove(long gameTime) {
        return Math.floorMod(gameTime, (long) BAR_TICKS) == 0;
    }

    public static boolean isHalfBeat(long gameTime) {
        return Math.floorMod(gameTime, (long) HALF_BEAT_TICKS) == 0;
    }

    public static long showUntil(long arrivalTick) {
        final long earliest = arrivalTick + SHOW_TICKS;
        return earliest + Math.floorMod(-earliest, (long) BAR_TICKS);
    }

    public static boolean isShowing(long gameTime, long showUntil) {
        return gameTime < showUntil && showUntil - gameTime <= SHOW_TICKS + BAR_TICKS;
    }

    public static boolean isFinalBeat(long gameTime, long showUntil) {
        return isShowing(gameTime, showUntil) && showUntil - gameTime <= BEAT_TICKS;
    }

    public static float notePitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    public static double noteHue(int rgb) {
        final double r = (rgb >> 16 & 0xFF) / 255.0;
        final double g = (rgb >> 8 & 0xFF) / 255.0;
        final double b = (rgb & 0xFF) / 255.0;
        int best = 0;
        double bestError = Double.MAX_VALUE;
        for (int note = 0; note <= 24; note++) {
            final double v = note / 24.0;
            final double dr = noteChannel(v, 0.0) - r;
            final double dg = noteChannel(v, 1.0 / 3.0) - g;
            final double db = noteChannel(v, 2.0 / 3.0) - b;
            final double error = dr * dr + dg * dg + db * db;
            if (error < bestError) {
                bestError = error;
                best = note;
            }
        }
        return best / 24.0;
    }

    static double noteChannel(double v, double offset) {
        return Math.max(0.0, Math.sin((v + offset) * 2.0 * Math.PI) * 0.65 + 0.35);
    }

    private static float[] notePitches(int[] notes) {
        final float[] pitches = new float[notes.length];
        for (int i = 0; i < notes.length; i++) {
            pitches[i] = notePitch(notes[i]);
        }
        return pitches;
    }
}
