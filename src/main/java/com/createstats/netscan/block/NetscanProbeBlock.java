package com.createstats.netscan.block;

import java.util.List;

import javax.annotation.Nullable;

import com.createstats.netscan.ScannerTier;
import com.createstats.netscan.blockentity.NetscanProbeBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.ClientScreenAccess;
import com.simibubi.create.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NetscanProbeBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape[] SHAPES = new VoxelShape[Direction.values().length];

    static {
        SHAPES[Direction.DOWN.ordinal()] = Block.box(2.0, 7.4, 2.0, 14.0, 16.0, 14.0);
        SHAPES[Direction.UP.ordinal()] = Block.box(2.0, 0.0, 2.0, 14.0, 8.6, 14.0);
        SHAPES[Direction.NORTH.ordinal()] = Block.box(2.0, 2.0, 7.4, 14.0, 14.0, 16.0);
        SHAPES[Direction.SOUTH.ordinal()] = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 8.6);
        SHAPES[Direction.WEST.ordinal()] = Block.box(7.4, 2.0, 2.0, 16.0, 14.0, 14.0);
        SHAPES[Direction.EAST.ordinal()] = Block.box(0.0, 2.0, 2.0, 8.6, 14.0, 14.0);
    }

    private final ScannerTier tier;

    public NetscanProbeBlock(Properties properties, ScannerTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public ScannerTier tier() {
        return this.tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        final VoxelShape shape = SHAPES[state.getValue(FACING).ordinal()];
        return shape == null ? Shapes.block() : shape;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.TOPOLOGY_SCANNER_PROBE.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.TOPOLOGY_SCANNER_PROBE.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((NetscanProbeBE) be).tick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof com.createstats.item.CableItem
                || stack.getItem() instanceof com.createstats.item.CopperWireItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof NetscanProbeBE scanner) {
            ClientScreenAccess.openTopologyScannerProbe(scanner);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats." + tierId());
    }

    private String tierId() {
        return switch (this.tier) {
            case FIELD -> "topology_scanner_probe";
            case ADVANCED -> "advanced_topology_scanner_probe";
            case INDUSTRIAL -> "industrial_topology_scanner_probe";
        };
    }
}
