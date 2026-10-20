package com.createstats.powertrack;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

public class CurrentCollectorMovement implements MovementBehaviour {
    @Override
    public void tick(MovementContext context) {
        if (context.world == null) {
            return;
        }
        if (context.world.isClientSide()) {
            com.createstats.powertrack.client.ClientRail.collectorTick(context);
        } else {
            PowerTrackService.noteShoe(context);
        }
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Nullable
    @Override
    @OnlyIn(Dist.CLIENT)
    public ActorVisual createVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld,
                                    MovementContext movementContext) {
        return null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        com.createstats.powertrack.client.CurrentCollectorRenderer.renderInContraption(context, renderWorld, matrices,
                buffer);
    }
}
