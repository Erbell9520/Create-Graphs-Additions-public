package com.createstats.train.smart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.createstats.CreateStats;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.display.GlobalTrainDisplayData;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.createmod.catnip.data.Pair;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

// ask create, don't re-derive. a second copy would disagree with the flap display next to it
public final class RailScanner {
    private RailScanner() {
    }

    private static final int WALK_BUDGET = 512;

    private static final int MAX_PATH_POINTS = 48;

    private static final int MAX_CARS = 24;

    private static final double STRAIGHT_ENOUGH = 7.0 / 8.0;

    private static final int MAX_TRACK_POINTS = 24000;

    private static final double TRACK_SIMPLIFY = 0.35;

    private static int cachedChecksum = Integer.MIN_VALUE;
    private static List<RailSnapshot.Station> cachedStations = List.of();
    private static List<RailSnapshot.Link> cachedLinks = List.of();
    private static List<RailSnapshot.Track> cachedTracks = List.of();
    private static boolean cachedTracksTruncated;

    // same checksum in two worlds happens, clear on world change
    public static void reset() {
        cachedChecksum = Integer.MIN_VALUE;
        cachedStations = List.of();
        cachedLinks = List.of();
        cachedTracks = List.of();
        cachedTracksTruncated = false;
    }

    public static RailSnapshot scan(Level level, boolean includeTopology) {
        final RailSnapshot snap = new RailSnapshot();
        if (level == null) {
            return snap;
        }
        snap.serverTick = level.getGameTime();

        final GlobalRailwayManager railways = Create.RAILWAYS;
        if (railways == null) {
            return snap;
        }

        try {
            final List<TrackGraph> graphs = new ArrayList<>(railways.trackNetworks.values());
            final int checksum = checksumOf(graphs);
            snap.topologyChecksum = checksum;

            if (checksum != cachedChecksum) {
                rebuildTopology(graphs);
                cachedChecksum = checksum;
            }
            if (includeTopology) {
                snap.stations().addAll(cachedStations);
                snap.links().addAll(cachedLinks);
                snap.tracks().addAll(cachedTracks);
                snap.tracksTruncated = cachedTracksTruncated;
            }

            final Map<UUID, RailSnapshot.Station> byId = new HashMap<>();
            for (final RailSnapshot.Station s : cachedStations) {
                byId.put(s.id, s);
            }
            readTrains(level, railways, byId, snap);
            readArrivals(snap);
        } catch (final LinkageError | RuntimeException e) {
            CreateStats.LOGGER.warn("Rail network scan failed: {}", e.toString());
        }
        return snap;
    }

    private static int checksumOf(List<TrackGraph> graphs) {
        int sum = graphs.size();
        for (final TrackGraph g : graphs) {
            sum = sum * 31 + g.getChecksum();

            int stationBits = 0;
            for (final GlobalStation station : g.getPoints(EdgePointType.STATION)) {
                int h = station.getId().hashCode();
                h = h * 31 + (station.name == null ? 0 : station.name.hashCode());
                h = h * 31 + (station.assembling ? 1 : 0);
                stationBits += h;
            }
            sum = sum * 31 + stationBits;
        }
        return sum;
    }

