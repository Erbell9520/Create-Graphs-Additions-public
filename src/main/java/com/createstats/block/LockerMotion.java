package com.createstats.block;

import net.minecraft.util.Mth;

public final class LockerMotion {
    private LockerMotion() {
    }

    public static final int DIAL_TICKS = 11;

    public static final int BOLT_TICKS = 7;

    public static final int BOLT_STAGGER = 2;

    public static final int DOOR_TICKS = 9;

    public static final int BOLTS = 3;

    public static final int UNLOCK_TICKS =
            DIAL_TICKS + BOLT_STAGGER * (BOLTS - 1) + BOLT_TICKS + DOOR_TICKS;

    public static final float DOOR_OPEN_DEG = 100.0f;

    public static final float DIAL_TURN_DEG = 315.0f;

    public static final float BOLT_THROW = 1.5f;

    public static final float LEAF_LEAD = 1.8f;

    public static final float PLUG_PHASE = 0.30f;

    private static final int[] ORDER = {2, 1, 0};

    private static float ease(float t) {
        final float c = Mth.clamp(t, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }

    private static float seat(float t) {
        final float c = Mth.clamp(t, 0.0f, 1.0f);
        final float s = ease(c);
        if (c < 0.78f) {
            return s;
        }
        final float u = (c - 0.78f) / 0.22f;
        final float kick = 0.055f * Mth.sin(u * Mth.PI) * (1.0f - u);
        return Mth.clamp(s - kick, 0.0f, 1.0f);
    }

    private static float mirror(float tick, boolean locking) {
        return locking ? UNLOCK_TICKS - tick : tick;
    }

    public static float dial(float tick, boolean locking) {
        return DIAL_TURN_DEG * ease(mirror(tick, locking) / DIAL_TICKS);
    }

    public static float bolt(int index, float tick, boolean locking) {
        final int slot = slotOf(index);
        if (locking) {
            final float start = DOOR_TICKS + BOLT_STAGGER * (BOLTS - 1 - slot);
            return BOLT_THROW * seat((tick - start) / BOLT_TICKS);
        }
        final float start = DIAL_TICKS + BOLT_STAGGER * slot;
        return BOLT_THROW * (1.0f - ease((tick - start) / BOLT_TICKS));
    }

    private static int slotOf(int boltIndex) {
        for (int slot = 0; slot < ORDER.length; slot++) {
            if (ORDER[slot] == boltIndex) {
                return slot;
            }
        }
        return 0;
    }

    private static float swing(float tick, boolean locking) {
        final float t = mirror(tick, locking);
        final float from = DIAL_TICKS + BOLT_STAGGER * (BOLTS - 1) + BOLT_TICKS;
        return ease((t - from) / DOOR_TICKS);
    }

    public static float lead(float tick, boolean locking) {
        return LEAF_LEAD * ease(swing(tick, locking) / PLUG_PHASE);
    }

    public static float door(float tick, boolean locking) {
        return DOOR_OPEN_DEG * ease((swing(tick, locking) - PLUG_PHASE) / (1.0f - PLUG_PHASE));
    }
}
