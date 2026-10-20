package com.createstats.security;

import com.createstats.CreateStats;
import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.blockentity.MobRepellerBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class RepellerWatch {
    private RepellerWatch() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        final BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof MobRepellerBE repeller)) {
            return;
        }
        final Player player = event.getPlayer();
        final HomeSecurityPanelBE panel = repeller.panel();
        if (player == null || panel == null) {
            return;
        }
        final String who = player.getGameProfile().getName();
        if (RepellerGate.removalTrips(repeller.clears(player))) {
            panel.tripped(pos, SensorKind.TAMPER, who + " took down a Mob Repeller");
        } else {
            panel.recordDevice(player, pos, "taken down", SecurityLog.Verdict.CHANGED);
        }
    }
}
