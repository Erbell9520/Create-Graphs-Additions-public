package com.createstats.security;

import com.createstats.CreateStats;
import com.createstats.block.StormCoilPartBlock;
import com.createstats.blockentity.HomeSecurityPanelBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class CoilBreakWatch {
    private CoilBreakWatch() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }

        final BlockPos base = StormCoilPartBlock.master(level, event.getPos(), event.getState());
        if (base == null || !(level.getBlockEntity(base) instanceof CoilDevice coil)) {
            return;
        }
        final Player player = event.getPlayer();
        final HomeSecurityPanelBE panel = coil.securityPanel();
        if (player == null || panel == null) {
            return;
        }
        final String who = player.getGameProfile().getName();
        if (CoilGate.removalTrips(coil.clears(player))) {
            panel.tripped(base, SensorKind.TAMPER, who + " took down a Storm Coil");
        } else {
            panel.recordDevice(player, base, "taken down", SecurityLog.Verdict.CHANGED);
        }
    }
}
