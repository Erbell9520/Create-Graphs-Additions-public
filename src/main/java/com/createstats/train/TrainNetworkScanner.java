package com.createstats.train;

import com.createstats.CreateStats;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.MinecraftServer;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.content.trains.schedule.Schedule;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceKey;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import java.util.HashMap;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import java.util.Collection;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashSet;
import com.simibubi.create.Create;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class TrainNetworkScanner
{
    private static final Logger LOG;
    private static int lastStationDiagnosticHash;

    public static TrainMapData scan(Level level) {
        final TrainMapData data = new TrainMapData();
        data.setServerTick(level.getGameTime());
        try {
            final GlobalRailwayManager railways = Create.RAILWAYS;
            if (railways == null) {
                return data;
            }
            final Set<UUID> seenStations = new HashSet<UUID>();
            for (TrackGraph graph : new ArrayList<TrackGraph>(railways.trackNetworks.values())) {
                final Collection<GlobalStation> stations = graph.getPoints(EdgePointType.STATION);
                for (GlobalStation station : stations) {
                    if (!seenStations.add(station.getId())) {
                        continue;
                    }
                    String dim = "overworld";
                    double sx = 0.0;
                    double sz = 0.0;
                    final Vec3 graphPos = resolveStationWorldPosition(graph, station);
                    if (graphPos != null) {
                        sx = graphPos.x;
                        sz = graphPos.z;
                    } else if (station.getBlockEntityPos() != null) {
                        sx = station.getBlockEntityPos().getX();
                        sz = station.getBlockEntityPos().getZ();
                    }
                    if (station.edgeLocation != null && station.edgeLocation.getFirst() != null && station.edgeLocation.getFirst().dimension != null) {
                        dim = station.edgeLocation.getFirst().dimension.location().toString();
                    } else if (station.getBlockEntityDimension() != null) {
                        dim = station.getBlockEntityDimension().location().toString();
                    }
                    data.getStations().add(new TrainMapData.StationEntry(station.getId(), (station.name != null) ? station.name : "Unknown", sx, sz, dim, station.assembling));
                }
            }
            final Map<UUID, String> stationNameById = new HashMap<UUID, String>();
            final Map<UUID, TrainMapData.StationEntry> stationById = new HashMap<UUID, TrainMapData.StationEntry>();
            for (TrainMapData.StationEntry se : data.getStations()) {
                stationNameById.put(se.id, se.name);
                stationById.put(se.id, se);
            }
            for (Train train : new ArrayList<Train>(railways.trains.values())) {
                if (train.invalid) {
                    continue;
                }
                final String trainName = (train.name != null) ? train.name.getString() : "Train";
                final String ownerName = resolveOwnerName(level, train.owner);
                double tx = 0.0;
                double tz = 0.0;
                String trainDim = "overworld";
                final float signedSpeed = (float)train.speed;
                final float speed = (float)Math.abs(train.speed);
                String nextDest = "";
                final boolean stopped = train.currentStation != null;
                String currentStationName = "";
                UUID currentStationId = null;
                TrainMapData.StationEntry currentStation = null;
                boolean waitingForSignal = false;
                int ticksWaitingForSignal = 0;
                boolean positionValid = false;
                final List<String> scheduleStops = new ArrayList<String>();
                if (train.currentStation != null) {
                    currentStation = stationById.get(train.currentStation);
                    if (currentStation != null) {
                        currentStationId = currentStation.id;
                    }
                    final String sName = stationNameById.get(train.currentStation);
                    if (sName != null) {
                        currentStationName = sName;
                    }
                }
                if (train.navigation != null && train.navigation.waitingForSignal != null) {
                    waitingForSignal = true;
                    ticksWaitingForSignal = train.navigation.ticksWaitingForSignal;
                }
                if (!train.carriages.isEmpty() && train.graph != null) {
                    try {
                        final Carriage leadCarriage = train.carriages.get(0);
                        final TravellingPoint leadPoint = leadCarriage.getLeadingPoint();
                        if (leadPoint != null) {
                            final Vec3 pos = leadPoint.getPosition(train.graph);
                            if (pos != null) {
                                tx = pos.x;
                                tz = pos.z;
                                positionValid = true;
                            }
                        }
                        final List<ResourceKey<Level>> dims = leadCarriage.getPresentDimensions();
                        if (!dims.isEmpty()) {
                            trainDim = dims.get(0).location().toString();
                        }
                    }
                    catch (Exception ex2) {}
                }
                if (!positionValid && currentStation != null) {
                    tx = currentStation.x;
                    tz = currentStation.z;
                    trainDim = currentStation.dimension;
                    positionValid = true;
                }
                if (train.navigation != null && train.navigation.destination != null) {
                    nextDest = ((train.navigation.destination.name != null) ? train.navigation.destination.name : "");
                }
                if (train.runtime != null && train.runtime.schedule != null) {
                    final Schedule schedule = train.runtime.schedule;
                    for (ScheduleEntry entry : schedule.entries) {
                        final ScheduleInstruction instruction = entry.instruction;
                        if (instruction instanceof DestinationInstruction destInst) {
                            final String filter = destInst.getFilter();
                            if (filter == null || filter.isEmpty()) {
                                continue;
                            }
                            scheduleStops.add(filter);
                        }
                    }
                }
                final TrainMapData.StationEntry destinationStation = resolveStationByFilter(data.getStations(), nextDest, trainDim);
                if (!positionValid && destinationStation != null) {
                    trainDim = destinationStation.dimension;
                }
                final TrainMapData.StationEntry previousStation = resolvePreviousScheduleStation(scheduleStops, destinationStation, nextDest, data.getStations(), trainDim);
                UUID destinationStationId = destinationStation != null ? destinationStation.id : null;
                UUID previousStationId = previousStation != null ? previousStation.id : null;
                UUID activeFromId = previousStationId;
                if (currentStationId != null && destinationStationId != null && !currentStationId.equals(destinationStationId)) {
                    activeFromId = currentStationId;
                }
                UUID activeToId = destinationStationId;
                final float segmentProgress = computeSegmentProgress(tx, tz, activeFromId != null ? stationById.get(activeFromId) : null, activeToId != null ? stationById.get(activeToId) : null, positionValid);
                final String routeKey = buildRouteKey(scheduleStops, data.getStations(), trainDim);
                data.getTrains().add(new TrainMapData.TrainEntry(train.id, trainName, ownerName, tx, tz, trainDim, speed, nextDest, scheduleStops, stopped, currentStationName, waitingForSignal, ticksWaitingForSignal, positionValid, currentStationId, previousStationId, destinationStationId, activeFromId, activeToId, segmentProgress, signedSpeed, routeKey));
            }
            logStationDiagnostics(data);
        }
        catch (Exception e) {
            TrainNetworkScanner.LOG.warn("Train network scan failed: {}", e.getMessage());
        }
        return data;
    }

    private static String resolveOwnerName(Level level, UUID ownerUUID) {
        if (ownerUUID == null) {
            return "Unknown";
        }
        final MinecraftServer server = level.getServer();
        if (server != null) {
            final GameProfileCache profile = server.getProfileCache();
            if (profile != null) {
                final Optional<GameProfile> opt = profile.get(ownerUUID);
                if (opt.isPresent()) {
                    return opt.get().getName();
                }
            }
        }
        return ownerUUID.toString().substring(0, 8);
    }

    private static Vec3 resolveStationWorldPosition(TrackGraph graph, GlobalStation station) {
        if (graph == null || station == null || station.edgeLocation == null || station.edgeLocation.getFirst() == null || station.edgeLocation.getSecond() == null) {
            return null;
        }
        final TrackNode firstNode = graph.locateNode(station.edgeLocation.getFirst());
        final TrackNode secondNode = graph.locateNode(station.edgeLocation.getSecond());
        if (firstNode == null || secondNode == null) {
            return null;
        }
        TrackEdge edge = graph.getConnection(Couple.create(firstNode, secondNode));
        if (edge == null) {
            edge = graph.getConnection(Couple.create(secondNode, firstNode));
        }
        if (edge == null) {
            return firstNode.getLocation().getLocation().lerp(secondNode.getLocation().getLocation(), 0.5);
        }
        final double edgeLength = edge.getLength();
        if (edgeLength <= 1.0E-4) {
            return firstNode.getLocation().getLocation().lerp(secondNode.getLocation().getLocation(), 0.5);
        }
        final double distance = station.getLocationOn(edge);
        return edge.getPosition(graph, distance / edgeLength);
    }

    private static TrainMapData.StationEntry resolveStationByFilter(List<TrainMapData.StationEntry> stations, String filter, String preferredDimension) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        TrainMapData.StationEntry fallback = null;
        for (TrainMapData.StationEntry station : orderedStations(stations)) {
            if (filter.equals(station.name)) {
                if (sameDimension(station.dimension, preferredDimension)) {
                    return station;
                }
                if (fallback == null) {
                    fallback = station;
                }
            }
        }
        if (fallback != null) {
            return fallback;
        }
        for (TrainMapData.StationEntry station : orderedStations(stations)) {
            if (TrainMapData.matchesStationFilter(filter, station.name)) {
                if (sameDimension(station.dimension, preferredDimension)) {
                    return station;
                }
                if (fallback == null) {
                    fallback = station;
                }
            }
        }
        return fallback;
    }

    private static TrainMapData.StationEntry resolvePreviousScheduleStation(List<String> scheduleStops, TrainMapData.StationEntry destinationStation, String destinationFilter, List<TrainMapData.StationEntry> stations, String preferredDimension) {
        if (scheduleStops == null || scheduleStops.size() < 2) {
            return null;
        }
        int destinationIndex = -1;
        for (int i = 0; i < scheduleStops.size(); ++i) {
            final String filter = scheduleStops.get(i);
            final TrainMapData.StationEntry station = resolveStationByFilter(stations, filter, preferredDimension);
            if (destinationStation != null && station != null && destinationStation.id.equals(station.id)) {
                destinationIndex = i;
                break;
            }
            if (destinationFilter != null && !destinationFilter.isEmpty() && TrainMapData.matchesStationFilter(filter, destinationFilter)) {
                destinationIndex = i;
                break;
            }
        }
        if (destinationIndex < 0) {
            return null;
        }
        final int previousIndex = (destinationIndex - 1 + scheduleStops.size()) % scheduleStops.size();
        return resolveStationByFilter(stations, scheduleStops.get(previousIndex), preferredDimension);
    }

    private static float computeSegmentProgress(double trainX, double trainZ, TrainMapData.StationEntry from, TrainMapData.StationEntry to, boolean positionValid) {
        if (!positionValid || from == null || to == null || from.id.equals(to.id)) {
            return 0.0f;
        }
        final double dx = to.x - from.x;
        final double dz = to.z - from.z;
        final double lengthSq = dx * dx + dz * dz;
        if (lengthSq < 1.0) {
            return 0.5f;
        }
        final double tx = trainX - from.x;
        final double tz = trainZ - from.z;
        return (float)Math.max(0.0, Math.min(1.0, (tx * dx + tz * dz) / lengthSq));
    }

    private static String buildRouteKey(List<String> scheduleStops, List<TrainMapData.StationEntry> stations, String preferredDimension) {
        if (scheduleStops == null || scheduleStops.isEmpty()) {
            return "";
        }
        final StringBuilder key = new StringBuilder();
        for (String stop : scheduleStops) {
            if (!key.isEmpty()) {
                key.append('|');
            }
            final TrainMapData.StationEntry station = resolveStationByFilter(stations, stop, preferredDimension);
            key.append(station != null ? station.id : stop);
        }
        return key.toString();
    }

    private static List<TrainMapData.StationEntry> orderedStations(List<TrainMapData.StationEntry> stations) {
        final ArrayList<TrainMapData.StationEntry> ordered = new ArrayList<TrainMapData.StationEntry>(stations);
        ordered.sort((a, b) -> {
            final int nameCompare = a.name.compareToIgnoreCase(b.name);
            return nameCompare != 0 ? nameCompare : a.id.compareTo(b.id);
        });
        return ordered;
    }

    private static boolean sameDimension(String a, String b) {
        return a != null && b != null && !a.isEmpty() && !b.isEmpty() && a.equals(b);
    }

    private static void logStationDiagnostics(TrainMapData data) {
        if (data.getStations().isEmpty()) {
            return;
        }
        final HashSet<String> uniqueCoords = new HashSet<String>();
        final HashSet<String> uniqueNames = new HashSet<String>();
        final HashMap<String, Integer> duplicateNames = new HashMap<String, Integer>();
        int zeroCoords = 0;
        int duplicateNameCount = 0;
        double minX = Double.MAX_VALUE;
        double maxX = -1.7976931348623157E308;
        double minZ = Double.MAX_VALUE;
        double maxZ = -1.7976931348623157E308;
        final StringBuilder sample = new StringBuilder();
        int sampleCount = 0;
        int hash = data.getStations().size() * 31 + data.getTrains().size();
        for (TrainMapData.StationEntry station : data.getStations()) {
            final String coordKey = station.dimension + "|" + station.x + "|" + station.z;
            uniqueCoords.add(coordKey);
            minX = Math.min(minX, station.x);
            maxX = Math.max(maxX, station.x);
            minZ = Math.min(minZ, station.z);
            maxZ = Math.max(maxZ, station.z);
            if (station.x == 0.0 && station.z == 0.0) {
                ++zeroCoords;
            }
            if (!uniqueNames.add(station.name)) {
                duplicateNameCount = duplicateNames.merge(station.name, 2, Integer::sum);
            }
            if (sampleCount < 6) {
                if (sampleCount > 0) {
                    sample.append(" | ");
                }
                sample.append(station.name).append("@").append((int)station.x).append(",").append((int)station.z).append("[").append(station.dimension).append("]");
                ++sampleCount;
            }
            hash = hash * 31 + station.id.hashCode();
            hash = hash * 31 + station.name.hashCode();
            hash = hash * 31 + Double.hashCode(station.x);
            hash = hash * 31 + Double.hashCode(station.z);
            hash = hash * 31 + station.dimension.hashCode();
        }
        if (hash == lastStationDiagnosticHash) {
            return;
        }
        lastStationDiagnosticHash = hash;
        final boolean suspicious = data.getStations().size() > 1 && (uniqueCoords.size() <= 1 || zeroCoords == data.getStations().size());
        final String summary = String.format("stations=%d uniqueCoords=%d zeroCoords=%d bounds=[%.1f..%.1f, %.1f..%.1f] dupNames=%d sample=%s",
            data.getStations().size(), uniqueCoords.size(), zeroCoords, minX, maxX, minZ, maxZ, duplicateNameCount, sample);
        if (suspicious) {
            CreateStats.LOGGER.warn("[TrainMapDebug] Suspicious station scan: {}", summary);
        } else {
            CreateStats.LOGGER.info("[TrainMapDebug] Station scan: {}", summary);
        }
    }

    static {
        LOG = LoggerFactory.getLogger((Class)TrainNetworkScanner.class);
        lastStationDiagnosticHash = 0;
    }
}
