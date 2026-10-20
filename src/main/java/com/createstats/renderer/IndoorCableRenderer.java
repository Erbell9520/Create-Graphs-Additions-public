package com.createstats.renderer;

import com.createstats.block.IndoorCableBlock;
import com.createstats.blockentity.IndoorCableBE;
import com.createstats.power.BoreLinks;
import com.createstats.power.IndoorCable;
import com.createstats.power.IndoorCableAnchor;
import com.createstats.power.IndoorCableLinks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class IndoorCableRenderer implements BlockEntityRenderer<IndoorCableBE> {
    private static final ResourceLocation CORD =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/cable_cord.png");

    private static final float PX = 1.0f / 16.0f;
    private static final float HALF = IndoorCable.HALF_PX * PX;
    private static final float COLLAR = IndoorCable.COLLAR_HALF_PX * PX;
    private static final float COLLAR_LEN = IndoorCable.COLLAR_LENGTH_PX * PX;

    private static final float LANE_INSET = 0.5f - 3.0f * PX;

    private static final float LANE_CLEARANCE = 1.0f * PX;

    public IndoorCableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(IndoorCableBE be, float partialTicks, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof IndoorCableBlock)) {
            return;
        }
        final VertexConsumer cord = Buffers.live(buffers, RenderType.entityCutoutNoCull(CORD));

        final VertexConsumer gland = BoreFitting.buffer(buffers);
        final Matrix4f mat = poseStack.last().pose();
        final IndoorCableAnchor anchor = be.anchor();
        final int tint = liveTint(CableRenderer.jacketColour(state.getValue(IndoorCableBlock.COLOR)), be);

        final IndoorCableLinks.Link[] links = new IndoorCableLinks.Link[6];
        int connected = 0;
        for (Direction dir : Direction.values()) {
            links[dir.ordinal()] = be.link(dir);
            if (links[dir.ordinal()] != IndoorCableLinks.Link.NONE) {
                connected++;
            }
        }
        if (connected == 0) {
            final Direction.Axis stubAxis = anchor.freeAxis();
            barOnAxis(cord, mat, anchor, stubAxis,
                    anchor.centreOn(stubAxis) - COLLAR, anchor.centreOn(stubAxis) + COLLAR,
                    HALF, tint, packedLight, 0);
            return;
        }
        drawCore(be, cord, gland, mat, anchor, links, tint, packedLight);
        drawBores(be, cord, mat, anchor, tint, packedLight, partialTicks);
    }

    private void drawCore(IndoorCableBE be, VertexConsumer cord, VertexConsumer gland, Matrix4f mat,
                          IndoorCableAnchor anchor, IndoorCableLinks.Link[] links, int tint, int light) {
        final Direction.Axis primary = primaryAxis(links);
        final Direction low = Direction.fromAxisAndDirection(primary, Direction.AxisDirection.NEGATIVE);
        final Direction high = Direction.fromAxisAndDirection(primary, Direction.AxisDirection.POSITIVE);
        final IndoorCableLinks.Link lowLink = links[low.ordinal()];
        final IndoorCableLinks.Link highLink = links[high.ordinal()];
        final float centre = anchor.centreOn(primary);

        barOnAxis(cord, mat, anchor, primary,
                lowLink != IndoorCableLinks.Link.NONE ? -reach(be, low, lowLink) : centre - HALF,
                highLink != IndoorCableLinks.Link.NONE ? 1.0f + reach(be, high, highLink) : centre + HALF,
                HALF, tint, light,
                (lowLink != IndoorCableLinks.Link.NONE ? bit(low) : 0)
                        | (highLink != IndoorCableLinks.Link.NONE ? bit(high) : 0));
        endFitting(be, cord, gland, mat, anchor, low, lowLink, tint, light);
        endFitting(be, cord, gland, mat, anchor, high, highLink, tint, light);
        stepToNeighbour(be, cord, mat, anchor, low, lowLink, tint, light);
        stepToNeighbour(be, cord, mat, anchor, high, highLink, tint, light);

        for (Direction dir : Direction.values()) {
            if (dir.getAxis() == primary) {
                continue;
            }
            final IndoorCableLinks.Link link = links[dir.ordinal()];
            if (link == IndoorCableLinks.Link.NONE) {
                continue;
            }
            final boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            final float here = anchor.centreOn(dir.getAxis());
            final float outer = positive ? 1.0f + reach(be, dir, link) : -reach(be, dir, link);
            final float inner = positive ? here + HALF : here - HALF;

            barOnAxis(cord, mat, anchor, dir.getAxis(), Math.min(inner, outer), Math.max(inner, outer),
                    HALF, tint, light, bit(dir) | bit(dir.getOpposite()));
            endFitting(be, cord, gland, mat, anchor, dir, link, tint, light);
            stepToNeighbour(be, cord, mat, anchor, dir, link, tint, light);
        }
    }

    private void stepToNeighbour(IndoorCableBE be, VertexConsumer cord, Matrix4f mat,
                                 IndoorCableAnchor anchor, Direction towards,
                                 IndoorCableLinks.Link link, int tint, int light) {
        if (link != IndoorCableLinks.Link.CABLE
                || towards.getAxisDirection() != Direction.AxisDirection.POSITIVE) {
            return;
        }
        final IndoorCableAnchor theirs = be.anchorTowards(towards);
        if (theirs == anchor) {
            return;
        }
        jogBetween(cord, mat, towards.getAxis(), 1.0f, anchor, theirs, tint, light);
    }

    private void endFitting(IndoorCableBE be, VertexConsumer cord, VertexConsumer gland, Matrix4f mat,
                            IndoorCableAnchor anchor, Direction dir, IndoorCableLinks.Link link,
                            int tint, int light) {
        if (link == IndoorCableLinks.Link.BORE) {
            final Direction.Axis[] across = BoreFitting.across(dir.getAxis());

            BoreFitting.draw(gland, mat, dir,
                    dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f,
                    anchor.centreOn(across[0]), anchor.centreOn(across[1]), HALF, HALF, light);
            return;
        }
        if (link != IndoorCableLinks.Link.DEVICE) {
            return;
        }
        final boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;

        final float outer = positive ? 1.0f + 0.5f * PX : -0.5f * PX;
        final float inner = positive ? 1.0f - COLLAR_LEN : COLLAR_LEN;
        barOnAxis(cord, mat, anchor, dir.getAxis(), Math.min(inner, outer), Math.max(inner, outer),
                COLLAR, tint, light, 0);
    }

    private void drawBores(IndoorCableBE be, VertexConsumer cord, Matrix4f mat, IndoorCableAnchor anchor,
                           int tint, int light, float partialTicks) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        for (BlockPos far : be.bores()) {
            final Direction dir = BoreLinks.axisBetween(be.getBlockPos(), far);
            if (dir == null || dir.getAxisDirection() != Direction.AxisDirection.POSITIVE) {
                continue;
            }
            final List<BlockPos> crossed = BoreLinks.crossings(be.getBlockPos(), far);
            final Direction.Axis axis = dir.getAxis();

            final IndoorCableAnchor farAnchor = anchorAt(level, far, anchor);

            final float[][] lanes = new float[crossed.size()][];
            for (int i = 0; i < crossed.size(); i++) {
                lanes[i] = laneIn(level, crossed.get(i), axis, anchor);
            }
            for (int i = 0; i < crossed.size(); i++) {
                if (lanes[i] == null) {
                    continue;
                }
                final float start = i + 1.0f;
                barAtLane(cord, mat, axis, start, start + 1.0f, lanes[i], tint, light);

                final float[] before = i == 0 ? laneOf(anchor, axis) : lanes[i - 1];
                if (before != null) {
                    jogLanes(cord, mat, axis, start, before, lanes[i], tint, light);
                }
                if (i == crossed.size() - 1) {
                    jogLanes(cord, mat, axis, start + 1.0f, lanes[i], laneOf(farAnchor, axis), tint, light);
                }
            }

            drawPulse(be, cord, mat, axis, anchor, farAnchor, lanes, crossed.size(), light, partialTicks);
        }
    }

    private void drawPulse(IndoorCableBE be, VertexConsumer cord, Matrix4f mat, Direction.Axis axis,
                           IndoorCableAnchor nearAnchor, IndoorCableAnchor farAnchor, float[][] lanes,
                           int span, int light, float partialTicks) {
        if (be.getLevel() == null || be.loadFraction() <= 0.0f) {
            return;
        }

        final float ticksPerBlock = 12.0f;
        final float travel = span;
        final float period = ticksPerBlock * travel;
        final float clock = (float) (com.createstats.client.AnimationClock.time(be.getLevel(), partialTicks)
                % period / period);
        final float head = 1.0f + clock * travel;

        final int index = (int) Math.floor(head) - 1;
        final float[] lane = index < 0 ? laneOf(nearAnchor, axis)
                : index >= lanes.length ? laneOf(farAnchor, axis) : lanes[index];
        if (lane == null) {
            return;
        }
        final float length = 0.4f;
        final float tail = Math.max(1.0f, head - length);
        final float nose = Math.min(span + 1.0f, head);
        if (nose - tail < 1.0e-3f) {
            return;
        }

        barAtLane(cord, mat, axis, tail, nose, lane, HALF + PULSE_SWELL, pulseTint(be), 0xF000F0);
    }

    private static int pulseTint(IndoorCableBE be) {
        return be.isOverloaded() ? 0xFFFFD9A0 : 0xFFFFF4DC;
    }

    private static IndoorCableAnchor anchorAt(Level level, BlockPos pos, IndoorCableAnchor fallback) {
        if (!level.hasChunkAt(pos)) {
            return fallback;
        }
        final BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof IndoorCableBlock ? IndoorCableBlock.anchorOf(state) : fallback;
    }

    private static float[] laneOf(IndoorCableAnchor anchor, Direction.Axis along) {
        final Direction.Axis[] across = across(along);
        return new float[] {anchor.centreOn(across[0]) - 0.5f, anchor.centreOn(across[1]) - 0.5f};
    }

    @Nullable
    private static float[] laneIn(Level level, BlockPos at, Direction.Axis axis, IndoorCableAnchor anchor) {
        if (!level.hasChunkAt(at)) {
            return null;
        }
        final BlockState state = level.getBlockState(at);
        if (state.isAir()) {
            return laneOf(anchor, axis);
        }
        final VoxelShape shape = state.getShape(level, at);
        if (shape.isEmpty()) {
            return laneOf(anchor, axis);
        }
        final List<float[]> candidates = new ArrayList<>(10);
        candidates.add(laneOf(anchor, axis));
        for (float[] lane : candidateLanes(axis)) {
            candidates.add(lane);
        }
        for (float[] lane : candidates) {
            if (!blocked(shape, axis, lane)) {
                return lane;
            }
        }
        return null;
    }

    private static float[][] candidateLanes(Direction.Axis axis) {
        final float d = LANE_INSET;

        return axis == Direction.Axis.Y
                ? new float[][] {{0, 0}, {-d, 0}, {d, 0}, {0, -d}, {0, d},
                                 {-d, -d}, {d, -d}, {-d, d}, {d, d}}
                : new float[][] {{-d, 0}, {0, 0}, {0, -d}, {0, d}, {d, 0},
                                 {-d, -d}, {-d, d}, {d, -d}, {d, d}};
    }

    private static boolean blocked(VoxelShape shape, Direction.Axis axis, float[] lane) {
        final float half = HALF + LANE_CLEARANCE;
        final Direction.Axis[] across = across(axis);
        final double[] min = new double[3];
        final double[] max = new double[3];
        min[axis.ordinal()] = 0.0;
        max[axis.ordinal()] = 1.0;
        for (int i = 0; i < 2; i++) {
            min[across[i].ordinal()] = 0.5 + lane[i] - half;
            max[across[i].ordinal()] = 0.5 + lane[i] + half;
        }
        return Shapes.joinIsNotEmpty(shape,
                Shapes.box(min[0], min[1], min[2], max[0], max[1], max[2]), BooleanOp.AND);
    }

    private void barAtLane(VertexConsumer cord, Matrix4f mat, Direction.Axis axis,
                           float from, float to, float[] lane, int tint, int light) {
        barAtLane(cord, mat, axis, from, to, lane, HALF, tint, light);
    }

    private void barAtLane(VertexConsumer cord, Matrix4f mat, Direction.Axis axis, float from, float to,
                           float[] lane, float half, int tint, int light) {
        final Direction.Axis[] across = across(axis);
        final float[] min = new float[3];
        final float[] max = new float[3];
        min[axis.ordinal()] = from;
        max[axis.ordinal()] = to;
        for (int i = 0; i < 2; i++) {
            min[across[i].ordinal()] = 0.5f + lane[i] - half;
            max[across[i].ordinal()] = 0.5f + lane[i] + half;
        }
        emit(cord, mat, min, max, tint, light, 0);
    }

    private static final float PULSE_SWELL = 0.16f * PX;

    private void jogBetween(VertexConsumer cord, Matrix4f mat, Direction.Axis axis, float at,
                            IndoorCableAnchor from, IndoorCableAnchor to, int tint, int light) {
        jogLanes(cord, mat, axis, at, laneOf(from, axis), laneOf(to, axis), tint, light);
    }

    private void jogLanes(VertexConsumer cord, Matrix4f mat, Direction.Axis axis, float at,
                          float[] from, float[] to, int tint, int light) {
        final Direction.Axis[] across = across(axis);
        final float[] running = new float[] {from[0], from[1]};
        for (int i = 0; i < 2; i++) {
            if (Math.abs(to[i] - running[i]) < 1.0e-4f) {
                continue;
            }
            final float[] min = new float[3];
            final float[] max = new float[3];
            min[axis.ordinal()] = at - HALF;
            max[axis.ordinal()] = at + HALF;
            final int other = 1 - i;
            min[across[i].ordinal()] = 0.5f + Math.min(running[i], to[i]) - HALF;
            max[across[i].ordinal()] = 0.5f + Math.max(running[i], to[i]) + HALF;
            min[across[other].ordinal()] = 0.5f + running[other] - HALF;
            max[across[other].ordinal()] = 0.5f + running[other] + HALF;
            emit(cord, mat, min, max, tint, light, 0);
            running[i] = to[i];
        }
    }

    private static Direction.Axis primaryAxis(IndoorCableLinks.Link[] links) {
        Direction.Axis best = null;
        int bestScore = -1;
        for (Direction.Axis axis : Direction.Axis.values()) {
            final boolean low = links[Direction.fromAxisAndDirection(
                    axis, Direction.AxisDirection.NEGATIVE).ordinal()] != IndoorCableLinks.Link.NONE;
            final boolean high = links[Direction.fromAxisAndDirection(
                    axis, Direction.AxisDirection.POSITIVE).ordinal()] != IndoorCableLinks.Link.NONE;
            final int score = (low && high) ? 2 : (low || high) ? 1 : 0;
            if (score > bestScore) {
                bestScore = score;
                best = axis;
            }
        }
        return best == null ? Direction.Axis.Y : best;
    }

    private static float reach(IndoorCableBE be, Direction dir, IndoorCableLinks.Link link) {
        return link == IndoorCableLinks.Link.DEVICE ? be.reachInto(dir) : 0.0f;
    }

    private static int bit(Direction dir) {
        return 1 << dir.ordinal();
    }

    private static Direction.Axis[] across(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.X};
        };
    }

    private void barOnAxis(VertexConsumer cord, Matrix4f mat, IndoorCableAnchor anchor,
                           Direction.Axis axis, float from, float to,
                           float half, int tint, int light, int skip) {
        final float[] min = new float[3];
        final float[] max = new float[3];
        min[axis.ordinal()] = from;
        max[axis.ordinal()] = to;
        for (Direction.Axis other : across(axis)) {
            min[other.ordinal()] = anchor.centreOn(other) - half;
            max[other.ordinal()] = anchor.centreOn(other) + half;
        }
        emit(cord, mat, min, max, tint, light, skip);
    }

    void emit(VertexConsumer vc, Matrix4f mat, float[] min, float[] max, int argb, int light, int skip) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        for (Direction dir : Direction.values()) {
            if ((skip & bit(dir)) != 0) {
                continue;
            }
            final float[][] corners = faceCorners(min, max, dir);
            final float su = span(min, max, uAxis(dir));
            final float sv = span(min, max, vAxis(dir));
            final boolean vIsShort = sv <= su;
            final float along = Math.max(0.06f, vIsShort ? su : sv);
            final float[][] uv = vIsShort
                    ? new float[][] {{0, 0}, {along, 0}, {along, 1}, {0, 1}}
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

    private static int liveTint(int jacket, IndoorCableBE be) {
        if (be.isOverloaded()) {
            return 0xFFB84A;
        }
        final float load = be.loadFraction();
        if (load <= 0.0f) {
            return jacket;
        }
        final int r = (jacket >> 16) & 0xFF;
        final int g = (jacket >> 8) & 0xFF;
        final int b = jacket & 0xFF;
        final float lift = 0.35f * load;
        return 0xFF000000
                | (Math.round(r + (255 - r) * lift) << 16)
                | (Math.round(g + (255 - g) * lift) << 8)
                | Math.round(b + (255 - b) * lift);
    }

    @Override
    public AABB getRenderBoundingBox(IndoorCableBE be) {
        AABB box = new AABB(be.getBlockPos()).inflate(0.5);
        for (BlockPos far : new ArrayList<>(be.bores())) {
            box = box.minmax(new AABB(far).inflate(0.5));
        }
        return box;
    }
}
