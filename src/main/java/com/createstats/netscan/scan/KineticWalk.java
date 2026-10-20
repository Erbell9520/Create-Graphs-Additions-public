package com.createstats.netscan.scan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;

public final class KineticWalk {
    public interface NeighbourSource {
        ScanNode probe(BlockPos pos);

        List<BlockPos> neighbours(BlockPos pos);

        float ratio(BlockPos from, BlockPos to);

        default boolean unloaded(BlockPos pos) {
            return false;
        }
    }

    private final BlockPos seed;
    private final int budget;

    private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
    private final Set<BlockPos> queued = new HashSet<>();
    private final Map<BlockPos, ScanNode> visited = new LinkedHashMap<>();
    private final List<ScanEdge> edges = new ArrayList<>();
    private final Set<ScanEdge> seenEdges = new HashSet<>();
    private final Set<BlockPos> boundary = new LinkedHashSet<>();

    private boolean started;
    private boolean truncated;
    private boolean deadSeed;

    public KineticWalk(BlockPos seed, int budget) {
        this.seed = seed == null ? null : seed.immutable();

        this.budget = Math.max(1, Math.min(4096, budget));
    }

    public BlockPos seed() {
        return this.seed;
    }

    public int budget() {
        return this.budget;
    }

    public int visitedCount() {
        return this.visited.size();
    }

    public boolean done() {
        return this.started && (this.frontier.isEmpty() || this.visited.size() >= this.budget);
    }

    public boolean truncated() {
        return this.truncated;
    }

    public boolean deadSeed() {
        return this.deadSeed;
    }

    public boolean step(int elements, NeighbourSource src) {
        if (this.seed == null || src == null) {
            this.started = true;
            this.deadSeed = true;
            return false;
        }
        if (!this.started) {
            this.started = true;
            this.frontier.add(this.seed);
            this.queued.add(this.seed);
        }

        int remaining = Math.max(1, elements);
        while (remaining-- > 0 && !this.frontier.isEmpty()) {
            if (this.visited.size() >= this.budget) {
                this.truncated = true;
                this.frontier.clear();
                this.queued.clear();
                return false;
            }
            final BlockPos pos = this.frontier.poll();
            final ScanNode node = src.probe(pos);
            if (node == null) {
                if (pos.equals(this.seed)) {
                    this.deadSeed = true;
                }
                continue;
            }
            this.visited.put(pos, node);

            for (final BlockPos next : src.neighbours(pos)) {
                if (next == null) {
                    continue;
                }
                final BlockPos immutable = next.immutable();

                addEdge(pos, immutable, src.ratio(pos, immutable));
                if (this.visited.containsKey(immutable) || this.queued.contains(immutable)) {
                    continue;
                }
                if (src.unloaded(immutable)) {
                    this.boundary.add(immutable);
                    continue;
                }
                this.frontier.add(immutable);
                this.queued.add(immutable);
            }
        }

        if (this.frontier.isEmpty()) {
            return false;
        }
        if (this.visited.size() >= this.budget) {
            this.truncated = true;
            this.frontier.clear();
            this.queued.clear();
            return false;
        }
        return true;
    }

    private void addEdge(BlockPos from, BlockPos to, float ratio) {
        final ScanEdge forward = new ScanEdge(from, to, ratio);
        final ScanEdge backward = new ScanEdge(to, from, ratio);
        if (this.seenEdges.contains(forward) || this.seenEdges.contains(backward)) {
            return;
        }
        this.seenEdges.add(forward);
        this.edges.add(forward);
    }

    public ScanGraph build(long networkId, long tick) {
        final List<ScanEdge> kept = new ArrayList<>(this.edges.size());
        for (final ScanEdge edge : this.edges) {
            if (this.visited.containsKey(edge.from()) && this.visited.containsKey(edge.to())) {
                kept.add(edge);
            }
        }
        return new ScanGraph(this.visited, kept, networkId, tick, this.truncated, this.boundary);
    }
}
