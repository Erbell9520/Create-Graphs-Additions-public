package com.createstats.vehiclelight;

import com.createstats.block.LampShapes;
import com.createstats.registry.ModBlockEntities;
import com.createstats.util.CreateStatsTooltipHelper;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.ToIntFunction;

public class VehicleLightBlock extends Block implements EntityBlock, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<VehicleLightGlow> GLOW = EnumProperty.create("glow", VehicleLightGlow.class);

    private final VehicleLightKind kind;

    public VehicleLightBlock(Properties properties, VehicleLightKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, net.minecraft.core.Direction.UP)
                .setValue(GLOW, VehicleLightGlow.OFF));
    }

    public static ToIntFunction<BlockState> emission(VehicleLightKind kind) {
        return state -> kind.emission(state.getValue(GLOW));
    }

    public VehicleLightKind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GLOW);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LampShapes.rotate(VehicleLightShapes.up(kind), state.getValue(FACING));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.VEHICLE_LIGHT.create(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.VEHICLE_LIGHT.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) -> ((VehicleLightBE) be).serverTick();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        CreateStatsTooltipHelper.addStageLightTooltip(tooltip, stack.getItem().getDescriptionId());
    }
}
