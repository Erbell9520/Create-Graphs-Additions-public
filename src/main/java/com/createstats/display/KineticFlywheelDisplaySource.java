package com.createstats.display;

import com.createstats.blockentity.KineticFlywheelBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class KineticFlywheelDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof KineticFlywheelBE flywheel)) {
            return EMPTY;
        }

        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());

        final int mode = flywheel.getModeIndex();
        final boolean halted = flywheel.isOverstressHalted();
        final boolean lowReserve = flywheel.isLowReserve();

        Component stateStr;
        if (halted) {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.halted");
        } else if (mode == 2) {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.discharging");
        } else if (mode == 1) {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.charging");
        } else if (flywheel.isRedstoneHeld()) {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.held");
        } else if (flywheel.isParked()) {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.parked");
        } else if (flywheel.isDepleted()) {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.depleted");
        } else if (flywheel.isWaitingForSurplus()) {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.waiting");
        } else if (lowReserve) {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.low_reserve");
        } else {
            stateStr = Component.translatable("create_stats.kinetic_flywheel.display.state.idle");
        }
        lines.add(Component.translatable("create_stats.kinetic_flywheel.display.line", stateStr));

        if (maxRows >= 2) {
            lines.add(Component.translatable("create_stats.kinetic_flywheel.display.charge",
                    String.format("%.0f", flywheel.getChargePercent())));
        }

        if (maxRows >= 3) {
            lines.add(Component.translatable("create_stats.kinetic_flywheel.display.stored",
                    String.format("%.0f", Math.abs(flywheel.getOutputRpm()))));
        }

        if (maxRows >= 4) {
            lines.add(Component.translatable("create_stats.kinetic_flywheel.display.output",
                    String.format("%.0f", flywheel.getOutputStress())));
        }

        if (maxRows >= 5) {
            final int tier = flywheel.getTier();
            final int count = flywheel.getFlywheelCount();
            lines.add(Component.translatable("create_stats.kinetic_flywheel.display.tier", tier + 1, count));
        }

        return lines;
    }

    @Override
    protected String getTranslationKey() {
        return "kinetic_flywheel";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }
}
