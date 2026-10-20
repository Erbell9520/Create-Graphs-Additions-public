package com.createstats.vccmk4;

import com.createstats.CreateStats;
import com.createstats.vccmk4.network.AerialViewPacket;
import com.createstats.vccmk4.network.Vcc4HelmPacket;
import com.createstats.vccmk4.network.Vcc4SnapshotPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;

// reflective, Screen import = dedicated server crash
public final class Vcc4ClientBridge {
    private static final String HOOKS = "com.createstats.vccmk4.client.Vcc4ClientHooks";

    private Vcc4ClientBridge() {
    }

    public static void snapshot(Vcc4SnapshotPacket packet) {
        invoke("snapshot", new Class<?>[]{Vcc4SnapshotPacket.class}, packet);
    }

    public static void helm(Vcc4HelmPacket packet) {
        invoke("helm", new Class<?>[]{Vcc4HelmPacket.class}, packet);
    }

    public static void aerialView(AerialViewPacket packet) {
        invoke("aerialView", new Class<?>[]{AerialViewPacket.class}, packet);
    }

    private static void invoke(String name, Class<?>[] types, Object... args) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try {
            Method m = Class.forName(HOOKS).getMethod(name, types);
            m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            CreateStats.LOGGER.error("[VCC Mk IV] client hook {} failed", name, e);
        }
    }
}
