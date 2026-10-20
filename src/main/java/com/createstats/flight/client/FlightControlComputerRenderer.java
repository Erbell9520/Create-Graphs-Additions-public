package com.createstats.flight.client;

import com.createstats.CreateStats;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.renderer.HologramRenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;

@OnlyIn(Dist.CLIENT)
public class FlightControlComputerRenderer implements BlockEntityRenderer<FlightControlComputerBE> {
    private static final double VIEW_DISTANCE = 96.0;
    private static final float HALF_WIDTH = 0.15f;
    private static final ResourceLocation BLANK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        CreateStats.MOD_ID, "textures/special/blank.png");

    public FlightControlComputerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    @SuppressWarnings("null")
    public void render(@Nonnull FlightControlComputerBE fcc, float partialTick, @Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FlightControlComputerBE.RouteArrowSnapshot arrow = fcc.routeArrowSnapshot();
        if (arrow == null || !closeEnough(fcc)) return;

        VertexConsumer glow = buffer.getBuffer(RenderType.entityTranslucentEmissive(BLANK_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        BlockPos originBlock = fcc.getBlockPos();
        Vec3 start = local(arrow.origin(), originBlock);
        Vec3 control = local(arrow.control(), originBlock);
        Vec3 end = local(arrow.end(), originBlock);

        int red = arrow.hold() ? 255 : 64;
        int green = arrow.hold() ? 174 : 225;
        int blue = arrow.hold() ? 64 : 255;
        int alpha = 150;
        Vec3 previous = start;
        for (int i = 1; i <= 6; i++) {
            double t = i / 6.0;
            Vec3 next = quadratic(start, control, end, t);
            emitThickSegment(glow, matrix, normal, previous, next, red, green, blue, alpha);
            previous = next;
        }
        renderArrowHead(glow, matrix, normal, start, end, red, green, blue, alpha);
    }

    private static void emitThickSegment(VertexConsumer glow, Matrix4f matrix, Matrix3f normal, Vec3 first, Vec3 second,
                                         int red, int green, int blue, int alpha) {
        HologramRenderHelper.emitLine3D(glow, matrix, normal, (float) first.x, (float) first.y, (float) first.z,
            (float) second.x, (float) second.y, (float) second.z, HALF_WIDTH, red, green, blue, alpha,
            HologramRenderHelper.FULL_BRIGHT);
        HologramRenderHelper.emitLine3D(glow, matrix, normal, (float) first.x, (float) (first.y + 0.08), (float) first.z,
            (float) second.x, (float) (second.y + 0.08), (float) second.z, HALF_WIDTH * 0.72f, red, green, blue,
            Math.min(255, alpha + 35), HologramRenderHelper.FULL_BRIGHT);
    }

    private static void renderArrowHead(VertexConsumer glow, Matrix4f matrix, Matrix3f normal, Vec3 start, Vec3 end,
                                        int red, int green, int blue, int alpha) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4) return;
        double nx = dx / length;
        double nz = dz / length;
        double px = -nz;
        double pz = nx;
        Vec3 left = new Vec3(end.x - nx * 0.55 + px * 0.34, end.y, end.z - nz * 0.55 + pz * 0.34);
        Vec3 right = new Vec3(end.x - nx * 0.55 - px * 0.34, end.y, end.z - nz * 0.55 - pz * 0.34);
        emitThickSegment(glow, matrix, normal, left, end, red, green, blue, Math.min(255, alpha + 40));
        emitThickSegment(glow, matrix, normal, right, end, red, green, blue, Math.min(255, alpha + 40));
        HologramRenderHelper.emitCube(glow, matrix, normal, (float) end.x, (float) end.y, (float) end.z, 0.12f,
            red, green, blue, Math.min(255, alpha + 55), HologramRenderHelper.FULL_BRIGHT);
    }

    private static Vec3 quadratic(Vec3 first, Vec3 control, Vec3 end, double t) {
        double inv = 1.0 - t;
        return new Vec3(
            inv * inv * first.x + 2.0 * inv * t * control.x + t * t * end.x,
            inv * inv * first.y + 2.0 * inv * t * control.y + t * t * end.y,
            inv * inv * first.z + 2.0 * inv * t * control.z + t * t * end.z);
    }

    private static Vec3 local(Vec3 world, BlockPos blockPos) {
        return new Vec3(world.x - blockPos.getX(), world.y - blockPos.getY(), world.z - blockPos.getZ());
    }

    private static boolean closeEnough(FlightControlComputerBE fcc) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer == null) return true;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        BlockPos pos = fcc.getBlockPos();
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return camera.distanceToSqr(center) <= VIEW_DISTANCE * VIEW_DISTANCE;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance((int) VIEW_DISTANCE);
    }
}
