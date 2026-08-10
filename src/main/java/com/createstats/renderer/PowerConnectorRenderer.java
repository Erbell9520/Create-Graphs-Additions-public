package com.createstats.renderer;

import com.createstats.blockentity.PowerConnectorBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** [1] */
public class PowerConnectorRenderer implements BlockEntityRenderer<PowerConnectorBE> {

    /**
     * Height of the copper terminal's centre above the mounting face, in block units.
     * Matches {@code TERMINAL_CENTRE_H} in {@code gen_power_connector.py}.
     */
    private static final double TERMINAL_CENTRE = 11.0 / 16.0;
    /** Slack as a fraction of the straight-line distance: ~2% longer, so the hang is taut, not droopy. */
    private static final double SLACK = 1.02;
    /** Copper cable sprite (greyscale twist, tinted per grade), bound directly rather than atlased. */
    private static final net.minecraft.resources.ResourceLocation WIRE_TEXTURE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/wire.png");


    public PowerConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PowerConnectorBE be, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.links().isEmpty()) {
            return;
        }
        final BlockPos origin = be.getBlockPos();
        final VertexConsumer consumer = wireBuffer(bufferSource);
        final Matrix4f matrix = poseStack.last().pose();
        final net.minecraft.world.phys.Vec3 from = attachPoint(be.getBlockState());
        for (BlockPos link : be.links()) {
            if (be.getLevel() != null && be.getLevel().getBlockState(link).getBlock()
                    instanceof com.createstats.block.PowerDistributionUnitBlock) {
                continue; // a wire to an output stud: the panel draws that one, from the stud's centre
            }
            if (link.asLong() <= origin.asLong()) {
                continue; // the other end draws this wire
            }
            // [2]
            final net.minecraft.world.phys.Vec3 to = be.getLevel() == null
                    ? attachPoint(null)
                    : attachPoint(be.getLevel().getBlockState(link));
            drawCable(consumer, matrix,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    packedLight, be.tierOf(link));
        }
    }

    /** [3] */
    public static net.minecraft.world.phys.Vec3 attachPoint(
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.block.state.BlockState state) {
        if (state != null && state.getBlock() instanceof com.createstats.block.FeMotorBlock) {
            return FeMotorRenderer.studAttachPoint(state);
        }
        if (state == null || !(state.getBlock() instanceof com.createstats.block.PowerConnectorBlock)) {
            return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        }
        final net.minecraft.core.Direction facing = state.getValue(com.createstats.block.PowerConnectorBlock.FACING);
        final com.createstats.block.ConnectorAnchor anchor = com.createstats.block.ConnectorAnchor.inPlaneOf(
                facing, state.getValue(com.createstats.block.PowerConnectorBlock.ANCHOR));
        // Start at the centre of the mounting face, step out to the terminal, then apply the anchor.
        double x = 0.5 + facing.getStepX() * (TERMINAL_CENTRE - 0.5) + anchor.offset(net.minecraft.core.Direction.Axis.X);
        double y = 0.5 + facing.getStepY() * (TERMINAL_CENTRE - 0.5) + anchor.offset(net.minecraft.core.Direction.Axis.Y);
        double z = 0.5 + facing.getStepZ() * (TERMINAL_CENTRE - 0.5) + anchor.offset(net.minecraft.core.Direction.Axis.Z);
        return new net.minecraft.world.phys.Vec3(x, y, z);
    }

    /** [4] */
    public static VertexConsumer wireBuffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityCutoutNoCull(WIRE_TEXTURE));
    }

    /** [5] */
    public static void drawCable(VertexConsumer vc, Matrix4f mat,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 int light, com.createstats.power.WireTier tier) {
        final double dist = Math.sqrt((double) (x1 - x0) * (x1 - x0)
                + (double) (y1 - y0) * (y1 - y0) + (double) (z1 - z0) * (z1 - z0));
        if (dist < 1.0e-4) {
            return;
        }
        final int segs = Math.max(8, Math.min(64, (int) Math.round(dist / 0.4)));
        final double[][] p = catenary(x0, y0, z0, x1, y1, z1, segs);
        final float r = tier.halfThickness();
        final int[] col = tier.strandA();
        for (int i = 0; i < segs; i++) {
            final double[] a = p[i];
            final double[] b = p[i + 1];
            final double ddx = b[0] - a[0], ddy = b[1] - a[1], ddz = b[2] - a[2];
            final double horiz = Math.sqrt(ddx * ddx + ddz * ddz);
            // Horizontal normal across the run, then a vertical normal completing the frame.
            double hnx, hny, hnz;
            if (horiz < 1.0e-5) {
                hnx = 1.0;
                hny = 0.0;
                hnz = 0.0;
            } else {
                hnx = -ddz / horiz;
                hny = 0.0;
                hnz = ddx / horiz;
            }
            double vnx = (-ddy) * hnz - (-ddz) * hny;
            double vny = (-ddz) * hnx - (-ddx) * hnz;
            double vnz = (-ddx) * hny - (-ddy) * hnx;
            final double vl = Math.sqrt(vnx * vnx + vny * vny + vnz * vnz);
            if (vl > 1.0e-6) {
                vnx /= vl;
                vny /= vl;
                vnz /= vl;
            }
            // The flat ribbon (offset by the horizontal normal), lit by the vertical normal; and the
            // upright ribbon (offset by the vertical normal), lit by the horizontal normal.
            emitBidiQuad(vc, mat, a, b, hnx * r, hny * r, hnz * r, vnx, vny, vnz, col, light);
            emitBidiQuad(vc, mat, a, b, vnx * r, vny * r, vnz * r, hnx, hny, hnz, col, light);
        }
    }

    /** A small-slack catenary sampled into {@code segs} points, ported from the Immersive Engineering
     *  wire maths: taut like real strung cable rather than the deep parabola we had. */
    private static double[][] catenary(double x0, double y0, double z0,
                                       double x1, double y1, double z1, int segs) {
        final double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        final double horLength = Math.sqrt(dx * dx + dz * dz);
        final double[][] out = new double[segs + 1][];
        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final double wireLength = dist * SLACK;
        final double goal = horLength < 1.0e-4 ? 0.0
                : Math.sqrt(Math.max(1.0e-9, wireLength * wireLength - dy * dy)) / horLength;
        if (horLength < 1.0e-4 || goal <= 1.0) {                    // vertical or already taut: straight
            for (int i = 0; i <= segs; i++) {
                final double t = (double) i / segs;
                out[i] = new double[] {x0 + dx * t, y0 + dy * t, z0 + dz * t};
            }
            return out;
        }
        double lower = 0.0;
        double upper = 1.0;
        while (Math.sinh(upper) / upper < goal) {
            lower = upper;
            upper *= 2.0;
        }
        for (int it = 0; it < 24; it++) {
            final double mid = (lower + upper) / 2.0;
            if (Math.sinh(mid) / mid < goal) {
                lower = mid;
            } else {
                upper = mid;
            }
        }
        final double l = (lower + upper) / 2.0;
        final double scale = horLength / (2.0 * l);
        final double offsetX = (horLength - scale * Math.log((wireLength + dy) / (wireLength - dy))) * 0.5;
        final double offsetY = (dy - wireLength * Math.cosh(l) / Math.sinh(l)) * 0.5;
        for (int i = 0; i <= segs; i++) {
            final double t = (double) i / segs;
            final double y = scale * Math.cosh((horLength * t - offsetX) / scale) + offsetY;
            out[i] = new double[] {x0 + dx * t, y0 + y, z0 + dz * t};
        }
        return out;
    }

    /** One segment ribbon, emitted front and back so the cable is lit and visible from either side. */
    private static void emitBidiQuad(VertexConsumer vc, Matrix4f mat, double[] a, double[] b,
                                     double rx, double ry, double rz,
                                     double nx, double ny, double nz, int[] col, int light) {
        final float ax0 = (float) (a[0] + rx), ay0 = (float) (a[1] + ry), az0 = (float) (a[2] + rz);
        final float ax1 = (float) (a[0] - rx), ay1 = (float) (a[1] - ry), az1 = (float) (a[2] - rz);
        final float bx0 = (float) (b[0] + rx), by0 = (float) (b[1] + ry), bz0 = (float) (b[2] + rz);
        final float bx1 = (float) (b[0] - rx), by1 = (float) (b[1] - ry), bz1 = (float) (b[2] - rz);
        final float fnx = (float) nx, fny = (float) ny, fnz = (float) nz;
        put(vc, mat, ax0, ay0, az0, 0.0f, 0.0f, col, light, fnx, fny, fnz);
        put(vc, mat, bx0, by0, bz0, 1.0f, 0.0f, col, light, fnx, fny, fnz);
        put(vc, mat, bx1, by1, bz1, 1.0f, 1.0f, col, light, fnx, fny, fnz);
        put(vc, mat, ax1, ay1, az1, 0.0f, 1.0f, col, light, fnx, fny, fnz);
        put(vc, mat, ax1, ay1, az1, 0.0f, 1.0f, col, light, -fnx, -fny, -fnz);
        put(vc, mat, bx1, by1, bz1, 1.0f, 1.0f, col, light, -fnx, -fny, -fnz);
        put(vc, mat, bx0, by0, bz0, 1.0f, 0.0f, col, light, -fnx, -fny, -fnz);
        put(vc, mat, ax0, ay0, az0, 0.0f, 0.0f, col, light, -fnx, -fny, -fnz);
    }

    private static void put(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                            float u, float v, int[] col, int light, float nx, float ny, float nz) {
        vc.addVertex(mat, x, y, z)
                .setColor(col[0], col[1], col[2], 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PowerConnectorBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(PowerConnectorBE.wireRange() + 1);
    }
}
