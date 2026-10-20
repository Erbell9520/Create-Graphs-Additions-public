package com.createstats.power;

// create gives contraptions items and fluids and nothing for energy, so it lives in the banks' saved data
public final class TrainBankLedger {
    private final int[] energies;
    private final int[] capacities;

    public TrainBankLedger(int[] energies, int[] capacities) {
        if (energies.length != capacities.length) {
            throw new IllegalArgumentException("cells need one capacity each");
        }
        this.energies = new int[energies.length];
        this.capacities = new int[capacities.length];
        for (int i = 0; i < energies.length; i++) {
            this.energies[i] = Math.max(0, energies[i]);
            this.capacities[i] = Math.max(0, capacities[i]);
        }
    }

    public int cells() {
        return energies.length;
    }

    public int energyAt(int cell) {
        return energies[cell];
    }

    public int stored() {
        int total = 0;
        for (int e : energies) {
            total = FeRates.plus(total, e);
        }
        return total;
    }

    public int capacity() {
        int total = 0;
        for (int i = 0; i < energies.length; i++) {
            total = FeRates.plus(total, Math.max(capacities[i], energies[i]));
        }
        return total;
    }

    public int room() {
        long room = 0L;
        for (int i = 0; i < energies.length; i++) {
            room += Math.max(0, capacities[i] - energies[i]);
        }
        return (int) Math.min(Integer.MAX_VALUE, room);
    }

    public int fill(int amount) {
        return FeStackFill.fillBottomUp(energies, capacities, amount);
    }

    public int drain(int amount) {
        return FeStackFill.drainTopDown(energies, amount);
    }

    public double fraction() {
        final int capacity = capacity();
        return capacity <= 0 ? 0.0 : Math.min(1.0, (double) stored() / capacity);
    }

    public static int comparatorLevel(long stored, long capacity) {
        if (capacity <= 0) {
            return 0;
        }
        final double f = Math.max(0.0, Math.min(1.0, (double) stored / capacity));
        return 1 + (int) Math.floor(f * 14.0);
    }
}
