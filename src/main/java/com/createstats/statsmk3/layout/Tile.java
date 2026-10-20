package com.createstats.statsmk3.layout;

import com.createstats.statsmk3.YAxisRange;

import net.minecraft.nbt.CompoundTag;

public final class Tile {
    public int gx;
    public int gy;

    public int gw = 1;
    public int gh = 1;

    public String channelId = "";

    public String instrumentId = "";

    public boolean label = true;

    public boolean value;

    private YAxisRange yAxis = YAxisRange.auto();

    public Tile() {
    }

    public Tile(int gx, int gy, int gw, int gh) {
        this.gx = gx;
        this.gy = gy;
        this.gw = Math.max(1, gw);
        this.gh = Math.max(1, gh);
    }

    public Tile copy() {
        final Tile t = new Tile(this.gx, this.gy, this.gw, this.gh);
        t.channelId = this.channelId;
        t.instrumentId = this.instrumentId;
        t.label = this.label;
        t.value = this.value;
        t.yAxis = this.yAxis;
        return t;
    }

    public int right() {
        return this.gx + this.gw;
    }

    public int bottom() {
        return this.gy + this.gh;
    }

    public int cells() {
        return this.gw * this.gh;
    }

    public boolean containsCell(int cx, int cy) {
        return cx >= this.gx && cx < right() && cy >= this.gy && cy < bottom();
    }

    public boolean overlaps(Tile other) {
        return other != null
                && this.gx < other.right() && other.gx < right()
                && this.gy < other.bottom() && other.gy < bottom();
    }

    public boolean isBound() {
        return this.channelId != null && !this.channelId.isEmpty();
    }

    public YAxisRange yAxis() {
        return this.yAxis;
    }

    public void setYAxis(YAxisRange range) {
        this.yAxis = range == null ? YAxisRange.auto() : range;
    }

    public CompoundTag save() {
        final CompoundTag t = new CompoundTag();
        t.putByte("X", (byte) this.gx);
        t.putByte("Y", (byte) this.gy);
        t.putByte("W", (byte) this.gw);
        t.putByte("H", (byte) this.gh);
        if (isBound()) {
            t.putString("Ch", this.channelId);
        }
        if (this.instrumentId != null && !this.instrumentId.isEmpty()) {
            t.putString("In", this.instrumentId);
        }
        // only non default flags, a layout is sent on every slider drag

        if (!this.label) {
            t.putBoolean("NoLbl", true);
        }
        if (this.value) {
            t.putBoolean("Val", true);
        }
        if (this.yAxis.isFixed()) {
            t.putFloat("YMin", this.yAxis.min());
            t.putFloat("YMax", this.yAxis.max());
        }
        return t;
    }

    public static Tile load(CompoundTag t) {
        final Tile tile = new Tile(t.getByte("X"), t.getByte("Y"), t.getByte("W"), t.getByte("H"));
        tile.channelId = t.contains("Ch") ? t.getString("Ch") : "";
        tile.instrumentId = t.contains("In") ? t.getString("In") : "";
        tile.label = !t.getBoolean("NoLbl");
        tile.value = t.getBoolean("Val");
        if (t.contains("YMin") && t.contains("YMax")) {
            tile.yAxis = YAxisRange.fixed(t.getFloat("YMin"), t.getFloat("YMax"));
        }
        return tile;
    }
}
