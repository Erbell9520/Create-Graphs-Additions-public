package com.createstats.train.egg;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestChoreographyTest {
    @Test
    void everyPoseIsTheSameSizeAndUsesOnlyThePalette() {
        assertEquals(ManifestChoreography.FRAMES, ManifestChoreography.poseCount(), "one bitmap a frame");
        assertEquals(ManifestChoreography.FRAMES, ManifestChoreography.MOVES.length, "one name a frame");
        for (int f = 0; f < ManifestChoreography.FRAMES; f++) {
            final String[] pose = ManifestChoreography.pose(f);
            assertEquals(ManifestChoreography.HEIGHT, pose.length, "frame " + f + " rows");
            for (int r = 0; r < pose.length; r++) {
                assertEquals(ManifestChoreography.WIDTH, pose[r].length(), "frame " + f + " row " + r + " width");
                for (char c : pose[r].toCharArray()) {
                    assertTrue(ManifestChoreography.PALETTE.indexOf(c) >= 0,
                            "frame " + f + " row " + r + " uses '" + c + "'");
                }
            }
        }
    }

    @Test
    void theLoopIsOneBarOfEightHalfBeats() {
        assertEquals(8, ManifestChoreography.FRAMES);
        assertEquals(5, ManifestChoreography.FRAME_TICKS, "a frame is half a beat");
        assertEquals(ManifestEgg.BAR_TICKS, ManifestChoreography.LOOP_TICKS, "one pass of the routine is one bar");
        for (long t = 4000; t < 4100; t++) {
            assertArrayEquals(ManifestChoreography.poseAt(t),
                    ManifestChoreography.poseAt(t + ManifestChoreography.LOOP_TICKS), "the loop repeats at tick " + t);
            if (ManifestEgg.isMove(t)) {
                assertEquals(0, ManifestChoreography.frameAt(t), "the routine starts on the train's big beat: " + t);
            }
        }
    }

    @Test
    void thePoseChangesOnHalfBeatLinesAndNowhereElse() {
        for (long t = 7001; t < 7200; t++) {
            final boolean changed = !Arrays.equals(ManifestChoreography.poseAt(t - 1), ManifestChoreography.poseAt(t));
            assertEquals(ManifestEgg.isHalfBeat(t), changed, "tick " + t);
        }
    }

    @Test
    void noTwoNeighbouringFramesAreTheSame() {
        for (int f = 0; f < ManifestChoreography.FRAMES; f++) {
            final int next = (f + 1) % ManifestChoreography.FRAMES;
            assertFalse(Arrays.equals(ManifestChoreography.pose(f), ManifestChoreography.pose(next)),
                    ManifestChoreography.MOVES[f] + " then " + ManifestChoreography.MOVES[next]
                            + ": the dance would stall");
        }
    }

    @Test
    void theShowEndsOnTheClapAndTheTaDa() {
        final long until = ManifestEgg.showUntil(12_345);
        final int lastBeat = ManifestChoreography.frameAt(until - ManifestEgg.BEAT_TICKS);
        final int lastHalf = ManifestChoreography.frameAt(until - ManifestEgg.HALF_BEAT_TICKS);
        assertEquals("clap", ManifestChoreography.MOVES[lastBeat]);
        assertEquals("ta-da", ManifestChoreography.MOVES[lastHalf]);
    }

    @Test
    void sheTurnsHerBackOnceAndHopsOnTheTaDa() {
        for (int f = 0; f < ManifestChoreography.FRAMES; f++) {
            final String[] pose = ManifestChoreography.pose(f);
            final String move = ManifestChoreography.MOVES[f];
            assertEquals(!"turn".equals(move), String.join("", pose).indexOf('e') >= 0, move + ": her face shows");
            assertEquals("ta-da".equals(move), pose[ManifestChoreography.HEIGHT - 1].chars().allMatch(c -> c == '.'),
                    move + ": her feet are off the floor only on the hop");
        }
    }

    @Test
    void aPoseHandedOutIsACopy() {
        final String[] pose = ManifestChoreography.poseAt(0);
        pose[0] = "xxxxxxxxxxxx";
        assertNotEquals("xxxxxxxxxxxx", ManifestChoreography.poseAt(0)[0], "no caller can edit the dance");
    }

    @Test
    void sixGirlsHaveSixHairColours() {
        assertEquals(ManifestEgg.CARRIAGES, ManifestChoreography.HAIR.length);
        assertEquals(6L, Arrays.stream(ManifestChoreography.HAIR).distinct().count());
    }
}
