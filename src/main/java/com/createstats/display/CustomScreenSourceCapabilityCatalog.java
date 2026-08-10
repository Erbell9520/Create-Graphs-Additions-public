package com.createstats.display;

import com.createstats.blockentity.SourceConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CustomScreenSourceCapabilityCatalog {
    private CustomScreenSourceCapabilityCatalog() {
    }

    public static List<CustomScreenSourceCapability> collect(Map<String, SourceConfig> sourceRegistry) {
        final ArrayList<CustomScreenSourceCapability> capabilities = new ArrayList<CustomScreenSourceCapability>();
        if (sourceRegistry == null || sourceRegistry.isEmpty()) {
            return capabilities;
        }
        for (SourceConfig source : sourceRegistry.values()) {
            if (source != null) {
                addSourceCapabilities(capabilities, source);
            }
        }
        return capabilities;
    }

    private static void addSourceCapabilities(List<CustomScreenSourceCapability> capabilities, SourceConfig source) {
        final String sourceType = safe(source.getSourceType());
        final String sourceId = safe(source.getSourceId());
        final SourceFamilyDefinition family = CustomScreenSourceFamilyCatalog.familyForSource(source);
        final CustomScreenSourceCapability.PaletteDomain domain = classifyDomain(family);
        final String owner = ownerScreen(family);
        final boolean online = source.isActive();

        capabilities.add(capability(source,
                domain,
                owner,
                "source_summary",
                "Source Summary",
                CustomScreenSourceCapability.SectionRole.STATUS,
                CustomScreenModuleType.STATUS_STRIP,
                "state",
                "Live identity and health for " + sourceId,
                false,
                online));

        capabilities.add(capability(source,
                domain,
                owner,
                "channel_overview",
                "Channel Overview",
                CustomScreenSourceCapability.SectionRole.LIST,
                CustomScreenModuleType.CHANNEL_LIST,
                "",
                "Source-owned channel and signal overview",
                false,
                online));

        addHeroScene(capabilities, source, family, domain, owner, online);
        for (SpecializedHudWidgetCatalog.Definition definition : SpecializedHudWidgetCatalog.definitionsFor(sourceType)) {
            capabilities.add(capability(source,
                    domain,
                    owner,
                    sectionId(definition.label(), definition.primaryKey()),
                    definition.label(),
                    roleFor(definition),
                    moduleTypeFor(definition),
                    definition.primaryKey(),
                    "Specialized HUD section from " + owner,
                    false,
                    online));
        }
    }

    private static void addHeroScene(List<CustomScreenSourceCapability> capabilities,
                                     SourceConfig source,
                     SourceFamilyDefinition family,
                                     CustomScreenSourceCapability.PaletteDomain domain,
                                     String owner,
                                     boolean online) {
    for (CustomScreenModuleType macroScene : family.getAllowedMacroScenes()) {
        switch (macroScene) {
        case AERO_FLIGHT_DECK -> capabilities.add(capability(source, domain, owner, "aero_flight_deck_scene", "Aero Flight Deck Scene",
            CustomScreenSourceCapability.SectionRole.HERO, CustomScreenModuleType.AERO_FLIGHT_DECK, "state",
            "Full flight deck specialized panel", true, online));
        case AEROLINE_DIRECTOR -> capabilities.add(capability(source, domain, owner, "aeroline_director_scene", "Aeroline Director Scene",
            CustomScreenSourceCapability.SectionRole.HERO, CustomScreenModuleType.AEROLINE_DIRECTOR, "state",
            "Target director and aircraft guidance panel", true, online));
        case DIESEL_ENGINE_PAGE -> capabilities.add(capability(source, domain, owner, "diesel_engine_scene", "Diesel Engine Scene",
            CustomScreenSourceCapability.SectionRole.HERO, CustomScreenModuleType.DIESEL_ENGINE_PAGE, "running",
            "Diesel engine specialized page", true, online));
        case BATTLE_SCREEN_COMPOSITION -> capabilities.add(capability(source, domain, owner, "battle_screen_scene", "Battle Bridge Scene",
            CustomScreenSourceCapability.SectionRole.HERO, CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION, "state",
            "Battle tactical board and weapon status", true, online));
        case BATTLE_TACTICAL_V2 -> capabilities.add(capability(source, domain, owner, "battle_tactical_v2_scene", "Battle Tactical V2",
            CustomScreenSourceCapability.SectionRole.HERO, CustomScreenModuleType.BATTLE_TACTICAL_V2, "state",
            "Responsive tactical console panel", true, online));
        default -> {
        }
        }
        }
    }

    private static CustomScreenSourceCapability capability(SourceConfig source,
                                                          CustomScreenSourceCapability.PaletteDomain domain,
                                                          String ownerScreen,
                                                          String sectionId,
                                                          String sectionLabel,
                                                          CustomScreenSourceCapability.SectionRole role,
                                                          CustomScreenModuleType moduleType,
                                                          String primaryMetric,
                                                          String description,
                                                          boolean hero,
                                                          boolean online) {
        return new CustomScreenSourceCapability(source.getSourceId(),
                source.getSourceType(),
                source.getDisplayLabel(),
                domain,
                ownerScreen,
                sectionId,
                sectionLabel,
                role,
                moduleType,
                primaryMetric,
                description,
                hero,
                online,
                source.getSourcePos());
    }

    private static CustomScreenSourceCapability.PaletteDomain classifyDomain(SourceFamilyDefinition family) {
        return switch (family.getGroup()) {
            case FLIGHT_NAVIGATION -> CustomScreenSourceCapability.PaletteDomain.FLIGHT;
            case KINETICS, ENERGY -> CustomScreenSourceCapability.PaletteDomain.ENGINES;
            case BATTLE -> CustomScreenSourceCapability.PaletteDomain.BATTLE;
            case FACTORY_INVENTORY -> CustomScreenSourceCapability.PaletteDomain.FACTORY;
            case DIESEL_FLUIDS -> CustomScreenSourceCapability.PaletteDomain.FLUIDS;
            case SENSORS_TEXT, LOGISTICS -> CustomScreenSourceCapability.PaletteDomain.SENSORS;
            case GENERIC -> CustomScreenSourceCapability.PaletteDomain.GENERIC;
        };
    }

    private static String ownerScreen(SourceFamilyDefinition family) {
        return CustomScreenSourceFamilyCatalog.GENERIC_UNKNOWN.equals(family.getFamilyId()) ? "Generic Source" : family.getDisplayName();
    }

    private static CustomScreenSourceCapability.SectionRole roleFor(SpecializedHudWidgetCatalog.Definition definition) {
        final String label = definition.label().toLowerCase(Locale.ROOT);
        if (label.contains("indicator") || label.contains("gauge") || label.contains("scope") || label.contains("horizon") || label.contains("tape")) {
            return CustomScreenSourceCapability.SectionRole.INSTRUMENT;
        }
        if (label.contains("list") || label.contains("row") || label.contains("content") || label.contains("trains")) {
            return CustomScreenSourceCapability.SectionRole.LIST;
        }
        if (label.contains("status") || label.contains("state") || label.contains("mode") || label.contains("strip")) {
            return CustomScreenSourceCapability.SectionRole.STATUS;
        }
        return CustomScreenSourceCapability.SectionRole.METRIC;
    }

    private static CustomScreenModuleType moduleTypeFor(SpecializedHudWidgetCatalog.Definition definition) {
        final String label = definition.label().toLowerCase(Locale.ROOT);
        final String primaryKey = definition.primaryKey().toLowerCase(Locale.ROOT);
        final String searchable = label + " " + primaryKey;
        if (searchable.contains("running") || searchable.contains("state") || searchable.contains("status") || searchable.contains("valid_fuel") || searchable.contains("connected")) {
            return CustomScreenModuleType.STATUS_STRIP;
        }
        if (searchable.contains("gauge") || searchable.contains("scope") || searchable.contains("horizon") || searchable.contains("altitude")
                || searchable.contains("velocity") || searchable.contains("stress") || searchable.contains("rpm") || searchable.contains("speed")
                || searchable.contains("fuel") || searchable.contains("fluid") || searchable.contains("tank") || searchable.contains("reserve") || searchable.contains("heat")
                || searchable.contains("charge") || searchable.contains("energy") || searchable.contains("fe/t") || searchable.contains("flow")
                || searchable.contains("pressure") || searchable.contains("signal") || searchable.contains("angle")) {
            return CustomScreenModuleType.ANALOG_GAUGE;
        }
        if (label.contains("gauge") || label.contains("scope") || label.contains("horizon") || label.contains("altitude") || label.contains("velocity")) {
            return CustomScreenModuleType.ANALOG_GAUGE;
        }
        if (label.contains("list") || label.contains("content") || label.contains("trains")) {
            return CustomScreenModuleType.CHANNEL_LIST;
        }
        if (label.contains("status") || label.contains("state") || label.contains("mode") || label.contains("strip")) {
            return CustomScreenModuleType.STATUS_STRIP;
        }
        if (definition.keys().length >= 3) {
            return CustomScreenModuleType.TRIPLE_CARD;
        }
        if (definition.keys().length == 2) {
            return CustomScreenModuleType.DUAL_CARD;
        }
        return CustomScreenModuleType.BIG_VALUE;
    }

    private static String sectionId(String label, String primaryKey) {
        final String base = (safe(label) + " " + safe(primaryKey)).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return base.isEmpty() ? "section" : base;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}