    private static void rebuildTopology(List<TrackGraph> graphs) {
        final List<RailSnapshot.Station> stations = new ArrayList<>();
        final List<RailSnapshot.Link> links = new ArrayList<>();
        final List<RailSnapshot.Track> tracks = new ArrayList<>();
        final int[] trackBudget = {MAX_TRACK_POINTS};
        final Set<UUID> seen = new HashSet<>();

        for (final TrackGraph graph : graphs) {
            final Collection<GlobalStation> points = graph.getPoints(EdgePointType.STATION);

            final Map<TrackNode, GlobalStation> stationNodes = new HashMap<>();

            for (final GlobalStation station : points) {
                if (!seen.add(station.getId())) {
                    continue;
                }
                final RailSnapshot.Station s = new RailSnapshot.Station();
                s.id = station.getId();
                s.name = station.name == null ? "Unknown" : station.name;
                s.assembling = station.assembling;
                s.packagePorts = station.connectedPorts == null ? 0 : station.connectedPorts.size();

                final Vec3 pos = worldPositionOf(graph, station);
                if (pos != null) {
                    s.x = pos.x;
                    s.y = pos.y;
                    s.z = pos.z;
                } else if (station.getBlockEntityPos() != null) {
                    s.x = station.getBlockEntityPos().getX();
                    s.y = station.getBlockEntityPos().getY();
                    s.z = station.getBlockEntityPos().getZ();
                }
                s.dimension = dimensionOf(station);

                final Train present = station.getPresentTrain();
                if (present != null) {
                    s.presentTrain = present.id;
                }
                final Train imminent = station.getImminentTrain();
                if (imminent != null && (present == null || !imminent.id.equals(present.id))) {
                    s.imminentTrain = imminent.id;
                }

                stations.add(s);

                final TrackNode node = nodeOf(graph, station);
                if (node != null) {
                    stationNodes.put(node, station);
                }
            }

            buildLinks(graph, stationNodes, links);
            buildTracks(graph, tracks, trackBudget);
        }

        cachedStations = List.copyOf(stations);
        cachedLinks = List.copyOf(links);
        cachedTracks = List.copyOf(tracks);
        cachedTracksTruncated = trackBudget[0] <= 0;
    }

    private static void buildTracks(TrackGraph graph, List<RailSnapshot.Track> into,
                                    int[] budget) {
        if (budget[0] <= 0) {
            return;
        }
        final List<TrackNode> nodes = new ArrayList<>();
        for (final TrackNodeLocation location : graph.getNodes()) {
            final TrackNode node = graph.locateNode(location);
            if (node != null) {
                nodes.add(node);
            }
        }

        nodes.sort(Comparator.comparingInt(TrackNode::getNetId));

        final Set<Long> walked = new HashSet<>();

        for (final TrackNode node : nodes) {
            if (degreeOf(graph, node) != 2) {
                traceRunsFrom(graph, node, walked, into, budget);
            }
        }

        for (final TrackNode node : nodes) {
            traceRunsFrom(graph, node, walked, into, budget);
        }
    }

    private static void traceRunsFrom(TrackGraph graph, TrackNode start, Set<Long> walked,
                                      List<RailSnapshot.Track> into, int[] budget) {
        final Map<TrackNode, TrackEdge> exits = graph.getConnectionsFrom(start);
        if (exits == null || exits.isEmpty()) {
            return;
        }
        for (final Map.Entry<TrackNode, TrackEdge> exit : new ArrayList<>(exits.entrySet())) {
            if (budget[0] <= 0) {
                return;
            }
            traceRun(graph, start, exit.getKey(), exit.getValue(), walked, into, budget);
        }
    }

    private static void traceRun(TrackGraph graph, TrackNode start, TrackNode first,
                                 TrackEdge firstEdge, Set<Long> walked,
                                 List<RailSnapshot.Track> into, int[] budget) {
        if (first == null || firstEdge == null || firstEdge.isInterDimensional()) {
            return;
        }
        if (!walked.add(edgeKey(start, first))) {
            return;
        }
        final List<float[]> path = new ArrayList<>();

        sample(graph, firstEdge, path);

        TrackNode previous = start;
        TrackNode current = first;
        int steps = 0;
        while (degreeOf(graph, current) == 2 && steps++ < WALK_BUDGET) {
            final Map<TrackNode, TrackEdge> onward = graph.getConnectionsFrom(current);
            TrackNode next = null;
            TrackEdge nextEdge = null;
            for (final Map.Entry<TrackNode, TrackEdge> option : onward.entrySet()) {
                if (option.getKey() != previous) {
                    next = option.getKey();
                    nextEdge = option.getValue();
                }
            }
            if (next == null || nextEdge == null || nextEdge.isInterDimensional()
                    || !walked.add(edgeKey(current, next))) {
                break;
            }
            sample(graph, nextEdge, path);
            previous = current;
            current = next;
        }

        final RailSnapshot.Track track = new RailSnapshot.Track();
        track.dimension = dimensionOf(start);
        track.path = simplify(path, TRACK_SIMPLIFY, budget);
        if (track.valid()) {
            into.add(track);
        }
    }

