package com.createstats.support;

import com.createstats.CreateStats;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class SupportEvents {
    private static int tickCounter;

    private SupportEvents() {
    }

    @SubscribeEvent
    public static void onGatherPermissions(PermissionGatherEvent.Nodes event) {
        event.addNodes(SupportSessions.NODE_TIER1, SupportSessions.NODE_TIER2);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
            && SupportSessions.access(player).atLeast(SupportPolicy.Level.SAFE)) {
            SupportSessions.sendStatus(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SupportSessions.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 20 == 0) {
            SupportSessions.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SupportSessions.reset();
    }
}
