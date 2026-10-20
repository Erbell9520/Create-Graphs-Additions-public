package com.createstats.alarm.annunciator;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnnunciatorEngine {
    private AnnunciatorEngine() {
    }

    public record Reading(float value, boolean available) {
        public static final Reading MISSING = new Reading(0.0f, false);

        public static Reading of(float value) {
            return new Reading(value, true);
        }
    }

    @FunctionalInterface
    public interface Sampler {
        Reading sample(WatchRule rule);
    }

    public record Outcome(List<RuleStatus> statuses, AlarmSeverity severity, AnnunciatorState state,
                          int culpritIndex, boolean hornSounding, int faultCount) {
        public static final Outcome IDLE = new Outcome(List.of(), AlarmSeverity.NORMAL,
                AnnunciatorState.NORMAL, -1, false, 0);

        @Nullable
        public RuleStatus culprit() {
            return this.culpritIndex < 0 || this.culpritIndex >= this.statuses.size()
                    ? null : this.statuses.get(this.culpritIndex);
        }

        public int outstandingCount() {
            int count = 0;
            for (RuleStatus status : this.statuses) {
                if (status.isOutstanding()) {
                    count++;
                }
            }
            return count;
        }
    }

    // unreadable source holds its state. if "can't read" meant "fine" every latched alarm would clear when a chunk unloads
    public static Outcome evaluate(List<WatchRule> rules, Map<String, RuleStatus> previous,
                                   Sampler sampler, boolean ack, boolean reset, boolean muted,
                                   long gameTime) {
        if (rules == null || rules.isEmpty()) {
            return Outcome.IDLE;
        }
        final List<RuleStatus> statuses = new ArrayList<>(rules.size());
        AlarmSeverity worst = AlarmSeverity.NORMAL;
        AnnunciatorState deviceState = AnnunciatorState.NORMAL;
        int culpritIndex = -1;
        AlarmSeverity culpritSeverity = AlarmSeverity.NORMAL;
        long culpritSince = Long.MIN_VALUE;
        int faults = 0;

        for (int i = 0; i < rules.size(); i++) {
            final WatchRule rule = rules.get(i);
            final RuleStatus was = previousOf(previous, rule.getId());

            if (!rule.isLive()) {
                statuses.add(was.withState(AnnunciatorState.NORMAL, AlarmSeverity.NORMAL,
                        was.value(), false, gameTime));
                continue;
            }

            final Reading reading = sampler.sample(rule);
            if (!reading.available()) {
                faults++;

                statuses.add(new RuleStatus(rule.getId(), was.state(), was.severity(),
                        was.value(), false, was.sinceTick()));
                worst = AlarmSeverity.worst(worst, was.effectiveSeverity());
                deviceState = moreUrgent(deviceState, was.state());
                continue;
            }

            final boolean wasTripped = was.state().isActive();
            final boolean condition = wasTripped
                    ? rule.getComparator().holds(reading.value(), rule.getThreshold(), rule.getDeadband())
                    : rule.getComparator().trips(reading.value(), rule.getThreshold(), rule.getDeadband());

            final AnnunciatorState next = AnnunciatorState.advance(
                    was.state(), condition, rule.isLatching(), ack, reset);
            final AlarmSeverity severity = next.isActive() ? rule.getSeverity() : was.severity();
            final RuleStatus status = was.withState(next, severity, reading.value(), true, gameTime);
            statuses.add(status);

            worst = AlarmSeverity.worst(worst, status.effectiveSeverity());
            deviceState = moreUrgent(deviceState, next);

            final AlarmSeverity effective = status.effectiveSeverity();
            if (status.isOutstanding()
                    && (effective.ordinal() > culpritSeverity.ordinal()
                    || (effective == culpritSeverity && status.sinceTick() >= culpritSince))) {
                culpritSeverity = effective;
                culpritSince = status.sinceTick();
                culpritIndex = i;
            }
        }

        final boolean horn = deviceState.hornSounding() && !muted;
        return new Outcome(List.copyOf(statuses), worst, deviceState, culpritIndex, horn, faults);
    }

    static AnnunciatorState moreUrgent(AnnunciatorState a, AnnunciatorState b) {
        return urgency(a) >= urgency(b) ? a : b;
    }

    private static int urgency(AnnunciatorState state) {
        return switch (state) {
            case NORMAL -> 0;
            case RINGBACK -> 1;
            case ACKNOWLEDGED -> 2;
            case ALARM -> 3;
        };
    }

    private static RuleStatus previousOf(Map<String, RuleStatus> previous, String ruleId) {
        if (previous == null) {
            return RuleStatus.initial(ruleId);
        }
        final RuleStatus was = previous.get(ruleId);
        return was == null ? RuleStatus.initial(ruleId) : was;
    }

    public static Map<String, RuleStatus> index(List<RuleStatus> statuses) {
        final Map<String, RuleStatus> byId = new HashMap<>(statuses.size() * 2);
        for (RuleStatus status : statuses) {
            byId.put(status.ruleId(), status);
        }
        return byId;
    }
}
