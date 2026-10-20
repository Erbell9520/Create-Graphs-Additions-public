package com.createstats.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import com.createstats.antenna.AntennaTier;
import com.createstats.CreateStats;
import com.createstats.book.StudentsManualItem;
import com.createstats.flight.item.GovernorCardItem;
import com.createstats.flight.item.PropellerCardItem;
import com.createstats.item.ConductorsLensItem;
import com.createstats.item.HingeItem;
import com.createstats.item.InductionCoilItem;
import com.createstats.item.KineticMultimeterItem;
import com.createstats.item.MachineSelectionCardItem;
import com.createstats.item.MemoryCogItem;
import com.createstats.item.PilotHelmetItem;
import com.createstats.item.SafetyClutchLinkCardItem;
import com.createstats.item.SignalAntennaItem;
import com.createstats.item.StatsHandheldItem;
import com.createstats.item.StatsConfiguratorItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.neoforged.fml.ModList;

public class ModItems
{
    public static final ItemEntry<StatsConfiguratorItem> STATS_CONFIGURATOR;
    public static final ItemEntry<StatsHandheldItem> STATS_HANDHELD;
    public static final ItemEntry<PilotHelmetItem> PILOT_HELMET;
    public static final ItemEntry<MemoryCogItem> MEMORY_COG;

    public static final ItemEntry<StudentsManualItem> STUDENTS_MANUAL;
    public static final ItemEntry<MachineSelectionCardItem> MACHINE_SELECTION_CARD;
    public static final ItemEntry<SafetyClutchLinkCardItem> SAFETY_CLUTCH_LINK_CARD;
    public static final ItemEntry<KineticMultimeterItem> KINETIC_MULTIMETER;
    public static final ItemEntry<ConductorsLensItem> CONDUCTORS_LENS;

    public static final ItemEntry<com.createstats.item.CableBoreItem> CABLE_BORE;
    public static final ItemEntry<com.createstats.item.ApplianceCordItem> APPLIANCE_CORD;

    public static final ItemEntry<com.createstats.item.SuctionWandItem> SUCTION_WAND;
    public static final ItemEntry<PropellerCardItem> PROPELLER_CARD;
    public static final ItemEntry<GovernorCardItem> GOVERNOR_CARD;
    public static final ItemEntry<SignalAntennaItem> SMALL_SIGNAL_ANTENNA;
    public static final ItemEntry<SignalAntennaItem> ADVANCED_SIGNAL_ANTENNA;
    public static final ItemEntry<SignalAntennaItem> TRT_SIGNAL_ANTENNA;
    public static final ItemEntry<HingeItem> HINGE;

    public static final ItemEntry<com.createstats.surveyor.SurveyorStaffItem> SURVEYOR_STAFF;
    public static final ItemEntry<InductionCoilItem> INDUCTION_COIL;

    public static final ItemEntry<com.createstats.item.SlagItem> SLAG;

    public static final ItemEntry<com.createstats.item.ToastItem> TOAST;

    public static final ItemEntry<net.minecraft.world.item.Item> BREAD_SLICE;

    public static final ItemEntry<net.minecraft.world.item.ItemNameBlockItem> BLUEBERRY;
    public static final ItemEntry<net.minecraft.world.item.ItemNameBlockItem> CINNAMON;
    public static final ItemEntry<net.minecraft.world.item.ItemNameBlockItem> VANILLA_BEAN;
    public static final ItemEntry<net.minecraft.world.item.ItemNameBlockItem> STRAWBERRY;

    public static final ItemEntry<net.minecraft.world.item.Item> FLOUR;

    public static final ItemEntry<net.minecraft.world.item.Item> COMPACTED_BIOFUEL;

    public static final ItemEntry<net.minecraft.world.item.Item> CAKE_MOLD;

