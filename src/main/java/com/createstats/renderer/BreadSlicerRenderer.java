package com.createstats.renderer;

import com.createstats.block.BreadSlicerBlock;
import com.createstats.blockentity.BreadSlicerBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BreadSlicerRenderer implements BlockEntityRenderer<BreadSlicerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float CUT_X = 7.40f;

    private static final float BLADE_PARK = 7.85f;

    private static final float BLADE_LIFT = 3.90f;

    private static final float SAW_Z = 0.26f;
    private static final float SAW_PER_PASS = 40.0f;

    private static final float SAW_ONSET = 0.75f;

    private static final float PUSH_START = 12.52f;

    private static final float PUSH_END = 8.00f;

    private static final float LOAF_LEN = 4.60f;

    @SuppressWarnings("unused")
    private static final float LOAF_H = 3.40f;

    private static final float HEEL = 0.60f;

    private static final float KERF = 0.05f;

    private static final float HOOD_HINGE_Y = 8.15f;
    private static final float HOOD_HINGE_Z = 11.90f;

    private static final float HOOD_OPEN = 50.0f;

    private static final float LEVER_X0 = 6.1625f;
    private static final float LEVER_STEP = -1.625f;

    private static final float BUTTON_PRESS = 0.16f;

    public BreadSlicerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BreadSlicerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BreadSlicerBlock)) {
            return;
        }

        ms.pushPose();
        applyFacing(ms, state.getValue(BreadSlicerBlock.FACING));

        renderBlade(be, partialTick, ms, buffer, state, light);
        renderFeed(be, partialTick, ms, buffer, state, light);
        renderFascia(be, partialTick, ms, buffer, state, light);
        renderHood(be, partialTick, ms, buffer, state, light);
        renderWires(be, ms, buffer, light);

        ms.popPose();
    }

    private void renderBlade(BreadSlicerBE be, float partialTick, PoseStack ms,
                             MultiBufferSource buffer, BlockState state, int light) {
        final float lift = be.isRunning() ? lift(be.strokePhase(partialTick)) : 0.0f;
        ms.pushPose();
        ms.translate(0.0f, lift * PX, saw(be.feed(partialTick), lift) * PX);
        draw(ms, buffer, state, light, ModPartialModels.BREAD_SLICER_BLADE);
        ms.popPose();
    }

    private static float lift(float phase) {
        if (phase < BreadSlicerBE.RISE_START) {
            return 0.0f;
        }
        if (phase < BreadSlicerBE.RISE_END) {
            return ease((phase - BreadSlicerBE.RISE_START)
                    / (BreadSlicerBE.RISE_END - BreadSlicerBE.RISE_START)) * BLADE_LIFT;
        }
        return (1.0f - ease((phase - BreadSlicerBE.RISE_END) / (1.0f - BreadSlicerBE.RISE_END)))
                * BLADE_LIFT;
    }

    private static float saw(float feed, float lift) {
        if (lift <= 0.0f) {
            return 0.0f;
        }
        return Mth.sin(feed * SAW_PER_PASS * Mth.TWO_PI)
                * SAW_Z * Mth.clamp(lift / SAW_ONSET, 0.0f, 1.0f);
    }

    private static float ease(float t) {
        return (1.0f - Mth.cos(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI)) * 0.5f;
    }

    private void renderFeed(BreadSlicerBE be, float partialTick, PoseStack ms,
                            MultiBufferSource buffer, BlockState state, int light) {
        final float pushX = PUSH_START - be.travel(partialTick);

        ms.pushPose();
        ms.translate(pushX * PX, 0.0f, 0.0f);
        draw(ms, buffer, state, light, ModPartialModels.BREAD_SLICER_PUSHER);
        ms.popPose();

        final int count = Math.max(1, be.strokes());
        final float pitch = BreadSlicerBE.CUT_SPAN / count;
        final int cut = be.strokesCut(partialTick);

        if (!be.loaf().isEmpty()) {
            final float overhang = (be.fedPitches(partialTick) - cut) * pitch;
            final float front = Math.max(pushX - LOAF_LEN, CUT_X - overhang);
            slab(ms, buffer, state, light,
                    cut == 0 ? ModPartialModels.BREAD_SLICER_LOAF_WHOLE
                            : ModPartialModels.BREAD_SLICER_LOAF,
                    front, pushX - front);
            stack(ms, buffer, state, light, front, pitch, cut);
            return;
        }

        if (!be.output().isEmpty()) {
            stack(ms, buffer, state, light, CUT_X, pitch,
                    Math.min(be.output().getCount(), count));
        }
    }

    private void stack(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                       float front, float pitch, int slices) {
        for (int i = 0; i < slices; i++) {
            slab(ms, buffer, state, light, ModPartialModels.BREAD_SLICER_SLICE,
                    front - (i + 1) * pitch + KERF, pitch - 2.0f * KERF);
        }
    }

    private void slab(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                      PartialModel model, float x0, float length) {
        if (length <= 0.0f) {
            return;
        }
        ms.pushPose();
        ms.translate(x0 * PX, 0.0f, 0.0f);
        ms.scale(length, 1.0f, 1.0f);
        draw(ms, buffer, state, light, model);
        ms.popPose();
    }

    private void renderFascia(BreadSlicerBE be, float partialTick, PoseStack ms,
                              MultiBufferSource buffer, BlockState state, int light) {
        ms.pushPose();
        ms.translate((LEVER_X0 + be.leverPosition(partialTick) * LEVER_STEP) * PX, 0.0f, 0.0f);
        draw(ms, buffer, state, light, ModPartialModels.BREAD_SLICER_LEVER);
        ms.popPose();

        final int sunk = be.pressedButton();
        final boolean cutting = be.isCutting();
        final PartialModel green = cutting ? ModPartialModels.BREAD_SLICER_BTN_START_LIT
                : dead(be) ? ModPartialModels.BREAD_SLICER_BTN_START_DEAD
                : ModPartialModels.BREAD_SLICER_BTN_START;
        button(ms, buffer, state, cutting ? LightTexture.FULL_BRIGHT : light, 0, sunk, green);
        button(ms, buffer, state, light, 1, sunk, ModPartialModels.BREAD_SLICER_BTN_STOP);
    }

    private static boolean dead(BreadSlicerBE be) {
        final BreadSlicerBE.Refusal why = be.checkRun();
        return why == BreadSlicerBE.Refusal.NO_POWER || why == BreadSlicerBE.Refusal.SHED
                || why == BreadSlicerBE.Refusal.GATED;
    }

    private void button(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                        int index, int sunk, PartialModel cap) {
        ms.pushPose();
        ms.translate(0.0f, 0.0f, (sunk == index ? BUTTON_PRESS : 0.0f) * PX);
        draw(ms, buffer, state, light, cap);
        ms.popPose();
    }

    private void renderHood(BreadSlicerBE be, float partialTick, PoseStack ms,
                            MultiBufferSource buffer, BlockState state, int light) {
        final float open = be.hood(partialTick);
        ms.pushPose();
        if (open > 0.0f) {
            ms.translate(0.0f, HOOD_HINGE_Y * PX, HOOD_HINGE_Z * PX);
            ms.mulPose(Axis.XP.rotationDegrees(ease(open) * HOOD_OPEN));
            ms.translate(0.0f, -HOOD_HINGE_Y * PX, -HOOD_HINGE_Z * PX);
        }
        draw(ms, buffer, state, light, ModPartialModels.BREAD_SLICER_HOOD_FRAME);
        drawGlass(ms, buffer, state, light, ModPartialModels.BREAD_SLICER_HOOD_GLASS);
        ms.popPose();
    }

    private void renderWires(BreadSlicerBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        if (be.links().isEmpty()) {
            return;
        }
        final net.minecraft.core.BlockPos origin = be.getBlockPos();
        final com.mojang.blaze3d.vertex.VertexConsumer consumer =
                PowerConnectorRenderer.wireBuffer(buffer);
        final org.joml.Matrix4f matrix = ms.last().pose();
        final net.minecraft.world.phys.Vec3 from = terminalAttachPoint(be.getBlockState());
        for (net.minecraft.core.BlockPos link : be.links()) {
            if (!PowerConnectorRenderer.drawsSpan(be.getLevel(), origin, link)) {
                continue;
            }
            final net.minecraft.world.phys.Vec3 to =
                    PowerConnectorRenderer.attachPoint(be.getLevel(), link, origin);
            PowerConnectorRenderer.drawCable(consumer, matrix, be.getLevel(), origin, link,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    public static net.minecraft.world.phys.Vec3 terminalAttachPoint(
            @org.jetbrains.annotations.Nullable BlockState state) {
        if (state == null || !(state.getBlock() instanceof BreadSlicerBlock)) {
            return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        }
        return BreadSlicerBlock.fromAuthoredFrameLocal(state, 5.6, 3.5, 14.75);
    }

    private static void draw(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                             PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static void drawGlass(PoseStack ms, MultiBufferSource buffer, BlockState state,
                                  int light, PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));
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

    @Override
    public AABB getRenderBoundingBox(BreadSlicerBE be) {
        return be.links().isEmpty()
                ? new AABB(be.getBlockPos()).inflate(0.5)
                : PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public boolean shouldRenderOffScreen(BreadSlicerBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
