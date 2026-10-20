package com.createstats.gui.battery;

import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeBandSettings;
import com.createstats.power.FeBoardDeal;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveBand;
import com.createstats.power.FeReserveReport;
import com.createstats.power.ReserveProjection;

import java.util.List;

public record BatteryView(int stored, int capacity, int generation, int delivered, int bankDrawn,
                          int bankCharged, int reserveRate, int chargeClaim, FeReserveReport.Stance stance,
                          int floorPct, int ceilingPct, int healthyPct, int configuredFloorPct, int holdPct,
                          int holdFe, int sustainedDraw, int criticalDraw, int claimPct, int storeCount,
                          int storeStored, int storeCapacity, int storeCharged, int storeAsk, int storeBudget,
                          boolean upstreamHolds, List<Input> inputs, List<Output> outputs,
                          ReserveProjection.Result outlook, int bandWant, FeBandSettings band, boolean bandCustom,
                          int fullRate) {
    public static final int DEFAULT_FULL_RATE = 65_536 * 3;

    public BatteryView {
        stance = stance == null ? FeReserveReport.Stance.CONSERVE : stance;
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        outlook = outlook == null ? ReserveProjection.Result.NONE : outlook;
        bandWant = Math.max(0, bandWant);
        band = band == null ? FeBandSettings.server(FeReserveBand.Band.DEFAULT, FeReserveBand.Minutes.DEFAULT) : band;
        fullRate = fullRate <= 0 ? DEFAULT_FULL_RATE : fullRate;
    }

    public BatteryView(int stored, int capacity, int generation, int delivered, int bankDrawn,
                       int bankCharged, int reserveRate, int chargeClaim, FeReserveReport.Stance stance,
                       int floorPct, int ceilingPct, int healthyPct, int configuredFloorPct, int holdPct,
                       int holdFe, int sustainedDraw, int criticalDraw, int claimPct, int storeCount,
                       int storeStored, int storeCapacity, int storeCharged, int storeAsk, int storeBudget,
                       boolean upstreamHolds, List<Input> inputs, List<Output> outputs,
                       ReserveProjection.Result outlook, int bandWant) {
        this(stored, capacity, generation, delivered, bankDrawn, bankCharged, reserveRate, chargeClaim, stance,
                floorPct, ceilingPct, healthyPct, configuredFloorPct, holdPct, holdFe, sustainedDraw, criticalDraw,
                claimPct, storeCount, storeStored, storeCapacity, storeCharged, storeAsk, storeBudget, upstreamHolds,
                inputs, outputs, outlook, bandWant, null, false, 0);
    }

    public BatteryView(int stored, int capacity, int generation, int delivered, int bankDrawn,
                       int bankCharged, int reserveRate, int chargeClaim, FeReserveReport.Stance stance,
                       int floorPct, int ceilingPct, int healthyPct, int configuredFloorPct, int holdPct,
                       int holdFe, int sustainedDraw, int criticalDraw, int claimPct, int storeCount,
                       int storeStored, int storeCapacity, int storeCharged, int storeAsk, int storeBudget,
                       boolean upstreamHolds, List<Input> inputs, List<Output> outputs,
                       ReserveProjection.Result outlook) {
        this(stored, capacity, generation, delivered, bankDrawn, bankCharged, reserveRate, chargeClaim, stance,
                floorPct, ceilingPct, healthyPct, configuredFloorPct, holdPct, holdFe, sustainedDraw, criticalDraw,
                claimPct, storeCount, storeStored, storeCapacity, storeCharged, storeAsk, storeBudget, upstreamHolds,
                inputs, outputs, outlook, 0);
    }

    public int floorMinutes() {
        if (bandWant <= 0 || capacity <= 0) {
            return -1;
        }
        final long floorFe = (long) capacity * configuredFloorPct / 100L;
        return (int) Math.min(Integer.MAX_VALUE, floorFe / ((long) bandWant * 1_200L));
    }

    public record Input(int index, boolean enabled, int flow, int tanks, int sources, int stores, int storeFlow,
                        boolean strayPanel, int bankMove) {
        public boolean wired() {
            return tanks + sources + stores > 0 || flow > 0 || strayPanel;
        }
    }

    public record Output(int index, boolean wired, boolean enabled, BatteryOutputMode mode, int limit,
                         int floorPct, FePriority tier, BatteryControllerPlan.Reason reason, int flow, int demand,
                         int ceiling, int devices, int panels, int critical, int boardAsked, int boardGranted,
                         FeBoardDeal.Reason boardReason, int stores, int storeFlow) {
        public Output {
            mode = mode == null ? BatteryOutputMode.DIRECT : mode;
            tier = tier == null ? FePriority.NORMAL : tier;
            reason = reason == null ? BatteryControllerPlan.Reason.IDLE : reason;
            boardReason = boardReason == null ? FeBoardDeal.Reason.GRANTED : boardReason;
        }

        // a board is funded at its wire, quoting that as demand puts a phantom 32k on every output
        public int asked() {
            return panels > 0 && boardAsked > 0 ? Math.max(boardAsked, 0) : Math.max(0, demand);
        }

        public boolean isShort() {
            return enabled && wired && switch (reason) {
                case PARTIAL, SHED, RECHARGE, NO_SURPLUS, FLOOR, RATIONED -> true;
                default -> panels > 0 && boardGranted < boardAsked;
            };
        }
    }

    public int socPct() {
        return capacity <= 0 ? 0 : (int) Math.max(0, Math.min(100, (long) stored * 100L / capacity));
    }

    public int asked() {
        int total = 0;
        for (Output o : outputs) {
            if (o.wired() && o.enabled()) {
                total = com.createstats.power.FeRates.plus(total, o.asked());
            }
        }
        return total;
    }

    public boolean anyStrayInput() {
        for (Input i : inputs) {
            if (i.strayPanel()) {
                return true;
            }
        }
        return false;
    }
}
