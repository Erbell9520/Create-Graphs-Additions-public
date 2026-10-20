package com.createstats.renderer;

import com.createstats.ModConfiguration;
import com.createstats.client.ModPartialModels;
import com.createstats.vccmk4.BtaG1BE;
import com.createstats.vccmk4.core.BtaG1Motion.Frame;
import com.createstats.vccmk4.core.BtaG1Motion.Lamp;
import com.createstats.vccmk4.core.BtaG1Motion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BtaG1Renderer implements BlockEntityRenderer<BtaG1BE> {
    private static final float RAKE_DEGREES = -22.5f;
    private static final float HINGE_X = 8f / 16f;
    private static final float HINGE_Y = 8f / 16f;
    private static final float HINGE_Z = 8f / 16f;
    private static final float DIAL_X = 5.5f / 16f;
    private static final float DIAL_Y = 11.5f / 16f;
    private static final float DIAL_Z = 8f / 16f;
    private static final float WHEEL_RAKE_DEGREES = -67.5f;
    private static final float WHEEL_X = 5.25f / 16f;
    private static final float WHEEL_Y = 7.5f / 16f;
    private static final float WHEEL_Z = 11.5784f / 16f;
    private static final float THROTTLE_X = 10f / 16f;
    private static final float THROTTLE_Y = 3.5f / 16f;
    private static final float THROTTLE_Z = 11.5f / 16f;
    private static final float BRAKE_X = 13f / 16f;
    private static final float BRAKE_Y = 3.5f / 16f;
    private static final float BRAKE_Z = 11.5f / 16f;

    private static final float[][] HALO = {
        {10.25f, 13.25f, 8.1f, 2.25f, 2.75f, 1},
        {12.75f, 13.25f, 8.1f, 2.25f, 2.75f, 1},
        {10.25f, 9.75f, 8.1f, 2.25f, 2.75f, 1},
        {12.75f, 9.75f, 8.1f, 2.25f, 2.75f, 1},
        {3f, 15.25f, 5.6f, 2.5f, 2.25f, 0},
        {5.5f, 15.25f, 5.6f, 2.5f, 2.25f, 0},
        {8f, 15.25f, 5.6f, 2.5f, 2.25f, 0},
        {10.5f, 15.25f, 5.6f, 2.5f, 2.25f, 0},
        {13f, 15.25f, 5.6f, 2.5f, 2.25f, 0},
    };

    private static final int[] HALO_RGB = {
        0xFF4E3E, 0xFF4E3E, 0xFFB62E, 0xF4F6FF, 0x5CF09C, 0x5CF09C, 0x5CE4FF, 0x6AA8FF, 0x5CF09C,
    };

    private static final ResourceLocation HALO_CARD =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");

    private static final float CENTRE = 0.5f;

    public BtaG1Renderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BtaG1BE be, float partialTick, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return;
        Frame f = be.frame(partialTick);
        VertexConsumer cutout = buffer.getBuffer(RenderType.cutoutMipped());

        ms.pushPose();
        applyFacing(ms, state.getValue(HorizontalDirectionalBlock.FACING));

        CachedBuffers.partial(ModPartialModels.BTA_G1_WHEEL, state)
                .translate(WHEEL_X, WHEEL_Y, WHEEL_Z)
                .rotateDegrees(WHEEL_RAKE_DEGREES, Direction.EAST)
                .rotateDegrees((float) f.wheelDeg, Direction.SOUTH)
                .translate(-CENTRE, -CENTRE, -CENTRE)
                .light(light)
                .renderInto(ms, cutout);

        lever(ModPartialModels.BTA_G1_THROTTLE, state, THROTTLE_X, THROTTLE_Y, THROTTLE_Z, (float) f.throttleDeg, light, ms, cutout);
        lever(ModPartialModels.BTA_G1_BRAKE, state, BRAKE_X, BRAKE_Y, BRAKE_Z, (float) f.brakeDeg, light, ms, cutout);

        int needleLight = SuperByteBuffer.maxLight(light, LightTexture.pack(Math.round((float) f.dial * 15.0f), 0));
        onPanel(CachedBuffers.partial(ModPartialModels.BTA_G1_NEEDLE, state))
                .translate(DIAL_X, DIAL_Y, DIAL_Z)
                .rotateDegrees((float) f.needleDeg, Direction.SOUTH)
                .translate(-CENTRE, -CENTRE, -CENTRE)
                .light(needleLight)
                .renderInto(ms, cutout);

        glow(ModPartialModels.BTA_G1_DIAL_LIT, true, f.dial, state, ms, cutout);
        for (Lamp lamp : Lamp.values()) {
            glow(modelOf(lamp), raked(lamp), f.lamp(lamp), state, ms, cutout);
        }

        halos(f, ms, buffer);

        onPanel(CachedBuffers.partial(ModPartialModels.BTA_G1_GLASS, state))
                .disableDiffuse()
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));

        ms.popPose();
    }

    private static void halos(Frame f, PoseStack ms, MultiBufferSource buffer) {
        VertexConsumer vc = null;
        for (Lamp lamp : Lamp.values()) {
            int alpha = BtaG1Motion.haloAlpha(f.lamp(lamp));
            if (alpha <= 2) continue;
            if (vc == null) vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(HALO_CARD));
            float[] h = HALO[lamp.ordinal()];
            ms.pushPose();
            if (h[5] > 0.5f) {
                ms.translate(HINGE_X, HINGE_Y, HINGE_Z);
                ms.mulPose(Axis.XP.rotationDegrees(RAKE_DEGREES));
                ms.translate(-HINGE_X, -HINGE_Y, -HINGE_Z);
            }
            float u = 1.0f / 16.0f;
            int argb = (alpha << 24) | HALO_RGB[lamp.ordinal()];
            card(vc, ms.last(), h[0] * u, h[1] * u, h[2] * u, h[3] * u, h[4] * u, argb);
            ms.popPose();
        }
    }

    private static void card(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float z, float hw, float hh, int argb) {
        corner(vc, pose, cx - hw, cy - hh, z, 0.0f, 1.0f, argb);
        corner(vc, pose, cx + hw, cy - hh, z, 1.0f, 1.0f, argb);
        corner(vc, pose, cx + hw, cy + hh, z, 1.0f, 0.0f, argb);
        corner(vc, pose, cx - hw, cy + hh, z, 0.0f, 0.0f, argb);
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0f, 0.0f, 1.0f);
    }

    private static void lever(PartialModel model, BlockState state, float x, float y, float z, float degrees,
                              int light, PoseStack ms, VertexConsumer out) {
        CachedBuffers.partial(model, state)
                .translate(x, y, z)
                .rotateDegrees(degrees, Direction.EAST)
                .translate(-CENTRE, -CENTRE, -CENTRE)
                .light(light)
                .renderInto(ms, out);
    }

    private static SuperByteBuffer onPanel(SuperByteBuffer part) {
        return part.translate(HINGE_X, HINGE_Y, HINGE_Z)
                .rotateDegrees(RAKE_DEGREES, Direction.EAST)
                .translate(-HINGE_X, -HINGE_Y, -HINGE_Z);
    }

    private static void glow(PartialModel model, boolean raked, double level, BlockState state, PoseStack ms,
                             VertexConsumer out) {
        if (level <= 0.004) return;
        int c = Math.round((float) Math.min(1.0, level) * 255.0f);
        SuperByteBuffer part = CachedBuffers.partial(model, state);
        if (raked) onPanel(part);
        part.disableDiffuse()
                .light(LightTexture.FULL_BRIGHT)
                .color(c, c, c, 255)
                .renderInto(ms, out);
    }

    private static PartialModel modelOf(Lamp lamp) {
        return switch (lamp) {
            case BRAKE -> ModPartialModels.BTA_G1_LENS_BRAKE;
            case PARK -> ModPartialModels.BTA_G1_LENS_PARK;
            case HOLD -> ModPartialModels.BTA_G1_LENS_HOLD;
            case REVERSE -> ModPartialModels.BTA_G1_LENS_REV;
            case LEFT -> ModPartialModels.BTA_G1_STRIP_LEFT;
            case READY -> ModPartialModels.BTA_G1_STRIP_READY;
            case AUTO -> ModPartialModels.BTA_G1_STRIP_AUTO;
            case BOOST -> ModPartialModels.BTA_G1_STRIP_BOOST;
            case RIGHT -> ModPartialModels.BTA_G1_STRIP_RIGHT;
        };
    }

    private static boolean raked(Lamp lamp) {
        return switch (lamp) {
            case BRAKE, PARK, HOLD, REVERSE -> true;
            case LEFT, READY, AUTO, BOOST, RIGHT -> false;
        };
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
    public boolean shouldRenderOffScreen(BtaG1BE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return ModConfiguration.cappedViewDistance(64);
    }
}
