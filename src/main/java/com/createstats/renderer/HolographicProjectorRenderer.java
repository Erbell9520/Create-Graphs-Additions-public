package com.createstats.renderer;

import java.util.Objects;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.createstats.blockentity.HolographicProjectorBE;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class HolographicProjectorRenderer implements BlockEntityRenderer<HolographicProjectorBE>
{
    private static final ResourceLocation BLANK_TEXTURE;
    
    public HolographicProjectorRenderer(BlockEntityRendererProvider.Context context) {
    }
    
    public boolean shouldRenderOffScreen(HolographicProjectorBE be) {
        return true;
    }
    
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
    
    public void render(HolographicProjectorBE be, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        AntennaRendererHelper.render(be, ps, buf, packedLight, packedOverlay);
        if (!be.hasData()) {
            return;
        }
        final ResourceLocation texLoc = ProjectorTextureManager.getOrUpdateTexture(be);
        if (texLoc == null) {
            return;
        }
        final HologramDisplayMode style = be.getHologramSettings().getDisplayMode();
        // Decorative base ring / glow disc / scan beam were removed for a cleaner look ,
        // the hologram panel itself is the only projected visual now.
        final float holoW = (float)be.getHologramSettings().getHologramWidth();
        final float holoH = (float)be.getHologramSettings().getHologramHeight();
        final float halfW = holoW / 2.0f;
        final float halfH = holoH / 2.0f;
        final float centerY = 0.875f + halfH;
        ps.pushPose();
        ps.translate(0.5, (double)centerY, 0.5);
        ps.scale(style.getWidthScale(), style.getHeightScale(), 1.0f);
        final float rotSpeed = be.getHologramSettings().getRotationSpeed();
        if (rotSpeed == 0f) {
            // rotationSpeed = 0 (Off): billboard, panel always faces the camera
            final float cameraYRot = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
            ps.mulPose(Axis.YP.rotationDegrees(-cameraYRot));
        } else {
            // [1]
            final long gameTime = (be.getLevel() != null) ? be.getLevel().getGameTime() : 0L;
            ps.mulPose(Axis.YP.rotationDegrees((gameTime + partialTick) * rotSpeed));
        }
        final VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(texLoc));
        final Matrix4f mat = ps.last().pose();
        final int alpha = 204;
        final int light = 15728880;
        vc.addVertex(mat, -halfW, -halfH, 0.0f).setColor(255, 255, 255, alpha).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, -halfW, halfH, 0.0f).setColor(255, 255, 255, alpha).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, halfW, halfH, 0.0f).setColor(255, 255, 255, alpha).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, halfW, -halfH, 0.0f).setColor(255, 255, 255, alpha).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, halfW, -halfH, 0.0f).setColor(255, 255, 255, alpha).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, halfW, halfH, 0.0f).setColor(255, 255, 255, alpha).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, -halfW, halfH, 0.0f).setColor(255, 255, 255, alpha).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, -halfW, -halfH, 0.0f).setColor(255, 255, 255, alpha).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        ps.popPose();
    }

    static {
        BLANK_TEXTURE = Objects.requireNonNull(ResourceLocation.tryParse("create_stats:textures/special/blank.png"));
    }
}
