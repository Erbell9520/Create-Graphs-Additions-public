package com.createstats.renderer;

import com.createstats.netscan.blockentity.NetscanInlineBE;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

// fallback for no flywheel backend, or the shaft just doesn't exist on those clients
public class NetscanInlineRenderer extends ShaftRenderer<NetscanInlineBE> {
    public NetscanInlineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
}
