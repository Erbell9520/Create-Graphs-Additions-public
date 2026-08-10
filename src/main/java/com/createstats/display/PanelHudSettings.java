package com.createstats.display;

import com.createstats.blockentity.SourceConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PanelHudSettings {
    private static final int VERSION = 3;
    private StressometerHudState stressometerHud;
    private List<HudWidgetState> widgets;

    public PanelHudSettings() {
        this.stressometerHud = new StressometerHudState();
        this.widgets = new ArrayList<HudWidgetState>();
        this.syncWidgetsFromStressometer();
    }

    public PanelHudSettings(PanelHudSettings other) {
        this.stressometerHud = (other != null) ? other.getStressometerHud().copy() : new StressometerHudState();
        this.widgets = new ArrayList<HudWidgetState>();
        this.syncWidgetsFromStressometer();
    }

    public StressometerHudState getStressometerHud() {
        return this.stressometerHud;
    }

    public void setStressometerHud(StressometerHudState stressometerHud) {
        this.stressometerHud = (stressometerHud != null) ? stressometerHud.copy() : new StressometerHudState();
        this.syncWidgetsFromStressometer();
    }

    public List<HudWidgetState> getWidgets() {
        this.syncWidgetsFromStressometer();
        return Collections.unmodifiableList(this.widgets);
    }

    public PanelHudSettings copy() {
        return new PanelHudSettings(this);
    }

    public boolean normalize(Map<String, SourceConfig> sourceRegistry) {
        boolean changed = this.stressometerHud.normalize(sourceRegistry);
        this.syncWidgetsFromStressometer();
        return changed;
    }

    public CompoundTag save() {
        this.syncWidgetsFromStressometer();
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        final ListTag widgetList = new ListTag();
        for (HudWidgetState widget : this.widgets) {
            widgetList.add(widget.save());
        }
        tag.put("Widgets", widgetList);
        tag.put("Stressometer", this.stressometerHud.save());
        return tag;
    }

    public static PanelHudSettings load(CompoundTag tag) {
        final PanelHudSettings settings = new PanelHudSettings();
        if (tag == null) {
            return settings;
        }
        if (tag.getInt("Version") == VERSION && tag.contains("Widgets", 9)) {
            settings.widgets.clear();
            final ListTag widgetList = tag.getList("Widgets", 10);
            for (int i = 0; i < widgetList.size() && settings.widgets.size() < 8; ++i) {
                final HudWidgetState widget = HudWidgetState.load(widgetList.getCompound(i));
                if (widget != null && HudWidgetState.STRESSOMETER_TYPE.equals(widget.getType()) && settings.findStressometerWidget() == null) {
                    settings.widgets.add(widget);
                    settings.stressometerHud = widget.getStressometerState();
                }
            }
            if (settings.findStressometerWidget() == null) {
                if (tag.contains("Stressometer", 10)) {
                    settings.stressometerHud = StressometerHudState.load(tag.getCompound("Stressometer"));
                }
                settings.syncWidgetsFromStressometer();
            }
            return settings;
        }
        if (tag.contains("Stressometer", 10)) {
            settings.stressometerHud = StressometerHudState.load(tag.getCompound("Stressometer"));
            settings.syncWidgetsFromStressometer();
            return settings;
        }
        return settings;
    }

    private HudWidgetState findStressometerWidget() {
        for (HudWidgetState widget : this.widgets) {
            if (widget != null && HudWidgetState.STRESSOMETER_TYPE.equals(widget.getType())) {
                return widget;
            }
        }
        return null;
    }

    private void syncWidgetsFromStressometer() {
        this.widgets.clear();
        this.widgets.add(HudWidgetState.stressometer(this.stressometerHud));
    }
}
