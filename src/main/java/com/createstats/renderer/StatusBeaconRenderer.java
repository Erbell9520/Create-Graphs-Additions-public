package com.createstats.renderer;

import com.createstats.block.StatusTowerLightBlock;
import com.createstats.blockentity.SignalPattern;
import com.createstats.blockentity.StatusTowerLightBE;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// the old one setBlock'd on the client every 5 ticks to blink. remesh for a blinking light
@OnlyIn(Dist.CLIENT)
public class StatusBeaconRenderer implements BlockEntityRenderer<StatusTowerLightBE> {
    private static final ResourceLocation LENS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/beacon_lens.png");
    private static final ResourceLocation MIRROR =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/beacon_mirror.png");

    private static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");

    private static final float PX = 1.0f / 16.0f;

    private static final float CX = 8.0f, CZ = 8.0f;

    private static final float DARK = 0.02f;

    private static final float[] HALO_SCALE = {1.10f, 1.30f};
    private static final float[] HALO_ALPHA = {0.40f, 0.16f};

    private static final int DEAD_GLASS = 0xFF2A2E34;

    private static final double SETTLE = 0.09D;

    private static final java.util.Map<BlockPos, Anim> ANIMS = new java.util.HashMap<>();

    private static final class Anim {
        float level;
        float r, g, b;
        boolean seeded;
        long lastNanos;
        long lastAccess;

        double dt;
    }

