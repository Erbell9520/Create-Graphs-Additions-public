package com.createstats.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem;
import com.createstats.antenna.AntennaTier;
import com.createstats.CreateStats;
import com.createstats.flight.item.GovernorCardItem;
import com.createstats.flight.item.PropellerCardItem;
import com.createstats.item.FactoryManualItem;
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
    public static final ItemEntry<FactoryManualItem> FACTORY_MANUAL;
    public static final ItemEntry<MachineSelectionCardItem> MACHINE_SELECTION_CARD;
    public static final ItemEntry<SafetyClutchLinkCardItem> SAFETY_CLUTCH_LINK_CARD;
    public static final ItemEntry<KineticMultimeterItem> KINETIC_MULTIMETER;
    public static final ItemEntry<PropellerCardItem> PROPELLER_CARD;
    public static final ItemEntry<GovernorCardItem> GOVERNOR_CARD;
    public static final ItemEntry<SignalAntennaItem> SMALL_SIGNAL_ANTENNA;
    public static final ItemEntry<SignalAntennaItem> ADVANCED_SIGNAL_ANTENNA;
    public static final ItemEntry<SignalAntennaItem> TRT_SIGNAL_ANTENNA;
    public static final ItemEntry<HingeItem> HINGE;
    public static final ItemEntry<InductionCoilItem> INDUCTION_COIL;
    public static final ItemEntry<com.createstats.item.SwitchLinkCardItem> SWITCH_LINK_CARD;
    public static final ItemEntry<com.createstats.item.CopperWireItem> COPPER_WIRE;
    public static final ItemEntry<com.createstats.item.CopperWireItem> INSULATED_WIRE;
    public static final ItemEntry<com.createstats.item.CopperWireItem> SHIELDED_WIRE;
    
    public static void register() {
        CreateStats.LOGGER.debug("Registering items for {}", "create_stats");
    }

    /** [1] */
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
        FACTORY_MANUAL = CreateStats.registrate().item("factory_manual", FactoryManualItem::new).properties(p -> p.stacksTo(1)).register();
        MACHINE_SELECTION_CARD = CreateStats.registrate().item("machine_selection_card", MachineSelectionCardItem::new).properties(p -> p.stacksTo(1)).register();
        SAFETY_CLUTCH_LINK_CARD = CreateStats.registrate().item("safety_clutch_link_card", SafetyClutchLinkCardItem::new).properties(p -> p.stacksTo(1)).register();
        KINETIC_MULTIMETER = CreateStats.registrate().item("kinetic_multimeter", KineticMultimeterItem::new).properties(p -> p.stacksTo(1)).register();
        PROPELLER_CARD = CreateStats.registrate().item("propeller_card", PropellerCardItem::new).properties(p -> p.stacksTo(1)).register();
        GOVERNOR_CARD = CreateStats.registrate().item("governor_card", GovernorCardItem::new).properties(p -> p.stacksTo(1)).register();
        SMALL_SIGNAL_ANTENNA = CreateStats.registrate().item("small_signal_antenna", p -> new SignalAntennaItem(AntennaTier.SMALL, p)).properties(p -> p.stacksTo(16)).register();
        ADVANCED_SIGNAL_ANTENNA = CreateStats.registrate().item("advanced_signal_antenna", p -> new SignalAntennaItem(AntennaTier.ADVANCED, p)).properties(p -> p.stacksTo(16)).register();
        TRT_SIGNAL_ANTENNA = CreateStats.registrate().item("trt_signal_antenna", p -> new SignalAntennaItem(AntennaTier.TRT, p)).properties(p -> p.stacksTo(16)).register();
        HINGE = CreateStats.registrate().item("hinge", HingeItem::new).properties(p -> p.stacksTo(16)).register();
        INDUCTION_COIL = CreateStats.registrate().item("induction_coil", InductionCoilItem::new).properties(p -> p.stacksTo(16)).register();
        SWITCH_LINK_CARD = CreateStats.registrate().item("switch_link_card", com.createstats.item.SwitchLinkCardItem::new).properties(p -> p.stacksTo(1)).register();
        COPPER_WIRE = CreateStats.registrate().item("copper_wire", com.createstats.item.CopperWireItem::new).properties(p -> p.stacksTo(64)).register();
        INSULATED_WIRE = CreateStats.registrate().item("insulated_wire", p -> new com.createstats.item.CopperWireItem(p, com.createstats.power.WireTier.INSULATED)).properties(p -> p.stacksTo(64)).register();
        SHIELDED_WIRE = CreateStats.registrate().item("shielded_wire", p -> new com.createstats.item.CopperWireItem(p, com.createstats.power.WireTier.SHIELDED)).properties(p -> p.stacksTo(64)).register();
    }

    /** The item that strings a given grade of wire. Used to refund the right wire when stripped. */
    public static net.minecraft.world.item.Item wireItem(com.createstats.power.WireTier tier) {
        return switch (tier) {
            case INSULATED -> INSULATED_WIRE.get();
            case SHIELDED -> SHIELDED_WIRE.get();
            default -> COPPER_WIRE.get();
        };
    }
}
