package com.createstats.renderer;

import com.createstats.block.CableBlock;
import com.createstats.blockentity.CableBE;
import com.createstats.power.CableLinks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** [1] */
public class CableRenderer implements BlockEntityRenderer<CableBE> {

    private static final ResourceLocation CORD =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/cable_cord.png");

    private static final float PX = 1.0f / 16.0f;
    // [2]

    /** Half width of one conductor in the pigtail, so each strand is half a pixel across. */
    private static final float STRAND = 0.25f * PX;
    /** How far the three strands are spread apart, centre to centre. */
    private static final float STRAND_SPREAD = 0.65f * PX;
    /** Length of the gland: the little collar where the strands gather into the cord. */
    private static final float GLAND = 0.75f * PX;
    /** Half width of the gland, a touch fatter than the cord so it reads as a fitting. */
    private static final float GLAND_HALF = 1.5f * PX;
    /** Below this much room inside a fitting there is no space for a pigtail, so the cord just butts. */
    private static final float STRAND_MIN_ROOM = 2.0f * PX;
    /** Most of the fitting a pigtail may occupy. Beyond this it stops reading as a detail. */
    private static final float STRAND_MAX = 3.0f * PX;

    /**
     * Where a piece finishes along its axis, whether anyone can see the end, and where a pigtail
     * carries on to.
     *
     * @param gland the coordinate the fine strands reach, or NaN when this arm has none
     */
    private record Arm(float end, boolean cap, float gland) {

        Arm(float end, boolean cap) {
            this(end, cap, Float.NaN);
        }

        boolean hasStrands() {
            return !Float.isNaN(gland);
        }
    }

