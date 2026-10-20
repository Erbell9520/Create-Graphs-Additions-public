package com.createstats.renderer;

import com.createstats.block.LockerMotion;
import com.createstats.block.SecurityLockerBlock;
import com.createstats.blockentity.SecurityLockerBE;
import com.createstats.client.ModPartialModels;
import com.createstats.gui.security.SecurityTheme;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class SecurityLockerRenderer implements BlockEntityRenderer<SecurityLockerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float PIN_X = 3.2f;
    private static final float PIN_Z = 7.7f;

    private static final float[] BOLT_Y = {4.0f, 8.0f, 12.0f};
    private static final float BOLT_AUTHORED_Y = 8.0f;

    private static final float DIAL_CX = 6.0f;
    private static final float DIAL_CY = 8.0f;

    private static final float DISP_X0 = 9.0f;
    private static final float DISP_Y0 = 9.2f;
    private static final float DISP_X1 = 12.0f;
    private static final float DISP_Y1 = 11.0f;
    private static final float DISP_Z = 6.61f;

    private static final float PIP_X = 2.1f;
    private static final float PIP_W = 0.8f;
    private static final float PIP_H = 0.9f;
    private static final float[] PIP_Y = {4.0f, 6.0f, 8.0f, 10.0f, 12.0f};
    private static final float PIP_Z = 7.01f;

    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            "create_stats", "textures/block/security_locker_glow.png");

    private static final int FULLBRIGHT = 0xF000F0;

    private static final int STANDBY = 0x521E18;
    private static final int PASSED = 0x54E892;
    private static final int DENIED = 0xFF3A28;

    public SecurityLockerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SecurityLockerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!state.hasProperty(SecurityLockerBlock.FACING)) {
            return;
        }
        final float tick = be.motionTicks(partialTick);
        final boolean locking = be.lockingNow();
        final float door = LockerMotion.door(tick, locking);
        final float lead = LockerMotion.lead(tick, locking);
        final float dial = LockerMotion.dial(tick, locking);

        ms.pushPose();
        orient(ms, state.getValue(SecurityLockerBlock.FACING));

        ms.pushPose();

        ms.translate(PIN_X * PX, 0.0f, PIN_Z * PX);
        ms.mulPose(Axis.YP.rotationDegrees(-door));
        ms.translate(-PIN_X * PX, 0.0f, -PIN_Z * PX);
        ms.translate(0.0f, 0.0f, lead * PX);

        put(ms, buffer, state, ModPartialModels.LOCKER_DOOR, light);

        for (int i = 0; i < BOLT_Y.length; i++) {
            ms.pushPose();
            ms.translate(LockerMotion.bolt(i, tick, locking) * PX,
                    (BOLT_Y[i] - BOLT_AUTHORED_Y) * PX, 0.0f);
            put(ms, buffer, state, ModPartialModels.LOCKER_BOLT, light);
            ms.popPose();
        }

        ms.pushPose();
        ms.translate(DIAL_CX * PX, DIAL_CY * PX, 0.0f);
        ms.mulPose(Axis.ZP.rotationDegrees(-dial));
        ms.translate(-DIAL_CX * PX, -DIAL_CY * PX, 0.0f);
        put(ms, buffer, state, ModPartialModels.LOCKER_DIAL, light);
        put(ms, buffer, state, ModPartialModels.LOCKER_SPOKE, light);
        ms.popPose();

        final int display = displayColour(be);
        if (display >= 0) {
            quad(ms, buffer, DISP_X0, DISP_Y0, DISP_X1, DISP_Y1, DISP_Z, display);
        }
        ms.popPose();

        // pips on the frame so they're readable when the door's shut, which is when you need them

        final int required = be.requiredLevel();
        final boolean governed = be.governed() && be.hasPower();
        for (int i = 0; i < PIP_Y.length; i++) {
            final int rung = i + 1;
            if (!governed || rung > required) {
                continue;
            }
            quad(ms, buffer, PIP_X, PIP_Y[i], PIP_X + PIP_W, PIP_Y[i] + PIP_H, PIP_Z,
                    SecurityTheme.ladderInk(required) & 0x00FFFFFF);
        }
        ms.popPose();
    }

    private static int displayColour(SecurityLockerBE be) {
        if (!be.hasPower()) {
            return -1;
        }
        if (be.beingMined()) {
            final long now = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
            return (now / 5L) % 2L == 0L ? DENIED : 0x521208;
        }
        return be.isOpen() ? PASSED : STANDBY;
    }

    private static void orient(PoseStack ms, Direction facing) {
        ms.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case SOUTH -> ms.mulPose(Axis.YP.rotationDegrees(180.0f));
            case WEST -> ms.mulPose(Axis.YP.rotationDegrees(90.0f));
            case EAST -> ms.mulPose(Axis.YP.rotationDegrees(270.0f));
            default -> {
            }
        }
        ms.translate(-0.5, -0.5, -0.5);
    }

    private static void put(PoseStack ms, MultiBufferSource buffer, BlockState state,
                            PartialModel model, int light) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static void quad(PoseStack ms, MultiBufferSource buffer, float x0, float y0, float x1,
                             float y1, float z, int rgb) {
        final VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(GLOW));
        final Matrix4f mat = ms.last().pose();
        final int r = (rgb >> 16) & 0xFF;
        final int g = (rgb >> 8) & 0xFF;
        final int b = rgb & 0xFF;
        final float u0 = x0 / 16.0f;
        final float u1 = x1 / 16.0f;
        final float v0 = (16.0f - y1) / 16.0f;
        final float v1 = (16.0f - y0) / 16.0f;
        final float zz = z * PX;

        vertex(vc, mat, x0 * PX, y1 * PX, zz, u0, v0, r, g, b);
        vertex(vc, mat, x0 * PX, y0 * PX, zz, u0, v1, r, g, b);
        vertex(vc, mat, x1 * PX, y0 * PX, zz, u1, v1, r, g, b);
        vertex(vc, mat, x1 * PX, y1 * PX, zz, u1, v0, r, g, b);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                               float u, float v, int r, int g, int b) {
        vc.addVertex(mat, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULLBRIGHT)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(SecurityLockerBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(48);
    }
}
