package com.createstats.renderer;

import com.createstats.block.DamageObserverBlock;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class DamageObserverRenderer implements BlockEntityRenderer<DamageObserverBE> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float LIFT = 0.02f / 16f;

    public DamageObserverRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(DamageObserverBE be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(DamageObserverBE be) {
        return new AABB(be.getBlockPos()).inflate(be.cover() + 1);
    }

    @Override
    public void render(DamageObserverBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        long clock = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        int colour = colour(be.permille());
        boolean blinkOff = be.lastLossAt() >= 0 && clock - be.lastLossAt() < 60 && (clock / 5) % 2 == 1;
        if (blinkOff) colour = 0xFF3A2020;

        pose.pushPose();
        Direction facing = state.hasProperty(DamageObserverBlock.FACING) ? state.getValue(DamageObserverBlock.FACING) : Direction.NORTH;
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        float a = 7f / 16f;
        float b = 9f / 16f;
        if (state.hasProperty(DamageObserverBlock.WALL_MOUNT) && state.getValue(DamageObserverBlock.WALL_MOUNT)) {
            float z = 3f / 16f + LIFT;
            quad(vc, m, a, a, z, b, a, z, b, b, z, a, b, z, colour);
        } else if (state.hasProperty(DamageObserverBlock.CEILING) && state.getValue(DamageObserverBlock.CEILING)) {
            float y = 13f / 16f - LIFT;
            quad(vc, m, a, y, a, b, y, a, b, y, b, a, y, b, colour);
        } else {
            float y = 3f / 16f + LIFT;
            quad(vc, m, a, y, a, b, y, a, b, y, b, a, y, b, colour);
        }
        pose.popPose();

        if (showsVolume(be)) {
            int r = be.cover();
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(pose, lines, -r, -r, -r, r + 1, r + 1, r + 1,
                    ((colour >> 16) & 0xFF) / 255f, ((colour >> 8) & 0xFF) / 255f, (colour & 0xFF) / 255f, 0.8f);
        }
    }

    private static int colour(int permille) {
        if (permille < 0) return 0xFF8A9490;
        if (permille >= 1000) return 0xFF4CC45A;
        if (permille >= 900) return 0xFFE0A000;
        return 0xFFE0402C;
    }

    private static boolean showsVolume(DamageObserverBE be) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        if (player.getMainHandItem().is(ModBlocks.DAMAGE_OBSERVER.asItem()) || player.getOffhandItem().is(ModBlocks.DAMAGE_OBSERVER.asItem())) {
            return true;
        }
        HitResult hit = mc.hitResult;
        return hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(be.getBlockPos());
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, 255).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, 255).setLight(FULL_BRIGHT);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, 255).setLight(FULL_BRIGHT);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, 255).setLight(FULL_BRIGHT);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, 255).setLight(FULL_BRIGHT);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, 255).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, 255).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, 255).setLight(FULL_BRIGHT);
    }
}
