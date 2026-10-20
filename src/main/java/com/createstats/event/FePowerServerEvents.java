package com.createstats.event;

import com.createstats.CreateStats;
import com.createstats.power.FePowerSync;
import com.createstats.power.FeProfiles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class FePowerServerEvents {
    private static final String PREFIX = "[Create: S&P] ";

    private FePowerServerEvents() {
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        FePowerSync.sendChunkContents(event.getPlayer(), event.getChunk());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!FeProfiles.showLoginReminder()
                || !FeProfiles.powerRequired()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.sendSystemMessage(Component.literal(PREFIX).withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("message.create_stats.fe_power_notice")
                        .withStyle(ChatFormatting.GRAY)));
    }
}
