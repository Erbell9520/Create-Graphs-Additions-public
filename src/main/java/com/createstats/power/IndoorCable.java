package com.createstats.power;

public final class IndoorCable {
    private IndoorCable() {
    }

    public static final float MULTIPLIER = 0.125f;

    public static final int SEGMENT_BUFFER = 8;

    public static final float HALF_PX = 0.5f;

    public static final float STANDOFF = 1.5f / 16.0f;

    public static final float COLLAR_HALF_PX = 1.75f;

    public static final float COLLAR_LENGTH_PX = 1.5f;

    public static final int MAX_BORE = 8;

    public static int rate(int baseRate) {
        return Math.round(Math.max(0, baseRate) * MULTIPLIER);
    }

    @org.jetbrains.annotations.Nullable
    public static net.neoforged.neoforge.energy.IEnergyStorage energyAt(
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            @org.jetbrains.annotations.Nullable net.minecraft.core.Direction fromSide) {
        if (level == null || !level.hasChunkAt(pos)) {
            return null;
        }
        final net.neoforged.neoforge.energy.IEnergyStorage sided = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, fromSide);
        if (sided != null) {
            return sided;
        }

        if (level.getBlockEntity(pos) instanceof com.createstats.blockentity.PowerMeterBE
                || level.getBlockEntity(pos) instanceof com.createstats.blockentity.PowerLimiterBE
                || level.getBlockEntity(pos) instanceof com.createstats.blockentity.UniversalPowerCouplerBE
                || level.getBlockEntity(pos) instanceof com.createstats.blockentity.StarterCapacitorBE) {
            return null;
        }
        return level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, null);
    }
}
