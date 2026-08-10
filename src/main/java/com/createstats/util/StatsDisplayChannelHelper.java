package com.createstats.util;

import com.createstats.blockentity.StatsDisplayPanelBE;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public final class StatsDisplayChannelHelper {

    private StatsDisplayChannelHelper() {
    }

    @Nullable
    public static StatsDisplayPanelBE.DataChannel selectPrimaryChannel(StatsDisplayPanelBE panel) {
        String mainChannelId = panel.getSettings().getDashboardMainChannelId();
        if (!mainChannelId.isEmpty()) {
            StatsDisplayPanelBE.DataChannel mainChannel = panel.getChannel(mainChannelId);
            if (mainChannel != null && mainChannel.getStore().getSampleCount() > 0) {
                return mainChannel;
            }
        }

        StatsDisplayPanelBE.DataChannel preferredVisible = findPreferredSourceChannel(panel.getVisibleChannels(), panel.getSettings().getPreferredSourceId());
        if (preferredVisible != null) {
            return preferredVisible;
        }

        List<StatsDisplayPanelBE.DataChannel> visibleChannels = panel.getVisibleChannels();
        if (!visibleChannels.isEmpty()) {
            return visibleChannels.get(0);
        }

        StatsDisplayPanelBE.DataChannel preferredActive = findPreferredSourceChannel(panel.getActiveChannels(), panel.getSettings().getPreferredSourceId());
        if (preferredActive != null) {
            return preferredActive;
        }

        List<StatsDisplayPanelBE.DataChannel> activeChannels = panel.getActiveChannels();
        return activeChannels.isEmpty() ? null : activeChannels.get(0);
    }

    public static double getNearestMemberDistanceSqr(Player player, StatsDisplayPanelBE panel) {
        StatsDisplayPanelBE controller = panel.getControllerBE();
        double nearest = Double.MAX_VALUE;
        for (BlockPos memberPos : controller.getMultiblockMembers()) {
            nearest = Math.min(nearest, player.distanceToSqr(memberPos.getX() + 0.5, memberPos.getY() + 0.5, memberPos.getZ() + 0.5));
        }
        return nearest;
    }

    @Nullable
    private static StatsDisplayPanelBE.DataChannel findPreferredSourceChannel(List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId) {
        if (preferredSourceId == null || preferredSourceId.isEmpty()) {
            return null;
        }
        String prefix = "src_" + preferredSourceId + "_";
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (channel.getId().startsWith(prefix)) {
                return channel;
            }
        }
        return null;
    }
}