    public CableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CableBE be, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CableBlock cable)) {
            return;
        }
        final float thickness = (float) cable.thicknessPx() * PX;
        final float half = cable.tier().halfWidthPx() * PX;
        final Matrix4f mat = poseStack.last().pose();
        final VertexConsumer cord = buffers.getBuffer(RenderType.entityCutoutNoCull(CORD));
        // [3]
        final int tint = liveTint(jacketColour(state.getValue(CableBlock.COLOR)), be, partialTicks);

        for (Direction face : Direction.values()) {
            if (!CableBlock.hasFace(state, face)) {
                continue;
            }
            drawFace(be, face, thickness, tint, packedLight, mat, cord, half);
        }
    }

    /** [4] */
    private static int liveTint(int jacket, CableBE be, float partialTicks) {
        final float load = be.loadFraction();
        if (be.isOverloaded()) {
            return 0xFFB84A;
        }
        if (load <= 0.02f) {
            return jacket;
        }
        // [5]
        final float speed = 1.5f + 6.0f * load;
        final net.minecraft.core.BlockPos at = be.getBlockPos();
        final float time = (be.getLevel() == null ? 0L : be.getLevel().getGameTime()) + partialTicks;
        final double phase = (time * speed * 0.05) - (at.getX() + at.getY() + at.getZ()) * 0.9;
        final float wave = (float) Math.max(0.0, Math.sin(phase));
        final float lift = 0.45f * load * (0.55f + 0.45f * wave * wave);
        final int r = (int) (((jacket >> 16) & 0xFF) + (255 - ((jacket >> 16) & 0xFF)) * lift);
        final int g = (int) (((jacket >> 8) & 0xFF) + (255 - ((jacket >> 8) & 0xFF)) * lift);
        final int b = (int) ((jacket & 0xFF) + (255 - (jacket & 0xFF)) * lift);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    /** One face's worth of cable: a bar along the through axis, and a branch for anything else. */
    private void drawFace(CableBE be, Direction face, float t, int jacket, int light,
                          Matrix4f mat, VertexConsumer cord, float half) {
        final Direction.Axis normal = face.getAxis();
        // The slab the cable lives in: hard against the mounting surface, standing off by t.
        final boolean negFace = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
        final float n0 = negFace ? 0.0f : 1.0f - t;
        final float n1 = negFace ? t : 1.0f;
        final Direction.Axis[] inPlane = inPlaneAxes(normal);

        final Arm aNeg = arm(be, face, negative(inPlane[0]), t);
        final Arm aPos = arm(be, face, positive(inPlane[0]), t);
        final Arm bNeg = arm(be, face, negative(inPlane[1]), t);
        final Arm bPos = arm(be, face, positive(inPlane[1]), t);

        // [6]
        final boolean firstIsPrimary = choosePrimary(aNeg != null && aPos != null,
                bNeg != null && bPos != null, aNeg != null || aPos != null, bNeg != null || bPos != null);

        final Direction.Axis pAxis = firstIsPrimary ? inPlane[0] : inPlane[1];
        final Direction.Axis sAxis = firstIsPrimary ? inPlane[1] : inPlane[0];
        final Arm pNeg = firstIsPrimary ? aNeg : bNeg;
        final Arm pPos = firstIsPrimary ? aPos : bPos;

        // The bar. Where there is no arm it stops at the centre, which is what makes a dead end read
        // as a deliberate stub rather than as nothing at all.
        int skip = bit(face);
        if (pNeg != null && !pNeg.cap()) {
            skip |= bit(negative(pAxis));
        }
        if (pPos != null && !pPos.cap()) {
            skip |= bit(positive(pAxis));
        }
        // [7]
        boxOnAxes(cord, mat, normal, n0, n1, pAxis,
                pNeg != null ? pNeg.end() : 0.5f - half,
                pPos != null ? pPos.end() : 0.5f + half,
                0.5f - half, 0.5f + half, jacket, light, skip);

        branch(cord, mat, normal, n0, n1, sAxis, firstIsPrimary ? bNeg : aNeg, false, jacket, light,
                face, half);
        branch(cord, mat, normal, n0, n1, sAxis, firstIsPrimary ? bPos : aPos, true, jacket, light,
                face, half);

        pigtail(cord, mat, normal, n0, n1, inPlane[0], aNeg, false, jacket, light, face, half);
        pigtail(cord, mat, normal, n0, n1, inPlane[0], aPos, true, jacket, light, face, half);
        pigtail(cord, mat, normal, n0, n1, inPlane[1], bNeg, false, jacket, light, face, half);
        pigtail(cord, mat, normal, n0, n1, inPlane[1], bPos, true, jacket, light, face, half);
    }

    /** [8] */
    private void pigtail(VertexConsumer cord, Matrix4f mat, Direction.Axis normal, float n0, float n1,
                         Direction.Axis axis, @Nullable Arm arm, boolean towardsPositive,
                         int jacket, int light, Direction face, float half) {
        if (arm == null || !arm.hasStrands()) {
            return;
        }
        final Direction out = towardsPositive ? positive(axis) : negative(axis);
        final float glandEnd = towardsPositive ? arm.end() + GLAND : arm.end() - GLAND;

        // [9]
        final float collar = Math.max(GLAND_HALF, half + 0.5f * PX);
        boxOnAxes(cord, mat, normal, n0, n1, axis, arm.end(), glandEnd,
                0.5f - collar, 0.5f + collar, jacket, light, bit(face));

        // [10]
        final float mid = (n0 + n1) * 0.5f;
        for (int i = -1; i <= 1; i++) {
            final float centre = 0.5f + i * STRAND_SPREAD;
            boxOnAxes(cord, mat, normal, mid - STRAND, mid + STRAND, axis, glandEnd, arm.gland(),
                    centre - STRAND, centre + STRAND, jacket, light, bit(out.getOpposite()));
        }
    }

    /** [11] */
    private void branch(VertexConsumer cord, Matrix4f mat, Direction.Axis normal, float n0, float n1,
                        Direction.Axis axis, @Nullable Arm arm, boolean towardsPositive,
                        int jacket, int light, Direction face, float half) {
        if (arm == null) {
            return;
        }
        final Direction out = towardsPositive ? positive(axis) : negative(axis);
        int skip = bit(face) | bit(out.getOpposite());
        if (!arm.cap()) {
            skip |= bit(out);
        }
        boxOnAxes(cord, mat, normal, n0, n1, axis,
                towardsPositive ? 0.5f + half : arm.end(),
                towardsPositive ? arm.end() : 0.5f - half,
                0.5f - half, 0.5f + half, jacket, light, skip);
    }

    /** [12] */
    @Nullable
    private static Arm arm(CableBE be, Direction face, Direction towards, float t) {
        final boolean pos = towards.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        // [13]
        final CableLinks.Link kind = be.link(face, towards);
        return switch (kind) {
            case NONE -> null;
            // [14]
            case FLAT -> new Arm(pos ? 1.0f : 0.0f, be.neighborThinner(face, towards, kind));
            // Inside corner. The owner runs on into the corner cube, where its end is buried in the
            // solid block it just turned against; the other stops against the owner's slab.
            case CONCAVE -> CableLinks.ownsCorner(face, towards)
                    ? new Arm(pos ? 1.0f : 0.0f, false)
                    : new Arm(pos ? 1.0f - t : t, false);
            // [15]
            case CONVEX -> CableLinks.ownsCorner(face, towards.getOpposite())
                    ? new Arm(pos ? 1.0f + t : -t, true)
                    : new Arm(pos ? 1.0f : 0.0f, be.neighborThinner(face, towards, kind));
            // [16]
            case DEVICE -> {
                final float reach = be.reachInto(towards);
                final float body = pos ? 1.0f + reach : -reach;
                if (reach < STRAND_MIN_ROOM) {
                    yield new Arm(body, true);   // a full block: no room, and nothing to see anyway
                }
                final float zone = Math.min(reach, STRAND_MAX);
                final float cordEnd = pos ? body - zone : body + zone;
                // Half a pixel past the body, so the strand ends are buried rather than coplanar
                // with the fitting's own surface.
                yield new Arm(cordEnd, false, pos ? body + 0.5f * PX : body - 0.5f * PX);
            }
        };
    }

    /** Which in-plane axis carries the bar: a through run beats a single arm, and the first beats the second. */
    private static boolean choosePrimary(boolean aThrough, boolean bThrough, boolean aAny, boolean bAny) {
        if (aThrough || bThrough) {
            return aThrough;
        }
        if (aAny || bAny) {
            return aAny;
        }
        return true;
    }

    private static Direction positive(Direction.Axis axis) {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
    }

    private static Direction negative(Direction.Axis axis) {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
    }

    private static int bit(Direction dir) {
        return 1 << dir.ordinal();
    }

    /** [17] */
    private void boxOnAxes(VertexConsumer vc, Matrix4f mat, Direction.Axis normal, float n0, float n1,
                           Direction.Axis run, float r0, float r1, float c0, float c1,
                           int argb, int light, int skip) {
        final Direction.Axis cross = third(normal, run);
        final float[] min = new float[3];
        final float[] max = new float[3];
        set(min, max, normal, n0, n1);
        set(min, max, run, r0, r1);
        set(min, max, cross, c0, c1);
        emit(vc, mat, min, max, argb, light, skip);
    }

    private static void set(float[] min, float[] max, Direction.Axis axis, float a, float b) {
        final int i = axis.ordinal();
        min[i] = Math.min(a, b);
        max[i] = Math.max(a, b);
    }

    private static Direction.Axis third(Direction.Axis a, Direction.Axis b) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != a && axis != b) {
                return axis;
            }
        }
        return Direction.Axis.Y;
    }

    private static Direction.Axis[] inPlaneAxes(Direction.Axis normal) {
        return switch (normal) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    /** [18] */
    private void emit(VertexConsumer vc, Matrix4f mat, float[] min, float[] max,
                      int argb, int light, int skip) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        for (Direction dir : Direction.values()) {
            if ((skip & bit(dir)) != 0) {
                continue;
            }
            final float[][] corners = faceCorners(min, max, dir);
            // [19]
            final float su = span(min, max, uAxis(dir));
            final float sv = span(min, max, vAxis(dir));
            final boolean vIsShort = sv <= su;
            final float along = Math.max(0.06f, vIsShort ? su : sv);
            final float[][] uv = vIsShort
                    ? new float[][] {{0, 0}, {along, 0}, {along, 1}, {0, 1}}
                    // Texture turned a quarter turn, so the gradient still crosses the short side.
                    : new float[][] {{0, 0}, {0, 1}, {along, 1}, {along, 0}};
            for (int i = 0; i < 4; i++) {
                vc.addVertex(mat, corners[i][0], corners[i][1], corners[i][2])
                        .setColor(r, g, b, a)
                        .setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
    }

    private static Direction.Axis uAxis(Direction dir) {
        return dir.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    private static Direction.Axis vAxis(Direction dir) {
        return dir.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    private static float span(float[] min, float[] max, Direction.Axis axis) {
        return max[axis.ordinal()] - min[axis.ordinal()];
    }

    private static float[][] faceCorners(float[] n, float[] x, Direction dir) {
        return switch (dir) {
            case DOWN -> new float[][] {{n[0], n[1], n[2]}, {x[0], n[1], n[2]}, {x[0], n[1], x[2]}, {n[0], n[1], x[2]}};
            case UP -> new float[][] {{n[0], x[1], x[2]}, {x[0], x[1], x[2]}, {x[0], x[1], n[2]}, {n[0], x[1], n[2]}};
            case NORTH -> new float[][] {{x[0], n[1], n[2]}, {n[0], n[1], n[2]}, {n[0], x[1], n[2]}, {x[0], x[1], n[2]}};
            case SOUTH -> new float[][] {{n[0], n[1], x[2]}, {x[0], n[1], x[2]}, {x[0], x[1], x[2]}, {n[0], x[1], x[2]}};
            case WEST -> new float[][] {{n[0], n[1], n[2]}, {n[0], n[1], x[2]}, {n[0], x[1], x[2]}, {n[0], x[1], n[2]}};
            case EAST -> new float[][] {{x[0], n[1], x[2]}, {x[0], n[1], n[2]}, {x[0], x[1], n[2]}, {x[0], x[1], x[2]}};
        };
    }

    /** [20] */
    private static int jacketColour(DyeColor dye) {
        final int argb = dye.getTextureDiffuseColor();
        return 0xFF000000
                | (mute((argb >> 16) & 0xFF, 0x3E) << 16)
                | (mute((argb >> 8) & 0xFF, 0x3B) << 8)
                | mute(argb & 0xFF, 0x36);
    }

    /** One channel, blended toward its share of a warm grey and then dimmed. */
    private static int mute(int channel, int grey) {
        return Math.round((channel * 0.66f + grey * 0.34f) * 0.86f);
    }

    /** [21] */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(CableBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.5);
    }
}
