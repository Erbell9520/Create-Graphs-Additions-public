package com.createstats.renderer;

import com.createstats.block.SwitchBlock;
import com.createstats.blockentity.SwitchBE;
import com.createstats.client.timeswitch.TimeSwitchView;
import com.createstats.timeswitch.TimeSwitchDial;
import com.createstats.timeswitch.TimeSwitchProgram;
import com.createstats.timeswitch.TimeSwitchProgram.Mode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
final class TimeSwitchRenderer {
    private TimeSwitchRenderer() {
    }

    private static final ResourceLocation PARTS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/time_switch_dynamic.png");

    private static final float PX = 1.0f / 16.0f;

    private static final float SHEET = 128.0f;
    private static final int CELL = 16;

    private record Tile(float u0, float v0, float u1, float v1) {
        Tile sub(float fx0, float fy0, float fx1, float fy1) {
            final float w = u1 - u0, h = v1 - v0;
            return new Tile(u0 + w * fx0, v0 + h * fy0, u0 + w * fx1, v0 + h * fy1);
        }
    }

    private static Tile cell(int col, int row) {
        final float inset = 0.5f / SHEET;
        return new Tile(col * CELL / SHEET + inset, row * CELL / SHEET + inset,
                (col + 1) * CELL / SHEET - inset, (row + 1) * CELL / SHEET - inset);
    }

    private static final Tile T_DIAL = new Tile(4.0f / SHEET, 4.0f / SHEET, 44.0f / SHEET, 44.0f / SHEET);
    private static final Tile T_DIAL_EDGE = cell(3, 0);
    private static final Tile T_SLIDER_TOP = cell(4, 0);
    private static final Tile T_SLIDER_SIDE = cell(5, 0);
    private static final Tile T_SLIDER_END = cell(6, 0);
    private static final Tile T_KNOB_TOP = cell(7, 0);
    private static final Tile T_KNOB_SIDE = cell(3, 1);
    private static final Tile T_FOLLOWER = cell(4, 1);
    private static final Tile T_LENS = cell(5, 1);
    private static final Tile T_HUB_SIDE = cell(6, 1);
    private static final Tile T_HUB_TOP = cell(7, 1);
    private static final Tile T_RUNNER = cell(3, 2);

    private static final float CU = (float) TimeSwitchDial.CU;
    private static final float CV = (float) TimeSwitchDial.CV;
    private static final float BODY_H = 1.4f;
    private static final float DIAL_EDGE_R = 4.95f;
    private static final float FACE_H = (float) TimeSwitchDial.FACE_H;
    private static final float DIAL_R = (float) TimeSwitchDial.DIAL_R;
    private static final float SLIDER_LEN = (float) TimeSwitchDial.SLIDER_LEN;
    private static final float SLIDER_W = (float) TimeSwitchDial.SLIDER_W;
    private static final float SLIDER_H = (float) TimeSwitchDial.SLIDER_H;
    private static final float OUT_R0 = (float) TimeSwitchDial.OUT_R0;
    private static final float IN_R0 = (float) TimeSwitchDial.IN_R0;
    private static final float HUB_R = (float) TimeSwitchDial.HUB_R;
    private static final float HUB_TOP_H = (float) TimeSwitchDial.HUB_TOP_H;
    private static final float SEAT = 0.10f;
    private static final float HAIRLINE = 0.006f;
    private static final float LENS_U0 = 6.7f, LENS_U1 = 9.3f, LENS_V0 = 1.15f, LENS_V1 = 2.05f;
    private static final float LENS_H0 = 1.3f, LENS_H1 = 1.74f;
    private static final float RUN_U = 13.25f, RUN_V = 2.75f, RUN_R = 0.6f, RUN_H = 1.704f;
    private static final float FOLLOWER_W = 0.36f, FOLLOWER_LEN = 1.05f;
    private static final float FOLLOWER_PIVOT_R = 5.3f, FOLLOWER_PIVOT_H = 2.36f;

