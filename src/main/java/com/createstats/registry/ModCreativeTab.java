package com.createstats.registry;

import com.createstats.CreateStats;
import com.createstats.baking.CakeFlavor;
import com.createstats.block.DoorMaterial;
import com.createstats.block.ShutterTier;
import com.createstats.security.SensorKind;
import com.createstats.upgrade.UpgradeDegree;
import com.createstats.upgrade.UpgradeKind;
import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ModCreativeTab
{
    private static final DeferredRegister<CreativeModeTab> TABS;
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATE_STATS_TAB;

    public record Section(String key, List<List<Supplier<Item>>> rows) {
        public Component title() {
            return Component.translatable("itemGroup.create_stats." + this.key);
        }
    }

    public record CatalogGrid(List<ItemStack> stacks, CatalogLayout layout, List<Section> sections) {
    }

    @SafeVarargs
    private static List<Supplier<Item>> row(Supplier<Item>... items) {
        return List.of(items);
    }

    @SafeVarargs
    private static Section section(String key, List<Supplier<Item>>... rows) {
        return new Section(key, List.of(rows));
    }

    private static final List<Section> SECTIONS = List.of(

        section("stats",
            row(ModBlocks.STATS_DISPLAY_PANEL::asItem, ModBlocks.STATS_DISPLAY_MK2::asItem,
                ModBlocks.STATS_DISPLAY_MK3::asItem, ModBlocks.STATS_DISPLAY_MK3_CONSOLE::asItem,
                ModBlocks.MINI_DISPLAY::asItem, ModBlocks.DATA_DIAL::asItem, ModBlocks.EINK_PANEL::asItem,
                ModBlocks.WEATHER_PANEL::asItem, ModBlocks.PRODUCTION_DETECTOR::asItem),
            row(ModBlocks.HOLOGRAPHIC_PROJECTOR::asItem, ModBlocks.HOLOGRAPHIC_PROJECTOR_MK3::asItem,
                ModBlocks.BATTLE_SCREEN_DISPLAY::asItem, ModBlocks.DAMAGE_OBSERVER::asItem,
                ModBlocks.CALCULATOR::asItem),
            row(ModBlocks.TOPOLOGY_SCANNER::asItem, ModBlocks.ADVANCED_TOPOLOGY_SCANNER::asItem,
                ModBlocks.INDUSTRIAL_TOPOLOGY_SCANNER::asItem, ModBlocks.TOPOLOGY_SCANNER_PROBE::asItem,
                ModBlocks.ADVANCED_TOPOLOGY_SCANNER_PROBE::asItem,
                ModBlocks.INDUSTRIAL_TOPOLOGY_SCANNER_PROBE::asItem, ModItems.KINETIC_MULTIMETER::get,
                ModBlocks.FLOW_METER::asItem),
            row(ModBlocks.KLAXON_ALARM::asItem, ModBlocks.SENTRY_ALARM::asItem,
                ModBlocks.STATUS_TOWER_LIGHT::asItem, ModBlocks.TABLE_ALARM_CLOCK::asItem,
                ModBlocks.ANNOUNCEMENT_COMPUTER::asItem, ModBlocks.PA_SPEAKER::asItem,
                ModBlocks.PA_MEGAPHONE::asItem),
            row(ModItems.STUDENTS_MANUAL::get, ModItems.STATS_CONFIGURATOR::get, ModItems.STATS_HANDHELD::get,
                ModItems.SMALL_SIGNAL_ANTENNA::get, ModItems.ADVANCED_SIGNAL_ANTENNA::get,
                ModItems.TRT_SIGNAL_ANTENNA::get)
        ),

        section("power",
            row(ModBlocks.OPEN_FRAME_DYNAMO::asItem, ModBlocks.KINETIC_DYNAMO::asItem,
                ModBlocks.INDUSTRIAL_ALTERNATOR::asItem, ModBlocks.BIOFUEL_BURNER::asItem,
                ModItems.COMPACTED_BIOFUEL::get, ModBlocks.SOLAR_PANEL::asItem,
                ModBlocks.REINFORCED_SOLAR_PANEL::asItem, ModBlocks.PHOTOVOLTAIC_ARRAY::asItem),
            row(ModBlocks.STARTER_CAPACITOR::asItem, ModBlocks.CAPACITOR_BANK::asItem,
                ModBlocks.FE_BATTERY_CONTROLLER::asItem, ModBlocks.PORTABLE_POWER_INTERFACE::asItem),

            row(ModBlocks.POWER_TRACK::asItem, ModBlocks.TRACK_FEEDER::asItem,
                ModBlocks.SECTION_INSULATOR::asItem, ModBlocks.CURRENT_COLLECTOR::asItem),
            row(ModBlocks.SURFACE_CABLE::asItem, ModBlocks.CABLE_TRAY::asItem, ModBlocks.BUS_DUCT::asItem,
                ModBlocks.BUSBAR_TRUNK::asItem, ModBlocks.INDOOR_CABLE::asItem, ModItems.CABLE_BORE::get),
            row(ModBlocks.POWER_CONNECTOR::asItem, ModBlocks.ADVANCED_POWER_CONNECTOR::asItem,
                ModBlocks.INDUSTRIAL_POWER_CONNECTOR::asItem, ModItems.COPPER_WIRE::get,
                ModItems.INSULATED_WIRE::get, ModItems.SHIELDED_WIRE::get, ModItems.HV_LINE::get),
            row(ModBlocks.WIRE_TERMINAL_JUNCTION::asItem, ModBlocks.CABLE_DIAGNOSTIC_JUNCTION::asItem,
                ModBlocks.POWER_OUTLET::asItem, ModBlocks.DUPLEX_POWER_OUTLET::asItem,
                ModItems.APPLIANCE_CORD::get),
            row(ModBlocks.POWER_DISTRIBUTION_UNIT::asItem, ModBlocks.PDU_CABINET::asItem, ModBlocks.POWER_METER::asItem,
                ModBlocks.POWER_LIMITER::asItem, ModBlocks.UNIVERSAL_POWER_COUPLER::asItem,
                ModBlocks.MAIN_BREAKER::asItem, ModBlocks.CREATIVE_MAIN_BREAKER::asItem),

            row(ModBlocks.STORM_COIL::asItem),

            row(ModBlocks.SPARK_CROWN::asItem),
            row(ModBlocks.ROCKER_SWITCH::asItem, ModBlocks.TOGGLE_SWITCH::asItem,
                ModBlocks.DOUBLE_SWITCH::asItem, ModBlocks.ISOLATOR_SWITCH::asItem,
                ModBlocks.PUSH_BUTTON::asItem, ModBlocks.ROTARY_DIMMER_DIAL::asItem,
                ModBlocks.ROTARY_DIMMER_PUCK::asItem, ModBlocks.TIME_SWITCH::asItem,
                ModBlocks.CONTACTOR::asItem, ModItems.SWITCH_LINK_CARD::get),
            row(ModItems.REFINED_COPPER::get, ModItems.REFINED_COPPER_SHEET::get,
                ModItems.UNFIRED_INSULATOR::get, ModItems.PORCELAIN_INSULATOR::get,
                ModItems.INCOMPLETE_SHIELDED_WIRE::get, ModItems.INCOMPLETE_HV_LINE::get)
        ),

        section("machines",
            row(ModBlocks.FE_MOTOR_MK1::asItem, ModBlocks.FE_MOTOR_MK2::asItem,
                ModBlocks.FLUID_PUMP_MK1::asItem, ModBlocks.FLUID_PUMP_MK2::asItem),
            row(ModBlocks.INDUCTION_RANGE::asItem, ModBlocks.INDUCTION_HEATER::asItem,
                ModItems.INDUCTION_COIL::get, ModBlocks.INDUCTION_SMELTER::asItem,
                ModBlocks.ARC_FURNACE::asItem, ModItems.SLAG::get, ModBlocks.BULK_WASHER::asItem),
            row(ModBlocks.VACUUM_COLLECTOR::asItem, ModBlocks.VACUUM_CLEANER::asItem,
                ModBlocks.VACUUM_SNIFFER::asItem, ModBlocks.SEISMIC_SCANNER::asItem),
            row(ModBlocks.SAFETY_CLUTCH::asItem, ModBlocks.AUTO_TRANSMISSION_CLUTCH::asItem,
                ModBlocks.CONTROL_CLUTCH::asItem, ModBlocks.SMART_GEARSHIFT::asItem,
                ModBlocks.LOAD_BALANCER::asItem, ModBlocks.KINETIC_FLYWHEEL::asItem),
            row(ModBlocks.FACTORY_LOGIC_CONTROLLER::asItem, ModItems.MACHINE_SELECTION_CARD::get,
                ModItems.SAFETY_CLUTCH_LINK_CARD::get, ModBlocks.OVERFLOW_GATE::asItem)
        ),

        section("transport",
            row(ModBlocks.VCC_MK4::asItem, ModBlocks.DRIVER_HELMET::asItem,
                ModBlocks.VEHICLE_LIGHT_WHITE::asItem, ModBlocks.VEHICLE_LIGHT_RED::asItem,
                ModBlocks.VEHICLE_LIGHT_AMBER::asItem),
            row(ModBlocks.VCC_MK4_AERIAL::asItem, ModBlocks.VCC_MK4_AERIAL_DECK::asItem, ModBlocks.FLIGHT_CONTROL_COMPUTER::asItem,
                ModBlocks.RPM_GOVERNOR::asItem, ModItems.PROPELLER_CARD::get, ModItems.GOVERNOR_CARD::get,
                ModBlocks.AVIATOR_HELMET::asItem, ModItems.PILOT_HELMET::get),
            row(ModItems.SURVEYOR_STAFF::get, ModBlocks.PATHFINDER_COMPUTER::asItem,
                ModBlocks.PATH_SCANNER::asItem, ModBlocks.CINE_CAMERA::asItem),
            row(ModBlocks.FREIGHT_MANIFEST_CLIPBOARD::asItem, ModBlocks.DISPATCH_BOARD::asItem,
                ModItems.CONDUCTORS_LENS::get),
            row(ModBlocks.VCC_MK_II::asItem, ModBlocks.VCC_MK3::asItem, ModItems.HINGE::get)
        ),

        homeSection(),
        securitySection()
    );

    private static Section homeSection() {
        final List<Supplier<Item>> molds = new ArrayList<>();
        molds.add(ModBlocks.OVEN::asItem);
        molds.add(ModItems.CAKE_MOLD::get);
        final List<Supplier<Item>> cakes = new ArrayList<>();
        for (CakeFlavor flavour : CakeFlavor.values()) {
            molds.add(ModItems.filledCakeMold(flavour)::get);
            cakes.add(() -> ModBlocks.cake(flavour).asItem());
        }
        return new Section("home", List.of(
            row(ModBlocks.CAGED_BULKHEAD_LAMP::asItem, ModBlocks.HIGH_BAY_LAMP::asItem,
                ModBlocks.FLOOD_LAMP::asItem, ModBlocks.VAPOUR_TIGHT_LAMP::asItem,
                ModBlocks.WELL_GLASS_LAMP::asItem, ModBlocks.EMERGENCY_LAMP::asItem,
                ModBlocks.STRIP_LAMP::asItem),
            row(ModBlocks.PANEL_LAMP::asItem, ModBlocks.DOME_LAMP::asItem, ModBlocks.WALL_SCONCE::asItem,
                ModBlocks.PENDANT_LAMP::asItem, ModBlocks.FILAMENT_BULB::asItem,
                ModBlocks.BANKERS_LAMP::asItem, ModBlocks.STAGE_LIGHT::asItem,
                ModBlocks.STAGE_LIGHT_MK2::asItem, ModBlocks.SMART_HOME_PANEL::asItem),
            row(ModBlocks.FLOOR_GRATING::asItem, ModBlocks.HAZARD_TRIM::asItem,
                ModBlocks.GUARD_RAILING::asItem, ModBlocks.CONDUIT_JUNCTION_BOX::asItem,
                ModBlocks.BLANK_MONITOR_BEZEL::asItem),
            row(ModBlocks.COFFEE_MACHINE::asItem, ModBlocks.CERAMIC_MUG::asItem, ModBlocks.ENAMEL_MUG::asItem,
                ModBlocks.STEEL_MUG::asItem, ModBlocks.WATER_COOLER::asItem),
            row(ModBlocks.TOASTER::asItem, ModItems.TOAST::get, ModBlocks.BREAD_SLICER::asItem,
                ModItems.BREAD_SLICE::get, ModBlocks.AIR_FRYER::asItem),
            row(ModItems.BLUEBERRY::get, ModItems.STRAWBERRY::get, ModItems.CINNAMON::get,
                ModItems.VANILLA_BEAN::get),
            List.copyOf(molds),
            List.copyOf(cakes)));
    }

    private static Section securitySection() {
        final List<Supplier<Item>> panel = new ArrayList<>();
        panel.add(ModBlocks.HOME_SECURITY_PANEL::asItem);
        for (SensorKind kind : SensorKind.all()) {
            panel.add(() -> ModBlocks.securitySensor(kind).asItem());
        }
        panel.add(ModBlocks.MOB_REPELLER::asItem);
        panel.add(ModBlocks.SECURITY_RELAY::asItem);
        final List<Supplier<Item>> openings = new ArrayList<>();
        for (ShutterTier tier : ShutterTier.all()) {
            openings.add(() -> ModBlocks.blastShutter(tier).asItem());
        }
        openings.add(ModBlocks.GARAGE_DOOR_HEADER::asItem);
        final List<Supplier<Item>> locker = new ArrayList<>();
        locker.add(ModBlocks.SECURITY_LOCKER::asItem);
        for (UpgradeKind kind : UpgradeKind.values()) {
            for (UpgradeDegree degree : UpgradeDegree.values()) {
                locker.add(() -> ModItems.storageUpgrade(kind, degree).get());
            }
        }
        return new Section("security", List.of(
            List.copyOf(panel),
            doorRow(DoorMaterial.IRON, DoorMaterial.REINFORCED, DoorMaterial.INDUSTRIAL_IRON,
                DoorMaterial.ANDESITE, DoorMaterial.BRASS, DoorMaterial.COPPER, DoorMaterial.ZINC),
            doorRow(DoorMaterial.OAK, DoorMaterial.SPRUCE, DoorMaterial.BIRCH, DoorMaterial.JUNGLE,
                DoorMaterial.ACACIA, DoorMaterial.DARK_OAK),
            doorRow(DoorMaterial.QUARTZ, DoorMaterial.ONYX, DoorMaterial.SALON, DoorMaterial.CRYSTAL,
                DoorMaterial.ATRIUM),
            List.copyOf(openings),
            List.copyOf(locker)));
    }

    private static List<Supplier<Item>> doorRow(DoorMaterial... materials) {
        final List<Supplier<Item>> row = new ArrayList<>();
        for (DoorMaterial material : materials) {
            row.add(() -> ModBlocks.poweredDoor(material).asItem());
        }
        return List.copyOf(row);
    }

    public static List<Section> catalogSections() {
        return SECTIONS;
    }

    public static List<ItemStack> itemsForSection(Section section) {
        final Set<Item> hidden = hiddenExperimental();
        final List<ItemStack> result = new ArrayList<>();
        for (List<Supplier<Item>> row : section.rows()) {
            addVisible(result, row, hidden);
        }
        return result;
    }

    public static CatalogGrid catalogGrid() {
        final Set<Item> hidden = hiddenExperimental();
        final List<ItemStack> stacks = new ArrayList<>();
        final int[] sectionRows = new int[SECTIONS.size()];
        for (int i = 0; i < SECTIONS.size(); i++) {
            final List<List<ItemStack>> families = new ArrayList<>();
            for (List<Supplier<Item>> row : SECTIONS.get(i).rows()) {
                final List<ItemStack> family = new ArrayList<>();
                addVisible(family, row, hidden);
                families.add(family);
            }
            final List<ItemStack> padded = CatalogRows.pad(families, ItemStack.EMPTY);
            sectionRows[i] = padded.size() / CatalogRows.COLUMNS;
            if (sectionRows[i] > 0) {
                for (int c = 0; c < CatalogRows.COLUMNS; c++) {
                    stacks.add(ItemStack.EMPTY);
                }
                stacks.addAll(padded);
            }
        }
        return new CatalogGrid(stacks, new CatalogLayout(sectionRows), SECTIONS);
    }

    private static void addVisible(List<ItemStack> out, List<Supplier<Item>> row, Set<Item> hidden) {
        for (Supplier<Item> entry : row) {
            final Item item = entry.get();
            if (item != Items.AIR && !hidden.contains(item)) {
                out.add(new ItemStack(item));
            }
        }
    }

    public static void register(IEventBus modEventBus) {
        ModCreativeTab.TABS.register(modEventBus);
    }

    private static Set<Item> hiddenExperimental() {
        boolean experimental;
        try {
            experimental = com.createstats.ModConfiguration.COMMON.showExperimentalBlocks.get();
        } catch (RuntimeException ignored) {
            experimental = false;
        }

        if (experimental) {
            return Set.of(ModItems.SUPPORT_TERMINAL.get());
        }

        return Set.of(ModBlocks.VCC_MK_II.get().asItem(), ModBlocks.VCC_MK3.get().asItem(),
            ModBlocks.STATS_DISPLAY_MK2.get().asItem(), ModBlocks.HOLOGRAPHIC_PROJECTOR_MK3.get().asItem(),
            ModItems.HINGE.get(), ModItems.SUPPORT_TERMINAL.get(), ModItems.CONDUCTORS_LENS.get());
    }

    private static CreativeModeTab buildTab() {
        return CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_stats"))
            .icon(() -> ModBlocks.STATS_DISPLAY_PANEL.asStack())
            .displayItems((params, output) -> {
                final Set<Item> hidden = hiddenExperimental();
                final Set<Item> added = new LinkedHashSet<>();

                for (Section section : SECTIONS) {
                    for (List<Supplier<Item>> row : section.rows()) {
                        for (Supplier<Item> entry : row) {
                            final Item item = entry.get();
                            if (item != Items.AIR && !hidden.contains(item)) {
                                added.add(item);
                            }
                        }
                    }
                }

                final CreateRegistrate registrate = CreateStats.registrate();
                registrate.getAll(Registries.BLOCK).forEach(entry -> {
                    if (CreateRegistrate.isInCreativeTab(entry, CREATE_STATS_TAB)) {
                        final Item item = ((Block) entry.get()).asItem();
                        if (item != Items.AIR && !hidden.contains(item)) {
                            added.add(item);
                        }
                    }
                });
                registrate.getAll(Registries.ITEM).forEach(entry -> {
                    if (CreateRegistrate.isInCreativeTab(entry, CREATE_STATS_TAB)) {
                        final Item item = (Item) entry.get();
                        if (!hidden.contains(item)) {
                            added.add(item);
                        }
                    }
                });

                added.forEach(item -> output.accept(new ItemStack(item)));
            })
            .build();
    }

    static {
        TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "create_stats");
        CREATE_STATS_TAB = ModCreativeTab.TABS.register("create_stats_tab", ModCreativeTab::buildTab);
    }
}
