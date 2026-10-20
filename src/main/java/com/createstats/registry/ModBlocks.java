package com.createstats.registry;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.createstats.CreateStats;
import com.createstats.block.BattleScreenDisplayBlock;
import com.createstats.block.CalculatorBlock;
import com.createstats.block.DamageObserverBlock;
import com.createstats.block.DispatchBoardBlock;
import com.createstats.block.FactoryLogicControllerBlock;
import com.createstats.block.FlowMeterBlock;
import com.createstats.block.FreightManifestClipboardBlock;
import com.createstats.block.HolographicProjectorBlock;
import com.createstats.block.InductionHeaterBlock;
import com.createstats.block.LoadBalancerBlock;
import com.createstats.block.MiniDisplayBlock;
import com.createstats.block.OverflowGateBlock;
import com.createstats.block.AutoTransmissionClutchBlock;
import com.createstats.block.ControlClutchBlock;
import com.createstats.block.DataDialBlock;
import com.createstats.block.KineticFlywheelBlock;
import com.createstats.block.SmartGearshiftBlock;
import com.createstats.block.StatusTowerLightBlock;
import com.createstats.block.StageLightBlock;
import com.createstats.block.StageLightMk2Block;
import com.createstats.block.ProjectedLightBlock;
import com.createstats.block.SafetyClutchBlock;
import com.createstats.block.StatsAlarmBlock;
import com.createstats.block.TableAlarmClockBlock;
import com.createstats.block.ProductionDetectorBlock;
import com.createstats.block.StatsRecorderBlock;
import com.createstats.block.StatsDisplayPanelBlock;
import com.createstats.flight.block.FlightControlComputerBlock;
import com.createstats.flight.block.RpmGovernorBlock;
import com.createstats.item.DispatchBoardItem;
import com.createstats.item.FreightManifestClipboardItem;
import com.createstats.item.OverflowGateItem;
import com.createstats.pathfinder.block.PathScannerBlock;
import com.createstats.pathfinder.block.PathfinderComputerBlock;
import com.tterrag.registrate.util.entry.BlockEntry;

public class ModBlocks
{
    public static final BlockEntry<StatsDisplayPanelBlock> STATS_DISPLAY_PANEL;
    public static final BlockEntry<com.createstats.statsmk2.StatsDisplayMk2Block> STATS_DISPLAY_MK2;
    public static final BlockEntry<com.createstats.statsmk3.Mk3WallBlock> STATS_DISPLAY_MK3;
    public static final BlockEntry<com.createstats.statsmk3.Mk3ConsoleBlock> STATS_DISPLAY_MK3_CONSOLE;
    public static final BlockEntry<StatsRecorderBlock> STATS_RECORDER;
    public static final BlockEntry<ProductionDetectorBlock> PRODUCTION_DETECTOR;
    public static final BlockEntry<StatsAlarmBlock> STATS_ALARM;

    public static final BlockEntry<com.createstats.block.KlaxonAlarmBlock> KLAXON_ALARM;

    public static final BlockEntry<com.createstats.block.SentryAlarmBlock> SENTRY_ALARM;
    public static final BlockEntry<TableAlarmClockBlock> TABLE_ALARM_CLOCK;
    public static final BlockEntry<StatusTowerLightBlock> STATUS_TOWER_LIGHT;
    public static final BlockEntry<StageLightBlock> STAGE_LIGHT;
    public static final BlockEntry<StageLightMk2Block> STAGE_LIGHT_MK2;
    public static final BlockEntry<ProjectedLightBlock> PROJECTED_LIGHT;
    public static final BlockEntry<DataDialBlock> DATA_DIAL;
    public static final BlockEntry<BattleScreenDisplayBlock> BATTLE_SCREEN_DISPLAY;
    public static final BlockEntry<DamageObserverBlock> DAMAGE_OBSERVER;
    public static final BlockEntry<MiniDisplayBlock> MINI_DISPLAY;
    public static final BlockEntry<HolographicProjectorBlock> HOLOGRAPHIC_PROJECTOR;

    public static final BlockEntry<com.createstats.holomk3.HoloProjectorMk3Block> HOLOGRAPHIC_PROJECTOR_MK3;
    public static final BlockEntry<CalculatorBlock> CALCULATOR;
    public static final BlockEntry<FactoryLogicControllerBlock> FACTORY_LOGIC_CONTROLLER;
    public static final BlockEntry<SafetyClutchBlock> SAFETY_CLUTCH;
    public static final BlockEntry<AutoTransmissionClutchBlock> AUTO_TRANSMISSION_CLUTCH;
    public static final BlockEntry<ControlClutchBlock> CONTROL_CLUTCH;
    public static final BlockEntry<LoadBalancerBlock> LOAD_BALANCER;
    public static final BlockEntry<SmartGearshiftBlock> SMART_GEARSHIFT;
    public static final BlockEntry<KineticFlywheelBlock> KINETIC_FLYWHEEL;
    public static final BlockEntry<InductionHeaterBlock> INDUCTION_HEATER;
    public static final BlockEntry<FlowMeterBlock> FLOW_METER;
    public static final BlockEntry<com.createstats.block.PowerMeterBlock> POWER_METER;
    public static final BlockEntry<com.createstats.block.PowerLimiterBlock> POWER_LIMITER;
    public static final BlockEntry<com.createstats.block.UniversalPowerCouplerBlock> UNIVERSAL_POWER_COUPLER;
    public static final BlockEntry<OverflowGateBlock> OVERFLOW_GATE;
    public static final BlockEntry<FreightManifestClipboardBlock> FREIGHT_MANIFEST_CLIPBOARD;
    public static final BlockEntry<DispatchBoardBlock> DISPATCH_BOARD;
    public static final BlockEntry<RpmGovernorBlock> RPM_GOVERNOR;
    public static final BlockEntry<FlightControlComputerBlock> FLIGHT_CONTROL_COMPUTER;
    public static final BlockEntry<com.createstats.vccmk2.VccMk2Block> VCC_MK_II;
    public static final BlockEntry<com.createstats.vccmk3.VccMk3Block> VCC_MK3;

    public static final BlockEntry<com.createstats.vccmk4.Vcc4Block> VCC_MK4;

    public static final BlockEntry<com.createstats.vccmk4.BtaG1Block> BTA_G1;

    public static final BlockEntry<com.createstats.vccmk4.air.AerialBlock> VCC_MK4_AERIAL;

    public static final BlockEntry<com.createstats.vccmk4.air.AerialDeckBlock> VCC_MK4_AERIAL_DECK;

    public static final BlockEntry<com.createstats.vehiclelight.VehicleLightBlock> VEHICLE_LIGHT_WHITE;
    public static final BlockEntry<com.createstats.vehiclelight.VehicleLightBlock> VEHICLE_LIGHT_RED;
    public static final BlockEntry<com.createstats.vehiclelight.VehicleLightBlock> VEHICLE_LIGHT_AMBER;

    public static final BlockEntry<com.createstats.driverhelmet.DriverHelmetBlock> DRIVER_HELMET;

    public static final BlockEntry<com.createstats.aviatorhelmet.AviatorHelmetBlock> AVIATOR_HELMET;
    public static final BlockEntry<PathfinderComputerBlock> PATHFINDER_COMPUTER;
    public static final BlockEntry<PathScannerBlock> PATH_SCANNER;
    public static final BlockEntry<com.createstats.block.KineticDynamoBlock> KINETIC_DYNAMO;
    public static final BlockEntry<com.createstats.block.InductionRangeBlock> INDUCTION_RANGE;

    public static final BlockEntry<com.createstats.block.ArcFurnaceBlock> ARC_FURNACE;

    public static final BlockEntry<com.createstats.block.InductionSmelterBlock> INDUCTION_SMELTER;
    public static final BlockEntry<com.createstats.block.KineticDynamoBlock> OPEN_FRAME_DYNAMO;
    public static final BlockEntry<com.createstats.block.KineticDynamoBlock> INDUSTRIAL_ALTERNATOR;

    public static final BlockEntry<com.createstats.block.BiofuelBurnerBlock> BIOFUEL_BURNER;

