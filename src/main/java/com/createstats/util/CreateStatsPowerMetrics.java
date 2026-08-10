package com.createstats.util;

import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.blockentity.KineticDynamoBE;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.data.MetricType;
import com.createstats.power.FeConsumer;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.IFeConsumer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** [1] */
public final class CreateStatsPowerMetrics {

    private CreateStatsPowerMetrics() {
    }

    /** True when this block entity is one of ours and metrics were written. */
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
        if (sourceBE instanceof IFeConsumer consumer) {
            putConsumer(metrics, consumer.feConsumer());
            return true;
        }
        return false;
    }

    /** How the readout should be labelled. Grids and machines are not "energy storage". */
    public static InstrumentSourceHelper.SourceKind kindFor(@Nullable BlockEntity sourceBE) {
        if (sourceBE instanceof PowerConnectorBE) {
            return InstrumentSourceHelper.SourceKind.POWER_GRID;
        }
        return InstrumentSourceHelper.SourceKind.ENERGY_STORAGE;
    }

    // power Connector, reported as the whole grid

    private static void putGrid(Map<String, InstrumentSourceHelper.MetricSample> metrics, PowerConnectorBE connector) {
        final FeWireNetwork network = connector.network();
        final FeGridSolver.Result result = network != null ? network.lastResult() : FeGridSolver.Result.EMPTY;
        final int nodes = network != null ? network.size() : 1;
        final float fillPct = result.capacity() > 0 ? result.stored() * 100.0f / result.capacity() : 0.0f;

        // Names shared with the Create Crafts and Additions wire vocabulary, so existing displays
        // pick these up with no changes.
        put(metrics, "network_buffer", "Grid Buffer", result.stored(), MetricType.ENERGY_FE);
        put(metrics, "network_pulled", "Grid In", result.collected(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "network_pushed", "Grid Out", result.delivered(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "network_demand", "Grid Demand", result.demand(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "wire_nodes", "Grid Nodes", nodes, MetricType.ITEM_COUNT);
        put(metrics, "connector_count", "Wires", connector.links().size(), MetricType.ITEM_COUNT);

        // Generic aliases so a grid graphs like any other energy source.
        put(metrics, "energy_stored", "Stored FE", result.stored(), MetricType.ENERGY_FE);
        put(metrics, "energy_capacity", "Capacity FE", result.capacity(), MetricType.ENERGY_FE);
        put(metrics, "energy_fill_pct", "Energy %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "fill_pct", "Fill %", fillPct, MetricType.FILL_LEVEL);
        put(metrics, "energy_input_rate", "Input FE/t", result.collected(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_output_rate", "Output FE/t", result.delivered(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_net_rate", "Net FE/t", result.net(), MetricType.ENERGY_FE_PER_TICK);

        // The two that only exist because the solve measures them.
        put(metrics, "energy_shortfall", "Shortfall FE/t", result.shortfall(), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "grid_shed", "Shed Devices", result.shed(), MetricType.ITEM_COUNT);
        putEndurance(metrics, result.enduranceTicks());
    }

    // capacitor Bank, reported as the whole pool

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

        // Measured throughput, so the specialized page has something live to draw rather than a
        // static fill number. Positive charging, negative discharging.
        final int rate = bank.chargeRatePerTick();
        put(metrics, "energy_net_rate", "Net FE/t", rate, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_input_rate", "Input FE/t", Math.max(0, rate), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_output_rate", "Output FE/t", Math.max(0, -rate), MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "bank_layers", "Stack Layers",
                bank.structureMax().getY() - bank.structureMin().getY() + 1, MetricType.ITEM_COUNT);
        putEndurance(metrics, bank.enduranceTicks() < 0 ? Integer.MAX_VALUE : bank.enduranceTicks());
    }

    // kinetic Dynamo, exact generation instead of a differenced guess

    private static void putDynamo(Map<String, InstrumentSourceHelper.MetricSample> metrics, KineticDynamoBE dynamo) {
        final int generated = dynamo.lastGeneratedFePerTick();
        put(metrics, "energy_output_rate", "Generating FE/t", generated, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_net_rate", "Net FE/t", generated, MetricType.ENERGY_FE_PER_TICK);
        put(metrics, "energy_stored", "Buffer FE", dynamo.storedEnergy(), MetricType.ENERGY_FE);
        put(metrics, "rpm", "RPM", Math.abs(dynamo.getSpeed()), MetricType.RPM);
        put(metrics, "running", "Running", generated > 0 ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        put(metrics, "stress_su", "Stress (SU)", dynamo.calculateStressApplied() * Math.abs(dynamo.getSpeed()),
                MetricType.STRESS_SU);
    }

    // any FE consumer, exact draw and real power state

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
        // Measured, not inferred. This is the reason this class exists.
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

    // shared

    /** [2] */
    private static void putEndurance(Map<String, InstrumentSourceHelper.MetricSample> metrics, int ticks) {
        final int reported = ticks == Integer.MAX_VALUE ? 0 : Math.max(0, ticks);
        put(metrics, "energy_endurance", "Endurance", reported, MetricType.ENDURANCE_TICKS);
    }

    private static void put(Map<String, InstrumentSourceHelper.MetricSample> metrics,
                            String id, String label, float value, MetricType type) {
        metrics.put(id, new InstrumentSourceHelper.MetricSample(id, label, value, type));
    }
}
