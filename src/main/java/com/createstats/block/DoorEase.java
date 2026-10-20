package com.createstats.block;

import net.minecraft.util.Mth;

public enum DoorEase {
    SERVO {
        @Override
        public float curve(float open, boolean opening) {
            return smoothstep(open);
        }
    },

    SWING {
        @Override
        public float curve(float open, boolean opening) {
            final float s = smoothstep(open);
            if (opening) {
                return s;
            }

            final float travelled = 1.0f - open;
            if (travelled < REBOUND_FROM) {
                return s;
            }

            final float u = (travelled - REBOUND_FROM) / (1.0f - REBOUND_FROM);
            final float kick = REBOUND * Mth.sin(u * Mth.PI) * (1.0f - u);
            return Mth.clamp(s + kick, 0.0f, 1.0f);
        }
    },

    DRUM {
        @Override
        public float curve(float open, boolean opening) {
            if (opening) {
                return Mth.lerp(WIND_SMOOTHING, open, smoothstep(open));
            }
            final float fallen = 1.0f - open;
            return Mth.clamp(1.0f - fallen * fallen, 0.0f, 1.0f);
        }
    };

    private static final float REBOUND_FROM = 0.80f;

    private static final float REBOUND = 0.06f;

    private static final float WIND_SMOOTHING = 0.35f;

    public abstract float curve(float open, boolean opening);

    public static float smoothstep(float t) {
        final float c = Mth.clamp(t, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }
}
