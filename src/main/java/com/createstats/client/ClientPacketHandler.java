package com.createstats.client;

import com.createstats.alarm.TableAlarmCalendarEvent;
import com.createstats.gui.DispatchBoardScreen;
import com.createstats.gui.FreightManifestClipboardScreen;
import com.createstats.gui.StatsHandheldScreen;
import com.createstats.gui.TableAlarmClockScreen;
import com.createstats.network.HandheldStatePacket;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.StationTrainRoster;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.client.Minecraft;
import com.createstats.data.TimeSeriesStore;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandler
{
    private ClientPacketHandler() {
    }

    public static void handleStatsUpdate(BlockPos pos, TimeSeriesStore store) {
        final Level level = (Level)Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StatsDisplayPanelBE panel) {
            final TimeSeriesStore clientStore = panel.getStore();
            final float[] data = store.getData();
            clientStore.clear();
            clientStore.setMetricType(store.getMetricType());
            for (float v : data) {
                clientStore.addSampleDirect(v);
            }
        }
    }

    public static void handleFreightManifestClipboardState(BlockPos pos, FreightManifest manifest,
            FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
            boolean saveReply, boolean saveAccepted) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof FreightManifestClipboardScreen screen) || !screen.isForClipboard(pos)) {
            return;
        }
        screen.updateLiveState(manifest, cargoView, deliveryBoard, saveReply, saveAccepted);
    }

    public static void handleDispatchBoardState(BlockPos pos, FreightManifest manifest,
                                                FreightStationCargoView cargoView,
                                                DeliveryOrderBoardView deliveryBoard,
                                                StationTrainRoster stationRoster) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof DispatchBoardScreen screen) || !screen.isForBoard(pos)) {
            return;
        }
        screen.updateLiveState(manifest, cargoView, deliveryBoard, stationRoster);
    }

    public static void handleTableAlarmCalendarData(List<TableAlarmCalendarEvent> events) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TableAlarmClockScreen screen) {
            screen.updateCalendarEvents(events);
        }
    }

    public static void handleHandheldState(HandheldStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof StatsHandheldScreen screen) {
            screen.updateRemoteState(packet);
        }
    }
}
