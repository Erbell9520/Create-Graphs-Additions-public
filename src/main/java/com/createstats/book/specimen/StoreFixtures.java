package com.createstats.book.specimen;

import com.createstats.book.content.Configs;
import com.createstats.book.scene.BlockFigures;
import com.createstats.gui.battery.BatteryView;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.DynamoTier;
import com.createstats.power.FeBankMerge;
import com.createstats.power.ReserveProjection;

import java.util.ArrayList;
import java.util.List;

public final class StoreFixtures {
    private StoreFixtures() {
    }

    public static final String BANK_FACE = "capacitor_bank.face";

    public static final String CONTROLLER_FACE = "fe_battery_controller.face";

    public record BankReading(int blocks, int capacity, int stored, int in, int out) {
        public float fill() {
            return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, stored / (float) capacity));
        }

        public int rate() {
            return in - out;
        }

        public int enduranceTicks() {
            final int rate = rate();
            if (rate > 0) {
                final int headroom = capacity - stored;
                return headroom <= 0 ? -1 : headroom / rate;
            }
            if (rate < 0) {
                return stored <= 0 ? -1 : stored / -rate;
            }
            return -1;
        }
    }

    public static int dynamoFlow() {
        return (int) Math.round(DynamoTier.STANDARD.fePerRpm() * com.createstats.block.KineticDynamoBlock.TOP_RPM);
    }

    public static BankReading bank() {
        final int blocks = BlockFigures.BANK_SIDE * BlockFigures.BANK_SIDE * BlockFigures.BANK_SIDE;
        final int capacity = FeBankMerge.capacity(blocks, Configs.bankBlockCapacity(), Configs.bankMergeExponent());
        return new BankReading(blocks, capacity, capacity / 2, dynamoFlow(), 0);
    }

    public record ControllerReading(int capacity, int pct, int rate, int generation, int delivered,
                                    List<BatteryControllerPlan.Reason> lamps, ReserveProjection.Result outlook) {
        public ControllerReading {
            lamps = List.copyOf(lamps);
        }
    }

    public static ControllerReading controller() {
        final BatteryView v = BatteryPlates.view();
        final List<BatteryControllerPlan.Reason> lamps = new ArrayList<>();
        for (final BatteryView.Output o : v.outputs()) {
            lamps.add(o.reason());
        }
        return new ControllerReading(v.capacity(), v.socPct(), v.reserveRate(), v.generation(), v.delivered(), lamps,
                v.outlook());
    }
}
