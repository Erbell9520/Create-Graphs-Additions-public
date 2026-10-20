package com.createstats.pducabinet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CabinetItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static CabinetItemRenderer instance;
    private CabinetItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels()); }
    public static CabinetItemRenderer get() { if(instance==null)instance=new CabinetItemRenderer();return instance; }
    @Override
    public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack ps,MultiBufferSource buffers,int light,int overlay) {
        ps.pushPose();ps.translate(0,-.5,0);
        CabinetMesh.draw(ps,buffers,light,overlay,CabinetTextures.item(),0,0,0);ps.popPose();
    }
}
