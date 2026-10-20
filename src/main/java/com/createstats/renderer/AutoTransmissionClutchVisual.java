package com.createstats.renderer;

import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.createstats.client.ModPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.function.Consumer;

public class AutoTransmissionClutchVisual extends KineticBlockEntityVisual<AutoTransmissionClutchBE> implements SimpleDynamicVisual {
    private static final float NEEDLE_PIVOT_X = 8.0f / 16.0f;
    private static final float NEEDLE_PIVOT_Z = 8.0f / 16.0f;
    private static final float NEEDLE_START_ANGLE = (float) (Math.PI * 5.0 / 4.0);

    protected final ArrayList<RotatingInstance> shafts = new ArrayList<>(2);
    protected final TransformedInstance needle;

    public AutoTransmissionClutchVisual(VisualizationContext context, AutoTransmissionClutchBE blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        float speed = blockEntity.getSpeed();
        for (Direction direction : Iterate.directionsInAxis(rotationAxis())) {
            var instance = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                .createInstance();
            instance.setup(blockEntity, speed * blockEntity.getRotationSpeedModifier(direction))
                .setPosition(getVisualPosition())
                .rotateToFace(Direction.SOUTH, direction)
                .setChanged();
            shafts.add(instance);
        }

        Instancer<TransformedInstance> needleModel =
            instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModPartialModels.AUTO_TRANSMISSION_CLUTCH_GAUGE_NEEDLE));
        needle = needleModel.createInstance();
        updateNeedleTransform(partialTick);
    }

    @Override
    public void update(float partialTick) {
        Direction[] directions = Iterate.directionsInAxis(rotationAxis());
        for (int i : Iterate.zeroAndOne) {
            shafts.get(i)
                .setup(blockEntity, blockEntity.getSpeed() * blockEntity.getRotationSpeedModifier(directions[i]))
                .setChanged();
        }

        updateNeedleTransform(partialTick);
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (Mth.equal(blockEntity.prevDialState, blockEntity.dialState)) {
            return;
        }

        updateNeedleTransform(ctx.partialTick());
    }

    @Override
    public void updateLight(float partialTick) {
        FlatLit[] lit = new FlatLit[shafts.size() + 1];
        for (int i = 0; i < shafts.size(); i++) {
            lit[i] = shafts.get(i);
        }
        lit[shafts.size()] = needle;
        relight(lit);
    }

    @Override
    protected void _delete() {
        shafts.forEach(AbstractInstance::delete);
        shafts.clear();
        needle.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        shafts.forEach(consumer);
        consumer.accept(needle);
    }

    private void updateNeedleTransform(float partialTick) {
        float progress = Mth.lerp(partialTick, blockEntity.prevDialState, blockEntity.dialState);
        float angle = NEEDLE_START_ANGLE - NeedlePhysics.SWEEP_RADIANS * progress;

        PoseStack poseStack = new PoseStack();
        TransformStack.of(poseStack)
            .translate(getVisualPosition())
            .translate(NEEDLE_PIVOT_X, 0.0f, NEEDLE_PIVOT_Z)
            .rotate(angle, Direction.UP)
            .translate(-NEEDLE_PIVOT_X, 0.0f, -NEEDLE_PIVOT_Z);

        needle.setTransform(poseStack).setChanged();
    }
}
