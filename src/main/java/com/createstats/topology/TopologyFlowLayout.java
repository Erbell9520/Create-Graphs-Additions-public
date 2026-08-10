package com.createstats.topology;

import com.createstats.ModConfiguration;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TopologyFlowLayout {
    private static final float X_PADDING = 0.1f;
    private static final float Y_PADDING = 0.12f;
    private static final float FIT_PADDING_X = 0.14f;
    private static final float FIT_PADDING_Y = 0.16f;

    private TopologyFlowLayout() {
    }

    public static LayoutResult layout(NetworkGraph graph) {
        return layout(graph, configuredIterationCount((graph != null) ? graph.getNodes().size() : 0), null, TopologyLayoutMode.ORGANIC);
    }

    public static LayoutResult layout(NetworkGraph graph, int iterationBudget) {
        return layout(graph, iterationBudget, null, TopologyLayoutMode.ORGANIC);
    }

    public static LayoutResult layout(NetworkGraph graph, int iterationBudget, Map<BlockPos, float[]> seedPositions) {
        return layout(graph, iterationBudget, seedPositions, TopologyLayoutMode.ORGANIC);
    }

    public static LayoutResult layout(NetworkGraph graph, int iterationBudget, Map<BlockPos, float[]> seedPositions, TopologyLayoutMode mode) {
        if (graph == null || graph.isEmpty()) {
            return LayoutResult.EMPTY;
        }
        final TopologyLayoutMode resolvedMode = (mode != null) ? mode : TopologyLayoutMode.ORGANIC;
        final Map<BlockPos, TopologyNode> nodes = graph.getNodes();
        final Map<BlockPos, List<BlockPos>> adjacency = buildAdjacency(graph);
        final List<BlockPos> roots = chooseRoots(nodes.values(), adjacency, nodes);
        final Map<BlockPos, BlockPos> preferredParents = buildPreferredParents(nodes);
        final Map<BlockPos, BlockPos> parent = new HashMap<BlockPos, BlockPos>();
        final Map<BlockPos, Integer> depth = new HashMap<BlockPos, Integer>();
        final LinkedHashSet<EdgeKey> primaryEdges = new LinkedHashSet<EdgeKey>();
        final LinkedHashSet<BlockPos> traversalRoots = new LinkedHashSet<BlockPos>();
        final HashSet<BlockPos> visited = new HashSet<BlockPos>();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<BlockPos>();
        for (BlockPos root : roots) {
            if (!visited.add(root)) {
                continue;
            }
            traversalRoots.add(root);
            parent.put(root, null);
            depth.put(root, 0);
            queue.add(root);
        }
        while (visited.size() < nodes.size()) {
            while (!queue.isEmpty()) {
                final BlockPos current = queue.removeFirst();
                final int currentDepth = depth.getOrDefault(current, 0);
                final List<BlockPos> preferredChildren = new ArrayList<BlockPos>();
                for (Map.Entry<BlockPos, BlockPos> entry : preferredParents.entrySet()) {
                    if (current.equals(entry.getValue()) && !visited.contains(entry.getKey())) {
                        preferredChildren.add(entry.getKey());
                    }
                }
                preferredChildren.sort(childComparator(nodes, adjacency, Collections.<BlockPos, Integer>emptyMap()));
                for (BlockPos child : preferredChildren) {
                    if (!visited.add(child)) {
                        continue;
                    }
                    parent.put(child, current);
                    depth.put(child, currentDepth + 1);
                    primaryEdges.add(EdgeKey.of(current, child));
                    queue.addLast(child);
                }
                final List<BlockPos> neighbors = new ArrayList<BlockPos>(adjacency.getOrDefault(current, Collections.emptyList()));
                neighbors.sort(neighborComparator(nodes, adjacency));
                for (BlockPos neighbor : neighbors) {
                    final BlockPos preferredParent = preferredParents.get(neighbor);
                    if (preferredParent != null && !preferredParent.equals(current)) {
                        continue;
                    }
                    if (!visited.add(neighbor)) {
                        continue;
                    }
                    parent.put(neighbor, current);
                    depth.put(neighbor, currentDepth + 1);
                    primaryEdges.add(EdgeKey.of(current, neighbor));
                    queue.addLast(neighbor);
                }
            }
            if (visited.size() >= nodes.size()) {
                break;
            }
            final BlockPos fallbackRoot = selectFallbackRoot(nodes.keySet(), visited, adjacency, nodes);
            if (fallbackRoot == null) {
                break;
            }
            traversalRoots.add(fallbackRoot);
            visited.add(fallbackRoot);
            parent.put(fallbackRoot, null);
            depth.put(fallbackRoot, 0);
            queue.addLast(fallbackRoot);
        }
        final Map<BlockPos, List<BlockPos>> children = new LinkedHashMap<BlockPos, List<BlockPos>>();
        for (BlockPos node : nodes.keySet()) {
            children.put(node, new ArrayList<BlockPos>());
        }
        for (Map.Entry<BlockPos, BlockPos> entry : parent.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            children.computeIfAbsent(entry.getValue(), k -> new ArrayList<BlockPos>()).add(entry.getKey());
        }
        final Map<BlockPos, Integer> subtreeSize = new HashMap<BlockPos, Integer>();
        for (BlockPos root2 : traversalRoots) {
            computeSubtreeSize(root2, children, nodes, subtreeSize);
        }
        final Map<BlockPos, Integer> laneIndex = new HashMap<BlockPos, Integer>();
        final int[] nextLane = { 0 };
        for (BlockPos root3 : traversalRoots) {
            assignLane(root3, children, nodes, adjacency, subtreeSize, laneIndex, nextLane);
            ++nextLane[0];
        }
        int maxDepth = 0;
        int maxLane = 0;
        for (Integer d : depth.values()) {
            maxDepth = Math.max(maxDepth, d);
        }
        for (Integer lane : laneIndex.values()) {
            maxLane = Math.max(maxLane, lane);
        }
        final Map<BlockPos, float[]> positions;
        switch (resolvedMode) {
            case SOURCE_FOCUS -> {
                positions = buildRadialPositions(nodes, depth, children, subtreeSize, new ArrayList<BlockPos>(traversalRoots), laneIndex, maxDepth, false);
                cleanupRadialLayout(positions, depth, maxDepth, false);
            }
            case SOURCE_RINGS -> {
                positions = buildRadialPositions(nodes, depth, children, subtreeSize, new ArrayList<BlockPos>(traversalRoots), laneIndex, maxDepth, true);
                cleanupRadialLayout(positions, depth, maxDepth, true);
            }
            case LINEAR -> {
                positions = buildLayeredPositions(nodes, depth, parent, laneIndex, maxDepth);
                cleanupLayeredLayout(positions, depth, maxDepth);
            }
            default -> {
                positions = buildInitialPositions(nodes, depth, laneIndex, maxDepth, maxLane, roots, resolvedMode);
                blendSeedPositions(positions, seedPositions);
                refinePositions(graph, positions, Math.max(0, iterationBudget), resolvedMode);
            }
        }
        return new LayoutResult(positions, primaryEdges, new ArrayList<BlockPos>(traversalRoots));
    }

    private static Map<BlockPos, float[]> buildInitialPositions(Map<BlockPos, TopologyNode> nodes, Map<BlockPos, Integer> depth, Map<BlockPos, Integer> laneIndex, int maxDepth, int maxLane, List<BlockPos> roots, TopologyLayoutMode mode) {
        final Map<BlockPos, float[]> positions = new LinkedHashMap<BlockPos, float[]>();
        final int rootCount = Math.max(1, roots.size());
        for (BlockPos nodePos : nodes.keySet()) {
            final int nodeDepth = depth.getOrDefault(nodePos, 0);
            final int lane = laneIndex.getOrDefault(nodePos, 0);
            final float laneFrac = (maxLane == 0) ? 0.5f : lane / (float)Math.max(1, maxLane);
            final float depthFrac = (maxDepth == 0) ? 0.0f : nodeDepth / (float)Math.max(1, maxDepth);
            float x;
            float y;
            switch (mode) {
                case SOURCE_FOCUS -> {
                    final float angle = laneFrac * 6.2831855f - 1.5707964f;
                    final float radius = (nodeDepth == 0) ? ((rootCount > 1) ? 0.045f : 0.0f) : (0.1f + 0.25f * (float)Math.pow(depthFrac, 0.78));
                    x = 0.5f + (float)Math.cos(angle) * radius;
                    y = 0.5f + (float)Math.sin(angle) * radius;
                }
                case SOURCE_RINGS -> {
                    final float angle = laneFrac * 6.2831855f - 1.5707964f;
                    final float radius = (nodeDepth == 0) ? ((rootCount > 1) ? 0.05f : 0.0f) : (0.11f + 0.27f * depthFrac);
                    x = 0.5f + (float)Math.cos(angle) * radius;
                    y = 0.5f + (float)Math.sin(angle) * radius;
                }
                case LINEAR -> {
                    x = (maxDepth == 0) ? 0.5f : (0.12f + 0.76f * depthFrac);
                    y = (maxLane == 0) ? 0.5f : (0.1f + 0.8f * laneFrac);
                }
                default -> {
                    x = (maxDepth == 0) ? 0.5f : (X_PADDING + (1.0f - X_PADDING * 2.0f) * depthFrac);
                    y = (maxLane == 0) ? 0.5f : (Y_PADDING + (1.0f - Y_PADDING * 2.0f) * laneFrac);
                }
            }
            positions.put(nodePos, new float[] { clamp(x, X_PADDING, 1.0f - X_PADDING), clamp(y, Y_PADDING, 1.0f - Y_PADDING) });
        }
        return positions;
    }

    private static void blendSeedPositions(Map<BlockPos, float[]> positions, Map<BlockPos, float[]> seedPositions) {
        if (seedPositions == null || seedPositions.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, float[]> entry : positions.entrySet()) {
            final float[] seed = seedPositions.get(entry.getKey());
            if (seed == null || seed.length < 2) {
                continue;
            }
            final float[] pos = entry.getValue();
            pos[0] = clamp(seed[0] * 0.82f + pos[0] * 0.18f, X_PADDING, 1.0f - X_PADDING);
            pos[1] = clamp(seed[1] * 0.82f + pos[1] * 0.18f, Y_PADDING, 1.0f - Y_PADDING);
        }
    }

    private static Map<BlockPos, float[]> buildRadialPositions(Map<BlockPos, TopologyNode> nodes, Map<BlockPos, Integer> depth, Map<BlockPos, List<BlockPos>> children, Map<BlockPos, Integer> subtreeSize, List<BlockPos> roots, Map<BlockPos, Integer> laneIndex, int maxDepth, boolean strictRings) {
        final Map<BlockPos, float[]> positions = new LinkedHashMap<BlockPos, float[]>();
        final List<BlockPos> orderedRoots = new ArrayList<BlockPos>(roots.isEmpty() ? nodes.keySet() : roots);
        orderedRoots.sort(Comparator.<BlockPos>comparingInt(pos -> laneIndex.getOrDefault(pos, 0)).thenComparingLong(BlockPos::asLong));
        if (orderedRoots.isEmpty()) {
            return positions;
        }
        final float fullCircle = 6.2831855f;
        final float sectorSpan = fullCircle / orderedRoots.size();
        for (int i = 0; i < orderedRoots.size(); ++i) {
            final float angle = -1.5707964f + sectorSpan * i;
            final float span = sectorSpan * 0.9f;
            final BlockPos root = orderedRoots.get(i);
            layoutRadialSubtree(root, angle - span * 0.5f, angle + span * 0.5f, depth, children, subtreeSize, laneIndex, positions, maxDepth, strictRings, orderedRoots.size());
        }
        return positions;
    }

    private static void layoutRadialSubtree(BlockPos node, float startAngle, float endAngle, Map<BlockPos, Integer> depth, Map<BlockPos, List<BlockPos>> children, Map<BlockPos, Integer> subtreeSize, Map<BlockPos, Integer> laneIndex, Map<BlockPos, float[]> positions, int maxDepth, boolean strictRings, int rootCount) {
        final int nodeDepth = depth.getOrDefault(node, 0);
        final float angle = (startAngle + endAngle) * 0.5f;
        final float depthFrac = (maxDepth <= 0) ? 0.0f : (nodeDepth / (float)maxDepth);
        final float radius;
        if (nodeDepth == 0) {
            radius = (rootCount > 1) ? 0.055f : 0.0f;
        }
        else if (strictRings) {
            radius = 0.12f + 0.3f * depthFrac;
        }
        else {
            radius = 0.085f + 0.31f * (float)Math.pow(depthFrac, 0.82f);
        }
        positions.put(node, new float[] { clamp(0.5f + (float)Math.cos(angle) * radius, X_PADDING, 1.0f - X_PADDING), clamp(0.5f + (float)Math.sin(angle) * radius, Y_PADDING, 1.0f - Y_PADDING) });
        final List<BlockPos> kids = new ArrayList<BlockPos>(children.getOrDefault(node, Collections.emptyList()));
        if (kids.isEmpty()) {
            return;
        }
        kids.sort(Comparator.<BlockPos>comparingInt((BlockPos pos) -> -subtreeSize.getOrDefault(pos, 1)).thenComparingInt(pos -> laneIndex.getOrDefault(pos, 0)).thenComparingLong(BlockPos::asLong));
        final float gap = Math.min(0.08f, (endAngle - startAngle) * 0.06f);
        final float usableSpan = Math.max(0.15f, endAngle - startAngle - gap * Math.max(0, kids.size() - 1));
        float totalWeight = 0.0f;
        for (BlockPos child : kids) {
            totalWeight += Math.max(1, subtreeSize.getOrDefault(child, 1));
        }
        float cursor = startAngle;
        for (BlockPos child2 : kids) {
            final float weight = Math.max(1, subtreeSize.getOrDefault(child2, 1));
            final float childSpan = usableSpan * (weight / Math.max(1.0f, totalWeight));
            layoutRadialSubtree(child2, cursor, cursor + childSpan, depth, children, subtreeSize, laneIndex, positions, maxDepth, strictRings, rootCount);
            cursor += childSpan + gap;
        }
    }

    private static Map<BlockPos, float[]> buildLayeredPositions(Map<BlockPos, TopologyNode> nodes, Map<BlockPos, Integer> depth, Map<BlockPos, BlockPos> parent, Map<BlockPos, Integer> laneIndex, int maxDepth) {
        final Map<Integer, List<BlockPos>> layers = new LinkedHashMap<Integer, List<BlockPos>>();
        for (BlockPos nodePos : nodes.keySet()) {
            layers.computeIfAbsent(depth.getOrDefault(nodePos, 0), key -> new ArrayList<BlockPos>()).add(nodePos);
        }
        for (List<BlockPos> layer : layers.values()) {
            layer.sort(Comparator.<BlockPos>comparingInt(pos -> laneIndex.getOrDefault(pos, 0)).thenComparingLong(BlockPos::asLong));
        }
        final Map<BlockPos, Integer> orderByNode = new HashMap<BlockPos, Integer>();
        for (int pass = 0; pass < 3; ++pass) {
            for (int layerIndex = 1; layerIndex <= maxDepth; ++layerIndex) {
                final List<BlockPos> layer2 = layers.get(layerIndex);
                if (layer2 == null || layer2.size() < 2) {
                    continue;
                }
                layer2.sort(Comparator.<BlockPos>comparingDouble(pos -> {
                    final BlockPos parentPos = parent.get(pos);
                    return (parentPos != null) ? orderByNode.getOrDefault(parentPos, laneIndex.getOrDefault(parentPos, 0)) : laneIndex.getOrDefault(pos, 0);
                }).thenComparingInt(pos -> laneIndex.getOrDefault(pos, 0)).thenComparingLong(BlockPos::asLong));
                for (int i = 0; i < layer2.size(); ++i) {
                    orderByNode.put(layer2.get(i), i);
                }
            }
        }
        final Map<BlockPos, float[]> positions = new LinkedHashMap<BlockPos, float[]>();
        for (Map.Entry<Integer, List<BlockPos>> entry : layers.entrySet()) {
            final int layerIndex2 = entry.getKey();
            final List<BlockPos> layer3 = entry.getValue();
            final float x = (maxDepth <= 0) ? 0.5f : (0.12f + 0.76f * (layerIndex2 / (float)maxDepth));
            final int count = layer3.size();
            for (int i2 = 0; i2 < count; ++i2) {
                final float y = (count <= 1) ? 0.5f : (0.12f + 0.76f * ((i2 + 1) / (float)(count + 1)));
                positions.put(layer3.get(i2), new float[] { clamp(x, X_PADDING, 1.0f - X_PADDING), clamp(y, Y_PADDING, 1.0f - Y_PADDING) });
            }
        }
        return positions;
    }

    private static void refinePositions(NetworkGraph graph, Map<BlockPos, float[]> positions, int iterations, TopologyLayoutMode mode) {
        final int nodeCount = positions.size();
        if (nodeCount < 3) {
            return;
        }
        if (iterations <= 0) {
            return;
        }
        final TopologyLayoutMode resolvedMode = (mode != null) ? mode : TopologyLayoutMode.ORGANIC;
        final float area = Math.max(0.1f, (1.0f - X_PADDING * 2.0f) * (1.0f - Y_PADDING * 2.0f));
        final float k = modeSpringConstant(resolvedMode) * (float)Math.sqrt(area / nodeCount);
        final float temperatureStart = modeTemperatureStart(resolvedMode);
        final float minX = X_PADDING;
        final float maxX = 1.0f - X_PADDING;
        final float minY = Y_PADDING;
        final float maxY = 1.0f - Y_PADDING;
        final float anchorX = modeAnchorX(resolvedMode);
        final float anchorY = modeAnchorY(resolvedMode);
        final float gravity = modeGravity(resolvedMode);
        final List<BlockPos> nodeList = new ArrayList<BlockPos>(positions.keySet());
        final Map<BlockPos, float[]> anchors = new HashMap<BlockPos, float[]>();
        final Map<BlockPos, float[]> disp = new HashMap<BlockPos, float[]>();
        for (Map.Entry<BlockPos, float[]> entry : positions.entrySet()) {
            final float[] pos = entry.getValue();
            anchors.put(entry.getKey(), new float[] { pos[0], pos[1] });
            disp.put(entry.getKey(), new float[2]);
        }
        float temperature = temperatureStart;
        for (int iter = 0; iter < iterations; ++iter) {
            for (float[] d : disp.values()) {
                d[0] = 0.0f;
                d[1] = 0.0f;
            }
            float maxDisp = 0.0f;
            for (int i = 0; i < nodeCount; ++i) {
                final BlockPos a = nodeList.get(i);
                final float[] posA = positions.get(a);
                final float[] dispA = disp.get(a);
                final TopologyNode nodeA = graph.getNodes().get(a);
                for (int j = i + 1; j < nodeCount; ++j) {
                    final BlockPos b = nodeList.get(j);
                    final float[] posB = positions.get(b);
                    final float[] dispB = disp.get(b);
                    final TopologyNode nodeB = graph.getNodes().get(b);
                    final float dx = posA[0] - posB[0];
                    final float dy = posA[1] - posB[1];
                    final float dist = Math.max(0.001f, (float)Math.sqrt(dx * dx + dy * dy));
                    float repulse = k * k / dist;
                    if ((nodeA != null && nodeA.isRootLike()) || (nodeB != null && nodeB.isRootLike())) {
                        repulse *= 1.35f;
                    }
                    final float minDist = minimumNodeDistance(nodeA, nodeB);
                    if (dist < minDist) {
                        repulse += (minDist - dist) * 5.5f;
                    }
                    final float fx = dx / dist * repulse;
                    final float fy = dy / dist * repulse;
                    dispA[0] += fx;
                    dispA[1] += fy;
                    dispB[0] -= fx;
                    dispB[1] -= fy;
                }
            }
            applyNodeEdgeRepulsion(graph, positions, disp);
            for (TopologyEdge edge : graph.getEdges()) {
                final float[] from = positions.get(edge.getFrom());
                final float[] to = positions.get(edge.getTo());
                final float[] fromDisp = disp.get(edge.getFrom());
                final float[] toDisp = disp.get(edge.getTo());
                if (from == null || to == null || fromDisp == null || toDisp == null) {
                    continue;
                }
                final float dx2 = to[0] - from[0];
                final float dy2 = to[1] - from[1];
                final float dist2 = Math.max(0.001f, (float)Math.sqrt(dx2 * dx2 + dy2 * dy2));
                final float attract = dist2 * dist2 / Math.max(0.001f, k) * 0.35f;
                final float fx2 = dx2 / dist2 * attract;
                final float fy2 = dy2 / dist2 * attract;
                fromDisp[0] += fx2;
                fromDisp[1] += fy2;
                toDisp[0] -= fx2;
                toDisp[1] -= fy2;
            }
            for (BlockPos pos2 : nodeList) {
                final float[] anchor = anchors.get(pos2);
                final float[] current = positions.get(pos2);
                final float[] currentDisp = disp.get(pos2);
                if (anchor == null || current == null || currentDisp == null) {
                    continue;
                }
                currentDisp[0] += (anchor[0] - current[0]) * anchorX;
                currentDisp[1] += (anchor[1] - current[1]) * anchorY;
                currentDisp[0] += (0.5f - current[0]) * gravity;
                currentDisp[1] += (0.5f - current[1]) * gravity;
            }
            for (BlockPos pos3 : nodeList) {
                final float[] current2 = positions.get(pos3);
                final float[] currentDisp2 = disp.get(pos3);
                if (current2 == null || currentDisp2 == null) {
                    continue;
                }
                final float dispLen = (float)Math.sqrt(currentDisp2[0] * currentDisp2[0] + currentDisp2[1] * currentDisp2[1]);
                if (dispLen > 0.0f) {
                    final float clamped = Math.min(dispLen, temperature);
                    current2[0] += currentDisp2[0] / dispLen * clamped;
                    current2[1] += currentDisp2[1] / dispLen * clamped;
                    maxDisp = Math.max(maxDisp, clamped);
                }
                current2[0] = Math.max(minX, Math.min(maxX, current2[0]));
                current2[1] = Math.max(minY, Math.min(maxY, current2[1]));
            }
            temperature = temperatureStart * (1.0f - (iter + 1) / (float)iterations);
            if (maxDisp < 0.0008f) {
                break;
            }
        }
        cleanupLayout(graph, positions);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float modeTemperatureStart(TopologyLayoutMode mode) {
        return switch (mode) {
            case SOURCE_FOCUS -> 0.095f;
            case SOURCE_RINGS -> 0.082f;
            case LINEAR -> 0.072f;
            default -> 0.11f;
        };
    }

    private static float modeAnchorX(TopologyLayoutMode mode) {
        return switch (mode) {
            case SOURCE_FOCUS -> 0.11f;
            case SOURCE_RINGS -> 0.14f;
            case LINEAR -> 0.18f;
            default -> 0.065f;
        };
    }

    private static float modeAnchorY(TopologyLayoutMode mode) {
        return switch (mode) {
            case SOURCE_FOCUS -> 0.085f;
            case SOURCE_RINGS -> 0.12f;
            case LINEAR -> 0.12f;
            default -> 0.03f;
        };
    }

    private static float modeGravity(TopologyLayoutMode mode) {
        return switch (mode) {
            case SOURCE_FOCUS -> 0.045f;
            case SOURCE_RINGS -> 0.032f;
            case LINEAR -> 0.012f;
            default -> 0.028f;
        };
    }

    private static float modeSpringConstant(TopologyLayoutMode mode) {
        return switch (mode) {
            case SOURCE_FOCUS -> 0.8f;
            case SOURCE_RINGS -> 0.75f;
            case LINEAR -> 0.7f;
            default -> 0.85f;
        };
    }

    private static void cleanupLayout(NetworkGraph graph, Map<BlockPos, float[]> positions) {
        if (positions.size() < 2) {
            return;
        }
        fitToBounds(positions, FIT_PADDING_X, FIT_PADDING_Y);
        for (int pass = 0; pass < 14; ++pass) {
            float maxAdjust = 0.0f;
            maxAdjust = Math.max(maxAdjust, resolveNodeCollisions(graph, positions));
            maxAdjust = Math.max(maxAdjust, resolveNodeEdgeCollisions(graph, positions));
            maxAdjust = Math.max(maxAdjust, resolveEdgeCrossings(graph, positions));
            recenterPositions(positions);
            clampPositions(positions);
            if (maxAdjust < 0.0006f) {
                break;
            }
        }
        fitToBounds(positions, FIT_PADDING_X, FIT_PADDING_Y);
        clampPositions(positions);
    }

    private static void cleanupRadialLayout(Map<BlockPos, float[]> positions, Map<BlockPos, Integer> depth, int maxDepth, boolean strictRings) {
        if (positions.size() < 2) {
            return;
        }
        for (int pass = 0; pass < 5; ++pass) {
            final List<Map.Entry<BlockPos, float[]>> entries = new ArrayList<Map.Entry<BlockPos, float[]>>(positions.entrySet());
            entries.sort(Comparator.<Map.Entry<BlockPos, float[]>>comparingInt(entry -> depth.getOrDefault(entry.getKey(), 0)).thenComparingDouble(entry -> Math.atan2(entry.getValue()[1] - 0.5f, entry.getValue()[0] - 0.5f)));
            for (int i = 1; i < entries.size(); ++i) {
                final BlockPos prevNode = entries.get(i - 1).getKey();
                final BlockPos currNode = entries.get(i).getKey();
                if (depth.getOrDefault(prevNode, 0) != depth.getOrDefault(currNode, 0)) {
                    continue;
                }
                final float[] prev = entries.get(i - 1).getValue();
                final float[] curr = entries.get(i).getValue();
                final float dx = curr[0] - prev[0];
                final float dy = curr[1] - prev[1];
                final float dist = (float)Math.sqrt(dx * dx + dy * dy);
                final float minDist = 0.055f;
                if (dist >= minDist) {
                    continue;
                }
                final float adjust = (minDist - dist) * 0.5f;
                final float tangentX = -(curr[1] - 0.5f);
                final float tangentY = curr[0] - 0.5f;
                final float tangentLen = Math.max(1.0E-4f, (float)Math.sqrt(tangentX * tangentX + tangentY * tangentY));
                prev[0] -= tangentX / tangentLen * adjust;
                prev[1] -= tangentY / tangentLen * adjust;
                curr[0] += tangentX / tangentLen * adjust;
                curr[1] += tangentY / tangentLen * adjust;
            }
            if (strictRings) {
                projectToDepthRings(positions, depth, maxDepth);
            }
            clampPositions(positions);
        }
        fitUniformToBounds(positions, FIT_PADDING_X, FIT_PADDING_Y);
        if (strictRings) {
            projectToDepthRings(positions, depth, maxDepth);
            fitUniformToBounds(positions, FIT_PADDING_X, FIT_PADDING_Y);
        }
        clampPositions(positions);
    }

    private static void cleanupLayeredLayout(Map<BlockPos, float[]> positions, Map<BlockPos, Integer> depth, int maxDepth) {
        if (positions.isEmpty()) {
            return;
        }
        final Map<Integer, List<Map.Entry<BlockPos, float[]>>> layers = new LinkedHashMap<Integer, List<Map.Entry<BlockPos, float[]>>>();
        for (Map.Entry<BlockPos, float[]> entry : positions.entrySet()) {
            layers.computeIfAbsent(depth.getOrDefault(entry.getKey(), 0), key -> new ArrayList<Map.Entry<BlockPos, float[]>>()).add(entry);
        }
        for (Map.Entry<Integer, List<Map.Entry<BlockPos, float[]>>> layerEntry : layers.entrySet()) {
            final List<Map.Entry<BlockPos, float[]>> layer = layerEntry.getValue();
            layer.sort(Comparator.comparingDouble(entry -> entry.getValue()[1]));
            final float x = (maxDepth <= 0) ? 0.5f : (0.12f + 0.76f * (layerEntry.getKey() / (float)maxDepth));
            final int count = layer.size();
            for (int i = 0; i < count; ++i) {
                final float[] pos = layer.get(i).getValue();
                pos[0] = x;
                pos[1] = (count <= 1) ? 0.5f : (0.12f + 0.76f * ((i + 1) / (float)(count + 1)));
            }
        }
        clampPositions(positions);
    }

    private static float resolveNodeCollisions(NetworkGraph graph, Map<BlockPos, float[]> positions) {
        float maxAdjust = 0.0f;
        final List<BlockPos> nodeList = new ArrayList<BlockPos>(positions.keySet());
        for (int i = 0; i < nodeList.size(); ++i) {
            final BlockPos a = nodeList.get(i);
            final float[] posA = positions.get(a);
            final TopologyNode nodeA = graph.getNodes().get(a);
            if (posA == null) {
                continue;
            }
            for (int j = i + 1; j < nodeList.size(); ++j) {
                final BlockPos b = nodeList.get(j);
                final float[] posB = positions.get(b);
                final TopologyNode nodeB = graph.getNodes().get(b);
                if (posB == null) {
                    continue;
                }
                float dx = posA[0] - posB[0];
                float dy = posA[1] - posB[1];
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                final float minDist = minimumNodeDistance(nodeA, nodeB);
                if (dist >= minDist) {
                    continue;
                }
                if (dist < 1.0E-4f) {
                    final float angle = (i * 37 + j * 53) * 0.17364818f;
                    dx = (float)Math.cos(angle);
                    dy = (float)Math.sin(angle);
                    dist = 1.0f;
                }
                final float push = (minDist - dist) * 0.5f;
                final float nx = dx / dist;
                final float ny = dy / dist;
                posA[0] += nx * push;
                posA[1] += ny * push;
                posB[0] -= nx * push;
                posB[1] -= ny * push;
                maxAdjust = Math.max(maxAdjust, push);
            }
        }
        return maxAdjust;
    }

    private static float resolveNodeEdgeCollisions(NetworkGraph graph, Map<BlockPos, float[]> positions) {
        float maxAdjust = 0.0f;
        for (Map.Entry<BlockPos, TopologyNode> entry : graph.getNodes().entrySet()) {
            final BlockPos nodePos = entry.getKey();
            final float[] point = positions.get(nodePos);
            if (point == null) {
                continue;
            }
            final float minDist = nodeEdgeClearance(entry.getValue());
            for (TopologyEdge edge : graph.getEdges()) {
                if (edge.getFrom().equals(nodePos) || edge.getTo().equals(nodePos)) {
                    continue;
                }
                final float[] from = positions.get(edge.getFrom());
                final float[] to = positions.get(edge.getTo());
                if (from == null || to == null) {
                    continue;
                }
                final float[] closest = closestPointOnSegment(point[0], point[1], from[0], from[1], to[0], to[1]);
                float dx = point[0] - closest[0];
                float dy = point[1] - closest[1];
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                if (dist >= minDist) {
                    continue;
                }
                if (dist < 1.0E-4f) {
                    dx = to[1] - from[1];
                    dy = from[0] - to[0];
                    dist = Math.max(1.0E-4f, (float)Math.sqrt(dx * dx + dy * dy));
                }
                final float push = minDist - dist;
                final float nx = dx / dist;
                final float ny = dy / dist;
                point[0] += nx * push;
                point[1] += ny * push;
                final float endpointPush = push * 0.2f;
                from[0] -= nx * endpointPush;
                from[1] -= ny * endpointPush;
                to[0] -= nx * endpointPush;
                to[1] -= ny * endpointPush;
                maxAdjust = Math.max(maxAdjust, push);
            }
        }
        return maxAdjust;
    }

    private static float resolveEdgeCrossings(NetworkGraph graph, Map<BlockPos, float[]> positions) {
        float maxAdjust = 0.0f;
        final List<TopologyEdge> edges = graph.getEdges();
        for (int i = 0; i < edges.size(); ++i) {
            final TopologyEdge first = edges.get(i);
            final float[] a = positions.get(first.getFrom());
            final float[] b = positions.get(first.getTo());
            if (a == null || b == null) {
                continue;
            }
            for (int j = i + 1; j < edges.size(); ++j) {
                final TopologyEdge second = edges.get(j);
                if (sharesEndpoint(first, second)) {
                    continue;
                }
                final float[] c = positions.get(second.getFrom());
                final float[] d = positions.get(second.getTo());
                if (c == null || d == null) {
                    continue;
                }
                if (!segmentsIntersect(a, b, c, d)) {
                    continue;
                }
                float px = -(b[1] - a[1]);
                float py = b[0] - a[0];
                final float plen = Math.max(1.0E-4f, (float)Math.sqrt(px * px + py * py));
                px /= plen;
                py /= plen;
                final float mid1x = (a[0] + b[0]) * 0.5f;
                final float mid1y = (a[1] + b[1]) * 0.5f;
                final float mid2x = (c[0] + d[0]) * 0.5f;
                final float mid2y = (c[1] + d[1]) * 0.5f;
                float sign = Math.signum((mid2x - mid1x) * px + (mid2y - mid1y) * py);
                if (sign == 0.0f) {
                    sign = 1.0f;
                }
                final float push = 0.014f;
                a[0] -= px * push * sign;
                a[1] -= py * push * sign;
                b[0] -= px * push * sign;
                b[1] -= py * push * sign;
                c[0] += px * push * sign;
                c[1] += py * push * sign;
                d[0] += px * push * sign;
                d[1] += py * push * sign;
                maxAdjust = Math.max(maxAdjust, push);
            }
        }
        return maxAdjust;
    }

    private static void applyNodeEdgeRepulsion(NetworkGraph graph, Map<BlockPos, float[]> positions, Map<BlockPos, float[]> disp) {
        for (Map.Entry<BlockPos, TopologyNode> entry : graph.getNodes().entrySet()) {
            final BlockPos nodePos = entry.getKey();
            final float[] point = positions.get(nodePos);
            final float[] nodeDisp = disp.get(nodePos);
            if (point == null || nodeDisp == null) {
                continue;
            }
            final float minDist = nodeEdgeClearance(entry.getValue());
            for (TopologyEdge edge : graph.getEdges()) {
                if (edge.getFrom().equals(nodePos) || edge.getTo().equals(nodePos)) {
                    continue;
                }
                final float[] from = positions.get(edge.getFrom());
                final float[] to = positions.get(edge.getTo());
                final float[] fromDisp = disp.get(edge.getFrom());
                final float[] toDisp = disp.get(edge.getTo());
                if (from == null || to == null || fromDisp == null || toDisp == null) {
                    continue;
                }
                final float[] closest = closestPointOnSegment(point[0], point[1], from[0], from[1], to[0], to[1]);
                float dx = point[0] - closest[0];
                float dy = point[1] - closest[1];
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                if (dist >= minDist) {
                    continue;
                }
                if (dist < 1.0E-4f) {
                    dx = to[1] - from[1];
                    dy = from[0] - to[0];
                    dist = Math.max(1.0E-4f, (float)Math.sqrt(dx * dx + dy * dy));
                }
                final float push = (minDist - dist) * 2.4f;
                final float nx = dx / dist;
                final float ny = dy / dist;
                nodeDisp[0] += nx * push;
                nodeDisp[1] += ny * push;
                final float endpointPush = push * 0.35f;
                fromDisp[0] -= nx * endpointPush;
                fromDisp[1] -= ny * endpointPush;
                toDisp[0] -= nx * endpointPush;
                toDisp[1] -= ny * endpointPush;
            }
        }
    }

    private static void fitToBounds(Map<BlockPos, float[]> positions, float padX, float padY) {
        if (positions.isEmpty()) {
            return;
        }
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] pos : positions.values()) {
            minX = Math.min(minX, pos[0]);
            maxX = Math.max(maxX, pos[0]);
            minY = Math.min(minY, pos[1]);
            maxY = Math.max(maxY, pos[1]);
        }
        final float width = Math.max(0.001f, maxX - minX);
        final float height = Math.max(0.001f, maxY - minY);
        final float usableX = Math.max(0.001f, 1.0f - padX * 2.0f);
        final float usableY = Math.max(0.001f, 1.0f - padY * 2.0f);
        for (float[] pos2 : positions.values()) {
            pos2[0] = padX + (pos2[0] - minX) / width * usableX;
            pos2[1] = padY + (pos2[1] - minY) / height * usableY;
        }
    }

    private static void fitUniformToBounds(Map<BlockPos, float[]> positions, float padX, float padY) {
        if (positions.isEmpty()) {
            return;
        }
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] pos : positions.values()) {
            minX = Math.min(minX, pos[0]);
            maxX = Math.max(maxX, pos[0]);
            minY = Math.min(minY, pos[1]);
            maxY = Math.max(maxY, pos[1]);
        }
        final float width = Math.max(0.001f, maxX - minX);
        final float height = Math.max(0.001f, maxY - minY);
        final float usableX = Math.max(0.001f, 1.0f - padX * 2.0f);
        final float usableY = Math.max(0.001f, 1.0f - padY * 2.0f);
        final float scale = Math.min(usableX / width, usableY / height);
        final float centerX = (minX + maxX) * 0.5f;
        final float centerY = (minY + maxY) * 0.5f;
        for (float[] pos2 : positions.values()) {
            pos2[0] = 0.5f + (pos2[0] - centerX) * scale;
            pos2[1] = 0.5f + (pos2[1] - centerY) * scale;
        }
    }

    private static void recenterPositions(Map<BlockPos, float[]> positions) {
        if (positions.isEmpty()) {
            return;
        }
        float sumX = 0.0f;
        float sumY = 0.0f;
        for (float[] pos : positions.values()) {
            sumX += pos[0];
            sumY += pos[1];
        }
        final float shiftX = 0.5f - sumX / positions.size();
        final float shiftY = 0.5f - sumY / positions.size();
        for (float[] pos2 : positions.values()) {
            pos2[0] += shiftX;
            pos2[1] += shiftY;
        }
    }

    private static void clampPositions(Map<BlockPos, float[]> positions) {
        for (float[] pos : positions.values()) {
            pos[0] = clamp(pos[0], X_PADDING, 1.0f - X_PADDING);
            pos[1] = clamp(pos[1], Y_PADDING, 1.0f - Y_PADDING);
        }
    }

    private static void projectToDepthRings(Map<BlockPos, float[]> positions, Map<BlockPos, Integer> depth, int maxDepth) {
        for (Map.Entry<BlockPos, float[]> entry : positions.entrySet()) {
            final int nodeDepth = depth.getOrDefault(entry.getKey(), 0);
            final float[] pos = entry.getValue();
            final float dx = pos[0] - 0.5f;
            final float dy = pos[1] - 0.5f;
            final float dist = Math.max(1.0E-4f, (float)Math.sqrt(dx * dx + dy * dy));
            final float targetRadius = (nodeDepth == 0) ? 0.0f : (0.12f + 0.3f * (nodeDepth / (float)Math.max(1, maxDepth)));
            if (targetRadius <= 0.0f) {
                pos[0] = 0.5f;
                pos[1] = 0.5f;
                continue;
            }
            pos[0] = 0.5f + dx / dist * targetRadius;
            pos[1] = 0.5f + dy / dist * targetRadius;
        }
    }

    private static float minimumNodeDistance(TopologyNode nodeA, TopologyNode nodeB) {
        return nodeVisualRadius(nodeA) + nodeVisualRadius(nodeB) + 0.016f;
    }

    private static float nodeEdgeClearance(TopologyNode node) {
        return nodeVisualRadius(node) + 0.018f;
    }

    private static float nodeVisualRadius(TopologyNode node) {
        if (node == null) {
            return 0.022f;
        }
        if (node.isSource()) {
            return 0.038f;
        }
        if (node.isRootLike()) {
            return 0.034f;
        }
        if (node.getType().showLabel()) {
            return 0.029f;
        }
        if (node.getType().isObserverLike()) {
            return 0.027f;
        }
        return 0.023f;
    }

    private static float[] closestPointOnSegment(float px, float py, float ax, float ay, float bx, float by) {
        final float dx = bx - ax;
        final float dy = by - ay;
        final float len2 = dx * dx + dy * dy;
        if (len2 < 1.0E-6f) {
            return new float[] { ax, ay };
        }
        final float t = clamp(((px - ax) * dx + (py - ay) * dy) / len2, 0.0f, 1.0f);
        return new float[] { ax + dx * t, ay + dy * t };
    }

    private static boolean sharesEndpoint(TopologyEdge first, TopologyEdge second) {
        return first.getFrom().equals(second.getFrom()) || first.getFrom().equals(second.getTo()) || first.getTo().equals(second.getFrom()) || first.getTo().equals(second.getTo());
    }

    private static boolean segmentsIntersect(float[] a, float[] b, float[] c, float[] d) {
        return linesIntersect(a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1]);
    }

    private static boolean linesIntersect(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        final float d1 = direction(x3, y3, x4, y4, x1, y1);
        final float d2 = direction(x3, y3, x4, y4, x2, y2);
        final float d3 = direction(x1, y1, x2, y2, x3, y3);
        final float d4 = direction(x1, y1, x2, y2, x4, y4);
        if (((d1 > 0.0f && d2 < 0.0f) || (d1 < 0.0f && d2 > 0.0f)) && ((d3 > 0.0f && d4 < 0.0f) || (d3 < 0.0f && d4 > 0.0f))) {
            return true;
        }
        return d1 == 0.0f && onSegment(x3, y3, x4, y4, x1, y1) || d2 == 0.0f && onSegment(x3, y3, x4, y4, x2, y2) || d3 == 0.0f && onSegment(x1, y1, x2, y2, x3, y3) || d4 == 0.0f && onSegment(x1, y1, x2, y2, x4, y4);
    }

    private static float direction(float ax, float ay, float bx, float by, float cx, float cy) {
        return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax);
    }

    private static boolean onSegment(float ax, float ay, float bx, float by, float px, float py) {
        return px >= Math.min(ax, bx) - 1.0E-4f && px <= Math.max(ax, bx) + 1.0E-4f && py >= Math.min(ay, by) - 1.0E-4f && py <= Math.max(ay, by) + 1.0E-4f;
    }

    public static int configuredIterationCount(int nodeCount) {
        int configured = 150;
        try {
            configured = (int)ModConfiguration.COMMON.topologyLayoutIterations.get();
        }
        catch (Exception ex) {}
        configured = Math.max(10, configured);
        final float scale = Math.min(1.0f, 220.0f / Math.max(220, nodeCount));
        return Math.max(12, Math.round(configured * scale));
    }

    private static Map<BlockPos, List<BlockPos>> buildAdjacency(NetworkGraph graph) {
        final Map<BlockPos, List<BlockPos>> adjacency = new LinkedHashMap<BlockPos, List<BlockPos>>();
        for (BlockPos pos : graph.getNodes().keySet()) {
            adjacency.put(pos, new ArrayList<BlockPos>());
        }
        for (TopologyEdge edge : graph.getEdges()) {
            adjacency.computeIfAbsent(edge.getFrom(), k -> new ArrayList<BlockPos>()).add(edge.getTo());
            adjacency.computeIfAbsent(edge.getTo(), k -> new ArrayList<BlockPos>()).add(edge.getFrom());
        }
        return adjacency;
    }

    private static List<BlockPos> chooseRoots(Collection<TopologyNode> nodeValues, Map<BlockPos, List<BlockPos>> adjacency, Map<BlockPos, TopologyNode> nodes) {
        final ArrayList<BlockPos> roots = new ArrayList<BlockPos>();
        for (TopologyNode node : nodeValues) {
            if (node.isRootLike()) {
                roots.add(node.getPos());
            }
        }
        if (roots.isEmpty()) {
            for (TopologyNode node2 : nodeValues) {
                if (node2.isSourceLike()) {
                    roots.add(node2.getPos());
                }
            }
        }
        if (roots.isEmpty()) {
            for (TopologyNode node3 : nodeValues) {
                if (node3.getType().isObserverLike()) {
                    roots.add(node3.getPos());
                }
            }
        }
        roots.sort(nodeComparator(nodes, adjacency));
        if (!roots.isEmpty()) {
            return roots;
        }
        final BlockPos fallback = selectFallbackRoot(nodes.keySet(), Collections.emptySet(), adjacency, nodes);
        if (fallback != null) {
            roots.add(fallback);
        }
        return roots;
    }

    private static Comparator<BlockPos> nodeComparator(Map<BlockPos, TopologyNode> nodes, Map<BlockPos, List<BlockPos>> adjacency) {
        return Comparator.<BlockPos>comparingInt(pos -> rootPriority(nodes.get(pos))).thenComparing((a, b) -> Integer.compare(adjacency.getOrDefault(b, Collections.emptyList()).size(), adjacency.getOrDefault(a, Collections.emptyList()).size())).thenComparingLong(BlockPos::asLong);
    }

    private static int rootPriority(TopologyNode node) {
        if (node == null) {
            return 5;
        }
        if (node.isSource()) {
            return 0;
        }
        if (node.isRootLike()) {
            return 1;
        }
        if (node.isSourceLike()) {
            return 2;
        }
        if (node.getType().isObserverLike()) {
            return 3;
        }
        if (node.getType().isMachineLike()) {
            return 4;
        }
        if (node.getType() == NodeType.SPEED_CONTROLLER) {
            return 6;
        }
        if (node.getType().isTransferOrgan()) {
            return 5;
        }
        return 4;
    }

    private static Comparator<BlockPos> neighborComparator(Map<BlockPos, TopologyNode> nodes, Map<BlockPos, List<BlockPos>> adjacency) {
        return Comparator.<BlockPos>comparingInt(pos -> {
            final TopologyNode node = nodes.get(pos);
            if (node != null && node.getType().isTransferOrgan()) {
                return 0;
            }
            if (node != null && node.isSourceLike()) {
                return 1;
            }
            return 2;
        }).thenComparing((a, b) -> Integer.compare(adjacency.getOrDefault(b, Collections.emptyList()).size(), adjacency.getOrDefault(a, Collections.emptyList()).size())).thenComparingLong(BlockPos::asLong);
    }

    private static BlockPos selectFallbackRoot(Collection<BlockPos> nodes, Set<BlockPos> visited, Map<BlockPos, List<BlockPos>> adjacency, Map<BlockPos, TopologyNode> nodeMap) {
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (BlockPos pos : nodes) {
            if (visited.contains(pos)) {
                continue;
            }
            final TopologyNode node = nodeMap.get(pos);
            int score = adjacency.getOrDefault(pos, Collections.emptyList()).size() * 8;
            if (node != null) {
                if (node.isSource()) {
                    score += 20;
                }
                else if (node.isRootLike()) {
                    score += 18;
                }
                else if (node.isSourceLike()) {
                    score += 16;
                }
                else if (node.getType().isObserverLike()) {
                    score += 14;
                }
                else if (node.getType().isMachineLike()) {
                    score += 8;
                }
                else if (node.getType() == NodeType.SPEED_CONTROLLER) {
                    score -= 12;
                }
                else if (node.getType().isTransferOrgan()) {
                    score -= 4;
                }
            }
            if (score > bestScore || best == null) {
                best = pos;
                bestScore = score;
            }
        }
        return best;
    }

    private static Map<BlockPos, BlockPos> buildPreferredParents(Map<BlockPos, TopologyNode> nodes) {
        final HashMap<BlockPos, BlockPos> parents = new HashMap<BlockPos, BlockPos>();
        for (TopologyNode node : nodes.values()) {
            if (node.isRootLike()) {
                continue;
            }
            final BlockPos upstreamPos = node.getUpstreamPos();
            if (upstreamPos == null || upstreamPos.equals(node.getPos()) || !nodes.containsKey(upstreamPos)) {
                continue;
            }
            parents.put(node.getPos(), upstreamPos);
        }
        breakParentCycles(parents);
        return parents;
    }

    private static void breakParentCycles(Map<BlockPos, BlockPos> parents) {
        final ArrayList<BlockPos> positions = new ArrayList<BlockPos>(parents.keySet());
        for (BlockPos start : positions) {
            final HashSet<BlockPos> path = new HashSet<BlockPos>();
            BlockPos current = start;
            while (current != null && parents.containsKey(current)) {
                if (!path.add(current)) {
                    parents.remove(current);
                    break;
                }
                current = parents.get(current);
            }
        }
    }

    private static int computeSubtreeSize(BlockPos node, Map<BlockPos, List<BlockPos>> children, Map<BlockPos, TopologyNode> nodes, Map<BlockPos, Integer> subtreeSize) {
        final List<BlockPos> kids = children.getOrDefault(node, Collections.emptyList());
        if (kids.isEmpty()) {
            subtreeSize.put(node, 1);
            return 1;
        }
        int total = 1;
        for (BlockPos child : kids) {
            total += computeSubtreeSize(child, children, nodes, subtreeSize);
        }
        subtreeSize.put(node, total);
        return total;
    }

    private static void assignLane(BlockPos node, Map<BlockPos, List<BlockPos>> children, Map<BlockPos, TopologyNode> nodes, Map<BlockPos, List<BlockPos>> adjacency, Map<BlockPos, Integer> subtreeSize, Map<BlockPos, Integer> laneIndex, int[] nextLane) {
        final List<BlockPos> kids = new ArrayList<BlockPos>(children.getOrDefault(node, Collections.emptyList()));
        if (kids.isEmpty()) {
            laneIndex.put(node, nextLane[0]++);
            return;
        }
        kids.sort(childComparator(nodes, adjacency, subtreeSize));
        BlockPos primary = null;
        for (BlockPos child : kids) {
            final TopologyNode childNode = nodes.get(child);
            if (childNode != null && childNode.getType().isTransferOrgan()) {
                primary = child;
                break;
            }
        }
        final ArrayList<BlockPos> others = new ArrayList<BlockPos>(kids);
        others.remove(primary);
        final int split = (others.size() + 1) / 2;
        for (int i = 0; i < split; ++i) {
            assignLane(others.get(i), children, nodes, adjacency, subtreeSize, laneIndex, nextLane);
        }
        if (primary != null) {
            assignLane(primary, children, nodes, adjacency, subtreeSize, laneIndex, nextLane);
        }
        for (int i = split; i < others.size(); ++i) {
            assignLane(others.get(i), children, nodes, adjacency, subtreeSize, laneIndex, nextLane);
        }
        if (primary != null) {
            laneIndex.put(node, laneIndex.getOrDefault(primary, 0));
            return;
        }
        int minLane = Integer.MAX_VALUE;
        int maxLane = Integer.MIN_VALUE;
        for (BlockPos child2 : kids) {
            final int lane = laneIndex.getOrDefault(child2, 0);
            minLane = Math.min(minLane, lane);
            maxLane = Math.max(maxLane, lane);
        }
        laneIndex.put(node, (minLane == Integer.MAX_VALUE) ? nextLane[0]++ : Math.round((minLane + maxLane) * 0.5f));
    }

    private static Comparator<BlockPos> childComparator(Map<BlockPos, TopologyNode> nodes, Map<BlockPos, List<BlockPos>> adjacency, Map<BlockPos, Integer> subtreeSize) {
        return Comparator.<BlockPos>comparingInt(pos -> {
            final TopologyNode node = nodes.get(pos);
            if (node != null && node.getType().isTransferOrgan()) {
                return 0;
            }
            return 1;
        }).thenComparing((a, b) -> Integer.compare(subtreeSize.getOrDefault(b, 1), subtreeSize.getOrDefault(a, 1))).thenComparing((a, b) -> Integer.compare(adjacency.getOrDefault(b, Collections.emptyList()).size(), adjacency.getOrDefault(a, Collections.emptyList()).size())).thenComparingLong(BlockPos::asLong);
    }

    public static final class LayoutResult {
        private static final LayoutResult EMPTY = new LayoutResult(Collections.emptyMap(), Collections.emptySet(), Collections.emptyList());
        private final Map<BlockPos, float[]> positions;
        private final Set<EdgeKey> primaryEdges;
        private final List<BlockPos> roots;

        private LayoutResult(Map<BlockPos, float[]> positions, Set<EdgeKey> primaryEdges, List<BlockPos> roots) {
            this.positions = Collections.unmodifiableMap(new LinkedHashMap<BlockPos, float[]>(positions));
            this.primaryEdges = Collections.unmodifiableSet(new LinkedHashSet<EdgeKey>(primaryEdges));
            this.roots = Collections.unmodifiableList(new ArrayList<BlockPos>(roots));
        }

        public Map<BlockPos, float[]> getPositions() {
            return this.positions;
        }

        public boolean isPrimaryEdge(TopologyEdge edge) {
            return this.primaryEdges.contains(EdgeKey.of(edge.getFrom(), edge.getTo()));
        }

        public BlockPos getPrimaryRoot() {
            return this.roots.isEmpty() ? null : this.roots.get(0);
        }

        public boolean isPrimaryRoot(BlockPos pos) {
            final BlockPos root = this.getPrimaryRoot();
            return root != null && root.equals(pos);
        }
    }

    private record EdgeKey(long a, long b) {
        private static EdgeKey of(BlockPos first, BlockPos second) {
            final long x = first.asLong();
            final long y = second.asLong();
            return (x <= y) ? new EdgeKey(x, y) : new EdgeKey(y, x);
        }
    }
}
