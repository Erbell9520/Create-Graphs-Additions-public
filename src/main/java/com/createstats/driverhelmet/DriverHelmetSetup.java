package com.createstats.driverhelmet;

import com.createstats.CreateStats;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DriverHelmetSetup {
    private DriverHelmetSetup() {
    }

    // visor up = engineer's goggles, via create's addIsWearingPredicate (that's literally what it's for)
    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GogglesItem.addIsWearingPredicate(DriverHelmets::gogglesUp));
    }

    @SubscribeEvent
    public static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("driver-helmet-3")
                .playToServer(DriverHelmetPacket.TYPE, DriverHelmetPacket.STREAM_CODEC, DriverHelmetPacket::handle)
                .playToClient(DriverTelemetryPacket.TYPE, DriverTelemetryPacket.STREAM_CODEC,
                        DriverTelemetryPacket::handle)
                .playToServer(HelmetGlowPacket.TYPE, HelmetGlowPacket.STREAM_CODEC, HelmetGlowPacket::handle)
                .playToClient(HelmetGlowSync.TYPE, HelmetGlowSync.STREAM_CODEC, HelmetGlowSync::handle);
    }
}
