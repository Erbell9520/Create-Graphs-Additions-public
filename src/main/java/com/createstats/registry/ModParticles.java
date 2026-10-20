package com.createstats.registry;

import com.createstats.CreateStats;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    private ModParticles() {
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, CreateStats.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VACUUM_AIR =
            PARTICLES.register("vacuum_air", () -> new SimpleParticleType(false));

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
