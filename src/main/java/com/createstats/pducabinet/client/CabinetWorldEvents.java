package com.createstats.pducabinet.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid="create_stats",value=Dist.CLIENT)
public final class CabinetWorldEvents {
    private CabinetWorldEvents() {}
    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if(event.getLevel().isClientSide())CabinetTextures.clear();
    }
}
