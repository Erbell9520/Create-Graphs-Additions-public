package com.createstats.chunkloader.client;

import com.createstats.chunkloader.look.CrownAnchors;
import com.createstats.chunkloader.look.CrownMesh;
import com.createstats.chunkloader.look.CrownPose;
import com.createstats.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SparkCrownItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static SparkCrownItemRenderer instance;

    private final CrownMesh mesh = new CrownMesh();
    private final CrownAnchors anchors = new CrownAnchors();
    private final CrownPose pose = CrownPose.item();
    private final CrownMatterSink matter = new CrownMatterSink();

    private SparkCrownItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static SparkCrownItemRenderer get() {
        if (instance == null) {
            instance = new SparkCrownItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack ps, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(ModBlocks.SPARK_CROWN.get().defaultBlockState(),
                ps, buffers, light, overlay);
        ps.pushPose();
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        matter.beginItem(buffers.getBuffer(Sheets.solidBlockSheet()), ps.last(), light, overlay);
        mesh.build(pose, anchors, matter, CrownMesh.LOD_FULL);
        matter.end();
        ps.popPose();
    }
}
