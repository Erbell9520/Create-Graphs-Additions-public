package com.createstats.vccmk4.air;

import com.createstats.aerial.AerialTelemetryBlock;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.CreateStatsTooltipHelper;
import com.createstats.vccmk4.ConsoleFault;
import com.createstats.vccmk4.air.core.CockpitShape;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// its own block. everything in one block was the FCC and nobody wants that again
public class AerialBlock extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable, AerialTelemetryBlock {
    public static final MapCodec<AerialBlock> CODEC = simpleCodec(AerialBlock::new);

    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        Direction[] byTurns = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int turns = 0; turns < byTurns.length; turns++) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : CockpitShape.boxes(CockpitShape.NORTH, turns)) {
                shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
            map.put(byTurns[turns], shape.optimize());
        }
        return map;
    }

    public AerialBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) be = SableBridge.getBlockEntityOnContraption(level, pos);
        if (!(be instanceof AerialBE aerial) || !(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;
        if (player.isShiftKeyDown()) {
            LeaveMode m = aerial.cycleLeaveMode();
            sp.displayClientMessage(Component.literal("Without a pilot in the air: " + m.label + ". The ship " + m.does + ".")
                    .withStyle(ChatFormatting.AQUA), true);
        } else {
            aerial.openFor(sp);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.vcc_mk4_aerial");
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AerialBE(ModBlockEntities.VCC_MK4_AERIAL.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.VCC_MK4_AERIAL.get()) return null;

        if (level.isClientSide) return (lvl, p, st, be) -> ConsoleFault.tick(be, ((AerialBE) be)::clientTick);

        return (lvl, p, st, be) -> ConsoleFault.tick(be, ((AerialBE) be)::serverTick);
    }
}
