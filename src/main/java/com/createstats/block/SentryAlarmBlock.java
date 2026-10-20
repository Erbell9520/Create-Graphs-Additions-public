package com.createstats.block;

import com.createstats.alarm.annunciator.AnnunciatorVariant;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class SentryAlarmBlock extends AnnunciatorBlock {
    public SentryAlarmBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public AnnunciatorVariant variant() {
        return AnnunciatorVariant.SENTRY;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(SentryAlarmBlock::new);
    }
}
