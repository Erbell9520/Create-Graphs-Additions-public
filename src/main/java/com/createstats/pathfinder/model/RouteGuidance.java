package com.createstats.pathfinder.model;

import com.createstats.pathfinder.util.PathfinderNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public record RouteGuidance(
    UUID routeId,
    String routeName,
    String sourceScannerId,
    float confidence,
    List<Vec3> waypoints,
    List<Float> targetSpeeds,
    List<Vec3> safetyStops,
    double routeCost,
    long nextReplanTick,
    int hazardFlags
) {
    public static final int SCHEMA_VERSION = 1;

    public RouteGuidance {
        if (routeId == null) routeId = UUID.randomUUID();
        routeName = PathfinderNbt.safeText(routeName, PathfinderNbt.MAX_ROUTE_NAME_LENGTH);
        sourceScannerId = PathfinderNbt.safeText(sourceScannerId, PathfinderNbt.MAX_ROUTE_NAME_LENGTH);
        confidence = Math.max(0f, Math.min(1f, confidence));
        waypoints = List.copyOf(waypoints == null ? List.of() : waypoints);
        if (waypoints.size() > PathfinderNbt.MAX_ROUTE_POINTS) {
            waypoints = List.copyOf(waypoints.subList(0, PathfinderNbt.MAX_ROUTE_POINTS));
        }
        targetSpeeds = List.copyOf(targetSpeeds == null ? List.of() : targetSpeeds);
        if (targetSpeeds.size() > PathfinderNbt.MAX_ROUTE_POINTS) {
            targetSpeeds = List.copyOf(targetSpeeds.subList(0, PathfinderNbt.MAX_ROUTE_POINTS));
        }
        safetyStops = List.copyOf(safetyStops == null ? List.of() : safetyStops);
        if (safetyStops.size() > PathfinderNbt.MAX_ROUTE_POINTS) {
            safetyStops = List.copyOf(safetyStops.subList(0, PathfinderNbt.MAX_ROUTE_POINTS));
        }
        routeCost = Math.max(0.0, routeCost);
        nextReplanTick = Math.max(0L, nextReplanTick);
    }

    public RouteCandidate toCandidate() {
        return new RouteCandidate(routeId, routeName, routeCost, estimatedDistance(), confidence,
            waypoints.size(), hazardFlags, RiskPreset.BALANCED);
    }

    public double estimatedDistance() {
        if (waypoints.size() < 2) return 0.0;
        double distance = 0.0;
        for (int i = 1; i < waypoints.size(); i++) {
            distance += waypoints.get(i - 1).distanceTo(waypoints.get(i));
        }
        return distance;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putUUID("RouteId", routeId);
        tag.putString("RouteName", routeName);
        tag.putString("SourceScannerId", sourceScannerId);
        tag.putFloat("Confidence", confidence);
        tag.put("Waypoints", PathfinderNbt.writeVec3List(waypoints, PathfinderNbt.MAX_ROUTE_POINTS));
        tag.put("TargetSpeeds", PathfinderNbt.writeFloatList(targetSpeeds, PathfinderNbt.MAX_ROUTE_POINTS));
        tag.put("SafetyStops", PathfinderNbt.writeVec3List(safetyStops, PathfinderNbt.MAX_ROUTE_POINTS));
        tag.putDouble("RouteCost", routeCost);
        tag.putLong("NextReplanTick", nextReplanTick);
        tag.putInt("HazardFlags", hazardFlags);
        return tag;
    }

    public static RouteGuidance load(CompoundTag tag) {
        if (tag == null) {
            return new RouteGuidance(UUID.randomUUID(), "", "", 0f, List.of(), List.of(), List.of(), 0.0, 0L, 0);
        }
        return new RouteGuidance(PathfinderNbt.readUuidOrRandom(tag, "RouteId"),
            tag.getString("RouteName"), tag.getString("SourceScannerId"), tag.getFloat("Confidence"),
            PathfinderNbt.readVec3List(tag, "Waypoints", PathfinderNbt.MAX_ROUTE_POINTS),
            PathfinderNbt.readFloatList(tag, "TargetSpeeds", PathfinderNbt.MAX_ROUTE_POINTS),
            PathfinderNbt.readVec3List(tag, "SafetyStops", PathfinderNbt.MAX_ROUTE_POINTS),
            tag.getDouble("RouteCost"), tag.getLong("NextReplanTick"), tag.getInt("HazardFlags"));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(routeId);
        buf.writeUtf(routeName, PathfinderNbt.MAX_ROUTE_NAME_LENGTH);
        buf.writeUtf(sourceScannerId, PathfinderNbt.MAX_ROUTE_NAME_LENGTH);
        buf.writeFloat(confidence);
        PathfinderNbt.writeVec3List(buf, waypoints, PathfinderNbt.MAX_ROUTE_POINTS);
        PathfinderNbt.writeFloatList(buf, targetSpeeds, PathfinderNbt.MAX_ROUTE_POINTS);
        PathfinderNbt.writeVec3List(buf, safetyStops, PathfinderNbt.MAX_ROUTE_POINTS);
        buf.writeDouble(routeCost);
        buf.writeVarLong(nextReplanTick);
        buf.writeVarInt(hazardFlags);
    }

    public static RouteGuidance decode(FriendlyByteBuf buf) {
        return new RouteGuidance(buf.readUUID(), buf.readUtf(PathfinderNbt.MAX_ROUTE_NAME_LENGTH),
            buf.readUtf(PathfinderNbt.MAX_ROUTE_NAME_LENGTH), buf.readFloat(),
            PathfinderNbt.readVec3List(buf, PathfinderNbt.MAX_ROUTE_POINTS),
            PathfinderNbt.readFloatList(buf, PathfinderNbt.MAX_ROUTE_POINTS),
            PathfinderNbt.readVec3List(buf, PathfinderNbt.MAX_ROUTE_POINTS),
            buf.readDouble(), buf.readVarLong(), buf.readVarInt());
    }
}
