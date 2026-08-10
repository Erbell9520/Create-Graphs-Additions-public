package com.createstats.renderer;

import com.createstats.blockentity.OverflowGateBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.FlapStuffs;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class OverflowGateRenderer extends SmartBlockEntityRenderer<OverflowGateBE> {

    public OverflowGateRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(OverflowGateBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
        int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        SuperByteBuffer flapBuffer = CachedBuffers.partial(AllPartialModels.BELT_TUNNEL_FLAP, be.getBlockState());
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        for (Direction direction : Iterate.directions) {
            if (!be.flaps.containsKey(direction)) {
                continue;
            }

            float flap = be.flaps.get(direction).getValue(partialTicks);
            FlapStuffs.renderFlaps(ms, vb, flapBuffer, FlapStuffs.TUNNEL_PIVOT, direction, flap, 0, light);
        }
    }
}
