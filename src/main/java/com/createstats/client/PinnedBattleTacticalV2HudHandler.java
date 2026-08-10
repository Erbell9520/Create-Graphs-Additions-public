package com.createstats.client;

import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** [1] */
@OnlyIn(Dist.CLIENT)
public final class PinnedBattleTacticalV2HudHandler {

    private PinnedBattleTacticalV2HudHandler() {}

    public static void onToggleKey() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Player player = minecraft.player;
        final Level level = minecraft.level;
        if (player == null || level == null) return;
        final HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            if (PinnedBattleTacticalV2HudOverlay.isPinned(null) || hasAnyPin()) {
                PinnedBattleTacticalV2HudOverlay.clear();
                player.displayClientMessage(Component.translatable("create_stats.battle_tactical_v2.pin.cleared"), true);
            } else {
                player.displayClientMessage(Component.translatable("create_stats.battle_tactical_v2.pin.no_target"), true);
            }
            return;
        }
        final BlockEntity blockEntity = level.getBlockEntity(blockHit.getBlockPos());
        if (!(blockEntity instanceof StatsDisplayPanelBE panel)) {
            player.displayClientMessage(Component.translatable("create_stats.battle_tactical_v2.pin.not_panel"), true);
            return;
        }
        final StatsDisplayPanelBE controller = panel.getControllerBE();
        if (controller == null) {
            player.displayClientMessage(Component.translatable("create_stats.battle_tactical_v2.pin.not_panel"), true);
            return;
        }
        final String sourceId = PinnedBattleTacticalV2HudOverlay.findBattleTacticalV2SourceId(controller);
        if (sourceId == null) {
            player.displayClientMessage(Component.translatable("create_stats.battle_tactical_v2.pin.no_module"), true);
            return;
        }
        if (PinnedBattleTacticalV2HudOverlay.isPinned(controller)) {
            PinnedBattleTacticalV2HudOverlay.clear();
            player.displayClientMessage(Component.translatable("create_stats.battle_tactical_v2.pin.cleared"), true);
        } else {
            PinnedBattleTacticalV2HudOverlay.pin(controller, sourceId);
            player.displayClientMessage(Component.translatable("create_stats.battle_tactical_v2.pin.set"), true);
        }
    }

    private static boolean hasAnyPin() {
        // Lightweight probe: any pin exists if isPinned(null) ever returns true. Since the overlay
        // returns false for null, treat absence of crosshair target as "no-op when nothing pinned".
        return false;
    }
}
