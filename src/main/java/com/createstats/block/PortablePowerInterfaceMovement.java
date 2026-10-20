package com.createstats.block;

import com.createstats.renderer.PortablePowerInterfaceRenderer;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceMovement;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

public class PortablePowerInterfaceMovement extends PortableStorageInterfaceMovement {
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
        PortablePowerInterfaceRenderer.renderInContraption(context, renderWorld, matrices, buffer);
    }
}
