package com.createstats.holomk3.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HoloModelCache {
    private static final float BOOT_SECONDS = 2.6f;

    private static final float IDLE_SECONDS = 12.0f;

    private static final Map<BlockPos, State> STATE = new HashMap<>();
    private static int evictCounter;

    private HoloModelCache() {
    }

    public static final class State {
        public float boot;

        public float pulse;
        private boolean wasActive;
        private float lastSeen;
        private float lastClock;
    }

    public static State advance(BlockPos pos, boolean active, float rawPulse, float seconds) {
        final State s = STATE.computeIfAbsent(pos.immutable(), p -> new State());

        final float dt = s.lastClock <= 0.0f ? 0.0f : Math.max(0.0f, Math.min(0.25f, seconds - s.lastClock));
        s.lastClock = seconds;
        s.lastSeen = seconds;
        if (active && !s.wasActive) {
            s.boot = 0.0f;
        }
        s.wasActive = active;
        if (!active) {
            s.boot = 0.0f;
        } else if (s.boot < 1.0f) {
            s.boot = Math.min(1.0f, s.boot + dt / BOOT_SECONDS);
        }

        s.pulse += (Math.max(0.0f, Math.min(1.0f, rawPulse)) - s.pulse) * Math.min(1.0f, dt * 6.0f);
        return s;
    }

    public static void evictIdle(float seconds) {
        if (++evictCounter < 600) {
            return;
        }
        evictCounter = 0;
        final Iterator<Map.Entry<BlockPos, State>> it = STATE.entrySet().iterator();
        while (it.hasNext()) {
            if (seconds - it.next().getValue().lastSeen > IDLE_SECONDS) {
                it.remove();
            }
        }
    }

    public static void forget(BlockPos pos) {
        STATE.remove(pos);
    }
}
