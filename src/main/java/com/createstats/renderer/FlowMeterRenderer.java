package com.createstats.renderer;

import com.createstats.block.FlowMeterBlock;
import com.createstats.blockentity.FlowMeterBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.joml.Matrix4f;

/** [1] */
@OnlyIn(Dist.CLIENT)
public class FlowMeterRenderer implements BlockEntityRenderer<FlowMeterBE> {

    private static final int IDLE_COLOR = 0x3EB4CC; // calm techy cyan when no fluid / idle

    public FlowMeterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FlowMeterBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof FlowMeterBlock)) {
            return;
        }
        final Direction.Axis axis = state.hasProperty(BlockStateProperties.AXIS)
                ? state.getValue(BlockStateProperties.AXIS)
                : Direction.Axis.Y;

        final int rate = be.getFlowRateMbPerTick();
        final boolean flowing = be.hasFlow() && rate > 0;
        final int dirSign = be.getFlowDirectionSign();

        final String fluidId = be.getFluidId();
        final int color = (fluidId != null && fluidId.contains(":"))
                ? FluidPalette.colorForFluidId(fluidId)
                : IDLE_COLOR;
        final float cr = ((color >> 16) & 0xFF) / 255.0f;
        final float cg = ((color >> 8) & 0xFF) / 255.0f;
        final float cb = (color & 0xFF) / 255.0f;

        final float time = be.getLevel() != null ? (float) (be.getLevel().getGameTime() % 100000L) + partialTick : partialTick;
        // 0..1 loudness from the rate (log so small flows still read, big flows saturate).
        final float intensity = flowing
                ? Mth.clamp((float) (Math.log1p(rate) / Math.log1p(80.0)), 0.18f, 1.0f)
                : 0.0f;

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        if (axis == Direction.Axis.X) {
            ms.mulPose(Axis.ZP.rotationDegrees(90.0f));
        } else if (axis == Direction.Axis.Z) {
            ms.mulPose(Axis.XP.rotationDegrees(90.0f));
        }
        ms.translate(-0.5, -0.5, -0.5);

        final VertexConsumer vc = buffer.getBuffer(RenderType.lightning());
        final Matrix4f mat = ms.last().pose();

        // sight-glass glow (fluid colour): one band per face, no overlapping layers (no z-fight)
        final float g0 = 6.4f / 16.0f;
        final float g1 = 9.6f / 16.0f;
        final float lo = 2.42f / 16.0f;
        final float hi = 13.58f / 16.0f;
        final float glassAlpha = flowing ? (0.60f * intensity + 0.32f) : (0.16f + 0.10f * (float) Math.sin(time * 0.16f));
        band(vc, mat, lo, hi, g0, g1, cr, cg, cb, glassAlpha);

        // satisfactory-style flow rings: bright brass collars that slide along the pipe in the flow
        // direction and bulge outward as they pass, so it visibly "pumps" the fluid along ---
        if (flowing) {
            // [2]
            float dir = dirSign >= 0 ? 1.0f : -1.0f;
            if (axis == Direction.Axis.X) {
                dir = -dir;
            }
            final float speed = 0.012f + 0.05f * intensity;
            final int rings = 3;
            for (int i = 0; i < rings; i++) {
                final float phase = (time * speed + i / (float) rings) % 1.0f;
                final float y = dir >= 0 ? phase : (1.0f - phase);
                final float bulge = (float) Math.sin(phase * Math.PI); // small at the ends, fat in the middle
                final float radius = 0.265f + 0.060f * bulge;          // expands as it travels
                final float half = 0.05f + 0.055f * bulge;             // and grows taller
                final float a = (0.45f + 0.35f * intensity) * (0.35f + 0.65f * bulge);
                final float rr = Math.min(1.0f, 0.85f + 0.15f * cr);
                final float gg = Math.min(1.0f, 0.66f + 0.22f * cg);
                final float bb = Math.min(1.0f, 0.32f + 0.30f * cb);
                band(vc, mat, 0.5f - radius, 0.5f + radius, y - half, y + half, rr, gg, bb, a);
            }
        }

        ms.popPose();
    }

    /** A glowing band wrapping the 4 vertical faces of a square column between y0..y1, spanning lo..hi. */
    private static void band(VertexConsumer vc, Matrix4f m, float lo, float hi, float y0, float y1,
                             float r, float g, float b, float a) {
        if (a <= 0.001f) {
            return;
        }
        // north (z = lo)
        quad(vc, m, lo, y0, lo, hi, y0, lo, hi, y1, lo, lo, y1, lo, r, g, b, a);
        // south (z = hi)
        quad(vc, m, hi, y0, hi, lo, y0, hi, lo, y1, hi, hi, y1, hi, r, g, b, a);
        // west (x = lo)
        quad(vc, m, lo, y0, hi, lo, y0, lo, lo, y1, lo, lo, y1, hi, r, g, b, a);
        // east (x = hi)
        quad(vc, m, hi, y0, lo, hi, y0, hi, hi, y1, hi, hi, y1, lo, r, g, b, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        // [3]
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
    }
}
