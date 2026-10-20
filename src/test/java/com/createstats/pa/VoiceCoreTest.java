package com.createstats.pa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.data.MetricType;
import com.createstats.pa.PaSubscription.Verbosity;
import com.createstats.pa.Verbalizer.Reading;
import com.createstats.pa.Verbalizer.Trend;
import org.junit.jupiter.api.Test;

class VoiceCoreTest {
    @Test
    void speaksSmallNumbersAsSingleWords() {
        assertEquals(List.of("zero"), Lexicon.number(0));
        assertEquals(List.of("seven"), Lexicon.number(7));
        assertEquals(List.of("nineteen"), Lexicon.number(19));
    }

    @Test
    void speaksTensWithoutSayingZero() {
        assertEquals(List.of("twenty"), Lexicon.number(20));
        assertEquals(List.of("ninety", "nine"), Lexicon.number(99));
    }

    @Test
    void speaksHundredsAndThousandsByRecursion() {
        assertEquals(List.of("one", "hundred"), Lexicon.number(100));
        assertEquals(List.of("four", "hundred", "seventy", "two"), Lexicon.number(472));
        assertEquals(List.of("one", "thousand", "two", "hundred", "six"), Lexicon.number(1206));
        assertEquals(List.of("three", "million"), Lexicon.number(3_000_000));
    }

    @Test
    void speaksNegatives() {
        assertEquals(List.of("negative", "forty", "two"), Lexicon.number(-42));
    }

    @Test
    void everyNumberIsSayableFromTheBakedVocabulary() {
        final List<Long> probes = new ArrayList<>();
        for (long v = 0; v <= 2000; v++) {
            probes.add(v);
        }
        probes.addAll(List.of(9999L, 10_000L, 123_456L, 1_000_000L, 987_654_321L, -1L, -1000L));
        for (long v : probes) {
            final List<String> clips = Lexicon.number(v);
            assertFalse(clips.isEmpty(), "no clips for " + v);
            for (String clip : clips) {
                assertTrue(Lexicon.allClips().contains(clip),
                        "clip '" + clip + "' for " + v + " is not in the baked vocabulary");
            }
        }
    }

    @Test
    void quantityRoundsHardAboveTenAndKeepsOneDecimalBelow() {
        assertEquals(List.of("eighty", "seven"), Lexicon.quantity(87.3));
        assertEquals(List.of("eighty", "eight"), Lexicon.quantity(87.6));
        assertEquals(List.of("three", "point", "four"), Lexicon.quantity(3.4));
        assertEquals(List.of("zero", "point", "five"), Lexicon.quantity(0.5));
    }

    @Test
    void quantityCarriesATenthThatRoundsToTen() {
        assertEquals(List.of("four"), Lexicon.quantity(3.98));
    }

    private static Reading reading(double value, MetricType type, Trend trend, boolean available) {
        return new Reading(Lexicon.STRESS_NETWORK, new DeviceId("create_stats:stressometer", 4),
                Lexicon.STRESS, value, type, trend, available);
    }

    @Test
    void everySentenceUsesOnlyBakedClips() {
        for (Verbosity verbosity : Verbosity.values()) {
            for (AlarmSeverity severity : AlarmSeverity.values()) {
                final Utterance u = Verbalizer.say(
                        reading(94.0, MetricType.STRESS_PERCENT, Trend.RISING, true), verbosity, severity);
                for (String clip : u.clips()) {
                    assertTrue(Lexicon.allClips().contains(clip), "unbaked clip: " + clip);
                }
            }
        }
    }

    @Test
    void normalVerbosityNamesTheSubjectAndTheId() {
        final Utterance u = Verbalizer.say(
                reading(94.0, MetricType.STRESS_PERCENT, Trend.STEADY, true),
                Verbosity.NORMAL, AlarmSeverity.WARNING);
        assertEquals(List.of("warning", "stress_network", "unit", "four", "stress",
                "ninety", "four", "percent"), u.clips());
    }

