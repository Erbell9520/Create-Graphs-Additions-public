package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableFeBufferTest {
    private static int changes;

    private static PortableFeBuffer buffer() {
        changes = 0;
        return new PortableFeBuffer(() -> changes++);
    }

    @Test
    @DisplayName("charge open reads as a load, offering reads as a source, neither reads as nothing")
    void rolesFollowTheMode() {
        final PortableFeBuffer b = buffer();
        b.configure(8192, true, false, 1_000_000);
        assertEquals(FeNodeRole.Kind.LOAD, FeNodeRole.of(b, 8192).kind());

        b.setEnergy(500);
        b.configure(8192, false, true, 0);
        assertEquals(FeNodeRole.Kind.GENERATOR, FeNodeRole.of(b, 8192).kind());

        b.configure(8192, false, false, 0);
        assertEquals(FeNodeRole.Kind.NONE, FeNodeRole.of(b, 8192).kind());
    }

    @Test
    @DisplayName("it can never be read as storage, so the base's banks can always charge it")
    void neverStorage() {
        final PortableFeBuffer b = buffer();
        assertThrows(IllegalArgumentException.class, () -> b.configure(8192, true, true, 100));
        for (boolean charge : new boolean[]{false, true}) {
            b.configure(8192, charge, !charge, 1_000_000);
            b.setEnergy(100);
            assertTrue(FeNodeRole.of(b, 8192).kind() != FeNodeRole.Kind.STORAGE);
        }
    }

    @Test
    @DisplayName("a closed handler refuses both directions at once")
    void closeRefusesBoth() {
        final PortableFeBuffer b = buffer();
        b.setEnergy(500);
        b.configure(8192, false, true, 0);
        b.close();
        assertEquals(0, b.extractEnergy(100, false));
        assertEquals(0, b.receiveEnergy(100, false));
        assertFalse(b.canExtract() || b.canReceive());
    }

    @Test
    @DisplayName("repeated pushes in one tick stop at the buffer, which is the dial (the §3.6 law)")
    void bufferIsTheCeilingAcrossPasses() {
        final PortableFeBuffer b = buffer();
        b.configure(4096, true, false, 1_000_000);
        int taken = 0;
        for (int pass = 0; pass < 5; pass++) {
            taken += b.receiveEnergy(3000, false);
        }
        assertEquals(4096, taken);
    }

    @Test
    @DisplayName("pushes stop at the train's room less what is already waiting")
    void trainRoomBounds() {
        final PortableFeBuffer b = buffer();
        b.setEnergy(300);
        b.configure(8192, true, false, 1000);
        assertEquals(700, b.receiveEnergy(8192, false));
        assertEquals(0, b.receiveEnergy(8192, false));
    }

    @Test
    @DisplayName("a simulate spends nothing and reports no change (INV-02)")
    void simulateSpendsNothing() {
        final PortableFeBuffer b = buffer();
        b.configure(8192, true, false, 1_000_000);
        assertEquals(8192, b.receiveEnergy(8192, true));
        assertEquals(0, b.energy());
        assertEquals(0, changes);
        b.setEnergy(100);
        b.configure(8192, false, true, 0);
        assertEquals(100, b.extractEnergy(8192, true));
        assertEquals(100, b.energy());
        assertEquals(0, changes);
    }

    @Test
    @DisplayName("capacity is never zero and never below the charge, so a turned-down dial deletes nothing")
    void capacityFloors() {
        final PortableFeBuffer b = buffer();
        b.configure(0, false, false, 0);
        assertTrue(b.getMaxEnergyStored() > 0);
        b.setEnergy(20_000);
        b.configure(256, false, false, 0);
        assertEquals(20_000, b.getMaxEnergyStored());
        assertEquals(20_000, b.energy());
    }

    @Test
    @DisplayName("a zero-capacity handler is a foreign mouth; this one never looks like one")
    void neverAMouth() {
        final PortableFeBuffer b = buffer();
        b.configure(0, false, false, 0);
        assertFalse(FeForeignHandlers.isNetworkMouth(b));
    }
}
