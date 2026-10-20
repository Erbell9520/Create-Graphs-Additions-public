package com.createstats.block;

import com.createstats.blockentity.WallOutletBE;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jetbrains.annotations.Nullable;

public class WallOutletBlock extends WallPlateBlock implements EntityBlock {
    public static final EnumProperty<Lamp> LAMP = EnumProperty.create("lamp", Lamp.class);

    public enum Lamp implements StringRepresentable {
        DEAD("dead"),

        LIVE("live"),

        LOADED("loaded");

        private final String name;

        Lamp(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public WallOutletBlock(Properties properties, double x0, double y0, double z0,
                           double x1, double y1, double z1) {
        super(properties, x0, y0, z0, x1, y1, z1);
        registerDefaultState(defaultBlockState().setValue(LAMP, Lamp.DEAD));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LAMP);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(LAMP, Lamp.DEAD);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallOutletBE(ModBlockEntities.WALL_OUTLET.get(), pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof WallOutletBE outlet) {
                outlet.tick();
            }
        };
    }
}
