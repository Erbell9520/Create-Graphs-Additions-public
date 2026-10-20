package com.createstats.powertrack;

import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.SectionGraph;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// one reader on both sides, the rail that conducts is the rail that's drawn
public final class GraphRail {
    public record At<P>(P point, int a, int b, double position) {
    }

    public final UUID graphId;
    public final int fingerprint;
    private final Map<ResourceKey<Level>, RailLayout> layouts;
    private final Map<ResourceKey<Level>, List<At<FeederPoint>>> feeders;
    private final Map<ResourceKey<Level>, List<At<InsulatorPoint>>> insulators;
    private final boolean anyPower;

    private GraphRail(UUID graphId, int fingerprint, Map<ResourceKey<Level>, RailLayout> layouts,
                      Map<ResourceKey<Level>, List<At<FeederPoint>>> feeders,
                      Map<ResourceKey<Level>, List<At<InsulatorPoint>>> insulators, boolean anyPower) {
        this.graphId = graphId;
        this.fingerprint = fingerprint;
        this.layouts = layouts;
        this.feeders = feeders;
        this.insulators = insulators;
        this.anyPower = anyPower;
    }

    public boolean hasPower() {
        return anyPower;
    }

    public RailLayout layout(ResourceKey<Level> dimension) {
        final RailLayout layout = layouts.get(dimension);
        return layout == null ? RailLayout.empty() : layout;
    }

    public Map<ResourceKey<Level>, RailLayout> layouts() {
        return layouts;
    }

    public List<At<FeederPoint>> feeders(ResourceKey<Level> dimension) {
        return feeders.getOrDefault(dimension, List.of());
    }

    public List<At<InsulatorPoint>> insulators(ResourceKey<Level> dimension) {
        return insulators.getOrDefault(dimension, List.of());
    }

