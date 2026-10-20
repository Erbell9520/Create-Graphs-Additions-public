package com.createstats.blockentity;

public final class FlywheelVoice {
    public static final float STORED_TOP_RPM = 128f;

    public static final float ACTIVITY_FLOOR = 0.03f;

    public static final float HUM_QUIET = 0.10f;
    public static final float HUM_FULL = 0.30f;

    public static final float CROWD_STEP = 0.12f;
    public static final int CROWD_MAX = 3;

    public static final float PITCH_LOW = 0.75f;
    public static final float PITCH_HIGH = 1.2f;

    public static final float CHIME_AT_PERCENT = 99.5f;
    public static final float CHIME_REARM_PERCENT = 95f;

    public static final int THUNK_GAP_TICKS = 200;

    private FlywheelVoice() {
    }

    public static float rotorRpm(float chargeFraction) {
        float f = clamp01(chargeFraction);
        return f <= 0f ? 0f : STORED_TOP_RPM * (float) Math.sqrt(f);
    }

    public static float activity(boolean charging, boolean discharging, float chargeLoad, float outputLoad) {
        if (charging) {
            return clamp01(chargeLoad);
        }
        if (discharging) {
            return clamp01(outputLoad);
        }
        return 0f;
    }

    public static float humVolume(float activity, float configVolume, int voices) {
        if (activity < ACTIVITY_FLOOR || configVolume <= 0f) {
            return 0f;
        }
        float above = (clamp01(activity) - ACTIVITY_FLOOR) / (1f - ACTIVITY_FLOOR);
        float base = HUM_QUIET + (HUM_FULL - HUM_QUIET) * (float) Math.sqrt(above);
        float crowd = 1f + CROWD_STEP * Math.max(0, Math.min(voices - 1, CROWD_MAX));
        return base * crowd * configVolume;
    }

    public static float humPitch(float rotorFraction) {
        return PITCH_LOW + (PITCH_HIGH - PITCH_LOW) * clamp01(rotorFraction);
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, v));
    }

    public static final class Cues {
        public static final int NONE = 0;

        public static final int CHIME = 1;

        public static final int THUNK = 2;

        private boolean primed;
        private boolean chimeArmed;
        private boolean wasHalted;
        private long lastThunk = Long.MIN_VALUE / 2;

        public int update(float chargePercent, boolean charging, boolean halted, long gameTime) {
            if (!primed) {
                primed = true;
                chimeArmed = chargePercent < CHIME_REARM_PERCENT;
                wasHalted = halted;
                return NONE;
            }
            int owed = NONE;
            if (chargePercent < CHIME_REARM_PERCENT) {
                chimeArmed = true;
            } else if (chimeArmed && charging && chargePercent >= CHIME_AT_PERCENT) {
                chimeArmed = false;
                owed |= CHIME;
            }
            if (halted && !wasHalted && gameTime - lastThunk >= THUNK_GAP_TICKS) {
                lastThunk = gameTime;
                owed |= THUNK;
            }
            wasHalted = halted;
            return owed;
        }
    }
}
