package com.createstats.renderer;

import com.createstats.block.LampBlock;
import com.createstats.blockentity.LampBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
public class LampRenderer implements BlockEntityRenderer<LampBE> {
    private static final ResourceLocation DIFFUSER =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_diffuser_on.png");
    private static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");

    private static final float PX = 1.0f / 16.0f;

    private static final float DARK = 0.02f;

    private static final float SEAT = 0.06f;

    private static final float[] HALO_INFLATE = {0.35f, 0.95f};
    private static final float[] HALO_ALPHA = {0.42f, 0.18f};

    private static final float OFF_MIX = 0.42f;

    private static final double INCANDESCENT_RISE = 0.30D;
    private static final double INCANDESCENT_FALL = 0.90D;
    private static final double FLUORESCENT_STRIKE = 0.95D;
    private static final double FLUORESCENT_FALL = 0.10D;
    private static final double HID_STRIKE = 2.60D;
    private static final double LED_SNAP = 0.05D;

    private static final double PHASE_MAX = 4.0D;

    private static final Map<BlockPos, Anim> ANIMS = new HashMap<>();

    private static final class Anim {
        float phase;
        boolean lit;
        long lastNanos;
        long lastAccess;
    }

    public LampRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LampBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof LampBlock)) {
            return;
        }
        final LampGlow.Glow glow = LampGlow.of(state.getBlock());
        if (glow == null) {
            return;
        }
        final Anim anim = advance(be, state.getValue(LampBlock.LIT));

        final float level = intensity(glow.family(), anim) * LampBlock.dimFraction(state);
        final int dye = state.getValue(LampBlock.COLOR).getTextureDiffuseColor();

        ps.pushPose();
        orient(ps, state.getValue(LampBlock.FACING), LampBlock.spin(state));

        final int tint = glassColour(dye, glow.family(), level);
        final int light = blend(packedLight, level);
        final VertexConsumer glass = buffers.getBuffer(RenderType.entityCutoutNoCull(DIFFUSER));
        for (LampGlow.Box b : glow.boxes()) {
            box(glass, ps, b.u0() - SEAT, b.h0() - SEAT, b.v0() - SEAT,
                    b.u1() + SEAT, b.h1() + SEAT, b.v1() + SEAT, tint, light);
        }
        if (level > DARK) {
            halo(buffers, ps, glow, dye, glow.family(), level);
        }
        ps.popPose();
    }

    private static Anim advance(LampBE be, boolean lit) {
        final long now = System.nanoTime();
        final Anim anim = ANIMS.computeIfAbsent(be.getBlockPos().immutable(), pos -> {
            final Anim created = new Anim();
            created.lit = lit;
            created.phase = 1.0f;
            created.lastNanos = now;
            return created;
        });

        final double dt = Math.max(0.0D, Math.min(0.08D, (now - anim.lastNanos) / 1_000_000_000.0D));
        anim.lastNanos = now;
        anim.lastAccess = now;
        if (lit != anim.lit) {
            anim.lit = lit;
            anim.phase = 0.0f;
        }

        anim.phase = (float) Math.min(PHASE_MAX, anim.phase + dt);
        cleanup(now);
        return anim;
    }

    private static float intensity(LampGlow.Family family, Anim anim) {
        final double t = anim.phase;
        if (!anim.lit) {
            return switch (family) {
                case INCANDESCENT -> (float) Math.pow(Math.max(0.0D, 1.0D - t / INCANDESCENT_FALL), 2.4D);
                case FLUORESCENT -> (float) Math.max(0.0D, 1.0D - t / FLUORESCENT_FALL);

                case HID -> t < 0.02D ? 1.0f - (float) (t / 0.02D) : 0.0f;
                case LED -> (float) Math.max(0.0D, 1.0D - t / LED_SNAP);
            };
        }
        return switch (family) {
            case INCANDESCENT -> {
                final double p = Math.min(1.0D, t / INCANDESCENT_RISE);

                yield (float) (1.0D - Math.pow(1.0D - p, 2.2D));
            }
            case FLUORESCENT -> {
                if (t < 0.06D) {
                    yield 0.0f;
                }

                if (t < 0.13D) {
                    yield 0.85f;
                }
                if (t < 0.30D) {
                    yield 0.04f;
                }
                if (t < 0.40D) {
                    yield 0.92f;
                }
                if (t < 0.52D) {
                    yield 0.10f;
                }
                final double p = Math.min(1.0D, (t - 0.52D) / (FLUORESCENT_STRIKE - 0.52D));

                final double ripple = 1.0D - 0.025D * Math.sin(t * 41.0D);
                yield (float) Math.min(1.0D, (0.55D + 0.45D * p) * ripple);
            }
            case HID -> {
                final double p = Math.min(1.0D, t / HID_STRIKE);

                final double base = 0.10D + 0.90D * (p * p * (3.0D - 2.0D * p));
                final double unstable = Math.max(0.0D, 1.0D - p * 2.4D);
                final double flicker = 1.0D - unstable * 0.34D * Math.abs(Math.sin(t * 23.0D));
                yield (float) Math.max(0.0D, Math.min(1.0D, base * flicker));
            }
            case LED -> (float) Math.min(1.0D, t / LED_SNAP);
        };
    }

    private static int glassColour(int dye, LampGlow.Family family, float level) {
        if (level <= DARK) {
            return 0xFF000000 | (lerp(0x6E, (dye >> 16) & 0xFF, OFF_MIX) << 16)
                    | (lerp(0x72, (dye >> 8) & 0xFF, OFF_MIX) << 8)
                    | lerp(0x76, dye & 0xFF, OFF_MIX);
        }
        final int r = (dye >> 16) & 0xFF;
        final int g = (dye >> 8) & 0xFF;
        final int b = dye & 0xFF;

        final float cold = 1.0f - level;
        final int wr;
        final int wg;
        final int wb;
        switch (family) {
            case INCANDESCENT -> {
                wr = lerp(r, 0xFF, cold * 0.30f);
                wg = lerp(g, 0x92, cold * 0.55f);
                wb = lerp(b, 0x30, cold * 0.75f);
            }
            case HID -> {
                wr = lerp(r, 0x74, cold * 0.72f);
                wg = lerp(g, 0xE6, cold * 0.45f);
                wb = lerp(b, 0xD2, cold * 0.55f);
            }

            default -> {
                wr = r;
                wg = g;
                wb = b;
            }
        }

        final float scale = 0.24f + 0.76f * level;
        return 0xFF000000 | (Math.round(wr * scale) << 16) | (Math.round(wg * scale) << 8)
                | Math.round(wb * scale);
    }

    private static int blend(int packedLight, float level) {
        final int block = LightTexture.block(packedLight);
        final int sky = LightTexture.sky(packedLight);
        return LightTexture.pack(Math.max(block, Math.round(15.0f * level)), sky);
    }

    private static int lerp(int from, int to, float t) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * Math.max(0.0f, Math.min(1.0f, t)))));
    }

    // not lightning(), it binds no texture and every lamp got a hard glowing cube
    private static void halo(MultiBufferSource buffers, PoseStack ps, LampGlow.Glow glow, int dye,
                             LampGlow.Family family, float level) {
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        final int colour = glassColour(dye, family, level);
        for (int shell = 0; shell < HALO_INFLATE.length; shell++) {
            final float grow = HALO_INFLATE[shell];

            final int alpha = Math.round(255.0f * HALO_ALPHA[shell] * level * level);
            if (alpha <= 2) {
                continue;
            }
            final int argb = (alpha << 24) | (colour & 0x00FFFFFF);
            for (LampGlow.Box b : glow.boxes()) {
                box(vc, ps, b.u0() - grow, b.h0() - Math.min(grow, b.h0()), b.v0() - grow,
                        b.u1() + grow, b.h1() + grow, b.v1() + grow, argb, LightTexture.FULL_BRIGHT);
            }
        }
    }

    private static void orient(PoseStack ps, Direction facing, int spin) {
        final float[] rot = com.createstats.block.MountedPart.rotationDegrees(facing);
        ps.translate(0.5, 0.5, 0.5);
        if (rot[0] != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(rot[0]));
        }
        if (rot[1] != 0.0f) {
            ps.mulPose(Axis.XP.rotationDegrees(rot[1]));
        }
        final float turn = com.createstats.block.MountedPart.spinDegrees(spin);
        if (turn != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(turn));
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    private static void box(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0,
                            float x1, float y1, float z1, int argb, int light) {
        final float ax = x0 * PX, ay = y0 * PX, az = z0 * PX;
        final float bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
        face(vc, ps, argb, light, Direction.SOUTH, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        face(vc, ps, argb, light, Direction.NORTH, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        face(vc, ps, argb, light, Direction.WEST, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        face(vc, ps, argb, light, Direction.EAST, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
        face(vc, ps, argb, light, Direction.UP, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        face(vc, ps, argb, light, Direction.DOWN, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
    }

    private static void face(VertexConsumer vc, PoseStack ps, int argb, int light, Direction normal,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        final PoseStack.Pose pose = ps.last();
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final float[][] p = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        final float[][] uv = {{0.0f, 1.0f}, {1.0f, 1.0f}, {1.0f, 0.0f}, {0.0f, 0.0f}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(r, g, b, a)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
        }
    }

    private static void cleanup(long now) {
        if (ANIMS.size() < 256) {
            return;
        }
        final Iterator<Map.Entry<BlockPos, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastAccess > 30_000_000_000L) {
                it.remove();
            }
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(LampBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.25);
    }
}
