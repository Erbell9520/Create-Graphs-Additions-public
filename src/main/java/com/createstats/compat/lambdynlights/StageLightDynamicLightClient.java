package com.createstats.compat.lambdynlights;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.createstats.CreateStats;
import com.createstats.blockentity.StageLightBE;
import com.createstats.flight.bridge.SableBridge;

import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** [1] */
public final class StageLightDynamicLightClient {

    /** Live Stage Light block entities on the client, keyed by position. */
    private static final Map<BlockPos, StageLightBE> ACTIVE = new HashMap<>();

    /** The behavior currently registered with LambDynamicLights for each lit Stage Light. */
    private static final Map<BlockPos, StageLightBeamBehavior> BEHAVIORS = new HashMap<>();

    private StageLightDynamicLightClient() {
    }

    /**
     * Register the client tick driver. Called only from the LambDynamicLights entrypoint, so it runs
     * exclusively when LambDynamicLights is installed and initialised (LDL is an optional dependency).
     */
    public static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(StageLightDynamicLightClient.class);
    }

    /** Called from a Stage Light block entity when it loads on the client. */
    public static void register(StageLightBE be) {
        ACTIVE.put(be.getBlockPos().immutable(), be);
    }

    /** Called from a Stage Light block entity when it is removed on the client. */
    public static void unregister(StageLightBE be) {
        final BlockPos key = be.getBlockPos();
        ACTIVE.remove(key);
        final StageLightBeamBehavior behavior = BEHAVIORS.remove(key);
        if (behavior != null) {
            behavior.markRemoved();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final DynamicLightBehaviorManager manager = StageLightDynamicLights.behaviorManager();
        if (manager == null) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            // World left: drop everything.
            if (!BEHAVIORS.isEmpty()) {
                for (StageLightBeamBehavior behavior : BEHAVIORS.values()) {
                    behavior.markRemoved();
                }
                BEHAVIORS.clear();
            }
            ACTIVE.clear();
            return;
        }

        final Iterator<Map.Entry<BlockPos, StageLightBE>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<BlockPos, StageLightBE> entry = it.next();
            final BlockPos pos = entry.getKey();
            final StageLightBE be = entry.getValue();

            if (be.isRemoved()) {
                it.remove();
                final StageLightBeamBehavior gone = BEHAVIORS.remove(pos);
                if (gone != null) {
                    gone.markRemoved();
                }
                continue;
            }

            if (!be.isLit()) {
                final StageLightBeamBehavior off = BEHAVIORS.remove(pos);
                if (off != null) {
                    off.markRemoved();
                }
                continue;
            }

            final BeamPose beam = computeBeamPoseWorld(be);
            final int range = be.getRange();
            // [2]
            final int reach = occludedReach(mc, be, beam, range);
            final boolean occluded = be.clientLightOccluded;
            final int peakRange = be.getPeakRange();
            final int maxRange = be.getMaxRange();
            // Effects modulate the real cone brightness deterministically from game time, so every
            // client agrees without sync. Strobe's off phase drops it to 0 (dark), pulse breathes it.
            final float fxFactor = StageLightBE.effectBrightnessFactor(
                    be.getEffect(), be.getEffectSpeed(), mc.level.getGameTime(), 0.0f);
            final int brightness = Math.round(be.getBrightness() * fxFactor);
            final int focus = be.getFocus();
            final int lightSpread = be.getLightSpread();
            StageLightBeamBehavior behavior = BEHAVIORS.get(pos);
            if (behavior == null) {
                behavior = new StageLightBeamBehavior(beam.sx(), beam.sy(), beam.sz(),
                        beam.dx(), beam.dy(), beam.dz(), reach, peakRange, maxRange, brightness, focus, lightSpread, occluded);
                manager.add(behavior);
                BEHAVIORS.put(pos.immutable(), behavior);
            } else {
                behavior.update(beam.sx(), beam.sy(), beam.sz(), beam.dx(), beam.dy(), beam.dz(),
                        reach, peakRange, maxRange, brightness, focus, lightSpread, occluded);
            }
        }
    }

    /** [3] */
    private static int occludedReach(Minecraft mc, StageLightBE be, BeamPose beam, int range) {
        if (mc.player == null || mc.level == null) {
            be.clientLightOccluded = false;
            return range;
        }
        final long now = System.nanoTime();
        if (now - be.clientLightClipNanos > 120_000_000L || be.clientLightReach <= 0.0f) {
            be.clientLightClipNanos = now;
            final Vec3 start = new Vec3(beam.sx(), beam.sy(), beam.sz());
            final Vec3 end = new Vec3(beam.sx() + beam.dx() * range,
                    beam.sy() + beam.dy() * range, beam.sz() + beam.dz() * range);
            final BlockHitResult hit = mc.level.clip(new ClipContext(start, end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                // [4]
                be.clientLightReach = (float) hit.getLocation().distanceTo(start)
                        + StageLightBeamBehavior.OCCLUDED_OVERSHOOT;
                be.clientLightOccluded = true;
            } else {
                be.clientLightReach = range;
                be.clientLightOccluded = false;
            }
        }
        return Math.max(1, Math.min(range, Math.round(be.clientLightReach)));
    }

    /** Beam source position and unit direction in world space. */
    private record BeamPose(double sx, double sy, double sz, double dx, double dy, double dz) {
    }

    /** [5] */
    private static BeamPose computeBeamPoseWorld(StageLightBE be) {
        final BlockPos pos = be.getBlockPos();
        final Vector3f dir = be.computeBeamDirection();
        final double sourceOffset = 0.6;

        final Object subLevel = SableBridge.subLevelAccessOf(be);
        if (subLevel != null) {
            final Vector3d headLocal = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            final Vector3d headWorld = SableBridge.localToWorld(subLevel, headLocal);
            if (headWorld != null && Double.isFinite(headWorld.x) && Double.isFinite(headWorld.y)
                    && Double.isFinite(headWorld.z)) {
                Vector3d worldDir = new Vector3d(dir.x, dir.y, dir.z);
                final Quaterniond bodyToWorld = SableBridge.bodyOrientation(subLevel);
                if (bodyToWorld != null) {
                    bodyToWorld.transform(worldDir);
                }
                if (worldDir.lengthSquared() > 1.0e-6) {
                    worldDir.normalize();
                }
                return new BeamPose(headWorld.x + worldDir.x * sourceOffset,
                        headWorld.y + worldDir.y * sourceOffset,
                        headWorld.z + worldDir.z * sourceOffset,
                        worldDir.x, worldDir.y, worldDir.z);
            }
        }

        return new BeamPose(pos.getX() + 0.5 + dir.x * sourceOffset,
                pos.getY() + 0.5 + dir.y * sourceOffset,
                pos.getZ() + 0.5 + dir.z * sourceOffset,
                dir.x, dir.y, dir.z);
    }
}
