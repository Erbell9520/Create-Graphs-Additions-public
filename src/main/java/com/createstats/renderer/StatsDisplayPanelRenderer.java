package com.createstats.renderer;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.math.Axis;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.core.Direction;
import com.createstats.block.StatsDisplayPanelBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;

@OnlyIn(Dist.CLIENT)
public class StatsDisplayPanelRenderer implements BlockEntityRenderer<StatsDisplayPanelBE>
{
    public StatsDisplayPanelRenderer(BlockEntityRendererProvider.Context context) {
    }

    public void render(StatsDisplayPanelBE be, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        if (!be.isController()) {
            return;
        }
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof StatsDisplayPanelBlock)) {
            return;
        }
        AntennaRendererHelper.render(be, ps, buf, packedLight, packedOverlay);
        final Direction facing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
        final ResourceLocation textureLoc = DisplayTextureManager.getOrUpdateTexture(be, partialTick);
        if (textureLoc == null) {
            return;
        }
        final int mbW = be.getMultiblockWidth();
        final int mbH = be.getMultiblockHeight();
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        final float yRot = switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
        ps.mulPose(Axis.YP.rotationDegrees(yRot));
        ps.translate(-0.5, -0.5, -0.5);
        ps.translate(0.0, 0.0, 3.0 / 16.0 + 0.03125);
        // keyed on the controller so the whole wall flickers together

        final float brightness = com.createstats.power.client.FePowerVisuals
                .screenBrightness(be.getControllerBE().getBlockPos(), be.feConsumer());
        this.renderTexturedQuad(ps, buf, textureLoc, mbW, mbH, packedLight, packedOverlay, brightness);
        ps.popPose();

        com.createstats.client.battle.BattleTacticalV2PanelHologram.render(be, ps, buf, packedLight, partialTick);
    }

    private void renderTexturedQuad(PoseStack ps, MultiBufferSource buf, ResourceLocation texture, int width, int height, int packedLight, int packedOverlay, float brightness) {
        final int tint = Math.max(0, Math.min(255, Math.round(255.0f * brightness)));
        final VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(texture));
        final Matrix4f mat = ps.last().pose();
        final Matrix3f norm = ps.last().normal();
        final float w = (float)width;
        final float h = (float)height;
        final float x0;
        final float inset = x0 = -0.03125f;
        final float x2 = w - inset;
        final float y0 = inset;
        final float y2 = h - inset;
        vc.addVertex(mat, x0, y2, 0.0f).setColor(tint, tint, tint, 255).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, x0, y0, 0.0f).setColor(tint, tint, tint, 255).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, x2, y0, 0.0f).setColor(tint, tint, tint, 255).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, x2, y2, 0.0f).setColor(tint, tint, tint, 255).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(0.0f, 0.0f, -1.0f);
    }

    public boolean shouldRenderOffScreen(StatsDisplayPanelBE be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(StatsDisplayPanelBE be) {
        return be.getRenderBoundingBox();
    }

    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
