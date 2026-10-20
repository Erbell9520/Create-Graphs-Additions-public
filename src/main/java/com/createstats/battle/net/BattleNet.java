package com.createstats.battle.net;

import com.createstats.CreateStats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class BattleNet {
    public static final String VERSION = "battle-3";

    private BattleNet() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(BattleBridgeActionPacket.TYPE, BattleBridgeActionPacket.STREAM_CODEC, BattleBridgeActionPacket::handle);
        registrar.playToServer(SectionMarkerPacket.TYPE, SectionMarkerPacket.STREAM_CODEC, SectionMarkerPacket::handle);
        registrar.playToServer(BattleKeyPacket.TYPE, BattleKeyPacket.STREAM_CODEC, BattleKeyPacket::handle);
        registrar.playToClient(BattleWatchPacket.TYPE, BattleWatchPacket.STREAM_CODEC, BattleWatchPacket::handle);
        registrar.playToClient(BattleShapePacket.TYPE, BattleShapePacket.STREAM_CODEC, BattleShapePacket::handle);
        registrar.playToClient(BattleDetailPacket.TYPE, BattleDetailPacket.STREAM_CODEC, BattleDetailPacket::handle);
    }
}
