package com.createstats.statsmk3.page;

import static com.createstats.statsmk3.page.MachinePageArtTest.caption;
import static com.createstats.statsmk3.page.MachinePageArtTest.ensureOut;
import static com.createstats.statsmk3.page.MachinePageArtTest.metric;
import static com.createstats.statsmk3.page.MachinePageArtTest.ramp;
import static com.createstats.statsmk3.page.MachinePageArtTest.series;
import static com.createstats.statsmk3.page.MachinePageArtTest.sheet;
import static com.createstats.statsmk3.page.MachinePageArtTest.unit;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.client.statspower.PowerBatteryView;
import com.createstats.client.statspower.PowerFlowView;
import com.createstats.client.statspower.PowerHover;
import com.createstats.client.statspower.PowerLegend;
import com.createstats.client.statspower.PowerPduView;
import com.createstats.client.statspower.PowerReadings;
import com.createstats.client.statspower.RasterFont;
import com.createstats.client.statspower.RasterGui;
import com.createstats.client.statspower.StatsPowerPage;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.gui.pdu.PduRect;
import com.createstats.util.PreviewImages;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

class StatsPowerArtTest {
    private static final File OUT = new File("fixture_preview");
    private static final int BG = 0x12161B;

    @Test
    void rendersDistributionSubpage() throws IOException {
        ensureOut();
        final BufferedImage img = grid("statspower_pdu.png", new Case[]{
                new Case("Distribution / shedding two tiers", 700, 390,
                        (g, r, p) -> PowerPduView.render(g, font(), r,
                                PowerReadings.readPdu(sheddingPdu()), hover(), p, false)),
                new Case("Distribution / everything served", 700, 390,
                        (g, r, p) -> PowerPduView.render(g, font(), r,
                                PowerReadings.readPdu(healthyPdu()), hover(), p, false)),
                new Case("Distribution / t + 0.5s", 700, 390,
                        (g, r, p) -> PowerPduView.render(g, font(), r,
                                PowerReadings.readPdu(sheddingPdu()), hover(), p + 10.0f, false)),
                new Case("Distribution / the key", 700, 390,
                        (g, r, p) -> {
                            PowerPduView.render(g, font(), r,
                                    PowerReadings.readPdu(sheddingPdu()), hover(), p, false);
                            PowerLegend.draw(g, font(), r, StatsPowerPage.Sub.PDU, p);
                        }),
                new Case("Distribution / 430px", 430, 260,
                        (g, r, p) -> PowerPduView.render(g, font(), r,
                                PowerReadings.readPdu(sheddingPdu()), hover(), p, false)),
                new Case("Distribution / cramped 264x123", 264, 123,
                        (g, r, p) -> PowerPduView.render(g, font(), r,
                                PowerReadings.readPdu(sheddingPdu()), hover(), p, false))});
        assertNotNull(img);
    }

    @Test
    void rendersBatterySubpage() throws IOException {
        ensureOut();
        final BufferedImage img = grid("statspower_battery.png", new Case[]{
                new Case("Battery / draining, one output below floor", 700, 390,
                        (g, r, p) -> PowerBatteryView.render(g, font(), r,
                                PowerReadings.readBank(drainingBank()), hover(), p, false)),
                new Case("Battery / charging hard", 700, 390,
                        (g, r, p) -> PowerBatteryView.render(g, font(), r,
                                PowerReadings.readBank(chargingBank()), hover(), p, false)),
                new Case("Battery / t + 0.5s", 700, 390,
                        (g, r, p) -> PowerBatteryView.render(g, font(), r,
                                PowerReadings.readBank(drainingBank()), hover(), p + 10.0f, false)),
                new Case("Battery / the key", 700, 390,
                        (g, r, p) -> {
                            PowerBatteryView.render(g, font(), r,
                                    PowerReadings.readBank(drainingBank()), hover(), p, false);
                            PowerLegend.draw(g, font(), r, StatsPowerPage.Sub.BANK, p);
                        }),
                new Case("Battery / 430px", 430, 260,
                        (g, r, p) -> PowerBatteryView.render(g, font(), r,
                                PowerReadings.readBank(drainingBank()), hover(), p, false)),
                new Case("Battery / cramped 264x123", 264, 123,
                        (g, r, p) -> PowerBatteryView.render(g, font(), r,
                                PowerReadings.readBank(drainingBank()), hover(), p, false))});
        assertNotNull(img);
    }

