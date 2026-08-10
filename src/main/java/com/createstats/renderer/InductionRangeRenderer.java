package com.createstats.renderer;

import com.createstats.blockentity.InductionRangeBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** [1] */
public class InductionRangeRenderer implements BlockEntityRenderer<InductionRangeBE> {

    /** Top of the trivet grill: the model puts it at 13.6 of 16. */
    private static final float GRILL_TOP = 13.6f / 16.0f;

    private final ItemRenderer itemRenderer;

    public InductionRangeRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(InductionRangeBE be, float partialTicks, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        final ItemStack food = be.displayedFood();
        if (food.isEmpty()) {
            return;
        }
        // [2]
        final int layers = 1 + (food.getCount() - 1) / 3;
        final int seed = (int) be.getBlockPos().asLong();
        for (int layer = 0; layer < layers; layer++) {
            ms.pushPose();
            ms.translate(0.5f, GRILL_TOP + 0.005f + layer * 0.04f, 0.5f);
            // Deterministic per position and per layer, so a pile looks tossed on rather than
            // stamped, and looks the same to every player without anything being synced.
            final float twist = ((seed >> (layer * 5)) % 360) + layer * 37.0f;
            ms.mulPose(Axis.YP.rotationDegrees(twist));
            ms.mulPose(Axis.XP.rotationDegrees(90.0f));
            ms.scale(0.42f, 0.42f, 0.42f);
            itemRenderer.renderStatic(food, ItemDisplayContext.FIXED, light, overlay, ms, buffer,
                    be.getLevel(), seed + layer);
            ms.popPose();
        }
    }
}
