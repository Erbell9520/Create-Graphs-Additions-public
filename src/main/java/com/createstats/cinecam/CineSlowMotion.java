package com.createstats.cinecam;

import com.createstats.CreateStats;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import javax.annotation.Nullable;
import java.util.Locale;

// slow mo by slowing the world tick rate, free. more frames per second would cost
@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class CineSlowMotion {
    private record Hold(ResourceKey<Level> dimension, BlockPos pos, float before, float set) {
    }

    @Nullable private static Hold hold;

    private CineSlowMotion() {
    }

    static boolean allowed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        return player.hasPermissions(2)
                || (server.isSingleplayerOwner(player.getGameProfile()) && server.getPlayerCount() == 1);
    }

    static void begin(ServerLevel level, BlockPos pos, ServerPlayer by, double speed) {
        if (speed >= 1.0) return;
        MinecraftServer server = level.getServer();
        String label = speedLabel(speed);

        int rate = level.getBlockEntity(pos) instanceof CineCameraBE cam ? cam.filmRate() : 30;
        int need = com.createstats.cinecam.core.FilmClock.smoothFps(rate, speed, false);
        if (hold != null) {
            by.displayClientMessage(Component.translatable("create_stats.cine_camera.slow.busy", need)
                    .withStyle(ChatFormatting.GOLD), false);
            return;
        }
        if (!allowed(by)) {
            by.displayClientMessage(Component.translatable("create_stats.cine_camera.slow.refused", need)
                    .withStyle(ChatFormatting.GOLD), false);
            return;
        }
        ServerTickRateManager ticks = server.tickRateManager();
        float before = ticks.tickrate();
        float want = (float) (20.0 * speed);
        if (before <= want) return;
        ticks.setTickRate(want);
        hold = new Hold(level.dimension(), pos.immutable(), before, want);
        server.getPlayerList().broadcastSystemMessage(Component.translatable("create_stats.cine_camera.slow.on",
                by.getDisplayName(), label).withStyle(ChatFormatting.GRAY), false);
    }

    static void end(ServerLevel level, BlockPos pos) {
        Hold h = hold;
        if (h == null || !h.pos().equals(pos) || !h.dimension().equals(level.dimension())) return;
        hold = null;
        MinecraftServer server = level.getServer();
        ServerTickRateManager ticks = server.tickRateManager();
        if (Math.abs(ticks.tickrate() - h.set()) > 1e-3f) return;
        ticks.setTickRate(h.before());
        server.getPlayerList().broadcastSystemMessage(Component.translatable("create_stats.cine_camera.slow.off")
                .withStyle(ChatFormatting.GRAY), false);
    }

    static boolean holding() {
        return hold != null;
    }

    private static String speedLabel(double speed) {
        return String.format(Locale.ROOT, "1/%d", Math.round(1.0 / speed));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        hold = null;
    }
}
