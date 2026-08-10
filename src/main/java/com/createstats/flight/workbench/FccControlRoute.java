package com.createstats.flight.workbench;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record FccControlRoute(
        UUID id,
        UUID targetGroupId,
        FccOutputType outputType,
        double scale,
        double offset,
        double minClamp,
        double maxClamp,
        boolean invert,
        boolean reverseAllowed,
        @Nullable Double angleLimitOverride,
        @Nullable Double rateLimitOverride,
        String note,
        FccRouteKind routeKind,
        boolean systemOwned
) {

    public FccControlRoute {
        if (id == null) throw new IllegalArgumentException("id");
        if (targetGroupId == null) throw new IllegalArgumentException("targetGroupId");
        if (outputType == null) throw new IllegalArgumentException("outputType");
        if (routeKind == null) routeKind = FccRouteKind.CUSTOM;
        note = note == null ? "" : note;
    }

    public FccControlRoute(UUID id, UUID targetGroupId, FccOutputType outputType,
                           double scale, double offset, double minClamp, double maxClamp,
                           boolean invert, boolean reverseAllowed,
                           @Nullable Double angleLimitOverride, @Nullable Double rateLimitOverride,
                           String note) {
        this(id, targetGroupId, outputType, scale, offset, minClamp, maxClamp, invert, reverseAllowed,
                angleLimitOverride, rateLimitOverride, note, FccRouteKind.CUSTOM, false);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("TargetGroupId", targetGroupId);
        tag.putString("OutputType", outputType.name());
        tag.putDouble("Scale", scale);
        tag.putDouble("Offset", offset);
        tag.putDouble("MinClamp", minClamp);
        tag.putDouble("MaxClamp", maxClamp);
        tag.putBoolean("Invert", invert);
        tag.putBoolean("ReverseAllowed", reverseAllowed);
        if (angleLimitOverride != null) tag.putDouble("AngleLimitOverride", angleLimitOverride);
        if (rateLimitOverride != null) tag.putDouble("RateLimitOverride", rateLimitOverride);
        if (!note.isEmpty()) tag.putString("Note", note);
        tag.putString("RouteKind", routeKind.name());
        tag.putBoolean("SystemOwned", systemOwned);
        return tag;
    }

    @Nullable
    public static FccControlRoute fromNbt(@Nullable CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id") || !tag.hasUUID("TargetGroupId")) return null;
        UUID id = tag.getUUID("Id");
        UUID targetGroupId = tag.getUUID("TargetGroupId");
        FccOutputType outputType = FccOutputType.safeValueOf(tag.getString("OutputType"), FccOutputType.RPM_DELTA);
        double scale = tag.contains("Scale") ? tag.getDouble("Scale") : 1.0;
        double offset = tag.getDouble("Offset");
        double minClamp = tag.contains("MinClamp") ? tag.getDouble("MinClamp") : -1.0;
        double maxClamp = tag.contains("MaxClamp") ? tag.getDouble("MaxClamp") : 1.0;
        boolean invert = tag.getBoolean("Invert");
        boolean reverseAllowed = tag.getBoolean("ReverseAllowed");
        Double angleLimitOverride = tag.contains("AngleLimitOverride") ? tag.getDouble("AngleLimitOverride") : null;
        Double rateLimitOverride = tag.contains("RateLimitOverride") ? tag.getDouble("RateLimitOverride") : null;
        String note = tag.getString("Note");
        FccRouteKind routeKind = FccRouteKind.safeValueOf(tag.getString("RouteKind"), FccRouteKind.CUSTOM);
        boolean systemOwned = tag.getBoolean("SystemOwned");
        return new FccControlRoute(id, targetGroupId, outputType, scale, offset, minClamp, maxClamp,
            invert, reverseAllowed, angleLimitOverride, rateLimitOverride, note, routeKind, systemOwned);
    }
}