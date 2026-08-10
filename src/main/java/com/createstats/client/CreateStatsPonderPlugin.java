package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

public class CreateStatsPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return CreateStats.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemLike> itemHelper = helper.withKeyFunction(itemLike -> BuiltInRegistries.ITEM.getKey(itemLike.asItem()));

        // Inventory Display gets its own dedicated guide (separate block + schematic).
        itemHelper.addStoryBoard(ModBlocks.PRODUCTION_DETECTOR.get(), "inventory_display/reading_sources", CreateStatsPonderScenes::inventoryDisplaySources,
                AllCreatePonderTags.DISPLAY_SOURCES, AllCreatePonderTags.DISPLAY_TARGETS);

        // Eight flowing chapters for the Stats Display panel (consolidated from 18 micro-scenes).
        // Each reuses an existing schematic path so no new structure files are required.
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_PANEL.get(), "stats_display/basics", CreateStatsPonderScenes::statsDisplayGettingStarted,
            AllCreatePonderTags.DISPLAY_TARGETS, AllCreatePonderTags.DISPLAY_SOURCES);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_PANEL.get(), "stats_display/home_source_browser", CreateStatsPonderScenes::statsDisplayReadingData,
            AllCreatePonderTags.DISPLAY_TARGETS);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_PANEL.get(), "stats_display/dashboards", CreateStatsPonderScenes::statsDisplayDashboards,
            AllCreatePonderTags.DISPLAY_TARGETS);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_PANEL.get(), "stats_display/create_sources", CreateStatsPonderScenes::statsDisplayCreateTelemetry,
            AllCreatePonderTags.DISPLAY_TARGETS, AllCreatePonderTags.DISPLAY_SOURCES);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_PANEL.get(), "stats_display/diesel_engines", CreateStatsPonderScenes::statsDisplayDieselTelemetry,
            AllCreatePonderTags.DISPLAY_TARGETS, AllCreatePonderTags.DISPLAY_SOURCES);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_PANEL.get(), "stats_display/logistics", CreateStatsPonderScenes::statsDisplayVehiclesLogistics,
            AllCreatePonderTags.DISPLAY_TARGETS, AllCreatePonderTags.DISPLAY_SOURCES);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_PANEL.get(), "stats_display/battle_board", CreateStatsPonderScenes::statsDisplayCombatTools,
            AllCreatePonderTags.DISPLAY_TARGETS, AllCreatePonderTags.DISPLAY_SOURCES);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_PANEL.get(), "stats_display/battle_tactical", CreateStatsPonderScenes::statsDisplayBattleTactical,
            AllCreatePonderTags.DISPLAY_TARGETS, AllCreatePonderTags.DISPLAY_SOURCES);

        // [1]
        itemHelper.addStoryBoard(ModBlocks.KINETIC_DYNAMO.get(), "stats_display/basics", PowerScenes::powerBasics);
        itemHelper.addStoryBoard(ModBlocks.POWER_CONNECTOR.get(), "stats_display/basics", PowerScenes::powerBasics);
        itemHelper.addStoryBoard(ModBlocks.CAPACITOR_BANK.get(), "stats_display/basics", PowerScenes::capacitorBank);
        itemHelper.addStoryBoard(ModBlocks.CAPACITOR_BANK.get(), "stats_display/dashboards", PowerScenes::feDependentMode);
        itemHelper.addStoryBoard(ModBlocks.POWER_DISTRIBUTION_UNIT.get(), "stats_display/basics", PowerScenes::powerDistributionUnit);
        itemHelper.addStoryBoard(ModBlocks.POWER_DISTRIBUTION_UNIT.get(), "stats_display/dashboards", PowerScenes::powerDistributionUnitAuto);

        // Lighting suite. The six fixed fittings share both chapters because they share a behaviour;
        // the two stage lights are aimable and get their own.
        for (com.tterrag.registrate.util.entry.BlockEntry<com.createstats.block.LampBlock> fitting :
                java.util.List.of(ModBlocks.CAGED_BULKHEAD_LAMP, ModBlocks.HIGH_BAY_LAMP, ModBlocks.STRIP_LAMP,
                        ModBlocks.DOME_LAMP, ModBlocks.WALL_SCONCE, ModBlocks.PANEL_LAMP)) {
            itemHelper.addStoryBoard(fitting.get(), "stats_display/basics", LightingScenes::lampFittings);
            itemHelper.addStoryBoard(fitting.get(), "stats_display/dashboards", LightingScenes::lampCircuits);
        }
        itemHelper.addStoryBoard(ModBlocks.STAGE_LIGHT.get(), "stats_display/basics", LightingScenes::stageLightGuide);
        itemHelper.addStoryBoard(ModBlocks.STAGE_LIGHT_MK2.get(), "stats_display/basics", LightingScenes::stageLightGuide);
        itemHelper.addStoryBoard(ModBlocks.STAGE_LIGHT_MK2.get(), "stats_display/dashboards", LightingScenes::stageLightMk2Guide);

        // Wall switches. All five share both chapters: the set behaves alike, and the differences
        // between the plates are exactly what the first scene is for.
        for (com.tterrag.registrate.util.entry.BlockEntry<com.createstats.block.SwitchBlock> plate :
                java.util.List.of(ModBlocks.ROCKER_SWITCH, ModBlocks.TOGGLE_SWITCH, ModBlocks.PUSH_BUTTON,
                        ModBlocks.ISOLATOR_SWITCH, ModBlocks.DOUBLE_SWITCH)) {
            itemHelper.addStoryBoard(plate.get(), "stats_display/basics", SwitchScenes::switchPlates);
            itemHelper.addStoryBoard(plate.get(), "stats_display/dashboards", SwitchScenes::switchCircuits);
        }

        // Surface wiring: the three cable grades, then the rating rule for point-to-point hops.
        for (com.tterrag.registrate.util.entry.BlockEntry<com.createstats.block.CableBlock> grade :
                java.util.List.of(ModBlocks.SURFACE_CABLE, ModBlocks.CABLE_TRAY, ModBlocks.BUS_DUCT)) {
            itemHelper.addStoryBoard(grade.get(), "stats_display/basics", CableScenes::cableGrades);
        }
        itemHelper.addStoryBoard(ModBlocks.ADVANCED_POWER_CONNECTOR.get(), "stats_display/basics", CableScenes::ratedHops);
        itemHelper.addStoryBoard(ModBlocks.POWER_CONNECTOR.get(), "stats_display/dashboards", CableScenes::ratedHops);

        // The three FE-driven machines. Both grades of Drive and Pump share a chapter: the tiers differ
        // only in the numbers, and the scene says both sets out loud.
        itemHelper.addStoryBoard(ModBlocks.FE_MOTOR_MK1.get(), "stats_display/basics", FeMachineScenes::electricDrive);
        itemHelper.addStoryBoard(ModBlocks.FE_MOTOR_MK2.get(), "stats_display/basics", FeMachineScenes::electricDrive);
        itemHelper.addStoryBoard(ModBlocks.FLUID_PUMP_MK1.get(), "stats_display/basics", FeMachineScenes::fluidPump);
        itemHelper.addStoryBoard(ModBlocks.FLUID_PUMP_MK2.get(), "stats_display/basics", FeMachineScenes::fluidPump);
        itemHelper.addStoryBoard(ModBlocks.FLOW_METER.get(), "stats_display/basics", FeMachineScenes::flowMeter,
                AllCreatePonderTags.DISPLAY_SOURCES);

        // The other two generator grades, plus the grade-comparison chapter on all three.
        itemHelper.addStoryBoard(ModBlocks.OPEN_FRAME_DYNAMO.get(), "stats_display/basics", PowerScenes::powerBasics);
        itemHelper.addStoryBoard(ModBlocks.INDUSTRIAL_ALTERNATOR.get(), "stats_display/basics", PowerScenes::powerBasics);
        itemHelper.addStoryBoard(ModBlocks.OPEN_FRAME_DYNAMO.get(), "stats_display/dashboards", PowerScenes::dynamoGrades);
        itemHelper.addStoryBoard(ModBlocks.KINETIC_DYNAMO.get(), "stats_display/dashboards", PowerScenes::dynamoGrades);
        itemHelper.addStoryBoard(ModBlocks.INDUSTRIAL_ALTERNATOR.get(), "stats_display/dashboards", PowerScenes::dynamoGrades);

        // Induction Range: the FE counterpart to the Induction Heater.
        itemHelper.addStoryBoard(ModBlocks.INDUCTION_RANGE.get(), "stats_display/basics", FeMachineScenes::inductionRange);

        // The two Mk II consoles.
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_MK2.get(), "stats_display/basics", Mk2Scenes::statsDisplayMk2,
                AllCreatePonderTags.DISPLAY_TARGETS);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_MK2.get(), "stats_display/home_source_browser",
                CreateStatsPonderScenes::statsDisplayReadingData, AllCreatePonderTags.DISPLAY_TARGETS);
        itemHelper.addStoryBoard(ModBlocks.STATS_DISPLAY_MK2.get(), "stats_display/dashboards",
                CreateStatsPonderScenes::statsDisplayDashboards, AllCreatePonderTags.DISPLAY_TARGETS);
        itemHelper.addStoryBoard(ModBlocks.VCC_MK_II.get(), "stats_display/basics", Mk2Scenes::vccMk2);

        // Kinetic Flywheel inertia-bank guide (own block + reused kinetic schematic).
        itemHelper.addStoryBoard(ModBlocks.KINETIC_FLYWHEEL.get(), "kinetic_flywheel/inertia_bank", CreateStatsPonderScenes::kineticFlywheelGuide,
            AllCreatePonderTags.DISPLAY_SOURCES);

        // Induction Heater - kinetic stove: boiler/basin heat plus the bulk cooking plate.
        itemHelper.addStoryBoard(ModBlocks.INDUCTION_HEATER.get(), "induction_heater/kinetic_kitchen", KineticControlScenes::inductionHeaterGuide,
            AllCreatePonderTags.DISPLAY_SOURCES);

        // RPM Governor - FCC-commanded kinetic actuator.
        itemHelper.addStoryBoard(ModBlocks.RPM_GOVERNOR.get(), "rpm_governor/governor_overview", KineticControlScenes::rpmGovernorGuide);

        // Smart Gearshift - redstone-selected exact output RPM.
        itemHelper.addStoryBoard(ModBlocks.SMART_GEARSHIFT.get(), "smart_gearshift/gear_stages", KineticControlScenes::smartGearshiftGuide);

        // Control Clutch - remotely engaged/disengaged by a Factory Logic Controller.
        itemHelper.addStoryBoard(ModBlocks.CONTROL_CLUTCH.get(), "control_clutch/remote_engage", KineticControlScenes::controlClutchGuide);

        // Safety Clutch - stress circuit-breaker.
        itemHelper.addStoryBoard(ModBlocks.SAFETY_CLUTCH.get(), "safety_clutch/stress_breaker", KineticControlScenes::safetyClutchGuide);

        // Auto-Transmission Clutch - self-tuning stress governor.
        itemHelper.addStoryBoard(ModBlocks.AUTO_TRANSMISSION_CLUTCH.get(), "auto_transmission_clutch/stress_governor", KineticControlScenes::autoTransmissionClutchGuide);

        // Load Balancer - twin-branch power split.
        itemHelper.addStoryBoard(ModBlocks.LOAD_BALANCER.get(), "load_balancer/twin_branch", KineticControlScenes::loadBalancerGuide);

        // Status Tower Light - operator status beacon.
        itemHelper.addStoryBoard(ModBlocks.STATUS_TOWER_LIGHT.get(), "status_tower_light/operator_beacon", DisplayScenes::statusTowerLightGuide);

        // Data Dial - wall-mounted analog gauge.
        itemHelper.addStoryBoard(ModBlocks.DATA_DIAL.get(), "data_dial/wall_gauge", DisplayScenes::dataDialGuide);
        itemHelper.addStoryBoard(ModBlocks.MINI_DISPLAY.get(), "mini_display/paired_screens", DisplayScenes::miniDisplayGuide);
        itemHelper.addStoryBoard(ModBlocks.HOLOGRAPHIC_PROJECTOR.get(), "holographic_projector/data_hologram", SensorConsoleScenes::holographicProjectorGuide);
        itemHelper.addStoryBoard(ModBlocks.BATTLE_SCREEN_DISPLAY.get(), "battle_screen_display/tactical_hologram", SensorConsoleScenes::battleScreenDisplayGuide);
        itemHelper.addStoryBoard(ModBlocks.DAMAGE_OBSERVER.get(), "damage_observer/hull_sentry", SensorConsoleScenes::damageObserverGuide);
        itemHelper.addStoryBoard(ModBlocks.STATS_RECORDER.get(), "stats_recorder/data_logger", RecorderScenes::statsRecorderGuide);
        itemHelper.addStoryBoard(ModBlocks.CALCULATOR.get(), "calculator/working_calculator", CalculatorScenes::calculatorGuide);
        itemHelper.addStoryBoard(ModBlocks.FACTORY_LOGIC_CONTROLLER.get(), "factory_logic_controller/automation_brain", LogisticsScenes::factoryLogicControllerGuide);
        itemHelper.addStoryBoard(ModBlocks.FREIGHT_MANIFEST_CLIPBOARD.get(), "freight_manifest_clipboard/station_manifest", LogisticsScenes::freightManifestClipboardGuide);
        itemHelper.addStoryBoard(ModBlocks.OVERFLOW_GATE.get(), "overflow_gate/smart_splitter", OverflowGateScenes::overflowGateGuide);
        itemHelper.addStoryBoard(ModBlocks.DISPATCH_BOARD.get(), "dispatch_board/station_board", DispatchScenes::dispatchBoardGuide);
        itemHelper.addStoryBoard(ModBlocks.FLIGHT_CONTROL_COMPUTER.get(), "vcc/overview", VccScenes::vccOverview);
        itemHelper.addStoryBoard(ModBlocks.FLIGHT_CONTROL_COMPUTER.get(), "vcc/linked", VccScenes::vccLinked);
        itemHelper.addStoryBoard(ModBlocks.PATHFINDER_COMPUTER.get(), "pathfinder_computer/route_planner", NavigationScenes::pathfinderComputerGuide);
        itemHelper.addStoryBoard(ModBlocks.PATH_SCANNER.get(), "path_scanner/terrain_scan", NavigationScenes::pathScannerGuide);
        itemHelper.addStoryBoard(ModBlocks.STATS_ALARM.get(), "stats_alarm/threshold_alert", AlarmScenes::statsAlarmGuide);
        itemHelper.addStoryBoard(ModBlocks.TABLE_ALARM_CLOCK.get(), "table_alarm_clock/alarm_timer", AlarmScenes::tableAlarmClockGuide);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<ItemLike> itemHelper = helper.withKeyFunction(itemLike -> BuiltInRegistries.ITEM.getKey(itemLike.asItem()));

        itemHelper.addToTag(AllCreatePonderTags.DISPLAY_TARGETS)
                .add(ModBlocks.STATS_DISPLAY_PANEL.get())
                .add(ModBlocks.STATS_RECORDER.get())
                .add(ModBlocks.HOLOGRAPHIC_PROJECTOR.get())
                .add(ModBlocks.STATUS_TOWER_LIGHT.get())
                .add(ModBlocks.DATA_DIAL.get())
            .add(ModBlocks.MINI_DISPLAY.get())
            .add(ModBlocks.BATTLE_SCREEN_DISPLAY.get())
            .add(ModBlocks.FLIGHT_CONTROL_COMPUTER.get())
            .add(ModBlocks.PATHFINDER_COMPUTER.get())
            .add(ModBlocks.STATS_DISPLAY_MK2.get());

        itemHelper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES)
                .add(ModBlocks.PRODUCTION_DETECTOR.get())
                .add(ModBlocks.SAFETY_CLUTCH.get())
                .add(ModBlocks.AUTO_TRANSMISSION_CLUTCH.get())
                .add(ModBlocks.CONTROL_CLUTCH.get())
                .add(ModBlocks.LOAD_BALANCER.get())
                .add(ModBlocks.SMART_GEARSHIFT.get())
                .add(ModBlocks.MINI_DISPLAY.get())
                .add(ModBlocks.FREIGHT_MANIFEST_CLIPBOARD.get())
                .add(ModBlocks.DAMAGE_OBSERVER.get())
                .add(ModBlocks.PATH_SCANNER.get())
                .add(ModBlocks.PATHFINDER_COMPUTER.get())
                .add(ModBlocks.KINETIC_FLYWHEEL.get())
                .add(ModBlocks.FLOW_METER.get());
    }
}
