package com.createstats.renderer;

import com.createstats.block.RotaryDimmerBlock;
import com.createstats.block.SwitchBlock;
import com.createstats.blockentity.SwitchBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
final class RotaryDimmerRenderer {
    private RotaryDimmerRenderer() {
    }

    private static final ResourceLocation PARTS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/rotary_dimmer_dynamic.png");

    private static final float PX = 1.0f / 16.0f;

    private static final int SHEET = 128;
    private static final int CELL = 32;

    private record Tile(float u0, float v0, float u1, float v1) {
        Tile sub(float fx0, float fy0, float fx1, float fy1) {
            final float w = u1 - u0, h = v1 - v0;
            return new Tile(u0 + w * fx0, v0 + h * fy0, u0 + w * fx1, v0 + h * fy1);
        }
    }

    private static Tile tile(int col, int row) {
        final float inset = 0.5f / SHEET;
        return new Tile(col * CELL / (float) SHEET + inset, row * CELL / (float) SHEET + inset,
                (col + 1) * CELL / (float) SHEET - inset, (row + 1) * CELL / (float) SHEET - inset);
    }

    private static final Tile T_SKIRT_SIDE = tile(0, 0);
    private static final Tile T_SKIRT_TOP = tile(1, 0);
    private static final Tile T_FLUTE = tile(2, 0);
    private static final Tile T_BARREL_SIDE = tile(3, 0);
    private static final Tile T_CAP_TOP = tile(0, 1);
    private static final Tile T_MARK = tile(1, 1);
    private static final Tile T_LENS = tile(2, 1);
    private static final Tile T_SEG_OFF = tile(3, 1);
    private static final Tile T_PUCK_TOP = tile(0, 2);
    private static final Tile T_PUCK_SIDE = tile(1, 2);
    private static final Tile T_PUCK_CAP = tile(2, 2);
    private static final Tile T_MARK_DARK = tile(3, 2);
    private static final Tile T_RING_OFF = tile(0, 3);
    private static final Tile T_BARREL_TOP = tile(1, 3);
    private static final Tile T_RING_LIT = tile(2, 3);
    private static final Tile T_SEG_LIT = tile(3, 3);

    private static final int LEVELS = RotaryDimmerBlock.LEVELS;

    private static final float SEAT = 0.10f;

    private static final float HAIRLINE = 0.006f;
    private static final float COS_16 = (float) Math.cos(Math.PI / 16.0);
    private static final float SIN_16 = (float) Math.sin(Math.PI / 16.0);

    private static final float DIAL_CX = 10.2f, DIAL_CZ = 8.0f;
    private static final float SKIRT_R = 3.5f, SKIRT_H0 = 1.70f, SKIRT_H1 = 2.60f;
    private static final int FLUTES = 8;
    private static final float BARREL_R = 2.3f, BARREL_H0 = 2.60f, BARREL_H1 = 4.80f;
    private static final float CAP_R = 2.0f, CAP_H1 = 5.10f;
    private static final float DIAL_TRAVEL = 0.60f;
    private static final float WIN_U0 = 2.2f, WIN_U1 = 4.6f;

    private static final float TICK_U = 5.5f;
    private static final float SEG_W = 2.1f, SEG_D = 0.64f, SEG_H = 0.20f, SEG_PITCH = 0.95f;
    private static final float SEG_V0 = 3.55f, SEG_H0 = 1.60f;
    private static final float LENS_W = 2.6f, LENS_D = 0.9f, LENS_H = 0.34f;
    private static final float LENS_U = 8.0f, LENS_SEAT_H = 2.0f, LENS_V = 1.45f;

    private static final float PUCK_CX = 8.0f, PUCK_CZ = 8.0f;
    private static final float PUCK_R = 4.8f, PUCK_H0 = 1.40f, PUCK_H1 = 2.50f;
    private static final float PUCK_CAP_R = 4.5f, PUCK_CAP_H1 = 2.62f;
    private static final float PUCK_TRAVEL = 0.60f;
    private static final float RING_R0 = 5.05f, RING_R1 = 5.55f, RING_H0 = 0.76f, RING_H1 = 0.92f;
    private static final float RING_ARC = 270.0f;

    private static final int WHITE = 0xFFFFFFFF;

    private static final int AMBER = 0xFFE0A63C;
    private static final int LENS_DEAD = 0xFF1E2E22;
    private static final int LENS_IDLE = 0xFFE0A63C;
    private static final int LENS_LIVE = 0xFF6BE39A;

    private static final int RING_LOW = 0xFFE88A30;
    private static final int RING_HIGH = 0xFFFFF0D0;

    private static final double NOTCH_SECONDS = 0.11D;
    private static final double PRESS_SECONDS = 0.06D;

    private static final Map<BlockPos, Anim> ANIMS = new HashMap<>();

    private static final class Anim {
        float from;
        float to;
        float t = 1.0f;

        float angle;

        float press;
        long lastNanos;
        long lastAccess;
    }

