package com.createstats.renderer;

import com.createstats.blockentity.CapacitorBankBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import java.util.Locale;

/** [1] */
public class CapacitorBankRenderer implements BlockEntityRenderer<CapacitorBankBE> {

    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/panel_solid.png");

    /** Depth at which the instrument sits proud of the shell. */
    private static final float PROUD = 0.02f;
    private static final float MARGIN = 0.10f;
    private static final float BEZEL = 0.055f;
    /** Blocks of height for one line of text. Font cells are 9px, so this is the divisor. */
    private static final float LINE = 0.115f;

    private static final int COL_WELL     = 0xFF070A0E;
    private static final int COL_PANEL    = 0xFF10161D;
    private static final int COL_GAP      = 0xFF0D1218;
    private static final int COL_BEZEL    = 0xFF6E5530;
    private static final int COL_BEZEL_LIT= 0xFF9C7A42;
    private static final int COL_SCREW    = 0xFF3D4653;
    private static final int COL_EMPTY    = 0xFF171E26;
    private static final int COL_HEADER   = 0xFF1A222C;
    private static final int COL_LABEL    = 0xFF6E8093;
    private static final int COL_VALUE    = 0xFFD8E4EE;
    private static final int COL_RULE     = 0xFF2A3541;
    private static final int COL_LOUVER   = 0xFF141A21;
    private static final int COL_LOUVER_EDGE = 0xFF2B3642;

    private final Font font;

    public CapacitorBankRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(CapacitorBankBE be, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!be.isRenderController() || !be.isSolidBox()) {
            return;
        }
        final BlockPos origin = be.getBlockPos();
        final BlockPos min = be.structureMin();
        final BlockPos max = be.structureMax();

        final float fill = be.groupFillFraction();
        final int colour = chargeColour(fill);
        final int light = LightTexture.FULL_BRIGHT;

        final float x0 = min.getX() - origin.getX();
        final float y0 = min.getY() - origin.getY();
        final float z0 = min.getZ() - origin.getZ();
        final float x1 = max.getX() - origin.getX() + 1.0f;
        final float z1 = max.getZ() - origin.getZ() + 1.0f;

