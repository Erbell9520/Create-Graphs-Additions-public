package com.createstats.client.ponder.fe;

import com.createstats.client.ponder.Ease;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.ponder.Labels;
import com.createstats.power.ponder.FePonderChapters.Warp;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.function.IntFunction;

// fade with the text, nothing pops
public final class FeLabels {
    static final int FADE = 8;
    static final int HOLD = 10;
    static final int CLOCK = 0xB8C4D0;
    private static final float DEPTH = 300f;

    private FeLabels() {
    }

    static int withAlpha(int rgb, float alpha) {
        final int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        return (Math.max(a, 4) << 24) | (rgb & 0xFFFFFF);
    }

    static final class Fader {
        String shown = "";
        int rgb = 0xFFFFFF;
        float level;
        float prevLevel;
        private final int hold;
        private String pending = "";
        private int held;

        Fader(int hold) {
            this.hold = hold;
        }

        void follow(String now, int rgbNow, boolean last) {
            held = now.equals(pending) ? held + 1 : 0;
            pending = now;
            prevLevel = level;
            final boolean want = !now.isEmpty() && held >= hold && !last;
            if (want) {
                if (!now.equals(shown) && level > 0.05f) {
                    level = Math.max(0f, level - 1f / FADE);
                } else {
                    shown = now;
                    rgb = rgbNow;
                    level = Math.min(1f, level + 1f / FADE);
                }
            } else {
                level = Math.max(0f, level - 1f / FADE);
            }
        }

        void steer(boolean want, String now) {
            prevLevel = level;
            if (want) shown = now;
            level = want ? Math.min(1f, level + 1f / FADE) : Math.max(0f, level - 1f / FADE);
        }
    }

    static final class Flag extends FeOverlay {
        Labels.Anchor anchor;
        Fader fader;
        int dx;
        int dy;

        boolean leftOf;

        @Override
        public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt, float fade) {
            if (fader == null) return;
            final float f = (float) Ease.SMOOTH.clamped(Mth.lerp(pt, fader.prevLevel, fader.level)) * fade;
            if (f < 0.05f || fader.shown.isEmpty() || anchor == null) return;
            final net.minecraft.world.phys.Vec2 at = scene.getTransform().sceneToScreen(anchor.at(pt), pt);
            final PoseStack ps = graphics.pose();
            ps.pushPose();
            ps.translate(at.x, at.y, DEPTH);
            final Font font = screen.getFontRenderer();
            final int tw = font.width(fader.shown) + 4;
            final int x = leftOf ? -tw - dx : dx;
            graphics.fill(x, dy, x + tw, dy + 10, Math.round(0x66 * f) << 24);
            graphics.drawString(font, fader.shown, x + 2, dy + 1, withAlpha(fader.rgb, f), false);
            ps.popPose();
        }
    }

    private static Flag element(PonderScene scene, Labels.Anchor anchor, Fader fader, int dx, int dy) {
        final Flag flag = new Flag();
        flag.anchor = anchor;
        flag.fader = fader;
        flag.dx = dx;
        flag.dy = dy;
        flag.setVisible(true);
        scene.addElement(flag);
        return flag;
    }

    public static KitInstruction travel(FePaths.Path path, String words, int rgb, boolean leftOf, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Flag flag;
            private double was;
            private double now;

            @Override
            protected void begin(PonderScene scene) {
                was = 0.0;
                now = 0.0;
                final Fader fader = new Fader(0);
                fader.rgb = rgb;
                flag = element(scene, pt -> path.at(Mth.lerp(pt, was, now) * path.length()), fader, 4, -12);
                flag.leftOf = leftOf;
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (flag == null) return;
                was = now;
                now = Ease.SMOOTH.clamped((tick - start + 1) / (double) Math.max(1, totalTicks - 1));
                flag.fader.steer(!last && tick - start < totalTicks - FADE, words);
                if (last) flag.setVisible(false);
            }
        };
    }

    public static KitInstruction flag(Labels.Anchor anchor, IntFunction<String> text, IntFunction<Integer> rgb, int dx,
                                      int dy, int start, int ticks) {
        return flag(anchor, text, rgb, dx, dy, false, HOLD, start, ticks);
    }

    public static KitInstruction flagLeft(Labels.Anchor anchor, IntFunction<String> text, IntFunction<Integer> rgb,
                                          int gap, int dy, int start, int ticks) {
        return flag(anchor, text, rgb, gap, dy, true, HOLD, start, ticks);
    }

    static KitInstruction flag(Labels.Anchor anchor, IntFunction<String> text, IntFunction<Integer> rgb, int dx, int dy,
                               boolean leftOf, int hold, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Flag flag;

            @Override
            protected void begin(PonderScene scene) {
                flag = element(scene, anchor, new Fader(hold), dx, dy);
                flag.leftOf = leftOf;
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (flag == null) return;
                flag.fader.follow(text.apply(tick), rgb.apply(tick), last);
                if (last) flag.setVisible(false);
            }
        };
    }

    static String clockText(Warp warp, int t) {
        double f = 0.0;
        for (int d = -Warp.RAMP; d <= Warp.RAMP; d++) f = Math.max(f, warp.factor(t + d));
        return warp.factor(t) > 1.05 && f > 1.4 ? String.format(Locale.ROOT, "time x%d", Math.round(f)) : "";
    }

    public static String reason(Recording rec, int machine, int r) {
        return rec.connected[machine][r] && !rec.admitted[machine][r] && rec.reason[machine][r].isShed()
                ? rec.reason[machine][r].shortLabel() : "";
    }

    public static KitInstruction figure(Labels.Anchor anchor, IntFunction<String> text,
                                        java.util.function.IntPredicate visible, int rgb, int dx, int dy, int start,
                                        int ticks) {
        return new KitInstruction(start, ticks) {
            private Flag flag;

            @Override
            protected void begin(PonderScene scene) {
                flag = element(scene, anchor, new Fader(0), dx, dy);
                flag.fader.rgb = rgb;
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (flag == null) return;
                final boolean want = visible.test(tick) && !last && tick - start < totalTicks - FADE;
                flag.fader.steer(want, want ? text.apply(tick) : flag.fader.shown);
                if (last) flag.setVisible(false);
            }
        };
    }

    public static Labels.Anchor at(Vec3 point) {
        return pt -> point;
    }
}
