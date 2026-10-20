package com.createstats.topology;

import java.util.Collections;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import java.util.Map;

public class CachedTopologyLayout
{
    private final Map<BlockPos, float[]> positions;
    private final int nodeCount;
    private final int layoutHash;
    private final int textureWidth;
    private final int textureHeight;
    private final long computedAtTick;
    private final boolean converged;
    private final TopologyFlowLayout.LayoutResult flowLayout;

    public CachedTopologyLayout(Map<BlockPos, float[]> positions, int nodeCount, int layoutHash, int textureWidth, int textureHeight, long computedAtTick, boolean converged, TopologyFlowLayout.LayoutResult flowLayout) {
        this.positions = Collections.unmodifiableMap((Map<? extends BlockPos, ? extends float[]>)new HashMap<BlockPos, float[]>(positions));
        this.nodeCount = nodeCount;
        this.layoutHash = layoutHash;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.computedAtTick = computedAtTick;
        this.converged = converged;
        this.flowLayout = flowLayout;
    }

    public Map<BlockPos, float[]> getPositions() {
        return this.positions;
    }

    public int getNodeCount() {
        return this.nodeCount;
    }

    public int getLayoutHash() {
        return this.layoutHash;
    }

    public int getTextureWidth() {
        return this.textureWidth;
    }

    public int getTextureHeight() {
        return this.textureHeight;
    }

    public long getComputedAtTick() {
        return this.computedAtTick;
    }

    public boolean isConverged() {
        return this.converged;
    }

    public TopologyFlowLayout.LayoutResult getFlowLayout() {
        return this.flowLayout;
    }

    public boolean isValid(int layoutHash, int nodeCount, int w, int h) {
        return this.layoutHash == layoutHash && this.textureWidth == w && this.textureHeight == h && this.nodeCount == nodeCount;
    }
}
