package com.createstats.block;

import com.createstats.blockentity.SparkCrownBE;
import com.createstats.chunkloader.CrownConfig;
import com.createstats.chunkloader.CrownOwners;
import com.createstats.chunkloader.CrownScreenAccess;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.guard.BlockFault;
import com.createstats.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SparkCrownBlock extends HorizontalDirectionalBlock implements IBE<SparkCrownBE>, IWrenchable {
    public static final MapCodec<SparkCrownBlock> CODEC = simpleCodec(SparkCrownBlock::new);

    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    public SparkCrownBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LIGHT, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIGHT);
    }

    public static int lightLevel(BlockState state) {
        return state.hasProperty(LIGHT) ? state.getValue(LIGHT) : 0;
    }

    public static Direction wireFace(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        final java.util.UUID owner = CrownOwners.ownerOf(placer);
        if (owner != null) {
            withBlockEntityDo(level, pos, be -> be.setOwner(owner, CrownOwners.nameOf(placer, owner)));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return use(level, pos, player);
    }

    public static InteractionResult use(Level level, BlockPos pos, Player player) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof SparkCrownBE) {
                CrownScreenAccess.openMap(pos);
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof SparkCrownBE be) {
            be.claimIfUnowned(server);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof SparkCrownBE be) {
            be.dropWiresAndUnlinkAll();
            be.giveBackAllCells(server, state.getValue(FACING));
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        final CrownLaw law = CrownConfig.law();
        tooltip.add(Component.translatable("block.create_stats.spark_crown.tooltip", fmt(law.totalDraw(1)),
                law.growthPercent(), law.maxPerStation(), fmt(law.totalDraw(law.maxPerStation())), law.maxPerPlayer())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.create_stats.spark_crown.tooltip.usage", law.rideSeconds())
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static String fmt(long v) {
        return String.format(java.util.Locale.ROOT, "%,d", v);
    }

    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return (lvl, pos, blockState, be) -> {
            if (!(be instanceof SparkCrownBE crown)) return;
            if (!crown.hasLevel()) crown.setLevel(lvl);
            BlockFault.tick("Spark Crown", crown, crown::tick);
        };
    }

    @Override
    public Class<SparkCrownBE> getBlockEntityClass() {
        return SparkCrownBE.class;
    }

    @Override
    public BlockEntityType<? extends SparkCrownBE> getBlockEntityType() {
        return ModBlockEntities.SPARK_CROWN.get();
    }
}
