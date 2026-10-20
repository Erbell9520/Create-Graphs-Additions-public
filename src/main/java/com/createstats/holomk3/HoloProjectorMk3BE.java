package com.createstats.holomk3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.createstats.blockentity.HolographicProjectorBE;

public class HoloProjectorMk3BE extends HolographicProjectorBE {
    private HoloMk3Settings mk3 = new HoloMk3Settings();

    public HoloProjectorMk3BE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public HoloMk3Settings mk3Settings() {
        return this.mk3;
    }

    public void setMk3Settings(HoloMk3Settings settings) {
        this.mk3 = settings == null ? new HoloMk3Settings() : settings;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        final float half = Math.max(this.mk3.getVolumeWidth(), this.mk3.getVolumeDepth()) * 0.5f + 1.5f;
        final double top = this.mk3.getLift() + this.mk3.getVolumeHeight() + 1.5f;
        return new AABB(this.worldPosition)
                .expandTowards(0.0, top, 0.0)
                .inflate(half);
    }

    @Override
    public String settingsKey() {
        return "create_stats:holo_projector_mk3";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSettings(tag, registries);
        tag.put("HoloMk3", this.mk3.save());
    }

    @Override
    protected void applyProjectorSettings(CompoundTag tag) {
        if (tag.contains("HoloMk3")) {
            this.setMk3Settings(HoloMk3Settings.load(tag.getCompound("HoloMk3")));
        }
        super.applyProjectorSettings(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("HoloMk3", this.mk3.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("HoloMk3")) {
            this.mk3 = HoloMk3Settings.load(tag.getCompound("HoloMk3"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.put("HoloMk3", this.mk3.save());
        return tag;
    }
}
