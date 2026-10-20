package com.createstats.renderer;

import com.createstats.block.BlastShutterBlock;
import com.createstats.block.DoorEase;
import com.createstats.block.ShutterTier;
import com.createstats.blockentity.BlastShutterBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// slats so it folds into 4px of housing
@OnlyIn(Dist.CLIENT)
public class BlastShutterRenderer implements BlockEntityRenderer<BlastShutterBE> {
    private static final float PX = 1.0f / 16.0f;

    public static final int SLATS = 6;

    public static final float SLAT_H = 2.0f;

    public BlastShutterRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static int slatsPerLeaf(int height) {
        return Math.round((16.0f * height - 2.0f * BlastShutterBlock.CASE_H) / SLAT_H / 2.0f);
    }

    public static float lowerStow() {
        return BlastShutterBlock.CASE_H - SLAT_H;
    }

    public static float upperStow(int height) {
        return 16.0f * height - BlastShutterBlock.CASE_H;
    }

    public static float lowerDeployed(int index) {
        return BlastShutterBlock.CASE_H + SLAT_H * index;
    }

    public static float upperDeployed(int index, int height) {
        return 16.0f * height - BlastShutterBlock.CASE_H - SLAT_H * (index + 1);
    }

    @Override
    public void render(BlastShutterBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BlastShutterBlock)) {
            return;
        }

        if (be.settled() && be.isSealed() == state.getValue(BlastShutterBlock.SEALED)) {
            return;
        }

        final float raw = be.progress(partialTick);

        final float t = Mth.clamp(DoorEase.SERVO.curve(raw, !be.isSealed()), 0.0f, 1.0f);
        if (t <= 0.0f) {
            return;
        }

        final int height = Math.max(BlastShutterBlock.MIN_HEIGHT, be.columnHeight());
        final int slats = slatsPerLeaf(height);
        final ShutterTier tier = be.tier();
        final PartialModel slat = tier == ShutterTier.BLAST
                ? ModPartialModels.BLAST_SHUTTER_SLAT : ModPartialModels.WINDOW_SHUTTER_SLAT;
        final Direction facing = state.getValue(BlastShutterBlock.FACING);
        final BlockPos pos = be.getBlockPos();

        final int[] lights = new int[height];
        for (int block = 0; block < height; block++) {
            lights[block] = lightAt(be, pos.above(block), light);
        }

        ms.pushPose();
        orient(ms, facing);

        final float mount = state.getValue(BlastShutterBlock.MOUNT).depth();
        if (mount != 0.0f) {
            ms.translate(0.0f, 0.0f, mount * PX);
        }
        final float stowLower = lowerStow();
        final float stowUpper = upperStow(height);
        for (int i = 0; i < slats; i++) {
            final float low = Mth.lerp(t, stowLower, lowerDeployed(i));
            drawSlat(ms, buffer, state, slat, low, lights[blockOf(low, height)]);

            final float high = Mth.lerp(t, stowUpper, upperDeployed(i, height));
            drawSlat(ms, buffer, state, slat, high, lights[blockOf(high, height)]);
        }
        ms.popPose();
    }

    private static int blockOf(float y, int height) {
        return Mth.clamp((int) (y * PX), 0, height - 1);
    }

    private static int lightAt(BlastShutterBE be, BlockPos at, int fallback) {
        return be.getLevel() == null ? fallback : LevelRenderer.getLightColor(be.getLevel(), at);
    }

    private static void drawSlat(PoseStack ms, MultiBufferSource buffer, BlockState state,
                                 PartialModel model, float y, int light) {
        ms.pushPose();
        ms.translate(0.0f, y * PX, 0.0f);
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        ms.popPose();
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

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(BlastShutterBE be) {
        final BlockPos pos = be.getBlockPos();
        final int height = Math.max(BlastShutterBlock.MIN_HEIGHT, be.columnHeight());
        return new net.minecraft.world.phys.AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + height, pos.getZ() + 1.0);
    }

    @Override
    public boolean shouldRenderOffScreen(BlastShutterBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
