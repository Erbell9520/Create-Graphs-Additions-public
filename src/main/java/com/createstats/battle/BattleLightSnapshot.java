package com.createstats.battle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record BattleLightSnapshot(List<Entry> lights) {
    private static final BattleLightSnapshot EMPTY = new BattleLightSnapshot(List.of());

    public static BattleLightSnapshot empty() {
        return EMPTY;
    }

    public int count() {
        return this.lights.size();
    }

    public int litCount() {
        int n = 0;
        for (Entry e : this.lights) {
            if (e.lit()) {
                n++;
            }
        }
        return n;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry e : this.lights) {
            list.add(e.save());
        }
        tag.put("Lights", list);
        return tag;
    }

    public static BattleLightSnapshot load(CompoundTag tag) {
        ListTag list = tag.getList("Lights", Tag.TAG_COMPOUND);
        List<Entry> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            entries.add(Entry.load(list.getCompound(i)));
        }
        return new BattleLightSnapshot(List.copyOf(entries));
    }

    public record Entry(long pos, boolean lit, int pan, int tilt, int range, int brightness,
                        int focus, int tint, boolean beamVisible, boolean volumetric, int trackingRange,
                        int beamWidth, int lightSpread, boolean tracking, int maxRange, int peakRange,
                        String trackingName, int effect, int effectSpeed) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("P", this.pos);
            t.putBoolean("L", this.lit);
            t.putInt("Pa", this.pan);
            t.putInt("Ti", this.tilt);
            t.putInt("R", this.range);
            t.putInt("B", this.brightness);
            t.putInt("F", this.focus);
            t.putInt("C", this.tint);
            t.putBoolean("Bv", this.beamVisible);
            t.putBoolean("V", this.volumetric);
            t.putInt("Tk", this.trackingRange);
            t.putInt("Bw", this.beamWidth);
            t.putInt("Ls", this.lightSpread);
            t.putBoolean("Tr", this.tracking);
            t.putInt("Mr", this.maxRange);
            t.putInt("Pr", this.peakRange);
            if (!this.trackingName.isEmpty()) {
                t.putString("Tn", this.trackingName);
            }
            t.putInt("Fx", this.effect);
            t.putInt("Fs", this.effectSpeed);
            return t;
        }

        public static Entry load(CompoundTag t) {
            return new Entry(
                    t.getLong("P"),
                    t.getBoolean("L"),
                    t.getInt("Pa"),
                    t.getInt("Ti"),
                    t.getInt("R"),
                    t.getInt("B"),
                    t.contains("F") ? t.getInt("F") : 4,
                    t.contains("C") ? t.getInt("C") : 0xFFFFFF,
                    !t.contains("Bv") || t.getBoolean("Bv"),
                    t.getBoolean("V"),
                    t.contains("Tk") ? t.getInt("Tk") : 96,
                    t.contains("Bw") ? t.getInt("Bw") : 9,
                    t.contains("Ls") ? t.getInt("Ls") : 9,
                    t.getBoolean("Tr"),
                    t.contains("Mr") ? t.getInt("Mr") : 64,
                    t.contains("Pr") ? t.getInt("Pr") : 32,
                    t.getString("Tn"),
                    t.getInt("Fx"),
                    t.contains("Fs") ? t.getInt("Fs") : 5);
        }
    }
}
