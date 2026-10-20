package com.createstats.client.hud;

import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class HudMicro {
    private HudMicro() {}

    public static final long HOVER_RAISE_MS = 120L;
    public static final long PRESS_DOWN_MS = 70L;
    public static final long ADD_TRACE_MS = 220L;
    public static final long REMOVE_FADE_MS = 200L;
    public static final long DRAG_LIFT_MS = 110L;
    public static final long SNAP_POP_MS = 140L;
    public static final long ERROR_SHAKE_MS = 80L;

    private static final Map<String, Long> HOVER_START = new HashMap<>();
    private static final Map<String, Long> PRESS_START = new HashMap<>();
    private static final Map<String, Long> ADD_START = new HashMap<>();
    private static final Map<String, Long> REMOVE_START = new HashMap<>();
    private static final Map<String, Long> SNAP_START = new HashMap<>();
    private static final Map<String, Long> ERROR_START = new HashMap<>();

    private static long lastSweep = 0L;

    public static void hover(String id, boolean hovering) {
        sweepIfStale();
        if (hovering) {
            HOVER_START.putIfAbsent(id, System.currentTimeMillis());
        } else {
            HOVER_START.remove(id);
        }
    }

    public static void press(String id) {
        sweepIfStale();
        PRESS_START.put(id, System.currentTimeMillis());
    }

    public static void onAdded(String id) { sweepIfStale(); ADD_START.put(id, System.currentTimeMillis()); }
    public static void onRemoved(String id) { sweepIfStale(); REMOVE_START.put(id, System.currentTimeMillis()); }
    public static void onSnap(String id) { sweepIfStale(); SNAP_START.put(id, System.currentTimeMillis()); }
    public static void onError(String id) { sweepIfStale(); ERROR_START.put(id, System.currentTimeMillis()); }

    public static float hoverRaise01(String id) {
        final Long start = HOVER_START.get(id);
        if (start == null) return 0f;
        final long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= HOVER_RAISE_MS) return 1f;
        return HudAnim.easeOutCubic(elapsed / (float) HOVER_RAISE_MS);
    }

    public static float pressScale01(String id) {
        final Long start = PRESS_START.get(id);
        if (start == null) return 1f;
        final long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= PRESS_DOWN_MS) {
            PRESS_START.remove(id);
            return 1f;
        }
        final float t = elapsed / (float) PRESS_DOWN_MS;

        final float depth = 0.08f;
        return 1f - depth * (t < 0.5f ? (t * 2f) : (2f - t * 2f));
    }

    public static float addTrace01(String id) {
        return progress01(ADD_START, id, ADD_TRACE_MS);
    }

    public static float removeFade01(String id) {
        return progress01(REMOVE_START, id, REMOVE_FADE_MS);
    }

    public static float snapPop01(String id) {
        return progress01(SNAP_START, id, SNAP_POP_MS);
    }

    public static int errorShakePx(String id) {
        final Long start = ERROR_START.get(id);
        if (start == null) return 0;
        final long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= ERROR_SHAKE_MS) {
            ERROR_START.remove(id);
            return 0;
        }
        final float decay = 1f - (elapsed / (float) ERROR_SHAKE_MS);
        return (int) ((((elapsed / 18) & 1) == 0 ? 1 : -1) * 2 * decay);
    }

    private static float progress01(Map<String, Long> map, String id, long durationMs) {
        final Long start = map.get(id);
        if (start == null) return 0f;
        final long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= durationMs) {
            map.remove(id);
            return 0f;
        }
        return HudAnim.easeOutCubic(elapsed / (float) durationMs);
    }

    private static void sweepIfStale() {
        final long now = System.currentTimeMillis();
        if (now - lastSweep < 3000L) return;
        lastSweep = now;
        sweep(PRESS_START, now, 1000L);
        sweep(ADD_START, now, 2000L);
        sweep(REMOVE_START, now, 2000L);
        sweep(SNAP_START, now, 2000L);
        sweep(ERROR_START, now, 2000L);
    }

    private static void sweep(Map<String, Long> map, long now, long maxAgeMs) {
        final Iterator<Map.Entry<String, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > maxAgeMs) it.remove();
        }
    }

    public static void drawHoverGlow(GuiGraphics g, String id, int x, int y, int w, int h, int accentArgb) {
        final float r = hoverRaise01(id);
        if (r <= 0.01f) return;
        final int alpha = (int) (40 + 100 * r);
        final int color = HudAnim.withAlpha(accentArgb, alpha);
        g.fill(x - 1, y - 1, x + w + 1, y, color);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, color);
        g.fill(x - 1, y, x, y + h, color);
        g.fill(x + w, y, x + w + 1, y + h, color);
    }

    public static void drawSnapPop(GuiGraphics g, String id, int x, int y, int w, int h, int accentArgb) {
        final float p = snapPop01(id);
        if (p <= 0.01f) return;
        final int outset = 1 + Math.round(3f * p);
        final int alpha = (int) (200 * (1f - p));
        final int color = HudAnim.withAlpha(accentArgb, alpha);
        g.fill(x - outset, y - outset, x + w + outset, y - outset + 1, color);
        g.fill(x - outset, y + h + outset - 1, x + w + outset, y + h + outset, color);
        g.fill(x - outset, y - outset, x - outset + 1, y + h + outset, color);
        g.fill(x + w + outset - 1, y - outset, x + w + outset, y + h + outset, color);
    }
}
