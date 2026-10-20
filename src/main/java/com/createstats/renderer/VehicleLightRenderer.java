package com.createstats.renderer;

import com.createstats.block.MountedPart;
import com.createstats.vehiclelight.HailSequence;
import com.createstats.vehiclelight.LampShow;
import com.createstats.vehiclelight.VehicleLightBE;
import com.createstats.vehiclelight.VehicleLightBeams;
import com.createstats.vehiclelight.VehicleLightBlock;
import com.createstats.vehiclelight.VehicleLightGlow;
import com.createstats.vehiclelight.VehicleLightKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

// world lit lens = brake light as dark as the road at night. emissive here
@OnlyIn(Dist.CLIENT)
public class VehicleLightRenderer implements BlockEntityRenderer<VehicleLightBE> {
    private static final float PX = 1.0f / 16.0f;
    private static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");
    private static final Map<VehicleLightKind, ResourceLocation> LENS = new EnumMap<>(VehicleLightKind.class);

    static {
        for (VehicleLightKind kind : VehicleLightKind.values()) {
            LENS.put(kind, ResourceLocation.fromNamespaceAndPath("create_stats",
                    "textures/block/vehicle_light_lens_" + kind.id() + ".png"));
        }
    }

    private static final double RISE = 0.06;
    private static final double FALL = 0.14;

    private static final float OFF = 0.46f;

    private static final float SEAT = 0.04f;
    private static final float[] HALO_INFLATE = {0.35f, 1.05f};
    private static final float[] HALO_ALPHA = {0.50f, 0.20f};

    private static final float BARREL = 4.0f;
    private static final float CORE = 2.4f;
    private static final int BARREL_ARGB = 0xD8202326;
    private static final double PITCH_DEG_PER_PX = 5.0;
    private static final double YAW_DEG_PER_PX = 7.5;

    private static final double CORE_REACH_X = 2.0;
    private static final double CORE_REACH_Z = 1.2;

