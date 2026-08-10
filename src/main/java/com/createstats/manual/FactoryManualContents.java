package com.createstats.manual;

import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class FactoryManualContents {
    private static final List<FactoryManualPage> PAGES = List.of(
            new FactoryManualPage(
                    "intro",
                    ModItems.FACTORY_MANUAL::asStack,
                    () -> Component.translatable("item.create_stats.factory_manual"),
                    "create_stats.manual.page.intro.body"
            ),
            blockPage("stats_display_panel", ModBlocks.STATS_DISPLAY_PANEL::asStack),
            itemPage("stats_configurator", ModItems.STATS_CONFIGURATOR::asStack),
            itemPage("stats_handheld", ModItems.STATS_HANDHELD::asStack),
            itemPage("memory_cog", ModItems.MEMORY_COG::asStack),
            namedPage("signal_antennas", ModItems.SMALL_SIGNAL_ANTENNA::asStack),
            namedPage("aero_stats_link", ModBlocks.STATS_DISPLAY_PANEL::asStack),
            namedPage("aero_source_matrix", ModBlocks.DATA_DIAL::asStack),
            namedPage("contraption_info", ModBlocks.STATS_DISPLAY_PANEL::asStack),
            namedPage("aero_flight_deck", ModBlocks.HOLOGRAPHIC_PROJECTOR::asStack),
            namedPage("aeroline_director", ModBlocks.HOLOGRAPHIC_PROJECTOR::asStack),
            namedPage("hot_air_burner_telemetry", ModBlocks.STATUS_TOWER_LIGHT::asStack),
            blockPage("calculator", ModBlocks.CALCULATOR::asStack),
            blockPage("stats_recorder", ModBlocks.STATS_RECORDER::asStack),
            blockPage("production_detector", ModBlocks.PRODUCTION_DETECTOR::asStack),
            blockPage("flow_meter", ModBlocks.FLOW_METER::asStack),
            blockPage("stats_alarm", ModBlocks.STATS_ALARM::asStack),
            blockPage("table_alarm_clock", ModBlocks.TABLE_ALARM_CLOCK::asStack),
            blockPage("status_tower_light", ModBlocks.STATUS_TOWER_LIGHT::asStack),
            blockPage("data_dial", ModBlocks.DATA_DIAL::asStack),
            blockPage("factory_logic_controller", ModBlocks.FACTORY_LOGIC_CONTROLLER::asStack),
            itemPage("machine_selection_card", ModItems.MACHINE_SELECTION_CARD::asStack),
            itemPage("safety_clutch_link_card", ModItems.SAFETY_CLUTCH_LINK_CARD::asStack),
            blockPage("mini_display", ModBlocks.MINI_DISPLAY::asStack),
            blockPage("holographic_projector", ModBlocks.HOLOGRAPHIC_PROJECTOR::asStack),
            blockPage("safety_clutch", ModBlocks.SAFETY_CLUTCH::asStack),
            blockPage("auto_transmission_clutch", ModBlocks.AUTO_TRANSMISSION_CLUTCH::asStack),
            blockPage("control_clutch", ModBlocks.CONTROL_CLUTCH::asStack),
            blockPage("load_balancer", ModBlocks.LOAD_BALANCER::asStack),
            blockPage("smart_gearshift", ModBlocks.SMART_GEARSHIFT::asStack),
            blockPage("overflow_gate", ModBlocks.OVERFLOW_GATE::asStack),
            blockPage("kinetic_flywheel", ModBlocks.KINETIC_FLYWHEEL::asStack),
            blockPage("freight_manifest_clipboard", ModBlocks.FREIGHT_MANIFEST_CLIPBOARD::asStack),
            blockPage("dispatch_board", ModBlocks.DISPATCH_BOARD::asStack),
                blockPage("rpm_governor", ModBlocks.RPM_GOVERNOR::asStack),
            blockPage("flight_control_computer", ModBlocks.FLIGHT_CONTROL_COMPUTER::asStack),
                blockPage("damage_observer", ModBlocks.DAMAGE_OBSERVER::asStack),
                blockPage("battle_screen_display", ModBlocks.BATTLE_SCREEN_DISPLAY::asStack),
                blockPage("path_scanner", ModBlocks.PATH_SCANNER::asStack),
                blockPage("pathfinder_computer", ModBlocks.PATHFINDER_COMPUTER::asStack),
            namedPage("personal_reflection", ModItems.FACTORY_MANUAL::asStack),
            namedPage("player_notes", ModItems.FACTORY_MANUAL::asStack)
    );

    private FactoryManualContents() {
    }

    public static List<FactoryManualPage> pages() {
        return PAGES;
    }

    private static FactoryManualPage blockPage(String id, Supplier<ItemStack> iconSupplier) {
        return new FactoryManualPage(
                id,
                iconSupplier,
                () -> iconSupplier.get().getHoverName(),
                "create_stats.manual.page." + id + ".body"
        );
    }

    private static FactoryManualPage itemPage(String id, Supplier<ItemStack> iconSupplier) {
        return new FactoryManualPage(
                id,
                iconSupplier,
                () -> iconSupplier.get().getHoverName(),
                "create_stats.manual.page." + id + ".body"
        );
    }

    private static FactoryManualPage namedPage(String id, Supplier<ItemStack> iconSupplier) {
        return new FactoryManualPage(
                id,
                iconSupplier,
                () -> Component.translatable("create_stats.manual.page." + id + ".title"),
                "create_stats.manual.page." + id + ".body"
        );
    }
}