    public StatusBeaconRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StatusTowerLightBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!state.hasProperty(StatusTowerLightBlock.SEGMENT)) {
            return;
        }
        final StatusTowerLightBlock.Segment segment = state.getValue(StatusTowerLightBlock.SEGMENT);
        final float[] span = BeaconGeometry.span(segment.getSerializedName());
        if (span == null) {
            return;
        }

        final SignalPattern pattern = be.lensPattern();
        final int colour = be.lensColour();

        final double seconds = be.getLevel() == null ? 0.0D
                : com.createstats.client.AnimationClock.time(be.getLevel(), partialTick) / 20.0D;
        final double jitter = jitterOf(be.getBlockPos());

        final Anim anim = advance(be.getBlockPos(), colour);

        final float level = (float) pattern.brightness(seconds, 0.0D, jitter);
        anim.level = approach(anim.level, level, anim.dt / SETTLE);
        final float lit = anim.level;

        final VertexConsumer glass = buffers.getBuffer(RenderType.entityCutoutNoCull(LENS));
        final int light = blend(packedLight, lit);
        for (int side = 0; side < BeaconGeometry.OUTLINE.length; side++) {
            final float[] a = BeaconGeometry.OUTLINE[side];
            final float[] b = BeaconGeometry.OUTLINE[(side + 1) % BeaconGeometry.OUTLINE.length];
            final double bearing = bearingOf(a, b);
            final float faceLevel = (float) pattern.brightness(seconds, bearing, jitter);
            wall(glass, ps, a, b, span[0], span[1], mix(anim, colour, faceLevel), light);
        }

        if (lit > DARK) {
            halo(buffers, ps, span, mix(anim, colour, lit), lit);
        }
        if (segment.hasCap()) {
            vane(buffers, ps, be, seconds, colour, lit, packedLight);
        }
        cleanup();
    }

    private static int mix(Anim anim, int colour, float level) {
        if (level <= DARK) {
            return DEAD_GLASS;
        }
        final float scale = 0.22f + 0.78f * Math.min(1.0f, level);
        final int r = Math.round(anim.r * scale);
        final int g = Math.round(anim.g * scale);
        final int b = Math.round(anim.b * scale);
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }

    private static Anim advance(BlockPos pos, int colour) {
        final long now = System.nanoTime();
        final Anim anim = ANIMS.computeIfAbsent(pos.immutable(), key -> new Anim());

        final double dt = anim.seeded
                ? Math.max(0.0D, Math.min(0.08D, (now - anim.lastNanos) / 1_000_000_000.0D))
                : 1.0D;
        anim.dt = dt;
        anim.lastNanos = now;
        anim.lastAccess = now;
        final float tr = (colour >> 16) & 0xFF;
        final float tg = (colour >> 8) & 0xFF;
        final float tb = colour & 0xFF;
        if (!anim.seeded) {
            anim.r = tr;
            anim.g = tg;
            anim.b = tb;
            anim.seeded = true;
        } else {
            final float step = (float) Math.min(1.0D, dt / SETTLE);
            anim.r += (tr - anim.r) * step;
            anim.g += (tg - anim.g) * step;
            anim.b += (tb - anim.b) * step;
        }
        return anim;
    }

    private static float approach(float value, float target, double step) {
        final float s = (float) Math.max(0.0D, Math.min(1.0D, step));
        return value + (target - value) * s;
    }

    private static int blend(int packedLight, float level) {
        final int block = LightTexture.block(packedLight);
        final int sky = LightTexture.sky(packedLight);
        return LightTexture.pack(Math.max(block, Math.round(15.0f * level)), sky);
    }

    private static double jitterOf(BlockPos pos) {
        final int h = pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 7;
        return (Math.floorMod(h, 1000)) / 1000.0D * 4.0D;
    }

    private static double bearingOf(float[] a, float[] b) {
        final float mx = (a[0] + b[0]) * 0.5f - CX;
        final float mz = (a[1] + b[1]) * 0.5f - CZ;

        return Math.atan2(mx, -mz);
    }

    private static void wall(VertexConsumer vc, PoseStack ps, float[] a, float[] b,
                             float y0, float y1, int argb, int light) {
        final float ax = a[0] * PX, az = a[1] * PX;
        final float bx = b[0] * PX, bz = b[1] * PX;
        final float lo = y0 * PX, hi = y1 * PX;
        final float nx = (a[0] + b[0]) * 0.5f - CX;
        final float nz = (a[1] + b[1]) * 0.5f - CZ;
        final float len = (float) Math.max(1.0e-4, Math.sqrt(nx * nx + nz * nz));

        final float ex = b[0] - a[0];
        final float ez = b[1] - a[1];
        final boolean flip = (ez * nx - ex * nz) < 0.0f;
        final float x0 = flip ? bx : ax;
        final float z0 = flip ? bz : az;
        final float x1 = flip ? ax : bx;
        final float z1 = flip ? az : bz;
        quad(vc, ps, argb, light, nx / len, 0.0f, nz / len,
                x0, lo, z0, x1, lo, z1, x1, hi, z1, x0, hi, z0);
    }

    private static void halo(MultiBufferSource buffers, PoseStack ps, float[] span, int colour,
                             float level) {
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        for (int shell = 0; shell < HALO_SCALE.length; shell++) {
            final int alpha = Math.round(255.0f * HALO_ALPHA[shell] * level * level);
            if (alpha <= 2) {
                continue;
            }
            final int argb = (alpha << 24) | (colour & 0x00FFFFFF);
            final float k = HALO_SCALE[shell];
            final float grow = (k - 1.0f) * 3.4f;
            for (int side = 0; side < BeaconGeometry.OUTLINE.length; side++) {
                final float[] a = scale(BeaconGeometry.OUTLINE[side], k);
                final float[] b = scale(BeaconGeometry.OUTLINE[(side + 1) % BeaconGeometry.OUTLINE.length], k);
                wall(vc, ps, a, b, span[0] - grow, span[1] + grow, argb, LightTexture.FULL_BRIGHT);
            }
        }
    }

    private static float[] scale(float[] point, float k) {
        return new float[] {CX + (point[0] - CX) * k, CZ + (point[1] - CZ) * k};
    }

    private static void vane(MultiBufferSource buffers, PoseStack ps, StatusTowerLightBE be,
                             double seconds, int colour, float level, int packedLight) {
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(MIRROR));

        final boolean spinning = be.lensPattern().spinsVane();
        final float angle = spinning
                ? (float) (seconds * SignalPattern.ROTATE_HZ * 360.0D)

                : 26.0f;
        final int tint = level <= DARK

                ? 0xFF6A7078
                : 0xFF000000
                        | (lerpChannel((colour >> 16) & 0xFF) << 16)
                        | (lerpChannel((colour >> 8) & 0xFF) << 8)
                        | lerpChannel(colour & 0xFF);
        final int light = blend(packedLight, level * 0.75f);

        ps.pushPose();
        ps.translate(CX * PX, 0.0f, CZ * PX);
        ps.mulPose(Axis.YP.rotationDegrees(angle));
        ps.translate(-CX * PX, 0.0f, -CZ * PX);

        blade(vc, ps, tint, light, false);
        blade(vc, ps, tint, light, true);
        ps.popPose();
    }

    private static void blade(VertexConsumer vc, PoseStack ps, int argb, int light, boolean across) {
        final float r = BeaconGeometry.VANE_RADIUS;
        final float t = BeaconGeometry.VANE_HALF_THICK;
        final float halfX = across ? t : r;
        final float halfZ = across ? r : t;
        box(vc, ps, CX - halfX, BeaconGeometry.VANE_Y0, CZ - halfZ,
                CX + halfX, BeaconGeometry.VANE_Y1, CZ + halfZ, argb, light);
    }

    private static int lerpChannel(int channel) {
        return Math.min(255, Math.round(60.0f + channel * 0.62f));
    }

    private static void box(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0,
                            float x1, float y1, float z1, int argb, int light) {
        final float ax = x0 * PX, ay = y0 * PX, az = z0 * PX;
        final float bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
        quad(vc, ps, argb, light, 0, 0, 1, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        quad(vc, ps, argb, light, 0, 0, -1, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        quad(vc, ps, argb, light, -1, 0, 0, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        quad(vc, ps, argb, light, 1, 0, 0, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
        quad(vc, ps, argb, light, 0, 1, 0, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        quad(vc, ps, argb, light, 0, -1, 0, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
    }

    private static void quad(VertexConsumer vc, PoseStack ps, int argb, int light,
                             float nx, float ny, float nz,
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
                    .setNormal(pose, nx, ny, nz);
        }
    }

    private static void cleanup() {
        if (ANIMS.size() < 256) {
            return;
        }
        final long now = System.nanoTime();
        ANIMS.entrySet().removeIf(e -> now - e.getValue().lastAccess > 30_000_000_000L);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(StatusTowerLightBE be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.3);
    }
}
