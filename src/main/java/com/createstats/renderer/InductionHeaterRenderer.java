package com.createstats.renderer;

import com.createstats.blockentity.InductionHeaterBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** [1] */
public class InductionHeaterRenderer extends KineticBlockEntityRenderer<InductionHeaterBE> {

    /** Plate surface height: the cook plate element tops out at 12/16. */
    private static final float PLATE_TOP = 12f / 16f;

    private final ItemRenderer itemRenderer;

    public InductionHeaterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    protected void renderSafe(InductionHeaterBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), Direction.DOWN);
        // [2]
        standardKineticRotationTransform(shaft, be, light)
            .translate(0.0, -0.5 / 16.0, 0.0)
            .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        renderPlateFood(be, ms, buffer, light, overlay);
    }

    private void renderPlateFood(InductionHeaterBE be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        ItemStack food = be.getCookStack();
        if (food.isEmpty()) {
            return;
        }
        // 1 layer per started 16 items: a single chop lies flat, a full stack piles four high.
        int layers = 1 + (food.getCount() - 1) / 16;
        int seed = (int) be.getBlockPos().asLong();
        for (int layer = 0; layer < layers; layer++) {
            ms.pushPose();
            ms.translate(0.5f, PLATE_TOP + 0.005f + layer * 0.045f, 0.5f);
            // Deterministic per-position, per-layer twist so piles look tossed, not stamped.
            float twist = ((seed >> (layer * 5)) % 360) + layer * 37f;
            ms.mulPose(Axis.YP.rotationDegrees(twist));
            ms.mulPose(Axis.XP.rotationDegrees(90.0f));
            ms.scale(0.45f, 0.45f, 0.45f);
            itemRenderer.renderStatic(food, ItemDisplayContext.FIXED, light, overlay, ms, buffer, be.getLevel(), seed + layer);
            ms.popPose();
        }
    }
}
