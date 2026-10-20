package com.createstats.display;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.alarm.annunciator.AnnunciatorState;
import com.createstats.alarm.annunciator.RuleStatus;
import com.createstats.alarm.annunciator.WatchRule;
import com.createstats.blockentity.AnnunciatorBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class AnnunciatorDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof AnnunciatorBE device)) {
            return EMPTY;
        }
        final int maxRows = Math.max(1, stats.maxRows());
        final List<MutableComponent> lines = new ArrayList<>();
        lines.add(summaryLine(device));

        final List<WatchRule> rules = device.getRules();
        final List<Entry> outstanding = new ArrayList<>();
        for (WatchRule rule : rules) {
            final RuleStatus status = device.statusOf(rule);
            if (status.isOutstanding()) {
                outstanding.add(new Entry(rule, status));
            }
        }
        outstanding.sort((a, b) -> {
            final int bySeverity = Integer.compare(b.status().effectiveSeverity().ordinal(),
                    a.status().effectiveSeverity().ordinal());
            return bySeverity != 0 ? bySeverity : Long.compare(b.status().sinceTick(), a.status().sinceTick());
        });

        for (Entry entry : outstanding) {
            if (lines.size() >= maxRows) {
                break;
            }
            lines.add(ruleLine(entry));
        }

        if (lines.size() < maxRows && device.getFaultCount() > 0
                && device.getDeviceState() != AnnunciatorState.NORMAL) {
            lines.add(Component.translatable("create_stats.annunciator.line.faults",
                    device.getFaultCount()).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    private record Entry(WatchRule rule, RuleStatus status) {
    }

    private static MutableComponent summaryLine(AnnunciatorBE device) {
        final AnnunciatorState state = device.getDeviceState();
        final ChatFormatting colour = colourFor(device.getSeverity(), state);
        if (state == AnnunciatorState.NORMAL) {
            if (device.getRules().isEmpty()) {
                return Component.translatable("create_stats.annunciator.line.no_rules")
                        .withStyle(colour);
            }

            if (device.getFaultCount() > 0) {
                return Component.translatable("create_stats.annunciator.line.faults",
                        device.getFaultCount()).withStyle(ChatFormatting.GOLD);
            }
            return Component.translatable("create_stats.annunciator.line.normal").withStyle(colour);
        }
        final String label = device.getCulpritLabel();
        if (label.isEmpty()) {
            return Component.translatable("create_stats.annunciator.line.state."
                    + state.getSerializedName()).withStyle(colour);
        }
        return Component.translatable("create_stats.annunciator.line.summary",
                Component.translatable("create_stats.annunciator.line.state."
                        + state.getSerializedName()),
                label, device.getCulpritValue()).withStyle(colour);
    }

    private static MutableComponent ruleLine(Entry entry) {
        return Component.translatable("create_stats.annunciator.line.rule",
                        entry.rule().getLabel(),
                        entry.status().available()
                                ? entry.rule().formatValue(entry.status().value())
                                : Component.translatable("create_stats.annunciator.line.no_data").getString())
                .withStyle(colourFor(entry.status().effectiveSeverity(), entry.status().state()));
    }

    private static ChatFormatting colourFor(AlarmSeverity severity, AnnunciatorState state) {
        if (state == AnnunciatorState.RINGBACK) {
            return ChatFormatting.LIGHT_PURPLE;
        }
        return switch (severity) {
            case NORMAL -> ChatFormatting.GREEN;
            case NOTICE -> ChatFormatting.AQUA;
            case WARNING -> ChatFormatting.GOLD;
            case CRITICAL -> ChatFormatting.RED;
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
        return "annunciator_status";
    }
}
