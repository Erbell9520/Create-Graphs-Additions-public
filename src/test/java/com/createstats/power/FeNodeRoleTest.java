package com.createstats.power;

import com.mrh0.createaddition.blocks.connector.base.FakeConnectorEnergyHandler;
import com.mrh0.createaddition.blocks.modular_accumulator.FakeAccumulatorEnergyHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeNodeRoleTest {
    private static IEnergyStorage box(int energy, int capacity, boolean give, boolean take) {
        return new IEnergyStorage() {
            int stored = energy;

            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                if (!take) {
                    return 0;
                }
                final int accepted = Math.min(maxReceive, capacity - stored);
                if (accepted > 0 && !simulate) {
                    stored += accepted;
                }
                return Math.max(0, accepted);
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                if (!give) {
                    return 0;
                }
                final int given = Math.min(maxExtract, stored);
                if (given > 0 && !simulate) {
                    stored -= given;
                }
                return Math.max(0, given);
            }

            @Override
            public int getEnergyStored() {
                return stored;
            }

            @Override
            public int getMaxEnergyStored() {
                return capacity;
            }

            @Override
            public boolean canExtract() {
                return give;
            }

            @Override
            public boolean canReceive() {
                return take;
            }
        };
    }

    @Test
    void aGeneratorGivesAndNeverTakes() {
        assertEquals(FeNodeRole.Kind.GENERATOR,
                FeNodeRole.of(box(5_000, 5_000, true, false), 4_096).kind());
    }

    @Test
    void aMachineTakesAndNeverGives() {
        assertEquals(FeNodeRole.Kind.LOAD,
                FeNodeRole.of(box(0, 5_000, false, true), 4_096).kind());
    }

    @Test
    void aBatteryIsStorageAndCountsTowardTheReserve() {
        final FeNodeRole role = FeNodeRole.of(box(20_000, 50_000, true, true), 4_096);
        assertEquals(FeNodeRole.Kind.STORAGE, role.kind());
        assertTrue(role.isReserve());
        assertTrue(role.givesNow());
        assertTrue(role.takesNow());
    }

    @Test
    void aFullBatteryIsStillStorageAndStillReserve() {
        final FeNodeRole role = FeNodeRole.of(box(50_000, 50_000, true, true), 4_096);
        assertEquals(FeNodeRole.Kind.STORAGE, role.kind());
        assertTrue(role.isReserve());
        assertTrue(role.givesNow());
        assertFalse(role.takesNow());
    }

    @Test
    void anEmptyBatteryIsStillStorageAndCannotGive() {
        final FeNodeRole role = FeNodeRole.of(box(0, 50_000, true, true), 4_096);
        assertEquals(FeNodeRole.Kind.STORAGE, role.kind());
        assertFalse(role.givesNow());
        assertTrue(role.takesNow());
    }

    @Test
    void aPushingConnectorIsAGeneratorAndNotAReserve() {
        final FeNodeRole role =
                FeNodeRole.of(new FakeConnectorEnergyHandler(true, 5_000, 1_000_000), 4_096);
        assertEquals(FeNodeRole.Kind.GENERATOR, role.kind());
        assertFalse(role.isReserve(), "their network's buffer is theirs, not ours");
    }

    @Test
    void aPullingConnectorIsALoadAndNotAReserve() {
        final FeNodeRole role =
                FeNodeRole.of(new FakeConnectorEnergyHandler(false, 5_000, 0), 4_096);
        assertEquals(FeNodeRole.Kind.LOAD, role.kind());
        assertFalse(role.isReserve());
    }

    @Test
    void aConnectorWithADryNetworkIsNothingThisTick() {
        assertEquals(FeNodeRole.Kind.NONE,
                FeNodeRole.of(new FakeConnectorEnergyHandler(true, 5_000, 0), 4_096).kind());
    }

    @Test
    void aHandlerWithNoCapacityIsNeverATank() {
        assertEquals(FeNodeRole.Kind.NONE, FeNodeRole.of(box(0, 0, true, true), 4_096).kind());
    }

    @Test
    void aKnownTankIsATankEvenWithNoCapacityYet() {
        final FeNodeRole role = FeNodeRole.of(FakeAccumulatorEnergyHandler.unresolved(), 4_096);
        assertEquals(FeNodeRole.Kind.STORAGE, role.kind());
        assertTrue(role.isReserve());

        assertFalse(role.givesNow());
        assertFalse(role.takesNow());
    }

    @Test
    void aFormedAccumulatorIsStorageBothWays() {
        final FeNodeRole role =
                FeNodeRole.of(new FakeAccumulatorEnergyHandler(500_000, 1_000_000, 4_096), 4_096);
        assertEquals(FeNodeRole.Kind.STORAGE, role.kind());
        assertTrue(role.isReserve());
        assertTrue(role.givesNow());
        assertTrue(role.takesNow());
    }

    @Test
    void aKnownTankIsNeverMistakenForAMouth() {
        assertFalse(FeForeignHandlers.isNetworkMouth(FakeAccumulatorEnergyHandler.unresolved()));
        assertTrue(FeForeignHandlers.isKnownStorage(FakeAccumulatorEnergyHandler.unresolved()));

        assertTrue(FeForeignHandlers.isNetworkMouth(new FakeConnectorEnergyHandler(true, 5_000, 900)));
        assertFalse(FeForeignHandlers.isKnownStorage(new FakeConnectorEnergyHandler(true, 5_000, 900)));
    }

    @Test
    void nothingAttachedIsNothing() {
        assertEquals(FeNodeRole.Kind.NONE, FeNodeRole.of((IEnergyStorage) null, 4_096).kind());
    }
}
