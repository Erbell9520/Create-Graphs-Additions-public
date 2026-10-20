package com.createstats.support;

import java.lang.reflect.Method;
import java.util.List;

import com.createstats.CreateStats;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class SupportClientAccess {
    private static final String CLIENT_HOOKS = "com.createstats.support.SupportClientHooks";

    private SupportClientAccess() {
    }

    public static void deliverReport(String title, List<String> lines) {
        invoke("deliverReport", new Class<?>[]{String.class, List.class}, title, lines);
    }

    public static void refreshTerminal() {
        invoke("refreshTerminal", new Class<?>[0]);
    }

    public static void saveExport(List<String> lines) {
        invoke("saveExport", new Class<?>[]{List.class}, lines);
    }

    private static void invoke(String name, Class<?>[] types, Object... args) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Method method = Class.forName(CLIENT_HOOKS).getMethod(name, types);
            method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            CreateStats.LOGGER.warn("[SUPPORT] client hook {} failed", name, e);
        }
    }
}
