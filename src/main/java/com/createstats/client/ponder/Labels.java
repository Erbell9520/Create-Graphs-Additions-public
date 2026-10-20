package com.createstats.client.ponder;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public final class Labels {
    @FunctionalInterface
    public interface Anchor {
        Vec3 at(float pt);
    }

    private static final int VEIL = 0x66000000;

    private static final float DEPTH = 300f;

    private static final int FADE = 8;

    private Labels() {
    }

    static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);

        return (Math.max(a, 4) << 24) | (rgb & 0xFFFFFF);
    }

    static int veil(float alpha) {
        return (Math.round(((VEIL >>> 24) & 0xFF) * alpha) << 24);
    }

    abstract static class Label extends AnimatedOverlayElementBase {
        Anchor anchor;
        String text = "";
        int rgb = 0xFFFFFF;

        @Override
        public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt, float fade) {
            if (fade < 0.05f || text.isEmpty() || anchor == null) return;
            Vec2 at = scene.getTransform().sceneToScreen(anchor.at(pt), pt);
            PoseStack ps = graphics.pose();
            ps.pushPose();
            ps.translate(at.x, at.y, DEPTH);
            draw(graphics, screen.getFontRenderer(), fade, pt);
            ps.popPose();
        }

        abstract void draw(GuiGraphics g, Font font, float fade, float pt);

        static void nameTag(GuiGraphics g, Font font, String text, int x, int y, int rgb, float fade) {
            int tw = font.width(text) + 4;
            g.fill(x, y, x + tw, y + 10, veil(fade));
            g.drawString(font, text, x + 2, y + 1, withAlpha(rgb, fade), false);
        }
    }

    static final class Tag extends Label {
        int dx = 6;
        int dy = -5;

        @Override
        void draw(GuiGraphics g, Font font, float fade, float pt) {
            nameTag(g, font, text, dx, dy, rgb, fade);
        }
    }

    public static KitInstruction tag(Anchor anchor, IntFunction<String> text, IntFunction<Integer> rgb, int dx, int dy,
                                     int start, int ticks) {
        return new Show<>(start, ticks, new Tag(), (t, tick) -> {
            t.anchor = anchor;
            t.text = text.apply(tick);
            t.rgb = rgb.apply(tick);
            t.dx = dx;
            t.dy = dy;
        });
    }

    static final class Chip extends Label {
        String next;
        int nextRgb;

        int flip;
        int prevFlip;
        static final int FLIP = 10;
        float rise;
        float prevRise;

        @Override
        void draw(GuiGraphics g, Font font, float fade, float pt) {
            float f = Mth.lerp(pt, prevFlip, flip) / FLIP;
            float scaleX = flip == 0 && prevFlip == 0 ? 1f : Math.abs(Mth.cos(f * Mth.PI));
            String shown = f < 0.5f || next == null ? text : next;
            int colour = f < 0.5f || next == null ? rgb : nextRgb;
            int tw = font.width(shown) + 4;
            float lift = Mth.lerp(pt, prevRise, rise);
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, -lift, 0);
            ps.scale(Math.max(0.02f, scaleX), 1f, 1f);

            g.fill(-1, -3, 1, 0, veil(fade));
            nameTag(g, font, shown, -tw / 2, -13, colour, fade);
            ps.popPose();
        }

        void tickFlip() {
            prevFlip = flip;
            prevRise = rise;
            if (flip > 0) {
                flip++;
                if (flip > FLIP) {
                    flip = 0;
                    prevFlip = 0;
                    if (next != null) {
                        text = next;
                        rgb = nextRgb;
                        next = null;
                    }
                }
            }
        }
    }

    public static KitInstruction chip(Anchor anchor, IntFunction<String> job, java.util.function.Function<String, Integer> rgb,
                                      int start, int ticks) {
        return new Show<>(start, ticks, new Chip(), (c, tick) -> {
            c.anchor = anchor;
            String j = job.apply(tick);
            c.tickFlip();
            if (tick == start) {
                c.text = j;
                c.rgb = rgb.apply(j);
                c.next = null;
                c.flip = 0;
                c.rise = 0f;
                c.prevRise = 0f;
            }
            c.rise = 4f * (float) Ease.OUT_CUBIC.clamped((tick - start + 1) / (double) FADE);
            String target = c.next != null ? c.next : c.text;
            if (!j.equals(target) && c.flip == 0) {
                c.next = j;
                c.nextRgb = rgb.apply(j);
                c.flip = 1;
            }
        });
    }

    static final class Keycap extends Label {
        boolean held;
        boolean wasHeld;
        float dip;
        float prevDip;

        @Override
        void draw(GuiGraphics g, Font font, float fade, float pt) {
            int w = font.width(text) + 6;
            int h = 12;
            float d = Mth.lerp(pt, prevDip, dip);
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0, d * 2f + (1f - fade) * -6f, 0);
            PonderUI.renderSpeechBox(g, 0, 0, w, h, d > 0.5f, Pointing.DOWN, true);
            ps.translate(0, 0, 100);
            g.drawString(font, text, 3, 2, withAlpha(held ? 0xFFFFFF : 0xE0E0E0, fade), false);
            ps.popPose();
        }

        void tickHeld(boolean now) {
            prevDip = dip;
            wasHeld = held;
            held = now;

            dip = held ? Math.min(1f, dip + 0.5f) : Math.max(0f, dip - 0.34f);
        }
    }

    public static KitInstruction keycap(Anchor anchor, String name, IntPredicate held, int start, int ticks) {
        return new Show<>(start, ticks, new Keycap(), (k, tick) -> {
            k.anchor = anchor;
            k.text = name;
            k.tickHeld(held.test(tick));
        });
    }

    @FunctionalInterface
    interface Update<L extends Label> {
        void apply(L label, int tick);
    }

    private static final class Show<L extends Label> extends KitInstruction {
        private final L label;
        private final Update<L> update;

        Show(int start, int ticks, L label, Update<L> update) {
            super(start, Math.max(ticks, 2 * FADE + 1));
            this.label = label;
            this.update = update;
        }

        @Override
        protected void begin(PonderScene scene) {
            label.setFade(0f);
            label.setFade(0f);
            label.setVisible(true);
            scene.addElement(label);
        }

        @Override
        protected void step(PonderScene scene, int tick, boolean last) {
            update.apply(label, tick);
            int in = tick - start + 1;
            int out = start + totalTicks - tick - 1;
            float f = Math.min(1f, Math.min(in / (float) FADE, out / (float) FADE));
            label.setFade((float) Ease.SMOOTH.clamped(f));
            if (last) label.setVisible(false);
        }
    }
}
