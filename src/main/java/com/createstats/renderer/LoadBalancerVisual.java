package com.createstats.renderer;

import com.createstats.block.LoadBalancerBlock;
import com.createstats.blockentity.LoadBalancerBE;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

public class LoadBalancerVisual extends KineticBlockEntityVisual<LoadBalancerBE> {

    protected final Direction[] ports;
    protected final RotatingInstance[] shafts = new RotatingInstance[3];

    public LoadBalancerVisual(VisualizationContext context, LoadBalancerBE blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        ports = getPorts(blockEntity.getBlockState());

        var instancer = instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF));

        for (int i = 0; i < ports.length; i++) {
            Direction direction = ports[i];
            RotatingInstance instance = instancer.createInstance();

            instance.setup(blockEntity, direction.getAxis(), getSpeed(direction))
                .setPosition(getVisualPosition())
                .rotateToFace(Direction.SOUTH, direction)
                .setChanged();

            shafts[i] = instance;
        }
    }

    @Override
    public void update(float partialTick) {
        Direction[] updatedPorts = getPorts(blockEntity.getBlockState());

        for (int i = 0; i < ports.length; i++) {
            Direction direction = updatedPorts[i];
            Direction previousDirection = ports[i];
            RotatingInstance shaft = shafts[i];

            shaft
                .setup(blockEntity, direction.getAxis(), getSpeed(direction))
                .setPosition(getVisualPosition())
                .setChanged();

            if (direction != previousDirection) {
                shaft.rotation.identity();
                shaft.rotateToFace(Direction.SOUTH, direction).setChanged();
            }

            ports[i] = direction;
        }
    }

    private static Direction[] getPorts(BlockState state) {
        return new Direction[] {
            LoadBalancerBlock.getInputFacing(state),
            LoadBalancerBlock.getLeftOutputFacing(state),
            LoadBalancerBlock.getRightOutputFacing(state)
        };
    }

    @Override
    public void updateLight(float partialTick) {
        FlatLit[] lit = new FlatLit[shafts.length];
        for (int i = 0; i < shafts.length; i++) {
            lit[i] = shafts[i];
        }
        relight(lit);
    }

    @Override
    protected void _delete() {
        for (RotatingInstance shaft : shafts) {
            if (shaft != null) {
                shaft.delete();
            }
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (RotatingInstance shaft : shafts) {
            if (shaft != null) {
                consumer.accept(shaft);
            }
        }
    }

    private float getSpeed(Direction direction) {
        return blockEntity.getSpeed() * blockEntity.getRotationSpeedModifier(direction);
    }
}
