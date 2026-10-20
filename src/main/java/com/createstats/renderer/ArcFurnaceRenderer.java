package com.createstats.renderer;

import com.createstats.block.ArcFurnaceBlock;
import com.createstats.blockentity.ArcFurnaceBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.joml.Matrix4f;

// the old one was 15 models and 192 variants to move electrodes in 3 snapping steps
@OnlyIn(Dist.CLIENT)
public class ArcFurnaceRenderer implements BlockEntityRenderer<ArcFurnaceBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/lamp_halo.png");

    private static final float HEARTH_Y = 8.0f;
    private static final float DECK_TOP = 13.0f;
    private static final float POT_LO = 4.0f;
    private static final float POT_HI = 12.0f;

    private static final float[][] ELECTRODES = {{8.0f, 6.0f}, {6.0f, 10.0f}, {10.0f, 10.0f}};
    private static final float TIP_DOWN = 9.4f;
    private static final float TIP_UP = 12.6f;

    private static final float[] HATCH_PIVOT = {8.0f, 3.0f, -0.375f};

    private static final float HATCH_ANGLE = 100.0f;

    private static final float DRAWER_SLIDE = 1.7f;

    private static final float[] FAN_CENTRE = {0.5f, 5.7f, 8.0f};

    private static final float CONSOLE_Z0 = 1.0f;
    private static final float CONSOLE_Z1 = 3.0f;
    private static final float[][] CONSOLE_LAMPS = {{1.0f, 3.0f}, {4.0f, 6.0f}};
    private static final float[][] CONSOLE_BARS = {{7.0f, 9.0f}, {10.0f, 12.0f}, {13.0f, 15.0f}};

    private static final float[] WINDOW = {-3.0f, 1.0f, 3.0f, 3.0f};

    private static final float[] ARC_WHITE = {0.93f, 0.96f, 1.00f};
    private static final float[] ARC_BLUE = {0.57f, 0.78f, 1.00f};
    private static final float[] HOT = {1.00f, 0.42f, 0.10f};
    private static final float[] HOT_HI = {1.00f, 0.78f, 0.36f};
    private static final float[] SICK = {0.94f, 0.34f, 0.12f};
    private static final float[] AMBER = {0.94f, 0.64f, 0.17f};
    private static final float[] GREEN = {0.36f, 0.85f, 0.45f};
    private static final float[] DEAD = {0.10f, 0.11f, 0.13f};
    private static final float[] BRASS = {0.80f, 0.63f, 0.30f};

    private final ItemRenderer itemRenderer;
    private final RandomSource jitter = RandomSource.create();

    public ArcFurnaceRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ArcFurnaceBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ArcFurnaceBlock)) {
            return;
        }
        final boolean running = state.getValue(ArcFurnaceBlock.RUNNING);
        final boolean arc = running && state.getValue(ArcFurnaceBlock.ARC);
        final boolean starved = running && state.getValue(ArcFurnaceBlock.STARVED);
        final float heat = be.glowHeat(partialTick);
        final float lift = be.electrodeLift(partialTick);
        final float open = be.hatchSwing(partialTick);
        final float tipY = Mth.lerp(lift, TIP_DOWN, TIP_UP);

        final int litBy = blend(light, heat);

        ms.pushPose();
        applyFacing(ms, state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));

        renderElectrodes(ms, buffer, state, litBy, tipY, arc, be, partialTick);
        renderHatch(ms, buffer, state, litBy, open, heat);
        renderDrawer(ms, buffer, state, light, be.drawerSlide(partialTick));
        renderFan(ms, buffer, state, light, be.fanSpin(partialTick));
        renderCharge(be, ms, buffer, litBy, overlay, heat);
        renderProduct(be, ms, buffer, light, overlay, open, partialTick);
        renderGlow(be, ms, buffer, heat, arc, starved, running, tipY, open, partialTick);

        ms.popPose();
    }

    private void renderElectrodes(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                                  float tipY, boolean arc, ArcFurnaceBE be, float partialTick) {
        for (int i = 0; i < ELECTRODES.length; i++) {
            final float bob = arc
                    ? Mth.sin((be.clientAge() + partialTick) * 0.31f + i * 2.1f) * 0.06f
                    : 0.0f;
            ms.pushPose();
            ms.translate(ELECTRODES[i][0] * PX, (tipY + bob) * PX, ELECTRODES[i][1] * PX);
            draw(ms, buffer, state, light, ModPartialModels.ARC_ELECTRODE);
            ms.popPose();
        }
    }

    private void renderHatch(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                             float open, float heat) {
        ms.pushPose();
        ms.translate(HATCH_PIVOT[0] * PX, HATCH_PIVOT[1] * PX, HATCH_PIVOT[2] * PX);

        ms.mulPose(Axis.XP.rotationDegrees(-HATCH_ANGLE * open));
        draw(ms, buffer, state, light, ModPartialModels.ARC_HATCH);

        if (heat > 0.02f && open < 0.6f) {
            final float fade = (1.0f - open / 0.6f) * heat;
            final VertexConsumer vc = Buffers.live(buffer, RenderType.lightning());
            final Matrix4f mat = ms.last().pose();
            final float[] col = mix(HOT, HOT_HI, heat * 0.6f);
            flat(vc, mat, WINDOW[0] * PX, WINDOW[1] * PX, WINDOW[2] * PX, WINDOW[3] * PX,
                    -0.42f * PX, col, 0.30f + 0.55f * fade);
            halo(buffer, ms, 0.0f, 2.0f * PX, -1.2f * PX, 7.0f * PX, col, 0.40f * fade);
        }
        ms.popPose();
    }

    private void renderDrawer(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                              float slide) {
        ms.pushPose();
        ms.translate(0.0f, 0.0f, -DRAWER_SLIDE * slide * PX);
        draw(ms, buffer, state, light, ModPartialModels.ARC_DRAWER);
        ms.popPose();
    }

    private void renderFan(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                           float spin) {
        for (int i = 0; i < 3; i++) {
            ms.pushPose();
            ms.translate(FAN_CENTRE[0] * PX, FAN_CENTRE[1] * PX, FAN_CENTRE[2] * PX);
            ms.mulPose(Axis.XP.rotationDegrees(spin + i * 120.0f));
            draw(ms, buffer, state, light, ModPartialModels.ARC_FAN_BLADE);
            ms.popPose();
        }
    }

    private void renderCharge(ArcFurnaceBE be, PoseStack ms, MultiBufferSource buffer, int light,
                              int overlay, float heat) {
        final float[][] spots = {{7.0f, 6.8f}, {9.2f, 9.4f}};
        final int[] slots = {ArcFurnaceBE.SLOT_A, ArcFurnaceBE.SLOT_B};
        for (int s = 0; s < slots.length; s++) {
            final ItemStack stack = be.item(slots[s]);
            if (stack.isEmpty()) {
                continue;
            }
            final int layers = Math.min(4, 1 + (stack.getCount() - 1) / 8);
            final int seed = (int) be.getBlockPos().asLong() + s * 31;
            for (int layer = 0; layer < layers; layer++) {
                ms.pushPose();
                ms.translate(spots[s][0] * PX, (HEARTH_Y + 0.08f) * PX + layer * 0.028f,
                        spots[s][1] * PX);
                final float twist = ((seed >> (layer * 5)) % 360) + layer * 37.0f;
                ms.mulPose(Axis.YP.rotationDegrees(twist));
                ms.mulPose(Axis.XP.rotationDegrees(90.0f));
                ms.scale(0.42f, 0.42f, 0.42f);
                itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer,
                        be.getLevel(), seed + layer);
                ms.popPose();
            }
            if (heat > 0.15f) {
                halo(buffer, ms, spots[s][0] * PX, (HEARTH_Y + 1.2f) * PX, spots[s][1] * PX,
                        5.0f * PX, HOT_HI, 0.45f * heat);
            }
        }
    }

    private void renderProduct(ArcFurnaceBE be, PoseStack ms, MultiBufferSource buffer, int light,
                               int overlay, float open, float partialTick) {
        if (open < 0.35f) {
            return;
        }
        final ItemStack product = be.item(ArcFurnaceBE.SLOT_OUT);
        if (product.isEmpty()) {
            return;
        }
        final float shown = (open - 0.35f) / 0.65f;
        final float t = (be.clientAge() + partialTick) * 0.04f;
        ms.pushPose();
        ms.translate(8.0f * PX, (5.0f + Mth.sin(t * 1.6f) * 0.25f) * PX, -1.6f * PX);
        ms.mulPose(Axis.YP.rotationDegrees(t * 30.0f));
        ms.scale(0.6f * shown, 0.6f * shown, 0.6f * shown);
        itemRenderer.renderStatic(product, ItemDisplayContext.GROUND, light, overlay, ms, buffer,
                be.getLevel(), (int) be.getBlockPos().asLong());
        ms.popPose();
    }

    private void renderGlow(ArcFurnaceBE be, PoseStack ms, MultiBufferSource buffer, float heat,
                            boolean arc, boolean starved, boolean running, float tipY, float open,
                            float partialTick) {
        final VertexConsumer vc = Buffers.live(buffer, RenderType.lightning());
        final Matrix4f mat = ms.last().pose();
        final float time = be.clientAge() + partialTick;

        if (heat > 0.02f) {
            final float flicker = arc
                    ? 0.82f + 0.11f * Mth.sin(time * 0.9f) + 0.07f * Mth.sin(time * 2.3f)
                    : 0.94f + 0.06f * Mth.sin(time * 0.12f);
            final float level = heat * flicker;
            final float[] pool = starved ? SICK : mix(HOT, HOT_HI, Math.max(0.0f, heat - 0.4f));

            flatXZ(vc, mat, 4.4f * PX, 11.6f * PX, 4.4f * PX, 11.6f * PX, (HEARTH_Y + 0.02f) * PX,
                    pool, 0.30f + 0.60f * level);
            flatXZ(vc, mat, 5.8f * PX, 10.2f * PX, 5.8f * PX, 10.2f * PX, (HEARTH_Y + 0.05f) * PX,
                    mix(pool, ARC_WHITE, level * 0.35f), 0.25f + 0.55f * level);

            final float wall = (0.10f + 0.40f * level);
            wallGlow(vc, mat, POT_LO + 0.03f, POT_HI - 0.03f, HEARTH_Y, HEARTH_Y + 3.4f, pool, wall);

            halo(buffer, ms, 0.5f, (HEARTH_Y + 3.0f) * PX, 0.5f, 12.0f * PX, pool, 0.42f * level);
        }

        if (arc) {
            renderArc(be, buffer, ms, vc, mat, tipY, starved, time);
        }
        renderConsole(vc, mat, running, arc, starved, be.tap());
    }

    private void renderArc(ArcFurnaceBE be, MultiBufferSource buffer, PoseStack ms, VertexConsumer vc,
                           Matrix4f mat, float tipY, boolean starved, float time) {
        final float[] core = starved ? AMBER : ARC_WHITE;
        final float[] fringe = starved ? SICK : ARC_BLUE;
        for (int i = 0; i < ELECTRODES.length; i++) {
            jitter.setSeed((long) (time / 2.0f) * 31L + i * 977L);
            if (starved && jitter.nextInt(3) == 0) {
                continue;
            }
            float x = ELECTRODES[i][0];
            float z = ELECTRODES[i][1];
            float y = tipY;
            final int steps = 4;
            final float drop = (tipY - HEARTH_Y - 0.3f) / steps;
            for (int s = 0; s < steps; s++) {
                final float nx = ELECTRODES[i][0] + (jitter.nextFloat() - 0.5f) * 1.3f;
                final float nz = ELECTRODES[i][1] + (jitter.nextFloat() - 0.5f) * 1.3f;
                final float ny = y - drop;
                filament(vc, mat, x, y, z, nx, ny, nz, core, fringe);
                x = nx;
                y = ny;
                z = nz;
            }
            halo(buffer, ms, x * PX, (HEARTH_Y + 0.4f) * PX, z * PX, 4.5f * PX, core,
                    starved ? 0.4f : 0.75f);
        }
    }

    private void renderConsole(VertexConsumer vc, Matrix4f mat, boolean running, boolean arc,
                               boolean starved, int tap) {
        final float y = (DECK_TOP + 0.02f) * PX;
        final float[] power = !running ? DEAD : (starved ? AMBER : GREEN);
        final float[] arcLamp = !arc ? DEAD : (starved ? SICK : ARC_WHITE);
        lamp(vc, mat, CONSOLE_LAMPS[0], y, power, running ? 0.95f : 0.35f);
        lamp(vc, mat, CONSOLE_LAMPS[1], y, arcLamp, arc ? 0.95f : 0.35f);
        for (int i = 0; i < CONSOLE_BARS.length; i++) {
            final boolean on = i < tap;
            final float[] col = !on ? DEAD : (!running ? BRASS : (starved ? AMBER : ARC_BLUE));
            lamp(vc, mat, CONSOLE_BARS[i], y, col, on ? 0.9f : 0.3f);
        }
    }

    private void lamp(VertexConsumer vc, Matrix4f mat, float[] span, float y, float[] col, float a) {
        flatXZ(vc, mat, span[0] * PX, span[1] * PX, CONSOLE_Z0 * PX, CONSOLE_Z1 * PX, y, col, a);
    }

    private static void filament(VertexConsumer vc, Matrix4f mat, float x0, float y0, float z0,
                                 float x1, float y1, float z1, float[] core, float[] fringe) {
        final float w = 0.055f;

        quad(vc, mat, (x0 - w) * PX, y0 * PX, z0 * PX, (x0 + w) * PX, y0 * PX, z0 * PX,
                (x1 + w) * PX, y1 * PX, z1 * PX, (x1 - w) * PX, y1 * PX, z1 * PX, core, 0.95f);
        quad(vc, mat, x0 * PX, y0 * PX, (z0 - w) * PX, x0 * PX, y0 * PX, (z0 + w) * PX,
                x1 * PX, y1 * PX, (z1 + w) * PX, x1 * PX, y1 * PX, (z1 - w) * PX, core, 0.95f);

        final float h = w * 4.0f;
        quad(vc, mat, (x0 - h) * PX, y0 * PX, z0 * PX, (x0 + h) * PX, y0 * PX, z0 * PX,
                (x1 + h) * PX, y1 * PX, z1 * PX, (x1 - h) * PX, y1 * PX, z1 * PX, fringe, 0.22f);
        quad(vc, mat, x0 * PX, y0 * PX, (z0 - h) * PX, x0 * PX, y0 * PX, (z0 + h) * PX,
                x1 * PX, y1 * PX, (z1 + h) * PX, x1 * PX, y1 * PX, (z1 - h) * PX, fringe, 0.22f);
    }

    private static void flatXZ(VertexConsumer vc, Matrix4f mat, float x0, float x1, float z0, float z1,
                               float y, float[] col, float a) {
        quad(vc, mat, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, col, a);
    }

    private static void flat(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1,
                             float z, float[] col, float a) {
        quad(vc, mat, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, col, a);
    }

    private static void wallGlow(VertexConsumer vc, Matrix4f mat, float lo, float hi, float y0,
                                 float y1, float[] col, float a) {
        final float l = lo * PX;
        final float h = hi * PX;
        final float b = y0 * PX;
        final float t = y1 * PX;
        quad(vc, mat, l, b, l, h, b, l, h, t, l, l, t, l, col, a);
        quad(vc, mat, l, b, h, h, b, h, h, t, h, l, t, h, col, a);
        quad(vc, mat, l, b, l, l, b, h, l, t, h, l, t, l, col, a);
        quad(vc, mat, h, b, l, h, b, h, h, t, h, h, t, l, col, a);
    }

    private static void halo(MultiBufferSource buffer, PoseStack ms, float x, float y, float z,
                             float radius, float[] col, float a) {
        if (a <= 0.01f) {
            return;
        }
        final VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(HALO));
        final Matrix4f mat = ms.last().pose();
        final int alpha = Math.round(255 * Math.min(1.0f, a));
        final int r = Math.round(255 * col[0]);
        final int g = Math.round(255 * col[1]);
        final int b = Math.round(255 * col[2]);

        textured(vc, mat, x - radius, y, z - radius, x + radius, y, z - radius,
                x + radius, y, z + radius, x - radius, y, z + radius, r, g, b, alpha);
        textured(vc, mat, x - radius, y - radius, z, x + radius, y - radius, z,
                x + radius, y + radius, z, x - radius, y + radius, z, r, g, b, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float[] col, float a) {
        if (a <= 0.004f) {
            return;
        }
        final float r = col[0];
        final float g = col[1];
        final float b = col[2];
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
    }

    private static void textured(VertexConsumer vc, Matrix4f m,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3,
                                 int r, int g, int b, int a) {
        vertex(vc, m, x0, y0, z0, 0.0f, 0.0f, r, g, b, a);
        vertex(vc, m, x1, y1, z1, 1.0f, 0.0f, r, g, b, a);
        vertex(vc, m, x2, y2, z2, 1.0f, 1.0f, r, g, b, a);
        vertex(vc, m, x3, y3, z3, 0.0f, 1.0f, r, g, b, a);
        vertex(vc, m, x3, y3, z3, 0.0f, 1.0f, r, g, b, a);
        vertex(vc, m, x2, y2, z2, 1.0f, 1.0f, r, g, b, a);
        vertex(vc, m, x1, y1, z1, 1.0f, 0.0f, r, g, b, a);
        vertex(vc, m, x0, y0, z0, 0.0f, 0.0f, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0f, 1.0f, 0.0f);
    }

    private static float[] mix(float[] a, float[] b, float t) {
        final float k = Mth.clamp(t, 0.0f, 1.0f);
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k,
                a[2] + (b[2] - a[2]) * k};
    }

    private static int blend(int packedLight, float level) {
        final int block = LightTexture.block(packedLight);
        final int sky = LightTexture.sky(packedLight);
        return LightTexture.pack(Math.max(block, Math.round(14.0f * level)), sky);
    }

    private static void draw(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                             PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
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

    @Override
    public AABB getRenderBoundingBox(ArcFurnaceBE be) {
        return new AABB(be.getBlockPos()).inflate(2.0);
    }
}
