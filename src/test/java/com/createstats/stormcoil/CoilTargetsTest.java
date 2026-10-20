package com.createstats.stormcoil;

import com.createstats.stormcoil.CoilTargets.Kind;
import com.createstats.stormcoil.CoilTargets.Mode;
import com.createstats.stormcoil.CoilTargets.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilTargetsTest {
    private static Target plain(Kind kind) {
        return new Target(kind, true, false, false, false, false, false, false);
    }

    @Test
    @DisplayName("never a player, a golem, an owned, named or leashed mob, the dead, or creative")
    void neverList() {
        for (Mode mode : Mode.values()) {
            for (Kind kind : Kind.values()) {
                assertFalse(CoilTargets.allows(mode, new Target(kind, false, false, false, false, false, false, false)));
                assertFalse(CoilTargets.allows(mode, new Target(kind, true, true, false, false, false, false, false)));
                assertFalse(CoilTargets.allows(mode, new Target(kind, true, false, true, false, false, false, false)));
                assertFalse(CoilTargets.allows(mode, new Target(kind, true, false, false, true, false, false, false)));
                assertFalse(CoilTargets.allows(mode, new Target(kind, true, false, false, false, true, false, false)));
            }
            assertFalse(CoilTargets.allows(mode, plain(Kind.PLAYER)));
            assertFalse(CoilTargets.allows(mode, plain(Kind.GOLEM)));
            assertFalse(CoilTargets.allows(mode, plain(Kind.OTHER)));
        }
    }

    @Test
    @DisplayName("Hostile: hostile mobs and creepers, again and again; never a villager or a pig")
    void hostile() {
        assertTrue(CoilTargets.allows(Mode.HOSTILE, plain(Kind.HOSTILE)));
        assertTrue(CoilTargets.allows(Mode.HOSTILE, plain(Kind.CREEPER)));
        assertTrue(CoilTargets.allows(Mode.HOSTILE,
                new Target(Kind.HOSTILE, true, false, false, false, false, false, true)), "until dead");
        assertFalse(CoilTargets.allows(Mode.HOSTILE, plain(Kind.VILLAGER)));
        assertFalse(CoilTargets.allows(Mode.HOSTILE, plain(Kind.PIG)));
        assertFalse(CoilTargets.allows(Mode.HOSTILE, plain(Kind.MOOSHROOM)));
    }

    @Test
    @DisplayName("Transform: each convertible once, a charged creeper skipped, nothing else")
    void transform() {
        for (Kind k : new Kind[] {Kind.CREEPER, Kind.VILLAGER, Kind.PIG, Kind.MOOSHROOM}) {
            assertTrue(CoilTargets.allows(Mode.TRANSFORM, plain(k)), k.name());
            assertFalse(CoilTargets.allows(Mode.TRANSFORM, new Target(k, true, false, false, false, false, false, true)),
                    "a second arc on the same " + k);
        }
        assertFalse(CoilTargets.allows(Mode.TRANSFORM,
                new Target(Kind.CREEPER, true, false, false, false, false, true, false)));
        assertFalse(CoilTargets.allows(Mode.TRANSFORM, plain(Kind.HOSTILE)));
    }

    @Test
    @DisplayName("Storm only never arcs")
    void stormOnly() {
        for (Kind kind : Kind.values()) {
            assertFalse(CoilTargets.allows(Mode.STORM_ONLY, plain(kind)));
        }
        assertEquals(Mode.STORM_ONLY, Mode.of(99));
    }

    @Test
    @DisplayName("the memory forgets after a day, keeps at most 256, and round-trips")
    void memory() {
        final CoilZapMemory m = new CoilZapMemory();
        final UUID a = UUID.randomUUID();
        m.remember(a, 100);
        assertTrue(m.has(a, 100 + CoilZapMemory.TTL - 1));
        assertFalse(m.has(a, 100 + CoilZapMemory.TTL));
        for (int i = 0; i < 400; i++) {
            m.remember(UUID.randomUUID(), 200);
        }
        assertEquals(CoilZapMemory.MAX, m.size());
        final CoilZapMemory copy = new CoilZapMemory();
        copy.unpack(m.pack());
        assertEquals(m.size(), copy.size());
        m.prune(200 + CoilZapMemory.TTL);
        assertEquals(0, m.size());
    }
}
