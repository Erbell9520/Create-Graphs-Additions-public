package com.createstats.topology;

import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import java.util.Map;

public class NetworkGraph
{
    private final Map<BlockPos, TopologyNode> nodes;
    private final List<TopologyEdge> edges;
    private final long networkId;
    private final float totalStress;
    private final float totalCapacity;
    private final boolean isOverStressed;
    private final int sourceCount;
    private final int explicitSourceCount;
    private final long discoveredAtTick;
    private final int contentHash;
    private final Set<BlockPos> unloadedBoundary;
    private final boolean truncated;
    public static final NetworkGraph EMPTY;

    public NetworkGraph(Map<BlockPos, TopologyNode> nodes, List<TopologyEdge> edges, long networkId, long discoveredAtTick) {
        this(nodes, edges, networkId, discoveredAtTick, Collections.emptySet(), false);
    }

    public NetworkGraph(Map<BlockPos, TopologyNode> nodes, List<TopologyEdge> edges, long networkId, long discoveredAtTick, Set<BlockPos> unloadedBoundary) {
        this(nodes, edges, networkId, discoveredAtTick, unloadedBoundary, false);
    }

    public NetworkGraph(Map<BlockPos, TopologyNode> nodes, List<TopologyEdge> edges, long networkId, long discoveredAtTick, Set<BlockPos> unloadedBoundary, boolean truncated) {
        this.nodes = Collections.unmodifiableMap((Map<? extends BlockPos, ? extends TopologyNode>)new LinkedHashMap<BlockPos, TopologyNode>(nodes));
        this.edges = Collections.unmodifiableList((List<? extends TopologyEdge>)new ArrayList<TopologyEdge>(edges));
        this.networkId = networkId;
        this.discoveredAtTick = discoveredAtTick;
        this.unloadedBoundary = Collections.unmodifiableSet((Set<? extends BlockPos>)new HashSet<BlockPos>(unloadedBoundary));
        this.truncated = truncated;
        float stress = 0.0f;
        float capacity = 0.0f;
        boolean overstressed = false;
        int sources = 0;
        int explicitSources = 0;
        for (TopologyNode node : nodes.values()) {
            stress += node.getStressImpact();
            capacity += node.getCapacityProvided();
            if (node.isSourceLike()) {
                ++sources;
            }
            if (node.isSource()) {
                ++explicitSources;
            }
            if (node.isOverStressed()) {
                overstressed = true;
            }
        }
        this.totalStress = stress;
        this.totalCapacity = capacity;
        this.isOverStressed = overstressed;
        this.sourceCount = sources;
        this.explicitSourceCount = explicitSources;
        this.contentHash = computeHash(nodes, edges);
    }

    private static int computeHash(Map<BlockPos, TopologyNode> nodes, List<TopologyEdge> edges) {
        int h = nodes.size() * 31;
        h = h * 31 + edges.size();
        for (TopologyEdge e : edges) {
            h = h * 31 + (int)(e.getFrom().asLong() ^ e.getTo().asLong());
            h = h * 31 + Float.floatToIntBits(e.getSpeedRatio());
        }
        for (TopologyNode node : nodes.values()) {
            h = h * 31 + (int)node.getPos().asLong();
            h = h * 31 + node.getType().ordinal();
            h = h * 31 + (node.isSource() ? 1 : 0);
            h = h * 31 + (node.isRootLike() ? 1 : 0);
            h = h * 31 + ((node.getUpstreamPos() != null) ? (int)node.getUpstreamPos().asLong() : 0);
        }
        return h;
    }

    public Map<BlockPos, TopologyNode> getNodes() {
        return this.nodes;
    }

    public List<TopologyEdge> getEdges() {
        return this.edges;
    }

    public long getNetworkId() {
        return this.networkId;
    }

    public float getTotalStress() {
        return this.totalStress;
    }

    public float getTotalCapacity() {
        return this.totalCapacity;
    }

    public boolean isOverStressed() {
        return this.isOverStressed;
    }

    public int getSourceCount() {
        return this.sourceCount;
    }

    public boolean hasExplicitSource() {
        return this.explicitSourceCount > 0;
    }

    public boolean hasMeasuredCapacity() {
        return this.totalCapacity > 0.01f;
    }

    public long getDiscoveredAtTick() {
        return this.discoveredAtTick;
    }

    public int getContentHash() {
        return this.contentHash;
    }

    public Set<BlockPos> getUnloadedBoundary() {
        return this.unloadedBoundary;
    }

    public boolean isTruncated() {
        return this.truncated;
    }

    public boolean isEmpty() {
        return this.nodes.isEmpty();
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("NetworkId", this.networkId);
        tag.putLong("DiscoveredAtTick", this.discoveredAtTick);
        final ListTag nodeList = new ListTag();
        for (TopologyNode node : this.nodes.values()) {
            nodeList.add(node.save());
        }
        tag.put("Nodes", (Tag)nodeList);
        final ListTag edgeList = new ListTag();
        for (TopologyEdge edge : this.edges) {
            edgeList.add(edge.save());
        }
        tag.put("Edges", (Tag)edgeList);
        final ListTag boundaryList = new ListTag();
        for (BlockPos bp : this.unloadedBoundary) {
            final CompoundTag bpTag = new CompoundTag();
            bpTag.putLong("Pos", bp.asLong());
            boundaryList.add(bpTag);
        }
        tag.put("UnloadedBoundary", (Tag)boundaryList);
        tag.putBoolean("Truncated", this.truncated);
        return tag;
    }

    public static NetworkGraph load(CompoundTag tag) {
        final long networkId = tag.getLong("NetworkId");
        final long discoveredAtTick = tag.getLong("DiscoveredAtTick");
        final Map<BlockPos, TopologyNode> nodes = new LinkedHashMap<BlockPos, TopologyNode>();
        final ListTag nodeList = tag.getList("Nodes", 10);
        for (int i = 0; i < nodeList.size(); ++i) {
            final TopologyNode node = TopologyNode.load(nodeList.getCompound(i));
            nodes.put(node.getPos(), node);
        }
        final List<TopologyEdge> edges = new ArrayList<TopologyEdge>();
        final ListTag edgeList = tag.getList("Edges", 10);
        for (int j = 0; j < edgeList.size(); ++j) {
            edges.add(TopologyEdge.load(edgeList.getCompound(j)));
        }
        final Set<BlockPos> boundary = new HashSet<BlockPos>();
        final ListTag boundaryList = tag.getList("UnloadedBoundary", 10);
        for (int k = 0; k < boundaryList.size(); ++k) {
            boundary.add(BlockPos.of(boundaryList.getCompound(k).getLong("Pos")));
        }
        return new NetworkGraph(nodes, edges, networkId, discoveredAtTick, boundary, tag.getBoolean("Truncated"));
    }

    static {
        EMPTY = new NetworkGraph(Collections.emptyMap(), Collections.emptyList(), 0L, 0L);
    }
}
