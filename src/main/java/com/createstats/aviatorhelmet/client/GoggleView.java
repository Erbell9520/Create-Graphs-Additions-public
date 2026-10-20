package com.createstats.aviatorhelmet.client;

import com.createstats.aviatorhelmet.AviatorGoggles;
import com.createstats.aviatorhelmet.GoggleMotion;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;

// time strokes from when this client saw it. integrated server lag puts the client up to 1s ahead, a 0.42s stroke would be done before the first frame
public final class GoggleView {
    static final double FRESH_TICKS = 60.0;

    static final double LONG_AGO = 60.0;
    private static final double HISTORY = Double.NEGATIVE_INFINITY;
    private static final int REMEMBERED = 128;

    private static final Map<Long, Double> FIRST_SEEN = new LinkedHashMap<>(REMEMBERED, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Double> eldest) {
            return size() > REMEMBERED;
        }
    };

    private GoggleView() {
    }

    static double now() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0 : mc.level.getGameTime() + AnimationTickHolder.getPartialTicks();
    }

    static double secondsInto(boolean down, long strokeTick) {
        final double now = now();
        final long key = strokeTick * 2 + (down ? 1 : 0);
        Double seen = FIRST_SEEN.get(key);
        if (seen == null) {
            seen = Math.abs(now - strokeTick) < FRESH_TICKS ? now : HISTORY;
            FIRST_SEEN.put(key, seen);
        }
        return seen == HISTORY ? LONG_AGO : Math.max(0.0, (now - seen) / 20.0);
    }

    static double secondsInto(AviatorGoggles goggles) {
        return secondsInto(goggles.down(), goggles.strokeTick());
    }

    static double[] pose(AviatorGoggles goggles) {
        return GoggleMotion.pose(goggles.down(), secondsInto(goggles));
    }
}
