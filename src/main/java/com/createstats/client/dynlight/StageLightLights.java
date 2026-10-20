package com.createstats.client.dynlight;

import com.createstats.blockentity.StageLightBE;
import com.createstats.dynlight.BeamLight;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

final class StageLightLights {
    static final double MAX_POOL_RADIUS = 16.0;

    private static final double LENS_OFFSET = 0.6;

    private static final Set<StageLightBE> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());

    private StageLightLights() {
    }

    static void loaded(BlockEntity be) {
        if (be instanceof StageLightBE light) {
            ACTIVE.add(light);
        }
    }

    static void unloaded(BlockEntity be) {
        if (be instanceof StageLightBE light) {
            ACTIVE.remove(light);
        }
    }

    static void collect(ClientLevel level, LightSink sink) {
        final Iterator<StageLightBE> it = ACTIVE.iterator();
        while (it.hasNext()) {
            final StageLightBE be = it.next();
            // ship blocks share the level's plot space, so this check is safe on ships. sable tax #5

            if (be.isRemoved() || (be.getLevel() instanceof ClientLevel other && other != level)) {
                it.remove();
                continue;
            }
            if (!be.isLit()) {
                continue;
            }
            final float effect = StageLightBE.effectBrightnessFactor(be.getEffect(), be.getEffectSpeed(),
                    level.getGameTime(), 0.0f);
            final int brightness = Math.round(be.getBrightness() * effect);
            if (brightness <= 0) {
                continue;
            }
            sink.add(beam(pose(be), brightness, be.getRange(), be.getPeakRange(), be.getMaxRange(), be.getFocus(),
                    be.getLightSpread()));
        }
    }

    static BeamLight beam(double[] pose, int brightness, int range, int peakRange, int maxRange, int focus,
                          int lightSpread) {
        return BeamLight.from(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5])
                .level(brightness)
                .reach(range)
                .fullTo(peakRange)
                .fadeTo(maxRange, StageLightBE.MIN_BRIGHTNESS)
                .pool(1.0, focus / 26.0, Math.min(lightSpread, MAX_POOL_RADIUS))
                .build();
    }

    private static double[] pose(StageLightBE be) {
        final BlockPos pos = be.getBlockPos();
        final Vector3f dir = be.computeBeamDirection();
        final Object subLevel = SableBridge.subLevelAccessOf(be);
        if (subLevel != null) {
            final Vector3d head = SableBridge.localToWorld(subLevel,
                    new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (head != null && Double.isFinite(head.x) && Double.isFinite(head.y) && Double.isFinite(head.z)) {
                final Vector3d aim = new Vector3d(dir.x, dir.y, dir.z);
                final Quaterniond bodyToWorld = SableBridge.bodyOrientation(subLevel);
                if (bodyToWorld != null) {
                    bodyToWorld.transform(aim);
                }
                if (aim.lengthSquared() > 1.0e-6) {
                    aim.normalize();
                }
                return new double[] {head.x + aim.x * LENS_OFFSET, head.y + aim.y * LENS_OFFSET,
                        head.z + aim.z * LENS_OFFSET, aim.x, aim.y, aim.z};
            }
        }
        return new double[] {pos.getX() + 0.5 + dir.x * LENS_OFFSET, pos.getY() + 0.5 + dir.y * LENS_OFFSET,
                pos.getZ() + 0.5 + dir.z * LENS_OFFSET, dir.x, dir.y, dir.z};
    }
}
