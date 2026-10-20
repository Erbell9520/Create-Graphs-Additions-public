package com.createstats.chunkloader.ponder;

import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.chunkloader.look.CrownMotion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownPonderScriptTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final CrownLaw LAW = CrownPonderScript.LAW;

    private static List<CrownPonderScript.Frame> frames() {
        return CrownPonderScript.frames();
    }

    @Test
    @DisplayName("dark until a line lands; then its own chunk, held only with a full ride-through for it in store")
    void ownChunkFirst() {
        final int own = CrownPonderScript.firstHolding(1, 0);
        assertTrue(own > CrownPonderScript.LINE_A + CrownPonderScript.UNSPOOL, "held before any line landed: " + own);
        final CrownState s = CrownPonderScript.at(own).state();
        assertTrue(s.storedFe() >= LAW.rideTicks() * LAW.totalDraw(1), "taken without five seconds in store");
        for (int t = 0; t < own; t++) {
            assertEquals(0, CrownPonderScript.at(t).state().held(), "held at " + t);
        }
        assertEquals(1, CrownPonderScript.at(CrownPonderScript.PICK - 1).state().held(), "only its own chunk picked");
    }

    @Test
    @DisplayName("after the pick it takes one chunk at a time, ten ticks apart at least, each with five seconds in store")
    void oneAtATime() {
        int last = -1000;
        int held = CrownPonderScript.at(CrownPonderScript.PICK - 1).state().held();
        for (int t = CrownPonderScript.PICK; t < CrownPonderScript.CUT; t++) {
            final CrownState s = CrownPonderScript.at(t).state();
            if (s.held() != held) {
                assertEquals(held + 1, s.held(), "a jump at " + t);
                assertTrue(t - last >= CrownHold.REACQUIRE_TICKS, "two chunks " + (t - last) + " ticks apart at " + t);
                assertTrue(s.storedFe() >= LAW.rideTicks() * LAW.totalDraw(s.held()),
                        "chunk " + s.held() + " taken without five seconds of its draw in store");
                last = t;
                held = s.held();
            }
            assertTrue(s.phase() != CrownHold.Phase.RIDING, "the two lines did not carry it at " + t);
        }
        assertEquals(CrownPonderScript.PICKS, held, "never reached a full station before the cut");
        assertEquals(100_707, CrownPonderScript.at(CrownPonderScript.CUT - 1).state().drawFePerTick());
    }

    @Test
    @DisplayName("cut: it rides through for exactly five seconds, then every chunk goes in one tick")
    void rideThroughThenAllAtOnce() {
        final int dark = CrownPonderScript.firstDark(CrownPonderScript.CUT);
        assertEquals(CrownPonderScript.PICKS, CrownPonderScript.at(dark - 1).state().held(), "chunks went one by one");
        assertEquals(LAW.rideTicks(), dark - CrownPonderScript.CUT, "the ride-through was not five seconds");
        for (int t = CrownPonderScript.CUT; t < dark; t++) {
            assertEquals(CrownHold.Phase.RIDING, CrownPonderScript.at(t).state().phase(), "not riding at " + t);
            assertEquals(0, CrownPonderScript.at(t).state().intakeFePerTick(), "power arrived through an open breaker");
        }
        for (int t = dark; t < CrownPonderScript.RESTORE; t++) {
            assertEquals(0, CrownPonderScript.at(t).state().held(), "held again before the power came back, " + t);
        }
    }

    @Test
    @DisplayName("power back: the comeback takes them one at a time, ten ticks apart at least, back to 25")
    void comeback() {
        int held = 0;
        int last = -1000;
        for (int t = CrownPonderScript.RESTORE; t <= CrownPonderScript.LENGTH; t++) {
            final CrownState s = CrownPonderScript.at(t).state();
            if (s.held() != held) {
                assertEquals(held + 1, s.held(), "a jump at " + t);
                assertTrue(t - last >= CrownHold.REACQUIRE_TICKS, "two chunks too close at " + t);
                last = t;
                held = s.held();
            }
        }
        assertEquals(CrownPonderScript.PICKS, held, "the comeback did not finish inside the scene");
        assertTrue(CrownPonderScript.LENGTH - last >= CrownMotion.ULTIMATE_CLOSE + 20,
                "the scene ends before the ULTIMATE close and its rest");
    }

    @Test
    @DisplayName("the figures the captions quote are the law's: 1,024, 10%, 20 on one line, 25 on two, 100,707, 5 s")
    void figuresAreTheLaws() throws IOException {
        assertEquals(1_024, LAW.totalDraw(1));
        assertEquals(10, LAW.growthPercent());
        assertTrue(LAW.totalDraw(20) <= CrownPonderScript.LINE_RATE, "20 chunks do not fit one HV Line");
        assertTrue(LAW.totalDraw(21) > CrownPonderScript.LINE_RATE, "21 chunks fit one HV Line");
        assertTrue(LAW.totalDraw(25) <= 2 * CrownPonderScript.LINE_RATE, "25 chunks do not fit two HV Lines");
        assertEquals(100_707, LAW.totalDraw(25));
        assertEquals(5, LAW.rideSeconds());
        assertEquals(25, LAW.maxPerStation());
        final String lang = Files.readString(LANG, StandardCharsets.UTF_8);
        for (String quoted : new String[] {"costs 1,024 FE/t", "10% more", "carries 20 chunks", "to all 25",
                "drawing 100,707 FE/t", "5 seconds of its draw", "each with 5 seconds in store"}) {
            assertTrue(lang.contains(quoted), "no caption quotes \"" + quoted + "\"");
        }
    }

    @Test
    @DisplayName("captions: in order, never stacked, each held a while and after the event it names")
    void cueSheet() {
        final List<CrownPonderScript.Cue> cues = CrownPonderScript.cues();
        for (int i = 0; i < cues.size(); i++) {
            final CrownPonderScript.Cue c = cues.get(i);
            assertTrue(c.hold() >= 60, c.name() + " holds only " + c.hold());
            if (i > 0) {
                final CrownPonderScript.Cue p = cues.get(i - 1);
                assertTrue(c.tick() > p.tick() + p.hold(), c.name() + " opens over " + p.name());
            }
            assertTrue(c.tick() + c.hold() + 10 <= CrownPonderScript.LENGTH, c.name() + " runs past the scene");
        }
        assertTrue(CrownPonderScript.cue("lines").tick() > CrownPonderScript.firstHolding(1, 0),
                "the lines caption says it holds its own chunk before it does");
        assertTrue(CrownPonderScript.cue("price").tick() > CrownPonderScript.firstHolding(4, CrownPonderScript.PICK));
        assertTrue(CrownPonderScript.cue("lines_carry").tick() > CrownPonderScript.firstHolding(21, CrownPonderScript.PICK));
        assertTrue(CrownPonderScript.cue("ride").tick() > CrownPonderScript.CUT);
        assertTrue(CrownPonderScript.cue("all_go").tick() > CrownPonderScript.firstDark(CrownPonderScript.CUT));
        assertTrue(CrownPonderScript.cue("comeback").tick() > CrownPonderScript.firstHolding(3, CrownPonderScript.RESTORE));

        final int[] order = {CrownPonderScript.cue("what").tick(), CrownPonderScript.LINE_A, CrownPonderScript.LINE_B,
                CrownPonderScript.cue("lines").tick(), CrownPonderScript.MAP, CrownPonderScript.cue("map").tick(),
                CrownPonderScript.cue("price").tick(), CrownPonderScript.cue("lines_carry").tick(),
                CrownPonderScript.cue("ultimate").tick(), CrownPonderScript.CUT - 6, CrownPonderScript.cue("ride").tick(),
                CrownPonderScript.cue("all_go").tick(), CrownPonderScript.RESTORE - 6,
                CrownPonderScript.cue("comeback").tick(), CrownPonderScript.LENGTH};
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i] >= order[i - 1], "the scene's waits are out of order at step " + i);
        }
    }

    @Test
    @DisplayName("the ULTIMATE caption comes once the crown, played by its own motion, has closed")
    void ultimateCaptionAfterTheClose() {
        final CrownMotion m = new CrownMotion();
        final int at = CrownPonderScript.cue("ultimate").tick();
        for (int t = 0; t <= at; t++) {
            m.tick(CrownPonderScript.at(t).state());
        }
        assertTrue(m.ultimate(), "the caption names the ULTIMATE form before the crown shows it");
    }

    @Test
    @DisplayName("never eight seconds with nothing changing (house standard S10)")
    void noLongStill() {
        final TreeSet<Integer> events = new TreeSet<>();
        for (CrownPonderScript.Cue c : CrownPonderScript.cues()) {
            events.add(c.tick());
        }
        events.add(CrownPonderScript.LINE_A);
        events.add(CrownPonderScript.LINE_B);
        events.add(CrownPonderScript.MAP);
        events.add(CrownPonderScript.CUT);
        events.add(CrownPonderScript.RESTORE);
        int held = 0;
        for (CrownPonderScript.Frame f : frames()) {
            if (f.state().held() != held) {
                events.add(f.tick());
                held = f.state().held();
            }
        }
        events.add(CrownPonderScript.LENGTH);
        final List<Integer> list = new ArrayList<>(events);
        for (int i = 1; i < list.size(); i++) {
            assertTrue(list.get(i) - list.get(i - 1) <= 160,
                    "nothing changes from " + list.get(i - 1) + " to " + list.get(i));
        }
    }
}
