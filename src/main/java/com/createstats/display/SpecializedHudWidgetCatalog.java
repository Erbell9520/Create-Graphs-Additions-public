package com.createstats.display;

import java.util.List;

public final class SpecializedHudWidgetCatalog {
    private SpecializedHudWidgetCatalog() {
    }

    public record Definition(StressometerHudState.Part part, String label, String primaryKey, String[] keys) {
    }

    public static List<Definition> definitionsFor(String sourceType) {
        if (sourceType == null) {
            return List.of();
        }
                final String familyId = CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType).getFamilyId();
                return switch (familyId) {
                        case "kinetics.stressometer" -> List.of(
                    def(StressometerHudState.Part.LOAD_GAUGE, "Stress Load Gauge", "stress_pct", "stress_pct", "stress_su", "capacity_su"),
                    def(StressometerHudState.Part.USED_CAPACITY, "Stress Used / Capacity", "stress_su", "stress_su", "capacity_su"),
                    def(StressometerHudState.Part.STRESS_PERCENT, "Load Percent", "stress_pct", "stress_pct"),
                    def(StressometerHudState.Part.REMAINING_STRESS, "Stress Headroom", "remaining_su", "remaining_su"),
                    def(StressometerHudState.Part.RPM, "Network RPM", "rpm", "rpm"),
                    def(StressometerHudState.Part.STATUS, "Stress State", "overstressed", "overstressed", "stress_pct")
            );
            case "factory.stockpile_switch" -> List.of(
                    def(StressometerHudState.Part.STOCKPILE_GAUGE, "Threshold Gauge", "fill_pct", "stock_level", "stock_capacity", "lower_thresh", "upper_thresh", "powered"),
                    def(StressometerHudState.Part.STOCKPILE_OUTPUT, "Output State", "powered", "powered", "fill_pct"),
                    def(StressometerHudState.Part.STOCKPILE_AMOUNT, "Stored Amount", "stock_level", "stock_level", "stock_capacity"),
                    def(StressometerHudState.Part.STOCKPILE_THRESHOLDS, "Threshold Range", "lower_thresh", "lower_thresh", "upper_thresh"),
                    def(StressometerHudState.Part.STOCKPILE_TREND, "Fill Trend", "fill_pct", "fill_pct")
            );
            case "factory.observer" -> List.of(
                    def(StressometerHudState.Part.OBSERVER_SUMMARY, "Observer Status", "total_items", "total_items", "unique_types"),
                    def(StressometerHudState.Part.OBSERVER_ITEMS, "Item List", "unique_types", "unique_types"),
                    def(StressometerHudState.Part.OBSERVER_FLUIDS, "Fluid List", "unique_types", "unique_types")
            );
            case "fluid.tank" -> List.of(
                    def(StressometerHudState.Part.FLUID_LEVEL, "Tank Level", "tank_fill", "tank_fill", "fluid_amount", "tank_capacity"),
                    def(StressometerHudState.Part.FLUID_VOLUME, "Fluid Volume", "fluid_amount", "fluid_amount", "tank_capacity"),
                    def(StressometerHudState.Part.FLUID_COMPACT, "Tank Overview", "tank_fill", "tank_fill", "fluid_amount", "tank_capacity")
            );
            case "diesel.process" -> List.of(
                    def(StressometerHudState.Part.FLUID_LEVEL, "Diesel Fluid Level", "fluid_fill_pct", "fluid_fill_pct", "tank_fill", "fluid_amount", "fluid_capacity"),
                    def(StressometerHudState.Part.FLUID_VOLUME, "Stored Fluid", "fluid_amount", "fluid_amount", "fluid_capacity", "tank_capacity"),
                    def(StressometerHudState.Part.FLUID_COMPACT, "Plant Tank Overview", "fluid_fill_pct", "fluid_fill_pct", "process_progress_pct", "heat_level")
            );
            case "diesel.pumpjack" -> List.of(
                    def(StressometerHudState.Part.FLUID_LEVEL, "Pumpjack Output Level", "fluid_fill_pct", "fluid_fill_pct", "fluid_amount", "fluid_capacity"),
                    def(StressometerHudState.Part.FLUID_VOLUME, "Oil Remaining", "oil_remaining_mb", "oil_remaining_mb", "pipe_length"),
                    def(StressometerHudState.Part.FLUID_COMPACT, "Pumpjack State", "pumpjack_started", "pumpjack_started", "pumpjack_valid", "pipe_length")
            );
            case "diesel.burner" -> List.of(
                    def(StressometerHudState.Part.BOILER_HEAT, "Burner Heat", "burner_heat", "burner_heat", "heat_level"),
                    def(StressometerHudState.Part.FLUID_LEVEL, "Burner Fuel", "fluid_fill_pct", "fluid_fill_pct", "fluid_amount", "fluid_capacity"),
                    def(StressometerHudState.Part.FLUID_COMPACT, "Valve", "valve_pct", "valve_pct", "burner_lit")
            );
            case "steam.boiler" -> List.of(
                    def(StressometerHudState.Part.BOILER_HEAT, "Boiler Heat", "boiler_gauge", "boiler_gauge", "heat_level"),
                    def(StressometerHudState.Part.BOILER_WATER, "Water Supply", "water_supply", "water_supply"),
                    def(StressometerHudState.Part.BOILER_ENGINES, "Engines / Whistles", "engines", "engines", "whistles")
            );
            case "factory.production" -> List.of(
                    // Primary key "throughput" uses label-substring match to find "Throughput • {item}" channels
                    def(StressometerHudState.Part.PRODUCTION_RATE, "Throughput", "throughput", "throughput", "total_seen"),
                    def(StressometerHudState.Part.PRODUCTION_TOTAL, "Total Seen", "total_seen", "total_seen"),
                    def(StressometerHudState.Part.PRODUCTION_CONTENT, "Content View", "total_items", "total_items", "unique_types")
            );
            case "factory.panel" -> List.of(
                    // Primary key "stock" uses label-substring match to find "{item} Stock" channels
                    def(StressometerHudState.Part.FACTORY_STOCK, "Factory Stock", "stock", "stock", "target"),
                    // Primary key "target" uses label-substring match to find "{item} Target" channels
                    def(StressometerHudState.Part.FACTORY_SHORTFALL, "Shortfall / Target", "target", "target", "promised", "stock")
            );
            case "logistics.train" -> List.of(
                    def(StressometerHudState.Part.TRAIN_STATION_STATUS, "Station Status", "station_status", "station_status"),
                    def(StressometerHudState.Part.INBOUND_TRAINS, "Inbound Trains", "inbound_train", "inbound_train"),
                    def(StressometerHudState.Part.SCHEDULE_DESTINATION, "Schedule / Destination", "next_destination", "next_destination"),
                    def(StressometerHudState.Part.SIGNAL_WAIT, "Signal Wait", "signal_wait", "signal_wait")
            );
            case "diesel.portable_engine" -> List.of(
                    def(StressometerHudState.Part.ENGINE_BURN, "Burn Tracker", "burn_pct", "burn_pct", "burn_time", "burn_total"),
                    def(StressometerHudState.Part.ENGINE_SPEED, "Generated Speed", "generated_speed", "generated_speed", "rpm"),
                    def(StressometerHudState.Part.ENGINE_FUEL, "Fuel Status", "fuel_infinite", "fuel_infinite", "superheated"),
                    def(StressometerHudState.Part.ENGINE_STATUS, "Engine State", "running", "running", "superheated")
            );
            case "energy.accumulator" -> List.of(
                    def(StressometerHudState.Part.FLUID_LEVEL, "Accumulator Charge", "energy_fill_pct", "energy_fill_pct", "energy_stored", "energy_capacity"),
                    def(StressometerHudState.Part.AIR_FLOW, "Energy Flow", "energy_net_rate", "energy_net_rate", "energy_input_rate", "energy_output_rate"),
                    def(StressometerHudState.Part.STOCKPILE_AMOUNT, "Stored Energy", "energy_stored", "energy_stored", "energy_capacity"),
                    def(StressometerHudState.Part.STATUS, "Accumulator Links", "connector_count", "connector_count", "accumulator_size", "energy_can_extract", "energy_can_receive")
            );
            case "energy.alternator" -> List.of(
                    def(StressometerHudState.Part.ENGINE_SPEED, "Alternator RPM", "rpm", "rpm"),
                    def(StressometerHudState.Part.AIR_FLOW, "Generated FE/t", "energy_output_rate", "energy_output_rate"),
                    def(StressometerHudState.Part.STATUS, "Alternator State", "running", "running")
            );
            case "energy.motor" -> List.of(
                    def(StressometerHudState.Part.ENGINE_SPEED, "Motor RPM", "rpm", "rpm"),
                    def(StressometerHudState.Part.AIR_FLOW, "Consumed FE/t", "energy_input_rate", "energy_input_rate"),
                    def(StressometerHudState.Part.USED_CAPACITY, "Generated Stress", "stress_su", "stress_su"),
                    def(StressometerHudState.Part.STATUS, "Motor State", "running", "running")
            );
            case "energy.connector" -> List.of(
                    def(StressometerHudState.Part.FLUID_LEVEL, "Energy Buffer", "energy_fill_pct", "energy_fill_pct", "energy_stored", "energy_capacity"),
                    def(StressometerHudState.Part.AIR_FLOW, "Energy Flow", "energy_net_rate", "energy_net_rate", "energy_input_rate", "energy_output_rate"),
                    def(StressometerHudState.Part.STATUS, "Energy State", "connected", "connected", "energy_can_receive", "energy_can_extract")
            );
            case "diesel.engine" -> List.of(
                    def(StressometerHudState.Part.ENGINE_BURN, "Fuel Tracker", "fuel_fill_pct", "fuel_fill_pct", "burn_pct", "fuel_amount", "fuel_capacity"),
                    def(StressometerHudState.Part.FLUID_LEVEL, "Linked Fuel Reserve", "reserve_fuel_fill_pct", "reserve_fuel_fill_pct", "reserve_fuel_amount", "reserve_fuel_capacity", "reserve_fuel_mismatch", "reserve_links"),
                    def(StressometerHudState.Part.ENGINE_SPEED, "Generated Speed", "generated_speed", "generated_speed", "generated_rpm", "rpm"),
                    def(StressometerHudState.Part.ENGINE_FUEL, "Fuel Status", "valid_fuel", "valid_fuel", "fuel_fill_pct", "redstone_disabled"),
                    def(StressometerHudState.Part.ENGINE_STATUS, "Diesel State", "running", "running", "enabled", "redstone_disabled")
            );
            case "diesel.reserve" -> List.of(
                    def(StressometerHudState.Part.FLUID_LEVEL, "Reserve Fuel Gauge", "reserve_fuel_fill_pct", "reserve_fuel_fill_pct", "reserve_fuel_amount", "reserve_fuel_capacity"),
                    def(StressometerHudState.Part.FLUID_VOLUME, "Reserve Stored Fuel", "reserve_fuel_amount", "reserve_fuel_amount", "reserve_fuel_capacity"),
                    def(StressometerHudState.Part.STATUS, "Reserve Link State", "reserve_links", "reserve_links", "reserve_fuel_mismatch")
            );
            case "kinetics.speedometer" -> List.of(
                    def(StressometerHudState.Part.RPM, "Network RPM", "rpm", "rpm", "abs_rpm"),
                    def(StressometerHudState.Part.STATUS, "Running State", "running", "running")
            );
            case "flight.velocity_sensor" -> List.of(
                    def(StressometerHudState.Part.VELOCITY_AXIS, "Axis Velocity", "adjusted_velocity", "adjusted_velocity"),
                    def(StressometerHudState.Part.VELOCITY_HORIZONTAL, "Horizontal Velocity", "horizontal_velocity", "horizontal_velocity"),
                    def(StressometerHudState.Part.VELOCITY_REDSTONE, "Signed Redstone", "signed_redstone", "signed_redstone", "redstone_strength"),
                    def(StressometerHudState.Part.VELOCITY_HEADING, "Direction / State", "heading_yaw", "heading_yaw", "heading_confidence", "velocity_fallback")
            );
            case "flight.gimbal_sensor" -> List.of(
                    def(StressometerHudState.Part.GIMBAL_ANGLES, "Tilt Row", "x_angle", "x_angle", "z_angle", "attitude_pitch"),
                    def(StressometerHudState.Part.GIMBAL_POWER, "Power Grid", "power_down", "power_down", "power_up", "power_north", "power_south", "power_west", "power_east"),
                    def(StressometerHudState.Part.AERO_HORIZON, "Attitude Indicator", "attitude_bank", "attitude_bank", "attitude_pitch", "attitude_yaw")
            );
            case "flight.altitude_sensor" -> List.of(
                    def(StressometerHudState.Part.ALTITUDE_HEIGHT, "World Height", "world_height", "world_height", "normalized_height"),
                    def(StressometerHudState.Part.ALTITUDE_AIR_SIGNAL, "Air Pressure Signal", "air_pressure", "air_pressure", "signal"),
                    def(StressometerHudState.Part.ALTITUDE_BAND, "Band Range", "band_min_height", "band_min_height", "band_max_height")
            );
            case "flight.docking_connector" -> List.of(
                    def(StressometerHudState.Part.DOCKING_STATE, "Docking State", "connector_state", "connector_state", "locked", "extended"),
                    def(StressometerHudState.Part.DOCKING_PAIR, "Pair / Distance", "paired", "paired", "pair_loaded", "closest_pair_distance"),
                    def(StressometerHudState.Part.DOCKING_POWER, "Power / Lock", "powered", "powered", "locked"),
                    def(StressometerHudState.Part.DOCKING_POSITION, "Pair Position", "pair_position", "pair_position", "pair_sublevel", "pair_x", "pair_y", "pair_z")
            );
            case "flight.steering_wheel" -> List.of(
                    def(StressometerHudState.Part.STEERING_ANGLE, "Angle Bar", "angle", "angle", "target_angle", "pending_target"),
                    def(StressometerHudState.Part.STEERING_SPEED, "Generated / Logical", "generated_speed", "generated_speed", "logical_speed", "rpm"),
                    def(StressometerHudState.Part.STEERING_ACTIVITY, "Steering Activity", "in_use", "held", "in_use", "running")
            );
            case "sensor.laser" -> List.of(
                    def(StressometerHudState.Part.LASER_POWER, "Laser Power", "current_power", "current_power", "powered"),
                    def(StressometerHudState.Part.LASER_HIT, "Hit / Range", "closest_hit_distance", "closest_hit_distance", "hit_present")
            );
            case "sensor.optical" -> List.of(
                    def(StressometerHudState.Part.OPTICAL_RAY, "Ray / Hit", "ray_distance", "ray_distance", "hit_block_distance", "hit_present"),
                    def(StressometerHudState.Part.OPTICAL_BLOCK, "Detected Block", "hit_block", "hit_block"),
                    def(StressometerHudState.Part.OPTICAL_POWER, "Optical Power", "powered", "powered", "hit_present")
            );
            case "flight.navigation_table" -> List.of(
                    def(StressometerHudState.Part.NAV_DISTANCE, "Target Distance", "distance_to_target", "distance_to_target", "last_distance_to_target"),
                    def(StressometerHudState.Part.NAV_DELTA, "Distance Delta", "distance_delta", "distance_delta", "target_dx", "target_dz"),
                    def(StressometerHudState.Part.NAV_ANGLE, "Relative Angle", "relative_angle", "relative_angle"),
                    def(StressometerHudState.Part.NAV_POWER, "Navigation Power", "power_down", "power_down", "power_up", "power_north", "power_south", "power_west", "power_east")
            );
            case "text.linked_typewriter" -> List.of(
                    def(StressometerHudState.Part.TYPEWRITER_TEXT, "Live Text", "typed_text", "typed_text"),
                    def(StressometerHudState.Part.TYPEWRITER_STATUS, "Keys / Power", "active_keys", "active_keys", "typed_length", "powered")
            );
            case "flight.air_support" -> List.of(
                    def(StressometerHudState.Part.AIR_SIGNAL, "Signal / Output", "signal_strength", "signal_strength", "gas_output", "powered", "can_output"),
                    def(StressometerHudState.Part.AIR_VOLUME, "Target / Capacity", "gas_target_volume", "gas_target_volume", "gas_capacity_volume", "gas_type"),
                    def(StressometerHudState.Part.AIR_FLOW, "Gas Flow Rate", "output_pct", "output_pct", "emitter_efficiency"),
                    def(StressometerHudState.Part.AIR_LIFT, "Balloon Lift", "balloon_lift", "balloon_lift")
            );
            case "flight.contraption_info" -> List.of(
                    def(StressometerHudState.Part.CONTRAPTION_SOLVER, "Solver Panel", "axis_mode", "axis_mode", "directional_coverage", "total_mass"),
                    def(StressometerHudState.Part.CONTRAPTION_FORCES, "Force Cards", "forward_thrust", "forward_thrust", "reverse_force", "gravity", "support"),
                    def(StressometerHudState.Part.CONTRAPTION_SUPPORT, "Support Cards", "support", "support", "levitation_support", "balloon_support", "aero_support", "propulsion_support"),
                    def(StressometerHudState.Part.CONTRAPTION_MARGINS, "Solver Footer", "vertical_margin", "vertical_margin", "forward_margin", "state")
            );
            case "kinetics.clutch" -> List.of(
                    def(StressometerHudState.Part.LOAD_GAUGE, "Stress Load Gauge", "stress_pct", "stress_pct", "stress_su", "capacity_su"),
                    def(StressometerHudState.Part.RPM, "Network RPM", "rpm", "rpm"),
                    def(StressometerHudState.Part.STATUS, "Clutch State", "running", "running", "overstressed")
            );
            case "kinetics.load_balancer" -> List.of(
                    def(StressometerHudState.Part.LOAD_GAUGE, "Balanced Stress Gauge", "stress_pct", "stress_pct", "stress_su", "capacity_su"),
                    def(StressometerHudState.Part.USED_CAPACITY, "Stress Budget", "stress_su", "stress_su", "capacity_su"),
                    def(StressometerHudState.Part.STATUS, "Topology State", "running", "running", "overstressed")
            );
            case "flight.aero_deck" -> List.of(
                    def(StressometerHudState.Part.AERO_HEALTH_STRIP, "Source Health Strip", "state", "state", "running", "adjusted_velocity", "world_height"),
                    def(StressometerHudState.Part.AERO_DRIVE, "Drive / Speed", "adjusted_velocity", "adjusted_velocity", "generated_speed", "burn_pct"),
                    def(StressometerHudState.Part.AERO_ALTITUDE, "Altitude Card", "world_height", "world_height", "air_pressure"),
                    def(StressometerHudState.Part.AERO_RANGE, "Range Card", "distance_to_target", "distance_to_target", "last_distance_to_target", "ray_distance", "closest_hit_distance"),
                    def(StressometerHudState.Part.AERO_ORIENTATION, "Orientation / Trim", "attitude_pitch", "attitude_pitch", "attitude_bank", "attitude_yaw", "target_angle"),
                    def(StressometerHudState.Part.AERO_VERTICAL_MARGIN, "Vertical Margin", "vertical_margin", "vertical_margin", "support", "gravity"),
                    def(StressometerHudState.Part.AERO_FORWARD_MARGIN, "Forward Margin", "forward_margin", "forward_margin", "forward_thrust", "reverse_force"),
                    def(StressometerHudState.Part.AERO_BRIDGE_ORDERS, "Bridge Orders", "relative_angle", "relative_angle", "target_angle", "distance_to_target", "next_destination"),
                    def(StressometerHudState.Part.AERO_ENGINE_STRIP, "Propulsion Strip", "burn_pct", "burn_pct", "generated_speed", "superheated", "fuel_infinite"),
                    def(StressometerHudState.Part.AERO_SHIP_STATUS, "Ship Status", "state", "state", "total_mass", "support"),
                    def(StressometerHudState.Part.AERO_HORIZON, "Attitude Indicator", "attitude_bank", "attitude_bank", "attitude_pitch", "attitude_yaw")
            );
            case "flight.aeroline_director" -> List.of(
                    def(StressometerHudState.Part.AEROLINE_MODE_BADGE, "Mode Badge", "state", "state", "running"),
                    def(StressometerHudState.Part.AEROLINE_STATUS_STRIP, "Status Strip", "state", "state", "distance_to_target", "powered", "running"),
                    def(StressometerHudState.Part.AEROLINE_DIRECTOR_WINDOW, "Director Window", "attitude_pitch", "attitude_pitch", "attitude_bank", "relative_angle", "distance_to_target"),
                    def(StressometerHudState.Part.AEROLINE_TARGET_SCOPE, "Target Scope", "distance_to_target", "distance_to_target", "relative_angle", "target_dx", "target_dz"),
                    def(StressometerHudState.Part.AEROLINE_VELOCITY_SCOPE, "Velocity Scope", "adjusted_velocity", "adjusted_velocity", "generated_speed", "logical_speed"),
                    def(StressometerHudState.Part.AEROLINE_ALTITUDE_SCOPE, "Altitude Scope", "world_height", "world_height", "vertical_margin", "air_pressure"),
                    def(StressometerHudState.Part.AEROLINE_FOOTER, "Director Footer", "state", "state", "vertical_margin", "distance_to_target", "next_destination")
            );
            case "logistics.tunnel" -> List.of(
                    def(StressometerHudState.Part.PRODUCTION_RATE, "Throughput", "line1", "line1"),
                    def(StressometerHudState.Part.PRODUCTION_TOTAL, "Passed Items", "total_items", "total_items")
            );
            case "text.mini_display" -> List.of(
                    def(StressometerHudState.Part.TYPEWRITER_TEXT, "Display Text", "display_text", "display_text"),
                    def(StressometerHudState.Part.TYPEWRITER_STATUS, "Display Status", "display_state", "display_state", "source_count")
            );
            default -> List.of();
        };
    }

    public static Definition definitionFor(String sourceType, StressometerHudState.Part part) {
        for (Definition definition : definitionsFor(sourceType)) {
            if (definition.part() == part) {
                return definition;
            }
        }
        return null;
    }

    public static boolean isSupportedSourceType(String sourceType) {
        return !definitionsFor(sourceType).isEmpty();
    }

    private static Definition def(StressometerHudState.Part part, String label, String primaryKey, String... keys) {
        return new Definition(part, label, primaryKey, keys);
    }
}
