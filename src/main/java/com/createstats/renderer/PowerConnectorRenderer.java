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

public class PowerConnectorRenderer implements BlockEntityRenderer<PowerConnectorBE> {
    private static final double TERMINAL_CENTRE = 11.0 / 16.0;

    private static final int SIDES = 8;

    private static final double LAY_PERIOD = 0.55;

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
            if (!drawsSpan(be.getLevel(), origin, link)) {
                continue;
            }

            final net.minecraft.world.phys.Vec3 to = attachPoint(be.getLevel(), link, origin);
            drawCable(consumer, matrix, be.getLevel(), origin, link,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    packedLight, be.tierOf(link));
        }
    }

    // a stud panel at the far end always draws, it knows which stud
    public static boolean drawsSpan(@org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter level,
                                    BlockPos origin, BlockPos link) {
        if (isStudPanel(level, link)) {
            return false;
        }
        if (link.asLong() > origin.asLong()) {
            return true;
        }
        if (com.createstats.power.ForeignTerminalNode.isBridgeTarget(level, link)) {
            return true;
        }

        if (level == null || !(level.getBlockState(origin).getBlock()
                instanceof com.createstats.block.UniversalPowerCouplerBlock)) {
            return false;
        }

        final net.minecraft.world.level.block.entity.BlockEntity far = level.getBlockEntity(link);
        return !(far instanceof com.createstats.power.FeWireTerminal)
                && !(far instanceof com.createstats.power.FeStudPanel);
    }

    public static boolean isStudPanel(
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (level == null) {
            return false;
        }
        final net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
        return block instanceof com.createstats.block.PowerDistributionUnitBlock
                || block instanceof com.createstats.block.DiagnosticJunctionBlock
                || block instanceof com.createstats.block.BatteryControllerBlock

                || block instanceof com.createstats.block.MainBreakerBlock

                || block instanceof com.createstats.block.ContactorBlock

                || block instanceof com.createstats.block.SparkCrownBlock

                || block instanceof com.createstats.block.StormCoilBlock;
    }

    public static net.minecraft.world.phys.Vec3 attachPoint(
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (level == null) {
            return attachPoint((net.minecraft.world.level.block.state.BlockState) null);
        }
        if (com.createstats.power.ForeignTerminalNode.isBridgeTarget(level, pos)) {
            return com.createstats.power.ForeignTerminalNode.anchorOf(level, pos);
        }
        return attachPoint(level.getBlockState(pos));
    }

    public static net.minecraft.world.phys.Vec3 attachPoint(
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter level,
            BlockPos pos, BlockPos from) {
        if (level != null
                && level.getBlockState(pos).getBlock()
                        instanceof com.createstats.block.UniversalPowerCouplerBlock) {
            return com.createstats.power.CouplerSockets.anchor(level.getBlockState(pos),
                    com.createstats.power.CouplerSockets.endToward(level.getBlockState(pos), pos, from));
        }
        return attachPoint(level, pos);
    }

    public static net.minecraft.world.phys.Vec3 attachPoint(
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.block.state.BlockState state) {
        if (state != null && state.getBlock() instanceof com.createstats.block.FeMotorBlock) {
            return FeMotorRenderer.studAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.CoffeeMachineBlock) {
            return CoffeeMachineRenderer.terminalAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.WaterCoolerBlock) {
            return WaterCoolerRenderer.terminalAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.AirFryerBlock) {
            return AirFryerRenderer.terminalAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.BulkWasherBlock) {
            return BulkWasherRenderer.terminalAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.OvenBlock) {
            return OvenRenderer.terminalAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.ToasterBlock) {
            return ToasterRenderer.terminalAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.BreadSlicerBlock) {
            return BreadSlicerRenderer.terminalAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.SecuritySensorBlock) {
            return SecuritySensorRenderer.terminalAttachPoint(state);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.SparkCrownBlock) {
            final double[] p = com.createstats.chunkloader.core.CrownStuds.bayCentre(
                    com.createstats.chunkloader.core.CrownStuds.turnsFor(
                            state.getValue(com.createstats.block.SparkCrownBlock.FACING).getName()));
            return new net.minecraft.world.phys.Vec3(p[0], p[1], p[2]);
        }
        if (state != null && state.getBlock() instanceof com.createstats.block.UniversalPowerCouplerBlock) {
            return com.createstats.power.CouplerSockets.anchor(
                    state, com.createstats.power.CouplerSockets.End.FOREIGN);
        }
        if (state == null || !(state.getBlock() instanceof com.createstats.block.PowerConnectorBlock)) {
            return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        }
        return connectorTerminal(state.getValue(com.createstats.block.PowerConnectorBlock.FACING),
                state.getValue(com.createstats.block.PowerConnectorBlock.ANCHOR));
    }

    public static net.minecraft.world.phys.Vec3 connectorTerminal(net.minecraft.core.Direction facing,
                                                                  com.createstats.block.ConnectorAnchor placed) {
        final com.createstats.block.ConnectorAnchor anchor = com.createstats.block.ConnectorAnchor.inPlaneOf(
                facing, placed);

        double x = 0.5 + facing.getStepX() * (TERMINAL_CENTRE - 0.5) + anchor.offset(net.minecraft.core.Direction.Axis.X);
        double y = 0.5 + facing.getStepY() * (TERMINAL_CENTRE - 0.5) + anchor.offset(net.minecraft.core.Direction.Axis.Y);
        double z = 0.5 + facing.getStepZ() * (TERMINAL_CENTRE - 0.5) + anchor.offset(net.minecraft.core.Direction.Axis.Z);
        return new net.minecraft.world.phys.Vec3(x, y, z);
    }

    public static VertexConsumer wireBuffer(MultiBufferSource buffers) {
        return Buffers.live(buffers, RenderType.entityCutoutNoCull(WIRE_TEXTURE));
    }

    public static void drawCable(VertexConsumer vc, Matrix4f mat,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 int light, com.createstats.power.WireTier tier) {
        drawCable(vc, mat, x0, y0, z0, x1, y1, z1, light, tier,
                com.createstats.power.WireTension.STANDARD.slack());
    }

    public static void drawCable(VertexConsumer vc, Matrix4f mat,
                                 @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter level,
                                 BlockPos origin, BlockPos link,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 int light, com.createstats.power.WireTier tier) {
        drawCable(vc, mat, x0, y0, z0, x1, y1, z1,
                com.createstats.client.wire.WireTensionView.light(origin, link, light), tier,
                com.createstats.client.wire.WireTensionView.slack(level, origin, link));
    }

    public static void drawCable(VertexConsumer vc, Matrix4f mat,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 int light, com.createstats.power.WireTier tier, double slack) {
        final double dist = Math.sqrt((double) (x1 - x0) * (x1 - x0)
                + (double) (y1 - y0) * (y1 - y0) + (double) (z1 - z0) * (z1 - z0));
        if (dist < 1.0e-4) {
            return;
        }
        final int segs = Mth.clamp((int) Math.round(dist / 0.5), 6, 48);

        drawTube(vc, mat, catenary(x0, y0, z0, x1, y1, z1, segs, slack), light,
                tier.halfThickness() * 1.15, tier.strandA());
    }

    public static void drawTube(VertexConsumer vc, Matrix4f mat, double[][] p, int light,
                                double radius, int[] col) {
        final int segs = p.length - 1;
        if (segs < 1) {
            return;
        }
        final double r = radius;

        final double[] start = tangent(p, 0, segs);
        double[] right = perpendicular(start);
        double[] up = cross(right, start);

        final double[] dirA = new double[SIDES * 3];
        final double[] dirB = new double[SIDES * 3];
        final double[] ringA = new double[SIDES * 3];
        final double[] ringB = new double[SIDES * 3];
        ring(p[0], right, up, r, dirA, ringA);
        double arc = 0.0;

        for (int i = 0; i < segs; i++) {
            final double[] a = p[i];
            final double[] b = p[i + 1];
            final double step = Math.sqrt((b[0] - a[0]) * (b[0] - a[0])
                    + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));

            final double[] next = tangent(p, i + 1, segs);
            right = transport(right, next);
            up = cross(right, next);
            ring(b, right, up, r, dirB, ringB);

            final float v0 = (float) (arc / LAY_PERIOD);
            final float v1 = (float) ((arc + step) / LAY_PERIOD);
            for (int k = 0; k < SIDES; k++) {
                final int k0 = k * 3;
                final int k1 = ((k + 1) % SIDES) * 3;
                final float u0 = (float) k / SIDES;
                final float u1 = (float) (k + 1) / SIDES;

                final double nx = dirA[k0] + dirA[k1] + dirB[k0] + dirB[k1];
                final double ny = dirA[k0 + 1] + dirA[k1 + 1] + dirB[k0 + 1] + dirB[k1 + 1];
                final double nz = dirA[k0 + 2] + dirA[k1 + 2] + dirB[k0 + 2] + dirB[k1 + 2];
                final double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                final float fnx = nl < 1.0e-9 ? 0.0f : (float) (nx / nl);
                final float fny = nl < 1.0e-9 ? 1.0f : (float) (ny / nl);
                final float fnz = nl < 1.0e-9 ? 0.0f : (float) (nz / nl);
                put(vc, mat, ringA, k0, u0, v0, col, light, fnx, fny, fnz);
                put(vc, mat, ringA, k1, u1, v0, col, light, fnx, fny, fnz);
                put(vc, mat, ringB, k1, u1, v1, col, light, fnx, fny, fnz);
                put(vc, mat, ringB, k0, u0, v1, col, light, fnx, fny, fnz);
            }
            System.arraycopy(ringB, 0, ringA, 0, ringA.length);
            System.arraycopy(dirB, 0, dirA, 0, dirA.length);
            arc += step;
        }
    }

    private static double[] tangent(double[][] p, int i, int segs) {
        final int lo = Math.max(0, i - 1);
        final int hi = Math.min(segs, i + 1);
        final double[] a = p[lo];
        final double[] b = p[hi];
        return normalize(b[0] - a[0], b[1] - a[1], b[2] - a[2], 0.0, 1.0, 0.0);
    }

    private static double[] perpendicular(double[] t) {
        final double[] seed = Math.abs(t[1]) > 0.9 ? new double[] {1.0, 0.0, 0.0} : new double[] {0.0, 1.0, 0.0};
        final double[] out = cross(seed, t);
        return normalize(out[0], out[1], out[2], 1.0, 0.0, 0.0);
    }

    private static double[] transport(double[] right, double[] tangent) {
        final double d = right[0] * tangent[0] + right[1] * tangent[1] + right[2] * tangent[2];
        final double x = right[0] - tangent[0] * d;
        final double y = right[1] - tangent[1] * d;
        final double z = right[2] - tangent[2] * d;

        if (x * x + y * y + z * z < 1.0e-12) {
            return perpendicular(tangent);
        }
        return normalize(x, y, z, 1.0, 0.0, 0.0);
    }

    private static void ring(double[] centre, double[] right, double[] up, double r,
                             double[] dirOut, double[] posOut) {
        for (int k = 0; k < SIDES; k++) {
            final double angle = 2.0 * Math.PI * k / SIDES;
            final double c = Math.cos(angle);
            final double s = Math.sin(angle);
            final int o = k * 3;
            dirOut[o] = right[0] * c + up[0] * s;
            dirOut[o + 1] = right[1] * c + up[1] * s;
            dirOut[o + 2] = right[2] * c + up[2] * s;
            posOut[o] = centre[0] + dirOut[o] * r;
            posOut[o + 1] = centre[1] + dirOut[o + 1] * r;
            posOut[o + 2] = centre[2] + dirOut[o + 2] * r;
        }
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double x, double y, double z, double fx, double fy, double fz) {
        final double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0e-9) {
            return new double[] {fx, fy, fz};
        }
        return new double[] {x / l, y / l, z / l};
    }

    private static double[][] catenary(double x0, double y0, double z0,
                                       double x1, double y1, double z1, int segs, double slack) {
        final double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        final double horLength = Math.sqrt(dx * dx + dz * dz);
        final double[][] out = new double[segs + 1][];
        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final double wireLength = dist * slack;
        final double goal = horLength < 1.0e-4 ? 0.0
                : Math.sqrt(Math.max(1.0e-9, wireLength * wireLength - dy * dy)) / horLength;
        if (horLength < 1.0e-4 || goal <= 1.0) {
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

    private static void put(VertexConsumer vc, Matrix4f mat, double[] ring, int offset,
                            float u, float v, int[] col, int light, float nx, float ny, float nz) {
        put(vc, mat, (float) ring[offset], (float) ring[offset + 1], (float) ring[offset + 2],
                u, v, col, light, nx, ny, nz);
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

    public static void drawCap(VertexConsumer vc, Matrix4f mat, double[] centre, double[] outward,
                               double radius, int[] col, int light) {
        final double[] right = perpendicular(outward);
        final double[] up = cross(right, outward);
        final double[] dir = new double[SIDES * 3];
        final double[] pos = new double[SIDES * 3];
        ring(centre, right, up, radius, dir, pos);
        final float nx = (float) outward[0];
        final float ny = (float) outward[1];
        final float nz = (float) outward[2];
        final float cx = (float) centre[0];
        final float cy = (float) centre[1];
        final float cz = (float) centre[2];
        for (int k = 0; k < SIDES; k++) {
            final int k0 = k * 3;
            final int k1 = ((k + 1) % SIDES) * 3;
            final float u0 = (float) k / SIDES;
            final float u1 = (float) (k + 1) / SIDES;

            put(vc, mat, cx, cy, cz, 0.5f, 0.5f, col, light, nx, ny, nz);
            put(vc, mat, (float) pos[k0], (float) pos[k0 + 1], (float) pos[k0 + 2], u0, 1.0f, col, light, nx, ny, nz);
            put(vc, mat, (float) pos[k1], (float) pos[k1 + 1], (float) pos[k1 + 2], u1, 1.0f, col, light, nx, ny, nz);
            put(vc, mat, cx, cy, cz, 0.5f, 0.5f, col, light, nx, ny, nz);
        }
    }

    public static final class WireBounds {
        private static final double SAG_PER_BLOCK = com.createstats.power.WireTension.loosest().sagPerBlock();

        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;
        private double span;

        public WireBounds(BlockPos origin) {
            minX = origin.getX();
            minY = origin.getY();
            minZ = origin.getZ();
            maxX = origin.getX() + 1.0;
            maxY = origin.getY() + 1.0;
            maxZ = origin.getZ() + 1.0;
        }

        public void include(BlockPos end) {
            minX = Math.min(minX, end.getX());
            minY = Math.min(minY, end.getY());
            minZ = Math.min(minZ, end.getZ());
            maxX = Math.max(maxX, end.getX() + 1.0);
            maxY = Math.max(maxY, end.getY() + 1.0);
            maxZ = Math.max(maxZ, end.getZ() + 1.0);
            span = Math.max(span, Math.max(maxX - minX, maxZ - minZ));
        }

        public net.minecraft.world.phys.AABB build() {
            return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ)
                    .expandTowards(0.0, -(span * SAG_PER_BLOCK + 0.5), 0.0);
        }
    }

    public static net.minecraft.world.phys.AABB wireBounds(BlockPos origin, Iterable<BlockPos> ends) {
        final WireBounds bounds = new WireBounds(origin);
        for (BlockPos end : ends) {
            bounds.include(end);
        }
        return bounds.build();
    }

    @Override
    public boolean shouldRenderOffScreen(PowerConnectorBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + PowerConnectorBE.wireRange());
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PowerConnectorBE be) {
        return wireBounds(be.getBlockPos(), be.links());
    }
}
