package com.createstats.display;

import net.minecraft.nbt.CompoundTag;

import java.util.Map;

public class CustomModuleBinding {
    public static final String PRIMARY_SOURCE = "primarySource";
    public static final String SECONDARY_SOURCE = "secondarySource";
    public static final String SUPPORT_SOURCE = "supportSource";
    public static final String COMPARISON_SOURCE = "comparisonSource";
    public static final String PRIMARY_METRIC = "primaryMetric";
    public static final String SECONDARY_METRIC = "secondaryMetric";
    public static final String STATUS_METRIC = "statusMetric";
        private static final Map<String, String> BINDING_KEY_ALIASES = Map.of(
            "primary_source", PRIMARY_SOURCE,
            "secondary_source", SECONDARY_SOURCE,
            "support_source", SUPPORT_SOURCE,
            "comparison_source", COMPARISON_SOURCE,
            "primaryMetricSlot", PRIMARY_METRIC,
            "secondaryMetricSlot", SECONDARY_METRIC,
            "statusMetricSlot", STATUS_METRIC,
            "primary_source_id", PRIMARY_SOURCE);

    private String key;
    private Mode mode;
    private String slot;
    private String sourceId;
    private String sourceTypeFilter;
    private String sourceTypeHint;
    private long sourcePosHint;
    private String metricLabel;

    public CustomModuleBinding() {
        this.key = "";
        this.mode = Mode.SEMANTIC_SLOT;
        this.slot = "";
        this.sourceId = "";
        this.sourceTypeFilter = "";
        this.sourceTypeHint = "";
        this.sourcePosHint = Long.MIN_VALUE;
        this.metricLabel = "";
    }

    public CustomModuleBinding(CustomModuleBinding other) {
        this.key = other != null ? safe(other.key) : "";
        this.mode = other != null && other.mode != null ? other.mode : Mode.SEMANTIC_SLOT;
        this.slot = other != null ? safe(other.slot) : "";
        this.sourceId = other != null ? safe(other.sourceId) : "";
        this.sourceTypeFilter = other != null ? safe(other.sourceTypeFilter) : "";
        this.sourceTypeHint = other != null ? safe(other.sourceTypeHint) : "";
        this.sourcePosHint = other != null ? other.sourcePosHint : Long.MIN_VALUE;
        this.metricLabel = other != null ? safe(other.metricLabel) : "";
    }

    public CustomModuleBinding copy() {
        return new CustomModuleBinding(this);
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = safe(key);
    }

    public Mode getMode() {
        return this.mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.SEMANTIC_SLOT;
    }

    public String getSlot() {
        return this.slot;
    }

    public void setSlot(String slot) {
        this.slot = safe(slot);
    }

    public String getSourceId() {
        return this.sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = safe(sourceId);
    }

    public String getSourceTypeFilter() {
        return this.sourceTypeFilter;
    }

    public void setSourceTypeFilter(String sourceTypeFilter) {
        this.sourceTypeFilter = safe(sourceTypeFilter);
    }

    public String getSourceTypeHint() {
        return this.sourceTypeHint;
    }

    public void setSourceTypeHint(String sourceTypeHint) {
        this.sourceTypeHint = safe(sourceTypeHint);
    }

    public long getSourcePosHint() {
        return this.sourcePosHint;
    }

    public void setSourcePosHint(long sourcePosHint) {
        this.sourcePosHint = sourcePosHint;
    }

    public String getMetricLabel() {
        return this.metricLabel;
    }

    public void setMetricLabel(String metricLabel) {
        this.metricLabel = safe(metricLabel);
    }

    public boolean matchesField(String bindingKey) {
        return !this.key.isEmpty() && this.key.equals(bindingKey);
    }

