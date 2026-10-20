package com.createstats.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class StageLightMk2BE extends StageLightBE {
    public static final int MK2_PEAK_RANGE = 64;
    public static final int MK2_MAX_RANGE = 128;
    public static final int MK2_TRACKING_RANGE = 160;

    public static final int BEACON_PEAK_RANGE = 128;
    public static final int BEACON_MAX_RANGE = 256;
    public static final int BEACON_TRACKING_RANGE = 256;

    private boolean beaconUpgrade;

    public StageLightMk2BE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int getPeakRange() {
        return this.beaconUpgrade ? BEACON_PEAK_RANGE : MK2_PEAK_RANGE;
    }

    @Override
    public int getMaxRange() {
        return this.beaconUpgrade ? BEACON_MAX_RANGE : MK2_MAX_RANGE;
    }

    @Override
    public int getTrackingRange() {
        return this.beaconUpgrade ? BEACON_TRACKING_RANGE : MK2_TRACKING_RANGE;
    }

    public boolean hasBeaconUpgrade() {
        return this.beaconUpgrade;
    }

    public void setBeaconUpgrade(boolean upgraded) {
        if (this.beaconUpgrade == upgraded) {
            return;
        }
        this.beaconUpgrade = upgraded;

        this.applyConfig(this.getPan(), this.getTilt(), this.getRange(), this.getBrightness(),
                this.getFocus(), this.getTint(), this.isBeamVisible(), this.isVolumetric(),
                this.getBeamWidth(), this.getLightSpread(), this.getEffect(), this.getEffectSpeed());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("BeaconUpgrade", this.beaconUpgrade);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        this.beaconUpgrade = tag.getBoolean("BeaconUpgrade");
        super.read(tag, registries, clientPacket);
    }
}
