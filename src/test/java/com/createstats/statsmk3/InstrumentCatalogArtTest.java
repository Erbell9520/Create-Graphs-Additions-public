package com.createstats.statsmk3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.instrument.Instrument;
import com.createstats.statsmk3.instrument.InstrumentCtx;
import com.createstats.statsmk3.instrument.InstrumentRegistry;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentCatalogArtTest {
    private static final File OUT = new File("fixture_preview");

    private static final Object[][] SHAPES = {
            {"gallery card", 133, 42},
            {"wide strip", 168, 22},
            {"small square", 62, 62},
            {"tall tile", 96, 128},
    };

    private static final int CELL_W = 184;
    private static final int CELL_H = 142;
    private static final int NAME_W = 132;

    @Test
    void drawsEveryInstrumentAtEveryShape() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final List<Instrument> all = InstrumentRegistry.all();

        final BufferedImage sheet = new BufferedImage(
                48 + NAME_W + SHAPES.length * CELL_W,
                80 + all.size() * CELL_H, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        caption(g, "Mk III instrument catalog. Six channels, one alerting, "
                + "the tile bound to the third.", 24, 26, 0xECEEF2);
        for (int c = 0; c < SHAPES.length; c++) {
            caption(g, (String) SHAPES[c][0] + "  " + SHAPES[c][1] + " x " + SHAPES[c][2],
                    24 + NAME_W + c * CELL_W, 48, 0x98A0AD);
        }

        for (int i = 0; i < all.size(); i++) {
            final Instrument instrument = all.get(i);
            final int y = 62 + i * CELL_H;
            caption(g, instrument.displayName(), 24, y + 20, 0xECEEF2);
            caption(g, instrument.description(), 24, y + 34, 0x5F6773);
            caption(g, "min " + (int) instrument.minSize() + " px", 24, y + 48, 0x5F6773);

            for (int c = 0; c < SHAPES.length; c++) {
                final int w = (int) SHAPES[c][1];
                final int h = (int) SHAPES[c][2];
                g.drawImage(render(instrument, w, h, model(6)),
                        24 + NAME_W + c * CELL_W, y + (CELL_H - h) / 2, null);
            }
        }

        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_instruments.png"));
    }

    @Test
    void survivesEveryDegenerateShapeOfData() {
        final CountingSurface s = new CountingSurface();
        final int[][] areas = {{1, 1}, {8, 4}, {24, 12}, {133, 42}, {168, 22}, {62, 62},
                {96, 128}, {320, 240}};
        for (final Instrument instrument : InstrumentRegistry.all()) {
            for (final InstrumentCtx ctx : degenerate()) {
                for (final int[] area : areas) {
                    for (final Lod lod : new Lod[]{Lod.FULL, Lod.REDUCED, Lod.preview(48)}) {
                        ctx.lod = lod;
                        s.reset();
                        instrument.draw(s, new Rect(0.0f, 0.0f, area[0], area[1]), ctx);
                    }
                }
            }
        }
    }

    @Test
    void everyInstrumentDrawsSomethingAtCardSize() {
        final CountingSurface s = new CountingSurface();
        final InstrumentCtx ctx = model(6);
        ctx.lod = Lod.preview(48);
        for (final Instrument instrument : InstrumentRegistry.all()) {
            s.reset();
            instrument.draw(s, new Rect(0.0f, 0.0f, 133.0f, 42.0f), ctx);
            assertTrue(s.triangles + s.textCalls > 0,
                    instrument.id() + " drew nothing on a gallery card");
        }
    }

    @Test
    void multiChannelInstrumentsLeadWithTheirBinding() {
        final InstrumentCtx ctx = model(6);
        ctx.primary = ctx.all.get(3);
        assertEquals(ctx.all.get(3).id, ctx.series().get(0).id,
                "the bound channel has to come first");
        assertEquals(ctx.all.size(), ctx.series().size(), "no channel may be lost in the reorder");

        final List<String> seen = new ArrayList<>();
        for (final ChannelView ch : ctx.series()) {
            assertTrue(!seen.contains(ch.id), ch.id + " appears twice");
            seen.add(ch.id);
        }

        ctx.primary = ctx.all.get(0);
        assertEquals(ctx.all, ctx.series());
    }

    private static BufferedImage render(Instrument instrument, int w, int h, InstrumentCtx ctx) {
        final RasterSurface s = new RasterSurface(w, h, Tokens.SURFACE & 0xFFFFFF);
        ctx.lod = Lod.preview(48);
        final Rect area = new Rect(0.0f, 0.0f, w, h).inset(3.0f);
        s.clip(area);
        instrument.draw(s, area, ctx);
        s.unclip();
        return s.finish();
    }

    private static InstrumentCtx model(int channels) {
        final String[] labels = {"Stress %", "Current Stress", "RPM", "Capacity (SU)",
                "Net FE/t", "Stored FE", "Fill Level %", "Items / min"};
        final InstrumentCtx ctx = new InstrumentCtx();
        ctx.all = new ArrayList<>();
        for (int i = 0; i < channels; i++) {
            final ChannelView ch = new ChannelView("src_a_auto_m" + i);
            ch.label = labels[i % labels.length];
            ch.unit = i == 0 ? "%" : "SU";
            ch.historyCount = 120;
            ch.history = new float[ch.historyCount];
            for (int t = 0; t < ch.historyCount; t++) {
                ch.history[t] = i == 4
                        ? 61.0f
                        : (float) (40.0 + i * 9 + t * 0.28
                                + Math.sin(t * 0.21 + i) * 9.0
                                + (t > 90 ? 14.0 : 0.0));
            }
            ch.value = ch.history[ch.historyCount - 1];
            ch.min = 0.0f;
            ch.max = 130.0f;
            ch.alerting = i == 2;
            ctx.all.add(ch);
        }

        ctx.primary = ctx.all.get(Math.min(2, channels - 1));
        ctx.time = 12.5f;
        return ctx;
    }

    private static List<InstrumentCtx> degenerate() {
        final List<InstrumentCtx> out = new ArrayList<>();
        out.add(model(1));
        out.add(model(2));
        out.add(model(60));

        final InstrumentCtx empty = model(3);
        for (final ChannelView ch : empty.all) {
            ch.history = new float[0];
            ch.historyCount = 0;
        }
        out.add(empty);

        final InstrumentCtx one = model(3);
        for (final ChannelView ch : one.all) {
            ch.history = new float[]{ch.value};
            ch.historyCount = 1;
        }
        out.add(one);

        final InstrumentCtx flat = model(3);
        for (final ChannelView ch : flat.all) {
            java.util.Arrays.fill(ch.history, 7.0f);
            ch.value = 7.0f;
            ch.min = 7.0f;
            ch.max = 7.0f;
        }
        out.add(flat);

        final InstrumentCtx alone = model(1);
        alone.all = List.of();
        out.add(alone);
        return out;
    }

    private static void caption(Graphics2D g, String text, int x, int y, int color) {
        g.setColor(new Color(color));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString(text, x, y);
    }
}
