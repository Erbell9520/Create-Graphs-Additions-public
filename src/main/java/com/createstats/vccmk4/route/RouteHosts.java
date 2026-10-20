package com.createstats.vccmk4.route;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RouteHosts {
    private static final Map<UUID, WeakReference<Vcc4Route>> HOSTS = new ConcurrentHashMap<>();

    private RouteHosts() {
    }

    static void register(UUID console, Vcc4Route route) {
        WeakReference<Vcc4Route> ref = HOSTS.get(console);
        if (ref == null || ref.get() != route) HOSTS.put(console, new WeakReference<>(route));
    }

    static void forget(UUID console, Vcc4Route route) {
        WeakReference<Vcc4Route> ref = HOSTS.get(console);
        if (ref != null && ref.get() == route) HOSTS.remove(console, ref);
    }

    @Nullable
    public static Vcc4Route get(UUID console) {
        WeakReference<Vcc4Route> ref = console == null ? null : HOSTS.get(console);
        Vcc4Route r = ref == null ? null : ref.get();
        if (ref != null && r == null) HOSTS.remove(console, ref);
        return r;
    }

    public static List<Vcc4Route> all() {
        List<Vcc4Route> out = new ArrayList<>();
        for (WeakReference<Vcc4Route> ref : HOSTS.values()) {
            Vcc4Route r = ref.get();
            if (r != null) out.add(r);
        }
        return out;
    }

    public static void clear() {
        HOSTS.clear();
    }
}
