package com.createstats.train.egg;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestEggTest {
    @Test
    void sixCarriagesNamedManifestAreTheManifestHoweverTheNameIsTyped() {
        for (String name : new String[] {"Manifest", "  manifest ", "MANIFEST", "MANİFEST", "manıfest"}) {
            assertTrue(ManifestEgg.isManifest(name, 6), "[" + name + "] with six carriages");
            assertTrue(ManifestEgg.matches(name, 6, 0.4), "[" + name + "] pulling away");
            assertTrue(ManifestEgg.matches(name, 6, -0.4), "[" + name + "] backing out");
        }
    }

    @Test
    void anyOtherTrainIsNot() {
        assertFalse(ManifestEgg.isManifest("Manifest", 5), "five carriages are not the group");
        assertFalse(ManifestEgg.isManifest("Manifest", 7), "and neither are seven");
        assertFalse(ManifestEgg.isManifest("Manifest 2", 6), "another name");
        assertFalse(ManifestEgg.isManifest("Manifesto", 6), "a longer one");
        assertFalse(ManifestEgg.isManifest("", 6), "no name");
        assertFalse(ManifestEgg.isManifest(null, 6), "a train whose name is null");
        assertFalse(ManifestEgg.matches("Manifest", 6, 0.0), "standing still is not one motion");
        assertFalse(ManifestEgg.matches("Manifest", 6, ManifestEgg.MIN_SPEED), "nor is floating-point dust");
        assertFalse(ManifestEgg.matches("Manifest", 5, 0.4), "five moving carriages are still not the group");
    }

    @Test
    void theBeatIsTwiceASecondAndEveryFourthIsABarLine() {
        assertEquals(10, ManifestEgg.BEAT_TICKS, "120 beats a minute");
        int beats = 0;
        int bars = 0;
        int halves = 0;
        for (long t = 1000; t < 1080; t++) {
            if (ManifestEgg.isBeat(t)) {
                beats++;
                assertTrue(ManifestEgg.isHalfBeat(t), "a beat is a half-beat line too: " + t);
            }
            if (ManifestEgg.isMove(t)) {
                bars++;
                assertTrue(ManifestEgg.isBeat(t), "a bar line is a beat: " + t);
            }
            if (ManifestEgg.isHalfBeat(t)) {
                halves++;
            }
        }
        assertEquals(8, beats, "eight beats in four seconds");
        assertEquals(2, bars, "every fourth beat is the big one");
        assertEquals(16, halves, "a frame of the dance every half beat");
    }

    @Test
    void aShowRunsAtLeastEightSecondsAndEndsOnABarLine() {
        for (long arrival = 999; arrival < 1100; arrival++) {
            final long until = ManifestEgg.showUntil(arrival);
            assertTrue(until - arrival >= ManifestEgg.SHOW_TICKS, "the whole show from tick " + arrival);
            assertTrue(until - arrival < ManifestEgg.SHOW_TICKS + ManifestEgg.BAR_TICKS,
                    "never a bar more: " + arrival);
            assertTrue(ManifestEgg.isMove(until), "it ends on a bar line, so the routine finishes: " + until);
            assertTrue(ManifestEgg.isShowing(arrival, until), "showing from the arrival");
            assertTrue(ManifestEgg.isShowing(until - 1, until), "showing to its last tick");
            assertFalse(ManifestEgg.isShowing(until, until), "over on the tick it ends");
        }
        assertFalse(ManifestEgg.isShowing(0L, 1_000_000L), "an end further off than any show could be is no show");
    }

    @Test
    void theBoardShoutsThroughTheLastBeatOnly() {
        final long until = ManifestEgg.showUntil(2003);
        assertTrue(ManifestEgg.isFinalBeat(until - ManifestEgg.BEAT_TICKS, until), "the last beat's first tick");
        assertTrue(ManifestEgg.isFinalBeat(until - 1, until), "its last tick");
        assertFalse(ManifestEgg.isFinalBeat(until - ManifestEgg.BEAT_TICKS - 1, until), "the beat before");
        assertFalse(ManifestEgg.isFinalBeat(until, until), "after the show");
    }

    @Test
    void sixGirlsWearSixColours() {
        assertEquals(ManifestEgg.CARRIAGES, ManifestEgg.COLOURS.length);
        assertEquals(6L, Arrays.stream(ManifestEgg.COLOURS).distinct().count(), "six distinct colours");
        for (int colour : ManifestEgg.COLOURS) {
            assertEquals(colour & 0xFFFFFF, colour, "RGB with no alpha: " + Integer.toHexString(colour));
        }

        assertArrayEquals(new int[] {0x3CE05A, 0xB45CFF, 0xFF6FB5, 0x4FA8FF, 0xFF5A5A, 0xFFE04F}, ManifestEgg.COLOURS);
    }

    @Test
    void theRollCallClimbsSixNotesANoteBlockCanPlay() {
        assertEquals(ManifestEgg.CARRIAGES, ManifestEgg.ROLL_CALL_NOTES.length);
        assertEquals(ManifestEgg.CARRIAGES, ManifestEgg.ROLL_CALL_PITCHES.length);
        for (int i = 0; i < ManifestEgg.CARRIAGES; i++) {
            final int note = ManifestEgg.ROLL_CALL_NOTES[i];
            assertTrue(note >= 0 && note <= 24, "a note block's clicks run 0 to 24: " + note);
            final float pitch = ManifestEgg.ROLL_CALL_PITCHES[i];
            assertEquals(ManifestEgg.notePitch(note), pitch, 0.0f, "the pitch is the note's");
            assertTrue(pitch >= 0.5f && pitch <= 2.0f, "a pitch a note block can play: " + pitch);
            if (i > 0) {
                assertTrue(pitch > ManifestEgg.ROLL_CALL_PITCHES[i - 1],
                        "six distinct notes, each above the last: " + i);
            }
        }

        assertEquals(0.5f, ManifestEgg.notePitch(0), 1e-6f);
        assertEquals(1.0f, ManifestEgg.notePitch(12), 1e-6f);
        assertEquals(2.0f, ManifestEgg.notePitch(24), 1e-6f);
        assertTrue(ManifestEgg.ROLL_CALL_SPACING * (ManifestEgg.CARRIAGES - 1) < 20, "the six ring inside a second");
    }

    @Test
    void eachGirlsNotesComeOutInHerOwnHue() {
        final Set<Double> hues = new HashSet<>();
        for (int colour : ManifestEgg.COLOURS) {
            final double v = ManifestEgg.noteHue(colour);
            assertTrue(v >= 0.0 && v <= 1.0, "a note particle's colour argument: " + v);
            assertEquals(0.0, v * 24.0 - Math.rint(v * 24.0), 1e-9, "one a note block sends, note / 24");
            hues.add(v);
        }
        assertEquals(6, hues.size(), "six girls, six note colours");

        final double green = ManifestEgg.noteHue(0x3CE05A);
        assertTrue(ManifestEgg.noteChannel(green, 1.0 / 3.0) > ManifestEgg.noteChannel(green, 0.0));
        assertTrue(ManifestEgg.noteChannel(green, 1.0 / 3.0) > ManifestEgg.noteChannel(green, 2.0 / 3.0));
    }
}
