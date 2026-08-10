package com.createstats.pathfinder.model;

import com.createstats.pathfinder.util.PathfinderNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record RouteCandidate(
    UUID routeId,
    String routeName,
    double routeCost,
    double estimatedDistance,
    float confidence,
    int waypointCount,
    int hazardFlags,
    RiskPreset riskPreset
) {
    public static final int SCHEMA_VERSION = 1;

    public RouteCandidate {
        if (routeId == null) routeId = UUID.randomUUID();
        routeName = PathfinderNbt.safeText(routeName, PathfinderNbt.MAX_ROUTE_NAME_LENGTH);
        routeCost = Math.max(0.0, routeCost);
        estimatedDistance = Math.max(0.0, estimatedDistance);
        confidence = Math.max(0f, Math.min(1f, confidence));
        waypointCount = Math.max(0, waypointCount);
        riskPreset = riskPreset == null ? RiskPreset.BALANCED : riskPreset;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putUUID("RouteId", routeId);
        tag.putString("RouteName", routeName);
        tag.putDouble("RouteCost", routeCost);
        tag.putDouble("EstimatedDistance", estimatedDistance);
        tag.putFloat("Confidence", confidence);
        tag.putInt("WaypointCount", waypointCount);
        tag.putInt("HazardFlags", hazardFlags);
        tag.putString("RiskPreset", riskPreset.name());
        return tag;
    }

    public static RouteCandidate load(CompoundTag tag) {
        if (tag == null) return new RouteCandidate(UUID.randomUUID(), "", 0.0, 0.0, 0f, 0, 0, RiskPreset.BALANCED);
        return new RouteCandidate(PathfinderNbt.readUuidOrRandom(tag, "RouteId"), tag.getString("RouteName"),
            tag.getDouble("RouteCost"), tag.getDouble("EstimatedDistance"), tag.getFloat("Confidence"),
            tag.getInt("WaypointCount"), tag.getInt("HazardFlags"),
            RiskPreset.safeValueOf(tag.getString("RiskPreset"), RiskPreset.BALANCED));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(routeId);
        buf.writeUtf(routeName, PathfinderNbt.MAX_ROUTE_NAME_LENGTH);
        buf.writeDouble(routeCost);
        buf.writeDouble(estimatedDistance);
        buf.writeFloat(confidence);
        buf.writeVarInt(waypointCount);
        buf.writeVarInt(hazardFlags);
        buf.writeEnum(riskPreset);
    }

    public static RouteCandidate decode(FriendlyByteBuf buf) {
        return new RouteCandidate(buf.readUUID(), buf.readUtf(PathfinderNbt.MAX_ROUTE_NAME_LENGTH),
            buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
            buf.readEnum(RiskPreset.class));
    }
}