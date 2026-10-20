package com.createstats.home;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HouseModel {
    public static final int MAX_ROOMS = 16;
    public static final int SCENE_SLOTS = 9;
    public static final int MAX_NAME = 24;

    public static final int MAX_SCENE_ENTRIES = 256;

    public static final List<String> ROOM_ICONS = List.of(
            "minecraft:cake", "minecraft:water_bucket", "minecraft:red_bed", "minecraft:painting",
            "minecraft:lantern", "minecraft:oak_door", "minecraft:bookshelf", "minecraft:flower_pot",
            "minecraft:crafting_table", "minecraft:jukebox", "minecraft:chest", "minecraft:poppy",
            "minecraft:anvil", "minecraft:cauldron", "minecraft:bell", "minecraft:writable_book",
            "minecraft:brewing_stand");

    public static final List<String> SCENE_ICONS = List.of(
            "minecraft:clock", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:iron_door",
            "minecraft:redstone_torch", "minecraft:red_bed", "minecraft:cake", "minecraft:jukebox",
            "minecraft:bell", "minecraft:sunflower", "minecraft:glow_berries", "minecraft:ender_eye");

    public static final int LOST_AFTER = 3;

    public static final int MEMORY = 16;

    public static final class Room {
        private final int id;
        private String name;
        private String icon;
        private int tint;
        private final int floorY;
        private Set<Long> cells;
        private boolean followPeople;

        private final FollowPeople follow = new FollowPeople();

        private int misses;

        private Room(int id, String name, String icon, int tint, int floorY, Set<Long> cells, boolean follow) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.tint = tint;
            this.floorY = floorY;
            this.cells = Collections.unmodifiableSet(new HashSet<>(cells));
            this.followPeople = follow;
        }

        public int id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String icon() {
            return icon;
        }

        public int tint() {
            return tint;
        }

        public int floorY() {
            return floorY;
        }

        public Set<Long> cells() {
            return cells;
        }

        public boolean followPeople() {
            return followPeople;
        }

        public FollowPeople follow() {
            return follow;
        }

        public boolean contains(int x, int z, int floor) {
            return Math.abs(floor - floorY) <= FloorBands.SAME_STOREY && cells.contains(RoomFill.pack(x, z));
        }

        // count a one block rim, ovens sockets and sconces are exactly the things you can't stand on
        public boolean holds(int x, int z, int floor) {
            if (Math.abs(floor - floorY) > FloorBands.SAME_STOREY) {
                return false;
            }
            return cells.contains(RoomFill.pack(x, z)) || cells.contains(RoomFill.pack(x + 1, z))
                    || cells.contains(RoomFill.pack(x - 1, z)) || cells.contains(RoomFill.pack(x, z + 1))
                    || cells.contains(RoomFill.pack(x, z - 1));
        }
    }

    public record Scene(String name, String icon, Map<Long, SceneDiff.State> states) {
    }

    private record Remembered(int floorY, long cell, String name, String icon, int tint, boolean follow) {
    }

    private final List<Room> rooms = new ArrayList<>();
    private final List<Remembered> remembered = new ArrayList<>();
    private final Scene[] scenes = new Scene[SCENE_SLOTS];
    private int nextRoomId = 1;
    private int bedtimeScene = -1;
    private int awayScene = -1;
    private int homeScene = -1;
    private boolean toasts = true;

    private int lastScene = -1;
    private int revision = 1;

    private final DayMeter meter = new DayMeter();
    private final Presence presence = new Presence();

    public int revision() {
        return revision;
    }

    public List<Room> rooms() {
        return Collections.unmodifiableList(rooms);
    }

    @Nullable
    public Room room(int id) {
        for (Room r : rooms) {
            if (r.id == id) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    public Room roomOf(int x, int z, int floor) {
        for (Room r : rooms) {
            if (r.contains(x, z, floor)) {
                return r;
            }
        }
        for (Room r : rooms) {
            if (r.holds(x, z, floor)) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    public Scene scene(int slot) {
        return slot >= 0 && slot < SCENE_SLOTS ? scenes[slot] : null;
    }

    public int bedtimeScene() {
        return bedtimeScene;
    }

    public int awayScene() {
        return awayScene;
    }

    public int homeScene() {
        return homeScene;
    }

    public boolean toasts() {
        return toasts;
    }

    public int lastScene() {
        return lastScene;
    }

    public DayMeter meter() {
        return meter;
    }

    public Presence presence() {
        return presence;
    }

    @Nullable
    public Room addRoom(String name, String icon, int floorY, Set<Long> cells) {
        if (rooms.size() >= MAX_ROOMS || cells.isEmpty()) {
            return null;
        }
        for (Room r : rooms) {
            if (overlaps(r, floorY, cells)) {
                return null;
            }
        }

        for (int i = remembered.size() - 1; i >= 0; i--) {
            final Remembered m = remembered.get(i);
            if (Math.abs(m.floorY() - floorY) <= FloorBands.SAME_STOREY && cells.contains(m.cell())) {
                remembered.remove(i);
                final Room room = new Room(nextRoomId++, m.name(), m.icon(), m.tint(), floorY, cells, m.follow());
                rooms.add(room);
                bump();
                return room;
            }
        }
        final Room room = new Room(nextRoomId++, clip(name), icon, nextTint(), floorY, cells, false);
        rooms.add(room);
        bump();
        return room;
    }

    @Nullable
    public Room overlapping(int floorY, Set<Long> cells) {
        for (Room r : rooms) {
            if (overlaps(r, floorY, cells)) {
                return r;
            }
        }
        return null;
    }

    private static boolean overlaps(Room r, int floorY, Set<Long> cells) {
        if (Math.abs(r.floorY - floorY) > FloorBands.SAME_STOREY) {
            return false;
        }
        final Set<Long> small = cells.size() < r.cells.size() ? cells : r.cells;
        final Set<Long> big = small == cells ? r.cells : cells;
        for (long c : small) {
            if (big.contains(c)) {
                return true;
            }
        }
        return false;
    }

    public void removeRoom(int id) {
        final Room r = room(id);
        if (r != null) {
            forget(r);
            bump();
        }
    }

    public boolean confirmRoom(int id, Set<Long> cells) {
        final Room r = room(id);
        if (r == null || cells.isEmpty()) {
            return false;
        }
        r.misses = 0;
        if (r.cells.equals(cells)) {
            return false;
        }
        r.cells = Collections.unmodifiableSet(new HashSet<>(cells));
        for (Room other : new ArrayList<>(rooms)) {
            if (other != r && overlaps(other, r.floorY, r.cells)) {
                forget(other);
            }
        }
        bump();
        return true;
    }

    public boolean missRoom(int id) {
        final Room r = room(id);
        if (r == null) {
            return false;
        }
        if (++r.misses < LOST_AFTER) {
            return false;
        }
        forget(r);
        bump();
        return true;
    }

    private void forget(Room r) {
        rooms.remove(r);
        remembered.add(new Remembered(r.floorY, centreCell(r.cells), r.name, r.icon, r.tint, r.followPeople));
        while (remembered.size() > MEMORY) {
            remembered.remove(0);
        }
    }

    public static long centreCell(Set<Long> cells) {
        long sx = 0;
        long sz = 0;
        for (long c : cells) {
            sx += RoomFill.unpackX(c);
            sz += RoomFill.unpackZ(c);
        }
        final int n = Math.max(1, cells.size());
        final double mx = sx / (double) n;
        final double mz = sz / (double) n;
        long best = cells.isEmpty() ? 0 : cells.iterator().next();
        double bestD = Double.MAX_VALUE;
        for (long c : cells) {
            final double dx = RoomFill.unpackX(c) - mx;
            final double dz = RoomFill.unpackZ(c) - mz;
            final double d = dx * dx + dz * dz;

            if (d < bestD || (d == bestD && c < best)) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    public void renameRoom(int id, String name) {
        final Room r = room(id);
        if (r != null && !clip(name).isEmpty() && !clip(name).equals(r.name)) {
            r.name = clip(name);
            bump();
        }
    }

    public void cycleRoomIcon(int id, int step) {
        final Room r = room(id);
        if (r != null) {
            r.icon = cycle(ROOM_ICONS, r.icon, step);
            bump();
        }
    }

    public void setFollowPeople(int id, boolean follow) {
        final Room r = room(id);
        if (r != null && r.followPeople != follow) {
            r.followPeople = follow;
            bump();
        }
    }

    private int nextTint() {
        final int[] order = {1, 3, 10, 4, 14, 5, 6, 9, 13, 2, 11, 12, 0, 8, 7, 15};
        outer:
        for (int t : order) {
            for (Room r : rooms) {
                if (r.tint == t) {
                    continue outer;
                }
            }
            return t;
        }
        return order[rooms.size() % order.length];
    }

    public void saveScene(int slot, String name, String icon, Map<Long, SceneDiff.State> states) {
        if (slot < 0 || slot >= SCENE_SLOTS) {
            return;
        }
        final Map<Long, SceneDiff.State> kept = new LinkedHashMap<>();
        for (Map.Entry<Long, SceneDiff.State> e : states.entrySet()) {
            if (kept.size() >= MAX_SCENE_ENTRIES) {
                break;
            }
            kept.put(e.getKey(), e.getValue());
        }
        scenes[slot] = new Scene(clip(name), icon, Collections.unmodifiableMap(kept));
        bump();
    }

    public void deleteScene(int slot) {
        if (scene(slot) != null) {
            scenes[slot] = null;
            if (bedtimeScene == slot) {
                bedtimeScene = -1;
            }
            if (awayScene == slot) {
                awayScene = -1;
            }
            if (homeScene == slot) {
                homeScene = -1;
            }
            if (lastScene == slot) {
                lastScene = -1;
            }
            bump();
        }
    }

    public void renameScene(int slot, String name) {
        final Scene s = scene(slot);
        if (s != null && !clip(name).isEmpty()) {
            scenes[slot] = new Scene(clip(name), s.icon(), s.states());
            bump();
        }
    }

    public void cycleSceneIcon(int slot, int step) {
        final Scene s = scene(slot);
        if (s != null) {
            scenes[slot] = new Scene(s.name(), cycle(SCENE_ICONS, s.icon(), step), s.states());
            bump();
        }
    }

    public void markRecalled(int slot) {
        if (lastScene != slot) {
            lastScene = slot;
            bump();
        }
    }

    public static final int LIGHTS = SCENE_SLOTS;

    public void setRule(Rule rule, int slot) {
        final int s = slot == LIGHTS ? LIGHTS : slot >= 0 && slot < SCENE_SLOTS && scenes[slot] != null ? slot : -1;
        switch (rule) {
            case BEDTIME -> bedtimeScene = s;
            case AWAY -> awayScene = s;
            case HOME -> homeScene = s;
        }
        bump();
    }

    public void setToasts(boolean on) {
        if (toasts != on) {
            toasts = on;
            bump();
        }
    }

    public enum Rule { BEDTIME, AWAY, HOME }

    public void save(CompoundTag tag) {
        final ListTag roomList = new ListTag();
        for (Room r : rooms) {
            final CompoundTag one = new CompoundTag();
            one.putInt("Id", r.id);
            one.putString("Name", r.name);
            one.putString("Icon", r.icon);
            one.putInt("Tint", r.tint);
            one.putInt("FloorY", r.floorY);
            one.putBoolean("Follow", r.followPeople);
            final long[] cells = new long[r.cells.size()];
            int i = 0;
            for (long c : r.cells) {
                cells[i++] = c;
            }
            one.putLongArray("Cells", cells);
            roomList.add(one);
        }
        tag.put("Rooms", roomList);
        tag.putInt("NextRoom", nextRoomId);
        final ListTag memoryList = new ListTag();
        for (Remembered m : remembered) {
            final CompoundTag one = new CompoundTag();
            one.putInt("FloorY", m.floorY());
            one.putLong("Cell", m.cell());
            one.putString("Name", m.name());
            one.putString("Icon", m.icon());
            one.putInt("Tint", m.tint());
            one.putBoolean("Follow", m.follow());
            memoryList.add(one);
        }
        tag.put("Remembered", memoryList);
        final ListTag sceneList = new ListTag();
        for (int slot = 0; slot < SCENE_SLOTS; slot++) {
            final Scene s = scenes[slot];
            if (s == null) {
                continue;
            }
            final CompoundTag one = new CompoundTag();
            one.putInt("Slot", slot);
            one.putString("Name", s.name());
            one.putString("Icon", s.icon());
            final long[] keys = new long[s.states().size()];
            final int[] packed = new int[s.states().size()];
            int i = 0;
            for (Map.Entry<Long, SceneDiff.State> e : s.states().entrySet()) {
                keys[i] = e.getKey();
                packed[i] = packState(e.getValue());
                i++;
            }
            one.putLongArray("Keys", keys);
            one.putIntArray("States", packed);
            sceneList.add(one);
        }
        tag.put("Scenes", sceneList);
        tag.putInt("RuleBed", bedtimeScene);
        tag.putInt("RuleAway", awayScene);
        tag.putInt("RuleHome", homeScene);
        tag.putBoolean("Toasts", toasts);
        tag.putInt("LastScene", lastScene);
        tag.putLongArray("Meter", meter.raw());
        tag.putLong("MeterHour", meter.lastHour());
    }

    public void load(CompoundTag tag) {
        rooms.clear();
        final ListTag roomList = tag.getList("Rooms", Tag.TAG_COMPOUND);
        for (int i = 0; i < roomList.size() && rooms.size() < MAX_ROOMS; i++) {
            final CompoundTag one = roomList.getCompound(i);
            final Set<Long> cells = new HashSet<>();
            final long[] raw = one.getLongArray("Cells");
            for (int c = 0; c < raw.length && c < RoomFill.MAX_CELLS; c++) {
                cells.add(raw[c]);
            }
            if (cells.isEmpty()) {
                continue;
            }
            rooms.add(new Room(one.getInt("Id"), clip(one.getString("Name")), one.getString("Icon"),
                    Math.floorMod(one.getInt("Tint"), 16), one.getInt("FloorY"), cells, one.getBoolean("Follow")));
        }
        nextRoomId = Math.max(1, tag.getInt("NextRoom"));
        for (Room r : rooms) {
            nextRoomId = Math.max(nextRoomId, r.id + 1);
        }
        remembered.clear();
        final ListTag memoryList = tag.getList("Remembered", Tag.TAG_COMPOUND);
        for (int i = 0; i < memoryList.size() && remembered.size() < MEMORY; i++) {
            final CompoundTag one = memoryList.getCompound(i);
            remembered.add(new Remembered(one.getInt("FloorY"), one.getLong("Cell"), clip(one.getString("Name")),
                    one.getString("Icon"), Math.floorMod(one.getInt("Tint"), 16), one.getBoolean("Follow")));
        }
        java.util.Arrays.fill(scenes, null);
        final ListTag sceneList = tag.getList("Scenes", Tag.TAG_COMPOUND);
        for (int i = 0; i < sceneList.size(); i++) {
            final CompoundTag one = sceneList.getCompound(i);
            final int slot = one.getInt("Slot");
            if (slot < 0 || slot >= SCENE_SLOTS) {
                continue;
            }
            final long[] keys = one.getLongArray("Keys");
            final int[] packed = one.getIntArray("States");
            final Map<Long, SceneDiff.State> states = new LinkedHashMap<>();
            for (int k = 0; k < Math.min(keys.length, packed.length) && k < MAX_SCENE_ENTRIES; k++) {
                states.put(keys[k], unpackState(packed[k]));
            }
            scenes[slot] = new Scene(clip(one.getString("Name")), one.getString("Icon"),
                    Collections.unmodifiableMap(states));
        }
        bedtimeScene = validRule(tag.contains("RuleBed") ? tag.getInt("RuleBed") : -1);
        awayScene = validRule(tag.contains("RuleAway") ? tag.getInt("RuleAway") : -1);
        homeScene = validRule(tag.contains("RuleHome") ? tag.getInt("RuleHome") : -1);
        toasts = !tag.contains("Toasts") || tag.getBoolean("Toasts");
        lastScene = validSlot(tag.contains("LastScene") ? tag.getInt("LastScene") : -1);
        meter.load(tag.getLongArray("Meter"), tag.contains("MeterHour") ? tag.getLong("MeterHour") : -1);
        bump();
    }

    private int validSlot(int slot) {
        return slot >= 0 && slot < SCENE_SLOTS && scenes[slot] != null ? slot : -1;
    }

    private int validRule(int value) {
        return value == LIGHTS ? LIGHTS : validSlot(value);
    }

    static int packState(SceneDiff.State s) {
        return (s.on() ? 1 : 0) | ((Math.max(-1, Math.min(32766, s.mode())) + 1) << 1)
                | ((Math.max(-1, Math.min(254, s.colour())) + 1) << 16);
    }

    static SceneDiff.State unpackState(int p) {
        return new SceneDiff.State((p & 1) != 0, ((p >> 1) & 0x7FFF) - 1, ((p >> 16) & 0xFF) - 1);
    }

    private void bump() {
        revision++;
    }

    static String clip(String name) {
        final String s = name == null ? "" : name.strip();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    static String cycle(List<String> list, String current, int step) {
        final int at = list.indexOf(current);
        final int next = Math.floorMod((at < 0 ? 0 : at) + (step == 0 ? 1 : step), list.size());
        return list.get(next);
    }
}
