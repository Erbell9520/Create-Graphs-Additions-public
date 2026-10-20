package com.createstats.powertrack;

import com.createstats.blockentity.TrackFeederBE;
import com.createstats.display.StatsLinkSource;
import com.createstats.power.FeRates;
import com.createstats.powertrack.core.FeederPlan;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

// say held/shed instead of "0 FE/t", or someone goes looking for a train
public class FeederDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof TrackFeederBE feeder)) {
            return EMPTY;
        }
        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());
        final FeederPlan.Mode mode = feeder.mode();
        lines.add(Component.literal(TrackFeederBE.glyph(mode) + " ")
                .append(Component.translatable(TrackFeederBE.modeKey(mode)))
                .append(" " + FeRates.perTick(feeder.rate())));
        if (lines.size() < maxRows) {
            lines.add(Component.literal(doing(feeder)));
        }
        if (lines.size() < maxRows && feeder.reason() != FeederPlan.Reason.NO_RAIL) {
            final SectionView v = feeder.sectionView();
            lines.add(Component.literal("Section " + switch (v.state()) {
                case SectionView.LIVE -> "live";
                case SectionView.COLLECTING -> "collecting";
                case SectionView.IDLE -> "fed, idle";
                default -> "dead";
            } + ", " + v.cars() + (v.cars() == 1 ? " car" : " cars")));
        }
        return lines;
    }

    private static String doing(TrackFeederBE feeder) {
        final int flow = feeder.railFlow();
        return switch (feeder.reason()) {
            case READY -> flow > 0 ? "Feeding " + FeRates.perTick(flow)
                    : flow < 0 ? "Collecting " + FeRates.perTick(-flow) : "Ready, no train";
            case SWITCHED_OFF -> "Off";
            case NO_RAIL -> "Not on Power Track";
            case FLOOR_HELD -> "Held at " + feeder.floorPct() + "%";
            case STATION_HELD -> "Held: board short";
            case WRONG_TERMINAL -> "Wrong terminal";
        };
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
        return "track_feeder";
    }
}
