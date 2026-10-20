package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.gui.pdu.PduTableModel.DeviceRow;
import com.createstats.gui.pdu.PduTableModel.GroupRow;
import com.createstats.gui.pdu.PduTableModel.Row;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.PduDeviceKind;
import com.createstats.power.PduReason;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduTableStorageRowsTest {
    private static int posCounter;

    private static BlockPos pos() {
        return new BlockPos(posCounter++, 64, 0);
    }

    private static BranchDevice load(String name, FePriority tier, int draw, boolean shed) {
        return new BranchDevice(pos(), name, 0, 0, tier, draw, shed, FeLock.NONE,
                shed ? PduReason.OVER_BUDGET : PduReason.RUNNING, false);
    }

    private static BranchDevice tank(String name, int flow, int stored, int capacity) {
        return new BranchDevice(pos(), name, 0, 0, FePriority.NORMAL, flow, false, FeLock.NONE,
                flow > 0 ? PduReason.STORAGE_CHARGING
                        : flow < 0 ? PduReason.STORAGE_DISCHARGING : PduReason.STORAGE_IDLE,
                false, PduDeviceKind.STORAGE, stored, capacity);
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

    private static GroupRow header(List<Row> rows, String label) {
        for (Row r : rows) {
            if (r instanceof GroupRow g && g.label().equals(label)) {
                return g;
            }
        }
        throw new AssertionError("no header " + label + " in " + groupLabels(rows));
    }

    private static int deviceRows(List<Row> rows) {
        int n = 0;
        for (Row r : rows) {
            if (r instanceof DeviceRow) {
                n++;
            }
        }
        return n;
    }

    @Test
    void aBatteryOnALegAppearsInTheDeviceList() {
        final List<BranchDevice> roster =
                List.of(load("Electric Motor", FePriority.NORMAL, 200, false),
                        tank("Modular Accumulator", 1_500, 400_000, 1_000_000));
        final List<Row> rows = PduTableModel.build(roster, PduTableModel.Group.TIER,
                PduTableModel.Sort.DRAW, false, "", false);
        assertEquals(2, deviceRows(rows));
    }

    @Test
    void aBatteryIsFoundByName() {
        final List<BranchDevice> roster = List.of(load("Electric Motor", FePriority.NORMAL, 200, false),
                tank("Modular Accumulator", 0, 1, 2));
        final List<Row> rows = PduTableModel.build(roster, PduTableModel.Group.TIER,
                PduTableModel.Sort.DRAW, false, "accum", false);
        assertEquals(1, deviceRows(rows));
    }

    @Test
    void aBatteryIsNeverMatchedByTheShedOnlyFilter() {
        final BranchDevice tank = tank("Modular Accumulator", 0, 0, 1_000_000);
        assertFalse(PduTableModel.matches(tank, "", true));
        assertTrue(PduTableModel.matches(tank, "", false));
    }

    @Test
    void aBatteryIsNeverMatchedByATierDrillDown() {
        final BranchDevice tank = tank("Modular Accumulator", 0, 0, 1_000_000);
        assertFalse(PduTableModel.matches(tank, "", false, FePriority.NORMAL));
        assertFalse(PduTableModel.matches(tank, "", false, FePriority.CRITICAL));
    }

    @Test
    void aBatteryGetsItsOwnSectionRatherThanATier() {
        final List<BranchDevice> roster = List.of(load("Electric Motor", FePriority.NORMAL, 200, false),
                tank("Modular Accumulator", 1_500, 400_000, 1_000_000));
        final List<String> labels = groupLabels(PduTableModel.build(roster,
                PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "", false));
        assertTrue(labels.contains("Storage"), labels.toString());

        assertEquals("Storage", labels.get(labels.size() - 1));
    }

    @Test
    void aBatteryIsNotCountedInALoadHeadersTally() {
        final List<BranchDevice> roster = List.of(load("Electric Motor", FePriority.NORMAL, 200, false),
                load("Induction Heater", FePriority.NORMAL, 300, true),
                tank("Modular Accumulator", 9_999, 400_000, 1_000_000));
        final List<Row> rows = PduTableModel.build(roster, PduTableModel.Group.TIER,
                PduTableModel.Sort.DRAW, false, "", false);
        final GroupRow normal = header(rows, FePriority.NORMAL.getDisplayName());
        assertEquals(2, normal.total());
        assertEquals(1, normal.served());
        assertEquals(500, normal.totalDraw());
        assertFalse(normal.storage());
    }

    @Test
    void theStorageHeaderTalliesChargeRatherThanService() {
        final List<BranchDevice> roster = List.of(tank("Modular Accumulator", 1_000, 250_000, 1_000_000),
                tank("Modular Accumulator", -400, 250_000, 1_000_000));
        final GroupRow storage = header(PduTableModel.build(roster, PduTableModel.Group.TIER,
                PduTableModel.Sort.DRAW, false, "", false), "Storage");
        assertTrue(storage.storage());
        assertEquals(2, storage.total());

        assertEquals(storage.total(), storage.served());

        assertEquals(600, storage.totalDraw());
        assertEquals(500_000, storage.stored());
        assertEquals(2_000_000, storage.capacity());
        assertEquals(0.25f, storage.fill(), 0.0001f);
    }

    @Test
    void aBatteryGroupsAndSortsUnderItsOwnStatus() {
        assertEquals(PduTableModel.Status.STORAGE,
                PduTableModel.Status.of(tank("Modular Accumulator", 0, 0, 1_000_000)));

        assertEquals("Storage", PduTableModel.Status.STORAGE.label());
    }

    @Test
    void aMachineRowIsUnchangedByTheNewFields() {
        final BranchDevice machine = load("Electric Motor", FePriority.HIGH, 200, false);
        assertEquals(PduDeviceKind.MACHINE, machine.kind());
        assertFalse(machine.isStorage());
        assertEquals(0, machine.stored());
        assertEquals(0, machine.capacity());
        assertEquals(0.0f, machine.fill(), 0.0001f);
    }

    @Test
    void anUnresolvedMultiblockIsListedHoldingNothing() {
        final BranchDevice tank = tank("Modular Accumulator", 0, 0, 0);
        assertTrue(tank.isStorage());
        assertEquals(0.0f, tank.fill(), 0.0001f);
        assertTrue(PduTableModel.matches(tank, "", false));
    }
}
