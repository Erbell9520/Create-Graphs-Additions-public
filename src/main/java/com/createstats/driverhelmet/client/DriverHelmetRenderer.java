package com.createstats.driverhelmet.client;

import com.createstats.client.ModPartialModels;
import com.createstats.driverhelmet.DriverHelmetBE;
import com.createstats.driverhelmet.DriverHelmetBlock;
import com.createstats.driverhelmet.VisorMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public class DriverHelmetRenderer extends SafeBlockEntityRenderer<DriverHelmetBE> {
    public DriverHelmetRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(DriverHelmetBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        final BlockState state = be.getBlockState();

        final double angle = HelmetView.angle(be.isOpen(), be.strokeTick());

        final float yRot = (state.getValue(HorizontalDirectionalBlock.FACING).toYRot() + 180.0f) % 360.0f;

        final int livery = state.hasProperty(DriverHelmetBlock.LIVERY) ? state.getValue(DriverHelmetBlock.LIVERY) : 0;
        final SuperByteBuffer visor = CachedBuffers.partial(ModPartialModels.DRIVER_HELMET_VISOR_LIVERIES[livery], state);
        visor.center()
                .rotate((float) Math.toRadians(-yRot), Direction.UP)
                .uncenter()
                .translate(0.0f, 0.0f, (float) (-VisorMotion.camPush(angle) / 16.0))
                .translate(0.0f, (float) (VisorMotion.PIVOT_Y / 16.0), (float) (VisorMotion.PIVOT_Z / 16.0))
                .rotate((float) Math.toRadians(angle), Direction.EAST)
                .translate(0.0f, (float) (-VisorMotion.PIVOT_Y / 16.0), (float) (-VisorMotion.PIVOT_Z / 16.0))
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
