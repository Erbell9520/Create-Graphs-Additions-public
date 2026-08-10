package com.createstats.display;

import com.createstats.blockentity.SourceConfig;
import com.createstats.renderer.CustomScreenModuleRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomScreenPhase7HardeningTest {
    @Test
    void migrationNormalizesLegacyAliasesAndVariantOverrides() {
        final CustomScreenLayout layout = new CustomScreenLayout();
        final CustomModuleInstance module = new CustomModuleInstance();
        module.setId("legacy-scene");
        module.setModuleType("macro_battle_screen");
        module.setBounds(new CustomModuleInstance.GridBounds(0, 0, 12, 8));

        final CustomModuleBinding binding = new CustomModuleBinding();
        binding.setKey("primary_source");
        binding.setMode(CustomModuleBinding.Mode.EXPLICIT_SOURCE);
        binding.setSourceId("missing-engine");
        module.addBinding(binding);
        layout.addModule(module);

        final CustomScreenLayout.VariantLayout variant = new CustomScreenLayout.VariantLayout();
        final CustomModuleInstance.VariantOverride validOverride = new CustomModuleInstance.VariantOverride();
        validOverride.setBounds(new CustomModuleInstance.GridBounds(99, 99, 1, 1));
        validOverride.setVariant("broken-variant");
        variant.putModuleOverride("legacy-scene", validOverride);

        final CustomModuleInstance.VariantOverride orphanOverride = new CustomModuleInstance.VariantOverride();
        orphanOverride.setBounds(new CustomModuleInstance.GridBounds(0, 0, 2, 2));
        variant.putModuleOverride("missing-module", orphanOverride);
        variant.addOmittedModule("missing-module");
        layout.setVariant(CustomScreenLayout.VariantFamily.TALL, variant);

        final CustomScreenLayout normalized = CustomScreenMigrationCoordinator.normalizeLayout(layout, Map.of());
        assertNotNull(normalized);

        final CustomModuleInstance normalizedModule = normalized.findModule("legacy-scene");
        assertNotNull(normalizedModule);
        assertEquals(CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION.getSerializedName(), normalizedModule.getModuleType());
        assertEquals(CustomModuleBinding.PRIMARY_SOURCE, normalizedModule.getBindings().get(0).getKey());

        final CompoundTag config = normalizedModule.getModuleConfig();
        assertEquals(1, config.getInt("configVersion"));
        assertEquals("STRATEGIC_OVERVIEW", config.getString("boardPreset"));

        final CustomScreenLayout.VariantLayout tallVariant = normalized.getVariant(CustomScreenLayout.VariantFamily.TALL);
        assertNotNull(tallVariant);
        assertFalse(tallVariant.getModuleOverrides().containsKey("missing-module"));
        assertFalse(tallVariant.getOmittedModules().contains("missing-module"));

        final CustomModuleInstance.VariantOverride normalizedOverride = tallVariant.getModuleOverrides().get("legacy-scene");
        assertNotNull(normalizedOverride);
        assertEquals("", normalizedOverride.getVariant());
        assertNotNull(normalizedOverride.getBounds());
        assertTrue(normalizedOverride.getBounds().getX() >= 0 && normalizedOverride.getBounds().getX() <= 9);
        assertTrue(normalizedOverride.getBounds().getY() >= 0 && normalizedOverride.getBounds().getY() <= 15);
    }

    @Test
    void settingsSaveLoadKeepsOptInStateWhileMigratingLayout() {
        final StatsDisplaySettings settings = new StatsDisplaySettings();
        final CustomScreenLayout layout = new CustomScreenLayout();
        final CustomModuleInstance module = new CustomModuleInstance();
        module.setId("legacy-diesel");
        module.setModuleType("macro_diesel_engine");
        layout.addModule(module);

        settings.setCustomScreenLayout(layout);
        settings.setCustomScreenLayoutEnabled(false);

        final StatsDisplaySettings loaded = StatsDisplaySettings.load(settings.save());
        assertTrue(loaded.hasCustomScreenLayout());
        assertFalse(loaded.isCustomScreenLayoutEnabled());
        assertEquals(1, loaded.getCustomScreenProfileCount());
        assertEquals("Custom Layout", loaded.activeCustomScreenProfileName());
        assertEquals(CustomScreenModuleType.DIESEL_ENGINE_PAGE.getSerializedName(), loaded.getCustomScreenLayout().findModule("legacy-diesel").getModuleType());
    }

    @Test
    void settingsSaveLoadKeepsNamedCustomProfilesAndActiveProfile() {
        final StatsDisplaySettings settings = new StatsDisplaySettings();
        final CustomScreenLayout alphaLayout = namedLayout("Alpha Deck");
        settings.setCustomScreenLayout(alphaLayout);
        settings.saveActiveCustomScreenProfile("Alpha Deck", alphaLayout);

        final CustomScreenLayout betaLayout = namedLayout("Beta Factory");
        settings.createCustomScreenProfile("Beta Factory", betaLayout);
        settings.setCustomScreenLayoutEnabled(true);

        final StatsDisplaySettings loaded = StatsDisplaySettings.load(settings.save());
        assertTrue(loaded.hasCustomScreenLayout());
        assertTrue(loaded.isCustomScreenLayoutEnabled());
        assertEquals(2, loaded.getCustomScreenProfileCount());
        assertEquals("Beta Factory", loaded.activeCustomScreenProfileName());
        assertEquals("Beta Factory", loaded.getCustomScreenLayout().getLayoutMeta().getName());

        assertTrue(loaded.cycleCustomScreenProfile());
        assertEquals("Alpha Deck", loaded.activeCustomScreenProfileName());
        assertEquals("Alpha Deck", loaded.getCustomScreenLayout().getLayoutMeta().getName());
    }

    @Test
    void customScreenStateRoundTripKeepsNamedProfilesForSync() {
        final StatsDisplaySettings source = new StatsDisplaySettings();
        final CustomScreenLayout alphaLayout = namedLayout("Alpha Deck");
        source.setCustomScreenLayout(alphaLayout);
        source.saveActiveCustomScreenProfile("Alpha Deck", alphaLayout);

        final CustomScreenLayout betaLayout = namedLayout("Beta Factory");
        source.createCustomScreenProfile("Beta Factory", betaLayout);
        source.setCustomScreenLayoutEnabled(true);

        final CompoundTag syncState = source.saveCustomScreenState();
        final StatsDisplaySettings target = new StatsDisplaySettings();
        target.setCustomScreenLayout(namedLayout("Old Layout"));
        target.createCustomScreenProfile("Old Other", namedLayout("Old Other"));

        target.loadCustomScreenState(syncState);
        target.setCustomScreenLayoutEnabled(syncState.getBoolean("CustomScreenLayoutEnabled"));

        assertTrue(target.hasCustomScreenLayout());
        assertTrue(target.isCustomScreenLayoutEnabled());
        assertEquals(2, target.getCustomScreenProfileCount());
        assertEquals(source.getActiveCustomScreenProfileId(), target.getActiveCustomScreenProfileId());
        assertEquals("Beta Factory", target.activeCustomScreenProfileName());
        assertEquals("Beta Factory", target.getCustomScreenLayout().getLayoutMeta().getName());
        assertFalse(target.getCustomScreenProfiles().stream().anyMatch(profile -> "Old Other".equals(profile.getName())));

        assertTrue(target.cycleCustomScreenProfile());
        assertEquals("Alpha Deck", target.activeCustomScreenProfileName());
        assertEquals("Alpha Deck", target.getCustomScreenLayout().getLayoutMeta().getName());
    }

    @Test
    void legacyLayoutSetterUpdatesTheActiveNamedProfile() {
        final StatsDisplaySettings settings = new StatsDisplaySettings();
        settings.setCustomScreenLayout(namedLayout("Alpha Deck"));
        settings.createCustomScreenProfile("Beta Factory", namedLayout("Beta Factory"));

        settings.setCustomScreenLayout(namedLayout("Beta Updated"));

        final StatsDisplaySettings loaded = StatsDisplaySettings.load(settings.save());
        assertEquals("Beta Factory", loaded.activeCustomScreenProfileName());
        assertNotNull(loaded.getCustomScreenLayout().findModule("beta-updated-module"));
        assertTrue(loaded.cycleCustomScreenProfile());
        assertNotNull(loaded.getCustomScreenLayout().findModule("alpha-deck-module"));
    }

    @Test
    void presetAliasesAndOnboardingPresetsResolve() {
        assertEquals("diesel_operations", CustomScreenPresetCatalog.find("diesel_starter").id());
        assertNotNull(CustomScreenPresetCatalog.find("stressometer_starter"));
        assertNotNull(CustomScreenPresetCatalog.find("portable_engine_starter"));
    }

    @Test
    void presetSupportedFamiliesResolveThroughCatalog() {
        for (CustomScreenPresetCatalog.PresetDefinition preset : CustomScreenPresetCatalog.allPresets()) {
            for (String familyId : preset.supportedSourceFamilies()) {
                if (!CustomScreenPresetCatalog.ANY_SOURCE_FAMILY.equals(familyId)) {
                    assertNotNull(CustomScreenSourceFamilyCatalog.definition(familyId), preset.id() + " uses unknown source family " + familyId);
                }
            }
        }
    }

    @Test
    void studioBindingSummaryMarksOfflineExplicitSources() {
        final CustomModuleBinding binding = new CustomModuleBinding();
        binding.setKey(CustomModuleBinding.PRIMARY_SOURCE);
        binding.setMode(CustomModuleBinding.Mode.EXPLICIT_SOURCE);
        binding.setSourceId("engine-01");

        final CustomScreenModuleRenderer.BindingSummary summary = CustomScreenModuleRenderer.summarizeBindingField(binding, Set.of());
        assertEquals(CustomScreenModuleRenderer.BindingStatus.OFFLINE, summary.status());
        assertEquals("Offline", summary.label());
        assertEquals("engine-01", summary.detail());
    }

    @Test
    void studioBindingSummaryMarksWrongFamilyExplicitSources() {
        final CustomModuleBinding binding = new CustomModuleBinding();
        binding.setKey(CustomModuleBinding.PRIMARY_SOURCE);
        binding.setMode(CustomModuleBinding.Mode.EXPLICIT_SOURCE);
        binding.setSourceId("stress-a");
        binding.setSourceTypeFilter("Modular Accumulator");

        final LinkedHashMap<String, SourceConfig> sources = new LinkedHashMap<String, SourceConfig>();
        sources.put("stress-a", new SourceConfig("stress-a", BlockPos.ZERO, "Stressometer"));

        final CustomScreenModuleRenderer.BindingSummary summary = CustomScreenModuleRenderer.summarizeBindingField(binding, Set.of("stress-a"), sources);
        assertEquals(CustomScreenModuleRenderer.BindingStatus.INCOMPATIBLE, summary.status());
        assertEquals("Wrong Family", summary.label());
        assertEquals("Stressometer != Modular Accumulator", summary.detail());
    }

    @Test
    void studioBindingSummaryNormalizesAliasFamiliesInWrongFamilyDetails() {
        final CustomModuleBinding binding = new CustomModuleBinding();
        binding.setKey(CustomModuleBinding.PRIMARY_SOURCE);
        binding.setMode(CustomModuleBinding.Mode.EXPLICIT_SOURCE);
        binding.setSourceId("legacy-battle");
        binding.setSourceTypeFilter("Aero Flight Deck");

        final LinkedHashMap<String, SourceConfig> sources = new LinkedHashMap<String, SourceConfig>();
        sources.put("legacy-battle", new SourceConfig("legacy-battle", BlockPos.ZERO, "Battle Screen"));

        final CustomScreenModuleRenderer.BindingSummary summary = CustomScreenModuleRenderer.summarizeBindingField(binding, Set.of("legacy-battle"), sources);
        assertEquals(CustomScreenModuleRenderer.BindingStatus.INCOMPATIBLE, summary.status());
        assertEquals("Battle Bridge != Aero Flight Deck", summary.detail());
    }

    @Test
    void repairPriorityKeepsWrongFamilyBeforeOfflineBeforeUnresolved() {
        assertTrue(CustomScreenModuleRenderer.repairPriority(CustomScreenModuleRenderer.BindingStatus.INCOMPATIBLE)
                < CustomScreenModuleRenderer.repairPriority(CustomScreenModuleRenderer.BindingStatus.OFFLINE));
        assertTrue(CustomScreenModuleRenderer.repairPriority(CustomScreenModuleRenderer.BindingStatus.OFFLINE)
                < CustomScreenModuleRenderer.repairPriority(CustomScreenModuleRenderer.BindingStatus.UNRESOLVED));
        assertTrue(CustomScreenModuleRenderer.isRepairStatus(CustomScreenModuleRenderer.BindingStatus.INCOMPATIBLE));
        assertFalse(CustomScreenModuleRenderer.isRepairStatus(CustomScreenModuleRenderer.BindingStatus.RESOLVED));
    }

    @Test
    void sourceCapabilityCatalogExposesLiveAerolineSections() {
        final LinkedHashMap<String, SourceConfig> sources = new LinkedHashMap<String, SourceConfig>();
        final SourceConfig aeroline = new SourceConfig("airship-alpha", BlockPos.ZERO, "Aeroline Director");
        sources.put(aeroline.getSourceId(), aeroline);

        final List<CustomScreenSourceCapability> capabilities = CustomScreenSourceCapabilityCatalog.collect(sources);
        assertTrue(capabilities.stream().anyMatch(capability -> capability.isHero()
                && "Aeroline Director Scene".equals(capability.getSectionLabel())));

        CustomScreenSourceCapability directorWindow = null;
        for (CustomScreenSourceCapability capability : capabilities) {
            if ("Director Window".equals(capability.getSectionLabel())) {
                directorWindow = capability;
                break;
            }
        }
        assertNotNull(directorWindow);

        final CustomModuleInstance module = directorWindow.createModule(0, 0);
        assertNotNull(module);
        assertEquals("airship-alpha", module.resolveBinding(CustomModuleBinding.PRIMARY_SOURCE).getSourceId());
        assertEquals("airship-alpha", module.resolveBinding(CustomModuleBinding.PRIMARY_METRIC).getSourceId());
        assertEquals("Aeroline Director", module.getModuleConfig().getString("ownerScreen"));
        assertEquals("Director Window", module.getModuleConfig().getString("sectionLabel"));
        assertEquals("airship-alpha", module.getModuleConfig().getString("sourceId"));
    }

    @Test
    void sourceCapabilityCatalogSeedsAnalogInstrumentModes() {
        final LinkedHashMap<String, SourceConfig> sources = new LinkedHashMap<String, SourceConfig>();
        sources.put("stress-a", new SourceConfig("stress-a", BlockPos.ZERO, "Stressometer"));
        sources.put("diesel-a", new SourceConfig("diesel-a", BlockPos.ZERO, "Diesel Engine"));
        sources.put("accumulator-a", new SourceConfig("accumulator-a", BlockPos.ZERO, "Modular Accumulator"));

        final List<CustomScreenSourceCapability> capabilities = CustomScreenSourceCapabilityCatalog.collect(sources);
        assertAnalogMode(capabilities, "Stress Load Gauge", "stress");
        assertAnalogMode(capabilities, "Fuel Tracker", "tank");
        assertAnalogMode(capabilities, "Accumulator Charge", "accumulator");
    }

    @Test
    void sourceCapabilityCatalogExposesDieselReserveSupportSections() {
        final LinkedHashMap<String, SourceConfig> sources = new LinkedHashMap<String, SourceConfig>();
        sources.put("diesel-group-a", new SourceConfig("diesel-group-a", BlockPos.ZERO, "Diesel Engine Group"));
        sources.put("reserve-a", new SourceConfig("reserve-a", BlockPos.ZERO, "Diesel Fuel Reserve"));

        final List<CustomScreenSourceCapability> capabilities = CustomScreenSourceCapabilityCatalog.collect(sources);
        assertAnalogMode(capabilities, "Linked Fuel Reserve", "tank");
        assertAnalogMode(capabilities, "Reserve Fuel Gauge", "tank");
        assertTrue(capabilities.stream().anyMatch(capability -> "reserve-a".equals(capability.getSourceId())
                && "Reserve Stored Fuel".equals(capability.getSectionLabel())
                && "reserve_fuel_amount".equals(capability.getPrimaryMetric())));
    }

            @Test
            void sourceCapabilityCatalogUsesFamilyContractsForHeroEligibility() {
            final LinkedHashMap<String, SourceConfig> sources = new LinkedHashMap<String, SourceConfig>();
            sources.put("legacy-battle", new SourceConfig("legacy-battle", BlockPos.ZERO, "Battle Screen"));
            sources.put("velocity-a", new SourceConfig("velocity-a", BlockPos.ZERO, "Velocity Sensor"));
            sources.put("reserve-a", new SourceConfig("reserve-a", BlockPos.ZERO, "Diesel Fuel Reserve"));
            sources.put("unknown-a", new SourceConfig("unknown-a", BlockPos.ZERO, "Unknown Machine"));

            final List<CustomScreenSourceCapability> capabilities = CustomScreenSourceCapabilityCatalog.collect(sources);
            assertTrue(capabilities.stream().anyMatch(capability -> "legacy-battle".equals(capability.getSourceId())
                && capability.isHero()
                && capability.getModuleType() == CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION
                && "Battle Bridge".equals(capability.getOwnerScreen())));
            assertFalse(capabilities.stream().anyMatch(capability -> "velocity-a".equals(capability.getSourceId())
                && capability.getModuleType() == CustomScreenModuleType.AERO_FLIGHT_DECK));
            assertFalse(capabilities.stream().anyMatch(capability -> "reserve-a".equals(capability.getSourceId())
                && capability.getModuleType() == CustomScreenModuleType.DIESEL_ENGINE_PAGE));
            assertTrue(capabilities.stream().anyMatch(capability -> "unknown-a".equals(capability.getSourceId())
                && capability.getDomain() == CustomScreenSourceCapability.PaletteDomain.GENERIC
                && "Generic Source".equals(capability.getOwnerScreen())));
            }

    @Test
    void sourceFamilyCatalogResolvesExactTypesAliasesAndUnknowns() {
        assertEquals("kinetics.stressometer", CustomScreenSourceFamilyCatalog.familyForSourceType("Stressometer").getFamilyId());
        assertEquals(SourceFamilyDefinition.SourceFamilyGroup.KINETICS, CustomScreenSourceFamilyCatalog.familyForSourceType("Stressometer").getGroup());
        assertEquals("energy.accumulator", CustomScreenSourceFamilyCatalog.familyForSourceType("Modular Accumulator").getFamilyId());
        assertEquals(SourceFamilyDefinition.SourceFamilyGroup.ENERGY, CustomScreenSourceFamilyCatalog.familyForSourceType("Modular Accumulator").getGroup());
        assertEquals("battle.bridge", CustomScreenSourceFamilyCatalog.familyForSourceType("Battle Screen").getFamilyId());
        assertEquals("battle.bridge", CustomScreenSourceFamilyCatalog.familyForSourceType("Battle Bridge").getFamilyId());
        assertTrue(CustomScreenSourceFamilyCatalog.isLegacyAlias("Battle Screen"));
        assertTrue(CustomScreenSourceFamilyCatalog.isAliasForFamily("Battle Screen", "battle.bridge"));
        assertFalse(CustomScreenSourceFamilyCatalog.isLegacyAlias("Battle Bridge"));
        assertEquals("diesel.engine", CustomScreenSourceFamilyCatalog.familyForSourceType("Diesel Engine Group").getFamilyId());
        assertEquals("diesel.reserve", CustomScreenSourceFamilyCatalog.familyForSourceType("Diesel Fuel Reserve").getFamilyId());
        assertEquals("diesel.pumpjack", CustomScreenSourceFamilyCatalog.familyForSourceType("Oil Pumpjack").getFamilyId());
        assertEquals(CustomScreenSourceFamilyCatalog.GENERIC_UNKNOWN, CustomScreenSourceFamilyCatalog.familyForSourceType("Unknown Machine").getFamilyId());
    }

    @Test
    void sourceFamilyCatalogKeepsSensitiveFamiliesIsolated() {
        final SourceFamilyDefinition stressometer = CustomScreenSourceFamilyCatalog.definition("kinetics.stressometer");
        final SourceFamilyDefinition accumulator = CustomScreenSourceFamilyCatalog.definition("energy.accumulator");
        final SourceFamilyDefinition velocity = CustomScreenSourceFamilyCatalog.definition("flight.velocity_sensor");
        final SourceFamilyDefinition aeroDeck = CustomScreenSourceFamilyCatalog.definition("flight.aero_deck");
        final SourceFamilyDefinition battle = CustomScreenSourceFamilyCatalog.definition("battle.bridge");
        final SourceFamilyDefinition dieselEngine = CustomScreenSourceFamilyCatalog.definition("diesel.engine");
        final SourceFamilyDefinition dieselReserve = CustomScreenSourceFamilyCatalog.definition("diesel.reserve");

        assertTrue(stressometer.matchesExactSourceType("Stressometer"));
        assertFalse(stressometer.matchesExactSourceType("Modular Accumulator"));
        assertEquals(SourceFamilyDefinition.RendererFamily.STRESSOMETER, stressometer.getRendererFamily());
        assertTrue(stressometer.getAllowedMacroScenes().isEmpty());

        assertTrue(accumulator.matchesExactSourceType("Modular Accumulator"));
        assertFalse(accumulator.matchesExactSourceType("Stressometer"));
        assertEquals(SourceFamilyDefinition.RendererFamily.ACCUMULATOR, accumulator.getRendererFamily());
        assertTrue(accumulator.getAllowedMacroScenes().isEmpty());

        assertEquals(SourceFamilyDefinition.RendererFamily.SENSOR, velocity.getRendererFamily());
        assertFalse(velocity.getAllowedMacroScenes().contains(CustomScreenModuleType.AERO_FLIGHT_DECK));
        assertTrue(aeroDeck.getAllowedMacroScenes().contains(CustomScreenModuleType.AERO_FLIGHT_DECK));

        assertTrue(battle.matchesLegacyAlias("Battle Screen"));
        assertTrue(battle.getAllowedMacroScenes().contains(CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION));
        assertFalse(battle.getAllowedMacroScenes().contains(CustomScreenModuleType.AERO_FLIGHT_DECK));

        assertTrue(dieselEngine.getAllowedMacroScenes().contains(CustomScreenModuleType.DIESEL_ENGINE_PAGE));
        assertFalse(dieselReserve.getAllowedMacroScenes().contains(CustomScreenModuleType.DIESEL_ENGINE_PAGE));

        assertEquals(SourceFamilyDefinition.RendererFamily.BATTLE_BRIDGE, battle.getRendererFamily());
        assertEquals(SourceFamilyDefinition.RendererFamily.BATTLE_BRIDGE, CustomScreenSourceFamilyCatalog.familyForSourceType("Battle Screen").getRendererFamily());
        assertTrue(BattleCompactTargetHandler.isBattleSource("battle screen"));
    }

    @Test
    void sourceFamilyFiltersMatchOnlyExactFamiliesAndAliases() {
        assertTrue(CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(new SourceConfig("diesel-a", BlockPos.ZERO, "Diesel Engine Group"), "Diesel Engine"));
        assertTrue(CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(new SourceConfig("battle-a", BlockPos.ZERO, "Battle Screen"), "Battle Bridge"));
        assertTrue(CustomScreenSourceFamilyCatalog.sourceTypeMatchesFamilyFilter("battle screen", "battle bridge"));

        assertFalse(CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(new SourceConfig("stress-a", BlockPos.ZERO, "Stressometer"), "Modular Accumulator"));
        assertFalse(CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(new SourceConfig("unknown-a", BlockPos.ZERO, "Unknown Machine"), "Other Unknown Machine"));
        assertTrue(CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(new SourceConfig("unknown-a", BlockPos.ZERO, "Unknown Machine"), "Unknown Machine"));
    }

        @Test
        void specializedHudWidgetCatalogUsesFamilyRegistry() {
        assertTrue(SpecializedHudWidgetCatalog.definitionsFor("diesel engine group").stream()
            .anyMatch(definition -> "Linked Fuel Reserve".equals(definition.label())));
        assertTrue(SpecializedHudWidgetCatalog.definitionsFor("modular accumulator").stream()
            .anyMatch(definition -> "Accumulator Charge".equals(definition.label())));
        assertTrue(SpecializedHudWidgetCatalog.definitionsFor("Battle Screen").isEmpty());
        assertFalse(SpecializedHudWidgetCatalog.definitionsFor("Stressometer").stream()
            .anyMatch(definition -> "Accumulator Charge".equals(definition.label())));
        }

    @Test
    void visualModeRoutingKeepsStressometerAndAccumulatorSeparate() {
        assertEquals("accumulator", CustomScreenSourceCapability.visualModeFor("modular accumulator", "Stored Capacity", "energy_capacity", "metric"));
        assertEquals("accumulator", CustomScreenSourceCapability.visualModeFor("Modular Accumulator", "Network RPM", "rpm", "metric"));
        assertEquals("stress", CustomScreenSourceCapability.visualModeFor("Stressometer", "Stress Used / Capacity", "capacity_su", "metric"));
        assertEquals("rpm", CustomScreenSourceCapability.visualModeFor("Stressometer", "Network RPM", "rpm", "metric"));
        assertEquals("accumulator", CustomScreenSourceCapability.visualModeFor("Energy Connector", "Energy Buffer", "energy_capacity", "metric"));
        assertEquals("dial", CustomScreenSourceCapability.visualModeFor("Unknown Machine", "Accumulator Capacity", "energy_capacity", "metric"));
        assertEquals("dial", CustomScreenSourceCapability.visualModeFor("Unknown Machine", "Stress Used / Capacity", "capacity_su", "metric"));
        assertEquals("rpm", CustomScreenSourceCapability.visualModeFor("Velocity Sensor", "Velocity X", "velocity_x", "metric"));
    }

    @Test
    void presetApplyUsesSourceFamilyCompatibilityForPrefill() {
        final LinkedHashMap<String, SourceConfig> sources = new LinkedHashMap<String, SourceConfig>();
        sources.put("stress-a", new SourceConfig("stress-a", BlockPos.ZERO, "Stressometer"));
        sources.put("diesel-a", new SourceConfig("diesel-a", BlockPos.ZERO, "Diesel Engine Group"));
        sources.put("battle-a", new SourceConfig("battle-a", BlockPos.ZERO, "Battle Bridge"));
        sources.put("legacy-battle", new SourceConfig("legacy-battle", BlockPos.ZERO, "Battle Screen"));

        final CustomScreenPresetCatalog.PresetDefinition dieselPreset = CustomScreenPresetCatalog.find("diesel_operations");
        final CustomScreenPresetCatalog.ApplyResult dieselMismatch = CustomScreenPresetCatalog.apply(null, dieselPreset, CustomScreenPresetCatalog.ApplyMode.REPLACE, "stress-a", sources);
        assertEquals(0, dieselMismatch.directBindings());
        assertTrue(dieselMismatch.unresolvedBindings() > 0);
        assertEquals("", explicitSourceFor(dieselMismatch.layout(), CustomScreenModuleType.DIESEL_ENGINE_PAGE));

        final CustomScreenPresetCatalog.ApplyResult dieselMatch = CustomScreenPresetCatalog.apply(null, dieselPreset, CustomScreenPresetCatalog.ApplyMode.REPLACE, "diesel-a", sources);
        assertTrue(dieselMatch.directBindings() > 0);
        assertEquals("diesel-a", explicitSourceFor(dieselMatch.layout(), CustomScreenModuleType.DIESEL_ENGINE_PAGE));

        final CustomScreenPresetCatalog.PresetDefinition aeroPreset = CustomScreenPresetCatalog.find("aero_bridge");
        final CustomScreenPresetCatalog.ApplyResult aeroMismatch = CustomScreenPresetCatalog.apply(null, aeroPreset, CustomScreenPresetCatalog.ApplyMode.REPLACE, "battle-a", sources);
        assertEquals(0, aeroMismatch.directBindings());
        assertEquals("", explicitSourceFor(aeroMismatch.layout(), CustomScreenModuleType.AERO_FLIGHT_DECK));

        final CustomScreenPresetCatalog.PresetDefinition battlePreset = CustomScreenPresetCatalog.find("battle_board");
        final CustomScreenPresetCatalog.ApplyResult battleAlias = CustomScreenPresetCatalog.apply(null, battlePreset, CustomScreenPresetCatalog.ApplyMode.REPLACE, "legacy-battle", sources);
        assertTrue(battleAlias.directBindings() > 0);
        assertEquals("legacy-battle", explicitSourceFor(battleAlias.layout(), CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION));
    }

    @Test
    void widgetContractsRelaxAuthoringMinimumsByOneCell() {
        final CustomScreenModuleContract analog = CustomScreenModuleCatalog.getContract(CustomScreenModuleType.ANALOG_GAUGE);
        assertNotNull(analog);
        assertEquals(4, analog.getMinReadableWidth());
        assertEquals(3, analog.getMinAuthoringWidth());
        assertEquals(2, analog.getMinAuthoringHeight());

        final CustomScreenModuleContract title = CustomScreenModuleCatalog.getContract(CustomScreenModuleType.TITLE);
        assertNotNull(title);
        assertEquals(title.getMinReadableWidth(), title.getMinAuthoringWidth());
        assertEquals(title.getMinReadableHeight(), title.getMinAuthoringHeight());
    }

    private static CustomScreenLayout namedLayout(String name) {
        final CustomScreenLayout layout = new CustomScreenLayout();
        final CustomScreenLayout.LayoutMeta meta = new CustomScreenLayout.LayoutMeta();
        meta.setName(name);
        layout.setLayoutMeta(meta);
        final CustomModuleInstance module = new CustomModuleInstance();
        module.setId(name.toLowerCase().replace(' ', '-') + "-module");
        module.setModuleType(CustomScreenModuleType.BIG_VALUE.getSerializedName());
        layout.addModule(module);
        return layout;
    }

    private static String explicitSourceFor(CustomScreenLayout layout, CustomScreenModuleType moduleType) {
        for (CustomModuleInstance module : layout.getModules()) {
            if (moduleType.getSerializedName().equals(module.getModuleType())) {
                final CustomModuleBinding binding = module.resolveBinding(CustomModuleBinding.PRIMARY_SOURCE);
                return binding != null ? binding.getSourceId() : "";
            }
        }
        return "";
    }

    private static void assertAnalogMode(List<CustomScreenSourceCapability> capabilities, String sectionLabel, String visualMode) {
        CustomScreenSourceCapability match = null;
        for (CustomScreenSourceCapability capability : capabilities) {
            if (sectionLabel.equals(capability.getSectionLabel())) {
                match = capability;
                break;
            }
        }
        assertNotNull(match, sectionLabel);
        assertEquals(CustomScreenModuleType.ANALOG_GAUGE, match.getModuleType());
        final CustomModuleInstance module = match.createModule(0, 0);
        assertNotNull(module);
        assertEquals(visualMode, module.getModuleConfig().getString("visualMode"));
        assertEquals(match.getSourceId(), module.resolveBinding(CustomModuleBinding.PRIMARY_METRIC).getSourceId());
    }
}