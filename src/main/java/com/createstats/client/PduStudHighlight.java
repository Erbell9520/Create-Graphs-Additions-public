package com.createstats.client;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.DiagnosticJunctionBE;
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

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class PduStudHighlight {
    private PduStudHighlight() {
    }

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

        if (!isHolding(mc, com.createstats.item.CopperWireItem.class)) {
            return;
        }
        if (mc.level.getBlockEntity(pos) instanceof DiagnosticJunctionBE junction
                && junction.kind() == com.createstats.block.DiagnosticJunctionBlock.Kind.WIRE) {
            drawDiagnosticStud(event, mc, hit, pos, junction);
            return;
        }
        if (mc.level.getBlockState(pos).getBlock() instanceof com.createstats.block.PduCabinetBlock) {
            drawCabinetStud(event, mc, hit, pos);
            return;
        }
        if (hit.getDirection() != Direction.UP) {
            return;
        }
        if (mc.level.getBlockEntity(pos) instanceof com.createstats.blockentity.BatteryControllerBE controller) {
            drawControllerPort(event, mc, hit, pos, controller);
            return;
        }
        if (!(mc.level.getBlockEntity(pos) instanceof PowerDistributionUnitBE panel)) {
            return;
        }
        final Direction facing = panel.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? panel.getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        final Vec3 at = hit.getLocation();
        final int stud = PduStuds.pick(facing, at.x - pos.getX(), at.z - pos.getZ());
        if (stud < 0) {
            lastAimedStud = -1;
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

        if (!pos.equals(lastAimedPanel) || stud != lastAimedStud) {
            lastAimedPanel = pos.immutable();
            lastAimedStud = stud;
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.create_stats.copper_wire.aim_stud", stud + 1), true);
        }
    }

    private static void drawCabinetStud(RenderLevelStageEvent event, Minecraft mc, BlockHitResult hit, BlockPos clicked) {
        final var state = mc.level.getBlockState(clicked);
        final var panel = com.createstats.block.PduCabinetBlock.controller(mc.level, clicked);
        if (panel == null) return;
        final int stud = com.createstats.block.PduCabinetBlock.pickedStud(state, clicked, hit.getLocation(), hit.getDirection());
        if (stud < 0) { lastAimedStud = -1; return; }
        final BlockPos pos = panel.getBlockPos();
        final int turn = com.createstats.block.PduCabinetBlock.turn(state.getValue(HorizontalDirectionalBlock.FACING));
        final double[] a = com.createstats.pducabinet.CabinetGeometry.stud(stud, turn);
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        final VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        drawDiagnosticBox(lines, pose.last().pose(), new double[]{a[0]-.065,a[1]-.065,a[2]-.065,a[0]+.065,a[1]+.065,a[2]+.065});
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        pose.popPose();
        if (!pos.equals(lastAimedPanel) || stud != lastAimedStud) {
            lastAimedPanel = pos.immutable(); lastAimedStud = stud;
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.create_stats.copper_wire.aim_stud", stud + 1), true);
        }
    }

    private static void drawDiagnosticStud(RenderLevelStageEvent event, Minecraft mc, BlockHitResult hit,
                                           BlockPos pos, DiagnosticJunctionBE junction) {
        final Direction facing = junction.getBlockState().hasProperty(com.createstats.block.DiagnosticJunctionBlock.FACING)
                ? junction.getBlockState().getValue(com.createstats.block.DiagnosticJunctionBlock.FACING)
                : Direction.NORTH;
        final Vec3 at = hit.getLocation();
        final int stud = com.createstats.power.DiagnosticJunctionStuds.pick(facing,
                at.x - pos.getX(), at.y - pos.getY(), at.z - pos.getZ());
        if (stud < 0) {
            lastAimedStud = -1;
            return;
        }

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        final VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        drawDiagnosticBox(lines, pose.last().pose(), com.createstats.power.DiagnosticJunctionStuds.boxOf(facing, stud));
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        pose.popPose();

        if (!pos.equals(lastAimedPanel) || stud != lastAimedStud) {
            lastAimedPanel = pos.immutable();
            lastAimedStud = stud;
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.create_stats.copper_wire.aim_stud", stud + 1), true);
        }
    }

    private static void drawControllerPort(RenderLevelStageEvent event, Minecraft mc, BlockHitResult hit,
                                           BlockPos pos,
                                           com.createstats.blockentity.BatteryControllerBE controller) {
        final Direction facing = controller.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? controller.getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        final Vec3 at = hit.getLocation();
        final int port = com.createstats.power.BatteryControllerPorts.pick(
                facing, at.x - pos.getX(), at.z - pos.getZ());
        if (port < 0) {
            lastAimedStud = -1;
            return;
        }

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        final VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        drawPortBox(lines, pose.last().pose(),
                com.createstats.power.BatteryControllerPorts.boxOf(facing, port),
                (float) com.createstats.power.BatteryControllerPorts.topY());
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
        pose.popPose();

        if (!pos.equals(lastAimedPanel) || port != lastAimedStud) {
            lastAimedPanel = pos.immutable();
            lastAimedStud = port;
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    com.createstats.power.BatteryControllerPorts.describe(port)
                            + ". Click to wire it."), true);
        }
    }

    @org.jetbrains.annotations.Nullable
    private static BlockPos lastAimedPanel;
    private static int lastAimedStud = -1;

    private static boolean isHolding(Minecraft mc, Class<?> itemType) {
        return itemType.isInstance(mc.player.getMainHandItem().getItem())
                || itemType.isInstance(mc.player.getOffhandItem().getItem());
    }

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

    private static void drawPortBox(VertexConsumer lines, Matrix4f mat, double[] box, float top) {
        final float low = top - 2.0f / 16.0f;
        final float x0 = (float) box[0];
        final float z0 = (float) box[1];
        final float x1 = (float) box[2];
        final float z1 = (float) box[3];
        edge(lines, mat, x0, top, z0, x1, top, z0);
        edge(lines, mat, x1, top, z0, x1, top, z1);
        edge(lines, mat, x1, top, z1, x0, top, z1);
        edge(lines, mat, x0, top, z1, x0, top, z0);
        edge(lines, mat, x0, low, z0, x0, top, z0);
        edge(lines, mat, x1, low, z0, x1, top, z0);
        edge(lines, mat, x1, low, z1, x1, top, z1);
        edge(lines, mat, x0, low, z1, x0, top, z1);
    }

    private static void drawDiagnosticBox(VertexConsumer lines, Matrix4f mat, double[] box) {
        final float x0 = (float) box[0];
        final float y0 = (float) box[1];
        final float z0 = (float) box[2];
        final float x1 = (float) box[3];
        final float y1 = (float) box[4];
        final float z1 = (float) box[5];
        edge(lines, mat, x0, y0, z0, x1, y0, z0);
        edge(lines, mat, x1, y0, z0, x1, y0, z1);
        edge(lines, mat, x1, y0, z1, x0, y0, z1);
        edge(lines, mat, x0, y0, z1, x0, y0, z0);
        edge(lines, mat, x0, y1, z0, x1, y1, z0);
        edge(lines, mat, x1, y1, z0, x1, y1, z1);
        edge(lines, mat, x1, y1, z1, x0, y1, z1);
        edge(lines, mat, x0, y1, z1, x0, y1, z0);
        edge(lines, mat, x0, y0, z0, x0, y1, z0);
        edge(lines, mat, x1, y0, z0, x1, y1, z0);
        edge(lines, mat, x1, y0, z1, x1, y1, z1);
        edge(lines, mat, x0, y0, z1, x0, y1, z1);
    }

    private static void edge(VertexConsumer lines, Matrix4f mat,
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
        lines.addVertex(mat, x0, y0, z0).setColor(R, G, B, 1.0f).setNormal(nx, ny, nz);
        lines.addVertex(mat, x1, y1, z1).setColor(R, G, B, 1.0f).setNormal(nx, ny, nz);
    }
}