    public static int fingerprint(TrackGraph graph) {
        long h = 1469598103934665603L;
        long sum = 0;
        int count = 0;
        for (TrackNodeLocation loc : graph.getNodes()) {
            final TrackNode node = graph.locateNode(loc);
            if (node == null) {
                continue;
            }
            for (Map.Entry<TrackNode, TrackEdge> en : graph.getConnectionsFrom(node).entrySet()) {
                final TrackEdge edge = en.getValue();
                final int a = edge.node1.getNetId();
                final int b = edge.node2.getNetId();
                if (a >= b) {
                    continue;
                }
                long k = SectionGraph.key(a, b);
                k = k * 31L + (edge.getTrackMaterial() == PowerTrack.MATERIAL ? 7 : 3);
                k = k * 31L + (edge.isTurn() ? 5 : 1);
                k = k * 31L + Math.round(edge.getLength() * 64.0);

                sum += mix(k);
                count++;
            }
        }
        h = (h ^ sum) * 1099511628211L;
        h = (h ^ count) * 1099511628211L;
        long points = 0;
        for (InsulatorPoint p : graph.getPoints(PowerTrack.INSULATOR)) {
            points += mix(p.getId().hashCode() * 31L + Math.round(p.position * 64.0)
                    + (p.edgeLocation == null ? 0 : p.edgeLocation.getFirst().hashCode() * 17L));
        }
        for (FeederPoint p : graph.getPoints(PowerTrack.FEEDER)) {
            points += mix(p.getId().hashCode() * 37L + Math.round(p.position * 64.0)
                    + (p.blockEntityPos == null ? 0 : p.blockEntityPos.asLong()));
        }
        h = (h ^ points) * 1099511628211L;
        return (int) (h ^ (h >>> 32));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    public static GraphRail read(TrackGraph graph) {
        final int fp = fingerprint(graph);
        final Map<ResourceKey<Level>, List<RailLayout.EdgeIn>> edges = new HashMap<>();
        final Set<Long> seen = new HashSet<>();

        final boolean anyPower = hasPowerEdge(graph);
        for (TrackNodeLocation loc : anyPower ? graph.getNodes() : java.util.Set.<TrackNodeLocation>of()) {
            final TrackNode node = graph.locateNode(loc);
            if (node == null) {
                continue;
            }
            for (Map.Entry<TrackNode, TrackEdge> en : graph.getConnectionsFrom(node).entrySet()) {
                final TrackEdge edge = en.getValue();
                if (edge.isInterDimensional()) {
                    continue;
                }
                final int a = edge.node1.getNetId();
                final int b = edge.node2.getNetId();
                if (a == b || !seen.add(SectionGraph.key(a, b))) {
                    continue;
                }
                final boolean power = edge.getTrackMaterial() == PowerTrack.MATERIAL;
                edges.computeIfAbsent(edge.node1.getLocation().getDimension(), d -> new ArrayList<>())
                        .add(new RailLayout.EdgeIn(a, b, edge.getLength(), power, samples(graph, edge)));
            }
        }
        final Map<ResourceKey<Level>, List<SectionGraph.Cut>> cuts = new HashMap<>();
        final Map<ResourceKey<Level>, List<At<InsulatorPoint>>> insulators = new HashMap<>();
        for (InsulatorPoint p : graph.getPoints(PowerTrack.INSULATOR)) {
            final At<InsulatorPoint> at = locate(graph, p, p.position);
            if (at == null) {
                continue;
            }
            final ResourceKey<Level> dim = p.edgeLocation.getFirst().getDimension();
            cuts.computeIfAbsent(dim, d -> new ArrayList<>()).add(new SectionGraph.Cut(at.a(), at.b(), at.position()));
            insulators.computeIfAbsent(dim, d -> new ArrayList<>()).add(at);
        }
        final Map<ResourceKey<Level>, List<At<FeederPoint>>> feeders = new HashMap<>();
        for (FeederPoint p : graph.getPoints(PowerTrack.FEEDER)) {
            final At<FeederPoint> at = locate(graph, p, p.position);
            if (at != null) {
                feeders.computeIfAbsent(p.edgeLocation.getFirst().getDimension(), d -> new ArrayList<>()).add(at);
            }
        }
        final Map<ResourceKey<Level>, RailLayout> layouts = new HashMap<>();
        if (anyPower) {
            for (Map.Entry<ResourceKey<Level>, List<RailLayout.EdgeIn>> en : edges.entrySet()) {
                layouts.put(en.getKey(), RailLayout.build(en.getValue(), cuts.getOrDefault(en.getKey(), List.of())));
            }
        }
        return new GraphRail(graph.id, fp, layouts, feeders, insulators, anyPower);
    }

    private static boolean hasPowerEdge(TrackGraph graph) {
        for (TrackNodeLocation loc : graph.getNodes()) {
            final TrackNode node = graph.locateNode(loc);
            if (node == null) {
                continue;
            }
            for (TrackEdge edge : graph.getConnectionsFrom(node).values()) {
                if (edge.getTrackMaterial() == PowerTrack.MATERIAL && !edge.isInterDimensional()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static <P extends com.simibubi.create.content.trains.signal.TrackEdgePoint> At<P> locate(
            TrackGraph graph, P point, double position) {
        if (point.edgeLocation == null) {
            return null;
        }
        final TrackNode a = graph.locateNode(point.edgeLocation.getFirst());
        final TrackNode b = graph.locateNode(point.edgeLocation.getSecond());
        if (a == null || b == null) {
            return null;
        }
        return new At<>(point, a.getNetId(), b.getNetId(), position);
    }

    static double[] samples(TrackGraph graph, TrackEdge edge) {
        final int n = edge.isTurn() ? Math.max(3, (int) Math.ceil(edge.getLength() * 4.0) + 1) : 2;
        final double[] xyz = new double[3 * n];
        for (int k = 0; k < n; k++) {
            final Vec3 p = edge.getPosition(graph, k / (double) (n - 1));
            xyz[3 * k] = p.x;
            xyz[3 * k + 1] = p.y;
            xyz[3 * k + 2] = p.z;
        }
        return xyz;
    }
}
