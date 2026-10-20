package com.createstats.repeller;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

// fires dozens of times a tick, nearly all nowhere near a repeller. bucket, identity test, done
public final class SpawnIndex<T> {
    public record Entry<T>(long id, T type, RepellerShape shape, double x, double y, double z, double radius,
                           AtomicLong repelled) {
    }

    @SuppressWarnings("rawtypes")
    private static final SpawnIndex EMPTY = new SpawnIndex<>(new Long2ObjectOpenHashMap<>(), 0, List.of());

    private final Long2ObjectOpenHashMap<Entry<T>[]> byChunk;
    private final int size;
    private final List<Entry<T>> entries;

    private final Object[] all;

    private final Long2ObjectOpenHashMap<Entry<T>> byId;

    private SpawnIndex(Long2ObjectOpenHashMap<Entry<T>[]> byChunk, int size, List<Entry<T>> entries) {
        this.byChunk = byChunk;
        this.size = size;
        this.entries = entries;
        this.all = entries.toArray();
        this.byId = new Long2ObjectOpenHashMap<>(entries.size());
        for (Entry<T> e : entries) {
            byId.put(e.id(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> SpawnIndex<T> empty() {
        return (SpawnIndex<T>) EMPTY;
    }

    @SuppressWarnings("unchecked")
    public static <T> SpawnIndex<T> of(Collection<Entry<T>> entries) {
        final List<Entry<T>> live = new ArrayList<>();
        for (Entry<T> e : entries) {
            if (e.radius() > 0.0 && e.type() != null) {
                live.add(e);
            }
        }
        if (live.isEmpty()) {
            return empty();
        }
        final Long2ObjectOpenHashMap<List<Entry<T>>> building = new Long2ObjectOpenHashMap<>();
        for (Entry<T> e : live) {
            for (long key : RepellerShape.chunksTouched(e.x(), e.z(), e.radius())) {
                building.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }
        }
        final Long2ObjectOpenHashMap<Entry<T>[]> frozen = new Long2ObjectOpenHashMap<>(building.size());
        for (Long2ObjectMap.Entry<List<Entry<T>>> kv : building.long2ObjectEntrySet()) {
            frozen.put(kv.getLongKey(), kv.getValue().toArray(new Entry[0]));
        }
        frozen.trim();
        return new SpawnIndex<>(frozen, live.size(), List.copyOf(live));
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    public Entry<T> find(T type, double x, double y, double z) {
        if (size == 0) {
            return null;
        }
        final Entry<T>[] bucket = byChunk.get(RepellerShape.chunkKeyAt(x, z));
        if (bucket == null) {
            return null;
        }
        for (Entry<T> e : bucket) {
            if ((e.type() == type || Objects.equals(e.type(), type))
                    && e.shape().contains(e.x(), e.y(), e.z(), e.radius(), x, y, z)) {
                return e;
            }
        }
        return null;
    }

    public Entry<T> byId(long id) {
        return size == 0 ? null : byId.get(id);
    }

    @SuppressWarnings("unchecked")
    public boolean mentions(T type) {
        for (int i = 0; i < all.length; i++) {
            final Entry<T> e = (Entry<T>) all[i];
            if (e.type() == type || Objects.equals(e.type(), type)) {
                return true;
            }
        }
        return false;
    }
}
