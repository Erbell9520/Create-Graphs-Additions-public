package com.createstats.flight.bridge;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class ForeignHandles {
    private ForeignHandles() {
    }

    @Nullable
    public static MethodHandle findStatic(Class<?> owner, String name, Class<?> ret, Class<?>... params) {
        try {
            return MethodHandles.publicLookup().findStatic(owner, name, MethodType.methodType(ret, params));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Nullable
    public static MethodHandle findVirtual(Class<?> owner, String name, Class<?> ret, Class<?>... params) {
        try {
            return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(ret, params));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    public static boolean fatal(Throwable t) {
        return t instanceof VirtualMachineError && !(t instanceof StackOverflowError);
    }
}
