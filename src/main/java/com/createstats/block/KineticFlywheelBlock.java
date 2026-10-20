package com.createstats.block;

import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

public class KineticFlywheelBlock extends DirectionalKineticBlock implements IBE<KineticFlywheelBE> {
    public static final EnumProperty<VisualState> VISUAL_STATE = EnumProperty.create("visual_state", VisualState.class);

    public KineticFlywheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VISUAL_STATE, VisualState.IDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VISUAL_STATE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    public static int getLight(BlockState state) {
        return switch (state.getValue(VISUAL_STATE)) {
            case DISCHARGING -> 8;
            case CHARGING -> 5;
            case IDLE -> 0;
        };
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof KineticFlywheelBE flywheel) {
            return flywheel.getComparatorOutput();
        }
        return 0;
    }

    @Override
    public Class<KineticFlywheelBE> getBlockEntityClass() {
        return KineticFlywheelBE.class;
    }

    @Override
    public BlockEntityType<? extends KineticFlywheelBE> getBlockEntityType() {
        return ModBlockEntities.KINETIC_FLYWHEEL.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.kinetic_flywheel");
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!player.isShiftKeyDown() && stack.is(AllBlocks.FLYWHEEL.asItem())) {
            if (level.getBlockEntity(pos) instanceof KineticFlywheelBE fw) {
                if (fw.getTier() >= fw.getMaxTier()) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                if (!level.isClientSide()) {
                    fw.setTier(fw.getTier() + 1);
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    playStageSound(level, pos, true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()
                && level.getBlockEntity(pos) instanceof KineticFlywheelBE fw && fw.getTier() > 0) {
            if (!level.isClientSide()) {
                popOneStage(level, pos, fw, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    private void popOneStage(Level level, BlockPos pos, KineticFlywheelBE fw, @Nullable Player player) {
        fw.setTier(fw.getTier() - 1);
        if (player == null || !player.isCreative()) {
            ItemStack returned = new ItemStack(AllBlocks.FLYWHEEL.asItem());
            if (player == null || !player.getInventory().add(returned)) {
                Block.popResource(level, pos, returned);
            }
        }
        playStageSound(level, pos, false);
    }

    private static void playStageSound(Level level, BlockPos pos, boolean attach) {
        level.playSound(null, pos,
            attach ? SoundEvents.ANVIL_PLACE : SoundEvents.ITEM_FRAME_REMOVE_ITEM,
            SoundSource.BLOCKS, 0.5f, attach ? 0.9f : 1.2f);
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
            server.sendParticles(
                attach ? net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT
                       : net.minecraft.core.particles.ParticleTypes.CRIT,
                cx, cy, cz, attach ? 22 : 14, 0.35, 0.35, 0.35, 0.06);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                cx, cy, cz, 6, 0.18, 0.18, 0.18, 0.01);
        }
    }

    @Override
    protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<ItemStack> drops = new java.util.ArrayList<>(super.getDrops(state, params));
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof KineticFlywheelBE fw) {
            for (int i = 0; i < fw.getTier(); i++) {
                drops.add(new ItemStack(AllBlocks.FLYWHEEL.asItem()));
            }
        }
        return drops;
    }

    public enum VisualState implements StringRepresentable {
        IDLE("idle"),
        CHARGING("charging"),
        DISCHARGING("discharging");

        private final String name;

        VisualState(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
