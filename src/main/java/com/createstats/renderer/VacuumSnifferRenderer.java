package com.createstats.renderer;

import com.createstats.blockentity.VacuumSnifferBE;
import com.createstats.renderer.VacuumMesh.Tile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class VacuumSnifferRenderer implements BlockEntityRenderer<VacuumSnifferBE> {
    private static final double CX = 8.0;
    private static final double CZ = 8.5;
    private static final double PY = 10.0;

    public VacuumSnifferRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void mount(PoseStack ps, Direction facing) {
        ps.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case DOWN -> ps.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> ps.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> {
                ps.mulPose(Axis.YP.rotationDegrees(180));
                ps.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case EAST -> {
                ps.mulPose(Axis.YP.rotationDegrees(-90));
                ps.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case WEST -> {
                ps.mulPose(Axis.YP.rotationDegrees(90));
                ps.mulPose(Axis.XP.rotationDegrees(-90));
            }
            default -> {
            }
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    @Override
    public void render(VacuumSnifferBE be, float partial, PoseStack ps, MultiBufferSource buffers, int light,
                       int overlay) {
        final VacuumSnifferBE.State state = be.state();
        ps.pushPose();
        mount(ps, be.facing());
        ps.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(VacuumCollectorRenderer.PARTS));

        final float yaw = be.yaw + be.yawVel * partial;
        final float pitch = Mth.clamp(be.pitch + be.pitchVel * partial, -80.0f, 85.0f);

        ps.pushPose();
        ps.translate(CX, 0, CZ);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(-CX, 0, -CZ);

        VacuumMesh.prism(vc, ps.last(), 'y', CX, CZ, 4.25, 6.75, 7.5, 16, Tile.IRON_DARK, Tile.IRON_DARK, false, true,
                0, 0, VacuumMesh.WHITE, light, overlay);
        for (double x0 : new double[] {3.25, 11.25}) {
            VacuumMesh.box(vc, ps.last(), x0, 7.5, CZ - 1.25, x0 + 1.5, PY + 1.25, CZ + 1.25, Tile.YELLOW, light, overlay);
            VacuumMesh.box(vc, ps.last(), x0 - 0.01, 7.9, CZ - 1.26, x0 + 1.51, 8.6, CZ + 1.26, Tile.HAZARD, light,
                    overlay);
        }
        for (double x0 : new double[] {3.0, 12.75}) {
            VacuumMesh.prism(vc, ps.last(), 'x', CZ, PY, 0.9, x0, x0 + 0.25, 8, Tile.BRASS_PLATE, Tile.BRASS_PLATE,
                    true, true, 0, 0, VacuumMesh.WHITE, light, overlay);
        }

        ps.pushPose();
        ps.translate(0, PY, CZ);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(0, -PY, -CZ);
        VacuumMesh.box(vc, ps.last(), 4.75, PY - 1.75, CZ - 0.5, 11.25, PY + 1.75, CZ + 3.5, Tile.YELLOW, light, overlay);
        VacuumMesh.box(vc, ps.last(), 5.25, PY + 1.75, CZ, 10.75, PY + 2.0, CZ + 3.0, Tile.YELLOW_DARK, light, overlay);

        for (int i = 0; i < 3; i++) {
            final double y = PY - 1.0 + i * 0.9;
            VacuumMesh.box(vc, ps.last(), 6.0, y, CZ + 3.5, 10.0, y + 0.4, CZ + 3.55, Tile.MOUTH, light, overlay);
        }
        VacuumMesh.prism(vc, ps.last(), 'z', CX, PY, 1.5, CZ - 4.0, CZ - 0.5, 16, Tile.RUBBER, null, false, false,
                0, 0, VacuumMesh.WHITE, light, overlay);
        VacuumMesh.prism(vc, ps.last(), 'z', CX, PY, 1.9, CZ - 4.6, CZ - 4.0, 16, Tile.STEEL_DARK, Tile.STEEL_DARK,
                false, true, 0, 1.5, VacuumMesh.WHITE, light, overlay);
        VacuumMesh.prism(vc, ps.last(), 'z', CX, PY, 2.6, CZ - 5.4, CZ - 4.6, 16, Tile.STEEL, Tile.STEEL, false, true,
                0, 1.9, VacuumMesh.WHITE, light, overlay);
        VacuumMesh.prism(vc, ps.last(), 'z', CX, PY, 3.4, CZ - 6.4, CZ - 5.4, 16, Tile.STEEL, Tile.STEEL, true, true,
                3.0, 2.6, VacuumMesh.WHITE, light, overlay);
        final boolean ring = state == VacuumSnifferBE.State.PULL;
        VacuumMesh.prism(vc, ps.last(), 'z', CX, PY, 3.0, CZ - 6.41, CZ - 6.4, 16, null,
                ring ? Tile.RING_ON : Tile.RING_OFF, true, false, 2.5, 0, VacuumMesh.WHITE,
                ring ? LightTexture.FULL_BRIGHT : light, overlay);
        VacuumMesh.disc(vc, ps.last(), 'z', CX, PY, 2.5, CZ - 6.3, -1, 16, Tile.MOUTH, VacuumMesh.WHITE, light, overlay);
        final Tile lamp = switch (state) {
            case PULL -> Tile.LAMP_GREEN;
            case IDLE, FULL, DISABLED -> Tile.LAMP_AMBER;
            default -> Tile.LAMP_OFF;
        };
        VacuumMesh.box(vc, ps.last(), CX - 1.5, PY + 2.0, CZ + 0.0, CX + 1.5, PY + 2.25, CZ + 3.0, Tile.IRON_DARK, light,
                overlay);
        VacuumMesh.box(vc, ps.last(), CX - 1.0, PY + 2.25, CZ + 0.5, CX + 1.0, PY + 2.5, CZ + 2.5, lamp, VacuumMesh.WHITE,
                lamp == Tile.LAMP_OFF ? light : LightTexture.FULL_BRIGHT, overlay);
        ps.popPose();

        final double pr = Math.toRadians(pitch);

        final double backY = PY + (-0.5) * Math.cos(pr) - 3.5 * Math.sin(pr);
        final double backZ = CZ + (-0.5) * Math.sin(pr) + 3.5 * Math.cos(pr);
        final double[][] hose = {{CX, backY, backZ}, {CX, Math.min(backY, 7.5) + 0.2, Math.max(backZ, CZ + 3.4) + 1.3},
                {CX, 7.5, CZ + 3.4}};
        VacuumMesh.tube(vc, ps.last(), hose, 0.75, Tile.RUBBER, light, overlay);
        ps.popPose();

        int shown = 0;
        for (int i = 0; i < 3; i++) {
            final ItemStack s = be.slots().getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            final double ang = Math.toRadians(shown * 120 + be.drum);
            ps.pushPose();
            ps.translate(CX + 0.55 * Math.cos(ang), 3.5 + 0.55 * Math.sin(ang), CZ - 6.2);
            ps.mulPose(Axis.ZP.rotationDegrees((float) (be.drum * 0.7 + shown * 90)));
            ps.scale(1.6f, 1.6f, 1.6f);
            Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, overlay, ps,
                    buffers, be.getLevel(), i);
            ps.popPose();
            shown++;
        }
        final VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(VacuumCollectorRenderer.PARTS));
        VacuumMesh.disc(glass, ps.last(), 'z', CX, 3.5, 1.25, CZ - 6.5, -1, 16, Tile.GLASS, VacuumMesh.WHITE, light,
                overlay);
        ps.popPose();
    }
}
