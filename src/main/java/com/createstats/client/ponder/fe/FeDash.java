package com.createstats.client.ponder.fe;

import com.createstats.client.ponder.Ease;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.gui.pdu.PduGlyphs;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.power.FePriority;
import com.createstats.power.ponder.FePonderChapters.Warp;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// on the screen, the in-world column was unreadable
public final class FeDash {
    static final int GAP = 14;

    static final int MARGIN = 12;
    static final int WORDS_Y = 5;
    static final int BAR_Y = 16;
    static final int BAR_H = 5;
    static final int FIGURE_Y = 27;
    static final int STRIP_Y = 38;
    static final int STRIP_H = 10;

    static final int CLOCK_X = 21;
    static final int CLOCK_Y = 60;
    static final int STEEL = 0x7C8C9C;
    static final int AMBER = 0xFFBA3C;
    static final int RED = 0xFF4A4A;
    static final int FLOOR = 0xFF6A3C;
    static final int MARK = 0xE4E9EF;
    static final int TRACK = 0x3A4550;
    private static final float DEPTH = 300f;

    private FeDash() {
    }

    static int left(Font font, String title) {
        return 55 + 70 + Math.min(180, font.width(title)) + GAP;
    }

    static int fillColour(double f) {
        return f < 0.15 ? RED : f < 0.30 ? AMBER : STEEL;
    }

    public static final class Model {
        final Recording rec;
        final Warp warp;

        final boolean marks;

        final Vec3[] from;

        final int[] order;
        final int total;
        double fill;
        double prevFill;
        float grow;
        float prevGrow;
        int appear;

        int flyStart = Integer.MAX_VALUE;
        final double[] fly;
        final double[] prevFly;
        final double[] dim;
        final double[] prevDim;

        double served;
        double prevServed;

        int servedNow;
        final FeLabels.Fader clock = new FeLabels.Fader(0);

        public Model(Recording rec, Warp warp, boolean marks, Vec3[] from) {
            this.rec = rec;
            this.warp = warp;
            this.marks = marks;
            this.from = from;
            final int n = rec.machines();
            final List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < n; i++) idx.add(i);
            idx.sort(Comparator.<Integer>comparingInt(i -> rec.machine(i).tier())
                    .thenComparingInt(i -> rec.machine(i).draw()).thenComparingInt(i -> i));
            order = idx.stream().mapToInt(Integer::intValue).toArray();
            int t = 0;
            for (int i = 0; i < n; i++) t += rec.machine(i).draw();
            total = t;
            fly = new double[n];
            prevFly = new double[n];
            dim = new double[n];
            prevDim = new double[n];
        }

        void settle(int t) {
            final int r = rec.at(warp.tick(t));
            double s = 0.0;
            for (int i = 0; i < dim.length; i++) {
                dim[i] = prevDim[i] = rec.admitted[i][r] ? 0.0 : 1.0;
                if (rec.admitted[i][r]) s += rec.machine(i).draw();
            }
            served = prevServed = s;
            servedNow = (int) s;
            prevFill = fill;
            prevGrow = grow;
        }

        public float fill(int t) {
            return rec.fill(warp.tick(t));
        }

        boolean strip() {
            return flyStart != Integer.MAX_VALUE;
        }

        void step(int t) {
            final int r = rec.at(warp.tick(t));
            prevFill = fill;
            prevGrow = grow;
            fill = fill(t);
            grow = Math.min(1f, (t - appear + 1) / 12f);
            clock.follow(FeLabels.clockText(warp, t), FeLabels.CLOCK, false);
            prevServed = served;
            double s = 0.0;
            for (int k = 0; k < order.length; k++) {
                final int i = order[k];
                prevFly[i] = fly[i];
                prevDim[i] = dim[i];
                fly[i] = strip() ? Mth.clamp((t - flyStart - 6.0 * k) / 24.0, 0.0, 1.0) : 0.0;
                final boolean on = rec.admitted[i][r];
                dim[i] += ((on ? 0.0 : 1.0) - dim[i]) * 0.2;
                if (on) s += rec.machine(i).draw();
            }
            served += (s - served) * (Math.abs(s - served) > 1.0 ? 0.3 : 1.0);
            servedNow = (int) s;
        }

