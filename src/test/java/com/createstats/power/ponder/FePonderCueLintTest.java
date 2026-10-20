package com.createstats.power.ponder;

import com.createstats.power.ponder.FePonderChapters.Cue;
import com.createstats.power.ponder.FePonderChapters.CueKind;
import com.createstats.power.ponder.FePonderChapters.Sheet;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.createstats.power.ponder.FePonderPlant.Tuning;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FePonderCueLintTest {
    private static List<Sheet> sheets() {
        final Tuning tune = Tuning.defaults();
        final Recording pdu = FePonderChapters.Pdu.replay(tune);
        final Recording auto = FePonderChapters.Auto.replay(tune);
        return List.of(FePonderChapters.Cables.sheet(),
                FePonderChapters.Pdu.sheet(pdu, FePonderChapters.Pdu.warp(pdu)),
                FePonderChapters.Auto.sheet(auto, FePonderChapters.Auto.warp(auto)),
                FePonderChapters.Spans.sheet(),
                FePonderChapters.Bank.sheet(FePonderChapters.Bank.replay(tune, FePonderChapters.Bank.Stock.defaults())),
                FePonderChapters.Controller.sheet(FePonderChapters.Controller.replay(tune,
                        FePonderChapters.Controller.Stock.defaults())),
                FePonderChapters.Deal.sheet(FePonderChapters.Deal.replay(tune,
                        FePonderChapters.Controller.Stock.defaults(), 1L, 2L, 3L)));
    }

    @Test
    void everyCaptionComesSixToTenTicksAfterItsMotion() {
        for (Sheet s : sheets()) {
            for (Cue c : s.captions()) {
                final int after = c.tick() - s.tick(c.about());
                assertTrue(after >= 6 && after <= 10, s.chapter() + " " + c.name() + " comes " + after
                        + " ticks after " + c.about());
            }
        }
    }

    @Test
    void captionsHoldAMinuteOfTicksAndNeverOverlap() {
        for (Sheet s : sheets()) {
            final List<Cue> caps = s.captions();
            for (int i = 0; i < caps.size(); i++) {
                assertTrue(caps.get(i).hold() >= 60, s.chapter() + " " + caps.get(i).name() + " holds " + caps.get(i).hold());
                if (i > 0) {
                    final Cue prev = caps.get(i - 1);
                    assertTrue(caps.get(i).tick() >= prev.tick() + prev.hold(), s.chapter() + " " + caps.get(i).name()
                            + " at " + caps.get(i).tick() + " overlaps " + prev.name() + " until " + (prev.tick() + prev.hold()));
                }
            }
        }
    }

    @Test
    void threeToSixKeyframes() {
        for (Sheet s : sheets()) {
            int keys = 0;
            for (Cue c : s.captions()) if (c.keyframe()) keys++;
            assertTrue(keys >= 3 && keys <= 6, s.chapter() + " has " + keys + " keyframes");
        }
    }

    @Test
    void fortyToSeventyFiveSecondsWithARestAtTheEnd() {
        for (Sheet s : sheets()) {
            assertTrue(s.length() >= 800 && s.length() <= 1_500, s.chapter() + " runs " + s.length() + " ticks");
            final List<Cue> caps = s.captions();
            final Cue last = caps.get(caps.size() - 1);
            assertTrue(last.tick() + last.hold() <= s.length() - 20, s.chapter() + " ends its last caption at "
                    + (last.tick() + last.hold()) + " of " + s.length() + ": no rest");
        }
    }

    @Test
    void nothingSitsStillForMoreThanEightSeconds() {
        for (Sheet s : sheets()) {
            final List<Integer> motions = new ArrayList<>();
            for (Cue c : s.cues()) if (c.kind() == CueKind.MOTION) motions.add(c.tick());
            motions.sort(Integer::compare);
            motions.add(s.length());
            for (int i = 1; i < motions.size(); i++) {
                assertTrue(motions.get(i) - motions.get(i - 1) <= 160, s.chapter() + ": still from "
                        + motions.get(i - 1) + " to " + motions.get(i));
            }
        }
    }

    @Test
    void cuesAreInSceneOrder() {
        for (Sheet s : sheets()) {
            final List<Cue> caps = s.captions();
            for (int i = 1; i < caps.size(); i++) {
                assertTrue(caps.get(i).tick() > caps.get(i - 1).tick(), s.chapter() + " registers captions out of order");
            }
        }
    }
}
