package com.createstats.aviatorhelmet.client;

import com.createstats.CreateStats;
import com.createstats.aviatorhelmet.AviatorGoggles;
import com.createstats.aviatorhelmet.AviatorHelmetItem;
import com.createstats.aviatorhelmet.GoggleMotion;
import com.createstats.aviatorhelmet.HudTone;
import com.createstats.aviatorhelmet.LampWarmth;
import com.createstats.client.ModPartialModels;
import com.createstats.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.data.ModelData;

public class AviatorHelmetItemRenderer extends BlockEntityWithoutLevelRenderer {
    static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "textures/entity/driver_helmet_glow.png");

    private static AviatorHelmetItemRenderer instance;

    private AviatorHelmetItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static AviatorHelmetItemRenderer get() {
        if (instance == null) {
            instance = new AviatorHelmetItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack ps, MultiBufferSource buffers,
                             int light, int overlay) {
        final Minecraft mc = Minecraft.getInstance();
        final AviatorGoggles goggles = AviatorHelmetItem.goggles(stack);

        final double seconds = mc.level == null ? GoggleView.LONG_AGO : GoggleView.secondsInto(goggles);
        final double[] pose = GoggleMotion.pose(goggles.down(), seconds);

        final BlockState state = ModBlocks.AVIATOR_HELMET.getDefaultState();
        final BlockRenderDispatcher blocks = mc.getBlockRenderer();
        final ModelBlockRenderer models = blocks.getModelRenderer();
        final VertexConsumer vc = buffers.getBuffer(NeoForgeRenderTypes.ITEM_UNSORTED_UNLIT_TRANSLUCENT.get());

        models.renderModel(ps.last(), vc, state, ModPartialModels.AVIATOR_HELMET_CAP.get(), 1.0f, 1.0f, 1.0f,
                light, overlay, ModelData.EMPTY, null);
        ps.pushPose();
        GoggleDraw.strapPose(ps, pose[0]);
        models.renderModel(ps.last(), vc, state, ModPartialModels.AVIATOR_HELMET_STRAP.get(), 1.0f, 1.0f, 1.0f,
                light, overlay, ModelData.EMPTY, null);
        ps.popPose();
        ps.pushPose();
        GoggleDraw.gogglePose(ps, pose[0], pose[1]);
        models.renderModel(ps.last(), vc, state, ModPartialModels.AVIATOR_HELMET_GOGGLES.get(), 1.0f, 1.0f, 1.0f,
                light, overlay, ModelData.EMPTY, null);
        if (context == ItemDisplayContext.HEAD && goggles.down()) {
            lamp(ps, buffers, goggles, seconds);
        }
        ps.popPose();
    }

    private static void lamp(PoseStack ps, MultiBufferSource buffers, AviatorGoggles goggles, double seconds) {
        if (!LampWarmth.lit(true, seconds)) {
            return;
        }
        final double warm = LampWarmth.warm(true, seconds);
        final HudTone tone = HudTone.of(goggles.tone());
        final float level = (float) LampWarmth.level(true, seconds);
        GoggleDraw.lamp(buffers.getBuffer(RenderType.eyes(GLOW)), ps.last(), HudTone.warmed(tone.core, warm), level);
    }
}