    static void render(SwitchBE be, RotaryDimmerBlock block, PoseStack ps, MultiBufferSource buffers,
                       int light) {
        final BlockState state = be.getBlockState();
        final boolean on = state.getValue(SwitchBlock.POWERED);
        final Anim anim = advance(be, on, block.kind());

        final int lit = on ? be.dimLevel() + 1 : 0;

        ps.pushPose();
        SwitchRenderer.orient(ps, state.getValue(SwitchBlock.FACING));
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        switch (block.kind()) {
            case DIAL -> dial(vc, ps, light, anim.angle, DIAL_TRAVEL * smooth(anim.press), lit, on,
                    be.isCarrying());
            case PUCK -> puck(vc, ps, light, anim.angle, PUCK_TRAVEL * smooth(anim.press), lit);
        }
        ps.popPose();
    }

    private static float aim(RotaryDimmerBlock.Kind kind, int level) {
        if (kind == RotaryDimmerBlock.Kind.PUCK) {
            final float span = RING_ARC / LEVELS;
            return -RING_ARC / 2.0f + span / 2.0f + span * level;
        }
        final float du = TICK_U - DIAL_CX;
        final float dv = SEG_V0 + SEG_PITCH * level - DIAL_CZ;
        return (float) Math.toDegrees(Math.atan2(-du, dv));
    }

    private static Anim advance(SwitchBE be, boolean on, RotaryDimmerBlock.Kind kind) {
        final long now = System.nanoTime();
        final float target = aim(kind, be.dimLevel());
        final Anim anim = ANIMS.computeIfAbsent(be.getBlockPos().immutable(), pos -> {
            final Anim created = new Anim();
            created.from = target;
            created.to = target;
            created.angle = target;
            created.press = on ? 0.0f : 1.0f;
            created.lastNanos = now;
            return created;
        });

        final double dt = Math.max(0.0D, Math.min(0.08D, (now - anim.lastNanos) / 1_000_000_000.0D));
        anim.lastNanos = now;
        anim.lastAccess = now;
        if (anim.to != target) {
            anim.from = anim.angle;
            anim.to = target;
            anim.t = 0.0f;
        }
        anim.t = Math.min(1.0f, anim.t + (float) (dt / NOTCH_SECONDS));
        anim.angle = anim.from + (anim.to - anim.from) * snap(anim.t);
        anim.press = approach(anim.press, on ? 0.0f : 1.0f, dt / PRESS_SECONDS);
        cleanup(now);
        return anim;
    }

    private static float approach(float value, float target, double step) {
        return value < target ? Math.min(target, value + (float) step)
                : Math.max(target, value - (float) step);
    }