    @Test
    void rendersFlowSubpage() throws IOException {
        ensureOut();
        final BufferedImage img = grid("statspower_flow.png", new Case[]{
                new Case("Flow / carrying, steady", 700, 390,
                        (g, r, p) -> PowerFlowView.render(g, font(), r,
                                PowerReadings.readMeter(flowingMeter()), hover(), p, false)),
                new Case("Flow / bursty", 700, 390,
                        (g, r, p) -> PowerFlowView.render(g, font(), r,
                                PowerReadings.readMeter(burstyMeter()), hover(), p, false)),
                new Case("Flow / idle", 700, 390,
                        (g, r, p) -> PowerFlowView.render(g, font(), r,
                                PowerReadings.readMeter(idleMeter()), hover(), p, false)),
                new Case("Flow / the key", 700, 390,
                        (g, r, p) -> {
                            PowerFlowView.render(g, font(), r,
                                    PowerReadings.readMeter(flowingMeter()), hover(), p, false);
                            PowerLegend.draw(g, font(), r, StatsPowerPage.Sub.FLOW, p);
                        }),
                new Case("Flow / 430px", 430, 260,
                        (g, r, p) -> PowerFlowView.render(g, font(), r,
                                PowerReadings.readMeter(flowingMeter()), hover(), p, false)),
                new Case("Flow / cramped 264x123", 264, 123,
                        (g, r, p) -> PowerFlowView.render(g, font(), r,
                                PowerReadings.readMeter(flowingMeter()), hover(), p, false))});
        assertNotNull(img);
    }

    private interface Paint {
        void draw(RasterGui g, PduRect area, float phase);
    }

    private record Case(String caption, int w, int h, Paint paint) {
    }

    private static RasterFont FONT;

    private static RasterFont font() {
        if (FONT == null) {
            FONT = new RasterFont();
        }
        return FONT;
    }

    private static PowerHover hover() {
        return new PowerHover(-9999, -9999);
    }

    private static BufferedImage grid(String name, Case[] cases) throws IOException {
        int colW = 0;
        int rowH = 0;
        for (final Case c : cases) {
            colW = Math.max(colW, c.w());
            rowH = Math.max(rowH, c.h());
        }
        final int cols = 2;
        final int rows = (cases.length + cols - 1) / cols;
        final BufferedImage out = sheet(24 + cols * (colW + 24), 34 + rows * (rowH + 28));
        final Graphics2D g = out.createGraphics();
        for (int i = 0; i < cases.length; i++) {
            final Case c = cases[i];
            final RasterGui gui = RasterGui.create(c.w(), c.h(), BG);
            c.paint().draw(gui, new PduRect(4, 4, c.w() - 8, c.h() - 8), 40.0f);
            final BufferedImage frame = gui.finish();
            final int x = 24 + (i % cols) * (colW + 24);
            final int y = 34 + (i / cols) * (rowH + 28);
            g.drawImage(frame, x, y, null);
            caption(g, c.caption() + "  (" + c.w() + "x" + c.h() + ")", x, y - 6);
        }
        g.dispose();
        PreviewImages.write(out, "png", new File(OUT, name));
        return out;
    }

