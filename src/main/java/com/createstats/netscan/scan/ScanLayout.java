package com.createstats.netscan.scan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;

// drivetrains have a direction, a force layout blob says nothing
public final class ScanLayout {
    private static final int BARYCENTRE_SWEEPS = 4;

    private static final float RING_MAX_RADIUS = 0.46f;

    private static final float POWER_MIN_SHARE = 0.06f;

    private ScanLayout() {
    }

    public static final class Result {
        private final ScanLayoutMode mode;
        private final Map<BlockPos, float[]> positions;
        private final Map<BlockPos, Integer> ranks;
        private final Map<BlockPos, BlockPos> parents;
        private final Map<BlockPos, List<BlockPos>> children;
        private final Map<BlockPos, Float> subtreeStress;
        private final Set<Long> primaryEdges;
        private final BlockPos primaryRoot;
        private final int depth;
        private final int hash;
        private final float maxSubtreeStress;
        private final int[] span;

        Result(ScanLayoutMode mode, Map<BlockPos, float[]> positions, Map<BlockPos, Integer> ranks,
               Map<BlockPos, BlockPos> parents, Map<BlockPos, List<BlockPos>> children,
               Map<BlockPos, Float> subtreeStress, Set<Long> primaryEdges, BlockPos primaryRoot,
               int depth, int hash, float maxSubtreeStress, int[] span) {
            this.mode = mode;
            this.positions = Collections.unmodifiableMap(positions);
            this.ranks = Collections.unmodifiableMap(ranks);
            this.parents = Collections.unmodifiableMap(parents);
            this.children = Collections.unmodifiableMap(children);
            this.subtreeStress = Collections.unmodifiableMap(subtreeStress);
            this.primaryEdges = primaryEdges;
            this.primaryRoot = primaryRoot;
            this.depth = depth;
            this.hash = hash;
            this.maxSubtreeStress = maxSubtreeStress;
            this.span = span;
        }

        public ScanLayoutMode mode() {
            return this.mode;
        }

        public Map<BlockPos, float[]> positions() {
            return this.positions;
        }

        public float[] position(BlockPos pos) {
            return this.positions.get(pos);
        }

        public boolean isPrimaryEdge(ScanEdge edge) {
            return this.primaryEdges.contains(key(edge.from(), edge.to()));
        }

        public int rank(BlockPos pos) {
            return this.ranks.getOrDefault(pos, 0);
        }

        public BlockPos primaryRoot() {
            return this.primaryRoot;
        }

        public boolean isPrimaryRoot(BlockPos pos) {
            return pos != null && pos.equals(this.primaryRoot);
        }

        public int depth() {
            return this.depth;
        }

        public int graphHash() {
            return this.hash;
        }

        public BlockPos parent(BlockPos pos) {
            return this.parents.get(pos);
        }

        public List<BlockPos> children(BlockPos pos) {
            return this.children.getOrDefault(pos, List.of());
        }

        public List<BlockPos> pathToRoot(BlockPos pos) {
            final List<BlockPos> chain = new ArrayList<>();
            final Set<BlockPos> seen = new HashSet<>();
            BlockPos current = pos;
            while (current != null && seen.add(current)) {
                chain.add(current);
                current = this.parents.get(current);
            }
            return chain;
        }

        public float subtreeStress(BlockPos pos) {
            return this.subtreeStress.getOrDefault(pos, 0.0f);
        }

        public float maxSubtreeStress() {
            return this.maxSubtreeStress;
        }

        public float carried(ScanEdge edge) {
            final BlockPos from = edge.from();
            final BlockPos to = edge.to();
            if (from.equals(this.parents.get(to))) {
                return subtreeStress(to);
            }
            if (to.equals(this.parents.get(from))) {
                return subtreeStress(from);
            }
            return 0.0f;
        }

        public int[] worldSpan() {
            return this.span.clone();
        }

        public float elevation(BlockPos pos) {
            if (pos == null || this.span[1] <= 0) {
                return 0.5f;
            }
            return clamp01((pos.getY() - this.span[3]) / (float) this.span[1]);
        }
    }

    public static Result layout(ScanGraph graph) {
        return layout(graph, ScanLayoutMode.DRIVE);
    }

    public static Result layout(ScanGraph graph, ScanLayoutMode mode) {
        final ScanLayoutMode use = mode == null ? ScanLayoutMode.DRIVE : mode;
        final Map<BlockPos, float[]> positions = new LinkedHashMap<>();
        final Map<BlockPos, Integer> ranks = new HashMap<>();
        final Set<Long> primary = new HashSet<>();
        if (graph == null || graph.isEmpty()) {
            return new Result(use, positions, ranks, Map.of(), Map.of(), Map.of(), primary, null, 0,
                    graph == null ? 0 : graph.contentHash(), 0.0f, new int[6]);
        }

        final Map<BlockPos, List<BlockPos>> adjacency = adjacency(graph);
        final List<BlockPos> order = new ArrayList<>(graph.nodes().keySet());
        final Map<BlockPos, Integer> discovery = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            discovery.put(order.get(i), i);
        }

