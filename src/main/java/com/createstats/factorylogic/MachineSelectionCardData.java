package com.createstats.factorylogic;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public class MachineSelectionCardData {
    public static final int MAX_AREA_BLOCKS = 4096;
    public static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 10;
    private static final int MIN_IDLE_TIMEOUT_SECONDS = 3;
    private static final int MAX_IDLE_TIMEOUT_SECONDS = 600;

    @Nullable
    private ResourceLocation machineBlockId;
    @Nullable
    private ResourceLocation dimensionId;
    @Nullable
    private BlockPos firstCorner;
    @Nullable
    private BlockPos secondCorner;
    private int idleTimeoutSeconds;

    public MachineSelectionCardData() {
        this.idleTimeoutSeconds = DEFAULT_IDLE_TIMEOUT_SECONDS;
    }

    @Nullable
    public ResourceLocation getMachineBlockId() {
        return this.machineBlockId;
    }

    public void setMachineBlockId(@Nullable ResourceLocation machineBlockId) {
        this.machineBlockId = machineBlockId;
    }

    @Nullable
    public ResourceLocation getDimensionId() {
        return this.dimensionId;
    }

    public void setDimensionId(@Nullable ResourceLocation dimensionId) {
        this.dimensionId = dimensionId;
    }

    @Nullable
    public BlockPos getFirstCorner() {
        return this.firstCorner;
    }

    public void setFirstCorner(@Nullable BlockPos firstCorner) {
        this.firstCorner = firstCorner;
    }

    public void startAreaSelection(BlockPos firstCorner, @Nullable ResourceLocation dimensionId) {
        this.firstCorner = firstCorner;
        this.secondCorner = null;
        this.dimensionId = dimensionId;
    }

    @Nullable
    public BlockPos getSecondCorner() {
        return this.secondCorner;
    }

    public void setSecondCorner(@Nullable BlockPos secondCorner) {
        this.secondCorner = secondCorner;
    }

    public int getIdleTimeoutSeconds() {
        return this.idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = clampTimeout(idleTimeoutSeconds);
    }

    public void adjustIdleTimeoutSeconds(int delta) {
        this.setIdleTimeoutSeconds(this.idleTimeoutSeconds + delta);
    }

    public boolean hasMachineType() {
        return this.machineBlockId != null;
    }

    public boolean hasArea() {
        return this.firstCorner != null && this.secondCorner != null;
    }

    public boolean isComplete() {
        return this.hasMachineType() && this.hasArea();
    }

    public boolean hasDimensionMismatch(@Nullable ResourceLocation dimensionId) {
        return this.firstCorner != null && this.dimensionId != null && !this.dimensionId.equals(dimensionId);
    }

    public void clearArea() {
        this.firstCorner = null;
        this.secondCorner = null;
        this.dimensionId = null;
    }

    public void clearAll() {
        this.machineBlockId = null;
        this.clearArea();
        this.idleTimeoutSeconds = DEFAULT_IDLE_TIMEOUT_SECONDS;
    }

    @Nullable
    public BlockPos getMinCorner() {
        return this.getMinCorner(null);
    }

    @Nullable
    public BlockPos getMinCorner(@Nullable BlockPos otherCorner) {
        BlockPos resolvedSecondCorner = this.resolveSecondCorner(otherCorner);
        if (this.firstCorner == null || resolvedSecondCorner == null) {
            return null;
        }
        return new BlockPos(
                Math.min(this.firstCorner.getX(), resolvedSecondCorner.getX()),
                Math.min(this.firstCorner.getY(), resolvedSecondCorner.getY()),
                Math.min(this.firstCorner.getZ(), resolvedSecondCorner.getZ()));
    }

    @Nullable
    public BlockPos getMaxCorner() {
        return this.getMaxCorner(null);
    }

    @Nullable
    public BlockPos getMaxCorner(@Nullable BlockPos otherCorner) {
        BlockPos resolvedSecondCorner = this.resolveSecondCorner(otherCorner);
        if (this.firstCorner == null || resolvedSecondCorner == null) {
            return null;
        }
        return new BlockPos(
                Math.max(this.firstCorner.getX(), resolvedSecondCorner.getX()),
                Math.max(this.firstCorner.getY(), resolvedSecondCorner.getY()),
                Math.max(this.firstCorner.getZ(), resolvedSecondCorner.getZ()));
    }

    public int getAreaBlockCount() {
        return this.getAreaBlockCount(null);
    }

    public int getAreaBlockCount(@Nullable BlockPos otherCorner) {
        BlockPos min = this.getMinCorner(otherCorner);
        BlockPos max = this.getMaxCorner(otherCorner);
        if (min == null || max == null) {
            return 0;
        }
        long width = max.getX() - min.getX() + 1L;
        long height = max.getY() - min.getY() + 1L;
        long depth = max.getZ() - min.getZ() + 1L;
        long blockCount = width * height * depth;
        if (blockCount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) blockCount;
    }

    public String getAreaSizeLabel() {
        return this.getAreaSizeLabel(null);
    }

    public String getAreaSizeLabel(@Nullable BlockPos otherCorner) {
        BlockPos min = this.getMinCorner(otherCorner);
        BlockPos max = this.getMaxCorner(otherCorner);
        if (min == null || max == null) {
            return "Unset";
        }
        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int depth = max.getZ() - min.getZ() + 1;
        return width + "x" + height + "x" + depth;
    }

    public boolean isAreaTooLarge(int maxBlocks) {
        return this.isAreaTooLarge(maxBlocks, null);
    }

    public boolean isAreaTooLarge(int maxBlocks, @Nullable BlockPos otherCorner) {
        return this.getAreaBlockCount(otherCorner) > maxBlocks;
    }

    @Nullable
    public AABB getSelectionBounds() {
        return this.getSelectionBounds(null);
    }

    @Nullable
    public AABB getSelectionBounds(@Nullable BlockPos otherCorner) {
        BlockPos min = this.getMinCorner(otherCorner);
        BlockPos max = this.getMaxCorner(otherCorner);
        if (min == null || max == null) {
            return null;
        }
        return new AABB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1D, max.getY() + 1D, max.getZ() + 1D);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (this.machineBlockId != null) {
            tag.putString("MachineBlockId", this.machineBlockId.toString());
        }
        if (this.dimensionId != null) {
            tag.putString("DimensionId", this.dimensionId.toString());
        }
        if (this.firstCorner != null) {
            tag.putLong("FirstCorner", this.firstCorner.asLong());
        }
        if (this.secondCorner != null) {
            tag.putLong("SecondCorner", this.secondCorner.asLong());
        }
        tag.putInt("IdleTimeoutSeconds", this.idleTimeoutSeconds);
        return tag;
    }

    public static MachineSelectionCardData load(CompoundTag tag) {
        MachineSelectionCardData data = new MachineSelectionCardData();
        if (tag.contains("MachineBlockId")) {
            data.machineBlockId = ResourceLocation.tryParse(tag.getString("MachineBlockId"));
        }
        if (tag.contains("DimensionId")) {
            data.dimensionId = ResourceLocation.tryParse(tag.getString("DimensionId"));
        }
        if (tag.contains("FirstCorner")) {
            data.firstCorner = BlockPos.of(tag.getLong("FirstCorner"));
        }
        if (tag.contains("SecondCorner")) {
            data.secondCorner = BlockPos.of(tag.getLong("SecondCorner"));
        }
        if (tag.contains("IdleTimeoutSeconds")) {
            data.idleTimeoutSeconds = clampTimeout(tag.getInt("IdleTimeoutSeconds"));
        }
        if (data.firstCorner == null) {
            data.secondCorner = null;
            data.dimensionId = null;
        }
        return data;
    }

    @Nullable
    private BlockPos resolveSecondCorner(@Nullable BlockPos otherCorner) {
        return otherCorner != null ? otherCorner : this.secondCorner;
    }

    private static int clampTimeout(int seconds) {
        return Math.max(MIN_IDLE_TIMEOUT_SECONDS, Math.min(MAX_IDLE_TIMEOUT_SECONDS, seconds));
    }
}
