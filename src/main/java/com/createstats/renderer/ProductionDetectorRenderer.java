package com.createstats.renderer;

import com.createstats.block.ProductionDetectorBlock;
import com.createstats.blockentity.ProductionDetectorBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ProductionDetectorRenderer implements BlockEntityRenderer<ProductionDetectorBE> {
    private static final float FACE_EPSILON = 1.0f / 512.0f;
    private static final float SCREEN_WIDTH = 14.0f / 16.0f;
    private static final float SCREEN_HEIGHT = 13.75f / 16.0f;
    private static final float SCREEN_TILT_DEGREES = -30.0f;
    private static final float SCREEN_BOTTOM_Y = 12.0f / 16.0f;
    private static final float SCREEN_FRONT_Z = 14.5f / 16.0f;
    private static final ResourceLocation ANDESITE_CASING_TEX =
        ResourceLocation.fromNamespaceAndPath("create", "block/andesite_casing");

    public ProductionDetectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ProductionDetectorBE be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ProductionDetectorBlock)) {
            return;
        }

        final ResourceLocation texture = ProductionDetectorTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }

        final Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        }));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        poseStack.pushPose();
        applyDisplayTransform(poseStack);
        renderSupportPlate(poseStack, buffer, packedLight, packedOverlay);
        renderDisplayQuad(poseStack, buffer, texture, packedOverlay);
        poseStack.popPose();
        poseStack.popPose();
    }

    private void applyDisplayTransform(PoseStack poseStack) {
        poseStack.translate(0.5f, SCREEN_BOTTOM_Y, SCREEN_FRONT_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(SCREEN_TILT_DEGREES));
    }

    private void renderSupportPlate(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        final TextureAtlasSprite sprite = Minecraft.getInstance()
            .getModelManager()
            .getAtlas(TextureAtlas.LOCATION_BLOCKS)
            .getSprite(ANDESITE_CASING_TEX);
        if (sprite == null) {
            return;
        }
        final VertexConsumer vc = buffer.getBuffer(Sheets.cutoutBlockSheet());
        final Matrix4f matrix = poseStack.last().pose();
        final Matrix3f normal = poseStack.last().normal();
        // [1]
        final float x0 = -SCREEN_WIDTH / 2.0f;
        final float x1 = SCREEN_WIDTH / 2.0f;
        final float y0 = 0.0f;
        final float y1 = SCREEN_HEIGHT;
        final float zFront = -0.1f / 16.0f;
        final float zBack = zFront - (1.5f / 16.0f);
        final float u0 = sprite.getU0();
        final float u1 = sprite.getU1();
        final float v0 = sprite.getV0();
        final float v1 = sprite.getV1();
        // Front face (+Z, toward the screen)
        plateVertex(vc, matrix, normal, x0, y1, zFront, u0, v0, packedLight, packedOverlay, 0.0f, 0.0f, 1.0f);
        plateVertex(vc, matrix, normal, x0, y0, zFront, u0, v1, packedLight, packedOverlay, 0.0f, 0.0f, 1.0f);
        plateVertex(vc, matrix, normal, x1, y0, zFront, u1, v1, packedLight, packedOverlay, 0.0f, 0.0f, 1.0f);
        plateVertex(vc, matrix, normal, x1, y1, zFront, u1, v0, packedLight, packedOverlay, 0.0f, 0.0f, 1.0f);
        // Back face (-Z)
        plateVertex(vc, matrix, normal, x1, y1, zBack, u1, v0, packedLight, packedOverlay, 0.0f, 0.0f, -1.0f);
        plateVertex(vc, matrix, normal, x1, y0, zBack, u1, v1, packedLight, packedOverlay, 0.0f, 0.0f, -1.0f);
        plateVertex(vc, matrix, normal, x0, y0, zBack, u0, v1, packedLight, packedOverlay, 0.0f, 0.0f, -1.0f);
        plateVertex(vc, matrix, normal, x0, y1, zBack, u0, v0, packedLight, packedOverlay, 0.0f, 0.0f, -1.0f);
        // Left edge (-X)
        plateVertex(vc, matrix, normal, x0, y1, zBack, u0, v0, packedLight, packedOverlay, -1.0f, 0.0f, 0.0f);
        plateVertex(vc, matrix, normal, x0, y0, zBack, u0, v1, packedLight, packedOverlay, -1.0f, 0.0f, 0.0f);
        plateVertex(vc, matrix, normal, x0, y0, zFront, u1, v1, packedLight, packedOverlay, -1.0f, 0.0f, 0.0f);
        plateVertex(vc, matrix, normal, x0, y1, zFront, u1, v0, packedLight, packedOverlay, -1.0f, 0.0f, 0.0f);
        // Right edge (+X)
        plateVertex(vc, matrix, normal, x1, y1, zFront, u0, v0, packedLight, packedOverlay, 1.0f, 0.0f, 0.0f);
        plateVertex(vc, matrix, normal, x1, y0, zFront, u0, v1, packedLight, packedOverlay, 1.0f, 0.0f, 0.0f);
        plateVertex(vc, matrix, normal, x1, y0, zBack, u1, v1, packedLight, packedOverlay, 1.0f, 0.0f, 0.0f);
        plateVertex(vc, matrix, normal, x1, y1, zBack, u1, v0, packedLight, packedOverlay, 1.0f, 0.0f, 0.0f);
        // Top edge (+Y)
        plateVertex(vc, matrix, normal, x0, y1, zBack, u0, v0, packedLight, packedOverlay, 0.0f, 1.0f, 0.0f);
        plateVertex(vc, matrix, normal, x0, y1, zFront, u0, v1, packedLight, packedOverlay, 0.0f, 1.0f, 0.0f);
        plateVertex(vc, matrix, normal, x1, y1, zFront, u1, v1, packedLight, packedOverlay, 0.0f, 1.0f, 0.0f);
        plateVertex(vc, matrix, normal, x1, y1, zBack, u1, v0, packedLight, packedOverlay, 0.0f, 1.0f, 0.0f);
        // Bottom edge (-Y)
        plateVertex(vc, matrix, normal, x0, y0, zFront, u0, v0, packedLight, packedOverlay, 0.0f, -1.0f, 0.0f);
        plateVertex(vc, matrix, normal, x0, y0, zBack, u0, v1, packedLight, packedOverlay, 0.0f, -1.0f, 0.0f);
        plateVertex(vc, matrix, normal, x1, y0, zBack, u1, v1, packedLight, packedOverlay, 0.0f, -1.0f, 0.0f);
        plateVertex(vc, matrix, normal, x1, y0, zFront, u1, v0, packedLight, packedOverlay, 0.0f, -1.0f, 0.0f);
    }

    private void plateVertex(VertexConsumer vc, Matrix4f matrix, Matrix3f normal, float x, float y, float z,
                             float u, float v, int packedLight, int packedOverlay,
                             float nx, float ny, float nz) {
        vc.addVertex(matrix, x, y, z)
            .setColor(160, 160, 160, 255)
            .setUv(u, v)
            .setOverlay(packedOverlay)
            .setLight(packedLight)
            .setNormal(nx, ny, nz);
    }

    private void renderDisplayQuad(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, int packedOverlay) {
        final VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        final Matrix4f matrix = poseStack.last().pose();
        final Matrix3f normal = poseStack.last().normal();
        final float x0 = -SCREEN_WIDTH / 2.0f;
        final float x1 = SCREEN_WIDTH / 2.0f;
        vertex(vertexConsumer, matrix, normal, x0, SCREEN_HEIGHT, FACE_EPSILON, 0.0f, 0.0f, packedOverlay);
        vertex(vertexConsumer, matrix, normal, x0, 0.0f, FACE_EPSILON, 0.0f, 1.0f, packedOverlay);
        vertex(vertexConsumer, matrix, normal, x1, 0.0f, FACE_EPSILON, 1.0f, 1.0f, packedOverlay);
        vertex(vertexConsumer, matrix, normal, x1, SCREEN_HEIGHT, FACE_EPSILON, 1.0f, 0.0f, packedOverlay);

        vertex(vertexConsumer, matrix, normal, x1, SCREEN_HEIGHT, FACE_EPSILON, 1.0f, 0.0f, packedOverlay);
        vertex(vertexConsumer, matrix, normal, x1, 0.0f, FACE_EPSILON, 1.0f, 1.0f, packedOverlay);
        vertex(vertexConsumer, matrix, normal, x0, 0.0f, FACE_EPSILON, 0.0f, 1.0f, packedOverlay);
        vertex(vertexConsumer, matrix, normal, x0, SCREEN_HEIGHT, FACE_EPSILON, 0.0f, 0.0f, packedOverlay);
    }

    private void vertex(VertexConsumer vertexConsumer, Matrix4f matrix, Matrix3f normal, float x, float y, float z,
                        float u, float v, int packedOverlay) {
        vertexConsumer.addVertex(matrix, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(packedOverlay)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(0.0f, 0.0f, 1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(ProductionDetectorBE be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(ProductionDetectorBE be) {
        final BlockPos pos = be.getBlockPos();
        return new AABB(pos).expandTowards(0.0, 1.0, 0.0).inflate(1.0);
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(64);
    }
}
