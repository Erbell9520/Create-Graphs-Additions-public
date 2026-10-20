package com.createstats.client;

import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import com.createstats.network.ToggleVisorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PilotHelmetVisorState {
    private PilotHelmetVisorState() {
    }

    public static boolean effectiveDeployed(LivingEntity wearer, ItemStack stack) {
        if (wearer == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof PilotHelmetItem)) {
            return false;
        }
        final PilotHelmetData data = PilotHelmetItem.getData(stack);
        if (data.isVisorDeployed()) {
            return true;
        }
        return data.isAutoIfSeated() && wearer.getVehicle() != null;
    }

    public static boolean isLocalVisorDeployed() {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        return effectiveDeployed(player, player.getItemBySlot(EquipmentSlot.HEAD));
    }

    public static void requestToggle() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        if (player == null) {
            return;
        }
        final ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty() || !(head.getItem() instanceof PilotHelmetItem)) {
            return;
        }
        final boolean willDeploy = !PilotHelmetItem.getData(head).isVisorDeployed();
        player.displayClientMessage(Component.translatable(
            willDeploy ? "message.create_stats.pilot_helmet.visor.down"
                       : "message.create_stats.pilot_helmet.visor.up"), true);
        PacketDistributor.sendToServer(ToggleVisorPacket.INSTANCE);
    }

    public static void clear() {
    }
}
