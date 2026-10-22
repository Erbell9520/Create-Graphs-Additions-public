package com.createstats.seismic.client;

import com.createstats.block.SeismicScannerBlock;
import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.client.AnimationClock;
import com.createstats.client.ModPartialModels;
import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicGeometry;
import com.createstats.seismic.SeismicRig;
import com.createstats.seismic.SeismicTargets;
import com.createstats.seismic.SeismicTrace;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// every part reports something true
public class SeismicScannerRenderer implements BlockEntityRenderer<SeismicScannerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final int GLOW_UNITS = LightTexture.FULL_BRIGHT & 0xFFFF;

    private static final float[] COLD_LENS = {0.32f, 0.30f, 0.28f};

    private static final float[] INK = {0.11f, 0.16f, 0.33f};
    private static final ResourceLocation LENS = ResourceLocation.fromNamespaceAndPath("create_stats",
            "block/seismic_scanner/lens");
    private static final ResourceLocation LAVA = ResourceLocation.withDefaultNamespace("block/lava_still");
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");

    private static final Map<String, BlockState> SPECIMENS = new HashMap<>();

    public SeismicScannerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SeismicScannerBE be, float partial, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        final BlockState state = be.getBlockState();
        final Level level = be.getLevel();
        if (!(state.getBlock() instanceof SeismicScannerBlock) || level == null) {
            return;
        }
        final long now = level.getGameTime();
        final double t = AnimationClock.time(now, partial);
        ms.pushPose();
        applyFacing(ms, state.getValue(HorizontalDirectionalBlock.FACING));

        VertexConsumer solid = buffers.getBuffer(RenderType.solid());

        final long landing = be.landingAt();
        final double h = be.falling(now)
                ? be.dropHeight() * (1.0 - SeismicCharge.fallen(t - be.dropAt(), be.fallTicks()))
                : Mth.lerp(partial, be.weightShownOld, be.weightShown)
                + (landing == Long.MIN_VALUE ? 0.0 : SeismicCharge.bounce(t - landing, be.dropHeight(), be.fallTicks()));
        final double rise = SeismicGeometry.weightRise(h);
        part(ModPartialModels.SEISMIC_WEIGHT, state, light).translate(0, rise * PX, 0).renderInto(ms, solid);

        final double eye = SeismicGeometry.EYE_TOP + rise;
        ropeRun(state, light, ms, solid, 0.0, eye, SeismicGeometry.SHEAVE_Y, eye);
        ropeRun(state, light, ms, solid, SeismicGeometry.DROP_X - SeismicGeometry.CABLE_X, SeismicGeometry.DRUM_Y,
                SeismicGeometry.SHEAVE_Y, -rise);

        part(ModPartialModels.SEISMIC_SHEAVE, state, light)
                .translate(SeismicGeometry.SHEAVE_X * PX, SeismicGeometry.SHEAVE_Y * PX, SeismicGeometry.SHEAVE_Z * PX)
                .rotateZDegrees((float) SeismicGeometry.sheaveDegrees(h))
                .translate(-SeismicGeometry.SHEAVE_X * PX, -SeismicGeometry.SHEAVE_Y * PX, -SeismicGeometry.SHEAVE_Z * PX)
                .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        solid = buffers.getBuffer(RenderType.solid());

        final float drum = (float) SeismicGeometry.drumDegrees(h);
        for (final PartialModel p : new PartialModel[] {ModPartialModels.SEISMIC_DRUM, ModPartialModels.SEISMIC_RATCHET}) {
            part(p, state, light).translate(0, SeismicGeometry.DRUM_Y * PX, SeismicGeometry.DRUM_Z * PX)
                    .rotateXDegrees(drum).translate(0, -SeismicGeometry.DRUM_Y * PX, -SeismicGeometry.DRUM_Z * PX)
                    .renderInto(ms, solid);
        }

        final double pull = pull(be, t);
        part(ModPartialModels.SEISMIC_LEVER, state, light)
                .translate(SeismicGeometry.LEVER_X * PX, SeismicGeometry.LEVER_Y * PX, SeismicGeometry.LEVER_Z * PX)
                .rotateXDegrees((float) (-SeismicGeometry.LEVER_PULL_DEG * pull))
                .translate(-SeismicGeometry.LEVER_X * PX, -SeismicGeometry.LEVER_Y * PX, -SeismicGeometry.LEVER_Z * PX)
                .renderInto(ms, solid);

        final double ride = SeismicGeometry.PAWL_RIDE_DEG * SeismicGeometry.pawlRide(h);
        final float pawl = (float) Math.max(ride, SeismicGeometry.PAWL_LIFT_DEG * pull);
        part(ModPartialModels.SEISMIC_PAWL, state, light)
                .translate(SeismicGeometry.PAWL_X * PX, SeismicGeometry.PAWL_Y * PX, SeismicGeometry.PAWL_Z * PX)
                .rotateXDegrees(pawl)
                .translate(-SeismicGeometry.PAWL_X * PX, -SeismicGeometry.PAWL_Y * PX, -SeismicGeometry.PAWL_Z * PX)
                .renderInto(ms, solid);

        final double tick = Mth.lerp(partial, be.tickShownOld, be.tickShown);
        part(ModPartialModels.SEISMIC_TICK, state, light).translate(0, SeismicGeometry.weightRise(tick) * PX, 0)
                .renderInto(ms, solid);

        final float paper = Mth.lerp(partial, be.paperDegOld, be.paperDeg);
        part(ModPartialModels.SEISMIC_PAPER, state, light)
                .translate(SeismicGeometry.PAPER_X * PX, 0, SeismicGeometry.PAPER_Z * PX)
                .rotateYDegrees(paper)
                .translate(-SeismicGeometry.PAPER_X * PX, 0, -SeismicGeometry.PAPER_Z * PX)
                .renderInto(ms, solid);
        final double pen = trace(be, t);
        part(ModPartialModels.SEISMIC_PEN, state, light).translate(0, pen * SeismicGeometry.PEN_TRAVEL * PX, 0)
                .renderInto(ms, solid);
        ink(be, ms, buffers, light, t);

        lamp(be, state, ms, buffers.getBuffer(RenderType.solid()), partial, light);

        final float out = Mth.lerp(partial, be.specimenOutOld, be.specimenOut);
        specimen(be, be.specimenShown, 1.0f - out, ms, buffers, light, overlay);
        ms.popPose();
    }

    private static SuperByteBuffer part(PartialModel model, BlockState state, int light) {
        return CachedBuffers.partial(model, state).light(light);
    }

    private static void ropeRun(BlockState state, int light, PoseStack ms, VertexConsumer vc, double dx, double lo, double hi,
                                double anchor) {
        final double lay = SeismicGeometry.ROPE_LAY;
        if (hi - lo < 0.01) {
            return;
        }
        for (double a = anchor + Math.floor((lo - anchor) / lay) * lay; a < hi; a += lay) {
            final double y0 = Math.max(lo, a);
            final double y1 = Math.min(hi, a + lay);
            if (y1 - y0 < 0.01) {
                continue;
            }
            part(ModPartialModels.SEISMIC_ROPE, state, light).translate(dx * PX, y0 * PX, 0)
                    .scale(1.0f, (float) ((y1 - y0) / lay), 1.0f).renderInto(ms, vc);
        }
    }

    static double pull(SeismicScannerBE be, double t) {
        if (be.dropAt() == Long.MIN_VALUE) {
            return 0.0;
        }
        final double s = t - be.dropAt();
        if (s < 0.0) {
            return 0.0;
        }
        final double hold = Math.max(3.0, be.fallTicks());
        if (s < 3.0) {
            final double e = 1.0 - s / 3.0;
            return 1.0 - e * e;
        }
        if (s < hold) {
            return 1.0;
        }
        final double back = (s - hold) / 6.0;
        return back >= 1.0 ? 0.0 : 1.0 - back * back;
    }

    static float[] lampColour(SeismicRig.Lamp lamp) {
        return switch (lamp) {
            case RED -> new float[] {1.0f, 0.24f, 0.18f};
            case GREEN -> new float[] {0.15f, 1.0f, 0.68f};
            case WHITE -> new float[] {1.0f, 1.0f, 0.94f};
            default -> new float[] {1.0f, 0.64f, 0.14f};
        };
    }

    private static void lamp(SeismicScannerBE be, BlockState state, PoseStack ms, VertexConsumer vc, float partial, int light) {
        final float glow = Mth.clamp(Mth.lerp(partial, be.lampGlowOld, be.lampGlow), 0.0f, 1.0f);
        final int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
            final float lit = Mth.lerp(partial, be.lampRgbOld[i], be.lampRgb[i]);
            rgb[i] = Math.round(255.0f * Mth.lerp(glow, COLD_LENS[i], lit));
        }
        final int block = light & 0xFFFF;
        final int sky = (light >>> 16) & 0xFFFF;
        final int warm = Math.round(block + (GLOW_UNITS - block) * glow) | (sky << 16);

        CachedBuffers.partial(ModPartialModels.SEISMIC_LAMP, state).color(rgb[0], rgb[1], rgb[2], 255).light(warm)
                .disableDiffuse().renderInto(ms, vc);
    }

    static double trace(SeismicScannerBE be, double when) {
        if (be.blowAt != Long.MIN_VALUE && when >= be.blowAt) {
            return SeismicTrace.at(when, be.blowAt, be.blowShown, be.blowEchoes);
        }
        if (be.prevBlowAt != Long.MIN_VALUE && when >= be.prevBlowAt) {
            return SeismicTrace.at(when, be.prevBlowAt, be.prevBlowShown, be.prevBlowEchoes);
        }
        return SeismicTrace.at(when, 0.0, 0.0, null);
    }

    private static void ink(SeismicScannerBE be, PoseStack ms, MultiBufferSource buffers, int light, double t) {
        final TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LENS);
        final float u = sprite.getU(0.5f);
        final float v = sprite.getV(0.5f);
        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        final PoseStack.Pose pose = ms.last();
        final double r = SeismicGeometry.PAPER_R + 0.035;
        final double cx = SeismicGeometry.PAPER_X;
        final double cz = SeismicGeometry.PAPER_Z;
        final double mid = (SeismicGeometry.PAPER_Y0 + SeismicGeometry.PAPER_Y1) / 2.0;
        final double half = 0.075;
        double px = 0;
        double py = 0;
        double pz = 0;
        double pa = 0;
        boolean first = true;
        for (double age = 0.0; ; age += 0.5) {
            final double phi = SeismicGeometry.PEN_DEG - SeismicGeometry.PAPER_DEG_PER_TICK * age;
            if (phi < SeismicGeometry.PAPER_LAST_DEG) {
                break;
            }
            final double y = mid + trace(be, t - age) * SeismicGeometry.PEN_TRAVEL;
            final double a = Math.toRadians(phi);
            final double x = cx + r * Math.sin(a);
            final double z = cz - r * Math.cos(a);
            if (!first) {
                final double ds = r * (pa - a);
                final double dy = y - py;
                final double len = Math.hypot(ds, dy);
                final double ws = len < 1.0e-9 ? 0.0 : dy / len * half;
                final double wy = len < 1.0e-9 ? half : ds / len * half;
                quad(vc, pose, px, py, pz, pa, x, y, z, a, ws, wy, u, v, light);
            }
            px = x;
            py = y;
            pz = z;
            pa = a;
            first = false;
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, double x0, double y0, double z0, double a0, double x1,
                             double y1, double z1, double a1, double ws, double wy, float u, float v, int light) {
        final float k = PX;
        final double tx0 = Math.cos(a0) * ws;
        final double tz0 = Math.sin(a0) * ws;
        final double tx1 = Math.cos(a1) * ws;
        final double tz1 = Math.sin(a1) * ws;
        final float nx = (float) Math.sin((a0 + a1) / 2.0);
        final float nz = (float) -Math.cos((a0 + a1) / 2.0);
        vertex(vc, pose, (x0 - tx0) * k, (y0 - wy) * k, (z0 - tz0) * k, nx, nz, u, v, light);
        vertex(vc, pose, (x1 - tx1) * k, (y1 - wy) * k, (z1 - tz1) * k, nx, nz, u, v, light);
        vertex(vc, pose, (x1 + tx1) * k, (y1 + wy) * k, (z1 + tz1) * k, nx, nz, u, v, light);
        vertex(vc, pose, (x0 + tx0) * k, (y0 + wy) * k, (z0 + tz0) * k, nx, nz, u, v, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float nx, float nz,
                               float u, float v, int light) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setColor(INK[0], INK[1], INK[2], 1.0f).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, 0.0f, nz);
    }

    private static void specimen(SeismicScannerBE be, @Nullable String id, float shown, PoseStack ms, MultiBufferSource buffers,
                                 int light, int overlay) {
        if (id == null || id.isEmpty() || SeismicCatalog.VOIDS.equals(id) || be.getLevel() == null || shown <= 0.01f) {
            return;
        }
        final double[] c = SeismicGeometry.CELL;
        final double size = Math.min(c[2] - c[0], c[3] - c[1]) - 0.4;
        final double tall = size * shown;
        final double x0 = (c[0] + c[2]) / 2.0 - size / 2.0;
        final double y0 = (c[1] + c[3]) / 2.0 - size / 2.0;
        final double depth = 0.25;
        final double z0 = SeismicGeometry.CELL_Z + 0.2;
        if (SeismicCatalog.LIQUIDS.equals(id)) {
            final TextureAtlasSprite lava = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LAVA);
            final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
            final PoseStack.Pose pose = ms.last();
            final float k = PX;
            final float u0 = lava.getU0();
            final float u1 = lava.getU(0.25f);

            final float v0 = lava.getV0();
            final float v1 = lava.getV(0.25f * shown);
            final float zf = (float) (z0 * k);
            final float[][] q = {{(float) ((x0 + size) * k), (float) (y0 * k), u1, v1}, {(float) (x0 * k), (float) (y0 * k), u0, v1},
                    {(float) (x0 * k), (float) ((y0 + tall) * k), u0, v0}, {(float) ((x0 + size) * k), (float) ((y0 + tall) * k), u1, v0}};
            for (final float[] p : q) {
                vc.addVertex(pose, p[0], p[1], zf).setColor(1f, 1f, 1f, 1f).setUv(p[2], p[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, -1f);
            }
            return;
        }
        final BlockState sample = SPECIMENS.computeIfAbsent(id, k -> {
            final Block b = SeismicTargets.sample(be.getLevel(), k);
            return b == null ? null : b.defaultBlockState();
        });
        if (sample == null) {
            return;
        }
        ms.pushPose();
        ms.translate(x0 * PX, y0 * PX, z0 * PX);
        ms.scale((float) (size * PX), (float) (tall * PX), (float) (depth * PX));
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(sample, ms, buffers, light, overlay);
        ms.popPose();
    }

    private static void applyFacing(PoseStack ms, Direction facing) {
        final float degrees = SeismicScannerBE.quarter(facing) * 90.0f;
        if (degrees == 0.0f) {
            return;
        }
        ms.translate(0.5f, 0.5f, 0.5f);
        ms.mulPose(Axis.YP.rotationDegrees(-degrees));
        ms.translate(-0.5f, -0.5f, -0.5f);
    }

    @Override
    public AABB getRenderBoundingBox(SeismicScannerBE be) {
        return be.renderBounds();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
