package com.createstats.client.wire;

import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.power.WireTension;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class WireTensionView {
    private static final double EASE_MS = 70.0;

    private static final long FORGET_MS = 5_000L;

    private static final Map<Long, double[]> SHOWN = new HashMap<>();
    private static long lastPurge;

    @Nullable
    private static BlockPos previewConnector;
    private static Map<Long, WireTension> previewTension = Map.of();
    private static Set<Long> previewHighlight = Set.of();

    private WireTensionView() {
    }

    public static double slack(@Nullable BlockGetter level, BlockPos a, BlockPos b) {
        final double target = target(level, a, b).slack();
        final long now = Util.getMillis();
        purge(now);
        final long key = spanKey(a, b);
        final double[] shown = SHOWN.get(key);
        if (shown == null) {
            SHOWN.put(key, new double[] {target, now});
            return target;
        }
        final double dt = Math.max(0.0, now - shown[1]);
        shown[1] = now;
        final double k = 1.0 - Math.exp(-dt / EASE_MS);
        shown[0] += (target - shown[0]) * k;
        if (Math.abs(target - shown[0]) < 1.0e-6) {
            shown[0] = target;
        }
        return shown[0];
    }

    public static int light(BlockPos a, BlockPos b, int packedLight) {
        if (previewConnector == null) {
            return packedLight;
        }
        if (a.equals(previewConnector) && previewHighlight.contains(b.asLong())
                || b.equals(previewConnector) && previewHighlight.contains(a.asLong())) {
            return LightTexture.FULL_BRIGHT;
        }
        return packedLight;
    }

    public static void preview(BlockPos connector, Map<Long, WireTension> tension, Set<Long> highlight) {
        previewConnector = connector.immutable();
        previewTension = tension;
        previewHighlight = highlight;
    }

    public static void clearPreview() {
        previewConnector = null;
        previewTension = Map.of();
        previewHighlight = Set.of();
    }

    private static WireTension target(@Nullable BlockGetter level, BlockPos a, BlockPos b) {
        if (previewConnector != null) {
            WireTension shown = null;
            if (a.equals(previewConnector)) {
                shown = previewTension.get(b.asLong());
            } else if (b.equals(previewConnector)) {
                shown = previewTension.get(a.asLong());
            }
            if (shown != null) {
                return shown;
            }
        }
        return PowerConnectorBE.tensionBetween(level, a, b);
    }

    private static long spanKey(BlockPos a, BlockPos b) {
        final long x = a.asLong();
        final long y = b.asLong();
        final long lo = Math.min(x, y);
        final long hi = Math.max(x, y);

        return lo * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(hi, 31);
    }

    private static void purge(long now) {
        if (now - lastPurge < FORGET_MS) {
            return;
        }
        lastPurge = now;
        final Iterator<double[]> it = SHOWN.values().iterator();
        while (it.hasNext()) {
            if (now - (long) it.next()[1] > FORGET_MS) {
                it.remove();
            }
        }
    }
}
