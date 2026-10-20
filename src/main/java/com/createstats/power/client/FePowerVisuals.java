package com.createstats.power.client;

import com.createstats.power.FeConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class FePowerVisuals {
    private static final long BOOT_MS = 900L;

    private static final float DEAD_BRIGHTNESS = 0.06f;
    private static final int MAX_TRACKED = 512;

    private static final Map<Long, State> STATES = new HashMap<>();

    private static final class State {
        boolean powered = true;
        long changedAt;
        long lastSeen;
    }

    private FePowerVisuals() {
    }

    public static float screenBrightness(BlockPos pos, @Nullable FeConsumer fe) {
        if (fe == null || !fe.requiresPower()) {
            return 1.0f;
        }
        final long now = System.currentTimeMillis();
        final State state = trackState(pos, fe.isPowered(), now);
        if (!fe.isPowered()) {
            return DEAD_BRIGHTNESS;
        }
        final long since = now - state.changedAt;
        if (since < BOOT_MS) {
            return bootCurve(since / (float) BOOT_MS);
        }
        if (fe.isBrownout()) {
            return brownoutFlicker(now, fe.fillPct());
        }
        return 1.0f;
    }

    public static boolean isBooting(BlockPos pos, @Nullable FeConsumer fe) {
        if (fe == null || !fe.requiresPower() || !fe.isPowered()) {
            return false;
        }
        final State state = STATES.get(pos.asLong());
        return state != null && System.currentTimeMillis() - state.changedAt < BOOT_MS;
    }

    public static boolean isDead(@Nullable FeConsumer fe) {
        return fe != null && fe.requiresPower() && !fe.isPowered();
    }

    private static float bootCurve(float t) {
        if (t < 0.10f) {
            return 0.0f;
        }
        if (t < 0.18f) {
            return 1.0f;
        }
        if (t < 0.28f) {
            return 0.10f;
        }
        if (t < 0.40f) {
            return 0.68f;
        }
        if (t < 0.48f) {
            return 0.28f;
        }
        return Mth.clamp(0.28f + (t - 0.48f) / 0.52f * 0.72f, 0.0f, 1.0f);
    }

    private static float brownoutFlicker(long now, float fillPct) {
        final float depth = Mth.clamp(fillPct / FeConsumer.LOW_POWER_PCT, 0.0f, 1.0f);
        final float base = 0.45f + 0.50f * depth;
        final float noise = hash01(now / 80L);
        final float dipChance = 0.30f - 0.18f * depth;
        if (noise < dipChance) {
            return base * 0.30f;
        }
        if (noise < dipChance + 0.12f) {
            return base * 0.70f;
        }
        return base;
    }

    private static float hash01(long value) {
        long x = value * 0x9E3779B97F4A7C15L;
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        return ((x >>> 40) & 0xFFFF) / 65535.0f;
    }

    private static State trackState(BlockPos pos, boolean powered, long now) {
        final long key = pos.asLong();
        State state = STATES.get(key);
        if (state == null) {
            state = new State();
            state.powered = powered;
            state.changedAt = now;
            if (STATES.size() >= MAX_TRACKED) {
                prune(now);
            }
            STATES.put(key, state);
        }
        if (state.powered != powered) {
            state.powered = powered;
            state.changedAt = now;
        }
        state.lastSeen = now;
        return state;
    }

    private static void prune(long now) {
        STATES.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 30_000L);
        if (STATES.size() >= MAX_TRACKED) {
            STATES.clear();
        }
    }
}
