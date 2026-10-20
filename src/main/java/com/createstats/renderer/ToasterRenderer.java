package com.createstats.renderer;

import com.createstats.block.ToasterBlock;
import com.createstats.blockentity.ToasterBE;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ToasterRenderer implements BlockEntityRenderer<ToasterBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float[] SLOT_X = {10.20f, 5.80f};
    private static final float SLICE_Z = 7.80f;
    private static final float CARRIAGE_Y_DOWN = 3.97f;
    private static final float CARRIAGE_TRAVEL = 3.60f;

    private static final float SLICE_H = 6.40f;

    private static final float LEVER_X = 3.60f;
    private static final float LEVER_Y_DOWN = 5.45f;
    private static final float LEVER_Z = 2.10f;

    private static final float DIAL_CX = 11.30f;
    private static final float DIAL_CY = 7.90f;
    private static final float DIAL_Z = 2.10f;
    private static final float DIAL_SWEEP = 240.0f;
    private static final int DIAL_STEPS = 7;

    private static final float[] ELEMENT_X = {11.34f, 9.06f, 6.94f, 4.66f};
    private static final float ELEMENT_Y = 7.10f;
    private static final float ELEMENT_Z = 7.80f;

    private static final float[] TRAY_ANCHOR = {8.00f, 1.45f, 2.20f};
    private static final float TRAY_TRAVEL = 4.00f;

    private static final float[] BUTTON_X = {8.40f, 5.90f};
    private static final float BUTTON_Y = 4.35f;
    private static final float BUTTON_Z = 2.20f;
    private static final float BUTTON_PRESS = 0.18f;

    private static final PartialModel[] ELEMENTS = {
            ModPartialModels.TOASTER_ELEMENT_0, ModPartialModels.TOASTER_ELEMENT_1,
            ModPartialModels.TOASTER_ELEMENT_2, ModPartialModels.TOASTER_ELEMENT_3,
    };

    private static final PartialModel[] SLICES = {
            ModPartialModels.TOASTER_SLICE_0, ModPartialModels.TOASTER_SLICE_1,
            ModPartialModels.TOASTER_SLICE_2, ModPartialModels.TOASTER_SLICE_3,
            ModPartialModels.TOASTER_SLICE_4, ModPartialModels.TOASTER_SLICE_5,
    };

    public ToasterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ToasterBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ToasterBlock)) {
            return;
        }

        ms.pushPose();
        applyFacing(ms, state.getValue(ToasterBlock.FACING));

        renderElements(be, partialTick, ms, buffer, state, light);
        renderCarriages(be, partialTick, ms, buffer, state, light);
        renderFascia(be, partialTick, ms, buffer, state, light);
        renderWires(be, ms, buffer, light);

        ms.popPose();
    }

    private void renderWires(ToasterBE be, PoseStack ms, MultiBufferSource buffer, int light) {
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
        if (state == null || !(state.getBlock() instanceof ToasterBlock)) {
            return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        }
        return ToasterBlock.fromAuthoredFrameLocal(state, 5.6, 5.1, 13.55);
    }

    private void renderElements(ToasterBE be, float partialTick, PoseStack ms,
                                MultiBufferSource buffer, BlockState state, int light) {
        final int stage = be.elementStage(partialTick);
        for (int i = 0; i < ELEMENT_X.length; i++) {
            final int shown = be.elementLit(i) ? stage : 0;
            ms.pushPose();
            ms.translate(ELEMENT_X[i] * PX, ELEMENT_Y * PX, ELEMENT_Z * PX);
            draw(ms, buffer, state, glow(light, shown), ELEMENTS[shown]);
            ms.popPose();
        }
    }

    private static int glow(int light, int stage) {
        if (stage <= 0) {
            return light;
        }
        return LightTexture.pack(Math.max(LightTexture.block(light), 5 + stage * 3),
                LightTexture.sky(light));
    }

    private void renderCarriages(ToasterBE be, float partialTick, PoseStack ms,
                                 MultiBufferSource buffer, BlockState state, int light) {
        final float travel = 1.0f - be.carriage(partialTick);
        final float y = CARRIAGE_Y_DOWN + travel * CARRIAGE_TRAVEL;
        final float hop = be.sliceHop(partialTick);

        for (int i = 0; i < SLOT_X.length; i++) {
            ms.pushPose();
            ms.translate(SLOT_X[i] * PX, y * PX, SLICE_Z * PX);
            draw(ms, buffer, state, light, ModPartialModels.TOASTER_CARRIAGE);
            ms.popPose();

            if (be.slot(i).isEmpty()) {
                continue;
            }
            final float tilt = be.sliceTilt(i, partialTick);
            ms.pushPose();
            ms.translate(SLOT_X[i] * PX, (y + hop) * PX, SLICE_Z * PX);
            if (tilt != 0.0f) {
                ms.translate(0.0f, SLICE_H * 0.5f * PX, 0.0f);
                ms.mulPose(Axis.XP.rotationDegrees(tilt));
                ms.translate(0.0f, -SLICE_H * 0.5f * PX, 0.0f);
            }
            drawCutout(ms, buffer, state, light, SLICES[clampStage(be.sliceStage(i))]);
            ms.popPose();
        }

        ms.pushPose();
        ms.translate(LEVER_X * PX, (LEVER_Y_DOWN + travel * CARRIAGE_TRAVEL) * PX, LEVER_Z * PX);
        draw(ms, buffer, state, light, ModPartialModels.TOASTER_LEVER);
        ms.popPose();
    }

    private static int clampStage(int stage) {
        return Math.max(0, Math.min(SLICES.length - 1, stage));
    }

    private void renderFascia(ToasterBE be, float partialTick, PoseStack ms,
                              MultiBufferSource buffer, BlockState state, int light) {
        ms.pushPose();
        ms.translate(DIAL_CX * PX, DIAL_CY * PX, DIAL_Z * PX);
        ms.mulPose(Axis.ZP.rotationDegrees(be.dialAngle(partialTick, DIAL_SWEEP, DIAL_STEPS)));
        draw(ms, buffer, state, light, ModPartialModels.TOASTER_DIAL);
        ms.popPose();

        ms.pushPose();
        ms.translate(TRAY_ANCHOR[0] * PX, TRAY_ANCHOR[1] * PX,
                (TRAY_ANCHOR[2] - be.trayOut(partialTick) * TRAY_TRAVEL) * PX);
        draw(ms, buffer, state, light, ModPartialModels.TOASTER_TRAY);
        ms.popPose();

        final int sunk = be.pressedButton();
        button(ms, buffer, state, light, 0, sunk, be.isBagel(), ModPartialModels.TOASTER_BTN_BLUE);
        button(ms, buffer, state, light, 1, sunk, be.isDown(), ModPartialModels.TOASTER_BTN_RED);
    }

    private void button(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                        int index, int sunk, boolean on, PartialModel colour) {
        ms.pushPose();
        ms.translate(BUTTON_X[index] * PX, BUTTON_Y * PX,
                (BUTTON_Z + (sunk == index ? BUTTON_PRESS : 0.0f)) * PX);
        draw(ms, buffer, state, on ? LightTexture.FULL_BRIGHT : light,
                on ? colour : ModPartialModels.TOASTER_BTN_OFF);
        ms.popPose();
    }

    private static void draw(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                             PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static void drawCutout(PoseStack ms, MultiBufferSource buffer, BlockState state,
                                   int light, PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
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
    public AABB getRenderBoundingBox(ToasterBE be) {
        return be.links().isEmpty()
                ? new AABB(be.getBlockPos()).inflate(0.5)
                : PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public boolean shouldRenderOffScreen(ToasterBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
