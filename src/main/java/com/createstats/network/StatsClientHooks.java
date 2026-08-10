package com.createstats.network;

import com.createstats.data.TimeSeriesStore;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightStationCargoView;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class StatsClientHooks {

    private static final String CLIENT_PACKET_HANDLER = "com.createstats.client.ClientPacketHandler";
    private static final String CLIENT_COG_HANDLER = "com.createstats.client.ClientCogHandler";

    private StatsClientHooks() {
    }

    public static void handleStatsUpdate(BlockPos pos, TimeSeriesStore store) {
        invoke(CLIENT_PACKET_HANDLER, "handleStatsUpdate",
            new Class<?>[]{BlockPos.class, TimeSeriesStore.class},
            pos, store);
    }

    public static void openCogDashboard(TimeSeriesStore store) {
        invoke(CLIENT_COG_HANDLER, "openDashboard",
            new Class<?>[]{TimeSeriesStore.class},
            store);
    }

    public static void handleHandheldState(HandheldStatePacket packet) {
        invoke(CLIENT_PACKET_HANDLER, "handleHandheldState",
            new Class<?>[]{HandheldStatePacket.class},
            packet);
    }

    public static void handleFreightManifestClipboardState(BlockPos pos, FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard) {
        invoke(CLIENT_PACKET_HANDLER, "handleFreightManifestClipboardState",
            new Class<?>[]{BlockPos.class, FreightStationCargoView.class, DeliveryOrderBoardView.class},
            pos, cargoView, deliveryBoard);
    }

    public static void handlePduHistory(BlockPos pduPos, int range, boolean full,
                                        short[] supply, short[] demand, byte[] shed) {
        invoke("com.createstats.client.PduHistoryClient", "receive",
            new Class<?>[]{BlockPos.class, int.class, boolean.class, short[].class, short[].class, byte[].class},
            pduPos, range, full, supply, demand, shed);
    }

    private static void invoke(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            invokeClient(className, methodName, parameterTypes, args);
        }
    }

    private static void invokeClient(String className, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> helperClass = Class.forName(className);
            Method method = helperClass.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke stats client hook " + methodName, e);
        }
    }
}
