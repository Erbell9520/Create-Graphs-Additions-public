package com.createstats.train.smart;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailDeadlockTest {
    private static final int HELD = TrainIntel.SIGNAL_HOLD_ALERT_TICKS + 200;

    @Test
    void aRingIsOneCriticalFaultRatherThanThreeWarnings() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState a = held("Kohlezug", HELD);
        final RailSnapshot.TrainState b = held("Erzzug", HELD + 40);
        final RailSnapshot.TrainState c = held("Holzzug", HELD);
        waitOn(a, b);
        waitOn(b, c);
        waitOn(c, a);
        snap.trains().addAll(List.of(a, b, c));

        final List<TrainIntel.Stall> stalls = TrainIntel.stalls(snap);
        assertEquals(1, stalls.size(), "one ring is one stall");
        assertTrue(stalls.get(0).circular);
        assertEquals(3, stalls.get(0).trains.size());
        assertEquals(HELD + 40, stalls.get(0).longestHold,
                "the ring reports the longest hold in it, not the first one found");

        final List<TrainIntel.Alert> alerts = TrainIntel.alerts(snap);
        assertEquals(1, alerts.size(), "three held trains in a ring produce exactly one alert");
        assertEquals(TrainIntel.Severity.CRITICAL, alerts.get(0).severity,
                "a wait that cannot resolve is not a warning");
        assertTrue(alerts.get(0).detail.contains("will not clear on its own"),
                "the alert has to say the thing that distinguishes this from traffic");
    }

    @Test
    void aQueueNamesTheTrainAtTheHeadOfIt() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState wreck = held("Schotterzug", 0);
        wreck.waitingForSignal = false;
        wreck.derailed = true;
        final RailSnapshot.TrainState first = held("Personenzug", HELD);
        final RailSnapshot.TrainState second = held("Postzug", HELD);
        waitOn(second, first);
        waitOn(first, wreck);
        snap.trains().addAll(List.of(wreck, first, second));

        final List<TrainIntel.Stall> stalls = TrainIntel.stalls(snap);
        assertEquals(1, stalls.size());
        assertFalse(stalls.get(0).circular);
        assertEquals(wreck.id, stalls.get(0).cause(), "the cause is the train at the head");

        final List<TrainIntel.Alert> alerts = TrainIntel.alerts(snap);
        boolean queued = false;
        for (final TrainIntel.Alert alert : alerts) {
            if (alert.detail.contains("held behind it")) {
                queued = true;
                assertEquals("Schotterzug", alert.subject,
                        "the alert is about the wreck, not about the trains queued behind it");
                assertTrue(alert.detail.contains("derailed"), "and it says why");
            }
            assertFalse(alert.subject.equals("Postzug"),
                    "a train whose only problem is the queue ahead does not get its own fault");
        }
        assertTrue(queued, "the queue should have been reported");
        assertTrue(alerts.stream().anyMatch(a -> a.detail.contains("Derailed")),
                "the wreck keeps its own derailment alert, which is the one that says what to fix");
    }

    @Test
    void aBriefRingIsNotADeadlock() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState a = held("Kohlezug", 40);
        final RailSnapshot.TrainState b = held("Erzzug", 40);
        waitOn(a, b);
        waitOn(b, a);
        snap.trains().addAll(List.of(a, b));

        assertTrue(TrainIntel.stalls(snap).isEmpty(),
                "a hold under the threshold is not evidence of anything");
        assertTrue(TrainIntel.alerts(snap).isEmpty(), "and it raises nothing");
    }

    @Test
    void anUnexplainedHoldStillReportsItself() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState lone = held("Kieszug", HELD);
        snap.trains().add(lone);

        assertTrue(TrainIntel.stalls(snap).isEmpty(), "there is no graph to build from one train");
        final List<TrainIntel.Alert> alerts = TrainIntel.alerts(snap);
        assertEquals(1, alerts.size());
        assertTrue(alerts.get(0).detail.contains("Nothing is occupying the block ahead"),
                "an unexplained hold should not be dressed up as a diagnosis");
    }

    @Test
    void aTrainBlockedByItselfIsIgnored() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState t = held("Rangierlok", HELD);
        t.blockedByTrain = t.id;
        snap.trains().add(t);

        assertTrue(TrainIntel.stalls(snap).isEmpty());
    }

    @Test
    void aRingRecordsWhichTrainEachOneWaitsOn() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState a = held("Kohlezug", HELD);
        final RailSnapshot.TrainState b = held("Erzzug", HELD);
        final RailSnapshot.TrainState c = held("Holzzug", HELD);
        waitOn(a, b);
        waitOn(b, c);
        waitOn(c, a);
        snap.trains().addAll(List.of(a, b, c));

        final TrainIntel.Stall ring = TrainIntel.stalls(snap).get(0);
        assertEquals(b.id, ring.waitsOn.get(a.id));
        assertEquals(c.id, ring.waitsOn.get(b.id));
        assertEquals(a.id, ring.waitsOn.get(c.id));
        assertEquals(3, ring.waitsOn.size(), "every train in a ring waits on exactly one other");
    }

    @Test
    void aQueuePointsEachTrainAtItsBlockerAndTheHeadAtNobody() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState wreck = held("Schotterzug", 0);
        wreck.waitingForSignal = false;
        wreck.derailed = true;
        final RailSnapshot.TrainState first = held("Personenzug", HELD);
        final RailSnapshot.TrainState second = held("Postzug", HELD);
        waitOn(second, first);
        waitOn(first, wreck);
        snap.trains().addAll(List.of(wreck, first, second));

        final TrainIntel.Stall queue = TrainIntel.stalls(snap).get(0);
        assertEquals(wreck.id, queue.waitsOn.get(first.id));
        assertEquals(first.id, queue.waitsOn.get(second.id));
        assertFalse(queue.waitsOn.containsKey(wreck.id),
                "the train at the head of a queue waits on nothing in the stall");
    }

    @Test
    void theWorstStallIsTheDeadlockNotTheLongerQueue() {
        final RailSnapshot snap = new RailSnapshot();
        final RailSnapshot.TrainState a = held("Ringzug A", HELD);
        final RailSnapshot.TrainState b = held("Ringzug B", HELD);
        waitOn(a, b);
        waitOn(b, a);

        final RailSnapshot.TrainState wreck = held("Wrackzug", 0);
        wreck.waitingForSignal = false;
        wreck.derailed = true;
        final RailSnapshot.TrainState queued = held("Wartezug", HELD * 4);
        waitOn(queued, wreck);
        snap.trains().addAll(List.of(a, b, wreck, queued));

        final TrainIntel.Stall worst = TrainIntel.worstStall(TrainIntel.stalls(snap));
        assertNotNull(worst);
        assertTrue(worst.circular,
                "a deadlock outranks a queue even when the queue has waited four times as long");
    }

    @Test
    void worstStallOfNothingIsNull() {
        assertNull(TrainIntel.worstStall(TrainIntel.stalls(new RailSnapshot())));
    }

    private static RailSnapshot.TrainState held(String name, int ticks) {
        final RailSnapshot.TrainState t = new RailSnapshot.TrainState();
        t.id = UUID.nameUUIDFromBytes(name.getBytes());
        t.name = name;
        t.dimension = "minecraft:overworld";
        t.hasSchedule = true;
        t.phase = RailSnapshot.SchedulePhase.IN_TRANSIT;
        t.waitingForSignal = ticks > 0;
        t.ticksWaitingForSignal = ticks;
        return t;
    }

    private static void waitOn(RailSnapshot.TrainState waiter, RailSnapshot.TrainState blocker) {
        waiter.blockedByTrain = blocker.id;
    }
}
