package com.createstats.renderer;

import com.createstats.block.DispatchBoardBlock;
import com.createstats.blockentity.DispatchBoardBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** [1] */
@OnlyIn(Dist.CLIENT)
public class DispatchBoardRenderer implements BlockEntityRenderer<DispatchBoardBE> {

    private static final int FULL_BRIGHT = 0x00F000F0;

    public DispatchBoardRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DispatchBoardBE be, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        if (!be.isController()) {
            return;
        }

        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof DispatchBoardBlock)) {
            return;
        }

        AntennaRendererHelper.render(be, ps, buf, packedLight, packedOverlay);

        ResourceLocation texture = DispatchBoardTextureManager.getOrUpdateTexture(be);
        if (texture == null) {
            return;
        }

        Direction facing = state.getValue(DispatchBoardBlock.FACING);
        int mbW = be.getMultiblockWidth();
        int mbH = be.getMultiblockHeight();

        ps.pushPose();

        // [2]
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yRot(facing)));
        ps.translate(-0.5, -0.5, -0.5);

        // [3]
        ps.translate(0.0, 0.0, 5.0 / 16.0 + 0.03125);

        renderTexturedQuad(ps, buf, texture, mbW, mbH, packedOverlay);

        ps.popPose();
    }

    private void renderTexturedQuad(PoseStack ps, MultiBufferSource buf, ResourceLocation texture,
                                    int width, int height, int packedOverlay) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(texture));
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();

        float inset = -0.03125f;
        float x0 = inset;
        float x1 = (float) width - inset;
        float y0 = inset;
        float y1 = (float) height - inset;

        // Quad vertices: top-left → bottom-left → bottom-right → top-right
        vertex(vc, pose, normal, x0, y1, 0.0f, 0.0f, 0.0f, packedOverlay);
        vertex(vc, pose, normal, x0, y0, 0.0f, 0.0f, 1.0f, packedOverlay);
        vertex(vc, pose, normal, x1, y0, 0.0f, 1.0f, 1.0f, packedOverlay);
        vertex(vc, pose, normal, x1, y1, 0.0f, 1.0f, 0.0f, packedOverlay);
    }

    private void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal,
                        float x, float y, float z, float u, float v, int packedOverlay) {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    @Override
    public boolean shouldRenderOffScreen(DispatchBoardBE be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(DispatchBoardBE be) {
        BlockPos origin = be.getControllerPos() != null ? be.getControllerPos() : be.getBlockPos();
        AABB base;
        if (be.isController() && (be.getMultiblockWidth() > 1 || be.getMultiblockHeight() > 1)) {
            BlockState state = be.getBlockState();
            if (state.hasProperty(DispatchBoardBlock.FACING)) {
                Direction facing = state.getValue(DispatchBoardBlock.FACING);
                Direction right = facing.getCounterClockWise();
                BlockPos corner = origin.relative(right, be.getMultiblockWidth() - 1).above(be.getMultiblockHeight() - 1);
                base = new AABB(origin).minmax(new AABB(corner));
            } else {
                base = new AABB(be.getBlockPos());
            }
        } else {
            base = new AABB(be.getBlockPos());
        }
        return base.inflate(64.0);
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(96);
    }

    // DispatchBoard FACING points toward the viewer (opposite of StatsDisplayPanel),
    // so every case is offset 180° from the StatsDisplayPanel convention.
    private static float yRot(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0.0f;
            case WEST -> 270.0f;
            case EAST -> 90.0f;
            default -> 180.0f;
        };
    }
}
