package com.createstats.power;

/** [1] */
public final class FeStackFill {

    private FeStackFill() {
    }

    /** [2] */
    public static int fillBottomUp(int[] energies, int[] capacities, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int remaining = amount;
        for (int i = 0; i < energies.length && remaining > 0; i++) {
            final int room = capacityAt(capacities, i) - energies[i];
            if (room <= 0) {
                continue;
            }
            final int placed = Math.min(room, remaining);
            energies[i] += placed;
            remaining -= placed;
        }
        return amount - remaining;
    }

    /** Convenience for a stack whose blocks all hold the same amount. */
    public static int fillBottomUp(int[] energies, int perBlockCapacity, int amount) {
        final int[] capacities = new int[energies.length];
        java.util.Arrays.fill(capacities, perBlockCapacity);
        return fillBottomUp(energies, capacities, amount);
    }

    private static int capacityAt(int[] capacities, int index) {
        return index < capacities.length ? Math.max(0, capacities[index]) : 0;
    }

    /**
     * Removes up to {@code amount} FE from the stack, highest block first. Returns how much was
     * actually removed, which is less than {@code amount} only when the stack ran out.
     */
    public static int drainTopDown(int[] energies, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int remaining = amount;
        for (int i = energies.length - 1; i >= 0 && remaining > 0; i--) {
            final int taken = Math.min(energies[i], remaining);
            if (taken <= 0) {
                continue;
            }
            energies[i] -= taken;
            remaining -= taken;
        }
        return amount - remaining;
    }
}
