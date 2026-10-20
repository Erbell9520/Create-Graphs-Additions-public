package com.createstats.netscan.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

// BFS order so parents come first, there's a test for that
public final class ScanGraph {
    public static final ScanGraph EMPTY = new ScanGraph(new LinkedHashMap<>(), List.of(), 0L, 0L,
            false, Set.of());

    private final Map<BlockPos, ScanNode> nodes;
    private final List<ScanEdge> edges;
    private final long networkId;
    private final long scannedAtTick;
    private final boolean truncated;
    private final Set<BlockPos> unloadedBoundary;

    private final int contentHash;
    private final float totalStress;
    private final float totalCapacity;
    private final int sourceCount;
    private final int machineCount;
    private final boolean overStressed;

    public ScanGraph(Map<BlockPos, ScanNode> nodes, List<ScanEdge> edges, long networkId,
                     long scannedAtTick, boolean truncated, Set<BlockPos> unloadedBoundary) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges = List.copyOf(edges);
        this.networkId = networkId;
        this.scannedAtTick = scannedAtTick;
        this.truncated = truncated;
        this.unloadedBoundary = Set.copyOf(unloadedBoundary);

        float stress = 0.0f;
        float capacity = 0.0f;
        int sources = 0;
        int machines = 0;
        boolean over = false;
        for (final ScanNode node : this.nodes.values()) {
            stress += Math.abs(node.stressImpact());
            capacity += node.capacityProvided();
            if (node.isSource()) {
                sources++;
            }
            if (node.kind().isLoad()) {
                machines++;
            }
            over |= node.isOverStressed();
        }
        this.totalStress = stress;
        this.totalCapacity = capacity;
        this.sourceCount = sources;
        this.machineCount = machines;
        this.overStressed = over;
        this.contentHash = computeHash();
    }

    private int computeHash() {
        int h = this.nodes.size() * 31 + this.edges.size();
        for (final ScanNode node : this.nodes.values()) {
            h = h * 31 + node.pos().hashCode();
            h = h * 31 + node.kind().ordinal();
            h = h * 31 + (node.isSource() ? 1 : 0);
            h = h * 31 + Float.floatToIntBits(node.stressCoefficient());
            h = h * 31 + (node.upstream() == null ? 0 : node.upstream().hashCode());
        }
        for (final ScanEdge edge : this.edges) {
            h = h * 31 + (edge.from().hashCode() ^ edge.to().hashCode());
            h = h * 31 + Float.floatToIntBits(edge.speedRatio());
        }
        return h;
    }

    public Map<BlockPos, ScanNode> nodes() {
        return this.nodes;
    }

    public List<ScanEdge> edges() {
        return this.edges;
    }

    public ScanNode node(BlockPos pos) {
        return this.nodes.get(pos);
    }

    public long networkId() {
        return this.networkId;
    }

    public long scannedAtTick() {
        return this.scannedAtTick;
    }

    public boolean truncated() {
        return this.truncated;
    }

    public Set<BlockPos> unloadedBoundary() {
        return this.unloadedBoundary;
    }

    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    public int size() {
        return this.nodes.size();
    }

    public int contentHash() {
        return this.contentHash;
    }

    public float totalStress() {
        return this.totalStress;
    }

    public float totalCapacity() {
        return this.totalCapacity;
    }

    public int sourceCount() {
        return this.sourceCount;
    }

    public int machineCount() {
        return this.machineCount;
    }

    public boolean isOverStressed() {
        return this.overStressed;
    }

    public float loadFraction() {
        return this.totalCapacity <= 0.0f ? (this.totalStress > 0.0f ? 1.0f : 0.0f)
                : Math.max(0.0f, this.totalStress / this.totalCapacity);
    }

    public List<ScanNode> byLoad() {
        final List<ScanNode> ranked = new ArrayList<>(this.nodes.values());
        ranked.sort((a, b) -> Float.compare(b.drawnStress(), a.drawnStress()));
        return ranked;
    }

    public ScanGraph withLive(float[] speeds, float[] stresses, float[] capacities, boolean[] over) {
        if (speeds == null || speeds.length != this.nodes.size()) {
            return this;
        }
        final Map<BlockPos, ScanNode> updated = new LinkedHashMap<>(this.nodes.size());
        int i = 0;
        for (final Map.Entry<BlockPos, ScanNode> entry : this.nodes.entrySet()) {
            updated.put(entry.getKey(), entry.getValue().withLive(speeds[i], stresses[i],
                    capacities[i], over[i]));
            i++;
        }
        return new ScanGraph(updated, this.edges, this.networkId, this.scannedAtTick, this.truncated,
                this.unloadedBoundary);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("NetworkId", this.networkId);
        tag.putLong("Tick", this.scannedAtTick);
        tag.putBoolean("Truncated", this.truncated);
        final ListTag nodeList = new ListTag();
        for (final ScanNode node : this.nodes.values()) {
            nodeList.add(node.save());
        }
        tag.put("Nodes", nodeList);
        final ListTag edgeList = new ListTag();
        for (final ScanEdge edge : this.edges) {
            edgeList.add(edge.save());
        }
        tag.put("Edges", edgeList);
        final long[] boundary = new long[this.unloadedBoundary.size()];
        int i = 0;
        for (final BlockPos pos : this.unloadedBoundary) {
            boundary[i++] = pos.asLong();
        }
        tag.putLongArray("Boundary", boundary);
        return tag;
    }

    public static ScanGraph load(CompoundTag tag) {
        if (tag == null || !tag.contains("Nodes")) {
            return EMPTY;
        }
        final Map<BlockPos, ScanNode> nodes = new LinkedHashMap<>();
        final ListTag nodeList = tag.getList("Nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodeList.size(); i++) {
            final ScanNode node = ScanNode.load(nodeList.getCompound(i));
            nodes.put(node.pos(), node);
        }
        final List<ScanEdge> edges = new ArrayList<>();
        final ListTag edgeList = tag.getList("Edges", Tag.TAG_COMPOUND);
        for (int i = 0; i < edgeList.size(); i++) {
            edges.add(ScanEdge.load(edgeList.getCompound(i)));
        }
        final Set<BlockPos> boundary = new LinkedHashSet<>();
        for (final long packed : tag.getLongArray("Boundary")) {
            boundary.add(BlockPos.of(packed));
        }
        return new ScanGraph(nodes, edges, tag.getLong("NetworkId"), tag.getLong("Tick"),
                tag.getBoolean("Truncated"), boundary);
    }
}
