package com.createstats.vccmk4;

import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BtaG1Block extends Vcc4Block {
    public static final MapCodec<BtaG1Block> CODEC = simpleCodec(BtaG1Block::new);

    public BtaG1Block(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends BtaG1BE> consoleType() {
        return ModBlockEntities.BTA_G1.get();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.VCC_MK4.get());
    }
}
