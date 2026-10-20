package com.createstats.vccmk4.client;

import com.createstats.CreateStats;
import com.createstats.driverhelmet.client.HudSession;
import com.createstats.vccmk4.network.Vcc4InputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Arrays;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class Vcc4ClientInput {
    private static final int[] NONE = new int[0];

    @Nullable private static BlockPos helm;
    private static int[] watch = NONE;
    @Nullable private static int[] lastHeld;
    private static long lastSent;

    private Vcc4ClientInput() {
    }

    public static void setHelm(BlockPos pos, boolean has, int[] keys) {
        if (has) {
            if (helm == null || !helm.equals(pos)) HudSession.helmTaken();
            helm = pos.immutable();
            watch = keys == null ? NONE : keys.clone();
        } else if (helm != null && helm.equals(pos)) {
            helm = null;
            watch = NONE;
        }
        lastHeld = null;
    }

    @Nullable
    public static BlockPos helm() {
        return helm;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            helm = null;
            watch = NONE;
            lastHeld = null;
            return;
        }
        if (helm == null) return;

        int[] held = NONE;
        if (reading(mc, player)) {
            int[] buf = new int[watch.length];
            int n = 0;
            for (int id : watch) {
                if (Vcc4Keys.isHeld(id)) buf[n++] = id;
            }
            held = Arrays.copyOf(buf, n);
        }
        long now = mc.level.getGameTime();
        if (lastHeld == null || !Arrays.equals(held, lastHeld) || now - lastSent >= 10) {
            PacketDistributor.sendToServer(new Vcc4InputPacket(helm, held));
            lastHeld = held;
            lastSent = now;
        }
    }

    private static boolean reading(Minecraft mc, LocalPlayer player) {
        if (!mc.isWindowActive() || !player.isPassenger()) return false;
        if (mc.screen == null) return true;
        return mc.screen instanceof HelmScreen console && console.pos().equals(helm) && console.passesKeys();
    }
}
