package com.createstats.statsmk3.page;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.util.PreviewImages;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachinePageArtTest {
    private static final File OUT = new File("fixture_preview");
    private static final int W = 560;
    private static final int H = 380;

    @Test
    void rendersPowerPage() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(1180, 34 + 3 * (H + 24) + 20);
        final Graphics2D g = sheet.createGraphics();

        place(g, render(healthyGrid(), 0.0f), 0, 0, "Power / charging, t=0.0s");
        place(g, render(healthyGrid(), 0.45f), 1, 0, "Power / charging, t=0.45s");

        place(g, render(drainingGrid(), 0.9f), 0, 1, "Power / draining");

        place(g, render(noBufferGrid(), 0.3f), 1, 1, "Power / no buffer");

        place(g, render(unaccountedGrid(), 0.6f), 0, 2, "Power / unlinked load on the network");

        final RasterSurface cramped = new RasterSurface(W, 190, 0x111318);
        PowerView.paint(cramped, new Rect(12.0f, 12.0f, W - 24.0f, 190.0f - 24.0f),
                healthyGrid(), 0.2f, 420.0f, 62.0f);
        g.drawImage(cramped.finish(), 24 + 570, 34 + 2 * (H + 24), null);
        caption(g, "Power / cramped to 190px", 24 + 570, 34 + 2 * (H + 24) - 8);

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_page_power.png"));
    }

    @Test
    void rendersManagedPowerPage() throws IOException {
        ensureOut();

        final BufferedImage sheet = sheet(2060, 34 + 3 * (H + 24) + 20);
        final Graphics2D g = sheet.createGraphics();

        place(g, render(pduGrid(), 0.0f), 0, 0, "Power / panel shedding two tiers");
        place(g, render(pduGrid(), 0.5f), 1, 0, "Power / panel shedding, t=0.5s");
        place(g, renderWide(pduGrid(), 0.25f), 2, 0, "Power / panel at 860px");

        place(g, render(controllerGrid(), 0.3f), 0, 1, "Power / controller, one output below floor");
        place(g, renderWide(controllerGrid(), 0.3f), 1, 1, "Power / controller at 860px");
        place(g, render(chainedGrid(), 0.7f), 2, 1, "Power / controller feeding a panel");

        place(g, render(ownHardwareGrid(), 0.4f), 0, 2, "Power / dynamos, bank and a grid readout");
        place(g, render(manySourcesGrid(), 0.4f), 1, 2, "Power / eleven dynamos, tail rolled up");

        final RasterSurface cramped = new RasterSurface(W, 200, 0x111318);
        PowerView.paint(cramped, new Rect(12.0f, 12.0f, W - 24.0f, 200.0f - 24.0f),
                pduGrid(), 0.2f, -180.0f, 34.0f);
        g.drawImage(cramped.finish(), 24 + 2 * 570, 34 + 2 * (H + 24), null);
        caption(g, "Power / panel cramped to 200px", 24 + 2 * 570, 34 + 2 * (H + 24) - 8);

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_page_power_managed.png"));
    }

    @Test
    void classifiesAndDeduplicates() {
        final PowerGraph.Grid own = PowerGraph.of(ownHardwareUnits());
        assertEquals(2, own.sources().size(), "connector must not count as a third generator");
        assertEquals(760.0f, own.generated(), 0.5f, "grid aggregate double counted with its members");
        assertEquals(1, own.storage().size());

        final PowerGraph.Grid idle = PowerGraph.of(list(
                unit("Kinetic Dynamo", "Stalled Dynamo", metric("energy_output_rate", 0.0f)),
                unit("Electric Motor", "Idle Motor", metric("energy_input_rate", 0.0f))));
        assertEquals(1, idle.sources().size(), "a dynamo at rest is still a generator");
        assertEquals(1, idle.loads().size(), "a motor at rest is still a load");

        final PowerGraph.Grid managed = PowerGraph.of(pduUnits());
        assertTrue(managed.isManaged());
        assertEquals(4, managed.allCircuits().size());
        assertEquals(1, managed.distributors().size());
        assertEquals(managed.distributors().get(0).served(), managed.consumed(), 0.5f,
                "demand must come from the panel, not from loads counted a second time");
        assertNotNull(managed.fault(), "a panel shedding machines has something to say");

        assertEquals(PowerGraph.Circuit.State.OVER_CAPACITY, managed.allCircuits().get(2).state());
        assertEquals(PowerGraph.Circuit.State.OPEN, managed.allCircuits().get(3).state());

        final PowerGraph.Grid chained = PowerGraph.of(chainedUnits());
        assertEquals(1, chained.distributors().size(), "the panel owns the distribution stage");
        assertTrue(chained.distributors().get(0).panel());
        assertTrue(chained.hasStorage(), "the controller's reserve is still the network's storage");

        final PowerGraph.Grid many = PowerGraph.of(manySourceUnits());
        assertEquals(6, many.sources().size());
        float banded = 0.0f;
        for (final PowerGraph.Node n : many.sources()) {
            banded += n.rate();
        }
        assertEquals(many.generated(), banded, 0.5f, "rolling up the tail must not lose power");
    }

    @Test
    void rendersOilfieldPage() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(1180, 34 + 3 * (H + 24) + 20);
        final Graphics2D g = sheet.createGraphics();

        place(g, renderField(pumpingField(), 0.12f, 1.0f), 0, 0, "Oilfield / pumping, t=0.12");
        place(g, renderField(pumpingField(), 0.45f, 1.0f), 1, 0, "Oilfield / pumping, t=0.45");
        place(g, renderField(nearlyDryField(), 0.30f, 1.0f), 0, 1, "Oilfield / nearly dry");
        place(g, renderField(brokenField(), 0.0f, 0.0f), 1, 1, "Oilfield / pipe broken, stopped");
        place(g, renderField(emptyField(), 0.0f, 0.0f), 0, 2, "Oilfield / nothing linked");

        final RasterSurface cramped = new RasterSurface(W, 200, 0x111318);
        OilfieldView.paint(cramped, new Rect(12.0f, 12.0f, W - 24.0f, 200.0f - 24.0f),
                pumpingField(), 0.2f, 1.0f);
        g.drawImage(cramped.finish(), 24 + 570, 34 + 2 * (H + 24), null);
        caption(g, "Oilfield / cramped to 200px", 24 + 570, 34 + 2 * (H + 24) - 8);

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_page_oilfield.png"));
    }

    private static BufferedImage renderField(OilfieldView.Field field, float phase, float duty) {
        final RasterSurface s = new RasterSurface(W, H, 0x111318);
        OilfieldView.paint(s, new Rect(12.0f, 12.0f, W - 24.0f, H - 24.0f), field, phase, duty);
        return s.finish();
    }

    private static OilfieldView.Field pumpingField() {
        final float[] history = ramp(140, 96_000.0f, 61_400.0f);
        return new OilfieldView.Field(List.of(
                new OilfieldView.Well("Derrick One", true, true, 24.0f, 61_400.0f, history, 41.0f),
                new OilfieldView.Well("Derrick Two", true, true, 18.0f, 22_800.0f,
                        ramp(140, 40_000.0f, 22_800.0f), 27.0f),
                new OilfieldView.Well("Derrick Three", false, true, 12.0f, 88_000.0f,
                        ramp(140, 88_000.0f, 88_000.0f), 0.0f)));
    }

    private static OilfieldView.Field nearlyDryField() {
        return new OilfieldView.Field(List.of(
                new OilfieldView.Well("Derrick One", true, true, 30.0f, 3_100.0f,
                        ramp(140, 96_000.0f, 3_100.0f), 38.0f)));
    }

    private static OilfieldView.Field brokenField() {
        return new OilfieldView.Field(List.of(
                new OilfieldView.Well("Derrick One", false, false, 9.0f, 74_000.0f,
                        ramp(140, 74_000.0f, 74_000.0f), 0.0f)));
    }

    private static OilfieldView.Field emptyField() {
        return new OilfieldView.Field(List.of());
    }

    @Test
    void rendersHeatAndFluidPages() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(1180, 34 + 2 * (H + 24) + 20);
        final Graphics2D g = sheet.createGraphics();

        place(g, renderHeat(inductionBank(), 0.4f), 0, 0, "Heat / induction, coil failing");
        place(g, renderHeat(boilerBank(), 0.4f), 1, 0, "Heat / boiler short of water");
        place(g, renderFluids(plant(), 1.1f), 0, 1, "Fluids / tanks and meters");
        place(g, renderFluids(new FluidsView.Plant(List.of(), List.of()), 0.0f), 1, 1,
                "Fluids / nothing linked");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_page_heat_fluids.png"));
    }

    private static BufferedImage renderHeat(HeatView.Bank bank, float time) {
        final RasterSurface s = new RasterSurface(W, H, 0x111318);
        final HeatView.Element e = bank.primary();
        HeatView.paint(s, new Rect(12.0f, 12.0f, W - 24.0f, H - 24.0f), bank, time,
                e == null ? 0.0f : e.heatFraction());
        return s.finish();
    }

    private static BufferedImage renderFluids(FluidsView.Plant plant, float time) {
        final RasterSurface s = new RasterSurface(W, H, 0x111318);
        FluidsView.paint(s, new Rect(12.0f, 12.0f, W - 24.0f, H - 24.0f), plant, time);
        return s.finish();
    }

    private static HeatView.Bank inductionBank() {
        return new HeatView.Bank(List.of(
                new HeatView.Element("Forge Heater", false, 3, 4, 3, 512.0f, 96.0f, 74.0f,
                        true, 14.0f, 96.0f, true, 62.0f, 21.0f, false, "Iron Ingot",
                        0.0f, 0, 0),
                new HeatView.Element("Backup Heater", false, 0, 4, 0, 0.0f, 0.0f, 0.0f,
                        false, 0.0f, 0.0f, false, 0.0f, 0.0f, false, "", 0.0f, 0, 0)));
    }

    private static HeatView.Bank boilerBank() {
        return new HeatView.Bank(List.of(
                new HeatView.Element("Main Boiler", true, 2, 4, 4, 0.0f, 0.0f, 0.0f,
                        false, 0.0f, 0.0f, false, 0.0f, 0.0f, false, "",
                        640.0f, 6, 2)));
    }

    private static FluidsView.Plant plant() {
        return new FluidsView.Plant(
                List.of(new FluidsView.Tank("Crude Store", "Crude Oil", 78.0f, 31_200.0f,
                                40_000.0f, 62.0f),
                        new FluidsView.Tank("Diesel Day Tank", "Diesel", 24.0f, 4_800.0f,
                                20_000.0f, -38.0f),
                        new FluidsView.Tank("Water", "Water", 96.0f, 38_400.0f, 40_000.0f, 0.2f)),
                List.of(new FluidsView.Meter("Tower Feed", "Crude Oil", 12.4f, 184_000.0f, 1.0f),
                        new FluidsView.Meter("Return Line", "Diesel", 3.1f, 42_000.0f, -1.0f),
                        new FluidsView.Meter("Standby", "Water", 0.0f, 9_100.0f, 1.0f)));
    }

    @Test
    void rendersDieselPage() throws IOException {
        ensureOut();
        final BufferedImage sheet = sheet(1180, 34 + 3 * (H + 24) + 20);
        final Graphics2D g = sheet.createGraphics();

        place(g, renderDiesel(wastefulBank(), 0.4f), 0, 0, "Diesel / 67% of fuel wasted");
        place(g, renderDiesel(matchedBank(), 0.4f), 1, 0, "Diesel / well matched, nothing to cut");
        place(g, renderDiesel(idleBank(), 0.9f), 0, 1, "Diesel / running with almost no load");
        place(g, renderDiesel(sharedBank(), 0.4f), 1, 1, "Diesel / windmill also on the network");
        place(g, renderDiesel(criticalBank(), 0.6f), 0, 2, "Diesel / dry soon, cut extends it");
        place(g, renderDiesel(stoppedBank(), 0.0f), 1, 2, "Diesel / held off by redstone");

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_page_diesel.png"));
    }

    private static BufferedImage renderDiesel(DieselView.Bank bank, float time) {
        final RasterSurface s = new RasterSurface(W, H, 0x111318);
        DieselView.paint(s, new Rect(12.0f, 12.0f, W - 24.0f, H - 24.0f),
                bank, bank.wasteFraction(), time);
        return s.finish();
    }

    private static DieselView.Bank wastefulBank() {
        return new DieselView.Bank(6, 6, 0, 0, 12_288.0f, 2_048.0f, 4_100.0f, 12_288.0f, 0.0f,
                28_400.0f, 40_000.0f, "Diesel", -22.0f,
                ramp(150, 39_000.0f, 28_400.0f), 150.0f, true, false);
    }

    private static DieselView.Bank matchedBank() {
        return new DieselView.Bank(4, 4, 0, 0, 8_192.0f, 2_048.0f, 7_400.0f, 8_192.0f, 0.0f,
                26_000.0f, 40_000.0f, "Biodiesel", -19.0f,
                ramp(150, 34_000.0f, 26_000.0f), 150.0f, true, false);
    }

    private static DieselView.Bank idleBank() {
        return new DieselView.Bank(8, 8, 0, 0, 16_384.0f, 2_048.0f, 220.0f, 16_384.0f, 0.0f,
                12_000.0f, 40_000.0f, "Diesel", -29.0f,
                ramp(150, 30_000.0f, 12_000.0f), 150.0f, true, false);
    }

    private static DieselView.Bank sharedBank() {
        return new DieselView.Bank(6, 6, 0, 0, 12_288.0f, 2_048.0f, 9_800.0f, 18_288.0f, 6_000.0f,
                28_400.0f, 40_000.0f, "Diesel", -22.0f,
                ramp(150, 39_000.0f, 28_400.0f), 150.0f, true, false);
    }

    private static DieselView.Bank criticalBank() {
        return new DieselView.Bank(4, 4, 0, 0, 8_192.0f, 2_048.0f, 1_900.0f, 8_192.0f, 0.0f,
                1_900.0f, 40_000.0f, "Diesel", -7.0f,
                ramp(150, 18_000.0f, 1_900.0f), 150.0f, true, false);
    }

    private static DieselView.Bank stoppedBank() {
        return new DieselView.Bank(6, 0, 0, 6, 0.0f, 0.0f, 4_200.0f, 0.0f, 0.0f,
                31_000.0f, 40_000.0f, "Diesel", 0.0f,
                ramp(150, 31_000.0f, 31_000.0f), 150.0f, true, true);
    }

    private static PowerGraph.Grid healthyGrid() {
        return PowerGraph.of(list(
                unit("Alternator", "North Alternator", metric("energy_output_rate", 512.0f),
                        metric("rpm", 128.0f)),
                unit("Alternator", "South Alternator", metric("energy_output_rate", 384.0f),
                        metric("rpm", 96.0f)),
                unit("Modular Accumulator", "Main Bank",
                        metric("energy_stored", 2_480_000.0f),
                        metric("energy_capacity", 4_000_000.0f),
                        metric("energy_net_rate", 420.0f),
                        metric("accumulator_width", 3.0f),
                        metric("accumulator_height", 5.0f),
                        series("energy_stored", ramp(120, 1_900_000.0f, 2_480_000.0f))),
                unit("Electric Motor", "Mixer Drive", metric("energy_input_rate", 256.0f),
                        metric("rpm", 64.0f)),
                unit("Electric Motor", "Press Line", metric("energy_input_rate", 152.0f),
                        metric("rpm", 32.0f)),
                unit("Electric Motor", "Saw Bank", metric("energy_input_rate", 68.0f),
                        metric("rpm", 16.0f))));
    }

    private static PowerGraph.Grid drainingGrid() {
        return PowerGraph.of(list(
                unit("Alternator", "North Alternator", metric("energy_output_rate", 40.0f),
                        metric("rpm", 8.0f)),
                unit("Modular Accumulator", "Main Bank",
                        metric("energy_capacity", 4_000_000.0f),
                        metric("energy_net_rate", -640.0f),
                        metric("accumulator_width", 3.0f),
                        metric("accumulator_height", 5.0f),
                        series("energy_stored", ramp(120, 1_400_000.0f, 310_000.0f))),
                unit("Electric Motor", "Mixer Drive", metric("energy_input_rate", 420.0f)),
                unit("Electric Motor", "Press Line", metric("energy_input_rate", 260.0f))));
    }

    private static PowerGraph.Grid noBufferGrid() {
        return PowerGraph.of(list(
                unit("Alternator", "Windmill Alternator", metric("energy_output_rate", 180.0f),
                        metric("rpm", 48.0f)),
                unit("Electric Motor", "Crusher", metric("energy_input_rate", 180.0f))));
    }

    private static PowerGraph.Grid unaccountedGrid() {
        return PowerGraph.of(list(
                unit("Alternator", "North Alternator", metric("energy_output_rate", 900.0f),
                        metric("rpm", 128.0f)),
                unit("Modular Accumulator", "Main Bank",
                        metric("energy_capacity", 4_000_000.0f),
                        metric("energy_net_rate", 100.0f),
                        metric("accumulator_width", 3.0f),
                        metric("accumulator_height", 5.0f),
                        series("energy_stored", ramp(120, 1_100_000.0f, 1_200_000.0f))),
                unit("Electric Motor", "Mixer Drive", metric("energy_input_rate", 300.0f))));
    }

    private static List<SourceProbe.Unit> ownHardwareUnits() {
        return list(
                unit("Kinetic Dynamo", "North Dynamo", metric("energy_output_rate", 300.0f),
                        metric("energy_net_rate", 300.0f), metric("rpm", 96.0f)),
                unit("Kinetic Dynamo", "South Dynamo", metric("energy_output_rate", 460.0f),
                        metric("energy_net_rate", 460.0f), metric("rpm", 128.0f)),
                unit("Capacitor Bank", "Cellar Bank",
                        metric("energy_capacity", 2_400_000.0f),
                        metric("energy_net_rate", 210.0f),
                        metric("bank_blocks", 12.0f), metric("bank_layers", 3.0f),
                        series("energy_stored", ramp(120, 1_050_000.0f, 1_480_000.0f))),
                unit("Power Connector", "Main Bus",
                        metric("network_pushed", 760.0f), metric("network_demand", 550.0f),
                        metric("network_pulled", 760.0f), metric("wire_nodes", 14.0f),
                        metric("energy_stored", 1_480_000.0f),
                        metric("energy_capacity", 2_400_000.0f)));
    }

    private static PowerGraph.Grid ownHardwareGrid() {
        return PowerGraph.of(ownHardwareUnits());
    }

    private static List<SourceProbe.Unit> manySourceUnits() {
        final List<SourceProbe.Unit> units = list();
        for (int i = 0; i < 11; i++) {
            units.add(unit("Kinetic Dynamo", "Dynamo " + (i + 1),
                    metric("energy_output_rate", 480.0f - i * 34.0f),
                    metric("rpm", 128.0f)));
        }
        units.add(unit("Capacitor Bank", "Bank",
                metric("energy_capacity", 3_000_000.0f),
                metric("energy_net_rate", 900.0f),
                metric("bank_blocks", 18.0f), metric("bank_layers", 3.0f),
                series("energy_stored", ramp(120, 1_400_000.0f, 2_100_000.0f))));
        units.add(unit("Electric Motor", "Press Line", metric("energy_input_rate", 1_990.0f)));
        return units;
    }

    private static PowerGraph.Grid manySourcesGrid() {
        return PowerGraph.of(manySourceUnits());
    }

    private static List<SourceProbe.Unit> pduUnits() {
        return list(
                unit("Kinetic Dynamo", "Main Dynamo", metric("energy_output_rate", 2_200.0f),
                        metric("rpm", 192.0f)),
                unit("Kinetic Dynamo", "Backup Dynamo", metric("energy_output_rate", 1_200.0f),
                        metric("rpm", 128.0f)),
                unit("Capacitor Bank", "Reserve",
                        metric("energy_capacity", 2_400_000.0f),
                        metric("energy_net_rate", 190.0f),
                        metric("bank_blocks", 9.0f), metric("bank_layers", 3.0f),
                        series("energy_stored", ramp(120, 1_600_000.0f, 1_820_000.0f))),
                pdu());
    }

    private static SourceProbe.Unit pdu() {
        return unit("Power Distribution Unit", "Workshop Panel",
                metric("pdu_legs_wired", 15.0f),
                metric("pdu_leg0_demand", 640.0f), metric("pdu_leg0_served", 640.0f),
                metric("pdu_leg0_ceiling", 2_048.0f), metric("pdu_leg0_state", 0.0f),
                metric("pdu_leg0_devices", 3.0f),
                metric("pdu_leg1_demand", 900.0f), metric("pdu_leg1_served", 520.0f),
                metric("pdu_leg1_ceiling", 4_096.0f), metric("pdu_leg1_state", 0.0f),
                metric("pdu_leg1_devices", 4.0f),
                metric("pdu_leg2_demand", 3_200.0f), metric("pdu_leg2_served", 2_048.0f),
                metric("pdu_leg2_ceiling", 2_048.0f), metric("pdu_leg2_state", 1.0f),
                metric("pdu_leg2_devices", 5.0f),
                metric("pdu_leg3_demand", 300.0f), metric("pdu_leg3_served", 0.0f),
                metric("pdu_leg3_ceiling", 2_048.0f), metric("pdu_leg3_state", 2.0f),
                metric("pdu_leg3_devices", 2.0f),
                metric("pdu_demand", 5_040.0f), metric("pdu_served_demand", 3_208.0f),
                metric("pdu_generation", 3_400.0f), metric("pdu_budget", 3_300.0f),
                metric("pdu_ceiling", 8_192.0f),
                metric("pdu_devices", 14.0f), metric("pdu_served_devices", 9.0f),
                metric("pdu_shed_devices", 5.0f), metric("pdu_mode", 2.0f),
                metric("pdu_tier0_total", 3.0f), metric("pdu_tier0_served", 3.0f),
                metric("pdu_tier1_total", 4.0f), metric("pdu_tier1_served", 4.0f),
                metric("pdu_tier2_total", 3.0f), metric("pdu_tier2_served", 2.0f),
                metric("pdu_tier3_total", 2.0f), metric("pdu_tier3_served", 0.0f),
                metric("pdu_tier4_total", 2.0f), metric("pdu_tier4_served", 0.0f),
                metric("energy_stored", 210_000.0f), metric("energy_capacity", 512_000.0f),
                metric("energy_input_rate", 3_208.0f), metric("energy_output_rate", 3_208.0f));
    }

    private static PowerGraph.Grid pduGrid() {
        return PowerGraph.of(pduUnits());
    }

    private static List<SourceProbe.Unit> controllerUnits() {
        return list(
                unit("Kinetic Dynamo", "Windmill Dynamo", metric("energy_output_rate", 900.0f),
                        metric("rpm", 64.0f)),
                controller(false));
    }

    private static SourceProbe.Unit controller(boolean managed) {
        if (managed) {
            return unit("FE Battery Controller", "Main Reserve",
                    metric("energy_capacity", 3_600_000.0f),
                    metric("energy_net_rate", -1_180.0f),
                    metric("energy_input_rate", 2_028.0f),
                    metric("energy_output_rate", 3_208.0f),
                    metric("bank_stance", 2.0f), metric("bank_managed", 1.0f),
                    metric("bank_out0_flow", 3_208.0f), metric("bank_out0_demand", 5_040.0f),
                    metric("bank_out0_ceiling", 8_192.0f), metric("bank_out0_floor", 30.0f),
                    metric("bank_out0_reason", 1.0f), metric("bank_out0_devices", 14.0f),
                    metric("bank_out0_mode", 0.0f), metric("bank_out0_tier", 0.0f),
                    metric("bank_out0_panels", 1.0f), metric("bank_out0_critical", 640.0f),
                    metric("bank_out1_reason", 8.0f), metric("bank_out2_reason", 8.0f),
                    series("energy_stored", ramp(120, 1_680_000.0f, 1_150_000.0f)));
        }
        return unit("FE Battery Controller", "House Reserve",
                metric("energy_capacity", 3_600_000.0f),
                metric("energy_net_rate", -420.0f),
                metric("energy_input_rate", 900.0f),
                metric("energy_output_rate", 1_320.0f),
                metric("bank_stance", 2.0f),
                metric("bank_managed", 0.0f),
                metric("bank_out0_flow", 620.0f), metric("bank_out0_demand", 620.0f),
                metric("bank_out0_ceiling", 4_096.0f), metric("bank_out0_floor", 0.0f),
                metric("bank_out0_reason", 0.0f), metric("bank_out0_devices", 4.0f),
                metric("bank_out0_mode", 0.0f), metric("bank_out0_tier", 0.0f),
                metric("bank_out1_flow", 700.0f), metric("bank_out1_demand", 900.0f),
                metric("bank_out1_ceiling", 4_096.0f), metric("bank_out1_floor", 40.0f),
                metric("bank_out1_reason", 1.0f), metric("bank_out1_devices", 6.0f),
                metric("bank_out1_mode", 0.0f), metric("bank_out1_tier", 2.0f),
                metric("bank_out2_flow", 0.0f), metric("bank_out2_demand", 480.0f),
                metric("bank_out2_ceiling", 4_096.0f), metric("bank_out2_floor", 70.0f),
                metric("bank_out2_reason", 3.0f), metric("bank_out2_devices", 3.0f),
                metric("bank_out2_mode", 2.0f), metric("bank_out2_tier", 3.0f),
                series("energy_stored", ramp(120, 1_680_000.0f, 1_150_000.0f)));
    }

    private static PowerGraph.Grid controllerGrid() {
        return PowerGraph.of(controllerUnits());
    }

    private static List<SourceProbe.Unit> chainedUnits() {
        return list(controller(true), pdu());
    }

    private static PowerGraph.Grid chainedGrid() {
        return PowerGraph.of(chainedUnits());
    }

    private static BufferedImage render(PowerGraph.Grid grid, float time) {
        return render(grid, time, W);
    }

    private static BufferedImage renderWide(PowerGraph.Grid grid, float time) {
        return render(grid, time, 860);
    }

    private static BufferedImage render(PowerGraph.Grid grid, float time, int width) {
        final RasterSurface s = new RasterSurface(width, H, 0x111318);
        PowerView.paint(s, new Rect(12.0f, 12.0f, width - 24.0f, H - 24.0f),
                grid, time, grid.net(), grid.fill() * 100.0f);
        return s.finish();
    }

    record Metric(String id, float value, float[] series) {
    }

    static Metric metric(String id, float value) {
        return new Metric(id, value, null);
    }

    static Metric series(String id, float[] data) {
        return new Metric(id, data[data.length - 1], data);
    }

    static SourceProbe.Unit unit(String type, String label, Metric... metrics) {
        final SourceConfig config = new SourceConfig("s" + Math.abs(label.hashCode() % 997),
                new BlockPos(0, 64, 0), type);
        config.setUserLabel(label);
        final Map<String, StatsDisplayPanelBE.DataChannel> channels = new LinkedHashMap<>();
        for (final Metric m : metrics) {
            final StatsDisplayPanelBE.DataChannel ch =
                    new StatsDisplayPanelBE.DataChannel(m.id(), m.id());
            if (m.series() == null) {
                ch.getStore().addSampleDirect(m.value());
            } else {
                for (final float v : m.series()) {
                    ch.getStore().addSampleDirect(v);
                }
            }
            channels.put(m.id(), ch);
        }
        return new SourceProbe.Unit(config, channels);
    }

    static float[] ramp(int n, float from, float to) {
        final float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            final float t = i / (float) (n - 1);
            out[i] = from + (to - from) * t
                    + (float) Math.sin(i * 0.28) * Math.abs(to - from) * 0.04f;
        }
        return out;
    }

    static void ensureOut() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
    }

    static BufferedImage sheet(int w, int h) {
        final BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161B));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return sheet;
    }

    static void place(Graphics2D g, BufferedImage img, int col, int row, String caption) {
        final int x = 24 + col * 570;
        final int y = 34 + row * (H + 24);
        g.drawImage(img, x, y, null);
        caption(g, caption, x, y - 8);
    }

    static void caption(Graphics2D g, String text, int x, int y) {
        g.setColor(new Color(0x98A0AD));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(text, x, y);
    }

    static List<SourceProbe.Unit> list(SourceProbe.Unit... units) {
        return new ArrayList<>(List.of(units));
    }
}
