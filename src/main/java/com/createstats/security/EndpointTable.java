package com.createstats.security;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

// ten maps, one class. the entrance was left off the prune for its whole life
final class EndpointTable<V> {
    private final V fallback;
    private final Map<SecurityEndpoint, V> entries = new HashMap<>();

    EndpointTable(V fallback) {
        this.fallback = fallback;
    }

    V get(SecurityEndpoint key) {
        V v = this.entries.get(key);
        if (v == null && !key.isLegacy()) {
            v = this.entries.get(SecurityEndpoint.legacy(key.pos()));
        }
        return v == null ? this.fallback : v;
    }

    boolean holds(SecurityEndpoint key) {
        return !Objects.equals(get(key), this.fallback);
    }

    boolean set(SecurityEndpoint key, V value, boolean settled) {
        final Map<SecurityEndpoint, V> before = Map.copyOf(this.entries);
        final SecurityEndpoint legacy = SecurityEndpoint.legacy(key.pos());
        final boolean legacyBehind = !key.isLegacy() && this.entries.containsKey(legacy);
        if (settled && legacyBehind) {
            this.entries.remove(legacy);
        }
        if (Objects.equals(value, this.fallback) && (settled || !legacyBehind)) {
            this.entries.remove(key);
        } else {
            this.entries.put(key, value);
        }
        return !before.equals(this.entries);
    }

    boolean prune(String dimension, Predicate<Long> dead, boolean settled) {
        return this.entries.keySet().removeIf(k ->
                (k.inDimension(dimension) || (settled && k.isLegacy())) && dead.test(k.pos()));
    }

    boolean settle(String dimension) {
        final Set<SecurityEndpoint> legacy = new HashSet<>();
        for (SecurityEndpoint k : this.entries.keySet()) {
            if (k.isLegacy()) {
                legacy.add(k);
            }
        }
        for (SecurityEndpoint k : legacy) {
            final V v = this.entries.remove(k);
            this.entries.putIfAbsent(k.in(dimension), v);
        }
        this.entries.values().removeIf(v -> Objects.equals(v, this.fallback));
        return !legacy.isEmpty();
    }

    boolean hasLegacy() {
        for (SecurityEndpoint k : this.entries.keySet()) {
            if (k.isLegacy()) {
                return true;
            }
        }
        return false;
    }

    int legacyCount() {
        int n = 0;
        for (SecurityEndpoint k : this.entries.keySet()) {
            if (k.isLegacy()) {
                n++;
            }
        }
        return n;
    }

    boolean isEmpty() {
        for (V v : this.entries.values()) {
            if (!Objects.equals(v, this.fallback)) {
                return false;
            }
        }
        return true;
    }

    Set<SecurityEndpoint> keys() {
        return Set.copyOf(this.entries.keySet());
    }

    void clear() {
        this.entries.clear();
    }

    void load(SecurityEndpoint key, V value) {
        if (value != null) {
            this.entries.put(key, value);
        }
    }

    ListTag save(BiConsumer<CompoundTag, V> writeValue) {
        final ListTag list = new ListTag();
        this.entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    final CompoundTag row = new CompoundTag();
                    if (!e.getKey().isLegacy()) {
                        row.putString("D", e.getKey().dimension());
                    }
                    row.putLong("P", e.getKey().pos());
                    writeValue.accept(row, e.getValue());
                    list.add(row);
                });
        return list;
    }

    void load(ListTag list, Function<CompoundTag, V> readValue) {
        this.entries.clear();
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag row = list.getCompound(i);
            if (!row.contains("P", Tag.TAG_LONG)) {
                continue;
            }
            final SecurityEndpoint key = row.contains("D", Tag.TAG_STRING)
                    ? SecurityEndpoint.of(row.getString("D"), row.getLong("P"))
                    : SecurityEndpoint.legacy(row.getLong("P"));
            load(key, readValue.apply(row));
        }
    }
}
