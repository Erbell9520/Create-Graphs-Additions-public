package com.createstats.client.ponder.seismic;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicPonderBeatsTest {
    private static void rules(String scene, List<SeismicPonderBeats.Cue> cues, int length, IntUnaryOperator stretch) {
        int keys = 0;
        for (int i = 0; i < cues.size(); i++) {
            final SeismicPonderBeats.Cue c = cues.get(i);
            assertTrue(c.tick() >= c.after() + 6, scene + " " + c.name() + " at " + c.tick() + " comes before its motion at "
                    + c.after());
            int real = 0;
            for (int t = c.tick(); t < c.end(); t++) {
                real += stretch.applyAsInt(t);
            }
            assertTrue(real >= 60, scene + " " + c.name() + " holds " + real + " client ticks; at least 60");
            if (i + 1 < cues.size()) {
                assertTrue(c.end() + 4 <= cues.get(i + 1).tick(), scene + " " + c.name() + " runs into "
                        + cues.get(i + 1).name());
            }
            if (c.key()) {
                keys++;
            }
        }
        assertTrue(keys >= 3 && keys <= 6, scene + " has " + keys + " keyframes");
        assertTrue(cues.get(cues.size() - 1).end() <= length - 10, scene + " ends on its last caption with no rest");
    }

    @Test
    void theSetupSceneKeepsTheStandard() {
        rules("setup", SeismicPonderBeats.Setup.cues(), SeismicPonderBeats.Setup.LENGTH, t -> 1);
    }

    @Test
    void theSurveySceneKeepsTheStandardInRealTime() {
        rules("survey", SeismicPonderBeats.Survey.cues(), SeismicPonderBeats.Survey.length(), SeismicPonderBeats.Survey.slow());
    }

    @Test
    void theReadingSceneKeepsTheStandard() {
        rules("reading", SeismicPonderBeats.Reading.cues(), SeismicPonderBeats.Reading.LENGTH, t -> 1);
    }

    @Test
    void theSlowMotionCoversTheDropAndTheEchoesAndIsOverForTheirCaption() {
        final IntUnaryOperator slow = SeismicPonderBeats.Survey.slow();
        final int impact = SeismicPonderBeats.Survey.impact();
        final int last = impact + SeismicPonderBeats.Survey.ground().lastEcho();
        assertEquals(1, slow.applyAsInt(SeismicPonderBeats.Survey.PULL - 1), "full speed before the pull");
        for (int t = impact; t <= last; t++) {
            assertEquals(SeismicPonderBeats.SLOW, slow.applyAsInt(t), "ten times slower from the landing to the last echo");
        }
        final SeismicPonderBeats.Cue echo = named(SeismicPonderBeats.Survey.cues(), "echo");
        assertEquals(1, slow.applyAsInt(echo.tick()), "back to full speed before the echoes are explained");
    }

    @Test
    void theRigsMomentsAreReadOffItsScript() {
        assertTrue(SeismicPonderBeats.Setup.readyCaption() >= SeismicPonderBeats.Setup.ready() + 6);
        assertTrue(SeismicPonderBeats.Survey.readingCaption() >= SeismicPonderBeats.Survey.reading() + 6,
                "the reading caption follows the reading");
        assertTrue(SeismicPonderBeats.Reading.LOOK_CAPTION >= SeismicPonderBeats.Reading.LOOK + SeismicPonderBeats.DWELL_TICKS,
                "the look caption follows the look's dwell");
        assertTrue(SeismicPonderBeats.Reading.walkEnd() < SeismicPonderBeats.Reading.LOOK, "it stops before it looks back");
    }

    private static SeismicPonderBeats.Cue named(List<SeismicPonderBeats.Cue> cues, String name) {
        for (final SeismicPonderBeats.Cue c : cues) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        throw new AssertionError("no cue " + name);
    }

    @Test
    void thePensCaptionComesWhileTheBlowIsOnThePaper() {
        final int impact = SeismicPonderBeats.Survey.impact();
        final int last = impact + SeismicPonderBeats.Survey.ground().lastEcho();
        final SeismicPonderBeats.Cue pen = named(SeismicPonderBeats.Survey.cues(), "pen");
        assertTrue(pen.tick() > last, "the pen's caption at " + pen.tick() + " comes before the last echo at " + last);
        final int off = impact + SeismicPonderBeats.Survey.RECORDER_SPAN;
        assertTrue(pen.end() <= off, "the thump runs off the strip at " + off + ", before the pen's caption ends at " + pen.end());
    }

    @Test
    void theOrbitTurnsUnderAFreeCaptionAndIsDoneBeforeAnyPointedOne() {
        final List<SeismicPonderBeats.Cue> cues = SeismicPonderBeats.Survey.cues();
        final int from = SeismicPonderBeats.Survey.orbit();
        final int to = from + SeismicPonderBeats.Survey.ORBIT_TICKS;
        SeismicPonderBeats.Cue during = null;
        for (final SeismicPonderBeats.Cue c : cues) {
            if (c.tick() <= from && from < c.end()) {
                during = c;
            }
            if (c.at() != null) {
                assertTrue(c.end() <= from || c.tick() > to, "the camera turns from " + from + " to " + to + " under the pointed "
                        + "caption " + c.name() + " (" + c.tick() + " to " + c.end() + ")");
            }
        }
        assertTrue(during != null && during.at() == null, "the camera turns at " + from + " under no free caption");
        assertTrue(from >= during.tick() + 20, "the camera turns in " + during.name() + "'s first 20 ticks");
    }

    @Test
    void theLookBringsBackTheVeinsTheWalkLeftBehind() {
        final SeismicPonderSurvey.Survey ground = SeismicPonderBeats.Reading.ground();
        final int impact = SeismicPonderBeats.Reading.impact();
        final Walker.Walk walk = SeismicPonderBeats.Reading.walk();
        final VeinView.Light light = new VeinView.Light(ground, impact);
        int dwell = 0;
        float[] before = null;
        for (int t = impact; t < SeismicPonderBeats.Reading.LENGTH; t++) {
            dwell = walk.looking(t) ? dwell + 1 : 0;
            if (dwell >= SeismicPonderBeats.DWELL_TICKS) {
                light.look(t);
            }
            final Vec3 e = walk.eye(t);
            light.step(t, e.x, e.y, e.z);
            if (t == SeismicPonderBeats.Reading.LOOK - 1) {
                before = light.shown.clone();
            }
        }
        assertTrue(before != null, "the look comes after the scene ends");
        int back = 0;
        for (int i = 0; i < before.length; i++) {
            assertTrue(light.shown[i] >= 0.95f, "vein " + i + " is not lit at the scene's end: " + Arrays.toString(light.shown));
            back += light.shown[i] - before[i] >= 0.3f ? 1 : 0;
        }
        assertTrue(back >= 2, "the look brings back " + back + " veins; before it " + Arrays.toString(before));
    }
}
