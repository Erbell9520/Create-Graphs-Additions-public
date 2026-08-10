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

/** [1] */
@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class FePowerServerEvents {

    private FePowerServerEvents() {
    }

    /**
     * Fires once per chunk per player, not per tick. Sends nothing for chunks that hold no FE
     * consumers, which is almost all of them.
     */
    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        FePowerSync.sendChunkContents(event.getPlayer(), event.getChunk());
    }

    /** [2] */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!FeProfiles.showLoginReminder() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        final boolean on = FeProfiles.feModeEnabled();
        player.sendSystemMessage(Component.translatable(
                on ? "message.create_stats.fe_mode_on" : "message.create_stats.fe_mode_off")
                .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
    }
}
