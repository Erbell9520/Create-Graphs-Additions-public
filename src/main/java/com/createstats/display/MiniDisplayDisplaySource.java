package com.createstats.display;

import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.data.MetricType;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MiniDisplayDisplaySource extends DisplaySource implements StatsLinkSource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof MiniDisplayBE sourceDisplay)) {
            return EMPTY;
        }
        final MiniDisplayBE miniDisplay = sourceDisplay.getControllerBE();

        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());

        this.addLine(lines, maxRows, this.buildStateLine(miniDisplay));
        if (lines.size() >= maxRows) {
            return lines;
        }

        switch (miniDisplay.getDisplayState()) {
            case NO_SOURCE -> {
                this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.mode", miniDisplay.getSettings().getSourceMode().getDisplayName()));
                this.addLine(lines, maxRows, Component.translatable(miniDisplay.getSettings().getSourceMode() == com.createstats.blockentity.MiniDisplaySettings.SourceMode.LOCAL
                        ? "create_stats.mini_display.disp.hint.local"
                        : "create_stats.mini_display.disp.hint.link"));
            }
            case UNSUPPORTED -> {
                this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.source", safeSourceType(miniDisplay)));
                this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.hint.unsupported"));
            }
            case LINK_LOST -> {
                this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.source", safeSourceType(miniDisplay)));
                this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.hint.waiting"));
            }
            case LIVE, LINK_STALE -> this.addLiveMetrics(lines, maxRows, miniDisplay);
        }

        return lines;
    }

    private void addLiveMetrics(List<MutableComponent> lines, int maxRows, MiniDisplayBE miniDisplay) {
        switch (miniDisplay.getActiveSourceKind()) {
            case ITEM_STORAGE -> this.addItemLines(lines, maxRows, miniDisplay);
            case FLUID_STORAGE, BOILER -> this.addFluidLines(lines, maxRows, miniDisplay);
            case ENERGY_STORAGE -> this.addEnergyLines(lines, maxRows, miniDisplay);
            case KINETIC_STRESS -> this.addKineticStressLines(lines, maxRows, miniDisplay);
            case KINETIC_SPEED -> this.addKineticSpeedLines(lines, maxRows, miniDisplay);
            case NO_SOURCE, UNKNOWN -> this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.source", safeSourceType(miniDisplay)));
        }
    }

    private void addItemLines(List<MutableComponent> lines, int maxRows, MiniDisplayBE miniDisplay) {
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.fill", String.format(Locale.ROOT, "%.1f", miniDisplay.getActiveFillPercent())));
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.total_items", String.format(Locale.ROOT, "%,.0f", miniDisplay.getActiveTotalItems())));
        if (miniDisplay.hasFirstDetailRow()) {
            this.addLine(lines, maxRows, this.formatTrackedCount(Component.translatable("create_stats.mini_display.disp.tracked_a"), miniDisplay.getFirstDetailValue(), miniDisplay.getFirstDetailLabel()));
        }
        if (miniDisplay.hasSecondDetailRow()) {
            this.addLine(lines, maxRows, this.formatTrackedCount(Component.translatable("create_stats.mini_display.disp.tracked_b"), miniDisplay.getSecondDetailValue(), miniDisplay.getSecondDetailLabel()));
        }
        if (!miniDisplay.hasFirstDetailRow()) {
            this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.view", miniDisplay.getSettings().getStorageDetailMode().getDisplayName()));
        }
    }

    private void addFluidLines(List<MutableComponent> lines, int maxRows, MiniDisplayBE miniDisplay) {
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.fill", String.format(Locale.ROOT, "%.1f", miniDisplay.getActiveFillPercent())));
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.fluid_amount", formatWhole(miniDisplay.getActiveFluidAmount())));
        if (miniDisplay.hasFirstDetailRow()) {
            final Component label = normalizeFluidDetailLabel(miniDisplay.getFirstDetailLabel());
            final String value = String.format(Locale.ROOT, "%.1f", miniDisplay.getFirstDetailValue());
            this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.detail_pct", label, value));
        }
        if (miniDisplay.getActiveFluidCapacity() > 0.0f) {
            this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.capacity_mb", formatWhole(miniDisplay.getActiveFluidCapacity())));
        }
    }

    private void addEnergyLines(List<MutableComponent> lines, int maxRows, MiniDisplayBE miniDisplay) {
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.charge", String.format(Locale.ROOT, "%.1f", miniDisplay.getActiveFillPercent())));
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.stored_fe", MetricType.ENERGY_FE.formatValue(miniDisplay.getActiveFluidAmount())));
        if (miniDisplay.getActiveFluidCapacity() > 0.0f) {
            this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.capacity", MetricType.ENERGY_FE.formatValue(miniDisplay.getActiveFluidCapacity())));
        }
        if (miniDisplay.hasFirstDetailRow()) {
            this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.net", MetricType.ENERGY_FE_PER_TICK.formatValue(miniDisplay.getFirstDetailValue())));
        }
        if (miniDisplay.hasSecondDetailRow()) {
            this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.detail_value", miniDisplay.getSecondDetailLabel(), formatWhole(miniDisplay.getSecondDetailValue())));
        }
    }

    private void addKineticStressLines(List<MutableComponent> lines, int maxRows, MiniDisplayBE miniDisplay) {
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.stress", String.format(Locale.ROOT, "%.1f", miniDisplay.getActiveStressPercent())));
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.used_su", formatWhole(miniDisplay.getActiveStressUsed())));
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.capacity_su", formatWhole(miniDisplay.getActiveStressCapacity())));
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.rpm", formatWhole(Math.abs(miniDisplay.getActiveRpm()))));
    }

    private void addKineticSpeedLines(List<MutableComponent> lines, int maxRows, MiniDisplayBE miniDisplay) {
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.rpm", formatWhole(Math.abs(miniDisplay.getActiveRpm()))));
        this.addLine(lines, maxRows, Component.translatable("create_stats.mini_display.disp.source", safeSourceType(miniDisplay)));
    }

    private MutableComponent buildStateLine(MiniDisplayBE miniDisplay) {
        final String mode = miniDisplay.getSettings().getSourceMode().getDisplayName().toUpperCase(Locale.ROOT);
        return switch (miniDisplay.getDisplayState()) {
            case LIVE -> Component.translatable("create_stats.mini_display.disp.state.live", sourceToken(miniDisplay), mode);
            case LINK_STALE -> Component.translatable("create_stats.mini_display.disp.state.stale", sourceToken(miniDisplay));
            case LINK_LOST -> Component.translatable("create_stats.mini_display.disp.state.link_lost");
            case NO_SOURCE -> Component.translatable("create_stats.mini_display.disp.state.no_source", mode);
            case UNSUPPORTED -> Component.translatable("create_stats.mini_display.disp.state.unsupported");
        };
    }

    private Component sourceToken(MiniDisplayBE miniDisplay) {
        return Component.translatable(switch (miniDisplay.getActiveSourceKind()) {
            case ITEM_STORAGE -> "create_stats.mini_display.disp.token.item";
            case FLUID_STORAGE -> "create_stats.mini_display.disp.token.fluid";
            case ENERGY_STORAGE -> "create_stats.mini_display.disp.token.energy";
            case POWER_GRID -> "create_stats.mini_display.disp.token.grid";
            case BOILER -> "create_stats.mini_display.disp.token.boiler";
            case KINETIC_STRESS -> "create_stats.mini_display.disp.token.kinetic";
            case KINETIC_SPEED -> "create_stats.mini_display.disp.token.speed";
            case NO_SOURCE, UNKNOWN -> "create_stats.mini_display.disp.token.readout";
        });
    }

    private String safeSourceType(MiniDisplayBE miniDisplay) {
        final String sourceType = miniDisplay.getActiveSourceType();
        return sourceType == null || sourceType.isBlank()
                ? Component.translatable("block.create_stats.mini_display").getString()
                : sourceType;
    }

    private MutableComponent formatTrackedCount(Component label, float count, String itemLabel) {
        final String trimmed = itemLabel == null || itemLabel.isBlank()
                ? Component.translatable("create_stats.mini_display.disp.tracked_item").getString()
                : itemLabel;
        return Component.translatable("create_stats.mini_display.disp.tracked_count", label, formatWhole(count), trimmed);
    }

    private static Component normalizeFluidDetailLabel(String label) {
        if (label == null || label.isBlank()) {
            return Component.translatable("create_stats.mini_display.disp.detail");
        }
        if (label.toLowerCase(Locale.ROOT).contains("heat")) {
            return Component.translatable("create_stats.mini_display.disp.heat");
        }
        return Component.translatable("create_stats.mini_display.disp.steam_fill");
    }

    private static String formatWhole(float value) {
        return String.format(Locale.ROOT, "%,.0f", value);
    }

    private void addLine(List<MutableComponent> lines, int maxRows, MutableComponent line) {
        if (lines.size() < maxRows) {
            lines.add(line);
        }
    }

    @Override
    protected String getTranslationKey() {
        return "stats_link";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }
}
