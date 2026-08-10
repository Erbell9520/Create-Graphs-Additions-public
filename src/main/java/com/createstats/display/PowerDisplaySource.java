package com.createstats.display;

import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.blockentity.KineticDynamoBE;
import com.createstats.blockentity.PowerConnectorBE;
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

/** [1] */
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
        } else {
            return EMPTY;
        }
        return lines;
    }

    private void appendGrid(List<MutableComponent> lines, PowerConnectorBE connector, int maxRows) {
        final FeWireNetwork network = connector.network();
        final FeGridSolver.Result result = network != null ? network.lastResult() : FeGridSolver.Result.EMPTY;
        final float pct = result.capacity() > 0 ? result.stored() * 100.0f / result.capacity() : 0.0f;

        add(lines, maxRows, "create_stats.power.line.grid_buffer",
                compact(result.stored()), compact(result.capacity()), String.format(Locale.ROOT, "%.0f", pct));
        add(lines, maxRows, "create_stats.power.line.grid_flow",
                compact(result.collected()), compact(result.delivered()));
        // Shortfall is the early warning: demand the grid could not meet, visible before anything
        // has actually browned out.
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

    /** Renders endurance as mm:ss, or a dash when nothing is draining and the answer is "forever". */
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

    /** Compact FE formatting, so a 5-wide sign is not swallowed by a seven-digit number. */
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

    /** [2] */
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
