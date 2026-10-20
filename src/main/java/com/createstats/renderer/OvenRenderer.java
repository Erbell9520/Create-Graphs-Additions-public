package com.createstats.renderer;

import com.createstats.baking.CakeFlavor;
import com.createstats.block.OvenBlock;
import com.createstats.blockentity.OvenBE;
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
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OvenRenderer implements BlockEntityRenderer<OvenBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float HINGE_Y = 1.70f;
    private static final float HINGE_Z = 1.05f;

    private static final float[] TIN_ANCHOR = {8.0f, 5.36f, 7.55f};
    private static final float TIN_H = 1.05f;
    private static final float BAKE_LOW = 0.55f;
    private static final float BAKE_HIGH = 1.95f;

    private static final float DIAL_CX = 12.70f;
    private static final float DIAL_CY = 13.55f;
    private static final float DIAL_Z = 1.30f;
    private static final float[] DIAL_ANGLES = {45.0f, 90.0f, 135.0f};

    private static final float[] BUTTON_X = {9.70f, 8.10f};
    private static final float BUTTON_Y = 13.55f;
    private static final float BUTTON_Z = 1.42f;
    private static final float BUTTON_PRESS = 0.16f;

    private static final float[] DIGIT_X = {6.30f, 5.05f, 3.30f, 2.05f};
    private static final float COLON_X = 4.175f;
    private static final float READOUT_Y = 13.50f;
    private static final float READOUT_Z = 1.45f;

    private static final float DOOR_OPEN = -88.0f;

    private static final PartialModel[] DIGITS = {
            ModPartialModels.OVEN_DIGIT_0, ModPartialModels.OVEN_DIGIT_1,
            ModPartialModels.OVEN_DIGIT_2, ModPartialModels.OVEN_DIGIT_3,
            ModPartialModels.OVEN_DIGIT_4, ModPartialModels.OVEN_DIGIT_5,
            ModPartialModels.OVEN_DIGIT_6, ModPartialModels.OVEN_DIGIT_7,
            ModPartialModels.OVEN_DIGIT_8, ModPartialModels.OVEN_DIGIT_9,
    };

    public OvenRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OvenBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof OvenBlock)) {
            return;
        }
        final boolean hot = be.isHeating();
        final boolean cavityLit = be.isCavityLit();

        ms.pushPose();
        applyFacing(ms, state.getValue(OvenBlock.FACING));

        renderCavity(be, ms, buffer, state, light, hot, cavityLit);
        renderPanel(be, ms, buffer, state, light);
        renderWires(be, ms, buffer, light);

        renderDoor(be, partialTick, ms, buffer, state, light, cavityLit);

        ms.popPose();
    }

    private void renderCavity(OvenBE be, PoseStack ms, MultiBufferSource buffer, BlockState state,
                              int light, boolean hot, boolean cavityLit) {
        draw(ms, buffer, state, hot ? LightTexture.FULL_BRIGHT : light,
                hot ? ModPartialModels.OVEN_ELEMENTS_ON : ModPartialModels.OVEN_ELEMENTS_OFF);

        final ItemStack tin = be.loaded();
        if (tin.isEmpty()) {
            return;
        }
        ms.pushPose();
        ms.translate(TIN_ANCHOR[0] * PX, TIN_ANCHOR[1] * PX, TIN_ANCHOR[2] * PX);
        final int inside = cavityLit ? LightTexture.FULL_BRIGHT : light;
        draw(ms, buffer, state, inside, ModPartialModels.OVEN_TIN);

        final CakeFlavor flavour = flavourOf(tin);
        if (flavour != null) {
            final float done = be.bakeFraction();
            final boolean baked = done >= OvenBE.BROWNING_POINT;
            ms.scale(1.0f, TIN_H * (BAKE_LOW + (BAKE_HIGH - BAKE_LOW) * done), 1.0f);
            draw(ms, buffer, state, inside,
                    (baked ? ModPartialModels.OVEN_CAKE : ModPartialModels.OVEN_BATTER).get(flavour));
        }
        ms.popPose();
    }

    private static CakeFlavor flavourOf(ItemStack tin) {
        for (final CakeFlavor flavour : CakeFlavor.values()) {
            if (tin.is(com.createstats.registry.ModItems.filledCakeMold(flavour).get())) {
                return flavour;
            }
        }
        return null;
    }

    private void renderDoor(OvenBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                            BlockState state, int light, boolean cavityLit) {
        ms.pushPose();
        ms.translate(8.0f * PX, HINGE_Y * PX, HINGE_Z * PX);
        ms.mulPose(Axis.XP.rotationDegrees(be.doorSwing(partialTick) * DOOR_OPEN));
        draw(ms, buffer, state, light, ModPartialModels.OVEN_DOOR);
        drawGlass(ms, buffer, state, cavityLit ? LightTexture.FULL_BRIGHT : light,
                cavityLit ? ModPartialModels.OVEN_GLASS_ON : ModPartialModels.OVEN_GLASS_OFF);
        ms.popPose();
    }

    private void renderPanel(OvenBE be, PoseStack ms, MultiBufferSource buffer, BlockState state,
                             int light) {
        final int lit = LightTexture.FULL_BRIGHT;
        final int sunk = be.pressedButton();

        ms.pushPose();
        ms.translate(DIAL_CX * PX, DIAL_CY * PX, DIAL_Z * PX);
        ms.mulPose(Axis.ZP.rotationDegrees(knobAngle(be)));
        draw(ms, buffer, state, light, ModPartialModels.OVEN_KNOB);
        ms.popPose();

        button(ms, buffer, state, light, 0, sunk, be.isStarted(), ModPartialModels.OVEN_BTN_GREEN);
        button(ms, buffer, state, light, 1, sunk, be.isLampOn(), ModPartialModels.OVEN_BTN_AMBER);

        final int left = be.remainingTicks();
        final boolean counting = left >= 0;
        final int[] digits = counting ? clockDigits(left) : temperatureDigits(be.celsius());
        for (int i = 0; i < DIGIT_X.length; i++) {
            ms.pushPose();
            ms.translate(DIGIT_X[i] * PX, READOUT_Y * PX, READOUT_Z * PX);
            final int value = digits[i];
            draw(ms, buffer, state, lit,
                    value < 0 ? ModPartialModels.OVEN_DIGIT_BLANK : DIGITS[value]);
            ms.popPose();
        }
        ms.pushPose();
        ms.translate(COLON_X * PX, READOUT_Y * PX, READOUT_Z * PX);

        draw(ms, buffer, state, counting ? lit : light,
                counting ? ModPartialModels.OVEN_COLON_ON : ModPartialModels.OVEN_COLON_OFF);
        ms.popPose();
    }

    private static float knobAngle(OvenBE be) {
        final float position = be.knobPosition(0.0f);
        final int low = Mth.clamp((int) Math.floor(position), 0, DIAL_ANGLES.length - 1);
        final int high = Mth.clamp(low + 1, 0, DIAL_ANGLES.length - 1);
        final float t = Mth.clamp(position - low, 0.0f, 1.0f);
        return Mth.lerp(t, DIAL_ANGLES[low], DIAL_ANGLES[high]) - 90.0f;
    }

    private void button(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                        int index, int sunk, boolean on, PartialModel colour) {
        ms.pushPose();
        ms.translate(BUTTON_X[index] * PX, BUTTON_Y * PX,
                (BUTTON_Z + (sunk == index ? BUTTON_PRESS : 0.0f)) * PX);
        draw(ms, buffer, state, on ? LightTexture.FULL_BRIGHT : light,
                on ? colour : ModPartialModels.OVEN_BTN_OFF);
        ms.popPose();
    }

    static int[] clockDigits(int ticks) {
        final int seconds = Math.min(99 * 60 + 59, (Math.max(0, ticks) + 19) / 20);
        final int minutes = seconds / 60;
        final int rest = seconds % 60;
        return new int[]{minutes / 10, minutes % 10, rest / 10, rest % 10};
    }

    static int[] temperatureDigits(int celsius) {
        final int value = Mth.clamp(celsius, 0, 9999);
        final int[] out = new int[4];
        int rest = value;
        for (int i = out.length - 1; i >= 0; i--) {
            if (rest == 0 && i != out.length - 1) {
                out[i] = -1;
            } else {
                out[i] = rest % 10;
                rest /= 10;
            }
        }
        return out;
    }

    private void renderWires(OvenBE be, PoseStack ms, MultiBufferSource buffer, int light) {
        if (be.links().isEmpty()) {
            return;
        }
        final net.minecraft.core.BlockPos origin = be.getBlockPos();
        final com.mojang.blaze3d.vertex.VertexConsumer consumer =
                PowerConnectorRenderer.wireBuffer(buffer);
        final org.joml.Matrix4f matrix = ms.last().pose();
        final net.minecraft.world.phys.Vec3 from = terminalAttachPoint(be.getBlockState());
        for (net.minecraft.core.BlockPos link : be.links()) {
            if (!PowerConnectorRenderer.drawsSpan(be.getLevel(), origin, link)) {
                continue;
            }
            final net.minecraft.world.phys.Vec3 to =
                    PowerConnectorRenderer.attachPoint(be.getLevel(), link, origin);
            PowerConnectorRenderer.drawCable(consumer, matrix, be.getLevel(), origin, link,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    public static net.minecraft.world.phys.Vec3 terminalAttachPoint(
            @org.jetbrains.annotations.Nullable BlockState state) {
        if (state == null || !(state.getBlock() instanceof OvenBlock)) {
            return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        }
        return OvenBlock.fromAuthoredFrameLocal(state, 6.0, 3.8, 15.9);
    }

    private static void draw(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                             PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    private static void drawGlass(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                                  PartialModel model) {
        CachedBuffers.partial(model, state)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));
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
    public boolean shouldRenderOffScreen(OvenBE be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(OvenBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