    private static float smooth(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float snap(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return smooth(t) + 0.16f * (float) Math.sin(t * Math.PI) * (t < 0.5f ? -1.0f : 1.0f);
    }

    private static void cleanup(long now) {
        if (ANIMS.size() < 192) {
            return;
        }
        final Iterator<Map.Entry<BlockPos, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastAccess > 30_000_000_000L) {
                it.remove();
            }
        }
    }

    private static void dial(VertexConsumer vc, PoseStack ps, int light, float angle, float drop,
                             int lit, boolean on, boolean carrying) {
        final float cx = DIAL_CX, cz = DIAL_CZ;
        drum(vc, ps, light, cx, cz, SKIRT_R, SKIRT_H0 - SEAT, SKIRT_H1, angle,
                T_SKIRT_SIDE, T_SKIRT_TOP, T_SKIRT_TOP);
        for (int k = 0; k < FLUTES; k++) {
            radial(vc, ps, light, cx, cz, SKIRT_R - 0.15f, SKIRT_R + 0.25f, 0.45f,
                    SKIRT_H0 + 0.15f, SKIRT_H1 - 0.10f, angle + 22.5f + k * (360.0f / FLUTES),
                    T_FLUTE.sub(0.0f, 0.0f, 0.5f, 1.0f), null, T_FLUTE.sub(0.5f, 0.0f, 1.0f, 1.0f),
                    T_SKIRT_TOP.sub(0.4f, 0.05f, 0.6f, 0.2f), null, WHITE);
        }
        drum(vc, ps, light, cx, cz, BARREL_R, BARREL_H0 - drop - SEAT, BARREL_H1 - drop, angle,
                T_BARREL_SIDE, T_BARREL_TOP, T_BARREL_TOP);
        drum(vc, ps, light, cx, cz, CAP_R, BARREL_H1 - drop - SEAT, CAP_H1 - drop, angle,
                T_BARREL_SIDE, T_CAP_TOP, T_CAP_TOP);

        radial(vc, ps, light, cx, cz, 0.4f, CAP_R - 0.1f, 0.25f, CAP_H1 - drop, CAP_H1 - drop + 0.06f,
                angle, T_MARK, null, T_MARK, T_MARK, null, WHITE);
        radial(vc, ps, light, cx, cz, BARREL_R - 0.1f, BARREL_R + 0.06f, 0.25f,
                BARREL_H0 - drop + 0.3f, BARREL_H1 - drop, angle, T_MARK, null, T_MARK, T_MARK, null, WHITE);

        final float u0 = (WIN_U0 + WIN_U1) * 0.5f - SEG_W * 0.5f;
        final float u1 = u0 + SEG_W;
        for (int i = 0; i < LEVELS; i++) {
            final boolean isLit = i < lit;
            final float v = SEG_V0 + SEG_PITCH * i;
            final Tile t = isLit ? T_SEG_LIT : T_SEG_OFF;
            box(vc, ps, u0, SEG_H0, v - SEG_D * 0.5f, u1, SEG_H0 + SEG_H, v + SEG_D * 0.5f,
                    t, null, t, t, t, t, isLit ? AMBER : WHITE, isLit ? LightTexture.FULL_BRIGHT : light);
        }

        final int pilot = !on ? LENS_DEAD : carrying ? LENS_LIVE : LENS_IDLE;
        box(vc, ps, LENS_U - LENS_W * 0.5f, LENS_SEAT_H - SEAT, LENS_V - LENS_D * 0.5f,
                LENS_U + LENS_W * 0.5f, LENS_SEAT_H + LENS_H, LENS_V + LENS_D * 0.5f,
                T_LENS, null, T_LENS, T_LENS, T_LENS, T_LENS, pilot, LightTexture.FULL_BRIGHT);
    }

    private static void puck(VertexConsumer vc, PoseStack ps, int light, float angle, float drop, int lit) {
        final float cx = PUCK_CX, cz = PUCK_CZ;
        drum(vc, ps, light, cx, cz, PUCK_R, PUCK_H0 - drop, PUCK_H1 - drop, angle,
                T_PUCK_SIDE, T_PUCK_TOP, T_SKIRT_TOP);
        drum(vc, ps, light, cx, cz, PUCK_CAP_R, PUCK_H1 - drop - SEAT, PUCK_CAP_H1 - drop, angle,
                T_PUCK_SIDE, T_PUCK_CAP, T_PUCK_CAP);
        radial(vc, ps, light, cx, cz, PUCK_CAP_R - 1.0f, PUCK_CAP_R - 0.1f, 0.2f,
                PUCK_CAP_H1 - drop, PUCK_CAP_H1 - drop + 0.05f, angle,
                T_MARK_DARK, null, T_MARK_DARK, T_MARK_DARK, null, WHITE);

        final float span = RING_ARC / LEVELS;
        final float halfW = RING_R1 * (float) Math.tan(Math.toRadians(span / 2.0)) + 0.04f;
        for (int i = 0; i < LEVELS; i++) {
            final boolean isLit = i < lit;
            final Tile t = isLit ? T_RING_LIT : T_RING_OFF;
            final float a = -RING_ARC / 2.0f + span / 2.0f + span * i;
            radial(vc, ps, isLit ? LightTexture.FULL_BRIGHT : light, cx, cz, RING_R0, RING_R1, halfW,
                    RING_H0, RING_H1 + 0.002f * i, a, t, t, t, t, null, isLit ? ringColour(i) : WHITE);
        }
    }

    private static int ringColour(int i) {
        final float t = i / (float) (LEVELS - 1);
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            final int lo = (RING_LOW >> shift) & 0xFF;
            final int hi = (RING_HIGH >> shift) & 0xFF;
            out |= Math.round(lo + (hi - lo) * t) << shift;
        }
        return out;
    }

    private static void turn(PoseStack ps, float cx, float cz, float clockwiseDegrees) {
        ps.translate(cx * PX, 0.0f, cz * PX);
        ps.mulPose(Axis.YP.rotationDegrees(-clockwiseDegrees));
        ps.translate(-cx * PX, 0.0f, -cz * PX);
    }

    private static void drum(VertexConsumer vc, PoseStack ps, int light, float cx, float cz, float r,
                             float y0, float y1, float spin, Tile side, Tile top, Tile bottom) {
        final float halfLen = r * COS_16;
        final float halfWid = r * SIN_16;
        final float fa = (r - halfLen) / (2.0f * r), fb = (r + halfLen) / (2.0f * r);
        final float fc = (r - halfWid) / (2.0f * r), fd = (r + halfWid) / (2.0f * r);
        final Tile topBand = top.sub(fc, fa, fd, fb);
        final Tile bottomBand = bottom.sub(fc, fa, fd, fb);
        final int shift = Math.round(spin / 22.5f);
        for (int k = 0; k < 8; k++) {
            final int eOut = Math.floorMod(k + shift, 16);
            final int eIn = Math.floorMod(k + 8 + shift, 16);
            ps.pushPose();
            turn(ps, cx, cz, k * 22.5f + spin);
            box(vc, ps, cx - halfWid, y0 + HAIRLINE * k, cz - halfLen, cx + halfWid, y1 - HAIRLINE * k,
                    cz + halfLen, topBand, bottomBand,
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
        box(vc, ps, cx - halfW, y0, cz + r0, cx + halfW, y1, cz + r1,
                up, down, in, out, sides, sides, argb, light);
        ps.popPose();
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

    private static void face(VertexConsumer vc, PoseStack ps, Tile tile, int argb, int light,
                             Direction normal,
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
