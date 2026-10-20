package com.createstats.battle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DamageObserverVoxelMap
{
    public final BlockPos observerPos;
    public final BlockPos minCorner;
    public final int dx;
    public final int dy;
    public final int dz;
    public final BlockState[] palette;
    public final int[] paletteIndices;
    public final BitSet baselineMask;
    public final long capturedTick;
    public final String observerName;
    public final String observerCategory;

    public BlockState[] cannonPalette;
    public int[] cannonIndices;
    public long[] cannonPositions;

    public DamageObserverVoxelMap(BlockPos observerPos, BlockPos minCorner,
                                  int dx, int dy, int dz,
                                  BlockState[] palette, int[] paletteIndices,
                                  BitSet baselineMask, long capturedTick,
                                  String observerName, String observerCategory)
    {
        this.observerPos = observerPos;
        this.minCorner = minCorner;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.palette = palette;
        this.paletteIndices = paletteIndices;
        this.baselineMask = baselineMask;
        this.capturedTick = capturedTick;
        this.observerName = observerName == null ? "" : observerName;
        this.observerCategory = observerCategory == null ? "" : observerCategory;
    }

    public int volume() {
        return this.dx * this.dy * this.dz;
    }

    public int indexFor(int lx, int ly, int lz) {
        return lx + this.dx * (ly + this.dy * lz);
    }

    public int presentCount() {
        int c = 0;
        for (int idx : this.paletteIndices) {
            if (idx > 0) c++;
        }
        return c;
    }

    public int missingCount() {
        int c = 0;
        int n = this.paletteIndices.length;
        for (int i = 0; i < n; i++) {
            if (this.baselineMask.get(i) && this.paletteIndices[i] == 0) c++;
        }
        return c;
    }

    public int newCount() {
        int c = 0;
        int n = this.paletteIndices.length;
        for (int i = 0; i < n; i++) {
            if (!this.baselineMask.get(i) && this.paletteIndices[i] > 0) c++;
        }
        return c;
    }

    public boolean hasCannonModel() {
        return this.cannonPositions != null && this.cannonPositions.length > 0
                && this.cannonIndices != null && this.cannonPalette != null;
    }

    public void setCannonModel(Map<BlockPos, BlockState> model) {
        if (model == null || model.isEmpty()) {
            this.cannonPositions = null;
            this.cannonIndices = null;
            this.cannonPalette = null;
            return;
        }
        int n = model.size();
        long[] positions = new long[n];
        int[] indices = new int[n];
        List<BlockState> pal = new ArrayList<>();
        Map<BlockState, Integer> lookup = new HashMap<>();
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> e : model.entrySet()) {
            positions[i] = e.getKey().asLong();
            BlockState s = e.getValue();
            Integer pi = lookup.get(s);
            if (pi == null) {
                pi = pal.size();
                pal.add(s);
                lookup.put(s, pi);
            }
            indices[i] = pi;
            i++;
        }
        this.cannonPositions = positions;
        this.cannonIndices = indices;
        this.cannonPalette = pal.toArray(new BlockState[0]);
    }

    public CompoundTag toNbt(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", this.observerPos.asLong());
        tag.putLong("Min", this.minCorner.asLong());
        tag.putInt("Dx", this.dx);
        tag.putInt("Dy", this.dy);
        tag.putInt("Dz", this.dz);
        tag.putLong("Tick", this.capturedTick);
        tag.putString("Name", this.observerName);
        tag.putString("Cat", this.observerCategory);

        ListTag paletteList = new ListTag();
        for (int i = 1; i < this.palette.length; i++) {
            paletteList.add(NbtUtils.writeBlockState(this.palette[i]));
        }
        tag.put("Palette", paletteList);

        tag.putIntArray("Idx", this.paletteIndices);
        tag.putLongArray("Baseline", this.baselineMask.toLongArray());

        if (this.hasCannonModel()) {
            CompoundTag cm = new CompoundTag();
            ListTag cannonPaletteList = new ListTag();
            for (BlockState state : this.cannonPalette) {
                cannonPaletteList.add(NbtUtils.writeBlockState(state));
            }
            cm.put("Pal", cannonPaletteList);
            cm.putIntArray("Idx", this.cannonIndices);
            cm.putLongArray("Pos", this.cannonPositions);
            tag.put("CM", cm);
        }
        return tag;
    }

    public static DamageObserverVoxelMap fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        BlockPos observerPos = BlockPos.of(tag.getLong("Pos"));
        BlockPos minCorner = BlockPos.of(tag.getLong("Min"));
        int dx = tag.getInt("Dx");
        int dy = tag.getInt("Dy");
        int dz = tag.getInt("Dz");
        long capturedTick = tag.getLong("Tick");
        String name = tag.getString("Name");
        String cat = tag.getString("Cat");

        ListTag paletteList = tag.getList("Palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[paletteList.size() + 1];
        palette[0] = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int i = 0; i < paletteList.size(); i++) {
            palette[i + 1] = NbtUtils.readBlockState(
                    registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                    paletteList.getCompound(i));
        }

        int[] indices = tag.getIntArray("Idx");
        BitSet baseline = BitSet.valueOf(tag.getLongArray("Baseline"));
        DamageObserverVoxelMap result = new DamageObserverVoxelMap(observerPos, minCorner, dx, dy, dz,
                palette, indices, baseline, capturedTick, name, cat);

        if (tag.contains("CM", Tag.TAG_COMPOUND)) {
            CompoundTag cm = tag.getCompound("CM");
            ListTag cannonPaletteList = cm.getList("Pal", Tag.TAG_COMPOUND);
            BlockState[] cannonPalette = new BlockState[cannonPaletteList.size()];
            for (int i = 0; i < cannonPaletteList.size(); i++) {
                cannonPalette[i] = NbtUtils.readBlockState(
                        registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                        cannonPaletteList.getCompound(i));
            }
            result.cannonPalette = cannonPalette;
            result.cannonIndices = cm.getIntArray("Idx");
            result.cannonPositions = cm.getLongArray("Pos");
        }
        return result;
    }

    public static final class Builder {
        private final BlockPos observerPos;
        private final BlockPos minCorner;
        private final int dx;
        private final int dy;
        private final int dz;
        private final int[] indices;
        private final BitSet baseline;
        private final List<BlockState> palette = new ArrayList<>();
        private final Map<BlockState, Integer> paletteLookup = new HashMap<>();
        private String name = "";
        private String category = "";

        public Builder(BlockPos observerPos, BlockPos minCorner, int dx, int dy, int dz) {
            this.observerPos = observerPos;
            this.minCorner = minCorner;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.indices = new int[dx * dy * dz];
            this.baseline = new BitSet(dx * dy * dz);
            this.palette.add(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }

        public Builder name(String n) { this.name = n == null ? "" : n; return this; }
        public Builder category(String c) { this.category = c == null ? "" : c; return this; }

        public int flatIndex(int lx, int ly, int lz) {
            return lx + this.dx * (ly + this.dy * lz);
        }

        public boolean inBounds(int lx, int ly, int lz) {
            return lx >= 0 && lx < this.dx && ly >= 0 && ly < this.dy && lz >= 0 && lz < this.dz;
        }

        public void markBaseline(int lx, int ly, int lz) {
            if (inBounds(lx, ly, lz)) this.baseline.set(flatIndex(lx, ly, lz));
        }

        public void setBlock(int lx, int ly, int lz, BlockState state) {
            if (!inBounds(lx, ly, lz) || state == null || state.isAir()) return;
            Integer idx = this.paletteLookup.get(state);
            if (idx == null) {
                idx = this.palette.size();
                this.palette.add(state);
                this.paletteLookup.put(state, idx);
            }
            this.indices[flatIndex(lx, ly, lz)] = idx;
        }

        public DamageObserverVoxelMap build(long capturedTick) {
            return new DamageObserverVoxelMap(
                    this.observerPos, this.minCorner,
                    this.dx, this.dy, this.dz,
                    this.palette.toArray(new BlockState[0]),
                    this.indices, this.baseline,
                    capturedTick, this.name, this.category);
        }
    }
}
