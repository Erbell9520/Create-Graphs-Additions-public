package com.createstats.vccmk4.console;

import com.createstats.surveyor.core.Comfort;
import com.createstats.vccmk4.network.Vcc4Snapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Vcc4RoutePageTest {
    private static final int[] SCREEN = {853, 533};

    @Test
    void cruiseStepsFromThePathwaysOwn() {
        assertEquals(7f, Vcc4Console.stepCruise(0f, 6f, 1), 1e-6);
        assertEquals(5f, Vcc4Console.stepCruise(0f, 6f, -1), 1e-6);
        assertEquals(3.5f, Vcc4Console.stepCruise(4f, 6f, -1), 1e-6);
        assertEquals(5f, Vcc4Console.stepCruise(4f, 6f, 1), 1e-6, "4 is the seam: up from it is a whole step, down a half");
        assertEquals(0f, Vcc4Console.stepCruise(1f, 6f, -1), 1e-6, "below 1 is the pathway's own again");
        assertEquals(40f, Vcc4Console.stepCruise(40f, 6f, 1), 1e-6, "and 40 is the top");
    }

    private static Vcc4Snapshot car() {
        return Vcc4ConsoleLayoutTest.carForRoutes();
    }

    private static Vcc4Console page(Orders host, Vcc4Snapshot s) {
        return new Vcc4Console(host, s, Vcc4Console.Page.ROUTE);
    }

    private static Vcc4ConsoleLayoutTest.Recorder render(Vcc4Console console, Orders host) {
        console.render(new Vcc4ConsoleLayoutTest.Recorder(host.base), SCREEN[0], SCREEN[1], -1, -1);
        Vcc4ConsoleLayoutTest.Recorder r = new Vcc4ConsoleLayoutTest.Recorder(host.base);
        console.render(r, SCREEN[0], SCREEN[1], -1, -1);
        return r;
    }

    private static void click(Vcc4Console console, Orders host, String text) {
        int[] at = render(console, host).where(text);
        assertTrue(at != null, "\"" + text + "\" is on the page");
        console.mouseClicked(at[0] + 2, at[1] + 3, 0);
    }

    private static String order(int op, UUID pathway, int a) {
        return op + " " + Vcc4ConsoleLayoutTest.RouteData.CONSOLE + " " + pathway + " " + a;
    }

    static final class Orders implements ConsoleHost {
        final Vcc4ConsoleLayoutTest.TestHost base;
        final List<String> sent = new ArrayList<>();

        Orders(Vcc4ConsoleLayoutTest.TestHost base) {
            this.base = base;
        }

        @Override
        public int width(String text) {
            return base.width(text);
        }

        @Override
        public String tr(String key, Object... args) {
            return base.tr(key, args);
        }

        @Override
        public String name(String idOrName) {
            return base.name(idOrName);
        }

        @Override
        public String keyName(int keyId) {
            return base.keyName(keyId);
        }

        @Override
        public List<String> keyAlso(int keyId) {
            return base.keyAlso(keyId);
        }

        @Override
        public boolean keyLeavesSeat(int keyId) {
            return base.keyLeavesSeat(keyId);
        }

        @Override
        public int resolveKey(int glfwKey, int scanCode) {
            return base.resolveKey(glfwKey, scanCode);
        }

        @Override
        public int resolveMouse(int button) {
            return base.resolveMouse(button);
        }

        @Override
        public void send(int action, int a, int b, long target) {
            sent.add("console " + action + " " + a + " " + b);
        }

        @Override
        public void sendOrder(int op, UUID console, UUID pathway, int a) {
            sent.add(op + " " + console + " " + pathway + " " + a);
        }

        @Override
        public void click() {
        }
    }
}
