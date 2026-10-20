package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.blockentity.VacuumCleanerBE;
import com.createstats.item.SuctionWandItem;
import com.createstats.vacuum.SuctionEngine;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class SuctionWandClient {
    private SuctionWandClient() {
    }

    private static final float WALK_BACK = 5.0f;

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        final Player player = event.getEntity();
        if (!player.isUsingItem() || player.isPassenger()
                || !(player.getUseItem().getItem() instanceof SuctionWandItem)) {
            return;
        }
        final Input input = event.getInput();
        input.leftImpulse *= WALK_BACK;
        input.forwardImpulse *= WALK_BACK;
    }

    public static void useTick(Level level, Player player, VacuumCleanerBE cleaner) {
        final VacuumCleanerBE.State state = cleaner.state();
        if (state != VacuumCleanerBE.State.SUCK && state != VacuumCleanerBE.State.IDLE) {
            return;
        }
        final Vec3 head = VacuumCleanerBE.wandHead(player);
        final HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
        final Vec3 out = hit.getLocation().subtract(head);
        VacuumAirStream.cone(level, head, out, Math.max(0.8, out.length()), 1.0, 2);
        if (state != VacuumCleanerBE.State.SUCK) {
            return;
        }
        VacuumWandSound.startIfNeeded(player, cleaner);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState target = level.getBlockState(pos);

        if (target.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && target.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
            target = level.getBlockState(pos);
        }
        if (SuctionEngine.loose(level, pos, target)) {
            VacuumAirStream.grit(level, pos, target, blockHit.getDirection(), head, 2);
        }
    }
}
