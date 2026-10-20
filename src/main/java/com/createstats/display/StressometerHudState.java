package com.createstats.display;

import com.createstats.blockentity.SourceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StressometerHudState {
    public static final int MIN_WIDTH = 220;
    public static final int MIN_HEIGHT = 150;
    private static final int HUD_MARGIN = 8;

    private boolean enabled;
    private SourceBindingMode bindingMode;
    private String boundSourceType;
    private long boundSourcePos;
    private Bounds bounds;
    private boolean showHeader;
    private boolean showNetworkName;
    private boolean showLoadBar;
    private boolean showStressUsed;
    private boolean showStressCapacity;
    private boolean showStressPercent;
    private boolean showStatus;
    private boolean showWarning;
    private boolean showRpm;
    private boolean showRemainingStress;
    private boolean showBackground;
    private boolean textShadow;
    private boolean compact;
    private float globalTextScale;
    private int globalPaddingOffset;
    private String selectedWidgetId;
    private ArrayList<PartWidget> widgets;

    public StressometerHudState() {
        this.enabled = false;
        this.bindingMode = SourceBindingMode.AUTO;
        this.boundSourceType = "Stressometer";
        this.boundSourcePos = Long.MIN_VALUE;
        this.bounds = new Bounds(620, 600, 340, 320);
        this.showHeader = true;
        this.showNetworkName = true;
        this.showLoadBar = true;
        this.showStressUsed = true;
        this.showStressCapacity = true;
        this.showStressPercent = true;
        this.showStatus = true;
        this.showWarning = true;
        this.showRpm = true;
        this.showRemainingStress = true;
        this.showBackground = true;
        this.textShadow = true;
        this.compact = false;
        this.globalTextScale = 1.0f;
        this.globalPaddingOffset = 0;
        this.widgets = new ArrayList<PartWidget>();
        this.applyPreset(LayoutPreset.FULL_PANEL);
    }

    public StressometerHudState(StressometerHudState other) {
        this.enabled = other != null && other.enabled;
        this.bindingMode = (other != null) ? other.bindingMode : SourceBindingMode.AUTO;
        this.boundSourceType = (other != null && other.boundSourceType != null && !other.boundSourceType.isEmpty()) ? other.boundSourceType : "Stressometer";
        this.boundSourcePos = (other != null) ? other.boundSourcePos : Long.MIN_VALUE;
        this.bounds = (other != null) ? other.bounds.copy() : new Bounds(620, 600, 340, 320);
        this.showHeader = other == null || other.showHeader;
        this.showNetworkName = other == null || other.showNetworkName;
        this.showLoadBar = other == null || other.showLoadBar;
        this.showStressUsed = other == null || other.showStressUsed;
        this.showStressCapacity = other == null || other.showStressCapacity;
        this.showStressPercent = other == null || other.showStressPercent;
        this.showStatus = other == null || other.showStatus;
        this.showWarning = other == null || other.showWarning;
        this.showRpm = other == null || other.showRpm;
        this.showRemainingStress = other == null || other.showRemainingStress;
        this.showBackground = other == null || other.showBackground;
        this.textShadow = other == null || other.textShadow;
        this.compact = other != null && other.compact;
        this.globalTextScale = (other != null) ? other.globalTextScale : 1.0f;
        this.globalPaddingOffset = (other != null) ? other.globalPaddingOffset : 0;
        this.selectedWidgetId = (other != null) ? other.selectedWidgetId : null;
        this.widgets = new ArrayList<PartWidget>();
        if (other != null) {
            for (PartWidget widget : other.widgets) {
                this.widgets.add(widget.copy());
            }
        }
        // only seed defaults for fresh state, an empty undo snapshot is empty on purpose

        if (other == null && this.widgets.isEmpty()) {
            this.applyPreset(this.compact ? LayoutPreset.COMPACT_ROW : LayoutPreset.FULL_PANEL);
        }
        this.ensureValidSelection();
    }

    public StressometerHudState copy() {
        return new StressometerHudState(this);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SourceBindingMode getBindingMode() {
        return this.bindingMode;
    }

    public void setBindingMode(SourceBindingMode bindingMode) {
        this.bindingMode = (bindingMode != null) ? bindingMode : SourceBindingMode.AUTO;
    }

    public String getBoundSourceType() {
        return this.boundSourceType;
    }

    public void setBoundSourceType(String boundSourceType) {
        this.boundSourceType = (boundSourceType != null && !boundSourceType.isEmpty()) ? boundSourceType : "Stressometer";
    }

    public long getBoundSourcePos() {
        return this.boundSourcePos;
    }

    public void setBoundSourcePos(long boundSourcePos) {
        this.boundSourcePos = boundSourcePos;
    }

    public Bounds getBounds() {
        return this.bounds;
    }

    public void setBounds(Bounds bounds) {
        this.bounds = (bounds != null) ? bounds.copy() : new Bounds(620, 600, 340, 320);
    }

    public boolean isHeaderVisible() {
        return this.showHeader;
    }

    public void setHeaderVisible(boolean showHeader) {
        this.showHeader = showHeader;
    }

    public boolean isNetworkNameVisible() {
        return this.showNetworkName;
    }

    public void setNetworkNameVisible(boolean showNetworkName) {
        this.showNetworkName = showNetworkName;
        this.ensurePrimaryContentVisible("source_label");
    }

    public boolean isLoadBarVisible() {
        return this.showLoadBar;
    }

    public void setLoadBarVisible(boolean showLoadBar) {
        this.showLoadBar = showLoadBar;
    }

    public boolean isStressUsedVisible() {
        return this.showStressUsed;
    }

    public void setStressUsedVisible(boolean showStressUsed) {
        this.showStressUsed = showStressUsed;
        this.ensurePrimaryContentVisible("stress_used");
    }

    public boolean isStressCapacityVisible() {
        return this.showStressCapacity;
    }

    public void setStressCapacityVisible(boolean showStressCapacity) {
        this.showStressCapacity = showStressCapacity;
        this.ensurePrimaryContentVisible("stress_capacity");
    }

    public boolean isStressPercentVisible() {
        return this.showStressPercent;
    }

    public void setStressPercentVisible(boolean showStressPercent) {
        this.showStressPercent = showStressPercent;
        this.ensurePrimaryContentVisible("stress_percent");
    }

    public boolean isStatusVisible() {
        return this.showStatus;
    }

    public void setStatusVisible(boolean showStatus) {
        this.showStatus = showStatus;
        this.ensurePrimaryContentVisible("status");
    }

    public boolean isWarningVisible() {
        return this.showWarning;
    }

    public void setWarningVisible(boolean showWarning) {
        this.showWarning = showWarning;
    }

    public boolean isRpmVisible() {
        return this.showRpm;
    }

    public void setRpmVisible(boolean showRpm) {
        this.showRpm = showRpm;
        this.ensurePrimaryContentVisible("rpm");
    }

    public boolean isRemainingStressVisible() {
        return this.showRemainingStress;
    }

    public void setRemainingStressVisible(boolean showRemainingStress) {
        this.showRemainingStress = showRemainingStress;
        this.ensurePrimaryContentVisible("remaining_stress");
    }

    public boolean isBackgroundVisible() {
        return this.showBackground;
    }

    public void setBackgroundVisible(boolean showBackground) {
        this.showBackground = showBackground;
    }

    public boolean hasTextShadow() {
        return this.textShadow;
    }

    public void setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
    }

    public boolean isCompact() {
        return this.compact;
    }

    public float getGlobalTextScale() {
        return this.globalTextScale;
    }

    public void setGlobalTextScale(float scale) {
        this.globalTextScale = Math.max(0.5f, Math.min(1.5f, scale));
    }

    public int getGlobalPaddingOffset() {
        return this.globalPaddingOffset;
    }

    public void setGlobalPaddingOffset(int offset) {
        this.globalPaddingOffset = Math.max(-4, Math.min(8, offset));
    }

    public void setCompact(boolean compact) {
        this.compact = compact;
    }

    public List<PartWidget> getWidgets() {
        return Collections.unmodifiableList(this.widgets);
    }

    public String getSelectedWidgetId() {
        return this.selectedWidgetId;
    }

    public void setSelectedWidgetId(String selectedWidgetId) {
        this.selectedWidgetId = selectedWidgetId;
        this.ensureValidSelection();
    }

    public PartWidget getSelectedWidget() {
        return this.getWidget(this.selectedWidgetId);
    }

    public PartWidget getWidget(String widgetId) {
        if (widgetId == null || widgetId.isEmpty()) {
            return null;
        }
        for (PartWidget widget : this.widgets) {
            if (widgetId.equals(widget.getId())) {
                return widget;
            }
        }
        return null;
    }

    public boolean hasPart(Part part) {
        return this.getWidget(part.id()) != null;
    }

    public PartWidget addWidget(Part part) {
        if (part == null) {
            return null;
        }
        PartWidget existing = this.getWidget(part.id());
        if (existing != null) {
            this.selectedWidgetId = existing.getId();
            return existing;
        }
        final PartWidget widget = PartWidget.create(part, this.widgets.size());
        this.widgets.add(widget);
        this.selectedWidgetId = widget.getId();
        this.syncLegacyFlagsFromWidgets();
        return widget;
    }

    public PartWidget addSourceCard(SourceConfig source) {
        if (source == null) {
            return null;
        }
        final List<SpecializedHudWidgetCatalog.Definition> definitions = SpecializedHudWidgetCatalog.definitionsFor(source.getSourceType());
        if (definitions.isEmpty()) {
            return null;
        }
        return this.addSpecializedWidget(source, definitions.get(0).part());
    }

    public PartWidget addSpecializedWidget(SourceConfig source, Part part) {
        if (source == null || part == null) {
            return null;
        }
        PartWidget existing = this.findSpecializedWidget(source, part);
        if (existing != null) {
            this.selectedWidgetId = existing.getId();
            return existing;
        }
        final SpecializedHudWidgetCatalog.Definition definition = SpecializedHudWidgetCatalog.definitionFor(source.getSourceType(), part);
        final PartWidget widget = PartWidget.createSpecialized(source, part, definition, this.widgets.size());
        this.widgets.add(widget);
        this.selectedWidgetId = widget.getId();
        return widget;
    }

    public PartWidget findSpecializedWidget(SourceConfig source, Part part) {
        if (source == null || part == null) {
            return null;
        }
        final String widgetId = PartWidget.specializedWidgetId(source, part);
        final PartWidget exact = this.getWidget(widgetId);
        if (exact != null) {
            return exact;
        }
        for (PartWidget widget : this.widgets) {
            if (widget != null && widget.matchesSpecializedSource(source, part)) {
                return widget;
            }
        }
        return null;
    }

    public boolean hasSpecializedWidget(SourceConfig source, Part part) {
        return this.findSpecializedWidget(source, part) != null;
    }

    public void clearWidgets() {
        this.widgets.clear();
        this.selectedWidgetId = null;
        this.syncLegacyFlagsFromWidgets();
    }

    public void replaceWidgets(List<PartWidget> replacement) {
        if (replacement == null || replacement.isEmpty()) {
            return;
        }
        this.widgets.clear();
        for (PartWidget widget : replacement) {
            if (widget != null && this.getWidget(widget.getId()) == null) {
                this.widgets.add(widget.copy());
            }
        }
        this.ensureValidSelection();
        this.syncLegacyFlagsFromWidgets();
    }

    public PartWidget addTrainInfoWidget() {
        return this.addUtilityWidget("train_info", Part.TRAIN_INFO);
    }

    public PartWidget addGradeWidget() {
        return this.addUtilityWidget("network_grade", Part.GRADE);
    }

    public PartWidget addUtilityWidget(String widgetId, Part part) {
        PartWidget existing = this.getWidget(widgetId);
        if (existing != null) {
            this.selectedWidgetId = existing.getId();
            return existing;
        }
        final PartWidget widget = PartWidget.createUtility(widgetId, part, this.widgets.size());
        this.widgets.add(widget);
        this.selectedWidgetId = widget.getId();
        return widget;
    }

    public void removeWidget(String widgetId) {
        if (widgetId == null || widgetId.isEmpty()) {
            return;
        }
        this.widgets.removeIf(widget -> widgetId.equals(widget.getId()));
        this.ensureValidSelection();
        this.syncLegacyFlagsFromWidgets();
    }

    public PartWidget duplicateWidget(String widgetId, int dx, int dy, int screenWidth, int screenHeight) {
        if (widgetId == null || widgetId.isEmpty()) return null;
        final PartWidget src = this.getWidget(widgetId);
        if (src == null) return null;
        final PartWidget dup = src.copy();

        String newId;
        int guard = 0;
        do {
            newId = src.getId() + "_dup" + Integer.toHexString((int)(System.nanoTime() & 0xFFFF));
            guard++;
        } while (this.getWidget(newId) != null && guard < 8);
        dup.setId(newId);

        final Bounds.ResolvedBounds b = src.resolveBounds(screenWidth, screenHeight, this);
        dup.setBoundsFromPixels(b.x() + dx, b.y() + dy, b.width(), b.height(), screenWidth, screenHeight);
        this.widgets.add(dup);
        this.selectedWidgetId = dup.getId();
        return dup;
    }

    public void applyPreset(LayoutPreset preset) {
        final LayoutPreset selected = (preset != null) ? preset : LayoutPreset.FULL_PANEL;
        this.widgets.clear();
        switch (selected) {
            case MINIMAL -> {
                this.widgets.add(PartWidget.create(Part.STRESS_PERCENT, 0, 20, 60, 220, 80));
                this.widgets.add(PartWidget.create(Part.STATUS, 1, 20, 150, 240, 70));
            }
            case COMPACT_ROW -> {
                this.widgets.add(PartWidget.create(Part.SOURCE_LABEL, 0, 20, 30, 260, 55));
                this.widgets.add(PartWidget.create(Part.STRESS_PERCENT, 1, 300, 30, 130, 55));
                this.widgets.add(PartWidget.create(Part.USED_CAPACITY, 2, 450, 30, 250, 55));
                this.widgets.add(PartWidget.create(Part.STATUS, 3, 720, 30, 180, 55));
            }
            case CARD_STACK -> {
                this.widgets.add(PartWidget.create(Part.HEADER, 0, 30, 30, 320, 60));
                this.widgets.add(PartWidget.create(Part.SOURCE_LABEL, 1, 30, 100, 380, 60));
                this.widgets.add(PartWidget.create(Part.LOAD_GAUGE, 2, 30, 180, 460, 150));
                this.widgets.add(PartWidget.create(Part.STRESS_PERCENT, 3, 30, 360, 210, 110));
                this.widgets.add(PartWidget.create(Part.STATUS, 4, 260, 360, 230, 110));
                this.widgets.add(PartWidget.create(Part.RPM, 5, 30, 500, 210, 110));
                this.widgets.add(PartWidget.create(Part.REMAINING_STRESS, 6, 260, 500, 230, 110));
            }
            case FULL_PANEL, FACTORY_FLOOR, SHIP_BRIDGE, PILOT_HUD -> {
                this.widgets.add(PartWidget.create(Part.HEADER, 0, 30, 30, 260, 58));
                this.widgets.add(PartWidget.create(Part.SOURCE_LABEL, 1, 30, 90, 360, 58));
                this.widgets.add(PartWidget.create(Part.STATUS, 2, 650, 30, 230, 70));
                this.widgets.add(PartWidget.create(Part.LOAD_GAUGE, 3, 30, 180, 850, 180));
                this.widgets.add(PartWidget.create(Part.USED_CAPACITY, 4, 30, 410, 270, 120));
                this.widgets.add(PartWidget.create(Part.STRESS_PERCENT, 5, 330, 410, 220, 120));
                this.widgets.add(PartWidget.create(Part.RPM, 6, 580, 410, 140, 120));
                this.widgets.add(PartWidget.create(Part.REMAINING_STRESS, 7, 740, 410, 180, 120));
            }
            case TRAIN_DISPATCH -> {
                this.widgets.add(PartWidget.create(Part.HEADER, 0, 30, 30, 320, 60));
                this.widgets.add(PartWidget.create(Part.SOURCE_LABEL, 1, 30, 100, 380, 60));
                this.widgets.add(PartWidget.create(Part.LOAD_GAUGE, 2, 30, 180, 460, 150));
                this.widgets.add(PartWidget.create(Part.STRESS_PERCENT, 3, 30, 360, 210, 110));
                this.widgets.add(PartWidget.create(Part.STATUS, 4, 260, 360, 230, 110));
                this.widgets.add(PartWidget.create(Part.RPM, 5, 30, 500, 210, 110));
                this.widgets.add(PartWidget.create(Part.REMAINING_STRESS, 6, 260, 500, 230, 110));
            }
        }
        this.selectedWidgetId = this.widgets.isEmpty() ? null : this.widgets.get(0).getId();
        this.syncLegacyFlagsFromWidgets();
    }

    public boolean isComponentVisible(String componentId) {
        return switch (componentId) {
            case "header" -> this.showHeader;
            case "network_name", "source_label" -> this.showNetworkName;
            case "load_bar" -> this.showLoadBar;
            case "stress_used" -> this.showStressUsed;
            case "stress_capacity" -> this.showStressCapacity;
            case "stress_percent" -> this.showStressPercent;
            case "status" -> this.showStatus;
            case "warning" -> this.showWarning;
            case "rpm" -> this.showRpm;
            case "remaining_stress" -> this.showRemainingStress;
            default -> false;
        };
    }

    public void setComponentVisible(String componentId, boolean visible) {
        switch (componentId) {
            case "header" -> this.showHeader = visible;
            case "network_name", "source_label" -> this.showNetworkName = visible;
            case "load_bar" -> this.showLoadBar = visible;
            case "stress_used" -> this.showStressUsed = visible;
            case "stress_capacity" -> this.showStressCapacity = visible;
            case "stress_percent" -> this.showStressPercent = visible;
            case "status" -> this.showStatus = visible;
            case "warning" -> this.showWarning = visible;
            case "rpm" -> this.showRpm = visible;
            case "remaining_stress" -> this.showRemainingStress = visible;
            default -> {
                return;
            }
        }
        this.ensurePrimaryContentVisible(componentId);
    }

    public void setAutoBinding() {
        this.bindingMode = SourceBindingMode.AUTO;
        this.boundSourcePos = Long.MIN_VALUE;
        this.boundSourceType = "Stressometer";
    }

    public void bindToSource(SourceConfig sourceConfig) {
        if (sourceConfig == null || sourceConfig.getSourcePos() == null) {
            this.setAutoBinding();
            return;
        }
        this.bindingMode = SourceBindingMode.FIXED;
        this.boundSourcePos = sourceConfig.getSourcePos().asLong();
        this.boundSourceType = (sourceConfig.getSourceType() != null && !sourceConfig.getSourceType().isEmpty())
                ? sourceConfig.getSourceType()
                : "Stressometer";
    }

    public String resolvePreferredSourceId(Map<String, SourceConfig> sourceRegistry) {
        if (this.bindingMode != SourceBindingMode.FIXED || sourceRegistry == null || sourceRegistry.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, SourceConfig> entry : sourceRegistry.entrySet()) {
            final SourceConfig config = entry.getValue();
            if (config == null || config.getSourcePos() == null) {
                continue;
            }
            if (config.getSourcePos().asLong() != this.boundSourcePos) {
                continue;
            }
            if (!this.boundSourceType.isEmpty() && !this.boundSourceType.equals(config.getSourceType())) {
                continue;
            }
            return entry.getKey();
        }
        return null;
    }

    public boolean normalize(Map<String, SourceConfig> sourceRegistry) {
        boolean changed = false;
        if (this.boundSourceType == null || this.boundSourceType.isEmpty()) {
            this.boundSourceType = "Stressometer";
            changed = true;
        }
        if (this.bounds.normalize()) {
            changed = true;
        }
        if (!this.hasPrimaryContentVisible()) {
            this.showStressPercent = true;
            changed = true;
        }
        if (this.widgets.isEmpty()) {
            this.rebuildWidgetsFromLegacyFlags();
            changed = true;
        }
        for (PartWidget widget : this.widgets) {
            if (widget.normalize()) {
                changed = true;
            }
        }
        this.ensureValidSelection();
        if (this.bindingMode == SourceBindingMode.FIXED && this.resolvePreferredSourceId(sourceRegistry) == null) {
            this.setAutoBinding();
            changed = true;
        }
        return changed;
    }

    public Bounds.ResolvedBounds resolveBounds(int screenWidth, int screenHeight) {
        return this.bounds.resolve(screenWidth, screenHeight);
    }

    public void setBoundsFromPixels(int x, int y, int width, int height, int screenWidth, int screenHeight) {
        this.bounds.setFromPixels(x, y, width, height, screenWidth, screenHeight);
    }

    public void resetBoundsToDefault(int screenWidth, int screenHeight) {
        final int width = Math.min(screenWidth - HUD_MARGIN * 2, Math.max(MIN_WIDTH, Math.min(340, screenWidth * 54 / 100)));
        final int height = Math.min(screenHeight - HUD_MARGIN * 2, Math.max(MIN_HEIGHT, Math.min(190, screenHeight * 42 / 100)));
        final int x = Math.max(HUD_MARGIN, screenWidth - width - HUD_MARGIN);
        final int y = Math.max(HUD_MARGIN, screenHeight - height - HUD_MARGIN);
        this.bounds.setFromPixels(x, y, width, height, screenWidth, screenHeight);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("Enabled", this.enabled);
        tag.putString("BindingMode", this.bindingMode.name());
        tag.putString("BoundSourceType", this.boundSourceType);
        if (this.boundSourcePos != Long.MIN_VALUE) {
            tag.putLong("BoundSourcePos", this.boundSourcePos);
        }
        tag.put("Bounds", this.bounds.save());
        tag.put("Components", this.saveComponents());
        tag.put("Style", this.saveStyle());
        tag.putString("SelectedWidgetId", this.selectedWidgetId != null ? this.selectedWidgetId : "");
        tag.put("PartWidgets", this.savePartWidgets());
        return tag;
    }

    public static StressometerHudState load(CompoundTag tag) {
        final StressometerHudState state = new StressometerHudState();
        if (tag == null) {
            return state;
        }
        state.enabled = tag.getBoolean("Enabled");
        if (tag.contains("BindingMode")) {
            try {
                state.bindingMode = SourceBindingMode.valueOf(tag.getString("BindingMode"));
            } catch (IllegalArgumentException ignored) {
                state.bindingMode = SourceBindingMode.AUTO;
            }
        }
        if (tag.contains("BoundSourceType")) {
            state.boundSourceType = tag.getString("BoundSourceType");
        }
        if (tag.contains("BoundSourcePos")) {
            state.boundSourcePos = tag.getLong("BoundSourcePos");
        }
        if (tag.contains("Bounds", 10)) {
            state.bounds = Bounds.load(tag.getCompound("Bounds"));
        }
        if (tag.contains("Components", 10)) {
            final CompoundTag components = tag.getCompound("Components");
            state.showHeader = !components.contains("Header") || components.getBoolean("Header");
            state.showNetworkName = !components.contains("NetworkName") || components.getBoolean("NetworkName");
            state.showLoadBar = !components.contains("LoadBar") || components.getBoolean("LoadBar");
            state.showStressUsed = !components.contains("StressUsed") || components.getBoolean("StressUsed");
            state.showStressCapacity = !components.contains("StressCapacity") || components.getBoolean("StressCapacity");
            state.showStressPercent = !components.contains("StressPercent") || components.getBoolean("StressPercent");
            state.showStatus = !components.contains("Status") || components.getBoolean("Status");
            state.showWarning = !components.contains("Warning") || components.getBoolean("Warning");
            state.showRpm = !components.contains("Rpm") || components.getBoolean("Rpm");
            state.showRemainingStress = !components.contains("RemainingStress") || components.getBoolean("RemainingStress");
        }
        if (tag.contains("Style", 10)) {
            final CompoundTag style = tag.getCompound("Style");
            state.showBackground = !style.contains("Background") || style.getBoolean("Background");
            state.textShadow = !style.contains("TextShadow") || style.getBoolean("TextShadow");
            state.compact = style.getBoolean("Compact");
            state.globalTextScale = style.contains("GlobalTextScale") ? Math.max(0.5f, Math.min(1.5f, style.getFloat("GlobalTextScale"))) : 1.0f;
            state.globalPaddingOffset = style.contains("GlobalPaddingOffset") ? Math.max(-4, Math.min(8, style.getInt("GlobalPaddingOffset"))) : 0;
        }
        if (tag.contains("PartWidgets", 9)) {
            state.loadPartWidgets(tag.getList("PartWidgets", 10));
        } else {
            state.rebuildWidgetsFromLegacyFlags();
        }
        if (tag.contains("SelectedWidgetId")) {
            state.selectedWidgetId = tag.getString("SelectedWidgetId");
        }
        state.ensureValidSelection();
        state.ensurePrimaryContentVisible("stress_percent");
        return state;
    }

    public CompoundTag saveComponents() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("Header", this.showHeader);
        tag.putBoolean("NetworkName", this.showNetworkName);
        tag.putBoolean("LoadBar", this.showLoadBar);
        tag.putBoolean("StressUsed", this.showStressUsed);
        tag.putBoolean("StressCapacity", this.showStressCapacity);
        tag.putBoolean("StressPercent", this.showStressPercent);
        tag.putBoolean("Status", this.showStatus);
        tag.putBoolean("Warning", this.showWarning);
        tag.putBoolean("Rpm", this.showRpm);
        tag.putBoolean("RemainingStress", this.showRemainingStress);
        return tag;
    }

    public CompoundTag saveStyle() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("Background", this.showBackground);
        tag.putBoolean("TextShadow", this.textShadow);
        tag.putBoolean("Compact", this.compact);
        tag.putFloat("GlobalTextScale", this.globalTextScale);
        tag.putInt("GlobalPaddingOffset", this.globalPaddingOffset);
        return tag;
    }

    private boolean hasPrimaryContentVisible() {
        return this.showNetworkName || this.showStressUsed || this.showStressCapacity || this.showStressPercent || this.showStatus || this.showRpm || this.showRemainingStress;
    }

    private void ensurePrimaryContentVisible(String changedComponentId) {
        if (this.hasPrimaryContentVisible()) {
            return;
        }
        if ("network_name".equals(changedComponentId)) {
            this.showStressPercent = true;
            return;
        }
        this.showNetworkName = true;
    }

    private ListTag savePartWidgets() {
        final ListTag list = new ListTag();
        for (PartWidget widget : this.widgets) {
            list.add(widget.save());
        }
        return list;
    }

    private void loadPartWidgets(ListTag list) {
        this.widgets.clear();
        for (int i = 0; i < list.size() && this.widgets.size() < 24; ++i) {
            final PartWidget widget = PartWidget.load(list.getCompound(i));
            if (widget != null && this.getWidget(widget.getId()) == null) {
                this.widgets.add(widget);
            }
        }
        this.syncLegacyFlagsFromWidgets();
    }

    private void rebuildWidgetsFromLegacyFlags() {
        this.widgets.clear();
        if (this.showHeader) this.widgets.add(PartWidget.create(Part.HEADER, this.widgets.size()));
        if (this.showNetworkName) this.widgets.add(PartWidget.create(Part.SOURCE_LABEL, this.widgets.size()));
        if (this.showLoadBar) this.widgets.add(PartWidget.create(Part.LOAD_GAUGE, this.widgets.size()));
        if (this.showStressUsed || this.showStressCapacity) this.widgets.add(PartWidget.create(Part.USED_CAPACITY, this.widgets.size()));
        if (this.showStressPercent) this.widgets.add(PartWidget.create(Part.STRESS_PERCENT, this.widgets.size()));
        if (this.showStatus) this.widgets.add(PartWidget.create(Part.STATUS, this.widgets.size()));
        if (this.showWarning && !this.showStatus) this.widgets.add(PartWidget.create(Part.WARNING, this.widgets.size()));
        if (this.showRpm) this.widgets.add(PartWidget.create(Part.RPM, this.widgets.size()));
        if (this.showRemainingStress) this.widgets.add(PartWidget.create(Part.REMAINING_STRESS, this.widgets.size()));
        if (this.widgets.isEmpty()) {
            this.widgets.add(PartWidget.create(Part.STRESS_PERCENT, 0));
        }
        this.ensureValidSelection();
    }

    private void syncLegacyFlagsFromWidgets() {
        this.showHeader = this.hasPart(Part.HEADER);
        this.showNetworkName = this.hasPart(Part.SOURCE_LABEL);
        this.showLoadBar = this.hasPart(Part.LOAD_GAUGE);
        this.showStressUsed = this.hasPart(Part.USED_CAPACITY);
        this.showStressCapacity = this.hasPart(Part.USED_CAPACITY);
        this.showStressPercent = this.hasPart(Part.STRESS_PERCENT);
        this.showStatus = this.hasPart(Part.STATUS);
        this.showWarning = this.hasPart(Part.WARNING) || this.hasPart(Part.STATUS);
        this.showRpm = this.hasPart(Part.RPM);
        this.showRemainingStress = this.hasPart(Part.REMAINING_STRESS);
    }

    private void ensureValidSelection() {
        if (this.getWidget(this.selectedWidgetId) != null) {
            return;
        }
        this.selectedWidgetId = this.widgets.isEmpty() ? null : this.widgets.get(0).getId();
    }

    public enum SourceBindingMode {
        AUTO,
        FIXED
    }

    public enum LayoutPreset {
        FULL_PANEL,
        COMPACT_ROW,
        CARD_STACK,
        MINIMAL,
        FACTORY_FLOOR,
        SHIP_BRIDGE,
        TRAIN_DISPATCH,
        PILOT_HUD
    }

    public enum Part {
        HEADER("header", "Header", 72, 14, 180, 24),
        SOURCE_LABEL("source_label", "Source label", 90, 16, 170, 24),
        LOAD_GAUGE("load_gauge", "Stress load gauge", 140, 36, 240, 64),
        USED_CAPACITY("used_capacity", "Used / capacity SU", 96, 28, 150, 44),
        STRESS_PERCENT("stress_percent", "Load percent", 72, 24, 110, 36),
        REMAINING_STRESS("remaining_stress", "Remaining SU", 84, 28, 130, 44),
        RPM("rpm", "Network RPM", 72, 28, 110, 44),
        STATUS("status", "Status badge", 86, 18, 130, 28),
        WARNING("warning", "Overstress warning", 110, 18, 150, 28),
        SOURCE_CARD("source_card", "Source card", 112, 32, 180, 58),
        TRAIN_INFO("train_info", "Next Train", 126, 36, 210, 68),
        GRADE("grade", "Network Grade", 86, 32, 140, 58),
        NETWORK_EFFICIENCY("network_efficiency", "Network Efficiency", 120, 44, 200, 80),
        STOCKPILE_GAUGE("stockpile_gauge", "Threshold Gauge", 150, 70, 210, 90),
        STOCKPILE_OUTPUT("stockpile_output", "Output State", 86, 24, 130, 36),
        STOCKPILE_AMOUNT("stockpile_amount", "Stored Amount", 96, 32, 150, 52),
        STOCKPILE_THRESHOLDS("stockpile_thresholds", "Threshold Range", 120, 34, 180, 56),
        STOCKPILE_TREND("stockpile_trend", "Fill Trend", 130, 42, 190, 64),
        ENGINE_BURN("engine_burn", "Burn Tracker", 140, 46, 210, 76),
        ENGINE_SPEED("engine_speed", "Generated Speed", 96, 32, 150, 52),
        ENGINE_FUEL("engine_fuel", "Fuel Status", 110, 32, 165, 52),
        ENGINE_STATUS("engine_status", "Engine State", 100, 28, 150, 44),
        VELOCITY_AXIS("velocity_axis", "Axis Velocity", 130, 42, 190, 64),
        VELOCITY_HORIZONTAL("velocity_horizontal", "Horizontal Velocity", 110, 32, 165, 52),
        VELOCITY_REDSTONE("velocity_redstone", "Signed Redstone", 104, 32, 156, 52),
        VELOCITY_HEADING("velocity_heading", "Direction / State", 130, 34, 190, 56),
        GIMBAL_ANGLES("gimbal_angles", "X / Z Angles", 120, 34, 180, 56),
        GIMBAL_ATTITUDE("gimbal_attitude", "Pitch / Bank / Yaw", 140, 42, 210, 68),
        GIMBAL_POWER("gimbal_power", "Power Directions", 150, 54, 220, 84),
        ALTITUDE_HEIGHT("altitude_height", "World Height", 96, 32, 150, 52),
        ALTITUDE_AIR_SIGNAL("altitude_air_signal", "Air Pressure Signal", 110, 34, 165, 56),
        ALTITUDE_BAND("altitude_band", "Band Range", 120, 34, 180, 56),
        DOCKING_STATE("docking_state", "Docking State", 112, 32, 170, 52),
        DOCKING_PAIR("docking_pair", "Pair / Distance", 130, 38, 190, 62),
        DOCKING_POWER("docking_power", "Power / Lock", 116, 34, 175, 56),
        DOCKING_POSITION("docking_position", "Pair Position", 150, 40, 220, 64),
        STEERING_ANGLE("steering_angle", "Angle Bar", 130, 42, 190, 64),
        STEERING_SPEED("steering_speed", "Generated / Logical", 130, 36, 190, 58),
        STEERING_ACTIVITY("steering_activity", "Steering Activity", 110, 32, 165, 52),
        LASER_POWER("laser_power", "Laser Power", 96, 32, 150, 52),
        LASER_HIT("laser_hit", "Hit / Range", 116, 34, 175, 56),
        OPTICAL_RAY("optical_ray", "Ray / Hit", 116, 34, 175, 56),
        OPTICAL_BLOCK("optical_block", "Detected Block", 140, 34, 210, 56),
        OPTICAL_POWER("optical_power", "Optical Power", 96, 32, 150, 52),
        NAV_DISTANCE("nav_distance", "Target Distance", 118, 34, 180, 56),
        NAV_DELTA("nav_delta", "Distance Delta", 118, 34, 180, 56),
        NAV_ANGLE("nav_angle", "Relative Angle", 110, 32, 165, 52),
        NAV_POWER("nav_power", "Navigation Power", 150, 54, 220, 84),
        TYPEWRITER_TEXT("typewriter_text", "Live Text", 160, 48, 240, 78),
        TYPEWRITER_STATUS("typewriter_status", "Keys / Power", 112, 32, 170, 52),
        AIR_SIGNAL("air_signal", "Signal / Output", 116, 34, 175, 56),
        AIR_VOLUME("air_volume", "Target / Capacity", 130, 38, 195, 62),
        AIR_FLOW("air_flow", "Gas Flow Rate", 130, 38, 195, 62),
        AIR_LIFT("air_lift", "Balloon Lift", 110, 32, 165, 52),
        CONTRAPTION_SOLVER("contraption_solver", "Solver Panel", 138, 42, 208, 70),
        CONTRAPTION_FORCES("contraption_forces", "Force Cards", 110, 44, 240, 78),
        CONTRAPTION_SUPPORT("contraption_support", "Support Cards", 106, 44, 225, 78),
        CONTRAPTION_MARGINS("contraption_margins", "Solver Footer", 90, 32, 240, 72),
        AERO_HEALTH_STRIP("aero_health_strip", "Source Health Strip", 140, 24, 250, 50),
        AERO_DRIVE("aero_drive", "Drive / Speed", 90, 36, 228, 88),
        AERO_ALTITUDE("aero_altitude", "Altitude Card", 88, 32, 188, 76),
        AERO_RANGE("aero_range", "Range Card", 88, 32, 206, 76),
        AERO_ORIENTATION("aero_orientation", "Orientation / Trim", 106, 40, 254, 108),
        AERO_VERTICAL_MARGIN("aero_vertical_margin", "Vertical Margin", 84, 32, 178, 76),
        AERO_FORWARD_MARGIN("aero_forward_margin", "Forward Margin", 84, 32, 178, 76),
        AERO_BRIDGE_ORDERS("aero_bridge_orders", "Bridge Orders", 100, 36, 228, 84),
        AERO_ENGINE_STRIP("aero_engine_strip", "Propulsion Strip", 108, 38, 238, 90),
        AERO_SHIP_STATUS("aero_ship_status", "Ship Status", 106, 38, 228, 88),
        AERO_HORIZON("aero_horizon", "Attitude Indicator", 68, 64, 120, 120),
        AEROLINE_MODE_BADGE("aeroline_mode_badge", "Mode Badge", 132, 30, 192, 52),
        AEROLINE_STATUS_STRIP("aeroline_status_strip", "Status Strip", 120, 24, 250, 58),
        AEROLINE_DIRECTOR_WINDOW("aeroline_director_window", "Director Window", 190, 110, 290, 180),
        AEROLINE_TARGET_SCOPE("aeroline_target_scope", "Target Scope", 86, 42, 188, 96),
        AEROLINE_VELOCITY_SCOPE("aeroline_velocity_scope", "Velocity Scope", 104, 42, 228, 96),
        AEROLINE_ALTITUDE_SCOPE("aeroline_altitude_scope", "Altitude Scope", 86, 42, 188, 96),
        AEROLINE_FOOTER("aeroline_footer", "Director Footer", 120, 38, 252, 94),
        OBSERVER_SUMMARY("observer_summary", "Observer Status", 130, 38, 180, 60),
        OBSERVER_ITEMS("observer_items", "Item List", 140, 48, 190, 68),
        OBSERVER_FLUIDS("observer_fluids", "Fluid List", 140, 48, 190, 68),
        FLUID_LEVEL("fluid_level", "Tank Level", 120, 50, 170, 72),
        FLUID_VOLUME("fluid_volume", "Fluid Volume", 110, 30, 154, 48),
        FLUID_COMPACT("fluid_compact", "Tank Overview", 130, 40, 188, 64),
        BOILER_HEAT("boiler_heat", "Boiler Heat", 130, 42, 178, 62),
        BOILER_WATER("boiler_water", "Water Supply", 110, 30, 154, 50),
        BOILER_ENGINES("boiler_engines", "Engines / Whistles", 120, 30, 165, 50),
        PRODUCTION_RATE("production_rate", "Throughput", 138, 46, 192, 68),
        PRODUCTION_TOTAL("production_total", "Total Seen", 100, 28, 140, 44),
        PRODUCTION_CONTENT("production_content", "Content View", 148, 54, 220, 88),
        FACTORY_STOCK("factory_stock", "Factory Stock", 138, 46, 192, 68),
        FACTORY_SHORTFALL("factory_shortfall", "Shortfall", 122, 36, 170, 56),
        TRAIN_STATION_STATUS("train_station_status", "Station Status", 116, 32, 178, 58),
        INBOUND_TRAINS("inbound_trains", "Inbound Trains", 148, 44, 204, 68),
        SCHEDULE_DESTINATION("schedule_destination", "Schedule / Destination", 130, 38, 188, 62),
        SIGNAL_WAIT("signal_wait", "Signal Wait", 100, 30, 154, 50);

        private final String id;
        private final String label;
        private final int minWidth;
        private final int minHeight;
        private final int defaultWidth;
        private final int defaultHeight;

        Part(String id, String label, int minWidth, int minHeight, int defaultWidth, int defaultHeight) {
            this.id = id;
            this.label = label;
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.defaultWidth = defaultWidth;
            this.defaultHeight = defaultHeight;
        }

        public String id() { return this.id; }
        public String label() { return this.label; }
        public int minWidth() { return this.minWidth; }
        public int minHeight() { return this.minHeight; }
        public int defaultWidth() { return this.defaultWidth; }
        public int defaultHeight() { return this.defaultHeight; }

        public static Part byId(String id) {
            if (id == null || id.isEmpty()) return null;
            for (Part part : values()) {
                if (part.id.equals(id)) {
                    return part;
                }
            }
            return PART_ALIASES.get(id);
        }

        // append only. renames go here or old layouts stop loading

        private static final java.util.Map<String, Part> PART_ALIASES = new java.util.concurrent.ConcurrentHashMap<String, Part>();
        public static void alias(String legacyId, Part target) {
            if (legacyId == null || legacyId.isEmpty() || target == null) {
                throw new IllegalArgumentException("alias requires non-empty legacyId and non-null target");
            }
            for (Part p : values()) {
                if (p.id.equals(legacyId)) {
                    throw new IllegalArgumentException("alias legacyId '" + legacyId + "' collides with current Part id");
                }
            }
            final Part previous = PART_ALIASES.putIfAbsent(legacyId, target);
            if (previous != null && previous != target) {
                throw new IllegalArgumentException("alias '" + legacyId + "' already registered to " + previous.id());
            }
        }

        public boolean isStressometerWidget() {
            return switch (this) {
                case HEADER, SOURCE_LABEL, LOAD_GAUGE, USED_CAPACITY, STRESS_PERCENT, REMAINING_STRESS, RPM, STATUS, WARNING -> true;
                default -> false;
            };
        }
    }

    public enum SourceCategory {
        STRESSOMETER("Topology"),
        MOTION("Motion"),
        OBSERVERS("Content Detector"),
        STORAGE("Storage"),
        TUNNELS("Tunnels"),
        TRAINS("Trains"),
        SHIPS("Ships"),
        EFFICIENCY("Utility"),
        MACHINES("Machines"),
        OTHER("Other");

        private final String label;

        SourceCategory(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public static SourceCategory byId(String id) {
            if (id != null && !id.isEmpty()) {
                if ("TOPOLOGY".equals(id)) {
                    return STRESSOMETER;
                }
                for (SourceCategory category : values()) {
                    if (category.name().equals(id)) {
                        return category;
                    }
                }
            }
            return STRESSOMETER;
        }

        public static SourceCategory forSourceType(String sourceType) {
            if (sourceType == null) {
                return OTHER;
            }
            return switch (sourceType) {
                case "Stressometer", "Speedometer", "Safety Clutch", "Auto Variable Clutch", "Load Balancer" -> STRESSOMETER;
                case "Velocity Sensor", "Gimbal Sensor", "Altitude Sensor", "Optical Sensor", "Laser Sensor" -> MOTION;
                case "Content Observer", "Smart Observer", "Production Detector" -> OBSERVERS;
                case "Stockpile Switch", "Fluid Tank", "Steam Boiler", "Factory Panel" -> STORAGE;
                case "Tunnel" -> TUNNELS;
                case "Train Station", "Freight Manifest", "Dispatch Board", "Navigation Table" -> TRAINS;
                case "Aero Flight Deck", "Aeroline Director" -> SHIPS;
                case "Portable Engine", "Docking Connector", "Steering Wheel", "Linked Typewriter", "Hot Air Burner", "Steam Vent", "Contraption Info", "Mini Display" -> MACHINES;
                default -> OTHER;
            };
        }
    }

    public static class PartWidget {
        private String id;
        private Part part;
        private Bounds bounds;
        private boolean background;
        private boolean textShadow;
        private SourceCategory sourceCategory;
        private String sourceId;
        private String sourceType;
        private long sourcePos;
        private String metricKey;
        private String metricLabel;
        private float textScale;
        private int paddingOffset;
        private String renderVariant;

        private PartWidget(String id, Part part, Bounds bounds, boolean background, boolean textShadow) {
            this(id, part, bounds, background, textShadow, SourceCategory.STRESSOMETER, "", "", Long.MIN_VALUE, "", "", 0.0f, 0);
        }

        private PartWidget(String id, Part part, Bounds bounds, boolean background, boolean textShadow, SourceCategory sourceCategory, String sourceId, String sourceType, long sourcePos, String metricKey, String metricLabel) {
            this(id, part, bounds, background, textShadow, sourceCategory, sourceId, sourceType, sourcePos, metricKey, metricLabel, 0.0f, 0);
        }

        private PartWidget(String id, Part part, Bounds bounds, boolean background, boolean textShadow, SourceCategory sourceCategory, String sourceId, String sourceType, long sourcePos, String metricKey, String metricLabel, float textScale, int paddingOffset) {
            this(id, part, bounds, background, textShadow, sourceCategory, sourceId, sourceType, sourcePos, metricKey, metricLabel, textScale, paddingOffset, "");
        }

        private PartWidget(String id, Part part, Bounds bounds, boolean background, boolean textShadow, SourceCategory sourceCategory, String sourceId, String sourceType, long sourcePos, String metricKey, String metricLabel, float textScale, int paddingOffset, String renderVariant) {
            this.id = (id != null && !id.isEmpty()) ? id : part.id();
            this.part = (part != null) ? part : Part.STRESS_PERCENT;
            this.bounds = (bounds != null) ? bounds.copy() : new Bounds(80, 80, 120, 60);
            this.background = background;
            this.textShadow = textShadow;
            this.sourceCategory = sourceCategory != null ? sourceCategory : SourceCategory.STRESSOMETER;
            this.sourceId = limit(sourceId, 96);
            this.sourceType = limit(sourceType, 96);
            this.sourcePos = sourcePos;
            this.metricKey = limit(metricKey, 96);
            this.metricLabel = limit(metricLabel, 96);
            this.textScale = textScale;
            this.paddingOffset = paddingOffset;
            this.renderVariant = limit(renderVariant != null ? renderVariant : "", 32);
            this.normalize();
        }

        public static PartWidget create(Part part, int index) {
            final int col = Math.max(0, index) % 3;
            final int row = Math.max(0, index) / 3;
            final int x = 40 + col * 300;
            final int y = Math.min(780, 40 + row * 170);
            return create(part, index, x, y, part.defaultWidth(), part.defaultHeight());
        }

        public static PartWidget create(Part part, int index, int xPermille, int yPermille, int widthPermille, int heightPermille) {
            return new PartWidget(part.id(), part, new Bounds(xPermille, yPermille, widthPermille, heightPermille), true, true);
        }

        public static PartWidget createSourceCard(SourceConfig source, int index) {
            return createSpecialized(source, Part.SOURCE_CARD, null, index);
        }

        public static PartWidget createSpecialized(SourceConfig source, Part part, SpecializedHudWidgetCatalog.Definition definition, int index) {
            final PartWidget positioned = create(part, index);
            final String metricKey = definition != null ? definition.primaryKey() : "auto";
            final String metricLabel = definition != null ? definition.label() : source.getSourceType();
            return new PartWidget(
                    specializedWidgetId(source, part),
                    part,
                    positioned.bounds,
                    true,
                    true,
                    SourceCategory.forSourceType(source.getSourceType()),
                    source.getSourceId(),
                    source.getSourceType(),
                    source.getSourcePos() != null ? source.getSourcePos().asLong() : Long.MIN_VALUE,
                    metricKey,
                    metricLabel
            );
        }

        public static PartWidget createUtility(String widgetId, Part part, int index) {
            final PartWidget positioned = create(part, index);
            final SourceCategory category = part == Part.TRAIN_INFO ? SourceCategory.TRAINS : SourceCategory.EFFICIENCY;
            return new PartWidget(widgetId, part, positioned.bounds, true, true, category, "", "", Long.MIN_VALUE, part.id(), part.label());
        }

        public static String sourceWidgetId(SourceConfig source) {
            return specializedWidgetId(source, Part.SOURCE_CARD);
        }

        public static String specializedWidgetId(SourceConfig source, Part part) {
            final String id = source != null ? source.getSourceId() : "";
            final String partId = part != null ? part.id() : "source";
            return "source_" + sanitizeId(id) + "_" + sanitizeId(partId);
        }

        public PartWidget copy() {
            return new PartWidget(this.id, this.part, this.bounds, this.background, this.textShadow, this.sourceCategory, this.sourceId, this.sourceType, this.sourcePos, this.metricKey, this.metricLabel, this.textScale, this.paddingOffset, this.renderVariant);
        }

        public String getId() { return this.id; }

        void setId(String id) { this.id = (id != null && !id.isEmpty()) ? id : this.id; }
        public Part getPart() { return this.part; }
        public SourceCategory getSourceCategory() { return this.sourceCategory; }
        public String getSourceId() { return this.sourceId; }
        public String getSourceType() { return this.sourceType; }
        public long getSourcePos() { return this.sourcePos; }
        public String getMetricKey() { return this.metricKey; }
        public String getMetricLabel() { return this.metricLabel; }
        public boolean isStressometerPart() { return this.part.isStressometerWidget(); }
        public boolean hasBackground() { return this.background; }
        public void setBackground(boolean background) { this.background = background; }
        public boolean hasTextShadow() { return this.textShadow; }
        public void setTextShadow(boolean textShadow) { this.textShadow = textShadow; }
        public Bounds getBounds() { return this.bounds; }
        public float getTextScale() { return this.textScale; }
        public void setTextScale(float scale) { this.textScale = Math.max(0.0f, Math.min(1.5f, scale)); }
        public int getPaddingOffset() { return this.paddingOffset; }
        public void setPaddingOffset(int offset) { this.paddingOffset = Math.max(-4, Math.min(8, offset)); }

        public String getRenderVariant() { return this.renderVariant; }
        public void setRenderVariant(String variant) { this.renderVariant = limit(variant != null ? variant : "", 32); }
        public float getEffectiveTextScale(float globalScale) { return this.textScale > 0.01f ? this.textScale : globalScale; }
        public int getEffectivePaddingOffset(int globalOffset) { return globalOffset + this.paddingOffset; }

        public Bounds.ResolvedBounds resolveBounds(int screenWidth, int screenHeight) {
            return this.bounds.resolve(screenWidth, screenHeight, this.part.minWidth(), this.part.minHeight());
        }

        public Bounds.ResolvedBounds resolveBounds(int screenWidth, int screenHeight, StressometerHudState state) {
            final float scale = state != null ? this.getEffectiveTextScale(state.getGlobalTextScale()) : 1.0f;
            final int scaledMinW = Math.max(8, Math.round(this.part.minWidth() * scale));
            final int scaledMinH = Math.max(8, Math.round(this.part.minHeight() * scale));
            return this.bounds.resolve(screenWidth, screenHeight, scaledMinW, scaledMinH);
        }

        public void setBoundsFromPixels(int x, int y, int width, int height, int screenWidth, int screenHeight) {
            this.bounds.setFromPixels(x, y, width, height, screenWidth, screenHeight, this.part.minWidth(), this.part.minHeight());
        }

        public boolean normalize() {
            boolean changed = this.migrateSourceCard();
            if (this.migrateLegacySpecializedPart()) {
                changed = true;
            }
            if (this.bounds.normalize()) {
                changed = true;
            }
            return changed;
        }

        private boolean migrateSourceCard() {
            if (this.part != Part.SOURCE_CARD) {
                return false;
            }
            final List<SpecializedHudWidgetCatalog.Definition> definitions = SpecializedHudWidgetCatalog.definitionsFor(this.sourceType);
            if (definitions.isEmpty()) {
                return false;
            }
            final SpecializedHudWidgetCatalog.Definition definition = definitions.get(0);
            this.part = definition.part();
            this.metricKey = limit(definition.primaryKey(), 96);
            this.metricLabel = limit(definition.label(), 96);
            return true;
        }

        private boolean migrateLegacySpecializedPart() {
            final Part migrated = legacyReplacement(this.sourceType, this.part);
            if (migrated == null || migrated == this.part) {
                return false;
            }
            this.part = migrated;
            final SpecializedHudWidgetCatalog.Definition definition = SpecializedHudWidgetCatalog.definitionFor(this.sourceType, migrated);
            if (definition != null) {
                this.metricKey = limit(definition.primaryKey(), 96);
                this.metricLabel = limit(definition.label(), 96);
            }
            return true;
        }

        public boolean matchesSpecializedSource(SourceConfig source, Part part) {
            if (source == null || part == null || this.part != part) {
                return false;
            }
            if (!this.sourceId.isEmpty() && this.sourceId.equals(source.getSourceId())) {
                return true;
            }
            if (this.sourcePos != Long.MIN_VALUE && source.getSourcePos() != null && this.sourcePos == source.getSourcePos().asLong()) {
                return true;
            }
            return !this.sourceType.isEmpty() && this.sourceType.equals(source.getSourceType());
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.id);
            tag.putString("Part", this.part.id());
            tag.putString("SourceCategory", this.sourceCategory.name());
            tag.putString("SourceId", this.sourceId);
            tag.putString("SourceType", this.sourceType);
            if (this.sourcePos != Long.MIN_VALUE) {
                tag.putLong("SourcePos", this.sourcePos);
            }
            tag.putString("MetricKey", this.metricKey);
            tag.putString("MetricLabel", this.metricLabel);
            tag.put("Bounds", this.bounds.save());
            final CompoundTag style = new CompoundTag();
            style.putBoolean("Background", this.background);
            style.putBoolean("TextShadow", this.textShadow);
            if (this.textScale > 0.01f) {
                style.putFloat("TextScale", this.textScale);
            }
            if (this.paddingOffset != 0) {
                style.putInt("PaddingOffset", this.paddingOffset);
            }
            if (!this.renderVariant.isEmpty()) {
                style.putString("Variant", this.renderVariant);
            }
            tag.put("Style", style);
            return tag;
        }

        public static PartWidget load(CompoundTag tag) {
            if (tag == null) {
                return null;
            }
            final Part part = Part.byId(tag.getString("Part"));
            if (part == null) {
                return null;
            }
            Bounds bounds = new Bounds(60, 60, part.defaultWidth(), part.defaultHeight());
            if (tag.contains("Bounds", 10)) {
                bounds = Bounds.load(tag.getCompound("Bounds"));
            }
            boolean background = true;
            boolean textShadow = true;
            SourceCategory sourceCategory = SourceCategory.byId(tag.getString("SourceCategory"));
            String sourceId = tag.getString("SourceId");
            String sourceType = tag.getString("SourceType");
            long sourcePos = tag.contains("SourcePos") ? tag.getLong("SourcePos") : Long.MIN_VALUE;
            String metricKey = tag.getString("MetricKey");
            String metricLabel = tag.getString("MetricLabel");
            if (tag.contains("Style", 10)) {
                final CompoundTag style = tag.getCompound("Style");
                background = !style.contains("Background") || style.getBoolean("Background");
                textShadow = !style.contains("TextShadow") || style.getBoolean("TextShadow");
            }
            final float textScale = tag.contains("Style", 10) && tag.getCompound("Style").contains("TextScale") ? tag.getCompound("Style").getFloat("TextScale") : 0.0f;
            final int paddingOffset = tag.contains("Style", 10) && tag.getCompound("Style").contains("PaddingOffset") ? tag.getCompound("Style").getInt("PaddingOffset") : 0;
            final String renderVariant = tag.contains("Style", 10) && tag.getCompound("Style").contains("Variant") ? tag.getCompound("Style").getString("Variant") : "";
            return new PartWidget(tag.getString("Id"), part, bounds, background, textShadow, sourceCategory, sourceId, sourceType, sourcePos, metricKey, metricLabel, textScale, paddingOffset, renderVariant);
        }

        private static String sanitizeId(String value) {
            if (value == null || value.isEmpty()) {
                return "unknown";
            }
            return value.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }

        private static Part legacyReplacement(String sourceType, Part part) {
            if (sourceType == null || part == null) {
                return null;
            }
            return switch (sourceType) {
                case "Gimbal Sensor" -> switch (part) {
                    case GIMBAL_ATTITUDE -> Part.GIMBAL_ANGLES;
                    default -> null;
                };
                case "Aero Flight Deck" -> switch (part) {
                    case VELOCITY_AXIS -> Part.AERO_DRIVE;
                    case GIMBAL_ATTITUDE -> Part.AERO_ORIENTATION;
                    case ALTITUDE_HEIGHT -> Part.AERO_ALTITUDE;
                    case CONTRAPTION_MARGINS -> Part.AERO_VERTICAL_MARGIN;
                    case STATUS -> Part.AERO_SHIP_STATUS;
                    default -> null;
                };
                case "Aeroline Director" -> switch (part) {
                    case STATUS -> Part.AEROLINE_MODE_BADGE;
                    case VELOCITY_AXIS -> Part.AEROLINE_STATUS_STRIP;
                    case GIMBAL_ATTITUDE -> Part.AEROLINE_DIRECTOR_WINDOW;
                    case ALTITUDE_HEIGHT -> Part.AEROLINE_ALTITUDE_SCOPE;
                    case CONTRAPTION_MARGINS -> Part.AEROLINE_FOOTER;
                    default -> null;
                };
                default -> null;
            };
        }

        private static String limit(String value, int maxLength) {
            if (value == null) {
                return "";
            }
            return value.length() > maxLength ? value.substring(0, maxLength) : value;
        }
    }

    public static class Bounds {
        private int xPermille;
        private int yPermille;
        private int widthPermille;
        private int heightPermille;

        public Bounds(int xPermille, int yPermille, int widthPermille, int heightPermille) {
            this.xPermille = xPermille;
            this.yPermille = yPermille;
            this.widthPermille = widthPermille;
            this.heightPermille = heightPermille;
            this.normalize();
        }

        public Bounds(Bounds other) {
            this(
                    (other != null) ? other.xPermille : 620,
                    (other != null) ? other.yPermille : 600,
                    (other != null) ? other.widthPermille : 340,
                    (other != null) ? other.heightPermille : 320
            );
        }

        public Bounds copy() {
            return new Bounds(this);
        }

        public boolean normalize() {
            final int oldX = this.xPermille;
            final int oldY = this.yPermille;
            final int oldW = this.widthPermille;
            final int oldH = this.heightPermille;
            this.widthPermille = clamp(this.widthPermille, 10, 1000);
            this.heightPermille = clamp(this.heightPermille, 10, 1000);
            this.xPermille = clamp(this.xPermille, 0, 1000 - this.widthPermille);
            this.yPermille = clamp(this.yPermille, 0, 1000 - this.heightPermille);
            return oldX != this.xPermille || oldY != this.yPermille || oldW != this.widthPermille || oldH != this.heightPermille;
        }

        public ResolvedBounds resolve(int screenWidth, int screenHeight) {
            return this.resolve(screenWidth, screenHeight, StressometerHudState.MIN_WIDTH, StressometerHudState.MIN_HEIGHT);
        }

        public ResolvedBounds resolve(int screenWidth, int screenHeight, int minWidth, int minHeight) {
            if (screenWidth <= 0 || screenHeight <= 0) {
                return new ResolvedBounds(0, 0, Math.max(1, minWidth), Math.max(1, minHeight));
            }
            int width = Math.max(minWidth, Math.round(screenWidth * this.widthPermille / 1000.0f));
            int height = Math.max(minHeight, Math.round(screenHeight * this.heightPermille / 1000.0f));
            width = Math.min(width, Math.max(minWidth, screenWidth - HUD_MARGIN * 2));
            height = Math.min(height, Math.max(minHeight, screenHeight - HUD_MARGIN * 2));
            int x = Math.round(screenWidth * this.xPermille / 1000.0f);
            int y = Math.round(screenHeight * this.yPermille / 1000.0f);
            x = clamp(x, HUD_MARGIN, Math.max(HUD_MARGIN, screenWidth - width - HUD_MARGIN));
            y = clamp(y, HUD_MARGIN, Math.max(HUD_MARGIN, screenHeight - height - HUD_MARGIN));
            return new ResolvedBounds(x, y, width, height);
        }

        public void setFromPixels(int x, int y, int width, int height, int screenWidth, int screenHeight) {
            this.setFromPixels(x, y, width, height, screenWidth, screenHeight, StressometerHudState.MIN_WIDTH, StressometerHudState.MIN_HEIGHT);
        }

        public void setFromPixels(int x, int y, int width, int height, int screenWidth, int screenHeight, int minWidth, int minHeight) {
            if (screenWidth <= 0 || screenHeight <= 0) {
                return;
            }
            final int clampedWidth = Math.min(Math.max(minWidth, width), Math.max(minWidth, screenWidth - HUD_MARGIN * 2));
            final int clampedHeight = Math.min(Math.max(minHeight, height), Math.max(minHeight, screenHeight - HUD_MARGIN * 2));
            final int clampedX = clamp(x, HUD_MARGIN, Math.max(HUD_MARGIN, screenWidth - clampedWidth - HUD_MARGIN));
            final int clampedY = clamp(y, HUD_MARGIN, Math.max(HUD_MARGIN, screenHeight - clampedHeight - HUD_MARGIN));
            this.xPermille = Math.round(clampedX * 1000.0f / Math.max(1, screenWidth));
            this.yPermille = Math.round(clampedY * 1000.0f / Math.max(1, screenHeight));
            this.widthPermille = Math.round(clampedWidth * 1000.0f / Math.max(1, screenWidth));
            this.heightPermille = Math.round(clampedHeight * 1000.0f / Math.max(1, screenHeight));
            this.normalize();
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putInt("XPermille", this.xPermille);
            tag.putInt("YPermille", this.yPermille);
            tag.putInt("WidthPermille", this.widthPermille);
            tag.putInt("HeightPermille", this.heightPermille);
            return tag;
        }

        public static Bounds load(CompoundTag tag) {
            return new Bounds(
                    tag.getInt("XPermille"),
                    tag.getInt("YPermille"),
                    tag.getInt("WidthPermille"),
                    tag.getInt("HeightPermille")
            );
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        public record ResolvedBounds(int x, int y, int width, int height) {
        }
    }
}