    private static final float FOLLOWER_DROP =
            (float) Math.toDegrees(Math.asin((SLIDER_H - 0.06f) / FOLLOWER_LEN));
    private static final float COS_16 = (float) Math.cos(Math.PI / 16.0);
    private static final float SIN_16 = (float) Math.sin(Math.PI / 16.0);

    private static final int WHITE = 0xFFFFFFFF;

    private static final int LENS_DEAD = 0xFF1E2E22;
    private static final int LENS_IDLE = 0xFFE0A63C;
    private static final int LENS_LIVE = 0xFF6BE39A;

    private static final int PREVIEW = 0xFFF0C060;

    private static final double SLIDER_SECONDS = 0.12D;
    private static final double KNOB_SECONDS = 0.14D;
    private static final double FOLLOWER_SECONDS = 0.16D;

    private static final double DIAL_CATCHUP_DEG_PER_S = 1080.0D;

    private static final Map<Level, Map<BlockPos, Anim>> ANIMS = new WeakHashMap<>();

    private static final class Anim {
        final float[] slider = new float[TimeSwitchProgram.HOURS];
        float follower;
        float knobFrom;
        float knobTo;
        float knobT = 1.0f;
        float knobV;
        double dial;
        long lastNanos;
        long lastAccess;
    }

    static void render(SwitchBE be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        final BlockState state = be.getBlockState();
        final double hourF = TimeSwitchView.hourF(level, partialTick);
        final int pins = TimeSwitchView.shownPins(be);
        final Mode mode = TimeSwitchView.shownMode(be);
        final int preview = TimeSwitchView.previewFor(be);
        final TimeSwitchDial.Aim aim = TimeSwitchView.updateAim(be, partialTick);
        final Anim anim = advance(be, hourF, preview >= 0 ? preview : pins, pins, mode);
        final float dial = (float) anim.dial;

        ps.pushPose();
        SwitchRenderer.orient(ps, state.getValue(SwitchBlock.FACING));
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));

        drum(vc, ps, light, CU, CV, DIAL_EDGE_R, BODY_H, FACE_H, dial, T_DIAL_EDGE, null);
        ps.pushPose();
        turn(ps, CU, CV, dial);
        print(vc, ps, CU, CV, DIAL_R, FACE_H + HAIRLINE, T_DIAL, light);
        ps.popPose();

        for (int h = 0; h < TimeSwitchProgram.HOURS; h++) {
            final float r0 = OUT_R0 + (IN_R0 - OUT_R0) * smooth(anim.slider[h]);
            final boolean aimed = aim.isPin() && aim.index() == h;
            final boolean changed = preview >= 0
                    && TimeSwitchProgram.isIn(preview, h) != TimeSwitchProgram.isIn(pins, h);
            final int tint = changed ? PREVIEW : WHITE;
            final float lift = aimed ? 0.12f : 0.0f;
            radial(vc, ps, light, CU, CV, r0, r0 + SLIDER_LEN, SLIDER_W * 0.5f, FACE_H - 0.05f,
                    FACE_H + SLIDER_H + lift, dial + (h + 0.5f) * 15.0f,
                    T_SLIDER_END, T_SLIDER_END, T_SLIDER_SIDE, T_SLIDER_TOP, null, tint);
        }

        ps.pushPose();
        final float pz = CV + FOLLOWER_PIVOT_R;
        ps.translate(0.0f, FOLLOWER_PIVOT_H * PX, pz * PX);
        ps.mulPose(Axis.XP.rotationDegrees(-FOLLOWER_DROP * smooth(anim.follower)));
        ps.translate(0.0f, -FOLLOWER_PIVOT_H * PX, -pz * PX);
        box(vc, ps, CU - FOLLOWER_W * 0.5f, FOLLOWER_PIVOT_H - 0.09f, pz - FOLLOWER_LEN,
                CU + FOLLOWER_W * 0.5f, FOLLOWER_PIVOT_H + 0.09f, pz + 0.12f,
                T_FOLLOWER, null, T_FOLLOWER, T_FOLLOWER, T_FOLLOWER, T_FOLLOWER, WHITE, light);
        ps.popPose();

        final float hubTop = HUB_TOP_H + (aim.kind() == TimeSwitchDial.Kind.HUB ? 0.1f : 0.0f);
        drum(vc, ps, light, CU, CV, HUB_R, FACE_H - SEAT, hubTop, dial, T_HUB_SIDE, T_HUB_TOP);

        final float kv = anim.knobV;
        box(vc, ps, (float) TimeSwitchDial.KNOB_U0, (float) TimeSwitchDial.KNOB_H0, kv - (float) TimeSwitchDial.KNOB_HALF,
                (float) TimeSwitchDial.KNOB_U1, (float) TimeSwitchDial.KNOB_H1, kv + (float) TimeSwitchDial.KNOB_HALF,
                T_KNOB_TOP, null, T_KNOB_SIDE, T_KNOB_SIDE, T_KNOB_SIDE, T_KNOB_SIDE, WHITE, light);

        final boolean closed = be.isClosed();
        final int lens = !closed ? LENS_DEAD : be.isCarrying() ? LENS_LIVE : LENS_IDLE;
        box(vc, ps, LENS_U0, LENS_H0, LENS_V0, LENS_U1, LENS_H1, LENS_V1,
                T_LENS, null, T_LENS, T_LENS, T_LENS, T_LENS, lens, closed ? LightTexture.FULL_BRIGHT : light);

        ps.pushPose();
        turn(ps, RUN_U, RUN_V, (float) ((hourF - Math.floor(hourF)) * 360.0));
        print(vc, ps, RUN_U, RUN_V, RUN_R, RUN_H, T_RUNNER, light);
        ps.popPose();

        if (aim.kind() != TimeSwitchDial.Kind.NONE) {
            outline(buffers.getBuffer(RenderType.lines()), ps, aim, anim, dial, mode);
        }
        ps.popPose();
    }

    private static Anim advance(SwitchBE be, double hourF, int shown, int pins, Mode mode) {
        final long now = System.nanoTime();
        final double target = TimeSwitchDial.dialDegrees(hourF);
        final float knobTarget = (float) TimeSwitchDial.detentV(mode);
        final boolean nowIn = TimeSwitchProgram.isIn(pins, (int) Math.floor(hourF) % TimeSwitchProgram.HOURS);
        final Map<BlockPos, Anim> world = ANIMS.computeIfAbsent(be.getLevel(), level -> new HashMap<>());
        final Anim anim = world.computeIfAbsent(be.getBlockPos().immutable(), pos -> {
            final Anim created = new Anim();
            for (int h = 0; h < TimeSwitchProgram.HOURS; h++) {
                created.slider[h] = TimeSwitchProgram.isIn(shown, h) ? 1.0f : 0.0f;
            }
            created.follower = nowIn ? 1.0f : 0.0f;
            created.knobFrom = knobTarget;
            created.knobTo = knobTarget;
            created.knobV = knobTarget;
            created.dial = target;
            created.lastNanos = now;
            return created;
        });

        final double dt = Math.max(0.0D, Math.min(0.08D, (now - anim.lastNanos) / 1_000_000_000.0D));
        anim.lastNanos = now;
        anim.lastAccess = now;
        for (int h = 0; h < TimeSwitchProgram.HOURS; h++) {
            anim.slider[h] = approach(anim.slider[h], TimeSwitchProgram.isIn(shown, h) ? 1.0f : 0.0f,
                    dt / SLIDER_SECONDS);
        }
        anim.follower = approach(anim.follower, nowIn ? 1.0f : 0.0f, dt / FOLLOWER_SECONDS);
        if (anim.knobTo != knobTarget) {
            anim.knobFrom = anim.knobV;
            anim.knobTo = knobTarget;
            anim.knobT = 0.0f;
        }
        anim.knobT = Math.min(1.0f, anim.knobT + (float) (dt / KNOB_SECONDS));
        anim.knobV = anim.knobFrom + (anim.knobTo - anim.knobFrom) * snap(anim.knobT);

        final double diff = wrap180(target - anim.dial);
        final double step = DIAL_CATCHUP_DEG_PER_S * dt;
        anim.dial = Math.abs(diff) <= Math.max(2.0D, step) ? target : anim.dial + Math.copySign(step, diff);
        cleanup(world, now);
        return anim;
    }

    private static double wrap180(double degrees) {
        final double d = degrees - Math.floor((degrees + 180.0D) / 360.0D) * 360.0D;
        return d;
    }

    private static float approach(float value, float target, double step) {
        return value < target ? Math.min(target, value + (float) step) : Math.max(target, value - (float) step);
    }

    private static float smooth(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float snap(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return smooth(t) + 0.16f * (float) Math.sin(t * Math.PI) * (t < 0.5f ? -1.0f : 1.0f);
    }

    private static void cleanup(Map<BlockPos, Anim> world, long now) {
        if (world.size() < 128) {
            return;
        }
        final Iterator<Map.Entry<BlockPos, Anim>> it = world.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastAccess > 30_000_000_000L) {
                it.remove();
            }
        }
    }

    private static void outline(VertexConsumer lines, PoseStack ps, TimeSwitchDial.Aim aim, Anim anim, float dial,
                                Mode mode) {
        ps.pushPose();
        switch (aim.kind()) {
            case PIN -> {
                final int h = aim.index();
                final float r0 = OUT_R0 + (IN_R0 - OUT_R0) * smooth(anim.slider[h]);
                turn(ps, CU, CV, dial + (h + 0.5f) * 15.0f);
                lineBox(lines, ps, CU - SLIDER_W * 0.5f - 0.04f, FACE_H, CV + r0 - 0.04f,
                        CU + SLIDER_W * 0.5f + 0.04f, FACE_H + SLIDER_H + 0.16f, CV + r0 + SLIDER_LEN + 0.04f);
            }
            case MODE -> {
                final Mode target = Mode.byId(aim.index());
                final float v = (float) TimeSwitchDial.detentV(target);
                final float half = target == mode ? (float) TimeSwitchDial.KNOB_HALF + 0.05f : 0.55f;
                lineBox(lines, ps, (float) TimeSwitchDial.KNOB_U0 - 0.04f, (float) TimeSwitchDial.SLIDE_FLOOR_H,
                        v - half, (float) TimeSwitchDial.KNOB_U1 + 0.04f,
                        (float) (target == mode ? TimeSwitchDial.KNOB_H1 + 0.05 : TimeSwitchDial.SLIDE_FLOOR_H + 0.3),
                        v + half);
            }
            case HUB -> lineBox(lines, ps, CU - HUB_R - 0.05f, FACE_H, CV - HUB_R - 0.05f,
                    CU + HUB_R + 0.05f, HUB_TOP_H + 0.15f, CV + HUB_R + 0.05f);
            default -> {
            }
        }
        ps.popPose();
    }

    private static void lineBox(VertexConsumer lines, PoseStack ps, float x0, float y0, float z0,
                                float x1, float y1, float z1) {
        LevelRenderer.renderLineBox(ps, lines, x0 * PX, y0 * PX, z0 * PX, x1 * PX, y1 * PX, z1 * PX,
                1.0f, 1.0f, 1.0f, 0.55f);
    }

    private static void turn(PoseStack ps, float cx, float cz, float clockwiseDegrees) {
        ps.translate(cx * PX, 0.0f, cz * PX);
        ps.mulPose(Axis.YP.rotationDegrees(-clockwiseDegrees));
        ps.translate(-cx * PX, 0.0f, -cz * PX);
    }

    private static void drum(VertexConsumer vc, PoseStack ps, int light, float cx, float cz, float r,
                             float y0, float y1, float spin, Tile side, @Nullable Tile top) {
        final float halfLen = r * COS_16;
        final float halfWid = r * SIN_16;
        final float fa = (r - halfLen) / (2.0f * r), fb = (r + halfLen) / (2.0f * r);
        final float fc = (r - halfWid) / (2.0f * r), fd = (r + halfWid) / (2.0f * r);
        final Tile topBand = top == null ? null : top.sub(fc, fa, fd, fb);
        final int shift = Math.round(spin / 22.5f);
        for (int k = 0; k < 8; k++) {
            final int eOut = Math.floorMod(k + shift, 16);
            final int eIn = Math.floorMod(k + 8 + shift, 16);
            ps.pushPose();
            turn(ps, cx, cz, k * 22.5f + spin);
            box(vc, ps, cx - halfWid, y0 + HAIRLINE * k, cz - halfLen, cx + halfWid, y1 - HAIRLINE * k,
                    cz + halfLen, topBand, null,
                    side.sub(eIn / 16.0f, 0.0f, (eIn + 1) / 16.0f, 1.0f),
                    side.sub(eOut / 16.0f, 0.0f, (eOut + 1) / 16.0f, 1.0f),
                    null, null, WHITE, light);
            ps.popPose();
        }
    }

    private static void radial(VertexConsumer vc, PoseStack ps, int light, float cx, float cz,
                               float r0, float r1, float halfW, float y0, float y1, float clockwise,
                               @Nullable Tile out, @Nullable Tile in, @Nullable Tile sides,
                               @Nullable Tile up, @Nullable Tile down, int argb) {
        ps.pushPose();
        turn(ps, cx, cz, clockwise);
        box(vc, ps, cx - halfW, y0, cz + r0, cx + halfW, y1, cz + r1, up, down, in, out, sides, sides, argb, light);
        ps.popPose();
    }

    private static void print(VertexConsumer vc, PoseStack ps, float cx, float cz, float r, float h, Tile t,
                              int light) {
        final PoseStack.Pose pose = ps.last();
        final float x0 = (cx - r) * PX, x1 = (cx + r) * PX, z0 = (cz - r) * PX, z1 = (cz + r) * PX, y = h * PX;
        vertex(vc, pose, x0, y, z1, t.u1(), t.v0(), light);
        vertex(vc, pose, x1, y, z1, t.u0(), t.v0(), light);
        vertex(vc, pose, x1, y, z0, t.u0(), t.v1(), light);
        vertex(vc, pose, x0, y, z0, t.u1(), t.v1(), light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int light) {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }

    private static void box(VertexConsumer vc, PoseStack ps,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            @Nullable Tile up, @Nullable Tile down, @Nullable Tile north, @Nullable Tile south,
                            @Nullable Tile west, @Nullable Tile east, int argb, int light) {
        final float ax = x0 * PX, ay = y0 * PX, az = z0 * PX;
        final float bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
        if (up != null) {
            face(vc, ps, up, argb, light, Direction.UP, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        }
        if (down != null) {
            face(vc, ps, down, argb, light, Direction.DOWN, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
        }
        if (south != null) {
            face(vc, ps, south, argb, light, Direction.SOUTH, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        }
        if (north != null) {
            face(vc, ps, north, argb, light, Direction.NORTH, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        }
        if (west != null) {
            face(vc, ps, west, argb, light, Direction.WEST, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        }
        if (east != null) {
            face(vc, ps, east, argb, light, Direction.EAST, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
        }
    }

    private static void face(VertexConsumer vc, PoseStack ps, Tile tile, int argb, int light, Direction normal,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        final PoseStack.Pose pose = ps.last();
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final float nx = normal.getStepX(), ny = normal.getStepY(), nz = normal.getStepZ();
        final float[][] p = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        final float[][] uv = {{tile.u0(), tile.v1()}, {tile.u1(), tile.v1()},
                {tile.u1(), tile.v0()}, {tile.u0(), tile.v0()}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(r, g, b, a)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
