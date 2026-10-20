package com.createstats.alarm.annunciator;

import net.minecraft.nbt.CompoundTag;

public record RuleStatus(String ruleId, AnnunciatorState state, AlarmSeverity severity,
                         float value, boolean available, long sinceTick) {
    public static RuleStatus initial(String ruleId) {
        return new RuleStatus(ruleId, AnnunciatorState.NORMAL, AlarmSeverity.NORMAL, 0.0f, false, 0L);
    }

    // ringback shows as NOTICE: not red anymore, not invisible either
    public AlarmSeverity effectiveSeverity() {
        return switch (this.state) {
            case NORMAL -> AlarmSeverity.NORMAL;
            case RINGBACK -> AlarmSeverity.NOTICE;
            case ALARM, ACKNOWLEDGED -> this.severity;
        };
    }

    public boolean isOutstanding() {
        return this.state.isOutstanding();
    }

    public RuleStatus withState(AnnunciatorState newState, AlarmSeverity newSeverity,
                                float newValue, boolean nowAvailable, long gameTime) {
        final long since = newState == this.state ? this.sinceTick : gameTime;
        return new RuleStatus(this.ruleId, newState, newSeverity, newValue, nowAvailable, since);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Rule", this.ruleId);
        tag.putString("State", this.state.getSerializedName());
        tag.putString("Severity", this.severity.getSerializedName());
        tag.putFloat("Value", this.value);
        tag.putBoolean("Available", this.available);
        tag.putLong("Since", this.sinceTick);
        return tag;
    }

    public static RuleStatus load(CompoundTag tag) {
        return new RuleStatus(
                tag.getString("Rule"),
                AnnunciatorState.byName(tag.getString("State")),
                AlarmSeverity.byName(tag.getString("Severity")),
                tag.getFloat("Value"),
                tag.getBoolean("Available"),
                tag.getLong("Since"));
    }
}