    private static SourceProbe.Unit sheddingPdu() {
        final List<MachinePageArtTest.Metric> m = new ArrayList<>();
        m.add(series("pdu_generation", ramp(150, 6400.0f, 6100.0f)));
        m.add(series("pdu_demand", ramp(150, 7200.0f, 9400.0f)));
        m.add(series("pdu_served_demand", ramp(150, 6900.0f, 6050.0f)));
        m.add(series("energy_fill_pct", ramp(150, 92.0f, 41.0f)));
        m.add(metric("energy_stored", 41000.0f));
        m.add(metric("energy_capacity", 100000.0f));
        m.add(metric("energy_input_rate", 6100.0f));
        m.add(metric("energy_output_rate", 6050.0f));
        m.add(metric("energy_net_rate", -240.0f));
        m.add(metric("energy_endurance", 3400.0f));
        m.add(metric("pdu_budget", 6800.0f));
        m.add(metric("pdu_ceiling", 8192.0f));
        m.add(metric("pdu_devices", 23.0f));
        m.add(metric("pdu_served_devices", 15.0f));
        m.add(metric("pdu_shed_devices", 8.0f));
        m.add(metric("pdu_mode", 2.0f));
        m.add(metric("pdu_surplus", 0.0f));
        m.add(metric("pdu_throughput_limited", 0.0f));
        m.add(metric("pdu_legs_wired", (float) (0b101101111)));
        leg(m, 0, 1800.0f, 1800.0f, 2048.0f, 0.0f, 5);
        leg(m, 1, 2600.0f, 1900.0f, 2048.0f, 1.0f, 6);
        leg(m, 2, 900.0f, 900.0f, 1024.0f, 0.0f, 3);
        leg(m, 3, 1400.0f, 1200.0f, 2048.0f, 0.0f, 4);
        leg(m, 5, 700.0f, 0.0f, 1024.0f, 3.0f, 2);
        leg(m, 6, 0.0f, 0.0f, 1024.0f, 2.0f, 1);
        leg(m, 8, 250.0f, 250.0f, 512.0f, 0.0f, 2);
        tier(m, 0, 4, 4);
        tier(m, 1, 6, 6);
        tier(m, 2, 6, 5);
        tier(m, 3, 4, 0);
        tier(m, 4, 3, 0);
        m.add(metric("pdu_reserve_linked", 1.0f));
        m.add(metric("pdu_reserve_pct", 34.0f));
        m.add(metric("pdu_reserve_stance", 2.0f));
        m.add(metric("pdu_reserve_allowance", 5200.0f));
        m.add(metric("pdu_upstream_sources", 3.0f));
        m.add(metric("pdu_reserve_tanks", 12.0f));
        m.add(metric("pdu_reserve_depth", 8.0f));
        m.add(metric("pdu_core_protected", 1.0f));
        return unit("Power Distribution Unit", "Main Switchboard",
                m.toArray(new MachinePageArtTest.Metric[0]));
    }

    private static SourceProbe.Unit healthyPdu() {
        final List<MachinePageArtTest.Metric> m = new ArrayList<>();
        m.add(series("pdu_generation", ramp(150, 8000.0f, 9200.0f)));
        m.add(series("pdu_demand", ramp(150, 4200.0f, 4900.0f)));
        m.add(series("pdu_served_demand", ramp(150, 4200.0f, 4900.0f)));
        m.add(series("energy_fill_pct", ramp(150, 74.0f, 99.0f)));
        m.add(metric("energy_stored", 99000.0f));
        m.add(metric("energy_capacity", 100000.0f));
        m.add(metric("energy_input_rate", 9200.0f));
        m.add(metric("energy_output_rate", 4900.0f));
        m.add(metric("energy_net_rate", 180.0f));
        m.add(metric("energy_endurance", 0.0f));
        m.add(metric("pdu_budget", 9000.0f));
        m.add(metric("pdu_ceiling", 12288.0f));
        m.add(metric("pdu_devices", 12.0f));
        m.add(metric("pdu_served_devices", 12.0f));
        m.add(metric("pdu_shed_devices", 0.0f));
        m.add(metric("pdu_mode", 1.0f));
        m.add(metric("pdu_surplus", 1.0f));
        m.add(metric("pdu_throughput_limited", 0.0f));
        m.add(metric("pdu_legs_wired", (float) (0b000010111)));
        leg(m, 0, 1600.0f, 1600.0f, 4096.0f, 0.0f, 4);
        leg(m, 1, 1200.0f, 1200.0f, 2048.0f, 0.0f, 3);
        leg(m, 2, 1400.0f, 1400.0f, 2048.0f, 0.0f, 4);
        leg(m, 4, 700.0f, 700.0f, 1024.0f, 0.0f, 1);
        tier(m, 0, 2, 2);
        tier(m, 1, 4, 4);
        tier(m, 2, 4, 4);
        tier(m, 3, 2, 2);
        tier(m, 4, 0, 0);
        m.add(metric("pdu_reserve_linked", 0.0f));
        m.add(metric("pdu_upstream_sources", 2.0f));
        return unit("Power Distribution Unit", "Workshop Panel",
                m.toArray(new MachinePageArtTest.Metric[0]));
    }

