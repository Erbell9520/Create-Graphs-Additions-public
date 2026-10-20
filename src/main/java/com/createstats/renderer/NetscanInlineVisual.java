package com.createstats.renderer;

import com.createstats.netscan.blockentity.NetscanInlineBE;
import com.simibubi.create.content.kinetics.base.ShaftVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;

// create's own ShaftVisual, follows texture packs
public class NetscanInlineVisual extends ShaftVisual<NetscanInlineBE> {
    public NetscanInlineVisual(VisualizationContext context, NetscanInlineBE blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
    }
}
