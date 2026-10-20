package com.createstats.renderer;

import com.createstats.antenna.RangeAttachment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AntennaOnlyRenderer<T extends BlockEntity & RangeAttachment> implements BlockEntityRenderer<T> {
    public AntennaOnlyRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T be, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        AntennaRendererHelper.render(be, ps, buf, packedLight, packedOverlay);
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
