package com.createstats.renderer;

import com.createstats.blockentity.VacuumCleanerBE;
import com.createstats.renderer.VacuumMesh.Tile;
import com.createstats.vacuum.HoseTether;
import com.createstats.vacuum.VacuumFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VacuumCleanerRenderer implements BlockEntityRenderer<VacuumCleanerBE> {
    private static final double HINGE_Y = 5.0;
    private static final double HINGE_Z = 10.25;

    private static final double[][] PARKED_HOSE = {
            {8.0, 10.0, 0.0}, {6.2, 8.4, -0.6}, {4.2, 7.2, -0.4}, {2.4, 7.6, 1.2}, {1.5, 9.4, 3.8},
            {1.4, 11.2, 6.4}, {1.6, 12.2, 8.5}};

    public VacuumCleanerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(VacuumCleanerBE be) {
        final BlockPos p = be.getBlockPos();
        final AABB block = new AABB(p);
        return be.holder() == null ? block.inflate(1.0) : block.inflate(HoseTether.LENGTH + 2.0);
    }

    @Override
    public boolean shouldRenderOffScreen(VacuumCleanerBE be) {
        return be.holder() != null;
    }

    @Override
    public void render(VacuumCleanerBE be, float partial, PoseStack ps, MultiBufferSource buffers, int light,
                       int overlay) {
        if (be.holder() != null) {
            drawHeldHose(be, partial, ps, buffers, overlay);
        }
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(VacuumFrame.yRotation(be.facing())));
        ps.translate(-0.5, 0.0, -0.5);
        ps.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(VacuumCollectorRenderer.PARTS));

        final float open = Mth.lerp(partial, be.flapPrev, be.flap) * 70.0f;
        ps.pushPose();
        ps.translate(0, HINGE_Y, HINGE_Z);
        ps.mulPose(Axis.XP.rotationDegrees(-open));
        ps.translate(0, -HINGE_Y, -HINGE_Z);
        VacuumMesh.box(vc, ps.last(), 6.1, HINGE_Y - 0.4, 6.1, 9.9, HINGE_Y, HINGE_Z, Tile.STEEL_DARK, light, overlay);
        VacuumMesh.box(vc, ps.last(), 11.75, HINGE_Y - 0.3, HINGE_Z - 0.3, 12.25, HINGE_Y + 0.3, HINGE_Z + 4.75,
                Tile.IRON_MID, light, overlay);
        VacuumMesh.prism(vc, ps.last(), 'x', HINGE_Z + 4.75, HINGE_Y, 1.25, 11.5, 12.9, 16, Tile.IRON, Tile.IRON,
                true, true, 0, 0, VacuumMesh.WHITE, light, overlay);
        VacuumMesh.prism(vc, ps.last(), 'x', HINGE_Z + 4.75, HINGE_Y, 1.3, 12.1, 12.35, 16, Tile.CLAMP, null,
                false, false, 0, 0, VacuumMesh.WHITE, light, overlay);
        ps.popPose();

        final Tile lamp = switch (be.state()) {
            case SUCK, DUMP -> Tile.LAMP_GREEN;
            case IDLE, FULL, DISABLED -> Tile.LAMP_AMBER;
            default -> Tile.LAMP_OFF;
        };
        VacuumMesh.box(vc, ps.last(), 7.0, 4.4, 5.5, 9.0, 5.4, 5.75, lamp, VacuumMesh.WHITE,
                lamp == Tile.LAMP_OFF ? light : LightTexture.FULL_BRIGHT, overlay);
        final boolean ledOn = be.state() != VacuumCleanerBE.State.OFF;
        VacuumMesh.box(vc, ps.last(), 3.25, 2.4, 3.5, 4.25, 2.65, 4.5, be.belowFull() ? Tile.LAMP_RED : Tile.LAMP_GREEN,
                VacuumMesh.WHITE, ledOn ? LightTexture.FULL_BRIGHT : light, overlay);

        if (be.holder() == null) {
            VacuumMesh.box(vc, ps.last(), 0.6, 1.0, 6.0, 2.6, 2.0, 11.0, Tile.YELLOW_DARK, light, overlay);
            VacuumMesh.box(vc, ps.last(), 0.7, 1.0, 6.2, 2.5, 1.1, 10.8, Tile.MOUTH, light, overlay);
            VacuumMesh.prism(vc, ps.last(), 'y', 1.6, 8.5, 0.55, 2.0, 11.0, 8, Tile.STEEL, Tile.STEEL, false, true, 0, 0,
                    VacuumMesh.WHITE, light, overlay);
            VacuumMesh.prism(vc, ps.last(), 'y', 1.6, 8.5, 0.75, 11.0, 12.2, 8, Tile.RUBBER, Tile.RUBBER, true, true,
                    0, 0, VacuumMesh.WHITE, light, overlay);
            VacuumMesh.box(vc, ps.last(), 2.1, 7.4, 7.9, 3.0, 8.6, 9.1, Tile.BRASS_PLATE, light, overlay);
            VacuumMesh.tube(vc, ps.last(), smooth(PARKED_HOSE, 5), 0.9, Tile.RUBBER, light, overlay);
        }

        final var inv = be.receiver();
        int shown = 0;
        for (int i = 0; i < inv.getSlots() && shown < 5; i++) {
            final ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            ps.pushPose();
            ps.translate(13.45, 8.9 + shown * 0.8, 8.5 + ((shown % 2) - 0.5) * 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(90));
            ps.scale(2.2f, 2.2f, 2.2f);
            Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, overlay, ps,
                    buffers, be.getLevel(), i);
            ps.popPose();
            shown++;
        }
        final VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(VacuumCollectorRenderer.PARTS));
        VacuumMesh.box(glass, ps.last(), 13.84, 8.0, 7.5, 13.85, 12.0, 9.5, Tile.SIGHT, light, overlay);
        ps.popPose();
    }

    private static void drawHeldHose(VacuumCleanerBE be, float partial, PoseStack ps, MultiBufferSource buffers,
                                     int overlay) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        final Player player = mc.level.getPlayerByUUID(be.holder());
        if (player == null) {
            return;
        }
        final BlockPos origin = be.getBlockPos();
        final Vec3 port = VacuumFrame.horizontal(BlockPos.ZERO, be.facing(), VacuumCleanerBE.PORT_X,
                VacuumCleanerBE.PORT_Y, VacuumCleanerBE.PORT_Z - 0.25);
        final Vec3 hand = hand(player, partial).subtract(origin.getX(), origin.getY(), origin.getZ());
        final double[] a = {port.x * 16, port.y * 16, port.z * 16};
        final double[] b = {hand.x * 16, hand.y * 16, hand.z * 16};
        final double span = HoseTether.distance(new double[] {port.x, port.y, port.z},
                new double[] {hand.x, hand.y, hand.z});
        final double floor = Math.max(0.0, Math.min(port.y, hand.y) + 0.05);
        final double sag = HoseTether.sag(span, floor) * 16;
        final int segs = 28;
        final double[][] pts = new double[segs + 1][];
        for (int i = 0; i <= segs; i++) {
            pts[i] = HoseTether.point(a, b, sag, i / (double) segs);
        }
        final Vec3 mid = new Vec3(pts[segs / 2][0] / 16 + origin.getX(), pts[segs / 2][1] / 16 + origin.getY(),
                pts[segs / 2][2] / 16 + origin.getZ());
        final int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(mid));
        ps.pushPose();
        ps.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        VacuumMesh.tube(buffers.getBuffer(RenderType.entityCutoutNoCull(VacuumCollectorRenderer.PARTS)), ps.last(),
                pts, 0.9, Tile.RUBBER, light, overlay);
        ps.popPose();
    }

    public static Vec3 hand(Player player, float partial) {
        final Minecraft mc = Minecraft.getInstance();
        final boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
        if (firstPerson) {
            final float yaw = Mth.lerp(partial, player.yRotO, player.getYRot());
            final double rad = Math.toRadians(yaw);
            final Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
            return player.getEyePosition(partial).add(player.getViewVector(partial).scale(0.55))
                    .add(right.scale(0.32)).add(0, -0.38, 0);
        }
        final float body = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot);
        final double rad = Math.toRadians(body);
        final Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        final Vec3 forward = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        return player.getPosition(partial).add(0, 0.8, 0).add(right.scale(0.36)).add(forward.scale(0.3));
    }

    // catmull-rom so the parked hose bends, doesn't kink
    private static double[][] smooth(double[][] ctrl, int per) {
        final java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < ctrl.length - 1; i++) {
            final double[] p0 = ctrl[Math.max(0, i - 1)];
            final double[] p1 = ctrl[i];
            final double[] p2 = ctrl[i + 1];
            final double[] p3 = ctrl[Math.min(ctrl.length - 1, i + 2)];
            for (int s = 0; s < per; s++) {
                final double t = s / (double) per;
                final double t2 = t * t;
                final double t3 = t2 * t;
                final double[] p = new double[3];
                for (int k = 0; k < 3; k++) {
                    p[k] = 0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                            + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
                }
                out.add(p);
            }
        }
        out.add(ctrl[ctrl.length - 1]);
        return out.toArray(new double[0][]);
    }
}
