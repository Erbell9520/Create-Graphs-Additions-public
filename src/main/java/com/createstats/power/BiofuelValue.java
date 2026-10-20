package com.createstats.power;

// duration never rate
public final class BiofuelValue {
    public static final int OUTPUT_FE_PER_TICK = 180;

    public static final int COMPOST_TICKS = 100;

    public static final int UNKNOWN_TICKS = BiofuelGrade.GREEN.burnTicks();

    public static final int COMPACTED_BURN_TICKS = BiofuelGrade.PRESSED.burnTicks();

    public static final float NOT_COMPOSTABLE = -1.0f;

    private BiofuelValue() {
    }

    public static int burnTicks(BiofuelGrade grade, float compostChance, int furnaceBurnTicks) {
        if (grade != null) {
            return grade.burnTicks();
        }
        if (compostChance > 0.0f) {
            return Math.max(1, Math.round(compostChance * COMPOST_TICKS));
        }
        if (furnaceBurnTicks > 0) {
            return Math.min(furnaceBurnTicks, BiofuelGrade.PLANK.burnTicks());
        }
        return UNKNOWN_TICKS;
    }

    public static long totalFe(int burnTicks) {
        return Math.max(0L, (long) burnTicks * OUTPUT_FE_PER_TICK);
    }

    public static String duration(int ticks) {
        final int safe = Math.max(0, ticks);
        if (safe < 1_200) {
            return seconds(safe) + " s";
        }
        final int wholeSeconds = (int) Math.round(safe / 20.0);
        final int minutes = wholeSeconds / 60;
        final int rest = wholeSeconds % 60;
        return rest == 0 ? minutes + " min" : minutes + " min " + rest + " s";
    }

    private static String seconds(int ticks) {
        if (ticks % 20 == 0) {
            return Integer.toString(ticks / 20);
        }
        final String text = String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    public static int tickOutput(int roomInBuffer) {
        return Math.max(0, Math.min(OUTPUT_FE_PER_TICK, roomInBuffer));
    }
}