        final int spanX = max.getX() - min.getX() + 1;
        final int spanZ = max.getZ() - min.getZ() + 1;
        final int height = max.getY() - min.getY() + 1;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            final int faceWidth = (dir.getAxis() == Direction.Axis.Z) ? spanX : spanZ;
            poseStack.pushPose();
            faceTransform(poseStack, dir, x0, y0, z0, x1, z1);
            drawFace(be, poseStack, bufferSource, faceWidth, height, fill, colour, light);
            poseStack.popPose();
        }
    }

    /** [2] */
    private static void faceTransform(PoseStack poseStack, Direction dir,
                                      float x0, float y0, float z0, float x1, float z1) {
        switch (dir) {
            case SOUTH -> poseStack.translate(x0, y0, z1);
            case NORTH -> {
                poseStack.translate(x1, y0, z0);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            case WEST -> {
                poseStack.translate(x0, y0, z0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
            }
            default -> {
                poseStack.translate(x1, y0, z1);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            }
        }
        poseStack.translate(0.0f, 0.0f, PROUD);
    }

    // composition

    private void drawFace(CapacitorBankBE be, PoseStack poseStack, MultiBufferSource buffers,
                          int faceWidth, int height, float fill, int colour, int light) {
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PANEL_TEXTURE));
        final Matrix4f mat = poseStack.last().pose();
        final float w = faceWidth;
        final float h = height;

        final float b0 = MARGIN;
        final float i0 = MARGIN + BEZEL;
        final float i1x = w - MARGIN - BEZEL;
        final float i1y = h - MARGIN - BEZEL;
        if (i1x <= i0 || i1y <= i0) {
            return;
        }
        final int bezel = fill > 0.02f ? COL_BEZEL_LIT : COL_BEZEL;
        // Bezel as four strips, never a filled rect with the panel laid over it.
        quad(vc, mat, b0, b0, w - MARGIN, i0, bezel, light);
        quad(vc, mat, b0, i1y, w - MARGIN, h - MARGIN, bezel, light);
        quad(vc, mat, b0, i0, i0, i1y, bezel, light);
        quad(vc, mat, i1x, i0, w - MARGIN, i1y, bezel, light);

        // Screws in the bezel corners. Small thing, but it is the difference between a painted
        // rectangle and something that looks bolted to a machine.
        final float s = Math.min(0.035f, BEZEL * 0.5f);
        final float sc = (MARGIN + i0) * 0.5f - s * 0.5f;
        quad(vc, mat, sc, sc, sc + s, sc + s, COL_SCREW, light);
        quad(vc, mat, w - sc - s, sc, w - sc, sc + s, COL_SCREW, light);
        quad(vc, mat, sc, h - sc - s, sc + s, h - sc, COL_SCREW, light);
        quad(vc, mat, w - sc - s, h - sc - s, w - sc, h - sc, COL_SCREW, light);

        if (faceWidth <= 1) {
            drawColumn(vc, mat, i0, i0, i1x, i1y, fill, colour, light);
            return;
        }

        // [3]
        final float spineW = Mth.clamp((i1x - i0) * 0.13f, 0.16f, 0.30f);
        drawColumn(vc, mat, i0, i0, i0 + spineW, i1y, fill, colour, light);
        final float gutter = 0.04f;
        quad(vc, mat, i0 + spineW, i0, i0 + spineW + gutter, i1y, COL_WELL, light);

        drawInstrument(be, poseStack, buffers, vc, mat,
                i0 + spineW + gutter, i0, i1x, i1y, faceWidth, height, fill, colour, light);
    }

    /** [4] */
    private void drawInstrument(CapacitorBankBE be, PoseStack poseStack, MultiBufferSource buffers,
                                VertexConsumer vc, Matrix4f mat, float u0, float v0, float u1, float v1,
                                int faceWidth, int height, float fill, int colour, int light) {
        if (u1 - u0 < 0.4f) {
            drawLouvers(vc, mat, u0, v0, u1, v1, light);
            return;
        }
        final int rate = be.chargeRatePerTick();
        final int stored = be.groupEnergy();

        // header strip.
        final float headerH = Math.min(0.20f, (v1 - v0) * 0.16f);
        quad(vc, mat, u0, v1 - headerH, u1, v1, COL_HEADER, light);
        text(poseStack, buffers, "CAPACITOR BANK", u0 + 0.05f, v1 - headerH + 0.045f, COL_LABEL, light);
        final int lampColour = stored <= 0 ? 0xFFFF4A4A
                : rate > 0 ? 0xFF58DC7A
                : rate < 0 ? 0xFFFFBA3C
                : 0xFF4C7BA8;
        final float lamp = Math.min(0.07f, headerH * 0.45f);
        final float lampY = v1 - headerH * 0.5f - lamp * 0.5f;
        quad(vc, mat, u1 - 0.05f - lamp, lampY, u1 - 0.05f, lampY + lamp, lampColour, light);
        final float contentTop = v1 - headerH - 0.014f;
        quad(vc, mat, u0, contentTop, u1, v1 - headerH, COL_RULE, light);

        // [5]
        final float available = contentTop - v0;
        final float moduleH = Mth.clamp(available * 0.55f, 0.50f, 1.05f);
        final float slack = Math.max(0.0f, available - moduleH);
        final float moduleBottom = v0 + Math.min(slack, slack * 0.34f);
        final float moduleTop = Math.min(contentTop, moduleBottom + moduleH);

        // Louvers above and below it, so the leftover space is hardware rather than void.
        drawLouvers(vc, mat, u0, moduleTop, u1, contentTop, light);
        if (faceWidth >= 3 && moduleBottom - v0 > 0.22f) {
            drawDistribution(vc, mat, u0, v0, u1, moduleBottom, height, fill, colour, light);
        } else {
            drawLouvers(vc, mat, u0, v0, u1, moduleBottom, light);
        }

        drawModule(be, poseStack, buffers, vc, mat, u0, moduleBottom, u1, moduleTop, fill, colour, light);
    }

    /** The readout proper: percentage, totals, charge bar, throughput and endurance. */
    private void drawModule(CapacitorBankBE be, PoseStack poseStack, MultiBufferSource buffers,
                            VertexConsumer vc, Matrix4f mat, float u0, float v0, float u1, float v1,
                            float fill, int colour, int light) {
        if (v1 - v0 < 0.2f) {
            return;
        }
        // Recessed screen: a thin surround, then the screen itself, drawn as disjoint strips.
        final float lip = 0.022f;
        quad(vc, mat, u0, v0, u1, v0 + lip, COL_RULE, light);
        quad(vc, mat, u0, v1 - lip, u1, v1, COL_RULE, light);
        quad(vc, mat, u0, v0 + lip, u0 + lip, v1 - lip, COL_RULE, light);
        quad(vc, mat, u1 - lip, v0 + lip, u1, v1 - lip, COL_RULE, light);
        final float su0 = u0 + lip;
        final float sv0 = v0 + lip;
        final float su1 = u1 - lip;
        final float sv1 = v1 - lip;

        final int stored = be.groupEnergy();
        final int capacity = be.groupCapacity();
        final int rate = be.chargeRatePerTick();
        final int endurance = be.enduranceTicks();
        final float pad = 0.05f;
        final float innerH = sv1 - sv0;

        final float bigH = Mth.clamp(innerH * 0.38f, 0.16f, 0.30f);
        final float barH = Mth.clamp(innerH * 0.16f, 0.07f, 0.15f);
        final float barTop = sv1 - pad - bigH - 0.035f;
        final float barBottom = barTop - barH;
        final boolean showBar = barBottom > sv0 + 0.02f;

        // [6]
        if (showBar) {
            quad(vc, mat, su0, barTop, su1, sv1, COL_PANEL, light);
            quad(vc, mat, su0, barBottom, su0 + pad, barTop, COL_PANEL, light);
            drawQuarteredBar(vc, mat, su0 + pad, barBottom, su1 - pad, barTop, fill, colour, light);
            quad(vc, mat, su1 - pad, barBottom, su1, barTop, COL_PANEL, light);
            quad(vc, mat, su0, sv0, su1, barBottom, COL_PANEL, light);
        } else {
            quad(vc, mat, su0, sv0, su1, sv1, COL_PANEL, light);
        }

        // Text is drawn through the font with POLYGON_OFFSET, so it never participates in the
        // tiling and cannot fight the band behind it.
        final String pct = String.format(Locale.ROOT, "%.0f%%", fill * 100.0f);
        text(poseStack, buffers, pct, su0 + pad, sv1 - pad - bigH, colour, light, bigH / 9.0f);
        final String amount = compact(stored) + " / " + compact(capacity) + " FE";
        text(poseStack, buffers, amount, su0 + pad + bigH * 2.3f, sv1 - pad - bigH * 0.78f, COL_VALUE, light);

        final String flow = rate == 0 ? "IDLE"
                : (rate > 0 ? "CHARGE +" : "DRAW -") + compact(Math.abs(rate)) + " FE/t";
        final int flowColour = rate > 0 ? 0xFF58DC7A : rate < 0 ? 0xFFFFBA3C : COL_LABEL;
        if (barBottom - LINE > sv0) {
            text(poseStack, buffers, flow, su0 + pad, sv0 + pad * 0.6f, flowColour, light);
            if (endurance >= 0) {
                final String time = (rate > 0 ? "FULL " : "EMPTY ") + clock(endurance);
                text(poseStack, buffers, time, su1 - pad - textWidth(time), sv0 + pad * 0.6f, COL_LABEL, light);
            }
        }
    }

    /**
     * Cooling louvers: horizontal slats with a lit lower edge. Purely hardware texture, and the
     * reason the face no longer has a flat black hole in it.
     */
    private void drawLouvers(VertexConsumer vc, Matrix4f mat, float u0, float v0, float u1, float v1, int light) {
        if (v1 - v0 < 0.06f || u1 <= u0) {
            return;
        }
        final int slats = Math.max(1, Math.round((v1 - v0) / 0.16f));
        final float slatH = (v1 - v0) / slats;
        final float edge = Math.min(0.028f, slatH * 0.3f);
        for (int i = 0; i < slats; i++) {
            final float base = v0 + i * slatH;
            quad(vc, mat, u0, base, u1, base + edge, COL_LOUVER_EDGE, light);
            quad(vc, mat, u0, base + edge, u1, base + slatH, COL_LOUVER, light);
        }
    }

    /** Thin bank: one continuous LED column, the whole readout on a face too narrow for text. */
    private void drawColumn(VertexConsumer vc, Matrix4f mat, float u0, float v0, float u1, float v1,
                            float fill, int colour, int light) {
        final int cells = Math.max(4, Math.round((v1 - v0) * 4.0f));
        final float cellH = (v1 - v0) / cells;
        final float gap = Math.min(cellH * 0.22f, 0.03f);
        final float litCells = fill * cells;

        for (int i = 0; i < cells; i++) {
            final float base = v0 + i * cellH;
            final float bodyTop = base + cellH - gap;
            final boolean lit = i < (int) litCells;
            final boolean partial = !lit && i == (int) litCells && litCells % 1.0f > 0.05f;
            quad(vc, mat, u0, base, u1, bodyTop,
                    lit ? colour : partial ? dim(colour, 0.4f) : COL_EMPTY, light);
            quad(vc, mat, u0, bodyTop, u1, base + cellH, COL_GAP, light);
        }
    }

    /** Charge bar split into four quarters; the ticks are the gaps, so nothing overlaps. */
    private void drawQuarteredBar(VertexConsumer vc, Matrix4f mat, float u0, float v0, float u1, float v1,
                                  float fill, int colour, int light) {
        final float tick = Math.min(0.03f, (u1 - u0) * 0.012f);
        final float quarterW = ((u1 - u0) - tick * 3.0f) / 4.0f;
        float x = u0;
        for (int q = 0; q < 4; q++) {
            final float qEnd = x + quarterW;
            final float local = Mth.clamp(fill * 4.0f - q, 0.0f, 1.0f);
            final float litEnd = x + quarterW * local;
            quad(vc, mat, x, v0, litEnd, v1, colour, light);
            quad(vc, mat, litEnd, v0, qEnd, v1, COL_EMPTY, light);
            x = qEnd;
            if (q < 3) {
                quad(vc, mat, x, v0, x + tick, v1, COL_WELL, light);
                x += tick;
            }
        }
    }

    /** One column per block layer, tallest at the bottom because the pool fills bottom up. */
    private void drawDistribution(VertexConsumer vc, Matrix4f mat, float u0, float v0, float u1, float v1,
                                  int layers, float fill, int colour, int light) {
        final float inner = 0.02f;
        final float gu0 = u0 + inner;
        final float gu1 = u1 - inner;
        final float gv0 = v0 + inner;
        final float gv1 = v1 - inner;
        if (gu1 <= gu0 || gv1 <= gv0 || layers <= 0) {
            quad(vc, mat, u0, v0, u1, v1, COL_WELL, light);
            return;
        }
        // Surround as four strips, so the columns below tile the inner area exactly once instead
        // of being laid over a background quad at the same depth.
        quad(vc, mat, u0, v0, u1, gv0, COL_WELL, light);
        quad(vc, mat, u0, gv1, u1, v1, COL_WELL, light);
        quad(vc, mat, u0, gv0, gu0, gv1, COL_WELL, light);
        quad(vc, mat, gu1, gv0, u1, gv1, COL_WELL, light);
        final float colGap = Math.min(0.04f, (gu1 - gu0) / (layers * 5.0f));
        final float colW = ((gu1 - gu0) - colGap * (layers - 1)) / layers;
        float cx = gu0;
        for (int i = 0; i < layers; i++) {
            final float cEnd = cx + colW;
            final float layerFill = Mth.clamp(fill * layers - i, 0.0f, 1.0f);
            final float top = gv0 + (gv1 - gv0) * layerFill;
            quad(vc, mat, cx, gv0, cEnd, top, dim(colour, 0.85f), light);
            quad(vc, mat, cx, top, cEnd, gv1, COL_EMPTY, light);
            cx = cEnd;
            if (i < layers - 1) {
                quad(vc, mat, cx, gv0, cx + colGap, gv1, COL_WELL, light);
                cx += colGap;
            }
        }
    }

    // primitives

    /** [7] */
    private static void quad(VertexConsumer vc, Matrix4f mat, float u0, float v0, float u1, float v1,
                             int argb, int light) {
        if (u1 <= u0 || v1 <= v0) {
            return;
        }
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        vc.addVertex(mat, u0, v0, 0.0f).setColor(r, g, b, a).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, u1, v0, 0.0f).setColor(r, g, b, a).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, u1, v1, 0.0f).setColor(r, g, b, a).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, u0, v1, 0.0f).setColor(r, g, b, a).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
    }

    private void text(PoseStack poseStack, MultiBufferSource buffers, String s,
                      float u, float v, int argb, int light) {
        text(poseStack, buffers, s, u, v, argb, light, LINE / 9.0f);
    }

    /** [8] */
    private void text(PoseStack poseStack, MultiBufferSource buffers, String s,
                      float u, float v, int argb, int light, float scale) {
        poseStack.pushPose();
        poseStack.translate(u, v + scale * 8.0f, 0.001f);
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(s, 0.0f, 0.0f, argb, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        poseStack.popPose();
    }

    private float textWidth(String s) {
        return font.width(s) * (LINE / 9.0f);
    }

    // formatting

    /** Compact FE, so a seven digit number cannot blow the width of a two block panel. */
    private static String compact(int value) {
        if (value >= 1_000_000_000) {
            return String.format(Locale.ROOT, "%.1fG", value / 1.0e9);
        }
        if (value >= 1_000_000) {
            return String.format(Locale.ROOT, "%.2fM", value / 1.0e6);
        }
        if (value >= 10_000) {
            return String.format(Locale.ROOT, "%.0fk", value / 1.0e3);
        }
        if (value >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1.0e3);
        }
        return String.valueOf(value);
    }

    private static String clock(int ticks) {
        final int seconds = ticks / 20;
        if (seconds >= 3600) {
            return String.format(Locale.ROOT, "%dh%02dm", seconds / 3600, (seconds % 3600) / 60);
        }
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static int chargeColour(float fill) {
        if (fill < 0.2f) {
            return 0xFFFF4A4A;
        }
        if (fill < 0.5f) {
            return 0xFFFFBA3C;
        }
        return 0xFF58DC7A;
    }

    private static int dim(int argb, float factor) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = Math.round(((argb >> 16) & 0xFF) * factor);
        final int g = Math.round(((argb >> 8) & 0xFF) * factor);
        final int b = Math.round((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
