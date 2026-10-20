package com.createstats.driverhelmet.client;

import com.createstats.driverhelmet.HelmetVisor;
import com.createstats.driverhelmet.VisorMotion;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;

public final class HelmetView {
    static final double FRESH_TICKS = 60.0;
    private static final double HISTORY = Double.NEGATIVE_INFINITY;
    private static final int REMEMBERED = 128;

    private static final Map<Long, Double> FIRST_SEEN = new LinkedHashMap<>(REMEMBERED, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Double> eldest) {
            return size() > REMEMBERED;
        }
    };

    private HelmetView() {
    }

    static double now() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0 : mc.level.getGameTime() + AnimationTickHolder.getPartialTicks();
    }

    static double secondsInto(boolean open, long strokeTick) {
        final double now = now();
        final long key = strokeTick * 2 + (open ? 1 : 0);
        Double seen = FIRST_SEEN.get(key);
        if (seen == null) {
            seen = Math.abs(now - strokeTick) < FRESH_TICKS ? now : HISTORY;
            FIRST_SEEN.put(key, seen);
        }
        return seen == HISTORY ? 60.0 : Math.max(0.0, (now - seen) / 20.0);
    }

    static double firstSeen(boolean open, long strokeTick) {
        secondsInto(open, strokeTick);
        final Double seen = FIRST_SEEN.get(strokeTick * 2 + (open ? 1 : 0));
        return seen == null || seen == HISTORY ? Double.NaN : seen;
    }

    static double angle(boolean open, long strokeTick) {
        return VisorMotion.angleAt(open, secondsInto(open, strokeTick));
    }

    static double angle(HelmetVisor visor) {
        return angle(visor.open(), visor.strokeTick());
    }
}
