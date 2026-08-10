package com.createstats.train;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public final class TrainMapLayoutHelper
{
    private static final float MIN_BOUND = 0.06f;
    private static final float MAX_BOUND = 0.94f;
    
    private TrainMapLayoutHelper() {
    }
    
    public static Map<UUID, float[]> computeLayout(TrainMapData data, Map<UUID, UUID> effectiveMap, TrainMapViewMode mode, int iterations) {
        final GraphSnapshot graph = buildGraph(data, effectiveMap);
        if (graph.nodeIds.isEmpty()) {
            return new LinkedHashMap<UUID, float[]>();
        }
        switch (mode) {
            case REAL_WORLD: {
                return computeRealWorldLayout(graph);
            }
            case SCHEMATIC: {
                return computeSchematicLayout(graph);
            }
            default: {
                return computeOriginalLayout(graph, iterations);
            }
        }
    }
    
    private static GraphSnapshot buildGraph(TrainMapData data, Map<UUID, UUID> effectiveMap) {
        final LinkedHashMap<UUID, NodeInfo> nodes = new LinkedHashMap<UUID, NodeInfo>();
        final LinkedHashMap<String, TrainMapData.StationEntry> byName = new LinkedHashMap<String, TrainMapData.StationEntry>();
        final LinkedHashMap<UUID, TrainMapData.StationEntry> byId = new LinkedHashMap<UUID, TrainMapData.StationEntry>();
        final ArrayList<TrainMapData.StationEntry> orderedStations = new ArrayList<TrainMapData.StationEntry>(data.getStations());
        orderedStations.sort(Comparator.comparing((TrainMapData.StationEntry station) -> station.name, String.CASE_INSENSITIVE_ORDER).thenComparing(station -> station.id));
        for (TrainMapData.StationEntry station : orderedStations) {
            byName.putIfAbsent(station.name, station);
            byId.put(station.id, station);
            final UUID nodeId = effectiveMap.getOrDefault(station.id, station.id);
            final NodeInfo node = nodes.computeIfAbsent(nodeId, id -> new NodeInfo(id));
            node.worldX += station.x;
            node.worldZ += station.z;
            ++node.weight;
        }
        final ArrayList<UUID> nodeIds = new ArrayList<UUID>(nodes.keySet());
        final int nodeCount = nodeIds.size();
        final HashMap<UUID, Integer> indices = new HashMap<UUID, Integer>();
        for (int i = 0; i < nodeCount; ++i) {
            indices.put(nodeIds.get(i), i);
        }
        final boolean[][] adjacency = new boolean[nodeCount][nodeCount];
        for (TrainMapData.TrainEntry train : data.getTrains()) {
            final List<TrainMapData.StationEntry> route = resolveRoute(train, byName, byId);
            for (int i = 0; i < route.size(); ++i) {
                final TrainMapData.StationEntry from = route.get(i);
                final TrainMapData.StationEntry to = route.get((i + 1) % route.size());
                final UUID fromId = effectiveMap.getOrDefault(from.id, from.id);
                final UUID toId = effectiveMap.getOrDefault(to.id, to.id);
                if (fromId.equals(toId)) {
                    continue;
                }
                final Integer fromIndex = indices.get(fromId);
                final Integer toIndex = indices.get(toId);
                if (fromIndex == null || toIndex == null || fromIndex.equals(toIndex)) {
                    continue;
                }
                adjacency[fromIndex][toIndex] = true;
                adjacency[toIndex][fromIndex] = true;
            }
        }
        final int[] degrees = new int[nodeCount];
        for (int i = 0; i < nodeCount; ++i) {
            for (int j = 0; j < nodeCount; ++j) {
                if (adjacency[i][j]) {
                    ++degrees[i];
                }
            }
        }
        return new GraphSnapshot(nodeIds, nodes, adjacency, degrees);
    }
    
    private static Map<UUID, float[]> computeOriginalLayout(GraphSnapshot graph, int iterations) {
        final int n = graph.nodeIds.size();
        if (n == 1) {
            return singletonLayout(graph.nodeIds.get(0));
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (UUID nodeId : graph.nodeIds) {
            final NodeInfo node = graph.nodes.get(nodeId);
            minX = Math.min(minX, node.avgX());
            maxX = Math.max(maxX, node.avgX());
            minZ = Math.min(minZ, node.avgZ());
            maxZ = Math.max(maxZ, node.avgZ());
        }
        final double rangeX = Math.max(1.0, maxX - minX);
        final double rangeZ = Math.max(1.0, maxZ - minZ);
        final float scaleFactor = (float)Math.max(1.0, Math.sqrt(n / 5.0));
        final float[] px = new float[n];
        final float[] py = new float[n];
        for (int i = 0; i < n; ++i) {
            final NodeInfo node2 = graph.nodes.get(graph.nodeIds.get(i));
            px[i] = (float)((node2.avgX() - minX) / rangeX) * scaleFactor;
            py[i] = (float)((node2.avgZ() - minZ) / rangeZ) * scaleFactor;
        }
        final float area = scaleFactor * scaleFactor;
        final float optimalDistance = (float)Math.sqrt(area / n);
        final float repulsionScale = Math.max(3.0f, (float)Math.log(n + 1));
        final HashMap<Long, Float> worldDistances = new HashMap<Long, Float>();
        double maxWorldDistance = 1.0;
        for (int i = 0; i < n; ++i) {
            for (int j = i + 1; j < n; ++j) {
                if (!graph.adjacency[i][j]) {
                    continue;
                }
                final NodeInfo a = graph.nodes.get(graph.nodeIds.get(i));
                final NodeInfo b = graph.nodes.get(graph.nodeIds.get(j));
                final double dx = a.avgX() - b.avgX();
                final double dz = a.avgZ() - b.avgZ();
                final float distance = (float)Math.sqrt(dx * dx + dz * dz);
                worldDistances.put(pairKey(i, j, n), distance);
                maxWorldDistance = Math.max(maxWorldDistance, distance);
            }
        }
        float temperature = 0.3f * (float)Math.sqrt(area);
        final float cooling = 0.97f;
        for (int iteration = 0; iteration < Math.max(0, iterations); ++iteration) {
            final float[] dx2 = new float[n];
            final float[] dy2 = new float[n];
            for (int i = 0; i < n; ++i) {
                final float weightA = (float)Math.sqrt(graph.nodes.get(graph.nodeIds.get(i)).weight);
                for (int j = i + 1; j < n; ++j) {
                    final float weightB = (float)Math.sqrt(graph.nodes.get(graph.nodeIds.get(j)).weight);
                    final float deltaX = px[j] - px[i];
                    final float deltaY = py[j] - py[i];
                    final float dist = Math.max(1.0E-4f, (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY));
                    final float force = repulsionScale * (optimalDistance * optimalDistance) / dist * weightA * weightB;
                    final float forceX = deltaX / dist * force;
                    final float forceY = deltaY / dist * force;
                    dx2[i] -= forceX;
                    dy2[i] -= forceY;
                    dx2[j] += forceX;
                    dy2[j] += forceY;
                }
            }
            for (int i = 0; i < n; ++i) {
                for (int j = i + 1; j < n; ++j) {
                    if (!graph.adjacency[i][j]) {
                        continue;
                    }
                    final float deltaX = px[j] - px[i];
                    final float deltaY = py[j] - py[i];
                    final float dist = Math.max(1.0E-4f, (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY));
                    final Float worldDistance = worldDistances.get(pairKey(i, j, n));
                    final float worldScale = (worldDistance != null) ? (0.3f + 0.7f * worldDistance / (float)maxWorldDistance) : 1.0f;
                    final float force = dist * dist / optimalDistance * worldScale;
                    final float forceX = deltaX / dist * force;
                    final float forceY = deltaY / dist * force;
                    dx2[i] += forceX;
                    dy2[i] += forceY;
                    dx2[j] -= forceX;
                    dy2[j] -= forceY;
                }
            }
            float centerX = 0.0f;
            float centerY = 0.0f;
            for (int i = 0; i < n; ++i) {
                centerX += px[i];
                centerY += py[i];
            }
            centerX /= n;
            centerY /= n;
            for (int i = 0; i < n; ++i) {
                dx2[i] += (centerX - px[i]) * 5.0E-4f;
                dy2[i] += (centerY - py[i]) * 5.0E-4f;
            }
            for (int i = 0; i < n; ++i) {
                final float displacement = (float)Math.sqrt(dx2[i] * dx2[i] + dy2[i] * dy2[i]);
                if (displacement <= 1.0E-4f) {
                    continue;
                }
                final float capped = Math.min(displacement, temperature);
                px[i] += dx2[i] / displacement * capped;
                py[i] += dy2[i] / displacement * capped;
            }
            temperature *= cooling;
        }
        final LinkedHashMap<UUID, float[]> result = new LinkedHashMap<UUID, float[]>();
        float sumX = 0.0f;
        float sumY = 0.0f;
        for (int i = 0; i < n; ++i) {
            sumX += px[i];
            sumY += py[i];
        }
        final float centerX = sumX / n;
        final float centerY = sumY / n;
        float minLayoutX = Float.MAX_VALUE;
        float maxLayoutX = -Float.MAX_VALUE;
        float minLayoutY = Float.MAX_VALUE;
        float maxLayoutY = -Float.MAX_VALUE;
        for (int i = 0; i < n; ++i) {
            px[i] = 0.5f + (px[i] - centerX);
            py[i] = 0.5f + (py[i] - centerY);
            minLayoutX = Math.min(minLayoutX, px[i]);
            maxLayoutX = Math.max(maxLayoutX, px[i]);
            minLayoutY = Math.min(minLayoutY, py[i]);
            maxLayoutY = Math.max(maxLayoutY, py[i]);
        }
        final float rangeLayoutX = Math.max(0.1f, maxLayoutX - minLayoutX);
        final float rangeLayoutY = Math.max(0.1f, maxLayoutY - minLayoutY);
        final float fit = 0.84f / Math.max(rangeLayoutX, rangeLayoutY);
        for (int i = 0; i < n; ++i) {
            final float fittedX = clampFloat(0.5f + (px[i] - 0.5f) * fit, MIN_BOUND, MAX_BOUND);
            final float fittedY = clampFloat(0.5f + (py[i] - 0.5f) * fit, MIN_BOUND, MAX_BOUND);
            result.put(graph.nodeIds.get(i), new float[] { fittedX, fittedY });
        }
        return result;
    }
    
    private static Map<UUID, float[]> computeRealWorldLayout(GraphSnapshot graph) {
        if (graph.nodeIds.size() == 1) {
            return singletonLayout(graph.nodeIds.get(0));
        }
        final float[] baseX = new float[graph.nodeIds.size()];
        final float[] baseY = new float[graph.nodeIds.size()];
        final LinkedHashMap<UUID, float[]> result = normalizeWorldPositions(graph, baseX, baseY, true);
        relaxOverlaps(graph, result, baseX, baseY, 18, 0.075f, 0.18f);
        fitToBounds(result, 0.82f);
        return result;
    }
    
    private static Map<UUID, float[]> computeSchematicLayout(GraphSnapshot graph) {
        if (graph.nodeIds.size() == 1) {
            return singletonLayout(graph.nodeIds.get(0));
        }
        final LinkedHashMap<UUID, float[]> layout = new LinkedHashMap<UUID, float[]>();
        final List<List<Integer>> components = findComponents(graph);
        components.sort((a, b) -> Integer.compare(b.size(), a.size()));
        final float bandGap = (components.size() <= 1) ? 0.0f : 0.05f;
        float totalUnits = 0.0f;
        for (List<Integer> component : components) {
            totalUnits += Math.max(1, component.size());
        }
        totalUnits += Math.max(0, components.size() - 1) * bandGap * Math.max(1, graph.nodeIds.size());
        float cursor = 0.1f;
        for (List<Integer> component : components) {
            final float bandHeight = 0.8f * Math.max(1, component.size()) / Math.max(1.0f, totalUnits);
            layoutComponent(graph, component, cursor, cursor + bandHeight, layout);
            cursor += bandHeight + bandGap;
        }
        final float[] anchorX = new float[graph.nodeIds.size()];
        final float[] anchorY = new float[graph.nodeIds.size()];
        for (int i = 0; i < graph.nodeIds.size(); ++i) {
            final float[] pos = layout.get(graph.nodeIds.get(i));
            anchorX[i] = (pos != null) ? pos[0] : 0.5f;
            anchorY[i] = (pos != null) ? pos[1] : 0.5f;
        }
        relaxOverlaps(graph, layout, anchorX, anchorY, 24, 0.09f, 0.25f);
        fitToBounds(layout, 0.8f);
        return layout;
    }
    
    private static void layoutComponent(GraphSnapshot graph, List<Integer> component, float top, float bottom, Map<UUID, float[]> layout) {
        final int root = pickComponentRoot(graph, component);
        final HashMap<Integer, Integer> layerByIndex = new HashMap<Integer, Integer>();
        final Queue<Integer> queue = new ArrayDeque<Integer>();
        queue.add(root);
        layerByIndex.put(root, 0);
        while (!queue.isEmpty()) {
            final int current = queue.remove();
            final int depth = layerByIndex.get(current);
            for (int next : component) {
                if (!graph.adjacency[current][next] || layerByIndex.containsKey(next)) {
                    continue;
                }
                layerByIndex.put(next, depth + 1);
                queue.add(next);
            }
        }
        int maxDepth = 0;
        for (int index : component) {
            final int depth2 = layerByIndex.getOrDefault(index, 0);
            maxDepth = Math.max(maxDepth, depth2);
        }
        final ArrayList<ArrayList<Integer>> layers = new ArrayList<ArrayList<Integer>>();
        for (int i = 0; i <= maxDepth; ++i) {
            layers.add(new ArrayList<Integer>());
        }
        for (int index2 : component) {
            layers.get(layerByIndex.getOrDefault(index2, 0)).add(index2);
        }
        for (List<Integer> layer : layers) {
            layer.sort(Comparator.comparingDouble(index3 -> graph.nodes.get(graph.nodeIds.get(index3)).avgZ()));
        }
        final float bandCenter = (top + bottom) * 0.5f;
        final float bandHeight = Math.max(0.12f, bottom - top);
        for (int depth3 = 0; depth3 < layers.size(); ++depth3) {
            final List<Integer> layer2 = layers.get(depth3);
            if (layer2.isEmpty()) {
                continue;
            }
            final float x = (layers.size() <= 1) ? 0.5f : (0.14f + depth3 * 0.72f / (layers.size() - 1));
            if (layer2.size() == 1) {
                layout.put(graph.nodeIds.get(layer2.get(0)), new float[] { x, bandCenter });
                continue;
            }
            final float yStart = bandCenter - bandHeight * 0.4f;
            final float step = bandHeight * 0.8f / Math.max(1, layer2.size() - 1);
            for (int i = 0; i < layer2.size(); ++i) {
                layout.put(graph.nodeIds.get(layer2.get(i)), new float[] { x, yStart + step * i });
            }
        }
    }
    
    private static int pickComponentRoot(GraphSnapshot graph, List<Integer> component) {
        double centerX = 0.0;
        double centerZ = 0.0;
        for (int index : component) {
            final NodeInfo node = graph.nodes.get(graph.nodeIds.get(index));
            centerX += node.avgX();
            centerZ += node.avgZ();
        }
        centerX /= Math.max(1, component.size());
        centerZ /= Math.max(1, component.size());
        int best = component.get(0);
        int bestDegree = Integer.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (int index2 : component) {
            final NodeInfo node2 = graph.nodes.get(graph.nodeIds.get(index2));
            final double dx = node2.avgX() - centerX;
            final double dz = node2.avgZ() - centerZ;
            final double distance = dx * dx + dz * dz;
            final int degree = graph.degrees[index2];
            if (degree > bestDegree || degree == bestDegree && distance < bestDistance) {
                best = index2;
                bestDegree = degree;
                bestDistance = distance;
            }
        }
        return best;
    }
    
    private static List<List<Integer>> findComponents(GraphSnapshot graph) {
        final ArrayList<List<Integer>> components = new ArrayList<List<Integer>>();
        final boolean[] visited = new boolean[graph.nodeIds.size()];
        for (int start = 0; start < graph.nodeIds.size(); ++start) {
            if (visited[start]) {
                continue;
            }
            final ArrayList<Integer> component = new ArrayList<Integer>();
            final Queue<Integer> queue = new ArrayDeque<Integer>();
            visited[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                final int current = queue.remove();
                component.add(current);
                for (int next = 0; next < graph.nodeIds.size(); ++next) {
                    if (!graph.adjacency[current][next] || visited[next]) {
                        continue;
                    }
                    visited[next] = true;
                    queue.add(next);
                }
            }
            components.add(component);
        }
        return components;
    }
    
    private static LinkedHashMap<UUID, float[]> normalizeWorldPositions(GraphSnapshot graph, float[] baseX, float[] baseY, boolean jitterDuplicates) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (UUID nodeId : graph.nodeIds) {
            final NodeInfo node = graph.nodes.get(nodeId);
            minX = Math.min(minX, node.avgX());
            maxX = Math.max(maxX, node.avgX());
            minZ = Math.min(minZ, node.avgZ());
            maxZ = Math.max(maxZ, node.avgZ());
        }
        final double rangeX = Math.max(1.0, maxX - minX);
        final double rangeZ = Math.max(1.0, maxZ - minZ);
        final boolean collapsed = Math.abs(maxX - minX) < 1.0E-3 && Math.abs(maxZ - minZ) < 1.0E-3;
        final HashMap<Long, Integer> duplicateBuckets = new HashMap<Long, Integer>();
        final LinkedHashMap<UUID, float[]> result = new LinkedHashMap<UUID, float[]>();
        for (int i = 0; i < graph.nodeIds.size(); ++i) {
            final UUID nodeId2 = graph.nodeIds.get(i);
            final NodeInfo node2 = graph.nodes.get(nodeId2);
            float x = collapsed ? 0.5f : (float)(0.12 + (node2.avgX() - minX) / rangeX * 0.76);
            float y = collapsed ? 0.5f : (float)(0.12 + (node2.avgZ() - minZ) / rangeZ * 0.76);
            final long bucket = ((long)Math.round(x * 1000.0f) << 32) ^ (long)Math.round(y * 1000.0f);
            final int duplicateIndex = duplicateBuckets.getOrDefault(bucket, 0);
            duplicateBuckets.put(bucket, duplicateIndex + 1);
            if (jitterDuplicates && (duplicateIndex > 0 || collapsed)) {
                final double angle = (nodeId2.hashCode() & 0xFFFF) / 65535.0 * 6.283185307179586 + duplicateIndex * 1.618033988749895;
                final float radius = collapsed ? (0.12f + (i % Math.max(1, graph.nodeIds.size())) * 0.015f) : duplicateIndex * 0.022f;
                x += (float)(Math.cos(angle) * radius);
                y += (float)(Math.sin(angle) * radius);
            }
            x = clampFloat(x, MIN_BOUND, MAX_BOUND);
            y = clampFloat(y, MIN_BOUND, MAX_BOUND);
            baseX[i] = x;
            baseY[i] = y;
            result.put(nodeId2, new float[] { x, y });
        }
        return result;
    }
    
    private static void relaxOverlaps(GraphSnapshot graph, Map<UUID, float[]> layout, float[] anchorX, float[] anchorY, int passes, float minimumDistance, float anchorStrength) {
        if (layout.isEmpty()) {
            return;
        }
        for (int pass = 0; pass < passes; ++pass) {
            for (int i = 0; i < graph.nodeIds.size(); ++i) {
                final float[] a = layout.get(graph.nodeIds.get(i));
                if (a == null) {
                    continue;
                }
                for (int j = i + 1; j < graph.nodeIds.size(); ++j) {
                    final float[] b = layout.get(graph.nodeIds.get(j));
                    if (b == null) {
                        continue;
                    }
                    float dx = b[0] - a[0];
                    float dy = b[1] - a[1];
                    float dist = (float)Math.sqrt(dx * dx + dy * dy);
                    if (dist < 1.0E-4f) {
                        final double angle = (graph.nodeIds.get(i).hashCode() ^ graph.nodeIds.get(j).hashCode()) * 0.017453292519943295;
                        dx = (float)Math.cos(angle) * 0.001f;
                        dy = (float)Math.sin(angle) * 0.001f;
                        dist = (float)Math.sqrt(dx * dx + dy * dy);
                    }
                    if (dist >= minimumDistance) {
                        continue;
                    }
                    final float push = (minimumDistance - dist) * 0.5f;
                    final float pushX = dx / dist * push;
                    final float pushY = dy / dist * push;
                    a[0] = clampFloat(a[0] - pushX, MIN_BOUND, MAX_BOUND);
                    a[1] = clampFloat(a[1] - pushY, MIN_BOUND, MAX_BOUND);
                    b[0] = clampFloat(b[0] + pushX, MIN_BOUND, MAX_BOUND);
                    b[1] = clampFloat(b[1] + pushY, MIN_BOUND, MAX_BOUND);
                }
            }
            for (int i = 0; i < graph.nodeIds.size(); ++i) {
                final float[] pos = layout.get(graph.nodeIds.get(i));
                if (pos == null) {
                    continue;
                }
                pos[0] += (anchorX[i] - pos[0]) * anchorStrength;
                pos[1] += (anchorY[i] - pos[1]) * anchorStrength;
                pos[0] = clampFloat(pos[0], MIN_BOUND, MAX_BOUND);
                pos[1] = clampFloat(pos[1], MIN_BOUND, MAX_BOUND);
            }
        }
    }
    
    private static void fitToBounds(Map<UUID, float[]> positions, float fill) {
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
        final float rangeX = Math.max(0.01f, maxX - minX);
        final float rangeY = Math.max(0.01f, maxY - minY);
        final float scale = fill / Math.max(rangeX, rangeY);
        final float centerX = (minX + maxX) * 0.5f;
        final float centerY = (minY + maxY) * 0.5f;
        for (float[] pos2 : positions.values()) {
            pos2[0] = clampFloat(0.5f + (pos2[0] - centerX) * scale, MIN_BOUND, MAX_BOUND);
            pos2[1] = clampFloat(0.5f + (pos2[1] - centerY) * scale, MIN_BOUND, MAX_BOUND);
        }
    }
    
    private static Map<UUID, float[]> singletonLayout(UUID nodeId) {
        final LinkedHashMap<UUID, float[]> result = new LinkedHashMap<UUID, float[]>();
        result.put(nodeId, new float[] { 0.5f, 0.5f });
        return result;
    }
    
    private static List<TrainMapData.StationEntry> resolveRoute(TrainMapData.TrainEntry train, Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId) {
        final ArrayList<TrainMapData.StationEntry> result = new ArrayList<TrainMapData.StationEntry>();
        if (train.routeKey != null && !train.routeKey.isEmpty()) {
            for (String token : train.routeKey.split("\\|")) {
                addRouteStation(result, findRouteStation(byName, byId, token));
            }
        }
        if (result.size() < 2) {
            result.clear();
            for (String filter : train.scheduleStops) {
                addRouteStation(result, findStation(byName, filter));
            }
        }
        if (result.size() < 2) {
            result.clear();
            TrainMapData.StationEntry from = byId.get(train.activeSegmentFromId);
            if (from == null) {
                from = byId.get(train.previousStationId);
            }
            if (from == null) {
                from = byId.get(train.currentStationId);
            }
            TrainMapData.StationEntry to = byId.get(train.activeSegmentToId);
            if (to == null) {
                to = byId.get(train.destinationStationId);
            }
            if (to == null) {
                to = findStation(byName, train.nextDestination);
            }
            addRouteStation(result, from);
            addRouteStation(result, to);
        }
        return result;
    }

    private static TrainMapData.StationEntry findRouteStation(Map<String, TrainMapData.StationEntry> byName, Map<UUID, TrainMapData.StationEntry> byId, String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            final TrainMapData.StationEntry station = byId.get(UUID.fromString(token));
            if (station != null) {
                return station;
            }
        }
        catch (IllegalArgumentException ex) {}
        return findStation(byName, token);
    }

    private static void addRouteStation(List<TrainMapData.StationEntry> route, TrainMapData.StationEntry station) {
        if (station == null) {
            return;
        }
        if (!route.isEmpty() && route.get(route.size() - 1).id.equals(station.id)) {
            return;
        }
        route.add(station);
    }
    
    private static TrainMapData.StationEntry findStation(Map<String, TrainMapData.StationEntry> byName, String filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        final TrainMapData.StationEntry exact = byName.get(filter);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, TrainMapData.StationEntry> entry : byName.entrySet()) {
            if (TrainMapData.matchesStationFilter(filter, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    private static long pairKey(int a, int b, int n) {
        return (long)Math.min(a, b) * (long)n + Math.max(a, b);
    }
    
    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private static final class GraphSnapshot
    {
        private final List<UUID> nodeIds;
        private final Map<UUID, NodeInfo> nodes;
        private final boolean[][] adjacency;
        private final int[] degrees;
        
        private GraphSnapshot(List<UUID> nodeIds, Map<UUID, NodeInfo> nodes, boolean[][] adjacency, int[] degrees) {
            this.nodeIds = Collections.unmodifiableList(nodeIds);
            this.nodes = nodes;
            this.adjacency = adjacency;
            this.degrees = degrees;
        }
    }
    
    private static final class NodeInfo
    {
        private final UUID id;
        private double worldX;
        private double worldZ;
        private int weight;
        
        private NodeInfo(UUID id) {
            this.id = id;
        }
        
        private double avgX() {
            return this.worldX / Math.max(1, this.weight);
        }
        
        private double avgZ() {
            return this.worldZ / Math.max(1, this.weight);
        }
    }
}
