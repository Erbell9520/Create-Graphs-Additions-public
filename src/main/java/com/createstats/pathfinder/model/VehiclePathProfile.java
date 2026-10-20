package com.createstats.pathfinder.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record VehiclePathProfile(
    float widthBlocks,
    float heightBlocks,
    int maxSafeStep,
    float maxSlope,
    float turnRadiusBlocks,
    float preferredSpeed,
    RiskPreset riskPreset
) {
    public static final int SCHEMA_VERSION = 1;

    public VehiclePathProfile {
        widthBlocks = clamp(widthBlocks, 1f, 16f);
        heightBlocks = clamp(heightBlocks, 1f, 16f);
        maxSafeStep = Math.max(0, Math.min(2, maxSafeStep));
        maxSlope = clamp(maxSlope, 0f, 4f);
        turnRadiusBlocks = clamp(turnRadiusBlocks, 1f, 64f);
        preferredSpeed = clamp(preferredSpeed, 0.5f, 64f);
        riskPreset = riskPreset == null ? RiskPreset.BALANCED : riskPreset;
    }

    public static VehiclePathProfile defaultOtomobil() {
        return new VehiclePathProfile(3f, 3f, 1, 1f, 6f, 8f, RiskPreset.BALANCED);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putFloat("WidthBlocks", widthBlocks);
        tag.putFloat("HeightBlocks", heightBlocks);
        tag.putInt("MaxSafeStep", maxSafeStep);
        tag.putFloat("MaxSlope", maxSlope);
        tag.putFloat("TurnRadiusBlocks", turnRadiusBlocks);
        tag.putFloat("PreferredSpeed", preferredSpeed);
        tag.putString("RiskPreset", riskPreset.name());
        return tag;
    }

    public static VehiclePathProfile load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return defaultOtomobil();
        VehiclePathProfile defaults = defaultOtomobil();
        return new VehiclePathProfile(
            tag.contains("WidthBlocks") ? tag.getFloat("WidthBlocks") : defaults.widthBlocks,
            tag.contains("HeightBlocks") ? tag.getFloat("HeightBlocks") : defaults.heightBlocks,
            tag.contains("MaxSafeStep") ? tag.getInt("MaxSafeStep") : defaults.maxSafeStep,
            tag.contains("MaxSlope") ? tag.getFloat("MaxSlope") : defaults.maxSlope,
            tag.contains("TurnRadiusBlocks") ? tag.getFloat("TurnRadiusBlocks") : defaults.turnRadiusBlocks,
            tag.contains("PreferredSpeed") ? tag.getFloat("PreferredSpeed") : defaults.preferredSpeed,
            RiskPreset.safeValueOf(tag.getString("RiskPreset"), defaults.riskPreset)
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(widthBlocks);
        buf.writeFloat(heightBlocks);
        buf.writeVarInt(maxSafeStep);
        buf.writeFloat(maxSlope);
        buf.writeFloat(turnRadiusBlocks);
        buf.writeFloat(preferredSpeed);
        buf.writeEnum(riskPreset);
    }

    public static VehiclePathProfile decode(FriendlyByteBuf buf) {
        return new VehiclePathProfile(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readEnum(RiskPreset.class));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
