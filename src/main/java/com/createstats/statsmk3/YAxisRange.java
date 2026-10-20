package com.createstats.statsmk3;

// auto carries no bounds, fixed carries both. not everything starts at zero
public record YAxisRange(Mode mode, float min, float max) {
    public enum Mode {
        AUTO,
        FIXED
    }

    private static final YAxisRange AUTO = new YAxisRange(Mode.AUTO, 0.0f, 1.0f);

    public YAxisRange {
        mode = mode == null ? Mode.AUTO : mode;
        if (mode == Mode.AUTO || !validFixed(min, max)) {
            mode = Mode.AUTO;
            min = 0.0f;
            max = 1.0f;
        }
    }

    public static YAxisRange auto() {
        return AUTO;
    }

    public static YAxisRange fixed(float min, float max) {
        return validFixed(min, max) ? new YAxisRange(Mode.FIXED, min, max) : AUTO;
    }

    public static boolean validFixed(float min, float max) {
        return Float.isFinite(min) && Float.isFinite(max) && max > min;
    }

    public boolean isFixed() {
        return this.mode == Mode.FIXED;
    }

    public static YAxisRange rounded(float observedMin, float observedMax) {
        if (!Float.isFinite(observedMin) || !Float.isFinite(observedMax)) {
            return fixed(0.0f, 1.0f);
        }
        float lo = Math.min(observedMin, observedMax);
        float hi = Math.max(observedMin, observedMax);
        if (hi - lo < 1.0e-6f) {
            final float pad = Math.max(1.0f, Math.abs(lo) * 0.1f);
            lo -= pad;
            hi += pad;
        }
        final float step = niceNumber((hi - lo) / 4.0f);
        lo = (float) Math.floor(lo / step) * step;
        hi = (float) Math.ceil(hi / step) * step;
        if (hi <= lo) {
            hi = lo + Math.max(1.0f, step);
        }
        return fixed(cleanZero(lo), cleanZero(hi));
    }

    private static float niceNumber(float value) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            return 1.0f;
        }
        final double exponent = Math.floor(Math.log10(value));
        final double scale = Math.pow(10.0, exponent);
        final double fraction = value / scale;
        final double nice = fraction <= 1.0 ? 1.0
                : fraction <= 2.0 ? 2.0
                : fraction <= 5.0 ? 5.0 : 10.0;
        return (float) (nice * scale);
    }

    private static float cleanZero(float value) {
        return value == 0.0f ? 0.0f : value;
    }
}
