package com.createstats.renderer;

import com.createstats.block.SwitchBlock;
import com.createstats.blockentity.SwitchBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class SwitchRenderer implements net.minecraft.client.renderer.blockentity.BlockEntityRenderer<SwitchBE> {
    private static final ResourceLocation PARTS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/switch_dynamic.png");

    private static final float PX = 1.0f / 16.0f;

    private record Tile(float u0, float v0, float u1, float v1) {
    }

    private static final int TILE_COLS = 4;
    private static final int TILE_ROWS = 3;

    private static Tile tile(int col, int row) {
        final float uInset = 0.5f / (TILE_COLS * 16.0f);
        final float vInset = 0.5f / (TILE_ROWS * 16.0f);
        return new Tile(col / (float) TILE_COLS + uInset, row / (float) TILE_ROWS + vInset,
                (col + 1) / (float) TILE_COLS - uInset, (row + 1) / (float) TILE_ROWS - vInset);
    }

    private static final Tile T_PADDLE = tile(0, 0);
    private static final Tile T_PADDLE_EDGE = tile(1, 0);
    private static final Tile T_PADDLE_BACK = tile(2, 0);
    private static final Tile T_LENS = tile(3, 0);
    private static final Tile T_DOLLY = tile(0, 1);
    private static final Tile T_BALL = tile(1, 1);
    private static final Tile T_NUT = tile(2, 1);
    private static final Tile T_MARK = tile(3, 1);
    private static final Tile T_HANDLE = tile(0, 2);
    private static final Tile T_GRIP = tile(1, 2);
    private static final Tile T_CAP = tile(2, 2);
    private static final Tile T_CAP_SIDE = tile(3, 2);

    private static final float PADDLE_V0 = 4.10f, PADDLE_V1 = 11.90f;
    private static final float PADDLE_H0 = 1.80f, PADDLE_H1 = 2.62f;
    private static final float PADDLE_PIVOT_V = 8.00f, PADDLE_PIVOT_H = 2.21f;
    private static final float PADDLE_TILT = 8.5f;

    private static final float[][] ROCKER_GANGS = {{5.00f, 11.00f}};
    private static final float[][] DOUBLE_GANGS = {{3.50f, 7.20f}, {8.80f, 12.50f}};

    private static final float DOLLY_H0 = 2.50f, DOLLY_H1 = 5.00f, DOLLY_H2 = 6.40f;
    private static final float DOLLY_PIVOT_V = 8.00f, DOLLY_PIVOT_H = 2.30f;
    private static final float DOLLY_THROW = 30.0f;

    private static final float ISO_H0 = 4.80f, ISO_H1 = 5.90f;
    private static final float ISO_PIVOT_U = 8.00f, ISO_PIVOT_V = 8.00f;

    private static final float CAP_H0 = 2.20f, CAP_H1 = 4.00f, CROWN_H1 = 4.50f;

    private static final float STOP_TRAVEL = 0.80f;

    private static final float LENS_W = 2.60f, LENS_D = 0.90f, LENS_H = 0.34f;
    private static final float RIM_TOP = 1.70f;

    private static final float SEAT = 0.10f;

    private static final double PADDLE_SECONDS = 0.09D;
    private static final double DOLLY_SECONDS = 0.13D;
    private static final double ISOLATOR_SECONDS = 0.30D;
    private static final double PRESS_SECONDS = 0.05D;
    private static final double RELEASE_SECONDS = 0.26D;

    private static final int LENS_DEAD = 0xFF1E2E22;

    private static final int LENS_IDLE = 0xFFE0A63C;

    private static final int LENS_LIVE = 0xFF6BE39A;

    private static final Map<BlockPos, Anim> ANIMS = new HashMap<>();

    private static final class Anim {
        float a;
        float b;

        boolean returning;
        long lastNanos;
        long lastAccess;
    }

    public SwitchRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SwitchBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof SwitchBlock)) {
            return;
        }

        if (state.getBlock() instanceof com.createstats.block.TimeSwitchBlock) {
            TimeSwitchRenderer.render(be, partialTick, ps, buffers, packedLight);
            return;
        }
        if (state.getBlock() instanceof com.createstats.block.RotaryDimmerBlock dimmer) {
            RotaryDimmerRenderer.render(be, dimmer, ps, buffers, packedLight);
            return;
        }
        final Kind kind = kindOf(state);
        if (kind == null) {
            return;
        }
        final Anim anim = advance(be, state);

        ps.pushPose();

        orient(ps, state.getValue(SwitchBlock.FACING));

        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        switch (kind) {
            case ROCKER -> {
                paddles(vc, ps, packedLight, ROCKER_GANGS, new float[]{anim.a});
                lens(vc, ps, 8.00f, RIM_TOP, 2.80f, pilot(be, state.getValue(SwitchBlock.POWERED)));
            }
            case DOUBLE -> {
                paddles(vc, ps, packedLight, DOUBLE_GANGS, new float[]{anim.a, anim.b});

                final boolean live = be.isCarrying();
                lens(vc, ps, 5.35f, RIM_TOP, 2.80f, pilotOf(state.getValue(SwitchBlock.POWERED), live));
                lens(vc, ps, 10.65f, RIM_TOP, 2.80f, pilotOf(state.getValue(SwitchBlock.POWERED_B), live));
            }
            case TOGGLE -> {
                dolly(vc, ps, packedLight, anim.a);
                lens(vc, ps, 8.00f, 1.60f, 3.70f, pilot(be, state.getValue(SwitchBlock.POWERED)));
            }
            case ISOLATOR -> {
                isolatorHandle(vc, ps, packedLight, anim.a);
                lens(vc, ps, 5.10f, 3.80f, 10.60f, pilot(be, state.getValue(SwitchBlock.POWERED)));
            }
            case BUTTON -> {
                stopHead(vc, ps, packedLight, anim);

                stopRing(vc, ps, pilot(be, state.getValue(SwitchBlock.POWERED)));
            }
            default -> {
            }
        }
        ps.popPose();
    }

    private enum Kind {ROCKER, DOUBLE, TOGGLE, ISOLATOR, BUTTON}

    private static Kind kindOf(BlockState state) {
        final net.minecraft.world.level.block.Block block = state.getBlock();
        if (block == com.createstats.registry.ModBlocks.ROCKER_SWITCH.get()) {
            return Kind.ROCKER;
        }
        if (block == com.createstats.registry.ModBlocks.DOUBLE_SWITCH.get()) {
            return Kind.DOUBLE;
        }
        if (block == com.createstats.registry.ModBlocks.TOGGLE_SWITCH.get()) {
            return Kind.TOGGLE;
        }
        if (block == com.createstats.registry.ModBlocks.ISOLATOR_SWITCH.get()) {
            return Kind.ISOLATOR;
        }
        if (block == com.createstats.registry.ModBlocks.PUSH_BUTTON.get()) {
            return Kind.BUTTON;
        }
        return null;
    }

    static void orient(PoseStack ps, Direction facing) {
        final float[] rot = com.createstats.block.MountedPart.rotationDegrees(facing);
        ps.translate(0.5, 0.5, 0.5);
        if (rot[0] != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(rot[0]));
        }
        if (rot[1] != 0.0f) {
            ps.mulPose(Axis.XP.rotationDegrees(rot[1]));
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    private static Anim advance(SwitchBE be, BlockState state) {
        final long now = System.nanoTime();
        final boolean wantA = state.getValue(SwitchBlock.POWERED);
        final boolean wantB = state.getValue(SwitchBlock.POWERED_B);
        final Anim anim = ANIMS.computeIfAbsent(be.getBlockPos().immutable(), pos -> {
            final Anim created = new Anim();
            created.a = wantA ? 1.0f : 0.0f;
            created.b = wantB ? 1.0f : 0.0f;
            created.lastNanos = now;
            return created;
        });

        final double dt = Math.max(0.0D, Math.min(0.08D, (now - anim.lastNanos) / 1_000_000_000.0D));
        anim.lastNanos = now;
        anim.lastAccess = now;

        final double seconds = switch (kindOf(state) == null ? Kind.ROCKER : kindOf(state)) {
            case TOGGLE -> DOLLY_SECONDS;
            case ISOLATOR -> ISOLATOR_SECONDS;

            case BUTTON -> wantA ? PRESS_SECONDS : RELEASE_SECONDS;
            default -> PADDLE_SECONDS;
        };
        anim.returning = !wantA && anim.a > 0.001f;
        anim.a = approach(anim.a, wantA ? 1.0f : 0.0f, dt / seconds);
        anim.b = approach(anim.b, wantB ? 1.0f : 0.0f, dt / PADDLE_SECONDS);
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

    private static void paddles(VertexConsumer vc, PoseStack ps, int light, float[][] gangs,
                                float[] states) {
        for (int i = 0; i < gangs.length; i++) {
            final float t = snap(states[Math.min(i, states.length - 1)]);

            final float tilt = PADDLE_TILT * (2.0f * t - 1.0f);
            ps.pushPose();
            ps.translate(0.0f, PADDLE_PIVOT_H * PX, PADDLE_PIVOT_V * PX);
            ps.mulPose(Axis.XP.rotationDegrees(tilt));
            ps.translate(0.0f, -PADDLE_PIVOT_H * PX, -PADDLE_PIVOT_V * PX);
            box(vc, ps, gangs[i][0], PADDLE_H0, PADDLE_V0, gangs[i][1], PADDLE_H1, PADDLE_V1,
                    T_PADDLE, T_PADDLE_BACK, T_PADDLE_EDGE, T_PADDLE_EDGE, 0xFFFFFFFF, light);
            ps.popPose();
        }
    }

    private static void dolly(VertexConsumer vc, PoseStack ps, int light, float state) {
        final float angle = DOLLY_THROW * (2.0f * snap(state) - 1.0f);
        ps.pushPose();
        ps.translate(0.0f, DOLLY_PIVOT_H * PX, DOLLY_PIVOT_V * PX);
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        ps.translate(0.0f, -DOLLY_PIVOT_H * PX, -DOLLY_PIVOT_V * PX);
        box(vc, ps, 7.3f, DOLLY_H0, 7.3f, 8.7f, DOLLY_H1, 8.7f,
                T_DOLLY, T_DOLLY, T_DOLLY, T_NUT, 0xFFFFFFFF, light);
        box(vc, ps, 6.9f, DOLLY_H1, 6.9f, 9.1f, DOLLY_H2, 9.1f,
                T_BALL, T_BALL, T_BALL, T_BALL, 0xFFFFFFFF, light);
        ps.popPose();
    }

    private static void isolatorHandle(VertexConsumer vc, PoseStack ps, int light, float state) {
        final float angle = 90.0f * (1.0f - smooth(state));
        ps.pushPose();
        ps.translate(ISO_PIVOT_U * PX, 0.0f, ISO_PIVOT_V * PX);
        ps.mulPose(Axis.YP.rotationDegrees(angle));
        ps.translate(-ISO_PIVOT_U * PX, 0.0f, -ISO_PIVOT_V * PX);

        box(vc, ps, 7.3f, ISO_H0 - SEAT, 7.4f, 8.7f, ISO_H1, 11.4f,
                T_HANDLE, T_HANDLE, T_HANDLE, T_HANDLE, 0xFFFFFFFF, light);
        box(vc, ps, 6.9f, ISO_H0 - SEAT, 11.4f, 9.1f, ISO_H1 + 0.40f, 12.40f,
                T_GRIP, T_GRIP, T_GRIP, T_GRIP, 0xFFFFFFFF, light);

        box(vc, ps, 7.75f, ISO_H1, 7.60f, 8.25f, ISO_H1 + 0.06f, 11.20f,
                T_MARK, T_MARK, T_MARK, T_MARK, 0xFFFFFFFF, light);
        ps.popPose();
    }

    private static void stopHead(VertexConsumer vc, PoseStack ps, int light, Anim anim) {
        final float press = smooth(anim.a);
        final float drop = STOP_TRAVEL * press;
        final float twist = anim.returning ? 26.0f * (float) Math.sin(press * Math.PI) : 0.0f;
        ps.pushPose();
        ps.translate(0.0f, -drop * PX, 0.0f);
        ps.translate(8.0f * PX, 0.0f, 8.0f * PX);
        ps.mulPose(Axis.YP.rotationDegrees(twist));
        ps.translate(-8.0f * PX, 0.0f, -8.0f * PX);
        box(vc, ps, 5.6f, CAP_H0, 5.6f, 10.4f, CAP_H1, 10.4f,
                T_CAP_SIDE, T_CAP_SIDE, T_CAP_SIDE, T_CAP, 0xFFFFFFFF, light);
        box(vc, ps, 6.2f, CAP_H1, 6.2f, 9.8f, CROWN_H1, 9.8f,
                T_CAP_SIDE, T_CAP_SIDE, T_CAP_SIDE, T_CAP, 0xFFFFFFFF, light);
        ps.popPose();
    }

    private static void stopRing(VertexConsumer vc, PoseStack ps, int colour) {
        final float y0 = 1.30f - SEAT, y1 = 1.62f;
        final float o0 = 4.90f, o1 = 11.10f, w = 0.55f;
        band(vc, ps, o0, y0, o0, o1, y1, o0 + w, colour);
        band(vc, ps, o0, y0, o1 - w, o1, y1, o1, colour);
        band(vc, ps, o0, y0, o0 + w, o0 + w, y1, o1 - w, colour);
        band(vc, ps, o1 - w, y0, o0 + w, o1, y1, o1 - w, colour);
    }

    private static void band(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0,
                             float x1, float y1, float z1, int colour) {
        box(vc, ps, x0, y0, z0, x1, y1, z1, T_LENS, T_LENS, T_LENS, T_LENS,
                colour, LightTexture.FULL_BRIGHT);
    }

    private static void lens(VertexConsumer vc, PoseStack ps, float u, float h, float v, int colour) {
        box(vc, ps, u - LENS_W * 0.5f, h - SEAT, v - LENS_D * 0.5f, u + LENS_W * 0.5f, h + LENS_H,
                v + LENS_D * 0.5f, T_LENS, T_LENS, T_LENS, T_LENS, colour, LightTexture.FULL_BRIGHT);
    }

    private static int pilot(SwitchBE be, boolean closed) {
        return pilotOf(closed, be.isCarrying());
    }

    private static int pilotOf(boolean closed, boolean carrying) {
        if (!closed) {
            return LENS_DEAD;
        }
        return carrying ? LENS_LIVE : LENS_IDLE;
    }

    private static void box(VertexConsumer vc, PoseStack ps,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            Tile top, Tile bottom, Tile side, Tile end, int argb, int light) {
        final float ax = x0 * PX, ay = y0 * PX, az = z0 * PX;
        final float bx = x1 * PX, by = y1 * PX, bz = z1 * PX;

        face(vc, ps, top, argb, light, Direction.UP,
                ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        face(vc, ps, bottom, argb, light, Direction.DOWN,
                ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
        face(vc, ps, end, argb, light, Direction.SOUTH,
                ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        face(vc, ps, end, argb, light, Direction.NORTH,
                bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        face(vc, ps, side, argb, light, Direction.WEST,
                ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        face(vc, ps, side, argb, light, Direction.EAST,
                bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
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
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SwitchBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.35);
    }
}
