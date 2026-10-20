package com.createstats.stormcoil.ponder;

import com.createstats.stormcoil.CoilLaw;
import com.createstats.stormcoil.CoilStage;
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

class CoilPonderScriptTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");

    @Test
    @DisplayName("IN charges the capacitor at IN's rate for the stage, never past its size, and only while it may")
    void inCharges() {
        for (CoilPonderScript.Frame f : CoilPonderScript.frames()) {
            final CoilStage stage = CoilStage.of(f.stage());
            assertTrue(f.in() <= CoilLaw.inRate(stage, CoilPonderScript.BASE), "IN past its rate at " + f.tick());
            assertTrue(f.capacitor() <= stage.capacitorSize(), "the capacitor over its size at " + f.tick());
            if (!f.inWired()) {
                assertEquals(0, f.in(), "IN took power with no wire at " + f.tick());
            }
            if (f.bank() >= stage.zapCost) {
                assertEquals(0, f.in(), "IN drew while the bank could pay the next arc, at " + f.tick());
            }
        }
        final CoilPonderScript.Frame before = CoilPonderScript.at(CoilPonderScript.HEIGHT - 1);
        assertEquals(CoilStage.ONE.capacitorSize(), before.capacitor(), "stage 1's capacitor never filled");
        assertEquals(CoilStage.FOUR.capacitorSize(),
                CoilPonderScript.at(CoilPonderScript.MOBS - 1).capacitor(), "stage 4's capacitor never filled");
    }

    @Test
    @DisplayName("nothing reaches OUT from IN: the load stays cold while IN is live, until a strike banks")
    void neverInToOut() {
        long in = 0;
        long out = 0;
        for (int t = 0; t < CoilPonderScript.STRIKE; t++) {
            in += CoilPonderScript.at(t).in();
            out += CoilPonderScript.at(t).out();
            assertEquals(0, CoilPonderScript.at(t).bank(), "the bank rose without a strike at " + t);
        }
        assertTrue(in > 0, "IN never charged");
        assertEquals(0, out, "OUT gave power before any lightning was banked");
        final CoilPonderScript.Frame after = CoilPonderScript.at(CoilPonderScript.STRIKE + 1);
        assertEquals(CoilPonderScript.RANGE_DRAW, after.out(), "OUT does not feed the range after the strike");
        assertEquals(0, after.in(), "IN still draws with lightning in the bank");
    }

    @Test
    @DisplayName("FE is conserved: what IN took and the strike banked is in the stores, given out, or spent on arcs")
    void conserved() {
        long in = 0;
        long out = 0;
        long struck = 0;
        long arcs = 0;
        for (CoilPonderScript.Frame f : CoilPonderScript.frames()) {
            in += f.in();
            out += f.out();
            if (f.tick() == CoilPonderScript.STRIKE) {
                struck += CoilLaw.strikeFe(CoilPonderScript.BASE);
            }
            if (f.lastZap() == f.tick()) {
                arcs += (long) f.volley() * CoilStage.of(f.stage()).zapCost;
            }
            assertEquals(in + struck, f.capacitor() + f.bank() + out + arcs, "FE appeared or vanished at " + f.tick());
        }
    }

    @Test
    @DisplayName("a volley a second at both husks, the fourth the last: four lightning hits on a husk's 20 health")
    void volleys() {
        final List<Integer> at = new ArrayList<>();
        for (CoilPonderScript.Frame f : CoilPonderScript.frames()) {
            if (f.lastZap() == f.tick()) {
                at.add(f.tick());
                assertEquals(CoilPonderScript.HUSKS, f.volley(), "a volley missed a husk at " + f.tick());
                assertEquals(4, f.stage(), "the volleys are meant at stage 4");
            }
        }
        assertEquals(CoilPonderScript.HITS_TO_KILL, at.size());
        for (int i = 1; i < at.size(); i++) {
            assertEquals(CoilLaw.ZAP_INTERVAL, at.get(i) - at.get(i - 1), "volleys not a second apart");
        }

        assertTrue(5 * CoilPonderScript.HITS_TO_KILL >= 20 && 5 * (CoilPonderScript.HITS_TO_KILL - 1) < 20);
    }

    @Test
    @DisplayName("the figures quoted: a strike banks 9,830,400 FE and the bank holds ten; the range draws its high")
    void figures() throws IOException {
        assertEquals(9_830_400, CoilLaw.strikeFe(CoilPonderScript.BASE));
        assertEquals(10, CoilLaw.STRIKES_HELD);
        assertEquals(com.createstats.blockentity.InductionRangeBE.DRAW_HIGH, CoilPonderScript.RANGE_DRAW);
        assertEquals(CoilLaw.strikeFe(CoilPonderScript.BASE),
                CoilPonderScript.at(CoilPonderScript.STRIKE).bank(), "the strike banked something else");
        final String lang = Files.readString(LANG, StandardCharsets.UTF_8);
        assertTrue(lang.contains("banks 9,830,400 FE: its storm bank holds ten"), "the bank caption's figures");
    }

    @Test
    @DisplayName("captions: in order, never stacked, after their events, and the scene's waits in order")
    void cueSheet() {
        final List<CoilPonderScript.Cue> cues = CoilPonderScript.cues();
        for (int i = 0; i < cues.size(); i++) {
            final CoilPonderScript.Cue c = cues.get(i);
            assertTrue(c.hold() >= 60, c.name() + " holds only " + c.hold());
            if (i > 0) {
                final CoilPonderScript.Cue p = cues.get(i - 1);
                assertTrue(c.tick() > p.tick() + p.hold(), c.name() + " opens over " + p.name());
            }
            assertTrue(c.tick() + c.hold() + 10 <= CoilPonderScript.LENGTH, c.name() + " runs past the scene");
        }
        assertTrue(CoilPonderScript.cue("in").tick() > CoilPonderScript.IN_WIRE + CoilPonderScript.UNSPOOL);
        assertTrue(CoilPonderScript.cue("out").tick() > CoilPonderScript.OUT_WIRE + CoilPonderScript.UNSPOOL);
        assertTrue(CoilPonderScript.cue("height").tick() > CoilPonderScript.HEIGHT + 2 * CoilPonderScript.STAGE_STEP);
        assertTrue(CoilPonderScript.cue("hostile").tick() > CoilPonderScript.FIRST_VOLLEY);
        assertTrue(CoilPonderScript.cue("storm").tick() > CoilPonderScript.STORM);
        assertTrue(CoilPonderScript.cue("bank").tick() > CoilPonderScript.STRIKE);
        final int[] order = {CoilPonderScript.cue("what").tick(), CoilPonderScript.IN_WIRE,
                CoilPonderScript.cue("in").tick(), CoilPonderScript.OUT_WIRE, CoilPonderScript.cue("out").tick(),
                CoilPonderScript.HEIGHT - 6, CoilPonderScript.cue("height").tick(), CoilPonderScript.cue("hostile").tick(),
                CoilPonderScript.cue("storm").tick(), CoilPonderScript.cue("bank").tick(),
                CoilPonderScript.cue("out_gives").tick(), CoilPonderScript.LENGTH};
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i] >= order[i - 1], "the scene's waits are out of order at step " + i);
        }
    }

    @Test
    @DisplayName("never eight seconds with nothing changing (house standard S10)")
    void noLongStill() {
        final TreeSet<Integer> events = new TreeSet<>();
        for (CoilPonderScript.Cue c : CoilPonderScript.cues()) {
            events.add(c.tick());
        }
        for (int t : new int[] {CoilPonderScript.IN_WIRE, CoilPonderScript.OUT_WIRE, CoilPonderScript.MOBS,
                CoilPonderScript.STORM, CoilPonderScript.STRIKE, CoilPonderScript.STORM_END, CoilPonderScript.LENGTH}) {
            events.add(t);
        }
        int stage = 1;
        for (CoilPonderScript.Frame f : CoilPonderScript.frames()) {
            if (f.stage() != stage || f.lastZap() == f.tick()) {
                events.add(f.tick());
                stage = f.stage();
            }
        }
        final List<Integer> list = new ArrayList<>(events);
        for (int i = 1; i < list.size(); i++) {
            assertTrue(list.get(i) - list.get(i - 1) <= 160,
                    "nothing changes from " + list.get(i - 1) + " to " + list.get(i));
        }
    }

    @Test
    @DisplayName("every volley is wound up WIND_UP ticks before it fires, and nothing is aimed when no volley follows")
    void windUpBeforeEachVolley() {
        int volleys = 0;
        for (int t = 0; t <= CoilPonderScript.LENGTH; t++) {
            final boolean fires = CoilPonderScript.at(t).lastZap() == t;
            if (fires) {
                volleys++;
                for (int w = t - CoilLaw.WIND_UP; w < t; w++) {
                    assertEquals(t - CoilLaw.WIND_UP, CoilPonderScript.aimAt(w), "the wind-up of the volley at " + t);
                }
                assertTrue(t - CoilLaw.WIND_UP >= CoilPonderScript.MOBS, "a volley aimed before its husks appeared");
            }
            final long aim = CoilPonderScript.aimAt(t);
            if (aim != Long.MIN_VALUE) {
                final int fire = (int) aim + CoilLaw.WIND_UP;
                assertEquals(fire, CoilPonderScript.at(fire).lastZap(), "an aim at " + t + " with no volley after it");
            }
        }
        assertEquals(CoilPonderScript.HITS_TO_KILL, volleys);
        assertEquals(Long.MIN_VALUE, CoilPonderScript.aimAt(CoilPonderScript.lastVolley()), "nothing after the last");
    }
}
