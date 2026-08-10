package com.createstats.display;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomScreenSourceCapability {
    private final String sourceId;
    private final String sourceType;
    private final String sourceLabel;
    private final PaletteDomain domain;
    private final String ownerScreen;
    private final String sectionId;
    private final String sectionLabel;
    private final SectionRole sectionRole;
    private final CustomScreenModuleType moduleType;
    private final String primaryMetric;
    private final String description;
    private final boolean hero;
    private final boolean online;
    private final BlockPos sourcePos;

    public CustomScreenSourceCapability(String sourceId,
                                        String sourceType,
                                        String sourceLabel,
                                        PaletteDomain domain,
                                        String ownerScreen,
                                        String sectionId,
                                        String sectionLabel,
                                        SectionRole sectionRole,
                                        CustomScreenModuleType moduleType,
                                        String primaryMetric,
                                        String description,
                                        boolean hero,
                                        boolean online,
                                        BlockPos sourcePos) {
        this.sourceId = safe(sourceId);
        this.sourceType = safe(sourceType);
        this.sourceLabel = safe(sourceLabel);
        this.domain = domain != null ? domain : PaletteDomain.GENERIC;
        this.ownerScreen = safe(ownerScreen);
        this.sectionId = safe(sectionId);
        this.sectionLabel = safe(sectionLabel);
        this.sectionRole = sectionRole != null ? sectionRole : SectionRole.STATUS;
        this.moduleType = moduleType != null ? moduleType : CustomScreenModuleType.STATUS_STRIP;
        this.primaryMetric = safe(primaryMetric);
        this.description = safe(description);
        this.hero = hero;
        this.online = online;
        this.sourcePos = sourcePos;
    }

    public String getSourceId() {
        return this.sourceId;
    }

    public String getSourceType() {
        return this.sourceType;
    }

    public String getSourceLabel() {
        return this.sourceLabel.isEmpty() ? this.sourceId : this.sourceLabel;
    }

    public PaletteDomain getDomain() {
        return this.domain;
    }

    public String getOwnerScreen() {
        return this.ownerScreen;
    }

    public String getSectionId() {
        return this.sectionId;
    }

    public String getSectionLabel() {
        return this.sectionLabel;
    }

    public SectionRole getSectionRole() {
        return this.sectionRole;
    }

    public CustomScreenModuleType getModuleType() {
        return this.moduleType;
    }

    public String getPrimaryMetric() {
        return this.primaryMetric;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isHero() {
        return this.hero;
    }

    public boolean isOnline() {
        return this.online;
    }

    public boolean matches(String query) {
        final String needle = safe(query).trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        final String haystack = (this.sourceId + " " + this.sourceType + " " + this.sourceLabel + " "
                + this.domain.getLabel() + " " + this.ownerScreen + " " + this.sectionId + " "
                + this.sectionLabel + " " + this.sectionRole.getLabel() + " " + this.primaryMetric + " "
                + this.description).toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }

    public CustomModuleInstance createModule(int gridX, int gridY) {
        final CustomModuleInstance module = CustomScreenModuleCatalog.createInstance(this.moduleType, gridX, gridY);
        if (module == null) {
            return null;
        }
        module.setId(this.moduleType.getSerializedName() + "-" + slug(this.sourceId) + "-" + slug(this.sectionId) + "-" + System.nanoTime());
        module.setBindings(this.seededBindings(module));
        module.setModuleConfig(this.seededConfig(module));
        module.normalize();
        return module;
    }

    private List<CustomModuleBinding> seededBindings(CustomModuleInstance module) {
        final ArrayList<CustomModuleBinding> bindings = new ArrayList<CustomModuleBinding>();
        bindings.add(explicitSourceBinding());
        if (!this.primaryMetric.isEmpty()) {
            bindings.add(metricBinding(this.metricBindingKey(module), this.primaryMetric, this.sectionLabel, this.sourceId, this.sourceType, this.sourcePos));
        }
        for (CustomModuleBinding binding : module.getBindings()) {
            if (binding == null) {
                continue;
            }
            final boolean replacesSource = CustomModuleBinding.PRIMARY_SOURCE.equals(binding.getKey());
            final boolean replacesMetric = !this.primaryMetric.isEmpty() && this.metricBindingKey(module).equals(binding.getKey());
            if (!replacesSource && !replacesMetric) {
                bindings.add(binding.copy());
            }
        }
        return bindings;
    }

    private CompoundTag seededConfig(CustomModuleInstance module) {
        final CompoundTag config = module.getModuleConfig();
        config.putString("ownerScreen", this.ownerScreen);
        config.putString("sectionId", this.sectionId);
        config.putString("sectionLabel", this.sectionLabel);
        config.putString("sectionRole", this.sectionRole.getSerializedName());
        config.putString("sourceId", this.sourceId);
        config.putString("sourceType", this.sourceType);
        config.putString("sourceLabel", this.getSourceLabel());
        config.putBoolean("sourceOnline", this.online);
        config.putBoolean("sourceHero", this.hero);
        if (!this.primaryMetric.isEmpty()) {
            config.putString("primaryMetric", this.primaryMetric);
        }
        switch (this.moduleType) {
            case TITLE -> config.putString("text", this.getSourceLabel());
            case SUBTITLE -> config.putString("text", this.ownerScreen + " / " + this.sourceType);
            case STATUS_BADGE -> config.putString("text", this.online ? "Live" : "Offline");
            case BIG_VALUE -> {
                config.putString("label", this.sectionLabel);
                config.putString("value", this.primaryMetric.isEmpty() ? "Live" : this.primaryMetric);
                config.putString("suffix", this.sourceType);
            }
            case ANALOG_GAUGE -> {
                config.putString("label", this.sectionLabel);
                config.putString("value", this.primaryMetric.isEmpty() ? "Live" : this.primaryMetric);
                config.putString("visualMode", visualModeFor(this.sourceType, this.sectionLabel, this.primaryMetric, this.sectionRole.getSerializedName()));
            }
            case STATUS_STRIP -> {
                config.putString("left", this.sectionLabel);
                config.putString("center", this.getSourceLabel());
                config.putString("right", this.ownerScreen);
            }
            case DUAL_CARD -> {
                config.putString("leftLabel", this.sectionLabel);
                config.putString("leftValue", this.primaryMetric.isEmpty() ? "Live" : this.primaryMetric);
                config.putString("rightLabel", "Source");
                config.putString("rightValue", this.sourceType);
            }
            case TRIPLE_CARD -> {
                config.putString("aLabel", this.sectionLabel);
                config.putString("aValue", this.primaryMetric.isEmpty() ? "Live" : this.primaryMetric);
                config.putString("bLabel", "Source");
                config.putString("bValue", this.sourceType);
                config.putString("cLabel", "Owner");
                config.putString("cValue", this.ownerScreen);
            }
            case CHANNEL_LIST -> config.putString("title", this.sectionLabel);
            case AERO_FLIGHT_DECK, AEROLINE_DIRECTOR, DIESEL_ENGINE_PAGE, BATTLE_SCREEN_COMPOSITION, BATTLE_TACTICAL_V2 -> {
                config.putString("title", this.sectionLabel);
                config.putString("subtitle", this.ownerScreen + " / " + this.getSourceLabel());
            }
            default -> {
            }
        }
        return config;
    }

    private String metricBindingKey(CustomModuleInstance module) {
        if (module != null) {
            final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(module.getModuleType());
            if (contract != null) {
                for (String field : contract.getBindingFields()) {
                    if (CustomModuleBinding.PRIMARY_METRIC.equals(field)
                            || CustomModuleBinding.SECONDARY_METRIC.equals(field)
                            || CustomModuleBinding.STATUS_METRIC.equals(field)) {
                        return field;
                    }
                }
            }
        }
        return CustomModuleBinding.PRIMARY_METRIC;
    }

    public static String visualModeFor(String sourceType, String sectionLabel, String primaryMetric, String sectionRole) {
        final String haystack = (safe(sourceType) + " " + safe(sectionLabel) + " " + safe(primaryMetric) + " " + safe(sectionRole)).toLowerCase(Locale.ROOT);
        final String familyId = CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType).getFamilyId();
        if ("energy.accumulator".equals(familyId) || "energy.connector".equals(familyId)) {
            return "accumulator";
        }
        if (familyId.startsWith("kinetics.") && (haystack.contains("rpm") || haystack.contains("speed"))) {
            return "rpm";
        }
        if (("kinetics.stressometer".equals(familyId) || "kinetics.clutch".equals(familyId) || "kinetics.load_balancer".equals(familyId))
                && (haystack.contains("stress") || haystack.contains("capacity") || haystack.contains("load"))) {
            return "stress";
        }
        if ("flight.velocity_sensor".equals(familyId) && (haystack.contains("velocity") || haystack.contains("speed") || haystack.contains("rpm"))) {
            return "rpm";
        }
        if (haystack.contains("fuel") || haystack.contains("fluid") || haystack.contains("tank") || haystack.contains("reserve") || haystack.contains("water") || haystack.contains("oil")) {
            return "tank";
        }
        if (haystack.contains("heat") || haystack.contains("burner") || haystack.contains("boiler")) {
            return "heat";
        }
        if (haystack.contains("altitude") || haystack.contains("height") || haystack.contains("pressure")) {
            return "altitude";
        }
        if (haystack.contains("horizon") || haystack.contains("attitude") || haystack.contains("angle")) {
            return "horizon";
        }
        return "dial";
    }

    private CustomModuleBinding explicitSourceBinding() {
        final CustomModuleBinding binding = new CustomModuleBinding();
        binding.setKey(CustomModuleBinding.PRIMARY_SOURCE);
        binding.setMode(CustomModuleBinding.Mode.EXPLICIT_SOURCE);
        binding.setSourceId(this.sourceId);
        binding.setSourceTypeFilter(this.sourceType);
        binding.setSourceTypeHint(this.sourceType);
        if (this.sourcePos != null) {
            binding.setSourcePosHint(this.sourcePos.asLong());
        }
        binding.normalize();
        return binding;
    }

    private static CustomModuleBinding metricBinding(String key, String slot, String label, String sourceId, String sourceType, BlockPos sourcePos) {
        final CustomModuleBinding binding = new CustomModuleBinding();
        binding.setKey(key);
        binding.setMode(CustomModuleBinding.Mode.SEMANTIC_SLOT);
        binding.setSlot(slot);
        binding.setMetricLabel(label);
        binding.setSourceId(sourceId);
        binding.setSourceTypeFilter(sourceType);
        binding.setSourceTypeHint(sourceType);
        if (sourcePos != null) {
            binding.setSourcePosHint(sourcePos.asLong());
        }
        binding.normalize();
        return binding;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static String slug(String value) {
        final String normalized = safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isEmpty() ? "source" : normalized;
    }

    public enum PaletteDomain {
        FLIGHT("Flight"),
        ENGINES("Engines"),
        BATTLE("Battle"),
        FACTORY("Factory"),
        FLUIDS("Fluids"),
        SENSORS("Sensors"),
        GENERIC("Generic");

        private final String label;

        PaletteDomain(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }
    }

    public enum SectionRole {
        HERO("Hero", "hero"),
        INSTRUMENT("Instrument", "instrument"),
        METRIC("Metric", "metric"),
        STATUS("Status", "status"),
        CONTROL("Control", "control"),
        LIST("List", "list"),
        UTILITY("Utility", "utility");

        private final String label;
        private final String serializedName;

        SectionRole(String label, String serializedName) {
            this.label = label;
            this.serializedName = serializedName;
        }

        public String getLabel() {
            return this.label;
        }

        public String getSerializedName() {
            return this.serializedName;
        }
    }
}