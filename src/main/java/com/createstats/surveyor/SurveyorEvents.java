package com.createstats.surveyor;

import com.createstats.vccmk4.route.RouteHosts;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "create_stats")
public final class SurveyorEvents {
    private SurveyorEvents() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide && event.getEntity().getMainHandItem().getItem() instanceof SurveyorStaffItem) {
            event.setCanceled(true);
        }
    }

    // sweep old sable holds nobody will come back for. sable tax #14
    static final int LEGACY_SWEEP_TICKS = 200;
    private static final Map<ResourceKey<Level>, Long> SWEEP_AT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SWEEP_AT.put(level.dimension(), level.getGameTime() + LEGACY_SWEEP_TICKS);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Long at = SWEEP_AT.get(level.dimension());
        if (at == null || level.getGameTime() < at) return;
        SWEEP_AT.remove(level.dimension());
        RouteChunkTickets.sweepSable(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RouteChunkTickets.forgetAll();
        RouteHosts.clear();
        SWEEP_AT.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RouteOrders.forget(event.getEntity().getUUID());
    }
}
