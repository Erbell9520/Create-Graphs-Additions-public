package com.createstats.renderer;

import com.createstats.block.AirFryerBlock;
import com.createstats.blockentity.AirFryerBE;
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
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// the readout alone would be ten thousand blockstates
@OnlyIn(Dist.CLIENT)
public class AirFryerRenderer implements BlockEntityRenderer<AirFryerBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float DRAWER_TRAVEL = 5.5f;

    private static final float[] PIVOT_IMPELLER = {8.00f, 14.90f, 9.00f};
    private static final float[] PIVOT_ROCKER = {12.50f, 14.85f, 3.40f};
    private static final float[] START_ANCHOR = {5.20f, 11.975f, 2.760f};
    private static final float START_PRESS = 0.14f;

    private static final float[] DIGIT_X = {11.135f, 10.045f, 8.355f, 7.265f};
    private static final float COLON_X = 9.200f;
    private static final float READOUT_Y = 11.975f;
    private static final float READOUT_Z = 2.760f;

    private static final float LAMP_POWER_X = 11.000f;
    private static final float LAMP_FAN_X = 9.200f;
    private static final float LAMP_HEAT_X = 7.400f;
    private static final float LAMP_Y = 10.150f;
    private static final float LAMP_Z = 2.860f;

    private static final float[] FOOD_ANCHOR = {8.00f, 3.60f, 6.20f};
    private static final float FOOD_SCALE = 0.38f;
    private static final float FOOD_LAYER_RISE = 0.035f;
    private static final int FOOD_PER_LAYER = 2;

    private static final int FOOD_MAX_LAYERS = 5;

    private static final float ROCKER_OFF = -14.0f;
    private static final float ROCKER_ON = 14.0f;

    private static final PartialModel[] DIGITS = {
            ModPartialModels.AIR_FRYER_DIGIT_0, ModPartialModels.AIR_FRYER_DIGIT_1,
            ModPartialModels.AIR_FRYER_DIGIT_2, ModPartialModels.AIR_FRYER_DIGIT_3,
            ModPartialModels.AIR_FRYER_DIGIT_4, ModPartialModels.AIR_FRYER_DIGIT_5,
            ModPartialModels.AIR_FRYER_DIGIT_6, ModPartialModels.AIR_FRYER_DIGIT_7,
            ModPartialModels.AIR_FRYER_DIGIT_8, ModPartialModels.AIR_FRYER_DIGIT_9,
    };

    private final ItemRenderer itemRenderer;

    public AirFryerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(AirFryerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof AirFryerBlock)) {
            return;
        }
        final boolean cooking = be.isCooking();

        ms.pushPose();
        applyFacing(ms, state.getValue(AirFryerBlock.FACING));

        renderDrawer(be, partialTick, ms, buffer, state, light, overlay, cooking);

        ms.pushPose();
        ms.translate(PIVOT_IMPELLER[0] * PX, PIVOT_IMPELLER[1] * PX, PIVOT_IMPELLER[2] * PX);
        ms.mulPose(Axis.YP.rotationDegrees(be.fanAngle(partialTick)));
        draw(ms, buffer, state, light, ModPartialModels.AIR_FRYER_IMPELLER);
        ms.popPose();

        ms.pushPose();
        ms.translate(PIVOT_ROCKER[0] * PX, PIVOT_ROCKER[1] * PX, PIVOT_ROCKER[2] * PX);
        ms.mulPose(Axis.XP.rotationDegrees(be.isOn() ? ROCKER_ON : ROCKER_OFF));
        draw(ms, buffer, state, light, ModPartialModels.AIR_FRYER_ROCKER);
        ms.popPose();

        renderFascia(be, ms, buffer, state, light, cooking);
        renderWires(be, ms, buffer, light);

        ms.popPose();
    }

    private void renderDrawer(AirFryerBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                              BlockState state, int light, int overlay, boolean cooking) {
        ms.pushPose();
        ms.translate(0.0f, 0.0f, -be.drawerSlide(partialTick) * DRAWER_TRAVEL * PX);
        draw(ms, buffer, state, light, ModPartialModels.AIR_FRYER_DRAWER);

        draw(ms, buffer, state, cooking ? LightTexture.FULL_BRIGHT : light,
                cooking ? ModPartialModels.AIR_FRYER_WINDOW_ON
                        : ModPartialModels.AIR_FRYER_WINDOW_OFF);
        renderFood(be, ms, buffer, light, overlay);
        ms.popPose();
    }

    private void renderFood(AirFryerBE be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        final ItemStack cooked = be.cookedStack();
        final ItemStack raw = be.rawStack();
        if (cooked.isEmpty() && raw.isEmpty()) {
            return;
        }
        final int cookedLayers = layersFor(cooked);
        final int rawLayers = Math.min(FOOD_MAX_LAYERS - cookedLayers, layersFor(raw));
        final int seed = (int) be.getBlockPos().asLong();
        for (int layer = 0; layer < cookedLayers + rawLayers; layer++) {
            final ItemStack shown = layer < cookedLayers ? cooked : raw;
            ms.pushPose();
            ms.translate(FOOD_ANCHOR[0] * PX,
                    FOOD_ANCHOR[1] * PX + layer * FOOD_LAYER_RISE,
                    FOOD_ANCHOR[2] * PX);
            ms.mulPose(Axis.YP.rotationDegrees(((seed >> (layer * 5)) % 360) + layer * 41.0f));
            ms.mulPose(Axis.XP.rotationDegrees(90.0f));
            ms.scale(FOOD_SCALE, FOOD_SCALE, FOOD_SCALE);
            itemRenderer.renderStatic(shown, ItemDisplayContext.FIXED, light, overlay, ms, buffer,
                    be.getLevel(), seed + layer);
            ms.popPose();
        }
    }

    private static int layersFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return Math.min(FOOD_MAX_LAYERS, 1 + (stack.getCount() - 1) / FOOD_PER_LAYER);
    }

    private void renderFascia(AirFryerBE be, PoseStack ms, MultiBufferSource buffer, BlockState state,
                              int light, boolean cooking) {
        final int lit = LightTexture.FULL_BRIGHT;

        ms.pushPose();
        ms.translate(START_ANCHOR[0] * PX, START_ANCHOR[1] * PX,
                (START_ANCHOR[2] + (be.isPressed() ? START_PRESS : 0.0f)) * PX);
        draw(ms, buffer, state, cooking ? lit : light,
                cooking ? ModPartialModels.AIR_FRYER_START_ON : ModPartialModels.AIR_FRYER_START_OFF);
        ms.popPose();

        lamp(ms, buffer, state, light, LAMP_POWER_X, be.isOn(),
                ModPartialModels.AIR_FRYER_LAMP_GREEN);
        lamp(ms, buffer, state, light, LAMP_FAN_X, be.isRunning(),
                ModPartialModels.AIR_FRYER_LAMP_BLUE);
        lamp(ms, buffer, state, light, LAMP_HEAT_X, cooking,
                ModPartialModels.AIR_FRYER_LAMP_AMBER);

        final int ticks = be.readoutTicks();
        if (ticks < 0) {
            return;
        }
        final int[] digits = clockDigits(ticks);
        for (int i = 0; i < DIGIT_X.length; i++) {
            ms.pushPose();
            ms.translate(DIGIT_X[i] * PX, READOUT_Y * PX, READOUT_Z * PX);
            draw(ms, buffer, state, lit, DIGITS[digits[i]]);
            ms.popPose();
        }
        ms.pushPose();
        ms.translate(COLON_X * PX, READOUT_Y * PX, READOUT_Z * PX);
        final boolean colon = be.colonLit();
        draw(ms, buffer, state, colon ? lit : light,
                colon ? ModPartialModels.AIR_FRYER_COLON_ON : ModPartialModels.AIR_FRYER_COLON_OFF);
        ms.popPose();
    }

    private void lamp(PoseStack ms, MultiBufferSource buffer, BlockState state, int light, float x,
                      boolean on, PartialModel colour) {
        ms.pushPose();
        ms.translate(x * PX, LAMP_Y * PX, LAMP_Z * PX);
        draw(ms, buffer, state, on ? LightTexture.FULL_BRIGHT : light,
                on ? colour : ModPartialModels.AIR_FRYER_LAMP_OFF);
        ms.popPose();
    }

    static int[] clockDigits(int ticks) {
        final int seconds = Math.min(99 * 60 + 59, (Math.max(0, ticks) + 19) / 20);
        final int minutes = seconds / 60;
        final int rest = seconds % 60;
        return new int[]{minutes / 10, minutes % 10, rest / 10, rest % 10};
    }

    private void renderWires(AirFryerBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        if (be.links().isEmpty()) {
            return;
        }
        final net.minecraft.core.BlockPos origin = be.getBlockPos();
        final com.mojang.blaze3d.vertex.VertexConsumer consumer = PowerConnectorRenderer.wireBuffer(buffer);
        final org.joml.Matrix4f matrix = ms.last().pose();
        final net.minecraft.world.phys.Vec3 from = terminalAttachPoint(be.getBlockState());
        for (net.minecraft.core.BlockPos link : be.links()) {
            if (!PowerConnectorRenderer.drawsSpan(be.getLevel(), origin, link)) {
                continue;
            }
            final net.minecraft.world.phys.Vec3 to = PowerConnectorRenderer.attachPoint(be.getLevel(), link, origin);
            PowerConnectorRenderer.drawCable(consumer, matrix, be.getLevel(), origin, link,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    public static net.minecraft.world.phys.Vec3 terminalAttachPoint(@org.jetbrains.annotations.Nullable
                                                                    BlockState state) {
        if (state == null || !(state.getBlock() instanceof AirFryerBlock)) {
            return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        }
        return AirFryerBlock.fromAuthoredFrameLocal(state, 5.4, 4.1, 15.7);
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
    public boolean shouldRenderOffScreen(AirFryerBE be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(AirFryerBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
