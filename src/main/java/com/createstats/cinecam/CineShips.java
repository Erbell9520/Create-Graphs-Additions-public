package com.createstats.cinecam;

import com.createstats.flight.bridge.SableBridge;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

public final class CineShips {
    private static final int RETRY_TICKS = 20;

    private static volatile Method isRemoved;
    private static volatile boolean isRemovedMissing;

    @Nullable private Object ship;
    @Nullable private UUID id;
    @Nullable private Level level;
    private long retryAt = Long.MIN_VALUE;

    @Nullable
    public Object find(Level level, UUID id, long now) {
        if (level != this.level || !id.equals(this.id)) {
            this.level = level;
            this.id = id;
            ship = null;
            retryAt = Long.MIN_VALUE;
        }
        if (ship != null && !removed(ship)) return ship;
        ship = null;
        if (retryAt != Long.MIN_VALUE && now < retryAt) return null;
        ship = SableBridge.findSubLevelById(level, id);
        retryAt = ship == null ? now + RETRY_TICKS : Long.MIN_VALUE;
        return ship;
    }

    public void forget() {
        ship = null;
        id = null;
        level = null;
        retryAt = Long.MIN_VALUE;
    }

    public static double ownRadius(@Nullable Object subLevel) {
        if (subLevel == null) return 0.0;
        try {
            Object plot = subLevel.getClass().getMethod("getPlot").invoke(subLevel);
            if (plot == null) return 0.0;
            Object box = plot.getClass().getMethod("getBoundingBox").invoke(plot);
            if (box == null) return 0.0;
            double[] v = new double[6];
            String[] names = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
            for (int i = 0; i < 6; i++) {
                Method m = box.getClass().getMethod(names[i]);
                m.setAccessible(true);
                if (!(m.invoke(box) instanceof Number n)) return 0.0;
                v[i] = n.doubleValue();
            }

            double dx = v[3] - v[0] + 1.0;
            double dy = v[4] - v[1] + 1.0;
            double dz = v[5] - v[2] + 1.0;
            return dx > 0 && dy > 0 && dz > 0 ? 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz) : 0.0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0.0;
        }
    }

    private static boolean removed(Object subLevel) {
        Method m = isRemoved;
        if (m == null) {
            if (isRemovedMissing) return false;
            try {
                m = subLevel.getClass().getMethod("isRemoved");
                isRemoved = m;
            } catch (NoSuchMethodException | RuntimeException e) {
                isRemovedMissing = true;
                return false;
            }
        }
        try {
            return Boolean.TRUE.equals(m.invoke(subLevel));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }
}
