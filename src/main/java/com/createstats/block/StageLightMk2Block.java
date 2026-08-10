package com.createstats.block;

import java.util.List;

import com.createstats.blockentity.StageLightBE;
import com.createstats.blockentity.StageLightMk2BE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** [1] */
public class StageLightMk2Block extends StageLightBlock {

    public StageLightMk2Block(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Slot a Beacon as the range upgrade.
        if (stack.is(Items.BEACON)) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StageLightMk2BE mk2 && !mk2.hasBeaconUpgrade()) {
                if (!level.isClientSide()) {
                    mk2.setBeaconUpgrade(true);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Wrench with the upgrade installed and no colour filter pops the Beacon back out.
        if (AllItems.WRENCH.isIn(stack)) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StageLightMk2BE mk2 && mk2.hasBeaconUpgrade() && !mk2.hasTint()) {
                if (!level.isClientSide()) {
                    mk2.setBeaconUpgrade(false);
                    if (!player.getAbilities().instabuild) {
                        Block.popResource(level, pos, new ItemStack(Items.BEACON));
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StageLightMk2BE mk2 && mk2.hasBeaconUpgrade()) {
                Block.popResource(level, pos, new ItemStack(Items.BEACON));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public Class<StageLightBE> getBlockEntityClass() {
        return StageLightBE.class;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntityType<? extends StageLightBE> getBlockEntityType() {
        return ModBlockEntities.STAGE_LIGHT_MK2.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addStageLightTooltip(tooltip,
                "block.create_stats.stage_light_mk2");
    }
}
