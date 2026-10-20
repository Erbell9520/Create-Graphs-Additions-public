package com.createstats.client.ponder.powertrack;

import com.createstats.client.ponder.KitInstruction;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public final class PowerTrackOverlays {
    static final int VEIL = 0x66000000;

    static final float DEPTH = 300f;

    public static final int FADE_OUT = PowerTrackOverlay.FADE;

    private PowerTrackOverlays() {
    }

    @FunctionalInterface
    public interface Anchor {
        Vec3 at(float pt);
    }

    static final class Tag extends PowerTrackOverlay {
        Anchor anchor;
        String text = "";
        int rgb = 0xFFFFFF;
        int dx;
        int dy;

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt, float a) {
            if (text.isEmpty() || anchor == null) {
                return;
            }
            final Vec2 at = scene.getTransform().sceneToScreen(anchor.at(pt), pt);
            final Font font = screen.getFontRenderer();
            final int w = font.width(text) + 4;
            final int x = Math.round(at.x) + dx - w / 2;
            final int y = Math.round(at.y) + dy;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, DEPTH);
            graphics.fill(x, y, x + w, y + 10, fill(VEIL, a));
            graphics.drawString(font, text, x + 2, y + 1, argb(rgb, a), false);
            graphics.pose().popPose();
        }
    }

    public static KitInstruction tag(Anchor anchor, IntFunction<String> text, IntUnaryOperator rgb, int dx, int dy, int start,
                                     int ticks) {
        final Tag tag = new Tag();
        return PowerTrackOverlay.show(tag, (scene, t, tick, left) -> {
            t.anchor = anchor;
            t.text = text.apply(tick);
            t.rgb = rgb.applyAsInt(tick);
            t.dx = dx;
            t.dy = dy;
            t.fadeToward(left >= FADE_OUT ? 1f : 0f);
        }, start, ticks);
    }

    static final class Note extends PowerTrackOverlay {
        String text = "";

        @Override
        protected void draw(PonderScene scene, PonderUI screen, GuiGraphics graphics, float pt, float a) {
            final Font font = screen.getFontRenderer();
            final int w = font.width(text);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, DEPTH);

            graphics.fill(19, 58, 23 + w + 2, 69, fill(VEIL, a));
            graphics.drawString(font, text, 22, 60, argb(0xE8E8E8, a), false);
            graphics.pose().popPose();
        }
    }

    public static KitInstruction note(String text, IntPredicate on, int start, int ticks) {
        final Note note = new Note();
        return PowerTrackOverlay.show(note, (scene, n, tick, left) -> {
            n.text = text;
            n.fadeToward(on.test(tick) && left >= FADE_OUT ? 1f : 0f);
        }, start, ticks);
    }
}
