package com.createstats.client.ponder.seismic;

import com.createstats.client.ponder.KitInstruction;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public abstract class SeismicOverlay extends AnimatedOverlayElementBase {
    protected float level;
    protected float levelOld;

    protected static final int FADE = 8;

    protected SeismicOverlay() {
        setFade(1f);
        setFade(1f);
    }

    protected float level(float pt) {
        return Mth.clamp(Mth.lerp(pt, levelOld, level), 0f, 1f);
    }

    protected void fadeToward(float target) {
        levelOld = level;
        final float step = 1f / FADE;
        level = level < target ? Math.min(target, level + step) : Math.max(target, level - step);
    }

    @Override
    public final void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt, float fade) {
        final float a = level(pt);
        if (a <= 0.01f) {
            return;
        }

        draw(scene, screen, graphics, pt, a * a * (3f - 2f * a));
    }

    protected abstract void draw(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt, float a);

    static int argb(int rgb, float a) {
        final int alpha = Mth.clamp(Math.round(a * 255f), 0, 255);
        return (Math.max(alpha, 4) << 24) | (rgb & 0xFFFFFF);
    }

    static int fill(int argb, float a) {
        final int alpha = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(a, 0f, 1f));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    static <O extends SeismicOverlay> KitInstruction show(O overlay, Update<O> update, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            @Override
            protected void begin(PonderScene scene) {
                overlay.level = 0f;
                overlay.levelOld = 0f;
                overlay.setVisible(true);
                scene.addElement(overlay);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                update.apply(scene, overlay, tick, start + totalTicks - tick - 1);
                if (last) {
                    overlay.setVisible(false);
                }
            }
        };
    }

    @FunctionalInterface
    interface Update<O> {
        void apply(PonderScene scene, O overlay, int tick, int left);
    }
}
