package com.createstats.vccmk4.air;

import com.createstats.aerial.AerialCommand;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AerialTelemetrySource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

// the right half hands everything to the left, otherwise half the clicks read nothing
public class AerialDeckPartBE extends BlockEntity implements AerialTelemetrySource {
    public AerialDeckPartBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public AerialBE master() {
        if (level == null) return null;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AerialDeckBlock)) return null;
        return level.getBlockEntity(AerialDeckBlock.master(state, worldPosition)) instanceof AerialBE aerial ? aerial : null;
    }

    @Nullable
    @Override
    public AerialSnapshot aerialSnapshot() {
        AerialBE aerial = master();
        return aerial == null ? null : aerial.aerialSnapshot();
    }

    @Override
    public boolean onAerialCommand(AerialCommand command) {
        AerialBE aerial = master();
        return aerial != null && aerial.onAerialCommand(command);
    }
}
