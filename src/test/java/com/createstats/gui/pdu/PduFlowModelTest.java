package com.createstats.gui.pdu;

import com.createstats.power.FePriority;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduFlowModelTest {
    private static int[] tiers(int critical, int high, int normal, int low, int optional) {
        return new int[] {critical, high, normal, low, optional};
    }

    private static PduFlowModel.Flow flow(PduFlowModel.SupplyParts supply, int[] served, int export,
                                          int charging, int upkeep) {
        return PduFlowModel.flow(new PduFlowModel.Inputs(supply, served, export, charging, upkeep));
    }

    @Test
    @DisplayName("both columns always sum to the same figure")
    void theRiverBalances() {
        final PduFlowModel.SupplyParts[] supplies = {
                new PduFlowModel.SupplyParts(0, 0, 0, 0, 0),
                new PduFlowModel.SupplyParts(800, 300, 0, 0, 0),
                new PduFlowModel.SupplyParts(800, 300, 2_000, 0, 150),
                new PduFlowModel.SupplyParts(0, 0, 0, 5_000, 0),
                new PduFlowModel.SupplyParts(Integer.MAX_VALUE, 10, 0, 0, Integer.MAX_VALUE),
        };
        final int[][] loads = {tiers(0, 0, 0, 0, 0), tiers(100, 0, 400, 0, 9), tiers(9_000, 1, 1, 1, 1)};
        for (PduFlowModel.SupplyParts s : supplies) {
            for (int[] served : loads) {
                for (int export : new int[] {0, 700}) {
                    final PduFlowModel.Flow f = flow(s, served, export, 50, 12);
                    assertEquals(f.sum(true), f.sum(false), "unbalanced for " + s);
                    assertEquals(f.total(), f.sum(true));
                }
            }
        }
    }

    @Test
    @DisplayName("a deficit is drawn from the buffer, a surplus is spare, never both")
    void deficitAndSurplusAreNamed() {
        final PduFlowModel.Flow short_ = flow(new PduFlowModel.SupplyParts(400, 0, 0, 0, 0),
                tiers(300, 0, 300, 0, 0), 0, 0, 0);
        assertTrue(has(short_.in(), PduFlowModel.Kind.FROM_BUFFER, 200));
        assertFalse(hasKind(short_.out(), PduFlowModel.Kind.SPARE));

        final PduFlowModel.Flow spare = flow(new PduFlowModel.SupplyParts(1_000, 0, 0, 0, 0),
                tiers(300, 0, 0, 0, 0), 0, 0, 0);
        assertTrue(has(spare.out(), PduFlowModel.Kind.SPARE, 700));
        assertFalse(hasKind(spare.in(), PduFlowModel.Kind.FROM_BUFFER));
    }

    @Test
    @DisplayName("solar is the weather share of the priced feed, and the rest are generators")
    void solarIsSplitOut() {
        final PduFlowModel.Flow f = flow(new PduFlowModel.SupplyParts(900, 360, 0, 0, 0),
                tiers(0, 0, 900, 0, 0), 0, 0, 0);
        assertTrue(has(f.in(), PduFlowModel.Kind.SOLAR, 360));
        assertTrue(has(f.in(), PduFlowModel.Kind.GENERATORS, 540));
    }

    @Test
    @DisplayName("supply above the priced figure is the controller when its report is larger, else the feed")
    void unpricedSupplyIsAttributed() {
        final PduFlowModel.Flow linked = flow(new PduFlowModel.SupplyParts(100, 0, 400, 1_500, 0),
                tiers(0, 0, 0, 0, 0), 0, 0, 0);
        assertTrue(has(linked.in(), PduFlowModel.Kind.CONTROLLER, 1_400));
        final PduFlowModel.Flow fed = flow(new PduFlowModel.SupplyParts(100, 0, 2_000, 0, 0),
                tiers(0, 0, 0, 0, 0), 0, 0, 0);
        assertTrue(has(fed.in(), PduFlowModel.Kind.UNPRICED, 1_900));

        assertEquals(2_000, new PduFlowModel.SupplyParts(100, 0, 2_000, 1_000, 0).sideSupply());
    }

    @Test
    @DisplayName("KI-79: over a fully priced feed the excess is the sources' buffers, never a feed")
    void aFullyPricedFeedHasNoUnpricedRow() {
        final PduFlowModel.Flow roof = flow(new PduFlowModel.SupplyParts(5_040, 5_040, 5_733, 0, 0, true),
                tiers(0, 0, 5_888, 0, 0), 0, 0, 51);
        assertTrue(has(roof.in(), PduFlowModel.Kind.SOLAR, 5_040));
        assertTrue(has(roof.in(), PduFlowModel.Kind.SOURCE_BUFFERS, 693));
        assertFalse(hasKind(roof.in(), PduFlowModel.Kind.UNPRICED));
        assertFalse(hasKind(roof.in(), PduFlowModel.Kind.GENERATORS));
        assertEquals(roof.sum(true), roof.sum(false));

        final PduFlowModel.Flow foreign = flow(new PduFlowModel.SupplyParts(5_040, 5_040, 5_733, 0, 0, false),
                tiers(0, 0, 5_888, 0, 0), 0, 0, 51);
        assertTrue(has(foreign.in(), PduFlowModel.Kind.UNPRICED, 693));
    }

    @Test
    @DisplayName("KI-106: a linked controller's delivery is the controller, even when its banks top it up")
    void aLinkedControllerIsNeverAnUnpricedFeed() {
        final PduFlowModel.Flow linked = flow(new PduFlowModel.SupplyParts(0, 0, 9_267, 7_700, 0, false, true),
                tiers(0, 9_216, 0, 0, 0), 0, 0, 51);
        assertTrue(has(linked.in(), PduFlowModel.Kind.CONTROLLER, 9_267));
        assertFalse(hasKind(linked.in(), PduFlowModel.Kind.UNPRICED));

        final PduFlowModel.Flow unlinked = flow(new PduFlowModel.SupplyParts(0, 0, 9_267, 7_700, 0, false),
                tiers(0, 9_216, 0, 0, 0), 0, 0, 51);
        assertTrue(has(unlinked.in(), PduFlowModel.Kind.UNPRICED, 9_267));
    }

    @Test
    @DisplayName("KI-106: a device held only by the margin is held for headroom, not blamed on generation")
    void theVerdictNamesAHeadroomHold() {
        final PduFlowModel.Budgets b = new PduFlowModel.Budgets(10_240, 10_240, 49_152, 0, 51, 0, 0,
                false, true, true, false, 49_152);
        final int[] byReason = new int[PduReason.values().length];
        byReason[PduReason.HEADROOM.ordinal()] = 1_024;
        final String v = PduFlowModel.verdict(PduMode.AUTO, b, 9_267, byReason, 4);
        assertTrue(v.contains("headroom"), v);
        assertTrue(v.contains("10.2k"), "the budget they fit is quoted: " + v);
        assertFalse(v.contains("generation stops short"), v);
    }

    @Test
    @DisplayName("zero nodes are left out, so a quiet board is not a column of empty boxes")
    void zeroNodesAreDropped() {
        final PduFlowModel.Flow f = flow(new PduFlowModel.SupplyParts(0, 0, 0, 0, 0),
                tiers(0, 0, 0, 0, 0), 0, 0, 0);
        assertTrue(f.in().isEmpty());
        assertTrue(f.out().isEmpty());
        assertEquals(0, f.total());
    }

    @Test
    @DisplayName("a column fits its box, every node is at least the floor, and order is kept")
    void layoutFitsAndFloors() {
        final List<PduFlowModel.Node> nodes = List.of(
                new PduFlowModel.Node(PduFlowModel.Kind.CRITICAL, 20_000),
                new PduFlowModel.Node(PduFlowModel.Kind.LOW, 3),
                new PduFlowModel.Node(PduFlowModel.Kind.UPKEEP, 40));
        final int[] ys = PduFlowModel.layout(nodes, 50, 200, 6, 14);
        for (int i = 0; i < nodes.size(); i++) {
            assertTrue(ys[i * 2 + 1] - ys[i * 2] >= 14, "node " + i + " below the floor");
            assertTrue(ys[i * 2] >= 50 && ys[i * 2 + 1] <= 250, "node " + i + " outside the box");
            if (i > 0) {
                assertTrue(ys[i * 2] >= ys[(i - 1) * 2 + 1], "node " + i + " overlaps the one above");
            }
        }
        assertEquals(250, ys[5], "the last node ends exactly at the bottom");
        assertTrue(ys[1] - ys[0] > ys[3] - ys[2], "the big tier is not the tall one");
    }

    @Test
    @DisplayName("a column too short for the floor still fits, by shrinking the floor")
    void aCrampedColumnStillFits() {
        final List<PduFlowModel.Node> nodes = List.of(
                new PduFlowModel.Node(PduFlowModel.Kind.CRITICAL, 1),
                new PduFlowModel.Node(PduFlowModel.Kind.HIGH, 1),
                new PduFlowModel.Node(PduFlowModel.Kind.NORMAL, 1),
                new PduFlowModel.Node(PduFlowModel.Kind.LOW, 1));
        final int[] ys = PduFlowModel.layout(nodes, 0, 30, 2, 14);
        assertTrue(ys[7] <= 30, "ran off the bottom: " + ys[7]);
        for (int i = 0; i < 4; i++) {
            assertTrue(ys[i * 2 + 1] > ys[i * 2], "node " + i + " has no height");
        }
    }

    @Test
    @DisplayName("a crowded column merges its smallest flows, keeps its sum and stays readable")
    void aCrowdedColumnMergesTheSmallest() {
        final List<PduFlowModel.Node> sinks = List.of(
                new PduFlowModel.Node(PduFlowModel.Kind.CRITICAL, 300),
                new PduFlowModel.Node(PduFlowModel.Kind.HIGH, 450),
                new PduFlowModel.Node(PduFlowModel.Kind.NORMAL, 380),
                new PduFlowModel.Node(PduFlowModel.Kind.EXPORT, 200),
                new PduFlowModel.Node(PduFlowModel.Kind.CHARGING, 90),
                new PduFlowModel.Node(PduFlowModel.Kind.UPKEEP, 16),
                new PduFlowModel.Node(PduFlowModel.Kind.SPARE, 84));

        final int max = PduFlowModel.capacity(72, 4, 12);
        assertEquals(4, max);
        final List<PduFlowModel.Node> fitted = PduFlowModel.fit(sinks, max, false);
        assertEquals(4, fitted.size());
        int before = 0;
        int after = 0;
        for (PduFlowModel.Node n : sinks) {
            before += n.fe();
        }
        for (PduFlowModel.Node n : fitted) {
            after += n.fe();
        }
        assertEquals(before, after, "merging changed the column's sum, so the river no longer balances");
        assertEquals(PduFlowModel.Kind.OTHER_OUT, fitted.get(3).kind());
        assertEquals(PduFlowModel.Kind.CRITICAL, fitted.get(0).kind(), "kept nodes lost their order");

        assertTrue(PduFlowModel.fit(sinks, 20, false) == sinks);
        final int[] ys = PduFlowModel.layout(fitted, 0, 72, 4, 12);
        for (int i = 0; i < fitted.size(); i++) {
            assertTrue(ys[i * 2 + 1] - ys[i * 2] >= 12, "a fitted node is still too thin to read");
        }
    }

    @Test
    @DisplayName("the bus slots cover the bus exactly, in proportion")
    void busSlotsCoverTheBus() {
        final List<PduFlowModel.Node> nodes = List.of(
                new PduFlowModel.Node(PduFlowModel.Kind.SOLAR, 300),
                new PduFlowModel.Node(PduFlowModel.Kind.GENERATORS, 700));
        final int[] ys = PduFlowModel.busSlots(nodes, 1_000, 10, 101);
        assertEquals(10, ys[0]);
        assertEquals(ys[1], ys[2], "slots are contiguous");
        assertEquals(111, ys[3], "the bus is covered exactly");
        assertEquals(30, ys[1] - ys[0]);
    }

    @Test
    @DisplayName("current dots are still for no flow and faster for more, without becoming a blur")
    void particleSpeedIsLogarithmic() {
        assertEquals(0.0f, PduFlowModel.particleSpeed(0));
        assertTrue(PduFlowModel.particleSpeed(10_000) > PduFlowModel.particleSpeed(100));
        assertTrue(PduFlowModel.particleSpeed(1_000_000) < 120.0f, "a big feed became a blur");
        final float phase = PduFlowModel.particlePhase(123_456L, 500, 12.0f);
        assertTrue(phase >= 0.0f && phase < 12.0f);
        assertEquals(0.0f, PduFlowModel.particlePhase(123_456L, 0, 12.0f));
    }

    @Test
    @DisplayName("the ladder scales to the decision, not to a million-FE critical ride")
    void ladderScaleIgnoresTheCoreRide() {
        final PduFlowModel.Budgets b = new PduFlowModel.Budgets(1_200, 900, 4_000_000, 300, 50, 0,
                200, false, false, false, false, 98_304);
        final int scale = PduFlowModel.ladderScale(b, 1_000, 1_500);
        assertTrue(scale >= 1_500 && scale <= 2_000, "scale " + scale);
        assertEquals(PduFlowModel.ladderX(0, scale, 10, 200), 10);
        assertEquals(PduFlowModel.ladderX(scale * 10, scale, 10, 200), 210, "off-scale pins to the end");
        assertEquals(2_000, PduFlowModel.niceCeil(1_680));
        assertEquals(5_000, PduFlowModel.niceCeil(4_100));
        assertEquals(10_000, PduFlowModel.niceCeil(10_000));
        assertEquals(1, PduFlowModel.niceCeil(0));
    }

    @Test
    @DisplayName("tier spans stack in admit order and the shed part follows the served part")
    void tierSpansStack() {
        final List<PduFlowModel.TierSpan> spans = PduFlowModel.tierSpans(
                tiers(300, 0, 500, 200, 0), tiers(300, 0, 200, 0, 0));
        assertEquals(3, spans.size(), "an empty tier is no span");
        assertEquals(FePriority.CRITICAL, spans.get(0).tier());
        assertEquals(0, spans.get(0).start());
        assertEquals(300, spans.get(1).start());
        assertEquals(500, spans.get(1).servedEnd());
        assertEquals(800, spans.get(1).end());
        assertEquals(300, spans.get(1).shed());
        assertEquals(1_000, spans.get(2).end());
    }

    @Test
    @DisplayName("the verdict names the line of the arithmetic that did the holding")
    void theVerdictNamesTheCause() {
        final PduFlowModel.Budgets dusk = new PduFlowModel.Budgets(1_000, 200, 1_000, 800, 0, 0, 0,
                false, false, false, false, 8_192);
        final int[] byReason = new int[PduReason.values().length];
        byReason[PduReason.SUPPLY_FALLING.ordinal()] = 500;
        byReason[PduReason.OVER_BUDGET.ordinal()] = 100;
        final String v = PduFlowModel.verdict(PduMode.AUTO, dusk, 1_000, byReason, 2);
        assertTrue(v.contains("forecast to fall"), v);
        assertTrue(v.contains("800"), "the drop is not quoted: " + v);

        final int[] wire = new int[PduReason.values().length];
        wire[PduReason.OVER_CAPACITY.ordinal()] = 900;
        assertTrue(PduFlowModel.verdict(PduMode.AUTO, dusk, 1_000, wire, 1).contains("More wire"));

        assertTrue(PduFlowModel.verdict(PduMode.AUTO, dusk, 1_000, new int[PduReason.values().length], 0)
                .startsWith("Everything is running"));
        assertTrue(PduFlowModel.verdict(PduMode.MANUAL, dusk, 1_000, byReason, 2).startsWith("Manual"));
        final PduFlowModel.Budgets floor = new PduFlowModel.Budgets(0, 0, 0, 0, 0, 0, 0, true, false,
                false, false, 0);
        assertTrue(PduFlowModel.verdict(PduMode.AUTO, floor, 0, byReason, 2).contains("reserve floor"));
    }

    @Test
    @DisplayName("figures read as a player reads them")
    void figuresAreReadable() {
        assertEquals("950", PduFlowModel.fe(950));
        assertEquals("9,999", PduFlowModel.fe(9_999));
        assertEquals("12.4k", PduFlowModel.fe(12_400));
        assertEquals("3.2M", PduFlowModel.fe(3_200_000));
    }

    private static boolean has(List<PduFlowModel.Node> nodes, PduFlowModel.Kind kind, int fe) {
        for (PduFlowModel.Node n : nodes) {
            if (n.kind() == kind && n.fe() == fe) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasKind(List<PduFlowModel.Node> nodes, PduFlowModel.Kind kind) {
        for (PduFlowModel.Node n : nodes) {
            if (n.kind() == kind) {
                return true;
            }
        }
        return false;
    }
}
