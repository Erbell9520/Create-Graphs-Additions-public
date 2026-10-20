package com.createstats.renderer;

import com.createstats.block.SecuritySensorBlock;
import com.createstats.blockentity.SecuritySensorBE;
import com.createstats.security.SensorKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class SecuritySensorRenderer implements BlockEntityRenderer<SecuritySensorBE> {
    private static final float PX = 1.0f / 16.0f;

    private record Lens(float x0, float y0, float x1, float y1, float z) {
    }

    private static Lens lensOf(SensorKind kind) {
        return switch (kind) {
            case MOTION -> new Lens(7f, 10f, 9f, 11f, 2.9f);
            case TAMPER -> new Lens(9f, 7f, 11f, 8f, 3f);
            case FIRE -> new Lens(7f, 8f, 9f, 9f, 2.6f);
            case WATER -> new Lens(6f, 11f, 8f, 12f, 2.6f);
        };
    }

    private static final float PROUD = 1.0f / 512.0f;

    private static final ResourceLocation LENS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/sensor_lens.png");

    private static final int FULLBRIGHT = 0xF000F0;

    public SecuritySensorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SecuritySensorBE be, float partialTick, PoseStack ps, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!state.hasProperty(SecuritySensorBlock.FACING)) {
            return;
        }
        final int rgb = lensColour(be);
        if (rgb < 0) {
            return;
        }

        ps.pushPose();
        orient(ps, state.getValue(SecuritySensorBlock.FACING));

        final Lens lens = lensOf(be.kind());
        final float z = lens.z() * PX + PROUD;
        final VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(LENS));
        final Matrix4f mat = ps.last().pose();
        final int r = (rgb >> 16) & 0xFF;
        final int g = (rgb >> 8) & 0xFF;
        final int b = rgb & 0xFF;

        vertex(vc, mat, lens.x0() * PX, lens.y1() * PX, z, 0.0f, 0.0f, r, g, b);
        vertex(vc, mat, lens.x0() * PX, lens.y0() * PX, z, 0.0f, 1.0f, r, g, b);
        vertex(vc, mat, lens.x1() * PX, lens.y0() * PX, z, 1.0f, 1.0f, r, g, b);
        vertex(vc, mat, lens.x1() * PX, lens.y1() * PX, z, 1.0f, 0.0f, r, g, b);
        ps.popPose();
    }

    private static void orient(PoseStack ps, Direction facing) {
        ps.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case SOUTH -> ps.mulPose(Axis.YP.rotationDegrees(180.0f));
            case WEST -> ps.mulPose(Axis.YP.rotationDegrees(90.0f));
            case EAST -> ps.mulPose(Axis.YP.rotationDegrees(270.0f));

            case UP -> ps.mulPose(Axis.XP.rotationDegrees(90.0f));
            case DOWN -> ps.mulPose(Axis.XP.rotationDegrees(270.0f));
            default -> {
            }
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    private static int lensColour(SecuritySensorBE be) {
        if (!be.hasPower()) {
            return -1;
        }
        final SensorKind kind = be.kind();

        final int hue = switch (kind) {
            case MOTION -> 0x66BB6A;
            case TAMPER -> 0xFFB74D;
            case FIRE -> 0xFF3B21;
            case WATER -> 0x29B6F6;
        };
        final float level;
        if (be.tripping()) {
            level = 1.0f;
        } else if (be.watching()) {
            level = 0.68f;
        } else {
            level = 0.30f;
        }
        return dim(hue, level);
    }

    private static int dim(int rgb, float level) {
        final int r = Math.round(((rgb >> 16) & 0xFF) * level);
        final int g = Math.round(((rgb >> 8) & 0xFF) * level);
        final int b = Math.round((rgb & 0xFF) * level);
        return (r << 16) | (g << 8) | b;
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

    public static Vec3 terminalAttachPoint(BlockState state) {
        if (!(state.getBlock() instanceof SecuritySensorBlock sensor)
                || !state.hasProperty(SecuritySensorBlock.FACING)) {
            return new Vec3(0.5, 0.5, 0.5);
        }
        final Direction facing = state.getValue(SecuritySensorBlock.FACING);
        final double depth = lensOf(sensor.kind()).z() / 16.0;

        return new Vec3(
                0.5 - facing.getStepX() * (0.5 - depth),
                0.5 - facing.getStepY() * (0.5 - depth),
                0.5 - facing.getStepZ() * (0.5 - depth));
    }

    @Override
    public boolean shouldRenderOffScreen(SecuritySensorBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(48);
    }
}
