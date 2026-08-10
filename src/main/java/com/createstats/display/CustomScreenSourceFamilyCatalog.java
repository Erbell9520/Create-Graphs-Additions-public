package com.createstats.display;

import com.createstats.blockentity.SourceConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomScreenSourceFamilyCatalog {
    public static final String GENERIC_UNKNOWN = "generic.unknown";

    private static final LinkedHashMap<String, SourceFamilyDefinition> DEFINITIONS = new LinkedHashMap<String, SourceFamilyDefinition>();
    private static final Map<String, SourceFamilyDefinition> EXACT_SOURCE_TYPES = new HashMap<String, SourceFamilyDefinition>();
    private static final Map<String, SourceFamilyDefinition> LEGACY_ALIASES = new HashMap<String, SourceFamilyDefinition>();

    static {
        register(def("kinetics.stressometer", "Stressometer", SourceFamilyDefinition.SourceFamilyGroup.KINETICS,
                List.of("Stressometer"), List.of(), SourceFamilyDefinition.RendererFamily.STRESSOMETER, List.of(), SourceFamilyDefinition.CompactPolicy.SIMPLIFY));
        register(def("kinetics.speedometer", "Speedometer", SourceFamilyDefinition.SourceFamilyGroup.KINETICS,
                List.of("Speedometer"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("kinetics.clutch", "Clutch", SourceFamilyDefinition.SourceFamilyGroup.KINETICS,
                List.of("Safety Clutch", "Auto Variable Clutch"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("kinetics.flywheel", "Kinetic Flywheel", SourceFamilyDefinition.SourceFamilyGroup.KINETICS,
                List.of("Kinetic Flywheel"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("kinetics.load_balancer", "Load Balancer", SourceFamilyDefinition.SourceFamilyGroup.KINETICS,
                List.of("Load Balancer"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("energy.accumulator", "Modular Accumulator", SourceFamilyDefinition.SourceFamilyGroup.ENERGY,
                List.of("Modular Accumulator"), List.of(), SourceFamilyDefinition.RendererFamily.ACCUMULATOR, List.of(), SourceFamilyDefinition.CompactPolicy.SIMPLIFY));
        register(def("energy.capacitor_bank", "Capacitor Bank", SourceFamilyDefinition.SourceFamilyGroup.ENERGY,
                List.of("Capacitor Bank"), List.of(), SourceFamilyDefinition.RendererFamily.CAPACITOR_BANK, List.of(), SourceFamilyDefinition.CompactPolicy.SIMPLIFY));
        register(def("energy.alternator", "Alternator", SourceFamilyDefinition.SourceFamilyGroup.ENERGY,
                List.of("Alternator"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("energy.motor", "Electric Motor", SourceFamilyDefinition.SourceFamilyGroup.ENERGY,
                List.of("Electric Motor"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("energy.connector", "Energy Connector", SourceFamilyDefinition.SourceFamilyGroup.ENERGY,
                List.of("Portable Energy Interface", "Energy Connector", "Large Energy Connector", "Tesla Coil", "Creative Energy Source"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("factory.stockpile_switch", "Stockpile Switch", SourceFamilyDefinition.SourceFamilyGroup.FACTORY_INVENTORY,
                List.of("Stockpile Switch"), List.of(), SourceFamilyDefinition.RendererFamily.STOCKPILE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("factory.observer", "Observer", SourceFamilyDefinition.SourceFamilyGroup.FACTORY_INVENTORY,
                List.of("Content Observer", "Smart Observer"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("factory.production", "Production / Inventory", SourceFamilyDefinition.SourceFamilyGroup.FACTORY_INVENTORY,
                List.of("Production Detector", "Inventory Display"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("factory.panel", "Factory Panel", SourceFamilyDefinition.SourceFamilyGroup.FACTORY_INVENTORY,
                List.of("Factory Panel"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("heat.induction_heater", "Induction Heater", SourceFamilyDefinition.SourceFamilyGroup.FACTORY_INVENTORY,
                List.of("Induction Heater"), List.of(), SourceFamilyDefinition.RendererFamily.INDUCTION_HEATER, List.of(), SourceFamilyDefinition.CompactPolicy.SIMPLIFY));
        register(def("diesel.engine", "Diesel Engine", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Diesel Engine", "Modular Diesel Engine", "Huge Diesel Engine", "Diesel Engine Group"), List.of(), SourceFamilyDefinition.RendererFamily.DIESEL_ENGINE, List.of(CustomScreenModuleType.DIESEL_ENGINE_PAGE), SourceFamilyDefinition.CompactPolicy.HIDE));
        register(def("diesel.portable_engine", "Portable Engine", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Portable Engine"), List.of(), SourceFamilyDefinition.RendererFamily.PORTABLE_ENGINE, List.of(), SourceFamilyDefinition.CompactPolicy.SIMPLIFY));
        register(def("diesel.reserve", "Diesel Fuel Reserve", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Diesel Fuel Reserve"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("diesel.process", "Diesel Process", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Diesel Distillation Tower", "Oil Barrel"), List.of(), SourceFamilyDefinition.RendererFamily.DIESEL_PROCESS, List.of(), SourceFamilyDefinition.CompactPolicy.HIDE));
        register(def("diesel.pumpjack", "Oil Pumpjack", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Oil Pumpjack"), List.of(), SourceFamilyDefinition.RendererFamily.DIESEL_PROCESS, List.of(), SourceFamilyDefinition.CompactPolicy.SIMPLIFY));
        register(def("diesel.burner", "Diesel Burner", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Diesel Burner"), List.of(), SourceFamilyDefinition.RendererFamily.DIESEL_PROCESS, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("fluid.tank", "Fluid Tank", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Fluid Tank"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("fluid.flow_meter", "Flow Meter", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Flow Meter"), List.of(), SourceFamilyDefinition.RendererFamily.FLOW_METER, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("steam.boiler", "Steam Boiler", SourceFamilyDefinition.SourceFamilyGroup.DIESEL_FLUIDS,
                List.of("Steam Boiler"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("flight.aero_deck", "Aero Flight Deck", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Aero Flight Deck"), List.of(), SourceFamilyDefinition.RendererFamily.AERO_FLIGHT_DECK, List.of(CustomScreenModuleType.AERO_FLIGHT_DECK), SourceFamilyDefinition.CompactPolicy.HIDE));
        register(def("flight.aeroline_director", "Aeroline Director", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Aeroline Director"), List.of(), SourceFamilyDefinition.RendererFamily.AEROLINE_DIRECTOR, List.of(CustomScreenModuleType.AEROLINE_DIRECTOR), SourceFamilyDefinition.CompactPolicy.HIDE));
        register(def("flight.navigation_table", "Navigation Table", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Navigation Table"), List.of(), SourceFamilyDefinition.RendererFamily.NAVIGATION, List.of(), SourceFamilyDefinition.CompactPolicy.SIMPLIFY));
        register(def("flight.velocity_sensor", "Velocity Sensor", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Velocity Sensor"), List.of(), SourceFamilyDefinition.RendererFamily.SENSOR, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("flight.gimbal_sensor", "Gimbal Sensor", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Gimbal Sensor"), List.of(), SourceFamilyDefinition.RendererFamily.SENSOR, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("flight.altitude_sensor", "Altitude Sensor", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Altitude Sensor"), List.of(), SourceFamilyDefinition.RendererFamily.SENSOR, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("flight.docking_connector", "Docking Connector", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Docking Connector"), List.of(), SourceFamilyDefinition.RendererFamily.SENSOR, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("flight.steering_wheel", "Steering Wheel", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Steering Wheel"), List.of(), SourceFamilyDefinition.RendererFamily.SENSOR, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("flight.air_support", "Hot Air / Steam Vent", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Hot Air Burner", "Steam Vent"), List.of(), SourceFamilyDefinition.RendererFamily.TEXT_UTILITY, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("flight.contraption_info", "Contraption Info", SourceFamilyDefinition.SourceFamilyGroup.FLIGHT_NAVIGATION,
                List.of("Contraption Info"), List.of(), SourceFamilyDefinition.RendererFamily.CONTRAPTION, List.of(), SourceFamilyDefinition.CompactPolicy.HIDE));
        register(def("battle.bridge", "Battle Bridge", SourceFamilyDefinition.SourceFamilyGroup.BATTLE,
                List.of("Battle Bridge"), List.of("Battle Screen"), SourceFamilyDefinition.RendererFamily.BATTLE_BRIDGE, List.of(CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION, CustomScreenModuleType.BATTLE_TACTICAL_V2), SourceFamilyDefinition.CompactPolicy.HIDE));
        register(def("sensor.laser", "Laser Sensor", SourceFamilyDefinition.SourceFamilyGroup.SENSORS_TEXT,
                List.of("Laser Sensor"), List.of(), SourceFamilyDefinition.RendererFamily.SENSOR, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("sensor.optical", "Optical Sensor", SourceFamilyDefinition.SourceFamilyGroup.SENSORS_TEXT,
                List.of("Optical Sensor"), List.of(), SourceFamilyDefinition.RendererFamily.SENSOR, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("text.linked_typewriter", "Linked Typewriter", SourceFamilyDefinition.SourceFamilyGroup.SENSORS_TEXT,
                List.of("Linked Typewriter"), List.of(), SourceFamilyDefinition.RendererFamily.TEXT_UTILITY, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("text.mini_display", "Mini Display", SourceFamilyDefinition.SourceFamilyGroup.SENSORS_TEXT,
                List.of("Mini Display"), List.of(), SourceFamilyDefinition.RendererFamily.TEXT_UTILITY, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("logistics.train", "Train Logistics", SourceFamilyDefinition.SourceFamilyGroup.LOGISTICS,
                List.of("Train Station", "Freight Manifest", "Dispatch Board"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def("logistics.tunnel", "Tunnel", SourceFamilyDefinition.SourceFamilyGroup.LOGISTICS,
                List.of("Tunnel"), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
        register(def(GENERIC_UNKNOWN, "Generic Source", SourceFamilyDefinition.SourceFamilyGroup.GENERIC,
                List.of(), List.of(), SourceFamilyDefinition.RendererFamily.NONE, List.of(), SourceFamilyDefinition.CompactPolicy.KEEP));
    }

    private CustomScreenSourceFamilyCatalog() {
    }

    public static SourceFamilyDefinition familyForSource(SourceConfig source) {
        return familyForSourceType(source != null ? source.getSourceType() : "");
    }

    public static SourceFamilyDefinition familyForSourceType(String sourceType) {
                final String key = key(sourceType);
        SourceFamilyDefinition definition = EXACT_SOURCE_TYPES.get(key);
        if (definition != null) {
            return definition;
        }
        definition = LEGACY_ALIASES.get(key);
        return definition != null ? definition : definition(GENERIC_UNKNOWN);
    }

    public static SourceFamilyDefinition definition(String familyId) {
        final SourceFamilyDefinition definition = DEFINITIONS.get(safe(familyId));
        return definition != null ? definition : DEFINITIONS.get(GENERIC_UNKNOWN);
    }

    public static List<SourceFamilyDefinition> allDefinitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static boolean isLegacyAlias(String sourceType) {
                return LEGACY_ALIASES.containsKey(key(sourceType));
    }

    public static boolean isAliasForFamily(String sourceType, String familyId) {
                final SourceFamilyDefinition definition = LEGACY_ALIASES.get(key(sourceType));
        return definition != null && definition.getFamilyId().equals(safe(familyId));
    }

        public static boolean matchesSourceTypeFilter(SourceConfig source, String sourceTypeFilter) {
                return source != null && sourceTypeMatchesFamilyFilter(source.getSourceType(), sourceTypeFilter);
        }

        public static boolean sourceTypeMatchesFamilyFilter(String sourceType, String sourceTypeFilter) {
                final String filter = safe(sourceTypeFilter);
                if (filter.isEmpty()) {
                        return true;
                }
                final SourceFamilyDefinition filterFamily = familyForSourceType(filter);
                if (!GENERIC_UNKNOWN.equals(filterFamily.getFamilyId())) {
                        return familyForSourceType(sourceType).getFamilyId().equals(filterFamily.getFamilyId());
                }
                return safe(sourceType).equalsIgnoreCase(filter);
        }

    private static void register(SourceFamilyDefinition definition) {
        DEFINITIONS.put(definition.getFamilyId(), definition);
        for (String sourceType : definition.getExactSourceTypes()) {
                        EXACT_SOURCE_TYPES.put(key(sourceType), definition);
        }
        for (String alias : definition.getLegacyAliases()) {
                        LEGACY_ALIASES.put(key(alias), definition);
        }
    }

    private static SourceFamilyDefinition def(String familyId,
                                              String displayName,
                                              SourceFamilyDefinition.SourceFamilyGroup group,
                                              Collection<String> exactSourceTypes,
                                              Collection<String> legacyAliases,
                                              SourceFamilyDefinition.RendererFamily rendererFamily,
                                              Collection<CustomScreenModuleType> allowedMacroScenes,
                                              SourceFamilyDefinition.CompactPolicy compactPolicy) {
        return new SourceFamilyDefinition(familyId,
                displayName,
                group,
                exactSourceTypes,
                legacyAliases,
                rendererFamily,
                allowedMacroScenes,
                SourceFamilyDefinition.GenericFallbackPolicy.ALLOW_GENERIC_WIDGETS,
                compactPolicy);
    }

    private static String safe(String value) {
        return value != null ? value.trim() : "";
    }

        private static String key(String value) {
                return safe(value).toLowerCase(java.util.Locale.ROOT);
        }
}