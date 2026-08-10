package com.createstats.display;

import com.createstats.blockentity.SourceConfig;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomScreenPresetCatalog {
    public static final String ANY_SOURCE_FAMILY = "any";
    private static final int BASE_COLUMNS = 18;
    private static final int BASE_ROWS = 12;
    private static final List<PresetDefinition> PRESETS = List.of(
            new PresetDefinition(
                    "starter_dashboard",
                    "Starter",
                    "Balanced starter layout with cards and list widgets.",
                    List.of(ANY_SOURCE_FAMILY),
                    List.of(
                            module(CustomScreenModuleType.TITLE, 0, 0, 10, 2, Map.of("text", "Stats Display")),
                            module(CustomScreenModuleType.SUBTITLE, 10, 0, 8, 2, Map.of("text", "Preset starter")),
                            module(CustomScreenModuleType.BIG_VALUE, 0, 2, 6, 4, Map.of("label", "Throughput")),
                            module(CustomScreenModuleType.ANALOG_GAUGE, 6, 2, 6, 4, Map.of("label", "RPM")),
                            module(CustomScreenModuleType.DUAL_CARD, 12, 2, 6, 4, Map.of()),
                            module(CustomScreenModuleType.STATUS_STRIP, 0, 6, 18, 2, Map.of()),
                            module(CustomScreenModuleType.CHANNEL_LIST, 0, 8, 9, 4, Map.of("title", "Focused Source")),
                            module(CustomScreenModuleType.TRIPLE_CARD, 9, 8, 9, 4, Map.of()))),
            new PresetDefinition(
                    "aero_bridge",
                    "Aero Bridge",
                    "Flight Deck hero scene plus support instruments.",
                    List.of("flight.aero_deck"),
                    List.of(
                            module(CustomScreenModuleType.TITLE, 0, 0, 12, 2, Map.of("text", "Aero Bridge")),
                            module(CustomScreenModuleType.SUBTITLE, 12, 0, 6, 2, Map.of("text", "Deck + support")),
                            module(CustomScreenModuleType.AERO_FLIGHT_DECK, 0, 2, 12, 8, Map.of()),
                            module(CustomScreenModuleType.STATUS_STRIP, 12, 2, 6, 2, Map.of("left", "Live", "center", "Flight", "right", "Ready")),
                            module(CustomScreenModuleType.BIG_VALUE, 12, 4, 6, 3, Map.of("label", "Altitude")),
                            module(CustomScreenModuleType.CHANNEL_LIST, 12, 7, 6, 3, Map.of("title", "Deck feeds")))),
            new PresetDefinition(
                    "director_console",
                    "Director",
                    "Aeroline Director hero scene plus route summary cards.",
                    List.of("flight.aeroline_director"),
                    List.of(
                            module(CustomScreenModuleType.TITLE, 0, 0, 12, 2, Map.of("text", "Director Console")),
                            module(CustomScreenModuleType.SUBTITLE, 12, 0, 6, 2, Map.of("text", "Mode + route")),
                            module(CustomScreenModuleType.AEROLINE_DIRECTOR, 0, 2, 12, 8, Map.of()),
                            module(CustomScreenModuleType.STATUS_STRIP, 12, 2, 6, 2, Map.of("left", "Mode", "center", "Track", "right", "Climb")),
                            module(CustomScreenModuleType.DUAL_CARD, 12, 4, 6, 3, Map.of("leftLabel", "Turn", "rightLabel", "Lane")),
                            module(CustomScreenModuleType.CHANNEL_LIST, 12, 7, 6, 3, Map.of("title", "Director feeds")))),
            new PresetDefinition(
                    "diesel_operations",
                    "Diesel Ops",
                    "Diesel engine macro page with supporting metrics.",
                    List.of("diesel.engine"),
                    List.of(
                            module(CustomScreenModuleType.TITLE, 0, 0, 12, 2, Map.of("text", "Diesel Operations")),
                            module(CustomScreenModuleType.SUBTITLE, 12, 0, 6, 2, Map.of("text", "Engine overview")),
                            module(CustomScreenModuleType.DIESEL_ENGINE_PAGE, 0, 2, 12, 8, Map.of()),
                            module(CustomScreenModuleType.BIG_VALUE, 12, 2, 6, 3, Map.of("label", "Reserve")),
                            module(CustomScreenModuleType.DUAL_CARD, 12, 5, 6, 3, Map.of("leftLabel", "Shaft", "rightLabel", "Burn")),
                            module(CustomScreenModuleType.CHANNEL_LIST, 12, 8, 6, 4, Map.of("title", "Engine feeds")))),
                        new PresetDefinition(
                            "stressometer_starter",
                            "Stressometer",
                            "Starter board for stressometer and stress network readouts.",
                            List.of("kinetics.stressometer"),
                            List.of(
                                module(CustomScreenModuleType.TITLE, 0, 0, 11, 2, Map.of("text", "Stressometer")),
                                module(CustomScreenModuleType.SUBTITLE, 11, 0, 7, 2, Map.of("text", "Network starter")),
                                module(CustomScreenModuleType.BIG_VALUE, 0, 2, 6, 4, Map.of("label", "Stress")),
                                module(CustomScreenModuleType.ANALOG_GAUGE, 6, 2, 6, 4, Map.of("label", "RPM")),
                                module(CustomScreenModuleType.DUAL_CARD, 12, 2, 6, 4, Map.of("leftLabel", "Load", "rightLabel", "Cap")),
                                module(CustomScreenModuleType.STATUS_STRIP, 0, 6, 18, 2, Map.of("left", "Live", "center", "Stress", "right", "Stable")),
                                module(CustomScreenModuleType.CHANNEL_LIST, 0, 8, 18, 4, Map.of("title", "Stress feeds")))),
                        new PresetDefinition(
                            "portable_engine_starter",
                            "Portable Engine",
                            "Starter board for portable engine monitoring.",
                            List.of("diesel.portable_engine"),
                            List.of(
                                module(CustomScreenModuleType.TITLE, 0, 0, 12, 2, Map.of("text", "Portable Engine")),
                                module(CustomScreenModuleType.SUBTITLE, 12, 0, 6, 2, Map.of("text", "Starter board")),
                                module(CustomScreenModuleType.BIG_VALUE, 0, 2, 6, 4, Map.of("label", "Burn")),
                                module(CustomScreenModuleType.STATUS_STRIP, 6, 2, 12, 2, Map.of("left", "Fuel", "center", "Heat", "right", "Drive")),
                                module(CustomScreenModuleType.DUAL_CARD, 6, 4, 12, 3, Map.of("leftLabel", "Speed", "rightLabel", "Heat")),
                                module(CustomScreenModuleType.CHANNEL_LIST, 0, 8, 18, 4, Map.of("title", "Portable feeds")))),
            new PresetDefinition(
                    "battle_board",
                    "Battle Board",
                    "Battle Bridge composition with tactical support widgets.",
                    List.of("battle.bridge"),
                    List.of(
                            module(CustomScreenModuleType.TITLE, 0, 0, 11, 2, Map.of("text", "Battle Board")),
                            module(CustomScreenModuleType.SUBTITLE, 11, 0, 7, 2, Map.of("text", "Strategic overview")),
                            module(CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION, 0, 2, 12, 8, Map.of("boardPreset", "STRATEGIC_OVERVIEW")),
                            module(CustomScreenModuleType.TRIPLE_CARD, 12, 2, 6, 3, Map.of("aLabel", "Hull", "bLabel", "Weapons", "cLabel", "Cargo")),
                            module(CustomScreenModuleType.STATUS_STRIP, 12, 5, 6, 2, Map.of("left", "Fleet", "center", "Trend", "right", "Focus")),
                                module(CustomScreenModuleType.CHANNEL_LIST, 12, 7, 6, 4, Map.of("title", "Battle feeds")))));
                    private static final Map<String, String> PRESET_ALIASES = Map.of(
                        "diesel_starter", "diesel_operations",
                        "aero_flight_deck_starter", "aero_bridge",
                        "battle_starter", "battle_board");

    private CustomScreenPresetCatalog() {
    }

    public static List<PresetDefinition> allPresets() {
        return PRESETS;
    }

    public static PresetDefinition find(String presetId) {
        final String resolvedId = resolvePresetId(presetId);
        if (resolvedId.isEmpty()) {
            return null;
        }
        for (PresetDefinition preset : PRESETS) {
            if (preset.id.equals(resolvedId)) {
                return preset;
            }
        }
        return null;
    }

    public static String resolvePresetId(String presetId) {
        if (presetId == null || presetId.isEmpty()) {
            return "";
        }
        return PRESET_ALIASES.getOrDefault(presetId, presetId);
    }

    public static ApplyResult apply(CustomScreenLayout currentLayout,
                                    PresetDefinition preset,
                                    ApplyMode mode,
                                    String focusedSourceId,
                                    Map<String, SourceConfig> sourceRegistry) {
        final CustomScreenLayout working = mode == ApplyMode.REPLACE || currentLayout == null ? new CustomScreenLayout() : currentLayout.copy();
        final Offset offset = mode == ApplyMode.ADD ? computeAddOffset(working, preset) : new Offset(0, 0);
        int directCount = 0;
        int fallbackCount = 0;
        int unresolvedCount = 0;
        int addedCount = 0;

        if (mode == ApplyMode.REPLACE) {
            working.setLayoutMeta(new CustomScreenLayout.LayoutMeta());
        }
        final CustomScreenLayout.LayoutMeta meta = working.getLayoutMeta() != null ? working.getLayoutMeta().copy() : new CustomScreenLayout.LayoutMeta();
        meta.setName(preset.label);
        meta.setNotes(preset.description);
        working.setLayoutMeta(meta);

        for (ModuleTemplate template : preset.templates) {
            final CustomModuleInstance module = CustomScreenModuleCatalog.createInstance(template.type, template.gridX + offset.x, template.gridY + offset.y);
            if (module == null) {
                continue;
            }
            module.setBounds(new CustomModuleInstance.GridBounds(template.gridX + offset.x, template.gridY + offset.y, template.width, template.height));
            final CompoundTag config = module.getModuleConfig();
            for (Map.Entry<String, String> entry : template.configOverrides.entrySet()) {
                config.putString(entry.getKey(), entry.getValue());
            }
            module.setModuleConfig(config);
            for (CustomModuleBinding binding : module.getBindings()) {
                if (binding == null) {
                    continue;
                }
                if (binding.getMode() == CustomModuleBinding.Mode.EXPLICIT_SOURCE) {
                    if (canPrefill(preset, binding, focusedSourceId, sourceRegistry)) {
                        binding.setSourceId(focusedSourceId);
                        directCount++;
                    }
                    else {
                        binding.setSourceId("");
                        unresolvedCount++;
                    }
                    binding.normalize();
                    continue;
                }
                if (binding.getMode() == CustomModuleBinding.Mode.SEMANTIC_SLOT && !binding.getSlot().isEmpty()) {
                    fallbackCount++;
                }
            }
            module.normalize();
            working.addModule(module);
            addedCount++;
        }

        final CustomScreenLayout normalized = CustomScreenMigrationCoordinator.normalizeLayout(working, sourceRegistry);
        final String summary = preset.label + (mode == ApplyMode.ADD ? " added" : " applied")
                + "  |  direct " + directCount
                + "  fallback " + fallbackCount
                + "  unresolved " + unresolvedCount;
        return new ApplyResult(normalized != null ? normalized : working, addedCount, directCount, fallbackCount, unresolvedCount, summary);
    }

    private static boolean canPrefill(PresetDefinition preset,
                                      CustomModuleBinding binding,
                                      String focusedSourceId,
                                      Map<String, SourceConfig> sourceRegistry) {
        if (focusedSourceId == null || focusedSourceId.isEmpty() || sourceRegistry == null) {
            return false;
        }
        final SourceConfig config = sourceRegistry.get(focusedSourceId);
        if (config == null) {
            return false;
        }
        final SourceFamilyDefinition focusedFamily = CustomScreenSourceFamilyCatalog.familyForSource(config);
        if (preset == null || !preset.supportsFamily(focusedFamily.getFamilyId())) {
            return false;
        }
        final String filter = binding.getSourceTypeFilter();
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        return CustomScreenSourceFamilyCatalog.sourceTypeMatchesFamilyFilter(config.getSourceType(), filter);
    }

    private static Offset computeAddOffset(CustomScreenLayout layout, PresetDefinition preset) {
        int maxRight = 0;
        int maxBottom = 0;
        for (CustomModuleInstance module : layout.getModules()) {
            if (module == null || module.getBounds() == null) {
                continue;
            }
            maxRight = Math.max(maxRight, module.getBounds().getX() + module.getBounds().getWidth());
            maxBottom = Math.max(maxBottom, module.getBounds().getY() + module.getBounds().getHeight());
        }
        if (maxRight + preset.width <= BASE_COLUMNS) {
            return new Offset(maxRight, 0);
        }
        if (maxBottom + preset.height <= BASE_ROWS) {
            return new Offset(0, maxBottom);
        }
        return new Offset(1, 1);
    }

    private static ModuleTemplate module(CustomScreenModuleType type,
                                         int gridX,
                                         int gridY,
                                         int width,
                                         int height,
                                         Map<String, String> configOverrides) {
        return new ModuleTemplate(type, gridX, gridY, width, height, configOverrides != null ? Map.copyOf(configOverrides) : Map.of());
    }

    public enum ApplyMode {
        REPLACE,
        ADD
    }

    public static final class PresetDefinition {
        private final String id;
        private final String label;
        private final String description;
        private final List<String> supportedSourceFamilies;
        private final List<ModuleTemplate> templates;
        private final int width;
        private final int height;

        private PresetDefinition(String id,
                                 String label,
                                 String description,
                                 List<String> supportedSourceFamilies,
                                 List<ModuleTemplate> templates) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.supportedSourceFamilies = supportedSourceFamilies != null ? List.copyOf(supportedSourceFamilies) : List.of();
            this.templates = templates != null ? List.copyOf(templates) : List.of();
            int maxRight = 0;
            int maxBottom = 0;
            for (ModuleTemplate template : this.templates) {
                maxRight = Math.max(maxRight, template.gridX + template.width);
                maxBottom = Math.max(maxBottom, template.gridY + template.height);
            }
            this.width = Math.max(1, maxRight);
            this.height = Math.max(1, maxBottom);
        }

        public String id() {
            return this.id;
        }

        public String label() {
            return this.label;
        }

        public String description() {
            return this.description;
        }

        public List<String> supportedSourceFamilies() {
            return this.supportedSourceFamilies;
        }

        public boolean supportsAnyFamily() {
            return this.supportedSourceFamilies.contains(ANY_SOURCE_FAMILY);
        }

        public boolean supportsFamily(String familyId) {
            return this.supportsAnyFamily() || this.supportedSourceFamilies.contains(familyId);
        }
    }

    public record ApplyResult(CustomScreenLayout layout,
                              int addedModules,
                              int directBindings,
                              int fallbackBindings,
                              int unresolvedBindings,
                              String summary) {
    }

    private record ModuleTemplate(CustomScreenModuleType type,
                                  int gridX,
                                  int gridY,
                                  int width,
                                  int height,
                                  Map<String, String> configOverrides) {
    }

    private record Offset(int x, int y) {
    }
}