    public static final BlockEntry<com.createstats.block.SolarPanelBlock> SOLAR_PANEL;
    public static final BlockEntry<com.createstats.block.SolarPanelBlock> REINFORCED_SOLAR_PANEL;
    public static final BlockEntry<com.createstats.block.SolarPanelBlock> PHOTOVOLTAIC_ARRAY;

    public static final BlockEntry<com.createstats.block.StarterCapacitorBlock> STARTER_CAPACITOR;
    public static final BlockEntry<com.createstats.block.LampBlock> CAGED_BULKHEAD_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> HIGH_BAY_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> STRIP_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> DOME_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> WALL_SCONCE;
    public static final BlockEntry<com.createstats.block.LampBlock> PANEL_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> PENDANT_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> FLOOD_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> VAPOUR_TIGHT_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> WELL_GLASS_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> FILAMENT_BULB;
    public static final BlockEntry<com.createstats.block.LampBlock> EMERGENCY_LAMP;
    public static final BlockEntry<com.createstats.block.LampBlock> BANKERS_LAMP;
    public static final BlockEntry<com.createstats.block.SwitchBlock> ROCKER_SWITCH;
    public static final BlockEntry<com.createstats.block.SwitchBlock> TOGGLE_SWITCH;
    public static final BlockEntry<com.createstats.block.SwitchBlock> DOUBLE_SWITCH;
    public static final BlockEntry<com.createstats.block.SwitchBlock> ISOLATOR_SWITCH;
    public static final BlockEntry<com.createstats.block.SwitchBlock> PUSH_BUTTON;
    public static final BlockEntry<com.createstats.block.RotaryDimmerBlock> ROTARY_DIMMER_DIAL;
    public static final BlockEntry<com.createstats.block.RotaryDimmerBlock> ROTARY_DIMMER_PUCK;
    public static final BlockEntry<com.createstats.block.TimeSwitchBlock> TIME_SWITCH;

    public static final BlockEntry<com.createstats.block.ContactorBlock> CONTACTOR;
    public static final BlockEntry<com.createstats.block.MainBreakerBlock> MAIN_BREAKER;
    public static final BlockEntry<com.createstats.block.MainBreakerBlock> CREATIVE_MAIN_BREAKER;
    public static final BlockEntry<com.createstats.block.CableBlock> SURFACE_CABLE;
    public static final BlockEntry<com.createstats.block.CableBlock> CABLE_TRAY;
    public static final BlockEntry<com.createstats.block.CableBlock> BUS_DUCT;

    public static final BlockEntry<com.createstats.block.CableBlock> BUSBAR_TRUNK;

    public static final BlockEntry<com.createstats.block.IndoorCableBlock> INDOOR_CABLE;
    public static final BlockEntry<com.createstats.block.PowerConnectorBlock> POWER_CONNECTOR;
    public static final BlockEntry<com.createstats.block.PowerConnectorBlock> ADVANCED_POWER_CONNECTOR;
    public static final BlockEntry<com.createstats.block.PowerConnectorBlock> INDUSTRIAL_POWER_CONNECTOR;
    public static final BlockEntry<com.createstats.block.CapacitorBankBlock> CAPACITOR_BANK;
    public static final BlockEntry<com.createstats.block.PowerDistributionUnitBlock> POWER_DISTRIBUTION_UNIT;
    public static final BlockEntry<com.createstats.block.PduCabinetBlock> PDU_CABINET;
    public static final BlockEntry<com.createstats.block.BatteryControllerBlock> FE_BATTERY_CONTROLLER;
    public static final BlockEntry<com.createstats.block.PortablePowerInterfaceBlock> PORTABLE_POWER_INTERFACE;

    public static final BlockEntry<com.createstats.powertrack.PowerTrackBlock> POWER_TRACK;
    public static final BlockEntry<com.createstats.powertrack.TrackFeederBlock> TRACK_FEEDER;
    public static final BlockEntry<com.createstats.powertrack.SectionInsulatorBlock> SECTION_INSULATOR;
    public static final BlockEntry<com.createstats.powertrack.CurrentCollectorBlock> CURRENT_COLLECTOR;
    public static final BlockEntry<com.createstats.block.FluidPumpBlock> FLUID_PUMP_MK1;
    public static final BlockEntry<com.createstats.block.FluidPumpBlock> FLUID_PUMP_MK2;
    public static final BlockEntry<com.createstats.block.FeMotorBlock> FE_MOTOR_MK1;
    public static final BlockEntry<com.createstats.block.FeMotorBlock> FE_MOTOR_MK2;
    public static final BlockEntry<com.createstats.block.FloorGratingBlock> FLOOR_GRATING;
    public static final BlockEntry<com.createstats.block.HazardTrimBlock> HAZARD_TRIM;
    public static final BlockEntry<com.createstats.block.GuardRailingBlock> GUARD_RAILING;
    public static final BlockEntry<com.createstats.block.WallPlateBlock> CONDUIT_JUNCTION_BOX;
    public static final BlockEntry<com.createstats.block.DiagnosticJunctionBlock> CABLE_DIAGNOSTIC_JUNCTION;
    public static final BlockEntry<com.createstats.block.DiagnosticJunctionBlock> WIRE_TERMINAL_JUNCTION;
    public static final BlockEntry<com.createstats.block.WallOutletBlock> POWER_OUTLET;
    public static final BlockEntry<com.createstats.block.WallOutletBlock> DUPLEX_POWER_OUTLET;
    public static final BlockEntry<com.createstats.block.BlankMonitorBezelBlock> BLANK_MONITOR_BEZEL;
    public static final BlockEntry<com.createstats.block.EInkPanelBlock> EINK_PANEL;
    public static final BlockEntry<com.createstats.block.SmartHomePanelBlock> SMART_HOME_PANEL;
    public static final BlockEntry<com.createstats.block.WeatherPanelBlock> WEATHER_PANEL;
    public static final BlockEntry<com.createstats.block.SecurityLockerBlock> SECURITY_LOCKER;
    public static final BlockEntry<com.createstats.block.HomeSecurityPanelBlock> HOME_SECURITY_PANEL;
    public static final BlockEntry<com.createstats.block.SecurityRelayBlock> SECURITY_RELAY;
    public static final BlockEntry<com.createstats.block.CoffeeMachineBlock> COFFEE_MACHINE;
    public static final BlockEntry<com.createstats.block.WaterCoolerBlock> WATER_COOLER;
    public static final BlockEntry<com.createstats.cinecam.CineCameraBlock> CINE_CAMERA;
    public static final BlockEntry<com.createstats.block.AirFryerBlock> AIR_FRYER;
    public static final BlockEntry<com.createstats.block.BulkWasherBlock> BULK_WASHER;
    public static final BlockEntry<com.createstats.block.ToasterBlock> TOASTER;
    public static final BlockEntry<com.createstats.block.BreadSlicerBlock> BREAD_SLICER;
    public static final BlockEntry<com.createstats.block.VacuumCollectorBlock> VACUUM_COLLECTOR;
    public static final BlockEntry<com.createstats.block.VacuumCleanerBlock> VACUUM_CLEANER;
    public static final BlockEntry<com.createstats.block.VacuumSnifferBlock> VACUUM_SNIFFER;
    public static final BlockEntry<com.createstats.block.MobRepellerBlock> MOB_REPELLER;

    public static final BlockEntry<com.createstats.block.StormCoilBlock> STORM_COIL;

    public static final BlockEntry<com.createstats.block.StormCoilPartBlock> STORM_COIL_PART;

    public static final BlockEntry<com.createstats.block.SparkCrownBlock> SPARK_CROWN;

    public static final BlockEntry<com.createstats.block.SparkCrownStructureBlock> SPARK_CROWN_STRUCTURE;

    public static final BlockEntry<com.createstats.block.SeismicScannerBlock> SEISMIC_SCANNER;
    public static final BlockEntry<com.createstats.block.OvenBlock> OVEN;

    private static final java.util.EnumMap<com.createstats.baking.CakeFlavor,
            BlockEntry<com.createstats.block.FlavouredCakeBlock>> CAKES =
            new java.util.EnumMap<>(com.createstats.baking.CakeFlavor.class);

