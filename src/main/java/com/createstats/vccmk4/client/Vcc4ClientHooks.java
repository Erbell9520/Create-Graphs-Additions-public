package com.createstats.vccmk4.client;

import com.createstats.vccmk4.network.AerialViewPacket;
import com.createstats.vccmk4.network.Vcc4HelmPacket;
import com.createstats.vccmk4.network.Vcc4SnapshotPacket;
import net.minecraft.client.Minecraft;

public final class Vcc4ClientHooks {
    private Vcc4ClientHooks() {
    }

    public static void snapshot(Vcc4SnapshotPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof Vcc4Screen open && open.pos().equals(packet.pos())) {
            open.accept(packet.snapshot());
            return;
        }
        if (packet.open()) mc.setScreen(new Vcc4Screen(packet.pos(), packet.snapshot()));
    }

    public static void helm(Vcc4HelmPacket packet) {
        Vcc4ClientInput.setHelm(packet.pos(), packet.helm(), packet.watch());
    }

    public static void aerialView(AerialViewPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AerialScreen open && open.pos().equals(packet.pos())) {
            open.accept(packet.view());
            return;
        }
        if (packet.open()) mc.setScreen(new AerialScreen(packet.pos(), packet.view()));
    }
}
