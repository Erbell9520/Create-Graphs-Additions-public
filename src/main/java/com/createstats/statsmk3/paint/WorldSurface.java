package com.createstats.statsmk3.paint;

import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.render.Mk3RenderTypes;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

// geometry every frame. the old path was 4Hz and 256px per block
@OnlyIn(Dist.CLIENT)
public final class WorldSurface implements PanelSurface {
    private static final float LAYER_STEP = 0.012f;

    private final MultiBufferSource buffers;
    private final Font font;
    private final int light;
    private final float brightness;

    private final Matrix4f base;
    private final Matrix4f scratch = new Matrix4f();

    private final List<Glyphs> deferred = new ArrayList<>();

    private VertexConsumer consumer;
    private float z;

    public WorldSurface(PoseStack pose, MultiBufferSource buffers, Font font,
                        int light, float brightness) {
        this.buffers = buffers;
        this.font = font;
        this.light = light;
        this.brightness = brightness < 0.0f ? 0.0f : Math.min(brightness, 1.0f);
        this.base = new Matrix4f(pose.last().pose());
    }

    @Override
    public float feather() {
        return 0.5f;
    }

    @Override
    public void advanceLayer() {
        this.z += LAYER_STEP;
    }

    @Override
    public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        final VertexConsumer vc = buffer();

        vertex(vc, x1, y1, c1);
        vertex(vc, x2, y2, c2);
        vertex(vc, x3, y3, c3);
        vertex(vc, x3, y3, c3);
    }

    private VertexConsumer buffer() {
        if (this.consumer == null) {
            this.consumer = this.buffers.getBuffer(Mk3RenderTypes.PANEL);
        }
        return this.consumer;
    }

    private void vertex(VertexConsumer vc, float x, float y, int argb) {
        final int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
            vc.addVertex(this.base, x, y, this.z).setColor(0, 0, 0, 0);
            return;
        }
        vc.addVertex(this.base, x, y, this.z).setColor(dim(argb, 16), dim(argb, 8), dim(argb, 0), a);
    }

    private int dim(int argb, int shift) {
        return Math.round(((argb >> shift) & 0xFF) * this.brightness);
    }

    private record Glyphs(String text, float x, float y, float z, int color, float scale) {
    }

    @Override
    public void text(String s, float x, float y, int color, float scale) {
        if (s == null || s.isEmpty()) {
            return;
        }
        final int a = (color >>> 24) & 0xFF;
        if (a == 0 || textCulled(s, x, y, scale)) {
            return;
        }
        final int tinted = (a << 24) | (dim(color, 16) << 16) | (dim(color, 8) << 8) | dim(color, 0);
        this.deferred.add(new Glyphs(s, x, y, this.z + LAYER_STEP, tinted, scale));
    }

    @Override
    public void textCenter(String s, float centerX, float y, int color, float scale) {
        text(s, centerX - this.font.width(s) * scale * 0.5f, y, color, scale);
    }

    @Override
    public void textRight(String s, float rightX, float y, int color, float scale) {
        text(s, rightX - this.font.width(s) * scale, y, color, scale);
    }

    @Override
    public float textWidth(String s, float scale) {
        return this.font.width(s) * scale;
    }

    private record Icon(net.minecraft.world.item.ItemStack stack, float x, float y, float size,
                        float z) {
    }

    private final List<Icon> icons = new ArrayList<>();

    @Override
    public void itemIcon(net.minecraft.world.item.ItemStack stack, float x, float y, float size) {
        if (stack == null || stack.isEmpty() || size <= 0.5f || culled(new Rect(x, y, size, size))) {
            return;
        }
        this.icons.add(new Icon(stack, x, y, size, this.z + LAYER_STEP));
    }

    @Override
    public boolean drawsItems() {
        return true;
    }

    private void finishIcons() {
        if (this.icons.isEmpty()) {
            return;
        }
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        final net.minecraft.client.renderer.entity.ItemRenderer renderer = mc.getItemRenderer();
        for (final Icon icon : this.icons) {
            final com.mojang.blaze3d.vertex.PoseStack pose = new com.mojang.blaze3d.vertex.PoseStack();
            pose.last().pose().set(this.base);
            pose.translate(icon.x() + icon.size() * 0.5f, icon.y() + icon.size() * 0.5f, icon.z());
            pose.scale(icon.size(), -icon.size(), Math.max(0.01f, icon.size() * 0.05f));
            renderer.renderStatic(icon.stack(),
                    net.minecraft.world.item.ItemDisplayContext.FIXED,
                    this.light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    pose, this.buffers, mc.level, 0);
        }
        this.icons.clear();
    }

    public void finish() {
        this.consumer = null;
        finishIcons();
        for (final Glyphs g : this.deferred) {
            this.scratch.set(this.base)
                    .translate(g.x(), g.y(), g.z())
                    .scale(g.scale(), g.scale(), g.scale());
            this.font.drawInBatch(g.text(), 0.0f, 0.0f, g.color(), false, this.scratch, this.buffers,
                    Font.DisplayMode.NORMAL, 0, this.light);
        }
        this.deferred.clear();
    }

    private final List<Rect> clipStack = new ArrayList<>();

    @Override
    public void clip(Rect r) {
        final Rect active = activeClip();
        this.clipStack.add(active == null ? r : active.intersect(r));
    }

    @Override
    public void unclip() {
        if (!this.clipStack.isEmpty()) {
            this.clipStack.remove(this.clipStack.size() - 1);
        }
    }

    private Rect activeClip() {
        return this.clipStack.isEmpty() ? null : this.clipStack.get(this.clipStack.size() - 1);
    }

    @Override
    public boolean culled(Rect r) {
        final Rect active = activeClip();
        return r.isEmpty() || (active != null && active.intersect(r).isEmpty());
    }

    private boolean textCulled(String s, float x, float y, float scale) {
        final Rect active = activeClip();
        if (active == null) {
            return false;
        }
        return active.intersect(new Rect(x, y, textWidth(s, scale), Text.lineHeight(scale)))
                .isEmpty();
    }
}
