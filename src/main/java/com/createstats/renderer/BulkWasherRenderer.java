package com.createstats.renderer;

import com.createstats.block.BulkWasherBlock;
import com.createstats.blockentity.BulkWasherBE;
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

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class BulkWasherRenderer implements BlockEntityRenderer<BulkWasherBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float[] PIVOT_DOOR = {13.4f, 6.3f, 1.0f};
    private static final float[] PIVOT_DRUM = {8.0f, 6.3f, 4.7f};
    private static final float[] WATER_ANCHOR = {8.0f, 3.6f, 4.7f};
    private static final float WATER_FULL = 2.2f;

    private static final float[] BUTTON_X = {13.0f, 11.5f, 10.0f};
    private static final float BUTTON_Y = 13.60f;
    private static final float BUTTON_Z = 1.42f;
    private static final float BUTTON_PRESS = 0.16f;

    private static final float[] PIP_X = {12.5f, 11.5f, 10.5f};
    private static final float PIP_Y = 11.85f;
    private static final float PIP_Z = 1.50f;

    private static final float[] DIGIT_X = {7.75f, 6.50f, 4.75f, 3.50f};
    private static final float COLON_X = 5.625f;
    private static final float READOUT_Y = 13.55f;
    private static final float READOUT_Z = 1.45f;

    private static final float[] LAMP_X = {7.6f, 6.3f, 5.0f};
    private static final float LAMP_Y = 11.85f;
    private static final float LAMP_Z = 1.50f;

    private static final float TUMBLE_RADIUS = 1.05f;
    private static final float TUMBLE_SCALE = 0.22f;
    private static final int TUMBLE_MAX = 5;
    private static final float TUMBLE_Z = 4.3f;

    private static final float DOOR_OPEN = -95.0f;

    private static final PartialModel[] DIGITS = {
            ModPartialModels.BULK_WASHER_DIGIT_0, ModPartialModels.BULK_WASHER_DIGIT_1,
            ModPartialModels.BULK_WASHER_DIGIT_2, ModPartialModels.BULK_WASHER_DIGIT_3,
            ModPartialModels.BULK_WASHER_DIGIT_4, ModPartialModels.BULK_WASHER_DIGIT_5,
            ModPartialModels.BULK_WASHER_DIGIT_6, ModPartialModels.BULK_WASHER_DIGIT_7,
            ModPartialModels.BULK_WASHER_DIGIT_8, ModPartialModels.BULK_WASHER_DIGIT_9,
    };

    private final ItemRenderer itemRenderer;

    public BulkWasherRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BulkWasherBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BulkWasherBlock)) {
            return;
        }
        final boolean running = be.isRunning();

        ms.pushPose();
        applyFacing(ms, state.getValue(BulkWasherBlock.FACING));

        renderDrum(be, partialTick, ms, buffer, state, light, overlay, running);
        renderWater(be, ms, buffer, state, light);
        renderPanel(be, ms, buffer, state, light, running);
        renderWires(be, ms, buffer, light);

        renderDoor(be, partialTick, ms, buffer, state, light, running);

        ms.popPose();
    }

    private void renderDrum(BulkWasherBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                            BlockState state, int light, int overlay, boolean running) {
        final float angle = be.drumAngle(partialTick);
        ms.pushPose();
        ms.translate(PIVOT_DRUM[0] * PX, PIVOT_DRUM[1] * PX, PIVOT_DRUM[2] * PX);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        draw(ms, buffer, state, light, ModPartialModels.BULK_WASHER_DRUM);
        ms.popPose();
        renderTumble(be, ms, buffer, light, overlay, angle);
    }

    private void renderTumble(BulkWasherBE be, PoseStack ms, MultiBufferSource buffer, int light,
                              int overlay, float angle) {
        final int total = be.drumCount();
        if (total <= 0) {
            return;
        }
        final ItemStack washed = firstOf(be.washedStacks());
        final ItemStack pending = firstOf(be.pendingStacks());
        final int shown = Math.min(TUMBLE_MAX, total);
        final int washedShown = Math.min(shown, Math.round(shown * be.washedCount() / (float) total));

        final List<ItemStack> load = new ArrayList<>(shown);
        for (int i = 0; i < shown; i++) {
            final ItemStack pick = i < washedShown ? washed : pending;
            load.add(pick.isEmpty() ? (washed.isEmpty() ? pending : washed) : pick);
        }

        final int seed = (int) be.getBlockPos().asLong();
        for (int i = 0; i < load.size(); i++) {
            final ItemStack shownStack = load.get(i);
            if (shownStack.isEmpty()) {
                continue;
            }
            final float theta = angle + i * (360.0f / load.size());
            final float radius = TUMBLE_RADIUS * (0.55f + 0.45f * (((i * 3) % 4) / 3.0f));
            final double rad = Math.toRadians(theta);
            ms.pushPose();
            ms.translate((PIVOT_DRUM[0] + (float) Math.cos(rad) * radius) * PX,
                    (PIVOT_DRUM[1] + (float) Math.sin(rad) * radius) * PX,
                    TUMBLE_Z * PX);
            ms.mulPose(Axis.ZP.rotationDegrees(theta + i * 37.0f));
            ms.scale(TUMBLE_SCALE, TUMBLE_SCALE, TUMBLE_SCALE);
            itemRenderer.renderStatic(shownStack, ItemDisplayContext.FIXED, light, overlay, ms, buffer,
                    be.getLevel(), seed + i);
            ms.popPose();
        }
    }

    private static ItemStack firstOf(List<ItemStack> side) {
        return side.isEmpty() ? ItemStack.EMPTY : side.get(0);
    }

    private void renderWater(BulkWasherBE be, PoseStack ms, MultiBufferSource buffer, BlockState state,
                             int light) {
        final float level = be.waterFraction();
        if (level <= 0.0f) {
            return;
        }
        ms.pushPose();
        ms.translate(WATER_ANCHOR[0] * PX, WATER_ANCHOR[1] * PX, WATER_ANCHOR[2] * PX);
        ms.scale(1.0f, level * WATER_FULL, 1.0f);
        draw(ms, buffer, state, light, ModPartialModels.BULK_WASHER_WATER);
        ms.popPose();
    }

    private void renderDoor(BulkWasherBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                            BlockState state, int light, boolean running) {
        ms.pushPose();
        ms.translate(PIVOT_DOOR[0] * PX, PIVOT_DOOR[1] * PX, PIVOT_DOOR[2] * PX);
        ms.mulPose(Axis.YP.rotationDegrees(be.doorSwing(partialTick) * DOOR_OPEN));
        draw(ms, buffer, state, light, ModPartialModels.BULK_WASHER_DOOR_FRAME);

        drawGlass(ms, buffer, state, running ? LightTexture.FULL_BRIGHT : light,
                running ? ModPartialModels.BULK_WASHER_GLASS_ON
                        : ModPartialModels.BULK_WASHER_GLASS_OFF);
        ms.popPose();
    }

    private void renderPanel(BulkWasherBE be, PoseStack ms, MultiBufferSource buffer, BlockState state,
                             int light, boolean running) {
        final int lit = LightTexture.FULL_BRIGHT;
        final boolean on = be.isOn();
        final int sunk = be.pressedButton();

        button(ms, buffer, state, light, 0, sunk, on,
                ModPartialModels.BULK_WASHER_BTN_GREEN);
        button(ms, buffer, state, light, 1, sunk, on,
                ModPartialModels.BULK_WASHER_BTN_AMBER);
        button(ms, buffer, state, light, 2, sunk, be.isStarted(),
                ModPartialModels.BULK_WASHER_BTN_BLUE);

        final int steps = on ? be.mode().ordinal() + 1 : 0;
        for (int i = 0; i < PIP_X.length; i++) {
            ms.pushPose();
            ms.translate(PIP_X[i] * PX, PIP_Y * PX, PIP_Z * PX);
            final boolean pipLit = i < steps;
            draw(ms, buffer, state, pipLit ? lit : light,
                    pipLit ? ModPartialModels.BULK_WASHER_PIP_ON
                            : ModPartialModels.BULK_WASHER_PIP_OFF);
            ms.popPose();
        }

        lamp(ms, buffer, state, light, LAMP_X[0], be.isOn(), ModPartialModels.BULK_WASHER_LAMP_GREEN);
        lamp(ms, buffer, state, light, LAMP_X[1], running, ModPartialModels.BULK_WASHER_LAMP_BLUE);
        lamp(ms, buffer, state, light, LAMP_X[2], be.isBlocked(),
                ModPartialModels.BULK_WASHER_LAMP_AMBER);

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
                colon ? ModPartialModels.BULK_WASHER_COLON_ON
                        : ModPartialModels.BULK_WASHER_COLON_OFF);
        ms.popPose();
    }

    private void button(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                        int index, int sunk, boolean on, PartialModel colour) {
        ms.pushPose();
        ms.translate(BUTTON_X[index] * PX, BUTTON_Y * PX,
                (BUTTON_Z + (sunk == index ? BUTTON_PRESS : 0.0f)) * PX);
        draw(ms, buffer, state, on ? LightTexture.FULL_BRIGHT : light,
                on ? colour : ModPartialModels.BULK_WASHER_BTN_OFF);
        ms.popPose();
    }

    private void lamp(PoseStack ms, MultiBufferSource buffer, BlockState state, int light, float x,
                      boolean on, PartialModel colour) {
        ms.pushPose();
        ms.translate(x * PX, LAMP_Y * PX, LAMP_Z * PX);
        draw(ms, buffer, state, on ? LightTexture.FULL_BRIGHT : light,
                on ? colour : ModPartialModels.BULK_WASHER_LAMP_OFF);
        ms.popPose();
    }

    static int[] clockDigits(int ticks) {
        final int seconds = Math.min(99 * 60 + 59, (Math.max(0, ticks) + 19) / 20);
        final int minutes = seconds / 60;
        final int rest = seconds % 60;
        return new int[]{minutes / 10, minutes % 10, rest / 10, rest % 10};
    }

    private void renderWires(BulkWasherBE be, PoseStack ms, MultiBufferSource buffer, int light) {
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
        if (state == null || !(state.getBlock() instanceof BulkWasherBlock)) {
            return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        }
        return BulkWasherBlock.fromAuthoredFrameLocal(state, 5.4, 3.8, 15.9);
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
    public boolean shouldRenderOffScreen(BulkWasherBE be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(BulkWasherBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
