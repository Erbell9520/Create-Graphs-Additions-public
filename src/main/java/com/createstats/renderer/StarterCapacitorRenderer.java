package com.createstats.renderer;

import com.createstats.block.StarterCapacitorBlock;
import com.createstats.blockentity.StarterCapacitorBE;
import com.createstats.client.ModPartialModels;
import com.createstats.power.CapacitorDischarge;
import com.createstats.power.StarterMode;
import com.createstats.power.StarterTrigger;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class StarterCapacitorRenderer implements BlockEntityRenderer<StarterCapacitorBE> {
    private static final float PX = 1.0f / 16.0f;
    private static final ResourceLocation FX =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/starter_capacitor_fx.png");
    private static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");

    private static final float[][] JAR_X = {{1.5f, 5.5f}, {6.0f, 10.0f}, {10.5f, 14.5f}};
    private static final float GLASS_Y0 = 5.5f;
    private static final float GLASS_Y1 = 9.5f;
    private static final float PIVOT_X = 14.5f;
    private static final float PIVOT_Z = 8.0f;
    private static final float PARKED = 90.0f;
    private static final float GAP_X0 = 6.5f;
    private static final float GAP_X1 = 9.5f;
    private static final float ARC_Y = 14.5f;
    private static final float BALL_L_X = 5.0f;
    private static final float BALL_R = 1.5f;

    private static final float LENS_X0 = 11.5f;
    private static final float LENS_X1 = 12.5f;
    private static final float LENS_Y0 = 2.5f;
    private static final float LENS_Y1 = 3.5f;
    private static final float LENS_Z = 1.49f;
    private static final float PROUD = 0.02f;

    private static final float[] ARC_CORE = {0.96f, 0.99f, 1.0f};
    private static final float[] ARC_FRINGE = {0.30f, 0.62f, 1.0f};
    private static final int[] CHARGE = {104, 220, 255};

    private final RandomSource jitter = RandomSource.create();

    public StarterCapacitorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StarterCapacitorBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof StarterCapacitorBlock)) {
            return;
        }
        final StarterTrigger t = be.trigger();
        final float phaseT = t.phaseTick() + (t.phase() == StarterTrigger.Phase.IDLE ? 0 : partialTick);
        final float time = be.clientAge() + partialTick;
        final long tick = be.getLevel() == null ? 0 : be.getLevel().getGameTime();

        ms.pushPose();
        applyFacing(ms, state.getValue(StarterCapacitorBlock.FACING));

        final float arm = armAngle(t.phase(), phaseT);
        CachedBuffers.partial(ModPartialModels.STARTER_CAPACITOR_ARM, state)
                .translate(PIVOT_X * PX, 0, PIVOT_Z * PX)
                .rotate((float) Math.toRadians(arm), Direction.UP)
                .translate(-PIVOT_X * PX, 0, -PIVOT_Z * PX)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        final boolean firing = t.phase() == StarterTrigger.Phase.FIRING;
        final float ramp = firing ? CapacitorDischarge.rampAt(phaseT) : 0.0f;
        final float flash = firing ? Math.max(0.0f, 1.0f - phaseT / 4.0f) : 0.0f;
        final Matrix4f mat = ms.last().pose();

        final float fill = Mth.clamp(be.port().stored() / (float) CapacitorDischarge.CAPACITY, 0.0f, 1.0f);
        float glow = 0.92f + 0.08f * Mth.sin(time * Mth.PI / 20.0f);
        if (firing) {
            jitter.setSeed(tick * 101L);
            glow = (0.75f + 0.45f * ramp) * (0.78f + 0.44f * jitter.nextFloat());
        }
        renderGlow(buffer.getBuffer(RenderType.entityTranslucentEmissive(FX)), mat, fill, glow, flash);

        final int lamp = lampSprite(be, t, time);
        if (lamp >= 0) {
            final VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(FX));
            final float u0 = lamp * 2 / 32.0f;
            card(vc, mat, LENS_X1 * PX, LENS_Y0 * PX, LENS_Z * PX, LENS_X0 * PX, LENS_Y1 * PX, LENS_Z * PX,
                    u0, 0.0f, u0 + 2 / 32.0f, 2 / 32.0f, 255, 255, 255, 255, 0, 0, -1);
        }

        final VertexConsumer live = Buffers.live(buffer, RenderType.lightning());
        if (firing && be.port().stored() > 0) {
            renderArcs(live, mat, Math.min(1.0f, ramp * (1.0f + 0.5f * be.burnShare())), tick);
        } else if (t.phase() == StarterTrigger.Phase.IDLE && be.mode() == StarterMode.AUTO && t.shortTicks() > 0
                && be.port().stored() >= CapacitorDischarge.CAPACITY) {
            renderCorona(live, mat, t.shortTicks() / (float) StarterTrigger.SUSTAIN_TICKS, tick);
        }
        if (flash > 0) {
            halo(buffer, ms, 8.0f * PX, ARC_Y * PX, 8.0f * PX, 7.0f * PX * (0.6f + 0.4f * flash),
                    new float[]{0.75f, 0.88f, 1.0f}, 0.85f * flash);
        } else if (firing) {
            halo(buffer, ms, 8.0f * PX, ARC_Y * PX, 8.0f * PX, 3.5f * PX, ARC_FRINGE, 0.35f * ramp);
        }
        ms.popPose();
    }

    static float armAngle(StarterTrigger.Phase phase, float t) {
        return switch (phase) {
            case CLOSING -> {
                if (t < StarterTrigger.SWING_TICKS) {
                    final float u = t / StarterTrigger.SWING_TICKS;
                    yield PARKED * (1.0f - u * u);
                }
                final float k = t - StarterTrigger.SWING_TICKS;
                if (k < 0.5f) {
                    yield Mth.lerp(k / 0.5f, 0.0f, -6.0f);
                }
                if (k < 1.2f) {
                    yield Mth.lerp((k - 0.5f) / 0.7f, -6.0f, 2.5f);
                }
                yield Mth.lerp(Mth.clamp((k - 1.2f) / 0.8f, 0.0f, 1.0f), 2.5f, 0.0f);
            }
            case FIRING -> 0.0f;
            case RELEASING -> {
                final float u = Mth.clamp(t / StarterTrigger.RELEASE_TICKS, 0.0f, 1.0f);
                final float e = 1.0f - u;
                yield PARKED * (1.0f - e * e * e);
            }
            default -> PARKED;
        };
    }

    private static int lampSprite(StarterCapacitorBE be, StarterTrigger t, float time) {
        switch (t.phase()) {
            case CLOSING, FIRING -> {
                return 2;
            }
            case RELEASING -> {
                return 1;
            }
            default -> {
            }
        }
        if (be.mode() == StarterMode.HOLD) {
            return -1;
        }
        final boolean full = be.port().stored() >= CapacitorDischarge.CAPACITY;
        if (!full) {
            return 1;
        }
        if (be.mode() == StarterMode.AUTO && t.shortTicks() > 0) {
            final float sec = t.shortTicks() / 20.0f;
            final float ph = Mth.TWO_PI * (sec + 0.4f * sec * sec);
            return Mth.sin(ph) > -0.2f ? 1 : -1;
        }
        return 0;
    }

    private static void renderGlow(VertexConsumer vc, Matrix4f m, float fill, float glow, float flash) {
        if (fill <= 0.0f) {
            return;
        }
        final float top = GLASS_Y0 + (GLASS_Y1 - GLASS_Y0) * fill;
        final float men = Math.max(GLASS_Y0, top - 0.5f);
        final int r = Math.round(Mth.lerp(flash * 0.35f, CHARGE[0], 255));
        final int g = Math.round(Mth.lerp(flash * 0.35f, CHARGE[1], 255));
        final int b = CHARGE[2];
        final int bodyA = Math.round(255 * Mth.clamp(0.62f * glow, 0.0f, 1.0f));
        final int menA = Math.round(255 * Mth.clamp(0.90f * glow, 0.0f, 1.0f));
        for (float[] jar : JAR_X) {
            glowBox(vc, m, jar[0], jar[1], 6.5f, 9.5f, men, top, r, g, b, bodyA, menA);
            glowBox(vc, m, jar[0] + 0.5f, jar[1] - 0.5f, 6.0f, 10.0f, men, top, r, g, b, bodyA, menA);
        }
    }

    private static void glowBox(VertexConsumer vc, Matrix4f m, float x0, float x1, float z0, float z1,
                                float men, float top, int r, int g, int b, int bodyA, int menA) {
        final float xa = (x0 - PROUD) * PX;
        final float xb = (x1 + PROUD) * PX;
        final float za = (z0 - PROUD) * PX;
        final float zb = (z1 + PROUD) * PX;
        final float[][] bands = {{GLASS_Y0, men, 4 / 32.0f, 12 / 32.0f, bodyA}, {men, top, 3 / 32.0f, 4 / 32.0f, menA}};
        for (float[] band : bands) {
            if (band[1] <= band[0]) {
                continue;
            }
            final float y0 = band[0] * PX;
            final float y1 = band[1] * PX;
            final int a = (int) band[4];

            card(vc, m, xb, y0, za, xa, y1, za, 0, band[2], 8 / 32.0f, band[3], r, g, b, a, 0, 0, -1);
            card(vc, m, xa, y0, zb, xb, y1, zb, 0, band[2], 8 / 32.0f, band[3], r, g, b, a, 0, 0, 1);
            card(vc, m, xa, y0, za, xa, y1, zb, 0, band[2], 8 / 32.0f, band[3], r, g, b, a, -1, 0, 0);
            card(vc, m, xb, y0, zb, xb, y1, za, 0, band[2], 8 / 32.0f, band[3], r, g, b, a, 1, 0, 0);
        }
    }

    private void renderArcs(VertexConsumer vc, Matrix4f m, float ramp, long tick) {
        final int n = Math.max(1, (int) Math.ceil(4 * ramp));
        final float jag = 0.25f + 0.65f * ramp;
        for (int a = 0; a < n; a++) {
            jitter.setSeed(tick * 31L + a * 977L);
            final float y0 = ARC_Y + (jitter.nextFloat() - 0.5f) * 1.6f;
            final float y1 = ARC_Y + (jitter.nextFloat() - 0.5f) * 1.6f;
            final float z0 = 8.0f + (jitter.nextFloat() - 0.5f) * 1.4f;
            final float z1 = 8.0f + (jitter.nextFloat() - 0.5f) * 1.4f;
            final float bow = (0.3f + 0.5f * ramp) * (jitter.nextFloat() - 0.3f);
            final float bright = (a == 0 ? 1.0f : 0.7f + 0.3f * jitter.nextFloat()) * (0.45f + 0.75f * ramp);
            final int k = 10;
            float px = GAP_X0 - 0.1f;
            float py = y0;
            float pz = z0;
            final float[][] pts = new float[k + 1][];
            pts[0] = new float[]{px, py, pz};
            for (int i = 1; i <= k; i++) {
                final float s = i / (float) k;
                final float env = Mth.sin(s * Mth.PI);
                final float nx = i == k ? GAP_X1 + 0.1f : Mth.lerp(s, GAP_X0, GAP_X1);
                final float ny = i == k ? y1 : Mth.lerp(s, y0, y1) + ((jitter.nextFloat() - 0.5f) * 2 * jag + bow) * env;
                final float nz = i == k ? z1 : Mth.lerp(s, z0, z1) + (jitter.nextFloat() - 0.5f) * 1.4f * jag * env;
                filament(vc, m, px, py, pz, nx, ny, nz, 0.045f + 0.035f * ramp, bright);
                px = nx;
                py = ny;
                pz = nz;
                pts[i] = new float[]{nx, ny, nz};
            }
            if (ramp > 0.35f && jitter.nextFloat() < 0.8f) {
                float[] p = pts[3 + jitter.nextInt(4)];
                for (int f = 0; f < 3; f++) {
                    final float[] q = {p[0] + (jitter.nextFloat() - 0.3f) * 0.7f, p[1] + 0.35f + jitter.nextFloat() * 0.45f,
                            p[2] + (jitter.nextFloat() - 0.5f) * 0.8f};
                    filament(vc, m, p[0], p[1], p[2], q[0], q[1], q[2], 0.035f, bright * 0.45f);
                    p = q;
                }
            }
        }
    }

    private void renderCorona(VertexConsumer vc, Matrix4f m, float c, long tick) {
        final int n = (int) (2 + 10 * c);
        for (int i = 0; i < n; i++) {
            jitter.setSeed(tick * 13L + i * 71L);
            if (jitter.nextFloat() > 0.35f + 0.6f * c) {
                continue;
            }
            final float th = (jitter.nextFloat() - 0.5f) * 2.2f;
            final float ph = (jitter.nextFloat() - 0.5f) * 2.2f;
            final float cx = Mth.cos(th) * Mth.cos(ph);
            final float cy = Mth.sin(th);
            final float cz = Mth.cos(th) * Mth.sin(ph);
            final float r0 = BALL_R * 1.02f;
            final float r1 = BALL_R * (1.15f + 0.25f * jitter.nextFloat());
            filament(vc, m, BALL_L_X + cx * r0, ARC_Y + cy * r0, 8.0f + cz * r0,
                    BALL_L_X + cx * r1, ARC_Y + cy * r1, 8.0f + cz * r1, 0.03f, 0.5f + 0.5f * c);
        }
    }

    private static void applyFacing(PoseStack ms, Direction facing) {
        final float degrees = switch (facing) {
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
        if (degrees == 0.0f) {
            return;
        }
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-degrees));
        ms.translate(-0.5f, -0.5f, -0.5f);
    }

    private static void filament(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0,
                                 float x1, float y1, float z1, float w, float bright) {
        crossed(vc, m, x0, y0, z0, x1, y1, z1, w, ARC_CORE, Math.min(1.0f, 0.95f * bright));
        crossed(vc, m, x0, y0, z0, x1, y1, z1, w * 4.0f, ARC_FRINGE, Math.min(1.0f, 0.24f * bright));
    }

    private static void crossed(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0,
                                float x1, float y1, float z1, float w, float[] col, float a) {
        quad(vc, m, (x0 - w) * PX, y0 * PX, z0 * PX, (x0 + w) * PX, y0 * PX, z0 * PX,
                (x1 + w) * PX, y1 * PX, z1 * PX, (x1 - w) * PX, y1 * PX, z1 * PX, col, a);
        quad(vc, m, x0 * PX, (y0 - w) * PX, z0 * PX, x0 * PX, (y0 + w) * PX, z0 * PX,
                x1 * PX, (y1 + w) * PX, z1 * PX, x1 * PX, (y1 - w) * PX, z1 * PX, col, a);
        quad(vc, m, x0 * PX, y0 * PX, (z0 - w) * PX, x0 * PX, y0 * PX, (z0 + w) * PX,
                x1 * PX, y1 * PX, (z1 + w) * PX, x1 * PX, y1 * PX, (z1 - w) * PX, col, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float[] col, float a) {
        if (a <= 0.004f) {
            return;
        }
        vc.addVertex(m, x0, y0, z0).setColor(col[0], col[1], col[2], a);
        vc.addVertex(m, x1, y1, z1).setColor(col[0], col[1], col[2], a);
        vc.addVertex(m, x2, y2, z2).setColor(col[0], col[1], col[2], a);
        vc.addVertex(m, x3, y3, z3).setColor(col[0], col[1], col[2], a);
        vc.addVertex(m, x3, y3, z3).setColor(col[0], col[1], col[2], a);
        vc.addVertex(m, x2, y2, z2).setColor(col[0], col[1], col[2], a);
        vc.addVertex(m, x1, y1, z1).setColor(col[0], col[1], col[2], a);
        vc.addVertex(m, x0, y0, z0).setColor(col[0], col[1], col[2], a);
    }

    private static void card(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float u0, float v0, float u1, float v1, int r, int g, int b, int a,
                             float nx, float ny, float nz) {
        if (a <= 1) {
            return;
        }
        vertex(vc, m, x0, y0, z0, u0, v1, r, g, b, a, nx, ny, nz);
        vertex(vc, m, x1, y0, z1, u1, v1, r, g, b, a, nx, ny, nz);
        vertex(vc, m, x1, y1, z1, u1, v0, r, g, b, a, nx, ny, nz);
        vertex(vc, m, x0, y1, z0, u0, v0, r, g, b, a, nx, ny, nz);
        vertex(vc, m, x0, y1, z0, u0, v0, r, g, b, a, -nx, -ny, -nz);
        vertex(vc, m, x1, y1, z1, u1, v0, r, g, b, a, -nx, -ny, -nz);
        vertex(vc, m, x1, y0, z1, u1, v1, r, g, b, a, -nx, -ny, -nz);
        vertex(vc, m, x0, y0, z0, u0, v1, r, g, b, a, -nx, -ny, -nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, float nx, float ny, float nz) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(nx, ny, nz);
    }

    private static void halo(MultiBufferSource buffer, PoseStack ms, float x, float y, float z, float radius,
                             float[] col, float a) {
        if (a <= 0.01f) {
            return;
        }
        final VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        final Matrix4f m = ms.last().pose();
        final int alpha = Math.round(255 * Math.min(1.0f, a));
        final int r = Math.round(255 * col[0]);
        final int g = Math.round(255 * col[1]);
        final int b = Math.round(255 * col[2]);
        card(vc, m, x - radius, y - radius, z, x + radius, y + radius, z, 0, 0, 1, 1, r, g, b, alpha, 0, 0, 1);
        card(vc, m, x, y - radius, z - radius, x, y + radius, z + radius, 0, 0, 1, 1, r, g, b, alpha, 1, 0, 0);
    }

    @Override
    public AABB getRenderBoundingBox(StarterCapacitorBE be) {
        return new AABB(be.getBlockPos()).inflate(1.0);
    }
}
