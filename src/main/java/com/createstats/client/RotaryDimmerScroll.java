package com.createstats.client;

import com.createstats.block.RotaryDimmerBlock;
import com.createstats.network.DimmerTurnPacket;
import com.simibubi.create.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// empty hand or wrench only, carrying blocks past a dimmer shouldn't change it
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class RotaryDimmerScroll {
    private RotaryDimmerScroll() {
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.player.isSpectator()
                || event.getScrollDeltaY() == 0.0) {
            return;
        }
        final ItemStack held = mc.player.getMainHandItem();
        if (!held.isEmpty() && !AllItems.WRENCH.isIn(held)) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof RotaryDimmerBlock)) {
            return;
        }
        event.setCanceled(true);
        PacketDistributor.sendToServer(new DimmerTurnPacket(hit.getBlockPos(),
                event.getScrollDeltaY() > 0 ? 1 : -1));
    }
}
