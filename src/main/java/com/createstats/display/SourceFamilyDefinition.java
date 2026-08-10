package com.createstats.display;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SourceFamilyDefinition {
    private final String familyId;
    private final String displayName;
    private final SourceFamilyGroup group;
    private final Set<String> exactSourceTypes;
    private final Set<String> legacyAliases;
    private final RendererFamily rendererFamily;
    private final Set<CustomScreenModuleType> allowedMacroScenes;
    private final GenericFallbackPolicy genericFallbackPolicy;
    private final CompactPolicy compactPolicy;

    public SourceFamilyDefinition(String familyId,
                                  String displayName,
                                  SourceFamilyGroup group,
                                  Collection<String> exactSourceTypes,
                                  Collection<String> legacyAliases,
                                  RendererFamily rendererFamily,
                                  Collection<CustomScreenModuleType> allowedMacroScenes,
                                  GenericFallbackPolicy genericFallbackPolicy,
                                  CompactPolicy compactPolicy) {
        this.familyId = safe(familyId);
        this.displayName = safe(displayName).isEmpty() ? this.familyId : safe(displayName);
        this.group = group != null ? group : SourceFamilyGroup.GENERIC;
        this.exactSourceTypes = immutableOrderedSet(exactSourceTypes);
        this.legacyAliases = immutableOrderedSet(legacyAliases);
        this.rendererFamily = rendererFamily != null ? rendererFamily : RendererFamily.NONE;
        this.allowedMacroScenes = immutableOrderedSet(allowedMacroScenes);
        this.genericFallbackPolicy = genericFallbackPolicy != null ? genericFallbackPolicy : GenericFallbackPolicy.ALLOW_GENERIC_WIDGETS;
        this.compactPolicy = compactPolicy != null ? compactPolicy : CompactPolicy.KEEP;
    }

    public String getFamilyId() {
        return this.familyId;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public SourceFamilyGroup getGroup() {
        return this.group;
    }

    public Set<String> getExactSourceTypes() {
        return this.exactSourceTypes;
    }

    public Set<String> getLegacyAliases() {
        return this.legacyAliases;
    }

    public RendererFamily getRendererFamily() {
        return this.rendererFamily;
    }

    public Set<CustomScreenModuleType> getAllowedMacroScenes() {
        return this.allowedMacroScenes;
    }

    public GenericFallbackPolicy getGenericFallbackPolicy() {
        return this.genericFallbackPolicy;
    }

    public CompactPolicy getCompactPolicy() {
        return this.compactPolicy;
    }

    public boolean matchesExactSourceType(String sourceType) {
        return this.exactSourceTypes.contains(safe(sourceType));
    }

    public boolean matchesLegacyAlias(String sourceType) {
        return this.legacyAliases.contains(safe(sourceType));
    }

    private static <T> Set<T> immutableOrderedSet(Collection<T> values) {
        final LinkedHashSet<T> set = new LinkedHashSet<T>();
        if (values != null) {
            for (T value : values) {
                if (value != null) {
                    set.add(value);
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static String safe(String value) {
        return value != null ? value.trim() : "";
    }

    public enum SourceFamilyGroup {
        KINETICS,
        ENERGY,
        DIESEL_FLUIDS,
        FACTORY_INVENTORY,
        FLIGHT_NAVIGATION,
        BATTLE,
        SENSORS_TEXT,
        LOGISTICS,
        GENERIC
    }

    public enum GenericFallbackPolicy {
        ALLOW_GENERIC_WIDGETS,
        ALLOW_GENERIC_ATOMS_ONLY,
        REQUIRE_NATIVE_CONTRACT
    }

    public enum RendererFamily {
        NONE,
        STRESSOMETER,
        STOCKPILE,
        ACCUMULATOR,
        DIESEL_ENGINE,
        DIESEL_PROCESS,
        PORTABLE_ENGINE,
        SENSOR,
        NAVIGATION,
        CONTRAPTION,
        BATTLE_BRIDGE,
        AERO_FLIGHT_DECK,
        AEROLINE_DIRECTOR,
        TEXT_UTILITY,
        FLOW_METER,
        INDUCTION_HEATER,
        CAPACITOR_BANK
    }

    public enum CompactPolicy {
        KEEP,
        SIMPLIFY,
        HIDE,
        OFFER_SIBLING
    }
}