package com.createstats.display;

import com.createstats.blockentity.InductionHeaterBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Display Link source for the Induction Heater. Streams the live heat tier, the thermal
 * charge ramp, the current SU draw and the installed coil's remaining life.
 */
public class InductionHeaterDisplaySource extends DisplaySource implements StatsLinkSource {

    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof InductionHeaterBE heater)) {
            return EMPTY;
        }
        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());
        lines.add(Component.translatable("create_stats.induction_heater.display.heat_line", prettyName(heater)));
        if (lines.size() < maxRows && heater.hasCookStack()) {
            lines.add(heater.isCookDone()
                ? Component.translatable("create_stats.induction_heater.display.ready",
                    heater.getCookStack().getCount(), heater.getCookStack().getHoverName())
                : Component.translatable("create_stats.induction_heater.display.cooking",
                    heater.getCookStack().getCount(), heater.getCookStack().getHoverName(),
                    String.format("%.0f", heater.getCookProgressPercent())));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.induction_heater.display.charge",
                String.format("%.0f", heater.getChargePercent())));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.induction_heater.display.draw",
                String.format("%.0f", heater.getCurrentDrawSu())));
        }
        if (lines.size() < maxRows) {
            lines.add(heater.hasCoil()
                ? Component.translatable("create_stats.induction_heater.display.coil",
                    String.format("%.0f", heater.getCoilHealthPercent()))
                : Component.translatable("create_stats.induction_heater.display.coil_none"));
        }
        return lines;
    }

    private static Component prettyName(InductionHeaterBE heater) {
        return Component.translatable(switch (heater.getHeat()) {
            case SMOULDERING -> "create_stats.induction_heater.display.heat.warm";
            case FADING, KINDLED -> "create_stats.induction_heater.display.heat.kindled";
            case SEETHING -> "create_stats.induction_heater.display.heat.superheated";
            default -> "create_stats.induction_heater.display.heat.idle";
        });
    }

    // Same rationale as the Flow Meter: keep refreshing on a steady cadence so an idle
    // heater is not pruned as a stale source by the Stats Display.
    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    public boolean shouldPassiveReset() {
        return true;
    }

    protected String getTranslationKey() {
        return "induction_heater";
    }
}
