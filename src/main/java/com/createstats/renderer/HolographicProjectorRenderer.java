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
import com.createstats.block.HolographicProjectorBlock;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.display.HologramSettings;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.BlockState;

@OnlyIn(Dist.CLIENT)
public class HolographicProjectorRenderer implements BlockEntityRenderer<HolographicProjectorBE>
{
    private static final ResourceLocation BLANK_TEXTURE;

    private static final float PANEL_LIFT = 1.25f;

    private static final float FACE_GAP = 0.0025f;

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
        // ACTIVE is the server's call

        final BlockState state = be.getBlockState();
        if (state.hasProperty(HolographicProjectorBlock.ACTIVE)
                && !state.getValue(HolographicProjectorBlock.ACTIVE)) {
            return;
        }
        if (!be.hasData()) {
            return;
        }
        final ResourceLocation texLoc = ProjectorTextureManager.getOrUpdateTexture(be);
        if (texLoc == null) {
            return;
        }
        final HologramSettings settings = be.getHologramSettings();
        final HologramDisplayMode style = settings.getDisplayMode();

        final float holoW = (float)settings.getHologramWidth();
        final float holoH = (float)settings.getHologramHeight();
        final float halfW = holoW / 2.0f;
        final float halfH = holoH / 2.0f;

        final float centerY = 0.125f + PANEL_LIFT + halfH;
        ps.pushPose();
        ps.translate(0.5, (double)centerY, 0.5);
        ps.scale(style.getWidthScale(), style.getHeightScale(), 1.0f);
        switch (settings.getAlignment()) {
            case BILLBOARD -> {
                final float cameraYRot = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
                ps.mulPose(Axis.YP.rotationDegrees(-cameraYRot));
            }
            case SPIN -> {
                final long gameTime = (be.getLevel() != null) ? be.getLevel().getGameTime() : 0L;
                ps.mulPose(Axis.YP.rotationDegrees((gameTime + partialTick) * settings.getRotationSpeed()));
            }

            default -> ps.mulPose(Axis.YP.rotationDegrees(railYRot(be.getBlockState())));
        }
        final VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(texLoc));
        final Matrix4f mat = ps.last().pose();
        final int alpha = 204;
        final int light = 15728880;

        vc.addVertex(mat, -halfW, -halfH, -FACE_GAP).setColor(255, 255, 255, alpha).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, -halfW, halfH, -FACE_GAP).setColor(255, 255, 255, alpha).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, halfW, halfH, -FACE_GAP).setColor(255, 255, 255, alpha).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, halfW, -halfH, -FACE_GAP).setColor(255, 255, 255, alpha).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, -1.0f);
        vc.addVertex(mat, halfW, -halfH, FACE_GAP).setColor(255, 255, 255, alpha).setUv(0.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, halfW, halfH, FACE_GAP).setColor(255, 255, 255, alpha).setUv(0.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, -halfW, halfH, FACE_GAP).setColor(255, 255, 255, alpha).setUv(1.0f, 0.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        vc.addVertex(mat, -halfW, -halfH, FACE_GAP).setColor(255, 255, 255, alpha).setUv(1.0f, 1.0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        ps.popPose();
    }

    private static float railYRot(BlockState state) {
        if (!state.hasProperty(HolographicProjectorBlock.FACING)) {
            return 0.0f;
        }
        return switch (state.getValue(HolographicProjectorBlock.FACING)) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
    }

    static {
        BLANK_TEXTURE = Objects.requireNonNull(ResourceLocation.tryParse("create_stats:textures/special/blank.png"));
    }
}
