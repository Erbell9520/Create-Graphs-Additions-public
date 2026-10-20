package com.createstats.registry;

import com.tterrag.registrate.util.nullness.NonNullSupplier;
import com.createstats.renderer.AntennaOnlyRenderer;
import com.createstats.renderer.BattleScreenDisplayRenderer;
import com.createstats.renderer.CalculatorRenderer;
import com.createstats.renderer.AutoTransmissionClutchRenderer;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.FlowMeterBE;
import com.createstats.renderer.ControlClutchRenderer;
import com.createstats.renderer.DispatchBoardRenderer;
import com.createstats.renderer.AutoTransmissionClutchVisual;
import com.createstats.renderer.ControlClutchVisual;
import com.createstats.renderer.DataDialRenderer;
import com.createstats.renderer.KineticFlywheelRenderer;
import com.createstats.renderer.DamageObserverRenderer;
import com.createstats.renderer.FactoryLogicControllerRenderer;
import com.createstats.renderer.LoadBalancerRenderer;
import com.createstats.renderer.LoadBalancerVisual;
import com.createstats.renderer.MiniDisplayRenderer;
import com.createstats.renderer.OverflowGateRenderer;
import com.createstats.renderer.SafetyClutchRenderer;
import com.createstats.renderer.SafetyClutchVisual;
import com.createstats.renderer.RpmGovernorRenderer;
import com.createstats.renderer.SmartGearshiftRenderer;
import com.createstats.renderer.StatsDisplayPanelRenderer;
import com.createstats.renderer.ProductionDetectorRenderer;
import com.createstats.renderer.HolographicProjectorRenderer;
import com.createstats.CreateStats;
import com.createstats.blockentity.CalculatorBE;
import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.blockentity.FactoryLogicControllerBE;
import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.blockentity.ControlClutchBE;
import com.createstats.blockentity.DataDialBE;
import com.createstats.blockentity.InductionHeaterBE;
import com.createstats.renderer.InductionHeaterRenderer;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.blockentity.LoadBalancerBE;
import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.OverflowGateBE;
import com.createstats.blockentity.SmartGearshiftBE;
import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.blockentity.StageLightBE;
import com.createstats.blockentity.StageLightMk2BE;
import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.blockentity.StatsAlarmBE;
import com.createstats.blockentity.TableAlarmClockBE;
import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.StatsRecorderBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.client.FlightControlComputerRenderer;
import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.createstats.pathfinder.client.PathScannerRenderer;
import com.createstats.pathfinder.client.PathfinderComputerRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public class ModBlockEntities
{
    public static final BlockEntityEntry<StatsDisplayPanelBE> STATS_DISPLAY_PANEL;
    public static final BlockEntityEntry<com.createstats.statsmk2.StatsDisplayMk2BlockEntity> STATS_DISPLAY_MK2;
    public static final BlockEntityEntry<com.createstats.statsmk3.Mk3PanelBlockEntity> STATS_DISPLAY_MK3;
    public static final BlockEntityEntry<com.createstats.statsmk3.Mk3ConsoleBlockEntity> STATS_DISPLAY_MK3_CONSOLE;
    public static final BlockEntityEntry<StatsRecorderBE> STATS_RECORDER;
    public static final BlockEntityEntry<ProductionDetectorBE> PRODUCTION_DETECTOR;
    public static final BlockEntityEntry<com.createstats.blockentity.WallOutletBE> WALL_OUTLET;
    public static final BlockEntityEntry<StatsAlarmBE> STATS_ALARM;

    public static final BlockEntityEntry<com.createstats.blockentity.AnnunciatorBE> ANNUNCIATOR;
    public static final BlockEntityEntry<TableAlarmClockBE> TABLE_ALARM_CLOCK;
    public static final BlockEntityEntry<StatusTowerLightBE> STATUS_TOWER_LIGHT;
    public static final BlockEntityEntry<StageLightBE> STAGE_LIGHT;
    public static final BlockEntityEntry<StageLightMk2BE> STAGE_LIGHT_MK2;
    public static final BlockEntityEntry<DataDialBE> DATA_DIAL;
    public static final BlockEntityEntry<BattleScreenDisplayBE> BATTLE_SCREEN_DISPLAY;
    public static final BlockEntityEntry<DamageObserverBE> DAMAGE_OBSERVER;
    public static final BlockEntityEntry<MiniDisplayBE> MINI_DISPLAY;
    public static final BlockEntityEntry<com.createstats.blockentity.EInkPanelBE> EINK_PANEL;
    public static final BlockEntityEntry<com.createstats.blockentity.SmartHomePanelBE> SMART_HOME_PANEL;
    public static final BlockEntityEntry<com.createstats.blockentity.WeatherPanelBE> WEATHER_PANEL;
    public static final BlockEntityEntry<com.createstats.blockentity.HomeSecurityPanelBE> HOME_SECURITY_PANEL;
    public static final BlockEntityEntry<com.createstats.blockentity.BlastShutterBE> BLAST_SHUTTER;
    public static final BlockEntityEntry<com.createstats.blockentity.SecurityLockerBE> SECURITY_LOCKER;
    public static final BlockEntityEntry<com.createstats.blockentity.SecuritySensorBE> SECURITY_SENSOR;
    public static final BlockEntityEntry<com.createstats.blockentity.SecurityRelayBE> SECURITY_RELAY;

    public static final BlockEntityEntry<com.createstats.blockentity.GarageDoorBE> GARAGE_DOOR;
    public static final BlockEntityEntry<HolographicProjectorBE> HOLOGRAPHIC_PROJECTOR;

    public static final BlockEntityEntry<com.createstats.holomk3.HoloProjectorMk3BE> HOLOGRAPHIC_PROJECTOR_MK3;
    public static final BlockEntityEntry<CalculatorBE> CALCULATOR;
    public static final BlockEntityEntry<FactoryLogicControllerBE> FACTORY_LOGIC_CONTROLLER;
    public static final BlockEntityEntry<SafetyClutchBE> SAFETY_CLUTCH;
    public static final BlockEntityEntry<AutoTransmissionClutchBE> AUTO_TRANSMISSION_CLUTCH;
    public static final BlockEntityEntry<ControlClutchBE> CONTROL_CLUTCH;
    public static final BlockEntityEntry<LoadBalancerBE> LOAD_BALANCER;
    public static final BlockEntityEntry<SmartGearshiftBE> SMART_GEARSHIFT;
    public static final BlockEntityEntry<KineticFlywheelBE> KINETIC_FLYWHEEL;
    public static final BlockEntityEntry<InductionHeaterBE> INDUCTION_HEATER;
    public static final BlockEntityEntry<FlowMeterBE> FLOW_METER;
    public static final BlockEntityEntry<com.createstats.blockentity.PowerMeterBE> POWER_METER;
    public static final BlockEntityEntry<com.createstats.blockentity.PowerLimiterBE> POWER_LIMITER;
    public static final BlockEntityEntry<com.createstats.blockentity.UniversalPowerCouplerBE> UNIVERSAL_POWER_COUPLER;
    public static final BlockEntityEntry<OverflowGateBE> OVERFLOW_GATE;
    public static final BlockEntityEntry<FreightManifestClipboardBE> FREIGHT_MANIFEST_CLIPBOARD;
    public static final BlockEntityEntry<DispatchBoardBE> DISPATCH_BOARD;
    public static final BlockEntityEntry<RpmGovernorBE> RPM_GOVERNOR;
    public static final BlockEntityEntry<com.createstats.flight.blockentity.FlightControlComputerBE> FLIGHT_CONTROL_COMPUTER;
    public static final BlockEntityEntry<com.createstats.vccmk2.VccMk2BlockEntity> VCC_MK_II;
    public static final BlockEntityEntry<com.createstats.vccmk3.VccMk3BlockEntity> VCC_MK3;

    public static final BlockEntityEntry<com.createstats.vccmk4.BtaG1BE> VCC_MK4;

    public static final BlockEntityEntry<com.createstats.vccmk4.BtaG1BE> BTA_G1;
    public static final BlockEntityEntry<com.createstats.vccmk4.air.AerialBE> VCC_MK4_AERIAL;

    public static final BlockEntityEntry<com.createstats.vccmk4.air.AerialDeckPartBE> VCC_MK4_AERIAL_DECK_PART;
    public static final BlockEntityEntry<com.createstats.vehiclelight.VehicleLightBE> VEHICLE_LIGHT;
    public static final BlockEntityEntry<com.createstats.driverhelmet.DriverHelmetBE> DRIVER_HELMET;
    public static final BlockEntityEntry<com.createstats.aviatorhelmet.AviatorHelmetBE> AVIATOR_HELMET;
    public static final BlockEntityEntry<PathfinderComputerBE> PATHFINDER_COMPUTER;
    public static final BlockEntityEntry<PathScannerBE> PATH_SCANNER;
    public static final BlockEntityEntry<com.createstats.blockentity.KineticDynamoBE> KINETIC_DYNAMO;
    public static final BlockEntityEntry<com.createstats.blockentity.BiofuelBurnerBE> BIOFUEL_BURNER;
    public static final BlockEntityEntry<com.createstats.blockentity.StarterCapacitorBE> STARTER_CAPACITOR;

    public static final BlockEntityEntry<com.createstats.blockentity.SolarPanelBE> SOLAR_PANEL;

    public static final BlockEntityEntry<com.createstats.netscan.blockentity.NetscanInlineBE> TOPOLOGY_SCANNER_INLINE;
    public static final BlockEntityEntry<com.createstats.netscan.blockentity.NetscanProbeBE> TOPOLOGY_SCANNER_PROBE;
    public static final BlockEntityEntry<com.createstats.blockentity.InductionRangeBE> INDUCTION_RANGE;
    public static final BlockEntityEntry<com.createstats.blockentity.ArcFurnaceBE> ARC_FURNACE;
    public static final BlockEntityEntry<com.createstats.blockentity.InductionSmelterBE> INDUCTION_SMELTER;
    public static final BlockEntityEntry<com.createstats.blockentity.CableBE> CABLE;
    public static final BlockEntityEntry<com.createstats.blockentity.IndoorCableBE> INDOOR_CABLE;
    public static final BlockEntityEntry<com.createstats.blockentity.SwitchBE> SWITCH;
    public static final BlockEntityEntry<com.createstats.blockentity.MainBreakerBE> MAIN_BREAKER;
    public static final BlockEntityEntry<com.createstats.blockentity.ContactorBE> CONTACTOR;
    public static final BlockEntityEntry<com.createstats.blockentity.LampBE> LAMP;
    public static final BlockEntityEntry<com.createstats.blockentity.PowerConnectorBE> POWER_CONNECTOR;
    public static final BlockEntityEntry<com.createstats.blockentity.DiagnosticJunctionBE> DIAGNOSTIC_JUNCTION;
    public static final BlockEntityEntry<com.createstats.blockentity.CapacitorBankBE> CAPACITOR_BANK;
    public static final BlockEntityEntry<com.createstats.blockentity.PowerDistributionUnitBE> POWER_DISTRIBUTION_UNIT;
    public static final BlockEntityEntry<com.createstats.blockentity.PduCabinetBE> PDU_CABINET;
    public static final BlockEntityEntry<com.createstats.blockentity.BatteryControllerBE> BATTERY_CONTROLLER;
    public static final BlockEntityEntry<com.createstats.blockentity.PortablePowerInterfaceBE> PORTABLE_POWER_INTERFACE;

    public static final BlockEntityEntry<com.createstats.blockentity.TrackFeederBE> TRACK_FEEDER;
    public static final BlockEntityEntry<com.createstats.powertrack.SectionInsulatorBE> SECTION_INSULATOR;
    public static final BlockEntityEntry<com.createstats.powertrack.CurrentCollectorBE> CURRENT_COLLECTOR;
    public static final BlockEntityEntry<com.createstats.blockentity.FluidPumpBE> FLUID_PUMP;
    public static final BlockEntityEntry<com.createstats.blockentity.FeMotorBE> FE_MOTOR;
    public static final BlockEntityEntry<com.createstats.blockentity.CoffeeMachineBE> COFFEE_MACHINE;
    public static final BlockEntityEntry<com.createstats.blockentity.WaterCoolerBE> WATER_COOLER;
    public static final BlockEntityEntry<com.createstats.cinecam.CineCameraBE> CINE_CAMERA;
    public static final BlockEntityEntry<com.createstats.blockentity.AirFryerBE> AIR_FRYER;
    public static final BlockEntityEntry<com.createstats.blockentity.BulkWasherBE> BULK_WASHER;
    public static final BlockEntityEntry<com.createstats.blockentity.ToasterBE> TOASTER;
    public static final BlockEntityEntry<com.createstats.blockentity.BreadSlicerBE> BREAD_SLICER;
    public static final BlockEntityEntry<com.createstats.blockentity.VacuumCollectorBE> VACUUM_COLLECTOR;
    public static final BlockEntityEntry<com.createstats.blockentity.VacuumCleanerBE> VACUUM_CLEANER;
    public static final BlockEntityEntry<com.createstats.blockentity.VacuumSnifferBE> VACUUM_SNIFFER;
    public static final BlockEntityEntry<com.createstats.blockentity.MobRepellerBE> MOB_REPELLER;
    public static final BlockEntityEntry<com.createstats.blockentity.StormCoilBE> STORM_COIL;
    public static final BlockEntityEntry<com.createstats.blockentity.SparkCrownBE> SPARK_CROWN;
    public static final BlockEntityEntry<com.createstats.blockentity.SeismicScannerBE> SEISMIC_SCANNER;
    public static final BlockEntityEntry<com.createstats.blockentity.OvenBE> OVEN;
    public static final BlockEntityEntry<com.createstats.blockentity.PaSpeakerBE> PA_SPEAKER;
    public static final BlockEntityEntry<com.createstats.blockentity.PaSpeakerBE> PA_MEGAPHONE;
    public static final BlockEntityEntry<com.createstats.blockentity.AnnouncementComputerBE> ANNOUNCEMENT_COMPUTER;
    public static final BlockEntityEntry<com.createstats.blockentity.PoweredDoorBE> POWERED_DOOR;

    public static void register() {
        CreateStats.LOGGER.debug("Registering block entities for {}", "create_stats");
    }

    static {
        STATS_DISPLAY_PANEL = CreateStats.registrate().blockEntity("stats_display_panel", StatsDisplayPanelBE::new).validBlock(() -> ModBlocks.STATS_DISPLAY_PANEL.get()).renderer(() -> StatsDisplayPanelRenderer::new).register();
        STATS_DISPLAY_MK2 = CreateStats.registrate().blockEntity("stats_display_mk2", com.createstats.statsmk2.StatsDisplayMk2BlockEntity::new).validBlock(() -> ModBlocks.STATS_DISPLAY_MK2.get()).renderer(() -> StatsDisplayPanelRenderer::new).register();
        STATS_DISPLAY_MK3 = CreateStats.registrate().blockEntity("stats_display_mk3", com.createstats.statsmk3.Mk3PanelBlockEntity::new).validBlock(() -> ModBlocks.STATS_DISPLAY_MK3.get()).renderer(() -> com.createstats.statsmk3.render.Mk3PanelRenderer::new).register();
        STATS_DISPLAY_MK3_CONSOLE = CreateStats.registrate().blockEntity("stats_display_mk3_console", com.createstats.statsmk3.Mk3ConsoleBlockEntity::new).validBlock(() -> ModBlocks.STATS_DISPLAY_MK3_CONSOLE.get()).renderer(() -> com.createstats.statsmk3.render.Mk3PanelRenderer::new).register();
        STATS_RECORDER = CreateStats.registrate().blockEntity("stats_recorder", StatsRecorderBE::new).validBlock(() -> ModBlocks.STATS_RECORDER.get()).renderer(() -> AntennaOnlyRenderer<StatsRecorderBE>::new).register();
        PRODUCTION_DETECTOR = CreateStats.registrate().blockEntity("production_detector", ProductionDetectorBE::new).validBlock(() -> ModBlocks.PRODUCTION_DETECTOR.get()).renderer(() -> ProductionDetectorRenderer::new).register();

        WALL_OUTLET = CreateStats.registrate().blockEntity("wall_outlet", com.createstats.blockentity.WallOutletBE::new).validBlocks(() -> ModBlocks.POWER_OUTLET.get(), () -> ModBlocks.DUPLEX_POWER_OUTLET.get()).renderer(() -> com.createstats.renderer.WallOutletRenderer::new).register();
        FLOW_METER = CreateStats.registrate().blockEntity("flow_meter", FlowMeterBE::new).validBlock(() -> ModBlocks.FLOW_METER.get()).renderer(() -> com.createstats.renderer.FlowMeterRenderer::new).register();
        POWER_METER = CreateStats.registrate().blockEntity("power_meter", com.createstats.blockentity.PowerMeterBE::new).validBlock(() -> ModBlocks.POWER_METER.get()).renderer(() -> com.createstats.renderer.PowerMeterRenderer::new).register();
        POWER_LIMITER = CreateStats.registrate().blockEntity("power_limiter", com.createstats.blockentity.PowerLimiterBE::new).validBlock(() -> ModBlocks.POWER_LIMITER.get()).renderer(() -> com.createstats.renderer.PowerLimiterRenderer::new).register();
        UNIVERSAL_POWER_COUPLER = CreateStats.registrate().blockEntity("universal_power_coupler", com.createstats.blockentity.UniversalPowerCouplerBE::new).validBlock(() -> ModBlocks.UNIVERSAL_POWER_COUPLER.get()).renderer(() -> com.createstats.renderer.UniversalPowerCouplerRenderer::new).register();
        STATS_ALARM = CreateStats.registrate().blockEntity("stats_alarm", StatsAlarmBE::new).validBlock(() -> ModBlocks.STATS_ALARM.get()).renderer(() -> AntennaOnlyRenderer<StatsAlarmBE>::new).register();
        ANNUNCIATOR = CreateStats.registrate().blockEntity("annunciator", com.createstats.blockentity.AnnunciatorBE::new).validBlocks(() -> ModBlocks.KLAXON_ALARM.get(), () -> ModBlocks.SENTRY_ALARM.get()).renderer(() -> com.createstats.renderer.AnnunciatorRenderer::new).register();
        TABLE_ALARM_CLOCK = CreateStats.registrate().blockEntity("table_alarm_clock", TableAlarmClockBE::new).validBlock(() -> ModBlocks.TABLE_ALARM_CLOCK.get()).register();
        STATUS_TOWER_LIGHT = CreateStats.registrate().blockEntity("status_tower_light", StatusTowerLightBE::new).validBlock(() -> ModBlocks.STATUS_TOWER_LIGHT.get()).renderer(() -> com.createstats.renderer.StatusBeaconRenderer::new).register();
        STAGE_LIGHT = CreateStats.registrate().blockEntity("stage_light", StageLightBE::new).validBlock(() -> ModBlocks.STAGE_LIGHT.get()).renderer(() -> com.createstats.renderer.StageLightRenderer::new).register();
        STAGE_LIGHT_MK2 = CreateStats.registrate().blockEntity("stage_light_mk2", StageLightMk2BE::new).validBlock(() -> ModBlocks.STAGE_LIGHT_MK2.get()).renderer(() -> com.createstats.renderer.StageLightRenderer::new).register();
        DATA_DIAL = CreateStats.registrate().blockEntity("data_dial", DataDialBE::new).validBlock(() -> ModBlocks.DATA_DIAL.get()).renderer(() -> DataDialRenderer::new).register();
        BATTLE_SCREEN_DISPLAY = CreateStats.registrate().blockEntity("battle_screen_display", BattleScreenDisplayBE::new).validBlock(() -> ModBlocks.BATTLE_SCREEN_DISPLAY.get()).renderer(() -> BattleScreenDisplayRenderer::new).register();
        DAMAGE_OBSERVER = CreateStats.registrate().blockEntity("damage_observer", DamageObserverBE::new).validBlock(() -> ModBlocks.DAMAGE_OBSERVER.get()).renderer(() -> DamageObserverRenderer::new).register();
        MINI_DISPLAY = CreateStats.registrate().blockEntity("mini_display", MiniDisplayBE::new).validBlock(() -> ModBlocks.MINI_DISPLAY.get()).renderer(() -> MiniDisplayRenderer::new).register();
        EINK_PANEL = CreateStats.registrate().blockEntity("eink_panel", com.createstats.blockentity.EInkPanelBE::new).validBlock(() -> ModBlocks.EINK_PANEL.get()).renderer(() -> com.createstats.renderer.EInkPanelRenderer::new).register();
        SMART_HOME_PANEL = CreateStats.registrate().blockEntity("smart_home_panel", com.createstats.blockentity.SmartHomePanelBE::new).validBlock(() -> ModBlocks.SMART_HOME_PANEL.get()).renderer(() -> com.createstats.renderer.SmartHomePanelRenderer::new).register();
        WEATHER_PANEL = CreateStats.registrate().blockEntity("weather_panel", com.createstats.blockentity.WeatherPanelBE::new).validBlock(() -> ModBlocks.WEATHER_PANEL.get()).renderer(() -> com.createstats.renderer.WeatherPanelRenderer::new).register();
        HOME_SECURITY_PANEL = CreateStats.registrate().blockEntity("home_security_panel", com.createstats.blockentity.HomeSecurityPanelBE::new).validBlock(() -> ModBlocks.HOME_SECURITY_PANEL.get()).register();
        BLAST_SHUTTER = CreateStats.registrate().blockEntity("blast_shutter", com.createstats.blockentity.BlastShutterBE::new).validBlocks(ModBlocks.blastShutterSuppliers()).renderer(() -> com.createstats.renderer.BlastShutterRenderer::new).register();
        SECURITY_LOCKER = CreateStats.registrate().blockEntity("security_locker", com.createstats.blockentity.SecurityLockerBE::new).validBlock(() -> ModBlocks.SECURITY_LOCKER.get()).renderer(() -> com.createstats.renderer.SecurityLockerRenderer::new).register();
        SECURITY_SENSOR = CreateStats.registrate().blockEntity("security_sensor", com.createstats.blockentity.SecuritySensorBE::new).validBlocks(ModBlocks.securitySensorSuppliers()).renderer(() -> com.createstats.renderer.SecuritySensorRenderer::new).register();
        SECURITY_RELAY = CreateStats.registrate().blockEntity("security_relay", com.createstats.blockentity.SecurityRelayBE::new).validBlock(() -> ModBlocks.SECURITY_RELAY.get()).renderer(() -> com.createstats.renderer.SecurityRelayRenderer::new).register();
        GARAGE_DOOR = CreateStats.registrate().blockEntity("garage_door", com.createstats.blockentity.GarageDoorBE::new).validBlock(() -> ModBlocks.GARAGE_DOOR_HEADER.get()).renderer(() -> com.createstats.renderer.GarageDoorRenderer::new).register();
        HOLOGRAPHIC_PROJECTOR = CreateStats.registrate().blockEntity("holographic_projector", HolographicProjectorBE::new).validBlock(() -> ModBlocks.HOLOGRAPHIC_PROJECTOR.get()).renderer(() -> HolographicProjectorRenderer::new).register();
        HOLOGRAPHIC_PROJECTOR_MK3 = CreateStats.registrate().blockEntity("holographic_projector_mk3", com.createstats.holomk3.HoloProjectorMk3BE::new).validBlock(() -> ModBlocks.HOLOGRAPHIC_PROJECTOR_MK3.get()).renderer(() -> com.createstats.holomk3.render.HoloProjectorMk3Renderer::new).register();
        CALCULATOR = CreateStats.registrate().blockEntity("calculator", CalculatorBE::new).validBlock(() -> ModBlocks.CALCULATOR.get()).renderer(() -> CalculatorRenderer::new).register();
        FACTORY_LOGIC_CONTROLLER = CreateStats.registrate().blockEntity("factory_logic_controller", FactoryLogicControllerBE::new).validBlock(() -> ModBlocks.FACTORY_LOGIC_CONTROLLER.get()).renderer(() -> FactoryLogicControllerRenderer::new).register();
        SAFETY_CLUTCH = CreateStats.registrate().blockEntity("safety_clutch", SafetyClutchBE::new).visual(() -> SafetyClutchVisual::new, false).validBlock(() -> ModBlocks.SAFETY_CLUTCH.get()).renderer(() -> SafetyClutchRenderer::new).register();
        AUTO_TRANSMISSION_CLUTCH = CreateStats.registrate().blockEntity("auto_transmission_clutch", AutoTransmissionClutchBE::new).visual(() -> AutoTransmissionClutchVisual::new, false).validBlock(() -> ModBlocks.AUTO_TRANSMISSION_CLUTCH.get()).renderer(() -> AutoTransmissionClutchRenderer::new).register();
        CONTROL_CLUTCH = CreateStats.registrate().blockEntity("control_clutch", ControlClutchBE::new).visual(() -> ControlClutchVisual::new, false).validBlock(() -> ModBlocks.CONTROL_CLUTCH.get()).renderer(() -> ControlClutchRenderer::new).register();
        LOAD_BALANCER = CreateStats.registrate().blockEntity("load_balancer", LoadBalancerBE::new).visual(() -> LoadBalancerVisual::new, false).validBlock(() -> ModBlocks.LOAD_BALANCER.get()).renderer(() -> LoadBalancerRenderer::new).register();
        SMART_GEARSHIFT = CreateStats.registrate().blockEntity("smart_gearshift", SmartGearshiftBE::new).validBlock(() -> ModBlocks.SMART_GEARSHIFT.get()).renderer(() -> SmartGearshiftRenderer::new).register();
        KINETIC_FLYWHEEL = CreateStats.registrate().blockEntity("kinetic_flywheel", KineticFlywheelBE::new).validBlock(() -> ModBlocks.KINETIC_FLYWHEEL.get()).renderer(() -> KineticFlywheelRenderer::new).register();
        INDUCTION_HEATER = CreateStats.registrate().blockEntity("induction_heater", InductionHeaterBE::new).validBlock(() -> ModBlocks.INDUCTION_HEATER.get()).renderer(() -> InductionHeaterRenderer::new).register();
        OVERFLOW_GATE = CreateStats.registrate().blockEntity("overflow_gate", OverflowGateBE::new).validBlock(() -> ModBlocks.OVERFLOW_GATE.get()).renderer(() -> OverflowGateRenderer::new).register();
        FREIGHT_MANIFEST_CLIPBOARD = CreateStats.registrate().blockEntity("freight_manifest_clipboard", FreightManifestClipboardBE::new).validBlock(() -> ModBlocks.FREIGHT_MANIFEST_CLIPBOARD.get()).renderer(() -> com.createstats.renderer.FreightManifestRenderer::new).register();
        DISPATCH_BOARD = CreateStats.registrate().blockEntity("dispatch_board", DispatchBoardBE::new).validBlock(() -> ModBlocks.DISPATCH_BOARD.get()).renderer(() -> DispatchBoardRenderer::new).register();
        RPM_GOVERNOR = CreateStats.registrate().blockEntity("rpm_governor", RpmGovernorBE::new).validBlock(() -> ModBlocks.RPM_GOVERNOR.get()).renderer(() -> RpmGovernorRenderer::new).register();
        FLIGHT_CONTROL_COMPUTER = CreateStats.registrate().blockEntity("flight_control_computer", com.createstats.flight.blockentity.FlightControlComputerBE::new).validBlock(() -> ModBlocks.FLIGHT_CONTROL_COMPUTER.get()).renderer(() -> FlightControlComputerRenderer::new).register();
        VCC_MK_II = CreateStats.registrate().blockEntity("vcc_mk2", com.createstats.vccmk2.VccMk2BlockEntity::new).validBlock(() -> ModBlocks.VCC_MK_II.get()).renderer(() -> com.createstats.vccmk2.VccMk2Renderer::new).register();
        VCC_MK3 = CreateStats.registrate().blockEntity("vcc_mk3", com.createstats.vccmk3.VccMk3BlockEntity::new).validBlock(() -> ModBlocks.VCC_MK3.get()).renderer(() -> com.createstats.renderer.VccMk3Renderer::new).register();
        VCC_MK4 = CreateStats.registrate().blockEntity("vcc_mk4", com.createstats.vccmk4.BtaG1BE::new).validBlock(() -> ModBlocks.VCC_MK4.get()).renderer(() -> com.createstats.renderer.BtaG1Renderer::new).register();
        BTA_G1 = CreateStats.registrate().blockEntity("bta_g1", com.createstats.vccmk4.BtaG1BE::new).validBlock(() -> ModBlocks.BTA_G1.get()).renderer(() -> com.createstats.renderer.BtaG1Renderer::new).register();
        VCC_MK4_AERIAL = CreateStats.registrate().blockEntity("vcc_mk4_aerial", com.createstats.vccmk4.air.AerialBE::new).validBlock(() -> ModBlocks.VCC_MK4_AERIAL.get()).validBlock(() -> ModBlocks.VCC_MK4_AERIAL_DECK.get()).renderer(() -> com.createstats.renderer.AerialCockpitRenderer::new).register();
        VCC_MK4_AERIAL_DECK_PART = CreateStats.registrate().blockEntity("vcc_mk4_aerial_flight_deck_part", com.createstats.vccmk4.air.AerialDeckPartBE::new).validBlock(() -> ModBlocks.VCC_MK4_AERIAL_DECK.get()).register();
        VEHICLE_LIGHT = CreateStats.registrate().blockEntity("vehicle_light", com.createstats.vehiclelight.VehicleLightBE::new).validBlocks(() -> ModBlocks.VEHICLE_LIGHT_WHITE.get(), () -> ModBlocks.VEHICLE_LIGHT_RED.get(), () -> ModBlocks.VEHICLE_LIGHT_AMBER.get()).renderer(() -> com.createstats.renderer.VehicleLightRenderer::new).register();
        DRIVER_HELMET = CreateStats.registrate().blockEntity("driver_helmet", com.createstats.driverhelmet.DriverHelmetBE::new).validBlock(() -> ModBlocks.DRIVER_HELMET.get()).renderer(() -> com.createstats.driverhelmet.client.DriverHelmetRenderer::new).register();
        AVIATOR_HELMET = CreateStats.registrate().blockEntity("aviator_helmet", com.createstats.aviatorhelmet.AviatorHelmetBE::new).validBlock(() -> ModBlocks.AVIATOR_HELMET.get()).renderer(() -> com.createstats.aviatorhelmet.client.AviatorHelmetRenderer::new).register();
        PATHFINDER_COMPUTER = CreateStats.registrate().blockEntity("pathfinder_computer", PathfinderComputerBE::new).validBlock(() -> ModBlocks.PATHFINDER_COMPUTER.get()).renderer(() -> PathfinderComputerRenderer::new).register();
        PATH_SCANNER = CreateStats.registrate().blockEntity("path_scanner", PathScannerBE::new).validBlock(() -> ModBlocks.PATH_SCANNER.get()).renderer(() -> PathScannerRenderer::new).register();

        ARC_FURNACE = CreateStats.registrate().blockEntity("arc_furnace", com.createstats.blockentity.ArcFurnaceBE::new).validBlock(() -> ModBlocks.ARC_FURNACE.get()).renderer(() -> com.createstats.renderer.ArcFurnaceRenderer::new).register();
        INDUCTION_SMELTER = CreateStats.registrate().blockEntity("induction_smelter", com.createstats.blockentity.InductionSmelterBE::new).validBlock(() -> ModBlocks.INDUCTION_SMELTER.get()).renderer(() -> com.createstats.renderer.InductionSmelterRenderer::new).register();
        INDUCTION_RANGE = CreateStats.registrate().blockEntity("induction_range", com.createstats.blockentity.InductionRangeBE::new).validBlock(() -> ModBlocks.INDUCTION_RANGE.get()).renderer(() -> com.createstats.renderer.InductionRangeRenderer::new).register();
        TOPOLOGY_SCANNER_INLINE = CreateStats.registrate().blockEntity("topology_scanner_inline", com.createstats.netscan.blockentity.NetscanInlineBE::new).visual(() -> com.createstats.renderer.NetscanInlineVisual::new, false).validBlocks(() -> ModBlocks.TOPOLOGY_SCANNER.get(), () -> ModBlocks.ADVANCED_TOPOLOGY_SCANNER.get(), () -> ModBlocks.INDUSTRIAL_TOPOLOGY_SCANNER.get()).renderer(() -> com.createstats.renderer.NetscanInlineRenderer::new).register();
        TOPOLOGY_SCANNER_PROBE = CreateStats.registrate().blockEntity("topology_scanner_probe", com.createstats.netscan.blockentity.NetscanProbeBE::new).validBlocks(() -> ModBlocks.TOPOLOGY_SCANNER_PROBE.get(), () -> ModBlocks.ADVANCED_TOPOLOGY_SCANNER_PROBE.get(), () -> ModBlocks.INDUSTRIAL_TOPOLOGY_SCANNER_PROBE.get()).register();
        KINETIC_DYNAMO = CreateStats.registrate().blockEntity("kinetic_dynamo", com.createstats.blockentity.KineticDynamoBE::new).validBlocks(() -> ModBlocks.KINETIC_DYNAMO.get(), () -> ModBlocks.OPEN_FRAME_DYNAMO.get(), () -> ModBlocks.INDUSTRIAL_ALTERNATOR.get()).renderer(() -> com.createstats.renderer.KineticDynamoRenderer::new).register();

        BIOFUEL_BURNER = CreateStats.registrate().blockEntity("biofuel_burner", com.createstats.blockentity.BiofuelBurnerBE::new).validBlock(() -> ModBlocks.BIOFUEL_BURNER.get()).register();

        STARTER_CAPACITOR = CreateStats.registrate().blockEntity("starter_capacitor", com.createstats.blockentity.StarterCapacitorBE::new).validBlock(() -> ModBlocks.STARTER_CAPACITOR.get()).renderer(() -> com.createstats.renderer.StarterCapacitorRenderer::new).register();

        SOLAR_PANEL = CreateStats.registrate().blockEntity("solar_panel", com.createstats.blockentity.SolarPanelBE::new).validBlocks(() -> ModBlocks.SOLAR_PANEL.get(), () -> ModBlocks.REINFORCED_SOLAR_PANEL.get(), () -> ModBlocks.PHOTOVOLTAIC_ARRAY.get()).register();
        INDOOR_CABLE = CreateStats.registrate().blockEntity("indoor_cable", com.createstats.blockentity.IndoorCableBE::new).validBlocks(() -> ModBlocks.INDOOR_CABLE.get()).renderer(() -> com.createstats.renderer.IndoorCableRenderer::new).register();

        CABLE = CreateStats.registrate().blockEntity("cable", com.createstats.blockentity.CableBE::new).validBlocks(() -> ModBlocks.SURFACE_CABLE.get(), () -> ModBlocks.CABLE_TRAY.get(), () -> ModBlocks.BUS_DUCT.get(), () -> ModBlocks.BUSBAR_TRUNK.get()).renderer(() -> com.createstats.renderer.CableRenderer::new).register();
        SWITCH = CreateStats.registrate().blockEntity("switch", com.createstats.blockentity.SwitchBE::new).validBlocks(() -> ModBlocks.ROCKER_SWITCH.get(), () -> ModBlocks.TOGGLE_SWITCH.get(), () -> ModBlocks.DOUBLE_SWITCH.get(), () -> ModBlocks.ISOLATOR_SWITCH.get(), () -> ModBlocks.PUSH_BUTTON.get(), () -> ModBlocks.ROTARY_DIMMER_DIAL.get(), () -> ModBlocks.ROTARY_DIMMER_PUCK.get(), () -> ModBlocks.TIME_SWITCH.get()).renderer(() -> com.createstats.renderer.SwitchRenderer::new).register();
        MAIN_BREAKER = CreateStats.registrate().blockEntity("main_breaker", com.createstats.blockentity.MainBreakerBE::new).validBlocks(() -> ModBlocks.MAIN_BREAKER.get(), () -> ModBlocks.CREATIVE_MAIN_BREAKER.get()).renderer(() -> com.createstats.renderer.MainBreakerRenderer::new).register();

        CONTACTOR = CreateStats.registrate().blockEntity("contactor", com.createstats.blockentity.ContactorBE::new).validBlock(() -> ModBlocks.CONTACTOR.get()).renderer(() -> com.createstats.renderer.ContactorRenderer::new).register();
        LAMP = CreateStats.registrate().blockEntity("lamp", com.createstats.blockentity.LampBE::new).validBlocks(() -> ModBlocks.CAGED_BULKHEAD_LAMP.get(), () -> ModBlocks.HIGH_BAY_LAMP.get(), () -> ModBlocks.STRIP_LAMP.get(), () -> ModBlocks.DOME_LAMP.get(), () -> ModBlocks.WALL_SCONCE.get(), () -> ModBlocks.PANEL_LAMP.get(), () -> ModBlocks.PENDANT_LAMP.get(), () -> ModBlocks.FLOOD_LAMP.get(), () -> ModBlocks.VAPOUR_TIGHT_LAMP.get(), () -> ModBlocks.WELL_GLASS_LAMP.get(), () -> ModBlocks.FILAMENT_BULB.get(), () -> ModBlocks.EMERGENCY_LAMP.get(), () -> ModBlocks.BANKERS_LAMP.get()).renderer(() -> com.createstats.renderer.LampRenderer::new).register();
        POWER_CONNECTOR = CreateStats.registrate().blockEntity("power_connector", com.createstats.blockentity.PowerConnectorBE::new).validBlocks(() -> ModBlocks.POWER_CONNECTOR.get(), () -> ModBlocks.ADVANCED_POWER_CONNECTOR.get(), () -> ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get()).renderer(() -> com.createstats.renderer.PowerConnectorRenderer::new).register();
        DIAGNOSTIC_JUNCTION = CreateStats.registrate().blockEntity("diagnostic_junction", com.createstats.blockentity.DiagnosticJunctionBE::new).validBlocks(() -> ModBlocks.CABLE_DIAGNOSTIC_JUNCTION.get(), () -> ModBlocks.WIRE_TERMINAL_JUNCTION.get()).renderer(() -> com.createstats.renderer.DiagnosticJunctionRenderer::new).register();
        CAPACITOR_BANK = CreateStats.registrate().blockEntity("capacitor_bank", com.createstats.blockentity.CapacitorBankBE::new).validBlock(() -> ModBlocks.CAPACITOR_BANK.get()).renderer(() -> com.createstats.renderer.CapacitorBankRenderer::new).register();
        POWER_DISTRIBUTION_UNIT = CreateStats.registrate().blockEntity("power_distribution_unit", com.createstats.blockentity.PowerDistributionUnitBE::new).validBlock(() -> ModBlocks.POWER_DISTRIBUTION_UNIT.get()).renderer(() -> com.createstats.renderer.PowerDistributionUnitRenderer::new).register();
        PDU_CABINET = CreateStats.registrate().blockEntity("pdu_cabinet", com.createstats.blockentity.PduCabinetBE::new).validBlock(() -> ModBlocks.PDU_CABINET.get()).renderer(() -> com.createstats.pducabinet.client.CabinetRenderer::new).register();
        BATTERY_CONTROLLER = CreateStats.registrate().blockEntity("battery_controller", com.createstats.blockentity.BatteryControllerBE::new).validBlock(() -> ModBlocks.FE_BATTERY_CONTROLLER.get()).renderer(() -> com.createstats.renderer.BatteryControllerRenderer::new).register();
        PORTABLE_POWER_INTERFACE = CreateStats.registrate().blockEntity("portable_power_interface", com.createstats.blockentity.PortablePowerInterfaceBE::new).validBlock(() -> ModBlocks.PORTABLE_POWER_INTERFACE.get()).renderer(() -> com.createstats.renderer.PortablePowerInterfaceRenderer::new).register();
        TRACK_FEEDER = CreateStats.registrate().blockEntity("track_feeder", com.createstats.blockentity.TrackFeederBE::new).validBlock(() -> ModBlocks.TRACK_FEEDER.get()).renderer(() -> com.createstats.powertrack.client.TrackFeederRenderer::new).register();
        SECTION_INSULATOR = CreateStats.registrate().blockEntity("section_insulator", com.createstats.powertrack.SectionInsulatorBE::new).validBlock(() -> ModBlocks.SECTION_INSULATOR.get()).renderer(() -> com.createstats.powertrack.client.SectionInsulatorRenderer::new).register();
        CURRENT_COLLECTOR = CreateStats.registrate().blockEntity("current_collector", com.createstats.powertrack.CurrentCollectorBE::new).validBlock(() -> ModBlocks.CURRENT_COLLECTOR.get()).renderer(() -> com.createstats.powertrack.client.CurrentCollectorRenderer::new).register();
        FLUID_PUMP = CreateStats.registrate().blockEntity("fluid_pump", com.createstats.blockentity.FluidPumpBE::new).validBlocks(() -> ModBlocks.FLUID_PUMP_MK1.get(), () -> ModBlocks.FLUID_PUMP_MK2.get()).renderer(() -> com.createstats.renderer.FluidPumpRenderer::new).register();
        FE_MOTOR = CreateStats.registrate().blockEntity("fe_motor", com.createstats.blockentity.FeMotorBE::new).validBlocks(() -> ModBlocks.FE_MOTOR_MK1.get(), () -> ModBlocks.FE_MOTOR_MK2.get()).renderer(() -> com.createstats.renderer.FeMotorRenderer::new).register();
        COFFEE_MACHINE = CreateStats.registrate().blockEntity("coffee_machine", com.createstats.blockentity.CoffeeMachineBE::new).validBlock(() -> ModBlocks.COFFEE_MACHINE.get()).renderer(() -> com.createstats.renderer.CoffeeMachineRenderer::new).register();
        ANNOUNCEMENT_COMPUTER = CreateStats.registrate().blockEntity("pa_brain", com.createstats.blockentity.AnnouncementComputerBE::new).validBlock(() -> ModBlocks.ANNOUNCEMENT_COMPUTER.get()).renderer(() -> com.createstats.renderer.AnnouncementComputerRenderer::new).register();
        WATER_COOLER = CreateStats.registrate().blockEntity("water_cooler", com.createstats.blockentity.WaterCoolerBE::new).validBlock(() -> ModBlocks.WATER_COOLER.get()).renderer(() -> com.createstats.renderer.WaterCoolerRenderer::new).register();
        CINE_CAMERA = CreateStats.registrate().blockEntity("cine_camera", com.createstats.cinecam.CineCameraBE::new).validBlock(() -> ModBlocks.CINE_CAMERA.get()).renderer(() -> com.createstats.cinecam.client.CineCameraRenderer::new).register();
        AIR_FRYER = CreateStats.registrate().blockEntity("air_fryer", com.createstats.blockentity.AirFryerBE::new).validBlock(() -> ModBlocks.AIR_FRYER.get()).renderer(() -> com.createstats.renderer.AirFryerRenderer::new).register();
        BULK_WASHER = CreateStats.registrate().blockEntity("bulk_washer", com.createstats.blockentity.BulkWasherBE::new).validBlock(() -> ModBlocks.BULK_WASHER.get()).renderer(() -> com.createstats.renderer.BulkWasherRenderer::new).register();
        TOASTER = CreateStats.registrate().blockEntity("toaster", com.createstats.blockentity.ToasterBE::new).validBlock(() -> ModBlocks.TOASTER.get()).renderer(() -> com.createstats.renderer.ToasterRenderer::new).register();
        OVEN = CreateStats.registrate().blockEntity("oven", com.createstats.blockentity.OvenBE::new).validBlock(() -> ModBlocks.OVEN.get()).renderer(() -> com.createstats.renderer.OvenRenderer::new).register();
        VACUUM_COLLECTOR = CreateStats.registrate().blockEntity("vacuum_collector", com.createstats.blockentity.VacuumCollectorBE::new).validBlock(() -> ModBlocks.VACUUM_COLLECTOR.get()).renderer(() -> com.createstats.renderer.VacuumCollectorRenderer::new).register();
        VACUUM_CLEANER = CreateStats.registrate().blockEntity("vacuum_cleaner", com.createstats.blockentity.VacuumCleanerBE::new).validBlock(() -> ModBlocks.VACUUM_CLEANER.get()).renderer(() -> com.createstats.renderer.VacuumCleanerRenderer::new).register();
        VACUUM_SNIFFER = CreateStats.registrate().blockEntity("vacuum_sniffer", com.createstats.blockentity.VacuumSnifferBE::new).validBlock(() -> ModBlocks.VACUUM_SNIFFER.get()).renderer(() -> com.createstats.renderer.VacuumSnifferRenderer::new).register();
        MOB_REPELLER = CreateStats.registrate().blockEntity("mob_repeller", com.createstats.blockentity.MobRepellerBE::new).validBlock(() -> ModBlocks.MOB_REPELLER.get()).renderer(() -> com.createstats.renderer.MobRepellerRenderer::new).register();

        STORM_COIL = CreateStats.registrate().blockEntity("storm_coil", com.createstats.blockentity.StormCoilBE::new).validBlock(() -> ModBlocks.STORM_COIL.get()).renderer(() -> com.createstats.renderer.StormCoilRenderer::new).register();

        SPARK_CROWN = CreateStats.registrate().blockEntity("spark_crown", com.createstats.blockentity.SparkCrownBE::new).validBlock(() -> ModBlocks.SPARK_CROWN.get()).renderer(() -> com.createstats.renderer.SparkCrownRenderer::new).register();
        SEISMIC_SCANNER = CreateStats.registrate().blockEntity("seismic_scanner", com.createstats.blockentity.SeismicScannerBE::new).validBlock(() -> ModBlocks.SEISMIC_SCANNER.get()).renderer(() -> com.createstats.seismic.client.SeismicScannerRenderer::new).register();
        BREAD_SLICER = CreateStats.registrate().blockEntity("bread_slicer", com.createstats.blockentity.BreadSlicerBE::new).validBlock(() -> ModBlocks.BREAD_SLICER.get()).renderer(() -> com.createstats.renderer.BreadSlicerRenderer::new).register();

        POWERED_DOOR = CreateStats.registrate().blockEntity("powered_door", com.createstats.blockentity.PoweredDoorBE::new).validBlocks(ModBlocks.poweredDoorSuppliers()).renderer(() -> com.createstats.renderer.PoweredDoorRenderer::new).register();

        PA_SPEAKER = CreateStats.registrate()
                .<com.createstats.blockentity.PaSpeakerBE>blockEntity("pa_speaker",
                (type, pos, state) -> new com.createstats.blockentity.PaSpeakerBE(type, pos, state,
                        com.createstats.pa.SpeakerKind.SPEAKER))
                .validBlock(() -> ModBlocks.PA_SPEAKER.get())
                .renderer(() -> com.createstats.renderer.PaSpeakerRenderer::new).register();
        PA_MEGAPHONE = CreateStats.registrate()
                .<com.createstats.blockentity.PaSpeakerBE>blockEntity("pa_megaphone",
                (type, pos, state) -> new com.createstats.blockentity.PaSpeakerBE(type, pos, state,
                        com.createstats.pa.SpeakerKind.MEGAPHONE))
                .validBlock(() -> ModBlocks.PA_MEGAPHONE.get())
                .renderer(() -> com.createstats.renderer.PaSpeakerRenderer::new).register();
    }
}
