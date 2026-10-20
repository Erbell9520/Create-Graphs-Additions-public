package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeymapTest {
    private static final int Z = KeyIds.key(90);
    private static final int R = KeyIds.key(82);
    private static final int Q = KeyIds.key(81);

    private static HeldKeys held(int... ids) {
        return HeldKeys.of(ids);
    }

    private static HeldKeys held(Channel... channels) {
        int[] ids = new int[channels.length];
        for (int i = 0; i < ids.length; i++) ids[i] = channels[i].id();
        return HeldKeys.of(ids);
    }

    @Test
    void shippedKeysDriveACar() {
        Keymap k = Keymap.defaults();
        assertEquals(1.0, k.axis(Control.THROTTLE, held(Channel.FORWARD)), 1.0e-9);
        assertEquals(-1.0, k.axis(Control.THROTTLE, held(Channel.BACK)), 1.0e-9);
        assertEquals(0.0, k.axis(Control.THROTTLE, held(Channel.FORWARD, Channel.BACK)), 1.0e-9, "both cancel");
        assertEquals(1.0, k.axis(Control.STEER, held(Channel.RIGHT)), 1.0e-9, "D is right");
        assertEquals(-1.0, k.axis(Control.STEER, held(Channel.LEFT)), 1.0e-9);
        assertTrue(k.held(Control.BRAKE, held(Channel.JUMP)));
        assertTrue(k.held(Control.BOOST, held(Channel.SPRINT)));
        assertFalse(k.held(Control.BRAKE, held(Channel.SPRINT)));
        assertEquals(1.0, k.axis(Control.AUX_A, held(Channel.AUX1)), 1.0e-9);
        assertEquals(-1.0, k.axis(Control.AUX_A, held(Channel.AUX2)), 1.0e-9);
        assertEquals(1.0, k.axis(Control.AUX_B, held(Channel.AUX3)), 1.0e-9);
        assertEquals(0.0, k.axis(Control.AUX_B, held(Channel.AUX1)), 1.0e-9);
        assertTrue(k.isDefault());
    }

    @Test
    void theNewControlsShipWithNoKeys() {
        Keymap k = Keymap.defaults();
        for (Control c : new Control[]{Control.LIFT, Control.SLIDE, Control.AUX_C, Control.AUX_D, Control.BEAM}) {
            assertFalse(k.bound(c), c + " has no key until the player picks one");
        }
        assertTrue(k.bound(Control.AUX_A));
    }

    @Test
    void anyPhysicalKeyCanDriveAControl() {
        Keymap k = Keymap.defaults();
        k.setPlus(Control.LIFT, R);
        k.setMinus(Control.LIFT, Q);
        assertEquals(1.0, k.axis(Control.LIFT, held(R)), 1.0e-9);
        assertEquals(-1.0, k.axis(Control.LIFT, held(Q)), 1.0e-9);
        assertEquals(0.0, k.axis(Control.LIFT, held(Channel.FORWARD)), 1.0e-9);
        k.setPlus(Control.AUX_A, Z);
        assertEquals(1.0, k.axis(Control.AUX_A, held(Z)), 1.0e-9, "a physical Z, not the vehicle binding");
        assertEquals(0.0, k.axis(Control.AUX_A, held(Channel.AUX1)), 1.0e-9);
    }

    @Test
    void invertFlipsAnAxisAndNeverAButton() {
        Keymap k = Keymap.defaults();
        k.setInverted(Control.STEER, true);
        assertEquals(-1.0, k.axis(Control.STEER, held(Channel.RIGHT)), 1.0e-9);
        k.setInverted(Control.BRAKE, true);
        assertFalse(k.inverted(Control.BRAKE), "a button has nothing to invert");
        assertEquals(1.0, k.axis(Control.BRAKE, held(Channel.JUMP)), 1.0e-9);
    }

    @Test
    void aButtonHasNoMinusKey() {
        Keymap k = Keymap.defaults();
        k.setMinus(Control.BOOST, R);
        assertEquals(KeyIds.NONE, k.minus(Control.BOOST));
    }

    @Test
    void keysInUseIsTheWatchList() {
        Keymap k = Keymap.defaults();
        int[] watch = k.keysInUse();
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, watch, "the nine bindings, sorted, once each");
        k.setPlus(Control.LIFT, R);
        k.setPlus(Control.AUX_C, R);
        assertEquals(10, k.keysInUse().length, "a shared key is watched once");
    }

    @Test
    void slotsOfNamesEveryPlaceAKeySits() {
        Keymap k = Keymap.defaults();
        k.setPlus(Control.LIFT, Channel.SPRINT.id());
        assertEquals(2, k.slotsOf(Channel.SPRINT.id()).size(), "boost and lift share sprint: a jump jet");
        assertEquals(Control.BOOST, k.slotsOf(Channel.SPRINT.id()).get(0).control(), "listed in display order");
        assertTrue(k.slotsOf(KeyIds.NONE).isEmpty());
    }

    @Test
    void intsRoundTripWithPhysicalKeys() {
        Keymap k = Keymap.defaults();
        k.setPlus(Control.THROTTLE, R);
        k.setInverted(Control.AUX_A, true);
        k.setMinus(Control.STEER, KeyIds.NONE);
        k.setPlus(Control.AUX_D, KeyIds.mouse(4));
        int[] ints = k.toInts();
        assertEquals(Control.values().length * Keymap.STRIDE, ints.length);
        Keymap back = Keymap.fromInts(ints);
        assertArrayEquals(ints, back.toInts());
        assertEquals(R, back.plus(Control.THROTTLE));
        assertTrue(back.inverted(Control.AUX_A));
        assertEquals(KeyIds.NONE, back.minus(Control.STEER));
        assertEquals(KeyIds.mouse(4), back.plus(Control.AUX_D));
    }

    @Test
    void aKeymapSavedBeforeTheNewControlsLoadsWithTheirDefaults() {
        int[] old = new int[6 * Keymap.STRIDE];
        int[] full = Keymap.defaults().toInts();
        System.arraycopy(full, 0, old, 0, old.length);
        old[0] = Channel.AUX3.id();
        Keymap k = Keymap.fromInts(old);
        assertEquals(Channel.AUX3.id(), k.plus(Control.THROTTLE));
        assertFalse(k.bound(Control.LIFT));
        assertEquals(Control.values().length * Keymap.STRIDE, k.toInts().length);
    }

    @Test
    void garbageIdsBecomeNoKey() {
        assertTrue(Keymap.fromInts(null).isDefault());
        assertTrue(Keymap.fromInts(new int[]{}).isDefault());
        Keymap k = Keymap.fromInts(new int[]{99, -4, 7, 1000 + 999, 2000, 0});
        assertEquals(KeyIds.NONE, k.plus(Control.THROTTLE), "an unknown id is no key");
        assertEquals(KeyIds.NONE, k.minus(Control.THROTTLE));
        assertTrue(k.inverted(Control.THROTTLE));
        assertEquals(KeyIds.NONE, k.plus(Control.STEER), "past GLFW's last key");
        assertEquals(KeyIds.NONE, k.minus(Control.STEER), "left mouse is attack, never a vehicle key");
    }

    @Test
    void resetRestoresEverything() {
        Keymap k = Keymap.defaults();
        k.setPlus(Control.THROTTLE, R);
        k.setInverted(Control.STEER, true);
        k.reset();
        assertTrue(k.isDefault());
    }

    @Test
    void heldKeysDropJunkAndDuplicates() {
        HeldKeys h = HeldKeys.of(R, R, 0, -5, 5000, Channel.JUMP.id());
        assertArrayEquals(new int[]{Channel.JUMP.id(), R}, h.toArray());
        assertTrue(h.has(R));
        assertFalse(h.has(KeyIds.NONE));
        int[] flood = new int[500];
        for (int i = 0; i < flood.length; i++) flood[i] = KeyIds.key(32 + (i % 300));
        assertTrue(HeldKeys.of(flood).toArray().length <= HeldKeys.MAX, "a crafted flood is capped");
        assertTrue(HeldKeys.of().isEmpty());
    }

    @Test
    void keyIdRangesDoNotOverlap() {
        for (Channel c : Channel.values()) {
            if (c == Channel.NONE) continue;
            assertTrue(KeyIds.isBinding(c.id()));
            assertFalse(KeyIds.isKey(c.id()));
            assertEquals(c, Channel.byId(c.id()));
        }
        assertEquals(KeyIds.NONE, KeyIds.key(-1), "GLFW's unknown key");
        assertEquals(KeyIds.NONE, KeyIds.mouse(0));
        assertEquals(KeyIds.NONE, KeyIds.mouse(1));
        assertTrue(KeyIds.isMouse(KeyIds.mouse(3)));
        assertEquals(82, KeyIds.glfwKey(R));
    }
}
