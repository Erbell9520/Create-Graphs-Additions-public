package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** [1] */
public final class FePowerFx {

    private FePowerFx() {
    }

    /** [2] */
    public static void handleTransition(Level level, BlockPos pos, FeConsumer feConsumer) {
        chargeFromNeighbors(level, pos, feConsumer);
        playTransition(level, pos, feConsumer.pollTransition());
    }

    /** [3] */
    public static void chargeFromNeighbors(Level level, BlockPos pos, FeConsumer feConsumer) {
        if (level == null || level.isClientSide()) {
            return;
        }
        int space = feConsumer.getMaxEnergyStored() - feConsumer.getEnergyStored();
        if (space <= 0) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (space <= 0) {
                break;
            }
            final IEnergyStorage source = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (source == null || !source.canExtract()) {
                continue;
            }
            // [4]
            final int offered = source.extractEnergy(space, true);
            if (offered <= 0) {
                continue;
            }
            final int wanted = feConsumer.receiveEnergy(offered, true);
            if (wanted <= 0) {
                continue;
            }
            final int drawn = source.extractEnergy(wanted, false);
            if (drawn <= 0) {
                continue;
            }
            // [5]
            space -= feConsumer.receiveEnergy(drawn, false);
        }
    }

    /** Variant for owners that already polled the transition themselves. */
    public static void playTransition(Level level, BlockPos pos, int transition) {
        if (transition == 0 || level == null || level.isClientSide()) {
            return;
        }
        if (transition > 0) {
            // Boot: rising beacon activation, pitched up so it reads as electronics, not a beacon.
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.55f, 1.45f);
        } else {
            // Hard cutoff: capacitor discharge. Descending deactivate plus one spark burst.
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.65f, 0.75f);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        12, 0.3, 0.25, 0.3, 0.05);
            }
        }
    }
}