    @Test
    void terseVerbosityIsJustTheValue() {
        final Utterance u = Verbalizer.say(
                reading(94.0, MetricType.STRESS_PERCENT, Trend.STEADY, true),
                Verbosity.TERSE, AlarmSeverity.NORMAL);
        assertEquals(List.of("ninety", "four", "percent"), u.clips());
    }

    @Test
    void fullVerbosityAddsTrendAndState() {
        final Utterance u = Verbalizer.say(
                reading(94.0, MetricType.STRESS_PERCENT, Trend.RISING, true),
                Verbosity.FULL, AlarmSeverity.NORMAL);
        assertTrue(u.clips().contains("rising"));
        assertTrue(u.clips().contains("high"));
    }

    @Test
    void anUnreadableTopicIsAnnouncedAsOffline() {
        final Utterance u = Verbalizer.say(
                reading(0.0, MetricType.STRESS_PERCENT, Trend.STEADY, false),
                Verbosity.NORMAL, AlarmSeverity.WARNING);
        assertTrue(u.clips().contains("offline"));
        assertFalse(u.clips().contains("percent"), "an unreadable topic must not quote a value");
    }

    @Test
    void endurenceIsConvertedOutOfTicks() {
        final Reading r = new Reading(Lexicon.BATTERY_BANK, new DeviceId("x", 2), Lexicon.ENDURANCE,
                1200.0, MetricType.ENDURANCE_TICKS, Trend.FALLING, true);
        final Utterance u = Verbalizer.say(r, Verbosity.NORMAL, AlarmSeverity.NORMAL);
        assertTrue(u.clips().containsAll(List.of("sixty", "seconds")), u.clips().toString());
    }

    @Test
    void normalSeverityOpensWithNoUrgencyWord() {
        final Utterance u = Verbalizer.say(
                reading(50.0, MetricType.STRESS_PERCENT, Trend.STEADY, true),
                Verbosity.NORMAL, AlarmSeverity.NORMAL);
        assertEquals("stress_network", u.clips().get(0));
    }

    @Test
    void aBulletinNamesTheWorstFewAndCountsTheRest() {
        final List<Reading> readings = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            readings.add(reading(90.0 - i, MetricType.STRESS_PERCENT, Trend.STEADY, true));
        }
        final Utterance u = Verbalizer.bulletin(readings, Verbosity.TERSE, 2);
        assertTrue(u.clips().contains("others"));
        assertTrue(u.clips().contains("outstanding"));

