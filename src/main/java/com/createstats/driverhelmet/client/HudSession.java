package com.createstats.driverhelmet.client;

import com.createstats.CreateStats;
import com.createstats.driverhelmet.DriverHelmetItem;
import com.createstats.driverhelmet.DriverHelmets;
import com.createstats.driverhelmet.HelmetGlowPacket;
import com.createstats.driverhelmet.HelmetVisor;
import com.createstats.driverhelmet.HudBoot;
import com.createstats.driverhelmet.HudTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class HudSession {
    private static final int GLOW_EVERY = 4;
    private static final int GLOW_KEEPALIVE = 20;

    private static double wornSince = Double.NaN;
    private static long wearings;
    private static boolean welcomeArmed;
    private static long welcomeBoot = Long.MIN_VALUE;

    private static double helmSince = Double.NaN;
    private static long helms;

    private static final long HELM_KEYS = Long.MIN_VALUE / 2;

    private static int glowClock;
    private static int glowSilence;
    private static int lastThrottle = Integer.MIN_VALUE;
    private static int lastState = -1;

    private HudSession() {
    }

    record Boot(double seconds, boolean welcome) {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final ItemStack helmet = mc.player == null ? ItemStack.EMPTY : DriverHelmets.worn(mc.player);
        if (helmet.isEmpty()) {
            wornSince = Double.NaN;
            welcomeArmed = false;
            lastThrottle = Integer.MIN_VALUE;
            return;
        }
        if (Double.isNaN(wornSince)) {
            wornSince = HelmetView.now();
            wearings++;
            welcomeArmed = true;
        }
        report(helmet);
    }

    public static void helmTaken() {
        helmSince = HelmetView.now();
        helms++;
    }

    static Boot boot(HelmetVisor visor) {
        final double now = HelmetView.now();
        final double stroke = HelmetView.firstSeen(false, visor.strokeTick());
        final long key;
        final double seconds;
        switch (HudBoot.anchor(stroke, wornSince, helmSince)) {
            case STROKE -> {
                key = visor.strokeTick();
                seconds = (now - stroke) / 20.0;
            }
            case WORN -> {
                key = -wearings;
                seconds = (now - wornSince) / 20.0 + HudBoot.START;
            }
            case HELM -> {
                key = HELM_KEYS + helms;
                seconds = (now - helmSince) / 20.0;
            }
            default -> {
                return new Boot(Double.MAX_VALUE, false);
            }
        }
        if (welcomeArmed) {
            welcomeArmed = false;
            welcomeBoot = key;
        }
        return new Boot(seconds, key == welcomeBoot);
    }

    private static void report(ItemStack helmet) {
        final HelmetVisor visor = DriverHelmetItem.visor(helmet);
        if (visor.open() || !HudTheme.of(visor.theme()).glows()) {
            lastThrottle = Integer.MIN_VALUE;
            return;
        }
        if (++glowClock % GLOW_EVERY != 0) {
            return;
        }
        final int[] reading = DriverHelmetHud.glowReading(visor);
        glowSilence += GLOW_EVERY;
        if (reading[0] == lastThrottle && reading[1] == lastState && glowSilence < GLOW_KEEPALIVE) {
            return;
        }
        lastThrottle = reading[0];
        lastState = reading[1];
        glowSilence = 0;
        PacketDistributor.sendToServer(new HelmetGlowPacket(reading[0], reading[1]));
    }
}
