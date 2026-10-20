package com.createstats.event;

import com.createstats.CreateStats;
import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class PilotHelmetServerEvents {
    private static final int REFRESH_INTERVAL_TICKS = 10;

    // under the 280 tick "external NV" threshold, over a missed refresh
    private static final int REFRESH_DURATION_TICKS = 220;

    private PilotHelmetServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }

        ItemStack worn = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(worn.getItem() instanceof PilotHelmetItem)) {
            return;
        }

        PilotHelmetData data = PilotHelmetItem.getData(worn);
        if (!data.isNightVisionEnabled()) {
            return;
        }

        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && current.getDuration() > REFRESH_DURATION_TICKS + 60) {
            data.setNightVisionEnabled(false);
            PilotHelmetItem.setData(worn, data);
            player.displayClientMessage(
                Component.literal("Helmet NV paused: external Night Vision detected.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        player.addEffect(new MobEffectInstance(
            MobEffects.NIGHT_VISION,
            REFRESH_DURATION_TICKS,
            0,
             false,
             false,
             false));
    }
}
