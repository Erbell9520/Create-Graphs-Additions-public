package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * One machine hanging off a piece of wiring, as a node the grid can serve.
 *
 * a machine needs a node of its own because wiring used to reach machines through
 * {@code attachedStorage()}, which returns <b>one</b> handler per node - so cable crossing a
 * machine and passing a lamp powered whichever it happened to look at first, and the other just
 * never got served. not a rule a player can see or predict, just reads as "cable works sometimes".
 *
 * instead of teaching every node to return a list and rewriting the solve around that, the flood
 * emits one of these per machine it touches. solver's untouched - it just sees an ordinary node
 * whose attached storage happens to be that machine.
 *
 * no buffer of its own, deliberately - capacity and stored energy are zero. this is a service
 * point, not a piece of wire. if it reported the machine's buffer, the pooled solve would count
 * that buffer once as grid storage and again as a sink to fill, and energy would appear from
 * nowhere.
 *
 * @param machine  where the machine is, which is also this node's identity, so two runs touching the
 *                 same machine cannot serve it twice in one tick
 * @param storage  the machine's handler
 * @param rate     the carrying wire's throughput, since that is what actually limits delivery
 */
public record FeAttachment(BlockPos machine, IEnergyStorage storage, int rate) implements FeGridMember {

    @Override
    public long nodeKey() {
        return machine.asLong();
    }

    @Override
    public int storedEnergy() {
        return 0;
    }

    @Override
    public void setStoredEnergy(int amount) {
        // Nothing to store: the machine's own buffer is behind attachedStorage().
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    @Override
    public int transferRate() {
        return rate;
    }

    @Override
    @Nullable
    public IEnergyStorage attachedStorage() {
        return storage;
    }

    /** [1] */
    @Override
    public boolean isGone(Level owner) {
        return false;
    }

    @Override
    public void setLastFlow(int flow) {
        // Nothing to show: the goggle readout belongs to the wire, not to this bookkeeping node.
    }

    @Override
    public void invalidateNetwork() {
        // A transient attachment owns no network; it is rebuilt from scratch on every flood.
    }
}
