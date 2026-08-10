package com.createstats.display;

import com.createstats.blockentity.SourceConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class HudWidgetState {
    public static final String STRESSOMETER_TYPE = "stressometer";
    public static final String STRESSOMETER_ID = "stressometer";
    private static final String[] STRESSOMETER_COMPONENTS = {
            "header",
            "source_label",
            "load_bar",
            "stress_used",
            "stress_capacity",
            "stress_percent",
            "status",
            "warning",
            "rpm",
            "remaining_stress"
    };

    private final String id;
    private final String type;
    private final StressometerHudState stressometerState;

    private HudWidgetState(String id, String type, StressometerHudState stressometerState) {
        this.id = normalizeId(id);
        this.type = STRESSOMETER_TYPE;
        this.stressometerState = (stressometerState != null) ? stressometerState.copy() : new StressometerHudState();
    }

    public static HudWidgetState stressometer(StressometerHudState state) {
        return new HudWidgetState(STRESSOMETER_ID, STRESSOMETER_TYPE, state);
    }

    public String getId() {
        return this.id;
    }

    public String getType() {
        return this.type;
    }

    public StressometerHudState getStressometerState() {
        return this.stressometerState.copy();
    }

    public boolean normalize(Map<String, SourceConfig> sourceRegistry) {
        return this.stressometerState.normalize(sourceRegistry);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("Type", this.type);
        tag.putBoolean("Enabled", this.stressometerState.isEnabled());
        tag.put("Bounds", this.stressometerState.getBounds().save());

        final CompoundTag source = new CompoundTag();
        source.putString("Mode", this.stressometerState.getBindingMode().name());
        source.putString("Family", "stress");
        source.putString("SourceType", this.stressometerState.getBoundSourceType());
        if (this.stressometerState.getBindingMode() == StressometerHudState.SourceBindingMode.FIXED && this.stressometerState.getBoundSourcePos() != Long.MIN_VALUE) {
            source.putLong("SourcePos", this.stressometerState.getBoundSourcePos());
        }
        tag.put("Source", source);

        final ListTag components = new ListTag();
        for (int i = 0; i < STRESSOMETER_COMPONENTS.length; ++i) {
            final String componentId = STRESSOMETER_COMPONENTS[i];
            final CompoundTag component = new CompoundTag();
            component.putString("Id", componentId);
            component.putBoolean("Visible", this.stressometerState.isComponentVisible(componentId));
            components.add(component);
        }
        tag.put("Components", components);
        tag.put("Style", this.stressometerState.saveStyle());
        tag.put("Stressometer", this.stressometerState.save());
        return tag;
    }

    @Nullable
    public static HudWidgetState load(CompoundTag tag) {
        if (tag == null || !STRESSOMETER_TYPE.equals(tag.getString("Type"))) {
            return null;
        }
        final StressometerHudState state = new StressometerHudState();
        if (tag.contains("Stressometer", 10)) {
            return new HudWidgetState(tag.getString("Id"), STRESSOMETER_TYPE, StressometerHudState.load(tag.getCompound("Stressometer")));
        }
        state.setEnabled(tag.getBoolean("Enabled"));
        if (tag.contains("Bounds", 10)) {
            state.setBounds(StressometerHudState.Bounds.load(tag.getCompound("Bounds")));
        }
        if (tag.contains("Source", 10)) {
            final CompoundTag source = tag.getCompound("Source");
            if (source.contains("Family") && !"stress".equals(source.getString("Family"))) {
                state.setAutoBinding();
            } else if (source.contains("Mode")) {
                try {
                    state.setBindingMode(StressometerHudState.SourceBindingMode.valueOf(source.getString("Mode")));
                } catch (IllegalArgumentException ignored) {
                    state.setBindingMode(StressometerHudState.SourceBindingMode.AUTO);
                }
            }
            state.setBoundSourceType(limit(source.getString("SourceType"), 64));
            if (state.getBindingMode() == StressometerHudState.SourceBindingMode.FIXED && source.contains("SourcePos")) {
                state.setBoundSourcePos(source.getLong("SourcePos"));
            } else {
                state.setAutoBinding();
            }
        }
        if (tag.contains("Components", 9)) {
            final ListTag components = tag.getList("Components", 10);
            for (int i = 0; i < components.size(); ++i) {
                final CompoundTag component = components.getCompound(i);
                state.setComponentVisible(component.getString("Id"), component.getBoolean("Visible"));
            }
        }
        if (tag.contains("Style", 10)) {
            final CompoundTag style = tag.getCompound("Style");
            state.setBackgroundVisible(!style.contains("Background") || style.getBoolean("Background"));
            state.setTextShadow(!style.contains("TextShadow") || style.getBoolean("TextShadow"));
            state.setCompact(style.getBoolean("Compact"));
        }
        return new HudWidgetState(tag.getString("Id"), STRESSOMETER_TYPE, state);
    }

    private static String normalizeId(String id) {
        if (id == null || id.isEmpty()) {
            return STRESSOMETER_ID;
        }
        return id.length() > 64 ? id.substring(0, 64) : id;
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
