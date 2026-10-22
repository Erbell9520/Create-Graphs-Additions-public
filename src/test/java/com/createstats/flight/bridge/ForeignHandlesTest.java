package com.createstats.flight.bridge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForeignHandlesTest {
    private static final String FIXTURE = "com.createstats.flight.bridge.fixture.";

    private static final class ServerLikeLoader extends ClassLoader {
        int refusals;

        ServerLikeLoader() {
            super(ForeignHandlesTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(FIXTURE)) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                if (name.endsWith(".DistC")) {
                    refusals++;
                    throw new RuntimeException("Attempted to load class " + name.replace('.', '/')
                            + " for invalid dist DEDICATED_SERVER");
                }
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (in == null) throw new ClassNotFoundException(name);
                        byte[] bytes = in.readAllBytes();
                        c = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) resolveClass(c);
                return c;
            }
        }
    }

    @Test
    void theFixtureFailsTheWayTheServerDid() throws Exception {
        ServerLikeLoader loader = new ServerLikeLoader();
        Class<?> owner = loader.loadClass(FIXTURE + "DistOwner");
        Class<?> a = loader.loadClass(FIXTURE + "DistA");
        assertThrows(Throwable.class, owner::getMethods, "listing the class must fail, or the fixture is not armed");
        assertThrows(Throwable.class, () -> owner.getMethod("get", a),
                "even the exact parameters must fail: getMethod resolves the sibling's types too");
    }

    @Test
    void aHandleFindsAndCallsTheOneMethodItAsksFor() throws Throwable {
        ServerLikeLoader loader = new ServerLikeLoader();
        Class<?> owner = loader.loadClass(FIXTURE + "DistOwner");
        Class<?> a = loader.loadClass(FIXTURE + "DistA");
        Object arg = a.getConstructor().newInstance();

        MethodHandle get = ForeignHandles.findStatic(owner, "get", String.class, a);
        assertNotNull(get, "findStatic must resolve get(DistA) without touching get(DistC)");
        assertEquals("A", get.invoke(arg));

        MethodHandle name = ForeignHandles.findVirtual(owner, "name", String.class, a);
        assertNotNull(name, "findVirtual must resolve name(DistA) on the same class");
        assertEquals("named", name.invoke(owner.getConstructor().newInstance(), arg));
    }

    @Test
    void aMissIsANullNeverAThrow() throws Exception {
        ServerLikeLoader loader = new ServerLikeLoader();
        Class<?> owner = loader.loadClass(FIXTURE + "DistOwner");
        Class<?> a = loader.loadClass(FIXTURE + "DistA");
        assertNull(ForeignHandles.findStatic(owner, "nothing", String.class, a));
        assertNull(ForeignHandles.findStatic(owner, "get", Integer.class, a), "the return type is part of the descriptor");
        assertNull(ReflectionAccessors.findMethodAccepting(owner, "get", a),
                "the listing helper must miss quietly on a class it cannot list, as it did not before 1.15.38");
    }

    @Test
    void onlyAMachineErrorIsFatal() {
        assertEquals(true, ForeignHandles.fatal(new OutOfMemoryError()));
        assertEquals(false, ForeignHandles.fatal(new StackOverflowError()));
        assertEquals(false, ForeignHandles.fatal(new RuntimeException()));
        assertEquals(false, ForeignHandles.fatal(new AssertionError()));
        assertEquals(false, ForeignHandles.fatal(new NoClassDefFoundError()));
    }
}
