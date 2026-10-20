package com.createstats.seismic.client;

import com.createstats.CreateStats;
import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class SeismicShake {
    static final double RANGE = com.createstats.blockentity.SeismicScannerBE.FELT_RADIUS;

    static final double PEAK_DEG = 1.8;

    static final double DECAY = 2.6;

    static final double LENGTH = 12.0;

    private static double strength;
    private static long started = Long.MIN_VALUE;

    private SeismicShake() {
    }

    public static void blow(SeismicScannerBE be, float blow) {
        final BlockPos pos = be.getBlockPos();
        final Vec3 plate = SableBridge.worldPointOf(be, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        if (plate != null) {
            blow(plate, blow);
        }
    }

    public static void blow(BlockPos pos, float blow) {
        blow(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), blow);
    }

    public static void blow(Vec3 plate, float blow) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        final double d = Math.sqrt(mc.player.distanceToSqr(plate));
        if (d >= RANGE) {
            return;
        }
        final double s = Math.max(0.0, blow) * (1.0 - d / RANGE) * mc.options.screenEffectScale().get();
        if (s <= 0.0) {
            return;
        }
        strength = s;
        started = mc.level.getGameTime();
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (started == Long.MIN_VALUE) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            started = Long.MIN_VALUE;
            return;
        }
        final double t = mc.level.getGameTime() - started + event.getPartialTick();
        if (t < 0.0 || t > LENGTH) {
            if (t > LENGTH) {
                started = Long.MIN_VALUE;
            }
            return;
        }
        final double amp = strength * PEAK_DEG * Math.exp(-t / DECAY);
        event.setPitch((float) (event.getPitch() + amp * Math.sin(t * 2.9)));
        event.setRoll((float) (event.getRoll() + amp * 0.55 * Math.sin(t * 3.7 + 1.1)));
        event.setYaw((float) (event.getYaw() + amp * 0.25 * Math.sin(t * 2.3 + 2.0)));
    }
}
