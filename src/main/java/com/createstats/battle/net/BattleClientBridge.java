package com.createstats.battle.net;

import com.createstats.CreateStats;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;

public final class BattleClientBridge {
    private static final String HOOKS = "com.createstats.battle.client.BattleClientHooks";

    private BattleClientBridge() {
    }

    public static void shape(BattleShapePacket packet) {
        invoke("shape", BattleShapePacket.class, packet);
    }

    public static void detail(BattleDetailPacket packet) {
        invoke("detail", BattleDetailPacket.class, packet);
    }

    public static void watch(BattleWatchPacket packet) {
        invoke("watch", BattleWatchPacket.class, packet);
    }

    private static void invoke(String name, Class<?> type, Object arg) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try {
            Method m = Class.forName(HOOKS).getMethod(name, type);
            m.invoke(null, arg);
        } catch (ReflectiveOperationException e) {
            CreateStats.LOGGER.error("[Battle] client hook {} failed", name, e);
        }
    }
}
