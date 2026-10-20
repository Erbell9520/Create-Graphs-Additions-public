package com.createstats.pathfinder.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class PathfinderNbt {
    public static final int MAX_ROUTE_POINTS = 512;
    public static final int MAX_ROUTE_NAME_LENGTH = 128;
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;

    private PathfinderNbt() {
    }

    public static ListTag writeVec3List(List<Vec3> points, int maxPoints) {
        ListTag list = new ListTag();
        int limit = Math.min(points == null ? 0 : points.size(), Math.max(0, maxPoints));
        for (int i = 0; i < limit; i++) {
            Vec3 point = points.get(i);
            if (point == null) continue;
            CompoundTag tag = new CompoundTag();
            tag.putDouble("X", point.x);
            tag.putDouble("Y", point.y);
            tag.putDouble("Z", point.z);
            list.add(tag);
        }
        return list;
    }

    public static List<Vec3> readVec3List(CompoundTag tag, String key, int maxPoints) {
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) return List.of();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        int limit = Math.min(list.size(), Math.max(0, maxPoints));
        List<Vec3> points = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            CompoundTag point = list.getCompound(i);
            points.add(new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z")));
        }
        return List.copyOf(points);
    }

    public static void writeVec3List(FriendlyByteBuf buf, List<Vec3> points, int maxPoints) {
        int limit = Math.min(points == null ? 0 : points.size(), Math.max(0, maxPoints));
        buf.writeVarInt(limit);
        for (int i = 0; i < limit; i++) {
            Vec3 point = points.get(i);
            buf.writeDouble(point == null ? 0.0 : point.x);
            buf.writeDouble(point == null ? 0.0 : point.y);
            buf.writeDouble(point == null ? 0.0 : point.z);
        }
    }

    public static List<Vec3> readVec3List(FriendlyByteBuf buf, int maxPoints) {
        int rawSize = readBoundedListSize(buf, maxPoints);
        int limit = Math.min(rawSize, Math.max(0, maxPoints));
        List<Vec3> points = new ArrayList<>(limit);
        for (int i = 0; i < rawSize; i++) {
            Vec3 point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            if (i < limit) points.add(point);
        }
        return List.copyOf(points);
    }

    public static ListTag writeFloatList(List<Float> values, int maxValues) {
        ListTag list = new ListTag();
        int limit = Math.min(values == null ? 0 : values.size(), Math.max(0, maxValues));
        for (int i = 0; i < limit; i++) {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("V", values.get(i) == null ? 0f : values.get(i));
            list.add(tag);
        }
        return list;
    }

    public static List<Float> readFloatList(CompoundTag tag, String key, int maxValues) {
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) return List.of();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        int limit = Math.min(list.size(), Math.max(0, maxValues));
        List<Float> values = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            values.add(list.getCompound(i).getFloat("V"));
        }
        return List.copyOf(values);
    }

    public static void writeFloatList(FriendlyByteBuf buf, List<Float> values, int maxValues) {
        int limit = Math.min(values == null ? 0 : values.size(), Math.max(0, maxValues));
        buf.writeVarInt(limit);
        for (int i = 0; i < limit; i++) {
            buf.writeFloat(values.get(i) == null ? 0f : values.get(i));
        }
    }

    public static List<Float> readFloatList(FriendlyByteBuf buf, int maxValues) {
        int rawSize = readBoundedListSize(buf, maxValues);
        int limit = Math.min(rawSize, Math.max(0, maxValues));
        List<Float> values = new ArrayList<>(limit);
        for (int i = 0; i < rawSize; i++) {
            float value = buf.readFloat();
            if (i < limit) values.add(value);
        }
        return List.copyOf(values);
    }

    public static byte[] safePayload(byte[] payload) {
        if (payload == null || payload.length == 0) return new byte[0];
        return Arrays.copyOf(payload, Math.min(payload.length, MAX_PAYLOAD_BYTES));
    }

    public static String safeText(String text, int maxLength) {
        if (text == null || text.isBlank()) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    public static UUID readUuidOrRandom(CompoundTag tag, String key) {
        return tag != null && tag.hasUUID(key) ? tag.getUUID(key) : UUID.randomUUID();
    }

    private static int readBoundedListSize(FriendlyByteBuf buf, int maxValues) {
        int rawSize = buf.readVarInt();
        int max = Math.max(0, maxValues);
        if (rawSize < 0 || rawSize > max) {
            throw new IllegalArgumentException("Pathfinder list size out of bounds: " + rawSize + " > " + max);
        }
        return rawSize;
    }
}