    public boolean normalize() {
        boolean changed = false;
        final String normalizedKey = resolveBindingKeyAlias(safe(this.key));
        if (!normalizedKey.equals(this.key)) {
            this.key = normalizedKey;
            changed = true;
        }
        final String normalizedSlot = safe(this.slot);
        if (!normalizedSlot.equals(this.slot)) {
            this.slot = normalizedSlot;
            changed = true;
        }
        final String normalizedSourceId = safe(this.sourceId);
        if (!normalizedSourceId.equals(this.sourceId)) {
            this.sourceId = normalizedSourceId;
            changed = true;
        }
        final String normalizedSourceTypeFilter = safe(this.sourceTypeFilter);
        if (!normalizedSourceTypeFilter.equals(this.sourceTypeFilter)) {
            this.sourceTypeFilter = normalizedSourceTypeFilter;
            changed = true;
        }
        final String normalizedSourceTypeHint = safe(this.sourceTypeHint);
        if (!normalizedSourceTypeHint.equals(this.sourceTypeHint)) {
            this.sourceTypeHint = normalizedSourceTypeHint;
            changed = true;
        }
        final String normalizedMetricLabel = safe(this.metricLabel);
        if (!normalizedMetricLabel.equals(this.metricLabel)) {
            this.metricLabel = normalizedMetricLabel;
            changed = true;
        }
        if (this.mode == null) {
            this.mode = this.sourceId.isEmpty() ? Mode.SEMANTIC_SLOT : Mode.EXPLICIT_SOURCE;
            changed = true;
        }
        if (this.mode == Mode.EXPLICIT_SOURCE && this.sourceId.isEmpty() && !this.slot.isEmpty()) {
            this.mode = Mode.SEMANTIC_SLOT;
            changed = true;
        }
        if (this.mode == Mode.SEMANTIC_SLOT && this.slot.isEmpty() && !this.sourceId.isEmpty()) {
            this.mode = Mode.EXPLICIT_SOURCE;
            changed = true;
        }
        return changed;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Key", this.key);
        tag.putString("Mode", this.mode.getSerializedName());
        if (!this.slot.isEmpty()) {
            tag.putString("Slot", this.slot);
        }
        if (!this.sourceId.isEmpty()) {
            tag.putString("SourceId", this.sourceId);
        }
        if (!this.sourceTypeFilter.isEmpty()) {
            tag.putString("SourceTypeFilter", this.sourceTypeFilter);
        }
        if (!this.sourceTypeHint.isEmpty()) {
            tag.putString("SourceTypeHint", this.sourceTypeHint);
        }
        if (this.sourcePosHint != Long.MIN_VALUE) {
            tag.putLong("SourcePosHint", this.sourcePosHint);
        }
        if (!this.metricLabel.isEmpty()) {
            tag.putString("MetricLabel", this.metricLabel);
        }
        return tag;
    }

    public static CustomModuleBinding load(CompoundTag tag) {
        final CustomModuleBinding binding = new CustomModuleBinding();
        if (tag == null) {
            return binding;
        }
        if (tag.contains("Key")) {
            binding.key = safe(tag.getString("Key"));
        }
        if (tag.contains("Mode")) {
            binding.mode = Mode.fromSerializedName(tag.getString("Mode"));
        }
        if (tag.contains("Slot")) {
            binding.slot = safe(tag.getString("Slot"));
        }
        if (tag.contains("SourceId")) {
            binding.sourceId = safe(tag.getString("SourceId"));
        }
        if (tag.contains("SourceTypeFilter")) {
            binding.sourceTypeFilter = safe(tag.getString("SourceTypeFilter"));
        }
        if (tag.contains("SourceTypeHint")) {
            binding.sourceTypeHint = safe(tag.getString("SourceTypeHint"));
        }
        if (tag.contains("SourcePosHint")) {
            binding.sourcePosHint = tag.getLong("SourcePosHint");
        }
        if (tag.contains("MetricLabel")) {
            binding.metricLabel = safe(tag.getString("MetricLabel"));
        }
        binding.normalize();
        return binding;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    public static String resolveBindingKeyAlias(String bindingKey) {
        final String safeKey = safe(bindingKey);
        return BINDING_KEY_ALIASES.getOrDefault(safeKey, safeKey);
    }

    public enum Mode {
        SEMANTIC_SLOT("semantic-slot"),
        EXPLICIT_SOURCE("explicit-source");

        private final String serializedName;

        Mode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public static Mode fromSerializedName(String serializedName) {
            for (Mode value : values()) {
                if (value.serializedName.equals(serializedName)) {
                    return value;
                }
            }
            return SEMANTIC_SLOT;
        }
    }
}
