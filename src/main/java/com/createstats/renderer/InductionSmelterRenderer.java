package com.createstats.renderer;

import com.createstats.block.InductionSmelterBlock;
import com.createstats.blockentity.InductionSmelterBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.CachedBuffers;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.joml.Matrix4f;

// the machine is cold, the work is hot. induction heats the workpiece, never the walls
@OnlyIn(Dist.CLIENT)
public class InductionSmelterRenderer implements BlockEntityRenderer<InductionSmelterBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");

    private static final float MOUTH_Y0 = 5.0f;
    private static final float MOUTH_Y1 = 11.0f;
    private static final float MOUTH_X0 = 5.0f;
    private static final float MOUTH_X1 = 11.0f;

    private static final float LEAF_INSET = 0.35f;
    private static final float LEAF_SLIDE = 3.2f;
    private static final float[] LEAF_PIVOT = {8.0f, MOUTH_Y0, LEAF_INSET};

    private static final float WORK_Y = MOUTH_Y0 + 0.35f;
    private static final float WORK_Z0 = 3.2f;
    private static final float WORK_Z1 = 11.4f;
    private static final float OUT_Z = 2.0f;

    private static final float[] DIAL_C = {2.0f, 9.0f};
    private static final float DIAL_HALF = 2.0f;
    private static final float DIAL_STAND = 0.4f;

    private static final float[] LEVER_PIVOT = {14.0f, 5.2f, -0.45f};

    private static final float[] FAN_CENTRE = {8.0f, 13.7f, 8.0f};

    private static final float COIL_W = 1.0f;

    private static final float NEEDLE_SWEEP = 130.0f;

    private static final float[] DETENT = {-52.0f, 0.0f, 52.0f};

    private static final float[] COIL = {1.00f, 0.72f, 0.34f};
    private static final float[] COIL_CORE = {1.00f, 0.95f, 0.82f};
    private static final float[] SICK = {0.96f, 0.42f, 0.16f};

    private static final float[] WORK_HOT = {1.00f, 0.44f, 0.11f};
    private static final float[] WORK_WHITE = {1.00f, 0.83f, 0.45f};
    private static final float[] BRASS = {0.86f, 0.70f, 0.34f};

    private final ItemRenderer itemRenderer;

    public InductionSmelterRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(InductionSmelterBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof InductionSmelterBlock)) {
            return;
        }
        final float open = be.shutterOpen(partialTick);
        final float needle = be.needleAt(partialTick);
        final float glow = be.coilGlow(partialTick);
        final boolean starved = be.isStarved();

        final int litBy = blend(light, glow * 0.8f);

        ms.pushPose();
        applyFacing(ms, state.getValue(HorizontalDirectionalBlock.FACING));

        renderShutter(ms, buffer, state, litBy, open);
        renderLever(ms, buffer, state, light, be.damper());
        renderNeedle(ms, buffer, state, litBy, needle);
        renderFan(ms, buffer, state, light, be.fanSpin(partialTick));
        renderWork(be, ms, buffer, litBy, overlay, open, partialTick);
        renderGlow(be, ms, buffer, glow, starved, needle, open, partialTick);

        ms.popPose();
    }

    private void renderShutter(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                               float open) {
        final float slide = LEAF_SLIDE * open;
        ms.pushPose();
        ms.translate((LEAF_PIVOT[0] - slide) * PX, LEAF_PIVOT[1] * PX, LEAF_PIVOT[2] * PX);
        draw(ms, buffer, state, light, ModPartialModels.SMELTER_LEAF_LEFT);
        ms.popPose();

        ms.pushPose();
        ms.translate((LEAF_PIVOT[0] + slide) * PX, LEAF_PIVOT[1] * PX, LEAF_PIVOT[2] * PX);
        draw(ms, buffer, state, light, ModPartialModels.SMELTER_LEAF_RIGHT);
        ms.popPose();
    }

    private void renderLever(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                             int damper) {
        ms.pushPose();
        ms.translate(LEVER_PIVOT[0] * PX, LEVER_PIVOT[1] * PX, LEVER_PIVOT[2] * PX);
        ms.mulPose(Axis.ZP.rotationDegrees(DETENT[Mth.clamp(damper, 0, DETENT.length - 1)]));
        draw(ms, buffer, state, light, ModPartialModels.SMELTER_LEVER);
        ms.popPose();
    }

    private void renderNeedle(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                              float at) {
        ms.pushPose();
        ms.translate(DIAL_C[0] * PX, DIAL_C[1] * PX, (-DIAL_STAND - 0.02f) * PX);
        ms.mulPose(Axis.ZP.rotationDegrees(NEEDLE_SWEEP * 0.5f - NEEDLE_SWEEP * at));
        draw(ms, buffer, state, light, ModPartialModels.SMELTER_NEEDLE);
        ms.popPose();
    }

    private void renderFan(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                           float spin) {
        for (int i = 0; i < 3; i++) {
            ms.pushPose();
            ms.translate(FAN_CENTRE[0] * PX, FAN_CENTRE[1] * PX, FAN_CENTRE[2] * PX);
            ms.mulPose(Axis.YP.rotationDegrees(spin + i * 120.0f));
            draw(ms, buffer, state, light, ModPartialModels.SMELTER_FAN_BLADE);
            ms.popPose();
        }
    }

    private void renderWork(InductionSmelterBE be, PoseStack ms, MultiBufferSource buffer, int light,
                            int overlay, float open, float partialTick) {
        final int seed = (int) be.getBlockPos().asLong();

        final ItemStack input = be.item(InductionSmelterBE.SLOT_IN);
        if (!input.isEmpty() && be.isHot()) {
            final float t = be.workTravel(partialTick);
            final float z = Mth.lerp(t, WORK_Z0, WORK_Z1);
            ms.pushPose();
            ms.translate(8.0f * PX, WORK_Y * PX, z * PX);
            ms.mulPose(Axis.XP.rotationDegrees(90.0f));
            ms.scale(0.44f, 0.44f, 0.44f);
            itemRenderer.renderStatic(input, ItemDisplayContext.FIXED, light, overlay, ms, buffer,
                    be.getLevel(), seed);
            ms.popPose();

            halo(buffer, ms, 8.0f * PX, (WORK_Y + 0.7f) * PX, z * PX, 4.2f * PX,
                    mix(WORK_HOT, WORK_WHITE, t * 0.55f), 0.22f + 0.46f * t);
        }

        if (open < 0.3f) {
            return;
        }
        final ItemStack out = be.item(InductionSmelterBE.SLOT_OUT);
        if (out.isEmpty()) {
            return;
        }
        final float shown = (open - 0.3f) / 0.7f;
        final int layers = Math.min(3, 1 + (out.getCount() - 1) / 12);
        for (int layer = 0; layer < layers; layer++) {
            ms.pushPose();
            ms.translate(8.0f * PX, (WORK_Y + layer * 0.42f) * PX, OUT_Z * PX);
            ms.mulPose(Axis.YP.rotationDegrees(((seed >> (layer * 5)) % 360) + layer * 41.0f));
            ms.mulPose(Axis.XP.rotationDegrees(90.0f));
            ms.scale(0.4f * shown, 0.4f * shown, 0.4f * shown);
            itemRenderer.renderStatic(out, ItemDisplayContext.FIXED, light, overlay, ms, buffer,
                    be.getLevel(), seed + layer);
            ms.popPose();
        }
    }

    private void renderGlow(InductionSmelterBE be, PoseStack ms, MultiBufferSource buffer, float glow,
                            boolean starved, float needle, float open, float partialTick) {
        final VertexConsumer vc = Buffers.live(buffer, RenderType.lightning());
        final Matrix4f mat = ms.last().pose();
        final float time = be.clientAge() + partialTick;

        if (glow > 0.02f) {
            final float flicker = starved
                    ? 0.55f + 0.30f * Mth.sin(time * 1.7f) + 0.15f * Mth.sin(time * 4.1f)
                    : 0.93f + 0.05f * Mth.sin(time * 0.21f) + 0.02f * Mth.sin(time * 0.53f);
            final float level = glow * flicker;
            final float[] col = starved ? SICK : COIL;

            for (float x0 : new float[]{MOUTH_X0 - COIL_W, MOUTH_X1}) {
                flat(vc, mat, x0 * PX, MOUTH_Y0 * PX, (x0 + COIL_W) * PX, MOUTH_Y1 * PX,
                        -0.02f * PX, col, 0.30f + 0.62f * level);
                flat(vc, mat, (x0 + 0.28f) * PX, (MOUTH_Y0 + 0.4f) * PX,
                        (x0 + COIL_W - 0.28f) * PX, (MOUTH_Y1 - 0.4f) * PX,
                        -0.05f * PX, mix(col, COIL_CORE, level * 0.5f), 0.22f + 0.55f * level);
                halo(buffer, ms, (x0 + COIL_W / 2.0f) * PX, (MOUTH_Y0 + MOUTH_Y1) / 2.0f * PX,
                        -0.6f * PX, 4.0f * PX, col, 0.20f * level);
            }

            for (float side : new float[]{-0.03f, 16.03f}) {
                wallGlow(vc, mat, side * PX, 5.0f * PX, 10.0f * PX, 4.0f * PX, 12.0f * PX,
                        col, 0.22f + 0.45f * level);
            }
        }

        if (needle > 0.02f) {
            flat(vc, mat, (DIAL_C[0] - DIAL_HALF + 1.0f) * PX, (DIAL_C[1] - DIAL_HALF + 1.0f) * PX,
                    (DIAL_C[0] + DIAL_HALF - 1.0f) * PX, (DIAL_C[1] + DIAL_HALF - 1.0f) * PX,
                    (-DIAL_STAND - 0.01f) * PX, mix(BRASS, COIL_CORE, needle * 0.4f),
                    0.16f + 0.42f * needle);
        }

        if (glow > 0.5f && open > 0.05f) {
            halo(buffer, ms, 0.5f, 16.6f * PX, 0.5f, 6.0f * PX, COIL, 0.14f * (glow - 0.5f) * 2.0f);
        }
    }

    private static void flat(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1,
                             float z, float[] col, float a) {
        quad(vc, m, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, col, a);
    }

    private static void wallGlow(VertexConsumer vc, Matrix4f m, float x, float y0, float y1,
                                 float z0, float z1, float[] col, float a) {
        quad(vc, m, x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0, col, a);
    }

    private static void halo(MultiBufferSource buffer, PoseStack ms, float x, float y, float z,
                             float radius, float[] col, float a) {
        if (a <= 0.01f) {
            return;
        }
        final VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        final Matrix4f mat = ms.last().pose();
        final int alpha = Math.round(255 * Math.min(1.0f, a));
        final int r = Math.round(255 * col[0]);
        final int g = Math.round(255 * col[1]);
        final int b = Math.round(255 * col[2]);

        textured(vc, mat, x - radius, y, z - radius, x + radius, y, z - radius,
                x + radius, y, z + radius, x - radius, y, z + radius, r, g, b, alpha);
        textured(vc, mat, x - radius, y - radius, z, x + radius, y - radius, z,
                x + radius, y + radius, z, x - radius, y + radius, z, r, g, b, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float[] col, float a) {
        if (a <= 0.004f) {
            return;
        }
        final float r = col[0];
        final float g = col[1];
        final float b = col[2];
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
    }

    private static void textured(VertexConsumer vc, Matrix4f m,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3,
                                 int r, int g, int b, int a) {
        vertex(vc, m, x0, y0, z0, 0.0f, 0.0f, r, g, b, a);
        vertex(vc, m, x1, y1, z1, 1.0f, 0.0f, r, g, b, a);
        vertex(vc, m, x2, y2, z2, 1.0f, 1.0f, r, g, b, a);
        vertex(vc, m, x3, y3, z3, 0.0f, 1.0f, r, g, b, a);
        vertex(vc, m, x3, y3, z3, 0.0f, 1.0f, r, g, b, a);
        vertex(vc, m, x2, y2, z2, 1.0f, 1.0f, r, g, b, a);
        vertex(vc, m, x1, y1, z1, 1.0f, 0.0f, r, g, b, a);
        vertex(vc, m, x0, y0, z0, 0.0f, 0.0f, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0f, 1.0f, 0.0f);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        final float k = Mth.clamp(t, 0.0f, 1.0f);
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k,
                a[2] + (b[2] - a[2]) * k};
    }

    private static int blend(int packedLight, float level) {
        final int block = LightTexture.block(packedLight);
        final int sky = LightTexture.sky(packedLight);
        return LightTexture.pack(Math.max(block, Math.round(13.0f * level)), sky);
    }

    private static void draw(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                             PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(InductionSmelterBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.5);
    }

    private static void applyFacing(PoseStack ms, Direction facing) {
        final float degrees = switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
        if (degrees == 0.0f) {
            return;
        }
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-degrees));
        ms.translate(-0.5f, -0.5f, -0.5f);
    }
}
