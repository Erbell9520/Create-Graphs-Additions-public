package com.createstats.compat.jei;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiFeReadoutCoverageTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/createstats/compat/jei/CreateStatsJeiPlugin.java");

    private static final String[][] REQUIRED = {
            {"create_stats.jei.powered_door.info",
                    "DoorPower.BUFFER", "DoorPower.IDLE_DRAIN", "DoorPower.CYCLE_COST"},
            {"create_stats.jei.garage_door.info",
                    "GarageDoorPower.CYCLE_PER_BLOCK", "GarageDoorPower.IDLE_BLOCKS_PER_FE",
                    "GarageDoorPower.BUFFER_CYCLES"},
            {"create_stats.jei.smart_home_panel.info", "SmartHomePanelBE.DRAIN"},
            {"create_stats.jei.home_security_panel.info", "HomeSecurityPanelBE.DRAIN"},
            {"create_stats.jei.weather_panel.info", "WeatherPanelBE.DRAIN"},
            {"create_stats.jei.biofuel_burner.info",
                    "BiofuelValue.OUTPUT_FE_PER_TICK", "BiofuelBurnerBE.BUFFER"},
            {"create_stats.jei.portable_power_interface.info",
                    "PortableFeLadder.feAt(0,", "PortableFeLadder.maxDial(", "PortablePowerInterfaceBE.ceiling()"},

            {"create_stats.jei.surface_cable.info", "FeRates.cable(", "CableTier.SURFACE"},
            {"create_stats.jei.cable_tray.info", "FeRates.cable(", "CableTier.TRAY"},
            {"create_stats.jei.bus_duct.info", "FeRates.cable(", "CableTier.DUCT"},
            {"create_stats.jei.busbar_trunk.info", "FeRates.cable(", "CableTier.TRUNK"},
            {"create_stats.jei.copper_wire.info", "FeRates.wire(", "WireTier.COPPER", "range"},
            {"create_stats.jei.insulated_wire.info", "FeRates.wire(", "WireTier.INSULATED", "range"},
            {"create_stats.jei.shielded_wire.info", "FeRates.wire(", "WireTier.SHIELDED", "range"},
            {"create_stats.jei.hv_line.info", "FeRates.wire(", "WireTier.HV", "range"},
            {"create_stats.jei.power_connector.info", "FeRates.connector(", "ConnectorTier.POWER.maxLinks()"},
            {"create_stats.jei.advanced_power_connector.info", "FeRates.connector(", "ConnectorTier.ADVANCED.maxLinks()"},
            {"create_stats.jei.industrial_power_connector.info", "FeRates.connector(", "ConnectorTier.INDUSTRIAL.maxLinks()"},
            {"create_stats.jei.refined_copper.info", "FeRates.amount(perIngot)", "ArcFurnaceBE.MIN_TAP"},
    };

    @Test
    void everyFeReadoutKeyCarriesItsConstants() throws IOException {
        String source = Files.readString(SOURCE);
        for (String[] entry : REQUIRED) {
            String key = entry[0];
            String statement = statementFor(source, key);
            for (int i = 1; i < entry.length; i++) {
                assertTrue(statement.contains(entry[i]),
                        "addIngredientInfo(\"" + key + "\") must reference " + entry[i]
                                + " but its statement was:\n" + statement);
            }
        }
    }

    private static final Path FUEL_CATEGORY = Path.of(
            "src/main/java/com/createstats/compat/jei/BiofuelFuelCategory.java");

    @Test
    void theFuelPageAsksTheMachineForEveryDuration() throws IOException {
        String category = codeOnly(Files.readString(FUEL_CATEGORY));
        assertTrue(category.contains("BiofuelFuel::burnTicksOf")
                        || category.contains("BiofuelFuel.burnTicksOf("),
                "the fuel page must price items with BiofuelFuel.burnTicksOf, the burner's own call");
        assertTrue(category.contains("BiofuelFuel.accepts("),
                "the fuel page must list only what BiofuelFuel.accepts admits");
        assertFalse(category.contains("COMPACTED_BURN_TICKS"),
                "the fuel page quotes a constant beside the game's answer, which is KI-47");

        String plugin = codeOnly(Files.readString(SOURCE));
        assertFalse(plugin.contains("COMPACTED_BURN_TICKS"),
                "an info page in the plugin quotes the brick's constant, which is KI-47");
    }

    @Test
    void theFuelPageIsRegisteredCategoryRecipesAndCatalyst() throws IOException {
        String plugin = codeOnly(Files.readString(SOURCE));
        assertTrue(plugin.contains("new BiofuelFuelCategory("),
                "registerCategories never adds BiofuelFuelCategory");
        assertTrue(plugin.contains("addRecipes(BiofuelFuelCategory.TYPE"),
                "registerRecipes never fills BiofuelFuelCategory, so the page would be empty");
        assertTrue(plugin.contains("addRecipeCatalyst(ModBlocks.BIOFUEL_BURNER.get(), BiofuelFuelCategory.TYPE"),
                "the Biofuel Burner is not a catalyst of its own fuel page");
    }

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static String statementFor(String source, String key) {
        Matcher keyMatch = Pattern.compile(Pattern.quote("\"" + key + "\"")).matcher(source);
        assertTrue(keyMatch.find(), "key not found at all: " + key);
        int close = source.indexOf("));", keyMatch.end());
        assertTrue(close >= 0, "no closing '));' after key: " + key);
        return source.substring(keyMatch.start(), close);
    }
}
