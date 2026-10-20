package com.createstats.powertrack.client;

import com.createstats.powertrack.CollectorGeometry;
import com.createstats.powertrack.CurrentCollectorBE;
import com.createstats.powertrack.core.CollectorPose;
import com.createstats.powertrack.core.CollectorSwing;
import com.createstats.powertrack.core.ShoeSlide;
import com.createstats.powertrack.core.SlideChase;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public class CurrentCollectorRenderer extends SafeBlockEntityRenderer<CurrentCollectorBE> {
    public CurrentCollectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(CurrentCollectorBE be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        final BlockState state = be.getBlockState();
        final BlockPos pos = be.getBlockPos();
        Vec3 centre = Vec3.atCenterOf(pos);
        int sign = 1;

        final ClientRail.SceneRail scene = ClientRail.scene(level);
        double override = Double.NaN;
        if (scene != null) {
            centre = centre.add(scene.shift(pos, partialTicks));
            sign = scene.trailSign(pos) < 0 ? -1 : 1;
            override = scene.angle(pos, partialTicks);
        }
        final Direction.Axis axis = placedAxis(level, centre, state);
        final Vec3 trail = CollectorGeometry.trailLocal(axis, sign);
        final Vec3 side = CollectorGeometry.trailLocal(CollectorGeometry.across(axis), 1);
        final CollectorGeometry.Placement placed = ClientRail.place(level, centre, trail, side, 0.0, false);
        final double angle = Double.isNaN(override) ? placed.rest().angleDeg() : override;
        final double slide = be.drawnSlide.step(AnimationTickHolder.getRenderTime(level) / 20.0, placed.slide());
        final VertexConsumer vc = buffer.getBuffer(RenderType.cutoutMipped());
        draw(state, axis, sign, angle, slide, null, sbb -> sbb.light(light).renderInto(ms, vc));
    }

    public static Direction.Axis placedAxis(Level level, Vec3 centre, BlockState state) {
        final Boolean alongX = ShoeSlide.alongX(ClientRail.locator(level), new double[]{centre.x, centre.y, centre.z});
        return alongX == null ? CollectorGeometry.beamAxis(state) : alongX ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static final class Motion {
        final CollectorSwing swing = new CollectorSwing();
        final SlideChase slide = new SlideChase();
    }

    public static void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                           ContraptionMatrices matrices, MultiBufferSource buffer) {
        final BlockState state = context.state;
        final Direction.Axis axis = CollectorGeometry.gearAxis(context.contraption, state);
        final int sign = CollectorGeometry.trailSign(context.contraption, context.localPos, axis);
        double angle = CollectorPose.STOP_DEG;
        double slide = 0.0;
        final AbstractContraptionEntity entity = context.contraption == null ? null : context.contraption.entity;
        if (entity != null && context.world != null) {
            final float pt = AnimationTickHolder.getPartialTicks();
            final Vec3 local = VecHelper.getCenterOf(context.localPos);
            final Vec3 centre = entity.toGlobalVector(local, pt);
            final Vec3 trail = entity.toGlobalVector(local.add(CollectorGeometry.trailLocal(axis, sign)), pt)
                    .subtract(centre);
            final Vec3 side = entity.toGlobalVector(local.add(CollectorGeometry.trailLocal(
                    CollectorGeometry.across(axis), 1)), pt).subtract(centre);
            final double offset = CollectorGeometry.centreOffset(context.contraption, context.localPos, axis);

            final CollectorGeometry.Placement placed = ClientRail.place(context.world, centre, trail, side, offset, true);
            if (!(context.temporaryData instanceof Motion)) {
                context.temporaryData = new Motion();
            }
            final Motion motion = (Motion) context.temporaryData;
            final double seconds = AnimationTickHolder.getRenderTime(context.world) / 20.0;
            angle = motion.swing.step(seconds, placed.rest().angleDeg(), placed.rest().touching());
            slide = motion.slide.step(seconds, placed.slide());
        }
        final VertexConsumer vc = buffer.getBuffer(RenderType.cutoutMipped());
        draw(state, axis, sign, angle, slide, matrices.getModel(),
                sbb -> sbb.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                        .useLevelLight(context.world, matrices.getWorld())
                        .renderInto(matrices.getViewProjection(), vc));
    }

    private static void draw(BlockState state, Direction.Axis axis, int sign, double angle, double slide,
                             com.mojang.blaze3d.vertex.PoseStack local, Consumer<SuperByteBuffer> out) {
        final boolean mirror = axis == Direction.Axis.Z ? sign < 0 : sign > 0;
        final float turn = (axis == Direction.Axis.X ? -90f : 0f) + (mirror ? 180f : 0f);

        final float run = (float) (mirror ? -slide : slide);
        final float px = (float) (CollectorPose.PIVOT_X / 16.0);
        final float py = (float) (CollectorPose.PIVOT_Y / 16.0);
        final float pz = (float) (CollectorPose.PIVOT_Z / 16.0);
        final double rad = Math.toRadians(angle);
        final float dy = (float) (CollectorPose.ARM * (1 - Math.cos(rad)) / 16.0);
        final float dz = (float) (CollectorPose.ARM * Math.sin(rad) / 16.0);

        final SuperByteBuffer beam = CachedBuffers.partial(PowerTrackPartials.COLLECTOR_BEAM, state);
        final SuperByteBuffer bracket = CachedBuffers.partial(PowerTrackPartials.COLLECTOR_BRACKET, state);
        final SuperByteBuffer arm = CachedBuffers.partial(PowerTrackPartials.COLLECTOR_ARM, state);
        final SuperByteBuffer shoe = CachedBuffers.partial(PowerTrackPartials.COLLECTOR_SHOE, state);
        for (SuperByteBuffer sbb : new SuperByteBuffer[]{beam, bracket, arm, shoe}) {
            if (local != null) {
                sbb.transform(local);
            }
            sbb.center().rotateYDegrees(turn).uncenter();
        }
        for (SuperByteBuffer sbb : new SuperByteBuffer[]{bracket, arm, shoe}) {
            sbb.translate(run, 0, 0);
        }

        arm.translate(px, py, pz).rotateXDegrees((float) -angle).translate(-px, -py, -pz);
        shoe.translate(0, dy, dz);
        out.accept(beam);
        out.accept(bracket);
        out.accept(arm);
        out.accept(shoe);

        final float length = Math.abs(run);
        if (length > 1f / 64f) {
            final SuperByteBuffer boom = CachedBuffers.partial(PowerTrackPartials.COLLECTOR_BOOM, state);
            if (local != null) {
                boom.transform(local);
            }
            boom.center().rotateYDegrees(turn).uncenter()
                    .translate(run < 0 ? -length : 0f, 0f, 0f)
                    .translate(0.5f, 0f, 0f).scale(length * 16f, 1f, 1f).translate(-0.5f, 0f, 0f);
            out.accept(boom);
        }
    }
}
