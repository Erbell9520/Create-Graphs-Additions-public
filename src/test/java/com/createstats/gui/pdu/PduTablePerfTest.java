package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.PduReason;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduTablePerfTest {
    private static List<BranchDevice> twoHundredDevices() {
        final List<BranchDevice> roster = new ArrayList<>(200);
        for (int i = 0; i < 200; i++) {
            final FePriority tier = FePriority.values()[i % FePriority.values().length];
            final boolean shed = i % 3 == 0;
            roster.add(new BranchDevice(new BlockPos(i, 64, i % 7), "Device " + i, 0, 0, tier,
                    (i * 137) % 6000, shed, FeLock.NONE,
                    shed ? PduReason.OVER_BUDGET : PduReason.RUNNING, false));
        }
        return roster;
    }

    @Test
    void twoHundredDeviceModelIsCheapToBuildAndFreeToKeep() throws IOException {
        final List<BranchDevice> roster = twoHundredDevices();

        for (int i = 0; i < 5000; i++) {
            PduTableModel.build(roster, PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "", false);
        }

        final int builds = 20000;
        long t0 = System.nanoTime();
        for (int i = 0; i < builds; i++) {
            final List<PduTableModel.Row> rows = PduTableModel.build(roster,
                    PduTableModel.Group.STATUS, PduTableModel.Sort.DRAW, false, "", false);
            assertEquals(200, PduTableModel.deviceCount(rows));
        }
        final double buildUs = (System.nanoTime() - t0) / 1000.0 / builds;

        final PduTable table = new PduTable();
        table.setData(roster, PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "", false);
        final int frames = 200000;
        t0 = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            table.setData(roster, PduTableModel.Group.TIER, PduTableModel.Sort.DRAW, false, "", false);
        }
        final double steadyUs = (System.nanoTime() - t0) / 1000.0 / frames;

        Files.writeString(Path.of("build", "pdu-perf.txt"), String.format(
                "PduTable 200-device model%n  build (on sync/control change): %.2f us/call%n"
                        + "  steady setData (per frame, no change): %.4f us/call%n", buildUs, steadyUs));

        assertTrue(buildUs < 2000.0, "200-device build took " + buildUs + " us, expected well under a frame");
        assertTrue(steadyUs < 50.0, "steady per-frame setData took " + steadyUs + " us, expected near zero");
    }
}
