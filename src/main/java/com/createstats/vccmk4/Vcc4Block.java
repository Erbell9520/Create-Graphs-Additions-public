package com.createstats.vccmk4;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.ground.GroundTelemetryBlock;
import com.createstats.util.CreateStatsTooltipHelper;
import com.createstats.vccmk4.core.ConsoleShape;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
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

public class Vcc4Block extends HorizontalDirectionalBlock implements EntityBlock, IWrenchable, GroundTelemetryBlock {
    public static final MapCodec<Vcc4Block> CODEC = simpleCodec(Vcc4Block::new);

    private static final Map<Direction, VoxelShape> SHAPES = buildShapes();

    public Vcc4Block(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static Map<Direction, VoxelShape> buildShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        Direction[] byTurns = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int turns = 0; turns < byTurns.length; turns++) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : ConsoleShape.boxes(turns)) {
                shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
            map.put(byTurns[turns], shape.optimize());
        }
        return map;
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
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!stack.has(com.simibubi.create.AllDataComponents.TRAIN_SCHEDULE)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return net.minecraft.world.ItemInteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) be = SableBridge.getBlockEntityOnContraption(level, pos);
        if (be instanceof Vcc4BE console && player instanceof ServerPlayer serverPlayer && level instanceof net.minecraft.server.level.ServerLevel server) {
            var read = com.createstats.vccmk4.route.CreateScheduleImport.read(stack, server.registryAccess());
            if (read == null || read.schedule().entries().isEmpty()) {
                serverPlayer.displayClientMessage(Component.translatable("create_stats.surveyor.schedule_empty"), true);
            } else {
                console.route().installSchedule(server, read.schedule(), read.progress(), serverPlayer);
            }
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) be = SableBridge.getBlockEntityOnContraption(level, pos);
        if (be instanceof Vcc4BE console && player instanceof ServerPlayer serverPlayer) {
            console.openFor(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.vcc_mk4");
    }

    // one answer for the type so a block can't build one and tick another
    protected BlockEntityType<? extends BtaG1BE> consoleType() {
        return com.createstats.registry.ModBlockEntities.VCC_MK4.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BtaG1BE(consoleType(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != consoleType()) return null;

        if (level.isClientSide) return (lvl, p, st, be) -> ConsoleFault.tick(be, ((BtaG1BE) be)::clientTick);
        return (lvl, p, st, be) -> ConsoleFault.tick(be, ((BtaG1BE) be)::tickServer);
    }
}
