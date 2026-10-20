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
import com.createstats.train.manifest.StationTrainRoster;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import com.createstats.gui.AlarmSettingsScreen;
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

    public static void openStatsDisplayMk3(StatsDisplayPanelBE panel) {
        panel = resolveController(panel);
        Minecraft.getInstance().setScreen((Screen)new com.createstats.statsmk3.view.Mk3StudioScreen(panel));
    }

    public static void openProductionDetector(ProductionDetectorBE detector) {
        Minecraft.getInstance().setScreen((Screen)new ProductionDetectorScreen(detector));
    }

    public static void cleanupProductionDetectorTexture(BlockPos pos) {
        ProductionDetectorTextureManager.cleanup(pos);
    }

    public static void openHoloMk3(com.createstats.holomk3.HoloProjectorMk3BE projector) {
        Minecraft.getInstance().setScreen(
                (Screen) new com.createstats.holomk3.gui.HoloMk3Screen(projector));
    }

    public static void openProjectorSettings(HolographicProjectorBE projector) {
        Minecraft.getInstance().setScreen((Screen)new HolographicProjectorSettingsScreen(projector));
    }

    public static void openAlarmSettings(StatsAlarmBE alarm) {
        Minecraft.getInstance().setScreen((Screen)new AlarmSettingsScreen(alarm));
    }

    public static void openAnnunciator(com.createstats.blockentity.AnnunciatorBE annunciator) {
        Minecraft.getInstance().setScreen(new com.createstats.gui.AnnunciatorScreen(annunciator));
    }

    public static void openStatusTowerLight(StatusTowerLightBE towerLight) {
        Minecraft.getInstance().setScreen((Screen)new StatusTowerLightScreen(towerLight));
    }

    public static void openPowerDistributionUnit(com.createstats.blockentity.PowerDistributionUnitBE pdu) {
        Minecraft.getInstance().setScreen((Screen)new com.createstats.gui.PowerDistributionUnitScreen(pdu));
    }

    public static void openPowerMeter(com.createstats.blockentity.PowerMeterBE meter) {
        Minecraft.getInstance().setScreen((Screen)new com.createstats.gui.PowerMeterScreen(meter));
    }

    public static void openPowerLimiter(com.createstats.blockentity.PowerLimiterBE limiter) {
        Minecraft.getInstance().setScreen((Screen)new com.createstats.gui.PowerLimiterScreen(limiter));
    }

    public static void openSmartHomePanel(com.createstats.blockentity.SmartHomePanelBE panel) {
        Minecraft.getInstance().setScreen(
                (Screen) new com.createstats.gui.SmartHomePanelScreen(panel));
    }

    public static void openWeatherPanel(com.createstats.blockentity.WeatherPanelBE panel) {
        Minecraft.getInstance().setScreen(
                (Screen) new com.createstats.gui.WeatherPanelScreen(panel));
    }

    public static void acceptSmartHomeRoster(com.createstats.network.SmartHomeSyncPacket packet) {
        if (Minecraft.getInstance().screen instanceof com.createstats.gui.SmartHomePanelScreen screen) {
            screen.accept(packet);
        }
    }

    public static void showSmartHomeToast(com.createstats.network.SmartHomeToastPacket packet) {
        Minecraft.getInstance().getToasts().addToast(
                new com.createstats.gui.home.HomeToast(packet.icon(), packet.title(), packet.body()));
    }

    public static void openHomeSecurityPanel(com.createstats.blockentity.HomeSecurityPanelBE panel) {
        Minecraft.getInstance().setScreen(
                (Screen) new com.createstats.gui.HomeSecurityPanelScreen(panel));
    }

    public static void openSecurityPanel(com.createstats.network.OpenSecurityPanelPacket packet) {
        final Minecraft mc = Minecraft.getInstance();

        if (packet.isShut()) {
            final boolean mine = (mc.screen instanceof com.createstats.gui.HomeSecurityPanelScreen open
                    && open.target().equals(packet.pos()))
                    || (mc.screen instanceof com.createstats.gui.PanelKeypadScreen pad
                    && pad.target().equals(packet.pos()));
            if (mine) {
                mc.setScreen(null);
            }
            return;
        }
        if (packet.keypad()) {
            // AWAY+ is a different screen, match the class too

            if (packet.isSeal()) {
                if (mc.screen instanceof com.createstats.gui.SealKeypadScreen open
                        && open.target().equals(packet.pos())) {
                    open.accept(packet);
                    return;
                }
                final com.createstats.gui.SealKeypadScreen seal =
                        new com.createstats.gui.SealKeypadScreen(packet.pos());
                seal.accept(packet);
                mc.setScreen((Screen) seal);
                return;
            }

            if (mc.screen instanceof com.createstats.gui.PanelKeypadScreen open
                    && !(mc.screen instanceof com.createstats.gui.SealKeypadScreen)
                    && open.target().equals(packet.pos())) {
                open.accept(packet);
                return;
            }
            final com.createstats.gui.PanelKeypadScreen keypad =
                    new com.createstats.gui.PanelKeypadScreen(packet.pos());
            keypad.accept(packet);
            mc.setScreen((Screen) keypad);
            return;
        }

        if (mc.screen instanceof com.createstats.gui.HomeSecurityPanelScreen) {
            return;
        }
        if (mc.level != null && mc.level.getBlockEntity(packet.pos())
                instanceof com.createstats.blockentity.HomeSecurityPanelBE panel) {
            mc.setScreen((Screen) new com.createstats.gui.HomeSecurityPanelScreen(panel));
        }
    }

    public static void acceptSecurityRelay(com.createstats.network.SecurityRelaySyncPacket packet) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.createstats.gui.SecurityRelayScreen open && open.pos().equals(packet.pos())) {
            if (packet.purpose() == com.createstats.network.SecurityRelaySyncPacket.CLOSE) {
                mc.setScreen(null);
            } else {
                open.accept(packet);
            }
            return;
        }
        if (packet.purpose() == com.createstats.network.SecurityRelaySyncPacket.OPEN) {
            mc.setScreen((Screen) new com.createstats.gui.SecurityRelayScreen(packet));
        }
    }

    public static void acceptSecurityRoster(com.createstats.network.SecuritySyncPacket packet) {
        if (Minecraft.getInstance().screen instanceof com.createstats.gui.HomeSecurityPanelScreen screen) {
            screen.accept(packet);
        }
    }

    public static void openLockerKeypad(com.createstats.network.OpenLockerKeypadPacket packet) {
        if (Minecraft.getInstance().screen instanceof com.createstats.gui.LockerKeypadScreen open
                && open.target().equals(packet.pos())) {
            open.accept(packet);
            return;
        }
        final com.createstats.gui.LockerKeypadScreen screen =
                new com.createstats.gui.LockerKeypadScreen(packet.pos());
        screen.accept(packet);
        Minecraft.getInstance().setScreen((Screen) screen);
    }

    public static void openLockerMode(com.createstats.network.OpenLockerModePacket packet) {
        Minecraft.getInstance().setScreen((Screen) new com.createstats.gui.LockerModeScreen(
                packet.pos(), packet.modeOf()));
    }

    public static void openBatteryController(com.createstats.blockentity.BatteryControllerBE controller) {
        Minecraft.getInstance().setScreen((Screen)new com.createstats.gui.BatteryControllerScreen(controller));
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
        Minecraft.getInstance().setScreen(new com.createstats.battle.client.BattleBridgeScreen(display));
    }

    public static void openDamageObserver(DamageObserverBE observer) {
        Minecraft.getInstance().setScreen(new com.createstats.battle.client.SectionMarkerScreen(observer));
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

    public static void openTopologyScanner(com.createstats.netscan.blockentity.NetscanInlineBE scanner) {
        Minecraft.getInstance().setScreen(
                new com.createstats.netscan.client.NetscanScreen(scanner));
    }

    public static void openTopologyScannerProbe(com.createstats.netscan.blockentity.NetscanProbeBE scanner) {
        Minecraft.getInstance().setScreen(
                new com.createstats.netscan.client.NetscanScreen(scanner));
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
                                         StationTrainRoster stationRoster, boolean canEdit) {
        Minecraft.getInstance().setScreen((Screen)new DispatchBoardScreen(pos, stationLink, manifest,
                cargoView, deliveryBoard, stationRoster, canEdit));
    }

    public static void openStudentsManual(String id) {
        Minecraft.getInstance().setScreen(new com.createstats.book.screen.StudentsManualScreen(id));
    }

    public static void openSupportTerminal() {
        Minecraft.getInstance().setScreen((Screen)new com.createstats.support.SupportTerminalScreen());
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
