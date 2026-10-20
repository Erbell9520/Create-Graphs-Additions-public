package com.createstats.power;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplianceStatusTest {
    @Test
    @DisplayName("a kitchen timer rounds up: a cycle with ticks left never reads 0:00")
    void clock() {
        assertEquals("0:00", ApplianceStatus.clock(0));
        assertEquals("0:01", ApplianceStatus.clock(1));
        assertEquals("0:01", ApplianceStatus.clock(20));
        assertEquals("0:02", ApplianceStatus.clock(21));
        assertEquals("1:12", ApplianceStatus.clock(72 * 20));
        assertEquals("12:05", ApplianceStatus.clock(725 * 20));
        assertEquals("0:00", ApplianceStatus.clock(-40));
    }

    @Test
    @DisplayName("progress runs from 0 at the start of a cycle to 1 at its end, clamped both ways")
    void progress() {
        final Component t = Component.literal("x");
        assertEquals(0f, ApplianceStatus.running(t, 100, 100, null).progress(), 1e-6);
        assertEquals(0.75f, ApplianceStatus.running(t, 100, 25, null).progress(), 1e-6);
        assertEquals(1f, ApplianceStatus.running(t, 100, -5, null).progress(), 1e-6);
        assertEquals(0f, ApplianceStatus.running(t, 100, 400, null).progress(), 1e-6);
        assertEquals(ApplianceStatus.NO_PROGRESS, ApplianceStatus.running(t, 0, 10, null).progress(), 1e-6);
        assertTrue(ApplianceStatus.running(t, 10, 5, null).running());
    }

    @Test
    @DisplayName("idle means no bar and not running, so the panel never reads it as a finished cycle")
    void idle() {
        final ApplianceStatus s = ApplianceStatus.idle(Component.literal("Ready"));
        assertFalse(s.running());
        assertEquals(ApplianceStatus.NO_PROGRESS, s.progress(), 1e-6);
    }
}