    public static final BlockEntry<com.createstats.block.HarvestBushBlock> BLUEBERRY_BUSH;
    public static final BlockEntry<com.createstats.block.HarvestBushBlock> CINNAMON_SHRUB;
    public static final BlockEntry<com.createstats.block.VanillaBeanBlock> VANILLA_BEAN_VINE;
    public static final BlockEntry<com.createstats.block.SimpleCropBlock> STRAWBERRY_CROP;
    public static final BlockEntry<com.createstats.block.PaSpeakerBlock> PA_SPEAKER;
    public static final BlockEntry<com.createstats.block.PaSpeakerBlock> PA_MEGAPHONE;
    public static final BlockEntry<com.createstats.block.MugBlock> CERAMIC_MUG;
    public static final BlockEntry<com.createstats.block.MugBlock> ENAMEL_MUG;
    public static final BlockEntry<com.createstats.block.MugBlock> STEEL_MUG;

    private static final java.util.EnumMap<com.createstats.block.DoorMaterial,
            BlockEntry<com.createstats.block.PoweredDoorBlock>> POWERED_DOORS =
            new java.util.EnumMap<>(com.createstats.block.DoorMaterial.class);

    private static final java.util.EnumMap<com.createstats.security.SensorKind,
            BlockEntry<com.createstats.block.SecuritySensorBlock>> SECURITY_SENSORS =
            new java.util.EnumMap<>(com.createstats.security.SensorKind.class);

    private static final java.util.EnumMap<com.createstats.block.ShutterTier,
            BlockEntry<com.createstats.block.BlastShutterBlock>> BLAST_SHUTTERS =
            new java.util.EnumMap<>(com.createstats.block.ShutterTier.class);

    public static final BlockEntry<com.createstats.block.GarageDoorHeaderBlock> GARAGE_DOOR_HEADER;
    public static final BlockEntry<com.createstats.block.GarageDoorBodyBlock> GARAGE_DOOR_BODY;

    public static final BlockEntry<com.createstats.block.AnnouncementComputerBlock> ANNOUNCEMENT_COMPUTER;

    public static final BlockEntry<com.createstats.netscan.block.NetscanInlineBlock> TOPOLOGY_SCANNER;
    public static final BlockEntry<com.createstats.netscan.block.NetscanInlineBlock> ADVANCED_TOPOLOGY_SCANNER;
    public static final BlockEntry<com.createstats.netscan.block.NetscanInlineBlock> INDUSTRIAL_TOPOLOGY_SCANNER;
    public static final BlockEntry<com.createstats.netscan.block.NetscanProbeBlock> TOPOLOGY_SCANNER_PROBE;
    public static final BlockEntry<com.createstats.netscan.block.NetscanProbeBlock> ADVANCED_TOPOLOGY_SCANNER_PROBE;
    public static final BlockEntry<com.createstats.netscan.block.NetscanProbeBlock> INDUSTRIAL_TOPOLOGY_SCANNER_PROBE;

    public static void register() {
        CreateStats.LOGGER.debug("Registering blocks for {}", "create_stats");
    }