    private static final java.util.Map<com.createstats.baking.CakeFlavor,
            ItemEntry<net.minecraft.world.item.Item>> FILLED_CAKE_MOLDS =
            new java.util.EnumMap<>(com.createstats.baking.CakeFlavor.class);
    public static final ItemEntry<com.createstats.item.SwitchLinkCardItem> SWITCH_LINK_CARD;
    public static final ItemEntry<com.createstats.item.CopperWireItem> COPPER_WIRE;
    public static final ItemEntry<com.createstats.item.CopperWireItem> INSULATED_WIRE;
    public static final ItemEntry<com.createstats.item.CopperWireItem> SHIELDED_WIRE;

    public static final ItemEntry<com.createstats.item.CopperWireItem> HV_LINE;

    public static final ItemEntry<net.minecraft.world.item.Item> REFINED_COPPER;

    public static final ItemEntry<net.minecraft.world.item.Item> REFINED_COPPER_SHEET;

    public static final ItemEntry<net.minecraft.world.item.Item> UNFIRED_INSULATOR;

    public static final ItemEntry<net.minecraft.world.item.Item> PORCELAIN_INSULATOR;

    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem>
            INCOMPLETE_SHIELDED_WIRE;
    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem>
            INCOMPLETE_HV_LINE;

    public static final ItemEntry<com.createstats.support.SupportTerminalItem> SUPPORT_TERMINAL;

    private static final java.util.Map<com.createstats.upgrade.UpgradeKind,
            java.util.Map<com.createstats.upgrade.UpgradeDegree,
                    ItemEntry<com.createstats.item.StorageUpgradeItem>>> STORAGE_UPGRADES =
            new java.util.EnumMap<>(com.createstats.upgrade.UpgradeKind.class);

    public static void register() {
        CreateStats.LOGGER.debug("Registering items for {}", "create_stats");
    }

    // geckolib only referenced inside isLoaded so it never class loads without it
    private static PilotHelmetItem createPilotHelmet(Item.Properties properties) {
        if (ModList.get().isLoaded("geckolib")) {
            return com.createstats.compat.geckolib.GeckolibCompat.createPilotHelmet(
                ModArmorMaterials.PILOT, ArmorItem.Type.HELMET, properties);
        }
        return new PilotHelmetItem(ModArmorMaterials.PILOT, ArmorItem.Type.HELMET, properties);
    }

