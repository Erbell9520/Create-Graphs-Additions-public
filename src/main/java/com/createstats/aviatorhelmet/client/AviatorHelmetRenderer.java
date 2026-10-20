package com.createstats.aviatorhelmet.client;

import com.createstats.aviatorhelmet.AviatorHelmetBE;
import com.createstats.aviatorhelmet.GoggleMotion;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public class AviatorHelmetRenderer extends SafeBlockEntityRenderer<AviatorHelmetBE> {
    public AviatorHelmetRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(AviatorHelmetBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        final BlockState state = be.getBlockState();
        // client time, not server stamp. see GoggleView
        final double[] pose = GoggleView.pose(be.goggles());

        final float yRot = (state.getValue(HorizontalDirectionalBlock.FACING).toYRot() + 180.0f) % 360.0f;
        final VertexConsumer vc = buffer.getBuffer(RenderType.solid());

        final SuperByteBuffer strap = CachedBuffers.partial(ModPartialModels.AVIATOR_HELMET_STRAP, state);
        turn(strap, yRot, pose[0]).light(light).renderInto(ms, vc);

        final SuperByteBuffer goggles = CachedBuffers.partial(ModPartialModels.AVIATOR_HELMET_GOGGLES, state);
        goggles.center()
                .rotate((float) Math.toRadians(-yRot), Direction.UP)
                .uncenter()
                .translate(0.0f, 0.0f, (float) (-pose[1] / 16.0));
        pivot(goggles, pose[0]).light(light).renderInto(ms, vc);
    }

    private static SuperByteBuffer turn(SuperByteBuffer b, float yRot, double angle) {
        b.center()
                .rotate((float) Math.toRadians(-yRot), Direction.UP)
                .uncenter();
        return pivot(b, angle);
    }

    private static SuperByteBuffer pivot(SuperByteBuffer b, double angle) {
        return b.translate(0.0f, (float) (GoggleMotion.PIVOT_Y / 16.0), (float) (GoggleMotion.PIVOT_Z / 16.0))
                .rotate((float) Math.toRadians(angle), Direction.EAST)
                .translate(0.0f, (float) (-GoggleMotion.PIVOT_Y / 16.0), (float) (-GoggleMotion.PIVOT_Z / 16.0));
    }
}
