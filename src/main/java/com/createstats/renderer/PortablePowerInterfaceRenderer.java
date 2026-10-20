package com.createstats.renderer;

import com.createstats.blockentity.PortablePowerInterfaceBE;
import com.createstats.client.ModPartialModels;
import com.createstats.power.PortableFeTransfer.Mode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

public class PortablePowerInterfaceRenderer extends SafeBlockEntityRenderer<PortablePowerInterfaceBE> {
    public PortablePowerInterfaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(PortablePowerInterfaceBE be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        final BlockState state = be.getBlockState();
        final float progress = be.extension(partialTicks);
        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        render(state, be.headLit() ? be.mode() : null, progress, null,
                sbb -> sbb.light(light).renderInto(ms, vb));
    }

    public static void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                           ContraptionMatrices matrices, MultiBufferSource buffer) {
        final BlockState state = context.state;
        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        final LerpedFloat animation = PortableStorageInterfaceMovement.getAnimation(context);
        final float progress = animation.getValue(AnimationTickHolder.getPartialTicks());
        final Mode lit = animation.settled() && progress > 0 ? modeOfTarget(context) : null;
        render(state, lit, progress, matrices.getModel(),
                sbb -> sbb.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                        .useLevelLight(context.world, matrices.getWorld())
                        .renderInto(matrices.getViewProjection(), vb));
    }

    private static Mode modeOfTarget(MovementContext context) {
        if (context.world == null || !context.data.contains("WorkingPos")) {
            return Mode.CHARGE;
        }
        final BlockPos pos = NbtUtils.readBlockPos(context.data, "WorkingPos").orElse(null);
        if (pos != null && context.world.getBlockEntity(pos) instanceof PortablePowerInterfaceBE ground) {
            return ground.mode();
        }
        return Mode.CHARGE;
    }

    private static void render(BlockState state, Mode lit, float progress, PoseStack local,
                               Consumer<SuperByteBuffer> draw) {
        final SuperByteBuffer middle = CachedBuffers.partial(middleFor(lit), state);
        final SuperByteBuffer top = CachedBuffers.partial(ModPartialModels.PORTABLE_POWER_INTERFACE_TOP, state);
        if (local != null) {
            middle.transform(local);
            top.transform(local);
        }
        final Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.UP;
        rotateToFacing(middle, facing);
        rotateToFacing(top, facing);
        middle.translate(0, progress * 0.5f + 0.375f, 0);
        top.translate(0, progress, 0);
        draw.accept(middle);
        draw.accept(top);
    }

    private static PartialModel middleFor(Mode lit) {
        if (lit == null) {
            return ModPartialModels.PORTABLE_POWER_INTERFACE_MIDDLE;
        }
        return lit == Mode.DRAW ? ModPartialModels.PORTABLE_POWER_INTERFACE_MIDDLE_DRAW
                : ModPartialModels.PORTABLE_POWER_INTERFACE_MIDDLE_CHARGE;
    }

    private static void rotateToFacing(SuperByteBuffer buffer, Direction facing) {
        buffer.center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 90)
                .uncenter();
    }
}
