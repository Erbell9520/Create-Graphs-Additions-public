package com.createstats.flight.workbench;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FccCompositeControl(
        UUID id,
        String label,
        FccWidgetType widgetType,
        boolean springCentered,
        double inputMin,
        double inputMax,
        double neutralValue,
        double deadzone,
        FccCurveType curveType,
        String animationTheme,
        boolean advancedMode,
        FccControlRole role,
        String zoneKey,
        boolean systemOwned,
        List<FccControlRoute> routes
) {
    public FccCompositeControl {
        if (id == null) throw new IllegalArgumentException("id");
        if (label == null || label.isEmpty()) throw new IllegalArgumentException("label");
        if (widgetType == null) throw new IllegalArgumentException("widgetType");
        if (curveType == null) throw new IllegalArgumentException("curveType");
        if (role == null) role = FccControlRole.CUSTOM;
        animationTheme = animationTheme == null ? "default" : animationTheme;
        zoneKey = zoneKey == null ? "" : zoneKey;
        routes = List.copyOf(routes == null ? List.of() : routes);
    }

    public FccCompositeControl(UUID id, String label, FccWidgetType widgetType, boolean springCentered,
                               double inputMin, double inputMax, double neutralValue, double deadzone,
                               FccCurveType curveType, String animationTheme, boolean advancedMode,
                               List<FccControlRoute> routes) {
        this(id, label, widgetType, springCentered, inputMin, inputMax, neutralValue, deadzone,
                curveType, animationTheme, advancedMode, FccControlRole.CUSTOM, "", false, routes);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Label", label);
        tag.putString("WidgetType", widgetType.name());
        tag.putBoolean("SpringCentered", springCentered);
        tag.putDouble("InputMin", inputMin);
        tag.putDouble("InputMax", inputMax);
        tag.putDouble("NeutralValue", neutralValue);
        tag.putDouble("Deadzone", deadzone);
        tag.putString("CurveType", curveType.name());
        tag.putString("AnimationTheme", animationTheme);
        tag.putBoolean("AdvancedMode", advancedMode);
        tag.putString("Role", role.name());
        if (!zoneKey.isEmpty()) tag.putString("ZoneKey", zoneKey);
        tag.putBoolean("SystemOwned", systemOwned);
        if (!routes.isEmpty()) {
            ListTag routeTags = new ListTag();
            for (FccControlRoute route : routes) routeTags.add(route.toNbt());
            tag.put("Routes", routeTags);
        }
        return tag;
    }

    @Nullable
    public static FccCompositeControl fromNbt(@Nullable CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id")) return null;
        UUID id = tag.getUUID("Id");
        String label = tag.getString("Label");
        if (label == null || label.isEmpty()) label = "Control";
        FccWidgetType widgetType = FccWidgetType.safeValueOf(tag.getString("WidgetType"), FccWidgetType.WHEEL);
        boolean springCentered = !tag.contains("SpringCentered") || tag.getBoolean("SpringCentered");
        double inputMin = tag.contains("InputMin") ? tag.getDouble("InputMin") : -1.0;
        double inputMax = tag.contains("InputMax") ? tag.getDouble("InputMax") : 1.0;
        double neutralValue = tag.contains("NeutralValue") ? tag.getDouble("NeutralValue") : 0.0;
        double deadzone = tag.contains("Deadzone") ? tag.getDouble("Deadzone") : 0.0;
        FccCurveType curveType = FccCurveType.safeValueOf(tag.getString("CurveType"), FccCurveType.LINEAR);
        String animationTheme = tag.getString("AnimationTheme");
        boolean advancedMode = tag.getBoolean("AdvancedMode");
        FccControlRole role = FccControlRole.safeValueOf(tag.getString("Role"), FccControlRole.CUSTOM);
        String zoneKey = tag.getString("ZoneKey");
        boolean systemOwned = tag.getBoolean("SystemOwned");
        List<FccControlRoute> routes = new ArrayList<>();
        if (tag.contains("Routes", Tag.TAG_LIST)) {
            ListTag routeTags = tag.getList("Routes", Tag.TAG_COMPOUND);
            for (int i = 0; i < routeTags.size(); i++) {
                FccControlRoute route = FccControlRoute.fromNbt(routeTags.getCompound(i));
                if (route != null) routes.add(route);
            }
        }
        return new FccCompositeControl(id, label, widgetType, springCentered, inputMin, inputMax,
                neutralValue, deadzone, curveType, animationTheme, advancedMode, role, zoneKey, systemOwned, routes);
    }
}