        final BlockPos root = chooseRoot(graph, order);

        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(root);
        ranks.put(root, 0);
        final Map<BlockPos, BlockPos> parents = new HashMap<>();
        int depth = 1;
        while (!queue.isEmpty()) {
            final BlockPos current = queue.poll();
            final int rank = ranks.get(current);
            depth = Math.max(depth, rank + 1);
            for (final BlockPos next : adjacency.getOrDefault(current, List.of())) {
                if (ranks.containsKey(next)) {
                    continue;
                }
                ranks.put(next, rank + 1);
                parents.put(next, current);
                queue.add(next);
            }
        }

        final List<BlockPos> orphans = new ArrayList<>();
        for (final BlockPos pos : order) {
            if (!ranks.containsKey(pos)) {
                ranks.put(pos, depth);
                orphans.add(pos);
            }
        }
        depth = Math.max(depth, 1);

        for (final Map.Entry<BlockPos, BlockPos> entry : parents.entrySet()) {
            primary.add(key(entry.getValue(), entry.getKey()));
            primary.add(key(entry.getKey(), entry.getValue()));
        }

        for (final BlockPos pos : order) {
            final ScanNode node = graph.node(pos);
            final BlockPos upstream = node == null ? null : node.upstream();
            if (upstream != null && ranks.containsKey(upstream)
                    && ranks.get(upstream) < ranks.getOrDefault(pos, 0)) {
                primary.add(key(upstream, pos));
                primary.add(key(pos, upstream));
            }
        }

        final Map<BlockPos, List<BlockPos>> children = childrenOf(order, parents);
        final Map<BlockPos, Float> subtree = subtreeStress(graph, order, ranks, children);
        float maxSubtree = 0.0f;
        for (final float value : subtree.values()) {
            maxSubtree = Math.max(maxSubtree, value);
        }
        final int[] span = worldSpan(order);

        final List<List<BlockPos>> columns = new ArrayList<>();
        for (int i = 0; i < depth; i++) {
            columns.add(new ArrayList<>());
        }
        for (final BlockPos pos : order) {
            columns.get(Math.min(depth - 1, ranks.getOrDefault(pos, 0))).add(pos);
        }

        reduceCrossings(columns, adjacency, discovery);

        switch (use) {
            case RING -> placeRing(positions, columns, children, root, orphans, depth);
            case POWER -> placePower(positions, columns, subtree, discovery, depth);
            case PLAN -> placePlan(positions, order, span);
            default -> placeDrive(positions, columns, depth);
        }

