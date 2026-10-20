package com.createstats.statsmk3.ui;

import java.util.ArrayDeque;
import java.util.Deque;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class DrawCtx implements PanelSurface {
    private static final int FLOATS_PER_VERTEX = 6;
    private static final int INITIAL_VERTICES = 4096;

    private final GuiGraphics graphics;
    private final Font font;

    private float[] verts = new float[INITIAL_VERTICES * FLOATS_PER_VERTEX];
    private int count;

    private final Deque<Rect> clipStack = new ArrayDeque<>();
    private float alphaMul = 1.0f;

    private float offsetX;
    private float offsetY;
    private final Deque<float[]> poseOffsets = new ArrayDeque<>();

    public interface Scissors {
        void enable(int x, int y, int right, int bottom);

        void disable();
    }

    private final Scissors scissors;

    public DrawCtx(GuiGraphics graphics, Font font) {
        this(graphics, font, new Scissors() {
            @Override
            public void enable(int x, int y, int right, int bottom) {
                graphics.enableScissor(x, y, right, bottom);
            }

            @Override
            public void disable() {
                graphics.disableScissor();
            }
        });
    }

    DrawCtx(GuiGraphics graphics, Font font, Scissors scissors) {
        this.graphics = graphics;
        this.font = font;
        this.scissors = scissors;
    }

    public GuiGraphics graphics() {
        return this.graphics;
    }

    public Font font() {
        return this.font;
    }

    @Override
    public void setAlpha(float mul) {
        this.alphaMul = mul < 0 ? 0 : Math.min(mul, 1.0f);
    }

    @Override
    public float alpha() {
        return this.alphaMul;
    }

    @Override
    public void pushPose() {
        flush();
        this.poseOffsets.push(new float[]{this.offsetX, this.offsetY});
        this.graphics.pose().pushPose();
    }

    @Override
    public void popPose() {
        flush();
        if (!this.poseOffsets.isEmpty()) {
            final float[] saved = this.poseOffsets.pop();
            this.offsetX = saved[0];
            this.offsetY = saved[1];
        }
        this.graphics.pose().popPose();
    }

    @Override
    public void translate(float dx, float dy) {
        flush();
        this.offsetX += dx;
        this.offsetY += dy;
        this.graphics.pose().translate(dx, dy, 0.0f);
    }

    @Override
    public void clip(Rect r) {
        flush();
        final Rect moved = this.offsetX == 0.0f && this.offsetY == 0.0f
                ? r : r.offset(this.offsetX, this.offsetY);
        final Rect current = this.clipStack.peek();
        this.clipStack.push(current == null ? moved : current.intersect(moved));

        this.scissors.enable(Math.round(moved.x), Math.round(moved.y),
                Math.round(moved.right()), Math.round(moved.bottom()));
    }

    @Override
    public void unclip() {
        if (this.clipStack.isEmpty()) {
            return;
        }
        flush();
        this.clipStack.pop();
        // one pop, vanilla's ScissorStack restores the parent. don't re-enable it here

        this.scissors.disable();
    }

    @Override
    public boolean culled(Rect r) {
        final Rect current = this.clipStack.peek();
        if (current == null) {
            return false;
        }
        final Rect moved = this.offsetX == 0.0f && this.offsetY == 0.0f
                ? r : r.offset(this.offsetX, this.offsetY);
        return current.intersect(moved).isEmpty();
    }

    @Override
    public void text(String s, float x, float y, int color, float scale) {
        drawText(s, x, y, color, scale, false);
    }

    public void textShadow(String s, float x, float y, int color, float scale) {
        drawText(s, x, y, color, scale, true);
    }

    @Override
    public void textCenter(String s, float centerX, float y, int color, float scale) {
        drawText(s, centerX - this.font.width(s) * scale * 0.5f, y, color, scale, false);
    }

    @Override
    public void textRight(String s, float rightX, float y, int color, float scale) {
        drawText(s, rightX - this.font.width(s) * scale, y, color, scale, false);
    }

    @Override
    public float textWidth(String s, float scale) {
        return this.font.width(s) * scale;
    }

    private void drawText(String s, float x, float y, int color, float scale, boolean shadow) {
        if (s == null || s.isEmpty()) {
            return;
        }
        final int c = applyAlpha(color);
        if (((c >>> 24) & 0xFF) == 0) {
            return;
        }
        flush();
        if (scale == 1.0f) {
            this.graphics.drawString(this.font, s, Math.round(x), Math.round(y), c, shadow);
            return;
        }
        this.graphics.pose().pushPose();
        this.graphics.pose().translate(x, y, 0.0f);
        this.graphics.pose().scale(scale, scale, 1.0f);
        this.graphics.drawString(this.font, s, 0, 0, c, shadow);
        this.graphics.pose().popPose();
    }

    @Override
    public void itemIcon(net.minecraft.world.item.ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty() || size <= 0.5f) {
            return;
        }
        flush();
        final float scale = size / 16.0f;
        this.graphics.pose().pushPose();
        this.graphics.pose().translate(x, y, 0.0f);
        this.graphics.pose().scale(scale, scale, 1.0f);
        this.graphics.renderItem(stack, 0, 0);
        this.graphics.pose().popPose();
    }

    @Override
    public boolean drawsItems() {
        return true;
    }

    @Override
    public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        ensure(3);
        vertex(x1, y1, applyAlpha(c1));
        vertex(x2, y2, applyAlpha(c2));
        vertex(x3, y3, applyAlpha(c3));
    }

    private int applyAlpha(int argb) {
        return this.alphaMul >= 1.0f ? argb : Tokens.fade(argb, this.alphaMul);
    }

    private void vertex(float x, float y, int argb) {
        final int base = this.count * FLOATS_PER_VERTEX;
        this.verts[base] = x;
        this.verts[base + 1] = y;
        this.verts[base + 2] = ((argb >> 16) & 0xFF) / 255.0f;
        this.verts[base + 3] = ((argb >> 8) & 0xFF) / 255.0f;
        this.verts[base + 4] = (argb & 0xFF) / 255.0f;
        this.verts[base + 5] = ((argb >>> 24) & 0xFF) / 255.0f;
        this.count++;
    }

    private void ensure(int additional) {
        final int needed = (this.count + additional) * FLOATS_PER_VERTEX;
        if (needed <= this.verts.length) {
            return;
        }
        int cap = this.verts.length;
        while (cap < needed) {
            cap <<= 1;
        }
        final float[] grown = new float[cap];
        System.arraycopy(this.verts, 0, grown, 0, this.count * FLOATS_PER_VERTEX);
        this.verts = grown;
    }

    public void flush() {
        if (this.count == 0) {
            return;
        }
        final Matrix4f pose = this.graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        final BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < this.count; i++) {
            final int b = i * FLOATS_PER_VERTEX;
            buf.addVertex(pose, this.verts[b], this.verts[b + 1], 0.0f)
                    .setColor(this.verts[b + 2], this.verts[b + 3], this.verts[b + 4], this.verts[b + 5]);
        }
        this.count = 0;
        final MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void finish() {
        flush();
        while (!this.clipStack.isEmpty()) {
            this.clipStack.pop();
            this.scissors.disable();
        }
        this.alphaMul = 1.0f;
        this.poseOffsets.clear();
        this.offsetX = 0.0f;
        this.offsetY = 0.0f;
    }
}
