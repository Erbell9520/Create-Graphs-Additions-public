package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.gui.pdu.PduTableModel.DeviceRow;
import com.createstats.gui.pdu.PduTableModel.GroupRow;
import com.createstats.gui.pdu.PduTableModel.Row;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.PduReason;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduTableModelTest {

    private static int posCounter;

    private static BlockPos pos() {
        return new BlockPos(posCounter++, 64, 0);
    }

    private static BranchDevice running(String name, FePriority tier, int draw) {
        return new BranchDevice(pos(), name, 0, 0, tier, draw, false, FeLock.NONE, PduReason.RUNNING, false);
    }

    private static BranchDevice shed(String name, FePriority tier, int draw) {
        return new BranchDevice(pos(), name, 0, 0, tier, draw, true, FeLock.NONE, PduReason.OVER_BUDGET, false);
    }

    private static BranchDevice status(String name, FePriority tier, PduTableModel.Status s) {
        return switch (s) {
            case RUNNING -> new BranchDevice(pos(), name, 0, 0, tier, 100, false, FeLock.NONE, PduReason.RUNNING, false);
            case SHED -> new BranchDevice(pos(), name, 0, 0, tier, 100, true, FeLock.NONE, PduReason.OVER_BUDGET, false);
            case HOLDING -> new BranchDevice(pos(), name, 0, 0, tier, 100, false, FeLock.NONE, PduReason.AWAITING_DWELL, false);
            case OFF -> new BranchDevice(pos(), name, 0, 0, tier, 100, true, FeLock.FORCE_OFF, PduReason.LOCKED_OFF, false);
        };
    }

    private static List<String> groupLabels(List<Row> rows) {
        final List<String> out = new ArrayList<>();
        for (Row r : rows) {
            if (r instanceof GroupRow g) {
                out.add(g.label());
            }
        }
        return out;
    }

    private static List<BranchDevice> devices(List<Row> rows) {
        final List<BranchDevice> out = new ArrayList<>();
        for (Row r : rows) {
            if (r instanceof DeviceRow d) {
                out.add(d.device());
            }
        }
        return out;
    }

    private static List<String> deviceNames(List<Row> rows) {
        final List<String> out = new ArrayList<>();
        for (BranchDevice d : devices(rows)) {
            out.add(d.name());
        }
        return out;
    }

    // size edge cases

    @Test
    void emptyRosterProducesNoRows() {
        final List<Row> rows = PduTableModel.build(List.of(), PduTableModel.Group.TIER,
                PduTableModel.Sort.DRAW, false, "", false);
        assertTrue(rows.isEmpty());
    }

    @Test
    void singleDeviceProducesOneHeaderThenOneRow() {
        final List<Row> rows = PduTableModel.build(List.of(running("Lamp", FePriority.NORMAL, 40)),
                PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "", false);
        assertEquals(2, rows.size());
        assertTrue(rows.get(0) instanceof GroupRow);
        assertTrue(rows.get(1) instanceof DeviceRow);
    }

    @Test
    void twoHundredDevicesSurviveEveryGroupMode() {
        final List<BranchDevice> roster = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            final FePriority tier = FePriority.values()[i % FePriority.values().length];
            roster.add(running("Device " + i, tier, (i * 37) % 5000));
        }
        for (PduTableModel.Group group : PduTableModel.Group.values()) {
            final List<Row> rows = PduTableModel.build(roster, group, PduTableModel.Sort.DRAW,
                    false, "", false);
            assertEquals(200, PduTableModel.deviceCount(rows),
                    "no device may be lost under group " + group);
        }
    }

    // grouping

    @Test
    void groupByTierOrdersGroupsInServeOrder() {
        final List<BranchDevice> roster = List.of(
                running("a", FePriority.OPTIONAL, 10),
                running("b", FePriority.CRITICAL, 10),
                running("c", FePriority.NORMAL, 10));
        final List<String> labels = groupLabels(PduTableModel.build(roster, PduTableModel.Group.TIER,
                PduTableModel.Sort.NAME, false, "", false));
        assertEquals(List.of("Critical", "Normal", "Optional"), labels);
    }

    @Test
    void groupByTypeOrdersGroupsAlphabetically() {
        final List<BranchDevice> roster = List.of(
                running("Zephyr", FePriority.NORMAL, 10),
                running("Alarm", FePriority.NORMAL, 10),
                running("Monitor", FePriority.NORMAL, 10));
        final List<String> labels = groupLabels(PduTableModel.build(roster, PduTableModel.Group.TYPE,
                PduTableModel.Sort.NAME, false, "", false));
        assertEquals(List.of("Alarm", "Monitor", "Zephyr"), labels);
    }

    @Test
    void groupByStatusPutsAttentionFirst() {
        final List<BranchDevice> roster = List.of(
                status("r", FePriority.NORMAL, PduTableModel.Status.RUNNING),
                status("o", FePriority.NORMAL, PduTableModel.Status.OFF),
                status("s", FePriority.NORMAL, PduTableModel.Status.SHED),
                status("h", FePriority.NORMAL, PduTableModel.Status.HOLDING));
        final List<String> labels = groupLabels(PduTableModel.build(roster, PduTableModel.Group.STATUS,
                PduTableModel.Sort.NAME, false, "", false));
        assertEquals(List.of("Shed", "Holding", "Running", "Off"), labels);
    }

    @Test
    void groupHeaderCarriesServedTotalAndDraw() {
        final List<BranchDevice> roster = List.of(
                running("a", FePriority.NORMAL, 100),
                shed("b", FePriority.NORMAL, 250));
        final GroupRow header = (GroupRow) PduTableModel.build(roster, PduTableModel.Group.TIER,
                PduTableModel.Sort.NAME, false, "", false).get(0);
        assertEquals(2, header.total());
        assertEquals(1, header.served(), "the shed device is not served");
        assertEquals(350, header.totalDraw());
    }

    // sorting

    @Test
    void sortByDrawIsBiggestFirstByDefault() {
        final List<BranchDevice> roster = List.of(
                running("small", FePriority.NORMAL, 10),
                running("big", FePriority.NORMAL, 900),
                running("mid", FePriority.NORMAL, 300));
        assertEquals(List.of("big", "mid", "small"), deviceNames(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "", false)));
    }

    @Test
    void sortByNameIsAlphabetical() {
        final List<BranchDevice> roster = List.of(
                running("Charlie", FePriority.NORMAL, 10),
                running("alpha", FePriority.NORMAL, 10),
                running("Bravo", FePriority.NORMAL, 10));
        assertEquals(List.of("alpha", "Bravo", "Charlie"), deviceNames(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.NAME, false, "", false)));
    }

    @Test
    void sortByStatusPutsShedFirstWithinAGroup() {
        final List<BranchDevice> roster = List.of(
                status("run", FePriority.NORMAL, PduTableModel.Status.RUNNING),
                status("off", FePriority.NORMAL, PduTableModel.Status.OFF),
                status("shed", FePriority.NORMAL, PduTableModel.Status.SHED),
                status("hold", FePriority.NORMAL, PduTableModel.Status.HOLDING));
        assertEquals(List.of("shed", "hold", "run", "off"), deviceNames(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.STATUS, false, "", false)));
    }

    @Test
    void reverseFlipsTheSort() {
        final List<BranchDevice> roster = List.of(
                running("small", FePriority.NORMAL, 10),
                running("big", FePriority.NORMAL, 900),
                running("mid", FePriority.NORMAL, 300));
        assertEquals(List.of("small", "mid", "big"), deviceNames(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, true, "", false)));
    }

    @Test
    void sortIsStableForEqualKeysInBothDirections() {
        // Three devices with identical draw. Their roster order must survive the sort, forward and
        // reversed, or the table jitters every time a value ties.
        final List<BranchDevice> roster = List.of(
                running("first", FePriority.NORMAL, 500),
                running("second", FePriority.NORMAL, 500),
                running("third", FePriority.NORMAL, 500));
        assertEquals(List.of("first", "second", "third"), deviceNames(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "", false)));
        assertEquals(List.of("first", "second", "third"), deviceNames(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, true, "", false)));
    }

    // filtering

    @Test
    void searchThatMatchesNothingProducesNoRows() {
        final List<BranchDevice> roster = List.of(running("Lamp", FePriority.NORMAL, 10));
        assertTrue(PduTableModel.build(roster, PduTableModel.Group.TIER, PduTableModel.Sort.DRAW,
                false, "reactor", false).isEmpty());
    }

    @Test
    void searchIsCaseInsensitive() {
        final List<BranchDevice> roster = List.of(
                running("Stage Light", FePriority.OPTIONAL, 10),
                running("Reactor", FePriority.CRITICAL, 10));
        assertEquals(List.of("Stage Light"), deviceNames(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "stage", false)));
        assertEquals(List.of("Stage Light"), deviceNames(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "STAGE", false)));
    }

    @Test
    void shedOnlyKeepsOnlyShedDevices() {
        final List<BranchDevice> roster = List.of(
                running("on", FePriority.NORMAL, 10),
                shed("off", FePriority.NORMAL, 10));
        assertEquals(List.of("off"), deviceNames(PduTableModel.build(roster, PduTableModel.Group.TIER,
                PduTableModel.Sort.DRAW, false, "", true)));
    }

    @Test
    void tierFilterKeepsOnlyThatTier() {
        final List<BranchDevice> roster = List.of(
                running("a", FePriority.CRITICAL, 10),
                running("b", FePriority.NORMAL, 10),
                running("c", FePriority.CRITICAL, 20));
        assertEquals(List.of("a", "c"), deviceNames(PduTableModel.build(roster, PduTableModel.Group.TIER,
                PduTableModel.Sort.NAME, false, "", false, FePriority.CRITICAL)));
    }

    @Test
    void matchesRespectsTheTierFilterAndTreatsNullAsAll() {
        final BranchDevice crit = running("x", FePriority.CRITICAL, 10);
        assertTrue(PduTableModel.matches(crit, "", false, FePriority.CRITICAL));
        assertFalse(PduTableModel.matches(crit, "", false, FePriority.NORMAL));
        assertTrue(PduTableModel.matches(crit, "", false, null), "a null tier filter matches every tier");
    }

    @Test
    void matchesPredicateHandlesEveryCase() {
        final BranchDevice on = running("Factory Logic Controller", FePriority.NORMAL, 10);
        final BranchDevice off = shed("Stage Light", FePriority.OPTIONAL, 10);
        assertTrue(PduTableModel.matches(on, "", false), "an empty search matches everything");
        assertTrue(PduTableModel.matches(on, null, false), "a null search matches everything");
        assertTrue(PduTableModel.matches(on, "logic", false), "substring, case folded");
        assertTrue(PduTableModel.matches(on, "LOGIC", false));
        assertFalse(PduTableModel.matches(on, "reactor", false), "no substring, no match");
        assertFalse(PduTableModel.matches(on, "", true), "shed only rejects a running device");
        assertTrue(PduTableModel.matches(off, "", true), "shed only keeps a shed device");
    }

    // status classification

    @Test
    void statusOfReadsLockThenReasonThenShed() {
        assertEquals(PduTableModel.Status.OFF,
                PduTableModel.Status.of(status("x", FePriority.NORMAL, PduTableModel.Status.OFF)));
        assertEquals(PduTableModel.Status.HOLDING,
                PduTableModel.Status.of(status("x", FePriority.NORMAL, PduTableModel.Status.HOLDING)));
        assertEquals(PduTableModel.Status.SHED,
                PduTableModel.Status.of(status("x", FePriority.NORMAL, PduTableModel.Status.SHED)));
        assertEquals(PduTableModel.Status.RUNNING,
                PduTableModel.Status.of(status("x", FePriority.NORMAL, PduTableModel.Status.RUNNING)));
    }

    @Test
    void buildDoesNotMutateTheInputRoster() {
        final List<BranchDevice> roster = new ArrayList<>(List.of(
                running("b", FePriority.NORMAL, 10),
                running("a", FePriority.NORMAL, 900)));
        final List<BranchDevice> snapshot = new ArrayList<>(roster);
        PduTableModel.build(roster, PduTableModel.Group.TIER, PduTableModel.Sort.NAME, false, "", false);
        assertEquals(snapshot, roster, "build sorted a copy, not the caller's list");
    }
}