        double startOf(int i) {
            double at = 0.0;
            for (int k : order) {
                if (k == i) return at;
                at += rec.machine(k).draw();
            }
            return at;
        }

        int servedNow() {
            return servedNow;
        }
    }

    static final class Board extends FeOverlay {
        Model m;

        @Override
        public void render(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float fade) {
            if (m == null || fade < 0.05f) return;
            final Font font = screen.getFontRenderer();
            final PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, 0, DEPTH);
            final int x0 = left(font, scene.getTitle());
            final int x1 = screen.width - MARGIN;
            final int w = x1 - x0;
            final float grow = (float) Ease.OUT_CUBIC.clamped(Mth.lerp(pt, m.prevGrow, m.grow)) * fade;
            if (w > 40 && grow > 0.01f) {
                final double f = Mth.clamp(Mth.lerp(pt, m.prevFill, m.fill), 0.0, 1.0);
                final int reach = x0 + Math.round(w * grow);
                g.fill(x0, BAR_Y, reach, BAR_Y + BAR_H, withAlpha(TRACK, 0.8f * grow));
                g.fill(x0, BAR_Y, x0 + (int) Math.round(w * grow * f), BAR_Y + BAR_H, withAlpha(fillColour(f), grow));
                final String figure = String.format(Locale.ROOT, "buffer %d%%", Math.round(f * 100.0));
                g.drawString(font, figure, x1 - font.width(figure), WORDS_Y, withAlpha(PduTheme.TEXT, grow), false);
                if (m.marks) {
                    tick(g, font, x0 + (int) Math.round(w * m.rec.setup.tuning().floorPct() / 100.0), "floor", FLOOR,
                            grow, x0, x1 - font.width(figure) - 6);
                    tick(g, font, x0 + (int) Math.round(w * m.rec.setup.tuning().restorePct() / 100.0), "restore mark",
                            MARK, grow, x0, x1 - font.width(figure) - 6);
                }
            }
            if (w > 40 && m.strip()) strip(scene, g, font, pt, fade, x0, x1);
            final float c = (float) Ease.SMOOTH.clamped(Mth.lerp(pt, m.clock.prevLevel, m.clock.level)) * fade;
            if (c > 0.05f && !m.clock.shown.isEmpty()) {
                final int tw = font.width(m.clock.shown) + 4;
                g.fill(CLOCK_X, CLOCK_Y, CLOCK_X + tw, CLOCK_Y + 10, Math.round(0x66 * c) << 24);
                g.drawString(font, m.clock.shown, CLOCK_X + 2, CLOCK_Y + 1, withAlpha(FeLabels.CLOCK, c), false);
            }
            ps.popPose();
        }

        private static void tick(GuiGraphics g, Font font, int x, String word, int rgb, float a, int lo, int hi) {
            g.fill(x, BAR_Y - 2, x + 1, BAR_Y + BAR_H + 2, withAlpha(rgb, a));
            final int tw = font.width(word);
            final int at = Math.max(lo, Math.min(hi - tw, x - tw / 2));
            g.drawString(font, word, at, WORDS_Y, withAlpha(rgb, a), false);
        }

        private void strip(PonderScene scene, GuiGraphics g, Font font, float pt, float fade, int x0, int x1) {
            final int w = x1 - x0;
            final double per = w / (double) Math.max(1, m.total);
            double first = 0.0;
            double all = 1.0;
            for (int i = 0; i < m.fly.length; i++) {
                final double f = Mth.lerp(pt, m.prevFly[i], m.fly[i]);
                first = Math.max(first, f);
                all = Math.min(all, f);
            }
            if (first <= 0.0) return;
            g.fill(x0, STRIP_Y, x1, STRIP_Y + STRIP_H, withAlpha(PduTheme.BG_DEEP, (float) Math.min(1.0, first * 2) * fade));
            for (int i : m.order) {
                final double f = Mth.lerp(pt, m.prevFly[i], m.fly[i]);
                if (f <= 0.0) continue;
                final int bandW = Math.max(2, (int) Math.round(m.rec.machine(i).draw() * per));
                final int slotX = x0 + (int) Math.round(m.startOf(i) * per);
                final double e = Ease.QUINTIC.clamped(f);
                int bx = slotX;
                int by = STRIP_Y;
                if (f < 1.0) {
                    final Vec2 src = scene.getTransform().sceneToScreen(m.from[i], pt);
                    bx = (int) Math.round(Mth.lerp(e, src.x - bandW / 2.0, slotX));
                    by = (int) Math.round(Mth.lerp(e, src.y - 14.0, STRIP_Y));
                }
                final double d = Mth.clamp(Mth.lerp(pt, m.prevDim[i], m.dim[i]), 0.0, 1.0);
                final int rgb = PduGlyphs.tierColour(FePriority.byOrdinal(m.rec.machine(i).tier())) & 0xFFFFFF;
                final float a = (float) Math.min(1.0, f * 1.6) * fade;
                g.fill(bx, by, bx + bandW, by + STRIP_H, withAlpha(rgb, (float) Mth.lerp(d, 1.0, 0x3A / 255.0) * a));
                if (d > 0.3) hatch(g, bx, by, bx + bandW, by + STRIP_H, (float) ((d - 0.3) / 0.7) * a);
                g.fill(bx + bandW - 1, by, bx + bandW, by + STRIP_H, withAlpha(PduTheme.BG_DEEP, a));
            }

            if (all >= 1.0) {
                final double s = Mth.lerp(pt, m.prevServed, m.served);
                final int rx = x0 + (int) Math.round(s * per);
                g.fill(rx, STRIP_Y - 3, rx + 1, STRIP_Y + STRIP_H + 3, withAlpha(PduTheme.TEXT, fade));
                final String figure = String.format(Locale.ROOT, "%,d of %,d FE/t served", m.servedNow(), m.total);
                g.drawString(font, figure, x1 - font.width(figure), FIGURE_Y,
                        withAlpha(m.servedNow() < m.total ? PduTheme.WARN : PduTheme.TEXT_DIM, fade), false);
            }
        }

        private static void hatch(GuiGraphics g, int x0, int y0, int x1, int y1, float a) {
            final int h = y1 - y0;
            final int c = Math.round(0x30 * a) << 24;
            for (int x = x0 - h; x < x1; x += 4) {
                for (int row = 0; row < h; row++) {
                    final int px = x + row;
                    if (px >= x0 && px < x1) g.fill(px, y0 + row, px + 1, y0 + row + 1, c);
                }
            }
        }
    }

    static int withAlpha(int rgb, float alpha) {
        return FeLabels.withAlpha(rgb, alpha);
    }

    static final int ROW = 22;

    static int chargeColour(double f) {
        return f < 0.2 ? RED : f < 0.5 ? AMBER : 0x58DC7A;
    }

    public record Row(java.util.function.IntToDoubleFunction fill, java.util.function.IntFunction<String> words,
                      java.util.function.IntPredicate shown, int[] marks) {
        public Row(java.util.function.IntToDoubleFunction fill, java.util.function.IntFunction<String> words,
                   java.util.function.IntPredicate shown) {
            this(fill, words, shown, new int[0]);
        }
    }

    public static String rowWords(String name, double fill, int flow) {
        final String pct = String.format(Locale.ROOT, "%s %d%%", name, Math.round(Mth.clamp(fill, 0.0, 1.0) * 100.0));
        if (flow > 0) return pct + String.format(Locale.ROOT, ", charging %,d FE/t", flow);
        if (flow < 0) return pct + String.format(Locale.ROOT, ", discharging %,d FE/t", -flow);
        return pct;
    }

    public static final class Banks {
        final Warp warp;
        final List<Row> rows;
        final double[] fill;
        final double[] prevFill;
        final float[] grow;
        final float[] prevGrow;
        final String[] words;
        final FeLabels.Fader clock = new FeLabels.Fader(0);

        public Banks(Warp warp, List<Row> rows) {
            this.warp = warp;
            this.rows = rows;
            fill = new double[rows.size()];
            prevFill = new double[rows.size()];
            grow = new float[rows.size()];
            prevGrow = new float[rows.size()];
            words = new String[rows.size()];
        }

        void step(int t) {
            for (int k = 0; k < rows.size(); k++) {
                final Row row = rows.get(k);
                prevFill[k] = fill[k];
                prevGrow[k] = grow[k];
                fill[k] = row.fill().applyAsDouble(t);
                grow[k] = row.shown().test(t) ? Math.min(1f, grow[k] + 1f / 12f) : Math.max(0f, grow[k] - 1f / 8f);

                if (row.shown().test(t) || words[k] == null) words[k] = row.words().apply(t);
            }
            clock.follow(FeLabels.clockText(warp, t), FeLabels.CLOCK, false);
        }

        int bottom() {
            int last = -1;
            for (int k = 0; k < grow.length; k++) if (grow[k] > 0.01f) last = k;
            return last < 0 ? 0 : BAR_Y + BAR_H + 2 + ROW * last;
        }
    }

    static final class BankBoard extends FeOverlay {
        Banks m;

        @Override
        public void render(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float fade) {
            if (m == null || fade < 0.05f) return;
            final Font font = screen.getFontRenderer();
            final PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, 0, DEPTH);
            final int x0 = left(font, scene.getTitle());
            final int x1 = screen.width - MARGIN;
            final int w = x1 - x0;
            for (int k = 0; k < m.rows.size() && w > 40; k++) {
                final float grow = (float) Ease.OUT_CUBIC.clamped(Mth.lerp(pt, m.prevGrow[k], m.grow[k])) * fade;
                if (grow <= 0.01f) continue;
                final int dy = ROW * k;
                final double f = Mth.clamp(Mth.lerp(pt, m.prevFill[k], m.fill[k]), 0.0, 1.0);
                g.fill(x0, BAR_Y + dy, x0 + Math.round(w * grow), BAR_Y + BAR_H + dy, withAlpha(TRACK, 0.8f * grow));
                g.fill(x0, BAR_Y + dy, x0 + (int) Math.round(w * grow * f), BAR_Y + BAR_H + dy,
                        withAlpha(chargeColour(f), grow));
                for (int pct : m.rows.get(k).marks()) {
                    final int x = x0 + (int) Math.round(w * grow * pct / 100.0);
                    g.fill(x, BAR_Y + dy - 2, x + 1, BAR_Y + BAR_H + dy + 2, withAlpha(MARK, grow));
                }
                final String words = m.words[k] == null ? "" : m.words[k];
                g.drawString(font, words, x1 - font.width(words), WORDS_Y + dy, withAlpha(PduTheme.TEXT, grow), false);
            }
            final float c = (float) Ease.SMOOTH.clamped(Mth.lerp(pt, m.clock.prevLevel, m.clock.level)) * fade;
            if (c > 0.05f && !m.clock.shown.isEmpty()) {
                final int tw = font.width(m.clock.shown) + 4;
                g.fill(CLOCK_X, CLOCK_Y, CLOCK_X + tw, CLOCK_Y + 10, Math.round(0x66 * c) << 24);
                g.drawString(font, m.clock.shown, CLOCK_X + 2, CLOCK_Y + 1, withAlpha(FeLabels.CLOCK, c), false);
            }
            ps.popPose();
        }
    }

    public static KitInstruction banks(Banks model, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private BankBoard board;

            @Override
            protected void begin(PonderScene scene) {
                board = new BankBoard();
                board.m = model;
                board.setVisible(true);
                scene.addElement(board);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (board == null) return;
                model.step(tick);
                if (last) board.setVisible(false);
            }
        };
    }

    public static KitInstruction show(Model model, int stripFrom, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Board board;

            @Override
            protected void begin(PonderScene scene) {
                model.appear = start;
                model.flyStart = stripFrom;
                board = new Board();
                board.m = model;
                board.setVisible(true);
                scene.addElement(board);
                model.step(start);
                model.settle(start);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (board == null) return;
                model.step(tick);
                if (last) board.setVisible(false);
            }
        };
    }
}
