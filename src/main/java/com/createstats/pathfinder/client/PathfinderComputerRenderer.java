package com.createstats.pathfinder.client;

import com.createstats.client.AnimationClock;
import com.createstats.pathfinder.block.PathfinderComputerBlock;
import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.createstats.pathfinder.model.RouteGuidance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class PathfinderComputerRenderer implements BlockEntityRenderer<PathfinderComputerBE> {
    private static final double VIEW_DISTANCE = 32.0;
    private static final float FRONT_Z = -0.006f;

    public PathfinderComputerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    @SuppressWarnings("null")
    public void render(@Nonnull PathfinderComputerBE computer, float partialTick, @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        if (!PathfinderBlockVisuals.closeEnough(computer, VIEW_DISTANCE)) {
            return;
        }

        long gameTime = PathfinderBlockVisuals.gameTime(computer);
        String status = computer.plannerStatus() == null ? "" : computer.plannerStatus();
        String statusLower = status.toLowerCase(Locale.ROOT);
        RouteGuidance guidance = computer.activeGuidance();
        boolean routeReady = guidance != null && guidance.confidence() > 0.0f && guidance.waypoints().size() >= 2;
        boolean holdingRoute = statusLower.contains("keeping last route") || statusLower.contains("hold");
        boolean hasScanner = computer.hasLinkedScanner();
        boolean scannerStale = computer.isLinkedScannerStale();
        boolean hasFcc = computer.hasLinkedFcc();
        boolean fccRejected = computer.linkedFccStatus() != null
            && computer.linkedFccStatus().toLowerCase(Locale.ROOT).contains("reject");
        boolean thinking = hasScanner && !scannerStale
            && (computer.linkedQueueDepth() > 0 || gameTime - computer.plannerLastComputedTick() <= 36L);
        boolean noRoute = !routeReady && !holdingRoute
            && (statusLower.contains("no route") || statusLower.contains("budget") || statusLower.contains("waiting"));

        BlockState state = computer.getBlockState();
        Direction facing = state.hasProperty(PathfinderComputerBlock.FACING)
            ? state.getValue(PathfinderComputerBlock.FACING) : Direction.NORTH;
        float pulse = PathfinderBlockVisuals.pulse(gameTime, partialTick, 0.16f, (computer.getBlockPos().asLong() & 63L) * 0.13f);

        poseStack.pushPose();
        PathfinderBlockVisuals.orientNorthModel(poseStack, facing);
        VertexConsumer glow = PathfinderBlockVisuals.glowBuffer(buffer);
        PoseStack.Pose pose = poseStack.last();

        renderPorts(computer, glow, pose, pulse, hasScanner, scannerStale, hasFcc, fccRejected, routeReady);
        if (routeReady || holdingRoute) {
            renderAcceptedRoute(glow, pose, holdingRoute, pulse);
        } else if (thinking) {
            renderThinkingDots(glow, pose, gameTime, partialTick);
        } else if (noRoute) {
            renderBrokenRoute(glow, pose, pulse);
        }
        renderTopPuck(glow, poseStack, gameTime, partialTick, hasScanner, scannerStale, routeReady, holdingRoute);
        poseStack.popPose();
    }

    private static void renderPorts(PathfinderComputerBE computer, VertexConsumer glow, PoseStack.Pose pose, float pulse,
                                    boolean hasScanner, boolean scannerStale, boolean hasFcc, boolean fccRejected,
                                    boolean routeReady) {
        if (!hasScanner) {
            PathfinderBlockVisuals.frontQuad(glow, pose, 0.13f, 0.135f, 0.25f, 0.27f, FRONT_Z,
                PathfinderBlockVisuals.AMBER_R, PathfinderBlockVisuals.AMBER_G, PathfinderBlockVisuals.AMBER_B, 52);
        } else if (scannerStale) {
            PathfinderBlockVisuals.frontQuad(glow, pose, 0.13f, 0.135f, 0.25f, 0.27f, FRONT_Z,
                PathfinderBlockVisuals.AMBER_R, PathfinderBlockVisuals.AMBER_G, PathfinderBlockVisuals.AMBER_B,
                PathfinderBlockVisuals.alpha(66.0f + pulse * 74.0f));
        } else {
            PathfinderBlockVisuals.frontQuad(glow, pose, 0.13f, 0.135f, 0.25f, 0.27f, FRONT_Z,
                PathfinderBlockVisuals.CYAN_R, PathfinderBlockVisuals.CYAN_G, PathfinderBlockVisuals.CYAN_B,
                PathfinderBlockVisuals.alpha(82.0f + pulse * 54.0f));
        }

        int outputRed = PathfinderBlockVisuals.AMBER_R;
        int outputGreen = PathfinderBlockVisuals.AMBER_G;
        int outputBlue = PathfinderBlockVisuals.AMBER_B;
        int outputAlpha = hasFcc ? PathfinderBlockVisuals.alpha(68.0f + pulse * 42.0f) : 42;
        if (hasFcc && routeReady && !fccRejected) {
            outputRed = PathfinderBlockVisuals.GREEN_R;
            outputGreen = PathfinderBlockVisuals.GREEN_G;
            outputBlue = PathfinderBlockVisuals.GREEN_B;
            outputAlpha = PathfinderBlockVisuals.alpha(90.0f + pulse * 58.0f);
        } else if (hasFcc && fccRejected) {
            outputRed = PathfinderBlockVisuals.RED_R;
            outputGreen = PathfinderBlockVisuals.RED_G;
            outputBlue = PathfinderBlockVisuals.RED_B;
            outputAlpha = PathfinderBlockVisuals.alpha(74.0f + pulse * 72.0f);
        }
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.75f, 0.135f, 0.87f, 0.27f, FRONT_Z,
            outputRed, outputGreen, outputBlue, outputAlpha);

        if (computer.linkedQueueDepth() > 0 && hasScanner && !scannerStale) {
            float beadX = 0.25f + (computer.linkedQueueDepth() % 5) * 0.03f;
            PathfinderBlockVisuals.frontQuad(glow, pose, beadX, 0.175f, beadX + 0.035f, 0.225f, FRONT_Z - 0.001f,
                PathfinderBlockVisuals.CYAN_R, PathfinderBlockVisuals.CYAN_G, PathfinderBlockVisuals.CYAN_B,
                PathfinderBlockVisuals.alpha(70.0f + pulse * 80.0f));
        }
    }

    private static void renderAcceptedRoute(VertexConsumer glow, PoseStack.Pose pose, boolean holdingRoute, float pulse) {
        int red = holdingRoute ? PathfinderBlockVisuals.AMBER_R : PathfinderBlockVisuals.CYAN_R;
        int green = holdingRoute ? PathfinderBlockVisuals.AMBER_G : PathfinderBlockVisuals.CYAN_G;
        int blue = holdingRoute ? PathfinderBlockVisuals.AMBER_B : PathfinderBlockVisuals.CYAN_B;
        int alpha = PathfinderBlockVisuals.alpha((holdingRoute ? 82.0f : 94.0f) + pulse * 54.0f);
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.27f, 0.34f, 0.38f, 0.38f, FRONT_Z, red, green, blue, alpha);
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.38f, 0.38f, 0.49f, 0.42f, FRONT_Z, red, green, blue, alpha);
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.49f, 0.42f, 0.61f, 0.46f, FRONT_Z, red, green, blue, alpha);
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.61f, 0.46f, 0.73f, 0.50f, FRONT_Z, red, green, blue, alpha);
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.70f, 0.48f, 0.77f, 0.55f, FRONT_Z - 0.001f,
            holdingRoute ? PathfinderBlockVisuals.AMBER_R : PathfinderBlockVisuals.GREEN_R,
            holdingRoute ? PathfinderBlockVisuals.AMBER_G : PathfinderBlockVisuals.GREEN_G,
            holdingRoute ? PathfinderBlockVisuals.AMBER_B : PathfinderBlockVisuals.GREEN_B,
            PathfinderBlockVisuals.alpha(alpha + 18.0f));
    }

    private static void renderThinkingDots(VertexConsumer glow, PoseStack.Pose pose, long gameTime, float partialTick) {
        for (int i = 0; i < 3; i++) {
            float dotPulse = PathfinderBlockVisuals.pulse(gameTime, partialTick, 0.34f, i * 2.1f);
            float x = 0.38f + i * 0.09f;
            PathfinderBlockVisuals.frontQuad(glow, pose, x, 0.43f, x + 0.045f, 0.485f, FRONT_Z,
                PathfinderBlockVisuals.CYAN_R, PathfinderBlockVisuals.CYAN_G, PathfinderBlockVisuals.CYAN_B,
                PathfinderBlockVisuals.alpha(44.0f + dotPulse * 96.0f));
        }
    }

    private static void renderBrokenRoute(VertexConsumer glow, PoseStack.Pose pose, float pulse) {
        int alpha = PathfinderBlockVisuals.alpha(62.0f + pulse * 70.0f);
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.30f, 0.36f, 0.39f, 0.40f, FRONT_Z,
            PathfinderBlockVisuals.AMBER_R, PathfinderBlockVisuals.AMBER_G, PathfinderBlockVisuals.AMBER_B, alpha);
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.48f, 0.42f, 0.57f, 0.46f, FRONT_Z,
            PathfinderBlockVisuals.AMBER_R, PathfinderBlockVisuals.AMBER_G, PathfinderBlockVisuals.AMBER_B, alpha);
        PathfinderBlockVisuals.frontQuad(glow, pose, 0.66f, 0.48f, 0.75f, 0.52f, FRONT_Z,
            PathfinderBlockVisuals.AMBER_R, PathfinderBlockVisuals.AMBER_G, PathfinderBlockVisuals.AMBER_B, alpha);
    }

    private static void renderTopPuck(VertexConsumer glow, PoseStack poseStack, long gameTime, float partialTick,
                                      boolean hasScanner, boolean scannerStale, boolean routeReady, boolean holdingRoute) {
        if (!hasScanner && !routeReady && !holdingRoute) {
            return;
        }
        float start = (float) (AnimationClock.time(gameTime, partialTick) % 72.0) / 72.0f * ((float)Math.PI * 2.0f);
        int red = scannerStale || holdingRoute ? PathfinderBlockVisuals.AMBER_R
            : routeReady ? PathfinderBlockVisuals.GREEN_R : PathfinderBlockVisuals.CYAN_R;
        int green = scannerStale || holdingRoute ? PathfinderBlockVisuals.AMBER_G
            : routeReady ? PathfinderBlockVisuals.GREEN_G : PathfinderBlockVisuals.CYAN_G;
        int blue = scannerStale || holdingRoute ? PathfinderBlockVisuals.AMBER_B
            : routeReady ? PathfinderBlockVisuals.GREEN_B : PathfinderBlockVisuals.CYAN_B;
        int alpha = PathfinderBlockVisuals.alpha(routeReady ? 112.0f : 76.0f);
        PathfinderBlockVisuals.topRing(glow, poseStack, 0.5f, 1.004f, 0.5f, 0.13f, 0.18f,
            start, routeReady || holdingRoute ? 4.7f : 2.2f, 16, red, green, blue, alpha);
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance((int)VIEW_DISTANCE);
    }
}
