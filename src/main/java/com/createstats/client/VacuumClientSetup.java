package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VacuumClientSetup {
    private VacuumClientSetup() {
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.VACUUM_AIR.get(), VacuumAirParticle.Provider::new);
    }
}
