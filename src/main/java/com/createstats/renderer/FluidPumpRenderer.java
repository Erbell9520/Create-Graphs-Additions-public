package com.createstats.renderer;

import com.createstats.blockentity.FluidPumpBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class FluidPumpRenderer implements BlockEntityRenderer<FluidPumpBE> {
    public FluidPumpRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidPumpBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        final float angle = be.renderAngle(partialTicks);

        ms.pushPose();
        ms.translate(0.5, 0.0, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(angle));
        ms.translate(-0.5, 0.0, -0.5);
        CachedBuffers.partial(ModPartialModels.FLUID_PUMP_IMPELLER, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();

        if (!be.links().isEmpty()) {
            final net.minecraft.core.BlockPos origin = be.getBlockPos();
            final com.mojang.blaze3d.vertex.VertexConsumer consumer = PowerConnectorRenderer.wireBuffer(buffer);
            final org.joml.Matrix4f matrix = ms.last().pose();
            for (final net.minecraft.core.BlockPos link : be.links()) {
                if (!PowerConnectorRenderer.drawsSpan(be.getLevel(), origin, link)) {
                    continue;
                }
                final net.minecraft.world.phys.Vec3 to =
                        PowerConnectorRenderer.attachPoint(be.getLevel(), link, origin);
                PowerConnectorRenderer.drawCable(consumer, matrix, be.getLevel(), origin, link,
                        0.5f, 0.5f, 0.5f,
                        (float) (link.getX() - origin.getX() + to.x),
                        (float) (link.getY() - origin.getY() + to.y),
                        (float) (link.getZ() - origin.getZ() + to.z),
                        light, be.tierOf(link));
            }
        }
    }

    // off screen render or wires vanish when their block is occluded
    @Override
    public boolean shouldRenderOffScreen(com.createstats.blockentity.FluidPumpBE be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.createstats.blockentity.FluidPumpBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
