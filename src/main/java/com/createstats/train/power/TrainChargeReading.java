package com.createstats.train.power;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;

// stored with its age, a stale number drawn live is the worst kind of wrong
public record TrainChargeReading(int stored, int capacity, long tick, String where) {
    public TrainChargeReading {
        stored = Math.max(0, stored);
        capacity = Math.max(0, capacity);
        where = where == null ? "" : where;
    }

    public boolean hasStorage() {
        return capacity > 0;
    }

    public double fraction() {
        return capacity <= 0 ? 0.0 : Math.min(1.0, (double) stored / capacity);
    }

    public int percent() {
        return (int) Math.round(fraction() * 100.0);
    }

    public long ageTicks(long now) {
        return Math.max(0L, now - tick);
    }

    public boolean isLive(long now) {
        return ageTicks(now) <= 40L;
    }

    public String ageLabel(long now) {
        final long age = ageTicks(now);
        if (age <= 40L) {
            return "live";
        }
        final long seconds = age / 20L;
        if (seconds < 60L) {
            return seconds + "s ago";
        }
        if (seconds < 3600L) {
            return (seconds / 60L) + " min ago";
        }
        return (seconds / 3600L) + " h ago";
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("S", stored);
        tag.putInt("C", capacity);
        tag.putLong("T", tick);
        if (!where.isEmpty()) {
            tag.putString("W", where);
        }
        return tag;
    }

    public static TrainChargeReading load(CompoundTag tag) {
        return new TrainChargeReading(tag.getInt("S"), tag.getInt("C"), tag.getLong("T"), tag.getString("W"));
    }

    public static void encodeNullable(FriendlyByteBuf buf, @Nullable TrainChargeReading reading) {
        buf.writeBoolean(reading != null);
        if (reading != null) {
            buf.writeVarInt(reading.stored());
            buf.writeVarInt(reading.capacity());
            buf.writeLong(reading.tick());
            buf.writeUtf(reading.where());
        }
    }

    @Nullable
    public static TrainChargeReading decodeNullable(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        return new TrainChargeReading(buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readUtf());
    }
}
