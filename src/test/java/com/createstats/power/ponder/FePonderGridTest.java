package com.createstats.power.ponder;

import com.createstats.power.ConnectorTier;
import com.createstats.power.FeRates;
import com.createstats.power.WireTier;
import com.createstats.power.ponder.FePonderChapters.Bank;
import com.createstats.power.ponder.FePonderChapters.Sheet;
import com.createstats.power.ponder.FePonderChapters.Spans;
import com.createstats.power.ponder.FePonderPlant.Tuning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.createstats.power.ponder.FePonderChapters.FULL_RPM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FePonderGridTest {
    private static final Tuning TUNE = Tuning.defaults();
    private static final int BASE = 8_192;

    private static int firstWorking(FePonderGrid.Recording r, int part, int from) {
        for (int t = from; t < r.length; t++) if (r.working[part][t]) return t;
        return -1;
    }

    private static int lastWorking(FePonderGrid.Recording r, int part) {
        for (int t = r.length - 1; t >= 0; t--) if (r.working[part][t]) return t;
        return -1;
    }

    @Test
    void aConnectorFeedsItsBlockItsSpansAddedUpToItsOwnGrade() {
        final List<WireTier> one = List.of(WireTier.COPPER);
        final List<WireTier> two = List.of(WireTier.COPPER, WireTier.COPPER);
        final List<WireTier> three = List.of(WireTier.COPPER, WireTier.COPPER, WireTier.COPPER);
        assertEquals(8_192, FeRates.connectorThroughput(BASE, ConnectorTier.POWER, one));
        assertEquals(16_384, FeRates.connectorThroughput(BASE, ConnectorTier.POWER, two));
        assertEquals(16_384, FeRates.connectorThroughput(BASE, ConnectorTier.POWER, three));
        final Sheet sheet = Spans.sheet();
        assertEquals(1, Spans.spansAt(sheet.tick("goggles_caption")), "the goggles caption reads one span");
        assertEquals(2, Spans.spansAt(sheet.tick("second_caption")), "the second caption comes once it has landed");
        assertEquals(3, Spans.spansAt(sheet.tick("third_caption")), "and the third once the third has");
    }

    @Test
    void eachGradeDoublesTheLastAndEachConnectorMatchesOneGradeOfWire() {
        assertEquals(8_192, BASE * WireTier.COPPER.multiplier());
        for (int i = 1; i < WireTier.values().length; i++) {
            assertEquals(2 * WireTier.values()[i - 1].multiplier(), WireTier.values()[i].multiplier(), "wire " + i);
        }
        assertEquals(WireTier.INSULATED.multiplier(), ConnectorTier.POWER.multiplier());
        assertEquals(WireTier.SHIELDED.multiplier(), ConnectorTier.ADVANCED.multiplier());
        assertEquals(WireTier.HV.multiplier(), ConnectorTier.INDUSTRIAL.multiplier());
        assertEquals(6, ConnectorTier.INDUSTRIAL.maxLinks(), "the Industrial Connector's tag says six wires");
    }

    @Test
    void theRangeRunsOnItsSpanFromThePowerUntilItIsStripped() {
        final FePonderGrid.Recording r = Spans.replay(TUNE);
        for (int t = 0; t < Spans.POWER; t++) assertFalse(r.working[Spans.RANGE][t], "nothing turns before the power");
        final int first = firstWorking(r, Spans.RANGE, Spans.POWER);
        assertTrue(first >= Spans.POWER && first <= Spans.POWER + 3, "the range heats as the dynamo turns: " + first);
        assertTrue(Spans.sheet().tick("power_caption") > first, "the power caption comes after the range heats");
        for (int t = first; t < Spans.STRIP + 10; t++) {
            assertTrue(r.working[Spans.RANGE][t], "one dynamo's 360 FE/t keeps a 256 FE/t range running: " + t);
        }

        final int last = lastWorking(r, Spans.RANGE);
        assertTrue(last >= Spans.STRIP + 10 && last <= Spans.STRIP + 10 + 2_000 / 256 + 1, "stops after the strip: " + last);
        assertTrue(Spans.sheet().length() - 20 > last, "and before the chapter's rest");
    }

    private static final Bank.Stock STOCK = Bank.Stock.defaults();
    private static Bank.Run bankRun;

    private static Bank.Run bank() {
        if (bankRun == null) bankRun = Bank.replay(TUNE, STOCK);
        return bankRun;
    }

    @Test
    void aMergedBankHoldsMoreThanItsBlocks() {
        assertEquals(1_024_000, STOCK.capacity(1));
        assertEquals(10_085_535, STOCK.capacity(Bank.BLOCKS), "1,024,000 x 8^1.1, FeBankMerge's own rounding");
        assertTrue(STOCK.capacity(Bank.BLOCKS) > Bank.BLOCKS * STOCK.capacity(1));
        assertEquals(STOCK.capacity(Bank.BLOCKS), bank().rec().parts.get(Bank.BANK).buffer());
    }

    @Test
    void theRangeIsServedFirstAndOnlyTheSurplusChargesTheBank() {
        final FePonderGrid.Recording r = bank().rec();
        final int made = (int) Math.floor(FULL_RPM * TUNE.fePerRpm());
        assertEquals(360, made);

        final int steady = r.firstFlow(Bank.BANK, true, Bank.POWER);
        assertTrue(steady > Bank.POWER + 10 && steady < Bank.POWER + 30, "the range's buffer first: " + steady);
        for (int t = Bank.POWER + 1; t < steady; t++) assertTrue(r.flow[Bank.RANGE][t] > 256, "refilling: " + t);
        for (int t = steady + 1; t < bank().stopped(); t++) {
            assertTrue(r.working[Bank.RANGE][t], "the range runs while the dynamo turns: " + t);
            assertEquals(made - 256, r.flow[Bank.BANK][t], "the bank takes only the surplus: " + t);
        }
        final float atStop = r.fill(Bank.BANK, bank().stopped());
        assertTrue(atStop > 0.55f && atStop < 0.65f, "the charge's lapse ends near 60%: " + atStop);
        final Sheet sheet = Bank.sheet(bank());
        assertTrue(bank().warp().tick(sheet.tick("power_caption")) >= Bank.POWER + 5, "the caption follows the flow");
    }

    @Test
    void stoppedTheBankCoversExactlyTheRangesDrawUntilItRunsDry() {
        final FePonderGrid.Recording r = bank().rec();
        final int stopped = bank().stopped();
        final int empty = bank().empty();
        assertTrue(empty > stopped, "the bank runs dry after the stop");

        int covered = 0;
        for (int t = stopped + 3; t < empty; t++) {
            assertEquals(-256, r.flow[Bank.BANK][t], "the bank covers exactly the range: " + t);
            assertTrue(r.working[Bank.RANGE][t]);
            covered++;
        }
        assertTrue(covered > 20_000, "the drain is a lapse, not a moment: " + covered);
        final int stops = firstStop(r, Bank.RANGE, empty);
        assertTrue(stops > empty && stops <= empty + 2_000 / 256 + 2, "dry, the range stops on its own buffer: " + stops);
        assertTrue(bank().warp().scene(stops) < Bank.NEVER, "and it is seen stopped before the spare is cabled in");
    }

    @Test
    void theFullBankRunsTheRangeAndNeverChargesTheEmptyOne() {
        final FePonderGrid.Recording r = bank().rec();
        final int in = bank().warp().tick(Bank.NEVER);
        final int out = bank().warp().tick(Bank.BREAK);

        for (int t = in; t < out; t++) assertEquals(0, r.energy[Bank.BANK][t], "the empty bank stays empty: " + t);
        for (int t = in + 10; t < out; t++) {
            assertEquals(0, r.energy[Bank.BANK][t], "the empty bank stays empty: " + t);
            assertEquals(0, r.flow[Bank.BANK][t]);
            assertEquals(-256, r.flow[Bank.SPARE][t], "the full one covers the range: " + t);
            assertTrue(r.working[Bank.RANGE][t]);
        }
        final int stops = firstStop(r, Bank.RANGE, out);
        assertTrue(stops <= out + 2_000 / 256 + 2, "broken, it takes its charge with it and the range stops: " + stops);
        assertTrue(bank().warp().scene(stops) < Bank.sheet(bank()).length() - 20, "before the rest");
    }

    @Test
    void theBankReplayIsQuick() {
        final long t0 = System.nanoTime();
        Bank.replay(TUNE, STOCK);
        final long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 1_500, "the chapter replays its two passes when it opens: " + ms + " ms");
    }

    private static int firstStop(FePonderGrid.Recording r, int part, int from) {
        for (int t = from; t < r.length; t++) if (!r.working[part][t]) return t;
        return r.length;
    }

    @Test
    void thePairSharesNothingUntilAWireJoinsThem() {
        final FePonderGrid.Recording r = Spans.replay(TUNE);
        for (int t = 0; t < Spans.joined(); t++) assertFalse(r.working[Spans.LAMP][t], "the lamp is dark before the join");
        final int lit = firstWorking(r, Spans.LAMP, Spans.joined());
        assertTrue(lit >= Spans.joined() && lit <= Spans.joined() + 2, "the lamp lights once joined: " + lit);
        assertTrue(Spans.sheet().tick("pair_caption") < Spans.JOIN, "the caption says it before the wire is strung");
        assertTrue(r.working[Spans.LAMP][Spans.sheet().length() - 1], "and it stays lit: the strip is the range's");
    }
}
