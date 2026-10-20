package com.createstats.compat;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewAgeCompatHelperTest {
    private static IEnergyStorage handler(int stored, int capacity, boolean receive, boolean extract) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return receive ? maxReceive : 0;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return extract ? Math.min(maxExtract, stored) : 0;
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
                return extract;
            }

            @Override
            public boolean canReceive() {
                return receive;
            }
        };
    }

    @Test
    void theirConnectorsHandlerIsRecognisedAsANetworkMouth() {
        assertTrue(NewAgeCompatHelper.isNetworkMouthHandler(
                handler(0, Integer.MAX_VALUE, true, true)));
    }

    @Test
    void anEnergiserIsATankAndNotAMouth() {
        assertFalse(NewAgeCompatHelper.isNetworkMouthHandler(handler(0, 4_000_000, true, true)));
    }

    @Test
    void aFullTankIsNotAMouthHoweverLargeItIs() {
        assertFalse(NewAgeCompatHelper.isNetworkMouthHandler(
                handler(Integer.MAX_VALUE, Integer.MAX_VALUE, true, true)));
    }

    @Test
    void aGiveOnlyHandlerIsNotAMouth() {
        assertFalse(NewAgeCompatHelper.isNetworkMouthHandler(handler(0, Integer.MAX_VALUE, false, true)));
    }

    @Test
    void nothingAtAllIsNotAMouth() {
        assertFalse(NewAgeCompatHelper.isNetworkMouthHandler(null));
    }
}
