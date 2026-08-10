package com.createstats.display;

import java.util.Iterator;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import java.util.HashSet;
import java.util.Set;
import com.createstats.renderer.HologramDisplayMode;

public class HologramSettings
{
    private HologramDisplayMode displayMode;
    private DisplayTheme displayTheme;
    private StatsDisplaySettings.TimeRange timeRange;
    private int customAccentColor;
    private float rotationSpeed;
    private float glowIntensity;
    private boolean showLabels;
    private boolean showGrid;
    private Set<String> hiddenChannelIds;
    private int cycleInterval;
    private int hologramWidth;
    private int hologramHeight;
    private boolean sizeUnlocked;
    
    public HologramSettings() {
        this.displayMode = HologramDisplayMode.LINE_3D;
        this.displayTheme = DisplayTheme.DARK;
        this.timeRange = StatsDisplaySettings.TimeRange.FIVE_MIN;
        this.customAccentColor = 0;
        this.rotationSpeed = 0.9f;
        this.glowIntensity = 0.7f;
        this.showLabels = true;
        this.showGrid = true;
        this.hiddenChannelIds = new HashSet<String>();
        this.cycleInterval = 60;
        this.hologramWidth = 4;
        this.hologramHeight = 3;
        this.sizeUnlocked = false;
    }
    
    public void cycleDisplayMode() {
        this.displayMode = this.displayMode.next();
    }
    
    public void cycleDisplayTheme() {
        this.displayTheme = this.displayTheme.next();
    }
    
    public void cycleTimeRange() {
        final StatsDisplaySettings.TimeRange[] values = StatsDisplaySettings.TimeRange.values();
        this.timeRange = values[(this.timeRange.ordinal() + 1) % values.length];
    }
    
    public HologramDisplayMode getDisplayMode() {
        return this.displayMode;
    }
    
    public void setDisplayMode(HologramDisplayMode mode) {
        this.displayMode = mode;
    }
    
    public DisplayTheme getDisplayTheme() {
        return this.displayTheme;
    }
    
    public void setDisplayTheme(DisplayTheme theme) {
        this.displayTheme = theme;
    }
    
    public StatsDisplaySettings.TimeRange getTimeRange() {
        return this.timeRange;
    }
    
    public void setTimeRange(StatsDisplaySettings.TimeRange range) {
        this.timeRange = range;
    }
    
    public int getCustomAccentColor() {
        return this.customAccentColor;
    }
    
    public void setCustomAccentColor(int color) {
        this.customAccentColor = color;
    }
    
    public float getRotationSpeed() {
        return this.rotationSpeed;
    }
    
    public void setRotationSpeed(float speed) {
        this.rotationSpeed = speed;
    }
    
    public float getGlowIntensity() {
        return this.glowIntensity;
    }
    
    public void setGlowIntensity(float intensity) {
        this.glowIntensity = intensity;
    }
    
    public boolean isShowLabels() {
        return this.showLabels;
    }
    
    public void setShowLabels(boolean show) {
        this.showLabels = show;
    }
    
    public boolean isShowGrid() {
        return this.showGrid;
    }
    
    public void setShowGrid(boolean show) {
        this.showGrid = show;
    }
    
    public Set<String> getHiddenChannelIds() {
        return this.hiddenChannelIds;
    }
    
    public int getCycleInterval() {
        return this.cycleInterval;
    }
    
    public void setCycleInterval(int interval) {
        this.cycleInterval = interval;
    }
    
    public int getHologramWidth() {
        return this.hologramWidth;
    }
    
    public void setHologramWidth(int w) {
        final int max = this.sizeUnlocked ? 32 : 16;
        this.hologramWidth = Math.max(1, Math.min(max, w));
    }
    
    public int getHologramHeight() {
        return this.hologramHeight;
    }
    
    public void setHologramHeight(int h) {
        final int max = this.sizeUnlocked ? 32 : 16;
        this.hologramHeight = Math.max(1, Math.min(max, h));
    }
    
