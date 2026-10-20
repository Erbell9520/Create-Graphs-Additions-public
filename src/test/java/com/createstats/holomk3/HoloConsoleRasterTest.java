package com.createstats.holomk3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.createstats.blockentity.HolographicProjectorBE.DataChannel;
import com.createstats.data.MetricType;
import com.createstats.holomk3.gui.HoloConsole;
import com.createstats.holomk3.scene.SceneBuilder;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.Widget;
import com.createstats.util.PreviewImages;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoloConsoleRasterTest {
    private static final File OUT = new File("fixture_preview");
    private static final int W = 660;
    private static final int H = 400;

    @Test
    void consoleSheet() throws IOException {
        OUT.mkdirs();
        final HoloConsole.Tab[] tabs = HoloConsole.Tab.values();
        final BufferedImage sheet = new BufferedImage(W * tabs.length + 12 * (tabs.length + 1),
                H + 34, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161A));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        for (int i = 0; i < tabs.length; i++) {
            final HoloConsole.Model m = model(tabs[i]);
            g.drawImage(draw(m), 12 + i * (W + 12), 12, null);
            g.setColor(new Color(0xA8B2BE));
            g.drawString(tabs[i].label, 14 + i * (W + 12), H + 26);
        }
        g.dispose();
        final File file = new File(OUT, "holo_mk3_console.png");
        PreviewImages.write(sheet, "PNG", file);
        System.out.println("wrote " + file.getPath());
    }

    @Test
    void everyControlIsInsideTheBody() {
        for (final HoloConsole.Tab tab : HoloConsole.Tab.values()) {
            final HoloConsole.Model m = model(tab);
            final HoloConsole.Layout layout = HoloConsole.layout(W, H);
            final List<Widget> widgets = HoloConsole.controls(m, layout);
            assertFalse(widgets.isEmpty(), "no controls at all on " + tab);
            for (final Widget widget : widgets) {
                final var b = widget.bounds();
                assertTrue(b.x >= layout.body().x - 0.5f && b.right() <= layout.body().right() + 0.5f,
                        tab + ": a control runs outside the body horizontally, " + b);
                assertTrue(b.y >= layout.body().y - 0.5f && b.bottom() <= layout.body().bottom() + 0.5f,
                        tab + ": a control runs outside the body vertically, " + b);
            }

            assertTrue(HoloConsole.labels(tab).length >= widgets.size(),
                    tab + ": " + widgets.size() + " controls but only "
                            + HoloConsole.labels(tab).length + " labels");
        }
    }

    private static BufferedImage draw(HoloConsole.Model m) {
        final RasterSurface s = new RasterSurface(W, H, 0x1A1D22);
        final HoloConsole.Layout layout = HoloConsole.layout(W, H);
        final List<Widget> widgets = HoloConsole.controls(m, layout);
        for (final Widget widget : widgets) {
            widget.update(1.0f);
        }
        HoloConsole.paint(s, m, layout, widgets, 4.0f);
        return s.finish();
    }

    private static HoloConsole.Model model(HoloConsole.Tab tab) {
        final HoloConsole.Model m = new HoloConsole.Model();
        m.tab = tab;
        m.position = "-184, 71, 302";
        m.power = 0.72f;
        m.scanEnabled = true;
        m.redstoneEnabled = true;
        m.cfg.setStyle(HoloMk3Settings.Style.VECTOR);

        final List<DataChannel> data = channels();
        for (final DataChannel ch : data) {
            m.channels.add(new HoloConsole.Channel(ch.getId(), ch.getLabel(),
                    ch.getStore().getMetricType().formatValue(ch.getStore().getLatestValue()), true));
        }
        m.cfg.setFocusChannelId(data.get(0).getId());
        m.preview = SceneBuilder.build(data, Set.of(), m.cfg, 5000L, 4.0f, 1.0f, 96);
        m.dirty = false;
        return m;
    }

    private static List<DataChannel> channels() {
        final String[][] spec = {
                {"src_stress_1_auto_stress_pct", "Stress %"},
                {"src_speed_2_auto_rpm", "RPM"},
                {"src_tank_3_auto_tank_fill", "Tank Fill %"},
        };
        final MetricType[] kinds = {MetricType.STRESS_PERCENT, MetricType.RPM,
                MetricType.FILL_LEVEL};
        final List<DataChannel> out = new java.util.ArrayList<>();
        for (int c = 0; c < spec.length; c++) {
            final DataChannel ch = new DataChannel(spec[c][0], spec[c][1]);
            ch.getStore().setMetricType(kinds[c]);
            for (int i = 0; i < 200; i++) {
                final float v = switch (c) {
                    case 0 -> 40.0f + 38.0f * (i / 200.0f) + 4.0f * (float) Math.sin(i * 0.4);
                    case 1 -> 16.0f * (1 + i / 50);
                    default -> 54.0f + 32.0f * (float) Math.sin(i * 0.03);
                };
                ch.getStore().addSample(v, 100L + i * 20L);
            }
            out.add(ch);
        }
        return out;
    }
}
