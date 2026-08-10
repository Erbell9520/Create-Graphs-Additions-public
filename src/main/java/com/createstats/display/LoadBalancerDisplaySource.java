package com.createstats.display;

import com.createstats.blockentity.LoadBalancerBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class LoadBalancerDisplaySource extends DisplaySource implements StatsLinkSource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof LoadBalancerBE balancer)) {
            return EMPTY;
        }

        List<MutableComponent> lines = new ArrayList<>();
        int maxRows = Math.max(1, stats.maxRows());

        Component state = switch (balancer.getTopologyStatus()) {
            case NO_SOURCE -> Component.translatable("create_stats.load_balancer.display.state.idle");
            case NO_BRANCHES -> Component.translatable("create_stats.load_balancer.display.state.no_branches");
            case SIDE_INPUT -> Component.translatable("create_stats.load_balancer.display.state.wrong_input");
            case MERGED_BRANCHES -> Component.translatable("create_stats.load_balancer.display.state.merged");
            case BRANCH_SOURCE -> Component.translatable("create_stats.load_balancer.display.state.branch_source");
            case TRUNCATED -> Component.translatable("create_stats.load_balancer.display.state.topology_limit");
            case OK -> !balancer.getSettings().isEnabled()
                    ? Component.translatable("create_stats.load_balancer.display.state.disabled")
                    : balancer.isBalancing()
                        ? Component.translatable("create_stats.load_balancer.display.state.balancing")
                        : Component.translatable("create_stats.load_balancer.display.state.pass_through");
        };
        lines.add(Component.translatable("create_stats.load_balancer.display.line", state));

        if (maxRows >= 2) {
            lines.add(Component.translatable("create_stats.load_balancer.display.split",
                    String.format("%.0f", balancer.getSettings().getLeftSharePct()),
                    String.format("%.0f", balancer.getSettings().getRightSharePct()),
                    balancer.getSettings().getSharingMode().getDisplayName()));
        }

        if (maxRows >= 3) {
            lines.add(Component.translatable("create_stats.load_balancer.display.lr",
                    String.format("%.0f", balancer.getTransmittedSpeedLeft()),
                    String.format("%.0f", balancer.getTransmittedSpeedRight()),
                    String.format("%.0f", balancer.getLastLeftStressSU()),
                    String.format("%.0f", balancer.getLastRightStressSU())));
        }

        if (maxRows >= 4) {
            lines.add(Component.translatable("create_stats.load_balancer.display.load",
                    String.format("%.1f", balancer.getLastStressPercent()),
                    String.format("%.0f", balancer.getLastLeftBudgetSU()),
                    String.format("%.0f", balancer.getLastRightBudgetSU())));
        }

        return lines;
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