    public boolean isSizeUnlocked() {
        return this.sizeUnlocked;
    }
    
    public void setSizeUnlocked(boolean unlocked) {
        if (!(this.sizeUnlocked = unlocked)) {
            if (this.hologramWidth > 16) {
                this.hologramWidth = 16;
            }
            if (this.hologramHeight > 16) {
                this.hologramHeight = 16;
            }
        }
    }
    
    public int getEffectiveAccentColor() {
        return (this.customAccentColor != 0) ? this.customAccentColor : this.displayTheme.accent;
    }
    
    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("DisplayMode", this.displayMode.name());
        tag.putString("DisplayTheme", this.displayTheme.name());
        tag.putString("TimeRange", this.timeRange.name());
        tag.putInt("AccentColor", this.customAccentColor);
        tag.putFloat("RotationSpeed", this.rotationSpeed);
        tag.putFloat("GlowIntensity", this.glowIntensity);
        tag.putBoolean("ShowLabels", this.showLabels);
        tag.putBoolean("ShowGrid", this.showGrid);
        tag.putInt("CycleInterval", this.cycleInterval);
        tag.putInt("HologramWidth", this.hologramWidth);
        tag.putInt("HologramHeight", this.hologramHeight);
        tag.putBoolean("SizeUnlocked", this.sizeUnlocked);
        if (!this.hiddenChannelIds.isEmpty()) {
            final ListTag list = new ListTag();
            for (String id : this.hiddenChannelIds) {
                list.add(StringTag.valueOf(id));
            }
            tag.put("HiddenChannels", (Tag)list);
        }
        return tag;
    }
    
    public static HologramSettings load(CompoundTag tag) {
        final HologramSettings s = new HologramSettings();
        try {
            if (tag.contains("DisplayMode")) {
                s.displayMode = HologramDisplayMode.valueOf(tag.getString("DisplayMode"));
            }
        }
        catch (IllegalArgumentException ex) {}
        // [1]
        if (s.displayMode == HologramDisplayMode.NETWORK) {
            s.displayMode = HologramDisplayMode.LINE_3D;
        }
        try {
            if (tag.contains("DisplayTheme")) {
                s.displayTheme = DisplayTheme.valueOf(tag.getString("DisplayTheme"));
            }
        }
        catch (IllegalArgumentException ex2) {}
        try {
            if (tag.contains("TimeRange")) {
                s.timeRange = StatsDisplaySettings.TimeRange.valueOf(tag.getString("TimeRange"));
            }
        }
        catch (IllegalArgumentException ex3) {}
        if (tag.contains("AccentColor")) {
            s.customAccentColor = tag.getInt("AccentColor");
        }
        if (tag.contains("RotationSpeed")) {
            s.rotationSpeed = tag.getFloat("RotationSpeed");
        }
        if (tag.contains("GlowIntensity")) {
            s.glowIntensity = tag.getFloat("GlowIntensity");
        }
        if (tag.contains("ShowLabels")) {
            s.showLabels = tag.getBoolean("ShowLabels");
        }
        if (tag.contains("ShowGrid")) {
            s.showGrid = tag.getBoolean("ShowGrid");
        }
        if (tag.contains("CycleInterval")) {
            s.cycleInterval = tag.getInt("CycleInterval");
        }
        if (tag.contains("HologramWidth")) {
            s.hologramWidth = Math.max(1, Math.min(32, tag.getInt("HologramWidth")));
        }
        if (tag.contains("HologramHeight")) {
            s.hologramHeight = Math.max(1, Math.min(32, tag.getInt("HologramHeight")));
        }
        if (tag.contains("SizeUnlocked")) {
            s.sizeUnlocked = tag.getBoolean("SizeUnlocked");
        }
        if (tag.contains("HiddenChannels", 9)) {
            final ListTag list = tag.getList("HiddenChannels", 8);
            for (int i = 0; i < list.size(); ++i) {
                s.hiddenChannelIds.add(list.getString(i));
            }
        }
        return s;
    }
}
