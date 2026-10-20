package com.createstats.power.ponder;

import com.createstats.power.CableTier;
import com.createstats.power.FePriority;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;
import com.createstats.power.ponder.FePonderChapters.Sheet;
import com.createstats.power.ponder.FePonderChapters.Warp;
import com.createstats.power.ponder.FePonderPlant.Flip;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.createstats.power.ponder.FePonderPlant.Tuning;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.createstats.power.ponder.FePonderChapters.FAN;
import static com.createstats.power.ponder.FePonderChapters.LAMP;
import static com.createstats.power.ponder.FePonderChapters.MILL;
import static com.createstats.power.ponder.FePonderChapters.RANGE;
import static com.createstats.power.ponder.FePonderChapters.REPELLER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FePonderReplayTest {
    private static final Tuning TUNE = Tuning.defaults();

    private static Recording pdu() {
        return FePonderChapters.Pdu.replay(TUNE);
    }

    private static Recording auto() {
        return FePonderChapters.Auto.replay(TUNE);
    }

    private static List<Flip> flipsOf(Recording r, int machine) {
        final List<Flip> out = new ArrayList<>();
        for (Flip f : r.flips) if (f.machine() == machine) out.add(f);
        return out;
    }

    @Test
    void oneDynamoIsLessThanATenthOfSurfaceCable() {
        final int rating = CableTier.SURFACE.rate(8_192);
        assertEquals(4_096, rating, "Surface Cable at the default base");
        final int[] flow = FePonderChapters.Cables.flow(TUNE, 200);
        assertEquals(360, FePonderPlant.potentialOf(256, TUNE), "a Kinetic Dynamo at 256 RPM");
        for (int t = 0; t < flow.length; t++) {
            assertEquals(360, flow[t], "an empty motor takes all the dynamo makes, tick " + t);
            assertTrue(flow[t] * 10 < rating, "less than a tenth");
        }
    }

    @Test
    void theFourGradesEachCarryTwiceTheOneBefore() {
        final CableTier[] tiers = CableTier.values();
        assertEquals(4, tiers.length);
        for (int i = 1; i < tiers.length; i++) {
            assertEquals(tiers[i - 1].rate(8_192) * 2, tiers[i].rate(8_192), tiers[i].name());
        }
    }

    @Test
    void manualServesCriticalFirstAndEachTierFromWhatIsLeft() {
        final Recording r = pdu();
        final int power = FePonderChapters.Pdu.POWER_AT;

        for (int i : new int[] {FAN, REPELLER}) {
            assertTrue(r.working[i][power + 3], r.machine(i).name() + " runs at once");
        }
        for (int i : new int[] {RANGE, MILL, LAMP}) {
            assertFalse(r.working[i][power + 3], r.machine(i).name() + " waits for what is left");
            assertEquals(PduReason.TIER_SHED, r.reason[i][power + 3]);
        }

        final int range = r.flip(RANGE, true, power);
        final int mill = r.flip(MILL, true, power);
        final int lamp = r.flip(LAMP, true, power);
        assertTrue(range > power && mill > power && lamp > power, "each comes on later");
        assertTrue(range <= mill && mill <= lamp, "in tier order: " + range + " " + mill + " " + lamp);
    }

    @Test
    void manualSpendsItsWholeBufferThenCutsTheTiersItCannotFeedTogether() {
        final Recording r = pdu();
        final int lever = FePonderChapters.Pdu.LEVER_AT;
        final int dry = FePonderChapters.Pdu.triage(r);
        assertTrue(dry > lever, "the cut comes after the clutch");

        for (int t = lever; t < dry - 1; t++) {
            for (int i = 0; i < r.machines(); i++) assertTrue(r.working[i][t], r.machine(i).name() + " at " + t);
        }
        assertTrue(r.energy[dry] <= r.intake[dry], "the buffer is spent: " + r.energy[dry]);

        for (int i : new int[] {RANGE, MILL, LAMP}) {
            final int off = r.flip(i, false, lever);
            assertTrue(Math.abs(off - dry) <= 1, r.machine(i).name() + " off at " + off + ", not with the rest");
            assertEquals(PduReason.TIER_SHED, r.reason[i][dry + 2]);
        }

        for (int t = dry; t < r.length; t++) assertTrue(r.working[FAN][t], "the fan at " + t);
        assertTrue(r.working[REPELLER][r.length - 1], "the repeller, on its own buffer");
    }

    @Test
    void theRangeIsNeverShownPartFed() {
        for (Recording r : List.of(pdu(), auto())) {
            boolean started = false;
            for (int t = 0; t < r.length; t++) {
                if (r.working[RANGE][t]) started = true;
                if (started && r.admitted[RANGE][t] && r.connected[RANGE][t]) {
                    assertTrue(r.working[RANGE][t], "the range admitted and not working at " + t);
                }
            }
        }
    }

    @Test
    void activeAutoRidesItsBufferWithEverythingRunningUntilTheMark() {
        final Recording r = auto();
        final int mark = FePonderChapters.Auto.mark(r);
        for (int t = FePonderChapters.Auto.LEVER_AT; t < mark; t++) {
            assertEquals(Integer.MAX_VALUE, r.runBudget[t], "the budget is the wire above the mark, at " + t);
            for (int i = 0; i < r.machines(); i++) assertTrue(r.admitted[i][t], r.machine(i).name() + " at " + t);
        }

        assertTrue(r.surplus[mark - 1], "above the restore mark the tick before");
        assertFalse(r.surplus[mark], "at the restore mark");
        assertTrue(Math.abs(r.energy[mark] - TUNE.restoreEnergy()) < 1_000, "at the mark: " + r.energy[mark]);
    }

    @Test
    void atTheMarkTheRangeIsSkippedAndTheSmallerMachinesAboveItKeepRunning() {
        final Recording r = auto();
        final int mark = FePonderChapters.Auto.mark(r);
        assertFalse(r.admitted[RANGE][mark]);
        assertEquals(PduReason.OVER_BUDGET, r.reason[RANGE][mark]);
        for (int i : new int[] {FAN, REPELLER, MILL, LAMP}) {
            assertTrue(r.admitted[i][mark], r.machine(i).name() + " keeps running");
        }

        assertTrue(r.machine(RANGE).tier() < r.machine(MILL).tier());
        assertTrue(r.machine(RANGE).tier() < r.machine(LAMP).tier());
        final int budget = r.runBudget[mark];
        assertTrue(budget < r.admittedDemand(mark) + r.machine(RANGE).draw(), "the range does not fit");
        assertTrue(budget >= r.admittedDemand(mark), "what runs fits");
    }

    @Test
    void asTheBufferFallsTheLeastImportantGoFirst() {
        final Recording r = auto();
        final int mark = FePonderChapters.Auto.mark(r);
        final int lamp = r.flip(LAMP, false, mark);
        final int mill = r.flip(MILL, false, mark);
        assertTrue(lamp > mark && mill > mark);
        assertTrue(lamp <= mill, "Optional before Low: " + lamp + " " + mill);
        assertTrue(r.runBudget[lamp] < r.runBudget[mark], "the line has sunk");
        for (int t = 0; t < r.length; t++) assertTrue(r.admitted[FAN][t], "Critical is never shed, at " + t);
    }

    @Test
    void pastTheMarkTheyRestartOneAtATimeMostImportantFirst() {
        final Recording r = auto();
        final int back = FePonderChapters.Auto.BACK_AT;
        final int range = r.flip(RANGE, true, back);
        final int mill = r.flip(MILL, true, back);
        final int lamp = r.flip(LAMP, true, back);
        assertTrue(range > back && range < mill && mill < lamp, range + " " + mill + " " + lamp);
        assertTrue(mill - range >= TUNE.restoreIntervalTicks() && lamp - mill >= TUNE.restoreIntervalTicks());
        for (int t : new int[] {range, mill, lamp}) {
            assertTrue(r.energy[t - 1] > TUNE.restoreEnergy() - 2_000, "restored only past the mark, at " + t);
        }

        int waiting = 0;
        for (int t = back + 10; t < range; t++) {
            if (r.reason[RANGE][t] == PduReason.AWAITING_SUPPLY) waiting++;
        }
        assertTrue(waiting > (range - back - 10) * 0.9, "No spare below the mark: " + waiting);
    }

    @Test
    void noMachineFlipsInsideItsDwell() {
        for (Recording r : List.of(pdu(), auto())) {
            if (r.mode[r.length - 1] != PduMode.AUTO) continue;
            for (int i = 0; i < r.machines(); i++) {
                final List<Flip> flips = flipsOf(r, i);
                for (int k = 1; k < flips.size(); k++) {
                    assertTrue(flips.get(k).tick() - flips.get(k - 1).tick() >= TUNE.dwellTicks(),
                            r.machine(i).name() + " flipped inside its dwell: " + flips);
                }
            }
        }
    }

    @Test
    void activeAutoNeverNeedsTheFillPassItDoesNotModel() {
        final Recording r = auto();
        for (int t = 0; t < r.length; t++) {
            for (int i = 0; i < r.machines(); i++) {
                assertTrue(r.buffer[i][t] >= r.machine(i).buffer() - r.machine(i).draw(),
                        r.machine(i).name() + " below full at " + t + ": " + r.buffer[i][t]);
            }
        }
    }

    @Test
    void everyEventTheCaptionsNameHappensAtOneToOne() {
        final Recording p = pdu();
        final Warp pw = FePonderChapters.Pdu.warp(p);
        final Recording a = auto();
        final Warp aw = FePonderChapters.Auto.warp(a);
        for (Flip f : p.flips) {
            if (f.tick() < FePonderChapters.Pdu.POWER_AT + 1) continue;
            assertEquals(1.0, pw.factor(pw.scene(f.tick())), 1.0e-9, "fe_pdu flip at replay " + f.tick());
        }
        for (Flip f : a.flips) {
            assertEquals(1.0, aw.factor(aw.scene(f.tick())), 1.0e-9, "fe_pdu_auto flip at replay " + f.tick());
        }
        assertEquals(1.0, aw.factor(aw.scene(FePonderChapters.Auto.LEVER_AT)), 1.0e-9, "the lever");
        assertEquals(1.0, aw.factor(aw.scene(FePonderChapters.Auto.BACK_AT)), 1.0e-9, "the lever back");
        assertEquals(1.0, pw.factor(pw.scene(FePonderChapters.Pdu.LEVER_AT)), 1.0e-9, "the clutch");
    }

    @Test
    void theWarpNeverRunsBackwardsAndReachesItsEnd() {
        final Recording a = auto();
        final Warp w = FePonderChapters.Auto.warp(a);
        for (int s = 1; s < w.length(); s++) assertTrue(w.at(s) >= w.at(s - 1), "backwards at " + s);
        assertTrue(w.tick(w.length() - 1) >= FePonderChapters.Auto.restored(a) + 100, "the payoff is played");
    }

    @Test
    void replaysAreQuick() {
        final long t0 = System.nanoTime();
        for (int k = 0; k < 5; k++) {
            FePonderChapters.Auto.warp(auto());
            FePonderChapters.Pdu.warp(pdu());
        }
        final double ms = (System.nanoTime() - t0) / 1.0e6 / 5;
        assertTrue(ms < 250, "a chapter's replay took " + ms + " ms; it runs on the client each time a scene opens");
    }

    @Test
    void theHeroPlantIsOneMachinePerTierCriticalFirst() {
        final List<FePonderPlant.Machine> hero = FePonderChapters.hero(true, true);
        for (int i = 0; i < hero.size(); i++) assertEquals(i, hero.get(i).tier(), hero.get(i).name());
        assertEquals(FePriority.CRITICAL.ordinal(), hero.get(FAN).tier());
        assertEquals(681, hero.stream().mapToInt(FePonderPlant.Machine::draw).sum(), "the plant's demand");
    }

    @Test
    void theCueSheetsAreBuiltFromTheReplays() {
        final Recording a = auto();
        final Sheet s = FePonderChapters.Auto.sheet(a, FePonderChapters.Auto.warp(a));
        assertTrue(s.tick("mark") < s.tick("deeper") && s.tick("deeper") < s.tick("restored"));
    }
}
