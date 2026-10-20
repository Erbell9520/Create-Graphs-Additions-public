package com.createstats.security;

import com.createstats.blockentity.HomeSecurityPanelBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// not a claim system. don't grow it into one
public final class PanelGuard {
    private PanelGuard() {
    }

    private static final int REPORT_COOLDOWN = 20;

    private static final Map<String, Long> LAST_REPORT = new HashMap<>();

    public static boolean mayBreak(boolean claimed, boolean owner, boolean creative, boolean op) {
        return !claimed || owner || creative || op;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        final BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof HomeSecurityPanelBE panel)) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        final boolean owner = panel.isOwnedBy(player.getUUID());
        if (mayBreak(panel.isClaimed(), owner, player.isCreative(), player.hasPermissions(2))) {
            return;
        }
        event.setCanceled(true);
        report(level, panel, player, pos);
    }

    private static void report(Level level, HomeSecurityPanelBE panel, Player player, BlockPos pos) {
        final long now = level.getGameTime();
        final String key = player.getUUID() + "@" + pos.asLong();
        final Long last = LAST_REPORT.get(key);
        if (last != null && now - last < REPORT_COOLDOWN) {
            return;
        }
        LAST_REPORT.put(key, now);
        player.displayClientMessage(
                Component.translatable("message.create_stats.security.panel_protected"), true);
        panel.tripped(pos, SensorKind.TAMPER,
                player.getGameProfile().getName() + " tried to break the panel");
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    static void forget(UUID player) {
        if (player != null) {
            LAST_REPORT.keySet().removeIf(key -> key.startsWith(player.toString() + "@"));
        }
    }
}
