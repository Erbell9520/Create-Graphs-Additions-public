package com.createstats.blockentity.inventory;

import com.createstats.inventory.core.MarkLadder;
import com.createstats.inventory.core.SetPointRelay;
import com.createstats.inventory.core.StallDetector;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

// marks by watch key, not row. pull the iron out and back and its marks come back
public final class InventorySettings {
    public static final String CONTAINER = "container";
    public static final String BELT = "belt";
    public static final String ENERGY = "energy";
    public static final int MAX_MARKS = 16;

    public record Marks(long low, long high) {
    }

    private final LinkedHashMap<String, Marks> marks = new LinkedHashMap<>(16, 0.75f, true);
    public SetPointRelay.Mode lowMode = SetPointRelay.Mode.STEADY;
    public SetPointRelay.Mode highMode = SetPointRelay.Mode.STEADY;
    public int stallIndex = StallDetector.DEFAULT_INDEX;
    public int chartDays = 3;
    public boolean preferBehind;

    public Boolean dieselMulti;

    public Marks marks(String key, MarkLadder unit) {
        final Marks m = this.marks.get(key);
        return m != null ? m : new Marks(unit.defaultLow(), unit.defaultHigh());
    }

    public void setMarks(String key, MarkLadder unit, long low, long high) {
        long lo = unit.clampTyped(low);
        long hi = unit.clampTyped(high);
        if (hi <= lo) {
            hi = Math.min(unit.typedMax(), lo + 1L);
            if (hi <= lo) {
                lo = hi - 1L;
            }
        }
        this.marks.put(key, new Marks(lo, hi));
        while (this.marks.size() > MAX_MARKS) {
            final String eldest = this.marks.keySet().iterator().next();
            this.marks.remove(eldest);
        }
    }

    public void save(CompoundTag tag) {
        final CompoundTag s = new CompoundTag();
        final ListTag list = new ListTag();
        for (Map.Entry<String, Marks> e : this.marks.entrySet()) {
            final CompoundTag m = new CompoundTag();
            m.putString("K", e.getKey());
            m.putLong("L", e.getValue().low());
            m.putLong("H", e.getValue().high());
            list.add(m);
        }
        s.put("Marks", list);
        s.putByte("LowMode", (byte) this.lowMode.ordinal());
        s.putByte("HighMode", (byte) this.highMode.ordinal());
        s.putByte("Stall", (byte) this.stallIndex);
        s.putByte("ChartDays", (byte) this.chartDays);
        s.putBoolean("PreferBehind", this.preferBehind);
        if (this.dieselMulti != null) {
            s.putBoolean("DieselMulti", this.dieselMulti);
        }
        tag.put("Settings", s);
    }

    public void load(CompoundTag tag) {
        this.marks.clear();
        if (!tag.contains("Settings", Tag.TAG_COMPOUND)) {
            return;
        }
        final CompoundTag s = tag.getCompound("Settings");
        final ListTag list = s.getList("Marks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < MAX_MARKS; i++) {
            final CompoundTag m = list.getCompound(i);
            this.marks.put(m.getString("K"), new Marks(Math.max(0L, m.getLong("L")), Math.max(0L, m.getLong("H"))));
        }
        this.lowMode = SetPointRelay.Mode.byOrdinal(s.getByte("LowMode"));
        this.highMode = SetPointRelay.Mode.byOrdinal(s.getByte("HighMode"));
        this.stallIndex = Math.floorMod(s.getByte("Stall"), StallDetector.STALL_TICKS.length);
        final int days = s.getByte("ChartDays");
        this.chartDays = days == 1 ? 1 : 3;
        this.preferBehind = s.getBoolean("PreferBehind");
        this.dieselMulti = s.contains("DieselMulti") ? s.getBoolean("DieselMulti") : null;
    }
}
