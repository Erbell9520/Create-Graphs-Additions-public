package com.createstats.display;

import java.util.List;

public class CustomScreenModuleContract {
    private final CustomScreenModuleType type;
    private final String displayName;
    private final Domain domain;
    private final CustomModuleInstance.ModuleScale scale;
    private final int defaultWidth;
    private final int defaultHeight;
    private final int minReadableWidth;
    private final int minReadableHeight;
    private final List<String> bindingFields;
    private final boolean allowSemanticFallback;
    private final List<String> variantKeys;
    private final CustomModuleInstance.VisibilityPolicy responsiveDefaults;
    private final HeroPolicy heroPolicy;
    private final String compactSiblingModuleId;
    private final PlacementPolicy placementPolicy;
    private final RendererStyle rendererStyle;

    public CustomScreenModuleContract(CustomScreenModuleType type,
                                      String displayName,
                                      Domain domain,
                                      CustomModuleInstance.ModuleScale scale,
                                      int defaultWidth,
                                      int defaultHeight,
                                      int minReadableWidth,
                                      int minReadableHeight,
                                      List<String> bindingFields,
                                      boolean allowSemanticFallback,
                                      List<String> variantKeys,
                                      CustomModuleInstance.VisibilityPolicy responsiveDefaults,
                                      HeroPolicy heroPolicy,
                                      String compactSiblingModuleId,
                                      PlacementPolicy placementPolicy,
                                      RendererStyle rendererStyle) {
        this.type = type;
        this.displayName = displayName != null ? displayName : "Module";
        this.domain = domain != null ? domain : Domain.STRUCTURE;
        this.scale = scale != null ? scale : CustomModuleInstance.ModuleScale.WIDGET;
        this.defaultWidth = Math.max(1, defaultWidth);
        this.defaultHeight = Math.max(1, defaultHeight);
        this.minReadableWidth = Math.max(1, minReadableWidth);
        this.minReadableHeight = Math.max(1, minReadableHeight);
        this.bindingFields = bindingFields != null ? List.copyOf(bindingFields) : List.of();
        this.allowSemanticFallback = allowSemanticFallback;
        this.variantKeys = variantKeys != null ? List.copyOf(variantKeys) : List.of("default");
        this.responsiveDefaults = responsiveDefaults != null ? responsiveDefaults.copy() : new CustomModuleInstance.VisibilityPolicy();
        this.heroPolicy = heroPolicy != null ? heroPolicy : HeroPolicy.NO;
        this.compactSiblingModuleId = compactSiblingModuleId != null ? compactSiblingModuleId : "";
        this.placementPolicy = placementPolicy != null ? placementPolicy : PlacementPolicy.FREE;
        this.rendererStyle = rendererStyle != null ? rendererStyle : RendererStyle.CARD;
    }

    public CustomScreenModuleType getType() {
        return this.type;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public Domain getDomain() {
        return this.domain;
    }

    public CustomModuleInstance.ModuleScale getScale() {
        return this.scale;
    }

    public int getDefaultWidth() {
        return this.defaultWidth;
    }

    public int getDefaultHeight() {
        return this.defaultHeight;
    }

    public int getMinReadableWidth() {
        return this.minReadableWidth;
    }

    public int getMinReadableHeight() {
        return this.minReadableHeight;
    }

    public int getMinAuthoringWidth() {
        if (this.scale == CustomModuleInstance.ModuleScale.WIDGET && this.heroPolicy == HeroPolicy.NO) {
            return Math.max(1, this.minReadableWidth - 1);
        }
        return this.minReadableWidth;
    }

    public int getMinAuthoringHeight() {
        if (this.scale == CustomModuleInstance.ModuleScale.WIDGET && this.heroPolicy == HeroPolicy.NO) {
            return Math.max(1, this.minReadableHeight - 1);
        }
        return this.minReadableHeight;
    }

    public List<String> getBindingFields() {
        return this.bindingFields;
    }

    public boolean allowsSemanticFallback() {
        return this.allowSemanticFallback;
    }

    public List<String> getVariantKeys() {
        return this.variantKeys;
    }

    public CustomModuleInstance.VisibilityPolicy createResponsiveDefaults() {
        return this.responsiveDefaults.copy();
    }

    public HeroPolicy getHeroPolicy() {
        return this.heroPolicy;
    }

    public String getCompactSiblingModuleId() {
        return this.compactSiblingModuleId;
    }

    public PlacementPolicy getPlacementPolicy() {
        return this.placementPolicy;
    }

    public RendererStyle getRendererStyle() {
        return this.rendererStyle;
    }

    public enum Domain {
        STRUCTURE("Structure"),
        METRICS("Metrics"),
        STATUS("Status");

        private final String label;

        Domain(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }
    }

    public enum HeroPolicy {
        YES,
        PREFER_YES,
        NO
    }

    public enum PlacementPolicy {
        FREE,
        HERO_LOCKED
    }

    public enum RendererStyle {
        TITLE,
        SUBTITLE,
        DIVIDER,
        BADGE,
        LOGO,
        BIG_VALUE,
        ANALOG_GAUGE,
        STATUS_STRIP,
        DUAL_CARD,
        TRIPLE_CARD,
        CHANNEL_LIST,
        MACRO_SCENE,
        CARD
    }
}