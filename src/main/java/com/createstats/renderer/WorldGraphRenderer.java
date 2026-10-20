package com.createstats.renderer;

import net.minecraft.client.renderer.RenderStateShard;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WorldGraphRenderer
{
    public static final RenderType GRAPH_QUADS;

    private WorldGraphRenderer() {
    }

    public static void fillRect(PoseStack ps, MultiBufferSource buf, float x, float y, float w, float h, int color, float z) {
        final VertexConsumer vc = buf.getBuffer(WorldGraphRenderer.GRAPH_QUADS);
        final Matrix4f mat = ps.last().pose();
        final float a = (color >> 24 & 0xFF) / 255.0f;
        final float r = (color >> 16 & 0xFF) / 255.0f;
        final float g = (color >> 8 & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        vc.addVertex(mat, x, y, z).setColor(r, g, b, a);
        vc.addVertex(mat, x, y + h, z).setColor(r, g, b, a);
        vc.addVertex(mat, x + w, y + h, z).setColor(r, g, b, a);
        vc.addVertex(mat, x + w, y, z).setColor(r, g, b, a);
    }

    public static void hLine(PoseStack ps, MultiBufferSource buf, float x, float y, float w, int color, float thickness, float z) {
        fillRect(ps, buf, x, y - thickness / 2.0f, w, thickness, color, z);
    }

    public static void vLine(PoseStack ps, MultiBufferSource buf, float x, float y, float h, int color, float thickness, float z) {
        fillRect(ps, buf, x - thickness / 2.0f, y, thickness, h, color, z);
    }

    public static void thickLine(PoseStack ps, MultiBufferSource buf, float x1, float y1, float x2, float y2, int color, float thickness, float z) {
        final VertexConsumer vc = buf.getBuffer(WorldGraphRenderer.GRAPH_QUADS);
        final Matrix4f mat = ps.last().pose();
        final float a = (color >> 24 & 0xFF) / 255.0f;
        final float r = (color >> 16 & 0xFF) / 255.0f;
        final float g = (color >> 8 & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        final float dx = x2 - x1;
        final float dy = y2 - y1;
        final float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            return;
        }
        final float nx = -dy / len * thickness / 2.0f;
        final float ny = dx / len * thickness / 2.0f;
        vc.addVertex(mat, x1 + nx, y1 + ny, z).setColor(r, g, b, a);
        vc.addVertex(mat, x1 - nx, y1 - ny, z).setColor(r, g, b, a);
        vc.addVertex(mat, x2 - nx, y2 - ny, z).setColor(r, g, b, a);
        vc.addVertex(mat, x2 + nx, y2 + ny, z).setColor(r, g, b, a);
    }

    public static void drawDot(PoseStack ps, MultiBufferSource buf, float cx, float cy, float radius, int color, float z) {
        fillRect(ps, buf, cx - radius, cy - radius, radius * 2.0f, radius * 2.0f, color, z);
    }

    public static void drawGrid(PoseStack ps, MultiBufferSource buf, float x, float y, float w, float h, int hDivs, int vDivs, int color, float z) {
        final float thickness = 0.3f;
        for (int i = 1; i < hDivs; ++i) {
            final float gy = y + h * i / hDivs;
            hLine(ps, buf, x, gy, w, color, thickness, z);
        }
        for (int i = 1; i < vDivs; ++i) {
            final float gx = x + w * i / vDivs;
            vLine(ps, buf, gx, y, h, color, thickness, z);
        }
    }

    public static void drawAxes(PoseStack ps, MultiBufferSource buf, float x, float y, float w, float h, int color, float z) {
        final float thickness = 0.6f;
        vLine(ps, buf, x, y, h, color, thickness, z);
        hLine(ps, buf, x, y + h, w, color, thickness, z);
    }

    public static void drawLineChart(PoseStack ps, MultiBufferSource buf, float[] data, float x, float y, float w, float h, int color, float thickness, float z, float minVal, float maxVal) {
        if (data.length < 2) {
            return;
        }
        if (Float.isNaN(minVal) || Float.isNaN(maxVal)) {
            float autoMin = Float.MAX_VALUE;
            float autoMax = -3.4028235E38f;
            for (float v : data) {
                autoMin = Math.min(autoMin, v);
                autoMax = Math.max(autoMax, v);
            }
            if (Float.isNaN(minVal)) {
                minVal = autoMin;
            }
            if (Float.isNaN(maxVal)) {
                maxVal = autoMax;
            }
        }
        float range = maxVal - minVal;
        if (range < 0.001f) {
            range = 1.0f;
        }
        for (int i = 0; i < data.length - 1; ++i) {
            final float t1 = i / (float)(data.length - 1);
            final float t2 = (i + 1) / (float)(data.length - 1);
            final float v2 = Math.max(0.0f, Math.min(1.0f, (data[i] - minVal) / range));
            final float v3 = Math.max(0.0f, Math.min(1.0f, (data[i + 1] - minVal) / range));
            final float px1 = x + t1 * w;
            final float py1 = y + h - v2 * h;
            final float px2 = x + t2 * w;
            final float py2 = y + h - v3 * h;
            thickLine(ps, buf, px1, py1, px2, py2, color, thickness, z);
        }
    }

    public static void drawLineChart(PoseStack ps, MultiBufferSource buf, float[] data, float x, float y, float w, float h, int color, float thickness, float z) {
        drawLineChart(ps, buf, data, x, y, w, h, color, thickness, z, Float.NaN, Float.NaN);
    }

    public static void drawBarChart(PoseStack ps, MultiBufferSource buf, float[] data, int maxBars, float x, float y, float w, float h, int color, float z) {
        if (data.length == 0) {
            return;
        }
        final int start = Math.max(0, data.length - maxBars);
        final int count = data.length - start;
        final float barW = w / count;
        final float gap = Math.max(0.3f, barW * 0.15f);
        float min = Float.MAX_VALUE;
        float max = -3.4028235E38f;
        for (int i = start; i < data.length; ++i) {
            min = Math.min(min, data[i]);
            max = Math.max(max, data[i]);
        }
        float range = max - min;
        if (range < 0.001f) {
            range = 1.0f;
        }
        for (int j = 0; j < count; ++j) {
            float norm = (data[start + j] - min) / range;
            norm = Math.max(0.02f, Math.min(1.0f, norm));
            final float barH = norm * h;
            final float bx = x + j * barW + gap / 2.0f;
            float bw = barW - gap;
            if (bw < 0.5f) {
                bw = 0.5f;
            }
            fillRect(ps, buf, bx, y + h - barH, bw, barH, color, z);
        }
    }

    public static void drawArc(PoseStack ps, MultiBufferSource buf, float cx, float cy, float radius, float startAngle, float endAngle, int color, float thickness, int segments, float z) {
        for (int i = 0; i < segments; ++i) {
            final float a1 = startAngle + (endAngle - startAngle) * i / segments;
            final float a2 = startAngle + (endAngle - startAngle) * (i + 1) / segments;
            final float x1 = cx + (float)Math.cos(a1) * radius;
            final float y1 = cy - (float)Math.sin(a1) * radius;
            final float x2 = cx + (float)Math.cos(a2) * radius;
            final float y2 = cy - (float)Math.sin(a2) * radius;
            thickLine(ps, buf, x1, y1, x2, y2, color, thickness, z);
        }
    }

    public static void fillArc(PoseStack ps, MultiBufferSource buf, float cx, float cy, float innerR, float outerR, float startAngle, float endAngle, int color, int segments, float z) {
        final VertexConsumer vc = buf.getBuffer(WorldGraphRenderer.GRAPH_QUADS);
        final Matrix4f mat = ps.last().pose();
        final float a = (color >> 24 & 0xFF) / 255.0f;
        final float r = (color >> 16 & 0xFF) / 255.0f;
        final float g = (color >> 8 & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        for (int i = 0; i < segments; ++i) {
            final float a2 = startAngle + (endAngle - startAngle) * i / segments;
            final float a3 = startAngle + (endAngle - startAngle) * (i + 1) / segments;
            final float cos1 = (float)Math.cos(a2);
            final float sin1 = (float)Math.sin(a2);
            final float cos2 = (float)Math.cos(a3);
            final float sin2 = (float)Math.sin(a3);
            vc.addVertex(mat, cx + cos1 * innerR, cy - sin1 * innerR, z).setColor(r, g, b, a);
            vc.addVertex(mat, cx + cos1 * outerR, cy - sin1 * outerR, z).setColor(r, g, b, a);
            vc.addVertex(mat, cx + cos2 * outerR, cy - sin2 * outerR, z).setColor(r, g, b, a);
            vc.addVertex(mat, cx + cos2 * innerR, cy - sin2 * innerR, z).setColor(r, g, b, a);
        }
    }

    public static float[] argb(int color) {
        return new float[] { (color >> 24 & 0xFF) / 255.0f, (color >> 16 & 0xFF) / 255.0f, (color >> 8 & 0xFF) / 255.0f, (color & 0xFF) / 255.0f };
    }

    public static int lerpColor(int colorA, int colorB, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        final int aA = colorA >> 24 & 0xFF;
        final int rA = colorA >> 16 & 0xFF;
        final int gA = colorA >> 8 & 0xFF;
        final int bA = colorA & 0xFF;
        final int aB = colorB >> 24 & 0xFF;
        final int rB = colorB >> 16 & 0xFF;
        final int gB = colorB >> 8 & 0xFF;
        final int bB = colorB & 0xFF;
        final int a = (int)(aA + (aB - aA) * t);
        final int r = (int)(rA + (rB - rA) * t);
        final int g = (int)(gA + (gB - gA) * t);
        final int b = (int)(bA + (bB - bA) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int withAlpha(int color, int alpha) {
        return alpha << 24 | (color & 0xFFFFFF);
    }

    static {
        GRAPH_QUADS = (RenderType)RenderType.create("create_stats_graph", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, RenderType.CompositeState.builder().setShaderState(Shards.POS_COLOR_SHADER).setLayeringState(Shards.POLY_OFFSET).setTransparencyState(Shards.TRANSLUCENT).setCullState(Shards.NOCULL).createCompositeState(false));
    }

    private static class Shards extends RenderStateShard
    {
        static final RenderStateShard.ShaderStateShard POS_COLOR_SHADER;
        static final RenderStateShard.LayeringStateShard POLY_OFFSET;
        static final RenderStateShard.TransparencyStateShard TRANSLUCENT;
        static final RenderStateShard.CullStateShard NOCULL;

        private Shards() {
            super((String)null, (Runnable)null, (Runnable)null);
        }

        static {
            POS_COLOR_SHADER = Shards.POSITION_COLOR_SHADER;
            POLY_OFFSET = Shards.POLYGON_OFFSET_LAYERING;
            TRANSLUCENT = Shards.TRANSLUCENT_TRANSPARENCY;
            NOCULL = Shards.NO_CULL;
        }
    }
}
