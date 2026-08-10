package com.createstats.client;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.power.PduStuds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** [1] */
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class PduStudHighlight {

    private PduStudHighlight() {
    }

    /** Amber, matching the panel's own accent, so it reads as this mod rather than as vanilla. */
    private static final float R = 1.0f;
    private static final float G = 0.83f;
    private static final float B = 0.18f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.hitResult == null
                || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        final BlockHitResult hit = (BlockHitResult) mc.hitResult;
        final BlockPos pos = hit.getBlockPos();

        // The studs are Copper Wire terminals, so they are only worth pointing out while holding wire.
        if (!isHolding(mc, com.createstats.item.CopperWireItem.class)) {
            return;
        }
        if (hit.getDirection() != Direction.UP) {
            return;   // the studs are on the bus bar; nothing to aim at from any other side
        }
        if (!(mc.level.getBlockEntity(pos) instanceof PowerDistributionUnitBE panel)) {
            return;
        }
        final Direction facing = panel.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? panel.getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        final Vec3 at = hit.getLocation();
        final int stud = PduStuds.pick(facing, at.x - pos.getX(), at.z - pos.getZ());
        if (stud < 0) {
            lastAimedStud = -1;   // aiming between studs counts as leaving the one you were on
            return;
        }

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        final VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        drawStud(lines, pose.last().pose(), PduStuds.boxOf(facing, stud));
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        pose.popPose();

        // [2]
        if (!pos.equals(lastAimedPanel) || stud != lastAimedStud) {
            lastAimedPanel = pos.immutable();
            lastAimedStud = stud;
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.create_stats.copper_wire.aim_stud", stud + 1), true);
        }
    }

    /** The stud the message was last said for, so it is said once per stud rather than per frame. */
    @org.jetbrains.annotations.Nullable
    private static BlockPos lastAimedPanel;
    private static int lastAimedStud = -1;

    private static boolean isHolding(Minecraft mc, Class<?> itemType) {
        return itemType.isInstance(mc.player.getMainHandItem().getItem())
                || itemType.isInstance(mc.player.getOffhandItem().getItem());
    }

    /** The top face outline plus a short skirt, so the box reads as a solid terminal from any angle. */
    private static void drawStud(VertexConsumer lines, Matrix4f mat, double[] box) {
        final float y = (float) PduStuds.topY();
        final float low = y - 2.0f / 16.0f;
        final float x0 = (float) box[0];
        final float z0 = (float) box[1];
        final float x1 = (float) box[2];
        final float z1 = (float) box[3];
        edge(lines, mat, x0, y, z0, x1, y, z0);
        edge(lines, mat, x1, y, z0, x1, y, z1);
        edge(lines, mat, x1, y, z1, x0, y, z1);
        edge(lines, mat, x0, y, z1, x0, y, z0);
        edge(lines, mat, x0, low, z0, x0, y, z0);
        edge(lines, mat, x1, low, z0, x1, y, z0);
        edge(lines, mat, x1, low, z1, x1, y, z1);
        edge(lines, mat, x0, low, z1, x0, y, z1);
    }

    private static void edge(VertexConsumer lines, Matrix4f mat,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        // RenderType.lines wants a normal per vertex; the direction of the segment is the honest one.
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
        lines.addVertex(mat, x0, y0, z0).setColor(R, G, B, 1.0f).setNormal(nx, ny, nz);
        lines.addVertex(mat, x1, y1, z1).setColor(R, G, B, 1.0f).setNormal(nx, ny, nz);
    }
}
