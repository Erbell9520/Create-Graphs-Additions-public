package com.createstats.display;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CustomScreenModuleCatalog {
    private static final LinkedHashMap<CustomScreenModuleType, CustomScreenModuleContract> CONTRACTS = new LinkedHashMap<CustomScreenModuleType, CustomScreenModuleContract>();
    private static final Map<String, String> MODULE_TYPE_ALIASES = Map.of(
            "macro_aero_flight_deck", CustomScreenModuleType.AERO_FLIGHT_DECK.getSerializedName(),
            "macro_aeroline_director", CustomScreenModuleType.AEROLINE_DIRECTOR.getSerializedName(),
            "macro_diesel_engine", CustomScreenModuleType.DIESEL_ENGINE_PAGE.getSerializedName(),
            "macro_battle_screen", CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION.getSerializedName(),
            "macro_battle_tactical_v2", CustomScreenModuleType.BATTLE_TACTICAL_V2.getSerializedName());
    private static final AtomicInteger NEXT_INSTANCE_ID = new AtomicInteger(1);

    static {
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.TITLE,
                "Title",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.ATOM,
                8,
                2,
                4,
                1,
                List.of(),
                false,
                List.of("default"),
                alwaysVisible(),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.TITLE));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.SUBTITLE,
                "Subtitle",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.ATOM,
                8,
                2,
                4,
                1,
                List.of(),
                false,
                List.of("default"),
                alwaysVisible(),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.SUBTITLE));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.DIVIDER,
                "Divider",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.ATOM,
                8,
                1,
                3,
                1,
                List.of(),
                false,
                List.of("default"),
                alwaysVisible(),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.DIVIDER));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.STATUS_BADGE,
                "Status Badge",
                CustomScreenModuleContract.Domain.STATUS,
                CustomModuleInstance.ModuleScale.ATOM,
                4,
                2,
                3,
                1,
                List.of(CustomModuleBinding.STATUS_METRIC),
                true,
                List.of("default", "warning"),
                alwaysVisible(),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.BADGE));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.LOGO,
                "Logo / Icon",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.ATOM,
                3,
                3,
                2,
                2,
                List.of(),
                false,
                List.of("default"),
                alwaysVisible(),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.LOGO));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.BIG_VALUE,
                "Big Value Card",
                CustomScreenModuleContract.Domain.METRICS,
                CustomModuleInstance.ModuleScale.WIDGET,
                6,
                4,
                4,
                3,
                List.of(CustomModuleBinding.PRIMARY_METRIC),
                true,
                List.of("default", "dense"),
                widgetVisibility(false),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.BIG_VALUE));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.ANALOG_GAUGE,
                "Analog Gauge",
                CustomScreenModuleContract.Domain.METRICS,
                CustomModuleInstance.ModuleScale.WIDGET,
                6,
                4,
                4,
                3,
                List.of(CustomModuleBinding.PRIMARY_METRIC),
                true,
                List.of("default", "compact"),
                widgetVisibility(false),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.ANALOG_GAUGE));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.STATUS_STRIP,
                "Status Strip",
                CustomScreenModuleContract.Domain.STATUS,
                CustomModuleInstance.ModuleScale.WIDGET,
                8,
                2,
                5,
                2,
                List.of(CustomModuleBinding.STATUS_METRIC),
                true,
                List.of("default"),
                alwaysVisible(),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.STATUS_STRIP));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.DUAL_CARD,
                "Dual Card",
                CustomScreenModuleContract.Domain.METRICS,
                CustomModuleInstance.ModuleScale.WIDGET,
                8,
                4,
                5,
                3,
                List.of(CustomModuleBinding.PRIMARY_METRIC, CustomModuleBinding.SECONDARY_METRIC),
                true,
                List.of("default"),
                widgetVisibility(false),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.DUAL_CARD));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.TRIPLE_CARD,
                "Triple Card",
                CustomScreenModuleContract.Domain.METRICS,
                CustomModuleInstance.ModuleScale.WIDGET,
                9,
                4,
                6,
                3,
                List.of(CustomModuleBinding.PRIMARY_METRIC, CustomModuleBinding.SECONDARY_METRIC, CustomModuleBinding.STATUS_METRIC),
                true,
                List.of("default"),
                widgetVisibility(true),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.TRIPLE_CARD));
        register(new CustomScreenModuleContract(
                CustomScreenModuleType.CHANNEL_LIST,
                "Channel List",
                CustomScreenModuleContract.Domain.STATUS,
                CustomModuleInstance.ModuleScale.WIDGET,
                6,
                5,
                4,
                4,
                List.of(CustomModuleBinding.PRIMARY_SOURCE),
                false,
                List.of("default"),
                widgetVisibility(true),
                CustomScreenModuleContract.HeroPolicy.NO,
                "",
                CustomScreenModuleContract.PlacementPolicy.FREE,
                CustomScreenModuleContract.RendererStyle.CHANNEL_LIST));
            register(new CustomScreenModuleContract(
                CustomScreenModuleType.AERO_FLIGHT_DECK,
                "Aero Flight Deck",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.MACRO_SCENE,
                12,
                10,
                10,
                8,
                List.of(CustomModuleBinding.PRIMARY_SOURCE),
                false,
                List.of("default"),
                heroVisibility(true),
                CustomScreenModuleContract.HeroPolicy.YES,
                "",
                CustomScreenModuleContract.PlacementPolicy.HERO_LOCKED,
                CustomScreenModuleContract.RendererStyle.MACRO_SCENE));
            register(new CustomScreenModuleContract(
                CustomScreenModuleType.AEROLINE_DIRECTOR,
                "Aeroline Director",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.MACRO_SCENE,
                12,
                9,
                10,
                8,
                List.of(CustomModuleBinding.PRIMARY_SOURCE),
                false,
                List.of("default"),
                heroVisibility(true),
                CustomScreenModuleContract.HeroPolicy.YES,
                "",
                CustomScreenModuleContract.PlacementPolicy.HERO_LOCKED,
                CustomScreenModuleContract.RendererStyle.MACRO_SCENE));
            register(new CustomScreenModuleContract(
                CustomScreenModuleType.DIESEL_ENGINE_PAGE,
                "Diesel Engine Page",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.MACRO_SCENE,
                10,
                8,
                8,
                6,
                List.of(CustomModuleBinding.PRIMARY_SOURCE),
                false,
                List.of("default"),
                heroVisibility(true),
                CustomScreenModuleContract.HeroPolicy.PREFER_YES,
                "",
                CustomScreenModuleContract.PlacementPolicy.HERO_LOCKED,
                CustomScreenModuleContract.RendererStyle.MACRO_SCENE));
            register(new CustomScreenModuleContract(
                CustomScreenModuleType.BATTLE_SCREEN_COMPOSITION,
                "Battle Bridge",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.MACRO_SCENE,
                12,
                9,
                10,
                8,
                List.of(CustomModuleBinding.PRIMARY_SOURCE),
                false,
                List.of("default"),
                heroVisibility(true),
                CustomScreenModuleContract.HeroPolicy.YES,
                "",
                CustomScreenModuleContract.PlacementPolicy.HERO_LOCKED,
                CustomScreenModuleContract.RendererStyle.MACRO_SCENE));
            register(new CustomScreenModuleContract(
                CustomScreenModuleType.BATTLE_TACTICAL_V2,
                "Battle Tactical V2",
                CustomScreenModuleContract.Domain.STRUCTURE,
                CustomModuleInstance.ModuleScale.MACRO_SCENE,
                12,
                9,
                1,
                1,
                List.of(CustomModuleBinding.PRIMARY_SOURCE),
                false,
                List.of("default"),
                heroVisibility(true),
                CustomScreenModuleContract.HeroPolicy.YES,
                "",
                CustomScreenModuleContract.PlacementPolicy.HERO_LOCKED,
                CustomScreenModuleContract.RendererStyle.MACRO_SCENE));
    }

    private CustomScreenModuleCatalog() {
    }

    public static CustomScreenModuleContract getContract(CustomScreenModuleType type) {
        return type != null ? CONTRACTS.get(type) : null;
    }

    public static CustomScreenModuleContract getContract(String moduleTypeId) {
        return getContract(CustomScreenModuleType.fromSerializedName(resolveModuleTypeId(moduleTypeId)));
    }

    public static String resolveModuleTypeId(String moduleTypeId) {
        final String safeId = moduleTypeId != null ? moduleTypeId : "";
        return MODULE_TYPE_ALIASES.getOrDefault(safeId, safeId);
    }

    public static List<CustomScreenModuleContract> allContracts() {
        return List.copyOf(CONTRACTS.values());
    }

    public static List<CustomScreenModuleContract> filteredContracts(String query, CustomScreenModuleContract.Domain domain) {
        final String needle = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        final ArrayList<CustomScreenModuleContract> filtered = new ArrayList<CustomScreenModuleContract>();
        for (CustomScreenModuleContract contract : CONTRACTS.values()) {
            if (contract == null) {
                continue;
            }
            if (domain != null && contract.getDomain() != domain) {
                continue;
            }
            if (!needle.isEmpty()) {
                final String haystack = contract.getDisplayName().toLowerCase(Locale.ROOT) + " " + contract.getType().getSerializedName();
                if (!haystack.contains(needle)) {
                    continue;
                }
            }
            filtered.add(contract);
        }
        return filtered;
    }

    public static CustomModuleInstance createInstance(CustomScreenModuleType type, int gridX, int gridY) {
        final CustomScreenModuleContract contract = getContract(type);
        if (contract == null) {
            return null;
        }
        final CustomModuleInstance module = new CustomModuleInstance();
        module.setId(type.getSerializedName() + "-" + NEXT_INSTANCE_ID.getAndIncrement());
        module.setModuleType(type.getSerializedName());
        module.setScale(contract.getScale());
        module.setBounds(new CustomModuleInstance.GridBounds(gridX, gridY, contract.getDefaultWidth(), contract.getDefaultHeight()));
        module.setVariant("default");
        module.setVisibilityPolicy(contract.createResponsiveDefaults());
        module.setModuleConfig(defaultConfig(type));
        module.setBindings(defaultBindings(type));
        module.normalize();
        return module;
    }

    public static CustomScreenLayout createStarterLayout() {
        final CustomScreenLayout layout = new CustomScreenLayout();
        final CustomModuleInstance title = createInstance(CustomScreenModuleType.TITLE, 0, 0);
        if (title != null) {
            title.setBounds(new CustomModuleInstance.GridBounds(0, 0, 10, 2));
            layout.addModule(title);
        }
        final CustomModuleInstance subtitle = createInstance(CustomScreenModuleType.SUBTITLE, 10, 0);
        if (subtitle != null) {
            subtitle.setBounds(new CustomModuleInstance.GridBounds(10, 0, 8, 2));
            layout.addModule(subtitle);
        }
        final CustomModuleInstance bigValue = createInstance(CustomScreenModuleType.BIG_VALUE, 0, 2);
        if (bigValue != null) {
            bigValue.setBounds(new CustomModuleInstance.GridBounds(0, 2, 6, 4));
            layout.addModule(bigValue);
        }
        final CustomModuleInstance gauge = createInstance(CustomScreenModuleType.ANALOG_GAUGE, 6, 2);
        if (gauge != null) {
            gauge.setBounds(new CustomModuleInstance.GridBounds(6, 2, 6, 4));
            layout.addModule(gauge);
        }
        final CustomModuleInstance dualCard = createInstance(CustomScreenModuleType.DUAL_CARD, 12, 2);
        if (dualCard != null) {
            dualCard.setBounds(new CustomModuleInstance.GridBounds(12, 2, 6, 4));
            layout.addModule(dualCard);
        }
        final CustomModuleInstance statusStrip = createInstance(CustomScreenModuleType.STATUS_STRIP, 0, 6);
        if (statusStrip != null) {
            statusStrip.setBounds(new CustomModuleInstance.GridBounds(0, 6, 18, 2));
            layout.addModule(statusStrip);
        }
        final CustomModuleInstance channelList = createInstance(CustomScreenModuleType.CHANNEL_LIST, 0, 8);
        if (channelList != null) {
            channelList.setBounds(new CustomModuleInstance.GridBounds(0, 8, 9, 4));
            layout.addModule(channelList);
        }
        final CustomModuleInstance tripleCard = createInstance(CustomScreenModuleType.TRIPLE_CARD, 9, 8);
        if (tripleCard != null) {
            tripleCard.setBounds(new CustomModuleInstance.GridBounds(9, 8, 9, 4));
            layout.addModule(tripleCard);
        }
        layout.normalize();
        return layout;
    }

    public static int findNextX(CustomScreenLayout layout, int width) {
        if (layout == null || layout.getModules().isEmpty()) {
            return 0;
        }
        int maxX = 0;
        for (CustomModuleInstance module : layout.getModules()) {
            if (module == null || module.getBounds() == null) {
                continue;
            }
            maxX = Math.max(maxX, module.getBounds().getX() + module.getBounds().getWidth());
        }
        return Math.max(0, maxX - Math.max(0, width / 2));
    }

    private static CustomScreenModuleContract register(CustomScreenModuleContract contract) {
        CONTRACTS.put(contract.getType(), contract);
        return contract;
    }

    private static CustomModuleInstance.VisibilityPolicy alwaysVisible() {
        final CustomModuleInstance.VisibilityPolicy policy = new CustomModuleInstance.VisibilityPolicy();
        for (CustomScreenLayout.VariantFamily family : CustomScreenLayout.VariantFamily.values()) {
            policy.set(family, CustomModuleInstance.VisibilityPolicy.VisibilityMode.SHOW);
        }
        return policy;
    }

    private static CustomModuleInstance.VisibilityPolicy widgetVisibility(boolean hideInCompact) {
        final CustomModuleInstance.VisibilityPolicy policy = alwaysVisible();
        if (hideInCompact) {
            policy.set(CustomScreenLayout.VariantFamily.COMPACT, CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE);
        }
        return policy;
    }

    private static CustomModuleInstance.VisibilityPolicy heroVisibility(boolean showTall) {
        final CustomModuleInstance.VisibilityPolicy policy = alwaysVisible();
        policy.set(CustomScreenLayout.VariantFamily.COMPACT, CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE);
        if (!showTall) {
            policy.set(CustomScreenLayout.VariantFamily.TALL, CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE);
        }
        return policy;
    }

    private static List<CustomModuleBinding> defaultBindings(CustomScreenModuleType type) {
        return switch (type) {
            case STATUS_BADGE -> List.of(semanticBinding(CustomModuleBinding.STATUS_METRIC, "status", "Network"));
            case BIG_VALUE -> List.of(semanticBinding(CustomModuleBinding.PRIMARY_METRIC, "throughput", "Throughput"));
            case ANALOG_GAUGE -> List.of(semanticBinding(CustomModuleBinding.PRIMARY_METRIC, "rpm", "RPM"));
            case STATUS_STRIP -> List.of(semanticBinding(CustomModuleBinding.STATUS_METRIC, "status", "Plant"));
            case DUAL_CARD -> List.of(
                    semanticBinding(CustomModuleBinding.PRIMARY_METRIC, "stress", "Load"),
                    semanticBinding(CustomModuleBinding.SECONDARY_METRIC, "capacity", "Capacity"));
            case TRIPLE_CARD -> List.of(
                    semanticBinding(CustomModuleBinding.PRIMARY_METRIC, "input", "Input"),
                    semanticBinding(CustomModuleBinding.SECONDARY_METRIC, "output", "Output"),
                    semanticBinding(CustomModuleBinding.STATUS_METRIC, "status", "State"));
            case CHANNEL_LIST -> List.of(explicitBinding(CustomModuleBinding.PRIMARY_SOURCE, "primary_source"));
            case AERO_FLIGHT_DECK -> List.of(explicitBinding(CustomModuleBinding.PRIMARY_SOURCE, "", "Aero Flight Deck"));
            case AEROLINE_DIRECTOR -> List.of(explicitBinding(CustomModuleBinding.PRIMARY_SOURCE, "", "Aeroline Director"));
            case DIESEL_ENGINE_PAGE -> List.of(explicitBinding(CustomModuleBinding.PRIMARY_SOURCE, "", "Diesel Engine"));
            case BATTLE_SCREEN_COMPOSITION -> List.of(explicitBinding(CustomModuleBinding.PRIMARY_SOURCE, "", "Battle Bridge"));
            case BATTLE_TACTICAL_V2 -> List.of(explicitBinding(CustomModuleBinding.PRIMARY_SOURCE, "", "Battle Bridge"));
            default -> List.of();
        };
    }

    private static CompoundTag defaultConfig(CustomScreenModuleType type) {
        final CompoundTag config = new CompoundTag();
        config.putInt("configVersion", 1);
        switch (type) {
            case TITLE -> config.putString("text", "Stats Display");
            case SUBTITLE -> config.putString("text", "Custom layout draft");
            case STATUS_BADGE -> config.putString("text", "Connected");
            case LOGO -> config.putString("text", "SD");
            case BIG_VALUE -> {
                config.putString("label", "Throughput");
                config.putString("value", "432");
                config.putString("suffix", "su");
            }
            case ANALOG_GAUGE -> {
                config.putString("label", "RPM");
                config.putString("value", "78%");
            }
            case STATUS_STRIP -> {
                config.putString("left", "Live");
                config.putString("center", "Nominal");
                config.putString("right", "Stable");
            }
            case DUAL_CARD -> {
                config.putString("leftLabel", "Used");
                config.putString("leftValue", "320");
                config.putString("rightLabel", "Cap");
                config.putString("rightValue", "512");
            }
            case TRIPLE_CARD -> {
                config.putString("aLabel", "Input");
                config.putString("aValue", "128");
                config.putString("bLabel", "Output");
                config.putString("bValue", "119");
                config.putString("cLabel", "State");
                config.putString("cValue", "Good");
            }
            case CHANNEL_LIST -> config.putString("title", "Channels");
            case AERO_FLIGHT_DECK -> {
                config.putString("title", "Aero Flight Deck");
                config.putString("subtitle", "Hero macro scene");
            }
            case AEROLINE_DIRECTOR -> {
                config.putString("title", "Aeroline Director");
                config.putString("subtitle", "Hero macro scene");
            }
            case DIESEL_ENGINE_PAGE -> {
                config.putString("title", "Diesel Engine");
                config.putString("subtitle", "Macro page");
            }
            case BATTLE_SCREEN_COMPOSITION -> {
                config.putString("title", "Battle Bridge");
                config.putString("subtitle", "Macro composition");
                config.putString("boardPreset", "STRATEGIC_OVERVIEW");
            }
            case BATTLE_TACTICAL_V2 -> {
                config.putString("title", "Battle Tactical V2");
                config.putString("subtitle", "Tactical console");
            }
            default -> {
            }
        }
        return config;
    }

    private static CustomModuleBinding semanticBinding(String key, String slot, String label) {
        final CustomModuleBinding binding = new CustomModuleBinding();
        binding.setKey(key);
        binding.setMode(CustomModuleBinding.Mode.SEMANTIC_SLOT);
        binding.setSlot(slot);
        binding.setMetricLabel(label);
        binding.normalize();
        return binding;
    }

    private static CustomModuleBinding explicitBinding(String key, String sourceId) {
        return explicitBinding(key, sourceId, "");
    }

    private static CustomModuleBinding explicitBinding(String key, String sourceId, String sourceTypeFilter) {
        final CustomModuleBinding binding = new CustomModuleBinding();
        binding.setKey(key);
        binding.setMode(CustomModuleBinding.Mode.EXPLICIT_SOURCE);
        binding.setSourceId(sourceId);
        binding.setSourceTypeFilter(sourceTypeFilter);
        binding.normalize();
        return binding;
    }
}
