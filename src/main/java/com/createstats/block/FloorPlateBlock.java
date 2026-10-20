package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FloorPlateBlock extends Block {
    private final VoxelShape shape;

    public FloorPlateBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    protected VoxelShape plateShape() {
        return shape;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
