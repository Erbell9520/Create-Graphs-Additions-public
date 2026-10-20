package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

public record FeNodeRole(Kind kind, boolean givesNow, boolean takesNow, boolean isReserve) {
    public enum Kind {
        NONE,

        GENERATOR,

        LOAD,

        STORAGE
    }

    private static final FeNodeRole NONE_ROLE = new FeNodeRole(Kind.NONE, false, false, false);
    private static final FeNodeRole SOURCE_ROLE = new FeNodeRole(Kind.GENERATOR, true, false, false);
    private static final FeNodeRole SINK_ROLE = new FeNodeRole(Kind.LOAD, false, true, false);

    public static FeNodeRole none() {
        return NONE_ROLE;
    }

    public static FeNodeRole of(FeGridNode node) {
        return of(node.attachedStorage(), node.transferRate());
    }

    public static FeNodeRole of(IEnergyStorage handler, int rate) {
        if (handler == null) {
            return NONE_ROLE;
        }
        final boolean claimsTake = handler.canReceive();
        final boolean claimsGive = handler.canExtract();

        if (FeForeignHandlers.isKnownStorage(handler)) {
            final int probeRate = Math.max(1, rate);
            return new FeNodeRole(Kind.STORAGE,
                    claimsGive && handler.extractEnergy(probeRate, true) > 0,
                    claimsTake && handler.receiveEnergy(probeRate, true) > 0,
                    true);
        }
        if (!claimsTake) {
            return claimsGive ? SOURCE_ROLE : NONE_ROLE;
        }
        if (!claimsGive) {
            return SINK_ROLE;
        }

        final int probe = Math.max(1, rate);
        final boolean gives = handler.extractEnergy(probe, true) > 0;
        final boolean takes = handler.receiveEnergy(probe, true) > 0;
        if (FeForeignHandlers.isNetworkMouth(handler)) {
            if (gives) {
                return SOURCE_ROLE;
            }
            return takes ? SINK_ROLE : NONE_ROLE;
        }
        if (!gives && !takes) {
            return new FeNodeRole(Kind.STORAGE, false, false, true);
        }
        return new FeNodeRole(Kind.STORAGE, gives, takes, true);
    }
}
