package com.createstats.util;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.blockentity.KineticDynamoBE;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.data.MetricType;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeConsumer;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveReport;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.IFeConsumer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class CreateStatsPowerMetrics {
    private CreateStatsPowerMetrics() {
    }

    public static boolean collect(@Nullable BlockEntity sourceBE, Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        if (sourceBE instanceof PowerConnectorBE connector) {
            putGrid(metrics, connector);
            return true;
        }
        if (sourceBE instanceof CapacitorBankBE bank) {
            putBank(metrics, bank);
            return true;
        }
        if (sourceBE instanceof KineticDynamoBE dynamo) {
            putDynamo(metrics, dynamo);
            return true;
        }
        if (sourceBE instanceof com.createstats.blockentity.FeMotorBE motor) {
            putMotor(metrics, motor);
            return true;
        }

        if (sourceBE instanceof PowerDistributionUnitBE pdu) {
            putPdu(metrics, pdu);
            return true;
        }
        if (sourceBE instanceof BatteryControllerBE controller) {
            putController(metrics, controller);
            return true;
        }

        if (sourceBE instanceof com.createstats.blockentity.PowerMeterBE meter) {
            putMeter(metrics, meter);
            return true;
        }
        if (sourceBE instanceof IFeConsumer consumer) {
            putConsumer(metrics, consumer.feConsumer());
            return true;
        }
        return false;
    }

    public static InstrumentSourceHelper.SourceKind kindFor(@Nullable BlockEntity sourceBE) {
        if (sourceBE instanceof PowerConnectorBE
                || sourceBE instanceof PowerDistributionUnitBE
                || sourceBE instanceof BatteryControllerBE
                || sourceBE instanceof com.createstats.blockentity.PowerMeterBE) {
            return InstrumentSourceHelper.SourceKind.POWER_GRID;
        }

        if (sourceBE instanceof com.createstats.blockentity.FeMotorBE) {
            return InstrumentSourceHelper.SourceKind.KINETIC_STRESS;
        }
        return InstrumentSourceHelper.SourceKind.ENERGY_STORAGE;
    }

    private static void putGrid(Map<String, InstrumentSourceHelper.MetricSample> metrics, PowerConnectorBE connector) {
        final FeWireNetwork network = connector.network();
        final FeGridSolver.Result result = network != null ? network.lastResult() : FeGridSolver.Result.EMPTY;
        final int nodes = network != null ? network.size() : 1;
        final float fillPct = result.capacity() > 0 ? result.stored() * 100.0f / result.capacity() : 0.0f;

        put(metrics, "network_buffer", "Grid Buffer", result.stored(), MetricType.ENERGY_FE);
        put(metrics, "network_pulled", "Grid In", result.collected(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "network_pushed", "Grid Out", result.delivered(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "network_demand", "Grid Demand", result.demand(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "wire_nodes", "Grid Nodes", nodes, MetricType.ITEM_COUNT);
        put(metrics, "connector_count", "Wires", connector.links().size(), MetricType.ITEM_COUNT);

        put(metrics, "energy_stored", "Stored FE", result.stored(), MetricType.ENERGY_FE);
        put(metrics, "energy_capacity", "Capacity FE", result.capacity(), MetricType.ENERGY_FE);
        put(metrics, "energy_fill_pct", "Energy %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "fill_pct", "Fill %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "energy_input_rate", "Input FE/t", result.collected(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_output_rate", "Output FE/t", result.delivered(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_net_rate", "Net FE/t", result.net(), MetricType.ENERGY_FE_PER_TICK);

        put(metrics, "energy_shortfall", "Shortfall FE/t", result.shortfall(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "grid_shed", "Shed Devices", result.shed(), MetricType.ITEM_COUNT);
        putEndurance(metrics, result.enduranceTicks());
    }

    private static void putBank(Map<String, InstrumentSourceHelper.MetricSample> metrics, CapacitorBankBE bank) {
        final int stored = bank.groupEnergy();
        final int capacity = bank.groupCapacity();
        final float fillPct = capacity > 0 ? stored * 100.0f / capacity : 0.0f;
        put(metrics, "energy_stored", "Stored FE", stored, MetricType.ENERGY_FE);
        put(metrics, "energy_capacity", "Capacity FE", capacity, MetricType.ENERGY_FE);
        put(metrics, "energy_fill_pct", "Energy %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "fill_pct", "Fill %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "network_buffer", "Bank Charge", stored, MetricType.ENERGY_FE);
        put(metrics, "bank_blocks", "Pooled Blocks", bank.groupSize(), MetricType.ITEM_COUNT);

        final int in = bank.chargeFePerTick();
        final int out = bank.dischargeFePerTick();
        put(metrics, "energy_net_rate", "Net FE/t", in - out, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_input_rate", "Charging FE/t", in, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_output_rate", "Discharging FE/t", out, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "bank_layers", "Stack Layers",
                bank.structureMax().getY() - bank.structureMin().getY() + 1, MetricType.ITEM_COUNT);

        putEndurance(metrics, bank.runtimeTicks() < 0 ? Integer.MAX_VALUE : bank.runtimeTicks());
    }

    private static void putDynamo(Map<String, InstrumentSourceHelper.MetricSample> metrics, KineticDynamoBE dynamo) {
        final int generated = dynamo.lastGeneratedFePerTick();
        final int potential = dynamo.potentialFePerTick();
        put(metrics, "energy_output_rate", "Generating FE/t", generated, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_net_rate", "Net FE/t", generated, MetricType.ENERGY_FE_PER_TICK);

        put(metrics, "energy_capacity_rate", "Capable FE/t", potential, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_stored", "Buffer FE", dynamo.storedEnergy(), MetricType.ENERGY_FE);
        put(metrics, "rpm", "RPM", Math.abs(dynamo.getSpeed()), MetricType.RPM);

        put(metrics, "running", "Running", potential > 0 ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        put(metrics, "stress_load_su", "Shaft Load (SU)",
                dynamo.calculateStressApplied() * Math.abs(dynamo.getSpeed()), MetricType.STRESS_SU);
    }

    private static void putMotor(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                 com.createstats.blockentity.FeMotorBE motor) {
        final int draw = motor.measuredDrawFePerTick();
        final int rated = motor.tierMaxDrawFePerTick();
        final int stored = motor.storedEnergy();
        final int capacity = motor.bufferCapacity();

        put(metrics, "energy_input_rate", "Draw FE/t", draw, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_draw_rate", "Draw FE/t", draw, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_capacity_rate", "Rated FE/t", rated, MetricType.ENERGY_FE_PER_TICK);

        put(metrics, "energy_output_rate", "Output FE/t", 0, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_net_rate", "Net FE/t", -draw, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_stored", "Buffer FE", stored, MetricType.ENERGY_FE);
        put(metrics, "energy_capacity", "Buffer Capacity", capacity, MetricType.ENERGY_FE);
        put(metrics, "energy_fill_pct", "Buffer %",
                capacity > 0 ? stored * 100.0f / capacity : 0.0f, MetricType.FILL_LEVEL);
        put(metrics, "running", "Running", draw > 0 ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        put(metrics, "fe_shed", "Shed", motor.isManagedShed() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
    }

    private static void putPdu(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                               PowerDistributionUnitBE pdu) {
        final FeGridSolver.BranchResult branch = pdu.lastBranchResult();
        final int stored = pdu.storedEnergy();
        final int capacity = pdu.capacity();
        final float fillPct = capacity > 0 ? stored * 100.0f / capacity : 0.0f;
        final int input = pdu.measuredInputFePerTick();

        put(metrics, "energy_stored", "Buffer FE", stored, MetricType.ENERGY_FE);
        put(metrics, "energy_capacity", "Buffer Capacity", capacity, MetricType.ENERGY_FE);
        put(metrics, "energy_fill_pct", "Buffer %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "fill_pct", "Fill %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "energy_input_rate", "Input FE/t", input, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_output_rate", "Delivered FE/t", branch.delivered(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_net_rate", "Net FE/t", input - branch.delivered(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_endurance", "Endurance",
                pdu.autoEnduranceTicks() == Integer.MAX_VALUE ? 0 : Math.max(0, pdu.autoEnduranceTicks()),
                MetricType.ENDURANCE_TICKS);

        put(metrics, "pdu_generation", "Upstream Generation", pdu.measuredGeneration(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "pdu_demand", "Branch Demand", pdu.branchDemandFePerTick(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "pdu_served_demand", "Branch Running", pdu.servedDemandFePerTick(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "pdu_budget", "Budget FE/t", pdu.budgetFePerTick(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "pdu_ceiling", "Wiring Ceiling", pdu.branchCeilingFePerTick(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "pdu_devices", "Managed Devices", pdu.roster().size(), MetricType.ITEM_COUNT);
        put(metrics, "pdu_served_devices", "Devices Served", branch.served(), MetricType.ITEM_COUNT);
        put(metrics, "pdu_shed_devices", "Devices Shed", branch.shed(), MetricType.ITEM_COUNT);
        put(metrics, "pdu_mode", "Mode", pdu.mode().ordinal(), MetricType.GENERIC);
        put(metrics, "pdu_surplus", "Surplus", pdu.isSurplus() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        put(metrics, "pdu_throughput_limited", "Wiring Limited",
                pdu.isThroughputLimited() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        int wiredMask = 0;
        for (int leg = 0; leg < PowerDistributionUnitBE.LEGS; leg++) {
            final boolean wired = pdu.isLegWired(leg);

            if (!wired && !pdu.legHasChannels(leg)) {
                continue;
            }
            if (wired) {
                wiredMask |= 1 << leg;
            }
            final String name = leg == PowerDistributionUnitBE.BACK_LEG ? "Back" : "Leg " + (leg + 1);
            put(metrics, "pdu_leg" + leg + "_demand", name + " Demand",
                    wired ? pdu.legShownDemand(leg) : 0, MetricType.ENERGY_FE_PER_TICK);
            put(metrics, "pdu_leg" + leg + "_served", name + " Carrying",
                    wired ? pdu.legShownAdmitted(leg) : 0, MetricType.ENERGY_FE_PER_TICK);
            put(metrics, "pdu_leg" + leg + "_ceiling", name + " Rating",
                    wired ? pdu.legRating(leg) : 0, MetricType.ENERGY_FE_PER_TICK);
            put(metrics, "pdu_leg" + leg + "_state", name + " State",
                    wired ? legState(pdu, leg) : LEG_OPEN, MetricType.GENERIC);
            put(metrics, "pdu_leg" + leg + "_devices", name + " Devices",
                    wired ? pdu.legDeviceCount(leg) : 0, MetricType.ITEM_COUNT);
        }
        put(metrics, "pdu_legs_wired", "Wired Legs", wiredMask, MetricType.GENERIC);

        final PowerDistributionUnitBE.TierRollCall roll = pdu.tierRollCall();
        for (FePriority tier : FePriority.values()) {
            final int i = tier.ordinal();
            put(metrics, "pdu_tier" + i + "_total", tier.getDisplayName() + " Devices",
                    i < roll.total().length ? roll.total()[i] : 0, MetricType.ITEM_COUNT);
            put(metrics, "pdu_tier" + i + "_served", tier.getDisplayName() + " Running",
                    i < roll.served().length ? roll.served()[i] : 0, MetricType.ITEM_COUNT);
        }

        put(metrics, "pdu_reserve_linked", "Reserve Linked", pdu.isReserveLinked() ? 1.0f : 0.0f,
                MetricType.BOOLEAN_FLAG);
        if (pdu.isReserveLinked()) {
            final FeReserveReport.Stance stance = pdu.linkedStance();
            put(metrics, "pdu_reserve_pct", "Reserve %", Math.max(0, pdu.linkedReserveSocPct()),
                    MetricType.FILL_LEVEL);
            put(metrics, "pdu_reserve_stance", "Reserve Stance",
                    stance == null ? 0 : stance.ordinal(), MetricType.GENERIC);
            put(metrics, "pdu_reserve_allowance", "Reserve Allowance", pdu.linkedSustainedDraw(),
                    MetricType.ENERGY_FE_PER_TICK);
        }
        put(metrics, "pdu_upstream_sources", "Upstream Sources", pdu.upstreamSourceCount(),
                MetricType.ITEM_COUNT);
        put(metrics, "pdu_reserve_tanks", "Reserve Tanks", pdu.reserveTankCount(), MetricType.ITEM_COUNT);
        put(metrics, "pdu_reserve_depth", "Tanks Holding", pdu.reserveDepth(), MetricType.ITEM_COUNT);
        put(metrics, "pdu_core_protected", "Core Protected", pdu.isCoreProtected() ? 1.0f : 0.0f,
                MetricType.BOOLEAN_FLAG);
    }

    private static float legState(PowerDistributionUnitBE pdu, int leg) {
        if (pdu.isLegTripped(leg)) {
            return LEG_TRIPPED;
        }
        if (!pdu.isLegClosed(leg)) {
            return LEG_OPEN;
        }
        if (pdu.isLegOverCapacity(leg)) {
            return LEG_OVER_CAPACITY;
        }
        return LEG_OK;
    }

    public static final float LEG_OK = 0.0f;
    public static final float LEG_OVER_CAPACITY = 1.0f;
    public static final float LEG_OPEN = 2.0f;
    public static final float LEG_TRIPPED = 3.0f;

    private static void putController(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                      BatteryControllerBE controller) {
        final int reserve = controller.reserveEnergy();
        final int reserveCap = controller.reserveCapacity();
        final float fillPct = reserveCap > 0 ? reserve * 100.0f / reserveCap : 0.0f;
        final int rate = controller.reserveRateFePerTick();

        put(metrics, "energy_stored", "Reserve FE", reserve, MetricType.ENERGY_FE);
        put(metrics, "energy_capacity", "Reserve Capacity", reserveCap, MetricType.ENERGY_FE);
        put(metrics, "energy_fill_pct", "Reserve %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "fill_pct", "Fill %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "energy_net_rate", "Reserve FE/t", rate, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_input_rate", "Input FE/t", controller.generationFePerTick(),
                MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_output_rate", "Delivered FE/t", controller.deliveredFePerTick(),
                MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "network_buffer", "Reserve FE", reserve, MetricType.ENERGY_FE);

        putEndurance(metrics, rate < 0 ? reserve / -rate : Integer.MAX_VALUE);

        put(metrics, "bank_bus", "Bus FE", controller.busEnergy(), MetricType.ENERGY_FE);
        put(metrics, "bank_reserve_pct", "Reserve %", controller.reservePct(), MetricType.FILL_LEVEL);
        final FeReserveReport report = controller.reserveState();
        put(metrics, "bank_stance", "Stance",
                report == null || report.stance() == null ? 0 : report.stance().ordinal(),
                MetricType.GENERIC);
        put(metrics, "bank_managed", "Panels Downstream",
                controller.hasManagedOutput() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            put(metrics, "bank_in" + i + "_flow", "Input " + (char) ('A' + i) + " FE/t",
                    controller.inputFlow(i), MetricType.ENERGY_FE_PER_TICK);
            put(metrics, "bank_in" + i + "_tanks", "Input " + (char) ('A' + i) + " Banks",
                    controller.inputTankCount(i), MetricType.ITEM_COUNT);
            put(metrics, "bank_in" + i + "_sources", "Input " + (char) ('A' + i) + " Sources",
                    controller.inputSourceCount(i), MetricType.ITEM_COUNT);
        }
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final String name = "Output " + (i + 1);
            final BatteryControllerPlan.Reason reason = controller.outputReason(i);
            final BatteryOutputMode mode = controller.outputMode(i);
            final FePriority priority = controller.outputPriority(i);
            put(metrics, "bank_out" + i + "_flow", name + " FE/t", controller.outputFlow(i),
                    MetricType.ENERGY_FE_PER_TICK);
            put(metrics, "bank_out" + i + "_demand", name + " Demand", controller.outputDemand(i),
                    MetricType.ENERGY_FE_PER_TICK);
            put(metrics, "bank_out" + i + "_ceiling", name + " Rating", controller.outputCeiling(i),
                    MetricType.ENERGY_FE_PER_TICK);
            put(metrics, "bank_out" + i + "_floor", name + " Floor", controller.outputFloorPct(i),
                    MetricType.FILL_LEVEL);
            put(metrics, "bank_out" + i + "_reason", name + " State",
                    reason == null ? 0 : reason.ordinal(), MetricType.GENERIC);
            put(metrics, "bank_out" + i + "_mode", name + " Mode",
                    mode == null ? 0 : mode.ordinal(), MetricType.GENERIC);
            put(metrics, "bank_out" + i + "_tier", name + " Tier",
                    priority == null ? 0 : priority.ordinal(), MetricType.GENERIC);
            put(metrics, "bank_out" + i + "_devices", name + " Devices",
                    controller.outputDeviceCount(i), MetricType.ITEM_COUNT);
            put(metrics, "bank_out" + i + "_panels", name + " Panels",
                    controller.outputPanelCount(i), MetricType.ITEM_COUNT);
            put(metrics, "bank_out" + i + "_critical", name + " Critical",
                    controller.outputCriticalDemand(i), MetricType.ENERGY_FE_PER_TICK);
        }
    }

    private static void putConsumer(Map<String, InstrumentSourceHelper.MetricSample> metrics, FeConsumer fe) {
        final int stored = fe.getEnergyStored();
        final int capacity = fe.getMaxEnergyStored();
        final int draw = fe.measuredDrainFePerTick();
        final int input = fe.measuredInputFePerTick();
        final float fillPct = fe.fillPct();

        put(metrics, "energy_stored", "Stored FE", stored, MetricType.ENERGY_FE);
        put(metrics, "energy_capacity", "Capacity FE", capacity, MetricType.ENERGY_FE);
        put(metrics, "energy_fill_pct", "Energy %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "fill_pct", "Fill %", fillPct, MetricType.FILL_LEVEL);

        put(metrics, "energy_draw_rate", "Draw FE/t", draw, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_output_rate", "Draw FE/t", draw, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_input_rate", "Input FE/t", input, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_net_rate", "Net FE/t", input - draw, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_load_multiplier", "Load x", fe.appliedLoadMultiplier(), MetricType.GENERIC);

        put(metrics, "fe_powered", "Powered", fe.isPowered() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        put(metrics, "running", "Running", fe.isPowered() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        put(metrics, "fe_brownout", "Brownout", fe.isBrownout() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        put(metrics, "fe_recharging", "Recharging", fe.isRecharging() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        final int net = input - draw;
        putEndurance(metrics, net >= 0 ? Integer.MAX_VALUE : stored / -net);
    }

    private static void putEndurance(Map<String, InstrumentSourceHelper.MetricSample> metrics, int ticks) {
        final int reported = ticks == Integer.MAX_VALUE ? 0 : Math.max(0, ticks);
        put(metrics, "energy_endurance", "Endurance", reported, MetricType.ENDURANCE_TICKS);
    }

    private static void putMeter(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                                 com.createstats.blockentity.PowerMeterBE meter) {
        final int rate = meter.getRateFePerTick();
        final com.createstats.blockentity.PowerMeterBE.Status state = meter.status();
        final boolean healthy = state == com.createstats.blockentity.PowerMeterBE.Status.FLOWING
                || state == com.createstats.blockentity.PowerMeterBE.Status.IDLE
                || state == com.createstats.blockentity.PowerMeterBE.Status.BACKED_UP;

        put(metrics, "meter_rate", "Measured FE/t", rate, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "meter_peak", "Peak FE/t", meter.getPeakFePerTick(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "meter_total", "Total FE", meter.getTotalFe(), MetricType.ENERGY_FE);
        put(metrics, "meter_flowing", "Flowing", rate > 0 ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        put(metrics, "meter_fault", "Fault", healthy ? 0.0f : 1.0f, MetricType.BOOLEAN_FLAG);
        put(metrics, "meter_connected", "Wired", healthy ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        put(metrics, "energy_input_rate", "Input FE/t", rate, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_output_rate", "Output FE/t", rate, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_net_rate", "Net FE/t", rate, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "network_demand", "Measured Demand", rate, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "running", "Flowing", rate > 0 ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);

        final int peak = meter.getPeakFePerTick();
        put(metrics, "fill_pct", "Load %", peak <= 0 ? 0.0f : Math.min(100.0f, rate * 100.0f / peak),
                MetricType.FILL_LEVEL);
    }

    private static void put(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                            String id, String label, float value, MetricType type) {
        metrics.put(id, new InstrumentSourceHelper.MetricSample(id, label, value, type));
    }
}
