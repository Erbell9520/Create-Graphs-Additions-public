package com.createstats.statsmk3;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SurveySettings {
    static final String TAG = "Survey";

    public static final float DEFAULT_YAW = 32.0f;
    public static final float DEFAULT_PITCH = 30.0f;
    public static final float MIN_ZOOM = 0.5f;
    public static final float MAX_ZOOM = 12.0f;

    static final float MAX_COORD = 30_000_000.0f;
    static final float MIN_Y = -2048.0f;
    static final float MAX_Y = 2048.0f;
    static final int MAX_TARGET = 128;
    static final int MAX_HIDDEN = 1024;

    public float yaw = DEFAULT_YAW;
    public float pitch = DEFAULT_PITCH;
    public float zoom = 1.0f;

    public float cx = Float.NaN;
    public float cy = Float.NaN;
    public float cz = Float.NaN;

    public float sliceLo = Float.NaN;
    public float sliceHi = Float.NaN;

    public String focus = "";

    public String hidden = "";

    public long pin = Long.MIN_VALUE;

    public boolean hasCentre() {
        return Float.isFinite(this.cx) && Float.isFinite(this.cy) && Float.isFinite(this.cz);
    }

    public boolean hasSlice() {
        return Float.isFinite(this.sliceLo) && Float.isFinite(this.sliceHi);
    }

    public void copyFrom(SurveySettings o) {
        this.yaw = o.yaw;
        this.pitch = o.pitch;
        this.zoom = o.zoom;
        this.cx = o.cx;
        this.cy = o.cy;
        this.cz = o.cz;
        this.sliceLo = o.sliceLo;
        this.sliceHi = o.sliceHi;
        this.focus = o.focus;
        this.hidden = o.hidden;
        this.pin = o.pin;
    }

    public boolean frames(SurveySettings o) {
        return same(yaw, o.yaw) && same(pitch, o.pitch) && same(zoom, o.zoom) && same(cx, o.cx) && same(cy, o.cy)
                && same(cz, o.cz) && same(sliceLo, o.sliceLo) && same(sliceHi, o.sliceHi) && focus.equals(o.focus)
                && hidden.equals(o.hidden);
    }

    private static boolean same(float a, float b) {
        return Float.isNaN(a) ? Float.isNaN(b) : Math.abs(a - b) < 1.0e-3f;
    }

    public void resetView() {
        this.yaw = DEFAULT_YAW;
        this.pitch = DEFAULT_PITCH;
        this.zoom = 1.0f;
        this.cx = Float.NaN;
        this.cy = Float.NaN;
        this.cz = Float.NaN;
    }

    void save(CompoundTag parent) {
        final CompoundTag t = new CompoundTag();
        t.putFloat("Yaw", this.yaw);
        t.putFloat("Pitch", this.pitch);
        t.putFloat("Zoom", this.zoom);
        if (hasCentre()) {
            t.putFloat("Cx", this.cx);
            t.putFloat("Cy", this.cy);
            t.putFloat("Cz", this.cz);
        }
        if (hasSlice()) {
            t.putFloat("SliceLo", this.sliceLo);
            t.putFloat("SliceHi", this.sliceHi);
        }
        t.putString("Focus", this.focus);
        t.putString("Hidden", this.hidden);
        if (this.pin != Long.MIN_VALUE) {
            t.putLong("Pin", this.pin);
        }
        parent.put(TAG, t);
    }

    void load(CompoundTag parent) {
        if (!parent.contains(TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        final CompoundTag t = parent.getCompound(TAG);
        final float yaw = t.getFloat("Yaw");
        this.yaw = Float.isFinite(yaw) ? (float) (((yaw % 360.0) + 360.0) % 360.0) : DEFAULT_YAW;
        final float pitch = t.getFloat("Pitch");
        this.pitch = Float.isFinite(pitch) ? Math.max(0.0f, Math.min(90.0f, pitch)) : DEFAULT_PITCH;
        final float zoom = t.contains("Zoom") ? t.getFloat("Zoom") : 1.0f;
        this.zoom = Float.isFinite(zoom) ? Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom)) : 1.0f;
        final float x = t.contains("Cx") ? t.getFloat("Cx") : Float.NaN;
        final float y = t.contains("Cy") ? t.getFloat("Cy") : Float.NaN;
        final float z = t.contains("Cz") ? t.getFloat("Cz") : Float.NaN;
        final boolean centre = Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Math.abs(x) <= MAX_COORD
                && Math.abs(z) <= MAX_COORD && y >= MIN_Y && y <= MAX_Y;
        this.cx = centre ? x : Float.NaN;
        this.cy = centre ? y : Float.NaN;
        this.cz = centre ? z : Float.NaN;
        final float lo = t.contains("SliceLo") ? t.getFloat("SliceLo") : Float.NaN;
        final float hi = t.contains("SliceHi") ? t.getFloat("SliceHi") : Float.NaN;
        final boolean slice = Float.isFinite(lo) && Float.isFinite(hi) && lo >= MIN_Y && hi <= MAX_Y && lo <= hi;
        this.sliceLo = slice ? lo : Float.NaN;
        this.sliceHi = slice ? hi : Float.NaN;
        final String focus = t.getString("Focus");
        this.focus = focus.length() > MAX_TARGET ? "" : focus;
        final String hidden = t.getString("Hidden");
        this.hidden = hidden.length() > MAX_HIDDEN ? "" : hidden;
        this.pin = t.contains("Pin") ? t.getLong("Pin") : Long.MIN_VALUE;
    }
}