    static {
        STATS_DISPLAY_PANEL = CreateStats.registrate().block("stats_display_panel", StatsDisplayPanelBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> 7)).simpleItem().register();
        STATS_DISPLAY_MK2 = CreateStats.registrate().block("stats_display_mk2", com.createstats.statsmk2.StatsDisplayMk2Block::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> 7)).simpleItem().register();
        STATS_DISPLAY_MK3 = CreateStats.registrate().block("stats_display_mk3", com.createstats.statsmk3.Mk3WallBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 7 : 0)).simpleItem().register();
        STATS_DISPLAY_MK3_CONSOLE = CreateStats.registrate().block("stats_display_mk3_console", com.createstats.statsmk3.Mk3ConsoleBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 7 : 0)).simpleItem().register();
        STATS_RECORDER = CreateStats.registrate().block("stats_recorder", StatsRecorderBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.0f, 6.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        PRODUCTION_DETECTOR = CreateStats.registrate().block("production_detector", ProductionDetectorBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BROWN).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        STATS_ALARM = CreateStats.registrate().block("stats_alarm", StatsAlarmBlock::new).properties(p -> p.mapColor(MapColor.COLOR_RED).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(StatsAlarmBlock.ACTIVE) ? 15 : 0)).simpleItem().register();

        KLAXON_ALARM = CreateStats.registrate().block("klaxon_alarm", com.createstats.block.KlaxonAlarmBlock::new).properties(p -> p.mapColor(MapColor.COLOR_RED).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(com.createstats.block.AnnunciatorBlock::lightLevel)).simpleItem().register();
        SENTRY_ALARM = CreateStats.registrate().block("sentry_alarm", com.createstats.block.SentryAlarmBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(com.createstats.block.AnnunciatorBlock::lightLevel)).simpleItem().register();
        TABLE_ALARM_CLOCK = CreateStats.registrate().block("table_alarm_clock", TableAlarmClockBlock::new).properties(p -> p.mapColor(MapColor.WOOD).strength(1.5f, 2.5f).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> s.getValue(TableAlarmClockBlock.ACTIVE) ? 10 : 0)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        STATUS_TOWER_LIGHT = CreateStats.registrate().block("status_tower_light", StatusTowerLightBlock::new).properties(p -> p.mapColor(MapColor.COLOR_YELLOW).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(StatusTowerLightBlock::getLightLevel)).simpleItem().register();
        STAGE_LIGHT = CreateStats.registrate().block("stage_light", StageLightBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(StageLightBlock::getLightEmission)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        STAGE_LIGHT_MK2 = CreateStats.registrate().block("stage_light_mk2", StageLightMk2Block::new).properties(p -> p.mapColor(MapColor.GOLD).strength(2.5f, 4.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(StageLightBlock::getLightEmission)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        PROJECTED_LIGHT = CreateStats.registrate().block("projected_light", ProjectedLightBlock::new).properties(p -> p.mapColor(MapColor.NONE).noCollission().noLootTable().replaceable().instabreak().noOcclusion().sound(SoundType.WOOL).lightLevel(ProjectedLightBlock::lightLevel)).register();
        DATA_DIAL = CreateStats.registrate().block("data_dial", DataDialBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        BATTLE_SCREEN_DISPLAY = CreateStats.registrate().block("battle_screen_display", BattleScreenDisplayBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(BattleScreenDisplayBlock.ACTIVE) ? 8 : 0)).simpleItem().register();
        DAMAGE_OBSERVER = CreateStats.registrate().block("damage_observer", DamageObserverBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(DamageObserverBlock.ACTIVE) ? 8 : 0)).simpleItem().register();
        MINI_DISPLAY = CreateStats.registrate().block("mini_display", MiniDisplayBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(MiniDisplayBlock.ACTIVE) ? 8 : 0)).simpleItem().register();
        HOLOGRAPHIC_PROJECTOR = CreateStats.registrate().block("holographic_projector", HolographicProjectorBlock::new).properties(p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> 10)).simpleItem().register();
        HOLOGRAPHIC_PROJECTOR_MK3 = CreateStats.registrate().block("holographic_projector_mk3", com.createstats.holomk3.HoloProjectorMk3Block::new).properties(p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.5f, 4.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(com.createstats.holomk3.HoloProjectorMk3Block::lightLevel)).simpleItem().register();
        CALCULATOR = CreateStats.registrate().block("calculator", CalculatorBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> 7)).simpleItem().register();
        FACTORY_LOGIC_CONTROLLER = CreateStats.registrate().block("factory_logic_controller", FactoryLogicControllerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.5f, 4.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> 6)).simpleItem().register();
        SAFETY_CLUTCH = CreateStats.registrate().block("safety_clutch", SafetyClutchBlock::new).properties(p -> p.mapColor(MapColor.COLOR_YELLOW).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        AUTO_TRANSMISSION_CLUTCH = CreateStats.registrate().block("auto_transmission_clutch", AutoTransmissionClutchBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        CONTROL_CLUTCH = CreateStats.registrate().block("control_clutch", ControlClutchBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        LOAD_BALANCER = CreateStats.registrate().block("load_balancer", LoadBalancerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        SMART_GEARSHIFT = CreateStats.registrate().block("smart_gearshift", SmartGearshiftBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        KINETIC_FLYWHEEL = CreateStats.registrate().block("kinetic_flywheel", KineticFlywheelBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().lightLevel(KineticFlywheelBlock::getLight)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        INDUCTION_HEATER = CreateStats.registrate().block("induction_heater", InductionHeaterBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(InductionHeaterBlock::getLight)).simpleItem().register();
        FLOW_METER = CreateStats.registrate().block("flow_meter", FlowMeterBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        POWER_METER = CreateStats.registrate().block("power_meter", com.createstats.block.PowerMeterBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 3.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();

        POWER_LIMITER = CreateStats.registrate().block("power_limiter", com.createstats.block.PowerLimiterBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 3.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();

        UNIVERSAL_POWER_COUPLER = CreateStats.registrate().block("universal_power_coupler", com.createstats.block.UniversalPowerCouplerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 3.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        OVERFLOW_GATE = CreateStats.registrate().block("overflow_gate", OverflowGateBlock::new).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).item(OverflowGateItem::new).build().register();
        FREIGHT_MANIFEST_CLIPBOARD = CreateStats.registrate().block("freight_manifest_clipboard", FreightManifestClipboardBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).item(FreightManifestClipboardItem::new).build().register();
        DISPATCH_BOARD = CreateStats.registrate().block("dispatch_board", p -> new DispatchBoardBlock(p, ModBlockEntities.DISPATCH_BOARD::get)).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.5f, 2.0f).sound(SoundType.WOOD).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).item(DispatchBoardItem::new).build().register();
        RPM_GOVERNOR = CreateStats.registrate().block("rpm_governor", RpmGovernorBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.5f, 4.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        FLIGHT_CONTROL_COMPUTER = CreateStats.registrate().block("flight_control_computer", FlightControlComputerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.5f, 4.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> 6)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        VCC_MK_II = CreateStats.registrate().block("vcc_mk2", com.createstats.vccmk2.VccMk2Block::new).properties(p -> p.mapColor(MapColor.GOLD).strength(2.5f, 4.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> 7)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        VCC_MK3 = CreateStats.registrate().block("vcc_mk3", com.createstats.vccmk3.VccMk3Block::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(s -> s.getValue(com.createstats.vccmk3.VccMk3Block.ARMED) ? 9 : 4)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        VCC_MK4 = CreateStats.registrate().block("vcc_mk4", com.createstats.vccmk4.Vcc4Block::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(s -> 5)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        BTA_G1 = CreateStats.registrate().block("bta_g1", com.createstats.vccmk4.BtaG1Block::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(s -> 5)).addLayer(() -> RenderType::cutoutMipped).register();
        VCC_MK4_AERIAL = CreateStats.registrate().block("vcc_mk4_aerial", com.createstats.vccmk4.air.AerialBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(s -> 5)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        VCC_MK4_AERIAL_DECK = CreateStats.registrate().block("vcc_mk4_aerial_flight_deck", com.createstats.vccmk4.air.AerialDeckBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(s -> 5)).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();

        VEHICLE_LIGHT_WHITE = CreateStats.registrate().<com.createstats.vehiclelight.VehicleLightBlock>block("vehicle_light_white", p -> new com.createstats.vehiclelight.VehicleLightBlock(p, com.createstats.vehiclelight.VehicleLightKind.WHITE)).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.0f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.vehiclelight.VehicleLightBlock.emission(com.createstats.vehiclelight.VehicleLightKind.WHITE))).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        VEHICLE_LIGHT_RED = CreateStats.registrate().<com.createstats.vehiclelight.VehicleLightBlock>block("vehicle_light_red", p -> new com.createstats.vehiclelight.VehicleLightBlock(p, com.createstats.vehiclelight.VehicleLightKind.RED)).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.0f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.vehiclelight.VehicleLightBlock.emission(com.createstats.vehiclelight.VehicleLightKind.RED))).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        VEHICLE_LIGHT_AMBER = CreateStats.registrate().<com.createstats.vehiclelight.VehicleLightBlock>block("vehicle_light_amber", p -> new com.createstats.vehiclelight.VehicleLightBlock(p, com.createstats.vehiclelight.VehicleLightKind.AMBER)).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.0f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.vehiclelight.VehicleLightBlock.emission(com.createstats.vehiclelight.VehicleLightKind.AMBER))).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();

        DRIVER_HELMET = CreateStats.registrate().block("driver_helmet", com.createstats.driverhelmet.DriverHelmetBlock::new).properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(0.8f, 3.0f).sound(SoundType.COPPER).noOcclusion()).item(com.createstats.driverhelmet.DriverHelmetItem::new).properties(p -> p.stacksTo(1).attributes(com.createstats.driverhelmet.DriverHelmetItem.attributes())).build().register();

        AVIATOR_HELMET = CreateStats.registrate().block("aviator_helmet", com.createstats.aviatorhelmet.AviatorHelmetBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BROWN).strength(0.6f, 2.0f).sound(SoundType.WOOD).noOcclusion()).item(com.createstats.aviatorhelmet.AviatorHelmetItem::new).properties(p -> p.stacksTo(1).attributes(com.createstats.aviatorhelmet.AviatorHelmetItem.attributes())).build().register();
        PATHFINDER_COMPUTER = CreateStats.registrate().block("pathfinder_computer", PathfinderComputerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.5f, 4.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        PATH_SCANNER = CreateStats.registrate().block("path_scanner", PathScannerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        ARC_FURNACE = CreateStats.registrate().block("arc_furnace", com.createstats.block.ArcFurnaceBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.5f, 8.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().lightLevel(com.createstats.block.ArcFurnaceBlock::getLight)).simpleItem().register();

        INDUCTION_SMELTER = CreateStats.registrate().block("induction_smelter", com.createstats.block.InductionSmelterBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.5f, 8.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().lightLevel(com.createstats.block.InductionSmelterBlock::getLight)).simpleItem().register();
        INDUCTION_RANGE = CreateStats.registrate().block("induction_range", com.createstats.block.InductionRangeBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().lightLevel(com.createstats.block.InductionRangeBlock::getLight)).simpleItem().register();
        KINETIC_DYNAMO = CreateStats.registrate().block("kinetic_dynamo", p -> new com.createstats.block.KineticDynamoBlock(p, com.createstats.power.DynamoTier.STANDARD)).properties(p -> p.mapColor(MapColor.TERRACOTTA_ORANGE).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        OPEN_FRAME_DYNAMO = CreateStats.registrate().block("open_frame_dynamo", p -> new com.createstats.block.KineticDynamoBlock(p, com.createstats.power.DynamoTier.OPEN_FRAME)).properties(p -> p.mapColor(MapColor.COLOR_BROWN).strength(2.0f, 3.5f).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        INDUSTRIAL_ALTERNATOR = CreateStats.registrate().block("industrial_alternator", p -> new com.createstats.block.KineticDynamoBlock(p, com.createstats.power.DynamoTier.INDUSTRIAL)).properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();

        BIOFUEL_BURNER = CreateStats.registrate().block("biofuel_burner", com.createstats.block.BiofuelBurnerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BROWN).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> s.getValue(com.createstats.block.BiofuelBurnerBlock.LIT) ? com.createstats.block.BiofuelBurnerBlock.LIT_LEVEL : 0)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();

        STARTER_CAPACITOR = CreateStats.registrate()
                .block("starter_capacitor", com.createstats.block.StarterCapacitorBlock::new)
                .properties(p -> p.mapColor(MapColor.COLOR_YELLOW).strength(2.5f, 6.0f)
                        .sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()
                        .lightLevel(s -> s.getValue(com.createstats.block.StarterCapacitorBlock.FIRING)
                                ? com.createstats.block.StarterCapacitorBlock.FIRING_LIGHT : 0))
                .item(com.createstats.item.StarterCapacitorItem::new)
                .build()
                .register();

        SOLAR_PANEL = CreateStats.registrate().block("solar_panel", p -> new com.createstats.block.SolarPanelBlock(p, com.createstats.power.SolarTier.BASIC)).properties(p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.0f, 3.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        REINFORCED_SOLAR_PANEL = CreateStats.registrate().block("reinforced_solar_panel", p -> new com.createstats.block.SolarPanelBlock(p, com.createstats.power.SolarTier.REINFORCED)).properties(p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        PHOTOVOLTAIC_ARRAY = CreateStats.registrate().block("photovoltaic_array", p -> new com.createstats.block.SolarPanelBlock(p, com.createstats.power.SolarTier.PHOTOVOLTAIC)).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.0f, 5.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        CAGED_BULKHEAD_LAMP = CreateStats.registrate().<com.createstats.block.LampBlock>block("caged_bulkhead_lamp", p -> new com.createstats.block.LampBlock.Spun(p, com.createstats.block.LampShapes.CAGED_BULKHEAD, 14)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(14))).simpleItem().register();
        HIGH_BAY_LAMP = CreateStats.registrate().block("high_bay_lamp", p -> new com.createstats.block.LampBlock(p, com.createstats.block.LampShapes.HIGH_BAY, 15)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(15))).simpleItem().register();
        STRIP_LAMP = CreateStats.registrate().<com.createstats.block.LampBlock>block("strip_lamp", p -> new com.createstats.block.LampBlock.Spun(p, com.createstats.block.LampShapes.STRIP, 14)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(14))).simpleItem().register();
        DOME_LAMP = CreateStats.registrate().block("dome_lamp", p -> new com.createstats.block.LampBlock(p, com.createstats.block.LampShapes.DOME, 13)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(13))).simpleItem().register();
        WALL_SCONCE = CreateStats.registrate().<com.createstats.block.LampBlock>block("wall_sconce", p -> new com.createstats.block.LampBlock.Spun(p, com.createstats.block.LampShapes.SCONCE, 12)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(12))).simpleItem().register();
        PANEL_LAMP = CreateStats.registrate().block("panel_lamp", p -> new com.createstats.block.LampBlock(p, com.createstats.block.LampShapes.PANEL, 15)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(15))).simpleItem().register();
        PENDANT_LAMP = CreateStats.registrate().block("pendant_lamp", p -> new com.createstats.block.LampBlock(p, com.createstats.block.LampShapes.PENDANT, 12)).properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(12))).simpleItem().register();
        FLOOD_LAMP = CreateStats.registrate().<com.createstats.block.LampBlock>block("flood_lamp", p -> new com.createstats.block.LampBlock.Spun(p, com.createstats.block.LampShapes.FLOOD, 15)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(15))).simpleItem().register();
        VAPOUR_TIGHT_LAMP = CreateStats.registrate().<com.createstats.block.LampBlock>block("vapour_tight_lamp", p -> new com.createstats.block.LampBlock.Spun(p, com.createstats.block.LampShapes.VAPOUR_TIGHT, 14)).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(14))).simpleItem().register();
        WELL_GLASS_LAMP = CreateStats.registrate().<com.createstats.block.LampBlock>block("well_glass_lamp", p -> new com.createstats.block.LampBlock.Spun(p, com.createstats.block.LampShapes.WELL_GLASS, 13)).properties(p -> p.mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(13))).simpleItem().register();
        FILAMENT_BULB = CreateStats.registrate().block("filament_bulb", p -> new com.createstats.block.LampBlock(p, com.createstats.block.LampShapes.FILAMENT, 10)).properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(1.5f, 2.0f).sound(SoundType.GLASS).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(10))).simpleItem().register();
        BANKERS_LAMP = CreateStats.registrate().<com.createstats.block.LampBlock>block("bankers_lamp", p -> new com.createstats.block.LampBlock.Spun(p, com.createstats.block.LampShapes.BANKERS, 11)).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(11))).simpleItem().register();
        EMERGENCY_LAMP = CreateStats.registrate().<com.createstats.block.LampBlock>block("emergency_lamp", p -> new com.createstats.block.LampBlock.Spun(p, com.createstats.block.LampShapes.EMERGENCY, 12)).properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(1.5f, 2.0f).sound(SoundType.LANTERN).noOcclusion().lightLevel(com.createstats.block.LampBlock.emission(12))).simpleItem().register();
        ROCKER_SWITCH = CreateStats.registrate().block("rocker_switch", p -> new com.createstats.block.SwitchBlock(p, com.createstats.block.LampShapes.ROCKER, false, com.createstats.block.SwitchBlock.Click.ROCKER)).properties(p -> p.mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();
        TOGGLE_SWITCH = CreateStats.registrate().block("toggle_switch", p -> new com.createstats.block.SwitchBlock(p, com.createstats.block.LampShapes.TOGGLE, false, com.createstats.block.SwitchBlock.Click.DOLLY)).properties(p -> p.mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();
        DOUBLE_SWITCH = CreateStats.registrate().block("double_switch", p -> new com.createstats.block.SwitchBlock(p, com.createstats.block.LampShapes.DOUBLE, true, com.createstats.block.SwitchBlock.Click.ROCKER)).properties(p -> p.mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();
        ISOLATOR_SWITCH = CreateStats.registrate().block("isolator_switch", p -> new com.createstats.block.SwitchBlock(p, com.createstats.block.LampShapes.ISOLATOR, false, com.createstats.block.SwitchBlock.Click.ISOLATOR)).properties(p -> p.mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();
        ROTARY_DIMMER_DIAL = CreateStats.registrate().block("rotary_dimmer_dial", p -> new com.createstats.block.RotaryDimmerBlock(p, com.createstats.block.LampShapes.DIMMER_DIAL, com.createstats.block.RotaryDimmerBlock.Kind.DIAL)).properties(p -> p.mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();
        ROTARY_DIMMER_PUCK = CreateStats.registrate().block("rotary_dimmer_puck", p -> new com.createstats.block.RotaryDimmerBlock(p, com.createstats.block.LampShapes.DIMMER_PUCK, com.createstats.block.RotaryDimmerBlock.Kind.PUCK)).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(0.8f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();

        TIME_SWITCH = CreateStats.registrate().block("time_switch", com.createstats.block.TimeSwitchBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();

        CONTACTOR = CreateStats.registrate().block("contactor", com.createstats.block.ContactorBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.2f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();
        PUSH_BUTTON = CreateStats.registrate().block("push_button", p -> new com.createstats.block.SwitchBlock(p, com.createstats.block.LampShapes.BUTTON, false, com.createstats.block.SwitchBlock.Click.STOP)).properties(p -> p.mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.STONE).noOcclusion()).simpleItem().register();

        MAIN_BREAKER = CreateStats.registrate().block("main_breaker", p -> new com.createstats.block.MainBreakerBlock(p, false)).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5f, 3.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()).simpleItem().register();

        CREATIVE_MAIN_BREAKER = CreateStats.registrate().block("creative_main_breaker", p -> new com.createstats.block.MainBreakerBlock(p, true)).properties(p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1.0f, 3600000.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()).simpleItem().register();

        SURFACE_CABLE = CreateStats.registrate().block("surface_cable", p -> new com.createstats.block.CableBlock(p, com.createstats.power.CableTier.SURFACE)).properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.4f).sound(SoundType.COPPER).noOcclusion()).item((b, p) -> new com.createstats.item.CableItem((com.createstats.block.CableBlock) b, p)).build().register();
        CABLE_TRAY = CreateStats.registrate().block("cable_tray", p -> new com.createstats.block.CableBlock(p, com.createstats.power.CableTier.TRAY)).properties(p -> p.mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.COPPER).noOcclusion()).item((b, p) -> new com.createstats.item.CableItem((com.createstats.block.CableBlock) b, p)).build().register();

        INDOOR_CABLE = CreateStats.registrate().block("indoor_cable", com.createstats.block.IndoorCableBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.3f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        BUS_DUCT = CreateStats.registrate().block("bus_duct", p -> new com.createstats.block.CableBlock(p, com.createstats.power.CableTier.DUCT)).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.8f).sound(SoundType.COPPER).noOcclusion()).item((b, p) -> new com.createstats.item.CableItem((com.createstats.block.CableBlock) b, p)).build().register();

        BUSBAR_TRUNK = CreateStats.registrate().block("busbar_trunk", p -> new com.createstats.block.CableBlock(p, com.createstats.power.CableTier.TRUNK)).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.2f).sound(SoundType.NETHERITE_BLOCK).noOcclusion()).item((b, p) -> new com.createstats.item.CableItem((com.createstats.block.CableBlock) b, p)).build().register();

        TOPOLOGY_SCANNER = CreateStats.registrate().block("topology_scanner", p -> new com.createstats.netscan.block.NetscanInlineBlock(p, com.createstats.netscan.ScannerTier.FIELD)).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(com.createstats.netscan.block.NetscanInlineBlock.LIT) ? 7 : 0)).simpleItem().register();
        ADVANCED_TOPOLOGY_SCANNER = CreateStats.registrate().block("advanced_topology_scanner", p -> new com.createstats.netscan.block.NetscanInlineBlock(p, com.createstats.netscan.ScannerTier.ADVANCED)).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.5f, 3.5f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(com.createstats.netscan.block.NetscanInlineBlock.LIT) ? 7 : 0)).simpleItem().register();
        INDUSTRIAL_TOPOLOGY_SCANNER = CreateStats.registrate().block("industrial_topology_scanner", p -> new com.createstats.netscan.block.NetscanInlineBlock(p, com.createstats.netscan.ScannerTier.INDUSTRIAL)).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.0f, 4.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(s -> s.getValue(com.createstats.netscan.block.NetscanInlineBlock.LIT) ? 7 : 0)).simpleItem().register();
        TOPOLOGY_SCANNER_PROBE = CreateStats.registrate().block("topology_scanner_probe", p -> new com.createstats.netscan.block.NetscanProbeBlock(p, com.createstats.netscan.ScannerTier.FIELD)).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(com.createstats.netscan.block.NetscanProbeBlock.LIT) ? 7 : 0)).simpleItem().register();
        ADVANCED_TOPOLOGY_SCANNER_PROBE = CreateStats.registrate().block("advanced_topology_scanner_probe", p -> new com.createstats.netscan.block.NetscanProbeBlock(p, com.createstats.netscan.ScannerTier.ADVANCED)).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.5f, 3.5f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(com.createstats.netscan.block.NetscanProbeBlock.LIT) ? 7 : 0)).simpleItem().register();
        INDUSTRIAL_TOPOLOGY_SCANNER_PROBE = CreateStats.registrate().block("industrial_topology_scanner_probe", p -> new com.createstats.netscan.block.NetscanProbeBlock(p, com.createstats.netscan.ScannerTier.INDUSTRIAL)).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.0f, 4.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(s -> s.getValue(com.createstats.netscan.block.NetscanProbeBlock.LIT) ? 7 : 0)).simpleItem().register();
        POWER_CONNECTOR = CreateStats.registrate().block("power_connector", p -> new com.createstats.block.PowerConnectorBlock(p, com.createstats.power.ConnectorTier.POWER)).properties(p -> p.mapColor(MapColor.COLOR_BROWN).strength(1.5f, 2.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        ADVANCED_POWER_CONNECTOR = CreateStats.registrate().block("advanced_power_connector", p -> new com.createstats.block.PowerConnectorBlock(p, com.createstats.power.ConnectorTier.ADVANCED)).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        INDUSTRIAL_POWER_CONNECTOR = CreateStats.registrate().block("industrial_power_connector", p -> new com.createstats.block.PowerConnectorBlock(p, com.createstats.power.ConnectorTier.INDUSTRIAL)).properties(p -> p.mapColor(MapColor.GOLD).strength(2.5f, 4.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        CAPACITOR_BANK = CreateStats.registrate().block("capacitor_bank", com.createstats.block.CapacitorBankBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().lightLevel(com.createstats.block.CapacitorBankBlock::getLightEmission)).item(com.createstats.item.CapacitorBankItem::new).build().register();
        POWER_DISTRIBUTION_UNIT = CreateStats.registrate().block("power_distribution_unit", com.createstats.block.PowerDistributionUnitBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 4)).simpleItem().register();
        PDU_CABINET = CreateStats.registrate().block("pdu_cabinet", com.createstats.block.PduCabinetBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 4)).simpleItem().register();
        FE_BATTERY_CONTROLLER = CreateStats.registrate().block("fe_battery_controller", com.createstats.block.BatteryControllerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 5)).simpleItem().register();

        PORTABLE_POWER_INTERFACE = CreateStats.registrate().block("portable_power_interface", com.createstats.block.PortablePowerInterfaceBlock::new).properties(p -> p.mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).strength(1.5f, 3.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion()).onRegister(com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour(new com.createstats.block.PortablePowerInterfaceMovement())).simpleItem().register();

        com.createstats.powertrack.PowerTrack.init();
        POWER_TRACK = CreateStats.registrate().block("power_track", p -> (com.createstats.powertrack.PowerTrackBlock) com.createstats.powertrack.PowerTrack.MATERIAL.createBlock(p)).initialProperties(com.simibubi.create.foundation.data.SharedProperties::stone).properties(p -> p.mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.METAL).noOcclusion().forceSolidOn()).addLayer(() -> net.minecraft.client.renderer.RenderType::cutoutMipped).clientExtension(() -> () -> new com.simibubi.create.content.trains.track.TrackBlock.RenderProperties()).onRegister(com.simibubi.create.foundation.data.CreateRegistrate.blockModel(() -> com.simibubi.create.content.trains.track.TrackModel::new)).item(com.simibubi.create.content.trains.track.TrackBlockItem::new).build().register();
        TRACK_FEEDER = CreateStats.registrate().block("track_feeder", com.createstats.powertrack.TrackFeederBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).item(com.simibubi.create.content.trains.track.TrackTargetingBlockItem.ofType(com.createstats.powertrack.PowerTrack.FEEDER)).build().register();
        SECTION_INSULATOR = CreateStats.registrate().block("section_insulator", com.createstats.powertrack.SectionInsulatorBlock::new).properties(p -> p.mapColor(MapColor.SNOW).strength(1.5f, 3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()).item(com.simibubi.create.content.trains.track.TrackTargetingBlockItem.ofType(com.createstats.powertrack.PowerTrack.INSULATOR)).build().register();
        CURRENT_COLLECTOR = CreateStats.registrate().block("current_collector", com.createstats.powertrack.CurrentCollectorBlock::new).properties(p -> p.mapColor(MapColor.WOOD).strength(1.5f, 3.0f).sound(SoundType.WOOD).noOcclusion()).onRegister(com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour(new com.createstats.powertrack.CurrentCollectorMovement())).simpleItem().register();
        FLUID_PUMP_MK1 = CreateStats.registrate().block("fluid_pump_mk1", p -> new com.createstats.block.FluidPumpBlock(p, com.createstats.power.PumpTier.MK_I)).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        FLUID_PUMP_MK2 = CreateStats.registrate().block("fluid_pump_mk2", p -> new com.createstats.block.FluidPumpBlock(p, com.createstats.power.PumpTier.MK_II)).properties(p -> p.mapColor(MapColor.METAL).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        FE_MOTOR_MK1 = CreateStats.registrate().block("fe_motor_mk1", p -> new com.createstats.block.FeMotorBlock(p, com.createstats.power.MotorTier.MK_I)).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();
        FE_MOTOR_MK2 = CreateStats.registrate().block("fe_motor_mk2", p -> new com.createstats.block.FeMotorBlock(p, com.createstats.power.MotorTier.MK_II)).properties(p -> p.mapColor(MapColor.GOLD).strength(2.5f, 4.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion()).simpleItem().register();

        FLOOR_GRATING = CreateStats.registrate().block("floor_grating", p -> new com.createstats.block.FloorGratingBlock(p, Block.box(0, 0, 0, 16, 2, 16))).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        HAZARD_TRIM = CreateStats.registrate().block("hazard_trim", p -> new com.createstats.block.HazardTrimBlock(p, Block.box(0, 0, 0, 16, 1.5, 16))).properties(p -> p.mapColor(MapColor.COLOR_YELLOW).strength(1.0f, 1.5f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        GUARD_RAILING = CreateStats.registrate().block("guard_railing", com.createstats.block.GuardRailingBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        CONDUIT_JUNCTION_BOX = CreateStats.registrate().block("conduit_junction_box", p -> new com.createstats.block.WallPlateBlock(p, 0, 0, 0, 16, 13.5, 6.4)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        CABLE_DIAGNOSTIC_JUNCTION = CreateStats.registrate().block("cable_diagnostic_junction", p -> new com.createstats.block.DiagnosticJunctionBlock(p, com.createstats.block.DiagnosticJunctionBlock.Kind.CABLE)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        WIRE_TERMINAL_JUNCTION = CreateStats.registrate().block("wire_terminal_junction", p -> new com.createstats.block.DiagnosticJunctionBlock(p, com.createstats.block.DiagnosticJunctionBlock.Kind.WIRE)).properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        POWER_OUTLET = CreateStats.registrate().block("power_outlet", p -> new com.createstats.block.WallOutletBlock(p, 2.0, 2.0, 0.0, 14.0, 14.0, 2.0)).properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5f).sound(SoundType.DECORATED_POT).noOcclusion()).simpleItem().register();
        DUPLEX_POWER_OUTLET = CreateStats.registrate().block("duplex_power_outlet", p -> new com.createstats.block.WallOutletBlock(p, 0.0, 2.0, 0.0, 16.0, 14.0, 2.0)).properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5f).sound(SoundType.DECORATED_POT).noOcclusion()).simpleItem().register();
        BLANK_MONITOR_BEZEL = CreateStats.registrate().block("blank_monitor_bezel", p -> new com.createstats.block.BlankMonitorBezelBlock(p, 2, 2, 0, 14, 14.4, 2.0)).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.5f, 2.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();

        EINK_PANEL = CreateStats.registrate().block("eink_panel", com.createstats.block.EInkPanelBlock::new).properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(1.5f, 2.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();

        SMART_HOME_PANEL = CreateStats.registrate().block("smart_home_panel", com.createstats.block.SmartHomePanelBlock::new).properties(p -> p.mapColor(MapColor.SNOW).strength(1.0f, 2.0f).sound(SoundType.GLASS).noOcclusion()).simpleItem().register();

        WEATHER_PANEL = CreateStats.registrate().block("weather_panel", com.createstats.block.WeatherPanelBlock::new).properties(p -> p.mapColor(MapColor.SNOW).strength(1.0f, 2.0f).sound(SoundType.GLASS).noOcclusion()).simpleItem().register();

        HOME_SECURITY_PANEL = CreateStats.registrate().block("home_security_panel", com.createstats.block.HomeSecurityPanelBlock::new).properties(p -> p.mapColor(MapColor.SNOW).strength(1.6f, 6.0f).sound(SoundType.GLASS).noOcclusion()).simpleItem().register();

        SECURITY_RELAY = CreateStats.registrate().block("security_relay", com.createstats.block.SecurityRelayBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion()).addLayer(() -> RenderType::cutout).simpleItem().register();

        COFFEE_MACHINE = CreateStats.registrate().block("coffee_machine", com.createstats.block.CoffeeMachineBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GREEN).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).simpleItem().register();
        // noOcclusion, the alcove is a real recess and the carboy is glass

        WATER_COOLER = CreateStats.registrate().block("water_cooler", com.createstats.block.WaterCoolerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();

        CINE_CAMERA = CreateStats.registrate().block("cine_camera", com.createstats.cinecam.CineCameraBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 4.0f).sound(SoundType.METAL).noOcclusion()).item(com.createstats.cinecam.CineCameraItem::new).build().register();

        AIR_FRYER = CreateStats.registrate().block("air_fryer", com.createstats.block.AirFryerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();

        BULK_WASHER = CreateStats.registrate().block("bulk_washer", com.createstats.block.BulkWasherBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        // noOcclusion or the cavity culls into a painted brick

        OVEN = CreateStats.registrate().block("oven", com.createstats.block.OvenBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.2f, 4.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(com.createstats.block.OvenBlock.LIT) ? com.createstats.block.OvenBlock.LIT_LEVEL : 0)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();

        for (final com.createstats.baking.CakeFlavor flavour
                : com.createstats.baking.CakeFlavor.values()) {
            CAKES.put(flavour, CreateStats.registrate()
                    .block(flavour.cakeName(),
                            p -> new com.createstats.block.FlavouredCakeBlock(flavour, p))
                    .properties(p -> p.mapColor(MapColor.WOOL).strength(0.5f)
                            .sound(SoundType.WOOL).noOcclusion())
                    .simpleItem()
                    .register());
        }

        TOASTER = CreateStats.registrate().block("toaster", com.createstats.block.ToasterBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.6f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(com.createstats.block.ToasterBlock.LIT) ? com.createstats.block.ToasterBlock.LIT_LEVEL : 0)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();

        VACUUM_COLLECTOR = CreateStats.registrate().block("vacuum_collector", com.createstats.block.VacuumCollectorBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 4.0f).sound(SoundType.COPPER).noOcclusion()).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();
        VACUUM_CLEANER = CreateStats.registrate().block("vacuum_cleaner", com.createstats.block.VacuumCleanerBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 4.0f).sound(SoundType.METAL).noOcclusion()).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();
        VACUUM_SNIFFER = CreateStats.registrate().block("vacuum_sniffer", com.createstats.block.VacuumSnifferBlock::new).properties(p -> p.mapColor(MapColor.COLOR_YELLOW).strength(1.6f, 3.0f).sound(SoundType.METAL).noOcclusion()).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();

        MOB_REPELLER = CreateStats.registrate().block("mob_repeller", com.createstats.block.MobRepellerBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(2.5f, 6.0f).sound(SoundType.METAL).noOcclusion()).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();

        STORM_COIL = CreateStats.registrate().block("storm_coil", com.createstats.block.StormCoilBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();
        STORM_COIL_PART = CreateStats.registrate().block("storm_coil_part", com.createstats.block.StormCoilPartBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)).register();

        SPARK_CROWN = CreateStats.registrate().block("spark_crown", com.createstats.block.SparkCrownBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().lightLevel(com.createstats.block.SparkCrownBlock::lightLevel).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)).item(com.createstats.chunkloader.SparkCrownItem::new).build().register();
        SPARK_CROWN_STRUCTURE = CreateStats.registrate().block("spark_crown_structure", com.createstats.block.SparkCrownStructureBlock::new).properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)).register();

        SEISMIC_SCANNER = CreateStats.registrate().block("seismic_scanner", com.createstats.block.SeismicScannerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_YELLOW).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();
        BREAD_SLICER = CreateStats.registrate().block("bread_slicer", com.createstats.block.BreadSlicerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.6f, 3.0f).sound(SoundType.COPPER).noOcclusion().lightLevel(s -> s.getValue(com.createstats.block.BreadSlicerBlock.LIT) ? com.createstats.block.BreadSlicerBlock.LIT_LEVEL : 0)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).simpleItem().register();

        BLUEBERRY_BUSH = CreateStats.registrate().block("blueberry_bush", com.createstats.block.HarvestBushBlock::new).properties(p -> p.mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).register();
        CINNAMON_SHRUB = CreateStats.registrate().block("cinnamon_shrub", com.createstats.block.HarvestBushBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BROWN).noCollission().randomTicks().instabreak().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).register();

        VANILLA_BEAN_VINE = CreateStats.registrate().block("vanilla_bean_vine", com.createstats.block.VanillaBeanBlock::new).properties(p -> p.mapColor(MapColor.PLANT).randomTicks().strength(0.2f, 3.0f).sound(SoundType.WOOD).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).register();
        STRAWBERRY_CROP = CreateStats.registrate().block("strawberry_crop", com.createstats.block.SimpleCropBlock::new).properties(p -> p.mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)).addLayer(() -> net.minecraft.client.renderer.RenderType::cutout).register();

        PA_SPEAKER = CreateStats.registrate().block("pa_speaker",
                p -> new com.createstats.block.PaSpeakerBlock(p,
                        com.createstats.block.LampShapes.PA_SPEAKER,
                        com.createstats.pa.SpeakerKind.SPEAKER,
                        () -> ModBlockEntities.PA_SPEAKER.get()))
                .properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5f, 2.0f)
                        .sound(SoundType.COPPER).noOcclusion())
                .simpleItem().register();
        PA_MEGAPHONE = CreateStats.registrate().block("pa_megaphone",
                p -> new com.createstats.block.PaSpeakerBlock(p,
                        com.createstats.block.LampShapes.PA_MEGAPHONE,
                        com.createstats.pa.SpeakerKind.MEGAPHONE,
                        () -> ModBlockEntities.PA_MEGAPHONE.get()))
                .properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 2.0f)
                        .sound(SoundType.COPPER).noOcclusion())
                .simpleItem().register();

        ANNOUNCEMENT_COMPUTER = CreateStats.registrate().block("pa_brain", com.createstats.block.AnnouncementComputerBlock::new).properties(p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0f, 3.0f).sound(SoundType.COPPER).noOcclusion()).simpleItem().register();
        CERAMIC_MUG = CreateStats.registrate().block("ceramic_mug", com.createstats.block.MugBlock::new).properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(0.2f).sound(SoundType.DECORATED_POT).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).item(com.createstats.item.CoffeeMugItem::new).properties(p -> p.stacksTo(16)).build().register();
        ENAMEL_MUG = CreateStats.registrate().block("enamel_mug", com.createstats.block.MugBlock::new).properties(p -> p.mapColor(MapColor.COLOR_GREEN).strength(0.3f).sound(SoundType.LANTERN).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).item(com.createstats.item.CoffeeMugItem::new).properties(p -> p.stacksTo(16)).build().register();
        STEEL_MUG = CreateStats.registrate().block("steel_mug", com.createstats.block.MugBlock::new).properties(p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.3f).sound(SoundType.LANTERN).noOcclusion()).addLayer(() -> RenderType::cutoutMipped).item(com.createstats.item.CoffeeMugItem::new).properties(p -> p.stacksTo(16)).build().register();

        for (final com.createstats.block.DoorMaterial material
                : com.createstats.block.DoorMaterial.all()) {
            POWERED_DOORS.put(material, CreateStats.registrate()
                    .block(material.blockId(), p -> new com.createstats.block.PoweredDoorBlock(material, p))
                    .properties(material::apply)
                    .simpleItem()
                    .register());
        }

        for (final com.createstats.block.ShutterTier tier
                : com.createstats.block.ShutterTier.all()) {
            BLAST_SHUTTERS.put(tier, CreateStats.registrate()
                    .block(tier.blockId(), p -> new com.createstats.block.BlastShutterBlock(tier, p))
                    .properties(tier::apply)
                    .simpleItem()
                    .register());
        }

        SECURITY_LOCKER = CreateStats.registrate()
                .block("security_locker", com.createstats.block.SecurityLockerBlock::new)
                .properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0f, 1200.0f)
                        .sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion())
                .item(com.createstats.item.SecurityLockerItem::new)
                .build()
                .register();

        for (final com.createstats.security.SensorKind kind
                : com.createstats.security.SensorKind.all()) {
            SECURITY_SENSORS.put(kind, CreateStats.registrate()
                    .block(kind.blockId(),
                            p -> new com.createstats.block.SecuritySensorBlock(p, kind))
                    .properties(p -> p.mapColor(MapColor.SNOW).strength(1.2f, 4.0f)
                            .sound(SoundType.COPPER).noOcclusion())
                    .simpleItem()
                    .register());
        }

        GARAGE_DOOR_HEADER = CreateStats.registrate()
                .block("garage_door_header", com.createstats.block.GarageDoorHeaderBlock::new)
                .properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(2.0f, 4.0f)
                        .sound(SoundType.METAL).noOcclusion())
                .simpleItem()
                .register();

        GARAGE_DOOR_BODY = CreateStats.registrate()
                .block("garage_door_body", com.createstats.block.GarageDoorBodyBlock::new)
                .properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(2.0f, 4.0f)
                        .sound(SoundType.METAL).noOcclusion())
                .register();
    }

    public static net.minecraft.world.level.block.Block[] garageDoorFeBlocks() {
        return new net.minecraft.world.level.block.Block[]{GARAGE_DOOR_HEADER.get()};
    }

    public static BlockEntry<com.createstats.block.BlastShutterBlock> blastShutter(
            com.createstats.block.ShutterTier tier) {
        return BLAST_SHUTTERS.get(tier);
    }

    public static net.minecraft.world.level.block.Block[] blastShutterBlocks() {
        final net.minecraft.world.level.block.Block[] out =
                new net.minecraft.world.level.block.Block[BLAST_SHUTTERS.size()];
        int i = 0;
        for (BlockEntry<com.createstats.block.BlastShutterBlock> entry : BLAST_SHUTTERS.values()) {
            out[i++] = entry.get();
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static com.tterrag.registrate.util.nullness.NonNullSupplier<
            ? extends net.minecraft.world.level.block.Block>[] blastShutterSuppliers() {
        final com.createstats.block.ShutterTier[] tiers = com.createstats.block.ShutterTier.all();
        final com.tterrag.registrate.util.nullness.NonNullSupplier<
                ? extends net.minecraft.world.level.block.Block>[] out =
                new com.tterrag.registrate.util.nullness.NonNullSupplier[tiers.length];
        for (int i = 0; i < tiers.length; i++) {
            final com.createstats.block.ShutterTier tier = tiers[i];
            out[i] = () -> blastShutter(tier).get();
        }
        return out;
    }

    public static BlockEntry<com.createstats.block.SecuritySensorBlock> securitySensor(
            com.createstats.security.SensorKind kind) {
        return SECURITY_SENSORS.get(kind);
    }

    public static net.minecraft.world.level.block.Block[] securitySensorBlocks() {
        final net.minecraft.world.level.block.Block[] out =
                new net.minecraft.world.level.block.Block[SECURITY_SENSORS.size()];
        int i = 0;
        for (BlockEntry<com.createstats.block.SecuritySensorBlock> entry : SECURITY_SENSORS.values()) {
            out[i++] = entry.get();
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static com.tterrag.registrate.util.nullness.NonNullSupplier<
            ? extends net.minecraft.world.level.block.Block>[] securitySensorSuppliers() {
        final com.createstats.security.SensorKind[] kinds = com.createstats.security.SensorKind.all();
        final com.tterrag.registrate.util.nullness.NonNullSupplier<
                ? extends net.minecraft.world.level.block.Block>[] out =
                new com.tterrag.registrate.util.nullness.NonNullSupplier[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            final com.createstats.security.SensorKind kind = kinds[i];
            out[i] = () -> securitySensor(kind).get();
        }
        return out;
    }

    public static BlockEntry<com.createstats.block.FlavouredCakeBlock> cake(
            com.createstats.baking.CakeFlavor flavour) {
        return CAKES.get(flavour);
    }

    public static BlockEntry<com.createstats.block.PoweredDoorBlock> poweredDoor(
            com.createstats.block.DoorMaterial material) {
        return POWERED_DOORS.get(material);
    }

    public static net.minecraft.world.level.block.Block[] poweredDoorBlocks() {
        final net.minecraft.world.level.block.Block[] out =
                new net.minecraft.world.level.block.Block[POWERED_DOORS.size()];
        int i = 0;
        for (BlockEntry<com.createstats.block.PoweredDoorBlock> entry : POWERED_DOORS.values()) {
            out[i++] = entry.get();
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static com.tterrag.registrate.util.nullness.NonNullSupplier<
            ? extends net.minecraft.world.level.block.Block>[] poweredDoorSuppliers() {
        final com.createstats.block.DoorMaterial[] materials = com.createstats.block.DoorMaterial.all();
        final com.tterrag.registrate.util.nullness.NonNullSupplier<
                ? extends net.minecraft.world.level.block.Block>[] out =
                new com.tterrag.registrate.util.nullness.NonNullSupplier[materials.length];
        for (int i = 0; i < materials.length; i++) {
            final com.createstats.block.DoorMaterial material = materials[i];
            out[i] = () -> poweredDoor(material).get();
        }
        return out;
    }

    public static net.minecraft.world.item.Item mugVariant(int index) {
        return switch (index) {
            case 1 -> ENAMEL_MUG.asItem();
            case 2 -> STEEL_MUG.asItem();
            default -> CERAMIC_MUG.asItem();
        };
    }

    public static int mugIndex(net.minecraft.world.item.Item item) {
        if (item == CERAMIC_MUG.asItem()) {
            return 0;
        }
        if (item == ENAMEL_MUG.asItem()) {
            return 1;
        }
        if (item == STEEL_MUG.asItem()) {
            return 2;
        }
        return -1;
    }
}