    public VehicleLightRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(VehicleLightBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof VehicleLightBlock block)) {
            return;
        }
        final VehicleLightKind kind = block.kind();
        final VehicleLightGlow glow = state.getValue(VehicleLightBlock.GLOW);
        final Direction facing = state.getValue(VehicleLightBlock.FACING);
        final long gameTime = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        final HailSequence.Hail hail = be.hailing();
        final boolean cut = hail != null && be.headlight().isCut(hail.start());
        final LampShow.Lens shown = LampShow.lens(kind, glow, gameTime, partialTick, hail, cut);
        final float level = advance(be, (float) shown.level());

        ps.pushPose();
        orient(ps, facing);

        final float mul = OFF + (1.0f - OFF) * level;
        final int grey = Math.round(255.0f * mul);
        final int tint = 0xFF000000 | (grey << 16) | (grey << 8) | grey;
        final int light = LightTexture.pack(Math.max(LightTexture.block(packedLight), Math.round(15.0f * level)),
                LightTexture.sky(packedLight));
        final VertexConsumer glass = buffers.getBuffer(RenderType.entityCutoutNoCull(LENS.get(kind)));
        final float[][] lens = kind.lens();
        final float u0 = lens[0][0];
        final float v0 = lens[0][2];
        for (float[] b : lens) {
            box(glass, ps, b[0] - SEAT, b[1] - SEAT, b[2] - SEAT, b[3] + SEAT, b[4] + SEAT, b[5] + SEAT,
                    u0, v0, tint, light);
        }
        final boolean headlamp = kind == VehicleLightKind.WHITE && facing.getAxis().isHorizontal();
        final VertexConsumer vc = headlamp || level > 0.02f
                ? buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO)) : null;
        if (headlamp) {
            final VehicleLightGlow step = shown.shown() == VehicleLightGlow.BRIGHT ? VehicleLightGlow.BRIGHT : VehicleLightGlow.DIM;
            projector(vc, ps, kind, be.headlight().downAt(partialTick) - VehicleLightBeams.restingTip(step),
                    be.headlight().leftAt(partialTick), level);
        }
        if (level > 0.02f) {
            final int colour = haloColour(kind);
            for (int shell = 0; shell < HALO_INFLATE.length; shell++) {
                final int alpha = Math.round(255.0f * HALO_ALPHA[shell] * level * level);
                if (alpha <= 2) {
                    continue;
                }
                final float g = HALO_INFLATE[shell];
                final int argb = (alpha << 24) | (colour & 0x00FFFFFF);
                for (float[] b : lens) {
                    halo(vc, ps, b[0] - g, b[1], b[2] - g, b[3] + g, b[4] + g, b[5] + g, argb);
                }
            }
        }
        ps.popPose();
    }

    private static void projector(VertexConsumer vc, PoseStack ps, VehicleLightKind kind, double down, double left,
                                  float level) {
        final float[] box = kind.lens()[0];
        final double dx = Double.isFinite(left) ? clamp(-left / YAW_DEG_PER_PX, CORE_REACH_X) : 0.0;
        final double dz = Double.isFinite(down) ? clamp(-down / PITCH_DEG_PER_PX, CORE_REACH_Z) : 0.0;
        final float cx = (float) ((box[0] + box[3]) / 2.0 + dx);
        final float cz = (float) ((box[2] + box[5]) / 2.0 + dz);
        disc(vc, ps, cx, cz, BARREL, kind.lensFront() + 0.03f, BARREL_ARGB);
        if (level > 0.02f) {
            final int alpha = Math.round(255.0f * (0.35f + 0.6f * level));
            disc(vc, ps, cx, cz, CORE, kind.lensFront() + 0.05f, (alpha << 24) | 0x00FFFFFF);
        }
    }

    private static void disc(VertexConsumer vc, PoseStack ps, float cx, float cz, float size, float y, int argb) {
        final float h = (y) * PX;
        final float ax = (cx - size / 2) * PX;
        final float bx = (cx + size / 2) * PX;
        final float az = (cz - size / 2) * PX;
        final float bz = (cz + size / 2) * PX;
        face(vc, ps, argb, LightTexture.FULL_BRIGHT, Direction.UP, 0, 0, 1, 1, ax, h, bz, bx, h, bz, bx, h, az, ax, h, az);
    }

    private static double clamp(double v, double reach) {
        return Math.max(-reach, Math.min(reach, v));
    }

    private static int haloColour(VehicleLightKind kind) {
        return switch (kind) {
            case WHITE -> 0xFFF4E2;
            case RED -> 0xFF2A1C;
            case AMBER -> 0xFF9C1E;
        };
    }

    private static float advance(VehicleLightBE be, float target) {
        final long now = System.nanoTime();
        if (be.shownLevel() < 0.0f) {
            be.show(target, now);
            return target;
        }
        final double dt = Math.max(0.0, Math.min(0.08, (now - be.shownNanos()) / 1.0e9));
        float shown = be.shownLevel();
        if (shown < target) {
            shown = (float) Math.min(target, shown + dt / RISE);
        } else if (shown > target) {
            shown = (float) Math.max(target, shown - dt / FALL);
        }
        be.show(shown, now);
        return shown;
    }

    private static void orient(PoseStack ps, Direction facing) {
        final float[] rot = MountedPart.rotationDegrees(facing);
        ps.translate(0.5, 0.5, 0.5);
        if (rot[0] != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(rot[0]));
        }
        if (rot[1] != 0.0f) {
            ps.mulPose(Axis.XP.rotationDegrees(rot[1]));
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    private static void box(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0, float x1, float y1,
                            float z1, float u0, float v0, int argb, int light) {
        final float ax = x0 * PX, ay = y0 * PX, az = z0 * PX;
        final float bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
        final float ua = (x0 - u0) * PX, ub = (x1 - u0) * PX;
        final float va = (z0 - v0) * PX, vb = (z1 - v0) * PX;
        final float edge = PX * 0.5f;
        face(vc, ps, argb, light, Direction.UP, ua, va, ub, vb,
                ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        face(vc, ps, argb, light, Direction.DOWN, ua, va, ub, vb,
                ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
        face(vc, ps, argb, light, Direction.SOUTH, ua, vb - edge, ub, vb,
                ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        face(vc, ps, argb, light, Direction.NORTH, ua, va, ub, va + edge,
                bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        face(vc, ps, argb, light, Direction.WEST, ua, va, ua + edge, vb,
                ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        face(vc, ps, argb, light, Direction.EAST, ub - edge, va, ub, vb,
                bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
    }

    private static void halo(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0, float x1, float y1,
                             float z1, int argb) {
        final float ax = x0 * PX, ay = y0 * PX, az = z0 * PX;
        final float bx = x1 * PX, by = y1 * PX, bz = z1 * PX;
        final int light = LightTexture.FULL_BRIGHT;
        face(vc, ps, argb, light, Direction.UP, 0, 0, 1, 1, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
        face(vc, ps, argb, light, Direction.SOUTH, 0, 0, 1, 1, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        face(vc, ps, argb, light, Direction.NORTH, 0, 0, 1, 1, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
        face(vc, ps, argb, light, Direction.WEST, 0, 0, 1, 1, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        face(vc, ps, argb, light, Direction.EAST, 0, 0, 1, 1, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
    }

    private static void face(VertexConsumer vc, PoseStack ps, int argb, int light, Direction normal,
                             float u0, float v0, float u1, float v1,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        final PoseStack.Pose pose = ps.last();
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        final float[][] p = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        final float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(pose, p[i][0], p[i][1], p[i][2])
                    .setColor(r, g, b, a)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
        }
    }

    @Override
    public AABB getRenderBoundingBox(VehicleLightBE be) {
        return new AABB(be.getBlockPos()).inflate(0.25);
    }
}
