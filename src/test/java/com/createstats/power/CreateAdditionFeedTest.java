package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateAdditionFeedTest {
    private static final class TheirNetwork implements CreateAdditionFeed.Network {
        final int nodeCount;
        int inBuff;
        int inDemand;
        int outBuff;
        int outDemand;
        long demanded;

        TheirNetwork(int nodeCount) {
            this.nodeCount = nodeCount;
        }

        int maxBuff() {
            return Math.min(this.nodeCount * (this.outDemand + this.inDemand * 2 + 10), 80_000);
        }

        void tick() {
            final int t = this.outBuff;
            this.outBuff = this.inBuff;
            this.inBuff = t;
            this.outDemand = this.inDemand;
            this.inDemand = 0;
        }

        @Override
        public int push(int energy, boolean simulate) {
            energy = Math.min(this.maxBuff() - this.inBuff, energy);
            energy = Math.max(energy, 0);
            if (!simulate) {
                this.inBuff += energy;
            }
            return energy;
        }

        @Override
        public int pull(int energy, boolean simulate) {
            final int r = Math.max(Math.min(energy, this.outBuff), 0);
            if (!simulate) {
                this.outBuff -= r;
            }
            return r;
        }

        @Override
        public void demand(int energy) {
            this.inDemand += energy;
            this.demanded += energy;
        }

        int inFlight() {
            return this.inBuff + this.outBuff;
        }
    }

    private record Their(@Nullable TheirNetwork network, int maxIn, int maxOut) implements CreateAdditionFeed.Connector {
    }

    private static int pullConnectorOnCreative(TheirNetwork n, int maxIn) {
        final int toPush = n.push(maxIn, true);
        n.push(toPush, false);
        return toPush;
    }

    private static int pushConnectorOn(TheirNetwork n, int maxOut, int room) {
        final int wants = Math.min(maxOut, room);
        n.demand(wants);
        return n.pull(wants, false);
    }

    @Test
    @DisplayName("the owner's import: a Large Connector in Pull on Creative Energy, a PULL Coupler wired to it")
    void importsThroughTheirPullConnector() {
        final TheirNetwork n = new TheirNetwork(1);
        final IEnergyStorage mouth = CreateAdditionFeed.mouth(new Their(n, 5000, 5000));
        long given = 0;
        long taken = 0;
        int last = 0;
        for (int tick = 0; tick < 60; tick++) {
            n.tick();
            given += pullConnectorOnCreative(n, 5000);

            last = mouth.extractEnergy(8192, false);
            taken += last;
            assertEquals(given, taken + n.inFlight(), "FE made or lost in their buffer at tick " + tick);
        }
        assertEquals(5000, last, "the import never reached the connector's rating");
        assertTrue(taken > 250_000, "moved " + taken);
    }

    @Test
    @DisplayName("an import registers demand only on the real draw, never on a simulate")
    void simulatesRegisterNoDemand() {
        final TheirNetwork n = new TheirNetwork(1);
        final IEnergyStorage mouth = CreateAdditionFeed.mouth(new Their(n, 5000, 5000));
        for (int i = 0; i < 100; i++) {
            mouth.extractEnergy(8192, true);
        }
        assertEquals(0, n.demanded);
        mouth.extractEnergy(8192, false);
        assertEquals(5000, n.demanded, "the real draw asks for the connector's rating, not the Coupler's");
    }

    @Test
    @DisplayName("without demand their buffer holds ten FE a node, which is what a draw that skipped it would get")
    void demandIsWhatSizesTheirBuffer() {
        final TheirNetwork n = new TheirNetwork(1);
        for (int tick = 0; tick < 20; tick++) {
            n.tick();
            pullConnectorOnCreative(n, 5000);
            n.pull(8192, false);
        }
        assertEquals(10, n.pull(8192, true) + n.inBuff);
    }

    @Test
    @DisplayName("the export: a PUSH Coupler wired to the Push connector on their machine")
    void exportsThroughTheirPushConnector() {
        final TheirNetwork n = new TheirNetwork(1);
        final IEnergyStorage mouth = CreateAdditionFeed.mouth(new Their(n, 5000, 5000));
        long gave = 0;
        long got = 0;
        int lastGot = 0;
        for (int tick = 0; tick < 60; tick++) {
            n.tick();

            final int wanted = mouth.receiveEnergy(8192, true);
            final int moved = mouth.receiveEnergy(wanted, false);
            assertEquals(wanted, moved, "their buffer took less than it promised");
            gave += moved;
            lastGot = pushConnectorOn(n, 5000, 3000);
            got += lastGot;
            assertEquals(gave, got + n.inFlight(), "FE made or lost in their buffer at tick " + tick);
        }
        assertEquals(3000, lastGot, "their machine is not getting what it asks for");
    }

    @Test
    @DisplayName("held to the far connector's own limits both ways")
    void heldToTheirLimits() {
        final TheirNetwork n = new TheirNetwork(1);
        n.outBuff = 50_000;
        n.outDemand = 50_000;
        final IEnergyStorage small = CreateAdditionFeed.mouth(new Their(n, 700, 1000));
        assertEquals(1000, small.extractEnergy(8192, false));
        assertEquals(700, small.receiveEnergy(8192, true));
        assertEquals(700, small.receiveEnergy(8192, false));
    }

    @Test
    @DisplayName("no network, nothing either way, and never read as storage")
    void noNetworkNothing() {
        final IEnergyStorage lone = CreateAdditionFeed.mouth(new Their(null, 5000, 5000));
        assertEquals(0, lone.extractEnergy(8192, false));
        assertEquals(0, lone.receiveEnergy(8192, false));
        assertFalse(lone.canExtract());
        assertFalse(lone.canReceive());
        final TheirNetwork n = new TheirNetwork(3);
        n.outBuff = 9000;
        final IEnergyStorage live = CreateAdditionFeed.mouth(new Their(n, 5000, 5000));
        assertTrue(live.canExtract() && live.canReceive());
        assertEquals(0, live.getEnergyStored(), "a whole network's FE in flight read as a battery");
        assertEquals(0, live.getMaxEnergyStored());
    }
}
