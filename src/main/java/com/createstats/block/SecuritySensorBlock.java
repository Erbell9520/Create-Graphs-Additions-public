package com.createstats.block;

import com.createstats.blockentity.SecuritySensorBE;
import com.createstats.registry.ModBlockEntities;
import com.createstats.security.SensorKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SecuritySensorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final SensorKind kind;

    private static double[] boxOf(SensorKind kind) {
        return switch (kind) {
            case MOTION -> new double[]{5, 4, 0, 11, 13.4, 5.2622};
            case TAMPER -> new double[]{1.5, 4, 0, 15.8, 11, 3};
            case FIRE -> new double[]{1.6, 1.6, 0, 14.4, 14.4, 2.6};
            case WATER -> new double[]{4.5, 1.2, 0, 11.5, 14.5, 2.6};
        };
    }

    private static VoxelShape rotate(double[] b, Direction facing) {
        final double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);

            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);

            case DOWN -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case UP -> Block.box(x0, 16 - z1, y0, x1, 16 - z0, y1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    private final java.util.Map<Direction, VoxelShape> shapes =
            new java.util.EnumMap<>(Direction.class);

    public SecuritySensorBlock(Properties properties, SensorKind kind) {
        super(properties);
        this.kind = kind;
        final double[] box = boxOf(kind);
        for (Direction facing : Direction.values()) {
            this.shapes.put(facing, rotate(box, facing));
        }
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public SensorKind kind() {
        return this.kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockState state = defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final Direction facing = state.getValue(FACING);
        final BlockPos support = pos.relative(facing);
        return level.getBlockState(support).isFaceSturdy(level, support, facing.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == state.getValue(FACING) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return this.shapes.get(state.getValue(FACING));
    }

    public static SensorKind kindOf(BlockState state) {
        return state.getBlock() instanceof SecuritySensorBlock sensor ? sensor.kind() : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecuritySensorBE(ModBlockEntities.SECURITY_SENSOR.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.SECURITY_SENSOR.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((SecuritySensorBE) be).tick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "block.create_stats." + this.kind.blockId(),
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec(
                        "tooltip.create_stats.security_sensor_draw",
                        com.createstats.power.FeRates.perTick(SecuritySensorBE.drawPerTick()))));
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return com.createstats.security.SecurityImmobility.REACTION;
    }
}
