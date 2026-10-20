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

import com.createstats.statsmk3.instrument.InstrumentCtx;
import com.createstats.statsmk3.instrument.InstrumentRegistry;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizonTest {
    private static final int GROUND = Tokens.CANVAS & 0xFFFFFF;

    private static final int INK_ABOVE = 10;

    @Test
    void aSpikeInAnOtherwiseFlatWindowSurvivesTheRobustScale() {
        final ChannelView ch = flat(120, 100.0f);
        ch.history[74] = 400.0f;
        ch.value = 100.0f;

        final BufferedImage img = render(ctx(ch), 160, 20);
        assertTrue(inkRows(img, 160, 20) >= 12,
                "the spike has to reach most of the strip, not be flattened into the reference rule");
    }

    @Test
    void oneOutlierDoesNotFlattenTheWindowAroundIt() {
        final ChannelView calm = wobble(120, 100.0f, 6.0f);
        final ChannelView spiked = wobble(120, 100.0f, 6.0f);
        spiked.history[110] = 900.0f;

        final int quiet = render(ctx(calm), 160, 20).getWidth() * 3 / 4;
        final long calmInk = ink(render(ctx(calm), 160, 20), 0, quiet);
        final long spikedInk = ink(render(ctx(spiked), 160, 20), 0, quiet);

        assertTrue(spikedInk >= calmInk * 0.6,
                "an outlier at the end of the window must not squash the stretch before it: "
                        + spikedInk + " against " + calmInk);
    }

    @Test
    void drawsNothingPastItsOwnEdge() {
        final int w = 200;
        final int h = 40;
        final RasterSurface s = new RasterSurface(w, h, GROUND);
        final Rect area = new Rect(20.0f, 8.0f, 120.0f, 18.0f);
        InstrumentRegistry.get("horizon").draw(s, area, ctx(wobble(120, 100.0f, 20.0f)));
        final BufferedImage img = s.finish();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (area.contains(x + 0.5, y + 0.5)) {
                    continue;
                }
                assertEquals(GROUND, img.getRGB(x, y) & 0xFFFFFF,
                        "painted outside the tile at " + x + "," + y);
            }
        }
    }

    @Test
    void stacksOnATallTileAndLeadsWithItsBinding() {
        final InstrumentCtx ctx = panel(6);
        ctx.primary = ctx.all.get(3);

        final List<String> tall = textOf(ctx, 96.0f, 128.0f);
        assertTrue(tall.size() >= 6, "a tall tile draws one label per stacked row: " + tall);
        assertEquals(ctx.all.get(3).label, tall.get(0),
                "the stack has to lead with the channel the tile is labelled with");
        assertTrue(tall.contains(ctx.all.get(0).label),
                "a promoted binding must not drop the channel it displaced: " + tall);
    }

    @Test
    void doesNotStackWhereARowCouldNotBeNamed() {
        final InstrumentCtx ctx = panel(6);
        ctx.primary = ctx.all.get(0);

        assertTrue(textOf(ctx, 168.0f, 22.0f).size() <= 1,
                "a long thin strip is the one shape this instrument is for; it must not become six");
        assertTrue(textOf(ctx, 56.0f, 96.0f).size() <= 1,
                "too narrow for a name is too narrow to stack");
    }

    @Test
    void aSmallSquareStillStatesItsScale() {
        final InstrumentCtx ctx = panel(1);
        final List<String> text = textOf(ctx, 56.0f, 56.0f);
        assertTrue(text.stream().anyMatch(t -> t.startsWith("±")),
                "a tile with room for a header row has to state its scale: " + text);
    }

    @Test
    void drawsASheetOfBothModes() throws IOException {
        final Object[][] shapes = {
                {"the strip it was added for", 210, 11},
                {"tall enough to state its scale", 210, 30},
                {"small square", 62, 62},
                {"stacked, narrow", 96, 132},
                {"stacked, room for the scale column", 240, 132},
        };
        final File out = new File("fixture_preview");
        if (!out.exists() && !out.mkdirs()) {
            throw new IOException("could not create " + out);
        }

        int height = 74;
        int width = 320;
        for (final Object[] shape : shapes) {
            height += (int) shape[2] + 46;
            width = Math.max(width, (int) shape[1] + 48);
        }
        final BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161B));
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        caption(g, "Horizon. A working panel: two steady, one swinging, one ramping, "
                + "one that spiked once, one flat.", 24, 28, 0xECEEF2);

        int y = 48;
        for (final Object[] shape : shapes) {
            final int w = (int) shape[1];
            final int h = (int) shape[2];
            caption(g, (String) shape[0] + "   " + w + " x " + h, 24, y + 12, 0x98A0AD);
            final RasterSurface s = new RasterSurface(w, h, Tokens.SURFACE & 0xFFFFFF);
            final Rect area = new Rect(0.0f, 0.0f, w, h);
            s.clip(area);
            InstrumentRegistry.get("horizon").draw(s, area, working());
            s.unclip();
            g.drawImage(s.finish(), 24, y + 20, null);
            y += h + 46;
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(out, "mk3_horizon.png"));
    }

    private static void caption(Graphics2D g, String text, int x, int y, int rgb) {
        g.setColor(new Color(rgb));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.drawString(text, x, y);
    }

    private static InstrumentCtx working() {
        final InstrumentCtx ctx = new InstrumentCtx();
        ctx.all = new ArrayList<>();
        final String[] labels = {"Stress %", "RPM", "Net FE/t", "Fill Level %", "Items / min",
                "Capacity (SU)"};
        for (int i = 0; i < labels.length; i++) {
            final ChannelView ch = new ChannelView("src_a_auto_m" + i);
            ch.label = labels[i];
            ch.unit = i == 0 ? "%" : "SU";
            ch.historyCount = 120;
            ch.history = new float[120];
            for (int t = 0; t < 120; t++) {
                ch.history[t] = switch (i) {
                    case 0 -> (float) (62.0 + Math.sin(t * 0.11) * 2.0);
                    case 1 -> (float) (128.0 + Math.sin(t * 0.09) * 1.4);
                    case 2 -> (float) (40.0 + Math.sin(t * 0.34) * 26.0 + Math.sin(t * 0.07) * 12.0);
                    case 3 -> (float) (18.0 + t * 0.52);
                    case 4 -> t == 88 ? 640.0f : (float) (74.0 + Math.sin(t * 0.2) * 3.0);
                    default -> 4096.0f;
                };
            }
            ch.value = ch.history[119];
            ch.min = 0.0f;
            ch.max = 4200.0f;
            ch.alerting = i == 4;
            ctx.all.add(ch);
        }
        ctx.primary = ctx.all.get(2);
        ctx.lod = Lod.FULL;
        return ctx;
    }

    private static BufferedImage render(InstrumentCtx ctx, int w, int h) {
        final RasterSurface s = new RasterSurface(w, h, GROUND);
        final Rect area = new Rect(0.0f, 0.0f, w, h);
        s.clip(area);
        InstrumentRegistry.get("horizon").draw(s, area, ctx);
        s.unclip();
        return s.finish();
    }

    private static long ink(BufferedImage img, int fromX, int toX) {
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = fromX; x < toX; x++) {
                if (luminance(img.getRGB(x, y)) > luminance(GROUND) + INK_ABOVE) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int inkRows(BufferedImage img, int w, int h) {
        int rows = 0;
        for (int y = 0; y < h; y++) {
            if (ink(img.getSubimage(0, y, w, 1), 0, w) > 0) {
                rows++;
            }
        }
        return rows;
    }

    private static int luminance(int rgb) {
        return (((rgb >> 16) & 0xFF) * 30 + ((rgb >> 8) & 0xFF) * 59 + (rgb & 0xFF) * 11) / 100;
    }

    private static List<String> textOf(InstrumentCtx ctx, float w, float h) {
        final Recorder s = new Recorder();
        InstrumentRegistry.get("horizon").draw(s, new Rect(0.0f, 0.0f, w, h), ctx);
        return s.drawn;
    }

    private static InstrumentCtx ctx(ChannelView ch) {
        final InstrumentCtx ctx = new InstrumentCtx();
        ctx.primary = ch;
        ctx.all = List.of(ch);
        ctx.lod = Lod.FULL;
        return ctx;
    }

    private static InstrumentCtx panel(int channels) {
        final InstrumentCtx ctx = new InstrumentCtx();
        ctx.all = new ArrayList<>();
        for (int i = 0; i < channels; i++) {
            final ChannelView ch = wobble(120, 40.0f + i * 12.0f, 5.0f + i);
            ch.label = "S" + i;
            ctx.all.add(ch);
        }
        ctx.primary = ctx.all.get(0);
        ctx.lod = Lod.FULL;
        return ctx;
    }

    private static ChannelView flat(int samples, float value) {
        final ChannelView ch = new ChannelView("src_a_auto_flat");
        ch.label = "Flat";
        ch.unit = "SU";
        ch.historyCount = samples;
        ch.history = new float[samples];
        java.util.Arrays.fill(ch.history, value);
        ch.value = value;
        ch.min = 0.0f;
        ch.max = value * 2.0f;
        return ch;
    }

    private static ChannelView wobble(int samples, float mid, float amplitude) {
        final ChannelView ch = new ChannelView("src_a_auto_w" + (int) mid);
        ch.label = "Wobble";
        ch.unit = "SU";
        ch.historyCount = samples;
        ch.history = new float[samples];
        for (int i = 0; i < samples; i++) {
            ch.history[i] = (float) (mid + Math.sin(i * 0.31) * amplitude
                    + Math.sin(i * 0.07) * amplitude * 0.4);
        }
        ch.value = ch.history[samples - 1];
        ch.min = 0.0f;
        ch.max = mid * 2.0f;
        return ch;
    }

    private static final class Recorder implements PanelSurface {
        private final List<String> drawn = new ArrayList<>();

        @Override
        public void tri(float x1, float y1, int c1, float x2, float y2, int c2,
                        float x3, float y3, int c3) {
        }

        @Override
        public void text(String s, float x, float y, int color, float scale) {
            if (s != null && !s.isEmpty()) {
                this.drawn.add(s);
            }
        }

        @Override
        public void textCenter(String s, float centerX, float y, int color, float scale) {
            text(s, centerX, y, color, scale);
        }

        @Override
        public void textRight(String s, float rightX, float y, int color, float scale) {
            text(s, rightX, y, color, scale);
        }

        @Override
        public float textWidth(String s, float scale) {
            return CountingSurface.advance(s, scale);
        }

        @Override
        public void clip(Rect r) {
        }

        @Override
        public void unclip() {
        }
    }
}
