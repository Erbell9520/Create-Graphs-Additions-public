package com.createstats.advancement;

import com.createstats.CreateStats;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** the advancements vanilla can't see. all of these are impossible criteria granted from code */
public final class ModAdvancements {
    public static final String BREAKER_TRIP = "power/breaker_trip";

    public static final String HAIL = "transport/hail";

    public static final String ACTION = "transport/action";

    public static final String MANIFEST_YAAA = "manifest_yaaa";

    public static final double TRIP_EARSHOT = 16.0;

    private ModAdvancements() {
    }

    public static void award(ServerPlayer player, String id) {
        final MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        final AdvancementHolder holder = server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, id));
        if (holder == null) {
            return;
        }
        final AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    public static void awardNearby(ServerLevel level, BlockPos pos, double radius, String id) {
        final double limit = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(pos.getCenter()) <= limit) {
                award(player, id);
            }
        }
    }
}
