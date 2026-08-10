package com.createstats.network;

import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class DispatchBoardClientHooks {

    private static final String CLIENT_SCREEN_HELPER = "com.createstats.client.ClientScreenHelper";
    private static final String CLIENT_PACKET_HANDLER = "com.createstats.client.ClientPacketHandler";

    private DispatchBoardClientHooks() {
    }

    public static void openDispatchBoard(BlockPos pos, FreightStationLink stationLink, FreightManifest manifest,
                                         FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard, boolean canEdit) {
        invoke(CLIENT_SCREEN_HELPER, "openDispatchBoard",
            new Class<?>[]{BlockPos.class, FreightStationLink.class, FreightManifest.class, FreightStationCargoView.class, DeliveryOrderBoardView.class, boolean.class},
            pos, stationLink, manifest, cargoView, deliveryBoard, canEdit);
    }

    public static void handleDispatchBoardState(BlockPos pos, FreightManifest manifest, FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard) {
        invoke(CLIENT_PACKET_HANDLER, "handleDispatchBoardState",
            new Class<?>[]{BlockPos.class, FreightManifest.class, FreightStationCargoView.class, DeliveryOrderBoardView.class},
            pos, manifest, cargoView, deliveryBoard);
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
            throw new IllegalStateException("Failed to invoke dispatch board client hook " + methodName, e);
        }
    }
}
