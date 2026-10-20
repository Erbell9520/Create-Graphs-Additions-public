package com.createstats.renderer;

import com.createstats.blockentity.MobRepellerBE;
import com.createstats.renderer.RepellerMesh.Tile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class MobRepellerRenderer implements BlockEntityRenderer<MobRepellerBE> {
    static final double HORN_Y = 12.0;
    static final double HINGE = 1.6;

    static final double SCOPE_X = 8.0;
    static final double SCOPE_Y = 7.95;

    static final double KNOB_TUNE_X = 11.25;
    static final double KNOB_RANGE_X = 4.75;
    static final double KNOB_Y = 7.95;

    static final double NEEDLE_LO = 12.55;
    static final double NEEDLE_HI = 3.45;

    public MobRepellerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MobRepellerBE be, float partial, PoseStack ps, MultiBufferSource buffers, int light,
                       int overlay) {
        final MobRepellerBE.State state = be.state();
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yRotation(be.facing())));
        ps.translate(-0.5, 0.0, -0.5);
        ps.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(RepellerMesh.PARTS));
        final PoseStack.Pose root = ps.last();

        RepellerMesh.prism(vc, root, 'y', 8, 8, 2.3, 10.0, 10.75, 12, Tile.BRASS, Tile.BRASS_PLATE, false, true,
                0, 1.3, light, overlay);
        RepellerMesh.prism(vc, root, 'y', 8, 8, 1.3, 10.75, 12.9, 12, Tile.IRON_MID, Tile.IRON_MID, false, true,
                0, 1.0, light, overlay);
        RepellerMesh.prism(vc, root, 'y', 8, 8, 1.0, 12.9, 13.5, 12, Tile.BRASS, Tile.TWEETER, false, true,
                0, 0, light, overlay);

        final float tilt = be.hornTilt;
        final double pinZ = 8.0 - HINGE;
        for (int k = 0; k < 4; k++) {
            ps.pushPose();
            ps.translate(8, 0, 8);
            ps.mulPose(Axis.YP.rotationDegrees(45.0f + 90.0f * k));
            ps.translate(-8, 0, -8);

            RepellerMesh.box(vc, ps.last(), 7.0, HORN_Y - 0.2, pinZ, 9.0, HORN_Y + 0.2, pinZ + 0.4, Tile.BRASS, light,
                    overlay);
            ps.pushPose();
            ps.translate(8, HORN_Y, pinZ + 0.2);
            ps.mulPose(Axis.XP.rotationDegrees(tilt));
            ps.translate(-8, -HORN_Y, -(pinZ + 0.2));
            RepellerMesh.box(vc, ps.last(), 8 - 0.7, HORN_Y - 0.7, pinZ - 0.2, 8 + 0.7, HORN_Y + 0.7, pinZ + 0.6,
                    Tile.IRON_DARK, light, overlay);
            RepellerMesh.horn(vc, ps.last(), 8, HORN_Y, pinZ - 0.2, pinZ - 3.3, 1.1, 1.1, 3.3, 2.76, Tile.GREEN,
                    Tile.HORN_MOUTH, Tile.BRASS, light, overlay);
            ps.popPose();
            ps.popPose();
        }

        final boolean warm = be.energyStored() > 0;
        final boolean lit = state.transmitting() || (state == MobRepellerBE.State.NO_STATION && warm);
        final int glow = LightTexture.FULL_BRIGHT;
        RepellerMesh.prism(vc, root, 'z', SCOPE_X, SCOPE_Y, 1.5, 2.95, 3.25, 16, Tile.BRASS, Tile.BRASS_PLATE, true,
                false, 1.2, 0, light, overlay);
        RepellerMesh.prism(vc, root, 'z', SCOPE_X, SCOPE_Y, 1.2, 2.95, 3.12, 16, Tile.IRON_DARK, null, false, false,
                0, 0, light, overlay);
        RepellerMesh.disc(vc, root, 'z', SCOPE_X, SCOPE_Y, 1.2, 3.12, -1, 16, lit ? Tile.SCOPE_ON : Tile.SCOPE_OFF,
                lit ? glow : light, overlay);
        if (lit) {
            final int tenths = be.stationTenths();
            final double khz = tenths > 0 ? tenths / 10.0 : 20.0;
            final double amp = state.transmitting() ? be.scopeAmp : 0.0;
            trace(vc, root, khz, amp, be.scopePhase + 0.9f * partial, glow, overlay);
        }

        knob(vc, ps, KNOB_TUNE_X, -135.0f + 270.0f * be.needle, light, overlay);
        knob(vc, ps, KNOB_RANGE_X, -135.0f + 270.0f * be.rangeKnob, light, overlay);

        final double nx = NEEDLE_LO + (NEEDLE_HI - NEEDLE_LO) * be.needle;
        RepellerMesh.box(vc, root, nx - 0.12, 3.55, 3.03, nx + 0.12, 5.95, 3.09, Tile.NEEDLE, light, overlay);

        final Tile lamp = lamp(state, warm);
        RepellerMesh.box(vc, root, 10.75, 1.6, 2.8, 12.25, 3.1, 3.0, lamp, lamp == Tile.LAMP_OFF ? light : glow,
                overlay);
        ps.popPose();
    }

    static Tile lamp(MobRepellerBE.State state, boolean warm) {
        return switch (state) {
            case RUNNING -> Tile.LAMP_GREEN;
            case SHORT -> Tile.LAMP_AMBER;
            case NO_STATION -> warm ? Tile.LAMP_AMBER : Tile.LAMP_OFF;
            case OFF, SHED -> Tile.LAMP_RED;

            case DISABLED, PANEL_OFF -> Tile.LAMP_OFF;
        };
    }

    private static void trace(VertexConsumer vc, PoseStack.Pose pose, double khz, double amp, double phaseDeg,
                              int light, int overlay) {
        final double cycles = 2.0 + 4.0 * (khz - 20.0) / 45.0;
        final double r = 1.2;
        final double a = 0.8 * r * Math.max(0.0, Math.min(1.0, amp));
        final int n = 24;
        final double phase = Math.toRadians(phaseDeg);
        double px = 0;
        double py = 0;
        for (int i = 0; i <= n; i++) {
            final double u = -1.0 + 2.0 * i / n;
            final double x = SCOPE_X - u * r * 0.86;
            final double y = SCOPE_Y + Math.sin(u * Math.PI * cycles + phase) * a;
            if (i > 0) {
                RepellerMesh.strip(vc, pose, px, py, x, y, 3.06, 0.12, Tile.TRACE, light, overlay);
            }
            px = x;
            py = y;
        }
    }

    private static void knob(VertexConsumer vc, PoseStack ps, double cx, float angle, int light, int overlay) {
        RepellerMesh.prism(vc, ps.last(), 'z', cx, KNOB_Y, 0.85, 2.75, 3.25, 12, Tile.KNURL, Tile.BRASS_PLATE, true,
                false, 0, 0, light, overlay);
        ps.pushPose();
        ps.translate(cx, KNOB_Y, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(angle));
        ps.translate(-cx, -KNOB_Y, 0);
        RepellerMesh.box(vc, ps.last(), cx - 0.11, KNOB_Y, 2.68, cx + 0.11, KNOB_Y + 0.72, 2.75, Tile.POINTER, light,
                overlay);
        ps.popPose();
    }

    static float yRotation(Direction facing) {
        return switch (facing) {
            case EAST -> -90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            default -> 0.0f;
        };
    }
}
