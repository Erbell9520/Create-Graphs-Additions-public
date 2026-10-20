package com.createstats.battle.core;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

public final class Places {
    public static final int MAX_OPEN = 32;
    public static final int REACH = 2;

    public static final byte KIND_DAMAGE = 0;
    public static final byte KIND_BROKE_AWAY = 1;

    public interface Namer {
        String name(int[] cells, byte kind);
    }

    public static final class Place {
        private final int id;
        private final byte kind;
        private final IntOpenHashSet cells = new IntOpenHashSet();
        private String name = "";
        private boolean dirty = true;
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        private Place(int id, byte kind) {
            this.id = id;
            this.kind = kind;
        }

        public int id() {
            return id;
        }

        public byte kind() {
            return kind;
        }

        public String name() {
            return name;
        }

        public int size() {
            return cells.size();
        }

        public boolean contains(int packed) {
            return cells.contains(packed);
        }

        public int[] cells() {
            return cells.toIntArray();
        }

        private void addCell(int packed) {
            cells.add(packed);
            int x = PackedPos.dx(packed);
            int y = PackedPos.dy(packed);
            int z = PackedPos.dz(packed);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        private int gap(Place o) {
            int gx = Math.max(0, Math.max(o.minX - maxX, minX - o.maxX));
            int gy = Math.max(0, Math.max(o.minY - maxY, minY - o.maxY));
            int gz = Math.max(0, Math.max(o.minZ - maxZ, minZ - o.maxZ));
            return Math.max(gx, Math.max(gy, gz));
        }
    }

    private final List<Place> open = new ArrayList<>();
    private final Int2IntOpenHashMap placeOfCell = new Int2IntOpenHashMap();
    private int nextId = 1;

    public Places() {
        placeOfCell.defaultReturnValue(0);
    }

    public List<Place> open() {
        return List.copyOf(open);
    }

    public int count() {
        return open.size();
    }

    public int nextId() {
        return nextId;
    }

    public Place placeOf(int packed) {
        int id = placeOfCell.get(packed);
        return id == 0 ? null : byId(id);
    }

    public Place byId(int id) {
        for (Place p : open) {
            if (p.id == id) return p;
        }
        return null;
    }

    public Place add(int packed, byte kind) {
        Place already = placeOf(packed);
        if (already != null) return already;
        List<Place> touching = new ArrayList<>(2);
        for (int ox = -REACH; ox <= REACH; ox++) {
            for (int oy = -REACH; oy <= REACH; oy++) {
                for (int oz = -REACH; oz <= REACH; oz++) {
                    if (ox == 0 && oy == 0 && oz == 0) continue;
                    int n = PackedPos.offset(packed, ox, oy, oz);
                    if (n < 0) continue;
                    int id = placeOfCell.get(n);
                    if (id == 0) continue;
                    Place p = byId(id);
                    if (p != null && p.kind == kind && !touching.contains(p)) touching.add(p);
                }
            }
        }
        Place target;
        if (touching.isEmpty()) {
            target = new Place(nextId++, kind);
            open.add(target);
        } else {
            touching.sort(Comparator.comparingInt(Place::id));
            target = touching.get(0);
            for (int i = 1; i < touching.size(); i++) merge(touching.get(i), target);
        }
        target.addCell(packed);
        placeOfCell.put(packed, target.id);
        if (open.size() > MAX_OPEN) shrink();
        return placeOf(packed);
    }

    public void load(int id, byte kind, String name, int[] cells) {
        Place p = new Place(id, kind);
        p.name = name == null ? "" : name;
        p.dirty = p.name.isEmpty();
        for (int c : cells) {
            if (placeOfCell.get(c) != 0) continue;
            p.addCell(c);
            placeOfCell.put(c, id);
        }
        if (p.size() == 0) return;
        open.add(p);
        nextId = Math.max(nextId, id + 1);
    }

    public void setNextId(int id) {
        nextId = Math.max(1, id);
    }

    public void remove(int packed) {
        int id = placeOfCell.remove(packed);
        if (id == 0) return;
        Place p = byId(id);
        if (p == null) return;
        p.cells.remove(packed);
        if (p.cells.isEmpty()) close(p);
    }

    public int[] accept(int id) {
        Place p = byId(id);
        if (p == null) return new int[0];
        int[] cells = p.cells();
        for (int c : cells) placeOfCell.remove(c);
        close(p);
        return cells;
    }

    public void clear() {
        open.clear();
        placeOfCell.clear();
        nextId = 1;
    }

    public void clearNames() {
        for (Place p : open) p.dirty = true;
    }

    public void nameDirty(Namer namer) {
        for (Place p : open) {
            if (!p.dirty) continue;
            p.name = namer.name(p.cells(), p.kind);
            p.dirty = false;
        }
    }

    public List<Place> ordered(IntPredicate important) {
        List<Place> out = new ArrayList<>(open);
        out.sort(Comparator.comparing((Place p) -> !holdsAny(p, important))
                .thenComparing(Comparator.comparingInt(Place::size).reversed())
                .thenComparingInt(Place::id));
        return out;
    }

    private static boolean holdsAny(Place p, IntPredicate important) {
        IntIterator it = p.cells.iterator();
        while (it.hasNext()) {
            if (important.test(it.nextInt())) return true;
        }
        return false;
    }

    private void merge(Place from, Place into) {
        IntIterator it = from.cells.iterator();
        while (it.hasNext()) {
            int c = it.nextInt();
            into.addCell(c);
            placeOfCell.put(c, into.id);
        }
        into.dirty = true;
        open.remove(from);
    }

    private void shrink() {
        while (open.size() > MAX_OPEN) {
            Place smallest = null;
            for (Place p : open) {
                if (smallest == null || p.size() < smallest.size()
                        || (p.size() == smallest.size() && p.id > smallest.id)) smallest = p;
            }
            Place nearest = null;
            int best = Integer.MAX_VALUE;
            for (Place p : open) {
                if (p == smallest || p.kind != smallest.kind) continue;
                int g = smallest.gap(p);
                if (g < best || (g == best && nearest != null && p.id < nearest.id)) {
                    best = g;
                    nearest = p;
                }
            }
            if (nearest == null) return;
            merge(smallest, nearest);
        }
    }

    private void close(Place p) {
        open.remove(p);
        if (open.isEmpty()) nextId = 1;
    }
}
