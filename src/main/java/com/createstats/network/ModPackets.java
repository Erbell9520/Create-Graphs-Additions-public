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
        PayloadRegistrar registrar = event.registrar(CreateStats.MOD_ID).versioned("28");

        // S2C (server → client)
        registrar.playToClient(FePowerSyncPacket.TYPE, FePowerSyncPacket.STREAM_CODEC, FePowerSyncPacket::handle);
        registrar.playToClient(PduHistoryPacket.TYPE, PduHistoryPacket.STREAM_CODEC, PduHistoryPacket::handle);
        registrar.playToClient(StatsUpdatePacket.TYPE, StatsUpdatePacket.STREAM_CODEC, StatsUpdatePacket::handle);
        registrar.playToClient(CogDataPacket.TYPE, CogDataPacket.STREAM_CODEC, CogDataPacket::handle);
        registrar.playToClient(HandheldStatePacket.TYPE, HandheldStatePacket.STREAM_CODEC, HandheldStatePacket::handle);
        registrar.playToClient(HologramSyncPacket.TYPE, HologramSyncPacket.STREAM_CODEC, HologramSyncPacket::handle);
        registrar.playToClient(OpenFreightManifestClipboardPacket.TYPE, OpenFreightManifestClipboardPacket.STREAM_CODEC, OpenFreightManifestClipboardPacket::handle);
        registrar.playToClient(OpenDispatchBoardPacket.TYPE, OpenDispatchBoardPacket.STREAM_CODEC, OpenDispatchBoardPacket::handle);
        registrar.playToClient(FreightManifestClipboardStatePacket.TYPE, FreightManifestClipboardStatePacket.STREAM_CODEC, FreightManifestClipboardStatePacket::handle);
        registrar.playToClient(DispatchBoardStatePacket.TYPE, DispatchBoardStatePacket.STREAM_CODEC, DispatchBoardStatePacket::handle);
        registrar.playToClient(TableAlarmCalendarDataPacket.TYPE, TableAlarmCalendarDataPacket.STREAM_CODEC, TableAlarmCalendarDataPacket::handle);

        // FCC (Phase 10)
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

        // C2S (client → server)
        registrar.playToServer(PduActionPacket.TYPE, PduActionPacket.STREAM_CODEC, PduActionPacket::handle);
        registrar.playToServer(PduSetPacket.TYPE, PduSetPacket.STREAM_CODEC, PduSetPacket::handle);
        registrar.playToServer(PduHistoryRequestPacket.TYPE, PduHistoryRequestPacket.STREAM_CODEC, PduHistoryRequestPacket::handle);
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
        registrar.playToServer(ProductionDetectorSourceModePacket.TYPE, ProductionDetectorSourceModePacket.STREAM_CODEC, ProductionDetectorSourceModePacket::handle);
        registrar.playToServer(ProductionDetectorDieselModePacket.TYPE, ProductionDetectorDieselModePacket.STREAM_CODEC, ProductionDetectorDieselModePacket::handle);
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
        registrar.playToServer(AnnotationActionPacket.TYPE, AnnotationActionPacket.STREAM_CODEC, AnnotationActionPacket::handle);
        registrar.playToServer(TrainMapGroupPacket.TYPE, TrainMapGroupPacket.STREAM_CODEC, TrainMapGroupPacket::handle);
        registrar.playToServer(PanelActivityPacket.TYPE, PanelActivityPacket.STREAM_CODEC, PanelActivityPacket::handle);
        registrar.playToServer(ScanControlPacket.TYPE, ScanControlPacket.STREAM_CODEC, ScanControlPacket::handle);
        registrar.playToServer(AlarmConfigPacket.TYPE, AlarmConfigPacket.STREAM_CODEC, AlarmConfigPacket::handle);
        registrar.playToServer(DetectorRangePacket.TYPE, DetectorRangePacket.STREAM_CODEC, DetectorRangePacket::handle);
        registrar.playToServer(SafetyClutchConfigPacket.TYPE, SafetyClutchConfigPacket.STREAM_CODEC, SafetyClutchConfigPacket::handle);
        registrar.playToServer(AutoTransmissionClutchConfigPacket.TYPE, AutoTransmissionClutchConfigPacket.STREAM_CODEC, AutoTransmissionClutchConfigPacket::handle);
        registrar.playToServer(LoadBalancerConfigPacket.TYPE, LoadBalancerConfigPacket.STREAM_CODEC, LoadBalancerConfigPacket::handle);
        registrar.playToServer(StatusTowerLightConfigPacket.TYPE, StatusTowerLightConfigPacket.STREAM_CODEC, StatusTowerLightConfigPacket::handle);
        registrar.playToServer(MiniDisplayConfigPacket.TYPE, MiniDisplayConfigPacket.STREAM_CODEC, MiniDisplayConfigPacket::handle);
        registrar.playToServer(DamageObserverConfigPacket.TYPE, DamageObserverConfigPacket.STREAM_CODEC, DamageObserverConfigPacket::handle);
        registrar.playToServer(DamageObserverActionPacket.TYPE, DamageObserverActionPacket.STREAM_CODEC, DamageObserverActionPacket::handle);
        registrar.playToServer(BattleScreenWeaponActionPacket.TYPE, BattleScreenWeaponActionPacket.STREAM_CODEC, BattleScreenWeaponActionPacket::handle);
        registrar.playToServer(BattleScreenLightActionPacket.TYPE, BattleScreenLightActionPacket.STREAM_CODEC, BattleScreenLightActionPacket::handle);
        registrar.playToServer(BattleScreenHologramTogglePacket.TYPE, BattleScreenHologramTogglePacket.STREAM_CODEC, BattleScreenHologramTogglePacket::handle);
        registrar.playToServer(HudLayoutConfigPacket.TYPE, HudLayoutConfigPacket.STREAM_CODEC, HudLayoutConfigPacket::handle);
        registrar.playToServer(PilotHelmetFccSubscriptionPacket.TYPE, PilotHelmetFccSubscriptionPacket.STREAM_CODEC, PilotHelmetFccSubscriptionPacket::handle);
        registrar.playToServer(PilotHelmetSettingsPacket.TYPE, PilotHelmetSettingsPacket.STREAM_CODEC, PilotHelmetSettingsPacket::handle);
        registrar.playToServer(PilotHelmetNightVisionTogglePacket.TYPE, PilotHelmetNightVisionTogglePacket.STREAM_CODEC, PilotHelmetNightVisionTogglePacket::handle);
        registrar.playToServer(SaveFreightManifestPacket.TYPE, SaveFreightManifestPacket.STREAM_CODEC, SaveFreightManifestPacket::handle);
        registrar.playToServer(RequestObserverVoxelMapPacket.TYPE, RequestObserverVoxelMapPacket.STREAM_CODEC, RequestObserverVoxelMapPacket::handle);

        // S2C voxel-map payload (Battle Tactical V2 redesign)
        registrar.playToClient(ObserverVoxelMapPayloadPacket.TYPE, ObserverVoxelMapPayloadPacket.STREAM_CODEC, ObserverVoxelMapPayloadPacket::handle);

        CreateStats.LOGGER.debug("Network packets registered");
    }
}
