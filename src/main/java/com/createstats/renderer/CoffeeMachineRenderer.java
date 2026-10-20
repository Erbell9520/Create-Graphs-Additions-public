package com.createstats.renderer;

import com.createstats.block.CoffeeMachineBlock;
import com.createstats.block.MugContent;
import com.createstats.blockentity.CoffeeMachineBE;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// blockstate animation snaps
@OnlyIn(Dist.CLIENT)
public class CoffeeMachineRenderer implements BlockEntityRenderer<CoffeeMachineBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final float[] PIVOT_LEVER = {10.6f, 13.45f, 7.6f};
    private static final float[] PIVOT_NEEDLE = {4.6f, 9.7f, 1.88f};
    private static final float[] PIVOT_WAND = {14.5f, 9.9f, 6.1f};
    private static final float[] PIVOT_ROCKER = {4.5f, 5.5f, 2.35f};
    private static final float[] PIVOT_PORTAFILTER = {10.6f, 9.0f, 5.1f};
    private static final float[] CUP_ANCHOR = {10.6f, 3.35f, 5.1f};

    private static final float LEVER_REST = 40.0f;
    private static final float LEVER_PULLED = -100.0f;
    private static final float NEEDLE_OFF = -110.0f;
    private static final float NEEDLE_ON = 26.0f;
    private static final float WAND_STOW = 8.0f;
    private static final float WAND_OUT = 42.0f;
    private static final float WAND_TILT_Z = 16.0f;
    private static final float ROCKER_OFF = -13.0f;
    private static final float ROCKER_ON = 13.0f;
    private static final float PF_LOCKED = 0.0f;
    private static final float PF_LOOSE = 22.0f;

    private static final float BREW_BASE = 0.40f;

    private static final PartialModel[] MUGS = {
            ModPartialModels.COFFEE_MUG_CERAMIC,
            ModPartialModels.COFFEE_MUG_ENAMEL,
            ModPartialModels.COFFEE_MUG_STEEL,
    };

    public CoffeeMachineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CoffeeMachineBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                       int light, int overlay) {
        final BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CoffeeMachineBlock)) {
            return;
        }

        ms.pushPose();
        applyFacing(ms, state.getValue(CoffeeMachineBlock.FACING));

        final float leverAngle = Mth.lerp(be.leverSwing(partialTick), LEVER_REST, LEVER_PULLED);
        pivoted(ms, buffer, state, light, ModPartialModels.COFFEE_LEVER, PIVOT_LEVER,
                Axis.XP.rotationDegrees(leverAngle));

        final float pressure = be.pressure(partialTick);
        pivoted(ms, buffer, state, light, ModPartialModels.COFFEE_NEEDLE, PIVOT_NEEDLE,
                Axis.ZP.rotationDegrees(Mth.lerp(pressure, NEEDLE_OFF, NEEDLE_ON)));

        ms.pushPose();
        ms.translate(PIVOT_WAND[0] * PX, PIVOT_WAND[1] * PX, PIVOT_WAND[2] * PX);
        ms.mulPose(Axis.ZP.rotationDegrees(WAND_TILT_Z));
        ms.mulPose(Axis.XP.rotationDegrees(Mth.lerp(be.wandSwing(partialTick), WAND_STOW, WAND_OUT)));
        draw(ms, buffer, state, light, ModPartialModels.COFFEE_WAND);
        ms.popPose();

        pivoted(ms, buffer, state, light, ModPartialModels.COFFEE_ROCKER, PIVOT_ROCKER,
                Axis.XP.rotationDegrees(be.isOn() ? ROCKER_ON : ROCKER_OFF));

        final float twist = Mth.lerp(be.lockSwing(partialTick), PF_LOOSE, PF_LOCKED);
        pivoted(ms, buffer, state, light, ModPartialModels.COFFEE_PORTAFILTER, PIVOT_PORTAFILTER,
                Axis.YP.rotationDegrees(twist));

        final boolean on = be.isOn();
        draw(ms, buffer, state, on ? LightTexture.FULL_BRIGHT : light,
                on ? ModPartialModels.COFFEE_LAMP_ON : ModPartialModels.COFFEE_LAMP_OFF);

        if (be.hasMug()) {
            renderMug(be, partialTick, ms, buffer, state, light);
        }
        if (be.isPouring()) {
            draw(ms, buffer, state, light, ModPartialModels.COFFEE_STREAM);
        }
        renderWires(be, ms, buffer, light);

        ms.popPose();
    }

    private void renderMug(CoffeeMachineBE be, float partialTick, PoseStack ms, MultiBufferSource buffer,
                           BlockState state, int light) {
        final int variant = Mth.clamp(be.mug(), 0, MUGS.length - 1);
        ms.pushPose();
        ms.translate(CUP_ANCHOR[0] * PX, CUP_ANCHOR[1] * PX, CUP_ANCHOR[2] * PX);

        final MugContent content = be.trayContent();
        if (content == MugContent.MILK || content == MugContent.LATTE) {
            draw(ms, buffer, state, light, content == MugContent.LATTE
                    ? ModPartialModels.COFFEE_FILL_LATTE : ModPartialModels.COFFEE_FILL_MILK);
        } else {
            final float fill = be.displayFill(partialTick);
            if (fill > 0.02f) {
                ms.pushPose();
                ms.translate(0.0f, BREW_BASE * PX, 0.0f);
                ms.scale(1.0f, fill, 1.0f);
                ms.translate(0.0f, -BREW_BASE * PX, 0.0f);
                draw(ms, buffer, state, light, ModPartialModels.COFFEE_BREW);
                ms.popPose();
            }
        }
        draw(ms, buffer, state, light, MUGS[variant]);
        ms.popPose();
    }

    private void renderWires(CoffeeMachineBE be, PoseStack ms, MultiBufferSource buffer, int light) {
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
            PowerConnectorRenderer.drawCable(consumer, matrix,
                    (float) from.x, (float) from.y, (float) from.z,
                    (float) (link.getX() - origin.getX() + to.x),
                    (float) (link.getY() - origin.getY() + to.y),
                    (float) (link.getZ() - origin.getZ() + to.z),
                    light, be.tierOf(link));
        }
    }

    public static net.minecraft.world.phys.Vec3 terminalAttachPoint(@org.jetbrains.annotations.Nullable
                                                                    BlockState state) {
        if (state == null || !(state.getBlock() instanceof CoffeeMachineBlock)) {
            return new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5);
        }
        return CoffeeMachineBlock.fromAuthoredFrameLocal(state, 11.2, 5.7, 15.9);
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

    @Override
    public boolean shouldRenderOffScreen(com.createstats.blockentity.CoffeeMachineBE be) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(com.createstats.blockentity.CoffeeMachineBE be) {
        return PowerConnectorRenderer.wireBounds(be.getBlockPos(), be.links());
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
