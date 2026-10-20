package com.createstats.alarm.annunciator;

import com.createstats.data.MetricType;
import com.createstats.display.MetricContract;
import net.minecraft.nbt.CompoundTag;

public class WatchRule {
    public static final int MAX_RULES = 8;

    private final String id;
    private String label;
    private RuleBinding binding;
    private MetricType metricType;
    private RuleMeasure measure;
    private RuleComparator comparator;
    private float threshold;
    private float deadband;
    private AlarmSeverity severity;
    private boolean latching;
    private boolean armed;

    public WatchRule(String id) {
        this.id = id;
        this.label = "";
        this.binding = RuleBinding.UNBOUND;
        this.metricType = MetricType.GENERIC;
        this.measure = RuleMeasure.LEVEL;
        this.comparator = RuleComparator.ABOVE;
        this.threshold = 0.0f;
        this.deadband = 0.0f;
        this.severity = AlarmSeverity.WARNING;
        this.latching = true;
        this.armed = false;
    }

    public String getId() {
        return this.id;
    }

    public String getLabel() {
        return (this.label == null || this.label.isEmpty()) ? this.binding.shortLabel() : this.label;
    }

    public String getRawLabel() {
        return this.label == null ? "" : this.label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public RuleBinding getBinding() {
        return this.binding;
    }

    public void setBinding(RuleBinding binding) {
        this.binding = binding == null ? RuleBinding.UNBOUND : binding;
    }

    public MetricType getMetricType() {
        return this.metricType;
    }

    public void setMetricType(MetricType metricType) {
        this.metricType = metricType == null ? MetricType.GENERIC : metricType;
    }

    public RuleMeasure getMeasure() {
        return this.measure;
    }

    public void setMeasure(RuleMeasure measure) {
        this.measure = measure == null ? RuleMeasure.LEVEL : measure;
    }

    public RuleComparator getComparator() {
        return this.comparator;
    }

    public void setComparator(RuleComparator comparator) {
        this.comparator = comparator == null ? RuleComparator.ABOVE : comparator;
    }

    public float getThreshold() {
        return this.threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = Float.isFinite(threshold) ? threshold : 0.0f;
    }

    public float getDeadband() {
        return this.deadband;
    }

    public void setDeadband(float deadband) {
        this.deadband = Float.isFinite(deadband) ? Math.max(0.0f, deadband) : 0.0f;
    }

    public AlarmSeverity getSeverity() {
        return this.severity;
    }

    public void setSeverity(AlarmSeverity severity) {
        this.severity = (severity == null || severity == AlarmSeverity.NORMAL)
                ? AlarmSeverity.WARNING : severity;
    }

    public boolean isLatching() {
        return this.latching;
    }

    public void setLatching(boolean latching) {
        this.latching = latching;
    }

    public boolean isArmed() {
        return this.armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
    }

    public boolean isLive() {
        return this.armed && this.binding.isBound();
    }

    public void applyDefaultsFor(MetricType type, float recentPeak) {
        this.metricType = type == null ? MetricType.GENERIC : type;
        final float[] alarm = MetricContract.defaultAlarm(this.metricType, null);
        final float[] range = MetricContract.fixedRange(this.metricType);
        if (range != null) {
            this.threshold = alarm[0];
            this.deadband = Math.abs(range[1] - range[0]) * 0.02f;
        } else {
            this.threshold = recentPeak > 0.001f ? recentPeak * 1.05f : alarm[0];
            this.deadband = Math.abs(this.threshold) * 0.02f;
        }
        this.comparator = alarm[1] > 0.5f ? RuleComparator.ABOVE : RuleComparator.BELOW;
    }

    public WatchRule copy() {
        return this.copyWithId(this.id);
    }

    public WatchRule copyWithId(String newId) {
        final WatchRule copy = new WatchRule(newId == null || newId.isEmpty() ? this.id : newId);
        copy.label = this.label;
        copy.binding = this.binding;
        copy.metricType = this.metricType;
        copy.measure = this.measure;
        copy.comparator = this.comparator;
        copy.threshold = this.threshold;
        copy.deadband = this.deadband;
        copy.severity = this.severity;
        copy.latching = this.latching;
        copy.armed = this.armed;
        return copy;
    }

    public String describe() {
        return this.getLabel() + " " + this.comparator.symbol() + " " + this.formatValue(this.threshold);
    }

    public String formatValue(float value) {
        final MetricType type = this.metricType == null ? MetricType.GENERIC : this.metricType;
        return type.formatValue(value) + this.measure.unitSuffix();
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("Label", this.getRawLabel());
        tag.put("Binding", this.binding.save());
        tag.putString("Metric", this.metricType.name());
        tag.putString("Measure", this.measure.getSerializedName());
        tag.putString("Comparator", this.comparator.getSerializedName());
        tag.putFloat("Threshold", this.threshold);
        tag.putFloat("Deadband", this.deadband);
        tag.putString("Severity", this.severity.getSerializedName());
        tag.putBoolean("Latching", this.latching);
        tag.putBoolean("Armed", this.armed);
        return tag;
    }

    public static WatchRule load(CompoundTag tag) {
        final WatchRule rule = new WatchRule(tag.getString("Id"));
        rule.label = tag.getString("Label");
        if (tag.contains("Binding")) {
            rule.binding = RuleBinding.load(tag.getCompound("Binding"));
        }
        rule.metricType = parseMetric(tag.getString("Metric"));
        rule.measure = RuleMeasure.byName(tag.getString("Measure"));
        rule.comparator = RuleComparator.byName(tag.getString("Comparator"));
        rule.threshold = tag.getFloat("Threshold");
        rule.deadband = Math.max(0.0f, tag.getFloat("Deadband"));
        rule.severity = AlarmSeverity.byName(tag.getString("Severity"));
        rule.latching = !tag.contains("Latching") || tag.getBoolean("Latching");
        rule.armed = tag.getBoolean("Armed");
        return rule;
    }

    private static MetricType parseMetric(String name) {
        try {
            return MetricType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return MetricType.GENERIC;
        }
    }
}
