package com.createstats.renderer;

import com.createstats.block.WaterCoolerBlock;
import com.createstats.blockentity.WaterCoolerBE;
import com.createstats.client.ModPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WaterCoolerRenderer implements BlockEntityRenderer<WaterCoolerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float[] TAP_X = {5.8f, 8.0f, 10.2f};
    private static final float PADDLE_PIVOT_Y = 12.65f;
    private static final float PADDLE_PIVOT_Z = 2.30f;
    private static final float SPOUT_TIP_Y = 9.2f;
    private static final float SPOUT_TIP_Z = 5.3f;

    private static final float PADDLE_REST = 0.0f;

    private static final float PADDLE_PRESSED = 20.0f;

    private static final float[] ROCKER_PIVOT = {2.5f, 7.0f, 2.84f};
    private static final float ROCKER_OFF = -13.0f;
    private static final float ROCKER_ON = 13.0f;

    private static final float LAMP_X = 2.5f;
    private static final float LAMP_Z = 2.82f;
    private static final float[] LAMP_Y = {12.4f, 11.0f, 9.6f};

    private static final float[] CUP_ANCHOR = {8.0f, 4.4f, 5.4f};

    private static final float FILL_BASE = 0.42f;

    private static final float WATER_BODY_BASE = 21.5f;
    private static final float WATER_BODY_TOP = 29.2f;

    private static final float BUBBLE_Z = 4.05f;
    private static final int BUBBLE_COUNT = 3;

    private static final PartialModel[] MUGS = {
            ModPartialModels.COFFEE_MUG_CERAMIC,
            ModPartialModels.COFFEE_MUG_ENAMEL,
            ModPartialModels.COFFEE_MUG_STEEL,
    };
    private static final PartialModel[] PADDLES = {
            ModPartialModels.COOLER_PADDLE_SPRING,
            ModPartialModels.COOLER_PADDLE_CHILLED,
            ModPartialModels.COOLER_PADDLE_SPARKLING,
    };
    private static final PartialModel[] STREAMS = {
            ModPartialModels.COOLER_STREAM_SPRING,
            ModPartialModels.COOLER_STREAM_CHILLED,
            ModPartialModels.COOLER_STREAM_SPARKLING,
    };
    private static final PartialModel[] FILLS = {
            ModPartialModels.COOLER_FILL_SPRING,
            ModPartialModels.COOLER_FILL_CHILLED,
            ModPartialModels.COOLER_FILL_SPARKLING,
    };

    public WaterCoolerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(WaterCoolerBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links())
                .expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    public boolean shouldRenderOffScreen(WaterCoolerBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }

    @Override
    public void render(WaterCoolerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof WaterCoolerBlock)
                || state.getValue(WaterCoolerBlock.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        ms.pushPose();
        applyFacing(ms, state.getValue(WaterCoolerBlock.FACING));

        final int upperLight = lightAbove(be, light);
        renderWater(be, partialTick, ms, buffer, state, upperLight);
        renderPaddles(be, partialTick, ms, buffer, state, light);
        renderLamps(be, ms, buffer, state, light);

        pivoted(ms, buffer, state, light, ModPartialModels.COOLER_ROCKER, ROCKER_PIVOT,
                Axis.XP.rotationDegrees(Mth.lerp(be.switchSwing(partialTick), ROCKER_OFF, ROCKER_ON)));

        renderTray(be, partialTick, ms, buffer, state, light);
        renderWires(be, ms, buffer, light);

        ms.popPose();
    }

    private void renderWater(WaterCoolerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                             BlockState state, int light) {
        final float fill = be.bottleFill(partialTick);
        if (fill <= 0.02f) {
            return;
        }
        draw(ms, buffer, state, light, ModPartialModels.COOLER_WATER_TAPER);

        ms.pushPose();
        ms.translate(0.0f, WATER_BODY_BASE * PX, 0.0f);
        ms.scale(1.0f, fill, 1.0f);
        ms.translate(0.0f, -WATER_BODY_BASE * PX, 0.0f);
        draw(ms, buffer, state, light, ModPartialModels.COOLER_WATER_BODY);
        ms.popPose();

        if (be.isPouring()) {
            renderGlug(be, partialTick, ms, buffer, state, light, fill);
        }
    }

    private void renderGlug(WaterCoolerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                            BlockState state, int light, float fill) {
        final float phase = be.glugPhase(partialTick);
        final float surface = WATER_BODY_BASE + (WATER_BODY_TOP - WATER_BODY_BASE) * fill;
        for (int i = 0; i < BUBBLE_COUNT; i++) {
            final float offset = (phase + i / (float) BUBBLE_COUNT) % 1.0f;
            final float y = WATER_BODY_BASE + 0.6f + offset * (surface - WATER_BODY_BASE - 1.2f);
            if (y >= surface - 0.6f) {
                continue;
            }

            final float x = 8.0f + Mth.sin((offset + i) * 6.283f) * 1.6f;
            ms.pushPose();
            ms.translate(x * PX, y * PX, BUBBLE_Z * PX);
            final float scale = 0.6f + 0.4f * Mth.sin(offset * 3.14159f);
            ms.scale(scale, scale, scale);
            draw(ms, buffer, state, light, ModPartialModels.COOLER_BUBBLE);
            ms.popPose();
        }
    }

    private void renderPaddles(WaterCoolerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                               BlockState state, int light) {
        final WaterCoolerBlock.Tap running = be.isPouring() ? be.pouringTap() : null;
        for (int i = 0; i < PADDLES.length; i++) {
            final boolean pressed = running != null && running.ordinal() == i;
            pivoted(ms, buffer, state, light, PADDLES[i],
                    new float[]{TAP_X[i], PADDLE_PIVOT_Y, PADDLE_PIVOT_Z},
                    Axis.XP.rotationDegrees(pressed ? PADDLE_PRESSED : PADDLE_REST));
        }
        if (running != null) {
            ms.pushPose();
            ms.translate(TAP_X[running.ordinal()] * PX, SPOUT_TIP_Y * PX, SPOUT_TIP_Z * PX);
            draw(ms, buffer, state, light, STREAMS[running.ordinal()]);
            ms.popPose();
        }
    }

    private void renderLamps(WaterCoolerBE be, PoseStack ms, MultiBufferSource buffer, BlockState state,
                             int light) {
        final PartialModel[] beads = {
                !be.isOn() ? ModPartialModels.COOLER_LAMP_OFF
                        : be.isCold() ? ModPartialModels.COOLER_LAMP_CYAN
                        : be.isChilling() ? ModPartialModels.COOLER_LAMP_AMBER
                        : ModPartialModels.COOLER_LAMP_OFF,

                be.isOn() && be.isCarbonatorReady()
                        ? ModPartialModels.COOLER_LAMP_WHITE : ModPartialModels.COOLER_LAMP_OFF,

                be.isOn() && be.hasPower()
                        ? ModPartialModels.COOLER_LAMP_GREEN : ModPartialModels.COOLER_LAMP_OFF,
        };
        for (int i = 0; i < beads.length; i++) {
            final boolean lit = beads[i] != ModPartialModels.COOLER_LAMP_OFF;
            ms.pushPose();
            ms.translate(LAMP_X * PX, LAMP_Y[i] * PX, LAMP_Z * PX);
            draw(ms, buffer, state, lit ? LightTexture.FULL_BRIGHT : light, beads[i]);
            ms.popPose();
        }
    }

    private void renderTray(WaterCoolerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                            BlockState state, int light) {
        if (!be.hasMug()) {
            return;
        }
        ms.pushPose();
        ms.translate(CUP_ANCHOR[0] * PX, CUP_ANCHOR[1] * PX, CUP_ANCHOR[2] * PX);

        final float fill = be.mugFill(partialTick);
        if (fill > 0.02f) {
            final int which = drinkIndex(be);
            ms.pushPose();
            ms.translate(0.0f, FILL_BASE * PX, 0.0f);
            ms.scale(1.0f, fill, 1.0f);
            ms.translate(0.0f, -FILL_BASE * PX, 0.0f);
            draw(ms, buffer, state, light, FILLS[which]);
            ms.popPose();
        }
        draw(ms, buffer, state, light, MUGS[Mth.clamp(be.mug(), 0, MUGS.length - 1)]);
        ms.popPose();
    }

    private static int drinkIndex(WaterCoolerBE be) {
        final WaterCoolerBlock.Tap running = be.pouringTap();
        if (running != null) {
            return running.ordinal();
        }
        return switch (be.trayContent()) {
            case CHILLED_WATER -> 1;
            case SPARKLING_WATER -> 2;
            default -> 0;
        };
    }

    private void renderWires(WaterCoolerBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        if (be.links().isEmpty()) {
            return;
        }
        final BlockPos origin = be.getBlockPos();
        final com.mojang.blaze3d.vertex.VertexConsumer consumer = PowerConnectorRenderer.wireBuffer(buffer);
        final org.joml.Matrix4f matrix = ms.last().pose();
        final Vec3 from = terminalAttachPoint(be.getBlockState());
        for (BlockPos link : be.links()) {
            if (!PowerConnectorRenderer.drawsSpan(be.getLevel(), origin, link)) {
                continue;
            }
            final Vec3 to = PowerConnectorRenderer.attachPoint(be.getLevel(), link, origin);
            PowerConnectorRenderer.drawCable(consumer, matrix, be.getLevel(), origin, link,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    public static Vec3 terminalAttachPoint(@org.jetbrains.annotations.Nullable BlockState state) {
        if (state == null || !(state.getBlock() instanceof WaterCoolerBlock)) {
            return new Vec3(0.5, 0.5, 0.5);
        }
        return WaterCoolerBlock.fromAuthoredFrameLocal(state, 8.0, 6.6, 13.25);
    }

    private static int lightAbove(WaterCoolerBE be, int fallback) {
        if (be.getLevel() == null) {
            return fallback;
        }
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(
                be.getLevel(), be.getBlockPos().above());
    }

    private static void pivoted(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                                PartialModel model, float[] pivot, org.joml.Quaternionf rotation) {
        ms.pushPose();
        ms.translate(pivot[0] * PX, pivot[1] * PX, pivot[2] * PX);
        ms.mulPose(rotation);
        draw(ms, buffer, state, light, model);
        ms.popPose();
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
}
