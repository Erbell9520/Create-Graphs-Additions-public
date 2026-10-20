package com.createstats.display;

import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.inventory.InventoryFace;
import com.createstats.inventory.core.EnergyReadout;
import com.createstats.inventory.core.FaceFormat;
import com.createstats.inventory.core.FlowClock;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductionDetectorDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final ProductionDetectorBE detector = resolve(context);
        if (detector == null) {
            return EMPTY;
        }
        final int max = Math.max(1, stats.maxRows());
        final List<MutableComponent> lines = new ArrayList<>();
        if (!detector.feConsumer().isPowered()) {
            lines.add(Component.translatable("create_stats.inventory_display.link.no_power"));
            return lines;
        }
        final InventoryFace f = detector.serverFace();
        if (f == null) {
            lines.add(Component.translatable("create_stats.inventory_display.link.nothing"));
            return lines;
        }
        switch (f.page) {
            case CONTAINER, VAULT -> {
                final long total = f.totalItems;
                final String used = Integer.toString(f.page == InventoryFace.Page.VAULT ? f.totalTypes : f.usedSlots);
                lines.add(Component.translatable("create_stats.inventory_display.link.items", f.title, FaceFormat.grouped(total), used,
                        Integer.toString(f.totalSlots)));
                if (f.gatherSeconds < 0) {
                    lines.add(Component.translatable("create_stats.inventory_display.link.flow", FaceFormat.wholeRate(f.inPerMinute),
                            FaceFormat.wholeRate(f.outPerMinute)));
                    lines.add(clockLine(f.clock, f.clockSeconds));
                }
            }
            case TANK, DIESEL -> lines.add(Component.translatable("create_stats.inventory_display.link.fluid", f.title,
                    FaceFormat.buckets(f.fluidAmount), FaceFormat.buckets(f.fluidCapacity)));
            case ENERGY -> {
                if (f.energyStored) {
                    lines.add(Component.translatable("create_stats.inventory_display.link.energy", f.title, FaceFormat.grouped(f.energy),
                            FaceFormat.grouped(f.energyCapacity)));

                    if (f.energyClock == EnergyReadout.Clock.QUOTED.ordinal() && f.clock == FlowClock.Kind.RUNTIME) {
                        lines.add(Component.translatable("create_stats.inventory_display.link.clock_runtime", clock(f.clockSeconds)));
                    } else if (f.energyClock != EnergyReadout.Clock.NONE.ordinal() && f.gatherSeconds < 0) {
                        lines.add(clockLine(f.clock, f.clockSeconds));
                    }
                } else {
                    lines.add(Component.literal(f.title.getString()));
                }
            }
            case BELT -> lines.add(Component.translatable("create_stats.inventory_display.link.belt", f.title,
                    FaceFormat.wholeRate(f.passPerMinute)));
            case WATCH_ONE, WATCH_MANY -> {
                for (InventoryFace.WatchRow r : f.rows) {
                    lines.add(Component.translatable("create_stats.inventory_display.link.watch", r.name(), FaceFormat.grouped(r.count()),
                            FaceFormat.grouped(r.low()), FaceFormat.grouped(r.high())));
                }
            }
            case LINK_TEXT -> {
                for (String s : f.lines) {
                    lines.add(Component.literal(s));
                }
            }
            default -> lines.add(Component.translatable("create_stats.inventory_display.link.nothing"));
        }
        return lines.size() <= max ? lines : new ArrayList<>(lines.subList(0, max));
    }

    private static MutableComponent clockLine(FlowClock.Kind kind, float seconds) {
        return switch (kind) {
            case FULL, HIGH -> Component.translatable("create_stats.inventory_display.link.clock_full", clock(seconds));
            case EMPTY, LOW, RUNTIME -> Component.translatable("create_stats.inventory_display.link.clock_empty", clock(seconds));
            default -> Component.translatable("create_stats.inventory_display.link.clock_steady");
        };
    }

    static MutableComponent clock(double seconds) {
        final long s = Math.max(0L, Math.round(seconds));
        if (s < 3600L) {
            return Component.literal(String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L));
        }
        if (s < 86_400L) {
            return Component.translatable("create_stats.inventory_display.gui.clock.hm", Long.toString(s / 3600L),
                    String.format(Locale.ROOT, "%02d", (s % 3600L) / 60L));
        }
        return Component.translatable("create_stats.inventory_display.gui.clock.days", Long.toString(s / 86_400L));
    }

    private static ProductionDetectorBE resolve(DisplayLinkContext context) {
        final Level level = context.level();
        BlockPos pos = context.getSourcePos();
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        final BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.PRODUCTION_DETECTOR.get()) && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
        }
        return level.getBlockEntity(pos) instanceof ProductionDetectorBE d ? d : null;
    }

    @Override
    protected String getTranslationKey() {
        return "inventory_display";
    }
}
