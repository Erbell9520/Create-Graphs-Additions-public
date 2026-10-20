package com.createstats.network;

import com.createstats.CreateStats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModPackets {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // bump this on every payload change. donkemonke rule applies

        PayloadRegistrar registrar = event.registrar(CreateStats.MOD_ID).versioned("63");

        registrar.playToClient(com.createstats.vccmk4.network.Vcc4SnapshotPacket.TYPE,
            com.createstats.vccmk4.network.Vcc4SnapshotPacket.STREAM_CODEC,
            com.createstats.vccmk4.network.Vcc4SnapshotPacket::handle);
        registrar.playToClient(com.createstats.vccmk4.network.Vcc4HelmPacket.TYPE,
            com.createstats.vccmk4.network.Vcc4HelmPacket.STREAM_CODEC,
            com.createstats.vccmk4.network.Vcc4HelmPacket::handle);
        registrar.playToServer(com.createstats.vccmk4.network.Vcc4InputPacket.TYPE,
            com.createstats.vccmk4.network.Vcc4InputPacket.STREAM_CODEC,
            com.createstats.vccmk4.network.Vcc4InputPacket::handle);
        registrar.playToServer(com.createstats.vccmk4.network.Vcc4ActionPacket.TYPE,
            com.createstats.vccmk4.network.Vcc4ActionPacket.STREAM_CODEC,
            com.createstats.vccmk4.network.Vcc4ActionPacket::handle);
        registrar.playToClient(com.createstats.vccmk4.network.AerialViewPacket.TYPE,
            com.createstats.vccmk4.network.AerialViewPacket.STREAM_CODEC,
            com.createstats.vccmk4.network.AerialViewPacket::handle);
        registrar.playToServer(com.createstats.vccmk4.network.AerialActionPacket.TYPE,
            com.createstats.vccmk4.network.AerialActionPacket.STREAM_CODEC,
            com.createstats.vccmk4.network.AerialActionPacket::handle);

        registrar.playToClient(AerialFeedPacket.TYPE, AerialFeedPacket.STREAM_CODEC, AerialFeedPacket::handle);
        registrar.playToServer(AerialCommandPacket.TYPE, AerialCommandPacket.STREAM_CODEC,
            AerialCommandPacket::handle);

        registrar.playToClient(GroundFeedPacket.TYPE, GroundFeedPacket.STREAM_CODEC, GroundFeedPacket::handle);
        registrar.playToServer(GroundCommandPacket.TYPE, GroundCommandPacket.STREAM_CODEC,
            GroundCommandPacket::handle);

        registrar.playToServer(com.createstats.surveyor.network.SurveyorEditPacket.TYPE,
            com.createstats.surveyor.network.SurveyorEditPacket.STREAM_CODEC,
            com.createstats.surveyor.network.SurveyorEditPacket::handle);
        registrar.playToServer(com.createstats.surveyor.network.SurveyorViewPacket.TYPE,
            com.createstats.surveyor.network.SurveyorViewPacket.STREAM_CODEC,
            com.createstats.surveyor.network.SurveyorViewPacket::handle);
        registrar.playToServer(com.createstats.surveyor.network.RouteActionPacket.TYPE,
            com.createstats.surveyor.network.RouteActionPacket.STREAM_CODEC,
            com.createstats.surveyor.network.RouteActionPacket::handle);
        registrar.playToClient(com.createstats.surveyor.network.PathwaySyncPacket.TYPE,
            com.createstats.surveyor.network.PathwaySyncPacket.STREAM_CODEC,
            com.createstats.surveyor.network.PathwaySyncPacket::handle);
        registrar.playToClient(com.createstats.surveyor.network.RouteTrackPacket.TYPE,
            com.createstats.surveyor.network.RouteTrackPacket.STREAM_CODEC,
            com.createstats.surveyor.network.RouteTrackPacket::handle);
        registrar.playToClient(com.createstats.surveyor.network.RoutePickedPacket.TYPE,
            com.createstats.surveyor.network.RoutePickedPacket.STREAM_CODEC,
            com.createstats.surveyor.network.RoutePickedPacket::handle);
        registrar.playToClient(com.createstats.surveyor.network.PathwayHealthPacket.TYPE,
            com.createstats.surveyor.network.PathwayHealthPacket.STREAM_CODEC,
            com.createstats.surveyor.network.PathwayHealthPacket::handle);
        registrar.playToClient(com.createstats.surveyor.network.PathwayTerrainPacket.TYPE,
            com.createstats.surveyor.network.PathwayTerrainPacket.STREAM_CODEC,
            com.createstats.surveyor.network.PathwayTerrainPacket::handle);
        registrar.playToClient(com.createstats.surveyor.network.EditHistoryPacket.TYPE,
            com.createstats.surveyor.network.EditHistoryPacket.STREAM_CODEC,
            com.createstats.surveyor.network.EditHistoryPacket::handle);

        registrar.playToServer(com.createstats.cinecam.net.CineControlPacket.TYPE,
            com.createstats.cinecam.net.CineControlPacket.STREAM_CODEC,
            com.createstats.cinecam.net.CineControlPacket::handle);
        registrar.playToServer(com.createstats.cinecam.net.CinePhotoPacket.TYPE,
            com.createstats.cinecam.net.CinePhotoPacket.STREAM_CODEC,
            com.createstats.cinecam.net.CinePhotoPacket::handle);

        registrar.playToServer(com.createstats.chunkloader.net.CrownMapRequestPacket.TYPE,
            com.createstats.chunkloader.net.CrownMapRequestPacket.STREAM_CODEC,
            com.createstats.chunkloader.net.CrownMapRequestPacket::handle);
        registrar.playToClient(com.createstats.chunkloader.net.CrownMapDataPacket.TYPE,
            com.createstats.chunkloader.net.CrownMapDataPacket.STREAM_CODEC,
            com.createstats.chunkloader.net.CrownMapDataPacket::handle);
        registrar.playToServer(com.createstats.chunkloader.net.CrownPickPacket.TYPE,
            com.createstats.chunkloader.net.CrownPickPacket.STREAM_CODEC,
            com.createstats.chunkloader.net.CrownPickPacket::handle);
        registrar.playToClient(com.createstats.chunkloader.net.CrownPickReplyPacket.TYPE,
            com.createstats.chunkloader.net.CrownPickReplyPacket.STREAM_CODEC,
            com.createstats.chunkloader.net.CrownPickReplyPacket::handle);

        registrar.playToClient(com.createstats.vccmk3.network.VccMk3SnapshotPacket.TYPE, com.createstats.vccmk3.network.VccMk3SnapshotPacket.STREAM_CODEC, com.createstats.vccmk3.network.VccMk3SnapshotPacket::handle);
        registrar.playToClient(com.createstats.vccmk3.network.VccMk3HudPacket.TYPE, com.createstats.vccmk3.network.VccMk3HudPacket.STREAM_CODEC, com.createstats.vccmk3.network.VccMk3HudPacket::handle);
        registrar.playToServer(com.createstats.vccmk3.network.VccMk3ActionPacket.TYPE, com.createstats.vccmk3.network.VccMk3ActionPacket.STREAM_CODEC, com.createstats.vccmk3.network.VccMk3ActionPacket::handle);
        registrar.playToClient(FePowerSyncPacket.TYPE, FePowerSyncPacket.STREAM_CODEC, FePowerSyncPacket::handle);
        registrar.playToClient(VacuumGulpPacket.TYPE, VacuumGulpPacket.STREAM_CODEC, VacuumGulpPacket::handle);
        registrar.playToClient(PduHistoryPacket.TYPE, PduHistoryPacket.STREAM_CODEC, PduHistoryPacket::handle);
        registrar.playToClient(PowerMeterHistoryPacket.TYPE, PowerMeterHistoryPacket.STREAM_CODEC, PowerMeterHistoryPacket::handle);
        registrar.playToClient(SmartHomeSyncPacket.TYPE, SmartHomeSyncPacket.STREAM_CODEC, SmartHomeSyncPacket::handle);
        registrar.playToClient(SmartHomeToastPacket.TYPE, SmartHomeToastPacket.STREAM_CODEC, SmartHomeToastPacket::handle);
        registrar.playToClient(com.createstats.network.OpenSecurityPanelPacket.TYPE,
                com.createstats.network.OpenSecurityPanelPacket.STREAM_CODEC,
                com.createstats.network.OpenSecurityPanelPacket::handle);
        registrar.playToServer(com.createstats.network.PanelKeypadPacket.TYPE,
                com.createstats.network.PanelKeypadPacket.STREAM_CODEC,
                com.createstats.network.PanelKeypadPacket::handle);
        registrar.playToClient(com.createstats.network.OpenLockerKeypadPacket.TYPE,
                com.createstats.network.OpenLockerKeypadPacket.STREAM_CODEC,
                com.createstats.network.OpenLockerKeypadPacket::handle);
        registrar.playToServer(com.createstats.network.LockerKeypadPacket.TYPE,
                com.createstats.network.LockerKeypadPacket.STREAM_CODEC,
                com.createstats.network.LockerKeypadPacket::handle);
        registrar.playToClient(com.createstats.network.OpenLockerModePacket.TYPE,
                com.createstats.network.OpenLockerModePacket.STREAM_CODEC,
                com.createstats.network.OpenLockerModePacket::handle);
        registrar.playToServer(com.createstats.network.LockerModePacket.TYPE,
                com.createstats.network.LockerModePacket.STREAM_CODEC,
                com.createstats.network.LockerModePacket::handle);
        registrar.playToServer(com.createstats.network.LockerScrollPacket.TYPE,
                com.createstats.network.LockerScrollPacket.STREAM_CODEC,
                com.createstats.network.LockerScrollPacket::handle);
        registrar.playToServer(WireTensionPacket.TYPE, WireTensionPacket.STREAM_CODEC, WireTensionPacket::handle);
        registrar.playToServer(DimmerTurnPacket.TYPE, DimmerTurnPacket.STREAM_CODEC, DimmerTurnPacket::handle);
        registrar.playToServer(TimeSwitchEditPacket.TYPE, TimeSwitchEditPacket.STREAM_CODEC, TimeSwitchEditPacket::handle);

        registrar.playToServer(TunerSurveyPacket.TYPE, TunerSurveyPacket.STREAM_CODEC, TunerSurveyPacket::handle);
        registrar.playToClient(TunerStationsPacket.TYPE, TunerStationsPacket.STREAM_CODEC, TunerStationsPacket::handle);
        registrar.playToServer(TunerApplyPacket.TYPE, TunerApplyPacket.STREAM_CODEC, TunerApplyPacket::handle);

        registrar.playToClient(SeismicVeinsPacket.TYPE, SeismicVeinsPacket.STREAM_CODEC, SeismicVeinsPacket::handle);

        registrar.playToClient(SeismicScenePacket.TYPE, SeismicScenePacket.STREAM_CODEC, SeismicScenePacket::handle);
        registrar.playToServer(SeismicPanelPacket.TYPE, SeismicPanelPacket.STREAM_CODEC, SeismicPanelPacket::handle);

        registrar.playToServer(SeismicRereadPacket.TYPE, SeismicRereadPacket.STREAM_CODEC, SeismicRereadPacket::handle);
        registrar.playToClient(SecuritySyncPacket.TYPE, SecuritySyncPacket.STREAM_CODEC, SecuritySyncPacket::handle);
        registrar.playToClient(SecurityRelaySyncPacket.TYPE, SecurityRelaySyncPacket.STREAM_CODEC, SecurityRelaySyncPacket::handle);
        registrar.playToClient(BatteryControllerHistoryPacket.TYPE,
                BatteryControllerHistoryPacket.STREAM_CODEC, BatteryControllerHistoryPacket::handle);
        registrar.playToClient(StatsUpdatePacket.TYPE, StatsUpdatePacket.STREAM_CODEC, StatsUpdatePacket::handle);
        registrar.playToClient(CogDataPacket.TYPE, CogDataPacket.STREAM_CODEC, CogDataPacket::handle);
        registrar.playToClient(HandheldStatePacket.TYPE, HandheldStatePacket.STREAM_CODEC, HandheldStatePacket::handle);
        registrar.playToClient(CableAnchorPacket.TYPE, CableAnchorPacket.STREAM_CODEC, CableAnchorPacket::handle);
        registrar.playToClient(DeviceSyncPacket.TYPE, DeviceSyncPacket.STREAM_CODEC, DeviceSyncPacket::handle);
        registrar.playToClient(TrainChargeSyncPacket.TYPE, TrainChargeSyncPacket.STREAM_CODEC, TrainChargeSyncPacket::handle);
        registrar.playToClient(HologramSyncPacket.TYPE, HologramSyncPacket.STREAM_CODEC, HologramSyncPacket::handle);
        registrar.playToClient(OpenFreightManifestClipboardPacket.TYPE, OpenFreightManifestClipboardPacket.STREAM_CODEC, OpenFreightManifestClipboardPacket::handle);
        registrar.playToClient(OpenDispatchBoardPacket.TYPE, OpenDispatchBoardPacket.STREAM_CODEC, OpenDispatchBoardPacket::handle);
        registrar.playToClient(FreightManifestClipboardStatePacket.TYPE, FreightManifestClipboardStatePacket.STREAM_CODEC, FreightManifestClipboardStatePacket::handle);
        registrar.playToClient(DispatchBoardStatePacket.TYPE, DispatchBoardStatePacket.STREAM_CODEC, DispatchBoardStatePacket::handle);
        registrar.playToClient(TableAlarmCalendarDataPacket.TYPE, TableAlarmCalendarDataPacket.STREAM_CODEC, TableAlarmCalendarDataPacket::handle);
        registrar.playToClient(PaSpeakPacket.TYPE, PaSpeakPacket.STREAM_CODEC, PaSpeakPacket::handle);
        registrar.playToClient(PaSnapshotPacket.TYPE, PaSnapshotPacket.STREAM_CODEC, PaSnapshotPacket::handle);
        registrar.playToServer(PaActionPacket.TYPE, PaActionPacket.STREAM_CODEC, PaActionPacket::handle);

        registrar.playToClient(com.createstats.flight.network.FccGuiSnapshotPacket.TYPE,
            com.createstats.flight.network.FccGuiSnapshotPacket.STREAM_CODEC,
            com.createstats.flight.network.FccGuiSnapshotPacket::handle);
        registrar.playToClient(com.createstats.flight.network.FccTelemetryPacket.TYPE,
            com.createstats.flight.network.FccTelemetryPacket.STREAM_CODEC,
            com.createstats.flight.network.FccTelemetryPacket::handle);
        registrar.playToServer(com.createstats.flight.network.FccModeChangePacket.TYPE,
            com.createstats.flight.network.FccModeChangePacket.STREAM_CODEC,
            com.createstats.flight.network.FccModeChangePacket::handle);
        registrar.playToServer(com.createstats.flight.network.FccHudSubscriptionPacket.TYPE,
            com.createstats.flight.network.FccHudSubscriptionPacket.STREAM_CODEC,
            com.createstats.flight.network.FccHudSubscriptionPacket::handle);
        registrar.playToServer(com.createstats.flight.network.FccSensorBindPacket.TYPE,
            com.createstats.flight.network.FccSensorBindPacket.STREAM_CODEC,
            com.createstats.flight.network.FccSensorBindPacket::handle);
        registrar.playToServer(com.createstats.flight.network.FccWorkbenchActionPacket.TYPE,
            com.createstats.flight.network.FccWorkbenchActionPacket.STREAM_CODEC,
            com.createstats.flight.network.FccWorkbenchActionPacket::handle);
        registrar.playToServer(com.createstats.vccmk2.VccMk2ActionPacket.TYPE,
            com.createstats.vccmk2.VccMk2ActionPacket.STREAM_CODEC,
            com.createstats.vccmk2.VccMk2ActionPacket::handle);
        registrar.playToServer(com.createstats.flight.network.FccOverrideCommandPacket.TYPE,
            com.createstats.flight.network.FccOverrideCommandPacket.STREAM_CODEC,
            com.createstats.flight.network.FccOverrideCommandPacket::handle);

        registrar.playToServer(PduActionPacket.TYPE, PduActionPacket.STREAM_CODEC, PduActionPacket::handle);
        registrar.playToServer(PduSetPacket.TYPE, PduSetPacket.STREAM_CODEC, PduSetPacket::handle);
        registrar.playToServer(ArcFurnaceActionPacket.TYPE, ArcFurnaceActionPacket.STREAM_CODEC,
                ArcFurnaceActionPacket::handle);
        registrar.playToServer(InductionSmelterActionPacket.TYPE,
                InductionSmelterActionPacket.STREAM_CODEC, InductionSmelterActionPacket::handle);
        registrar.playToServer(BatteryControllerSetPacket.TYPE, BatteryControllerSetPacket.STREAM_CODEC,
                BatteryControllerSetPacket::handle);
        registrar.playToServer(PduHistoryRequestPacket.TYPE, PduHistoryRequestPacket.STREAM_CODEC, PduHistoryRequestPacket::handle);
        registrar.playToServer(PowerMeterHistoryRequestPacket.TYPE, PowerMeterHistoryRequestPacket.STREAM_CODEC, PowerMeterHistoryRequestPacket::handle);
        registrar.playToServer(BatteryControllerHistoryRequestPacket.TYPE,
                BatteryControllerHistoryRequestPacket.STREAM_CODEC,
                BatteryControllerHistoryRequestPacket::handle);
        registrar.playToServer(PowerMeterActionPacket.TYPE, PowerMeterActionPacket.STREAM_CODEC, PowerMeterActionPacket::handle);
        registrar.playToServer(PowerLimiterConfigPacket.TYPE, PowerLimiterConfigPacket.STREAM_CODEC, PowerLimiterConfigPacket::handle);
        registrar.playToServer(SmartHomeActionPacket.TYPE, SmartHomeActionPacket.STREAM_CODEC, SmartHomeActionPacket::handle);
        registrar.playToServer(SecurityActionPacket.TYPE, SecurityActionPacket.STREAM_CODEC, SecurityActionPacket::handle);
        registrar.playToServer(SecurityRelayActionPacket.TYPE, SecurityRelayActionPacket.STREAM_CODEC, SecurityRelayActionPacket::handle);
        registrar.playToServer(SmartHomeRenamePacket.TYPE, SmartHomeRenamePacket.STREAM_CODEC, SmartHomeRenamePacket::handle);
        registrar.playToServer(RequestStatsPacket.TYPE, RequestStatsPacket.STREAM_CODEC, RequestStatsPacket::handle);
        registrar.playToServer(ToggleVisorPacket.TYPE, ToggleVisorPacket.STREAM_CODEC, ToggleVisorPacket::handle);
        registrar.playToServer(CalculatorStatePacket.TYPE, CalculatorStatePacket.STREAM_CODEC, CalculatorStatePacket::handle);
        registrar.playToServer(TableAlarmClockStatePacket.TYPE, TableAlarmClockStatePacket.STREAM_CODEC, TableAlarmClockStatePacket::handle);
        registrar.playToServer(RequestTableAlarmCalendarPacket.TYPE, RequestTableAlarmCalendarPacket.STREAM_CODEC, RequestTableAlarmCalendarPacket::handle);
        registrar.playToServer(TableAlarmCalendarSavePacket.TYPE, TableAlarmCalendarSavePacket.STREAM_CODEC, TableAlarmCalendarSavePacket::handle);
        registrar.playToServer(TableAlarmCalendarInvitePacket.TYPE, TableAlarmCalendarInvitePacket.STREAM_CODEC, TableAlarmCalendarInvitePacket::handle);
        registrar.playToServer(ConfigSyncPacket.TYPE, ConfigSyncPacket.STREAM_CODEC, ConfigSyncPacket::handle);
        registrar.playToServer(FlowMeterTriggerPacket.TYPE, FlowMeterTriggerPacket.STREAM_CODEC, FlowMeterTriggerPacket::handle);
        registrar.playToServer(StockpileThresholdPacket.TYPE, StockpileThresholdPacket.STREAM_CODEC, StockpileThresholdPacket::handle);
        registrar.playToServer(DetectorResetPacket.TYPE, DetectorResetPacket.STREAM_CODEC, DetectorResetPacket::handle);
        registrar.playToServer(InventoryDisplaySettingsPacket.TYPE, InventoryDisplaySettingsPacket.STREAM_CODEC, InventoryDisplaySettingsPacket::handle);
        registrar.playToClient(InventoryFacePacket.TYPE, InventoryFacePacket.STREAM_CODEC, InventoryFacePacket::handle);
        registrar.playToClient(InventoryBooksPacket.TYPE, InventoryBooksPacket.STREAM_CODEC, InventoryBooksPacket::handle);
        registrar.playToServer(DisplayLinkDieselModePacket.TYPE, DisplayLinkDieselModePacket.STREAM_CODEC, DisplayLinkDieselModePacket::handle);
        registrar.playToServer(DisplayLinkDieselFuelReservePacket.TYPE, DisplayLinkDieselFuelReservePacket.STREAM_CODEC, DisplayLinkDieselFuelReservePacket::handle);
        registrar.playToServer(SourceLabelPacket.TYPE, SourceLabelPacket.STREAM_CODEC, SourceLabelPacket::handle);
        registrar.playToServer(StageLightConfigPacket.TYPE, StageLightConfigPacket.STREAM_CODEC, StageLightConfigPacket::handle);
        registrar.playToServer(KineticFlywheelConfigPacket.TYPE, KineticFlywheelConfigPacket.STREAM_CODEC, KineticFlywheelConfigPacket::handle);
        registrar.playToServer(RequestCogDataPacket.TYPE, RequestCogDataPacket.STREAM_CODEC, RequestCogDataPacket::handle);
        registrar.playToServer(RequestHandheldStatePacket.TYPE, RequestHandheldStatePacket.STREAM_CODEC, RequestHandheldStatePacket::handle);
        registrar.playToServer(RequestFreightManifestClipboardStatePacket.TYPE, RequestFreightManifestClipboardStatePacket.STREAM_CODEC, RequestFreightManifestClipboardStatePacket::handle);
        registrar.playToServer(RequestDispatchBoardStatePacket.TYPE, RequestDispatchBoardStatePacket.STREAM_CODEC, RequestDispatchBoardStatePacket::handle);
        registrar.playToServer(FreightManifestDeliveryOrderActionPacket.TYPE, FreightManifestDeliveryOrderActionPacket.STREAM_CODEC, FreightManifestDeliveryOrderActionPacket::handle);
        registrar.playToServer(HologramConfigPacket.TYPE, HologramConfigPacket.STREAM_CODEC, HologramConfigPacket::handle);
        registrar.playToServer(HoloMk3ConfigPacket.TYPE, HoloMk3ConfigPacket.STREAM_CODEC, HoloMk3ConfigPacket::handle);
        registrar.playToServer(AnnotationActionPacket.TYPE, AnnotationActionPacket.STREAM_CODEC, AnnotationActionPacket::handle);
        registrar.playToServer(TrainMapGroupPacket.TYPE, TrainMapGroupPacket.STREAM_CODEC, TrainMapGroupPacket::handle);
        registrar.playToServer(PanelActivityPacket.TYPE, PanelActivityPacket.STREAM_CODEC, PanelActivityPacket::handle);
        registrar.playToServer(ScanControlPacket.TYPE, ScanControlPacket.STREAM_CODEC, ScanControlPacket::handle);
        registrar.playToServer(Mk3ConfigPacket.TYPE, Mk3ConfigPacket.STREAM_CODEC, Mk3ConfigPacket::handle);
        registrar.playToServer(AlarmConfigPacket.TYPE, AlarmConfigPacket.STREAM_CODEC, AlarmConfigPacket::handle);
        registrar.playToServer(AnnunciatorConfigPacket.TYPE, AnnunciatorConfigPacket.STREAM_CODEC, AnnunciatorConfigPacket::handle);
        registrar.playToServer(AnnunciatorControlPacket.TYPE, AnnunciatorControlPacket.STREAM_CODEC, AnnunciatorControlPacket::handle);
        registrar.playToServer(SafetyClutchConfigPacket.TYPE, SafetyClutchConfigPacket.STREAM_CODEC, SafetyClutchConfigPacket::handle);
        registrar.playToServer(AutoTransmissionClutchConfigPacket.TYPE, AutoTransmissionClutchConfigPacket.STREAM_CODEC, AutoTransmissionClutchConfigPacket::handle);
        registrar.playToServer(LoadBalancerConfigPacket.TYPE, LoadBalancerConfigPacket.STREAM_CODEC, LoadBalancerConfigPacket::handle);
        registrar.playToServer(StatusTowerLightConfigPacket.TYPE, StatusTowerLightConfigPacket.STREAM_CODEC, StatusTowerLightConfigPacket::handle);
        registrar.playToServer(MiniDisplayConfigPacket.TYPE, MiniDisplayConfigPacket.STREAM_CODEC, MiniDisplayConfigPacket::handle);
        registrar.playToServer(HudLayoutConfigPacket.TYPE, HudLayoutConfigPacket.STREAM_CODEC, HudLayoutConfigPacket::handle);
        registrar.playToServer(PilotHelmetFccSubscriptionPacket.TYPE, PilotHelmetFccSubscriptionPacket.STREAM_CODEC, PilotHelmetFccSubscriptionPacket::handle);
        registrar.playToServer(PilotHelmetSettingsPacket.TYPE, PilotHelmetSettingsPacket.STREAM_CODEC, PilotHelmetSettingsPacket::handle);
        registrar.playToServer(PilotHelmetNightVisionTogglePacket.TYPE, PilotHelmetNightVisionTogglePacket.STREAM_CODEC, PilotHelmetNightVisionTogglePacket::handle);
        registrar.playToServer(SaveFreightManifestPacket.TYPE, SaveFreightManifestPacket.STREAM_CODEC, SaveFreightManifestPacket::handle);

        registrar.playToServer(RailSubscribePacket.TYPE, RailSubscribePacket.STREAM_CODEC, RailSubscribePacket::handle);
        registrar.playToClient(RailSnapshotPacket.TYPE, RailSnapshotPacket.STREAM_CODEC, RailSnapshotPacket::handle);

        registrar.playToServer(NetscanSubscribePacket.TYPE, NetscanSubscribePacket.STREAM_CODEC, NetscanSubscribePacket::handle);
        registrar.playToClient(NetscanLivePacket.TYPE, NetscanLivePacket.STREAM_CODEC, NetscanLivePacket::handle);

        registrar.playToServer(com.createstats.support.SupportRequestPacket.TYPE,
            com.createstats.support.SupportRequestPacket.STREAM_CODEC,
            com.createstats.support.SupportRequestPacket::handle);
        registrar.playToServer(com.createstats.support.SupportActionPacket.TYPE,
            com.createstats.support.SupportActionPacket.STREAM_CODEC,
            com.createstats.support.SupportActionPacket::handle);
        registrar.playToServer(com.createstats.support.SupportHelloPacket.TYPE,
            com.createstats.support.SupportHelloPacket.STREAM_CODEC,
            com.createstats.support.SupportHelloPacket::handle);
        registrar.playToServer(com.createstats.support.SupportProofPacket.TYPE,
            com.createstats.support.SupportProofPacket.STREAM_CODEC,
            com.createstats.support.SupportProofPacket::handle);
        registrar.playToClient(com.createstats.support.SupportReportPacket.TYPE,
            com.createstats.support.SupportReportPacket.STREAM_CODEC,
            com.createstats.support.SupportReportPacket::handle);
        registrar.playToClient(com.createstats.support.SupportChallengePacket.TYPE,
            com.createstats.support.SupportChallengePacket.STREAM_CODEC,
            com.createstats.support.SupportChallengePacket::handle);
        registrar.playToClient(com.createstats.support.SupportStatusPacket.TYPE,
            com.createstats.support.SupportStatusPacket.STREAM_CODEC,
            com.createstats.support.SupportStatusPacket::handle);
        registrar.playToClient(com.createstats.support.SupportExportPacket.TYPE,
            com.createstats.support.SupportExportPacket.STREAM_CODEC,
            com.createstats.support.SupportExportPacket::handle);

        CreateStats.LOGGER.debug("Network packets registered");
    }
}
