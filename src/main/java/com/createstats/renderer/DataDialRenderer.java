package com.createstats.renderer;

import com.createstats.block.DataDialBlock;
import com.createstats.blockentity.DataDialBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public class DataDialRenderer implements BlockEntityRenderer<DataDialBE>
{
    private static final float NEEDLE_PIVOT_X = 8.0f / 16.0f;
    private static final float NEEDLE_PIVOT_Y = 8.0f / 16.0f;
    private static final float NEEDLE_PIVOT_Z = 3.0f / 16.0f;
    private static final float NEEDLE_START_ANGLE = (float) (Math.PI * 5.0 / 4.0);

    public DataDialRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DataDialBE be, float partialTick, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        final Direction facing = be.getBlockState().getValue(DataDialBlock.FACING);
        final float progress = Mth.lerp(partialTick, be.prevDialState, be.dialState);
        final float angle = NEEDLE_START_ANGLE - NeedlePhysics.SWEEP_RADIANS * progress;

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        }));
        ms.translate(-0.5, -0.5, -0.5);

        final SuperByteBuffer needle = CachedBuffers.partial(ModPartialModels.DATA_DIAL_NEEDLE, be.getBlockState());
        needle.translate(NEEDLE_PIVOT_X, NEEDLE_PIVOT_Y, NEEDLE_PIVOT_Z)
                .rotate(angle, Direction.SOUTH)
                .translate(-NEEDLE_PIVOT_X, -NEEDLE_PIVOT_Y, -NEEDLE_PIVOT_Z)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        ms.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(DataDialBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
