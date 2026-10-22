package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public final class CreateAdditionFeed {
    private CreateAdditionFeed() {
    }

    public interface Network {
        int pull(int energy, boolean simulate);

        int push(int energy, boolean simulate);

        void demand(int energy);
    }

    public interface Connector {
        @Nullable
        Network network();

        int maxIn();

        int maxOut();
    }

    public static IEnergyStorage mouth(Connector connector) {
        return new Mouth(connector);
    }

    record Mouth(Connector connector) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final Network network = this.connector.network();
            final int ask = Math.min(maxReceive, this.connector.maxIn());
            if (network == null || ask <= 0) {
                return 0;
            }
            return Math.max(0, Math.min(ask, network.push(ask, simulate)));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final Network network = this.connector.network();
            final int ask = Math.min(maxExtract, this.connector.maxOut());
            if (network == null || ask <= 0) {
                return 0;
            }
            if (!simulate) {
                network.demand(ask);
            }
            return Math.max(0, Math.min(ask, network.pull(ask, simulate)));
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return this.connector.maxOut() > 0 && this.connector.network() != null;
        }

        @Override
        public boolean canReceive() {
            return this.connector.maxIn() > 0 && this.connector.network() != null;
        }
    }
}
