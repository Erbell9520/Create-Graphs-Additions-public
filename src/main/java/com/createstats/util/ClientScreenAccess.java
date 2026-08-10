package com.createstats.util;

import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.CalculatorBE;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.LoadBalancerBE;
import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.blockentity.StatsAlarmBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.blockentity.TableAlarmClockBE;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class ClientScreenAccess {

    private static final String CLIENT_SCREEN_HELPER = "com.createstats.client.ClientScreenHelper";

    private ClientScreenAccess() {
    }

    public static void openSettings(StatsDisplayPanelBE panel) {
        invoke("openSettings", new Class<?>[]{StatsDisplayPanelBE.class}, panel);
    }

    public static void openStatsDisplay(StatsDisplayPanelBE panel) {
        invoke("openStatsDisplay", new Class<?>[]{StatsDisplayPanelBE.class}, panel);
    }

    public static void openStatsDisplayMk2(StatsDisplayPanelBE panel) {
        invoke("openStatsDisplayMk2", new Class<?>[]{StatsDisplayPanelBE.class}, panel);
    }

    public static void openProductionDetector(ProductionDetectorBE detector) {
        invoke("openProductionDetector", new Class<?>[]{ProductionDetectorBE.class}, detector);
    }

    public static void cleanupProductionDetectorTexture(BlockPos pos) {
        invoke("cleanupProductionDetectorTexture", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openProjectorSettings(HolographicProjectorBE projector) {
        invoke("openProjectorSettings", new Class<?>[]{HolographicProjectorBE.class}, projector);
    }

    public static void openAlarmSettings(StatsAlarmBE alarm) {
        invoke("openAlarmSettings", new Class<?>[]{StatsAlarmBE.class}, alarm);
    }

    public static void openStatusTowerLight(StatusTowerLightBE towerLight) {
        invoke("openStatusTowerLight", new Class<?>[]{StatusTowerLightBE.class}, towerLight);
    }

    public static void openPowerDistributionUnit(com.createstats.blockentity.PowerDistributionUnitBE pdu) {
        invoke("openPowerDistributionUnit", new Class<?>[]{com.createstats.blockentity.PowerDistributionUnitBE.class}, pdu);
    }

    public static void openStageLight(com.createstats.blockentity.StageLightBE stageLight) {
        invoke("openStageLight", new Class<?>[]{com.createstats.blockentity.StageLightBE.class}, stageLight);
    }

    public static void openTableAlarmClock(TableAlarmClockBE clock) {
        invoke("openTableAlarmClock", new Class<?>[]{TableAlarmClockBE.class}, clock);
    }

    public static void openMiniDisplay(MiniDisplayBE miniDisplay) {
        invoke("openMiniDisplay", new Class<?>[]{MiniDisplayBE.class}, miniDisplay);
    }

    public static void openBattleScreenDisplay(BattleScreenDisplayBE display) {
        invoke("openBattleScreenDisplay", new Class<?>[]{BattleScreenDisplayBE.class}, display);
    }

    public static void openDamageObserver(DamageObserverBE observer) {
        invoke("openDamageObserver", new Class<?>[]{DamageObserverBE.class}, observer);
    }

    public static void openCalculator(CalculatorBE calculator) {
        invoke("openCalculator", new Class<?>[]{CalculatorBE.class}, calculator);
    }

    public static void openSafetyClutch(SafetyClutchBE clutch) {
        invoke("openSafetyClutch", new Class<?>[]{SafetyClutchBE.class}, clutch);
    }

    public static void openAutoTransmissionClutch(AutoTransmissionClutchBE transmission) {
        invoke("openAutoTransmissionClutch", new Class<?>[]{AutoTransmissionClutchBE.class}, transmission);
    }

    public static void openLoadBalancer(LoadBalancerBE balancer) {
        invoke("openLoadBalancer", new Class<?>[]{LoadBalancerBE.class}, balancer);
    }

    public static void openFactoryManual() {
        invoke("openFactoryManual", new Class<?>[0]);
    }

    public static void openStatsHandheld(InteractionHand hand) {
        invoke("openStatsHandheld", new Class<?>[]{InteractionHand.class}, hand);
    }

    public static void openPilotHelmet(InteractionHand hand) {
        invoke("openPilotHelmet", new Class<?>[]{InteractionHand.class}, hand);
    }

    public static void openFreightManifestClipboard(BlockPos pos, FreightStationLink stationLink, FreightManifest manifest,
                                                    FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
                                                    boolean canEdit) {
        invoke("openFreightManifestClipboard",
                new Class<?>[]{BlockPos.class, FreightStationLink.class, FreightManifest.class,
                        FreightStationCargoView.class, DeliveryOrderBoardView.class, boolean.class},
                pos, stationLink, manifest, cargoView, deliveryBoard, canEdit);
    }

    public static void openDispatchBoard(BlockPos pos, FreightStationLink stationLink, FreightManifest manifest,
                                         FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
                                         boolean canEdit) {
        invoke("openDispatchBoard",
                new Class<?>[]{BlockPos.class, FreightStationLink.class, FreightManifest.class,
                        FreightStationCargoView.class, DeliveryOrderBoardView.class, boolean.class},
                pos, stationLink, manifest, cargoView, deliveryBoard, canEdit);
    }

    private static void invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            invokeClient(methodName, parameterTypes, args);
        }
    }

    private static void invokeClient(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> helperClass = Class.forName(CLIENT_SCREEN_HELPER);
            Method method = helperClass.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to open client screen via " + methodName, e);
        }
    }
}
