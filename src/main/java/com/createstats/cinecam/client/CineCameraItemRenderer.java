package com.createstats.cinecam.client;

import com.createstats.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CineCameraItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float FIT = 0.42f;

    private static CineCameraItemRenderer instance;

    private CineCameraItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static CineCameraItemRenderer get() {
        if (instance == null) instance = new CineCameraItemRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack ps, MultiBufferSource buffers,
                             int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5f, 0.02f, 0.5f);
        ps.scale(FIT, FIT, FIT);
        ps.translate(-0.5f, 0f, -0.5f);
        CineCameraRenderer.draw(ps, buffers, ModBlocks.CINE_CAMERA.getDefaultState(), light, light,
                CineCameraRenderer.Pose.REST);
        ps.popPose();
    }
}
