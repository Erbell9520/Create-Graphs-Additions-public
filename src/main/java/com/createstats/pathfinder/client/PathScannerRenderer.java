package com.createstats.pathfinder.client;

import com.createstats.pathfinder.block.PathScannerBlock;
import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.createstats.renderer.HologramRenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;

@OnlyIn(Dist.CLIENT)
public class PathScannerRenderer implements BlockEntityRenderer<PathScannerBE> {
    private static final double VIEW_DISTANCE = 32.0;
    private static final float FRONT_Z = -0.006f;

    public PathScannerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    @SuppressWarnings("null")
    public void render(@Nonnull PathScannerBE scanner, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!PathfinderBlockVisuals.closeEnough(scanner, VIEW_DISTANCE)) {
            return;
        }

        long gameTime = PathfinderBlockVisuals.gameTime(scanner);
        boolean recentlyProcessed = scanner.lastProcessedChunks() > 0 && gameTime - scanner.lastScanTick() <= 24L;
        boolean scanning = scanner.queueDepth() > 0 || recentlyProcessed;
        boolean hasCoverage = scanner.expectedChunkCount() > 0 && scanner.loadedChunkCount() > 0;
        boolean hasUnknown = scanner.expectedChunkCount() > 0 && scanner.unknownChunkCount() > 0;
        if (!scanning && !hasCoverage && !hasUnknown) {
            return;
        }

        BlockState state = scanner.getBlockState();
        Direction facing = state.hasProperty(PathScannerBlock.FACING) ? state.getValue(PathScannerBlock.FACING) : Direction.NORTH;
        float pulse = PathfinderBlockVisuals.pulse(gameTime, partialTick, 0.18f, (scanner.getBlockPos().asLong() & 31L) * 0.21f);
        int lensAlpha = PathfinderBlockVisuals.alpha(scanning ? 130.0f + pulse * 60.0f : hasCoverage ? 74.0f : 30.0f);
        int unknownAlpha = PathfinderBlockVisuals.alpha(hasUnknown ? 78.0f + pulse * 72.0f : 0.0f);

        poseStack.pushPose();
        PathfinderBlockVisuals.orientNorthModel(poseStack, facing);
        VertexConsumer glow = PathfinderBlockVisuals.glowBuffer(buffer);
        PoseStack.Pose pose = poseStack.last();

        PathfinderBlockVisuals.frontQuad(glow, pose, 0.385f, 0.385f, 0.615f, 0.505f, FRONT_Z,
            PathfinderBlockVisuals.CYAN_R, PathfinderBlockVisuals.CYAN_G, PathfinderBlockVisuals.CYAN_B, lensAlpha);
        PathfinderBlockVisuals.topQuad(glow, pose, 0.886f, 0.72f, 0.39f, 0.84f, 0.61f,
            PathfinderBlockVisuals.CYAN_R, PathfinderBlockVisuals.CYAN_G, PathfinderBlockVisuals.CYAN_B,
            PathfinderBlockVisuals.alpha(hasCoverage ? 72.0f + pulse * 48.0f : 22.0f));
        PathfinderBlockVisuals.topQuad(glow, pose, 0.886f, 0.16f, 0.39f, 0.28f, 0.61f,
            PathfinderBlockVisuals.AMBER_R, PathfinderBlockVisuals.AMBER_G, PathfinderBlockVisuals.AMBER_B, unknownAlpha);

        if (scanning) {
            renderLensSweep(glow, poseStack, gameTime, partialTick, lensAlpha);
            renderScanFan(glow, pose, gameTime, partialTick, pulse);
        }
        poseStack.popPose();
    }

    private static void renderLensSweep(VertexConsumer glow, PoseStack poseStack, long gameTime, float partialTick, int alpha) {
        float start = ((gameTime + partialTick) % 48.0f) / 48.0f * ((float)Math.PI * 2.0f);
        PathfinderBlockVisuals.frontArc(glow, poseStack, 0.5f, 0.445f, FRONT_Z - 0.002f,
            0.18f, 0.215f, start, 1.35f, 8,
            PathfinderBlockVisuals.CYAN_R, PathfinderBlockVisuals.CYAN_G, PathfinderBlockVisuals.CYAN_B,
            PathfinderBlockVisuals.alpha(alpha * 0.7f));
    }

    private static void renderScanFan(VertexConsumer glow, PoseStack.Pose pose, long gameTime, float partialTick, float pulse) {
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        int fanAlpha = PathfinderBlockVisuals.alpha(30.0f + pulse * 26.0f);
        HologramRenderHelper.emitQuad(glow, matrix, normal,
            0.46f, 0.455f, -0.05f,
            0.54f, 0.455f, -0.05f,
            0.86f, 0.455f, -0.78f,
            0.14f, 0.455f, -0.78f,
            PathfinderBlockVisuals.CYAN_R, PathfinderBlockVisuals.CYAN_G, PathfinderBlockVisuals.CYAN_B,
            fanAlpha, HologramRenderHelper.FULL_BRIGHT, 0.0f, 1.0f, 0.0f);

        float travel = ((gameTime + partialTick) % 30.0f) / 30.0f;
        float beadZ = -0.11f - travel * 0.56f;
        HologramRenderHelper.emitCube(glow, matrix, normal, 0.5f, 0.455f, beadZ, 0.018f,
            PathfinderBlockVisuals.CYAN_R, PathfinderBlockVisuals.CYAN_G, PathfinderBlockVisuals.CYAN_B,
            PathfinderBlockVisuals.alpha(92.0f - travel * 40.0f), HologramRenderHelper.FULL_BRIGHT);
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance((int)VIEW_DISTANCE);
    }
}