        assertTrue(u.clips().contains("five"), u.clips().toString());
    }

    @Test
    void anEmptyBulletinSaysSoRatherThanSayingNothing() {
        final Utterance u = Verbalizer.bulletin(List.of(), Verbosity.NORMAL, 3);
        assertTrue(u.clips().contains("no_faults"));
    }

    @Test
    void aBulletinStaysInsideTheSpokenBudget() {
        final List<Reading> readings = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            readings.add(reading(88.0, MetricType.STRESS_PERCENT, Trend.RISING, true));
        }
        final Utterance u = Verbalizer.bulletin(readings, Verbosity.NORMAL, 3);
        assertTrue(u.clips().size() <= 34,
                "bulletin ran to " + u.clips().size() + " clips: " + u.clips());
    }

    private static Utterance u(AlarmSeverity severity, String key) {
        return new Utterance(List.of("attention", "one"), severity, key);
    }

    @Test
    void theSameMessageTwiceInARowIsDropped() {
        final Announcer a = new Announcer();
        assertTrue(a.offer(u(AlarmSeverity.NOTICE, "k"), 0));
        assertNotNull(a.tick(0));
        assertFalse(a.offer(u(AlarmSeverity.NOTICE, "k"), 30),
                "a repeat inside the guard window must not queue");
    }

    @Test
    void theSameMessageIsAllowedAgainAfterTheGuardWindow() {
        final Announcer a = new Announcer();
        a.offer(u(AlarmSeverity.NOTICE, "k"), 0);
        a.tick(0);
        assertTrue(a.offer(u(AlarmSeverity.NOTICE, "k"), Announcer.REPEAT_GUARD_TICKS + 1));
    }

    @Test
    void aCriticalAlertBargesInOnSomethingAlreadySpeaking() {
        final Announcer a = new Announcer();
        a.offer(u(AlarmSeverity.NOTICE, "routine"), 0);
        assertNotNull(a.tick(0));
        assertTrue(a.isSpeaking(1));
        a.offer(u(AlarmSeverity.CRITICAL, "fire"), 1);
        final Utterance next = a.tick(1);
        assertNotNull(next, "a critical alert must not wait for a notice to finish");
        assertEquals("fire", next.dedupeKey());
    }

    @Test
    void aRoutineMessageWaitsForWhateverIsSpeaking() {
        final Announcer a = new Announcer();
        a.offer(u(AlarmSeverity.WARNING, "first"), 0);
        a.tick(0);
        a.offer(u(AlarmSeverity.NOTICE, "second"), 1);
        assertNull(a.tick(1), "a notice must not cut across a warning");
    }

    @Test
    void acknowledgeSilencesRoutineTrafficButNotCriticals() {
        final Announcer a = new Announcer();
        a.acknowledge(100);
        assertFalse(a.offer(u(AlarmSeverity.WARNING, "routine"), 101));
        assertTrue(a.offer(u(AlarmSeverity.CRITICAL, "fire"), 101),
                "acknowledging must never be able to hide a critical alert");
    }

    @Test
    void anItemThatWaitedTooLongIsDropped() {
        final Announcer a = new Announcer();
        a.offer(u(AlarmSeverity.NOTICE, "old"), 0);
        assertNull(a.tick(Announcer.MAX_WAIT_TICKS + 5));
        assertEquals(0, a.queued());
    }

    @Test
    void anItemAlreadyWaitingIsNotQueuedTwice() {
        final Announcer a = new Announcer();
        assertTrue(a.offer(u(AlarmSeverity.NOTICE, "k"), 0));
        assertFalse(a.offer(u(AlarmSeverity.NOTICE, "k"), 40),
                "the dedupe guard only knows what has been spoken, so the queue has to check itself");
        assertEquals(1, a.queued());
    }

    private static final class Shut implements Announcer.Gate {
        @Override
        public boolean open() {
            return false;
        }

        @Override
        public boolean affords(Utterance utterance) {
            return true;
        }
    }

    private static final class Broke implements Announcer.Gate {
        @Override
        public boolean open() {
            return true;
        }

        @Override
        public boolean affords(Utterance utterance) {
            return utterance.clips().size() <= 1;
        }
    }

    @Test
    void aShutGateHoldsTheQueueRatherThanDrainingIt() {
        final Announcer a = new Announcer();
        final Announcer.Gate shut = new Shut();
        a.offer(u(AlarmSeverity.WARNING, "first"), 0);
        a.offer(u(AlarmSeverity.NOTICE, "second"), 0);

        for (long t = 0; t < 200; t++) {
            assertNull(a.tick(t, shut), "nothing may be spoken through a shut gate");
        }
        assertEquals(2, a.queued(), "and nothing may be lost through one either");
        assertTrue(a.isHeld());
        assertFalse(a.isSpeaking(200), "a held arbiter is not a speaking one");
    }

    @Test
    void whatWasHeldIsSaidWhenTheGateOpens() {
        final Announcer a = new Announcer();
        final Announcer.Gate shut = new Shut();
        a.offer(u(AlarmSeverity.WARNING, "held"), 0);
        for (long t = 0; t < 100; t++) {
            a.tick(t, shut);
        }
        final Utterance spoken = a.tick(100, Announcer.Gate.OPEN);
        assertNotNull(spoken, "the hold must not have consumed it");
        assertEquals("held", spoken.dedupeKey());
        assertFalse(a.isHeld());
    }

    @Test
    void theStalenessClockStopsWhileTheGateIsShut() {
        final Announcer a = new Announcer();
        final Announcer.Gate shut = new Shut();
        a.offer(u(AlarmSeverity.NOTICE, "patient"), 0);
        final long held = Announcer.MAX_WAIT_TICKS * 3L;
        for (long t = 0; t < held; t++) {
            a.tick(t, shut);
        }
        assertEquals(1, a.queued(), "held is held, however long the hold lasts");
        assertNotNull(a.tick(held, Announcer.Gate.OPEN));
    }

    @Test
    void anItemStillAgesOutAfterAHold() {
        final Announcer a = new Announcer();
        final Announcer.Gate shut = new Shut();
        a.offer(u(AlarmSeverity.NOTICE, "old"), 0);
        for (long t = 0; t < 500; t++) {
            a.tick(t, shut);
        }
        assertEquals(1, a.queued());
        assertNull(a.tick(500 + Announcer.MAX_WAIT_TICKS + 5, Announcer.Gate.OPEN));
        assertEquals(0, a.queued(), "the clock resumes from where the hold left it");
    }

    @Test
    void anUnaffordableSentenceStaysQueuedAndSaysSo() {
        final Announcer a = new Announcer();
        final Announcer.Gate broke = new Broke();
        a.offer(new Utterance(List.of("attention", "one", "two"), AlarmSeverity.WARNING, "long"), 0);
        assertNull(a.tick(0, broke));
        assertEquals(1, a.queued());
        assertTrue(a.isStarved());
        assertFalse(a.isHeld(), "starved is not held: the device is willing, the buffer is not");

        final Utterance spoken = a.tick(1, Announcer.Gate.OPEN);
        assertNotNull(spoken);
        assertFalse(a.isStarved());
    }

    @Test
    void thePeekedQueueIsInSpeakingOrder() {
        final Announcer a = new Announcer();
        a.offer(u(AlarmSeverity.NOTICE, "quiet"), 0);
        a.offer(u(AlarmSeverity.CRITICAL, "fire"), 10);
        a.offer(u(AlarmSeverity.WARNING, "warm"), 20);

        final List<Announcer.Waiting> queue = a.peekQueue(30);
        assertEquals(List.of("fire", "warm", "quiet"),
                queue.stream().map(w -> w.utterance().dedupeKey()).toList());
        assertEquals(20, queue.get(0).waitedTicks(), "waiting time is measured, not guessed");
        assertEquals(30, queue.get(2).waitedTicks());
    }

    @Test
    void theSpokenWordIndexTracksTheSentence() {
        final Announcer a = new Announcer();
        a.offer(new Utterance(List.of("attention", "one", "two"), AlarmSeverity.NOTICE, "k"), 0);
        a.tick(0, Announcer.Gate.OPEN);
        assertEquals(0, a.speakingWordIndex(0));
        assertEquals(1, a.speakingWordIndex(Utterance.TICKS_PER_CLIP));
        assertEquals(2, a.speakingWordIndex(Utterance.TICKS_PER_CLIP * 2));
        assertEquals(2, a.speakingWordIndex(Utterance.TICKS_PER_CLIP * 3),
                "the index must never run off the end of the sentence");
    }

    @Test
    void aClearIsADifferentAnnouncementFromTheTripThatCausedIt() {
        final Reading r = reading(94.0, MetricType.STRESS_PERCENT, Trend.STEADY, true);
        final Utterance raised = Verbalizer.say(r, Verbosity.NORMAL, AlarmSeverity.WARNING,
                Verbalizer.Phase.RAISED);
        final Utterance cleared = Verbalizer.say(r, Verbosity.NORMAL, AlarmSeverity.WARNING,
                Verbalizer.Phase.CLEARED);
        assertFalse(raised.dedupeKey().equals(cleared.dedupeKey()),
                "sharing a key is what silenced every recovery");

        final Announcer a = new Announcer();
        assertTrue(a.offer(raised, 0));
        assertNotNull(a.tick(0));
        assertTrue(a.offer(cleared, 30), "a clear must survive its own trip's guard window");
    }

    @Test
    void aClearIsSpokenAsARecovery() {
        final Utterance cleared = Verbalizer.say(
                reading(82.0, MetricType.STRESS_PERCENT, Trend.FALLING, true),
                Verbosity.NORMAL, AlarmSeverity.WARNING, Verbalizer.Phase.CLEARED);
        assertEquals(Lexicon.ALL_CLEAR, cleared.clips().get(0));
        assertTrue(cleared.clips().contains(Lexicon.NOMINAL));
        assertEquals(AlarmSeverity.NORMAL, cleared.severity(),
                "good news read in the warning voice is a system crying wolf at its own recovery");
    }

    @Test
    void aTerseClearStillNamesWhatCleared() {
        final Utterance cleared = Verbalizer.say(
                reading(82.0, MetricType.STRESS_PERCENT, Trend.STEADY, true),
                Verbosity.TERSE, AlarmSeverity.WARNING, Verbalizer.Phase.CLEARED);
        assertTrue(cleared.clips().contains(Lexicon.STRESS_NETWORK),
                "\"all clear, eighty two percent\" leaves the listener guessing what recovered");
    }

    @Test
    void aRestoredTopicSaysSoWithoutQuotingANumber() {
        final Utterance restored = Verbalizer.say(
                reading(41.0, MetricType.STRESS_PERCENT, Trend.STEADY, true),
                Verbosity.NORMAL, AlarmSeverity.WARNING, Verbalizer.Phase.RESTORED);
        assertTrue(restored.clips().contains(Lexicon.RESTORED));
        assertFalse(restored.clips().contains(Lexicon.PERCENT));
        assertFalse(restored.clips().contains(Lexicon.WARNING));
    }

    @Test
    void everyPhaseUsesOnlyBakedClips() {
        for (Verbalizer.Phase phase : Verbalizer.Phase.values()) {
            for (Verbosity verbosity : Verbosity.values()) {
                for (boolean available : new boolean[]{true, false}) {
                    final Utterance u = Verbalizer.say(
                            reading(94.0, MetricType.STRESS_PERCENT, Trend.RISING, available),
                            verbosity, AlarmSeverity.CRITICAL, phase);
                    for (String clip : u.clips()) {
                        assertTrue(Lexicon.allClips().contains(clip),
                                "unbaked clip " + clip + " in " + phase + "/" + verbosity);
                    }
                }
            }
        }
    }

    @Test
    void availabilityIsAnEdgeAndNotALevel() {
        final PaSubscription sub = new PaSubscription(
                PaBinding.metric(net.minecraft.core.BlockPos.ZERO, "stress_pct"));
        assertTrue(sub.isAvailable(), "a topic nobody has failed to read yet is not offline");
        assertTrue(sub.updateAvailability(false), "going unreadable is an edge");
        for (int i = 0; i < 20; i++) {
            assertFalse(sub.updateAvailability(false), "staying unreadable is not");
        }
        assertTrue(sub.updateAvailability(true), "coming back is an edge too");
    }

    @Test
    void aDeviceVerdictBypassesTheComparator() {
        final PaSubscription sub = new PaSubscription(
                PaBinding.device(net.minecraft.core.BlockPos.ZERO));
        assertTrue(sub.updateDeviceTrip(true));
        assertTrue(sub.tripped(), "an alarming device must read as tripped on the OUTSTANDING list");
        assertFalse(sub.updateDeviceTrip(true));
        assertTrue(sub.updateDeviceTrip(false));
        assertFalse(sub.tripped());
    }

    @Test
    void aValueRestingOnItsLimitAnnouncesOnceAndThenStaysQuiet() {
        final PaSubscription sub = new PaSubscription(PaBinding.metric(net.minecraft.core.BlockPos.ZERO, "stress_pct"));
        sub.setThreshold(90.0f);
        sub.setDeadband(5.0f);

        assertTrue(sub.updateTrip(90.0f), "crossing the limit is an edge");
        for (int i = 0; i < 50; i++) {
            assertFalse(sub.updateTrip(90.0f + (i % 2 == 0 ? 0.01f : -0.01f)),
                    "a value wobbling on the limit must not keep producing edges");
        }
        assertTrue(sub.updateTrip(84.0f), "falling clear of the deadband is an edge");
        assertFalse(sub.tripped());
    }
}