        return new Result(use, positions, ranks, parents, children, subtree, primary, root, depth,
                graph.contentHash(), maxSubtree, span);
    }

    private static void placeDrive(Map<BlockPos, float[]> positions, List<List<BlockPos>> columns,
                                   int depth) {
        for (int rank = 0; rank < columns.size(); rank++) {
            final List<BlockPos> column = columns.get(rank);
            final float x = depth == 1 ? 0.5f : rank / (float) (depth - 1);
            for (int i = 0; i < column.size(); i++) {
                final float span = Math.max(1, column.size());
                final float y = column.size() == 1 ? 0.5f : (i + 0.5f) / span;
                positions.put(column.get(i), new float[]{x, y});
            }
        }
    }

    // children inherit a wedge of the parent's angle by leaf count
    private static void placeRing(Map<BlockPos, float[]> positions, List<List<BlockPos>> columns,
                                  Map<BlockPos, List<BlockPos>> children, BlockPos root,
                                  List<BlockPos> orphans, int depth) {
        final Map<BlockPos, Integer> columnIndex = new HashMap<>();
        for (final List<BlockPos> column : columns) {
            for (int i = 0; i < column.size(); i++) {
                columnIndex.put(column.get(i), i);
            }
        }
        final Map<BlockPos, Integer> leaves = new HashMap<>();
        leafCount(root, children, leaves, new HashSet<>());

        positions.put(root, new float[]{0.5f, 0.5f});

        allocate(root, -(float) Math.PI * 0.5f, (float) Math.PI * 1.5f, 1, positions, children,
                leaves, columnIndex, depth);

        for (int i = 0; i < orphans.size(); i++) {
            final float angle = (float) (Math.PI * 2.0 * (i + 0.5f) / Math.max(1, orphans.size()));
            positions.put(orphans.get(i), new float[]{
                    0.5f + (float) Math.cos(angle) * 0.485f,
                    0.5f + (float) Math.sin(angle) * 0.485f});
        }
    }

    private static void allocate(BlockPos node, float from, float to, int rank,
                                 Map<BlockPos, float[]> positions,
                                 Map<BlockPos, List<BlockPos>> children,
                                 Map<BlockPos, Integer> leaves, Map<BlockPos, Integer> columnIndex,
                                 int depth) {
        final List<BlockPos> kids = new ArrayList<>(children.getOrDefault(node, List.of()));
        if (kids.isEmpty()) {
            return;
        }

        kids.sort((a, b) -> Integer.compare(columnIndex.getOrDefault(a, 0),
                columnIndex.getOrDefault(b, 0)));

        int total = 0;
        for (final BlockPos kid : kids) {
            total += Math.max(1, leaves.getOrDefault(kid, 1));
        }
        final float radius = depth <= 1 ? 0.0f
                : RING_MAX_RADIUS * Math.min(1.0f, rank / (float) (depth - 1));
        float cursor = from;
        for (final BlockPos kid : kids) {
            final float share = Math.max(1, leaves.getOrDefault(kid, 1)) / (float) Math.max(1, total);
            final float wedge = (to - from) * share;
            final float angle = cursor + wedge * 0.5f;
            positions.put(kid, new float[]{
                    0.5f + (float) Math.cos(angle) * radius,
                    0.5f + (float) Math.sin(angle) * radius});
            allocate(kid, cursor, cursor + wedge, rank + 1, positions, children, leaves, columnIndex,
                    depth);
            cursor += wedge;
        }
    }

    private static int leafCount(BlockPos node, Map<BlockPos, List<BlockPos>> children,
                                 Map<BlockPos, Integer> out, Set<BlockPos> seen) {
        if (!seen.add(node)) {
            return 0;
        }
        final List<BlockPos> kids = children.getOrDefault(node, List.of());
        if (kids.isEmpty()) {
            out.put(node, 1);
            return 1;
        }
        int total = 0;
        for (final BlockPos kid : kids) {
            total += leafCount(kid, children, out, seen);
        }
        out.put(node, Math.max(1, total));
        return Math.max(1, total);
    }

    private static void placePower(Map<BlockPos, float[]> positions, List<List<BlockPos>> columns,
                                   Map<BlockPos, Float> subtree, Map<BlockPos, Integer> discovery,
                                   int depth) {
        for (int rank = 0; rank < columns.size(); rank++) {
            final List<BlockPos> column = new ArrayList<>(columns.get(rank));
            if (column.isEmpty()) {
                continue;
            }
            column.sort((a, b) -> {
                final int cmp = Float.compare(subtree.getOrDefault(b, 0.0f),
                        subtree.getOrDefault(a, 0.0f));
                return cmp != 0 ? cmp
                        : Integer.compare(discovery.getOrDefault(a, 0), discovery.getOrDefault(b, 0));
            });
            final float x = depth == 1 ? 0.5f : rank / (float) (depth - 1);
            if (column.size() == 1) {
                positions.put(column.get(0), new float[]{x, 0.5f});
                continue;
            }

            float peak = 0.0f;
            for (final BlockPos pos : column) {
                peak = Math.max(peak, subtree.getOrDefault(pos, 0.0f));
            }
            final float floor = peak * POWER_MIN_SHARE;
            float total = 0.0f;
            for (final BlockPos pos : column) {
                total += Math.max(floor, subtree.getOrDefault(pos, 0.0f));
            }
            if (total <= 0.0f) {
                for (int i = 0; i < column.size(); i++) {
                    positions.put(column.get(i), new float[]{x, (i + 0.5f) / column.size()});
                }
                continue;
            }
            float cursor = 0.0f;
            for (final BlockPos pos : column) {
                final float weight = Math.max(floor, subtree.getOrDefault(pos, 0.0f)) / total;
                positions.put(pos, new float[]{x, clamp01(cursor + weight * 0.5f)});
                cursor += weight;
            }
        }
    }

    private static void placePlan(Map<BlockPos, float[]> positions, List<BlockPos> order, int[] span) {
        final float extent = Math.max(1.0f, Math.max(span[0], span[2]));
        final float centreX = span[4] + span[0] * 0.5f;
        final float centreZ = span[5] + span[2] * 0.5f;
        final Map<Long, Integer> stacked = new HashMap<>();
        for (final BlockPos pos : order) {
            final long cell = (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
            final int nth = stacked.merge(cell, 1, Integer::sum) - 1;
            final float angle = nth * 2.399963f;
            final float nudge = nth == 0 ? 0.0f : 0.006f * (float) Math.sqrt(nth);
            positions.put(pos, new float[]{
                    clamp01(0.5f + (pos.getX() - centreX) / extent * 0.92f
                            + (float) Math.cos(angle) * nudge),
                    clamp01(0.5f + (pos.getZ() - centreZ) / extent * 0.92f
                            + (float) Math.sin(angle) * nudge)});
        }
    }

    private static Map<BlockPos, List<BlockPos>> childrenOf(List<BlockPos> order,
                                                            Map<BlockPos, BlockPos> parents) {
        final Map<BlockPos, List<BlockPos>> children = new LinkedHashMap<>();
        for (final BlockPos pos : order) {
            final BlockPos parent = parents.get(pos);
            if (parent != null) {
                children.computeIfAbsent(parent, k -> new ArrayList<>()).add(pos);
            }
        }
        return children;
    }

    private static Map<BlockPos, Float> subtreeStress(ScanGraph graph, List<BlockPos> order,
                                                      Map<BlockPos, Integer> ranks,
                                                      Map<BlockPos, List<BlockPos>> children) {
        final Map<BlockPos, Float> out = new HashMap<>();
        final List<BlockPos> byDepth = new ArrayList<>(order);
        byDepth.sort((a, b) -> Integer.compare(ranks.getOrDefault(b, 0), ranks.getOrDefault(a, 0)));
        for (final BlockPos pos : byDepth) {
            final ScanNode node = graph.node(pos);
            float total = node == null ? 0.0f : node.drawnStress();
            for (final BlockPos kid : children.getOrDefault(pos, List.of())) {
                total += out.getOrDefault(kid, 0.0f);
            }
            out.put(pos, total);
        }
        return out;
    }

    private static int[] worldSpan(List<BlockPos> order) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final BlockPos pos : order) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX > maxX) {
            return new int[]{0, 0, 0, 0, 0, 0};
        }
        return new int[]{maxX - minX, maxY - minY, maxZ - minZ, minY, minX, minZ};
    }

    private static void reduceCrossings(List<List<BlockPos>> columns,
                                        Map<BlockPos, List<BlockPos>> adjacency,
                                        Map<BlockPos, Integer> discovery) {
        final Map<BlockPos, Integer> index = new HashMap<>();
        for (final List<BlockPos> column : columns) {
            for (int i = 0; i < column.size(); i++) {
                index.put(column.get(i), i);
            }
        }
        for (int sweep = 0; sweep < BARYCENTRE_SWEEPS; sweep++) {
            final boolean forward = sweep % 2 == 0;
            for (int c = forward ? 1 : columns.size() - 2;
                 forward ? c < columns.size() : c >= 0;
                 c += forward ? 1 : -1) {
                final List<BlockPos> column = columns.get(c);
                final int reference = forward ? c - 1 : c + 1;
                if (reference < 0 || reference >= columns.size()) {
                    continue;
                }
                final Map<BlockPos, Float> bary = new HashMap<>();
                for (final BlockPos pos : column) {
                    float sum = 0.0f;
                    int count = 0;
                    for (final BlockPos neighbour : adjacency.getOrDefault(pos, List.of())) {
                        final Integer at = index.get(neighbour);
                        if (at != null && columns.get(reference).contains(neighbour)) {
                            sum += at;
                            count++;
                        }
                    }

                    bary.put(pos, count == 0 ? index.getOrDefault(pos, 0).floatValue() : sum / count);
                }
                column.sort((a, b) -> {
                    final int cmp = Float.compare(bary.get(a), bary.get(b));
                    return cmp != 0 ? cmp
                            : Integer.compare(discovery.getOrDefault(a, 0), discovery.getOrDefault(b, 0));
                });
                for (int i = 0; i < column.size(); i++) {
                    index.put(column.get(i), i);
                }
            }
        }
    }

    private static Map<BlockPos, List<BlockPos>> adjacency(ScanGraph graph) {
        final Map<BlockPos, List<BlockPos>> adjacency = new LinkedHashMap<>();
        for (final BlockPos pos : graph.nodes().keySet()) {
            adjacency.put(pos, new ArrayList<>());
        }
        for (final ScanEdge edge : graph.edges()) {
            final List<BlockPos> from = adjacency.get(edge.from());
            final List<BlockPos> to = adjacency.get(edge.to());
            if (from != null && !from.contains(edge.to())) {
                from.add(edge.to());
            }
            if (to != null && !to.contains(edge.from())) {
                to.add(edge.from());
            }
        }
        return adjacency;
    }

    private static BlockPos chooseRoot(ScanGraph graph, List<BlockPos> order) {
        for (final BlockPos pos : order) {
            final ScanNode node = graph.node(pos);
            if (node != null && node.isSource()) {
                return pos;
            }
        }
        for (final BlockPos pos : order) {
            final ScanNode node = graph.node(pos);
            if (node != null && node.upstream() == null) {
                return pos;
            }
        }
        return order.get(0);
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
    }

    private static long key(BlockPos from, BlockPos to) {
        return from.asLong() * 31L ^ to.asLong();
    }
}
