package com.createstats.registry;

import com.createstats.CreateStats;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateStats.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CONTENT =
            COMPONENTS.register("content", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static final DeferredHolder<DataComponentType<?>,
            DataComponentType<com.createstats.security.LockerSeal>> LOCKER_SEAL =
            COMPONENTS.register("locker_seal", () ->
                    DataComponentType.<com.createstats.security.LockerSeal>builder()
                            .persistent(com.createstats.security.LockerSeal.CODEC)
                            .networkSynchronized(com.createstats.security.LockerSeal.STREAM_CODEC)
                            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_FE =
            COMPONENTS.register("stored_fe", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CAPACITOR_MODE =
            COMPONENTS.register("capacitor_mode", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.core.GlobalPos>> VACUUM_HOSE =
            COMPONENTS.register("vacuum_hose", () -> DataComponentType.<net.minecraft.core.GlobalPos>builder()
                    .persistent(net.minecraft.core.GlobalPos.CODEC)
                    .networkSynchronized(net.minecraft.core.GlobalPos.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.createstats.driverhelmet.HelmetVisor>> HELMET_VISOR =
            COMPONENTS.register("helmet_visor", () -> DataComponentType.<com.createstats.driverhelmet.HelmetVisor>builder()
                    .persistent(com.createstats.driverhelmet.HelmetVisor.CODEC)
                    .networkSynchronized(com.createstats.driverhelmet.HelmetVisor.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.createstats.aviatorhelmet.AviatorGoggles>> AVIATOR_GOGGLES =
            COMPONENTS.register("aviator_goggles", () -> DataComponentType.<com.createstats.aviatorhelmet.AviatorGoggles>builder()
                    .persistent(com.createstats.aviatorhelmet.AviatorGoggles.CODEC)
                    .networkSynchronized(com.createstats.aviatorhelmet.AviatorGoggles.STREAM_CODEC)
                    .build());

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
