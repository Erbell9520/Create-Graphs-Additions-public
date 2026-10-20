package com.createstats.alarm.annunciator;

import com.createstats.data.MetricType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnunciatorEngineTest {
    private static WatchRule rule(String id, RuleComparator comparator, float threshold,
                                  float deadband, AlarmSeverity severity) {
        final WatchRule watch = new WatchRule(id);
        watch.setBinding(RuleBinding.panelChannel(new BlockPos(0, 64, 0), id));
        watch.setMetricType(MetricType.FILL_LEVEL);
        watch.setComparator(comparator);
        watch.setThreshold(threshold);
        watch.setDeadband(deadband);
        watch.setSeverity(severity);
        watch.setArmed(true);
        return watch;
    }

    private static AnnunciatorEngine.Outcome run(List<WatchRule> rules,
                                                 Map<String, RuleStatus> previous,
                                                 AnnunciatorEngine.Sampler sampler,
                                                 boolean ack, boolean reset, long time) {
        return AnnunciatorEngine.evaluate(rules, previous, sampler, ack, reset, false, time);
    }

    @Test
    @DisplayName("a rule pointed at nothing can never trip")
    void unboundRulesAreInert() {
        final WatchRule unbound = new WatchRule("a");
        unbound.setArmed(true);
        final AnnunciatorEngine.Outcome outcome = run(List.of(unbound), Map.of(),
                r -> AnnunciatorEngine.Reading.of(999.0f), false, false, 100L);
        assertEquals(AnnunciatorState.NORMAL, outcome.state());
        assertEquals(AlarmSeverity.NORMAL, outcome.severity());
        assertNull(outcome.culprit());
    }

    @Test
    @DisplayName("disarming a rule drops it back to normal rather than freezing its last state")
    void disarmingClearsRatherThanFreezes() {
        final WatchRule watch = rule("a", RuleComparator.ABOVE, 90.0f, 2.0f, AlarmSeverity.CRITICAL);
        AnnunciatorEngine.Outcome outcome = run(List.of(watch), Map.of(),
                r -> AnnunciatorEngine.Reading.of(95.0f), false, false, 10L);
        assertEquals(AnnunciatorState.ALARM, outcome.state());

        watch.setArmed(false);
        outcome = run(List.of(watch), AnnunciatorEngine.index(outcome.statuses()),
                r -> AnnunciatorEngine.Reading.of(95.0f), false, false, 20L);
        assertEquals(AnnunciatorState.NORMAL, outcome.state());
        assertEquals(AlarmSeverity.NORMAL, outcome.severity());
    }

    @Test
    @DisplayName("an unreadable source holds its last opinion instead of reading as healthy")
    void missingDataIsNotGoodNews() {
        final WatchRule watch = rule("a", RuleComparator.ABOVE, 90.0f, 2.0f, AlarmSeverity.CRITICAL);
        AnnunciatorEngine.Outcome outcome = run(List.of(watch), Map.of(),
                r -> AnnunciatorEngine.Reading.of(95.0f), false, false, 10L);
        assertEquals(AnnunciatorState.ALARM, outcome.state());

        outcome = run(List.of(watch), AnnunciatorEngine.index(outcome.statuses()),
                r -> AnnunciatorEngine.Reading.MISSING, false, false, 20L);
        assertEquals(AnnunciatorState.ALARM, outcome.state(), "held, not cleared");
        assertEquals(1, outcome.faultCount(), "and reported separately as a fault");
        assertFalse(outcome.statuses().get(0).available());
    }

    @Test
    @DisplayName("the deadband survives a scan-to-scan round trip")
    void hysteresisHoldsAcrossScans() {
        final WatchRule watch = rule("a", RuleComparator.ABOVE, 90.0f, 5.0f, AlarmSeverity.WARNING);
        AnnunciatorEngine.Outcome outcome = run(List.of(watch), Map.of(),
                r -> AnnunciatorEngine.Reading.of(91.0f), false, false, 10L);
        assertEquals(AnnunciatorState.ALARM, outcome.state());

        outcome = run(List.of(watch), AnnunciatorEngine.index(outcome.statuses()),
                r -> AnnunciatorEngine.Reading.of(87.0f), false, false, 20L);
        assertEquals(AnnunciatorState.ALARM, outcome.state());

        outcome = run(List.of(watch), AnnunciatorEngine.index(outcome.statuses()),
                r -> AnnunciatorEngine.Reading.of(80.0f), false, false, 30L);
        assertEquals(AnnunciatorState.RINGBACK, outcome.state(), "genuinely recovered, unseen");
    }

    @Test
    @DisplayName("the device shows the worst of its rules")
    void severityFoldsToTheWorst() {
        final WatchRule mild = rule("mild", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.NOTICE);
        final WatchRule bad = rule("bad", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.CRITICAL);
        final AnnunciatorEngine.Outcome outcome = run(List.of(mild, bad), Map.of(),
                r -> AnnunciatorEngine.Reading.of(60.0f), false, false, 10L);
        assertEquals(AlarmSeverity.CRITICAL, outcome.severity());
        assertNotNull(outcome.culprit());
        assertEquals("bad", outcome.culprit().ruleId(), "and names the worst one");
        assertEquals(2, outcome.outstandingCount());
    }

    @Test
    @DisplayName("among equally bad rules the device names the newest")
    void tiesGoToTheFreshest() {
        final WatchRule first = rule("first", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.WARNING);
        final WatchRule second = rule("second", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.WARNING);
        AnnunciatorEngine.Outcome outcome = run(List.of(first, second), Map.of(),
                r -> r.getId().equals("first") ? AnnunciatorEngine.Reading.of(60.0f)
                        : AnnunciatorEngine.Reading.of(10.0f), false, false, 10L);
        assertEquals("first", outcome.culprit().ruleId());

        outcome = run(List.of(first, second), AnnunciatorEngine.index(outcome.statuses()),
                r -> AnnunciatorEngine.Reading.of(60.0f), false, false, 40L);
        assertEquals("second", outcome.culprit().ruleId(), "the new arrival takes the drum");
    }

    @Test
    @DisplayName("an unacknowledged alarm outranks an acknowledged one in the device fold")
    void urgencyOrderIsNotOrdinalOrder() {
        final WatchRule seen = rule("seen", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.CRITICAL);
        final WatchRule fresh = rule("fresh", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.NOTICE);

        AnnunciatorEngine.Outcome outcome = run(List.of(seen), Map.of(),
                r -> AnnunciatorEngine.Reading.of(60.0f), false, false, 10L);
        outcome = run(List.of(seen), AnnunciatorEngine.index(outcome.statuses()),
                r -> AnnunciatorEngine.Reading.of(60.0f), true, false, 20L);
        assertEquals(AnnunciatorState.ACKNOWLEDGED, outcome.state());

        final Map<String, RuleStatus> previous = AnnunciatorEngine.index(outcome.statuses());
        outcome = run(List.of(seen, fresh), previous,
                r -> AnnunciatorEngine.Reading.of(60.0f), false, false, 30L);
        assertEquals(AnnunciatorState.ALARM, outcome.state(),
                "the new unseen notice takes the device back to fast flash");
        assertEquals(AlarmSeverity.CRITICAL, outcome.severity(),
                "while the worst severity is still the acknowledged critical one");
    }

    @Test
    @DisplayName("a ringback is downgraded to a notice so the device stops painting itself red")
    void ringbackIsDowngraded() {
        final WatchRule watch = rule("a", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.CRITICAL);
        AnnunciatorEngine.Outcome outcome = run(List.of(watch), Map.of(),
                r -> AnnunciatorEngine.Reading.of(60.0f), false, false, 10L);
        outcome = run(List.of(watch), AnnunciatorEngine.index(outcome.statuses()),
                r -> AnnunciatorEngine.Reading.of(10.0f), false, false, 20L);
        assertEquals(AnnunciatorState.RINGBACK, outcome.state());
        assertEquals(AlarmSeverity.NOTICE, outcome.severity(),
                "the condition is over, so the record must not still read as critical");
    }

    @Test
    @DisplayName("mute silences the horn without blinding the device")
    void muteStopsTheNoiseOnly() {
        final WatchRule watch = rule("a", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.CRITICAL);
        final AnnunciatorEngine.Outcome outcome = AnnunciatorEngine.evaluate(List.of(watch), Map.of(),
                r -> AnnunciatorEngine.Reading.of(60.0f), false, false, true, 10L);
        assertFalse(outcome.hornSounding(), "no noise");
        assertEquals(AnnunciatorState.ALARM, outcome.state(), "but it still knows, and still shows");
        assertEquals(AlarmSeverity.CRITICAL, outcome.severity());
    }

    @Test
    @DisplayName("an empty device is idle rather than null")
    void noRulesIsASafeState() {
        assertSame(AnnunciatorEngine.Outcome.IDLE,
                run(List.of(), Map.of(), r -> AnnunciatorEngine.Reading.MISSING, false, false, 1L));
        assertEquals(0, AnnunciatorEngine.Outcome.IDLE.outstandingCount());
        assertNull(AnnunciatorEngine.Outcome.IDLE.culprit());
    }

    @Test
    @DisplayName("statuses come back in rule order, so the Sentry's tile grid does not shuffle")
    void orderIsStable() {
        final List<WatchRule> rules = List.of(
                rule("a", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.WARNING),
                rule("b", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.WARNING),
                rule("c", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.WARNING));
        final AnnunciatorEngine.Outcome outcome = run(rules, Map.of(),
                r -> AnnunciatorEngine.Reading.of(60.0f), false, false, 10L);
        assertEquals(List.of("a", "b", "c"), outcome.statuses().stream().map(RuleStatus::ruleId).toList());
    }

    @Test
    @DisplayName("time in state only resets when the state actually changes")
    void sinceTickTracksTransitionsNotScans() {
        final WatchRule watch = rule("a", RuleComparator.ABOVE, 50.0f, 1.0f, AlarmSeverity.WARNING);
        AnnunciatorEngine.Outcome outcome = run(List.of(watch), Map.of(),
                r -> AnnunciatorEngine.Reading.of(60.0f), false, false, 10L);
        assertEquals(10L, outcome.statuses().get(0).sinceTick());

        outcome = run(List.of(watch), AnnunciatorEngine.index(outcome.statuses()),
                r -> AnnunciatorEngine.Reading.of(70.0f), false, false, 99L);
        assertEquals(10L, outcome.statuses().get(0).sinceTick(),
                "still the same alarm, so the clock on it must not restart");
        assertTrue(outcome.statuses().get(0).isOutstanding());
    }
}
