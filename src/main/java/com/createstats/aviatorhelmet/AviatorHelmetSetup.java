package com.createstats.aviatorhelmet;

import com.createstats.CreateStats;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class AviatorHelmetSetup {
    private AviatorHelmetSetup() {
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GogglesItem.addIsWearingPredicate(AviatorHelmets::gogglesDown));
    }

    @SubscribeEvent
    public static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("aviator-helmet-1")
                .playToServer(AviatorHelmetPacket.TYPE, AviatorHelmetPacket.STREAM_CODEC, AviatorHelmetPacket::handle)
                .playToClient(AerialHudPacket.TYPE, AerialHudPacket.STREAM_CODEC, AerialHudPacket::handle);
    }
}
