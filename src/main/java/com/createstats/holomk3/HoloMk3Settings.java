package com.createstats.holomk3;

import net.minecraft.nbt.CompoundTag;

public class HoloMk3Settings {
    public enum Style {
        VECTOR("Vector"),

        PARTICLE("Particle"),

        SLICE("Slice"),
        // the only style with real self occlusion
        SOLID("Solid"),

        PRISM("Prism");

        private final String displayName;

        Style(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Style next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    public enum Layout {
        TANK("Tank"),

        ARRAY("Array");

        private final String displayName;

        Layout(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Layout next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    public enum Motion {
        FIXED("Fixed"),

        BILLBOARD("Face me"),

        SPIN("Spin");

        private final String displayName;

        Motion(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Motion next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    public static final float MIN_EXTENT = 1.0f;
    public static final float MAX_EXTENT = 12.0f;
    public static final int MIN_LANES = 1;
    public static final int MAX_LANES = 8;

    private Style style = Style.VECTOR;
    private Layout layout = Layout.TANK;
    private Motion motion = Motion.FIXED;

    private float spinSpeed = 0.35f;

    private float volumeWidth = 3.2f;
    private float volumeHeight = 2.2f;
    private float volumeDepth = 2.0f;

    private float lift = 0.6f;

    private int laneLimit = 4;

    private float sweepSeconds = 7.0f;

    private float depthCue = 0.62f;

    private float glow = 0.85f;

    private boolean graticule = true;
    private boolean callouts = true;
    private boolean cone = true;
    private boolean floorShadows = true;

    private boolean shaderPath = false;

    private String focusChannelId = "";

    public Style getStyle() {
        return this.style;
    }

    public void setStyle(Style style) {
        this.style = style == null ? Style.VECTOR : style;
    }

    public void cycleStyle() {
        this.style = this.style.next();
    }

    public Layout getLayout() {
        return this.layout;
    }

    public void setLayout(Layout layout) {
        this.layout = layout == null ? Layout.TANK : layout;
    }

    public Motion getMotion() {
        return this.motion;
    }

    public void setMotion(Motion motion) {
        this.motion = motion == null ? Motion.FIXED : motion;
    }

    public float getSpinSpeed() {
        return this.spinSpeed;
    }

    public void setSpinSpeed(float speed) {
        this.spinSpeed = clamp(speed, 0.0f, 4.0f);
    }

    public float getVolumeWidth() {
        return this.volumeWidth;
    }

    public void setVolumeWidth(float w) {
        this.volumeWidth = clamp(w, MIN_EXTENT, MAX_EXTENT);
    }

    public float getVolumeHeight() {
        return this.volumeHeight;
    }

    public void setVolumeHeight(float h) {
        this.volumeHeight = clamp(h, MIN_EXTENT, MAX_EXTENT);
    }

    public float getVolumeDepth() {
        return this.volumeDepth;
    }

    public void setVolumeDepth(float d) {
        this.volumeDepth = clamp(d, MIN_EXTENT, MAX_EXTENT);
    }

    public float getLift() {
        return this.lift;
    }

    public void setLift(float lift) {
        this.lift = clamp(lift, 0.0f, 6.0f);
    }

    public int getLaneLimit() {
        return this.laneLimit;
    }

    public void setLaneLimit(int limit) {
        this.laneLimit = (int) clamp(limit, MIN_LANES, MAX_LANES);
    }

    public float getSweepSeconds() {
        return this.sweepSeconds;
    }

    public void setSweepSeconds(float seconds) {
        this.sweepSeconds = clamp(seconds, 0.0f, 60.0f);
    }

    public float getDepthCue() {
        return this.depthCue;
    }

    public void setDepthCue(float cue) {
        this.depthCue = clamp(cue, 0.0f, 1.0f);
    }

    public float getGlow() {
        return this.glow;
    }

    public void setGlow(float glow) {
        this.glow = clamp(glow, 0.1f, 1.5f);
    }

    public boolean isGraticule() {
        return this.graticule;
    }

    public void setGraticule(boolean on) {
        this.graticule = on;
    }

    public boolean isCallouts() {
        return this.callouts;
    }

    public void setCallouts(boolean on) {
        this.callouts = on;
    }

    public boolean isCone() {
        return this.cone;
    }

    public void setCone(boolean on) {
        this.cone = on;
    }

    public boolean isFloorShadows() {
        return this.floorShadows;
    }

    public void setFloorShadows(boolean on) {
        this.floorShadows = on;
    }

    public boolean isShaderPath() {
        return this.shaderPath;
    }

    public void setShaderPath(boolean on) {
        this.shaderPath = on;
    }

    public String getFocusChannelId() {
        return this.focusChannelId;
    }

    public void setFocusChannelId(String id) {
        this.focusChannelId = id == null ? "" : id;
    }

    private static float clamp(float v, float lo, float hi) {
        if (!Float.isFinite(v)) {
            return lo;
        }
        return v < lo ? lo : Math.min(v, hi);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Style", this.style.name());
        tag.putString("Layout", this.layout.name());
        tag.putString("Motion", this.motion.name());
        tag.putFloat("SpinSpeed", this.spinSpeed);
        tag.putFloat("VolW", this.volumeWidth);
        tag.putFloat("VolH", this.volumeHeight);
        tag.putFloat("VolD", this.volumeDepth);
        tag.putFloat("Lift", this.lift);
        tag.putInt("LaneLimit", this.laneLimit);
        tag.putFloat("SweepSeconds", this.sweepSeconds);
        tag.putFloat("DepthCue", this.depthCue);
        tag.putFloat("Glow", this.glow);
        tag.putBoolean("Graticule", this.graticule);
        tag.putBoolean("Callouts", this.callouts);
        tag.putBoolean("Cone", this.cone);
        tag.putBoolean("FloorShadows", this.floorShadows);
        tag.putBoolean("ShaderPath", this.shaderPath);
        tag.putString("FocusChannel", this.focusChannelId);
        return tag;
    }

    public static HoloMk3Settings load(CompoundTag tag) {
        final HoloMk3Settings s = new HoloMk3Settings();
        s.setStyle(enumOr(Style.class, tag.getString("Style"), Style.VECTOR));
        s.setLayout(enumOr(Layout.class, tag.getString("Layout"), Layout.TANK));
        s.setMotion(enumOr(Motion.class, tag.getString("Motion"), Motion.FIXED));
        if (tag.contains("SpinSpeed")) {
            s.setSpinSpeed(tag.getFloat("SpinSpeed"));
        }
        if (tag.contains("VolW")) {
            s.setVolumeWidth(tag.getFloat("VolW"));
        }
        if (tag.contains("VolH")) {
            s.setVolumeHeight(tag.getFloat("VolH"));
        }
        if (tag.contains("VolD")) {
            s.setVolumeDepth(tag.getFloat("VolD"));
        }
        if (tag.contains("Lift")) {
            s.setLift(tag.getFloat("Lift"));
        }
        if (tag.contains("LaneLimit")) {
            s.setLaneLimit(tag.getInt("LaneLimit"));
        }
        if (tag.contains("SweepSeconds")) {
            s.setSweepSeconds(tag.getFloat("SweepSeconds"));
        }
        if (tag.contains("DepthCue")) {
            s.setDepthCue(tag.getFloat("DepthCue"));
        }
        if (tag.contains("Glow")) {
            s.setGlow(tag.getFloat("Glow"));
        }
        if (tag.contains("Graticule")) {
            s.setGraticule(tag.getBoolean("Graticule"));
        }
        if (tag.contains("Callouts")) {
            s.setCallouts(tag.getBoolean("Callouts"));
        }
        if (tag.contains("Cone")) {
            s.setCone(tag.getBoolean("Cone"));
        }
        if (tag.contains("FloorShadows")) {
            s.setFloorShadows(tag.getBoolean("FloorShadows"));
        }
        if (tag.contains("ShaderPath")) {
            s.setShaderPath(tag.getBoolean("ShaderPath"));
        }
        s.setFocusChannelId(tag.getString("FocusChannel"));
        return s;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
