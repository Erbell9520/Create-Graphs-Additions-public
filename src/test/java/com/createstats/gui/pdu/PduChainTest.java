package com.createstats.gui.pdu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduChainTest {
    private static PduChain.Upstream linked(int socPct, int sustained, int claim,
                                            String stance) {
        return new PduChain.Upstream(true, 1, socPct, socPct * 100_000, 10_000_000, stance,
                "holding the band", 6_000, sustained, sustained + 4_000, claim, 10, 30,
                0, 0, 0, 0, false);
    }

    private static PduChain.Upstream unlinked(int tanks, int stored, int capacity, int sources) {
        return new PduChain.Upstream(false, 0, 0, 0, 0, "", "", 0, 0, 0, 0, 0, 0,
                tanks, stored, capacity, sources, false);
    }

    private static PduChain.Upstream blind(int supply) {
        return new PduChain.Upstream(false, 0, 0, 0, 0, "", "", 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, false, 0, 0, supply);
    }

    private static PduChain.Board board() {
        return new PduChain.Board(2_000, 300_000, 512_000, 15, 4_000, 5_000, 8, 2, false, false);
    }

    private static PduChain.Downstream nothing() {
        return new PduChain.Downstream(List.of(), 0, 0, 0, -1);
    }

    private static String text(List<PduDerivation.Row> rows) {
        final StringBuilder sb = new StringBuilder();
        for (PduDerivation.Row row : rows) {
            sb.append(row.label()).append(' ').append(row.value()).append(' ')
                    .append(row.note()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void aLinkedBoardNamesTheControllerRatherThanItsOwnEmptySurvey() {
        final String s = text(PduChain.rows(linked(63, 2_100, 0, "Nominal"), board(), nothing()));
        assertTrue(s.contains("Battery Controller"), s);
        assertTrue(s.contains("63% charged"), s);
        assertTrue(s.contains("Nominal"), s);

        assertTrue(s.contains("band 10 to 30 percent"), s);
    }

    @Test
    void theRechargeIsNamedAndSaidToEndByItself() {
        final String s = text(PduChain.rows(linked(22, 0, 1_200, "Conserve"), board(), nothing()));
        assertTrue(s.contains("held back to refill the banks"), s);
        assertTrue(s.contains("-1,200 FE/t"), s);
        assertTrue(s.contains("ends by itself"), s);
    }

    @Test
    void noRechargeMeansNoRechargeLine() {
        final String s = text(PduChain.rows(linked(63, 2_100, 0, "Nominal"), board(), nothing()));
        assertFalse(s.contains("held back to refill"), s);
    }

    @Test
    void anUnlinkedBoardReportsTheTanksItSurveyedForItself() {
        final String s = text(PduChain.rows(unlinked(2, 4_000_000, 10_000_000, 3), board(),
                nothing()));
        assertTrue(s.contains("2 tanks on the feed"), s);
        assertTrue(s.contains("40% charged"), s);
        assertFalse(s.contains("Battery Controller"), s);
    }

    @Test
    void aBoardWithNothingUpstreamIsToldSoRatherThanShownZeroes() {
        final String s = text(PduChain.rows(unlinked(0, 0, 0, 0), board(), nothing()));
        assertTrue(s.contains("Nothing is reaching this board."), s);
        assertTrue(s.contains("side boss"), s);
    }

    @Test
    void aMisplacedFeedIsSaidBeforeAnythingItWouldExplain() {
        final PduChain.Upstream up = new PduChain.Upstream(false, 0, 0, 0, 0, "", "", 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, true);
        final List<PduDerivation.Row> rows = PduChain.rows(up, board(), nothing());

        assertTrue(rows.get(1).label().contains("New Age connector"), rows.get(1).label());
    }

    @Test
    void aLegWiredToAControllerInputIsCalledOutFirstInDownstream() {
        final PduChain.Downstream down = new PduChain.Downstream(List.of(), 0, 0, 0, 2);
        final String s = text(PduChain.rows(linked(63, 2_100, 0, "Nominal"), board(), down));
        assertTrue(s.contains("Leg 3 runs to a Battery Controller input."), s);
        assertTrue(s.contains("back boss"), s);
    }

    @Test
    void chainedPanelsAndControllersAreBothListed() {
        final PduChain.Downstream down = new PduChain.Downstream(
                List.of(new PduChain.Panel("Power Distribution Unit ID:02", 2, 1_900, "High",
                        "Running")),
                1, 900, 1_400, -1);
        final String s = text(PduChain.rows(linked(63, 2_100, 0, "Nominal"), board(), down));
        assertTrue(s.contains("Leg 3  Power Distribution Unit ID:02"), s);
        assertTrue(s.contains("1,900 FE/t"), s);
        assertTrue(s.contains("Battery Controller on the back boss"), s);

        assertTrue(s.contains("Other draw on the back boss"), s);
        assertTrue(s.contains("500 FE/t"), s);
    }

    @Test
    void aBoardShedFromUpstreamSaysWhoDidItAndWhatItMeans() {
        final PduChain.Board shed =
                new PduChain.Board(0, 60_000, 512_000, 15, 0, 5_000, 2, 8, true, false);
        final String s = text(PduChain.rows(linked(12, 0, 0, "Critical"), shed, nothing()));
        assertTrue(s.contains("The panel upstream has shed this board."), s);
        assertTrue(s.contains("coasting on its own buffer"), s);
    }

    @Test
    void lockedOffOutranksShedBecauseItIsADecisionRatherThanAShortage() {
        final PduChain.Board off =
                new PduChain.Board(0, 60_000, 512_000, 15, 0, 5_000, 0, 10, true, true);
        final String s = text(PduChain.rows(linked(90, 5_000, 0, "Surplus"), off, nothing()));
        assertTrue(s.contains("Locked off from the panel upstream."), s);
        assertFalse(s.contains("has shed this board"), s);
    }

    @Test
    void aBoardFedByTwoControllersSaysSo() {
        final PduChain.Upstream two = new PduChain.Upstream(true, 2, 55, 5_500, 10_000, "Conserve",
                "holding the band", 900, 40, 120, 0, 10, 30, 0, 0, 0, 0, false);
        final String s = text(PduChain.rows(two, board(), nothing()));
        assertTrue(s.contains("2 Battery Controllers"), s);
        assertTrue(s.contains("worst of their stances"), s);
    }

    @Test
    void aBoardFedBySomethingItCannotPriceIsNotToldToGoAndWireOne() {
        final String s = text(PduChain.rows(blind(2_033), board(), nothing()));
        assertTrue(s.contains("Fed by something this board cannot price"), s);
        assertTrue(s.contains("2,033 FE/t"), s);
        assertFalse(s.contains("Nothing is reaching this board"), s);
        assertFalse(s.contains("Feed a side boss"), s);
    }

    @Test
    void aBoardThatIsGenuinelyStarvedStillSaysSoAndStillSaysWhatToDo() {
        final String s = text(PduChain.rows(blind(0), board(), nothing()));
        assertTrue(s.contains("Nothing is reaching this board"), s);
        assertTrue(s.contains("Feed a side boss"), s);
        assertFalse(s.contains("cannot price"), s);
    }

    @Test
    void theStrayLegFixNamesCableRatherThanJustTheBoss() {
        final String s = text(PduChain.rows(blind(2_033), board(),
                new PduChain.Downstream(List.of(), 0, 0, 0, 4)));
        assertTrue(s.contains("Battery Controller input"), s);
        assertTrue(s.contains("Run cable off this board's back boss"), s);
        assertTrue(s.contains("connector on that cable run"), s);
    }
}
