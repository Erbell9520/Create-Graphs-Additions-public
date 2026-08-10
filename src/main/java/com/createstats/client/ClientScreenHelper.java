package com.createstats.client;

import com.createstats.blockentity.CalculatorBE;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.TableAlarmClockBE;
import com.createstats.gui.DispatchBoardScreen;
import com.createstats.gui.FreightManifestClipboardScreen;
import com.createstats.gui.TableAlarmClockScreen;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import com.createstats.gui.AlarmSettingsScreen;
import com.createstats.gui.BattleScreenDisplayScreen;
import com.createstats.gui.DamageObserverScreen;
import com.createstats.gui.FactoryManualScreen;
import com.createstats.blockentity.StatsAlarmBE;
import com.createstats.gui.StatusTowerLightScreen;
import com.createstats.gui.HolographicProjectorSettingsScreen;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.blockentity.LoadBalancerBE;
import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.gui.AutoTransmissionClutchScreen;
import com.createstats.gui.LoadBalancerScreen;
import com.createstats.gui.MiniDisplayScreen;
import com.createstats.gui.PilotHelmetScreen;
import com.createstats.gui.SafetyClutchScreen;
import com.createstats.gui.SettingsScreen;
import com.createstats.gui.StatsHandheldScreen;
import com.createstats.renderer.ProductionDetectorTextureManager;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientScreenHelper
{
    private ClientScreenHelper() {
    }
    
    public static void openSettings(StatsDisplayPanelBE panel) {
        panel = resolveController(panel);
        Minecraft.getInstance().setScreen((Screen)new SettingsScreen(panel));
    }
    
    public static void openStatsDisplay(StatsDisplayPanelBE panel) {
        panel = resolveController(panel);
        Minecraft.getInstance().setScreen((Screen)new StatsDisplayScreen(panel));
    }

    public static void openStatsDisplayHome(StatsDisplayPanelBE panel) {
        panel = resolveController(panel);
        StatsDisplayScreen.showHomeOnNextOpen();
        Minecraft.getInstance().setScreen((Screen)new StatsDisplayScreen(panel));
    }

    public static void openStatsDisplayMk2(StatsDisplayPanelBE panel) {
        panel = resolveController(panel);
        Minecraft.getInstance().setScreen((Screen)new StatsDisplayMk2Screen(panel));
    }
    
    public static void openProductionDetector(ProductionDetectorBE detector) {
        Minecraft.getInstance().setScreen((Screen)new ProductionDetectorScreen(detector));
    }

    public static void cleanupProductionDetectorTexture(BlockPos pos) {
        ProductionDetectorTextureManager.cleanup(pos);
    }
    
    public static void openProjectorSettings(HolographicProjectorBE projector) {
        Minecraft.getInstance().setScreen((Screen)new HolographicProjectorSettingsScreen(projector));
    }
    
    public static void openAlarmSettings(StatsAlarmBE alarm) {
        Minecraft.getInstance().setScreen((Screen)new AlarmSettingsScreen(alarm));
    }

    public static void openStatusTowerLight(StatusTowerLightBE towerLight) {
        Minecraft.getInstance().setScreen((Screen)new StatusTowerLightScreen(towerLight));
    }

    public static void openPowerDistributionUnit(com.createstats.blockentity.PowerDistributionUnitBE pdu) {
        Minecraft.getInstance().setScreen((Screen)new com.createstats.gui.PowerDistributionUnitScreen(pdu));
    }

    public static void openStageLight(com.createstats.blockentity.StageLightBE stageLight) {
        Minecraft.getInstance().setScreen((Screen)new com.createstats.gui.StageLightScreen(stageLight));
    }

    public static void openTableAlarmClock(TableAlarmClockBE clock) {
        Minecraft.getInstance().setScreen((Screen)new TableAlarmClockScreen(clock));
    }

    public static void openMiniDisplay(MiniDisplayBE miniDisplay) {
        openMiniDisplay(miniDisplay, null);
    }

    public static void openMiniDisplay(MiniDisplayBE miniDisplay, Screen returnScreen) {
        miniDisplay = miniDisplay.getControllerBE();
        Minecraft.getInstance().setScreen((Screen)new MiniDisplayScreen(miniDisplay, returnScreen));
    }

    public static void openBattleScreenDisplay(BattleScreenDisplayBE display) {
        Minecraft.getInstance().setScreen((Screen)new BattleScreenDisplayScreen(display));
    }

    public static void openDamageObserver(DamageObserverBE observer) {
        Minecraft.getInstance().setScreen((Screen)new DamageObserverScreen(observer));
    }
    
    public static void openCalculator(CalculatorBE calculator) {
        calculator = resolveController(calculator);
        Minecraft.getInstance().setScreen((Screen)new CalculatorScreen(calculator));
    }
    
    public static void openSafetyClutch(SafetyClutchBE clutch) {
        openSafetyClutch(clutch, null);
    }

    public static void openSafetyClutch(SafetyClutchBE clutch, Screen returnScreen) {
        Minecraft.getInstance().setScreen((Screen)new SafetyClutchScreen(clutch, returnScreen));
    }

    public static void openAutoTransmissionClutch(AutoTransmissionClutchBE transmission) {
        openAutoTransmissionClutch(transmission, null);
    }

    public static void openAutoTransmissionClutch(AutoTransmissionClutchBE transmission, Screen returnScreen) {
        Minecraft.getInstance().setScreen((Screen)new AutoTransmissionClutchScreen(transmission, returnScreen));
    }

    public static void openLoadBalancer(LoadBalancerBE balancer) {
        openLoadBalancer(balancer, null);
    }

    public static void openLoadBalancer(LoadBalancerBE balancer, Screen returnScreen) {
        Minecraft.getInstance().setScreen((Screen)new LoadBalancerScreen(balancer, returnScreen));
    }

    public static void openFreightManifestClipboard(BlockPos pos, FreightStationLink link, FreightManifest manifest,
                                                    FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
                                                    boolean canEdit) {
        Minecraft.getInstance().setScreen((Screen)new FreightManifestClipboardScreen(pos, link, manifest, cargoView, deliveryBoard, canEdit));
    }

    public static void openDispatchBoard(BlockPos pos, FreightStationLink stationLink, FreightManifest manifest,
                                         FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
                                         boolean canEdit) {
        Minecraft.getInstance().setScreen((Screen)new DispatchBoardScreen(pos, stationLink, manifest, cargoView, deliveryBoard, canEdit));
    }

    public static void openFactoryManual() {
        Minecraft.getInstance().setScreen((Screen)new FactoryManualScreen());
    }

    public static void openStatsHandheld(InteractionHand hand) {
        Minecraft.getInstance().setScreen((Screen)new StatsHandheldScreen(hand));
    }

    public static void openPilotHelmet(InteractionHand hand) {
        Minecraft.getInstance().setScreen((Screen)new PilotHelmetScreen(hand));
    }
    
    private static StatsDisplayPanelBE resolveController(StatsDisplayPanelBE panel) {
        return panel.getControllerBE();
    }
    
    private static CalculatorBE resolveController(CalculatorBE calculator) {
        return calculator.getControllerBE();
    }
    
}
