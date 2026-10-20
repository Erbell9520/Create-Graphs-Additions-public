package com.createstats.client.ponder.powertrack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerTrackPonderBeatsTest {
    private static void rules(String scene, List<PowerTrackPonderBeats.Cue> cues, int length, IntUnaryOperator stretch) {
        int keys = 0;
        for (int i = 0; i < cues.size(); i++) {
            final PowerTrackPonderBeats.Cue c = cues.get(i);
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
        assertTrue(length >= 800 && length <= 1500, scene + " runs " + length / 20.0 + " s; the standard says 40 to 75");
    }

    @Test
    void theLayingSceneKeepsTheStandard() {
        rules("laying", PowerTrackPonderBeats.Laying.cues(), PowerTrackPonderBeats.Laying.LENGTH, t -> 1);
    }

    @Test
    void theCollectorSceneKeepsTheStandardInRealTime() {
        rules("collector", PowerTrackPonderBeats.Collector.cues(), PowerTrackPonderBeats.Collector.LENGTH,
                PowerTrackPonderBeats.Collector.slow());
    }

    @Test
    void theFeederSceneKeepsTheStandard() {
        rules("feeder", PowerTrackPonderBeats.Feeder.cues(), PowerTrackPonderBeats.Feeder.LENGTH, t -> 1);
    }

    @Test
    void theGapsSceneKeepsTheStandard() {
        rules("gaps", PowerTrackPonderBeats.Gaps.cues(), PowerTrackPonderBeats.Gaps.LENGTH, t -> 1);
    }

    @Test
    void theSlowMotionCoversTheGapCrossing() {
        final IntUnaryOperator slow = PowerTrackPonderBeats.Collector.slow();
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Collector.frames();
        int firstOff = -1;
        int lastOn = -1;
        for (RailStory.Frame f : frames) {
            if (f.tick() < PowerTrackPonderBeats.Collector.GLIDE_2) {
                continue;
            }
            for (RailStory.Shoe s : f.shoes()) {
                if (!s.touching() && firstOff < 0) {
                    firstOff = f.tick();
                }
            }
            if (f.carSection() >= 0 && firstOff >= 0 && f.shoes()[0].touching() && f.shoes()[1].touching()) {
                lastOn = f.tick();
                break;
            }
        }
        assertTrue(firstOff > 0 && lastOn > firstOff, "the shoes leave the rail and both land again: " + firstOff + " to " + lastOn);
        for (int t = firstOff; t <= lastOn; t++) {
            assertTrue(slow.applyAsInt(t) >= 2, "slow motion at " + t + " while a shoe is off the rail (" + firstOff + " to " + lastOn + ")");
        }
        assertEquals(1, slow.applyAsInt(PowerTrackPonderBeats.Collector.GIVE), "full speed again when the car gives");
        assertEquals(1, slow.applyAsInt(PowerTrackPonderBeats.Collector.GLIDE_2), "full speed as the glide starts");
    }

    @Test
    void theCarGlidesEaseAndNeverOverlap() {
        for (CarRun run : List.of(PowerTrackPonderBeats.Collector.run(), PowerTrackPonderBeats.Feeder.run())) {
            int lastEnd = -1;
            for (CarRun.Glide g : run.glides()) {
                assertTrue(g.start() >= lastEnd, "a glide starts before the last ends");
                lastEnd = g.end();

                final double first = Math.abs(run.at(g.start() + 1) - run.at(g.start()));
                final double mid = Math.abs(run.at(g.start() + g.ramp() + 1) - run.at(g.start() + g.ramp()));
                final double last = Math.abs(run.at(g.end()) - run.at(g.end() - 1));
                assertTrue(first < 0.02, "a linear start: " + first + " blocks on the first tick");
                assertTrue(last < 0.02, "a hard stop: " + last + " blocks on the last tick");
                assertEquals(CarRun.CRUISE, mid, 0.01, "cruise at walking pace");
                assertEquals(g.distance(), run.at(g.end()) - run.at(g.start()), 1e-9, "the glide covers its distance");
            }
        }
    }

    @Test
    void theFeederScenesMomentsAreReadOffItsStory() {
        assertTrue(PowerTrackPonderBeats.Feeder.floorHeld() > PowerTrackPonderBeats.Feeder.SUPPLY_CUE,
                "A's floor holds after Supply is shown");
        assertTrue(PowerTrackPonderBeats.Feeder.floorHeld() + 6 <= PowerTrackPonderBeats.Feeder.FLOOR_CAPTION,
                "the floor caption follows the hold");
        assertTrue(PowerTrackPonderBeats.Feeder.autoCharging() > PowerTrackPonderBeats.Feeder.AUTO_B,
                "the car charges again only once both feeders are on Auto");
        assertTrue(PowerTrackPonderBeats.Collector.floorReached() + 6 <= PowerTrackPonderBeats.Collector.floorCaption(),
                "the collector scene's floor caption follows the giving's end");
    }
}
