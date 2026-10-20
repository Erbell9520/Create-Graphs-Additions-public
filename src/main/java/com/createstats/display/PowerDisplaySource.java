package com.createstats.display;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.blockentity.KineticDynamoBE;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeWireNetwork;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PowerDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        final int maxRows = Math.max(1, stats.maxRows());
        final List<MutableComponent> lines = new ArrayList<>();

        if (be instanceof PowerConnectorBE connector) {
            appendGrid(lines, connector, maxRows);
        } else if (be instanceof CapacitorBankBE bank) {
            appendBank(lines, bank, maxRows);
        } else if (be instanceof KineticDynamoBE dynamo) {
            appendDynamo(lines, dynamo, maxRows);
        } else if (be instanceof PowerDistributionUnitBE pdu) {
            appendPdu(lines, pdu, maxRows);
        } else if (be instanceof BatteryControllerBE controller) {
            appendController(lines, controller, maxRows);
        } else {
            return EMPTY;
        }
        return lines;
    }

    private void appendPdu(List<MutableComponent> lines, PowerDistributionUnitBE pdu, int maxRows) {
        final FeGridSolver.BranchResult branch = pdu.lastBranchResult();
        add(lines, maxRows, "create_stats.power.line.pdu_branch",
                String.valueOf(branch.served()), String.valueOf(branch.served() + branch.shed()));
        add(lines, maxRows, "create_stats.power.line.pdu_load",
                compact(pdu.servedDemandFePerTick()), compact(pdu.branchDemandFePerTick()));
        add(lines, maxRows, "create_stats.power.line.pdu_supply",
                compact(pdu.measuredGeneration()), pdu.mode().getDisplayName());
        if (pdu.isThroughputLimited()) {
            add(lines, maxRows, "create_stats.power.line.pdu_wiring", compact(pdu.branchCeilingFePerTick()));
        }
    }

    private void appendController(List<MutableComponent> lines, BatteryControllerBE controller, int maxRows) {
        add(lines, maxRows, "create_stats.power.line.bank_charge",
                compact(controller.reserveEnergy()), compact(controller.reserveCapacity()),
                String.valueOf(controller.reservePct()));
        add(lines, maxRows, "create_stats.power.line.grid_flow",
                compact(controller.generationFePerTick()), compact(controller.deliveredFePerTick()));
        final com.createstats.power.FeReserveReport report = controller.reserveState();
        if (report != null && report.stance() != null) {
            add(lines, maxRows, "create_stats.power.line.controller_stance", report.stance().label());
        }

        if (controller.reserveCapacity() > 0 && lines.size() < maxRows) {
            lines.add(Component.translatableWithFallback("create_stats.power.line.controller_outlook",
                    "Foreseen: %s", com.createstats.power.ReserveWords.controllerOutlook(controller.outlook(),
                            controller.reserveRateFePerTick())));
        }
    }

    private void appendGrid(List<MutableComponent> lines, PowerConnectorBE connector, int maxRows) {
        final FeWireNetwork network = connector.network();
        final FeGridSolver.Result result = network != null ? network.lastResult() : FeGridSolver.Result.EMPTY;
        final float pct = result.capacity() > 0 ? result.stored() * 100.0f / result.capacity() : 0.0f;

        add(lines, maxRows, "create_stats.power.line.grid_buffer",
                compact(result.stored()), compact(result.capacity()), String.format(Locale.ROOT, "%.0f", pct));
        add(lines, maxRows, "create_stats.power.line.grid_flow",
                compact(result.collected()), compact(result.delivered()));

        if (result.shortfall() > 0) {
            add(lines, maxRows, "create_stats.power.line.grid_shortfall", compact(result.shortfall()));
        } else {
            add(lines, maxRows, "create_stats.power.line.grid_demand", compact(result.demand()));
        }
        appendEndurance(lines, maxRows, result.enduranceTicks());
        add(lines, maxRows, "create_stats.power.line.grid_nodes",
                String.valueOf(network != null ? network.size() : 1));
    }

    private void appendBank(List<MutableComponent> lines, CapacitorBankBE bank, int maxRows) {
        final int stored = bank.groupEnergy();
        final int capacity = bank.groupCapacity();
        final float pct = capacity > 0 ? stored * 100.0f / capacity : 0.0f;
        add(lines, maxRows, "create_stats.power.line.bank_charge",
                compact(stored), compact(capacity), String.format(Locale.ROOT, "%.0f", pct));
        add(lines, maxRows, "create_stats.power.line.bank_blocks", String.valueOf(bank.groupSize()));
    }

    private void appendDynamo(List<MutableComponent> lines, KineticDynamoBE dynamo, int maxRows) {
        add(lines, maxRows, "create_stats.power.line.dynamo_output",
                compact(dynamo.lastGeneratedFePerTick()));
        add(lines, maxRows, "create_stats.power.line.dynamo_rpm",
                String.format(Locale.ROOT, "%.0f", Math.abs(dynamo.getSpeed())));
        add(lines, maxRows, "create_stats.power.line.dynamo_buffer", compact(dynamo.storedEnergy()));
    }

    private void appendEndurance(List<MutableComponent> lines, int maxRows, int ticks) {
        if (ticks == Integer.MAX_VALUE) {
            add(lines, maxRows, "create_stats.power.line.endurance_stable");
            return;
        }
        final int seconds = ticks / 20;
        add(lines, maxRows, "create_stats.power.line.endurance",
                String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
    }

    private static void add(List<MutableComponent> lines, int maxRows, String key, Object... args) {
        if (lines.size() < maxRows) {
            lines.add(Component.translatable(key, args));
        }
    }

    private static String compact(int value) {
        if (value >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return String.format(Locale.ROOT, "%.0fk", value / 1_000.0);
        }
        if (value >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    public boolean shouldPassiveReset() {
        return true;
    }

    @Override
    protected String getTranslationKey() {
        return "power_readout";
    }
}
