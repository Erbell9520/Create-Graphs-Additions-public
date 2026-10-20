package com.createstats.book.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
final class PosedGraphics extends GuiGraphics {
    private PosedGraphics(Minecraft mc, GuiGraphics under) {
        super(mc, under.bufferSource());
        pose().last().pose().set(under.pose().last().pose());
        pose().last().normal().set(under.pose().last().normal());
    }

    static GuiGraphics over(GuiGraphics under) {
        return new PosedGraphics(Minecraft.getInstance(), under);
    }

    @Override
    public void enableScissor(int x0, int y0, int x1, int y1) {
        final Matrix4f m = pose().last().pose();
        final Vector3f a = m.transformPosition(x0, y0, 0.0f, new Vector3f());
        final Vector3f b = m.transformPosition(x1, y1, 0.0f, new Vector3f());
        super.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
    }
}
