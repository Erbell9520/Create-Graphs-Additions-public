package com.createstats.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// no BE on purpose. a 9x6 door is 54 of these, the header knows everything
public class GarageDoorBodyBlock extends HorizontalDirectionalBlock
        implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    public static final MapCodec<GarageDoorBodyBlock> CODEC = simpleCodec(GarageDoorBodyBlock::new);

    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public static final float CURTAIN_Z0 = 6.0f;
    public static final float CURTAIN_Z1 = 10.0f;

    private static final Map<Direction, VoxelShape> SHUT = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHUT.put(facing, rotate(CURTAIN_Z0, CURTAIN_Z1, facing));
        }
    }

    private static VoxelShape rotate(double z0, double z1, Direction facing) {
        return switch (facing) {
            case SOUTH -> Block.box(0, 0, 16 - z1, 16, 16, 16 - z0);
            case WEST -> Block.box(z0, 0, 0, z1, 16, 16);
            case EAST -> Block.box(16 - z1, 0, 0, 16 - z0, 16, 16);
            default -> Block.box(0, 0, z0, 16, 16, z1);
        };
    }

    public GarageDoorBodyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : SHUT.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(OPEN);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? 0 : 15;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return GarageDoorHeaderBlock.use(level, pos, state, player);
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        final com.createstats.blockentity.GarageDoorBE door =
                GarageDoorHeaderBlock.controllerFrom(level, pos, state);
        return door == null ? pos : door.getBlockPos();
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return com.createstats.security.SecurityImmobility.REACTION;
    }
}