    private static void leg(List<MachinePageArtTest.Metric> m, int i, float demand, float served,
                            float ceiling, float state, int devices) {
        m.add(metric("pdu_leg" + i + "_demand", demand));
        m.add(metric("pdu_leg" + i + "_served", served));
        m.add(metric("pdu_leg" + i + "_ceiling", ceiling));
        m.add(metric("pdu_leg" + i + "_state", state));
        m.add(metric("pdu_leg" + i + "_devices", (float) devices));
    }

    private static void tier(List<MachinePageArtTest.Metric> m, int i, int total, int served) {
        m.add(metric("pdu_tier" + i + "_total", (float) total));
        m.add(metric("pdu_tier" + i + "_served", (float) served));
    }

    private static SourceProbe.Unit drainingBank() {
        final List<MachinePageArtTest.Metric> m = new ArrayList<>();
        m.add(series("energy_net_rate", swing(150, -2600.0f, 900.0f)));
        m.add(series("energy_fill_pct", ramp(150, 78.0f, 23.0f)));
        m.add(metric("energy_stored", 460000.0f));
        m.add(metric("energy_capacity", 2000000.0f));
        m.add(series("energy_input_rate", ramp(150, 5200.0f, 4100.0f)));
        m.add(series("energy_output_rate", ramp(150, 6100.0f, 6700.0f)));
        m.add(metric("energy_endurance", 3540.0f));
        m.add(metric("bank_bus", 8200.0f));
        m.add(metric("bank_stance", 3.0f));
        m.add(metric("bank_managed", 1.0f));
        feed(m, 0, 2600.0f, 6, 2);
        feed(m, 1, 1500.0f, 4, 1);
        feed(m, 2, 0.0f, 0, 0);
        circuit(m, 0, 3800.0f, 3800.0f, 8192.0f, 0.0f, 0.0f, 9, 1, 0, 0, 0);
        circuit(m, 1, 2900.0f, 3400.0f, 4096.0f, 25.0f, 800.0f, 6, 0, 1, 1, 2);
        circuit(m, 2, 0.0f, 1600.0f, 4096.0f, 60.0f, 0.0f, 4, 0, 3, 2, 3);
        return unit("FE Battery Controller", "Base Reserve",
                m.toArray(new MachinePageArtTest.Metric[0]));
    }

    private static SourceProbe.Unit chargingBank() {
        final List<MachinePageArtTest.Metric> m = new ArrayList<>();
        m.add(series("energy_net_rate", swing(150, -400.0f, 3100.0f)));
        m.add(series("energy_fill_pct", ramp(150, 22.0f, 71.0f)));
        m.add(metric("energy_stored", 1420000.0f));
        m.add(metric("energy_capacity", 2000000.0f));
        m.add(series("energy_input_rate", ramp(150, 6800.0f, 9400.0f)));
        m.add(series("energy_output_rate", ramp(150, 6100.0f, 6300.0f)));
        m.add(metric("energy_endurance", 0.0f));
        m.add(metric("bank_bus", 9800.0f));
        m.add(metric("bank_stance", 1.0f));
        m.add(metric("bank_managed", 1.0f));
        feed(m, 0, 4200.0f, 8, 3);
        feed(m, 1, 3100.0f, 6, 2);
        feed(m, 2, 2100.0f, 4, 1);
        circuit(m, 0, 3800.0f, 3800.0f, 8192.0f, 0.0f, 0.0f, 9, 1, 0, 0, 0);
        circuit(m, 1, 2500.0f, 2500.0f, 4096.0f, 25.0f, 800.0f, 6, 0, 0, 1, 2);
        circuit(m, 2, 0.0f, 900.0f, 4096.0f, 60.0f, 0.0f, 4, 0, 5, 2, 3);
        return unit("FE Battery Controller", "Bank Alpha",
                m.toArray(new MachinePageArtTest.Metric[0]));
    }

