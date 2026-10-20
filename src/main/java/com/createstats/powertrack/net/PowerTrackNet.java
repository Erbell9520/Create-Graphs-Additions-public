package com.createstats.powertrack.net;

import com.createstats.CreateStats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class PowerTrackNet {
    public static final String VERSION = "powertrack-1";

    private PowerTrackNet() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(RailStatePacket.TYPE, RailStatePacket.STREAM_CODEC, RailStatePacket::handle);
        registrar.playToClient(CarCurrentPacket.TYPE, CarCurrentPacket.STREAM_CODEC, CarCurrentPacket::handle);
    }
}
