package com.createstats.renderer;

import com.createstats.block.DamageObserverBlock;
import com.createstats.blockentity.DamageObserverBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Objects;

@OnlyIn(Dist.CLIENT)
public class DamageObserverRenderer implements BlockEntityRenderer<DamageObserverBE> {
    private static final ResourceLocation BLANK_TEXTURE = Objects.requireNonNull(ResourceLocation.tryParse("create_stats:textures/special/blank.png"));

    public DamageObserverRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DamageObserverBE observer, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = observer.getBlockState();
        if (!(state.getBlock() instanceof DamageObserverBlock)) {
            return;
        }
        Direction facing = state.getValue(DamageObserverBlock.FACING);
        boolean wallMount = state.getValue(DamageObserverBlock.WALL_MOUNT);
        long gameTime = observer.getLevel() != null ? observer.getLevel().getGameTime() : 0L;
        float time = gameTime + partialTick;
        int baseColor = healthColor(observer.getHealthPercent(), observer.getMissingBlocks());

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationFor(facing)));
        poseStack.translate(-0.5, 0.0, -0.5);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(BLANK_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float ledZ = wallMount ? 0.56f : 0.9f;
        float ledY = wallMount ? 0.74f : 0.78f;
        renderPixelStrip(consumer, matrix, normal, ledY, ledZ, time, baseColor, observer.getMissingBlocks());
        renderLensPulse(consumer, matrix, normal, wallMount ? 0.51f : 0.84f, ledZ + 0.02f, time, baseColor, observer.getMissingBlocks());
        poseStack.popPose();
    }

    private static void renderPixelStrip(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float y, float z, float time, int baseColor, int missingBlocks) {
        for (int index = 0; index < 7; index++) {
            int color = pixelColor(baseColor, index, time, missingBlocks);
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            int alpha = 145 + Math.floorMod((int) time + index * 31, 90);
            float x = 0.26f + index * 0.08f;
            HologramRenderHelper.emitCube(consumer, matrix, normal, x, y, z, 0.013f, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        }
    }

    private static void renderLensPulse(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float y, float z, float time, int baseColor, int missingBlocks) {
        int red = baseColor >> 16 & 0xFF;
        int green = baseColor >> 8 & 0xFF;
        int blue = baseColor & 0xFF;
        float pulse = (float) ((Math.sin(time * 0.18f) + 1.0f) * 0.5f);
        float radius = 0.055f + pulse * (missingBlocks > 0 ? 0.025f : 0.014f);
        int alpha = missingBlocks > 0 ? 120 : 82;
        for (int segment = 0; segment < 18; segment++) {
            float angle = (float) (Math.PI * 2.0 * segment / 18.0);
            float x = 0.5f + (float) Math.cos(angle) * radius;
            float dotY = y + (float) Math.sin(angle) * radius;
            HologramRenderHelper.emitCube(consumer, matrix, normal, x, dotY, z, 0.006f, red, green, blue, alpha, HologramRenderHelper.FULL_BRIGHT);
        }
        HologramRenderHelper.emitCube(consumer, matrix, normal, 0.5f, y, z, 0.018f, red, green, blue, 170, HologramRenderHelper.FULL_BRIGHT);
    }

    private static int pixelColor(int baseColor, int index, float time, int missingBlocks) {
        if (missingBlocks > 0 && Math.floorMod((int) time / 4 + index, 3) == 0) {
            return 0xFF5D69;
        }
        if (Math.floorMod((int) time / 5 + index, 5) == 0) {
            return 0x4AD9FF;
        }
        return baseColor;
    }

    private static int rotationFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> 0;
        };
    }

    private static int healthColor(double healthPercent, int missingBlocks) {
        if (healthPercent >= 85.0 && missingBlocks == 0) {
            return 0x62E8A3;
        }
        if (healthPercent >= 55.0) {
            return 0xFFC15E;
        }
        return 0xFF5D69;
    }
}