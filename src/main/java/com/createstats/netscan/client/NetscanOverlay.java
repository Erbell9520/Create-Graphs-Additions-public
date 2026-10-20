package com.createstats.netscan.client;

import java.util.Map;

import com.createstats.netscan.NetscanHost;
import com.createstats.netscan.client.view.MapView;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.statsmk3.ui.Tokens;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import org.joml.Matrix4f;

// outlines through walls, the press you can't find is the one you can't see
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class NetscanOverlay {
    private NetscanOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        final BlockPos scanner = activeScanner(mc);
        if (scanner == null || !(mc.level.getBlockEntity(scanner) instanceof NetscanHost host)) {
            return;
        }
        final ScanGraph graph = host.scanEngine().graph();
        if (graph.isEmpty()) {
            return;
        }

        final Vec3 camera = event.getCamera().getPosition();
        final double range = overlayRange();
        final double rangeSq = range * range;
        final int budget = overlayBudget();

        float heaviest = 0.0f;
        for (final ScanNode node : graph.nodes().values()) {
            heaviest = Math.max(heaviest, node.drawnStress());
        }

        final PoseStack pose = event.getPoseStack();
        final VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        int drawn = 0;
        for (final Map.Entry<BlockPos, ScanNode> entry : graph.nodes().entrySet()) {
            if (drawn >= budget) {
                break;
            }
            final BlockPos pos = entry.getKey();
            if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) {
                continue;
            }
            drawn++;
            final ScanNode node = entry.getValue();
            final float weight = heaviest <= 0.0f ? 0.0f : node.drawnStress() / heaviest;
            final float alpha = node.isOverStressed() ? 0.95f : 0.28f + 0.62f * weight;

            pose.pushPose();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

            box(lines, pose.last().pose(), MapView.colorFor(node), alpha, -0.01f, 1.01f);
            pose.popPose();
        }

        for (final ScanEdge edge : graph.edges()) {
            final BlockPos from = edge.from();
            final BlockPos to = edge.to();
            if (camera.distanceToSqr(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5) > rangeSq) {
                continue;
            }
            pose.pushPose();
            pose.translate(-camera.x, -camera.y, -camera.z);
            segment(lines, pose.last().pose(),
                    from.getX() + 0.5f, from.getY() + 0.5f, from.getZ() + 0.5f,
                    to.getX() + 0.5f, to.getY() + 0.5f, to.getZ() + 0.5f,
                    edge.isRatioChange() ? Tokens.ACCENT : 0xFF4EA8FF,
                    edge.isRatioChange() ? 0.9f : 0.35f);
            pose.popPose();
        }

        pose.pushPose();
        pose.translate(scanner.getX() - camera.x, scanner.getY() - camera.y, scanner.getZ() - camera.z);
        box(lines, pose.last().pose(), Tokens.ACCENT, 1.0f, -0.04f, 1.04f);
        pose.popPose();

        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static BlockPos activeScanner(Minecraft mc) {
        final BlockPos watching = NetscanClient.watching();
        if (watching != null) {
            return watching;
        }
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockEntity(hit.getBlockPos()) instanceof NetscanHost) {
            return hit.getBlockPos();
        }
        return null;
    }

    private static double overlayRange() {
        try {
            return com.createstats.ModConfiguration.COMMON.scannerOverlayRange.get();
        } catch (RuntimeException ignored) {
            return 48.0;
        }
    }

    private static int overlayBudget() {
        try {
            return com.createstats.ModConfiguration.COMMON.scannerOverlayMaxElements.get();
        } catch (RuntimeException ignored) {
            return 256;
        }
    }

    private static void box(VertexConsumer lines, Matrix4f mat, int color, float alpha,
                            float lo, float hi) {
        edge(lines, mat, color, alpha, lo, lo, lo, hi, lo, lo);
        edge(lines, mat, color, alpha, hi, lo, lo, hi, lo, hi);
        edge(lines, mat, color, alpha, hi, lo, hi, lo, lo, hi);
        edge(lines, mat, color, alpha, lo, lo, hi, lo, lo, lo);

        edge(lines, mat, color, alpha, lo, hi, lo, hi, hi, lo);
        edge(lines, mat, color, alpha, hi, hi, lo, hi, hi, hi);
        edge(lines, mat, color, alpha, hi, hi, hi, lo, hi, hi);
        edge(lines, mat, color, alpha, lo, hi, hi, lo, hi, lo);

        edge(lines, mat, color, alpha, lo, lo, lo, lo, hi, lo);
        edge(lines, mat, color, alpha, hi, lo, lo, hi, hi, lo);
        edge(lines, mat, color, alpha, hi, lo, hi, hi, hi, hi);
        edge(lines, mat, color, alpha, lo, lo, hi, lo, hi, hi);
    }

    private static void segment(VertexConsumer lines, Matrix4f mat, float x0, float y0, float z0,
                                float x1, float y1, float z1, int color, float alpha) {
        edge(lines, mat, color, alpha, x0, y0, z0, x1, y1, z1);
    }

    private static void edge(VertexConsumer lines, Matrix4f mat, int color, float alpha,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        float nx = x1 - x0;
        float ny = y1 - y0;
        float nz = z1 - z0;
        final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-5f) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        final float r = ((color >> 16) & 0xFF) / 255.0f;
        final float g = ((color >> 8) & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        lines.addVertex(mat, x0, y0, z0).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
        lines.addVertex(mat, x1, y1, z1).setColor(r, g, b, alpha).setNormal(nx, ny, nz);
    }
}
