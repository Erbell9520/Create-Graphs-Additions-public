package com.createstats.client.ponder.seismic;

import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.tuner.TunerIcons;
import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicTrace;
import com.createstats.tuner.TunerRange;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class SeismicOverlays {
    static final float DEPTH = 300f;

    static final int VEIL = 0x66000000;

    private SeismicOverlays() {
    }

    @FunctionalInterface
    public interface Anchor {
        Vec3 at(float pt);
    }

    static final class Tag extends SeismicOverlay {
        Anchor anchor;
        String text = "";
        int rgb = 0xFFFFFF;

        int dx;
        int dy = -12;

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float a) {
            if (anchor == null || text.isEmpty()) {
                return;
            }
            final Vec2 at = scene.getTransform().sceneToScreen(anchor.at(pt), pt);
            final Font font = screen.getFontRenderer();
            final PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(at.x + dx, at.y + dy, DEPTH);
            final int w = font.width(text) + 4;
            g.fill(-w / 2, -1, w - w / 2, 9, fill(VEIL, a));
            g.drawString(font, text, -w / 2 + 2, 0, argb(rgb, a), false);
            ps.popPose();
        }
    }

    public static KitInstruction tag(Anchor anchor, IntFunction<String> text, int rgb, int dx, int dy, int start, int ticks) {
        final Tag t = new Tag();
        t.anchor = anchor;
        t.rgb = rgb;
        t.dx = dx;
        t.dy = dy;
        return SeismicOverlay.show(t, (scene, o, tick, left) -> {
            o.text = text.apply(tick);
            o.fadeToward(left < FADE_OUT ? 0f : 1f);
        }, start, ticks);
    }

    static final int FADE_OUT = 9;

    static final class Hub extends SeismicOverlay {
        static final int W = 120;
        static final int H = 48;
        private static final int OUTLINE = 0xFF000000;
        private static final int HIGHLIGHT = 0xFFFFFFFF;
        private static final int FACE = 0xFFC6C6C6;
        private static final int RECESSED = 0xFF8B8B8B;
        private static final int SLOT_DARK = 0xFF373737;
        private static final int SHADOW = 0xFF555555;
        private static final int LABEL = 0xFF404040;
        private static final int WARN = 0xFFAA0000;

        Anchor anchor;

        int dx;

        String station = "";
        int range = SeismicCharge.MIN_RADIUS;

        float rise;

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float a) {
            if (anchor == null) {
                return;
            }
            final Vec2 at = scene.getTransform().sceneToScreen(anchor.at(pt), pt);
            final Font font = screen.getFontRenderer();
            final PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(Math.round(at.x + dx - W / 2f), Math.round(at.y - H / 2f + 6f * (1f - a)), DEPTH);
            final int x = 0;
            final int y = 0;
            g.fill(x, y, x + W, y + H, fill(OUTLINE, a));
            g.fill(x + 1, y + 1, x + W - 1, y + H - 1, fill(FACE, a));
            g.fill(x + 1, y + 1, x + W - 1, y + 2, fill(HIGHLIGHT, a));
            g.fill(x + 1, y + 1, x + 2, y + H - 1, fill(HIGHLIGHT, a));
            g.fill(x + 1, y + H - 2, x + W - 1, y + H - 1, fill(SHADOW, a));
            g.fill(x + W - 2, y + 1, x + W - 1, y + H - 1, fill(SHADOW, a));
            g.fill(x + 4, y + 4, x + 22, y + 22, fill(SLOT_DARK, a));
            g.fill(x + 5, y + 5, x + 22, y + 22, fill(HIGHLIGHT, a));
            g.fill(x + 5, y + 5, x + 21, y + 21, fill(RECESSED, a));
            final boolean tuned = !station.isEmpty();
            final String name;
            final String sub;
            if (tuned) {
                if (a > 0.6f) {
                    TunerIcons.draw(g, "minecraft:iron_ore", x + 5, y + 5, 16);
                }
                name = Component.translatable(Blocks.IRON_ORE.getDescriptionId()).getString();
                sub = Component.translatable("block.create_stats.seismic_scanner.tuner.depth",
                        SeismicCatalog.bowlBottom(SeismicStage.RIG_Y, SeismicCharge.clampRadius(range))).getString();
            } else {
                name = Component.translatable("create_stats.tuner.no_station").getString();
                sub = Component.translatable("create_stats.tuner.draws_nothing").getString();
            }
            ps.translate(0, 0, 10);
            final int textW = W - 30;
            g.drawString(font, font.plainSubstrByWidth(name, textW), x + 26, y + 5, argb(LABEL, a), false);
            g.drawString(font, font.plainSubstrByWidth(sub, textW), x + 26, y + 15, argb(SHADOW, a), false);
            final String cost = Component.translatable("block.create_stats.seismic_scanner.tuner.cost",
                    String.format(Locale.ROOT, "%,d", tuned ? SeismicCharge.burstFor(range) : 0)).getString();
            final String what = font.plainSubstrByWidth(Component.translatable("block.create_stats.seismic_scanner.tuner.range",
                    range).getString(), W - 12 - font.width(cost));
            g.drawString(font, what, x + 5, y + 26, argb(LABEL, a), false);
            g.drawString(font, cost, x + W - 5 - font.width(cost), y + 26, argb(LABEL, a), false);
            if (tuned && SeismicCatalog.bandMissed(SeismicPonderSurvey.IRON, SeismicStage.RIG_Y, SeismicCharge.clampRadius(range))) {
                final String note = Component.translatable("block.create_stats.seismic_scanner.tuner.band",
                        SeismicCatalog.densest(SeismicPonderSurvey.IRON)).getString();
                g.drawString(font, font.plainSubstrByWidth(note, W - 10), x + 5, y + 37, argb(WARN, a), false);
            } else {
                final TunerRange r = SeismicScannerBE.RANGE;
                final int n = r.notches();
                final int pitch = Math.max(3, Math.min(7, (W - 10) / Math.max(1, n)));
                final int on = r.notchOf(range);
                for (int i = 0; i < n; i++) {
                    final int px = x + 5 + i * pitch;
                    g.fill(px, y + 38, px + pitch - 1, y + 42, fill(i == on ? OUTLINE : SHADOW, a));
                    g.fill(px + 1, y + 39, px + pitch - 2, y + 41, fill(i <= on ? HIGHLIGHT : RECESSED, a));
                }
            }
            ps.popPose();
        }
    }

    public static KitInstruction hub(Anchor anchor, int dx, IntFunction<String> station, IntFunction<Integer> range,
                                     int start, int ticks) {
        final Hub h = new Hub();
        h.anchor = anchor;
        h.dx = dx;
        return SeismicOverlay.show(h, (scene, o, tick, left) -> {
            o.station = station.apply(tick);
            o.range = range.apply(tick);
            o.fadeToward(left < FADE_OUT ? 0f : 1f);
        }, start, ticks);
    }

    static final class Recorder extends SeismicOverlay {
        static final int GAP = 14;
        static final int MARGIN = 12;
        static final int TOP = 9;
        static final int HEIGHT = 30;

        static final int PAPER = 0xFFE9E3D2;
        static final int RULE = 0xFFC9C0A8;
        static final int INK = 0xFF1C2954;
        static final int BRASS = 0xFFB8913A;
        static final int WORDS = 0xE0E0E0;

        String title = "";
        String label = "";

        int blowAt = Integer.MIN_VALUE / 2;
        double strength;
        int[] echoes = new int[0];

        int span = 48;
        int tick;

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float a) {
            final Font font = screen.getFontRenderer();
            final int w = screen.width;
            final int x0 = 55 + 70 + Math.min(180, font.width(title)) + GAP;
            final int x1 = w - MARGIN;
            if (x1 - x0 < 60) {
                return;
            }
            final PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, 0, DEPTH);
            g.drawString(font, label, x0, TOP - 9 + 1, argb(WORDS, a * 0.85f), false);
            final int y0 = TOP + 2;
            final int y1 = y0 + HEIGHT;

            g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, fill(BRASS, a));
            g.fill(x0, y0, x1, y1, fill(PAPER, a));
            final double now = tick + pt;
            final double perTick = (x1 - x0 - 6) / (double) span;

            for (int k = (int) Math.floor(now / 20.0) * 20; k > now - span - 20; k -= 20) {
                final int rx = (int) Math.round(x1 - 6 - (now - k) * perTick);
                if (rx > x0 && rx < x1 - 6) {
                    g.fill(rx, y0 + 1, rx + 1, y1 - 1, fill(RULE, a));
                }
            }
            final int mid = (y0 + y1) / 2;
            g.fill(x0 + 1, mid, x1 - 6, mid + 1, fill(RULE, a * 0.6f));

            final double amp = (HEIGHT / 2.0) - 2.5;
            double lastX = Double.NaN;
            double lastY = Double.NaN;
            for (double px = 0; px <= x1 - 6 - x0; px += 0.5) {
                final double age = px / perTick;
                final double when = now - age;
                final double yv = SeismicTrace.at(when, blowAt, when >= blowAt ? strength : 0.0, echoes);
                final double sx = x1 - 6 - px;
                final double sy = mid - yv * amp;
                if (!Double.isNaN(lastX)) {
                    stroke(g, lastX, lastY, sx, sy, fill(INK, a));
                }
                lastX = sx;
                lastY = sy;
            }

            final double penY = mid - SeismicTrace.at(now, blowAt, now >= blowAt ? strength : 0.0, echoes) * amp;
            final int py = (int) Math.round(penY);
            g.fill(x1 - 6, py - 1, x1 - 1, py + 1, fill(BRASS, a));
            g.fill(x1 - 2, y0, x1 - 1, y1, fill(BRASS, a * 0.7f));
            ps.popPose();
        }

        private static void stroke(GuiGraphics g, double xa, double ya, double xb, double yb, int colour) {
            final int x = (int) Math.floor(Math.min(xa, xb));
            final int top = (int) Math.floor(Math.min(ya, yb));
            final int bottom = (int) Math.ceil(Math.max(ya, yb));
            g.fill(x, top, x + 1, Math.max(top + 1, bottom), colour);
        }
    }

    public static KitInstruction recorder(String title, String label, IntPredicate blown, int blowAt, double strength,
                                          int[] echoes, int span, int start, int ticks) {
        final Recorder r = new Recorder();
        r.title = title;
        r.label = label;
        r.strength = strength;
        r.span = span;
        return SeismicOverlay.show(r, (scene, o, tick, left) -> {
            o.tick = tick;
            if (blown.test(tick)) {
                o.blowAt = blowAt;
                o.echoes = echoes;
            }
            o.fadeToward(left < FADE_OUT ? 0f : 1f);
        }, start, ticks);
    }

    static final class SlowNote extends SeismicOverlay {
        String text = "";

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float a) {
            final PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, 0, DEPTH);
            final Font font = screen.getFontRenderer();
            g.fill(19, 58, 23 + font.width(text) + 2, 69, fill(VEIL, a));
            g.drawString(font, text, 22, 60, argb(0xE8E8E8, a), false);
            ps.popPose();
        }
    }

    public static KitInstruction slowNote(String text, IntPredicate slow, int start, int ticks) {
        final SlowNote n = new SlowNote();
        n.text = text;
        return SeismicOverlay.show(n, (scene, o, tick, left) -> o.fadeToward(slow.test(tick) && left >= FADE_OUT ? 1f : 0f),
                start, ticks);
    }

    static final class Reading extends SeismicOverlay {
        String text = "";

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics g, float pt, float a) {
            final Font font = screen.getFontRenderer();
            final PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, 0, DEPTH);
            final int[] r = rect(font.width(text), screen.width, screen.height);
            g.fill(r[0], r[1], r[2], r[3], fill(VEIL, a));
            g.drawString(font, text, r[0] + 3, r[1] + 2, argb(0xFFFFFF, a), true);
            ps.popPose();
        }

        // action bar height, height - 68
        static int[] rect(int textWidth, int width, int height) {
            final int w = textWidth;
            final int x = width / 2 - w / 2;
            final int y = height - 68;
            return new int[] {x - 3, y - 2, x + w + 3, y + 10};
        }
    }

    static final int READING_OUT = 20;

    public static KitInstruction reading(String text, int start, int ticks) {
        final Reading r = new Reading();
        r.text = text;
        return SeismicOverlay.show(r, (scene, o, tick, left) -> o.fadeToward(left < READING_OUT ? 0f : 1f), start, ticks);
    }

    public static VeinView.Clear readingClear(String text, int start, int ticks) {
        return (tick, font, width, height) -> readingUp(tick, start, ticks)
                ? Reading.rect(font.width(text), width, height) : null;
    }

    static boolean readingUp(int tick, int start, int ticks) {
        return tick >= start && tick < start + ticks - READING_OUT + SeismicOverlay.FADE;
    }

    public static String readingLine(SeismicPonderSurvey.Survey s) {
        return Component.translatable("block.create_stats.seismic_scanner.report.found",
                Component.translatable(Blocks.IRON_ORE.getDescriptionId()), s.veins().size(), s.nearest(), s.bottom()).getString();
    }

    static float lerp(float pt, float a, float b) {
        return Mth.lerp(pt, a, b);
    }
}
