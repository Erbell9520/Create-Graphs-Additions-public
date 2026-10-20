package com.createstats.security;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// the only place in the mod that decides if you get through a door
public final class SecurityRoster {
    public static final int MAX_ENTRIES = 64;

    public record Entry(UUID id, String name, SecurityRole role, int level, long lastSeen) {
        public Entry withRole(SecurityRole newRole) {
            return new Entry(id, name, newRole, level, lastSeen);
        }

        public Entry withLevel(int newLevel) {
            return new Entry(id, name, role, Clearance.clamp(newLevel), lastSeen);
        }

        public Entry withName(String newName) {
            return new Entry(id, newName, role, level, lastSeen);
        }

        public Entry seenNow(long now) {
            return new Entry(id, name, role, level, now);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private UUID owner;

    private Runnable onChange = () -> {
    };

    public SecurityRoster() {
    }

    public void onChange(Runnable callback) {
        this.onChange = callback == null ? () -> {
        } : callback;
    }

    private void changed() {
        this.onChange.run();
    }

    public UUID owner() {
        return this.owner;
    }

    public void setOwner(UUID id, String name) {
        if (id == null) {
            return;
        }
        final UUID previous = this.owner;
        this.owner = id;
        if (previous != null && !previous.equals(id)) {
            replace(previous, e -> e.withRole(SecurityRole.ADMIN));
        }
        final Entry existing = find(id);
        if (existing == null) {
            this.entries.add(new Entry(id, name, SecurityRole.OWNER, Clearance.MAX, 0L));
        } else {
            replace(id, e -> e.withRole(SecurityRole.OWNER).withName(name));
        }
        changed();
    }

    public boolean isOwner(UUID id) {
        return id != null && id.equals(this.owner);
    }

    public List<Entry> entries() {
        return List.copyOf(this.entries);
    }

    public int size() {
        return this.entries.size();
    }

    public Entry find(UUID id) {
        if (id == null) {
            return null;
        }
        for (Entry e : this.entries) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    public SecurityRole roleOf(UUID id) {
        if (isOwner(id)) {
            return SecurityRole.OWNER;
        }
        final Entry e = find(id);
        return e == null ? SecurityRole.MEMBER : e.role();
    }

    public int levelOf(UUID id) {
        final Entry e = find(id);
        return e == null ? 0 : e.level();
    }

    public boolean passes(UUID id, int required) {
        if (id == null) {
            return false;
        }
        if (isOwner(id)) {
            return true;
        }
        final Entry e = find(id);
        return e != null && e.level() >= Clearance.clamp(required);
    }

    public boolean add(UUID id, String name) {
        if (id == null || this.entries.size() >= MAX_ENTRIES) {
            return false;
        }
        if (find(id) != null) {
            replace(id, e -> e.withName(name));
            changed();
            return true;
        }
        this.entries.add(new Entry(id, name, SecurityRole.MEMBER, Clearance.DEFAULT_GRANTED, 0L));
        changed();
        return true;
    }

    public boolean remove(UUID id) {
        if (isOwner(id)) {
            return false;
        }
        return mark(this.entries.removeIf(e -> e.id().equals(id)));
    }

    public boolean setLevel(UUID id, int level) {
        if (isOwner(id)) {
            return false;
        }
        return mark(replace(id, e -> e.withLevel(level)));
    }

    public boolean setRole(UUID id, SecurityRole role) {
        if (isOwner(id) || role == SecurityRole.OWNER) {
            return false;
        }
        return mark(replace(id, e -> e.withRole(role)));
    }

    public void touch(UUID id, long now) {
        mark(replace(id, e -> e.seenNow(now)));
    }

    private boolean mark(boolean changedSomething) {
        if (changedSomething) {
            changed();
        }
        return changedSomething;
    }

    private boolean replace(UUID id, java.util.function.UnaryOperator<Entry> op) {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).id().equals(id)) {
                this.entries.set(i, op.apply(this.entries.get(i)));
                return true;
            }
        }
        return false;
    }

    public List<Entry> sorted() {
        final List<Entry> copy = new ArrayList<>(this.entries);
        copy.sort(Comparator
                .comparingInt((Entry e) -> e.role().ordinal())
                .thenComparing(Comparator.comparingInt(Entry::level).reversed())
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        final ListTag list = new ListTag();
        for (Entry e : this.entries) {
            final CompoundTag row = new CompoundTag();
            row.putUUID("Id", e.id());
            row.putString("Name", e.name() == null ? "" : e.name());
            row.putString("Role", e.role().name());
            row.putInt("Level", e.level());
            row.putLong("Seen", e.lastSeen());
            list.add(row);
        }
        tag.put("Entries", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.entries.clear();
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        final ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < MAX_ENTRIES; i++) {
            final CompoundTag row = list.getCompound(i);
            if (!row.hasUUID("Id")) {
                continue;
            }
            this.entries.add(new Entry(
                    row.getUUID("Id"),
                    row.getString("Name"),
                    SecurityRole.byName(row.getString("Role")),
                    Clearance.clamp(row.getInt("Level")),
                    row.getLong("Seen")));
        }
    }

    public SecurityRoster copy() {
        final SecurityRoster out = new SecurityRoster();
        out.owner = this.owner;
        out.entries.addAll(this.entries);
        return out;
    }
}
