package com.createstats.block;

import java.util.List;

import com.createstats.blockentity.StarterCapacitorBE;
import com.createstats.item.StarterCapacitorItem;
import com.createstats.power.CapacitorDischarge;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModDataComponents;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class StarterCapacitorBlock extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable {
    public static final MapCodec<StarterCapacitorBlock> CODEC = simpleCodec(StarterCapacitorBlock::new);
    public static final BooleanProperty FIRING = BooleanProperty.create("firing");
    public static final int FIRING_LIGHT = 12;

    private static final double[][] BOXES = {
            {0.5, 0.0, 1.5, 15.5, 1.5, 12.5},
            {0.5, 1.5, 1.5, 15.5, 9.5, 12.0},
            {0.0, 2.5, 5.5, 16.0, 7.5, 10.5},
            {3.0, 9.5, 6.5, 15.5, 16.0, 9.5},
            {13.0, 13.0, 9.5, 16.0, 16.0, 13.0},
    };
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape s = Shapes.empty();
            for (double[] b : BOXES) {
                s = Shapes.or(s, turned(b, d));
            }
            SHAPES[d.get2DDataValue()] = s.optimize();
        }
    }

    private static VoxelShape turned(double[] b, Direction d) {
        final double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (d) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public StarterCapacitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(FIRING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FIRING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !(level.getBlockEntity(pos) instanceof StarterCapacitorBE be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            final ItemStack stack = StarterCapacitorItem.carrying(asItem(), be.port().stored(), be.mode().ordinal());
            level.removeBlock(pos, false);
            if (!player.getInventory().add(stack)) {
                Block.popResource(level, pos, stack);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 0.9f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        final BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof StarterCapacitorBE cap) {
            return List.of(StarterCapacitorItem.carrying(asItem(), cap.port().stored(), cap.mode().ordinal()));
        }
        return super.getDrops(state, params);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StarterCapacitorBE be) {
            be.loadFromItem(stack.getOrDefault(ModDataComponents.STORED_FE.get(), 0),
                    stack.getOrDefault(ModDataComponents.CAPACITOR_MODE.get(), 0));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StarterCapacitorBE be) {
            return Math.round(15.0f * be.port().stored() / CapacitorDischarge.CAPACITY);
        }
        return 0;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.STARTER_CAPACITOR.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type != ModBlockEntities.STARTER_CAPACITOR.get()) {
            return null;
        }

        return (lvl, pos, blockState, be) -> ((StarterCapacitorBE) be).tick();
    }
}
