package com.createstats.sound;

import com.createstats.block.GarageDoorCurl;
import com.createstats.block.GarageDoorFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarageDoorSoundTest {
    private static int cueTick(float fraction, int ticks) {
        return Math.max(0, Math.min(ticks - 1, (int) (fraction * ticks)));
    }

    @Test
    @DisplayName("the arrival is reachable at every legal height, not just the tall ones")
    void arrivalIsReachableAtEveryHeight() {
        final DoorSound.Kit kit = GarageDoorSound.kit();
        for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
            final int ticks = GarageDoorCurl.moveTicks(h);
            final int at = cueTick(kit.arriveAt(), ticks);
            assertTrue(at <= ticks - 1,
                    "a " + h + " high door arrives at tick " + at + " but the client stops after "
                            + (ticks - 1) + ", so it would seat in silence");
            assertTrue(at >= ticks / 2,
                    "a " + h + " high door seats at tick " + at + " of " + ticks
                            + ", which is nowhere near the end of its travel");
        }
    }

    @Test
    @DisplayName("the loop window is inside the travel at every legal height")
    void loopWindowFitsEveryTravel() {
        final DoorSound.Kit kit = GarageDoorSound.kit();
        assertTrue(kit.loop() != null, "a roller shutter with no travel loop is a silent motor");
        for (int h = GarageDoorFrame.MIN_HEIGHT; h <= GarageDoorFrame.MAX_DROP; h++) {
            final int ticks = GarageDoorCurl.moveTicks(h);
            assertTrue(cueTick(kit.loopFrom(), ticks) < cueTick(kit.loopTo(), ticks),
                    "a " + h + " high door opens and shuts its loop on the same tick");
        }
    }

    @Test
    @DisplayName("a bigger bay sounds heavier: lower, louder, and monotonically so")
    void sizeSetsTheColour() {
        float lastPitch = Float.MAX_VALUE;
        float lastVolume = 0.0f;
        for (int w = GarageDoorFrame.MIN_WIDTH; w <= GarageDoorFrame.MAX_WIDTH; w++) {
            final int area = w * GarageDoorFrame.MAX_DROP;
            final float pitch = GarageDoorSound.pitch(area);
            final float volume = GarageDoorSound.volume(area);
            assertTrue(pitch <= lastPitch, "a " + w + " wide bay is not lower than a narrower one");
            assertTrue(volume >= lastVolume, "a " + w + " wide bay is not louder than a narrower one");
            lastPitch = pitch;
            lastVolume = volume;
        }

        final float small = GarageDoorSound.pitch(GarageDoorSound.LIGHTEST_AREA);
        final float large = GarageDoorSound.pitch(GarageDoorSound.HEAVIEST_AREA);
        assertTrue(small - large > 0.08f,
                "the smallest and largest doors are only " + (small - large) + " apart in pitch");
    }

    @Test
    @DisplayName("an area outside the two extremes is clamped rather than extrapolated")
    void outOfRangeAreasClamp() {
        assertEquals(GarageDoorSound.hardnessFor(GarageDoorSound.LIGHTEST_AREA),
                GarageDoorSound.hardnessFor(0), 1.0e-4f,
                "an unformed door with no area should sound like the smallest one, not like nothing");
        assertEquals(GarageDoorSound.hardnessFor(GarageDoorSound.HEAVIEST_AREA),
                GarageDoorSound.hardnessFor(1000), 1.0e-4f,
                "an impossible area should clamp rather than pitch the sample into the floor");
    }
}
