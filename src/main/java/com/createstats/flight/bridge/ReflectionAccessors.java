package com.createstats.flight.bridge;

import com.createstats.CreateStats;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** [1] */
public final class ReflectionAccessors {

    static final Object MISSING = new Object();

    private static final ConcurrentMap<String, Object> CLASS_CACHE  = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Object> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Object> FIELD_CACHE  = new ConcurrentHashMap<>();

    private ReflectionAccessors() {}

    /** Try multiple class FQN candidates; return the first that resolves, or {@code null}. */
    @Nullable
    public static Class<?> findClassAny(String... candidates) {
        for (String fqn : candidates) {
            Class<?> c = findClass(fqn);
            if (c != null) return c;
        }
        return null;
    }

    /** Passive class lookup, does NOT run static initializers. Result cached. */
    @Nullable
    public static Class<?> findClass(String fqn) {
        Object cached = CLASS_CACHE.get(fqn);
        if (cached == MISSING) return null;
        if (cached != null) return (Class<?>) cached;
        try {
            Class<?> c = Class.forName(fqn, false, ReflectionAccessors.class.getClassLoader());
            CLASS_CACHE.put(fqn, c);
            return c;
        } catch (ClassNotFoundException | LinkageError ex) {
            CLASS_CACHE.put(fqn, MISSING);
            return null;
        }
    }

    /** Active class lookup, runs static initializers (use only when reading static state). */
    @Nullable
    public static Class<?> initClass(String fqn) {
        try {
            return Class.forName(fqn, true, ReflectionAccessors.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }

    /** [2] */
    @Nullable
    public static Method findMethodAccepting(Class<?> owner, String name, Class<?>... argTypes) {
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != argTypes.length) continue;
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                Class<?> expected = argTypes[i];
                if (expected == null) continue; // wildcard, accept any type at this position
                // params[i].isAssignableFrom(expected) == true means: a variable of type
                // params[i] can hold an instance of expected (i.e. expected is a subtype)
                if (!params[i].isAssignableFrom(expected)) { ok = false; break; }
            }
            if (ok) { m.setAccessible(true); return m; }
        }
        return null;
    }

    /** Look up a method (any visibility) and make it accessible. Cached. */
    @Nullable
    public static Method method(Class<?> owner, String name, Class<?>... params) {
        String key = owner.getName() + '#' + name + paramSig(params);
        Object cached = METHOD_CACHE.get(key);
        if (cached == MISSING) return null;
        if (cached != null) return (Method) cached;
        try {
            Method m = owner.getDeclaredMethod(name, params);
            m.setAccessible(true);
            METHOD_CACHE.put(key, m);
            return m;
        } catch (NoSuchMethodException | LinkageError ex) {
            // Walk superclass chain for the simple no-arg case (cheap & common).
            if (params.length == 0) {
                Class<?> p = owner.getSuperclass();
                while (p != null && p != Object.class) {
                    try {
                        Method m = p.getDeclaredMethod(name);
                        m.setAccessible(true);
                        METHOD_CACHE.put(key, m);
                        return m;
                    } catch (NoSuchMethodException ignore) {
                        p = p.getSuperclass();
                    }
                }
            }
            METHOD_CACHE.put(key, MISSING);
            return null;
        }
    }

    /** Look up a field (any visibility) and make it accessible. Cached. */
    @Nullable
    public static Field field(Class<?> owner, String name) {
        String key = owner.getName() + '@' + name;
        Object cached = FIELD_CACHE.get(key);
        if (cached == MISSING) return null;
        if (cached != null) return (Field) cached;
        Class<?> p = owner;
        while (p != null && p != Object.class) {
            try {
                Field f = p.getDeclaredField(name);
                f.setAccessible(true);
                FIELD_CACHE.put(key, f);
                return f;
            } catch (NoSuchFieldException ignore) {
                p = p.getSuperclass();
            }
        }
        FIELD_CACHE.put(key, MISSING);
        return null;
    }

    /** Invoke {@code m} on {@code receiver} swallowing reflection failures. */
    @Nullable
    public static Object invoke(@Nullable Method m, @Nullable Object receiver, Object... args) {
        if (m == null) return null;
        try {
            return m.invoke(receiver, args);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return null;
        }
    }

    /** Read field {@code f} from {@code receiver} swallowing reflection failures. */
    @Nullable
    public static Object read(@Nullable Field f, @Nullable Object receiver) {
        if (f == null) return null;
        try {
            return f.get(receiver);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return null;
        }
    }

    /** Quietly evaluate a supplier; return {@code null} on any throwable. */
    @Nullable
    public static <T> T tryGet(Supplier<T> s) {
        try { return s.get(); }
        catch (LinkageError | RuntimeException ex) { return null; }
    }

    /** Log a one-shot informational message via the mod logger. */
    static void warnOnce(String capability, String detail) {
        CreateStats.LOGGER.warn("[FCC/Bridge] capability '{}' unavailable: {}", capability, detail);
    }

    private static String paramSig(Class<?>[] params) {
        if (params.length == 0) return "()";
        StringBuilder sb = new StringBuilder().append('(');
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(params[i].getName());
        }
        return sb.append(')').toString();
    }
}
