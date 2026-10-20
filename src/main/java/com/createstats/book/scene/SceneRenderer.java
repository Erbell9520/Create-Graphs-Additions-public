package com.createstats.book.scene;

import com.createstats.book.BookTokens;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public final class SceneRenderer {
    private static final float YAW_PER_TICK = 0.9f;

    private SceneRenderer() {
    }

    public static void render(GuiGraphics g, int x, int y, int w, int h, SceneSpec spec, float ticks) {
        if (spec.placements().isEmpty() || w <= 8 || h <= 8) {
            return;
        }
        g.fill(x, y, x + w, y + h, BookTokens.SCENE_WELL);
        corners(g, x, y, w, h);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final SceneSpec.Placement p : spec.placements()) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
            maxZ = Math.max(maxZ, p.z());
        }

        final double cx = (minX + maxX + 1.0) * 0.5;
        final double cy = (minY + maxY + 1.0) * 0.5;
        final double cz = (minZ + maxZ + 1.0) * 0.5;
        final double extent = Math.max(1.0, Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) + 1.0);
        final float yaw = spec.spin() ? ((ticks * YAW_PER_TICK) % 360.0f + 360.0f) % 360.0f : 32.0f;

        final double scale = Math.min(w, h) * 0.52 / extent;

        g.flush();
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        final PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2.0f, y + h / 2.0f, 250.0f);
        pose.scale((float) scale, (float) -scale, (float) scale);
        pose.mulPose(Axis.XP.rotationDegrees(spec.pitch()));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        Lighting.setupFor3DItems();
        final BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        final MultiBufferSource.BufferSource buffer = g.bufferSource();
        for (final SceneSpec.Placement p : spec.placements()) {
            final BlockState state = p.state().get();
            if (state == null || state.isAir()) {
                continue;
            }
            pose.pushPose();
            pose.translate(p.x() - cx, p.y() - cy, p.z() - cz);
            final BakedModel model = brd.getBlockModel(state);
            final RenderType world = ItemBlockRenderTypes.getRenderType(state, false);
            final RenderType sheet = world == RenderType.translucent()
                    ? Sheets.translucentCullBlockSheet() : Sheets.cutoutBlockSheet();
            brd.getModelRenderer().renderModel(pose.last(), buffer.getBuffer(sheet), state, model,
                    1.0f, 1.0f, 1.0f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffer.endBatch();
        pose.popPose();
        g.disableScissor();
        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        g.flush();
    }

    private static void corners(GuiGraphics g, int x, int y, int w, int h) {
        final int c = BookTokens.RULE;
        final int len = 7;
        g.fill(x, y, x + len, y + 1, c);
        g.fill(x, y, x + 1, y + len, c);
        g.fill(x + w - len, y, x + w, y + 1, c);
        g.fill(x + w - 1, y, x + w, y + len, c);
        g.fill(x, y + h - 1, x + len, y + h, c);
        g.fill(x, y + h - len, x + 1, y + h, c);
        g.fill(x + w - len, y + h - 1, x + w, y + h, c);
        g.fill(x + w - 1, y + h - len, x + w, y + h, c);
    }
}
