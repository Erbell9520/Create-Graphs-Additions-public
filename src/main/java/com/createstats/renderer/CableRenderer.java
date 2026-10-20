package com.createstats.renderer;

import com.createstats.block.CableBlock;
import com.createstats.blockentity.CableBE;
import com.createstats.power.CableLinks;
import com.createstats.power.CableTier;
import com.createstats.renderer.CableProfile.Band;
import com.createstats.renderer.CableProfile.Grade;
import com.createstats.renderer.CableProfile.Part;
import com.createstats.renderer.CableProfile.Region;
import com.createstats.renderer.CableProfile.Skin;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// neighbours as blockstate = 24 booleans = 16M states. no thanks
public class CableRenderer implements BlockEntityRenderer<CableBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final Map<CableTier, ResourceLocation> ATLASES = new EnumMap<>(CableTier.class);

    static {
        for (CableTier tier : CableTier.values()) {
            ATLASES.put(tier, ResourceLocation.fromNamespaceAndPath("create_stats",
                    "textures/block/" + CableProfile.of(tier).atlas() + ".png"));
        }
    }

    private static final float STRAND_PX = 0.25f;

    private static final float STRAND_SPREAD_PX = 0.65f;

    private static final float GLAND = 0.75f * PX;

    private static final float GLAND_HALF_PX = 1.5f;

    private static final float STRAND_MIN_ROOM = 2.0f * PX;

    private static final float STRAND_MAX = 3.0f * PX;

    private enum Kind {
        THROUGH,

        CHANGE,

        STOP,

        OVER,

        UNDER,

        WRAP,

        DEVICE
    }

    private record Arm(Kind kind, boolean pos, float reach) {
        float end(Band band, Grade grade) {
            final float boundary = pos ? 1.0f : 0.0f;
            final float out = pos ? 1.0f : -1.0f;
            return switch (kind) {
                case THROUGH, CHANGE -> boundary;
                case STOP -> boundary - out * band.n1() * PX;
                case OVER, WRAP -> boundary + out * band.n1() * PX;
                case UNDER -> boundary + out * band.n0() * PX;
                case DEVICE -> {
                    final float body = boundary + out * reach;
                    if (!roomForStrands()) {
                        yield body;
                    }

                    yield grade.strands() ? body - out * Math.min(reach, STRAND_MAX)
                            : body + out * 0.5f * PX;
                }
            };
        }

        boolean roomForStrands() {
            return kind == Kind.DEVICE && reach >= STRAND_MIN_ROOM;
        }

        boolean strands(Grade grade) {
            return grade.strands() && roomForStrands();
        }

        float glandTip() {
            final float out = pos ? 1.0f : -1.0f;
            return (pos ? 1.0f : 0.0f) + out * (reach + 0.5f * PX);
        }

        boolean capped() {
            return kind == Kind.OVER || kind == Kind.WRAP || (kind == Kind.DEVICE && !roomForStrands());
        }
    }

    private record End(float at, boolean face, boolean cut) {
    }

    private record Ctx(VertexConsumer vc, Matrix4f mat, Grade grade, int tint, int light, Direction face) {
        int colour(boolean dyed) {
            return dyed ? 0xFF000000 | tint : 0xFFFFFFFF;
        }

        boolean negFace() {
            return face.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
        }

        int mount() {
            return bit(face);
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
        final CableTier tier = cable.tier();
        final Grade grade = CableProfile.of(tier);
        final Matrix4f mat = poseStack.last().pose();

        final VertexConsumer vc = Buffers.live(buffers, RenderType.entityCutoutNoCull(ATLASES.get(tier)));

        final int tint = liveTint(jacketColour(state.getValue(CableBlock.COLOR)), be, partialTicks);

        for (Direction face : Direction.values()) {
            if (!CableBlock.hasFace(state, face)) {
                continue;
            }
            drawFace(be, new Ctx(vc, mat, grade, tint, packedLight, face));
        }
        drawBoreGlands(be, mat, buffers, tier.thicknessPx() * PX, tier.halfWidthPx() * PX, packedLight);
    }

    // mark bores, they're invisible between their ends
    private void drawBoreGlands(CableBE be, Matrix4f mat, MultiBufferSource buffers,
                                float thickness, float halfWidth, int light) {
        if (be.bores().isEmpty()) {
            return;
        }
        final BlockState state = be.getBlockState();
        final VertexConsumer gland = BoreFitting.buffer(buffers);
        for (net.minecraft.core.BlockPos far : be.bores()) {
            final Direction towards = com.createstats.power.BoreLinks.axisBetween(be.getBlockPos(), far);
            if (towards == null) {
                continue;
            }
            final Direction.Axis[] across = BoreFitting.across(towards.getAxis());
            final float[] centre = {0.5f, 0.5f, 0.5f};

            final float[] half = {halfWidth, halfWidth};
            for (Direction face : Direction.values()) {
                if (face.getAxis() == towards.getAxis() || !CableBlock.hasFace(state, face)) {
                    continue;
                }

                final int i = face.getAxis().ordinal();
                centre[i] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? 1.0f - thickness * 0.5f : thickness * 0.5f;
                for (int k = 0; k < 2; k++) {
                    if (across[k] == face.getAxis()) {
                        half[k] = thickness * 0.5f;
                    }
                }
                break;
            }
            BoreFitting.draw(gland, mat, towards,
                    towards.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : 0.0f,
                    centre[across[0].ordinal()], centre[across[1].ordinal()],
                    half[0], half[1], light);
        }
    }

    private static int liveTint(int jacket, CableBE be, float partialTicks) {
        final float load = be.loadFraction();
        if (be.isOverloaded()) {
            return 0xFFB84A;
        }
        if (load <= 0.02f) {
            return jacket & 0xFFFFFF;
        }

        final float speed = 1.5f + 6.0f * load;
        final net.minecraft.core.BlockPos at = be.getBlockPos();
        final double time = com.createstats.client.AnimationClock.time(be.getLevel(), partialTicks);
        final double phase = be.flowPhase().advance(time, speed * 0.05)
                - (at.getX() + at.getY() + at.getZ()) * CableProfile.PULSE_STEP;
        final float wave = (float) Math.max(0.0, Math.sin(phase));
        final float lift = 0.45f * load * (0.55f + 0.45f * wave * wave);
        final int r = (int) (((jacket >> 16) & 0xFF) + (255 - ((jacket >> 16) & 0xFF)) * lift);
        final int g = (int) (((jacket >> 8) & 0xFF) + (255 - ((jacket >> 8) & 0xFF)) * lift);
        final int b = (int) ((jacket & 0xFF) + (255 - (jacket & 0xFF)) * lift);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private void drawFace(CableBE be, Ctx ctx) {
        final Direction.Axis[] inPlane = inPlaneAxes(ctx.face().getAxis());
        final Arm aNeg = arm(be, ctx.face(), negative(inPlane[0]));
        final Arm aPos = arm(be, ctx.face(), positive(inPlane[0]));
        final Arm bNeg = arm(be, ctx.face(), negative(inPlane[1]));
        final Arm bPos = arm(be, ctx.face(), positive(inPlane[1]));
        if (ctx.grade().nested()) {
            drawNested(be, ctx, inPlane, aNeg, aPos, bNeg, bPos);
        } else {
            drawStacked(be, ctx, inPlane, aNeg, aPos, bNeg, bPos);
        }
    }

    private void drawStacked(CableBE be, Ctx ctx, Direction.Axis[] inPlane,
                             @Nullable Arm aNeg, @Nullable Arm aPos, @Nullable Arm bNeg, @Nullable Arm bPos) {
        final boolean firstIsPrimary = choosePrimary(aNeg != null && aPos != null,
                bNeg != null && bPos != null, aNeg != null || aPos != null, bNeg != null || bPos != null);
        final Direction.Axis pAxis = firstIsPrimary ? inPlane[0] : inPlane[1];
        final Direction.Axis sAxis = firstIsPrimary ? inPlane[1] : inPlane[0];
        final Arm pNeg = firstIsPrimary ? aNeg : bNeg;
        final Arm pPos = firstIsPrimary ? aPos : bPos;
        final Arm sNeg = firstIsPrimary ? bNeg : aNeg;
        final Arm sPos = firstIsPrimary ? bPos : aPos;
        final boolean branches = sNeg != null || sPos != null;
        final Grade grade = ctx.grade();

        for (Band band : grade.bands()) {
            final End lo = end(pNeg, false, band, grade, branches);
            final End hi = end(pPos, true, band, grade, branches);
            bar(ctx, pAxis, lo, hi, band);
            branch(ctx, sAxis, sNeg, false, band);
            branch(ctx, sAxis, sPos, true, band);
        }

        if (pNeg != null && pPos != null && !branches && hardwareHere(be, grade.spacing())) {
            for (Part part : grade.hardware()) {
                part(ctx, pAxis, part.a0() * PX, part.a1() * PX, part);
            }
        }

        finish(ctx, pAxis, pNeg, false, !branches);
        finish(ctx, pAxis, pPos, true, !branches);
        finish(ctx, sAxis, sNeg, false, false);
        finish(ctx, sAxis, sPos, true, false);
    }

    private static End end(@Nullable Arm arm, boolean pos, Band band, Grade grade, boolean branches) {
        final boolean fitted = !grade.fitting().isEmpty();
        if (arm == null) {
            if (branches) {
                return new End(0.5f + (pos ? 1 : -1) * band.half() * PX, true, false);
            }
            return new End(0.5f + (pos ? 1 : -1) * grade.halfWidthPx() * PX, !fitted, true);
        }
        final float at = arm.end(band, grade);
        if (arm.kind() == Kind.CHANGE) {
            return new End(at, !fitted, true);
        }
        if (arm.strands(grade)) {
            return new End(at, false, false);
        }
        return new End(at, arm.capped(), false);
    }

    private void bar(Ctx ctx, Direction.Axis run, End lo, End hi, Band band) {
        if (hi.at() - lo.at() < 1.0e-4f) {
            return;
        }
        int skip = ctx.mount();
        if (!lo.face()) {
            skip |= bit(negative(run));
        }
        if (!hi.face()) {
            skip |= bit(positive(run));
        }
        box(ctx, run, lo.at(), hi.at(), band.c0(), band.c1(), band.n0(), band.n1(), band.skin(), true,
                band.dyed(), skip, lo.cut(), hi.cut());
    }

    private void branch(Ctx ctx, Direction.Axis axis, @Nullable Arm arm, boolean pos, Band band) {
        if (arm == null) {
            return;
        }
        final End outer = end(arm, pos, band, ctx.grade(), true);
        final float inner = 0.5f + (pos ? 1 : -1) * band.half() * PX;
        int skip = ctx.mount() | bit(pos ? negative(axis) : positive(axis));
        if (!outer.face()) {
            skip |= bit(pos ? positive(axis) : negative(axis));
        }
        final float from = Math.min(inner, outer.at());
        final float to = Math.max(inner, outer.at());
        if (to - from < 1.0e-4f) {
            return;
        }
        box(ctx, axis, from, to, band.c0(), band.c1(), band.n0(), band.n1(), band.skin(), true,
                band.dyed(), skip, !pos && outer.cut(), pos && outer.cut());
    }

    private void finish(Ctx ctx, Direction.Axis axis, @Nullable Arm arm, boolean pos, boolean stub) {
        final Grade grade = ctx.grade();
        final List<Part> fitting = grade.fitting();
        if (arm == null) {
            if (stub && !fitting.isEmpty()) {
                fitting(ctx, axis, 0.5f + (pos ? 1 : -1) * grade.halfWidthPx() * PX, pos);
            }
            return;
        }
        if (arm.kind() == Kind.CHANGE) {
            if (!fitting.isEmpty()) {
                fitting(ctx, axis, pos ? 1.0f : 0.0f, pos);
            }
            return;
        }
        if (!arm.strands(grade)) {
            return;
        }

        final float cordEnd = arm.end(grade.bands().get(0), grade);
        final float out = pos ? 1.0f : -1.0f;
        final float strandsFrom;
        if (!fitting.isEmpty()) {
            fitting(ctx, axis, cordEnd, pos);
            strandsFrom = cordEnd;
        } else {
            final float collar = Math.max(GLAND_HALF_PX, grade.halfWidthPx() + 0.5f);
            strandsFrom = cordEnd + out * GLAND;
            box(ctx, axis, Math.min(cordEnd, strandsFrom), Math.max(cordEnd, strandsFrom),
                    8 - collar, 8 + collar, 0, grade.thicknessPx(), grade.gland(), false, false,
                    ctx.mount(), false, false);
        }

        final float mid = grade.thicknessPx() * 0.5f;
        final Skin paint = Skin.uniform(grade.paint());
        final float tip = arm.glandTip();
        final int skip = bit(pos ? negative(axis) : positive(axis));
        for (int i = -1; i <= 1; i++) {
            final float c = 8 + i * STRAND_SPREAD_PX;
            box(ctx, axis, Math.min(strandsFrom, tip), Math.max(strandsFrom, tip),
                    c - STRAND_PX, c + STRAND_PX, mid - STRAND_PX, mid + STRAND_PX, paint, false, true,
                    skip, false, false);
        }
    }

    private void fitting(Ctx ctx, Direction.Axis axis, float at, boolean pos) {
        for (Part part : ctx.grade().fitting()) {
            final float a0 = pos ? at - part.a1() * PX : at + part.a0() * PX;
            final float a1 = pos ? at - part.a0() * PX : at + part.a1() * PX;
            part(ctx, axis, a0, a1, part);
        }
    }

    private void part(Ctx ctx, Direction.Axis run, float a0, float a1, Part part) {
        box(ctx, run, a0, a1, part.c0(), part.c1(), part.n0(), part.n1(), part.skin(), false,
                part.dyed(), ctx.mount(), false, false);
    }

    private void drawNested(CableBE be, Ctx ctx, Direction.Axis[] inPlane,
                            @Nullable Arm aNeg, @Nullable Arm aPos, @Nullable Arm bNeg, @Nullable Arm bPos) {
        final Grade grade = ctx.grade();
        final Arm[] arms = {aNeg, aPos, bNeg, bPos};
        int count = 0;
        for (Arm a : arms) {
            if (a != null) {
                count++;
            }
        }

        if (count >= 3) {
            for (Part part : grade.splice()) {
                part(ctx, inPlane[0], part.a0() * PX, part.a1() * PX, part);
            }
            final float edge = CableProfile.SPLICE_HALF * PX;
            for (int i = 0; i < 4; i++) {
                final Arm arm = arms[i];
                if (arm == null) {
                    continue;
                }
                final Direction.Axis axis = i < 2 ? inPlane[0] : inPlane[1];
                for (Band band : grade.bands()) {
                    branchFrom(ctx, axis, arm, arm.pos(), band, 0.5f + (arm.pos() ? edge : -edge));
                }
            }
            return;
        }

        final boolean throughA = aNeg != null && aPos != null;
        final boolean throughB = bNeg != null && bPos != null;
        if (count == 2 && (throughA || throughB)) {
            final Direction.Axis axis = throughA ? inPlane[0] : inPlane[1];
            final Arm neg = throughA ? aNeg : bNeg;
            final Arm pos = throughA ? aPos : bPos;
            for (Band band : grade.bands()) {
                bar(ctx, axis, end(neg, false, band, grade, false), end(pos, true, band, grade, false), band);
            }
            if (hardwareHere(be, grade.spacing())) {
                for (Part part : grade.hardware()) {
                    part(ctx, axis, part.a0() * PX, part.a1() * PX, part);
                }
            }
            return;
        }

        if (count == 2) {
            final Arm p = aNeg != null ? aNeg : aPos;
            final Arm s = bNeg != null ? bNeg : bPos;
            for (Band band : grade.bands()) {
                elbow(ctx, inPlane[0], p, inPlane[1], s, band);
            }
            return;
        }

        final Direction.Axis axis = (aNeg != null || aPos != null) ? inPlane[0]
                : (bNeg != null || bPos != null) ? inPlane[1] : inPlane[0];
        final Arm neg = axis == inPlane[0] ? aNeg : bNeg;
        final Arm pos = axis == inPlane[0] ? aPos : bPos;
        for (Band band : grade.bands()) {
            bar(ctx, axis, end(neg, false, band, grade, false), end(pos, true, band, grade, false), band);
        }
    }

    private void branchFrom(Ctx ctx, Direction.Axis axis, Arm arm, boolean pos, Band band, float inner) {
        final End outer = end(arm, pos, band, ctx.grade(), true);
        int skip = ctx.mount() | bit(pos ? negative(axis) : positive(axis));
        if (!outer.face()) {
            skip |= bit(pos ? positive(axis) : negative(axis));
        }
        final float from = Math.min(inner, outer.at());
        final float to = Math.max(inner, outer.at());
        if (to - from < 1.0e-4f) {
            return;
        }
        box(ctx, axis, from, to, band.c0(), band.c1(), band.n0(), band.n1(), band.skin(), true,
                band.dyed(), skip, !pos && outer.cut(), pos && outer.cut());
    }

    private void elbow(Ctx ctx, Direction.Axis pAxis, Arm p, Direction.Axis sAxis, Arm s, Band band) {
        final Grade grade = ctx.grade();
        final float sp = p.pos() ? 1.0f : -1.0f;
        final float ss = s.pos() ? 1.0f : -1.0f;
        final float centre = (band.c0() + band.c1()) * 0.5f;
        final float w = (band.c1() - band.c0()) * 0.5f;
        final float turn = 0.5f + sp * ss * (centre - 8.0f) * PX;

        final End pEnd = end(p, p.pos(), band, grade, true);
        final float corner = turn - sp * w * PX;
        final End cornerEnd = new End(corner, true, false);
        bar(ctx, pAxis, p.pos() ? cornerEnd : pEnd, p.pos() ? pEnd : cornerEnd, band);

        final float start = centre * PX + ss * w * PX;
        final End sEnd = end(s, s.pos(), band, grade, true);
        int skip = ctx.mount() | bit(s.pos() ? negative(sAxis) : positive(sAxis));
        if (!sEnd.face()) {
            skip |= bit(s.pos() ? positive(sAxis) : negative(sAxis));
        }
        final float from = Math.min(start, sEnd.at());
        final float to = Math.max(start, sEnd.at());
        if (to - from < 1.0e-4f) {
            return;
        }
        final float cross = turn * 16.0f;
        box(ctx, sAxis, from, to, cross - w, cross + w, band.n0(), band.n1(), band.skin(), true,
                band.dyed(), skip, !s.pos() && sEnd.cut(), s.pos() && sEnd.cut());
    }

    @Nullable
    private static Arm arm(CableBE be, Direction face, Direction towards) {
        final boolean pos = towards.getAxisDirection() == Direction.AxisDirection.POSITIVE;

        if (be.boreTowards(towards) != null && towards.getAxis() != face.getAxis()) {
            return new Arm(Kind.THROUGH, pos, 0.0f);
        }

        final CableLinks.Link kind = be.link(face, towards);
        return switch (kind) {
            case NONE -> null;

            case FLAT -> new Arm(be.neighborOtherGrade(face, towards, kind) ? Kind.CHANGE : Kind.THROUGH,
                    pos, 0.0f);

            case CONCAVE -> new Arm(CableLinks.ownsCorner(face, towards) ? Kind.THROUGH : Kind.STOP,
                    pos, 0.0f);

            case CONVEX -> CableLinks.ownsCorner(face, towards.getOpposite())
                    ? new Arm(Kind.OVER, pos, 0.0f)
                    : new Arm(be.neighborOtherGrade(face, towards, kind) ? Kind.CHANGE : Kind.UNDER,
                            pos, 0.0f);

            case DEVICE_WRAP -> new Arm(Kind.WRAP, pos, 0.0f);

            case DEVICE -> new Arm(Kind.DEVICE, pos, be.reachInto(towards));
        };
    }

    private static boolean choosePrimary(boolean aThrough, boolean bThrough, boolean aAny, boolean bAny) {
        if (aThrough || bThrough) {
            return aThrough;
        }
        if (aAny || bAny) {
            return aAny;
        }
        return true;
    }

    private static boolean hardwareHere(CableBE be, int spacing) {
        if (spacing <= 0) {
            return false;
        }
        final net.minecraft.core.BlockPos at = be.getBlockPos();

        return Math.floorMod(at.getX() + at.getY() + at.getZ(), spacing) == 0;
    }

    private void box(Ctx ctx, Direction.Axis run, float a0, float a1, float c0, float c1, float n0,
                     float n1, Skin skin, boolean strip, boolean dyed, int skip, boolean negCut,
                     boolean posCut) {
        final Direction.Axis normal = ctx.face().getAxis();
        final Direction.Axis cross = third(normal, run);
        final boolean negFace = ctx.negFace();
        final float[] min = new float[3];
        final float[] max = new float[3];
        set(min, max, run, a0, a1);
        set(min, max, cross, c0 * PX, c1 * PX);
        if (negFace) {
            set(min, max, normal, n0 * PX, n1 * PX);
        } else {
            set(min, max, normal, 1.0f - n1 * PX, 1.0f - n0 * PX);
        }
        final int argb = ctx.colour(dyed);
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final float start = Math.min(a0, a1) * 16.0f;
        for (Direction dir : Direction.values()) {
            if ((skip & bit(dir)) != 0) {
                continue;
            }
            final float[][] corners = faceCorners(min, max, dir);
            for (float[] p : corners) {
                final float a = p[run.ordinal()] * 16.0f;
                final float c = p[cross.ordinal()] * 16.0f;
                final float n = negFace ? p[normal.ordinal()] * 16.0f : (1.0f - p[normal.ordinal()]) * 16.0f;
                final float along = strip ? CableProfile.STRIP_OFFSET + a : a - start;
                final float u;
                final float v;
                if (dir.getAxis() == normal) {
                    final Region reg = skin.top();
                    u = reg.u() + along;
                    v = reg.v() + (c - c0);
                } else if (dir.getAxis() == cross) {
                    final Region reg = skin.flank();
                    u = reg.u() + along;
                    v = reg.v() + (n1 - n);
                } else {
                    final boolean negEnd = dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
                    final Region reg = (negEnd ? negCut : posCut) ? skin.cut() : skin.end();
                    u = reg.u() + (c - c0);
                    v = reg.v() + (n1 - n);
                }
                ctx.vc().addVertex(ctx.mat(), p[0], p[1], p[2])
                        .setColor(r, g, b, 255)
                        .setUv(u / CableProfile.ATLAS, v / CableProfile.ATLAS)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(ctx.light())
                        .setNormal(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
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

    static int jacketColour(DyeColor dye) {
        final int argb = dye.getTextureDiffuseColor();
        return 0xFF000000
                | (mute((argb >> 16) & 0xFF, 0x3E) << 16)
                | (mute((argb >> 8) & 0xFF, 0x3B) << 8)
                | mute(argb & 0xFF, 0x36);
    }

    private static int mute(int channel, int grey) {
        return Math.round((channel * 0.66f + grey * 0.34f) * 0.86f);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(CableBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.5);
    }
}
