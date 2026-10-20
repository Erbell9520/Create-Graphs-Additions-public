package com.createstats.surveyor.client;

import com.createstats.vccmk4.client.PathwayBoardScreen;

import com.createstats.surveyor.network.EditHistoryPacket;
import com.createstats.surveyor.network.PathwayHealthPacket;
import com.createstats.surveyor.network.PathwayTerrainPacket;
import com.createstats.surveyor.network.PathwaySyncPacket;
import com.createstats.surveyor.network.RoutePickedPacket;
import com.createstats.surveyor.network.RouteTrackPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

// SurveyorWiringTest keeps this and the bridge in sync
public final class SurveyorClientHooks {
    private SurveyorClientHooks() {
    }

    public static void sync(PathwaySyncPacket packet) {
        PathwayClientCache.accept(packet);
        PathwayBoardScreen.refresh();
    }

    public static void health(PathwayHealthPacket packet) {
        PathwayClientCache.accept(packet);
        PathwayBoardScreen.refresh();
    }

    public static void terrain(PathwayTerrainPacket packet) {
        PathwayClientCache.accept(packet);
    }

    public static void history(EditHistoryPacket packet) {
        StaffInput.history(packet.undo(), packet.redo());
    }

    public static void tracks(RouteTrackPacket packet) {
        PathwayClientCache.accept(packet);
        PathwayBoardScreen.refresh();
    }

    public static void picked(RoutePickedPacket packet) {
        StaffInput.picked(packet);
    }

    public static void useOnBlock(UseOnContext ctx) {
        StaffInput.useOnBlock(ctx);
    }

    public static void useAir(Player player) {
        StaffInput.useAir(player);
    }
}