    static {
        STATS_CONFIGURATOR = CreateStats.registrate().item("stats_configurator", StatsConfiguratorItem::new).properties(p -> p.stacksTo(1)).register();
        STATS_HANDHELD = CreateStats.registrate().item("stats_handheld", StatsHandheldItem::new).properties(p -> p.stacksTo(1)).register();
        PILOT_HELMET = CreateStats.registrate().item("pilot_helmet", p -> createPilotHelmet(p)).properties(p -> p.stacksTo(1).fireResistant()).register();
        MEMORY_COG = CreateStats.registrate().item("memory_cog", MemoryCogItem::new).properties(p -> p.stacksTo(1)).register();
        STUDENTS_MANUAL = CreateStats.registrate().item("students_manual", StudentsManualItem::new).properties(p -> p.stacksTo(1)).register();
        MACHINE_SELECTION_CARD = CreateStats.registrate().item("machine_selection_card", MachineSelectionCardItem::new).properties(p -> p.stacksTo(1)).register();
        SAFETY_CLUTCH_LINK_CARD = CreateStats.registrate().item("safety_clutch_link_card", SafetyClutchLinkCardItem::new).properties(p -> p.stacksTo(1)).register();
        KINETIC_MULTIMETER = CreateStats.registrate().item("kinetic_multimeter", KineticMultimeterItem::new).properties(p -> p.stacksTo(1)).register();
        CONDUCTORS_LENS = CreateStats.registrate().item("conductors_lens", ConductorsLensItem::new).properties(p -> p.stacksTo(1)).register();
        CABLE_BORE = CreateStats.registrate().item("cable_bore", com.createstats.item.CableBoreItem::new).properties(p -> p.stacksTo(1)).register();
        SUCTION_WAND = CreateStats.registrate().item("suction_wand", com.createstats.item.SuctionWandItem::new).properties(p -> p.stacksTo(1)).register();
        APPLIANCE_CORD = CreateStats.registrate().item("appliance_cord", com.createstats.item.ApplianceCordItem::new).properties(p -> p.stacksTo(16)).register();
        PROPELLER_CARD = CreateStats.registrate().item("propeller_card", PropellerCardItem::new).properties(p -> p.stacksTo(1)).register();
        GOVERNOR_CARD = CreateStats.registrate().item("governor_card", GovernorCardItem::new).properties(p -> p.stacksTo(1)).register();
        SMALL_SIGNAL_ANTENNA = CreateStats.registrate().item("small_signal_antenna", p -> new SignalAntennaItem(AntennaTier.SMALL, p)).properties(p -> p.stacksTo(16)).register();
        ADVANCED_SIGNAL_ANTENNA = CreateStats.registrate().item("advanced_signal_antenna", p -> new SignalAntennaItem(AntennaTier.ADVANCED, p)).properties(p -> p.stacksTo(16)).register();
        TRT_SIGNAL_ANTENNA = CreateStats.registrate().item("trt_signal_antenna", p -> new SignalAntennaItem(AntennaTier.TRT, p)).properties(p -> p.stacksTo(16)).register();
        HINGE = CreateStats.registrate().item("hinge", HingeItem::new).properties(p -> p.stacksTo(16)).register();
        SURVEYOR_STAFF = CreateStats.registrate().item("surveyor_staff", com.createstats.surveyor.SurveyorStaffItem::new)
                .properties(p -> p.stacksTo(1)).register();
        INDUCTION_COIL = CreateStats.registrate().item("induction_coil", InductionCoilItem::new).properties(p -> p.stacksTo(16)).register();
        SLAG = CreateStats.registrate().item("slag", com.createstats.item.SlagItem::new).register();
        TOAST = CreateStats.registrate().item("toast", com.createstats.item.ToastItem::new).properties(p -> p.food(com.createstats.item.ToastItem.defaultFood())).register();

        BREAD_SLICE = CreateStats.registrate().item("bread_slice", net.minecraft.world.item.Item::new).properties(p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.6f).fast().build())).register();

        BLUEBERRY = CreateStats.registrate().item("blueberry", p -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.BLUEBERRY_BUSH.get(), p)).properties(p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.1f).build())).register();
        CINNAMON = CreateStats.registrate().item("cinnamon", p -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.CINNAMON_SHRUB.get(), p)).register();
        VANILLA_BEAN = CreateStats.registrate().item("vanilla_bean", p -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.VANILLA_BEAN_VINE.get(), p)).register();
        STRAWBERRY = CreateStats.registrate().item("strawberry", p -> new net.minecraft.world.item.ItemNameBlockItem(ModBlocks.STRAWBERRY_CROP.get(), p)).properties(p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).build())).register();
        FLOUR = CreateStats.registrate().item("flour", net.minecraft.world.item.Item::new).register();
        COMPACTED_BIOFUEL = CreateStats.registrate().item("compacted_biofuel", net.minecraft.world.item.Item::new).register();

        CAKE_MOLD = CreateStats.registrate().item("cake_mold", net.minecraft.world.item.Item::new).properties(p -> p.stacksTo(16)).register();
        for (final com.createstats.baking.CakeFlavor flavour : com.createstats.baking.CakeFlavor.values()) {
            FILLED_CAKE_MOLDS.put(flavour, CreateStats.registrate()
                    .item(flavour.filledMoldName(), net.minecraft.world.item.Item::new)
                    .properties(p -> p.stacksTo(16)).register());
        }
        SWITCH_LINK_CARD = CreateStats.registrate().item("switch_link_card", com.createstats.item.SwitchLinkCardItem::new).properties(p -> p.stacksTo(16)).register();
        COPPER_WIRE = CreateStats.registrate().item("copper_wire", com.createstats.item.CopperWireItem::new).properties(p -> p.stacksTo(64)).register();
        INSULATED_WIRE = CreateStats.registrate().item("insulated_wire", p -> new com.createstats.item.CopperWireItem(p, com.createstats.power.WireTier.INSULATED)).properties(p -> p.stacksTo(64)).register();
        SHIELDED_WIRE = CreateStats.registrate().item("shielded_wire", p -> new com.createstats.item.CopperWireItem(p, com.createstats.power.WireTier.SHIELDED)).properties(p -> p.stacksTo(64)).register();
        HV_LINE = CreateStats.registrate().item("hv_line", p -> new com.createstats.item.CopperWireItem(p, com.createstats.power.WireTier.HV)).properties(p -> p.stacksTo(64)).register();
        REFINED_COPPER = CreateStats.registrate().item("refined_copper", net.minecraft.world.item.Item::new).properties(p -> p.stacksTo(64)).register();
        REFINED_COPPER_SHEET = CreateStats.registrate().item("refined_copper_sheet", net.minecraft.world.item.Item::new).properties(p -> p.stacksTo(64)).register();
        UNFIRED_INSULATOR = CreateStats.registrate().item("unfired_insulator", net.minecraft.world.item.Item::new).properties(p -> p.stacksTo(64)).register();
        PORCELAIN_INSULATOR = CreateStats.registrate().item("porcelain_insulator", net.minecraft.world.item.Item::new).properties(p -> p.stacksTo(64)).register();
        INCOMPLETE_SHIELDED_WIRE = CreateStats.registrate().item("incomplete_shielded_wire", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new).properties(p -> p.stacksTo(64)).register();
        INCOMPLETE_HV_LINE = CreateStats.registrate().item("incomplete_hv_line", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new).properties(p -> p.stacksTo(64)).register();
        SUPPORT_TERMINAL = CreateStats.registrate().item("support_terminal", com.createstats.support.SupportTerminalItem::new).properties(p -> p.stacksTo(1)).register();

        for (final com.createstats.upgrade.UpgradeKind kind : com.createstats.upgrade.UpgradeKind.values()) {
            final java.util.Map<com.createstats.upgrade.UpgradeDegree,
                    ItemEntry<com.createstats.item.StorageUpgradeItem>> byDegree =
                    new java.util.EnumMap<>(com.createstats.upgrade.UpgradeDegree.class);
            for (final com.createstats.upgrade.UpgradeDegree degree : com.createstats.upgrade.UpgradeDegree.values()) {
                final String name = kind.getSerializedName() + "_upgrade_" + degree.getSerializedName();
                byDegree.put(degree, CreateStats.registrate()
                        .item(name, p -> new com.createstats.item.StorageUpgradeItem(kind, degree, p))
                        .properties(p -> p.stacksTo(16)).register());
            }
            STORAGE_UPGRADES.put(kind, byDegree);
        }
    }

    public static ItemEntry<com.createstats.item.StorageUpgradeItem> storageUpgrade(
            com.createstats.upgrade.UpgradeKind kind, com.createstats.upgrade.UpgradeDegree degree) {
        return STORAGE_UPGRADES.get(kind).get(degree);
    }

    public static ItemEntry<net.minecraft.world.item.Item> filledCakeMold(
            com.createstats.baking.CakeFlavor flavour) {
        return FILLED_CAKE_MOLDS.get(flavour);
    }

    public static net.minecraft.world.item.Item wireItem(com.createstats.power.WireTier tier) {
        return switch (tier) {
            case COPPER -> COPPER_WIRE.get();
            case INSULATED -> INSULATED_WIRE.get();
            case SHIELDED -> SHIELDED_WIRE.get();
            case HV -> HV_LINE.get();
        };
    }
}
