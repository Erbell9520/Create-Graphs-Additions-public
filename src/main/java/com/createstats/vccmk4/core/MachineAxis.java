package com.createstats.vccmk4.core;

import java.util.List;
import java.util.Locale;

// two kinds of machine
public final class MachineAxis {
    public static final int DEFAULT_RPM = 64;
    public static final int MIN_RPM = 8;
    public static final int MAX_RPM = 256;
    public static final int RPM_STEP = 8;

    private static final List<String> RUNNERS = List.of("drill", "saw", "fan", "millstone", "mixer", "crushing",
            "pump", "press", "borehead", "cutting", "grinder", "harvester");

    public record Setting(int rpm, boolean latched, boolean inverted) {
        public static final Setting SPRUNG = new Setting(DEFAULT_RPM, false, false);
        public static final Setting LATCHED = new Setting(DEFAULT_RPM, true, false);

        public Setting sane() {
            int r = Math.max(MIN_RPM, Math.min(MAX_RPM, rpm));
            return r == rpm ? this : new Setting(r, latched, inverted);
        }

        public Setting withRpm(int v) {
            return new Setting(v, latched, inverted).sane();
        }

        public Setting withLatched(boolean v) {
            return new Setting(rpm, v, inverted);
        }

        public Setting withInverted(boolean v) {
            return new Setting(rpm, latched, v);
        }
    }

    private int state;
    private double lastInput;

    public int state() {
        return state;
    }

    public void reset() {
        state = 0;
        lastInput = 0.0;
    }

    public double step(Setting setting, double input, boolean twoKeys) {
        Setting s = setting.sane();
        double in = GroundSteer.clamp(s.inverted() ? -input : input, -1.0, 1.0);
        if (!s.latched()) {
            state = 0;
            lastInput = in;
            return in;
        }
        boolean up = in > 0.5 && lastInput <= 0.5;
        boolean down = in < -0.5 && lastInput >= -0.5;
        lastInput = in;
        if (twoKeys) {
            if (up) state = Math.min(1, state + 1);
            if (down) state = Math.max(-1, state - 1);
        } else if (up || down) {
            state = state != 0 ? 0 : (up ? 1 : -1);
        }
        return state;
    }

    public static boolean runnerByDefault(List<String> blockIds) {
        if (blockIds == null) return false;
        for (String id : blockIds) {
            String lower = id == null ? "" : id.toLowerCase(Locale.ROOT);
            for (String word : RUNNERS) {
                if (lower.contains(word)) return true;
            }
        }
        return false;
    }

    public static Setting defaultSetting(boolean runner) {
        return runner ? Setting.LATCHED : Setting.SPRUNG;
    }
}