    private static int degreeOf(TrackGraph graph, TrackNode node) {
        final Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
        return connections == null ? 0 : connections.size();
    }

    private static long edgeKey(TrackNode a, TrackNode b) {
        final int lo = Math.min(a.getNetId(), b.getNetId());
        final int hi = Math.max(a.getNetId(), b.getNetId());
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    private static String dimensionOf(TrackNode node) {
        final TrackNodeLocation location = node.getLocation();
        return location == null || location.dimension == null
                ? "minecraft:overworld" : location.dimension.location().toString();
    }

    private static float[] simplify(List<float[]> points, double tolerance, int[] budget) {
        final int n = points.size();
        if (n < 2 || budget[0] <= 0) {
            return new float[0];
        }
        final boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;

        final java.util.ArrayDeque<int[]> pending = new java.util.ArrayDeque<>();
        pending.push(new int[]{0, n - 1});
        while (!pending.isEmpty()) {
            final int[] span = pending.pop();
            final int from = span[0];
            final int to = span[1];
            if (to - from < 2) {
                continue;
            }
            final float[] a = points.get(from);
            final float[] b = points.get(to);
            double worst = tolerance;
            int at = -1;
            for (int i = from + 1; i < to; i++) {
                final double d = perpendicular(points.get(i), a, b);
                if (d > worst) {
                    worst = d;
                    at = i;
                }
            }
            if (at >= 0) {
                keep[at] = true;
                pending.push(new int[]{from, at});
                pending.push(new int[]{at, to});
            }
        }

        int kept = 0;
        for (final boolean k : keep) {
            if (k) {
                kept++;
            }
        }
        if (kept > budget[0]) {
            budget[0] = 0;
            return new float[0];
        }
        budget[0] -= kept;

        final float[] out = new float[kept * 2];
        int w = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out[w * 2] = points.get(i)[0];
                out[w * 2 + 1] = points.get(i)[1];
                w++;
            }
        }
        return out;
    }

    private static double perpendicular(float[] p, float[] a, float[] b) {
        final double dx = b[0] - a[0];
        final double dz = b[1] - a[1];
        final double lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1.0e-6) {
            return Math.hypot(p[0] - a[0], p[1] - a[1]);
        }
        final double t = Math.max(0.0, Math.min(1.0,
                ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / lengthSq));
        return Math.hypot(p[0] - (a[0] + dx * t), p[1] - (a[1] + dz * t));
    }

    private static void buildLinks(TrackGraph graph, Map<TrackNode, GlobalStation> stationNodes,
                                   List<RailSnapshot.Link> into) {
        final Set<String> emitted = new HashSet<>();

        for (final Map.Entry<TrackNode, GlobalStation> entry : stationNodes.entrySet()) {
            final TrackNode start = entry.getKey();
            final GlobalStation from = entry.getValue();

            for (final Map.Entry<TrackNode, TrackEdge> exit
                    : graph.getConnectionsFrom(start).entrySet()) {
                TrackNode previous = start;
                TrackNode current = exit.getKey();
                TrackEdge edge = exit.getValue();
                if (current == null || edge == null) {
                    continue;
                }

                double length = edge.getLength();
                final List<float[]> path = new ArrayList<>();
                sample(graph, edge, path);

                TrackEdge arriving = edge;
                boolean clean = true;
                int steps = 0;

                while (!stationNodes.containsKey(current) && steps++ < WALK_BUDGET) {
                    final Map<TrackNode, TrackEdge> onward = graph.getConnectionsFrom(current);
                    if (onward == null || onward.size() < 2) {
                        clean = false;
                        break;
                    }
                    TrackNode next = null;
                    TrackEdge nextEdge = null;
                    double straightest = STRAIGHT_ENOUGH;
                    for (final Map.Entry<TrackNode, TrackEdge> option : onward.entrySet()) {
                        if (option.getKey() == previous || option.getValue() == null
                                || option.getValue().isInterDimensional()) {
                            continue;
                        }
                        final double alignment = arrivalDirection(arriving, previous)
                                .dot(departureDirection(option.getValue(), current));
                        if (alignment > straightest) {
                            straightest = alignment;
                            next = option.getKey();
                            nextEdge = option.getValue();
                        }
                    }
                    if (next == null || nextEdge == null) {
                        clean = false;
                        break;
                    }
                    length += nextEdge.getLength();
                    sample(graph, nextEdge, path);
                    arriving = nextEdge;
                    previous = current;
                    current = next;
                }

                if (!clean || steps >= WALK_BUDGET) {
                    continue;
                }
                final GlobalStation to = stationNodes.get(current);
                if (to == null || to.getId().equals(from.getId())) {
                    continue;
                }

                final String key = from.getId().compareTo(to.getId()) < 0
                        ? from.getId() + ">" + to.getId()
                        : to.getId() + ">" + from.getId();
                if (!emitted.add(key)) {
                    continue;
                }

                final RailSnapshot.Link link = new RailSnapshot.Link();
                link.from = from.getId();
                link.to = to.getId();
                link.length = length;
                link.path = flatten(path);
                into.add(link);
            }
        }
    }

    private static Vec3 arrivalDirection(TrackEdge edge, TrackNode from) {
        return edge.node1 == from ? edge.getDirection(false) : edge.getDirection(true).scale(-1.0);
    }

    private static Vec3 departureDirection(TrackEdge edge, TrackNode from) {
        return edge.node1 == from ? edge.getDirection(true) : edge.getDirection(false).scale(-1.0);
    }

    private static void sample(TrackGraph graph, TrackEdge edge, List<float[]> into) {
        final int samples = edge.isTurn() ? 6 : 2;

        for (int i = into.isEmpty() ? 0 : 1; i < samples; i++) {
            final Vec3 p = edge.getPosition(graph, i / (double) (samples - 1));
            if (p != null) {
                into.add(new float[]{(float) p.x, (float) p.z});
            }
        }
    }

    private static float[] flatten(List<float[]> points) {
        double tolerance = 0.2;
        for (int attempt = 0; attempt < 8; attempt++) {
            final int[] budget = {Integer.MAX_VALUE};
            final float[] out = simplify(points, tolerance, budget);
            if (out.length / 2 <= MAX_PATH_POINTS) {
                return out;
            }
            tolerance *= 2.0;
        }
        return simplify(points, tolerance, new int[]{MAX_PATH_POINTS});
    }

    private static TrackNode nodeOf(TrackGraph graph, GlobalStation station) {
        if (station.edgeLocation == null || station.edgeLocation.getFirst() == null) {
            return null;
        }
        return graph.locateNode(station.edgeLocation.getFirst());
    }

    private static Vec3 worldPositionOf(TrackGraph graph, GlobalStation station) {
        if (station.edgeLocation == null || station.edgeLocation.getFirst() == null
                || station.edgeLocation.getSecond() == null) {
            return null;
        }
        final TrackNode first = graph.locateNode(station.edgeLocation.getFirst());
        final TrackNode second = graph.locateNode(station.edgeLocation.getSecond());
        if (first == null || second == null) {
            return null;
        }
        TrackEdge edge = graph.getConnection(Couple.create(first, second));
        if (edge == null) {
            edge = graph.getConnection(Couple.create(second, first));
        }
        if (edge == null) {
            return first.getLocation().getLocation().lerp(second.getLocation().getLocation(), 0.5);
        }
        final double length = edge.getLength();
        if (length <= 1.0e-4) {
            return first.getLocation().getLocation().lerp(second.getLocation().getLocation(), 0.5);
        }
        return edge.getPosition(graph, station.getLocationOn(edge) / length);
    }

    private static String dimensionOf(GlobalStation station) {
        if (station.edgeLocation != null && station.edgeLocation.getFirst() != null
                && station.edgeLocation.getFirst().dimension != null) {
            return station.edgeLocation.getFirst().dimension.location().toString();
        }
        if (station.getBlockEntityDimension() != null) {
            return station.getBlockEntityDimension().location().toString();
        }
        return "minecraft:overworld";
    }

    private static void readTrains(Level level, GlobalRailwayManager railways,
                                   Map<UUID, RailSnapshot.Station> byId, RailSnapshot snap) {
        for (final Train train : new ArrayList<>(railways.trains.values())) {
            if (train.invalid) {
                continue;
            }
            final RailSnapshot.TrainState t = new RailSnapshot.TrainState();
            t.id = train.id;
            t.name = train.name == null ? "Train" : train.name.getString();
            t.owner = ownerName(level, train.owner);
            t.speed = (float) train.speed;
            t.throttle = (float) train.throttle;
            t.carriages = train.carriages == null ? 0 : train.carriages.size();
            t.derailed = train.derailed;
            t.fuelTicks = train.fuelTicks;
            t.occupiedBlocks = train.occupiedSignalBlocks == null ? 0
                    : train.occupiedSignalBlocks.size();
            t.reservedBlocks = train.reservedSignalBlocks == null ? 0
                    : train.reservedSignalBlocks.size();

            t.mapColor = train.mapColorIndex;
            try {
                t.iconId = train.icon == null ? "" : String.valueOf(train.icon.getId());
            } catch (final RuntimeException | LinkageError ignored) {
                t.iconId = "";
            }
            t.doubleEnded = train.doubleEnded;
            t.backwards = train.currentlyBackwards;
            t.manual = train.manualTick;
            t.targetSpeed = (float) train.targetSpeed;

            try {
                t.maxSpeed = (float) train.maxSpeed();
            } catch (final RuntimeException ignored) {
                t.maxSpeed = 0.0f;
            }

            if (train.status != null) {
                t.navigationOk = !train.status.navigation;
                t.trackOk = !train.status.track;
                t.conductorOk = !train.status.conductor;
            }

            readPosition(train, t);
            readNavigation(train, byId, t);
            readSchedule(level, train, t);
            readCargo(train, t);
            readCharge(level, train, t);
            com.createstats.powertrack.PowerTrackService.fillRail(train, t);
            readFormation(train, t);

            if (t.dimension.isEmpty()) {
                final RailSnapshot.Station at = byId.get(
                        t.currentStation != null ? t.currentStation : t.destinationStation);
                if (at != null) {
                    t.dimension = at.dimension;
                    if (!t.positionValid) {
                        t.x = at.x;
                        t.z = at.z;
                    }
                }
            }
            snap.trains().add(t);
        }
    }

    private static void readPosition(Train train, RailSnapshot.TrainState t) {
        if (train.carriages == null || train.carriages.isEmpty() || train.graph == null) {
            return;
        }
        try {
            final Carriage lead = train.carriages.get(0);
            final TravellingPoint point = lead.getLeadingPoint();
            if (point != null) {
                final Vec3 pos = point.getPosition(train.graph);
                if (pos != null) {
                    t.x = pos.x;
                    t.z = pos.z;
                    t.positionValid = true;
                }
            }
            final List<ResourceKey<Level>> dims = lead.getPresentDimensions();
            if (!dims.isEmpty()) {
                t.dimension = dims.get(0).location().toString();
            }
        } catch (final RuntimeException ignored) {
        }
    }

    private static void readNavigation(Train train, Map<UUID, RailSnapshot.Station> byId,
                                       RailSnapshot.TrainState t) {
        if (train.currentStation != null && byId.containsKey(train.currentStation)) {
            t.currentStation = train.currentStation;
        }
        if (train.navigation == null) {
            return;
        }
        t.distanceToDestination = train.navigation.distanceToDestination;
        t.distanceStartedAt = train.navigation.distanceStartedAt;
        if (train.navigation.waitingForSignal != null) {
            t.waitingForSignal = true;
            t.ticksWaitingForSignal = train.navigation.ticksWaitingForSignal;
            t.distanceToSignal = train.navigation.distanceToSignal;
            readBlocker(train, t);
        }
        final GlobalStation destination = train.navigation.destination;
        if (destination != null) {
            t.destinationName = destination.name == null ? "" : destination.name;
            if (byId.containsKey(destination.getId())) {
                t.destinationStation = destination.getId();
            }
        }
    }

    private static void readBlocker(Train train, RailSnapshot.TrainState t) {
        try {
            final Pair<UUID, Boolean> waiting = train.navigation.waitingForSignal;
            if (waiting == null || train.graph == null) {
                return;
            }
            final SignalBoundary boundary = train.graph.getPoint(EdgePointType.SIGNAL,
                    waiting.getFirst());
            if (boundary == null || boundary.groups == null) {
                return;
            }

            final UUID ahead = boundary.groups.get(!Boolean.TRUE.equals(waiting.getSecond()));
            if (ahead == null) {
                return;
            }
            final SignalEdgeGroup group = Create.RAILWAYS.signalEdgeGroups.get(ahead);
            if (group == null || group.trains == null) {
                return;
            }
            for (final Train other : group.trains) {
                if (other != null && !other.id.equals(train.id)) {
                    t.blockedByTrain = other.id;
                    return;
                }
            }

            if (group.reserved != null) {
                t.blockedAt = "a reserved block";
            }
        } catch (final RuntimeException | LinkageError ignored) {
        }
    }

    private static void readSchedule(Level level, Train train, RailSnapshot.TrainState t) {
        final ScheduleRuntime runtime = train.runtime;
        if (runtime == null) {
            return;
        }
        t.schedulePaused = runtime.paused;
        t.scheduleCompleted = runtime.completed;
        t.currentEntry = runtime.currentEntry;
        t.scheduleTitle = runtime.currentTitle == null ? "" : runtime.currentTitle;
        t.phase = runtime.state == null ? RailSnapshot.SchedulePhase.NONE
                : RailSnapshot.SchedulePhase.of(runtime.state.ordinal() + 1);

        t.ticksInTransit = runtime.ticksInTransit;
        t.legPlannedTicks = -1;
        final List<Integer> predicted = runtime.predictionTicks;
        if (predicted != null && runtime.currentEntry >= 0 && runtime.currentEntry < predicted.size()) {
            final Integer planned = predicted.get(runtime.currentEntry);
            // -1 untimed, -2 unreachable. neither is lateness

            if (planned != null && planned > 0) {
                t.legPlannedTicks = planned;
            }
        }

        final Schedule schedule = runtime.getSchedule();
        if (schedule == null) {
            return;
        }
        t.hasSchedule = true;
        for (final ScheduleEntry entry : schedule.entries) {
            final ScheduleInstruction instruction = entry.instruction;
            if (instruction instanceof DestinationInstruction destination) {
                final String filter = destination.getFilter();
                if (filter != null && !filter.isEmpty()) {
                    t.stops.add(filter);
                }
            }
        }

        try {
            t.waitingStatus = runtime.getWaitingStatus(level).getString();
        } catch (final RuntimeException ignored) {
            t.waitingStatus = "";
        }
    }

    private static void readFormation(Train train, RailSnapshot.TrainState t) {
        if (train.carriages == null || train.carriages.isEmpty()) {
            return;
        }
        float length = 0.0f;
        try {
            for (int i = 0; i < train.carriages.size() && i < MAX_CARS; i++) {
                final Carriage carriage = train.carriages.get(i);
                final RailSnapshot.Car car = new RailSnapshot.Car();
                car.spacing = carriage.bogeySpacing;
                car.blocked = carriage.blocked;
                car.stalled = carriage.stalled;
                car.twoBogeys = carriage.isOnTwoBogeys();
                car.conductor = carriage.presentConductors != null
                        && carriage.presentConductors.either(Boolean::booleanValue);
                car.portal = carriage.presentInMultipleDimensions();
                readCarStorage(carriage, car);
                t.cars.add(car);

                length += Math.max(1, carriage.bogeySpacing);
                if (train.carriageSpacing != null && i < train.carriageSpacing.size()) {
                    final Integer gap = train.carriageSpacing.get(i);
                    if (gap != null) {
                        length += gap;
                    }
                }
            }
        } catch (final RuntimeException | LinkageError ignored) {
        }
        t.length = length;
    }

    private static void readCarStorage(Carriage carriage, RailSnapshot.Car car) {
        if (carriage.storage == null) {
            return;
        }
        try {
            final IItemHandlerModifiable mounted = carriage.storage.getMountedItems();
            car.slots = mounted.getSlots();
            for (int slot = 0; slot < car.slots; slot++) {
                final ItemStack stack = mounted.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    car.usedSlots++;
                    car.items += stack.getCount();
                }
            }
            final IFluidHandler tanks = carriage.storage.getFluids();
            for (int tank = 0; tank < tanks.getTanks(); tank++) {
                car.fluidCapacity += tanks.getTankCapacity(tank);
                car.fluid += tanks.getFluidInTank(tank).getAmount();
            }
        } catch (final RuntimeException | LinkageError ignored) {
        }
    }

    private static void readCharge(Level level, Train train, RailSnapshot.TrainState t) {
        try {
            final com.createstats.train.power.TrainChargeReading reading =
                    com.createstats.train.power.TrainChargeProbe.readAndRecord(level, train, "");
            if (reading != null && reading.hasStorage()) {
                t.feStored = reading.stored();
                t.feCapacity = reading.capacity();
                t.feReadTick = reading.tick();
            }
        } catch (final RuntimeException ignored) {
        }
    }

    private static void readCargo(Train train, RailSnapshot.TrainState t) {
        if (train.carriages == null) {
            return;
        }
        final Map<String, Integer> items = new HashMap<>();
        final Map<String, Integer> fluids = new HashMap<>();
        try {
            for (final Carriage carriage : train.carriages) {
                if (carriage.storage == null) {
                    continue;
                }
                final IItemHandlerModifiable mounted = carriage.storage.getMountedItems();
                for (int slot = 0; slot < mounted.getSlots(); slot++) {
                    final ItemStack stack = mounted.getStackInSlot(slot);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    t.itemCount += stack.getCount();
                    items.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
                }
                final IFluidHandler tanks = carriage.storage.getFluids();
                for (int tank = 0; tank < tanks.getTanks(); tank++) {
                    final FluidStack fluid = tanks.getFluidInTank(tank);
                    if (fluid.isEmpty()) {
                        continue;
                    }
                    t.fluidAmount += fluid.getAmount();
                    fluids.merge(fluidName(fluid), fluid.getAmount(), Integer::sum);
                }
            }
        } catch (final RuntimeException | LinkageError ignored) {
            return;
        }
        t.topItem = largest(items);
        t.topFluid = largest(fluids);
    }

    private static String fluidName(FluidStack fluid) {
        final var key = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        return key == null ? "fluid" : key.getPath().replace('_', ' ');
    }

    private static String largest(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static void readArrivals(RailSnapshot snap) {
        try {
            for (final Map.Entry<String, Collection<GlobalTrainDisplayData.TrainDeparturePrediction>>
                    entry : GlobalTrainDisplayData.statusByDestination.entrySet()) {
                for (final GlobalTrainDisplayData.TrainDeparturePrediction p : entry.getValue()) {
                    if (p == null || p.train == null) {
                        continue;
                    }
                    final RailSnapshot.Arrival a = new RailSnapshot.Arrival();
                    a.trainId = p.train.id;
                    a.trainName = p.train.name == null ? "Train" : p.train.name.getString();
                    a.station = p.destination == null ? entry.getKey() : p.destination;
                    a.scheduleTitle = p.scheduleTitle == null ? "" : p.scheduleTitle.getString();
                    a.ticks = p.ticks;
                    snap.arrivals().add(a);
                }
            }
        } catch (final LinkageError | RuntimeException ignored) {
            return;
        }

        snap.arrivals().sort(Comparator
                .comparingInt((RailSnapshot.Arrival a) -> a.ticks < 0 ? Integer.MAX_VALUE
                        : a.ticks < 200 ? 0 : a.ticks)
                .thenComparing(a -> a.trainName));
    }

    private static String ownerName(Level level, UUID owner) {
        if (owner == null) {
            return "";
        }
        final MinecraftServer server = level.getServer();
        if (server != null) {
            final GameProfileCache cache = server.getProfileCache();
            if (cache != null) {
                final Optional<GameProfile> profile = cache.get(owner);
                if (profile.isPresent()) {
                    return profile.get().getName();
                }
            }
        }
        return owner.toString().substring(0, 8);
    }
}
