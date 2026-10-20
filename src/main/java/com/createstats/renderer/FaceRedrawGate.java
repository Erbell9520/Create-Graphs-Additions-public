package com.createstats.renderer;

// now - Long.MIN_VALUE overflows. the gate was false forever, the face never drew once
final class FaceRedrawGate {
    static final long NEVER = Long.MIN_VALUE;

    private FaceRedrawGate() {
    }

    static boolean due(long now, long lastDrawTick, int minTicks) {
        if (lastDrawTick == NEVER) {
            return true;
        }
        if (now < lastDrawTick) {
            return true;
        }
        return now - lastDrawTick >= minTicks;
    }
}
