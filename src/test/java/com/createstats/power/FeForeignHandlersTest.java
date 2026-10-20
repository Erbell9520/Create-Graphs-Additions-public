package com.createstats.power;

import com.mrh0.createaddition.blocks.connector.base.FakeConnectorEnergyHandler;
import com.mrh0.createaddition.blocks.modular_accumulator.FakeAccumulatorEnergyHandler;
import mekanism.common.tile.transmitter.FakeUniversalCableHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import owmii.powah.block.energycell.FakeEnergyCellHandler;
import sonar.fluxnetworks.common.connection.FakeFluxPointHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeForeignHandlersTest {
    @BeforeEach
    void forgetWhatEarlierCasesTaughtIt() {
        FeForeignHandlers.forgetLearned();
    }

    @Test
    void aMekanismUniversalCableIsAMouthAndNeverAReserve() {
        final IEnergyStorage cable = new FakeUniversalCableHandler(false, 12_000_000, 40_000_000);

        assertTrue(FeForeignHandlers.isNetworkMouth(cable));
        assertFalse(FeForeignHandlers.isKnownStorage(cable));
        assertEquals(FeNodeRole.Kind.GENERATOR, FeNodeRole.of(cable, 4_096).kind(),
                "pushing, so it is a source this tick");
        assertFalse(FeNodeRole.of(cable, 4_096).isReserve(),
                "and never part of what this branch has in the bank");
    }

    @Test
    void aMekanismCableInPullModeIsALoad() {
        final IEnergyStorage cable = new FakeUniversalCableHandler(true, 0, 40_000_000);

        assertEquals(FeNodeRole.Kind.LOAD, FeNodeRole.of(cable, 4_096).kind());
        assertFalse(FeNodeRole.of(cable, 4_096).isReserve());
    }

    @Test
    void aFluxPointIsAMouthDespiteReportingAWholeNetwork() {
        final IEnergyStorage point = new FakeFluxPointHandler(2_000_000_000L);

        assertTrue(FeForeignHandlers.isNetworkMouth(point));
        assertEquals(FeNodeRole.Kind.LOAD, FeNodeRole.of(point, 4_096).kind(),
                "a point takes and posts to the network, so it is a load");
        assertFalse(FeNodeRole.of(point, 4_096).isReserve());
    }

    @Test
    void theOriginalCreateAdditionConnectorStillReadsAsAMouth() {
        final IEnergyStorage connector = new FakeConnectorEnergyHandler(true, 5_000, 1_000_000);

        assertTrue(FeForeignHandlers.isNetworkMouth(connector));
        assertFalse(FeForeignHandlers.isKnownStorage(connector));
    }

    @Test
    void aPowahEnergyCellIsATank() {
        final IEnergyStorage cell = new FakeEnergyCellHandler(500_000, 1_000_000, 4_096);

        assertTrue(FeForeignHandlers.isKnownStorage(cell));
        assertFalse(FeForeignHandlers.isNetworkMouth(cell));

        final FeNodeRole role = FeNodeRole.of(cell, 4_096);
        assertEquals(FeNodeRole.Kind.STORAGE, role.kind());
        assertTrue(role.isReserve(), "a real tank's charge is part of what the grid has");
        assertTrue(role.givesNow());
        assertTrue(role.takesNow());
    }

    @Test
    void aTankStillResolvingIsListedHoldingNothingRatherThanVanishing() {
        final IEnergyStorage unresolved = FakeEnergyCellHandler.unresolved();

        assertTrue(FeForeignHandlers.isKnownStorage(unresolved));
        assertFalse(FeForeignHandlers.isNetworkMouth(unresolved),
                "named storage beats the capacity rule, which is the whole point of the second list");

        final FeNodeRole role = FeNodeRole.of(unresolved, 4_096);
        assertEquals(FeNodeRole.Kind.STORAGE, role.kind());
        assertFalse(role.givesNow());
        assertFalse(role.takesNow());
    }

    @Test
    void aModularAccumulatorIsStillATank() {
        assertTrue(FeForeignHandlers.isKnownStorage(FakeAccumulatorEnergyHandler.unresolved()));
        assertFalse(FeForeignHandlers.isNetworkMouth(FakeAccumulatorEnergyHandler.unresolved()));
    }

    @Test
    void ourOwnHandlersAreNeverForeign() {
        final FeConsumer consumer = new FeConsumer(settings());
        assertFalse(FeForeignHandlers.isNetworkMouth(consumer));
        assertFalse(FeForeignHandlers.isKnownStorage(consumer), "a consumer is a machine, not a tank");
    }

    @Test
    void anUnknownHandlerWithNoCapacityIsAMouthOnItsOwnMerits() {
        assertTrue(FeForeignHandlers.isNetworkMouth(new FakeUnknownHandler(0, 0)));
    }

    @Test
    void anUnknownHandlerThatHoldsSomethingDegradesToABattery() {
        final IEnergyStorage unknown = new FakeUnknownHandler(5_000, 50_000);
        assertFalse(FeForeignHandlers.isNetworkMouth(unknown));
        assertFalse(FeForeignHandlers.isKnownStorage(unknown));
        assertEquals(FeNodeRole.Kind.STORAGE, FeNodeRole.of(unknown, 4_096).kind());
    }

    @Test
    void nullIsNeither() {
        assertFalse(FeForeignHandlers.isNetworkMouth(null));
        assertFalse(FeForeignHandlers.isKnownStorage(null));
    }

    @Test
    void forgettingLearnedVerdictsDoesNotChangeTheNameListAnswers() {
        final IEnergyStorage cable = new FakeUniversalCableHandler(false, 12_000_000, 40_000_000);
        final IEnergyStorage cell = new FakeEnergyCellHandler(500_000, 1_000_000, 4_096);
        assertTrue(FeForeignHandlers.isNetworkMouth(cable));
        assertTrue(FeForeignHandlers.isKnownStorage(cell));

        FeForeignHandlers.forgetLearned();

        assertTrue(FeForeignHandlers.isNetworkMouth(cable));
        assertTrue(FeForeignHandlers.isKnownStorage(cell));
    }

    @Test
    void learnIgnoresAMissingLevelOrPositionRatherThanThrowing() {
        final IEnergyStorage cable = new FakeUniversalCableHandler(false, 1_000, 2_000);
        FeForeignHandlers.learn(null, null, cable);
        FeForeignHandlers.learn(null, net.minecraft.core.BlockPos.ZERO, cable);
        assertTrue(FeForeignHandlers.isNetworkMouth(cable), "and the name list still answers");
    }

    private record FakeUnknownHandler(int stored, int capacity) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return Math.max(0, Math.min(maxReceive, capacity - stored));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return Math.min(maxExtract, stored);
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
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private static FeConsumer.Settings settings() {
        return new FeConsumer.Settings() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public int capacity() {
                return 100_000;
            }

            @Override
            public int idleDrain() {
                return 0;
            }

            @Override
            public int activeDrain() {
                return 0;
            }

            @Override
            public int burstCost() {
                return 0;
            }

            @Override
            public int rebootThresholdPct() {
                return 0;
            }
        };
    }
}
