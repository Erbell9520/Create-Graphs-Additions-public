package com.createstats.block;

import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PowerConnectorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public static final net.minecraft.world.level.block.state.properties.EnumProperty<ConnectorAnchor> ANCHOR =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("anchor", ConnectorAnchor.class);

    private final com.createstats.power.ConnectorTier tier;

    private static final VoxelShape SHAPE_UP = Block.box(5, 0, 5, 11, 12, 11);
    private static final VoxelShape SHAPE_DOWN = Block.box(5, 4, 5, 11, 16, 11);
    private static final VoxelShape SHAPE_NORTH = Block.box(5, 5, 4, 11, 11, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(5, 5, 0, 11, 11, 12);
    private static final VoxelShape SHAPE_WEST = Block.box(4, 5, 5, 16, 11, 11);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 5, 5, 12, 11, 11);

    public PowerConnectorBlock(Properties properties, com.createstats.power.ConnectorTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(ANCHOR, ConnectorAnchor.CENTER));
    }

    public com.createstats.power.ConnectorTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ANCHOR);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction face = context.getClickedFace();

        final BlockPos supportPos = context.getClickedPos().relative(face.getOpposite());
        final BlockState support = context.getLevel().getBlockState(supportPos);
        final ConnectorAnchor anchor = ConnectorAnchor.forSupport(
                face, support.getShape(context.getLevel(), supportPos));
        return defaultBlockState().setValue(FACING, face).setValue(ANCHOR, anchor);
    }

    public static VoxelShape shapeOf(BlockState state) {
        final VoxelShape base = switch (state.getValue(FACING)) {
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_UP;
        };
        final ConnectorAnchor anchor = ConnectorAnchor.inPlaneOf(state.getValue(FACING), state.getValue(ANCHOR));
        if (anchor == ConnectorAnchor.CENTER) {
            return base;
        }
        return base.move(anchor.offset(Direction.Axis.X),
                anchor.offset(Direction.Axis.Y),
                anchor.offset(Direction.Axis.Z));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.POWER_CONNECTOR.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.POWER_CONNECTOR.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((PowerConnectorBE) be).tick();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof PowerConnectorBE connector) {
            connector.dropWiresAndUnlinkAll();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, stack.getItem().getDescriptionId(),
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec("tooltip.create_stats.connector_rate",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.connector(tier)),
                        tier.maxLinks())));
    }
}
