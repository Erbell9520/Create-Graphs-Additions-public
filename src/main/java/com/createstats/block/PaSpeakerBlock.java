package com.createstats.block;

import com.createstats.blockentity.PaSpeakerBE;
import com.createstats.pa.SpeakerKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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

// authored pointing up, the blockstate rotates. hitbox via LampShapes#rotate so it can't disagree
public class PaSpeakerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final VoxelShape upShape;
    private final SpeakerKind kind;
    private final java.util.function.Supplier<BlockEntityType<PaSpeakerBE>> type;

    public PaSpeakerBlock(Properties properties, VoxelShape upShape, SpeakerKind kind,
                          java.util.function.Supplier<BlockEntityType<PaSpeakerBE>> type) {
        super(properties);
        this.upShape = upShape;
        this.kind = kind;
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    public SpeakerKind kind() {
        return this.kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return LampShapes.rotate(this.upShape, state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PaSpeakerBE(this.type.get(), pos, state, this.kind);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> given) {
        if (given != this.type.get()) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, pos, st, be) -> ((PaSpeakerBE) be).clientTick()
                : (lvl, pos, st, be) -> ((PaSpeakerBE) be).serverTick();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moving) {
        if (state.getBlock() != newState.getBlock() && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof PaSpeakerBE speaker) {
            speaker.dropWiresAndUnlinkAll();
            speaker.invalidateNetwork();
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
