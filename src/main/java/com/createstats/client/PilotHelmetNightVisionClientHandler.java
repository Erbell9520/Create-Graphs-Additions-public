package com.createstats.client;

import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import com.createstats.network.PilotHelmetNightVisionTogglePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// silent no-op without the helmet
public final class PilotHelmetNightVisionClientHandler {
    private PilotHelmetNightVisionClientHandler() {
    }

    public static void onToggleKey() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack worn = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(worn.getItem() instanceof PilotHelmetItem)) {
            return;
        }

        PilotHelmetData data = PilotHelmetItem.getData(worn);
        boolean newState = !data.isNightVisionEnabled();

        PacketDistributor.sendToServer(new PilotHelmetNightVisionTogglePacket(newState));

        player.displayClientMessage(
            Component.translatable(newState
                    ? "message.create_stats.pilot_helmet.nv_on"
                    : "message.create_stats.pilot_helmet.nv_off")
                .withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.GRAY),
            true);
    }
}
