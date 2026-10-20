package com.createstats.pa;

import java.util.Map;

import com.createstats.data.MetricType;
import com.createstats.util.InstrumentSourceHelper.SourceKind;

public final class PaVocabulary {
    private PaVocabulary() {
    }

    private static final Map<String, String> MEASURES = Map.ofEntries(
        Map.entry("rpm", Lexicon.SPEED),
        Map.entry("abs_rpm", Lexicon.SPEED),
        Map.entry("generated_speed", Lexicon.SPEED),
        Map.entry("stored_rpm", Lexicon.SPEED),
        Map.entry("stress_pct", Lexicon.STRESS),
        Map.entry("stress_su", Lexicon.STRESS),
        Map.entry("capacity_su", Lexicon.CAPACITY),
        Map.entry("remaining_su", Lexicon.CAPACITY),
        Map.entry("output_su", Lexicon.CAPACITY),
        Map.entry("overstressed", Lexicon.STRESS),
        Map.entry("fill_pct", Lexicon.FILL),
        Map.entry("tank_fill", Lexicon.FILL),
        Map.entry("fluid_amount", Lexicon.LEVEL),
        Map.entry("fluid_capacity", Lexicon.CAPACITY),
        Map.entry("tank_capacity", Lexicon.CAPACITY),
        Map.entry("stock_level", Lexicon.STOCK),
        Map.entry("stock_capacity", Lexicon.CAPACITY),
        Map.entry("total_items", Lexicon.STOCK),
        Map.entry("item_capacity", Lexicon.CAPACITY),
        Map.entry("unique_types", Lexicon.STOCK),
        Map.entry("charge_pct", Lexicon.CHARGE),
        Map.entry("energy_stored", Lexicon.CHARGE),
        Map.entry("energy_output_rate", Lexicon.FLOW),
        Map.entry("energy_net_rate", Lexicon.FLOW),
        Map.entry("heat_level", Lexicon.TEMPERATURE),
        Map.entry("heat_pct", Lexicon.TEMPERATURE),
        Map.entry("boiler_gauge", Lexicon.TEMPERATURE),
        Map.entry("throughput", Lexicon.THROUGHPUT),
        Map.entry("running", Lexicon.LEVEL),
        Map.entry("powered", Lexicon.LEVEL));

    public static String measure(String metricId) {
        if (metricId == null || metricId.isEmpty()) {
            return Lexicon.LEVEL;
        }
        if (metricId.startsWith("item_")) {
            return Lexicon.STOCK;
        }
        return MEASURES.getOrDefault(metricId, Lexicon.LEVEL);
    }

    public static String measureForChannel(String channelId, MetricType type) {
        if (channelId != null) {
            final int auto = channelId.indexOf("_auto_");
            if (auto >= 0) {
                return measure(channelId.substring(auto + "_auto_".length()));
            }
        }
        return measureOfType(type);
    }

    public static String measureOfType(MetricType type) {
        if (type == null) {
            return Lexicon.LEVEL;
        }
        return switch (type) {
            case RPM -> Lexicon.SPEED;
            case STRESS_SU, STRESS_PERCENT -> Lexicon.STRESS;
            case FILL_LEVEL, WATER_SUPPLY -> Lexicon.FILL;
            case FLUID_MB, FLUID_MB_PER_TICK -> Lexicon.LEVEL;
            case ENERGY_FE -> Lexicon.CHARGE;
            case ENERGY_FE_PER_TICK -> Lexicon.FLOW;
            case ITEMS_PER_MIN, ITEMS_PER_SEC, ITEMS_PER_HR -> Lexicon.THROUGHPUT;
            case ITEM_COUNT -> Lexicon.STOCK;
            case HEAT_LEVEL, BURN_TIME -> Lexicon.TEMPERATURE;
            case ENDURANCE_TICKS -> Lexicon.ENDURANCE;
            default -> Lexicon.LEVEL;
        };
    }

    public static String unit(MetricType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case RPM -> Lexicon.RPM;
            case STRESS_SU -> Lexicon.STRESS_UNITS;
            case STRESS_PERCENT, FILL_LEVEL, EFFICIENCY_SCORE -> Lexicon.PERCENT;
            case ENERGY_FE -> Lexicon.FE;
            case ENERGY_FE_PER_TICK -> Lexicon.FE_PER_TICK;
            case FLUID_MB, WATER_SUPPLY, FLUID_MB_PER_TICK -> Lexicon.MILLIBUCKETS;
            case ITEMS_PER_MIN, ITEMS_PER_SEC, ITEMS_PER_HR -> Lexicon.PER_MINUTE;
            case ANGLE_DEGREES -> Lexicon.DEGREES;
            case ENDURANCE_TICKS, BURN_TIME -> Lexicon.SECONDS;
            default -> "";
        };
    }

    public static double convertForSpeech(MetricType type, double value) {
        if (type == MetricType.ENDURANCE_TICKS || type == MetricType.BURN_TIME) {
            return value / 20.0;
        }
        return value;
    }

    public static String subject(SourceKind kind) {
        if (kind == null) {
            return Lexicon.MACHINE;
        }
        return switch (kind) {
            case KINETIC_STRESS, KINETIC_SPEED -> Lexicon.STRESS_NETWORK;
            case ITEM_STORAGE -> Lexicon.ITEM_STORE;
            case FLUID_STORAGE -> Lexicon.FLUID_TANK;
            case ENERGY_STORAGE -> Lexicon.BATTERY_BANK;
            case POWER_GRID -> Lexicon.POWER_GRID;
            case BOILER -> Lexicon.BOILER;
            default -> Lexicon.MACHINE;
        };
    }

    public static String subjectForBlock(String registryPath, SourceKind fallback) {
        if (registryPath == null) {
            return subject(fallback);
        }
        if (registryPath.contains("annunciator") || registryPath.contains("alarm")
                || registryPath.contains("klaxon") || registryPath.contains("sentry")) {
            return Lexicon.ALARM;
        }
        if (registryPath.contains("stats_display") || registryPath.contains("panel")
                || registryPath.contains("eink")) {
            return Lexicon.PANEL;
        }
        if (registryPath.contains("battery") || registryPath.contains("capacitor")) {
            return Lexicon.BATTERY_BANK;
        }
        if (registryPath.contains("stressometer") || registryPath.contains("speedometer")) {
            return Lexicon.STRESS_NETWORK;
        }
        if (registryPath.contains("boiler")) {
            return Lexicon.BOILER;
        }
        if (registryPath.contains("tank") || registryPath.contains("pump")) {
            return Lexicon.FLUID_TANK;
        }
        if (registryPath.contains("connector") || registryPath.contains("pdu")
                || registryPath.contains("dynamo")) {
            return Lexicon.POWER_GRID;
        }
        return subject(fallback);
    }
}
