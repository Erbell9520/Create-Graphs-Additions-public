package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForeignTerminalNodeTest {
    private static IEnergyStorage connector(int networkBuffer, boolean pushing, boolean pulling) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return pulling ? Math.min(maxReceive, networkBuffer) : 0;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return pushing ? Math.min(maxExtract, networkBuffer) : 0;
            }

            @Override
            public int getEnergyStored() {
                return networkBuffer;
            }

            @Override
            public int getMaxEnergyStored() {
                return networkBuffer * 4;
            }

            @Override
            public boolean canExtract() {
                return true;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    @Test
    void hidesTheOtherNetworksBufferSoItIsNeverCountedAsOurReserve() {
        final IEnergyStorage mouth = ForeignTerminalNode.mouth(connector(64_000, true, false));
        assertEquals(0, mouth.getEnergyStored());
        assertEquals(0, mouth.getMaxEnergyStored());
    }

    @Test
    void aPushingConnectorIsASourceAndNotAReserve() {
        final FeNodeRole role = FeNodeRole.of(ForeignTerminalNode.mouth(connector(64_000, true, false)), 8192);
        assertEquals(FeNodeRole.Kind.GENERATOR, role.kind());
        assertTrue(role.givesNow());
        assertFalse(role.isReserve());
    }

    @Test
    void aPullingConnectorIsALoadAndNotAReserve() {
        final FeNodeRole role = FeNodeRole.of(ForeignTerminalNode.mouth(connector(64_000, false, true)), 8192);
        assertEquals(FeNodeRole.Kind.LOAD, role.kind());
        assertTrue(role.takesNow());
        assertFalse(role.isReserve());
    }

    @Test
    void anIdleConnectorIsNothingRatherThanAFullBattery() {
        final FeNodeRole role = FeNodeRole.of(ForeignTerminalNode.mouth(connector(64_000, false, false)), 8192);
        assertEquals(FeNodeRole.Kind.NONE, role.kind());
        assertFalse(role.isReserve());
    }

    @Test
    void aConnectorIsNeverStorageHoweverItIsSet() {
        for (boolean pushing : new boolean[] {true, false}) {
            for (boolean pulling : new boolean[] {true, false}) {
                final FeNodeRole role =
                        FeNodeRole.of(ForeignTerminalNode.mouth(connector(64_000, pushing, pulling)), 8192);
                assertFalse(role.isReserve(),
                        "pushing=" + pushing + " pulling=" + pulling + " was read as grid reserve");
                assertEquals(false, role.kind() == FeNodeRole.Kind.STORAGE,
                        "pushing=" + pushing + " pulling=" + pulling + " was read as storage");
            }
        }
    }

    @Test
    void transfersPassStraightThroughUnchanged() {
        final IEnergyStorage mouth = ForeignTerminalNode.mouth(connector(1_000, true, true));
        assertEquals(600, mouth.extractEnergy(600, false));
        assertEquals(1_000, mouth.extractEnergy(4_000, false));
        assertEquals(600, mouth.receiveEnergy(600, false));
        assertEquals(1_000, mouth.receiveEnergy(4_000, false));
        assertTrue(mouth.canExtract());
        assertTrue(mouth.canReceive());
    }

    @Test
    void aQuietConnectorDoesNotBecomeAFullTank() {
        final FeNodeRole direct = FeNodeRole.of(connector(64_000, false, false), 8192);
        assertEquals(FeNodeRole.Kind.STORAGE, direct.kind(), "precondition: the raw handler reads as a tank");
        final FeNodeRole bridged = FeNodeRole.of(ForeignTerminalNode.mouth(connector(64_000, false, false)), 8192);
        assertEquals(FeNodeRole.Kind.NONE, bridged.kind());
    }
}
