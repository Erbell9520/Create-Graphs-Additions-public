package com.createstats.display;

import com.createstats.blockentity.SourceConfig;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Map;

public final class CustomScreenMigrationCoordinator {
    private CustomScreenMigrationCoordinator() {
    }

    public static CustomScreenLayout loadLayout(CompoundTag tag, Map<String, SourceConfig> sourceRegistry) {
        return normalizeLayout(tag != null ? CustomScreenLayout.load(tag) : null, sourceRegistry);
    }

    public static CustomScreenLayout normalizeLayout(CustomScreenLayout layout, Map<String, SourceConfig> sourceRegistry) {
        if (layout == null) {
            return null;
        }
        final CustomScreenLayout working = layout.copy();
        applyAliasesAndMigrations(working);
        validateVariantOverrides(working);
        validateBindings(working, sourceRegistry);
        working.normalize();
        return working;
    }

    private static void applyAliasesAndMigrations(CustomScreenLayout layout) {
        for (CustomModuleInstance module : layout.getModules()) {
            if (module == null) {
                continue;
            }
            module.setModuleType(CustomScreenModuleCatalog.resolveModuleTypeId(module.getModuleType()));
            for (CustomModuleBinding binding : module.getBindings()) {
                if (binding == null) {
                    continue;
                }
                binding.setKey(CustomModuleBinding.resolveBindingKeyAlias(binding.getKey()));
                binding.normalize();
            }
            migrateModuleConfig(module);
            module.normalize();
        }
    }

    private static void migrateModuleConfig(CustomModuleInstance module) {
        final CompoundTag config = module.getModuleConfig();
        if (!config.contains("configVersion")) {
            config.putInt("configVersion", 1);
        }
        switch (module.getModuleType()) {
            case "scene_battle_screen_composition" -> {
                if (!config.contains("boardPreset")) {
                    config.putString("boardPreset", "STRATEGIC_OVERVIEW");
                }
            }
            case "scene_aero_flight_deck", "scene_aeroline_director", "scene_diesel_engine_page" -> {
                if (!config.contains("title")) {
                    final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(module.getModuleType());
                    config.putString("title", contract != null ? contract.getDisplayName() : "Macro Scene");
                }
                if (!config.contains("subtitle")) {
                    config.putString("subtitle", "Recovered scene");
                }
            }
            default -> {
            }
        }
        config.putInt("configVersion", Math.max(1, config.getInt("configVersion")));
        module.setModuleConfig(config);
    }

    private static void validateVariantOverrides(CustomScreenLayout layout) {
        for (CustomScreenLayout.VariantFamily family : CustomScreenLayout.VariantFamily.values()) {
            if (family == CustomScreenLayout.VariantFamily.BASE) {
                continue;
            }
            final CustomScreenLayout.VariantLayout variantLayout = layout.getVariant(family);
            if (variantLayout == null) {
                continue;
            }
            for (String omittedId : new ArrayList<String>(variantLayout.getOmittedModules())) {
                if (layout.findModule(omittedId) == null) {
                    variantLayout.removeOmittedModule(omittedId);
                }
            }
            for (Map.Entry<String, CustomModuleInstance.VariantOverride> entry : new ArrayList<Map.Entry<String, CustomModuleInstance.VariantOverride>>(variantLayout.getModuleOverrides().entrySet())) {
                final String moduleId = entry.getKey();
                final CustomModuleInstance module = layout.findModule(moduleId);
                if (module == null) {
                    variantLayout.removeModuleOverride(moduleId);
                    continue;
                }
                final CustomModuleInstance.VariantOverride override = entry.getValue() != null ? entry.getValue().copy() : null;
                if (override == null) {
                    variantLayout.removeModuleOverride(moduleId);
                    continue;
                }
                final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(module.getModuleType());
                if (override.getBounds() != null) {
                    clampBounds(override.getBounds(), contract, family);
                }
                if (contract != null && override.getVariant() != null && !override.getVariant().isEmpty()
                        && !contract.getVariantKeys().contains(override.getVariant())) {
                    override.setVariant("");
                }
                if (override.isEmpty()) {
                    variantLayout.removeModuleOverride(moduleId);
                }
                else {
                    variantLayout.putModuleOverride(moduleId, override);
                }
            }
            if (variantLayout.isEmpty()) {
                layout.setVariant(family, null);
            }
        }
    }

    private static void validateBindings(CustomScreenLayout layout, Map<String, SourceConfig> sourceRegistry) {
        if (sourceRegistry == null) {
            return;
        }
        for (CustomModuleInstance module : layout.getModules()) {
            if (module == null) {
                continue;
            }
            final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(module.getModuleType());
            if (contract == null) {
                continue;
            }
            final ArrayList<CustomModuleBinding> validBindings = new ArrayList<CustomModuleBinding>();
            for (CustomModuleBinding binding : module.getBindings()) {
                if (binding == null) {
                    continue;
                }
                binding.setKey(CustomModuleBinding.resolveBindingKeyAlias(binding.getKey()));
                if (!contract.getBindingFields().contains(binding.getKey())) {
                    continue;
                }
                if (binding.getMode() == CustomModuleBinding.Mode.EXPLICIT_SOURCE
                        && !binding.getSourceId().isEmpty()
                        && !sourceRegistry.containsKey(binding.getSourceId())
                        && !binding.getSourceTypeFilter().isEmpty()) {
                    binding.setSourceTypeHint(binding.getSourceTypeFilter());
                }
                binding.normalize();
                validBindings.add(binding.copy());
            }
            module.setBindings(validBindings);
            module.normalize();
        }
    }

    private static void clampBounds(CustomModuleInstance.GridBounds bounds,
                                    CustomScreenModuleContract contract,
                                    CustomScreenLayout.VariantFamily family) {
        final GridShape shape = GridShape.forVariant(family);
        final int minWidth = contract != null ? contract.getMinAuthoringWidth() : 1;
        final int minHeight = contract != null ? contract.getMinAuthoringHeight() : 1;
        bounds.setX(Math.max(0, Math.min(shape.columns - 1, bounds.getX())));
        bounds.setY(Math.max(0, Math.min(shape.rows - 1, bounds.getY())));
        bounds.setWidth(Math.max(Math.min(shape.columns - bounds.getX(), minWidth), Math.min(shape.columns - bounds.getX(), bounds.getWidth())));
        bounds.setHeight(Math.max(Math.min(shape.rows - bounds.getY(), minHeight), Math.min(shape.rows - bounds.getY(), bounds.getHeight())));
    }

    private record GridShape(int columns, int rows) {
        private static GridShape forVariant(CustomScreenLayout.VariantFamily family) {
            return switch (family) {
                case SQUARE -> new GridShape(14, 14);
                case TALL -> new GridShape(10, 16);
                case COMPACT -> new GridShape(10, 8);
                case BASE, WIDE -> new GridShape(18, 12);
            };
        }
    }
}
