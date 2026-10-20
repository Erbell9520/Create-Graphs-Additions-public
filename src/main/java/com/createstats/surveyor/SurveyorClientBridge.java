package com.createstats.surveyor;

import com.createstats.CreateStats;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// reflective, a direct client ref is a dedicated server crash at class load
public final class SurveyorClientBridge {
    private static final String HOOKS = "com.createstats.surveyor.client.SurveyorClientHooks";
    private static final Map<String, Method> CACHE = new ConcurrentHashMap<>();

    private SurveyorClientBridge() {
    }

    public static <T> void call(String name, Class<T> type, T arg) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try {
            Method m = CACHE.get(name);
            if (m == null) {
                m = Class.forName(HOOKS).getMethod(name, type);
                CACHE.put(name, m);
            }
            m.invoke(null, arg);
        } catch (ReflectiveOperationException e) {
            CreateStats.LOGGER.error("[Pathways] client hook {} failed", name, e);
        }
    }
}