    private static void feed(List<MachinePageArtTest.Metric> m, int i, float flow, int tanks,
                             int sources) {
        m.add(metric("bank_in" + i + "_flow", flow));
        m.add(metric("bank_in" + i + "_tanks", (float) tanks));
        m.add(metric("bank_in" + i + "_sources", (float) sources));
    }

    private static void circuit(List<MachinePageArtTest.Metric> m, int i, float flow, float demand,
                                float ceiling, float floor, float critical, int devices, int panels,
                                int reason, int mode, int tier) {
        m.add(metric("bank_out" + i + "_flow", flow));
        m.add(metric("bank_out" + i + "_demand", demand));
        m.add(metric("bank_out" + i + "_ceiling", ceiling));
        m.add(metric("bank_out" + i + "_floor", floor));
        m.add(metric("bank_out" + i + "_critical", critical));
        m.add(metric("bank_out" + i + "_devices", (float) devices));
        m.add(metric("bank_out" + i + "_panels", (float) panels));
        m.add(metric("bank_out" + i + "_reason", (float) reason));
        m.add(metric("bank_out" + i + "_mode", (float) mode));
        m.add(metric("bank_out" + i + "_tier", (float) tier));
    }

    private static SourceProbe.Unit flowingMeter() {
        return meterUnit("Refinery Feed", "minecraft:water", 1.0f,
                ramp(160, 38.0f, 46.0f), ramp(160, 3_100_000.0f, 3_284_512.0f));
    }

    private static SourceProbe.Unit burstyMeter() {
        return meterUnit("Bottling Line", "create:honey", -1.0f,
                burst(160, 0.0f, 120.0f), ramp(160, 812_000.0f, 903_400.0f));
    }

    private static SourceProbe.Unit idleMeter() {
        return meterUnit("Standby Branch", "", 0.0f,
                ramp(160, 12.0f, 0.0f), new float[]{44_120.0f});
    }

    private static SourceProbe.Unit meterUnit(String label, String fluidId, float direction,
                                              float[] rate, float[] total) {
        final SourceConfig config = new SourceConfig("meter_" + Math.abs(label.hashCode() % 997),
                new BlockPos(18, 64, -42), "Flow Meter");
        config.setUserLabel(label);
        final Map<String, StatsDisplayPanelBE.DataChannel> channels = new LinkedHashMap<>();
        channels.put("flow_rate_mbt", channel("flow_rate_mbt", "Flow Rate", rate));
        channels.put("flow_total_mb", channel("flow_total_mb", "Total Moved", total));
        channels.put("flow_direction", channel("flow_direction", "Direction",
                new float[]{direction}));
        channels.put("fluid_id", channel("fluid_id", fluidId, new float[]{0.0f}));
        return new SourceProbe.Unit(config, channels);
    }

    private static StatsDisplayPanelBE.DataChannel channel(String id, String label, float[] data) {
        final StatsDisplayPanelBE.DataChannel ch = new StatsDisplayPanelBE.DataChannel(id, label);
        for (final float v : data) {
            ch.getStore().addSampleDirect(v);
        }
        return ch;
    }

    private static float[] swing(int n, float low, float high) {
        final float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            final double t = i / (double) (n - 1);
            out[i] = (float) (low + (high - low) * (0.5 + 0.5 * Math.sin(t * 9.0)));
        }
        return out;
    }

    private static float[] burst(int n, float low, float high) {
        final float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = (i / 9) % 2 == 0 ? high * (0.82f + 0.18f * (i % 5) / 5.0f) : low;
        }
        return out;
    }
}
